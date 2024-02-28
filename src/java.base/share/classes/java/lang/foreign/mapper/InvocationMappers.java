package java.lang.foreign.mapper;

import jdk.internal.foreign.mapper.InternalInvocationMappers;
import jdk.internal.foreign.mapper.MapperUtil;

import java.lang.foreign.FunctionDescriptor;
import java.util.Objects;

/**
 * A collection of utility methods that can be used to map native calls
 * to functional interfaces.
 *
 * @since 23
 */
public final class InvocationMappers {

    // Suppresses default constructor, ensuring non-instantiability.
    private InvocationMappers() {}

    /**
     * {@return a proxy implementation of the provided {@code functionalInterface}
     *          backed by a native call with the name of the abstract method of the
     *          interface and with a native signature that is automatically derived by
     *          analysing the return and parameter types of the abstract method of the interface}
     *
     * @param functionalInterface for which to create a proxy implementation
     * @param <T> functional interface type
     * @throws IllegalArgumentException if the provided {@code functionalInterface}
     *         is not an interface, is a sealed interface, is hidden or
     *         is not annotated with {@linkplain FunctionalInterface}
     * @throws IllegalArgumentException if there are ambiguities in mapping such as
     *         a MemorySegment is present as an argument
     * @see #ofProxy(FunctionDescriptor, Class)
     */
/*    @Deprecated
    public static <T> T ofProxy(Class<T> functionalInterface) {
        MapperUtil.requireFunctionalInterface(functionalInterface);
        return InternalInvocationMappers.ofProxy(functionalInterface);
    }*/

/*    *//**
     * {@return a proxy implementation of the provided {@code functionalInterface}
     *      backed by a native call with the name of the abstract method of the
     *      interface and with with the provided {@code native signature}}
     *
     * @param nativeSignature for the underlying native method call
     * @param functionalInterface for which to create a proxy implementation
     * @param <T> functional interface type
     * @throws IllegalArgumentException if the provided {@code functionalInterface}
     *         is not an interface, is a sealed interface, is hidden or
     *         is not annotated with {@linkplain FunctionalInterface}
     * @see #ofProxy(FunctionDescriptor, Class)
     *//*
    public static <T> T ofProxy(FunctionDescriptor nativeSignature,
                                Class<T> functionalInterface) {
        MapperUtil.requireFunctionalInterface(functionalInterface);
        Objects.requireNonNull(nativeSignature);
        return InternalInvocationMappers.ofProxy(nativeSignature, functionalInterface);
    }*/

//    /**
//     * {@return an implementation of the provided {@code compositeInterface} that
//     *          delegates to the provided {@code implementations} of functional
//     *          interfaces}
//     *
//     * @param compositeInterface for which an implementation is to be returned
//     * @param implementations    an array of implementations to delegate
//     * @param <R>                the type of the composite interface
//     * @throws IllegalArgumentException if the provided {@code compositeInterface}
//     *         has one or more abstract methods that is not declared by extending
//     *         a functional interface
//     * @throws IllegalArgumentException if any of the provided {@code implementations}
//     *         does not implement a functional interface
//     * @throws IllegalArgumentException if any of the provided {@code implementations}
//     *         does not implement a sub-interface of R.
//     * @throws IllegalArgumentException if any combination of two of the provided
//     *         {@code implementations} implements the same functional interface
//     */
//    public static <R> R ofComposed(Class<R> compositeInterface,
//                                   Object... implementations) {
//        MapperUtil.requireInterface(compositeInterface);
//        Arrays.stream(implementations)
//                .forEach(Objects::requireNonNull);
//        return InternalInvocationMappers.ofComposed(compositeInterface, implementations);
//    }

}
