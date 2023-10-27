package java.lang.foreign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public interface SegmentMapper<T> {

    interface HasSegment {
        MemorySegment segment();
    }

    Class<T> type();

    GroupLayout layout();

    // Convenience methods

    default T get(SegmentAllocator allocator) {
        return get(allocator.allocate(layout()));
    }

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

    static <T> SegmentMapper<T> ofInterface(Class<T> type, GroupLayout layout) {
        return ofInterface(MethodHandles.publicLookup(), type, layout);
    }

    static <T> SegmentMapper<T> ofInterface(MethodHandles.Lookup lookup, Class<T> type, GroupLayout layout) {
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

    static <T extends Record> SegmentMapper<T> ofRecord(Class<T> type, GroupLayout layout) {
        return ofRecord(MethodHandles.publicLookup(), type, layout);
    }

    static <T extends Record> SegmentMapper<T> ofRecord(MethodHandles.Lookup lookup, Class<T> type, GroupLayout layout) {
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(type);
        if (type.equals(Record.class)) {
            throw new IllegalArgumentException(type.getName() + " is not a real Record");
        }
        Objects.requireNonNull(layout);

        throw new UnsupportedOperationException("To do");
    }

    // Demonstration of the concepts

    GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));
    record Point(int x, int y){}

    interface PointAccessor extends HasSegment {
        int x(); void x(int x);
        int y(); void y(int y);
    }

    static void main(String[] args) {
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 0, 0});

        SegmentMapper<Point> recordMapper = SegmentMapper.ofRecord(Point.class, POINT); // OfRecord[type=x.y.Point, layout=...]
        Point point = recordMapper.get(segment); // Point[x=3, y=4]
        recordMapper.setAtIndex(segment, 1, point); // segment: 3, 4, 3, 4

        SegmentMapper<PointAccessor> interfaceMapper =
                SegmentMapper.ofInterface(PointAccessor.class, POINT); // OfInterface[type=x.y.PointAccessor, layout=...]
        PointAccessor pointAccessor = interfaceMapper.getAtIndex(segment, 1);
        pointAccessor.x(6); // segment: 3, 4, 6, 4
        pointAccessor.y(8); // segment: 3, 4, 6, 8
        System.out.println(pointAccessor); // PointAccessor[x=6, y=8, segment=...]

        // Creates a PointAccessor with a private segment
        PointAccessor privatePointAccessor = interfaceMapper.get(Arena.ofAuto()); // PointAccessor[x=0, y=0, segment=...]
        pointAccessor.x(10); // PointAccessor[x=10, y=0, segment=...]
        pointAccessor.y(11); // PointAccessor[x=10, y=11, segment=...]
        pointAccessor.segment(); // ...
        interfaceMapper.setAtIndex(segment, 1, pointAccessor); // segment: 3, 4, 10, 11

        // Private class
        record MyPoint(int x, int y) {}

        var myMapper = SegmentMapper.ofRecord(MethodHandles.lookup(), MyPoint.class, POINT); // OfRecord[type=...Foo$MyPoint, layout=...]
        MyPoint myPoint = myMapper.get(segment, 8); // MyPoint[x=10, y=11]
    }

}
