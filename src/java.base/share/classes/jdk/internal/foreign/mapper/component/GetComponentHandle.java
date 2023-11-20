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

        String name = component.getName();
        var componentType = component.getType();

        ContainerType containerType = ContainerType.of(componentType);

        if (containerType == ContainerType.LIST) {
            // Todo::fix this
            return MethodHandles.empty(MethodType.methodType(componentType, MemorySegment.class, long.class));
        }

        if (!componentType.isArray()) {
            throw new IllegalArgumentException("Unable to map '" + sl +
                    "' because the component '" + componentType.getName() + " " + name + "' is not an array");
        }

        MultidimensionalSequenceLayoutInfo info = MultidimensionalSequenceLayoutInfo.of(sl, componentType);

        if (info.elementLayout() instanceof ValueLayout.OfBoolean) {
            throw new IllegalArgumentException("Arrays of booleans (" + info.elementLayout() + ") are not supported");
        }

        if (dimensionOf(componentType) != info.sequences().size()) {
            throw new IllegalArgumentException("Unable to map '" + sl + "'" +
                    " of dimension " + info.sequences().size() +
                    " because the component '" + componentType.getName() + " " + name + "'" +
                    " has a dimension of " + dimensionOf(componentType));
        }

        // Handle multi-dimensional arrays
        if (info.sequences().size() > 1) {
            var mh = LOOKUP.findStatic(Util.class, "toMultiArrayFunction",
                    MethodType.methodType(Object.class,
                            MemorySegment.class,
                            MultidimensionalSequenceLayoutInfo.class,
                            long.class,
                            Class.class,
                            Function.class));
            // (MemorySegment, MultidimensionalSequenceLayoutInfo, long offset, Class leafType, Function mapper) ->
            // (MemorySegment, long offset, Class leafType, Function mapper)
            mh = MethodHandles.insertArguments(mh, 1, info);

            // (MemorySegment, long offset, Class leafType, Function mapper) ->
            // (MemorySegment, long, Class leafType, Function mapper)
            mh = transposeOffset(mh, byteOffset);

            switch (info.elementLayout()) {
                case ValueLayout vl -> {
                    // (MemorySegment, long offset, Class leafType, Function mapper) ->
                    // (MemorySegment, long offset, Function mapper)
                    mh = MethodHandles.insertArguments(mh, 2, vl.carrier());
                    Function<MemorySegment, Object> leafArrayMapper =
                            switch (vl) {
                                case ValueLayout.OfByte ofByte    -> ms -> ms.toArray(ofByte);
                                case ValueLayout.OfBoolean ofBool -> throw new UnsupportedOperationException("boolean arrays not supported: " + ofBool);
                                case ValueLayout.OfShort ofShort  -> ms -> ms.toArray(ofShort);
                                case ValueLayout.OfChar ofChar    -> ms -> ms.toArray(ofChar);
                                case ValueLayout.OfInt ofInt      -> ms -> ms.toArray(ofInt);
                                case ValueLayout.OfLong ofLong    -> ms -> ms.toArray(ofLong);
                                case ValueLayout.OfFloat ofFloat  -> ms -> ms.toArray(ofFloat);
                                case ValueLayout.OfDouble ofDbl   -> ms -> ms.toArray(ofDbl);
                                case AddressLayout ad -> ms -> ms.elements(ad)
                                        .map(s -> s.get(ad, 0))
                                        .toArray(MemorySegment[]::new);
                            };
                    // (MemorySegment, long offset, Function mapper) ->
                    // (MemorySegment, long offset)
                    mh = MethodHandles.insertArguments(mh, 2, leafArrayMapper);
                    return castReturnType(mh, component.getType());
                }
                case GroupLayout gl -> {
                    var arrayComponentType = info.type();
                    // The "local" byteOffset for the record component mapper is zero
                    var componentMapper = recordMapper(arrayComponentType, gl, 0);
                    // Change the return type to Object so that we may use Array.set() below
                    var mapperCtor = componentMapper.getHandle()
                            .asType(GET_TYPE);

                    Function<MemorySegment, Object> leafArrayMapper = ms ->
                            toArray(ms, gl, arrayComponentType, mapperCtor);

                    // (MemorySegment, long offset, Class leafType, Function mapper) ->
                    // (MemorySegment, long offset, Function mapper)
                    mh = MethodHandles.insertArguments(mh, 2, arrayComponentType);
                    // (MemorySegment, long offset, Function mapper) ->
                    // (MemorySegment, long offset)
                    mh = MethodHandles.insertArguments(mh, 2, leafArrayMapper);
                    return castReturnType(mh, component.getType());
                }
                case SequenceLayout _ -> throw new InternalError("Should not reach here");
                case PaddingLayout  _ -> throw fail(component, sl);
            }
        }

        // Faster single-dimensional arrays
        switch (info.elementLayout()) {
            case ValueLayout vl -> {
                assertTypesMatch(component, info.type(), vl);
                var mt = MethodType.methodType(vl.carrier().arrayType(),
                        MemorySegment.class, topValueLayoutType(vl), long.class, long.class);
                var mh = findStaticToArray(mt);
                // (MemorySegment, OfX, long offset, long count) -> (MemorySegment, OfX, long offset)
                mh = MethodHandles.insertArguments(mh, 3, info.sequences().getFirst().elementCount());
                // (MemorySegment, OfX, long offset) -> (MemorySegment, long offset)
                mh = MethodHandles.insertArguments(mh, 1, vl);
                // (MemorySegment, long offset) -> (MemorySegment, long offset)
                return castReturnType(transposeOffset(mh, byteOffset), component.getType());
            }
            case GroupLayout gl -> {
                // The "local" byteOffset for the record component mapper is zero
                var componentMapper = recordMapper(info.type(), gl, 0);
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
                    mh = MethodHandles.insertArguments(mh, 3, info.sequences().getFirst().elementCount());
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
