
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.stream.Collectors;

import static java.lang.foreign.ValueLayout.*;

public final class LinkerAdditions {

    private LinkerAdditions() {
    }

    // Code in java.lang.foreign.Linker

    // @CallerSensitive
    // @Restricted
    public static <T> T downcallVirtual(Class<T> type,
                                        FunctionDescriptor fd,
                                        Linker.Option... options) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(type.getAnnotation(FunctionalInterface.class));
        // check first parameter is MemorySegment (What if it is another MS parameter?)


        return null;
    }

    // Only for methods with primitive and Address layouts
    //
    public static <T> T downcall(Class<T> type,
                                 Linker.Option... options) {
        assertFunctionalInterface(type);
        Method abstractMethod = abstractMethod(type);

        Class<?> retType = abstractMethod.getReturnType();
        ValueLayout[] argLayouts = Arrays.stream(abstractMethod.getParameters())
                .map(Parameter::getType)
                .map(LinkerAdditions::layoutFor)
                .toArray(ValueLayout[]::new);

        FunctionDescriptor fd;
        if (void.class.equals(retType) || Void.class.equals(retType)) {
            fd = FunctionDescriptor.ofVoid(argLayouts);
        } else {
            fd = FunctionDescriptor.of(layoutFor(retType), argLayouts);
        }
        return downcall(type, fd, options);
    }


    // @CallerSensitive
    // @Restricted
    public static <T> T downcall(Class<T> type,
                                 FunctionDescriptor fd,
                                 Linker.Option... options) {
        assertFunctionalInterface(type);
        Method abstractMethod = abstractMethod(type);
        // Convert java methods like `clockNanosleep` to C methods "clock_nanosleep"
        String snakeCase = camelToSnakeCase(abstractMethod.getName());
        MemorySegment address = Linker.nativeLinker().defaultLookup()
                .find(snakeCase)
                .orElseThrow(() -> new NoSuchElementException("Unable to find method: " + snakeCase));

        return downcall(type, address, fd, options);
    }

    // @CallerSensitive
    // @Restricted
    public static <T> T downcall(Class<T> type,
                                 MemorySegment address,
                                 FunctionDescriptor fd,
                                 Linker.Option... options) {
        assertFunctionalInterface(type);

        Method abstractMethod = abstractMethod(type);
        MethodType methodType = MethodType.methodType(abstractMethod.getReturnType(), abstractMethod.getParameterTypes());
        return downcall(type, methodType, address, fd, options);
    }

    // @CallerSensitive
    // @Restricted
    public static <T> T downcall(Class<T> type,
                                 MethodType methodType,
                                 MemorySegment address,
                                 FunctionDescriptor fd,
                                 Linker.Option... options) {
        assertFunctionalInterface(type);

        Method abstractMethod = abstractMethod(type);

        if (!methodType.returnType().isAssignableFrom(abstractMethod.getReturnType())) {
            throw new IllegalArgumentException("Unable to match the return types of " + abstractMethod + " and " + methodType);
        }

        // Todo: Check parameters

        MethodHandle methodHandle = Linker.nativeLinker().downcallHandle(address, fd, options);

        Map<Method, MethodHandle> defaultMethodHandles = Arrays.stream(type.getMethods())
                .filter(Method::isDefault)
                .collect(Collectors.toMap(Function.identity(), toMethodHandleFunction()));

        @SuppressWarnings("unchecked")
        T t = (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (proxy, method1, args) -> {
            if (abstractMethod.equals(method1)) {
                // Fix so we could invokeExact
                if (args == null) {
                    return methodHandle.invoke();
                } else {
                    return methodHandle.invoke(args);
                }
            }
            if (Object.class.equals(method1.getDeclaringClass())) {
                return switch (method1.getName()) {
                    case "toString" -> proxy.getClass().getName() + '@' + Integer.toHexString(proxy.hashCode());
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals"   -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(
                            "java.lang.Object method not supported on proxy: " + method1);
                };
            }
            System.out.println("Looking for default method: " + method1);
            System.out.println("method1.getDeclaringClass() = " + method1.getDeclaringClass());

            MethodHandle mh = defaultMethodHandles.get(method1);
            if (mh == null) {
                throw new InternalError("Unable to find default method in " + defaultMethodHandles.keySet() + ": " + method1);
            }
            if (args ==  null) {
                return mh.bindTo(proxy).invoke();
            } else {
                return mh.bindTo(proxy).invokeWithArguments(args);
            }
        });

        return t;
    }



    // @CallerSensitive
    // @Restricted
/*    public static <T> T downcall(Class<T> type,
                                 RecordMapper.Lookup lookup,
                                 Linker.Option... options) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(type.getAnnotation(FunctionalInterface.class));

        // Convert from CaMeLcAsE to divided_case


        return null;
    }*/
    public static void main(String[] args) {
        MemorySegment upcall = upcall(IntBinaryOperator.class,
                LinkerAdditions::compare,
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT),
                Arena.ofShared()
        );

        MemorySegment upcall2 = upcall(BinaryOperator.class,
                (BinaryOperator<String>) LinkerAdditions::append,
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT),
                Arena.ofShared()
        );

    }

    public static int compare(int a, int b) {
        return Integer.compare(a, b);
    }

    public static String append(String a, String b) {
        return "0";
    }

    // @CallerSensitive
    // @Restricted

    public static <T> MemorySegment upcall(Class<T> type,
                                           T instance,
                                           FunctionDescriptor fd,
                                           Arena arena,
                                           Linker.Option... options) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(type.getAnnotation(FunctionalInterface.class));

        return null;
    }


    // Utility methods

    private static void assertFunctionalInterface(Class<?> type) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(type.getAnnotation(FunctionalInterface.class),
                "The provided type is not annotated with " + FunctionalInterface.class.getSimpleName() + ":" + type);
    }


    private static Function<Method, MethodHandle> toMethodHandleFunction() {
        return m -> {
            try {
                MethodHandles.Lookup mhLookup = MethodHandles.privateLookupIn(m.getDeclaringClass(), MethodHandles.lookup());
                MethodType mhType = MethodType.methodType(m.getReturnType(), m.getParameterTypes());
                return Modifier.isStatic(m.getModifiers())
                        ? mhLookup.findStatic(m.getDeclaringClass(), m.getName(), mhType)
                        : mhLookup.findSpecial(m.getDeclaringClass(), m.getName(), mhType, m.getDeclaringClass());
            } catch (IllegalAccessException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static Method abstractMethod(Class<?> type) {
        return Arrays.stream(type.getMethods())
                .filter(m -> !m.isDefault())
                .findFirst()
                // The @FunctionalInterface guarantees exactly one abstract method
                .orElseThrow(() -> new InternalError("Unable to find the single abstract method in " + type));
    }


    private static final String CAMEL_REGEXP = "([a-z])([A-Z]+)";
    private static final String CAMEL_REPLACE = "$1_$2";

    private static String camelToSnakeCase(String in) {
        return in.replaceAll(CAMEL_REGEXP, CAMEL_REPLACE)
                .toLowerCase(Locale.ROOT);
    }

    private static ValueLayout layoutFor(Class<?> type) {

        // _UNALIGNED?

        if (type == boolean.class) {
            return JAVA_BOOLEAN;
        } else if (type == byte.class) {
            return JAVA_BYTE;
        } else if (type == short.class) {
            return JAVA_SHORT;
        } else if (type == char.class) {
            return JAVA_CHAR;
        } else if (type == int.class) {
            return JAVA_INT;
        } else if (type == long.class) {
            return JAVA_LONG;
        } else if (type == float.class) {
            return JAVA_FLOAT;
        } else if (type == double.class) {
            return JAVA_DOUBLE;
        } else if (type == MemorySegment.class) {
            return ADDRESS;
        } else {
            throw new IllegalStateException("Unknown type: " + type);
        }
    }

/*
    Stream<Method> methods(Class<?> type) {
        type.getMethods()
        Method method = Arrays.stream(type.getMethods())
    }

    void types(Class<?> type, Set<Class<?>> types) {

    }*/

}
