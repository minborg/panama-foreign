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
import jdk.internal.classfile.ClassBuilder;
import jdk.internal.classfile.ClassHierarchyResolver;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.Label;
import jdk.internal.foreign.mapper.MethodInfo.AccessorType;
import jdk.internal.foreign.mapper.MethodInfo.Cardinality;
import jdk.internal.foreign.mapper.MethodInfo.Key;
import jdk.internal.foreign.mapper.MethodInfo.ValueType;
import jdk.internal.foreign.mapper.component.Util;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.constant.ConstantDescs.*;
import static jdk.internal.classfile.Classfile.*;
import static jdk.internal.foreign.layout.MemoryLayoutUtil.requireNonNegative;

/**
 * A record mapper that is matching components of an interface with elements in a GroupLayout.
 */
@ValueBased
public final class SegmentInterfaceMapper<T> implements SegmentMapper<T>, HasLookup {

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();

    private static final ClassDesc VALUE_LAYOUTS_CLASS_DESC = desc(ValueLayout.class);
    private static final ClassDesc MEMORY_SEGMENT_CLASS_DESC = desc(MemorySegment.class);

    static final String SEGMENT_FIELD_NAME = "segment";
    static final String OFFSET_FIELD_NAME = "offset";
    public static final String SECRET_SEGMENT_METHOD_NAME = "$_$_$sEgMeNt$_$_$";
    public static final String SECRET_OFFSET_METHOD_NAME = "$_$_$oFfSeT$_$_$";

    private final MethodHandles.Lookup lookup;
    private final Class<T> type;
    private final GroupLayout layout;
    private final Class<T> implClass;
    private final MethodHandle getHandle;
    private final MethodHandle setHandle;
    private final MethodHandle segmentGetHandle;
    private final MethodHandle offsetGetHandle;
    private final List<AffectedMemory> affectedMemories;

    private SegmentInterfaceMapper(MethodHandles.Lookup lookup,
                                   Class<T> type,
                                   GroupLayout layout,
                                   List<AffectedMemory> affectedMemories) {
        this.lookup = lookup;
        this.type = MapperUtil.requireImplementableInterfaceType(type);
        this.layout = layout;
        this.affectedMemories = affectedMemories;
        Accessors accessors = Accessors.of(type, layout);

        // Add affected memory for all the setters seen on this level
        accessors.stream(k -> k.accessorType() == AccessorType.SETTER)
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
            this.segmentGetHandle = lookup.unreflect(implClass.getMethod(SECRET_SEGMENT_METHOD_NAME))
                    .asType(MethodType.methodType(MemorySegment.class, Object.class));
            this.offsetGetHandle = lookup.unreflect(implClass.getMethod(SECRET_OFFSET_METHOD_NAME))
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
        ClassDesc interfaceClassDesc = desc(type);
        ClassLoader loader = type.getClassLoader();

        // We need to materialize these methods so that the order is preserved
        List<MethodInfo> virtualMethods = accessors.stream()
                .filter(mi -> mi.key().valueType().isVirtual())
                .toList();

        byte[] bytes = of(ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofClassLoading(loader)))
                .build(classDesc, cb -> {
                    // public final
                    cb.withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SUPER);
                    // extends Object
                    cb.withSuperclass(CD_Object);
                    // implements "type"
                    cb.withInterfaceSymbols(interfaceClassDesc);
                    // private final MemorySegment $segment$;
                    cb.withField(SEGMENT_FIELD_NAME, MEMORY_SEGMENT_CLASS_DESC, ACC_PRIVATE | ACC_FINAL);
                    // private final long $offset$;
                    cb.withField(OFFSET_FIELD_NAME, CD_long, ACC_PRIVATE | ACC_FINAL);

                    cb.withMethodBody(INIT_NAME, MethodTypeDesc.of(CD_void, MEMORY_SEGMENT_CLASS_DESC, CD_long), ACC_PUBLIC, cob -> {
                        cob.aload(0)
                                // Call Object's constructor
                                .invokespecial(CD_Object, INIT_NAME, MTD_void, false)
                                // Set "segment"
                                .aload(0)
                                .aload(1)
                                .putfield(classDesc, SEGMENT_FIELD_NAME, MEMORY_SEGMENT_CLASS_DESC)
                                // Set "offset"
                                .aload(0)
                                .lload(2)
                                .putfield(classDesc, OFFSET_FIELD_NAME, CD_long)
                                .return_();
                    });


                    // MemorySegment $_$_$sEgMeNt$_$_$() {
                    //     return segment;
                    // }
                    cb.withMethodBody(SECRET_SEGMENT_METHOD_NAME, MethodTypeDesc.of(MEMORY_SEGMENT_CLASS_DESC), ACC_PUBLIC, cob ->
                            cob.aload(0)
                                    .getfield(classDesc, SEGMENT_FIELD_NAME, MEMORY_SEGMENT_CLASS_DESC)
                                    .areturn()
                    );

                    // long $_$_$oFfSeT$_$_$() {
                    //     return offset;
                    // }
                    cb.withMethodBody(SECRET_OFFSET_METHOD_NAME, MethodTypeDesc.of(CD_long), ACC_PUBLIC, cob ->
                            cob.aload(0)
                                    .getfield(classDesc, OFFSET_FIELD_NAME, CD_long)
                                    .lreturn()
                    );


                    // @Override
                    // <t> gX(c1, c2, ..., cN) {
                    //     long indexOffset = f(dimensions, c1, c2, ..., long cN);
                    //     return segment.get(JAVA_t, offset + elementOffset + indexOffset);
                    // }
                    accessors.stream(Set.of(Key.SCALAR_VALUE_GETTER, Key.ARRAY_VALUE_GETTER))
                            .forEach(a -> generateValueGetter(cb, classDesc, a));

                    // @Override
                    // void gX(c1, c2, ..., cN, <t> t) {
                    //     long indexOffset = f(dimensions, c1, c2, ..., long cN);
                    //     segment.set(JAVA_t, offset + elementOffset + indexOffset, t);
                    // }
                    accessors.stream(Set.of(Key.SCALAR_VALUE_SETTER, Key.ARRAY_VALUE_SETTER))
                            .forEach(a -> generateValueSetter(cb, classDesc, a));

                    for (int i = 0; i < virtualMethods.size(); i++) {
                        MethodInfo a = virtualMethods.get(i);
                        switch (a.key().accessorType()) {
                            // @Override
                            // <T extends Record> T gX(long c1, long c2, ..., long cN) {
                            //     long indexOffset = f(dimensions, c1, c2, ..., long cN);
                            //     return (T) mh[x].invokeExact(segment, offset + elementOffset + indexOffset);
                            // }
                            case GETTER -> generateInvokeVirtualGetter(cb, classDesc, a, i);
                            // @Override
                            // <T extends Record> void gX(T t) {
                            //     mh[x].invokeExact(segment, offset + elementOffset, t);
                            // }
                            case SETTER -> generateRecordSetter(cb, classDesc, a, i);
                        }
                    }

                    // @Override
                    // int hashCode() {
                    //     return System.identityHashCode(this);
                    // }
                    cb.withMethodBody("hashCode", MethodTypeDesc.of(CD_int), ACC_PUBLIC | ACC_FINAL, cob ->
                            cob.aload(0)
                                    .invokestatic(desc(System.class), "identityHashCode", MethodTypeDesc.of(CD_int, CD_Object))
                                    .ireturn()
                    );

                    // @Override
                    // boolean equals(Object o) {
                    //     return this == o;
                    // }
                    cb.withMethodBody("equals", MethodTypeDesc.of(CD_boolean, CD_Object), ACC_PUBLIC | ACC_FINAL, cob -> {
                                Label l0 = cob.newLabel();
                                Label l1 = cob.newLabel();
                                cob.aload(0)
                                        .aload(1)
                                        .if_acmpne(l0)
                                        .iconst_1()
                                        .goto_(l1)
                                        .labelBinding(l0)
                                        .iconst_0()
                                        .labelBinding(l1)
                                        .ireturn()
                                ;
                            }
                    );

                    //  @Override
                    //  public String toString() {
                    //      return "Foo[g0()=" + g0() + ", g1()=" + g1() + ... "]";
                    //  }
                    List<MethodInfo> getters = accessors.stream(key -> key.accessorType()==AccessorType.GETTER)
                            .toList();
                    generateToString(cb, classDesc, getters);
                });
        try {

            // Todo: These handles can be de-duplicated

            List<MethodHandle> classData = virtualMethods.stream()
                    .map(a -> switch (a.key()) {
                                case SCALAR_INTERFACE_GETTER,
                                     ARRAY_INTERFACE_GETTER -> interfaceGetMethodHandleFor(a);
                                case SCALAR_RECORD_GETTER,
                                     ARRAY_RECORD_GETTER    -> recordGetMethodHandleFor(a);
                                case SCALAR_RECORD_SETTER   -> recordSetMethodHandleFor(a);
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
    // is mapped to a setter only.
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

    void setAll(MemorySegment segment, long offset, T t) {
        MemorySegment srcSegment = segment(t)
                .orElseThrow(SegmentInterfaceMapper::notImplType);
        long srcOffset = offset(t)
                .orElseThrow(SegmentInterfaceMapper::notImplType);
        MemorySegment.copy(srcSegment, srcOffset, segment, offset, layout().byteSize());
    }

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

    private Map<Key, List<MethodInfo>> accessors() {
        return Arrays.stream(type.getMethods())
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> !m.isDefault())
                .map(this::methodInfo)
                .collect(Collectors.groupingBy(MethodInfo::key));
    }

    private MethodInfo methodInfo(Method method) {

        AccessorType accessorType = isGetter(method)
                ? AccessorType.GETTER
                : AccessorType.SETTER;

        Class<?> targetType = (accessorType == AccessorType.GETTER)
                ? method.getReturnType()
                : getterType(method);

        ValueType valueType;
        if (targetType.isPrimitive() || targetType.equals(MemorySegment.class)) {
            valueType = ValueType.VALUE;
        } else if (targetType.isInterface()) {
            valueType = ValueType.INTERFACE;
        } else if (targetType.isRecord()) {
            valueType = ValueType.RECORD;
        } else {
            throw new IllegalArgumentException("Type " + targetType + " is neither a primitive value, an interface nor a record: " + method);
        }

        var elementPath = MemoryLayout.PathElement.groupElement(method.getName());
        MemoryLayout element;
        try {
            element = layout.select(elementPath);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Unable to resolve '" + method + "' in " + layout, iae);
        }
        var offset = layout.byteOffset(elementPath);

        return switch (element) {
            case ValueLayout vl -> {
                if (!targetType.equals(vl.carrier())) {
                    throw new IllegalArgumentException("The type " + targetType + " for method " + method +
                            "does not match " + element);
                }
                yield new MethodInfo(Key.of(Cardinality.SCALAR, valueType, accessorType),
                        method, targetType, LayoutInfo.of(vl), offset);
            }
            case GroupLayout gl ->
                    new MethodInfo(Key.of(Cardinality.SCALAR, valueType, accessorType),
                            method, targetType, LayoutInfo.of(gl), offset);
            case SequenceLayout sl -> {
                MethodInfo info = new MethodInfo(Key.of(Cardinality.ARRAY, valueType, accessorType)
                        , method, targetType, LayoutInfo.of(sl), offset);
                int noDimensions = info.layoutInfo().arrayInfo().orElseThrow().dimensions().size();
                // The last parameter for a setter is the new value
                int expectedParameterIndexCount = method.getParameterCount() - (accessorType == AccessorType.SETTER ? 1 : 0);
                if (expectedParameterIndexCount != noDimensions) {
                    throw new IllegalArgumentException(
                            "Sequence layout has a dimension of " + noDimensions +
                                    " and so, the method parameter count does not" +
                                    " match for: " + method);
                }
                yield info;
            }
            default -> throw new IllegalArgumentException("Cannot map " + element + " for " + type);
        };
    }

    static Class<?> getterType(Method method) {
        if (method.getParameterCount() == 0) {
            throw new IllegalArgumentException("A setter must take at least one argument: " + method);
        }
        return method.getParameterTypes()[method.getParameterCount() - 1];
    }

    static boolean isGetter(Method method) {
        return method.getReturnType() != void.class;
    }

    private MethodHandle interfaceGetMethodHandleFor(MethodInfo methodInfo) {

        GroupLayout groupLayout = (GroupLayout) methodInfo.layoutInfo().arrayInfo()
                .map(ArrayInfo::elementLayout)
                .orElse(methodInfo.layoutInfo().layout());

        List<AffectedMemory> affectedElementMemories = new ArrayList<>();

        // Todo: As the offset is zero, we can cache these mappers (per type and layout)
        SegmentInterfaceMapper<?> innerMapper = new SegmentInterfaceMapper<>(
                lookup,
                methodInfo.type(),
                groupLayout,
                affectedElementMemories);

        affectedElementMemories.stream()
                .map(am -> am.translate(methodInfo.offset()))
                .forEach(affectedMemories::add);

        return innerMapper.getHandle();
    }

    private MethodHandle recordGetMethodHandleFor(MethodInfo methodInfo) {

        GroupLayout groupLayout = (GroupLayout) methodInfo.layoutInfo().arrayInfo()
                .map(ArrayInfo::elementLayout)
                .orElse(methodInfo.layoutInfo().layout());

        // Todo: As the offset is zero, we can cache these mappers (per type and layout)
        SegmentMapper<?> innerMapper = new SegmentRecordMapper<>(lookup,
                MapperUtil.castToRecordClass(methodInfo.type()),
                groupLayout,
                0, // The actual offset is added later at invocation
                0);

        return innerMapper.getHandle()
                .asType(MethodType.methodType(Object.class, MemorySegment.class, long.class));
    }

    private MethodHandle recordSetMethodHandleFor(MethodInfo methodInfo) {

        // Todo: As the offset is zero, we can cache these mappers (per type and layout)
        SegmentMapper<?> innerMapper = new SegmentRecordMapper<>(lookup,
                MapperUtil.castToRecordClass(methodInfo.type()),
                (GroupLayout) methodInfo.layoutInfo().layout(),
                0, // The actual offset is added later at invocation
                0);

        return innerMapper.setHandle();
    }

    private void generateValueGetter(ClassBuilder cb,
                                     ClassDesc classDesc,
                                     MethodInfo info) {

        String name = info.method().getName();
        ClassDesc returnDesc = desc(info.type());
        ScalarInfo scalarInfo = info.layoutInfo().scalarInfo().orElseThrow();

        var getDesc = MethodTypeDesc.of(returnDesc, scalarInfo.valueLayoutDesc(), CD_long);

        // If it is an array, there is a number of long parameters
        List<ClassDesc> parameterDesc = info.layoutInfo().arrayInfo()
                .map(ai -> ai.dimensions().stream().map(_ -> CD_long).toList())
                .orElse(Collections.emptyList());

        cb.withMethodBody(name, MethodTypeDesc.of(returnDesc, parameterDesc), ACC_PUBLIC, cob -> {
                    cob.aload(0)
                            .getfield(classDesc, SEGMENT_FIELD_NAME, MEMORY_SEGMENT_CLASS_DESC)
                            .getstatic(VALUE_LAYOUTS_CLASS_DESC, scalarInfo.memberName(), scalarInfo.valueLayoutDesc());
                    offsetBlock(cob, classDesc, info.offset());

                    // Is this an array accessor?
                    info.layoutInfo().arrayInfo().ifPresent(
                            ai -> reduceArrayIndexes(cob, ai)
                    );

                    cob
                            .invokeinterface(MEMORY_SEGMENT_CLASS_DESC, "get", getDesc);
                    // ireturn(), dreturn() etc.
                    info.layoutInfo().returnOp().accept(cob);
                }
        );
    }

    private void generateValueSetter(ClassBuilder cb,
                                     ClassDesc classDesc,
                                     MethodInfo info) {

        String name = info.method().getName();

        // If it is an array, there is a number of long parameters
        List<ClassDesc> parameterDesc = info.layoutInfo().arrayInfo()
                .map(ai -> Stream.concat(ai.dimensions().stream().map(_ -> CD_long), Stream.of(desc(info.type()))).toList())
                .orElse(List.of(desc(info.type())));

        ScalarInfo scalarInfo = info.layoutInfo().scalarInfo().orElseThrow();

        var setDesc = MethodTypeDesc.of(CD_void, scalarInfo.valueLayoutDesc(), CD_long, desc(info.type()));

        cb.withMethodBody(name, MethodTypeDesc.of(CD_void, parameterDesc), ACC_PUBLIC, cob -> {
                    cob.aload(0)
                            .getfield(classDesc, SEGMENT_FIELD_NAME, MEMORY_SEGMENT_CLASS_DESC)
                            .getstatic(VALUE_LAYOUTS_CLASS_DESC, scalarInfo.memberName(), scalarInfo.valueLayoutDesc());
                    offsetBlock(cob, classDesc, info.offset());

                    // Is this an array accessor?
                    info.layoutInfo().arrayInfo().ifPresent(
                            ai -> reduceArrayIndexes(cob, ai)
                    );

                    int valueSlotNo = (parameterDesc.size() - 1) * 2 + 1;
                    // iload, dload, etc.
                    info.layoutInfo().paramOp().accept(cob, valueSlotNo);
                    cob.invokeinterface(MEMORY_SEGMENT_CLASS_DESC, "set", setDesc)
                            .return_();
                }
        );

    }

    private void generateInvokeVirtualGetter(ClassBuilder cb,
                                             ClassDesc classDesc,
                                             MethodInfo info,
                                             int boostrapIndex) {

        var name = info.method().getName();
        var returnDesc = desc(info.type());

        DynamicConstantDesc<MethodHandle> desc = DynamicConstantDesc.of(
                BSM_CLASS_DATA_AT,
                boostrapIndex
        );

        // If it is an array, there is a number of long parameters
        List<ClassDesc> parameterDesc = info.layoutInfo().arrayInfo()
                .map(ai -> ai.dimensions().stream().map(_ -> CD_long).toList())
                .orElse(Collections.emptyList());

        cb.withMethodBody(name, MethodTypeDesc.of(returnDesc, parameterDesc), ACC_PUBLIC, cob -> {
                    cob.ldc(desc)
                            .checkcast(desc(MethodHandle.class)) // MethodHandle
                            .aload(0)
                            .getfield(classDesc, SEGMENT_FIELD_NAME, MEMORY_SEGMENT_CLASS_DESC); // MemorySegment

                    offsetBlock(cob, classDesc, info.offset());

                    // Is this an array accessor?
                    info.layoutInfo().arrayInfo().ifPresent(
                            ai -> reduceArrayIndexes(cob, ai)
                    );

                    cob
                            .invokevirtual(CD_MethodHandle, "invokeExact", MethodTypeDesc.of(CD_Object, MEMORY_SEGMENT_CLASS_DESC, CD_long))
                            .checkcast(returnDesc)
                            .areturn();
                }
        );
    }

    // Example:
    //   The dimensions are [3, 4] and the element byte size is 8 bytes
    //   reduce(2, 3) -> 2 * 4 * 8 + 3 * 8 = 88
    // public static long reduce(long i1, long i2) {
    //     long offset = Objects.checkIndex(i1, 3) * (8 * 4) +
    //     Objects.checkIndex(i2, 4) * 8;
    //     return offset;
    // }
    void reduceArrayIndexes(CodeBuilder cob,
                            ArrayInfo arrayInfo) {
        long elementByteSize = arrayInfo.elementLayout().byteSize();
        // Check parameters and push scaled offsets on the stack
        for (int i = 0; i < arrayInfo.dimensions().size(); i++) {
            long dimension = arrayInfo.dimensions().get(i);
            long factor = arrayInfo.dimensions().stream()
                    .skip(i + 1)
                    .reduce(elementByteSize, Math::multiplyExact);

            cob.lload(1 + i * 2)
                    .ldc(dimension)
                    .invokestatic(desc(Objects.class), "checkIndex", MethodTypeDesc.of(CD_long, CD_long, CD_long))
                    .ldc(factor)
                    .lmul();
        }
        // Sum their values (including the value that existed *before* this method was invoked)
        for (int i = 0; i < arrayInfo.dimensions().size(); i++) {
            cob.ladd();
        }
    }

    private void generateRecordSetter(ClassBuilder cb,
                                      ClassDesc classDesc,
                                      MethodInfo info,
                                      int boostrapIndex) {
        var name = info.method().getName();
        var parameterDesc = desc(info.type());

        DynamicConstantDesc<MethodHandle> desc = DynamicConstantDesc.of(
                BSM_CLASS_DATA_AT,
                boostrapIndex
        );

        cb.withMethodBody(name, MethodTypeDesc.of(CD_void, parameterDesc), ACC_PUBLIC, cob -> {
                    cob.ldc(desc)
                            .checkcast(desc(MethodHandle.class)) // MethodHandle
                            .aload(0)
                            .getfield(classDesc, SEGMENT_FIELD_NAME, MEMORY_SEGMENT_CLASS_DESC); // MemorySegment
                    offsetBlock(cob, classDesc, info.offset())
                            .aload(1) // Record
                            .checkcast(desc(Record.class))
                            .invokevirtual(CD_MethodHandle, "invokeExact", MethodTypeDesc.of(CD_void, MEMORY_SEGMENT_CLASS_DESC, CD_long, CD_Object))
                            .return_();
                }
        );
    }

    // Generate code that calculates "this.offset + layoutOffset"
    private static CodeBuilder offsetBlock(CodeBuilder cob,
                                           ClassDesc classDesc,
                                           long layoutOffset) {
        cob.aload(0)
                .getfield(classDesc, OFFSET_FIELD_NAME, CD_long); // long

        if (layoutOffset != 0) {
            cob.ldc(layoutOffset)
                    .ladd();
        }
        return cob;
    }

    private void generateToString(ClassBuilder cb,
                                  ClassDesc classDesc,
                                  List<MethodInfo> getters) {

        // We want the components to appear in the order reported by Class::getMethods
        // So, we first construct a map that we can use to lookup MethodInfo objects
        Map<Method, MethodInfo> methods = getters.stream()
                .collect(Collectors.toMap(MethodInfo::method, Function.identity()));
        List<MethodInfo> sortedGetters = Arrays.stream(type.getMethods())
                .map(methods::get)
                .filter(Objects::nonNull) // Unmapped methods discarded (e.g. static methods)
                .toList();

        // Foo[g0()=\u0001, g1()=\u0001, ...]
        var recipe = sortedGetters.stream()
                .map(m -> m.layoutInfo().arrayInfo()
                        .map(ai -> String.format("%s()=%s%s", m.method().getName(), m.type().getSimpleName(), ai.dimensions()))
                        .orElse(String.format("%s()=\u0001", m.method().getName()))
                )
                .collect(Collectors.joining(", ", type.getSimpleName() + "[", "]"));

        List<MethodInfo> nonArrayGetters = sortedGetters.stream()
                .filter(i -> i.layoutInfo().arrayInfo().isEmpty())
                .toList();

        DirectMethodHandleDesc bootstrap = ofCallsiteBootstrap(
                desc(StringConcatFactory.class),
                "makeConcatWithConstants",
                CD_CallSite,
                CD_String, CD_Object.arrayType()
        );

        List<ClassDesc> getDescriptions = classDescs(nonArrayGetters);

        DynamicCallSiteDesc desc = DynamicCallSiteDesc.of(
                bootstrap,
                "toString",
                MethodTypeDesc.of(CD_String, getDescriptions), // String, g0, g1, ...
                recipe
        );

        cb.withMethodBody("toString",
                MethodTypeDesc.of(CD_String),
                ACC_PUBLIC | ACC_FINAL,
                cob -> {
                    for (int i = 0; i < nonArrayGetters.size(); i++) {
                        var name = nonArrayGetters.get(i).method().getName();
                        cob.aload(0);
                        // Method gi:()?
                        cob.invokevirtual(classDesc, name, MethodTypeDesc.of(getDescriptions.get(i)));
                    }
                    cob.invokedynamic(desc);
                    cob.areturn();
                });

    }

    private static List<ClassDesc> classDescs(List<MethodInfo> methods) {
        return methods.stream()
                .map(MethodInfo::type)
                .map(SegmentInterfaceMapper::desc)
                .toList();
    }

    static ClassDesc desc(Class<?> clazz) {
        return clazz.describeConstable()
                .orElseThrow();
    }

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

    record AffectedMemory(long offset, long size){

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
}