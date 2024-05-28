package java.lang.foreign.mapper;

import jdk.internal.foreign.mapper.MapperUtil;
import jdk.internal.foreign.mapper.SegmentMapperImpl;
import jdk.internal.foreign.mapper.SegmentRecordMapper2;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.function.Function;

/**
 * A record mapper can project memory segment onto and from record class instances.
 * <p>
 * More specifically, a segment mapper can project a backing
 * {@linkplain MemorySegment MemorySegment} into new {@link Record} instances
 * means of matching the names of the record
 * components with the names of member layouts in a group layout.
 * A segment mapper can also be used in the other direction, where records
 * can be used to update a target memory segment. By using any of
 * the {@linkplain #map(Class, Function, Function) map} operations, segment mappers can be
 * used to map between memory segments and additional Java types other than records
 * (such as JavaBeans).
 *
 * <p>
 * In short, a segment mapper finds, for each record component,
 * a corresponding member layout with the same name in the group layout. There are some
 * restrictions on the record component type and the corresponding member layout type
 * (e.g. a record component of type {@code int} can only be matched with a member layout
 * having a carrier type of {@code int.class} (such as {@link ValueLayout#JAVA_INT})).
 * <p>
 * Using the member layouts (e.g. observing offsets, alignment constraints, and
 * {@link java.nio.ByteOrder byte ordering}), a number of extraction methods are then
 * identified for all the record components or interface methods and these are stored
 * internally in the segment mapper.
 *
 * <h2 id="mapping-records">Mapping Records</h2>
 *
 * The example below shows how to extract an instance of a public
 * <em>{@code Point} record class</em> from a {@link MemorySegment} and vice versa:
 * {@snippet lang = java:
 *
 *  static final GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));
 *  public record Point(int x, int y){}
 *  ...
 *  MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 0, 0});
 *
 *  // Obtain a SegmentMapper for the Point record type
 *  SegmentMapper<Point> recordMapper = SegmentMapper.ofRecord(Point.class, POINT);
 *
 *  // Extracts a new Point record from the provided MemorySegment
 *  Point point = recordMapper.get(segment); // Point[x=3, y=4]
 *
 *  // Writes the Point record to another MemorySegment
 *  MemorySegment otherSegment = Arena.ofAuto().allocate(MemoryLayout.sequenceLayout(2, POINT));
 *  recordMapper.setAtIndex(otherSegment, 1, point); // segment: 0, 0, 3, 4
 *}
 * <p>
 * Boxing, widening, narrowing and general type conversion must be explicitly handled by
 * user code. In the following example, the above {@code Point} (using primitive
 * {@code int x} and {@code int y} coordinates) are explicitly mapped to a narrowed
 * point type (instead using primitive {@code byte x} and {@code byte y} coordinates):
 * <p>
 * {@snippet lang = java:
 * public record NarrowedPoint(byte x, byte y) {
 *
 *     static NarrowedPoint fromPoint(Point p) {
 *         return new NarrowedPoint((byte) p.x, (byte) p.y);
 *     }
 *
 *     static Point toPoint(NarrowedPoint p) {
 *         return new Point(p.x, p.y);
 *     }
 *
 * }
 *
 * RecordMapper<NarrowedPoint> narrowedPointMapper =
 *         RecordMapper.ofRecord(Point.class, POINT)              // SegmentMapper<Point>
 *         .map(NarrowedPoint.class, NarrowedPoint::fromPoint, NarrowedPoint::toPoint); // SegmentMapper<NarrowedPoint>
 *
 * // Extracts a new NarrowedPoint from the provided MemorySegment
 * NarrowedPoint narrowedPoint = narrowedPointMapper.get(segment); // NarrowedPoint[x=3, y=4]
 *}
 *
 * <h2 id="formal-mapping">Formal mapping description</h2>
 *
 * Components and layouts are matched with respect to their name and the exact return type and/or
 * the exact parameter types. No widening or narrowing is employed.
 *
 * <h2 id="restrictions">Restrictions</h2>
 *
 * Generic records need to have their generic type parameters (if any)
 * know at compile time. This applies to all extended interfaces recursively.
 * <p>
 * Records must not implement (directly and/or via inheritance) more than
 * one abstract method with the same name and erased parameter types. Hence, covariant
 * overriding is not supported.
 *
 * <h2 id="general-mapping">General mapping</h2>
 *
 * In addition to mapping records, general mapping capabilities can be made
 * using a factory method that takes custom getter and setter method handles.
 *
 * @param <T> the type this mapper converts MemorySegments from and to.
 *
 * @implSpec Implementations of this interface are immutable, thread-safe and
 *           <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 23
 */

public sealed interface RecordMapper<T>
        extends SegmentMapper<T>
        permits SegmentMapperImpl {

    /**
     * {@return a segment mapper that maps {@linkplain MemorySegment memory segments}
     *          to the provided record {@code type} using the natural layout of {@code type}}
     * <p>
     * Reflective analysis on the provided {@code type} will be made using the
     * MethodHandles.Lookup.publicLookup().
     * <p>
     * The natural layout will be computed as {@linkplain NaturalLayout#ofRecord(Class)} was
     * called with the provided {@code type}.
     *
     * @param type to map memory segment from and to
     * @param <T> the type the returned mapper converts MemorySegments from and to
     * @throws IllegalArgumentException if the provided record {@code type} directly
     *         declares any generic type parameter
     * @throws IllegalArgumentException if the provided record {@code type} is
     *         {@linkplain java.lang.Record}
     * @throws IllegalArgumentException if the provided record {@code type} cannot
     *         be reflectively analysed using
     *         the MethodHandles.Lookup.publicLookup()
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         components for which there are no exact mapping (of names and types) in
     *         the provided {@code layout} or if the provided {@code type} is not public or
     *         if the method is otherwise unable to create a segment mapper as specified above
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         components for which there are no natural layout (e.g. arrays)
     * @see #ofRecord(MethodHandles.Lookup, Class, GroupLayout)
     */
    static <T extends Record> RecordMapper<T> ofRecord(Class<T> type) {
        return ofRecord(MethodHandles.publicLookup(), type, NaturalLayout.ofRecord(type));
    }

    /**
     * {@return a segment mapper that maps {@linkplain MemorySegment memory segments}
     *          to the provided record {@code type} using the provided {@code layout}}
     * <p>
     * Reflective analysis on the provided {@code type} will be made using the
     * MethodHandles.Lookup.publicLookup().
     *
     * @param type to map memory segment from and to
     * @param layout to be used when mapping the provided {@code type}
     * @param <T> the type the returned mapper converts MemorySegments from and to
     * @throws IllegalArgumentException if the provided record {@code type} directly
     *         declares any generic type parameter
     * @throws IllegalArgumentException if the provided record {@code type} is
     *         {@linkplain java.lang.Record}
     * @throws IllegalArgumentException if the provided record {@code type} cannot
     *         be reflectively analysed using
     *         the MethodHandles.Lookup.publicLookup()
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         components for which there are no exact mapping (of names and types) in
     *         the provided {@code layout} or if the provided {@code type} is not public or
     *         if the method is otherwise unable to create a segment mapper as specified above
     * @see #ofRecord(MethodHandles.Lookup, Class, GroupLayout)
     */
    static <T extends Record> RecordMapper<T> ofRecord(Class<T> type,
                                                       GroupLayout layout) {
        return ofRecord(MethodHandles.publicLookup(), type, layout);
    }

    /**
     * {@return a segment mapper that maps {@linkplain MemorySegment memory segments}
     *          to the provided record {@code type} using the provided {@code layout}
     *          and using the provided {@code lookup}}
     *
     * @param lookup to use when performing reflective analysis on the
     *                provided {@code type}
     * @param type to map memory segment from and to
     * @param layout to be used when mapping the provided {@code type}
     * @param <T> the type the returned mapper converts MemorySegments from and to
     * @throws IllegalArgumentException if the provided record {@code type} directly
     *         declares any generic type parameter
     * @throws IllegalArgumentException if the provided record {@code type} is
     *         {@linkplain java.lang.Record}
     * @throws IllegalArgumentException if the provided record {@code type} cannot
     *         be reflectively analysed using the provided {@code lookup}
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         components for which there are no exact mapping (of names and types) in
     *         the provided {@code layout} or if the provided {@code type} is not public or
     *         if the method is otherwise unable to create a segment mapper as specified above
     */
    static <T extends Record> RecordMapper<T> ofRecord(MethodHandles.Lookup lookup,
                                                       Class<T> type,
                                                       GroupLayout layout) {
        Objects.requireNonNull(lookup);
        MapperUtil.requireRecord(type);
        Objects.requireNonNull(layout);
        SegmentRecordMapper2<T> recordMapper = SegmentRecordMapper2.create(lookup, type, layout);
        return new SegmentMapperImpl<>(type, layout, recordMapper.getter(), recordMapper.setter());
    }

}
