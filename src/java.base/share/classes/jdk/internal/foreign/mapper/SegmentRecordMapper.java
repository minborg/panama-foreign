/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.foreign.mapper;

import jdk.internal.ValueBased;
import jdk.internal.util.ArraysSupport;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A record mapper that is matching components of a record with elements in a GroupLayout.
 *
 * @param <T> the Record type
 */
@ValueBased
public final class SegmentRecordMapper<T>
        implements SegmentMapper<T>, HasLookup {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();
    private static final MethodHandle SUM_LONG;

    static {
        try {
            SUM_LONG = PUBLIC_LOOKUP.findStatic(Long.class, "sum", MethodType.methodType(long.class, long.class, long.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final MethodHandles.Lookup lookup;
    @Stable
    private final Class<T> type;
    @Stable
    private final GroupLayout layout;
    private final long offset;
    private final int depth;
    @Stable
    private final boolean isExhaustive;
    // (MemorySegment, long)T
    @Stable
    private final MethodHandle getHandle;
    @Stable
    private final MethodHandle setHandle;

    public static <T> SegmentRecordMapper<T> create(MethodHandles.Lookup lookup,
                                                    Class<T> type,
                                                    GroupLayout layout) {
        return new SegmentRecordMapper<>(type, layout, 0, 0, lookup);
    }

    private SegmentRecordMapper(Class<T> type,
                                GroupLayout layout) {
        this(type, layout, 0L, 0, PUBLIC_LOOKUP);
    }

    private SegmentRecordMapper(Class<T> type,
                                GroupLayout layout,
                                long offset,
                                int depth,
                                MethodHandles.Lookup lookup) {
        this.type = type;
        this.layout = layout;
        this.offset = offset;
        this.depth = depth;
        this.lookup = lookup;

        assertMappingsCorrect();

        // For each component, find an f(a) = MethodHandle(MemorySegment) that returns the component type
        var handles = Arrays.stream(type.getRecordComponents())
                .map(this::methodHandle)
                .toList();

        Class<?>[] ctorParameterTypes = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);

        // There is exactly one member layout for each record component
        this.isExhaustive = layout.memberLayouts().size() == handles.size();

        MethodHandle ctor;
        try {
            ctor = lookup.findConstructor(type, MethodType.methodType(void.class, ctorParameterTypes));
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new IllegalArgumentException("There is no constructor in '" + type.getName() +
                    "' for " + Arrays.toString(ctorParameterTypes) + " using lookup " + lookup, e);
        }

        // (x,y,...)T -> ((MS, long)x, (MS, long)y, ...)T
        for (int i = handles.size() - 1; i >= 0; i--) {
            ctor = MethodHandles.collectArguments(ctor, i, handles.get(i));
        }

        var mt = MethodType.methodType(type, MemorySegment.class, long.class);

        // 0, 1, 0, 1, ...
        int[] reorder = IntStream.range(0, handles.size())
                .flatMap(i -> IntStream.rangeClosed(0, 1))
                .toArray();

        // Fold the many identical (MemorySegment, long) arguments into a single argument
        ctor = MethodHandles.permuteArguments(ctor, mt, reorder);
        if (depth == 0) {
            // This is the base level mh so, we need to cast to Object as the final
            // apply() method will do the final cast
            ctor = ctor.asType(MethodType.methodType(Object.class, MemorySegment.class, long.class));
        }
        // The constructor MethodHandle is now of type (MemorySegment, long)T unless it is
        // the one of depth zero when it is (MemorySegment, long)Object
        this.getHandle = ctor;
        this.setHandle = null;
    }

    private MethodHandle methodHandle(RecordComponent component) {

        var pathElement = MemoryLayout.PathElement.groupElement(component.getName());
        var componentLayout = layout().select(pathElement);
        var byteOffset = layout().byteOffset(pathElement) + offset;
        try {
            return switch (componentLayout) {
                case ValueLayout vl -> methodHandle(vl, component, byteOffset);
                case GroupLayout gl -> methodHandle(gl, component, byteOffset);
                case SequenceLayout sl -> methodHandle(sl, component, byteOffset);
                case PaddingLayout __ -> throw fail(component, componentLayout);
            };
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    private MethodHandle methodHandle(ValueLayout vl,
                                      RecordComponent component,
                                      long byteOffset) throws NoSuchMethodException, IllegalAccessException {

        assertTypesMatch(component, component.getType(), vl);
        var mt = MethodType.methodType(vl.carrier(), topValueLayoutType(vl), long.class);
        var mh = LOOKUP.findVirtual(MemorySegment.class, "get", mt);
        // (MemorySegment, OfX, long) -> (MemorySegment, long)
        mh = MethodHandles.insertArguments(mh, 1, vl);


        // (long, long)long -> (long)long
        MethodHandle sum = MethodHandles.insertArguments(SUM_LONG, 1, byteOffset);

        // (MemorySegment, long) -> (MemorySegment, long)

        return MethodHandles.filterArguments(mh, 1, sum);

        //return MethodHandles.collectArguments(mh,1, sum);

        //return MethodHandles.insertArguments(mh, 1, byteOffset);
    }

    private MethodHandle methodHandle(GroupLayout gl,
                                      RecordComponent component,
                                      long byteOffset) throws NoSuchMethodException, IllegalAccessException {
        if (type().equals(component.getType())) {
            throw new IllegalArgumentException(
                    "A type may not use a component of the same type: " + type() + " in " + gl);
        }
        // Simply return the raw MethodHandle of the recursively computed record mapper
        return recordMapper(component.getType(), gl, byteOffset).getHandle();
    }

    private MethodHandle methodHandle(SequenceLayout sl,
                                      RecordComponent component,
                                      long byteOffset) throws NoSuchMethodException, IllegalAccessException {

        String name = component.getName();
        var componentType = component.getType();
        if (!componentType.isArray()) {
            throw new IllegalArgumentException("Unable to map '" + sl +
                    "' because the component '" + componentType.getName() + " " + name + "' is not an array");
        }

        MultidimensionalSequenceLayoutInfo info = MultidimensionalSequenceLayoutInfo.of(sl, componentType);

        if (info.elementLayout() instanceof ValueLayout.OfBoolean) {
            throw new IllegalArgumentException("Arrays of booleans (" + info.elementLayout() + ") are not supported");
        }

        if (dimensionOf(componentType) != info.sequences().size()) {
            throw new IllegalArgumentException("Unable to map '" + sl + "'" +
                    " of dimension " + info.sequences().size() +
                    " because the component '" + componentType.getName() + " " + name + "'" +
                    " has a dimension of " + dimensionOf(componentType));
        }

        // Handle multi-dimensional arrays
        if (info.sequences().size() > 1) {
            var mh = LOOKUP.findStatic(SegmentRecordMapper.class, "toMultiArrayFunction",
                    MethodType.methodType(Object.class, MemorySegment.class, MultidimensionalSequenceLayoutInfo.class, long.class, Class.class, Function.class));
            // (MemorySegment, MultidimensionalSequenceLayoutInfo, long offset, Class leafType, Function mapper) ->
            // (MemorySegment, long offset, Class leafType, Function mapper)
            mh = MethodHandles.insertArguments(mh, 1, info);
            // (MemorySegment, long offset, Class leafType, Function mapper) ->
            // (MemorySegment, Class leafType, Function mapper)
            mh = MethodHandles.insertArguments(mh, 1, byteOffset);

            switch (info.elementLayout()) {
                case ValueLayout vl -> {
                    // (MemorySegment, Class leafType, Function mapper) ->
                    // (MemorySegment, Function mapper)
                    mh = MethodHandles.insertArguments(mh, 1, vl.carrier());
                    Function<MemorySegment, Object> leafArrayMapper =
                            switch (vl) {
                                case ValueLayout.OfByte ofByte -> ms -> ms.toArray(ofByte);
                                case ValueLayout.OfBoolean ofBoolean ->
                                        throw new UnsupportedOperationException("boolean arrays not supported: " + ofBoolean);
                                case ValueLayout.OfShort ofShort -> ms -> ms.toArray(ofShort);
                                case ValueLayout.OfChar ofChar -> ms -> ms.toArray(ofChar);
                                case ValueLayout.OfInt ofInt -> ms -> ms.toArray(ofInt);
                                case ValueLayout.OfLong ofLong -> ms -> ms.toArray(ofLong);
                                case ValueLayout.OfFloat ofFloat -> ms -> ms.toArray(ofFloat);
                                case ValueLayout.OfDouble ofDouble -> ms -> ms.toArray(ofDouble);
                                case AddressLayout addressLayout -> ms -> ms.elements(addressLayout)
                                        .map(s -> s.get(addressLayout, 0))
                                        .toArray(MemorySegment[]::new);
                            };
                    // (MemorySegment, Function mapper) ->
                    // (MemorySegment)
                    mh = MethodHandles.insertArguments(mh, 1, leafArrayMapper);
                    return castReturnType(mh, component.getType());
                }
                case GroupLayout gl -> {
                    var arrayComponentType = info.type();
                    // The "local" byteOffset for the record component mapper is zero
                    var componentMapper = recordMapper(arrayComponentType, gl, 0);
                    // Change the return type to Object so that we may use Array.set() below
                    var mapperCtor = componentMapper.getHandle()
                            .asType(MethodType.methodType(Object.class, MemorySegment.class));

                    Function<MemorySegment, Object> leafArrayMapper = ms ->
                            toArray(ms, gl, arrayComponentType, mapperCtor);

                    // (MemorySegment, Class leafType, Function mapper) ->
                    // (MemorySegment, Function mapper)
                    mh = MethodHandles.insertArguments(mh, 1, arrayComponentType);
                    // (MemorySegment, Function mapper) ->
                    // (MemorySegment)
                    mh = MethodHandles.insertArguments(mh, 1, leafArrayMapper);
                    return castReturnType(mh, component.getType());
                }
                case SequenceLayout __ -> {
                    throw new InternalError("Should not reach here");
                }
                case PaddingLayout __ -> throw fail(component, sl);
            }
        }

        // Faster single-dimensional arrays
        switch (info.elementLayout()) {
            case ValueLayout vl -> {
                assertTypesMatch(component, info.type(), vl);
                var mt = MethodType.methodType(vl.carrier().arrayType(),
                        MemorySegment.class, topValueLayoutType(vl), long.class, long.class);
                var mh = LOOKUP.findStatic(SegmentRecordMapper.class, "toArray", mt);
                // (MemorySegment, OfX, long offset, long count) -> (MemorySegment, OfX, long offset)
                mh = MethodHandles.insertArguments(mh, 3, info.sequences().getFirst().elementCount());
                // (MemorySegment, OfX, long offset) -> (MemorySegment, long offset)
                mh = MethodHandles.insertArguments(mh, 1, vl);
                // (MemorySegment, long offset) -> (MemorySegment)
                return castReturnType(MethodHandles.insertArguments(mh, 1, byteOffset), component.getType());
            }
            case GroupLayout gl -> {
                // The "local" byteOffset for the record component mapper is zero
                var componentMapper = recordMapper(info.type(), gl, 0);
                try {
                    var mt = MethodType.methodType(Object.class.arrayType(),
                            MemorySegment.class, GroupLayout.class, long.class, long.class, Class.class, MethodHandle.class);
                    var mh = LOOKUP.findStatic(SegmentRecordMapper.class, "toArray", mt);
                    var mapper = componentMapper.getHandle().asType(MethodType.methodType(Object.class, MemorySegment.class));
                    // (MemorySegment, GroupLayout, long offset, long count, Class, MethodHandle) ->
                    // (MemorySegment, GroupLayout, long offset, long count, Class)
                    mh = MethodHandles.insertArguments(mh, 5, mapper);
                    // (MemorySegment, GroupLayout, long offset, long count, Class) ->
                    // (MemorySegment, GroupLayout, long offset, long count)
                    mh = MethodHandles.insertArguments(mh, 4, componentMapper.type());
                    // (MemorySegment, GroupLayout, long offset, long count) ->
                    // (MemorySegment, GroupLayout, long offset)
                    mh = MethodHandles.insertArguments(mh, 3, info.sequences().getFirst().elementCount());
                    // (MemorySegment, GroupLayout, long offset) ->
                    // (MemorySegment, long offset)
                    mh = MethodHandles.insertArguments(mh, 1, gl);
                    // (MemorySegment, long offset) -> (MemorySegment)Record[]
                    mh = MethodHandles.insertArguments(mh, 1, byteOffset);
                    // (MemorySegment, long offset)Record[] -> (MemorySegment)componentType
                    return MethodHandles.explicitCastArguments(mh, MethodType.methodType(component.getType(), MemorySegment.class));
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            case SequenceLayout __ ->  throw new InternalError("Should not reach here");
            case PaddingLayout __ -> throw fail(component, sl);
        }
    }

    private IllegalArgumentException fail(RecordComponent component,
                                          MemoryLayout layout) {
        throw new IllegalArgumentException(
                "Unable to map " + layout + " to " + type.getName() + "." + component.getName());
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public GroupLayout layout() {
        return layout;
    }

    @Override
    public boolean isExhaustive() {
        return isExhaustive;
    }

    @Override
    public MethodHandle getHandle() {
        return getHandle;
    }

    @Override
    public MethodHandle setHandle() {
        return setHandle;
    }

    @Override
    public <R> SegmentMapper<R> map(Class<R> newType,
                                    Function<? super T, ? extends R> toMapper,
                                    Function<? super R, ? extends T> fromMapper) {
        return new Mapped<>(this, newType, toMapper, fromMapper);
    }

    @Override
    public MethodHandles.Lookup lookup() {
        return lookup;
    }

    /*
    @SuppressWarnings("unchecked")
    @Override
    public T apply(MemorySegment segment) {
        try {
            return (T) ctor.invokeExact(segment);
        } catch (Throwable e) {
            throw new IllegalArgumentException(
                    "Unable to invoke the canonical constructor of " + type.getName() +
                            " using " + segment, e);
        }
    }

    @Override
    public T apply(MemorySegment segment, long offset) {
        return offset == 0
                ? apply(segment)
                : apply(segment.asSlice(offset));
    }*/

    @Override
    public String toString() {
        return "LayoutRecordMapper{" +
                "type=" + type.getName() + ", " +
                "layout=" + layout + ", " +
                "offset=" + offset + "}";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SegmentRecordMapper<?> that &&
                offset == that.offset &&
                depth == that.depth &&
                Objects.equals(type, that.type) &&
                Objects.equals(layout, that.layout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, layout, offset, depth);
    }

    static Class<? extends ValueLayout> topValueLayoutType(ValueLayout vl) {
        // All the permitted implementations OfXImpl of the ValueLayout interfaces declare
        // its main top interface OfX as the sole interface (e.g. OfIntImpl implements only OfInt directly)
        return vl.getClass().getInterfaces()[0].asSubclass(ValueLayout.class);
    }

    void assertTypesMatch(RecordComponent component,
                          Class<?> recordComponentType,
                          ValueLayout vl) {

        if (!(recordComponentType == vl.carrier())) {
            throw new IllegalArgumentException("Unable to match types because the component '" +
                    component.getName() + "' (in " + type.getName() + ") has the type of '" + component.getType() +
                    "' but the layout carrier is '" + vl.carrier() + "' (in " + layout + ")");
        }
    }

    void assertMappingsCorrect() {
        var nameMappingCounts = layout.memberLayouts().stream()
                .map(MemoryLayout::name)
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // Make sure we have all components distinctly mapped
        for (RecordComponent component : type.getRecordComponents()) {
            String name = component.getName();
            switch (nameMappingCounts.getOrDefault(name, 0L).intValue()) {
                case 0 -> throw new IllegalArgumentException("No mapping for " +
                        type.getName() + "." + component.getName() +
                        " in layout " + layout);
                case 1 -> { /* Happy path */ }
                default -> throw new IllegalArgumentException("Duplicate mappings for " +
                        type.getName() + "." + component.getName() +
                        " in layout " + layout);
            }
        }
    }

    private <R> SegmentRecordMapper<R> recordMapper(Class<R> componentType,
                                                    GroupLayout gl,
                                                    long byteOffset) {

        return new SegmentRecordMapper<>(componentType, gl, byteOffset, depth + 1, lookup);
    }

    record MultidimensionalSequenceLayoutInfo(List<SequenceLayout> sequences,
                                              MemoryLayout elementLayout,
                                              Class<?> type){

        int[] dimensions() {
            return sequences().stream()
                    .mapToLong(SequenceLayout::elementCount)
                    .mapToInt(Math::toIntExact)
                    .toArray();
        }

        int firstDimension() {
            return (int) sequences().getFirst().elementCount();
        }

        int lastDimension() {
            return (int) sequences().getLast().elementCount();
        }

        long layoutByteSize() {
            return sequences()
                    .getFirst()
                    .byteSize();
        }

        MultidimensionalSequenceLayoutInfo removeFirst() {
            var removed = new ArrayList<>(sequences);
            removed.removeFirst();
            return new MultidimensionalSequenceLayoutInfo(removed, elementLayout, type);
        }

        static MultidimensionalSequenceLayoutInfo of(SequenceLayout sequenceLayout,
                                                     Class<?> arrayComponent) {
            MemoryLayout current = sequenceLayout;
            List<SequenceLayout> sequences = new ArrayList<>();
            while(true) {
                if (current instanceof SequenceLayout element) {
                    long count = element.elementCount();
                    if (count > ArraysSupport.SOFT_MAX_ARRAY_LENGTH) {
                        throw new IllegalArgumentException("Unable to accommodate '" + element + "' in an array.");
                    }
                    current = element.elementLayout();
                    sequences.add(element);
                } else {
                    return new MultidimensionalSequenceLayoutInfo(
                            List.copyOf(sequences), current, deepArrayComponentType(arrayComponent));
                }
            }
        }

        private static Class<?> deepArrayComponentType(Class<?> arrayType) {
            Class<?> recordComponentType = arrayType;
            while (recordComponentType.isArray()) {
                recordComponentType = Objects.requireNonNull(recordComponentType.componentType());
            }
            return recordComponentType;
        }

    }

    // Provide widening and boxing magic
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

        return MethodHandles.explicitCastArguments(mh, MethodType.methodType(to, MemorySegment.class));
    }

    static int dimensionOf(Class<?> arrayClass) {
        return (int) Stream.<Class<?>>iterate(arrayClass, Class::isArray, Class::componentType)
                .count();
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
                        return (R) mapper.invokeExact(s);
                    } catch (Throwable t) {
                        throw new IllegalArgumentException(t);
                    }

                })
                .toArray(s -> (R[]) Array.newInstance(type, Math.toIntExact(s)));
    }

    // Below are `MemorySegment::toArray` wrapper methods that is also taking an offset
    // Begin: Reflectively used methods

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


    static final class Mapped<T, R, O extends SegmentMapper<T> & HasLookup>
            implements SegmentMapper<R>, HasLookup {

        @Stable
        private final O original;
        @Stable
        private final Class<R> newType;
        @Stable
        private final Function<? super T, ? extends R> toMapper;
        @Stable
        private final Function<? super R, ? extends T> fromMapper;
        @Stable
        private final MethodHandle getHandle;
        @Stable
        private final MethodHandle setHandle;

        Mapped(O original,
               Class<R> newType,
               Function<? super T, ? extends R> toMapper,
               Function<? super R, ? extends T> fromMapper) {

            this.newType = newType;
            this.original = original;
            this.toMapper = toMapper;
            this.fromMapper = fromMapper;
            MethodHandle toMh = findVirtual("mapTo", original.type(), newType);
            getHandle = MethodHandles.filterReturnValue(original.getHandle(), toMh);
            MethodHandle fromMh = findVirtual("mapFrom", newType, original.type());
            setHandle = MethodHandles.filterReturnValue(original.setHandle(), fromMh);
        }

        @Override
        public Class<R> type() {
            return newType;
        }

        @Override
        public GroupLayout layout() {
            return original.layout();
        }

        @Override
        public boolean isExhaustive() {
            // There is no way to evaluate exhaustiveness
            return false;
        }

        @Override
        public MethodHandle getHandle() {
            return getHandle;
        }

        @Override
        public MethodHandle setHandle() {
            return setHandle;
        }

        @Override
        public <R1> SegmentMapper<R1> map(Class<R1> newType,
                                          Function<? super R, ? extends R1> toMapper,
                                          Function<? super R1, ? extends R> fromMapper) {
            return new Mapped<>(this, newType, toMapper, fromMapper);
        }

        @Override
        public MethodHandles.Lookup lookup() {
            return original.lookup();
        }

        // Used reflective when obtaining a MethodHandle
        R mapTo(T t) {
            return toMapper.apply(t);
        }

        // Used reflective when obtaining a MethodHandle
        T mapFrom(R r) {
            return fromMapper.apply(r);
        }

        static MethodHandle findVirtual(String name,
                                        Class<?> from,
                                        Class<?> to) {
            try {
                return LOOKUP.findVirtual(Mapped.class, name, MethodType.methodType(to, from));
            } catch (ReflectiveOperationException e) {
                // Should not happen
                throw new InternalError(e);
            }
        }

    }

}