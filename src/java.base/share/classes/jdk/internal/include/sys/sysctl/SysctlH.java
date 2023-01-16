package jdk.internal.include.sys.sysctl;

import jdk.internal.include.common.CaptureCallStateUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

import static jdk.internal.include.common.CaptureCallStateUtil.ERRNO;
import static jdk.internal.include.common.Constants$root.C_INT$LAYOUT;
import static jdk.internal.include.common.Util.shouldNotReachHere;

public class SysctlH {

    private SysctlH() {}

    /**
     * Accepts an ASCII representation of the name and internally looks up the integer name vector.
     * <p>
     * {@snippet :
     * int sysctlbyname(char*, void*, size_t*, void*, size_t);
     * }
     *
     * @param errCap  points to the errno struct.
     * @param name    an ASCII representation of the name and internally looks up the integer name vector.
     * @param oldp    The information is copied into the buffer specified by oldp.
     * @param oldlenp The size of the buffer is given by the location specified by oldlenp before the call,
     *                and that location gives the amount of data copied after a successful call and after a
     *                call that returns with the error code ENOMEM.
     *                If the amount of data available is greater than the size of the buffer supplied, the
     *                call supplies as much data as fits in the buffer provided and returns with the
     *                error code ENOMEM. If the old value is not desired, oldp and oldlenp should be set to NULL.
     * @param newp    is set to point to a buffer if a new value is to be set. If a new value is not to be set,
     *                newp should be set to NULL and newlen set to 0.
     * @param newlenp newp is set to point to a buffer of length newlenp
     * @return Upon successful completion, zero is returned, otherwise the value -1
     * is returned and the global variable errno is set to indicate the error.
     * @see <a href="https://nxmnpg.lemoda.net/3/sysctlbyname">sysctlbyname</a> for further details.
     */
    public static int sysctlbyname(MemorySegment errCap,
                                   MemorySegment name,
                                   MemorySegment oldp, MemorySegment oldlenp,
                                   MemorySegment newp, long newlenp) {
        try {
            return (int)SYSCTLBYNAME_MH.invokeExact(errCap, name, oldp, oldlenp, newp, newlenp);
        } catch (Throwable ex$) {
            throw shouldNotReachHere(ex$);
        }
    }

    // Todo: Dedup these methods
    public static int errno(MemorySegment errCap) {
        return (int) SYSCTLBYNAME_ERRNO_VH.get(errCap);
    }

    // Todo: Dedup these methods
    public static MemorySegment allocateErrCap(Arena arena) {
        return MemorySegment.allocateNative(ERRNO_CSS_BYTE_SIZE, arena.scope());
    }

    private static final MethodHandle SYSCTLBYNAME_MH;
    private static final VarHandle SYSCTLBYNAME_ERRNO_VH;
    private static final long ERRNO_CSS_BYTE_SIZE;

    static {
        var connectCapture = CaptureCallStateUtil
                .createCapture("sysctlbyname", constants$0.sysctlbyname$FUNC, ERRNO);
        SYSCTLBYNAME_MH = connectCapture.methodHandle();
        SYSCTLBYNAME_ERRNO_VH = connectCapture.captureHandle();
        ERRNO_CSS_BYTE_SIZE = connectCapture.ccsByteSize();
    }


    public static int getValAsInt(String name) {
        try (var arena = Arena.openConfined()) {
            var errCap = SysctlH.allocateErrCap(arena);
            var prop = arena.allocateUtf8String(name);
            var oldp = MemorySegment.allocateNative(C_INT$LAYOUT, arena.scope());
            var oldlenp = MemorySegment.allocateNative(C_INT$LAYOUT, arena.scope());
            oldlenp.set(C_INT$LAYOUT, C_INT$LAYOUT.byteSize(), 0);
            int ret = SysctlH.sysctlbyname(errCap, prop, oldp, oldlenp, MemorySegment.NULL, 0);
            if (ret == 0) {
                return Math.toIntExact(prop.get(C_INT$LAYOUT, 0));
            } else {
                throw new IllegalStateException("Unable to invoke sysctlbyname: " + name);
            }
        }
    }

}
