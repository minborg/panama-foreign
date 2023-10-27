package java.lang.foreign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public interface SegmentMapperOld<T> {

    interface Mappable2 {
        default MemorySegment segment() {
            return backingSegment(this).orElseThrow();
        }
    }

    interface UnMappable {
        default MemorySegment segment() {
            throw new UnsupportedOperationException();
        }
    }

    Class<T> type();

    GroupLayout layout();

    interface OfInterface<T> extends SegmentMapperOld<T> {

        // Convenience methods

        T of(Arena arena);

        default T ofSegment(MemorySegment segment) {
            return ofSegment(segment, 0);
        }

        default T ofSegmentAtIndex(MemorySegment segment, long index) {
            return ofSegment(segment, layout().byteSize() * index);
        }

        @SuppressWarnings("unchecked")
        default T ofSegment(MemorySegment segment, long offset) {
            try {
                return (T) createHandle().invokeExact(segment, offset);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        // Basic method

        MethodHandle createHandle(); // (MemorySegment, long)T
    }

    interface OfRecord<T extends Record> extends SegmentMapperOld<T> {

        // Convenience methods

        default T get(MemorySegment segment) {
            return get(segment, 0);
        }

        default T getAtIndex(MemorySegment segment, long index) {
            return get(segment, layout().byteSize() * index);
        }

        @SuppressWarnings("unchecked")
        default T get(MemorySegment segment, long offset) {
            try {
                return (T) getHandle().invokeExact(segment, offset);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        default void set(MemorySegment segment, T t) {
            set(segment, 0, t);
        }

        default void setAtIndex(MemorySegment segment, long index, T t) {
            set(segment, layout().byteSize() * index, t);
        }

        default void set(MemorySegment segment, long offset, T t) {
            try {
                getHandle().invokeExact(segment, offset, t);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        // Basic methods

        MethodHandle getHandle(); // (MemorySegment, long)T

        MethodHandle setHandle(); //(MemorySegment, long, T)void

    }

    static <T> OfInterface<T> ofInterface(Class<T> type, GroupLayout layout) {
        return ofInterface(MethodHandles.publicLookup(), type, layout);
    }

    static <T> OfInterface<T> ofInterface(MethodHandles.Lookup lookup, Class<T> type, GroupLayout layout) {
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(type);
        if (!type.isInterface()) {
            throw new IllegalArgumentException(type.getName() + " is not an interface");
        }
        if (type.isHidden()) {
            throw new IllegalArgumentException(type.getName() + " is a hidden interface");
        }
        if (type.isSealed()) {
            throw new IllegalArgumentException(type.getName() + " is a sealed interface");
        }
        Objects.requireNonNull(layout);

        throw new UnsupportedOperationException("To do");
    }

    static <T extends Record> OfRecord<T> ofRecord(Class<T> type, GroupLayout layout) {
        return ofRecord(MethodHandles.publicLookup(), type, layout);
    }

    static <T extends Record> OfRecord<T> ofRecord(MethodHandles.Lookup lookup, Class<T> type, GroupLayout layout) {
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(type);
        if (type.equals(Record.class)) {
            throw new IllegalArgumentException(type.getName() + " is not a real Record");
        }
        Objects.requireNonNull(layout);

        throw new UnsupportedOperationException("To do");
    }

    // Mappable
    static Optional<MemorySegment> backingSegment(Object o) {
        return Optional.empty();
    }

    // Demonstration of the concepts

    GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));
    record Point(int x, int y){}

    interface PointAccessor {
        int x(); void x(int x);
        int y(); void y(int y);
    }

    static void main(String[] args) {
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 0, 0});

        SegmentMapperOld.OfRecord<Point> recordMapper = SegmentMapperOld.ofRecord(Point.class, POINT); // OfRecord[type=x.y.Point, layout=...]
        Point point = recordMapper.get(segment); // Point[x=3, y=4]
        recordMapper.setAtIndex(segment, 1, point); // segment: 3, 4, 3, 4

        SegmentMapperOld.OfInterface<PointAccessor> interfaceMapper =
                SegmentMapperOld.ofInterface(PointAccessor.class, POINT); // OfInterface[type=x.y.PointAccessor, layout=...]
        PointAccessor pointAccessor = interfaceMapper.ofSegmentAtIndex(segment, 1);
        pointAccessor.x(6); // segment: 3, 4, 6, 4
        pointAccessor.y(8); // segment: 3, 4, 6, 8
        System.out.println(pointAccessor); // PointAccessor[x=6, y=8]

        // Private class
        record MyPoint(int x, int y) {}

        var myMapper = SegmentMapperOld.ofRecord(MethodHandles.lookup(), MyPoint.class, POINT); // OfRecord[type=...Foo$MyPoint, layout=...]
        MyPoint myPoint = myMapper.get(segment, 8); // MyPoint[x=6, y=4]
    }

}
