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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;
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
    public static final MethodHandle SET_NO_OP = MethodHandles.empty(SET_TYPE);

    static {
        try {
            SUM_LONG = LOOKUP.findStatic(Long.class,
                    "sum",
                    MethodType.methodType(long.class, long.class, long.class));
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


    public static MethodHandle findStaticListToArray(MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
        return LOOKUP.findStatic(Util.class, "listToArray", methodType);
    }

    // Below are `MemorySegment::toArray` wrapper methods that is also taking an offset
    // Begin: Reflectively used methods

    // Get operations

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

    // Extract an array from a list.

    private static byte[] listToArray(ValueLayout.OfByte layout,
                                      List<Byte> list) {
        int size = list.size();
        byte[] result = new byte[size];
        for (int i = 0; i < size; i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    private static short[] listToArray(ValueLayout.OfShort layout,
                                       List<Short> list) {
        int size = list.size();
        short[] result = new short[size];
        for (int i = 0; i < size; i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    private static char[] listToArray(ValueLayout.OfChar layout,
                                      List<Character> list) {
        int size = list.size();
        char[] result = new char[size];
        for (int i = 0; i < size; i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    private static int[] listToArray(ValueLayout.OfInt layout,
                                     List<Integer> list) {
        int size = list.size();
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    private static float[] listToArray(ValueLayout.OfFloat layout,
                                       List<Float> list) {
        int size = list.size();
        float[] result = new float[size];
        for (int i = 0; i < size; i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    private static long[] listToArray(ValueLayout.OfLong layout,
                                      List<Long> list) {
        int size = list.size();
        long[] result = new long[size];
        for (int i = 0; i < size; i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    private static double[] listToArray(ValueLayout.OfDouble layout,
                                        List<Double> list) {
        int size = list.size();
        double[] result = new double[size];
        for (int i = 0; i < size; i++) {
            result[i] = list.get(i);
        }
        return result;
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

    public static Class<?> firstGenericType(RecordComponent rc) {
        Type genericType = rc.getGenericType();
        if (genericType instanceof ParameterizedType parameterizedType) {
            Type firstGenericParameter = parameterizedType.getActualTypeArguments()[0];
            if (firstGenericParameter instanceof Class<?> c) {
                return c;
            }
            throw new IllegalArgumentException("Type is not a Class " + firstGenericParameter);
        }
        throw new IllegalArgumentException("Unable to determine the generic type of " + rc);
    }


}
