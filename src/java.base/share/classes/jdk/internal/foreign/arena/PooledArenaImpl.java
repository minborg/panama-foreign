package jdk.internal.foreign.arena;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;

public final class PooledArenaImpl
    extends AbstractDelegatingArena
    implements Arena.PooledArena {

    private final List<ConcurrentLinkedQueue<MemorySegment>> queues;
    private final ConcurrentMap<Long, MemorySegment> outstandingSegments;

    public PooledArenaImpl(Arena delegate) {
        super(delegate);
        queues = IntStream.range(0, 63 - 3)
                .mapToObj(__ -> new ConcurrentLinkedQueue<MemorySegment>())
                .toList();
        outstandingSegments = new ConcurrentHashMap<>();
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        // Todo: Fix alignment
        int bucket = bucket(byteSize);
        var original = queues.get(bucket).poll();
        if (original == null) {
            original = delegate.allocate(sizeFor(bucket), byteAlignment);
        }
        var slice = original.asSlice(0L, byteSize);
        outstandingSegments.merge(slice.address(), original, PooledArenaImpl::throwingMerger);
        return slice;
    }

    @Override
    public void recycle(MemorySegment segment) {
        // Todo: Check for duplicates and overlaps
        var original = outstandingSegments.remove(segment.address());
        if (original == null) {
            throw new IllegalArgumentException(
                    "Unable to recycle because the segment " + segment +
                            " was not previously allocated from this Arena or has already been recycled");
        }
        int bucket = bucket(original.byteSize());
        queues.get(bucket).add(original);
    }

    private static MemorySegment throwingMerger(MemorySegment a, MemorySegment b) {
        throw new IllegalStateException();
    }

    // Computes the smallest power of two that is larger than the given byteSize
    private static int bucket(long byteSize) {
        return byteSize == 0
                ? 0
                : 65 - Long.numberOfLeadingZeros(byteSize - 1);
    }

    // The smallest value that is an even power of two but
    // equal or greater than all values in the provided bucket
    private static long sizeFor(long bucket) {
        return bucket == 0
                ? 0
                : 1L << (bucket - 1);
    }

}
