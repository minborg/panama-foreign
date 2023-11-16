package jdk.internal.foreign.mapper.component;

import jdk.internal.foreign.mapper.MapperUtil;
import jdk.internal.foreign.mapper.SegmentRecordMapper;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

public final class Util {

    static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public static final MethodType GET_TYPE = MethodType.methodType(Object.class,
            MemorySegment.class, long.class);
    public static final MethodType SET_TYPE = MethodType.methodType(void.class,
            MemorySegment.class, long.class, Object.class);

    // A MethodHandle of type (long, long)long that adds the two terms
    private static final MethodHandle SUM_LONG;
    // A MethodHandle of type (MemorySegment, long, Object)void that does nothing
    public static final MethodHandle SET_NO_OP;

    static {
        try {
            SUM_LONG = LOOKUP.findStatic(Long.class,
                    "sum",
                    MethodType.methodType(long.class, long.class, long.class));
            SET_NO_OP = LOOKUP.findStatic(Util.class,
                    "noop",
                    SET_TYPE);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Util() { }

    // Explicitly cast the return type
    static MethodHandle castReturnType(MethodHandle mh,
                                       Class<?> to) {
        var from = mh.type().returnType();
        if (from == to) {
            // We are done as it is
            return mh;
        }

        if (!to.isPrimitive() && !to.isArray()) {
            throw new IllegalArgumentException("Cannot convert '" + from + "' to '" + to.getName());
        }

        return MethodHandles.explicitCastArguments(mh, GET_TYPE.changeReturnType(to));
    }

    static int dimensionOf(Class<?> arrayClass) {
        return (int) Stream.<Class<?>>iterate(arrayClass, Class::isArray, Class::componentType)
                .count();
    }

    static MethodHandle findStaticToArray(MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
        return LOOKUP.findStatic(Util.class, "toArray", methodType);
    }

    // Wrapper to create an array of Records

    static <R> R[] toArray(MemorySegment segment,
                           GroupLayout elementLayout,
                           long offset,
                           long count,
                           Class<R> type,
                           MethodHandle mapper) {

        var slice = slice(segment, elementLayout, offset, count);
        return toArray(slice, elementLayout, type, mapper);
    }

    @SuppressWarnings("unchecked")
    static <R> R[] toArray(MemorySegment segment,
                           GroupLayout elementLayout,
                           Class<R> type,
                           MethodHandle mapper) {

        return segment.elements(elementLayout)
                .map(s -> {
                    try {
                        return (R) mapper.invokeExact(s, 0L);
                    } catch (Throwable t) {
                        throw new IllegalArgumentException(t);
                    }

                })
                .toArray(s -> (R[]) Array.newInstance(type, Math.toIntExact(s)));
    }

    // Below are `MemorySegment::toArray` wrapper methods that is also taking an offset
    // Begin: Reflectively used methods

    static byte[] toArray(MemorySegment segment,
                          ValueLayout.OfByte elementLayout,
                          long offset,
                          long count) {

        return slice(segment, elementLayout, offset, count).toArray(elementLayout);
    }

    static short[] toArray(MemorySegment segment,
                           ValueLayout.OfShort elementLayout,
                           long offset,
                           long count) {

        return slice(segment, elementLayout, offset, count).toArray(elementLayout);
    }

    static char[] toArray(MemorySegment segment,
                          ValueLayout.OfChar elementLayout,
                          long offset,
                          long count) {

        return slice(segment, elementLayout, offset, count).toArray(elementLayout);
    }

    static int[] toArray(MemorySegment segment,
                         ValueLayout.OfInt elementLayout,
                         long offset,
                         long count) {

        return slice(segment, elementLayout, offset, count).toArray(elementLayout);
    }

    static long[] toArray(MemorySegment segment,
                          ValueLayout.OfLong elementLayout,
                          long offset,
                          long count) {

        return slice(segment, elementLayout, offset, count).toArray(elementLayout);
    }

    static float[] toArray(MemorySegment segment,
                           ValueLayout.OfFloat elementLayout,
                           long offset,
                           long count) {

        return slice(segment, elementLayout, offset, count).toArray(elementLayout);
    }

    static double[] toArray(MemorySegment segment,
                            ValueLayout.OfDouble elementLayout,
                            long offset,
                            long count) {

        return slice(segment, elementLayout, offset, count).toArray(elementLayout);
    }

    static MemorySegment[] toArray(MemorySegment segment,
                                   AddressLayout elementLayout,
                                   long offset,
                                   long count) {

        return slice(segment, elementLayout, offset, count)
                .elements(elementLayout)
                .map(s -> s.get(elementLayout, 0))
                .toArray(MemorySegment[]::new);
    }

    // End: Reflectively used methods

    private static MemorySegment slice(MemorySegment segment,
                                       MemoryLayout elementLayout,
                                       long offset,
                                       long count) {

        return segment.asSlice(offset, elementLayout.byteSize() * count);
    }

    static Object toMultiArrayFunction(MemorySegment segment,
                                       MultidimensionalSequenceLayoutInfo info,
                                       long offset,
                                       Class<?> leafType,
                                       Function<MemorySegment, Object> leafArrayConstructor) {

        int[] dimensions = info.dimensions();
        // Create the array to return
        Object result = Array.newInstance(leafType, dimensions);

        int firstDimension = info.firstDimension();

        var infoFirstRemoved = info.removeFirst();
        int secondDimension = infoFirstRemoved.firstDimension();
        long chunkByteSize = infoFirstRemoved.layoutByteSize();

        for (int i = 0; i < firstDimension; i++) {
            Object part;
            if (dimensions.length == 2) {
                // Trivial case: Just extract the array from the memory segment
                var slice = slice(segment, info.elementLayout(), offset + i * chunkByteSize, secondDimension);
                part = leafArrayConstructor.apply(slice);
            } else {
                // Recursively convert to arrays of (dimension - 1)
                var slice = segment.asSlice(i * chunkByteSize);
                part = toMultiArrayFunction(slice, infoFirstRemoved, offset, leafType, leafArrayConstructor);
            }
            Array.set(result, i, part);
        }
        return result;
    }

    // Represents a no operation action for set operations (e.g. for Records with no components)
    // Used via reflection
    static void noop(MemorySegment segment, long offset, Object t) {
    }

    // Transposing offsets

    private static final Map<Long, MethodHandle> TRANSPOSERS = new ConcurrentHashMap<>();

    public static MethodHandle transposeOffset(MethodHandle mh,
                                               long offset) {

        // (MemorySegment, long)x -> (MemorySegment, long)x
        return MethodHandles.filterArguments(mh, 1, transposer(offset));
    }

    private static MethodHandle transposer(long offset) {
        return offset < 16
                ? TRANSPOSERS.computeIfAbsent(offset, Util::transposer0)
                : transposer0(offset);
    }

    private static MethodHandle transposer0(long offset) {
        // (long, long)long -> (long)long
        return MethodHandles.insertArguments(Util.SUM_LONG, 1, offset);
    }


}
