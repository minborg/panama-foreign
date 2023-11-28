package java.lang.foreign.mapper;

import java.lang.foreign.MemorySegment;

/**
 * Exposes the backing memory segment for segment mapped interfaces.
 * <p>
 * Interfaces types provided to factory methods of SegmentMapper that are implementing the
 * {@code SegmentBacked} interface will obtain an extra method {@code segment()} that will return
 * the backing segment for the interface (either internal or external).
 * <p>
 * It is an error to let a record implement this interface and then provide such a record type to
 * any of the record factory methods of SegmentMapper.
 */
public interface SegmentBacked {
    /**
     * {@return the segment that backs this interface (internal or external)}
     */
    MemorySegment segment();
}
