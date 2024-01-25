package jdk.internal.foreign.mapper;

import jdk.internal.misc.Unsafe;

import java.lang.foreign.mapper.SegmentMapper;

public final class StandardSharable implements SegmentMapper.Sharable {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final SegmentMapper.Sharable INSTANCE = new StandardSharable();

    private StandardSharable() {}

    public void acquire() {
        UNSAFE.loadFence();
    }

    public void release() {
        UNSAFE.storeFence();
    }

    public static SegmentMapper.Sharable instance() {
        return INSTANCE;
    }

}
