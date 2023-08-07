package jdk.internal.foreign.arena;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

public final class ChunkedArenaImpl
    extends AbstractDelegatingArena
    implements Arena {

    private final long chunkSize;
    private MemorySegment current;
    private long cursor;

    public ChunkedArenaImpl(Arena delegate, long chunkSize) {
        super(delegate);
        this.chunkSize = chunkSize;
        this.current = delegate.allocate(chunkSize);
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {

        if (byteSize > chunkSize) {
            // We can never allocate this from the current segment so just create a new allocation
            return delegate.allocate(byteSize, byteAlignment);
        }

        // Todo: Make this section concurrent
        synchronized (this) {
            // Todo: Fix alignment
            long remaining = current.byteSize() - cursor;
            if (byteSize > remaining) {
                // Throw away what is remaining. Todo: We should be able to store these shards for later use
                current = delegate.allocate(chunkSize);
            }
            var seg = current.asSlice(cursor, byteSize);
            cursor += seg.byteSize();
            return seg;
        }

    }

}
