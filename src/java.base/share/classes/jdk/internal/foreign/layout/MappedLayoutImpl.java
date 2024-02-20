package jdk.internal.foreign.layout;

import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.mapper.SegmentRecordMapper2;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MappedLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SequenceLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class MappedLayoutImpl<T>
        extends AbstractLayout<MappedLayoutImpl<T>>
        implements MappedLayout<T> {


    private static final JavaLangInvokeAccess JAVA_LANG_INVOKE_ACCESS = SharedSecrets.getJavaLangInvokeAccess();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final VarHandle CARRIER_VH;

    static {
        try {
            CARRIER_VH = LOOKUP.findVarHandle(MappedLayoutImpl.class, "handle", VarHandle.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final MethodHandles.Lookup lookup;
    private final Class<T> carrier;
    private final MemoryLayout targetLayout;
    private final MethodHandle getter;
    private final MethodHandle setter;
    @Stable
    private VarHandle handle;

    private MappedLayoutImpl(MethodHandles.Lookup lookup,
                             Class<T> carrier,
                             MemoryLayout targetLayout,
                             MethodHandle getter,
                             MethodHandle setter,
                             long byteAlignment,
                             Optional<String> name) {
        super(targetLayout.byteSize(), byteAlignment, name);
        this.lookup = lookup;
        this.carrier = carrier;
        this.getter = getter;
        this.setter = setter;
        this.targetLayout = targetLayout;
    }

    @Override
    public Class<T> carrier() {
        return carrier;
    }

    @Override
    public MappedLayoutImpl<T> withByteAlignment(long byteAlignment) {
        throw new UnsupportedOperationException("The byte alignment is fixed for mapped layouts and obtained from the target layout");
    }

    @Override
    public VarHandle varHandle(PathElement... elements) {
        if (elements.length == 0) {
            return varHandle();
        }
        return targetLayout.varHandle(elements);
    }

    @Override
    public VarHandle varHandle() {
        if (handle == null) {
            VarHandle newVarHandle = varHandle0();
            // Only propagate a single witness value
            handle = (VarHandle) CARRIER_VH.compareAndExchange(this, null, newVarHandle);
            if (handle == null) {
                handle = newVarHandle;
            }

        }
        return handle;
    }

    @Override
    public long byteOffset(PathElement... elements) {
        return targetLayout.byteOffset(elements);
    }

    @Override
    public MethodHandle byteOffsetHandle(PathElement... elements) {
        return targetLayout.byteOffsetHandle(elements);
    }

    @Override
    public VarHandle arrayElementVarHandle(PathElement... elements) {
        return targetLayout.arrayElementVarHandle(elements);
    }

    @Override
    public MethodHandle sliceHandle(PathElement... elements) {
        return targetLayout.sliceHandle(elements);
    }

    @Override
    public MemoryLayout select(PathElement... elements) {
        return targetLayout.select(elements);
    }

    @Override
    public MemoryLayout targetLayout() {
        return targetLayout;
    }

    @Override
    public <R> MappedLayout<R> map(Class<R> newCarrier,
                                   Function<? super T, ? extends R> toMapper,
                                   Function<? super R, ? extends T> fromMapper) {

        MethodType methodType = MethodType.methodType(Object.class, Function.class, Object.class);

        MethodHandle getterFilter = findStatic("mapTo", methodType);
        getterFilter = MethodHandles.insertArguments(getterFilter, 0, toMapper);

        MethodHandle setterFilter = findStatic("mapFrom", methodType);
        setterFilter = MethodHandles.insertArguments(setterFilter, 0, fromMapper);

        return map(newCarrier, getterFilter, setterFilter);
    }

    @Override
    public <R> MappedLayout<R> map(Class<R> newCarrier,
                                   MethodHandle getterFilter,
                                   MethodHandle setterFilter) {
        MethodHandle mappedGetter = MethodHandles.filterReturnValue(getter, getterFilter);
        MethodHandle mappedSetter = MethodHandles.filterArguments(setter, 2, setterFilter); // pos 2 is T
        return new MappedLayoutImpl<>(lookup, newCarrier, targetLayout, mappedGetter, mappedSetter, byteAlignment(), name());
    }

    /**
     * {@return a new instance of type T projected from at provided
     *          external {@code segment} at the provided {@code offset}}
     *
     * @param segment the external segment to be projected at the new instance
     * @param offset  from where in the segment to project the new instance
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with the provided segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of this layout's target layout
     * @throws IndexOutOfBoundsException if
     *         {@code offset > segment.byteSize() - layout().byteSize()}
     */
    @SuppressWarnings("unchecked")
    public T get(MemorySegment segment, long offset) {
        try {
            return (T) getter
                    .invokeExact(segment, offset);
        } catch (NullPointerException |
                 IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke getter " + getter + " with " +
                    "segment="  + segment +
                    ", offset=" + offset, e);
        }
    }

    /**
     * Writes the provided instance {@code t} of type T into the provided {@code segment}
     * at the provided {@code offset}.
     *
     * @param segment in which to write the provided {@code t}
     * @param offset offset in bytes (relative to the provided segment address) at which
     *               this access operation will occur
     * @param t instance to write into the provided segment
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with this segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of this layout's target layout.
     * @throws IndexOutOfBoundsException if {@code offset > segment.byteSize() - layout.byteSize()}
     * @throws UnsupportedOperationException if
     *         this segment is {@linkplain MemorySegment#isReadOnly() read-only}
     * @throws UnsupportedOperationException if
     *         {@code value} is not a {@linkplain MemorySegment#isNative() native} segment // Todo: only for pointers
     * @throws IllegalArgumentException if an array length does not correspond to the
     *         {@linkplain SequenceLayout#elementCount() element count} of a sequence layout
     * @throws NullPointerException if a required parameter is {@code null}
     */
    public void set(MemorySegment segment, long offset, T t) {
        try {
            setter.invokeExact(segment, offset, (Object) t);
        } catch (IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException |
                 UnsupportedOperationException |
                 NullPointerException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke setter " + setter + " with " +
                    "segment=" + segment +
                    ", offset=" + offset +
                    ", t=" + t, e);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MappedLayoutImpl<?> ml &&
                name().equals(ml.name()) &&
                targetLayout.equals(ml.targetLayout) &&
                carrier.equals(ml.carrier) &&
                super.equals(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name(), targetLayout, carrier, super.hashCode());
    }

    @Override
    public String toString() {
        String descriptor = carrier.descriptorString();
        return decorateLayoutString(String.format("%s%d", descriptor, byteSize()));
    }

    @Override
    MappedLayoutImpl<T> dup(long byteAlignment, Optional<String> name) {
        return new MappedLayoutImpl<>(lookup, carrier, targetLayout, getter, setter, byteAlignment, name);
    }

    public VarHandle varHandle0() {
        if (getter != null && setter != null) {
            return JAVA_LANG_INVOKE_ACCESS
                    .memorySegmentMappedHandle(targetLayout.byteSize(), targetLayout.byteAlignment()-1, getter, setter);
        }
        throw new UnsupportedOperationException("Unable to determine varHandle()");
    }

    public static <T extends Record> MappedLayout<T> of(MethodHandles.Lookup lookup,
                                                        Class<T> carrier,
                                                        GroupLayout targetLayout) {

        var mapper = SegmentRecordMapper2.create(lookup, carrier, targetLayout);

        return new MappedLayoutImpl<>(lookup,
                carrier,
                targetLayout,
                mapper.getHandle(),
                mapper.setHandle(),
                targetLayout.byteAlignment(),
                Optional.empty());
    }

    public static <T> MappedLayout<T> of(Class<T> carrier,
                                         MemoryLayout targetLayout,
                                         MethodHandle getter,
                                         MethodHandle setter) {
        return new MappedLayoutImpl<>(MethodHandles.publicLookup(),
                carrier,
                targetLayout,
                getter,
                setter,
                targetLayout.byteAlignment(),
                Optional.empty());
    }

    public static <T> MappedLayout<T> of(Class<T> carrier,
                                         MemoryLayout targetLayout,
                                         Function<? super MemorySegment, ? extends T> getter,
                                         BiConsumer<? super MemorySegment, ? super T> setter) {
        return of(carrier, targetLayout, getterHandle(getter), setterHandle(setter));
    }

    // Methods for adapting method handles

    public MethodHandle filterReturnValue(MethodHandle target) {
        MethodHandle filter = MethodHandles.insertArguments(getter, 1, 0L);
        return MethodHandles.filterReturnValue(target, filter);
    }

    public MethodHandle filterArgument(MethodHandle target,
                                       SegmentAllocator allocator,
                                       int pos) {
        return null;
    }

    private MethodHandle filterArgument0(SegmentAllocator allocator) {
        // Allocate segment
        return null;
    }


    // Methods for the map() operation

    // Used reflectively
    private static <T, R> R mapTo(Function<? super T, ? extends R> toMapper, T t) {
        return toMapper.apply(t);
    }

    // Used reflectively
    private static <T, R> T mapFrom(Function<? super R, ? extends T> fromMapper, R r) {
        return fromMapper.apply(r);
    }

    // Methods for turning lambdas/functions into method handles

    private static <T> MethodHandle getterHandle(Function<? super MemorySegment, ? extends T> getter) {
        MethodType type = MethodType.methodType(Object.class, MemorySegment.class, long.class, Function.class);
        MethodHandle mh = findStatic( "getter0", type);
        return MethodHandles.insertArguments(mh, 2, getter);
    }

    private static <T> MethodHandle setterHandle(BiConsumer<? super MemorySegment, ? super T> setter) {
        MethodType type = MethodType.methodType(void.class, MemorySegment.class, long.class, Object.class, BiConsumer.class);
        MethodHandle mh = findStatic("setter0", type);
        return MethodHandles.insertArguments(mh, 3, setter);
    }

    // Used reflectively
    private static <T> T getter0(MemorySegment segment, long offset,
                                 Function<? super MemorySegment, ? extends T> getter) {
        return getter.apply(segment.asSlice(offset));
    }

    // Used reflectively
    private static <T> void setter0(MemorySegment segment, long offset, T value,
                                    BiConsumer<? super MemorySegment, ? super T> setter) {
        setter.accept(segment.asSlice(offset), value);
    }

    // Method for finding local static methods in this class
    private static MethodHandle findStatic(String name, MethodType methodType) {
        try {
            return LOOKUP.findStatic(MappedLayoutImpl.class, name, methodType);
        } catch (ReflectiveOperationException e) {
            // Should not happen
            throw new InternalError(e);
        }
    }



}
