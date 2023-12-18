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
import jdk.internal.classfile.ClassHierarchyResolver;
import jdk.internal.foreign.mapper.MethodInfo.AccessorType;
import jdk.internal.foreign.mapper.MethodInfo.Key;
import jdk.internal.foreign.mapper.component.Util;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static jdk.internal.classfile.Classfile.*;
import static jdk.internal.foreign.layout.MemoryLayoutUtil.requireNonNegative;

/**
 * A record mapper that is matching components of an interface with elements in a GroupLayout.
 */
@ValueBased
public final class SegmentInterfaceMapper<T> implements SegmentMapper<T>, HasLookup {

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();

    private final MethodHandles.Lookup lookup;
    private final Class<T> type;
    private final GroupLayout layout;
    private final Class<T> implClass;
    private final MethodHandle getHandle;
    private final MethodHandle setHandle;
    private final MethodHandle segmentGetHandle;
    private final MethodHandle offsetGetHandle;
    private final List<AffectedMemory> affectedMemories;
    private final MapperCache mapperCache;

    private SegmentInterfaceMapper(MethodHandles.Lookup lookup,
                                   Class<T> type,
                                   GroupLayout layout,
                                   List<AffectedMemory> affectedMemories) {
        this.lookup = lookup;
        this.type = MapperUtil.requireImplementableInterfaceType(type);
        this.layout = layout;
        this.affectedMemories = affectedMemories;
        this.mapperCache = MapperCache.of(lookup);
        Accessors accessors = Accessors.of(type, layout);

        // Add affected memory for all the setters seen on this level
        accessors.stream(AccessorType.SETTER)
                .map(AffectedMemory::from)
                .forEach(affectedMemories::add);

        List<MethodInfo> interfaceSetters = accessors.stream(Set.of(Key.SCALAR_INTERFACE_SETTER, Key.ARRAY_INTERFACE_SETTER))
                .toList();
        if (!interfaceSetters.isEmpty()) {
            throw new IllegalArgumentException("Setters cannot take an interface as a parameter: " + interfaceSetters);
        }
        assertMappingsCorrectAndTotal(type, layout, accessors);
        this.implClass = generateClass(accessors);
        this.getHandle = computeGetHandle();
        this.setHandle = computeSetHandle();

        try {
            this.segmentGetHandle = lookup.unreflect(implClass.getMethod(MapperUtil.SECRET_SEGMENT_METHOD_NAME))
                    .asType(MethodType.methodType(MemorySegment.class, Object.class));
            this.offsetGetHandle = lookup.unreflect(implClass.getMethod(MapperUtil.SECRET_OFFSET_METHOD_NAME))
                    .asType(MethodType.methodType(long.class, Object.class));;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MethodHandles.Lookup lookup() {
        return lookup;
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
    public MethodHandle getHandle() {
        return getHandle;
    }

    @Override
    public MethodHandle setHandle() {
        return setHandle;
    }

    @Override
    public Optional<MemorySegment> segment(T source) {
        if (isImplClass(source)) {
            try {
                return Optional.of((MemorySegment) segmentGetHandle.invokeExact(source));
            } catch (Throwable _) {
            }
        }
        return Optional.empty();
    }

    @Override
    public OptionalLong offset(T source) {
        if (isImplClass(source)) {
            try {
                return OptionalLong.of((long) offsetGetHandle.invokeExact(source));
            } catch (Throwable _) {
            }
        }
        return OptionalLong.empty();
    }

    List<AffectedMemory> affectedMemories() {
        return affectedMemories;
    }

    boolean isImplClass(T source) {
        // Implicit null check of source
        return implClass == source.getClass();
    }

    @Override
    public String toString() {
        return "SegmentInterfaceMapper[" +
                "lookup=" + lookup + ", " +
                "type=" + type + ", " +
                "layout=" + layout + ", " +
                "implClass=" + implClass + ", " +
                "memoryChunks = " + affectedMemories.size() + ", " +
                "getHandle=" + getHandle + ", " +
                "setHandle=" + setHandle + ']';
    }

    @Override
    public <R> SegmentMapper<R> map(Class<R> newType, Function<? super T, ? extends R> toMapper) {
        return Mapped.of(this, newType, toMapper);
    }

    @Override
    public <R> SegmentMapper<R> map(Class<R> newType,
                                    Function<? super T, ? extends R> toMapper,
                                    Function<? super R, ? extends T> fromMapper) {
        throw twoWayMappersUnsupported();
    }

    private Class<T> generateClass(Accessors accessors) {
        ClassDesc classDesc = ClassDesc.of(type.getSimpleName() + "InterfaceMapper");
        ClassLoader loader = type.getClassLoader();

        // We need to materialize these methods so that the order is preserved
        // during generation of the class.
        List<MethodInfo> virtualMethods = accessors.stream()
                .filter(mi -> mi.key().valueType().isVirtual())
                .toList();

        byte[] bytes = of(ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofClassLoading(loader)))
                .build(classDesc, cb -> {
                    ByteCodeGenerator generator = ByteCodeGenerator.of(type, cb);

                    // public final XxInterfaceMapper implements Xx {
                    //     private final MemorySegment segment;
                    //     private final long offset;
                    generator.classDefinition();

                    // void XxInterfaceMapper(MemorySegment segment, long offset) {
                    //    this.segment = segment;
                    //    this.offset = offset;
                    // }
                    generator.constructor();

                    // MemorySegment $_$_$sEgMeNt$_$_$() {
                    //     return segment;
                    // }
                    generator.obscuredSegment();

                    // long $_$_$oFfSeT$_$_$() {
                    //     return offset;
                    // }
                    generator.obscuredOffset();

                    // @Override
                    // <t> gX(c1, c2, ..., cN) {
                    //     long indexOffset = f(dimensions, c1, c2, ..., long cN);
                    //     return segment.get(JAVA_t, offset + elementOffset + indexOffset);
                    // }
                    accessors.stream(Set.of(Key.SCALAR_VALUE_GETTER, Key.ARRAY_VALUE_GETTER))
                            .forEach(generator::valueGetter);

                    // @Override
                    // void gX(c1, c2, ..., cN, <t> t) {
                    //     long indexOffset = f(dimensions, c1, c2, ..., long cN);
                    //     segment.set(JAVA_t, offset + elementOffset + indexOffset, t);
                    // }
                    accessors.stream(Set.of(Key.SCALAR_VALUE_SETTER, Key.ARRAY_VALUE_SETTER))
                            .forEach(generator::valueSetter);

                    for (int i = 0; i < virtualMethods.size(); i++) {
                        MethodInfo a = virtualMethods.get(i);
                        switch (a.key().accessorType()) {
                            // @Override
                            // <T> T gX(long c1, long c2, ..., long cN) {
                            //     long indexOffset = f(dimensions, c1, c2, ..., long cN);
                            //     return (T) mh[x].invokeExact(segment, offset + elementOffset + indexOffset);
                            // }
                            case GETTER -> generator.invokeVirtualGetter(a, i);
                            // @Override
                            // <T> void gX(T t) {
                            //     long indexOffset = f(dimensions, c1, c2, ..., long cN);
                            //     mh[x].invokeExact(segment, offset + elementOffset + indexOffset, t);
                            // }
                            case SETTER -> generator.invokeVirtualSetter(a, i);
                        }
                    }

                    // @Override
                    // int hashCode() {
                    //     return System.identityHashCode(this);
                    // }
                    generator.hashCode_();

                    // @Override
                    // boolean equals(Object o) {
                    //     return this == o;
                    // }
                    generator.equals_();

                    //  @Override
                    //  public String toString() {
                    //      return "Foo[g0()=" + g0() + ", g1()=" + g1() + ... "]";
                    //  }
                    List<MethodInfo> getters = accessors.stream(AccessorType.GETTER)
                            .toList();
                    generator.toString_(getters);
                });
        try {
            List<MethodHandle> classData = virtualMethods.stream()
                    .map(a -> switch (a.key()) {
                                case SCALAR_INTERFACE_GETTER,
                                     ARRAY_INTERFACE_GETTER -> mapperCache.interfaceGetMethodHandleFor(a, affectedMemories::add);
                                case SCALAR_RECORD_GETTER,
                                     ARRAY_RECORD_GETTER    -> mapperCache.recordGetMethodHandleFor(a);
                                case SCALAR_RECORD_SETTER,
                                     ARRAY_RECORD_SETTER    -> mapperCache.recordSetMethodHandleFor(a);
                                default -> throw new InternalError("Should not reach here " + a);
                            }
                    ).toList();

            if (MapperUtil.isDebug()) {
                Path path = Path.of( classDesc.displayName() + ".class");
                try {
                    Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    System.out.println("Wrote class file " + path.toAbsolutePath());
                } catch (IOException e) {
                    System.out.println("Unable to write class file: " + path.toAbsolutePath() + " " + e.getMessage());
                }
            }

            @SuppressWarnings("unchecked")
            Class<T> c = (Class<T>) lookup
                    .defineHiddenClassWithClassData(bytes, classData, true)
                    .lookupClass();
            return c;
        } catch (IllegalAccessException | VerifyError e) {
            throw new IllegalArgumentException("Unable to define interface mapper proxy class for " +
                    type + " using " + layout, e);
        }
    }

    // Private methods and classes

    // (MemorySegment, long)Object
    private MethodHandle computeGetHandle() {
        try {
            // (MemorySegment, long)void
            var ctor = lookup.findConstructor(implClass, MethodType.methodType(void.class, MemorySegment.class, long.class));
            // -> (MemorySegment, long)Object
            ctor = ctor.asType(ctor.type().changeReturnType(Object.class));
            return ctor;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Unable to find constructor for " + implClass, e);
        }
    }

    // (MemorySegment, long, T)void
    // This method will return a MethodHandle that will update memory that
    // is mapped to a setter. Memory that is not mapped to a setter will be
    // unaffected.
    private MethodHandle computeSetHandle() {
        List<AffectedMemory> fragments = affectedMemories.stream()
                .sorted(Comparator.comparingLong(AffectedMemory::offset))
                .toList();

        fragments = AffectedMemory.coalesce(fragments);

        try {
            return switch (fragments.size()) {
                case 0 -> MethodHandles.empty(Util.SET_TYPE);
                case 1 -> {
                    MethodType mt = MethodType.methodType(void.class, MemorySegment.class, long.class, Object.class);
                    yield LOCAL_LOOKUP.findVirtual(SegmentInterfaceMapper.class, "setAll", mt)
                            .bindTo(this);
                }
                default -> {
                    MethodType mt = MethodType.methodType(void.class, MemorySegment.class, long.class, Object.class, List.class);
                    MethodHandle mh = LOCAL_LOOKUP.findVirtual(SegmentInterfaceMapper.class, "setFragments", mt)
                            .bindTo(this);
                    yield MethodHandles.insertArguments(mh, 3, fragments);
                }
            };
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Unable to find setter", e);
        }
    }

    // Invoked reflectively
    void setAll(MemorySegment segment, long offset, T t) {
        MemorySegment srcSegment = segment(t)
                .orElseThrow(SegmentInterfaceMapper::notImplType);
        long srcOffset = offset(t)
                .orElseThrow(SegmentInterfaceMapper::notImplType);
        MemorySegment.copy(srcSegment, srcOffset, segment, offset, layout().byteSize());
    }

    // Invoked reflectively
    void setFragments(MemorySegment segment, long offset, T t, List<AffectedMemory> fragments) {
        MemorySegment srcSegment = segment(t)
                .orElseThrow(SegmentInterfaceMapper::notImplType);
        long srcOffset = offset(t)
                .orElseThrow(SegmentInterfaceMapper::notImplType);
        for (AffectedMemory m: fragments) {
            MemorySegment.copy(srcSegment, srcOffset + m.offset(), segment, offset + m.offset(), m.size());
        }
    }

    static IllegalArgumentException notImplType() {
        return new IllegalArgumentException("The provided object of type T is not created by this mapper.");
    }

    private static void assertMappingsCorrectAndTotal(Class<?> type,
                                                      GroupLayout layout,
                                                      Accessors accessors) {
        var nameMappingCounts = layout.memberLayouts().stream()
                .map(MemoryLayout::name)
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<MethodInfo> allMethods = accessors.stream().toList();

        // Make sure we have all components distinctly mapped
        for (MethodInfo component : allMethods) {
            String name = component.method().getName();
            switch (nameMappingCounts.getOrDefault(name, 0L).intValue()) {
                case 0 -> throw new IllegalArgumentException("No mapping for " +
                        type.getName() + "." + name +
                        " in layout " + layout);
                case 1 -> { /* Happy path */ }
                default -> throw new IllegalArgumentException("Duplicate mappings for " +
                        type.getName() + "." + name +
                        " in layout " + layout);
            }
        }

        // Make sure all methods of the type are mapped (totality)
        Set<Method> accessorMethods = allMethods.stream()
                .map(MethodInfo::method)
                .collect(Collectors.toSet());

        var missing = Arrays.stream(type.getMethods())
                .filter(Predicate.not(accessorMethods::contains))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Unable to map methods: " + missing);
        }

    }

/*    // Begin Caching
    private MethodHandle interfaceGetMethodHandleFor(MethodInfo methodInfo) {
        SegmentInterfaceMapper<?> innerMapper = (SegmentInterfaceMapper<?>) cachedInterfaceMapper(methodInfo);
        innerMapper.affectedMemories.stream()
                .map(am -> am.translate(methodInfo.offset()))
                .forEach(affectedMemories::add);
        return innerMapper.getHandle();
    }

    private MethodHandle recordGetMethodHandleFor(MethodInfo methodInfo) {
        return cachedRecordMapper(methodInfo)
                .getHandle()
                .asType(MethodType.methodType(Object.class, MemorySegment.class, long.class));
    }

    private MethodHandle recordSetMethodHandleFor(MethodInfo methodInfo) {
        return cachedRecordMapper(methodInfo)
                .setHandle();
    }

    private SegmentMapper<?> cachedInterfaceMapper(MethodInfo methodInfo) {
        return subMappers.computeIfAbsent(CacheKey.of(methodInfo), _ ->
                SegmentMapper.ofInterface(lookup, methodInfo.type(), methodInfo.targetLayout()));
    }

    private SegmentMapper<?> cachedRecordMapper(MethodInfo methodInfo) {
        return subMappers.computeIfAbsent(CacheKey.of(methodInfo), _ ->
                SegmentMapper.ofRecord(lookup, MapperUtil.castToRecordClass(methodInfo.type()), methodInfo.targetLayout()));
    }

    record CacheKey(Class<?> type,
                   GroupLayout layout) {

        static CacheKey of(MethodInfo methodInfo) {
            return new CacheKey(methodInfo.type(), methodInfo.targetLayout().withoutName());
        }

    }
    // End: Caching*/

    // Used to keep track of which memory shards gets accessed
    // by setters. We need this when computing the setHandle
    record AffectedMemory(long offset,
                          long size) {

        AffectedMemory {
            requireNonNegative(offset);
            requireNonNegative(size);
        }

        static AffectedMemory from(MethodInfo mi) {
            return new AffectedMemory(mi.offset(), mi.layoutInfo().layout().byteSize());
        }

        AffectedMemory translate(long delta) {
            return new AffectedMemory(offset() + delta, size());
        }

        static List<AffectedMemory> coalesce(List<AffectedMemory> items) {
            List<AffectedMemory> result = new ArrayList<>();
            int i = 0;
            for ( ; i<items.size();i++) {
                AffectedMemory current = items.get(i);
                for (int j = i + 1; j < result.size(); j++) {
                    AffectedMemory next = items.get(j);
                    if (current.isBefore(next)) {
                        current = current.merge(next);
                    } else {
                        break;
                    }
                }
                result.add(current);
            }
            return result;
        }

        private boolean isBefore(AffectedMemory other) {
            return offset + size == other.offset();
        }

        private AffectedMemory merge(AffectedMemory other) {
            return new AffectedMemory(offset, size + other.size());
        }

    }

    public static <T> SegmentInterfaceMapper<T> create(MethodHandles.Lookup lookup,
                                                       Class<T> type,
                                                       GroupLayout layout) {
        return new SegmentInterfaceMapper<>(lookup, type, layout, new ArrayList<>());
    }

    // Mapping

    /**
     * This class models composed record mappers.
     *
     * @param lookup       to use for reflective operations
     * @param type         new type to map to/from
     * @param layout       original layout
     * @param getHandle    for get operations
     * @param toMapper     a function that goes from T to R
     * @param <T>          original mapper type
     * @param <R>          composed mapper type
     */
    // Records have trusted instance fields.
    @ValueBased
    record Mapped<T, R>(
            @Override MethodHandles.Lookup lookup,
            @Override Class<R> type,
            @Override GroupLayout layout,
            @Override MethodHandle getHandle,
            Function<? super T, ? extends R> toMapper
    ) implements SegmentMapper<R>, HasLookup {

        static final MethodHandle SET_OPERATIONS_UNSUPPORTED;

        static {
            MethodHandle handle;
            try {
                handle = LOCAL_LOOKUP.findStatic(
                        Mapped.class,
                        "setOperationsUnsupported",
                        MethodType.methodType(void.class, MemorySegment.class, long.class, Object.class));

            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
            SET_OPERATIONS_UNSUPPORTED = handle;
        }

        Mapped(MethodHandles.Lookup lookup,
               Class<R> type,
               GroupLayout layout,
               MethodHandle getHandle,
               Function<? super T, ? extends R> toMapper
        ) {
            this.lookup = lookup;
            this.type = type;
            this.layout = layout;
            this.toMapper = toMapper;
            MethodHandle toMh = findVirtual("mapTo").bindTo(this);
            this.getHandle = MethodHandles.filterReturnValue(getHandle, toMh);
        }

        @Override
        public MethodHandle setHandle() {
            return SET_OPERATIONS_UNSUPPORTED;
        }

        @Override
        public <R1> SegmentMapper<R1> map(Class<R1> newType,
                                          Function<? super R, ? extends R1> toMapper,
                                          Function<? super R1, ? extends R> fromMapper) {
            throw twoWayMappersUnsupported();
        }

        @Override
        public <R1> SegmentMapper<R1> map(Class<R1> newType,
                                          Function<? super R, ? extends R1> toMapper) {
            return of(this, newType, toMapper);
        }

        // Used reflective when obtaining a MethodHandle
        R mapTo(T t) {
            return toMapper.apply(t);
        }

        // Used reflective when obtaining a MethodHandle
        /*T mapFrom(R r) {
            return fromMapper.apply(r);
        }*/

        static <T, R, O extends SegmentMapper<T> & HasLookup> Mapped<T, R> of(
                O original,
                Class<R> newType,
                Function<? super T, ? extends R> toMapper) {

            Objects.requireNonNull(original);
            Objects.requireNonNull(newType);
            Objects.requireNonNull(toMapper);

            return new Mapped<>(original.lookup(),
                    newType,
                    original.layout(),
                    original.getHandle(),
                    toMapper
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

        private static void setOperationsUnsupported(MemorySegment s, long o, Object t) {
            throw new UnsupportedOperationException("SegmentMapper::set operations are not supported for mapped interface mappers");
        }

    }

    private static UnsupportedOperationException twoWayMappersUnsupported() {
        return new UnsupportedOperationException("Two-way mappers are not supported for interface mappers");
    }

}