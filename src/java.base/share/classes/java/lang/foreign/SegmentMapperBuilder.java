package java.lang.foreign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/* public */ interface SegmentMapperBuilder<T> {

    SegmentMapperBuilder<T> lookup(MethodHandles.Lookup lookup);

    SegmentMapperBuilder<T> mapping(TypeMapping<?, ?> typeMapping);

    SegmentMapperBuilder<T> widening();

    SegmentMapperBuilder<T> narrowing();

    SegmentMapperBuilder<T> narrowingExact();

    SegmentMapperBuilder<T> toStringMapping();

    SegmentMapperBuilder<T> allocator(SegmentAllocator allocator);

    SegmentMapper<T> build();

    static <T> SegmentMapperBuilder<T> ofInterface(Class<T> type,
                                                   GroupLayout layout) {
        Objects.requireNonNull(type);
        if (!type.isInterface()) {
            throw newIae(type, "not an interface");
        }
        if (type.isHidden()) {
            throw newIae(type, "a hidden interface");
        }
        if (type.isSealed()) {
            throw newIae(type, "a sealed interface");
        }
        Objects.requireNonNull(layout);

        throw new UnsupportedOperationException("Todo");
    }

    static <T extends Record> SegmentMapperBuilder<T> ofRecord(Class<T> type,
                                                               GroupLayout layout) {
        Objects.requireNonNull(type);
        if (type.equals(Record.class)) {
            throw newIae(type, "not a real Record");
        }
        Objects.requireNonNull(layout);

        throw new UnsupportedOperationException("Todo");
    }

    // This is two-way mapping
    // Consider one-way mapping
    interface TypeMapping<U, V> {
        @SuppressWarnings("unchecked")
        default Class<U> uType() {
            return (Class<U>) vToU().type().returnType();
        }

        @SuppressWarnings("unchecked")
        default Class<V> vType() {
            return (Class<V>) uToV().type().returnType();
        }

        MethodHandle uToV();

        MethodHandle vToU();

        static <U, V> TypeMapping<U, V> of(Class<U> uType,
                                           Class<V> vType,
                                           MethodHandle uToV,
                                           MethodHandle vToU) {
            Objects.requireNonNull(uType);
            Objects.requireNonNull(vType);
            Objects.requireNonNull(uToV);
            Objects.requireNonNull(vToU);
            MethodType uToVType = uToV.type();
            // Can be relaxed
            if (uToVType.returnType().equals(vType)) {
                throw new IllegalArgumentException("utoV return type is not " + vType);
            }
            // Check all the other MethodHandle invariants
            return new TypeMapping<>() {
                @Override
                public MethodHandle uToV() {
                    return uToV;
                }

                @Override
                public MethodHandle vToU() {
                    return vToU;
                }
            };
        }

        static <U, V> TypeMapping<U, V> of(Class<U> uType,
                                           Class<V> vType,
                                           Function<? super U, ? extends V> uToV,
                                           Function<? super V, ? extends U> vTou) {
            throw new UnsupportedOperationException("Todo");
        }

        static <U, V> TypeMapping<U, V> ofPrimitivesExact(Class<U> uType,
                                                          Class<V> vType) {
            if (!uType.isPrimitive()) {
                throw new IllegalArgumentException("Not a primitive type: " + uType);
            }
            if (!vType.isPrimitive()) {
                throw new IllegalArgumentException("Not a primitive type: " + vType);
            }
            throw new UnsupportedOperationException("Todo");
        }

        static <U, V> TypeMapping<U, V> ofPrimitiveString(Class<U> uType) {
            if (!uType.isPrimitive()) {
                throw new IllegalArgumentException("Not a primitive type: " + uType);
            }
            throw new UnsupportedOperationException("Todo");
        }

    }

    private static IllegalArgumentException newIae(Class<?> type, String trailingInfo) {
        return new IllegalArgumentException(type.getName() + " is " + trailingInfo);
    }

    // Demo

    GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));

    record Point(int x, int y){}
    record StrangePoint(byte x, long y) {}
    record StringPoint(String x, String y) {}

    interface PointAccessor extends SegmentMapper.SegmentBacked {
        int x();
        void x(int x);
        int y();
        void y(int x);
    }

    static void main(String[] args) {

        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 0, 0});
        MemorySegment otherSegment = Arena.ofAuto().allocate(MemoryLayout.sequenceLayout(2, POINT));

        // Regular mapping
        SegmentMapper<Point> mapperPoint = SegmentMapperBuilder.ofRecord(Point.class, POINT).build();



        // Widening and narrowing
        SegmentMapper<StrangePoint> mapperStrangePoint = SegmentMapperBuilder.ofRecord(StrangePoint.class, POINT)
                .widening()
                .narrowingExact()
                .build();

        StrangePoint p = mapperStrangePoint.get(segment); // StrangePoint[byte x=3, long y=4]
        mapperStrangePoint.set(otherSegment, p); // otherSegment: 3, 4, 0, 0 (ints)



        // Object.toString() mapping (one way)
        SegmentMapper<StringPoint> mapperStringPoint = SegmentMapperBuilder.ofRecord(StringPoint.class, POINT)
                .toStringMapping()
                .build();

        StringPoint sp = mapperStringPoint.get(segment); // StringPoint[x="3", y="4"]
        mapperStringPoint.set(otherSegment, sp); // IllegalArgumentException as a String cannot be converted to an int



        // Explicit two-way String mapping for ints
        SegmentMapper<StringPoint> mapperStringPoint2 = SegmentMapperBuilder.ofRecord(StringPoint.class, POINT)
                .mapping(TypeMapping.ofPrimitiveString(int.class))
                .build();

        StringPoint sp2 = mapperStringPoint.get(segment); // StringPoint[x="3", y="4"]
        mapperStringPoint.set(otherSegment, sp2); // otherSegment: 3, 4, 0, 0 (ints)




        // Custom two-way mapping
        try {
            MethodHandle longToIntExact = MethodHandles.publicLookup().findStatic(Math.class, "toIntExact", MethodType.methodType(int.class, long.class));
            MethodHandle intToLong = MethodHandles.lookup().findStatic(SegmentMapperBuilder.class, "intToLong", MethodType.methodType(long.class, int.class));
            TypeMapping<Integer, Long> intLongExact = TypeMapping.of(int.class, long.class, longToIntExact, intToLong);

            // Explicit mapping
            SegmentMapper<StrangePoint> mapperStrangePointExplicit = SegmentMapperBuilder.ofRecord(StrangePoint.class, POINT)
                    .mapping(intLongExact)
                    .build();

        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }

    static long intToLong(int i) {
        return i;
    }


}
