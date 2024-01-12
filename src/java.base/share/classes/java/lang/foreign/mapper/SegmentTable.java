package java.lang.foreign.mapper;

import jdk.internal.foreign.mapper.MapperUtil;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SequenceLayout;
import java.lang.invoke.MethodHandles;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * A
 * @param <T> t
 */
public interface SegmentTable<T> {

    /**
     * {@return the type that this mapper is mapping to and from}
     */
    Class<T> type();

    /**
     * {@return the original {@link GroupLayout } that this mapper is using to map
     *          record components or interface methods}
     * <p>
     * Composed segment mappers (obtained via either the {@link SegmentMapper#map(Class, Function)}
     * or the {@link SegmentMapper#map(Class, Function, Function)} will still return the
     * group layout from the <em>original</em> SegmentMapper.
     */
    GroupLayout layout();

    /**
     * {@return the number of rows in this SegmentTable}
     */
    long size();

    /**
     * {@return a new instance of type T projected at the underlying
     *          segments at the given {@code index} row}
     *
     * @param index a logical row index
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with an underlying segments are not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such an underlying segment's {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if {@code index * layout().byteSize()} overflows
     *         for an underlying segment
     * @throws IndexOutOfBoundsException if
     *         {@code index > size()}
     */
    T get(long index);

    /**
     * Writes the provided {@code t} instance of type T into the underlying segments
     * at the provided {@code index} row}.
     *
     * @param index a logical row index
     * @param t instance to write into the underlying segments
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with an underlying segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that an underlying segment's {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link #layout()}
     * @throws IndexOutOfBoundsException if {@code index > size()}
     * @throws UnsupportedOperationException if an underlying segment is
     *         {@linkplain MemorySegment#isReadOnly() read-only}
     * @throws UnsupportedOperationException if {@code value} is not a
     *         {@linkplain MemorySegment#isNative() native} segment
     * @throws IllegalArgumentException if an array length does not correspond to the
     *         {@linkplain SequenceLayout#elementCount() element count} of a sequence layout
     */
    void set(long index, T t);

    /**
     * {@return a new sequential {@code Stream} of elements of type T}
     */
    default Stream<T> stream() {
        return LongStream.range(0, size())
                .mapToObj(this::get);
    }

    // Todo: Introspective stream or explicit operations?

    // Todo: Explicit where operations?

    /**
     * w
     * @param predicates p
     * @return s
     */
    default Stream<T> where(List<Predicate<? super T>> predicates) {

        @SuppressWarnings("unchecked")
        List<Predicate<T>> preds = (List<Predicate<T>>) (List<?>) predicates;
        Predicate<T> reduce = preds.stream()
                .skip(1)
                .reduce(preds.getFirst(), Predicate::and);

        return stream()
                .filter(reduce);
    }

    /**
     * o
     * @param comparators c
     * @return s
     */
    default Stream<T> orderBy(List<Comparator<? super T>> comparators) {

        @SuppressWarnings("unchecked")
        List<Comparator<T>> comps = (List<Comparator<T>>) (List<?>) comparators;
        Comparator<T> reduce = comps.stream()
                .skip(1)
                .reduce(comps.getFirst(), Comparator::thenComparing);

        return stream()
                .sorted(reduce);
    }

    // We might need both where and orderBy ...


    // Todo: join any number of tables
    // Todo: support different join types
    // Todo: Join on several keys and perhaps where ops

    /**
     * J
     * @param other o
     * @param mapper m
     * @return s
     * @param <R> r
     * @param <U> u
     */
    default <R, U>
    Stream<R> join(SegmentTable<U> other,
                   BiFunction<T, U, R> mapper) {
        return stream()
                .flatMap(t -> other.stream()
                        .map(u -> mapper.apply(t, u)));
    }

    /**
     * J
     * @param other o
     * @param mapper m
     * @param thisExtractor t
     * @param otherExtractor o
     * @param comparator c
     * @return s
     * @param <R> r
     * @param <U> u
     * @param <V> v
     */
    default <R, U, V extends Comparable<? super V>>
    Stream<R> join(SegmentTable<U> other,
                   BiFunction<? super T, ? super U, R> mapper,
                   Function<? super T, ? extends V> thisExtractor,
                   Function<? super U, ? extends V> otherExtractor,
                   Comparator<V> comparator) {
        return stream()
                .flatMap(t -> other.stream()
                        .filter(u -> comparator.compare(thisExtractor.apply(t), otherExtractor.apply(u)) == 0)
                        .map(u -> mapper.apply(t, u)));
    }

    /**
     * {@return the segments that backs this table}
     */
    Map<List<MemoryLayout.PathElement>, MemorySegment> segments();

    /**
     * {@return a Stencil that can be used to create SegmentTables that can map memory segments
     *          to the provided interface {@code type} using the provided {@code layout}
     *          and using the provided {@code lookup}}
     *
     * @implNote The order in which methods appear (e.g. in the {@code toString} method)
     *           is unspecified.
     *
     * @implNote The returned class can be a
     *           <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>
     *           class; programmers should treat instances that are
     *           {@linkplain Object#equals(Object) equal} as interchangeable and should
     *           not use instances for synchronization, or unpredictable behavior may
     *           occur. For example, in a future release, synchronization may fail.
     *
     * @param lookup to use when performing reflective analysis on the
     *               provided {@code type}
     * @param type to map memory segments from and to
     * @param layout to be used when mapping the provided {@code type}
     * @param <T> the type the returned mapper converts MemorySegments from and to
     * @throws IllegalArgumentException if the provided {@code type} is not an interface
     * @throws IllegalArgumentException if the provided {@code type} is a hidden interface
     * @throws IllegalArgumentException if the provided {@code type} is a sealed interface
     * @throws IllegalArgumentException if the provided interface {@code type} directly
     *         declares any generic type parameter
     * @throws IllegalArgumentException if the provided interface {@code type} cannot be
     *         reflectively analysed using the provided {@code lookup}
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         methods for which there are no exact mapping (of names and types) in
     *         the provided {@code layout} or if the provided {@code type} is not public or
     *         if the method is otherwise unable to create a segment mapper as specified above
     */
    static <T> Stencil<T> ofInterface(MethodHandles.Lookup lookup,
                                      Class<T> type,
                                      GroupLayout layout) {
        Objects.requireNonNull(lookup);
        MapperUtil.requireImplementableInterfaceType(type);
        Objects.requireNonNull(layout);
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a Stencil that can be used to create SegmentTables that can map memory segments
     *          to the provided record {@code type} using the provided {@code layout}
     *          and using the provided {@code lookup}}
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
    static <T extends Record> Stencil<T> ofRecord(Class<T> type,
                                                  GroupLayout layout) {
        return ofRecord(MethodHandles.publicLookup(), type, layout);
    }

    /**
     * {@return a Stencil that can be used to create SegmentTables that can map memory segments
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
    static <T extends Record> Stencil<T> ofRecord(MethodHandles.Lookup lookup,
                                                  Class<T> type,
                                                  GroupLayout layout) {
        Objects.requireNonNull(lookup);
        MapperUtil.requireRecordType(type);
        Objects.requireNonNull(layout);
        throw new UnsupportedOperationException();
    }

    /**
     * S
     * @param <T> t
     */
    interface Stencil<T> {

        /**
         * Enables internal indexing
         *
         * @return this stencil
         */
        Stencil<T> withIndices();

        /**
         * Enables read-only mode
         *
         * @return this stencil
         */
        Stencil<T> withReadOnlyAccess();

        /**
         * {@return a new segment table}
         * @param allocator a
         * @param rows r
         */
        SegmentTable<T> create(SegmentAllocator allocator, long rows);

        /**
         * {@return a new segment table}
         * @param allocator a
         * @param elements e
         */
        // Closes the stream
        // This method successively allocates fragments which are then merged to
        // coalesced column segment upon reaching the end of the stream
        SegmentTable<T> create(SegmentAllocator allocator, Stream<? extends T> elements);

        /**
         * {@return a new segment table}
         * @param segmentFactory s
         */
        // Column oriented
        SegmentTable<T> create(Function<List<MemoryLayout.PathElement>, MemorySegment> segmentFactory);

        /**
         * {@return a new segment table}
         * @param map s
         */
        SegmentTable<T> create(Map<List<MemoryLayout.PathElement>, MemorySegment> map);

        /**
         * {@return a new segment table}
         * @param map s
         */
        default SegmentTable<T> createFromFlat(Map<String, MemorySegment> map) {
            var m = map.entrySet().stream()
                    .map(e -> new AbstractMap.SimpleImmutableEntry<>(
                            List.of(MemoryLayout.PathElement.groupElement(e.getKey())),
                            e.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return create(m);
        }

    }

}
