package java.lang.foreign.mapper;

import jdk.internal.foreign.mapper.MapperUtil;
import jdk.internal.foreign.mapper.SegmentMapperImpl;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.function.Function;

/**
 * A segment mapper can project memory segment onto and from class instances.
 * <p>
 * More specifically, a segment mapper can project a backing
 * {@linkplain MemorySegment MemorySegment} into new {@link Record} instances or new
 * instances that implements an interface by means of matching the names of the record
 * components or interface methods with the names of member layouts in a group layout.
 * A segment mapper can also be used in the other direction, where records and interface
 * implementing instances can be used to update a target memory segment. By using any of
 * the {@linkplain #map(Function) map} operations, segment mappers can be used to map
 * between memory segments and additional Java types other than record and interfaces
 * (such as JavaBeans).
 *
 * <p>
 * In short, a segment mapper finds, for each record component or interface method,
 * a corresponding member layout with the same name in the group layout. There are some
 * restrictions on the record component type and the corresponding member layout type
 * (e.g. a record component of type {@code int} can only be matched with a member layout
 * having a carrier type of {@code int.class} (such as {@link ValueLayout#JAVA_INT})).
 * <p>
 * Using the member layouts (e.g. observing offsets and
 * {@link java.nio.ByteOrder byte ordering}), a number of extraction methods are then
 * identified for all the record components or interface methods and these are stored
 * internally in the segment mapper.
 *
 * <h2 id="mapping-kinds">Mapping kinds</h2>
 *
 * Segment mappers can be of three fundamental kinds;
 * <ul>
 *     <li>Record</li>
 *     <li>Interface implementation backed by an internal segment</li>
 *     <li>Interface implementation backed by an external segment</li>
 * </ul>
 * <p>
 * The characteristics of the various mapper kinds are summarized in
 * the following table:
 *
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
 * <tr><th scope="row" style="font-weight:normal">Interface implementation backed by an internal segment</th>
 *     <td style="text-align:center;">Eager</td>
 *     <td style="text-align:center;">Copy the relevant values from the source segment into the internal backing segment, then wrap the latter into a new interface instance</td>
 *     <td style="text-align:center;">Copy the relevant values from the internal backing segment into the target segment</td>
 *     <td style="text-align:center;">If instance of <code>SegmentBacked</code></td></tr>
 * <tr><th scope="row" style="font-weight:normal">Interface implementation backed by an external segment</th>
 *     <td style="text-align:center;">Lazy</td>
 *     <td style="text-align:center;">Wrap the source segment into a new interface instance</td>
 *     <td style="text-align:center;">Copy the relevant values from the initial source segment into the target segment</td>
 *     <td style="text-align:center;">If instance of <code>SegmentBacked</code></td></tr>
 * </tbody>
 * </table></blockquote>

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
 * SegmentMapper<NarrowedPoint> narrowedPointMapper =
 *         SegmentMapper.ofRecord(Point.class, POINT)              // SegmentMapper<Point>
 *         .map(NarrowedPoint::fromPoint, NarrowedPoint::toPoint); // SegmentMapper<NarrowedPoint>
 *
 * // Extracts a new NarrowedPoint from the provided MemorySegment
 * NarrowedPoint narrowedPoint = narrowedPointMapper.get(segment); // NarrowedPoint[x=3, y=4]
 * }
 *
 * <h2 id="mapping-interfaces-internal">Mapping interfaces backed by an internal segment</h2>
 *
 * The example below shows how to extract an instance of a public
 * <em>interface backed by an internal segment</em>:
 * {@snippet lang = java:
 *
 *  static final GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));
 *
 *  public interface PointAccessor {
 *       int x();
 *       void x(int x);
 *       int y();
 *       void y(int x);
 *  }
 *
 *  ...
 *
 *  SegmentMapper<PointAccessor> mapper = SegmentMapper.ofInterface(PointAccessor.class, POINT);
 *
 *  try (var arena = Arena.ofConfined()) {
 *      // Creates a new Point interface instance with an internal segment
 *      PointAccessor point = mapper.get(arena); // Point[x=0, y=0] (uninitialized)
 *      point.x(3); // Point[x=3, y=0]
 *      point.y(4); // Point[x=3, y=4]
 *
 *      MemorySegment otherSegment = arena.allocate(MemoryLayout.sequenceLayout(2, POINT)); // otherSegment: 0, 0, 0, 0
 *      mapper.setAtIndex(otherSegment, 1, point); // otherSegment: 0, 0, 3, 4
 *  }
 *}
 *
 * <h2 id="mapping-interfaces-external">Mapping interfaces backed by an external segment</h2>
 *
 * Here is another example showing how to extract an instance of a public
 * <em>interface with an external segment</em>:
 * {@snippet lang = java:
 *
 *  static final GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));
 *
 *  public interface Point {
 *       int x();
 *       void x(int x);
 *       int y();
 *       void y(int x);
 *  }
 *
 *  ...
 *
 *  MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 0, 0});
 *
 *  SegmentMapper<Point> mapper = SegmentMapper.ofInterface(Point.class, POINT);
 *
 *  // Creates a new Point interface instance with an external segment
 *  Point point = mapper.get(segment); // Point[x=3, y=4]
 *  point.x(6); // Point[x=6, y=4]
 *  point.y(8); // Point[x=6, y=8]
 *
 *  MemorySegment otherSegment = Arena.ofAuto().allocate(MemoryLayout.sequenceLayout(2, POINT)); // otherSegment: 0, 0, 0, 0
 *  mapper.setAtIndex(otherSegment, 1, point); // segment: 0, 0, 6, 8
 *  }
 *}
 * <p>
 * Boxing, widening, narrowing and general type conversion must be explicitly handled
 * by user code. In the following example, the above {@code PointAccessor} interface
 * (using primitive {@code int x} and {@code int y} coordinates) are explicitly mapped to
 * a narrowed point type (instead using primitive {@code byte x} and
 * {@code byte y} coordinates):
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
 *            @Override public byte x()       { return (byte)pa.x(); }
 *            @Override public void x(byte x) { pa.x(x); }
 *            @Override public byte y()       { return (byte) pa.y();}
 *            @Override public void y(byte y) { pa.y(y); }
 *       };
 *    }
 *
 * }
 *
 * SegmentMapper<NarrowedPointAccessor> narrowedPointMapper =
 *          SegmentMapper.ofInterface(PointAccessor.class, POINT)
 *                 .map(NarrowedPointAccessor::fromPointAccessor);
 *
 * MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4});
 *
 * // Creates a new NarrowedPointAccessor from the provided MemorySegment
 * NarrowedPointAccessor narrowedPointAccessor = narrowedPointMapper.get(segment); // NarrowedPointAccessor[x=3, y=4]
 *
 * MemorySegment otherSegment = Arena.ofAuto().allocate(MemoryLayout.sequenceLayout(2, POINT));
 * narrowedPointMapper.setAtIndex(otherSegment, 1, narrowedPointAccessor); // otherSegment = 0, 0, 3, 4
 *}
 *
 * <h2 id="segment-exposure">Backing segment exposure</h2>
 *
 * Interfaces that are used in conjunction with segment mappers can elect to implement
 * the {@linkplain SegmentBacked} interface. Mappers reflecting such interfaces will
 * automatically connect the {@linkplain SegmentBacked#segment() segment()} method to
 * the backing segment (be it internal or external). This is useful when modelling structs
 * that are passed and/or received by native calls:
 * <p>
 * {@snippet lang = java:
 * static final GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));
 *
 * // Automatically adds a segment() method that connects to the backing segment
 * public interface PointAccessor extends SegmentMapper.SegmentBacked {
 *     int x();
 *     void x(int x);
 *     int y();
 *     void y(int x);
 * }
 *
 * static double nativeDistance(MemorySegment pointStruct) {
 *     // Calls a native method
 *     // ...
 * }
 *
 * public static void main(String[] args) {
 *
 *     SegmentMapper<PointAccessor> mapper =
 *             SegmentMapper.ofInterface(PointAccessor.class, POINT);
 *
 *     try (Arena arena = Arena.ofConfined()){
 *         // Creates an interface mapper backed by an internal segment
 *         PointAccessor point = mapper.get(arena);
 *         point.x(3);
 *         point.y(4);
 *
 *         // Pass the backing internal segment to a native method
 *         double distance = nativeDistance(point.segment()); // 5
 *     }
 *
 * }
 * }
 *
 * <h2 id="formal-mapping">Formal mapping description</h2>
 *
 *  TBW.
 *
 * @param <T> the type this mapper converts MemorySegments from and to.
 *
 * @since 23
 */
// Todo: Interfaces with internal segments should be directly available via separate factory methods
// Todo: Map components to MemorySegment (escape hatch)
// Todo: Discuss non-exact mapping (e.g. int -> String), invokeExact vs. invoke
// Todo: map() can be dropped in favour of "manual mapping"
// Todo: segment() and type() return values for composed mappers
// @PreviewFeature(feature=PreviewFeature.Feature.SEGMENT_MAPPERS)
public interface SegmentMapper<T> {

    /**
     * Exposes the backing memory segment for segment mapped interfaces.
     * <p>
     * Interfaces types provided to factory methods of SegmentMapper that are
     * implementing the {@code SegmentBacked} interface will obtain an extra method
     * {@code segment()} that will return the backing segment for the interface
     * (either internal or external).
     * <p>
     * It is an error to let a record implement this interface and then provide such
     * a record type to any of the record factory methods of SegmentMapper.
     */
    //@PreviewFeature(feature=PreviewFeature.Feature.SEGMENT_MAPPERS)
    interface SegmentBacked {
        /**
         * {@return the segment that backs this interface (internal or external)}
         */
        MemorySegment segment();
    }

    /**
     * {@return the original type that this mapper is mapping to and from}
     * <p>
     * Composed segment mappers (obtained via either the {@link SegmentMapper#map(Function)}
     * or the {@link SegmentMapper#map(Function, Function)} will still return the type from
     * the <em>original</em> SegmentMapper.
     */
    Class<T> type();

    /**
     * {@return the original {@link GroupLayout } that this mapper is using to map
     *          record components or interface methods}
     * <p>
     * Composed segment mappers (obtained via either the {@link SegmentMapper#map(Function)}
     * or the {@link SegmentMapper#map(Function, Function)} will still return the
     * group layout from the <em>original</em> SegmentMapper.
     */
    GroupLayout layout();

    /**
     * {@return {@code true} if this mapper exhaustively maps all the contents of
     *          a segment to components of this mapper}
     * <p>
     * More formally, if S is a segment with {@code S.byteSize() == layout().byteSize()}
     * then there exists exactly one distinct mapping for every permutation of S's
     * contents.
     */
    boolean isExhaustive();

    // Convenience methods

    /**
     * {@return a new instance of type T backed by an internal segment allocated from
     *          the provided {@code allocator}}
     * <p>
     * If an exceptions is thrown by the allocator (specifically when calling
     * {@linkplain SegmentAllocator#allocate(MemoryLayout)}), that exception is relayed
     * to the caller.
     *
     * @param allocator to be used for allocating the internal segment
     */
    default T get(SegmentAllocator allocator) {
        return get(allocator.allocate(layout()));
    }

    /**
     * {@return a new instance of type T projected from the provided
     *          external {@code segment} at offset zero}
     *
     * @param segment the external segment to be projected to the new instance
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with the provided segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if
     *         {@code layout().byteSize() > segment.byteSize()}
     */
    default T get(MemorySegment segment) {
        return get(segment, 0);
    }

    /**
     * {@return a new instance of type T projected from the provided
     *          external {@code segment} at the provided {@code offset}}
     *
     * @param segment the external segment to be projected to the new instance
     * @param offset  from where in the segment to project the new instance
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with the provided segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if
     *         {@code offset > segment.byteSize() - layout().byteSize()}
     */
    @SuppressWarnings("unchecked")
    default T get(MemorySegment segment, long offset) {
        try {
            return (T) getHandle().invokeExact(segment, offset);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@return a new instance of type T projected from the provided external
     *          {@code segment} at the given {@code index} scaled by the
     *          {@code layout().byteSize()}}
     *
     * @param segment the external segment to be projected to the new instance
     * @param index a logical index, the offset in bytes (relative to the provided
     *              segment address) at which the access operation will occur can
     *              be expressed as {@code (index * layout().byteSize())}
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with the provided segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if {@code index * layout().byteSize()} overflows
     * @throws IndexOutOfBoundsException if
     *         {@code index * layout().byteSize() > segment.byteSize() - layout.byteSize()}
     */
    default T getAtIndex(MemorySegment segment, long index) {
        return get(segment, layout().byteSize() * index);
    }

    /**
     * Writes the provided instance {@code t} of type T into the provided {@code segment}
     * at offset zero.
     *
     * @param segment in which to write the provided {@code t}
     * @param t instance to write into the provided segment
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with this segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if {@code layout().byteSize() > segment.byteSize()}
     * @throws UnsupportedOperationException if this segment is
     *         {@linkplain MemorySegment#isReadOnly() read-only}
     * @throws UnsupportedOperationException if {@code value} is not a
     *         {@linkplain MemorySegment#isNative() native} segment
     */
    default void set(MemorySegment segment, T t) {
        set(segment, 0, t);
    }

    /**
     * Writes the provided instance {@code t} of type T into the provided {@code segment}
     * at the provided {@code offset}.
     *
     * @param segment in which to write the provided {@code t}
     * @param offset offset in bytes (relative to the provided segment address) at which
     *               this access operation will occur
     * @param t instance to write into the provided segment
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with this segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if {@code offset > segment.byteSize() - layout.byteSize()}
     * @throws UnsupportedOperationException if
     *         this segment is {@linkplain MemorySegment#isReadOnly() read-only}
     * @throws UnsupportedOperationException if
     *         {@code value} is not a {@linkplain MemorySegment#isNative() native} segment
     */
    default void set(MemorySegment segment, long offset, T t) {
        try {
            setHandle().invokeExact(segment, offset, t);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the provided {@code t} instance of type T into the provided {@code segment}
     * at the provided {@code index} scaled by the {@code layout().byteSize()}}.
     *
     * @param segment in which to write the provided {@code t}
     * @param index a logical index, the offset in bytes (relative to the provided
     *              segment address) at which the access operation will occur can be
     *              expressed as {@code (index * layout().byteSize())}
     * @param t instance to write into the provided segment
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with this segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if {@code offset > segment.byteSize() - layout.byteSize()}
     * @throws UnsupportedOperationException if this segment is
     *         {@linkplain MemorySegment#isReadOnly() read-only}
     * @throws UnsupportedOperationException if {@code value} is not a
     *         {@linkplain MemorySegment#isNative() native} segment
     */
    default void setAtIndex(MemorySegment segment, long index, T t) {
        set(segment, layout().byteSize() * index, t);
    }

    // Basic methods

    /**
     * {@return a method handle that returns new instances of type T projected from
     * a provided external {@code MemorySegment} at a provided {@code long} offset}
     * <p>
     * The returned method handle has the following characteristics:
     * <ul>
     *     <li>its return type is {@code T};</li>
     *     <li>it has a leading parameter of type {@code MemorySegment}
     *         corresponding to the memory segment to be accessed</li>
     *     <li>it has a trailing {@code long} parameter, corresponding to
     *         the base offset</li>
     * </ul>
     *
     * @see #get(MemorySegment, long)
     */
    MethodHandle getHandle();

    /**
     * {@return a method handle that writes a provided instance of type T into
     * a provided {@code MemorySegment} at a provided {@code long} offset}
     * <p>
     * The returned method handle has the following characteristics:
     * <ul>
     *     <li>its return type is void;</li>
     *     <li>it has a leading parameter of type {@code MemorySegment}
     *         corresponding to the memory segment to be accessed</li>
     *     <li>it has a following {@code long} parameter, corresponding to
     *         the base offset</li>
     *     <li>it has a trailing {@code T} parameter, corresponding to
     *         the value to set</li>
     * </ul>
     *
     * @see #set(MemorySegment, long, Object)
     */
    MethodHandle setHandle();

    /**
     * {@return a new segment mapper that would apply the provided {@code toMapper} after
     *          performing get operations on this segment mapper and that would apply the
     *          provided {@code fromMapper} before performing set operations on this
     *          segment mapper}
     * <p>
     * It should be noted that the type R can represent almost any class and is not
     * restricted to records and interfaces.
     *
     * @param toMapper to apply after get operations on this segment mapper
     * @param fromMapper to apply before set operations on this segment mapper
     * @param <R> the type of the new segment mapper
     */
    <R> SegmentMapper<R> map(Function<? super T, ? extends R> toMapper,
                             Function<? super R, ? extends T> fromMapper);

    /**
     * {@return a new segment mapper that would apply the provided {@code toMapper} after
     *          performing get operations on this segment mapper and that would throw an
     *          {@linkplain UnsupportedOperationException} for set operations if this
     *          segment mapper is a record mapper}
     * <p>
     * It should be noted that the type R can represent almost any class and is not
     * restricted to records and interfaces.
     *
     * @param toMapper to apply after get operations on this segment mapper
     * @param <R> the type of the new segment mapper
     */
    <R> SegmentMapper<R> map(Function<? super T, ? extends R> toMapper);

    /**
     * {@return a segment mapper that maps {@linkplain MemorySegment memory segments}
     *          to the provided interface {@code type} using the provided {@code layout}}
     * <p>
     * Reflective analysis on the provided {@code type} will be made using the
     * {@linkplain MethodHandles.Lookup#publicLookup() public lookup}.
     *
     * @param type to map memory segment from and to
     * @param layout to be used when mapping the provided {@code type}
     * @param <T> the type the returned mapper converts MemorySegments from and to
     * @throws IllegalArgumentException if the provided {@code type} is not an interface
     * @throws IllegalArgumentException if the provided {@code type} is a hidden interface
     * @throws IllegalArgumentException if the provided {@code type} is a sealed interface
     * @throws IllegalArgumentException if the provided interface {@code type} cannot be
     *        reflectively analysed using the
     *        {@linkplain MethodHandles.Lookup#publicLookup() public lookup}
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         methods for which there are no exact mapping (of names and types) in
     *         the provided {@code layout} or if the provided {@code type} is not public
     *         or if the method is otherwise unable to create a segment mapper as
     *         specified above.
     * @see #ofInterface(MethodHandles.Lookup, Class, GroupLayout)
     */
    static <T> SegmentMapper<T> ofInterface(Class<T> type,
                                            GroupLayout layout) {
        return ofInterface(MethodHandles.publicLookup(), type, layout);
    }

    /**
     * {@return a segment mapper that maps {@linkplain MemorySegment memory segments}
     *          to the provided interface {@code type} using the provided {@code layout}
     *          and using the provided {@code lookup}}
     *
     * @param lookup to use when performing reflective analysis on the
     *               provided {@code type}
     * @param type to map memory segment from and to
     * @param layout to be used when mapping the provided {@code type}
     * @param <T> the type the returned mapper converts MemorySegments from and to
     * @throws IllegalArgumentException if the provided {@code type} is not an interface
     * @throws IllegalArgumentException if the provided {@code type} is a hidden interface
     * @throws IllegalArgumentException if the provided {@code type} is a sealed interface
     * @throws IllegalArgumentException if the provided interface {@code type} cannot be
     *         reflectively analysed using the provided {@code lookup}
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         methods for which there are no exact mapping (of names and types) in
     *         the provided {@code layout} or if the provided {@code type} is not public or
     *         if the method is otherwise unable to create a segment mapper as specified above
     */
    static <T> SegmentMapper<T> ofInterface(MethodHandles.Lookup lookup,
                                            Class<T> type,
                                            GroupLayout layout) {
        Objects.requireNonNull(lookup);
        MapperUtil.requireImplementableInterfaceType(type);
        Objects.requireNonNull(layout);

        return new SegmentMapperImpl<>(lookup, type, layout);
    }

    /**
     * {@return a segment mapper that maps {@linkplain MemorySegment memory segments}
     *          to the provided record {@code type} using the provided {@code layout}}
     * <p>
     * Reflective analysis on the provided {@code type} will be made using the
     * {@linkplain MethodHandles.Lookup#publicLookup() public lookup}.
     *
     * @param type to map memory segment from and to
     * @param layout to be used when mapping the provided {@code type}
     * @param <T> the type the returned mapper converts MemorySegments from and to
     * @throws IllegalArgumentException if the provided interface {@code type} is
     *         {@linkplain java.lang.Record}
     * @throws IllegalArgumentException if the provided interface {@code type} cannot
     *         be reflectively analysed using
     *         the {@linkplain MethodHandles.Lookup#publicLookup() public lookup}
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         components for which there are no exact mapping (of names and types) in
     *         the provided {@code layout} or if the provided {@code type} is not public or
     *         if the method is otherwise unable to create a segment mapper as specified above
     * @see #ofRecord(MethodHandles.Lookup, Class, GroupLayout)
     */
    static <T extends Record> SegmentMapper<T> ofRecord(Class<T> type,
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
     * @throws IllegalArgumentException if the provided interface {@code type} is
     *         {@linkplain java.lang.Record}
     * @throws IllegalArgumentException if the provided interface {@code type} cannot
     *         be reflectively analysed using the provided {@code lookup}
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         components for which there are no exact mapping (of names and types) in
     *         the provided {@code layout} or if the provided {@code type} is not public or
     *         if the method is otherwise unable to create a segment mapper as specified above
     */
    static <T extends Record> SegmentMapper<T> ofRecord(MethodHandles.Lookup lookup,
                                                        Class<T> type,
                                                        GroupLayout layout) {
        Objects.requireNonNull(lookup);
        MapperUtil.requireRecordType(type);
        Objects.requireNonNull(layout);
        return new SegmentMapperImpl<>(lookup, type, layout);
    }

}
