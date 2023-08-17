package jdk.internal.foreign.arena;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardOpenOption.WRITE;

public final class OfResizingImpl
    extends AbstractDelegatingArena
    implements Arena.OfResizing {

    private static final Set<OpenOption> OPTIONS = Set.of(CREATE, SPARSE, READ, WRITE);

    // Todo: Use anonymous files
    private final AtomicLong cnt = new AtomicLong();
    private final Path path;

    private final ConcurrentMap<Long, FileChannel> fileChannels;

    public OfResizingImpl(Arena delegate, Path path) {
        super(delegate);
        this.path = path;
        this.fileChannels = new ConcurrentHashMap<>();
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        // Todo: Fix alignment (Mapped files are super-aligned)
        final FileChannel fc;
        final MemorySegment segment;
        try {
            fc = FileChannel.open(path.resolve("segment-"+ cnt.getAndIncrement()), OPTIONS);
            segment = fc.map(READ_WRITE, 0L, byteSize, delegate);
        } catch (IOException ioe) {
            // Todo: Fix this
            throw new IllegalArgumentException(ioe);
        }
        fileChannels.put(segment.address(), fc);
        return segment;
    }

    @Override
    public MemorySegment mirror(MemorySegment originalSegment, long newByteSize) {
        var fc = fileChannels.get(originalSegment.address());
        if (fc == null) {
            throw new IllegalArgumentException(
                    "Unable to expand because the segment " + originalSegment +
                            " was not previously allocated from this Arena");
        }

        try {
            return fc.map(READ_WRITE, 0L, newByteSize, delegate);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        super.close();
        fileChannels.values()
                .forEach(OfResizingImpl::closeFc);
    }

    private static void closeFc(FileChannel fc) {
        try {
            fc.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
