package jdk.internal.foreign.mapper;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class InternalInvocationMappers {

    private InternalInvocationMappers() {
    }

    public static <T> T ofProxy(Class<T> functionalInterface) {
        return ofProxy(signature(functionalInterface), functionalInterface);
    }

    public static <T> T ofProxy(FunctionDescriptor nativeSignature, Class<T> functionalInterface) {
        Method abstractMethod = abstractMethod(functionalInterface);
        MethodHandle handle = handle(nativeSignature, abstractMethod);
        MethodType methodType = methodType(abstractMethod);
        handle = adapt(handle, methodType);
        return MethodHandleProxies.asInterfaceInstance(functionalInterface, handle);
    }

    public static MethodHandle adapt(MethodHandle downcall,
                                     MethodType methodType) {
        throw new UnsupportedOperationException();
    }

    public static <R> R ofComposed(Class<R> compositeInterface,
                                   Object... implementations) {
        throw new UnsupportedOperationException();
    }


    //

    private static Method abstractMethod(Class<?> functionalInterface) {
        return Arrays.stream(functionalInterface.getMethods())
                .filter(m -> Modifier.isAbstract(m.getModifiers()))
                .findFirst()
                .orElseThrow();
    }

    private static MethodHandle handle(FunctionDescriptor nativeSignature,
                                       Method abstractMethod) {

        Linker linker = Linker.nativeLinker();
        MethodHandle handle = linker.downcallHandle(
                linker.defaultLookup().find(abstractMethod.getName()).orElseThrow(),
                nativeSignature
        );

        return handle;
    }

    private static MethodType methodType(Method method) {
        Class<?> returnType = method.getReturnType();
        Class<?>[] parameterTypes = method.getParameterTypes();
        return parameterTypes.length == 0
                ? MethodType.methodType(returnType)
                : MethodType.methodType(returnType, parameterTypes);
    }


    private static FunctionDescriptor signature(Class<?> inter) {
        throw new UnsupportedOperationException();
    }

}
