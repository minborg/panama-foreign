package jdk.internal.foreign.mapper.component;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;
import java.util.List;

import static jdk.internal.foreign.mapper.component.Util.*;

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
                               RecordComponent recordComponent,
                               long byteOffset) throws NoSuchMethodException, IllegalAccessException {

        assertSequenceLayoutValid(sl);

        var recordComponentType = recordComponent.getType();
        ContainerType containerType = ContainerType.of(recordComponentType, sl);

        // (T)[x] or (T)List<x>
        MethodHandle extractor = lookup.unreflect(recordComponent.getAccessor());

        // Only single-dimensional arrays/Lists are supported
        return switch (sl.elementLayout()) {
            case ValueLayout vl -> {
                // assertTypesMatch(recordComponent, valueType, vl);

                var mt = MethodType.methodType(void.class,
                        Object.class, int.class,
                        MemorySegment.class, ValueLayout.class, long.class,
                        int.class);
                // (Object, int, MemorySegment, ValueLayout, long, int)void
                var mh = MethodHandles.publicLookup()
                        .findStatic(MemorySegment.class, "copy", mt);
                // -> (Object, int, MemorySegment, ValueLayout, long)void
                mh = MethodHandles.insertArguments(mh, 5, (int) sl.elementCount());
                // -> (Object, MemorySegment, ValueLayout, long)void
                mh = MethodHandles.insertArguments(mh, 1, 0);
                // -> (Object, MemorySegment, long)void
                mh = MethodHandles.insertArguments(mh, 2, vl);
                // -> (Object, MemorySegment, long)void
                var newMt = MethodType.methodType(void.class, MemorySegment.class, long.class, Object.class);
                // -> (MemorySegment, long, Object)void
                mh = MethodHandles.permuteArguments(mh, newMt, 2, 0, 1);

                if (containerType == ContainerType.LIST) {
                    var listToArrayType = MethodType.methodType(vl.carrier().arrayType(), topValueLayoutType(vl), List.class);
                    // (ofX, List)x[]
                    MethodHandle listToArray = Util.findStaticListToArray(listToArrayType);
                    // (List)x[] (the ofX parameter is only used to resolve the correct underlying method)
                    listToArray = MethodHandles.insertArguments(listToArray, 0, vl);
                    // (T)List<x> -> (T)[x]
                    extractor = MethodHandles.filterReturnValue(extractor, listToArray);
                    // The extractor is now of type (T)[x] regardless if we are looking at
                    // an array or a List.
                }

                // -> (MemorySegment, long, T)void
                mh = MethodHandles.filterArguments(mh, 2, extractor.asType(MethodType.methodType(Object.class, extractor.type().parameterType(0))));
                // -> (MemorySegment, long, T)void
                yield Util.transposeOffset(mh, byteOffset);
            }
            case GroupLayout gl when containerType == ContainerType.ARRAY -> {

                Class<?> valueType = recordComponentType.getComponentType();

                // The "local" byteOffset for the record recordComponent mapper is zero
                var componentMapper = recordMapper(valueType, gl, 0);
                try {
                    var mt = MethodType.methodType(void.class,
                            MemorySegment.class, GroupLayout.class, long.class, MethodHandle.class, Object[].class);
                    var mh = Util.findStaticFromArray(mt);
                    var mapper = componentMapper.setHandle().asType(SET_TYPE);
                    // (MemorySegment, GroupLayout, long offset, MethodHandle, Object[])void ->
                    // (MemorySegment, GroupLayout, long offset, Object[])void
                    mh = MethodHandles.insertArguments(mh, 3, mapper);
                    // (MemorySegment, GroupLayout, long offset, Object[])void ->
                    // (MemorySegment, long offset, Object[])void
                    mh = MethodHandles.insertArguments(mh, 1, gl);

                    if (containerType == ContainerType.LIST) {

                    }

                    // (MemorySegment, long offset, Object[])void ->
                    // (MemorySegment, long offset, T)void
                    mh = MethodHandles.filterArguments(mh, 2, extractor.asType(MethodType.methodType(Object[].class, extractor.type().parameterType(0))));

                    // (MemorySegment, long offset, Object[])void -> (MemorySegment, long offset, Object[])void
                    mh = Util.transposeOffset(mh, byteOffset);
                    // (MemorySegment, long offset, Object[])void -> (MemorySegment, long offset, Object)void
                    yield MethodHandles.explicitCastArguments(mh, SET_TYPE);
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            case GroupLayout gl -> {

                Class<?> valueType = firstGenericType(recordComponent);

                // The "local" byteOffset for the record recordComponent mapper is zero
                var componentMapper = recordMapper(valueType, gl, 0);
                try {
                    var mt = MethodType.methodType(void.class,
                            MemorySegment.class, GroupLayout.class, long.class, MethodHandle.class, List.class);
                    var mh = Util.findStaticFromList(mt);
                    var mapper = componentMapper.setHandle().asType(SET_TYPE);
                    // (MemorySegment, GroupLayout, long offset, MethodHandle, List)void ->
                    // (MemorySegment, GroupLayout, long offset, List)void
                    mh = MethodHandles.insertArguments(mh, 3, mapper);
                    // (MemorySegment, GroupLayout, long offset, List)void ->
                    // (MemorySegment, long offset, List)void
                    mh = MethodHandles.insertArguments(mh, 1, gl);
                    // (MemorySegment, long offset, List)void ->
                    // (MemorySegment, long offset, T)void
                    mh = MethodHandles.filterArguments(mh, 2, extractor.asType(MethodType.methodType(List.class, extractor.type().parameterType(0))));
                    // (MemorySegment, long offset, T)void -> (MemorySegment, long offset, T)void
                    mh = Util.transposeOffset(mh, byteOffset);
                    // (MemorySegment, long offset, T)void -> (MemorySegment, long offset, Object)void
                    yield MethodHandles.explicitCastArguments(mh, SET_TYPE);
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            case SequenceLayout _ ->  throw new InternalError("Should not reach here");
            case PaddingLayout _ -> throw fail(recordComponent, sl);
        };

    }

}
