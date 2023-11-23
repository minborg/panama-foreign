package jdk.internal.foreign.mapper.component;

import java.lang.foreign.AddressLayout;
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
import java.util.function.Function;

import static jdk.internal.foreign.mapper.component.Util.*;
import static jdk.internal.foreign.mapper.component.Util.GET_TYPE;

final class GetComponentHandle<T>
        extends AbstractComponentHandle<T>
        implements ComponentHandle<T> {

    GetComponentHandle(MethodHandles.Lookup lookup,
                       Class<T> type,
                       GroupLayout layout,
                       long offset,
                       int depth) {
        super(lookup, type, layout, offset, depth);
    }

    @Override
    public MethodHandle handle(ValueLayout vl,
                               RecordComponent component,
                               long byteOffset) throws NoSuchMethodException, IllegalAccessException {

        assertTypesMatch(component, component.getType(), vl);
        var mt = MethodType.methodType(vl.carrier(), topValueLayoutType(vl), long.class);
        var mh = MethodHandles.publicLookup().findVirtual(MemorySegment.class, "get", mt);
        // (MemorySegment, OfX, long)x -> (MemorySegment, long)x
        mh = MethodHandles.insertArguments(mh, 1, vl);

        return transposeOffset(mh, byteOffset);
    }

    @Override
    public MethodHandle handle(GroupLayout gl,
                               RecordComponent component,
                               long byteOffset) {
        // Todo: There has to be a more general way of detecting circularity
        if (type.equals(component.getType())) {
            throw new IllegalArgumentException(
                    "A type may not use a component of the same type: " + type + " in " + gl);
        }
        // Simply return the raw MethodHandle of the recursively computed record mapper
        return recordMapper(component.getType(), gl, byteOffset)
                .getHandle();
    }

    @Override
    public MethodHandle handle(SequenceLayout sl,
                               RecordComponent component,
                               long byteOffset) throws NoSuchMethodException, IllegalAccessException {

        assertSequenceLayoutValid(sl);

        var componentType = component.getType();
        ContainerType containerType = ContainerType.of(componentType, sl);

        Class<?> valueType = componentType.isArray()
                ? componentType.getComponentType()
                : firstGenericType(component);

        // Faster single-dimensional arrays
        switch (sl.elementLayout()) {
            case ValueLayout vl -> {
                var mt = MethodType.methodType(vl.carrier().arrayType(),
                        MemorySegment.class, topValueLayoutType(vl), long.class, long.class);
                var mh = findStaticToArray(mt);
                // (MemorySegment, OfX, long offset, long count)x[] -> (MemorySegment, OfX, long offset)x[]
                mh = MethodHandles.insertArguments(mh, 3, sl.elementCount());
                // (MemorySegment, OfX, long offset)x[] -> (MemorySegment, long offset)x[]
                mh = MethodHandles.insertArguments(mh, 1, vl);
                // (MemorySegment, long offset)x[] -> (MemorySegment, long offset)x[]

                if (containerType == ContainerType.LIST) {
                    // (OfX, [x])List<X>
                    MethodHandle finisher = Util.findStaticArrayToList(MethodType.methodType(List.class, topValueLayoutType(vl), vl.carrier().arrayType()));
                    // (OfX, [x])List<X> -> ([x])List<X>
                    finisher = MethodHandles.insertArguments(finisher, 0, vl);
                    mh = MethodHandles.filterReturnValue(mh, finisher);
                }

                return castReturnType(transposeOffset(mh, byteOffset), component.getType());
            }
            case GroupLayout gl -> {
                // The "local" byteOffset for the record component mapper is zero
                var componentMapper = recordMapper(valueType, gl, 0);
                try {
                    var mt = MethodType.methodType(Object.class.arrayType(),
                            MemorySegment.class, GroupLayout.class, long.class, long.class, Class.class, MethodHandle.class);
                    var mh = findStaticToArray(mt);
                    var mapper = componentMapper.getHandle().asType(GET_TYPE);
                    // (MemorySegment, GroupLayout, long offset, long count, Class, MethodHandle) ->
                    // (MemorySegment, GroupLayout, long offset, long count, Class)
                    mh = MethodHandles.insertArguments(mh, 5, mapper);
                    // (MemorySegment, GroupLayout, long offset, long count, Class) ->
                    // (MemorySegment, GroupLayout, long offset, long count)
                    mh = MethodHandles.insertArguments(mh, 4, componentMapper.type());
                    // (MemorySegment, GroupLayout, long offset, long count) ->
                    // (MemorySegment, GroupLayout, long offset)
                    mh = MethodHandles.insertArguments(mh, 3, sl.elementCount());
                    // (MemorySegment, GroupLayout, long offset) ->
                    // (MemorySegment, long offset)
                    mh = MethodHandles.insertArguments(mh, 1, gl);
                    // (MemorySegment, long offset) -> (MemorySegment, long offset)Record[]
                    mh = transposeOffset(mh, byteOffset);
                    // (MemorySegment, long offset)Record[] -> (MemorySegment, long)componentType
                    return MethodHandles.explicitCastArguments(mh, GET_TYPE.changeReturnType(component.getType()));
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            case SequenceLayout _ ->  throw new InternalError("Should not reach here");
            case PaddingLayout  _ -> throw fail(component, sl);
        }
    }

}
