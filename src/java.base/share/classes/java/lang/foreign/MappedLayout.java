package java.lang.foreign;

import jdk.internal.foreign.layout.MappedLayoutImpl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.function.Function;

/**
 * A mapped layout with a carrier of type {@code T} who is backed by a target layout.
 *
 * @implSpec
 * Implementing classes are immutable, thread-safe and
 * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @param <T> the carrier type
 * @since 23
 */
public sealed interface MappedLayout<T> extends MemoryLayout permits MappedLayoutImpl {

    /**
     * {@inheritDoc}
     */
    @Override
    MappedLayout<T> withName(String name);

    /**
     * {@inheritDoc}
     */
    @Override
    MappedLayout<T> withoutName();

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperationException will always be thrown because this is
     *         an inherently unsupported operation on mapped layouts. Byte alignment
     *         can instead be defined using the target layout.
     */
    @Override
    MappedLayout<T> withByteAlignment(long byteAlignment);

    /**
     * {@return the carrier associated with this mapped layout}
     */
    Class<T> carrier();

    /**
     * {@return a var handle which can be used to access values described by this mapped
     *          layout, in a given memory segment}
     * <p>
     * The returned var handle's {@linkplain VarHandle#varType() var type} is the
     * {@linkplain ValueLayout#carrier() carrier type} of this mapped layout, and the
     * list of coordinate types is {@code (MemorySegment, long)}, where the
     * memory segment coordinate corresponds to the memory segment to be accessed, and
     * the {@code long} coordinate corresponds to the byte offset into the accessed
     * memory segment at which the access occurs.
     * <p>
     * The returned var handle checks that accesses are aligned according to
     * this mapped layout's {@linkplain MemoryLayout#byteAlignment() alignment constraint}.
     *
     * @apiNote This method is similar, but more efficient than calling
     *          {@code MemoryLayout#varHandle(PathElement...)} with an empty path
     *          element array, as it avoids the creation of the var args array.
     *
     * @apiNote The returned var handle features certain
     *          <a href="MemoryLayout.html#access-mode-restrictions">access mode restrictions</a>
     *          common to all memory access var handles derived from memory layouts. Only
     *          plain memory access is supported by the returned VarHandle.
     *
     * @see MemoryLayout#varHandle(PathElement...)
     */
    VarHandle varHandle();

    /**
     * {@return the target layout associated with this mapped layout (if any)}
     */
    MemoryLayout targetLayout();

    /**
     * {@return a new mapped layout that would apply the provided {@code getterFilter} after
     *          performing get operations on a memory segment and that would apply the
     *          provided {@code setterFilter} before performing set operations on a
     *          memory segment}
     * <p>
     * It should be noted that the type R can represent almost any class and is not
     * restricted to records.
     *
     * @param newCarrier   the new type the returned mapper shall use
     * @param getterFilter to apply after get operations using this mapped layout
     * @param setterFilter to apply before set operations using this mapped layout
     * @param <R>          the carrier type of the new mapped layout
     */
    <R> MappedLayout<R> map(Class<R> newCarrier,
                            MethodHandle getterFilter,
                            MethodHandle setterFilter);

    /**
     * {@return a new mapped layout that would apply the provided {@code toMapper} after
     *          performing get operations on a memory segment and that would apply the
     *          provided {@code fromMapper} before performing set operations on a
     *          memory segment}
     * <p>
     * It should be noted that the type R can represent almost any class and is not
     * restricted to records.
     * <p>
     * This is a convenience method for the more low level
     * {@linkplain #map(Class, MethodHandle, MethodHandle)} method.
     *
     * @param newCarrier  the new type the returned mapper shall use
     * @param toMapper    to apply after get operations using this mapped layout
     * @param fromMapper  to apply before set operations using this mapped layout
     * @param <R>         the carrier type of the new mapped layout
     * @see #map(Class, MethodHandle, MethodHandle)
     */
    <R> MappedLayout<R> map(Class<R> newCarrier,
                            Function<? super T, ? extends R> toMapper,
                            Function<? super R, ? extends T> fromMapper);


}
