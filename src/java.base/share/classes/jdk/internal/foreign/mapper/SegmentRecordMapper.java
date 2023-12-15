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
import jdk.internal.foreign.mapper.component.ComponentHandle;
import jdk.internal.foreign.mapper.component.Util;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A record mapper that is matching components of a record with elements in a GroupLayout.
 *
 * @param lookup       to use for reflective operations
 * @param type         record type to map to/from
 * @param layout       group layout to use for matching record components
 * @param getHandle    for get operations
 * @param setHandle    for set operations
 * @param <T>          mapper type
 */
// Records have trusted instance fields.
// Todo: Make this a regular class
@ValueBased
public record SegmentRecordMapper<T extends Record>(
            @Override MethodHandles.Lookup lookup,
            @Override Class<T> type,
            @Override GroupLayout layout,
            long offset,
            int depth,
            // (MemorySegment, long)T
            @Override MethodHandle getHandle,
            // (MemorySegment, long, T)void
            @Override MethodHandle setHandle) implements SegmentMapper<T>, HasLookup {

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();

    public SegmentRecordMapper(MethodHandles.Lookup lookup,
                               Class<T> type,
                               GroupLayout layout,
                               long offset,
                               int depth) {
        this(lookup, type, layout, offset, depth, null, null);
    }

    // Canonical constructor in which we ignore some of the
    // input values and derive them internally instead.
    public SegmentRecordMapper(MethodHandles.Lookup lookup,
                               Class<T> type,
                               GroupLayout layout,
                               long offset,
                               int depth,
                               MethodHandle getHandle,    // Ignored
                               MethodHandle setHandle) {  // Ignored
        this.lookup = lookup;
        this.type = MapperUtil.requireRecordType(type);
        this.layout = layout;
        this.offset = offset;
        this.depth = depth;
        Handles handles = handles(this);
        this.getHandle = handles.getHandle();
        this.setHandle = handles.setHandle();
    }

    @Override
    public <R> SegmentMapper<R> map(Class<R> newType,
                                    Function<? super T, ? extends R> toMapper,
                                    Function<? super R, ? extends T> fromMapper) {
        // return new Mapped<>(this, newType, toMapper, fromMapper);
        return Mapped.of(this, newType, toMapper, fromMapper);
    }


    // Private methods and classes

    // This method is using a partially initialized mapper
    private static <T extends Record> Handles handles(SegmentRecordMapper<T> mapper) {
        assertMappingsCorrect(mapper.type(), mapper.layout());

        // The types for the constructor/components
        Class<?>[] componentTypes = Arrays.stream(mapper.type().getRecordComponents())
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);

        // (MemorySegment, long)Object
        MethodHandle getHandle = computeGetHandle(mapper, componentTypes);

        // (MemorySegment, long, T)void
        MethodHandle setHandle = computeSetHandle(mapper, componentTypes);

        return new Handles(getHandle, setHandle);
    }

    // (MemorySegment, long)Object
    private static <T extends Record> MethodHandle computeGetHandle(SegmentRecordMapper<T> mapper,
                                                                    Class<?>[] componentTypes) {

        ComponentHandle<T> getComponentHandle =
                ComponentHandle.ofGet(mapper.lookup(), mapper.type(), mapper.layout(), mapper.offset());

        // For each component, find an f(a) = MethodHandle(MemorySegment, long) that returns the component type
        List<MethodHandle> getHandles = Arrays.stream(mapper.type().getRecordComponents())
                .map(getComponentHandle::handle)
                .toList();

        MethodHandle ctor;
        try {
            ctor = mapper.lookup().findConstructor(mapper.type(), MethodType.methodType(void.class, componentTypes));
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new IllegalArgumentException("There is no constructor in '" + mapper.type().getName() +
                    "' for " + Arrays.toString(componentTypes) + " using lookup " + mapper.lookup(), e);
        }

        // (x,y,...)T -> ((MS, long)x, (MS, long)y, ...)T
        for (int i = getHandles.size() - 1; i >= 0; i--) {
            ctor = MethodHandles.collectArguments(ctor, i, getHandles.get(i));
        }

        var mt = Util.GET_TYPE.changeReturnType(mapper.type());

        // 0, 1, 0, 1, ...
        int[] reorder = IntStream.range(0, getHandles.size())
                .flatMap(_ -> IntStream.rangeClosed(0, 1))
                .toArray();

        // Fold the many identical (MemorySegment, long) arguments into a single argument
        ctor = MethodHandles.permuteArguments(ctor, mt, reorder);
        if (mapper.depth() == 0) {
            // This is the base level mh so, we need to cast to Object as the final
            // apply() method will do the final cast
            ctor = ctor.asType(Util.GET_TYPE);
        }
        // The constructor MethodHandle is now of type (MemorySegment, long)T unless it is
        // the one of depth zero when it is (MemorySegment, long)Object
        return ctor;
    }

    // (MemorySegment, long, T)void
    private static <T extends Record> MethodHandle computeSetHandle(SegmentRecordMapper<T> mapper,
                                                                    Class<?>[] componentTypes) {
        // for each component, extracts its value and write to the correct location

        ComponentHandle<T> setComponentHandle =
                ComponentHandle.ofSet(mapper.lookup(), mapper.type(), mapper.layout(), mapper.offset());

        List<MethodHandle> setHandles = Arrays.stream(mapper.type().getRecordComponents())
                .map(setComponentHandle::handle)
                .toList();

        return switch (setHandles.size()) {
            case 0 -> Util.SET_NO_OP;
            case 1 -> setHandles.getFirst();
            case 2, 3, 4, 5, 6, 7 -> compose(setHandles);
            default -> iterate(setHandles);
        };
    }

    // Creates a new MH where the sub-method handles are composed to a single MH
    private static MethodHandle compose(SequencedCollection<MethodHandle> setHandles) {
        return setHandles.stream()
                .skip(1)
                .reduce(setHandles.getFirst(),
                        SegmentRecordMapper::accumulate,
                        SegmentRecordMapper::accumulate);
    }

    // Merges two set operations into a single one by composition
    private static MethodHandle accumulate(MethodHandle a, MethodHandle b) {
        // (MemorySegment, long, Object)void -> (MemorySegment, long, Object, MemorySegment, long, Object)void
        // NB: collectArguments with void return types will compose
        MethodHandle result = MethodHandles.collectArguments(a, 0, b);
        // De-duplicate the arguments
        return MethodHandles.permuteArguments(result, Util.SET_TYPE, 0, 1, 2, 0, 1, 2);
    }

    // Creates a new MH that will iterate over the sub-method handles
    private static MethodHandle iterate(SequencedCollection<MethodHandle> setHandles) {
        try {
            var mh = LOCAL_LOOKUP.findStatic(SegmentRecordMapper.class,
                    "doSetOperations",
                    Util.SET_TYPE.appendParameterTypes(SequencedCollection.class));
            return MethodHandles.insertArguments(mh, 3, setHandles);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    // Helper method for the iterate() method
    // Used reflectively
    private static void doSetOperations(MemorySegment segment,
                                        long offset,
                                        Object t,
                                        SequencedCollection<MethodHandle> setHandles) {
        for (var setHandle : setHandles) {
            try {
                setHandle.invokeExact(segment, offset, t);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }
    }

    private static void assertMappingsCorrect(Class<?> type, GroupLayout layout) {
        var nameMappingCounts = layout.memberLayouts().stream()
                .map(MemoryLayout::name)
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        if (!type.isRecord()) {
            throw new IllegalArgumentException("Not a record class: " + type);
        }

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

    /**
     * This class models composed record mappers.
     *
     * @param lookup       to use for reflective operations
     * @param type         new type to map to/from
     * @param layout       original layout
     * @param getHandle    for get operations
     * @param setHandle    for set operations
     * @param toMapper     a function that goes from T to R
     * @param fromMapper   a function that goes from R to T
     * @param <T>          original mapper type
     * @param <R>          composed mapper type
     */
    // Records have trusted instance fields.
    @ValueBased
    record Mapped<T, R> (
            @Override MethodHandles.Lookup lookup,
            @Override Class<R> type,
            @Override GroupLayout layout,
            @Override MethodHandle getHandle,
            @Override MethodHandle setHandle,
            Function<? super T, ? extends R> toMapper,
            Function<? super R, ? extends T> fromMapper
    ) implements SegmentMapper<R>, HasLookup {

        Mapped(MethodHandles.Lookup lookup,
               Class<R> type,
               GroupLayout layout,
               MethodHandle getHandle,
               MethodHandle setHandle,
               Function<? super T, ? extends R> toMapper,
               Function<? super R, ? extends T> fromMapper
        ) {
            this.lookup = lookup;
            this.type = type;
            this.layout = layout;
            this.toMapper = toMapper;
            this.fromMapper = fromMapper;
            MethodHandle toMh = findVirtual("mapTo").bindTo(this);
            this.getHandle = MethodHandles.filterReturnValue(getHandle, toMh);
            MethodHandle fromMh = findVirtual("mapFrom").bindTo(this);
            if (setHandle!=null) {
                this.setHandle = MethodHandles.filterArguments(setHandle, 2, fromMh);
            } else {
                this.setHandle = null;
            }
        }

        @Override
        public <R1> SegmentMapper<R1> map(Class<R1> newType,
                                          Function<? super R, ? extends R1> toMapper,
                                          Function<? super R1, ? extends R> fromMapper) {
            return of(this, newType, toMapper, fromMapper);
        }

        // Used reflective when obtaining a MethodHandle
        R mapTo(T t) {
            return toMapper.apply(t);
        }

        // Used reflective when obtaining a MethodHandle
        T mapFrom(R r) {
            return fromMapper.apply(r);
        }

        static <T, R, O extends SegmentMapper<T> & HasLookup> Mapped<T, R> of(
                O original,
                Class<R> newType,
                Function<? super T, ? extends R> toMapper,
                Function<? super R, ? extends T> fromMapper) {

            Objects.requireNonNull(original);
            Objects.requireNonNull(newType);
            Objects.requireNonNull(toMapper);
            Objects.requireNonNull(fromMapper);

            return new Mapped<>(original.lookup(),
                        newType,
                        original.layout(),
                        original.getHandle(),
                        original.setHandle(),
                        toMapper,
                        fromMapper
                );
        }

        private static MethodHandle findVirtual(String name) {
            try {
                var mt = MethodType.methodType(Object.class, Object.class);
                return LOCAL_LOOKUP.findVirtual(Mapped.class, name, mt);
            } catch (ReflectiveOperationException e) {
                // Should not happen
                throw new InternalError(e);
            }
        }

    }

}