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
                               RecordComponent component,
                               long byteOffset) throws NoSuchMethodException, IllegalAccessException {

        if (sl.elementLayout() instanceof SequenceLayout) {
            // We only support single dimension arrays
            return Util.SET_NO_OP;
        }

        if (sl.elementCount() > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("Unable to map'" + sl +
                    "' because the element count is too big " + sl.elementCount());
        }

        if (sl.elementLayout() instanceof ValueLayout.OfBoolean) {
            throw new IllegalArgumentException("Arrays of booleans (" + sl.elementLayout() + ") are not supported");
        }

        var componentType = component.getType();
        ContainerType containerType = ContainerType.of(componentType);
        if (containerType == ContainerType.UNKNOWN) {
            throw new IllegalArgumentException("Unable to map '" + sl +
                    "' because the component '" + componentType.getName() + " " + component.getName() + "' is not an array or a List.");
        }

        Class<?> valueType = componentType.isArray()
                ? componentType.getComponentType()
                : firstGenericType(component);

        // (T)[x] or (T)List<x>
        MethodHandle extractor = lookup.unreflect(component.getAccessor());

        // Only single-dimensional arrays/Lists are supported
        switch (sl.elementLayout()) {
            case ValueLayout vl -> {
                // assertTypesMatch(component, valueType, vl);

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
                return Util.transposeOffset(mh, byteOffset);
            }
            case GroupLayout gl -> {
                // Todo: Fix me!
                return SET_NO_OP;
                /*
                // The "local" byteOffset for the record component mapper is zero
                var componentMapper = recordMapper(info.type(), gl, 0);
                try {
                    var mt = MethodType.methodType(Object.class.arrayType(),
                            MemorySegment.class, GroupLayout.class, long.class, long.class, Class.class, MethodHandle.class);
                    var mh = Util.findStaticToArray(mt);
                    var mapper = componentMapper.getHandle().asType(GET_TYPE);
                    // (MemorySegment, GroupLayout, long offset, long count, Class, MethodHandle) ->
                    // (MemorySegment, GroupLayout, long offset, long count, Class)
                    mh = MethodHandles.insertArguments(mh, 5, mapper);
                    // (MemorySegment, GroupLayout, long offset, long count, Class) ->
                    // (MemorySegment, GroupLayout, long offset, long count)
                    mh = MethodHandles.insertArguments(mh, 4, componentMapper.type());
                    // (MemorySegment, GroupLayout, long offset, long count) ->
                    // (MemorySegment, GroupLayout, long offset)
                    mh = MethodHandles.insertArguments(mh, 3, info.sequences().getFirst().elementCount());
                    // (MemorySegment, GroupLayout, long offset) ->
                    // (MemorySegment, long offset)
                    mh = MethodHandles.insertArguments(mh, 1, gl);
                    // (MemorySegment, long offset) -> (MemorySegment, long offset)Record[]
                    mh = Util.transposeOffset(mh, byteOffset);
                    // (MemorySegment, long offset)Record[] -> (MemorySegment, long)componentType
                    return MethodHandles.explicitCastArguments(mh, GET_TYPE.changeReturnType(component.getType()));
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }*/
            }
            case SequenceLayout _ ->  throw new InternalError("Should not reach here");
            case PaddingLayout _ -> throw fail(component, sl);
        }

    }

}
