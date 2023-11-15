package jdk.internal.foreign.mapper.component;

import jdk.internal.foreign.mapper.MapperUtil;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;

import static jdk.internal.foreign.mapper.MapperUtil.SET_TYPE;

final class SetComponentHandle<T>
        extends AbstractComponentHandle<T>
        implements ComponentHandle<T> {

    SetComponentHandle(MethodHandles.Lookup lookup,
                       Class<T> type,
                       GroupLayout layout,
                       long offset,
                       int depth) {
        super(lookup, type, layout, offset, depth);
    }

    @Override
    public MethodHandle handle(RecordComponent component) {
        return super.handle(component)
                .asType(SET_TYPE);
    }

    @Override
    public MethodHandle handle(ValueLayout vl,
                               RecordComponent component,
                               long byteOffset) throws NoSuchMethodException, IllegalAccessException {

        assertTypesMatch(component, component.getType(), vl);

        var mt = MethodType.methodType(void.class, topValueLayoutType(vl), long.class, vl.carrier());
        var mh = MethodHandles.publicLookup().findVirtual(MemorySegment.class, "set", mt);
        // (MemorySegment, OfX, long, x)void -> (MemorySegment, long, x)void
        mh = MethodHandles.insertArguments(mh, 1, vl);

        // (long, long)long -> (long)long
        MethodHandle sum = MethodHandles.insertArguments(MapperUtil.SUM_LONG, 1, byteOffset);

        // (MemorySegment, long, x) -> (MemorySegment, long, x)
        mh = MethodHandles.filterArguments(mh, 1, sum);

        // (Object)x
        MethodHandle extractor = lookup.unreflect(component.getAccessor());
        // (MemorySegment, long, x) -> (MemorySegment, long, Object)
        mh = MethodHandles.filterArguments(mh, 2, extractor);
        return mh;
    }

    @Override
    public MethodHandle handle(GroupLayout vl,
                               RecordComponent component,
                               long byteOffset) {
        return MapperUtil.SET_NO_OP;
    }

    @Override
    public MethodHandle handle(SequenceLayout vl,
                               RecordComponent component,
                               long byteOffset) throws NoSuchMethodException, IllegalAccessException {
        return MapperUtil.SET_NO_OP;
    }

}
