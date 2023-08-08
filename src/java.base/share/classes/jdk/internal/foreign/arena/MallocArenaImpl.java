package jdk.internal.foreign.arena;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public final class MallocArenaImpl
    extends AbstractDelegatingArena
    implements Arena {

    private static final MethodHandle MALLOC;
    private static final MethodHandle FREE;

    static {
        Linker linker = Linker.nativeLinker();
        MALLOC = linker.downcallHandle(linker.defaultLookup().find("malloc").orElseThrow(), FunctionDescriptor.of(ADDRESS, JAVA_LONG));
        FREE = linker.downcallHandle(linker.defaultLookup().find("free").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS));
    }

    public MallocArenaImpl(Arena delegate) {
        super(delegate);
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        try {
            MemorySegment raw = (MemorySegment) MALLOC.invokeExact(byteSize + byteAlignment);
            return raw.reinterpret(byteSize, delegate, MallocArenaImpl::free);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void free(MemorySegment segment) {
        try {
            FREE.invokeExact(segment);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

}
