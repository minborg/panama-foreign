package jdk.internal.foreign.arena;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

public final class OfRecordingImpl
    extends AbstractDelegatingArena
    implements Arena.OfRecording {

    private final Queue<Event> queue = new ConcurrentLinkedQueue<>();

    public OfRecordingImpl(Arena delegate) {
        super(delegate);
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        long beginNs = System.nanoTime();
        var seg = delegate.allocate(byteSize, byteAlignment);
        long durationNs = System.nanoTime() - beginNs;
        queue.add(new Event(beginNs, durationNs, byteSize, byteAlignment));
        return seg;
    }

    @Override
    public Stream<Event> events() {
        return queue.stream();
    }
}
