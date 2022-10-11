package jdk.internal.foreign;

import java.lang.foreign.MemoryLayout;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a transformer that take a memory layout and returns a
 * transformed memory layout.
 *
 * @since 20
 */
@FunctionalInterface
public interface LayoutTransformer {

    /**
     * Transforms the provided {@code layout}.
     *
     * @param layout to be transformed
     * @return the result of the transformation
     */
    MemoryLayout transform(MemoryLayout layout);

    /**
     * Returns a composed transformer that first applies this transformer to
     * its input, and then applies the {@code after} transformer to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param after the transformer to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the {@code after} function
     * @throws NullPointerException if after is null
     */
    default LayoutTransformer andThen(LayoutTransformer after) {
        Objects.requireNonNull(after);
        return (MemoryLayout l) -> after.transform(transform(l));
    }

    /**
     * {@return a transformer that always returns its input argument}.
     */
    static LayoutTransformer identity() {
        return m -> m;
    }

    /**
     * Returns a transformer that tries to match the provided {@code type} and, if successful,
     * applies the provided {@code matchingMapper}, otherwise an {@link #identity()} transformer is
     * applied (i.e. no transformation is done).
     *
     * @param type           to match
     * @param matchingMapper to apply on matches
     * @param <T>            matching type
     * @return a matching transformer.
     */
    static <T> LayoutTransformer matching(Class<T> type,
                                          Function<? super T, ? extends MemoryLayout> matchingMapper) {
        return l -> type.isInstance(l) ? matchingMapper.apply(type.cast(l)) : l;
    }



}
