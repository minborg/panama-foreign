package jdk.internal.include.common;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

public final class CaptureCallStateUtil {

    private CaptureCallStateUtil() {}

    public static final String ERRNO = "errno";

    public static Capture createCapture(String targetName,
                                        FunctionDescriptor functionDescriptor,
                                        String capturedState) {
        var targetAddress = RuntimeHelper.requireNonNull(
                RuntimeHelper.symbolLookup().find(targetName).orElse(null),
                targetName);

        var ccs = Linker.Option.captureCallState(capturedState);
        var handle = Linker.nativeLinker().downcallHandle(targetAddress, functionDescriptor, ccs);
        var captureHandle = ccs.layout().varHandle(MemoryLayout.PathElement.groupElement(capturedState));
        return new Capture(handle, ccs.layout().byteSize(), captureHandle);
    }

    public record Capture(MethodHandle methodHandle,
                          long ccsByteSize,
                          VarHandle captureHandle) {}

}
