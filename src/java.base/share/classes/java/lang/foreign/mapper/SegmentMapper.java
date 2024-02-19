package java.lang.foreign.mapper;

import jdk.internal.foreign.mapper.MapperUtil;
import jdk.internal.foreign.mapper.SegmentRecordMapper2;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A segment mapper can project memory segment onto and from class instances.
 * <p>
 * More specifically, a segment mapper can project a backing
 * {@linkplain MemorySegment MemorySegment} into new {@link Record} instances
 * that implements an interface by means of matching the names of the record
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
 * SegmentMapper<NarrowedPoint> narrowedPointMapper =
 *         SegmentMapper.ofRecord(Point.class, POINT)              // SegmentMapper<Point>
 *         .map(NarrowedPoint.class, NarrowedPoint::fromPoint, NarrowedPoint::toPoint); // SegmentMapper<NarrowedPoint>
 *
 * // Extracts a new NarrowedPoint from the provided MemorySegment
 * NarrowedPoint narrowedPoint = narrowedPointMapper.get(segment); // NarrowedPoint[x=3, y=4]
 * }
 *
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
 * @param <T> the type this mapper converts MemorySegments from and to.
 *
 * @implSpec Implementations of this interface are immutable, thread-safe and
 *           <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 23
 */


// Todo: How do we handle "extra" setters for interfaces? They should not append

// Cerializer
// Todo: Check all exceptions in JavaDocs: See TestScopedOperations
// Todo: Consider generating a graphics rendering.
// Todo: Add in doc that getting via an AddressValue will return a MS managed by Arena.global()
// Todo: Provide safe sharing across threads (e.g. implement a special Interface with piggybacking/volatile access)
// Todo: Prevent several variants in a record from being mapped to a union (otherwise, which will "win" when writing?)
// Todo: There seams to be a problem with the ByteOrder in the mapper. See TestJepExamplesUnions
// Todo: Let SegmentMapper::getHandle and ::setHandle return the sharp types (e.g. Point) see MethodHandles::exactInvoker

// Done: The generated interface classes should be @ValueBased
// Done: Python "Pandas" (tables), Tabular access from array, Joins etc. <- TEST
//       -> See TestDataProcessingRecord and TestDataProcessingInterface
// No: ~map() can be dropped in favour of "manual mapping"~
// Done: Interfaces with internal segments should be directly available via separate factory methods
//       -> Fixed via SegmentMapper::create
// Done: Discuss if an exception is thrown in one of the sub-setters... This means partial update of the MS
//       This can be fixed using double-buffering. Maybe provide a scratch segment somehow that tracks where writes
//       has been made (via a separate class BufferedMapper?)
//       -> Fixed via TestInterfaceMapper::doubleBuffered
// No:   Map components to MemorySegment (escape hatch). Records should be immutable and not connected. Maybe we could
//       create a copy of a segment with the same life cycle?
public interface SegmentMapper<T> {

    /**
     * {@return the type that this mapper is mapping to and from}
     */
    Class<T> type();

    /**
     * {@return the original {@link GroupLayout } that this mapper is using to map
     *          record components}
     * <p>
     * Composed segment mappers (obtained via either the {@link SegmentMapper#map(Class, Function)}
     * or the {@link SegmentMapper#map(Class, Function, Function)} will still return the
     * group layout from the <em>original</em> SegmentMapper.
     */
    GroupLayout layout();

    // Convenience methods

    /**
     * {@return a new instance of type T projected at the provided
     *          external {@code segment} at offset zero}
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang = java:
     *    get(segment, 0L);
     * }
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
        return get(segment, 0L);
    }

    /**
     * {@return a new instance of type T projected at the provided external
     *          {@code segment} at the given {@code index} scaled by the
     *          {@code layout().byteSize()}}
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang = java:
     *    get(segment, layout().byteSize() * index);
     * }
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
     * {@return a new sequential {@code Stream} of elements of type T}
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang=java :
     * segment.elements(layout())
     *     .map(this::get);
     * }
     * @param segment to carve out instances from
     * @throws IllegalArgumentException if {@code layout().byteSize() == 0}.
     * @throws IllegalArgumentException if {@code segment.byteSize() % layout().byteSize() != 0}.
     * @throws IllegalArgumentException if {@code layout().byteSize() % layout().byteAlignment() != 0}.
     * @throws IllegalArgumentException if this segment is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the
     *         alignment constraint</a> in the layout of this segment mapper.
     */
    default Stream<T> stream(MemorySegment segment) {
        return segment.elements(layout())
                .map(this::get);
    }

    /**
     * {@return a new sequential {@code Stream} of {@code pageSize} elements of
     *          type T starting at the element {@code pageNumber * pageSize}}
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang=java :
     * stream(segment)
     *     .skip(pageNumber * pageSize)
     *     .limit(pageSize);
     * }
     * but may be much more efficient for large page numbers.
     *
     * @param segment    to carve out instances from
     * @param pageSize   the size of each page
     * @param pageNumber the page number to which to skip
     * @throws IllegalArgumentException if {@code layout().byteSize() == 0}.
     * @throws IllegalArgumentException if {@code segment.byteSize() % layout().byteSize() != 0}.
     * @throws IllegalArgumentException if {@code layout().byteSize() % layout().byteAlignment() != 0}.
     * @throws IllegalArgumentException if this segment is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the
     *         alignment constraint</a> in the layout of this segment mapper.
     */
    default Stream<T> page(MemorySegment segment,
                           long pageSize,
                           long pageNumber) {
        long skipBytes = Math.min(segment.byteSize(), layout().scale(0, pageNumber * pageSize));
        MemorySegment skippedSegment = segment.asSlice(skipBytes);
        return stream(skippedSegment)
                .limit(pageSize);
    }

    /**
     * {@return a new instance of type T projected from at provided
     *          external {@code segment} at the provided {@code offset}}
     *
     * @param segment the external segment to be projected at the new instance
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
            return (T) getHandle()
                    .invokeExact(segment, offset);
        } catch (NullPointerException |
                 IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke getHandle() with " +
                    "segment="  + segment +
                    ", offset=" + offset, e);
        }
    }

    /**
     * Writes the provided instance {@code t} of type T into the provided {@code segment}
     * at offset zero.
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang = java:
     *    set(segment, 0L, t);
     * }
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
     * @throws IllegalArgumentException if an array length does not correspond to the
     *         {@linkplain SequenceLayout#elementCount() element count} of a sequence layout
     * @throws NullPointerException if a required parameter is {@code null}
     */
    default void set(MemorySegment segment, T t) {
        set(segment, 0L, t);
    }

    /**
     * Writes the provided {@code t} instance of type T into the provided {@code segment}
     * at the provided {@code index} scaled by the {@code layout().byteSize()}}.
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang = java:
     *    set(segment, layout().byteSize() * index, t);
     * }
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
     * @throws IllegalArgumentException if an array length does not correspond to the
     *         {@linkplain SequenceLayout#elementCount() element count} of a sequence layout
     * @throws NullPointerException if a required parameter is {@code null}
     */
    default void setAtIndex(MemorySegment segment, long index, T t) {
        set(segment, layout().byteSize() * index, t);
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
     *         {@code value} is not a {@linkplain MemorySegment#isNative() native} segment // Todo: only for pointers
     * @throws IllegalArgumentException if an array length does not correspond to the
     *         {@linkplain SequenceLayout#elementCount() element count} of a sequence layout
     * @throws NullPointerException if a required parameter is {@code null}
     */
    default void set(MemorySegment segment, long offset, T t) {
        try {
            setHandle()
                    .invokeExact(segment, offset, (Object) t);
        } catch (IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException |
                 UnsupportedOperationException |
                 NullPointerException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke setHandle() with " +
                    "segment=" + segment +
                    ", offset=" + offset +
                    ", t=" + t, e);
        }
    }

    // Basic methods

    /**
     * {@return a method handle that returns new instances of type T projected at
     *          a provided external {@code MemorySegment} at a provided {@code long} offset}
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
     *          a provided {@code MemorySegment} at a provided {@code long} offset}
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
     * restricted to records.
     *
     * @param  newType the new type the returned mapper shall use
     * @param toMapper to apply after get operations on this segment mapper
     * @param fromMapper to apply before set operations on this segment mapper
     * @param <R> the type of the new segment mapper
     * @throws UnsupportedOperationException if this is an interface mapper.
     */
    <R> SegmentMapper<R> map(Class<R> newType,
                             Function<? super T, ? extends R> toMapper,
                             Function<? super R, ? extends T> fromMapper);

    /**
     * {@return a new segment mapper that would apply the provided {@code toMapper} after
     *          performing get operations on this segment mapper and that would throw an
     *          {@linkplain UnsupportedOperationException} for set operations if this
     *          segment mapper is a record mapper}
     * <p>
     * It should be noted that the type R can represent almost any class and is not
     * restricted to records.
     *
     * @param  newType the new type the returned mapper shall use
     * @param toMapper to apply after get operations on this segment mapper
     * @param <R> the type of the new segment mapper
     */
    <R> SegmentMapper<R> map(Class<R> newType,
                             Function<? super T, ? extends R> toMapper);


    /**
     * {@return a segment mapper that maps {@linkplain MemorySegment memory segments}
     *          to the provided record {@code type} using the natural layout of {@code type}}
     * <p>
     * Reflective analysis on the provided {@code type} will be made using the
     * {@linkplain MethodHandles.Lookup#publicLookup() public lookup}.
     * <p>
     * The natural layout will be computed as {@linkplain MemoryLayout#naturalLayout(Class)} was
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
     *         the {@linkplain MethodHandles.Lookup#publicLookup() public lookup}
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         components for which there are no exact mapping (of names and types) in
     *         the provided {@code layout} or if the provided {@code type} is not public or
     *         if the method is otherwise unable to create a segment mapper as specified above
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         components for which there are no natural layout (e.g. arrays)
     * @see #ofRecord(MethodHandles.Lookup, Class, GroupLayout)
     */
    static <T extends Record> SegmentMapper<T> ofRecord(Class<T> type) {
        return ofRecord(MethodHandles.publicLookup(), type, MemoryLayout.naturalLayout(type));
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
     * @throws IllegalArgumentException if the provided record {@code type} directly
     *         declares any generic type parameter
     * @throws IllegalArgumentException if the provided record {@code type} is
     *         {@linkplain java.lang.Record}
     * @throws IllegalArgumentException if the provided record {@code type} cannot
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
    static <T extends Record> SegmentMapper<T> ofRecord(MethodHandles.Lookup lookup,
                                                        Class<T> type,
                                                        GroupLayout layout) {
        Objects.requireNonNull(lookup);
        MapperUtil.requireRecordType(type);
        Objects.requireNonNull(layout);
        return SegmentRecordMapper2.create(lookup, type, layout);
    }

}
