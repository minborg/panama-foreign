package jdk.internal.foreign.arena;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

// Not thread safe
public final class SlicingArenaImpl
        extends AbstractDelegatingArena
        implements Arena.OfSlicing {

    private final MemorySegment segment;
    private SegmentAllocator allocator;

    public void acquire() {
        if (allocator != null) {
            throw new IllegalStateException("Already acquired");
        }
        allocator = SegmentAllocator.slicingAllocator(segment);
    }

    public void release() {
        if (allocator == null) {
            throw new IllegalStateException("Already released");
        }
    }

    public SlicingArenaImpl(Arena delegate, long size) {
        super(delegate);
        segment = delegate.allocate(size);
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        if (allocator == null) {
            throw new IllegalStateException("The slicing arena has not been acquired");
        }
        return allocator.allocate(byteSize, byteAlignment);
    }

}
