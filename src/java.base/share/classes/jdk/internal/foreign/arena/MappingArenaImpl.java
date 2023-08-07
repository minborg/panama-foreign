package jdk.internal.foreign.arena;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardOpenOption.WRITE;

public final class MappingArenaImpl
    extends AbstractDelegatingArena
    implements Arena {

    private final FileChannel fc;
    private final AtomicLong offset = new AtomicLong();

    public MappingArenaImpl(Arena delegate, Path path, Set<OpenOption> options) {
        super(delegate);
        try {
           fc = FileChannel.open(path, options);
        } catch (IOException ioe) {
            // Todo: Fix this
            throw new IllegalArgumentException(ioe);
        }
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        try {
            // Todo: Fix alignment
            return fc.map(READ_WRITE, offset.getAndAdd(byteSize), byteSize, delegate);
        } catch (IOException ioe) {
            // Todo: Fix this
            throw new IllegalStateException(ioe);
        }
    }

    @Override
    public void close() {
        super.close();
        try {
            fc.close();
        } catch (IOException ioe) {
            // Todo: Fix this
            throw new IllegalStateException(ioe);
        }
    }
}
