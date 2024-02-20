package jdk.internal.foreign.mapper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;

/**
 * This class models a general segment mappers.
 *
 * @param type       new type to map to/from
 * @param layout     original layout
 * @param getter     for get operations
 * @param setter     for set operations
 * @param <T>        mapper type
 */
public record SegmentMapperImpl<T>(
        @Override Class<T> type,
        @Override MemoryLayout layout,
        @Override MethodHandle getter,
        @Override MethodHandle setter
) implements SegmentMapper<T> {


    public SegmentMapperImpl {
        // Todo: Check MH invariants
    }

}
