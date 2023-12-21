package jdk.internal.foreign.mapper;

import jdk.internal.ValueBased;
import jdk.internal.foreign.mapper.accessor.AccessorInfo;
import jdk.internal.foreign.mapper.accessor.ArrayInfo;
import jdk.internal.foreign.mapper.component.Transpose;
import jdk.internal.foreign.mapper.component.Util;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.Arrays;

@ValueBased
final class GetMethodHandleGenerator {

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();
    private static final MethodHandle TO_2D_ARRAY;

    static {
        try {
            TO_2D_ARRAY = LOCAL_LOOKUP.findStatic(GetMethodHandleGenerator.class,
                    "to2DArray",
                    MethodType.methodType(Object.class, MemorySegment.class, long.class,
                            Class.class, int[].class, ValueLayout.class, MemoryLayout.class));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final MethodHandles.Lookup lookup;
    private final MapperCache mapperCache;

    private static final MethodHandle AS_SLICE_OFFSET_LAYOUT;

    static {
        try {
            AS_SLICE_OFFSET_LAYOUT = MethodHandles.publicLookup()
                    .findVirtual(MemorySegment.class,
                            "asSlice",
                            MethodType.methodType(MemorySegment.class, long.class, MemoryLayout.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private GetMethodHandleGenerator(MethodHandles.Lookup lookup,
                                     MapperCache mapperCache) {
        this.lookup = lookup;
        this.mapperCache = mapperCache;
    }

    MethodHandle ofScalarValue(AccessorInfo accessorInfo) throws ReflectiveOperationException {
        Class<?> interfaceType = accessorInfo.layoutInfo().scalarInfo().orElseThrow().interfaceType();
        MethodType methodType = MethodType.methodType(accessorInfo.type(), interfaceType, long.class);
        // (MemorySegment, ValueLayout.ofx, long)x
        var mh = MethodHandles.publicLookup().findVirtual(MemorySegment.class, "get", methodType);
        // (MemorySegment, long)x
        return MethodHandles.insertArguments(mh, 1, accessorInfo.layoutInfo().layout());
    }

    public MethodHandle ofScalarRecord(AccessorInfo accessorInfo) {
        return mapperCache.recordGetMethodHandleFor(accessorInfo);
    }

    public MethodHandle ofArrayValue(AccessorInfo accessorInfo) throws ReflectiveOperationException {
        ArrayInfo arrayInfo = accessorInfo.layoutInfo().arrayInfo().orElseThrow();
        Class<?> interfaceType = accessorInfo.layoutInfo().scalarInfo().orElseThrow().interfaceType();

        MemoryLayout elementLayout = arrayInfo.elementLayout();
        MemoryLayout sequenceLayout = accessorInfo.layoutInfo().layout();
        if (arrayInfo.dimensions().size() == 1) {
            return ofDimension1ArrayValue(accessorInfo.type(), interfaceType, elementLayout, sequenceLayout,0);
        }
        Class<?> baseComponentType = Util.baseComponentType(accessorInfo.type());
        int[] dimensions = arrayInfo.dimensions().stream().mapToInt(Long::intValue).toArray();
        if (dimensions.length == 2) {
            // (...)Object -> (MemorySegment, long)Object
            MethodHandle mh = MethodHandles.insertArguments(TO_2D_ARRAY,2, baseComponentType, dimensions, elementLayout, sequenceLayout);
            // (MemorySegment, long)Object -> (MemorySegment, long)x[]
            mh = mh.asType(mh.type().changeReturnType(accessorInfo.type()));
            return mh;
        }
        throw new IllegalArgumentException("No support of arrays of dimension > 2");
    }

    // Reflective use
    private static Object to2DArray(MemorySegment segment, long offset,
                                    Class<?> baseComponentType,
                                    int[] dimensions,
                                    ValueLayout elementLayout,
                                    MemoryLayout sequenceLayout) {

        System.out.println("segment = " + segment);
        System.out.println("offset = " + offset);
        System.out.println("Arrays.toString(dimensions) = " + Arrays.toString(dimensions));

        Object result = Array.newInstance(baseComponentType, dimensions);
        long sliceSize = dimensions[1] * elementLayout.byteSize();

        System.out.println("sliceSize = " + sliceSize);

        for (int i = 0; i < dimensions[0]; i++) {
            var slice = segment.asSlice(offset + sliceSize * i, sliceSize);
            switch (elementLayout) {
                case ValueLayout.OfByte   by -> Array.set(result, i, slice.toArray(by));
                case ValueLayout.OfChar   ch -> Array.set(result, i, slice.toArray(ch));
                case ValueLayout.OfShort  sh -> Array.set(result, i, slice.toArray(sh));
                case ValueLayout.OfInt    in -> Array.set(result, i, slice.toArray(in));
                case ValueLayout.OfFloat  fl -> Array.set(result, i, slice.toArray(fl));
                case ValueLayout.OfLong   lo -> Array.set(result, i, slice.toArray(lo));
                case ValueLayout.OfDouble db -> Array.set(result, i, slice.toArray(db));
                default -> throw new IllegalArgumentException(
                        "Unable to map " + baseComponentType + Arrays.toString(dimensions) +
                                " to " + sequenceLayout);
            }
        }
        return result;
    }

/*    private Object toArray(AccessorInfo accessorInfo, ArrayInfo arrayInfo) {
        Object result = Array.newInstance(accessorInfo.type());
        int[] dimensions =  arrayInfo.dimensions().stream().mapToInt(Long::intValue).toArray();
        int[] counters = new int[dimensions.length];
        long offset = 0;
        int lastDimensionIndex = counters.length -1;
        int dimensionIndex = lastDimensionIndex;
        int index = 0;
        while (!Arrays.equals(dimensions, counters)) {
            Array.set
        }
        return result;
    }*/


    private MethodHandle ofDimension1ArrayValue(Class<?> type,
                                                Class<?> interfaceType,
                                                MemoryLayout elementLayout,
                                                MemoryLayout sequenceLayout,
                                                long offset) throws ReflectiveOperationException {
        MethodType methodType = MethodType.methodType(type, interfaceType);
        // (MemorySegment, ValueLayout.ofx)x[]
        var mh = MethodHandles.publicLookup().findVirtual(MemorySegment.class, "toArray", methodType);
        // (MemorySegment, ValueLayout.ofx)x[] -> (MemorySegment)x[]
        mh = MethodHandles.insertArguments(mh, 1, elementLayout);
        // (MemorySegment)x[] -> (MemorySegment, long, MemoryLayout)x[]
        mh = MethodHandles.collectArguments(mh, 0, AS_SLICE_OFFSET_LAYOUT);
        // (MemorySegment, long, MemoryLayout)x[] -> (MemorySegment, long)x[]
        mh = MethodHandles.insertArguments(mh, 2, sequenceLayout);
        return Transpose.transposeOffset(mh, offset);
    }


    static GetMethodHandleGenerator create(MethodHandles.Lookup lookup,
                                           MapperCache mapperCache) {
        return new GetMethodHandleGenerator(lookup, mapperCache);
    }
}
