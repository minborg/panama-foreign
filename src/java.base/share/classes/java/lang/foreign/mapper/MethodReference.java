package java.lang.foreign.mapper;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * A
 * @param <T> base type
 * @param <R> return type
 */
@FunctionalInterface
public interface MethodReference<T, R> extends Function<T, R> /*, Serializable*/ {

    /**
     * {@return a value when applying this method reference to the provided {@code target}}
     *
     * @param target to apply this method reference
     */
    @Override
    R apply(T target);

    /**
     * {@return the method}
     */
    default Method method() {
        return method(this);
    };

    // The "magic" method
    private static <T, R> Method method(MethodReference<T, R> methodReference) {
        return null;
    }

}
