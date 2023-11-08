package jdk.internal.foreign.mapper;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.function.Function;

public class SegmentMapperImpl<T> implements SegmentMapper<T> {

    private final MethodHandles.Lookup lookup;
    private final Class<T> type;
    private final GroupLayout layout;
    private final boolean exhaustive;

    private final MethodHandle getHandle;
    private final MethodHandle setHandle;

    public SegmentMapperImpl(MethodHandles.Lookup lookup,
                             Class<T> type,
                             GroupLayout layout) {
        this.lookup = lookup;
        this.type = type;
        this.layout = layout;
        this.getHandle = null;  // Todo: Fix me
        this.setHandle = null;  // Todo: Fix me
        this.exhaustive = true; // Todo: Fix me
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public GroupLayout layout() {
        return layout;
    }

    @Override
    public MethodHandle getHandle() {
        return getHandle;
    }

    @Override
    public MethodHandle setHandle() {
        return setHandle;
    }

    @Override
    public boolean isExhaustive() {
        return exhaustive;
    }

    @Override
    public <R> SegmentMapper<R> map(Function<? super T, ? extends R> toMapper, Function<? super R, ? extends T> fromMapper) {
        throw newUoe();
    }

    @Override
    public <R> SegmentMapper<R> map(Function<? super T, ? extends R> toMapper) {
        throw newUoe();
    }

    private static UnsupportedOperationException newUoe() {
        throw new UnsupportedOperationException("Todo");
    }

}
