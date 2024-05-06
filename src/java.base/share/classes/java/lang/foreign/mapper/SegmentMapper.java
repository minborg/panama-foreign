package java.lang.foreign.mapper;

import jdk.internal.foreign.mapper.InternalInvocationMappers;
import jdk.internal.foreign.mapper.MapperUtil;
import jdk.internal.foreign.mapper.SegmentMapperImpl;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A segment mapper can project memory segment onto and from class instances.
 * <p>
 * More specifically, a segment mapper can project a backing
 * {@linkplain MemorySegment MemorySegment} into new {@link Record} instances
 * means of matching the names of the record
 * components with the names of member layouts in a group layout.
 * A segment mapper can also be used in the other direction, where records
 * can be used to update a target memory segment. By using any of
 * the {@linkplain #map(Class, Function, Function) map} operations, segment mappers can be
 * used to map between memory segments and additional Java types other than records
 * (such as JavaBeans).
 *
 * <p>
 * In short, a segment mapper finds, for each record component,
 * a corresponding member layout with the same name in the group layout. There are some
 * restrictions on the record component type and the corresponding member layout type
 * (e.g. a record component of type {@code int} can only be matched with a member layout
 * having a carrier type of {@code int.class} (such as {@link ValueLayout#JAVA_INT})).
 * <p>
 * Using the member layouts (e.g. observing offsets, alignment constraints, and
 * {@link java.nio.ByteOrder byte ordering}), a number of extraction methods are then
 * identified for all the record components or interface methods and these are stored
 * internally in the segment mapper.
 *
 * <h2 id="mapping-records">Mapping Records</h2>
 *
 * The example below shows how to extract an instance of a public
 * <em>{@code Point} record class</em> from a {@link MemorySegment} and vice versa:
 * {@snippet lang = java:
 *
 *  static final GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));
 *  public record Point(int x, int y){}
 *  ...
 *  MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 0, 0});
 *
 *  // Obtain a SegmentMapper for the Point record type
 *  SegmentMapper<Point> recordMapper = SegmentMapper.ofRecord(Point.class, POINT);
 *
 *  // Extracts a new Point record from the provided MemorySegment
 *  Point point = recordMapper.get(segment); // Point[x=3, y=4]
 *
 *  // Writes the Point record to another MemorySegment
 *  MemorySegment otherSegment = Arena.ofAuto().allocate(MemoryLayout.sequenceLayout(2, POINT));
 *  recordMapper.setAtIndex(otherSegment, 1, point); // segment: 0, 0, 3, 4
 *}
 * <p>
 * Boxing, widening, narrowing and general type conversion must be explicitly handled by
 * user code. In the following example, the above {@code Point} (using primitive
 * {@code int x} and {@code int y} coordinates) are explicitly mapped to a narrowed
 * point type (instead using primitive {@code byte x} and {@code byte y} coordinates):
 * <p>
 * {@snippet lang = java:
 * public record NarrowedPoint(byte x, byte y) {
 *
 *     static NarrowedPoint fromPoint(Point p) {
 *         return new NarrowedPoint((byte) p.x, (byte) p.y);
 *     }
 *
 *     static Point toPoint(NarrowedPoint p) {
 *         return new Point(p.x, p.y);
 *     }
 *
 * }
 *
 * SegmentMapper<NarrowedPoint> narrowedPointMapper =
 *         SegmentMapper.ofRecord(Point.class, POINT)              // SegmentMapper<Point>
 *         .map(NarrowedPoint.class, NarrowedPoint::fromPoint, NarrowedPoint::toPoint); // SegmentMapper<NarrowedPoint>
 *
 * // Extracts a new NarrowedPoint from the provided MemorySegment
 * NarrowedPoint narrowedPoint = narrowedPointMapper.get(segment); // NarrowedPoint[x=3, y=4]
 *}
 *
 * <h2 id="formal-mapping">Formal mapping description</h2>
 *
 * Components and layouts are matched with respect to their name and the exact return type and/or
 * the exact parameter types. No widening or narrowing is employed.
 *
 * <h2 id="restrictions">Restrictions</h2>
 *
 * Generic records need to have their generic type parameters (if any)
 * know at compile time. This applies to all extended interfaces recursively.
 * <p>
 * Records must not implement (directly and/or via inheritance) more than
 * one abstract method with the same name and erased parameter types. Hence, covariant
 * overriding is not supported.
 *
 * <h2 id="general-mapping">General mapping</h2>
 *
 * In addition to mapping records, general mapping capabilities can be made
 * using a factory method that takes custom getter and setter method handles.
 *
 * @param <T> the type this mapper converts MemorySegments from and to.
 *
 * @implSpec Implementations of this interface are immutable, thread-safe and
 *           <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 23
 */
public sealed interface SegmentMapper<T> permits RecordMapper, PrimitiveMapper, SegmentMapperImpl {

    /**
     * {@return the carrier that this mapper is mapping to and from}
     */
    Class<T> carrier();

    /**
     * {@return the original {@link GroupLayout } that this mapper is using to map record
     * components}
     * <p>
     * Composed segment mappers (obtained via either the {@link SegmentMapper#map(Class, Function)}
     * or the {@link SegmentMapper#map(Class, Function, Function)} will still return the memory
     * layout from the <em>original</em> SegmentMapper.
     */
    MemoryLayout layout();

    // Convenience methods

    /**
     * {@return a new instance of type T projected at the provided
     *          external {@code segment} at offset zero}
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang = java:
     *    get(segment, 0L);
     * }
     *
     * @param segment the external segment to be projected to the new instance
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with the provided segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if
     *         {@code layout().byteSize() > segment.byteSize()}
     */
    default T get(MemorySegment segment) {
        return get(segment, 0L);
    }

    /**
     * {@return a new instance of type T projected at the provided external
     *          {@code segment} at the given {@code index} scaled by the
     *          {@code layout().byteSize()}}
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang = java:
     *    get(segment, layout().byteSize() * index);
     * }
     *
     * @param segment the external segment to be projected to the new instance
     * @param index a logical index, the offset in bytes (relative to the provided
     *              segment address) at which the access operation will occur can
     *              be expressed as {@code (index * layout().byteSize())}
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with the provided segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if {@code index * layout().byteSize()} overflows
     * @throws IndexOutOfBoundsException if
     *         {@code index * layout().byteSize() > segment.byteSize() - layout.byteSize()}
     */
    default T getAtIndex(MemorySegment segment, long index) {
        return get(segment, layout().byteSize() * index);
    }

    /**
     * {@return a new sequential {@code Stream} of elements of type T}
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang=java :
     * segment.elements(layout())
     *     .map(this::get);
     * }
     * @param segment to carve out instances from
     * @throws IllegalArgumentException if {@code layout().byteSize() == 0}.
     * @throws IllegalArgumentException if {@code segment.byteSize() % layout().byteSize() != 0}.
     * @throws IllegalArgumentException if {@code layout().byteSize() % layout().byteAlignment() != 0}.
     * @throws IllegalArgumentException if this segment is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the
     *         alignment constraint</a> in the layout of this segment mapper.
     */
    default Stream<T> stream(MemorySegment segment) {
        return segment.elements(layout())
                .map(this::get);
    }

    /**
     * {@return a new sequential {@code Stream} of {@code pageSize} elements of
     *          type T starting at the element {@code pageNumber * pageSize}}
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang=java :
     * stream(segment)
     *     .skip(pageNumber * pageSize)
     *     .limit(pageSize);
     * }
     * but may be much more efficient for large page numbers.
     *
     * @param segment    to carve out instances from
     * @param pageSize   the size of each page
     * @param pageNumber the page number to which to skip
     * @throws IllegalArgumentException if {@code layout().byteSize() == 0}.
     * @throws IllegalArgumentException if {@code segment.byteSize() % layout().byteSize() != 0}.
     * @throws IllegalArgumentException if {@code layout().byteSize() % layout().byteAlignment() != 0}.
     * @throws IllegalArgumentException if this segment is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the
     *         alignment constraint</a> in the layout of this segment mapper.
     */
    default Stream<T> page(MemorySegment segment,
                           long pageSize,
                           long pageNumber) {
        long skipBytes = Math.min(segment.byteSize(), layout().scale(0, pageNumber * pageSize));
        MemorySegment skippedSegment = segment.asSlice(skipBytes);
        return stream(skippedSegment)
                .limit(pageSize);
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
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if
     *         {@code offset > segment.byteSize() - layout().byteSize()}
     */
    @SuppressWarnings("unchecked")
    default T get(MemorySegment segment, long offset) {
        try {
            return (T) getter()
                    .asType(MethodType.methodType(Object.class, MemorySegment.class, long.class))
                    .invokeExact(segment, offset);
        } catch (NullPointerException |
                 IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke getter() with " +
                    "segment="  + segment +
                    ", offset=" + offset, e);
        }
    }

    /**
     * Writes the provided instance {@code t} of type T into the provided {@code segment}
     * at offset zero.
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang = java:
     *    set(segment, 0L, t);
     * }
     *
     * @param segment in which to write the provided {@code t}
     * @param t instance to write into the provided segment
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with this segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if {@code layout().byteSize() > segment.byteSize()}
     * @throws UnsupportedOperationException if this segment is
     *         {@linkplain MemorySegment#isReadOnly() read-only}
     * @throws UnsupportedOperationException if {@code value} is not a
     *         {@linkplain MemorySegment#isNative() native} segment
     * @throws IllegalArgumentException if an array length does not correspond to the
     *         {@linkplain SequenceLayout#elementCount() element count} of a sequence layout
     * @throws NullPointerException if a required parameter is {@code null}
     */
    default void set(MemorySegment segment, T t) {
        set(segment, 0L, t);
    }

    /**
     * Writes the provided {@code t} instance of type T into the provided {@code segment}
     * at the provided {@code index} scaled by the {@code layout().byteSize()}}.
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang = java:
     *    set(segment, layout().byteSize() * index, t);
     * }
     * @param segment in which to write the provided {@code t}
     * @param index a logical index, the offset in bytes (relative to the provided
     *              segment address) at which the access operation will occur can be
     *              expressed as {@code (index * layout().byteSize())}
     * @param t instance to write into the provided segment
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with this segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if {@code offset > segment.byteSize() - layout.byteSize()}
     * @throws UnsupportedOperationException if this segment is
     *         {@linkplain MemorySegment#isReadOnly() read-only}
     * @throws UnsupportedOperationException if {@code value} is not a
     *         {@linkplain MemorySegment#isNative() native} segment
     * @throws IllegalArgumentException if an array length does not correspond to the
     *         {@linkplain SequenceLayout#elementCount() element count} of a sequence layout
     * @throws NullPointerException if a required parameter is {@code null}
     */
    default void setAtIndex(MemorySegment segment, long index, T t) {
        set(segment, layout().byteSize() * index, t);
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
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if {@code offset > segment.byteSize() - layout.byteSize()}
     * @throws UnsupportedOperationException if
     *         this segment is {@linkplain MemorySegment#isReadOnly() read-only}
     * @throws UnsupportedOperationException if
     *         {@code value} is not a {@linkplain MemorySegment#isNative() native} segment // Todo: only for pointers
     * @throws IllegalArgumentException if an array length does not correspond to the
     *         {@linkplain SequenceLayout#elementCount() element count} of a sequence layout
     * @throws NullPointerException if a required parameter is {@code null}
     */
    default void set(MemorySegment segment, long offset, T t) {
        try {
            setter()
                    .asType(MethodType.methodType(void.class, MemorySegment.class, long.class, Object.class))
                    .invokeExact(segment, offset, (Object) t);
        } catch (IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException |
                 UnsupportedOperationException |
                 NullPointerException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke setter() with " +
                    "segment=" + segment +
                    ", offset=" + offset +
                    ", t=" + t, e);
        }
    }

    // Basic methods

    /**
     * {@return a "getter" method handle that returns new instances of type T projected at
     *          a provided external {@code MemorySegment} at a provided {@code long} offset}
     * <p>
     * The returned method handle has the following characteristics:
     * <ul>
     *     <li>its return type is {@code T};</li>
     *     <li>it has a leading parameter of type {@code MemorySegment}
     *         corresponding to the memory segment to be accessed</li>
     *     <li>it has a trailing {@code long} parameter, corresponding to
     *         the base offset</li>
     * </ul>
     *
     * @see #get(MemorySegment, long)
     */
    MethodHandle getter();

    /**
     * {@return a "setter" method handle that writes a provided instance of type T into
     *          a provided {@code MemorySegment} at a provided {@code long} offset}
     * <p>
     * The returned method handle has the following characteristics:
     * <ul>
     *     <li>its return type is void;</li>
     *     <li>it has a leading parameter of type {@code MemorySegment}
     *         corresponding to the memory segment to be accessed</li>
     *     <li>it has a following {@code long} parameter, corresponding to
     *         the base offset</li>
     *     <li>it has a trailing {@code T} parameter, corresponding to
     *         the value to set</li>
     * </ul>
     *
     * @see #set(MemorySegment, long, Object)
     */
    MethodHandle setter();

    /**
     * {@return a new segment mapper that would apply the provided {@code toMapper} after
     *          performing get operations on this segment mapper and that would apply the
     *          provided {@code fromMapper} before performing set operations on this
     *          segment mapper}
     * <p>
     * It should be noted that the type R can represent almost any class and is not
     * restricted to records.
     *
     * @param  newType the new type the returned mapper shall use
     * @param toMapper to apply after get operations on this segment mapper
     * @param fromMapper to apply before set operations on this segment mapper
     * @param <R> the type of the new segment mapper
     * @throws UnsupportedOperationException if this is an interface mapper.
     */
    default <R> SegmentMapper<R> map(Class<R> newType,
                                     Function<? super T, ? extends R> toMapper,
                                     Function<? super R, ? extends T> fromMapper) {
        return MapperUtil.map(this, newType, toMapper, fromMapper);
    }

    /**
     * {@return a new segment mapper that would apply the provided {@code toMapper} after
     *          performing get operations on this segment mapper and that would throw an
     *          {@linkplain UnsupportedOperationException} for set operations if this
     *          segment mapper is a record mapper}
     * <p>
     * It should be noted that the type R can represent almost any class and is not
     * restricted to records.
     *
     * @param  newType the new type the returned mapper shall use
     * @param toMapper to apply after get operations on this segment mapper
     * @param <R> the type of the new segment mapper
     */
    default <R> SegmentMapper<R> map(Class<R> newType,
                                     Function<? super T, ? extends R> toMapper) {
        return MapperUtil.map(this, newType, toMapper);
    }


    /**
     * Represents a function that accepts two arguments in the form of a MemorySegment and
     * a long offset and produces a result of type T. This is a two-arity specialization of
     * {@link Function}.
     *
     * @param <T> the type of the result of the function
     *
     * @see Function
     * @since 23
     */
    @FunctionalInterface
    interface Getter<T> {

        /**
         * {@return a value of type T given the provided {@code segment} and {@code offset}}
         * @param segment from which to read the value
         * @param offset  at which offset to begin reading the value from
         */
        T get(MemorySegment segment, long offset);
    }

    /**
     * Represents an operation that accepts three input arguments in the form
     * of a MemorySegment, a long offset, and a value of type T and that returns
     * no result. This is a three-arity specialization of {@link Consumer}.
     * Unlike most other functional interfaces, {@code Setter} is expected
     * to operate via side-effects.
     *
     * @param <T> the type of the third argument to the operation
     *
     * @see Consumer
     * @since 23
     */
    @FunctionalInterface
    interface Setter<T> {

        /**
         * Performs a write operation with the given arguments.
         *
         * @param segment to write a representation of the value into
         * @param offset  at which offset to begin writing the value to
         * @param value   the value to write
         */
        void set(MemorySegment segment, long offset, T value);
    }

    /**
     * {@return a method handle that delegates to the provided {@code targetHandle} but
     *          by adapting it the polymorphic signature of the provided {@code methodType}}
     * <p>
     * Adaptation may involve applying zero or more mappers of type {@linkplain RecordMapper},
     * {@linkplain PrimitiveMapper}, and {@linkplain ArrayMapper} to the arguments and/or
     * the returned value of the {@code targetHandle}.
     *
     * @param targetHandle to adapt
     * @param methodType   describing the desired polymorphic signature of
     *                     the returned method handle
     * @throws IllegalArgumentException if no combination of mappers exists to
     *         adapt the {@code targetHandle} to the polymorphic signature of
     *         {@code methodType}
     */
    static MethodHandle adapt(MethodHandle targetHandle,
                              MethodType methodType) {
        Objects.requireNonNull(targetHandle);
        Objects.requireNonNull(methodType);
        return InternalInvocationMappers.adapt(targetHandle, methodType);
    }

    /**
     * {@return a proxy implementation of the provided {@code functionalInterface}
     *          backed by a native call with the name of the abstract method of the
     *          interface and with with the provided {@code native signature}}
     *
     * @param nativeSignature     for the underlying native method call
     * @param functionalInterface for which to create a proxy implementation
     * @param <T>                 functional interface type
     * @throws IllegalArgumentException if the provided {@code functionalInterface}
     *         is not an interface, is a sealed interface, is hidden or
     *         is not annotated with {@linkplain FunctionalInterface}
     */
    static <T> T downcall(FunctionDescriptor nativeSignature,
                          Class<T> functionalInterface) {
        MapperUtil.requireFunctionalInterface(functionalInterface);
        Objects.requireNonNull(nativeSignature);
        return InternalInvocationMappers.ofProxy(nativeSignature, functionalInterface);
    }


}
