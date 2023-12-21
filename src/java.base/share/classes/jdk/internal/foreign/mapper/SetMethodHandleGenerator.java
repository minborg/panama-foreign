package jdk.internal.foreign.mapper;

import jdk.internal.ValueBased;
import jdk.internal.foreign.mapper.accessor.ArrayInfo;
import jdk.internal.foreign.mapper.component.Util;
import jdk.internal.foreign.mapper.accessor.AccessorInfo;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;

import static jdk.internal.foreign.mapper.component.Util.REQUIRE_ARRAY_LENGTH;

@ValueBased
final class SetMethodHandleGenerator {

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

    public MethodHandle ofScalarRecord(AccessorInfo accessorInfo) throws ReflectiveOperationException {
        return mapperCache.recordSetMethodHandleFor(accessorInfo);
    }

    public MethodHandle ofArrayValue(AccessorInfo accessorInfo) throws ReflectiveOperationException {
        ArrayInfo arrayInfo = accessorInfo.layoutInfo().arrayInfo().orElseThrow();
        if (arrayInfo.dimensions().size() == 1) {
            int length = arrayInfo.dimensions().getFirst().intValue();
            MethodType methodType = MethodType.methodType(void.class,
                    Object.class, int.class, MemorySegment.class, ValueLayout.class, long.class, int.class);
            // (Object arr, int , MemorySegment, ValueLayout.ofx, long, int)void
            var mh = MethodHandles.publicLookup().findStatic(MemorySegment.class, "copy", methodType);
            // -> (Object arr, MemorySegment, ValueLayout.ofx, long, int)void
            mh = MethodHandles.insertArguments(mh, 1 , 0);
            // -> (Object arr, MemorySegment, long, int)void
            mh = MethodHandles.insertArguments(mh, 2, arrayInfo.elementLayout());
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
        throw new UnsupportedOperationException();
    }


    // Factory
    static SetMethodHandleGenerator create(MethodHandles.Lookup lookup,
                                           MapperCache mapperCache) {
        return new SetMethodHandleGenerator(lookup, mapperCache);
    }
}
