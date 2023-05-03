package java.lang.foreign.rec;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.List;

/**
 * A
 * @param <S> s
 * @param <T> t
 */
public sealed interface LayoutPath<S, T> {

    /**
     * {@return m}
     */
    List<Method> methods();

    /**
     * {@return h}
     */
    VarHandle handle();

    /**
     * {@return o}
     */
    long offset();

    /**
     * {@return sl}
     */
    Class<?> sourceLayout();

    /**
     * {@return tl}
     */
    Class<?> targetLayout();

    /**
     * {@return lp}
     * @param <R> r
     * @param extractor e
     */
    <R> LayoutPath<S, R> andThen(MethodReference<T, R> extractor);

    /**
     * {@return lp}
     * @param index i
     * @param elementType i
     * @param <R> return type
     */
    <R> LayoutPath<S, R> andThen(long index, Class<R> elementType);

    /**
     * {@return lp}
     * @param extractor to apply
     * @param <S> source type
     * @param <T> target type
     */
    static <S, T> LayoutPath<S, T> of(MethodReference<S, T> extractor) {
        return null;
    }


    /**
     * Impl
     * @param <S> source type
     * @param <T> target type
     */
    final class PathImpl<S, T> implements LayoutPath<S, T> {

        /**
         * C
         */
        public PathImpl() {
        }

        @Override
        public List<Method> methods() {
            return null;
        }

        @Override
        public <R> LayoutPath<S, R> andThen(MethodReference<T, R> extractor) {
            return null;
        }

        @Override
        public VarHandle handle() {
            return null;
        }

        @Override
        public long offset() {
            return 0;
        }

        @Override
        public Class<?> sourceLayout() {
            return null;
        }

        @Override
        public Class<?> targetLayout() {
            return null;
        }

        @Override
        public <R> LayoutPath<S, R> andThen(long index, Class<R> elementType) {
            return null;
        }
    }


}
