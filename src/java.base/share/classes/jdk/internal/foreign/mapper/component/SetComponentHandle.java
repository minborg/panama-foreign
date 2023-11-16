package jdk.internal.foreign.mapper.component;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;

import static jdk.internal.foreign.mapper.component.Util.SET_TYPE;

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

        // (MemorySegment, long, x) -> (MemorySegment, long, x)
        mh = Util.transposeOffset(mh, byteOffset);

        // (Object)x
        MethodHandle extractor = lookup.unreflect(component.getAccessor());
        // (MemorySegment, long, x) -> (MemorySegment, long, Object)
        mh = MethodHandles.filterArguments(mh, 2, extractor);
        return mh;
    }

    @Override
    public MethodHandle handle(GroupLayout gl,
                               RecordComponent component,
                               long byteOffset) throws IllegalAccessException {
        // (T)x
        MethodHandle extractor = lookup.unreflect(component.getAccessor());

        // (T)Object
        extractor = extractor.asType(extractor.type().changeReturnType(Object.class));

        // (MemorySegment, long, T)
        MethodHandle mh = recordMapper(component.getType(), gl, byteOffset)
                .setHandle();

        // (MemorySegment, long, T) -> (MemorySegment, long, x)
        return MethodHandles.filterArguments(mh, 2, extractor);
    }

    @Override
    public MethodHandle handle(SequenceLayout sl,
                               RecordComponent component,
                               long byteOffset) throws NoSuchMethodException, IllegalAccessException {
        return Util.SET_NO_OP;
    }

}
