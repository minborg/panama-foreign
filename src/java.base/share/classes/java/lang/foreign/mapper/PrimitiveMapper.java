package java.lang.foreign.mapper;

import jdk.internal.foreign.mapper.PrimitiveMapperUtil;
import jdk.internal.foreign.mapper.SegmentMapperImpl;

import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * A segment mapper that can convert certain value layouts to a primitive
 * target value.
 * <p>
 * Values are converted ...
 *
 * @param <T> target value type
 * @since 23
 */
public sealed interface PrimitiveMapper<T>
        extends SegmentMapper<T>
        permits SegmentMapperImpl {

    /**
     * {@return a }
     * @param source s
     * @param target t
     * @param <T> type
     */
    static <T> PrimitiveMapper<T> of(ValueLayout source, Class<T> target) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        return PrimitiveMapperUtil.of(source, target);
    }

}
