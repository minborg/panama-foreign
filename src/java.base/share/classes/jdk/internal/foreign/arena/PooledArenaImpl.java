package jdk.internal.foreign.arena;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

public final class PooledArenaImpl
    extends AbstractDelegatingArena
    implements Arena.PooledArena {

    private final List<ConcurrentLinkedQueue<MemorySegment>> queues;

    public PooledArenaImpl(Arena delegate) {
        super(delegate);
        queues = IntStream.range(0, 63 - 3)
                .mapToObj(__ -> new ConcurrentLinkedQueue<MemorySegment>())
                .toList();
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        // Todo: Fix alignment
        var seg = queues.get(bucket(byteSize)).poll();
        if (seg == null) {
            seg = delegate.allocate(byteSize, byteAlignment);
        }
        return seg;
    }

    @Override
    public void recycle(MemorySegment segment) {
        // Todo: Check that this is a segment that was actually obtained from this arena
        // or maybe allow new segments being injected. However, overlapping segments must not be returned.
        queues.get(bucket(segment.byteSize())).add(segment);
    }

    // Computes the smallest power of two that is larger than the given byteSize
    // Maybe have another more granular bucket strategy
    private static int bucket(long byteSize) {
        return -1 >>> Long.numberOfLeadingZeros(byteSize - 1);
    }

}
