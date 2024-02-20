package jdk.internal.foreign.mapper;

import jdk.internal.ValueBased;
import jdk.internal.foreign.mapper.accessor.ArrayInfo;
import jdk.internal.foreign.mapper.component.Util;
import jdk.internal.foreign.mapper.accessor.AccessorInfo;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;

import static jdk.internal.foreign.mapper.component.Util.REQUIRE_ARRAY_LENGTH;

@ValueBased
final class SetMethodHandleGenerator {

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();
    private static final MethodHandle FROM_2D_ARRAY;
    private static final MethodHandle FROM_RECORD_ARRAY;

    static {
        try {
            FROM_2D_ARRAY = LOCAL_LOOKUP.findStatic(SetMethodHandleGenerator.class,
                    "from2DArray",
                    MethodType.methodType(void.class, MemorySegment.class, long.class,  Object.class,
                            int[].class, ValueLayout.class));
            FROM_RECORD_ARRAY = LOCAL_LOOKUP.findStatic(SetMethodHandleGenerator.class,
                    "fromRecordArray",
                    MethodType.methodType(void.class, MemorySegment.class, long.class, Object.class,
                            Class.class, int.class, long.class, MethodHandle.class));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final MethodHandles.Lookup lookup;
    private final MapperCache mapperCache;

    private SetMethodHandleGenerator(MethodHandles.Lookup lookup,
                                     MapperCache mapperCache) {
        this.lookup = lookup;
        this.mapperCache = mapperCache;
    }

    MethodHandle ofScalarValue(AccessorInfo accessorInfo) throws ReflectiveOperationException {
        Class<?> interfaceType = accessorInfo.layoutInfo().scalarInfo().orElseThrow().interfaceType();
        MethodType methodType = MethodType.methodType(void.class, interfaceType, long.class, accessorInfo.type());
        var mh = MethodHandles.publicLookup().findVirtual(MemorySegment.class, "set", methodType);
        // (MemorySegment, OfX, long, x)void -> (MemorySegment, long, x)void
        return MethodHandles.insertArguments(mh, 1, accessorInfo.layoutInfo().layout());
    }

    MethodHandle ofScalarRecord(AccessorInfo accessorInfo) throws ReflectiveOperationException {
        return mapperCache.recordSetMethodHandleFor(accessorInfo);
    }

    MethodHandle ofArrayValue(AccessorInfo accessorInfo) throws ReflectiveOperationException {
        ArrayInfo arrayInfo = accessorInfo.layoutInfo().arrayInfo().orElseThrow();
        MemoryLayout elementLayout = arrayInfo.elementLayout();
        if (arrayInfo.dimensions().size() == 1) {
            int length = arrayInfo.dimensions().getLast().intValue();
            MethodType methodType = MethodType.methodType(void.class,
                    Object.class, int.class, MemorySegment.class, ValueLayout.class, long.class, int.class);
            // (Object arr, int , MemorySegment, ValueLayout.ofx, long, int)void
            var mh = MethodHandles.publicLookup().findStatic(MemorySegment.class, "copy", methodType);
            // -> (Object arr, MemorySegment, ValueLayout.ofx, long, int)void
            mh = MethodHandles.insertArguments(mh, 1 , 0);
            // -> (Object arr, MemorySegment, long, int)void
            mh = MethodHandles.insertArguments(mh, 2, elementLayout);
            // (Object arr, MemorySegment, long, int)void -> (Object arr, MemorySegment, long)void
            mh = MethodHandles.insertArguments(mh, 3, length);

            var newMt = MethodType.methodType(void.class, MemorySegment.class, long.class, Object.class);
            // (Object arr, MemorySegment, long)void -> (MemorySegment, long, Object)void
            mh = MethodHandles.permuteArguments(mh, newMt, 2, 0, 1);

            // Make sure the array length equals the sequence layout element count
            MethodHandle requireArrayLength = MethodHandles.insertArguments(REQUIRE_ARRAY_LENGTH, 1, length);
            mh = MethodHandles.filterArguments(mh, 2, requireArrayLength);

            // (MemorySegment, long, Object)void -> (MemorySegment, long, x[])void
            return mh.asType(mh.type().changeParameterType(2, accessorInfo.type()));
        }
        int[] dimensions = arrayInfo.dimensions().stream().mapToInt(Long::intValue).toArray();
        if (dimensions.length == 2) {
            // (...)void -> (MemorySegment, long, Object)void
            MethodHandle mh = MethodHandles.insertArguments(FROM_2D_ARRAY,3, dimensions, elementLayout);
            // (MemorySegment, long, Object)void -> (MemorySegment, long, x[])void
            mh = mh.asType(mh.type().changeParameterType(2, accessorInfo.type()));
            return mh;
        }
        throw new IllegalArgumentException("No support of arrays of dimension > 2");
    }

    MethodHandle ofArrayRecord(AccessorInfo accessorInfo) {
        ArrayInfo arrayInfo = accessorInfo.layoutInfo().arrayInfo().orElseThrow();
        MemoryLayout elementLayout = arrayInfo.elementLayout();
        Class<?> baseComponentType = Util.baseComponentType(accessorInfo.type());
        int[] dimensions = arrayInfo.dimensions().stream().mapToInt(Long::intValue).toArray();
        MethodHandle setter = mapperCache.cachedRecordMapper(baseComponentType, (GroupLayout) arrayInfo.elementLayout())
                .setter();
        setter = setter.asType(setter.type().changeParameterType(2, Object.class));
        if (dimensions.length == 1) {
            MethodHandle mh = MethodHandles.insertArguments(FROM_RECORD_ARRAY, 3, baseComponentType, dimensions[0], elementLayout.byteSize(), setter);
            mh = mh.asType(mh.type().changeParameterType(2, accessorInfo.type()));
            return mh;
        }
        throw new IllegalArgumentException("No support of arrays of dimension > 1");
    }

    // Support Methods

    // Reflective use
    private static void from2DArray(MemorySegment segment, long offset, Object array,
                                    int[] dimensions,
                                    ValueLayout elementLayout) {

        long sliceSize = dimensions[1] * elementLayout.byteSize();
        Util.requireArrayLength(array, dimensions[0]);
        for (int i = 0; i < dimensions[0]; i++) {
            Object subArray = Array.get(array, i);
            Util.requireArrayLength(subArray, dimensions[1]);
            MemorySegment.copy(subArray, 0, segment, elementLayout, offset + sliceSize * i, dimensions[1]);
        }
    }

    // Reflective use
    private static void fromRecordArray(MemorySegment segment, long offset, Object array,
                                        Class<?> baseComponentType,
                                        int dimension,
                                        long sliceSize,
                                        MethodHandle setter) {

        Util.requireArrayLength(array, dimension);
        for (int i = 0; i < dimension; i++) {
            try {
                Object element = Array.get(array, i);
                setter.invokeExact(segment, offset + i * sliceSize, element);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException propagate) {
                throw propagate;
            } catch (Throwable throwable) {
                throw new RuntimeException("Unable to write " + baseComponentType, throwable);
            }
        }
    }

    // Factory
    static SetMethodHandleGenerator create(MethodHandles.Lookup lookup,
                                           MapperCache mapperCache) {
        return new SetMethodHandleGenerator(lookup, mapperCache);
    }
}
