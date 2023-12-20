package jdk.internal.foreign.mapper;

import jdk.internal.ValueBased;
import jdk.internal.foreign.mapper.accessor.AccessorInfo;
import jdk.internal.foreign.mapper.accessor.ArrayInfo;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@ValueBased
final class GetMethodHandleGenerator {

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

        System.out.println("arrayInfo = " + arrayInfo);

        Class<?> interfaceType = accessorInfo.layoutInfo().scalarInfo().orElseThrow().interfaceType();
        if (arrayInfo.dimensions().size() == 1) {
            MethodType methodType = MethodType.methodType(accessorInfo.type(), interfaceType);
            // (MemorySegment, ValueLayout.ofx)x[]
            var mh = MethodHandles.publicLookup().findVirtual(MemorySegment.class, "toArray", methodType);
            // (MemorySegment, ValueLayout.ofx)x[] -> (MemorySegment)x[]
            mh = MethodHandles.insertArguments(mh, 1, arrayInfo.elementLayout());
            // (MemorySegment)x[] -> (MemorySegment, long, MemoryLayout)x[]
            mh = MethodHandles.collectArguments(mh, 0, AS_SLICE_OFFSET_LAYOUT);
            // (MemorySegment, long, MemoryLayout)x[] -> (MemorySegment, long)x[]
            return MethodHandles.insertArguments(mh, 2, accessorInfo.layoutInfo().layout());
        }
        throw new UnsupportedOperationException();
    }

    static GetMethodHandleGenerator create(MethodHandles.Lookup lookup,
                                           MapperCache mapperCache) {
        return new GetMethodHandleGenerator(lookup, mapperCache);
    }
}
