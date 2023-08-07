package jdk.internal.foreign.arena;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

sealed class AbstractDelegatingArena
        implements Arena
        permits ChunkedArenaImpl, MappingArenaImpl, PooledArenaImpl, RecordingArenaImpl {

    final Arena delegate;

    AbstractDelegatingArena(Arena delegate) {
        this.delegate = delegate;
    }

    @Override
    public final MemorySegment.Scope scope() {
        return delegate.scope();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
