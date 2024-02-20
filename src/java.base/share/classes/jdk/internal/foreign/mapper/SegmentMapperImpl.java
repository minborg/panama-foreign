package jdk.internal.foreign.mapper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;

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
        // (MemorySegment, long)T
        MapperUtil.assertParameterType(getter, 0, MemorySegment.class);
        MapperUtil.assertParameterType(getter, 1, long.class);
        //Todo: MapperUtil.assertReturnType(getter, type);
        MapperUtil.assertReturnType(getter, Object.class);

        // (MemorySegment, long, T)void
        MapperUtil.assertParameterType(setter, 0, MemorySegment.class);
        MapperUtil.assertParameterType(setter, 1, long.class);
        // Todo: MapperUtil.assertParameterType(setter, 2, type);
        MapperUtil.assertParameterType(setter, 2, Object.class);
        MapperUtil.assertReturnType(setter, void.class);
    }

}
