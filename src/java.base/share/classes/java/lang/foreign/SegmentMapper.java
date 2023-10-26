package java.lang.foreign;

import java.lang.invoke.MethodHandles;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public interface SegmentMapper<T> {

    Class<T> type();

    GroupLayout layout();

    interface OfInterface<T> extends SegmentMapper<T> {

        default T ofSegment(MemorySegment segment) {
            return ofSegment(segment, 0);
        }

        default T ofSegmentAtIndex(MemorySegment segment, long index) {
            return ofSegment(segment, layout().byteSize() * index);
        }

        T ofSegment(MemorySegment segment, long offset);

    }

    interface OfRecord<T extends Record> extends SegmentMapper<T> {

        default T get(MemorySegment segment) {
            return get(segment, 0);
        }

        default T getAtIndex(MemorySegment segment, long index) {
            return get(segment, layout().byteSize() * index);
        }

        T get(MemorySegment segment, long offset);

        default void set(MemorySegment segment, T t) {
            set(segment, 0, t);
        }

        default void setAtIndex(MemorySegment segment, long index, T t) {
            set(segment, layout().byteSize() * index, t);
        }

        void set(MemorySegment segment, long offset, T t);

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

    GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));
    record Point(int x, int y){}

    interface PointAccessor {
        int x(); void x(int x);
        int y(); void y(int y);
    }

    static void main(String[] args) {
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 0, 0});

        SegmentMapper.OfRecord<Point> recordMapper = SegmentMapper.ofRecord(Point.class, POINT); // OfRecord[type=x.y.Point, layout=...]
        Point point = recordMapper.get(segment); // Point[x=3, y=4]
        recordMapper.setAtIndex(segment, 1, point); // segment: 3, 4, 3, 4

        SegmentMapper.OfInterface<PointAccessor> interfaceMapper =
                SegmentMapper.ofInterface(PointAccessor.class, POINT); // OfInterface[type=x.y.PointAccessor, layout=...]
        PointAccessor pointAccessor = interfaceMapper.ofSegmentAtIndex(segment, 1);
        pointAccessor.x(6); // segment: 3, 4, 6, 4
        pointAccessor.y(8); // segment: 3, 4, 6, 8
        System.out.println(pointAccessor); // PointAccessor[x=6, y=8]

        // Private
        record MyPoint(int x, int y) {}

        var myMapper = SegmentMapper.ofRecord(MethodHandles.lookup(), MyPoint.class, POINT); // OfRecord[type=...Foo$MyPoint, layout=...]
        MyPoint myPoint = myMapper.get(segment, 8); // MyPoint[x=6, y=4]
    }

}
