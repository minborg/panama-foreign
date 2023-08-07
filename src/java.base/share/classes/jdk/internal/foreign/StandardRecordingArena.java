package jdk.internal.foreign;

import java.lang.foreign.Arena;
import java.util.LongSummaryStatistics;
import java.util.stream.Stream;

public final class StandardRecordingArena
    extends AbstractDelegatingArena
    implements Arena.RecordingArena {

    public StandardRecordingArena(Arena delegate) {
        super(delegate);
    }

    @Override
    public LongSummaryStatistics sizeStatistics() {
        return null;
    }

    @Override
    public LongSummaryStatistics alignmentStatistics() {
        return null;
    }

    @Override
    public Stream<Event> events() {
        return null;
    }
}
