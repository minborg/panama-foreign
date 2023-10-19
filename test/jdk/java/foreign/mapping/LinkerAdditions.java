
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.IntBinaryOperator;
import java.util.function.UnaryOperator;

import static java.lang.foreign.ValueLayout.JAVA_INT;

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

        return null;
    }

    // @CallerSensitive
    // @Restricted
    public static <T> T downcall(Class<T> type,
                                 FunctionDescriptor fd,
                                 Linker.Option... options) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(type.getAnnotation(FunctionalInterface.class));

        // Convert from CaMeLcAsE to divided_case
        // Derive the lookup via name magic

        return null;
    }

    // @CallerSensitive
    // @Restricted
    public static <T> T downcall(Class<T> type,
                                 MemorySegment lookup,
                                 FunctionDescriptor fd,
                                 Linker.Option... options) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(type.getAnnotation(FunctionalInterface.class));

        // Derive the MethodType

        return null;
    }

    // @CallerSensitive
    // @Restricted
    public static <T> T downcall(Class<T> type,
                                 MethodType methodType,
                                 MemorySegment lookup,
                                 FunctionDescriptor fd,
                                 Linker.Option... options) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(type.getAnnotation(FunctionalInterface.class));

        return null;
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

}
