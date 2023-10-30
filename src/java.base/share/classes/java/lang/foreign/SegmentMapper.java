package java.lang.foreign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * A segment mapper that can project source {@linkplain MemorySegment MemorySegments} into new
 * {@link Record} instances or new interface instances by means of matching the names of the
 * record components or interface methods with the names of member layouts in a group layout. The mapper can
 * also be used in the other direction, where records and interface instances can be used to update
 * a target memory segment.
 * <p>
 * In short, the mapper finds, for each record component or interface method, a corresponding member layout with the same
 * name in the group layout. There are some restrictions on the record component type and the
 * corresponding member layout type (e.g. a record component of type {@code int} can only be matched
 * with a member layout having a carrier type of {@code int.class} (such as {@link ValueLayout#JAVA_INT})).
 * <p>
 * Using the member layouts (e.g. observing offsets and {@link java.nio.ByteOrder byte ordering}), a
 * number of extraction methods are then identified for all the record components or interface methods and
 * these are stored internally in the segment mapper.
 * <p>
 * Segment mappers can be of three fundamental kinds;
 * <ul>
 *     <li>Record</li>
 *     <li>Interface with an internal segment</li>
 *     <li>Interface with an external segment</li>
 * </ul>
 * <p>
 * The characteristics of the various mapper kinds are summarized in the following table:
 * <p>
 * <blockquote><table class="plain">
 * <caption style="display:none">Mapper characteristics</caption>
 * <thead>
 * <tr>
 *     <th scope="col">Mapper kind</th>
 *     <th scope="col">Temporal mode</th>
 *     <th scope="col">Get operations</th>
 *     <th scope="col">Set operations</th>
 *     <th scope="col">Segment access</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row" style="font-weight:normal">Record</th>
 *     <td style="text-align:center;">Eager</td>
 *     <td style="text-align:center;">Extract all component values from the source segment, build the record</td>
 *     <td style="text-align:center;">Write all component values to the target segment</td>
 *     <td style="text-align:center;">N/A</td></tr>
 * <tr><th scope="row" style="font-weight:normal">Interface with an internal segment</th>
 *     <td style="text-align:center;">Eager</td>
 *     <td style="text-align:center;">Copy the relevant values from the source segment into the internal backing segment, then wrap the latter into a new interface instance</td>
 *     <td style="text-align:center;">Copy the relevant values from the internal backing segment into the target segment</td>
 *     <td style="text-align:center;">If instance of <code>SegmentBacked</code></td></tr>
 * <tr><th scope="row" style="font-weight:normal">Interface with an external segment</th>
 *     <td style="text-align:center;">Lazy</td>
 *     <td style="text-align:center;">Wrap the source segment into a new interface instance</td>
 *     <td style="text-align:center;">Copy the relevant values from the initial source segment into the target segment</td>
 *     <td style="text-align:center;">If instance of <code>SegmentBacked</code></td></tr>
 * </tbody>
 * </table></blockquote>
 * <p>
 * The example below shows how to extract an instance of a public <em>{@code Point} record class</em>
 * from a {@link MemorySegment}:
 * {@snippet lang = java:
 *
 *  GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));
 *  public record Point(int x, int y){}
 *
 *  MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 0, 0});
 *
 *  SegmentMapper<Point> recordMapper = SegmentMapper.ofRecord(Point.class, POINT); // SegmentMapper[type=x.y.Point, layout=...]
 *  // Extracts a new Point from the provided MemorySegment
 *  Point point = recordMapper.get(segment); // Point[x=3, y=4]
 *  recordMapper.setAtIndex(segment, 1, point); // segment: 3, 4, 3, 4
 *}
 * <p>
 * Boxing, widening and narrowing must be explicitly handled by user code.  In the following example, the above
 * {@code Point} (using primitive {@code int x} and {@code int y} coordinates) are explicitly mapped to
 * a narrowed point type (instead using primitive {@code byte x} and {@code byte y} coordinates):
 * <p>
 * {@snippet lang = java:
 * public record NarrowedPoint(byte x, byte y) {
 *
 *     static NarrowedPoint fromPoint(Point p) {
 *         return new NarrowedPoint((byte) p.x, (byte) p.y);
 *     }
 *
 *     static NarrowedPoint toPoint(NarrowedPoint p) {
 *         return new Point(p.x, p.y);
 *     }
 *
 * }
 *
 * SegmentMapper<NarrowedPoint> narrowedPointMapper = pointLayout.recordMapper(Point.class)
 *                 .andThen(NarrowedPoint::fromPoint, NarrowedPoint::toPoint);
 *
 *     // Extracts a new NarrowedPoint from the provided MemorySegment
 *     NarrowedPoint narrowedPoint = narrowedPointMapper.apply(segment); // NarrowedPoint[x=3, y=4]
 * }
 * <p>
 * The example below shows how to extract an instance of a public <em>interface with an internal segment</em>:
 * {@snippet lang = java:
 *
 *  GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));
 *  public interface Point {
 *       int x();
 *       void x(int x);
 *       int y();
 *       void y(int x);
 *  }
 *
 *  SegmentMapper<Point> mapper = SegmentMapper.ofInterface(Point.class, POINT); // SegmentMapper[type=x.y.Point, layout=...]
 *  try (var arena = Arena.ofConfined()) {
 *      // Creates a new Point interface instance with an internal segment
 *      Point point = mapper.get(arena); // Point[x=0, y=0] (uninitialized)
 *      point.x(3); // Point[x=3, y=0]
 *      point.y(4); // Point[x=3, y=4]
 *
 *      MemorySegment otherSegment = arena.allocate(MemoryLayout.sequenceLayout(2, POINT)); // otherSegment: 0, 0, 0, 0
 *      mapper.setAtIndex(otherSegment, 1); // otherSegment: 0, 0, 3, 4
 *  }
 *}
 * <p>
 * Here is another example showing how to extract an instance of a public <em>interface with an external segment</em>:
 * {@snippet lang = java:
 *
 *  GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));
 *  public interface Point {
 *       int x();
 *       void x(int x);
 *       int y();
 *       void y(int x);
 *  }
 *
 *  MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 0, 0});
 *
 *  SegmentMapper<Point> mapper = SegmentMapper.ofInterface(Point.class, POINT); // SegmentMapper[type=x.y.Point, layout=...]
 *
 *  // Creates a new Point interface instance with an external segment
 *  Point point = mapper.get(segment); // Point[x=3, y=4]
 *  point.x(6); // Point[x=6, y=4]
 *  point.y(8); // Point[x=6, y=8]
 *  mapper.setAtIndex(segment, 1, point); // segment: 6, 8, 6, 8
 *  }
 *}
 * <p>
 * Boxing, widening and narrowing must be explicitly handled by user code.  In the following example, the above
 * {@code Point} interface (using primitive {@code int x} and {@code int y} coordinates) are explicitly mapped to
 * a narrowed point type (instead using primitive {@code byte x} and {@code byte y} coordinates):
 * <p>
 * {@snippet lang = java:
 * interface NarrowedPointAccessor {
 *    byte x();
 *    void x(byte x);
 *    byte y();
 *    void y(byte y);
 *
 *    static NarrowedPointAccessor fromPointAccessor(PointAccessor pa) {
 *        return new NarrowedPointAccessor() {
 *            @Override public byte x() { return (byte)pa.x(); }
 *            @Override public void x(byte x) { pa.x(x); }
 *            @Override public byte y() { return (byte) pa.y();}
 *            @Override public void y(byte y) { pa.y(y); }
 *       };
 *    }
 *
 * }
 *
 * SegmentMapper<NarrowedPointAccessor> narrowedPointMapper = pointLayout.recordMapper(Point.class)
 *                 .andThen(NarrowedPointAccessor::fromPointAccessor);
 *
 * MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4});
 *
 * // Creates a new NarrowedPointAccessor from the provided MemorySegment
 * NarrowedPointAccessor narrowedPointAccessor = narrowedPointMapper.apply(segment); // NarrowedPointAccessor[x=3, y=4]
 *}
 * <p>
 * @param <T> the type this mapper converts to and from.

 * @since 23
 */
public interface SegmentMapper<T> {

    /**
     * Interfaces implementing this method will obtain an extra
     * method {@code segment()} that will return the backing
     * segment for the interface (either internal or external).
     */
    interface SegmentBacked {
        MemorySegment segment();
    }

    /**
     * {@return the original type that this mapper is mapping to and from}
     * <p>
     * Composed segment mappers (obtained via either the {@link SegmentMapper#andThen(Function)} or the
     * {@link SegmentMapper#andThen(Function, Function)} will still return the type from the original
     * SegmentMapper.
     */
    Class<T> type();

    /**
     * {@return the original {@link GroupLayout} that this mapper is using to map components or interface methods}
     * <p>
     * Composed segment mappers (obtained via either the {@link SegmentMapper#andThen(Function)} or the
     * {@link SegmentMapper#andThen(Function, Function)} will still return the group layout from the original
     * SegmentMapper.
     */
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

    // For records
    <R> SegmentMapper<R> andThen(Function<? super T, ? extends R> toMapper,
                                 Function<? super R, ? extends T> fromMapper);

    // For interfaces and one-way record mapping
    <R> SegmentMapper<R> andThen(Function<? super T, ? extends R> mapper);

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

    interface PointAccessor {
        int x(); void x(int x);
        int y(); void y(int y);
    }

    interface BytePointAccessor {
        byte x();
        void x(byte x);
        byte y();
        void y(byte y);

        static BytePointAccessor fromPointAccessor(PointAccessor pa) {
            return new BytePointAccessor() {
                @Override public byte x() { return (byte)pa.x(); }
                @Override public void x(byte x) { pa.x(x); }
                @Override public byte y() { return (byte) pa.y();}
                @Override public void y(byte y) { pa.y(y); }

            };
        }

        static PointAccessor toPointAccessor(BytePointAccessor bpa) {
            return new PointAccessor() {
                @Override public int x() { return (byte)bpa.x(); }
                @Override public void x(int x) { bpa.x((byte)x); }
                @Override public int y() { return bpa.y();}
                @Override public void y(int y) { bpa.y((byte)y); }
            };
        }

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
        //pointAccessor.segment(); // ...
        interfaceMapper.setAtIndex(segment, 1, pointAccessor); // segment: 3, 4, 10, 11

        // Private class
        record MyPoint(int x, int y) {}

        var myMapper = SegmentMapper.ofRecord(MethodHandles.lookup(), MyPoint.class, POINT); // OfRecord[type=...Foo$MyPoint, layout=...]
        MyPoint myPoint = myMapper.get(segment, 8); // MyPoint[x=10, y=11]
    }

}
