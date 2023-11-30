package java.lang.foreign.mapper;

import java.lang.foreign.MemorySegment;

/**
 * Exposes the backing memory segment and offset for segment mapped interfaces.
 * <p>
 * Interfaces types provided to factory methods of SegmentMapper that are implementing the
 * {@code SegmentBacked} interface will obtain two extra methods:
 * <ul>
 * <li>{@code segment()} that will return the backing segment for the interface.
 * <li>{@code offset()} that will return the offset in the backing segment.
 * </ul>
 * It is an error to let a record implement this interface and then provide such a record type to
 * any of the record factory methods of SegmentMapper.
 */
public interface SegmentBacked {

    /**
     * {@return the segment that backs this interface}
     */
    MemorySegment segment();

    /**
     * {@return the offset in the backing segment}
     */
    long offset();

}
