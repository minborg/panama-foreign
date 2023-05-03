package java.lang.foreign.rec;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * MR
 * @param <T> Source type
 * @param <R> Return type
 */
@FunctionalInterface
public interface MethodReference<T, R> extends Function<T, R> /*, Serializable */ {

    /**
     * {@return a value when applying this method reference to the provided {@code target}}
     * @param target to apply this method reference
     */
    @Override
    R apply(T target);

    /**
     * {@return m}
     */
    default Method method() {
        return method(this);
    };

    /**
     * {@return M}
     * @param methodReference to use
     * @param <T> Source
     * @param <R> Target
     */
    // The "magic" method
    private static <T, R> Method method(MethodReference<T, R> methodReference) {
        return null;
    }

}
