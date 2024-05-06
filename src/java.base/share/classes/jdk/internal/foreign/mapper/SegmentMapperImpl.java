package jdk.internal.foreign.mapper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.PrimitiveMapper;
import java.lang.foreign.mapper.RecordMapper;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static jdk.internal.foreign.mapper.component.Util.GET_TYPE;
import static jdk.internal.foreign.mapper.component.Util.SET_TYPE;

/**
 * This class models a general segment mapper.
 *
 * @param carrier the carrier type
 * @param layout  layout for which mapping is done
 * @param getter  for get operations
 * @param setter  for set operations
 * @param <T>     mapper type
 */
public record SegmentMapperImpl<T>(
        @Override Class<T> carrier,
        @Override MemoryLayout layout,
        @Override MethodHandle getter,
        @Override MethodHandle setter,
        MethodHandle objectGetter,
        MethodHandle objectSetter
) implements RecordMapper<T>, SegmentMapper<T>, PrimitiveMapper<T> {

    public SegmentMapperImpl(
            Class<T> carrier,
            MemoryLayout layout,
            MethodHandle getter,
            MethodHandle setter) {
        this(carrier, layout, getter, setter, getter.asType(GET_TYPE), setter.asType(SET_TYPE));
    }

    public SegmentMapperImpl {
        // (MemorySegment, long)T
        MapperUtil.assertParameterType(getter, 0, MemorySegment.class, "getter");
        MapperUtil.assertParameterType(getter, 1, long.class, "getter");
        //Todo: MapperUtil.assertReturnType(getter, type);

        // (MemorySegment, long, T)void
        MapperUtil.assertParameterType(setter, 0, MemorySegment.class, "setter");
        MapperUtil.assertParameterType(setter, 1, long.class, "setter");
        // Todo: MapperUtil.assertParameterType(setter, 2, type);
        MapperUtil.assertReturnType(setter, void.class, "setter");
    }

    @SuppressWarnings("unchecked")
    @Override
    public T get(MemorySegment segment, long offset) {
        try {
            return (T) objectGetter
                    .invokeExact(segment, offset);
        } catch (NullPointerException |
                 IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke getter() with " +
                    "segment=" + segment +
                    ", offset=" + offset, e);
        }
    }

    public void set(MemorySegment segment, long offset, T t) {
        try {
            objectSetter
                    .invokeExact(segment, offset, (Object) t);
        } catch (IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException |
                 UnsupportedOperationException |
                 NullPointerException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke setter() with " +
                    "segment=" + segment +
                    ", offset=" + offset +
                    ", t=" + t, e);
        }
    }

}
