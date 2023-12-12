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
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.constant.ConstantDescs.*;
import static jdk.internal.classfile.Classfile.*;
import static jdk.internal.foreign.mapper.SegmentInterfaceMapper.ParameterTypes.*;

/**
 * A record mapper that is matching components of an interface with elements in a GroupLayout.
 */
@ValueBased
public final class SegmentInterfaceMapper<T> implements SegmentMapper<T>, HasLookup {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final ClassDesc VALUE_LAYOUTS_CLASS_DESC = desc(ValueLayout.class);
    private static final ClassDesc MEMORY_SEGMENT_CLASS_DESC = desc(MemorySegment.class);

    static final String SEGMENT_FIELD_NAME = "segment";
    static final String OFFSET_FIELD_NAME = "offset";

    private final MethodHandles.Lookup lookup;
    private final Class<T> type;
    private final GroupLayout layout;
    private final long offset;
    private final int depth;
    private final Class<T> implClass;
    private final boolean isExhaustive;
    private final MethodHandle getHandle;
    private final MethodHandle setHandle;

    private SegmentInterfaceMapper(MethodHandles.Lookup lookup,
                                   Class<T> type,
                                   GroupLayout layout,
                                   long offset,
                                   int depth) {
        this.lookup = lookup;
        this.type = MapperUtil.requireImplementableInterfaceType(type);
        this.layout = layout;
        this.offset = offset;
        this.depth = depth;
        List<MethodInfo> scalarGetters = scalarGetters(type, layout);
        List<MethodInfo> scalarSetters = scalarSetters(type, layout);
        List<MethodInfo> interfaceGetters = interfaceGetters(type, layout);
        assertNoInterfaceSetters(type);
        List<MethodInfo> recordGetters = recordGetters(type, layout);
        List<MethodInfo> recordSetters = recordSetters(type, layout);
        List<MethodInfo> arrayInterfaceGetters = arrayInterfaceGetters(type, layout);

        List<MethodInfo> allAccessors = concat(
                scalarGetters, scalarSetters,
                interfaceGetters,
                recordGetters, recordSetters,
                arrayInterfaceGetters);
        assertMappingsCorrect(type, layout, allAccessors);
        assertTotality(type, allAccessors);
        this.implClass = generateClass(
                scalarGetters, scalarSetters,
                interfaceGetters,
                recordGetters, recordSetters,
                arrayInterfaceGetters);
        Handles handles = handles();
        this.isExhaustive = handles.isExhaustive();
        this.getHandle = handles.getHandle();
        this.setHandle = handles.setHandle();
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
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (SegmentInterfaceMapper<?>) obj;
        return Objects.equals(this.lookup, that.lookup) &&
                Objects.equals(this.type, that.type) &&
                Objects.equals(this.layout, that.layout) &&
                this.offset == that.offset &&
                this.depth == that.depth &&
                Objects.equals(this.implClass, that.implClass) &&
                this.isExhaustive == that.isExhaustive &&
                Objects.equals(this.getHandle, that.getHandle) &&
                Objects.equals(this.setHandle, that.setHandle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lookup, type, layout, offset, depth, implClass, isExhaustive, getHandle, setHandle);
    }

    @Override
    public String toString() {
        return "SegmentInterfaceMapper[" +
                "lookup=" + lookup + ", " +
                "type=" + type + ", " +
                "layout=" + layout + ", " +
                "offset=" + offset + ", " +
                "depth=" + depth + ", " +
                "implClass=" + implClass + ", " +
                "isExhaustive=" + isExhaustive + ", " +
                "getHandle=" + getHandle + ", " +
                "setHandle=" + setHandle + ']';
    }

    @Override
    public <R> SegmentMapper<R> map(Class<R> newType,
                                    Function<? super T, ? extends R> toMapper,
                                    Function<? super R, ? extends T> fromMapper) {
        return Mapped.of(this, newType, toMapper, fromMapper);
    }

    private Class<T> generateClass(List<MethodInfo> scalarGetters,
                                   List<MethodInfo> scalarSetters,
                                   List<MethodInfo> interfaceGetters,
                                   List<MethodInfo> recordGetters,
                                   List<MethodInfo> recordSetters,
                                   List<MethodInfo> arrayInterfaceGetters) {
        ClassDesc classDesc = ClassDesc.of(type.getSimpleName() + "InterfaceMapper");
        ClassDesc interfaceClassDesc = desc(type);
        ClassLoader loader = type.getClassLoader();

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
                    cb.withMethodBody("$_$_$sEgMeNt$_$_$", MethodTypeDesc.of(MEMORY_SEGMENT_CLASS_DESC), ACC_PUBLIC, cob ->
                            cob.aload(0)
                                    .getfield(classDesc, SEGMENT_FIELD_NAME, MEMORY_SEGMENT_CLASS_DESC)
                                    .areturn()
                    );

                    // long $_$_$oFfSeT$_$_$() {
                    //     return offset;
                    // }
                    cb.withMethodBody("$_$_$oFfSeT$_$_$", MethodTypeDesc.of(CD_long), ACC_PUBLIC, cob ->
                            cob.aload(0)
                                    .getfield(classDesc, OFFSET_FIELD_NAME, CD_long)
                                    .lreturn()
                    );

                    for (MethodInfo scalarGetter : scalarGetters) {
                        // @Override
                        // <t> gX() {
                        //     return segment.get(JAVA_t, offset + elementOffset);
                        // }
                        generateScalarGetter(cb, classDesc, scalarGetter);
                    }

                    // @Override
                    // void gX(<t> t) {
                    //     segment.get(JAVA_t, offset + elementOffset, t);
                    // }
                    for (MethodInfo scalarSetter : scalarSetters) {
                        generateScalarSetter(cb, classDesc, scalarSetter);
                    }

                    int boostrapIndex = 0;

                    // @Override
                    // T gX() {
                    //     return (T) mh[x].invokeExact(segment, offset + elementOffset);
                    // }
                    for (MethodInfo interfaceGetter : interfaceGetters) {
                        generateInvokeVirtualGetter(cb, classDesc, interfaceGetter, boostrapIndex++);
                    }

                    // @Override
                    // <T extends Record> T gX() {
                    //     return (T) mh[x].invokeExact(segment, offset + elementOffset);
                    // }
                    for (MethodInfo recordGetter : recordGetters) {
                        generateInvokeVirtualGetter(cb, classDesc, recordGetter, boostrapIndex++);
                    }

                    // @Override
                    // <T extends Record> void gX(T t) {
                    //     mh[x].invokeExact(segment, offset + elementOffset, t);
                    // }
                    for (MethodInfo recordSetter : recordSetters) {
                        generateRecordSetter(cb, classDesc, recordSetter, boostrapIndex++);
                    }

                    // @Override
                    // <T extends Record> T gX(long c1, long c2, ..., long cN) {
                    //     long indexOffset = f(dimensions, c1, c2, ..., long cN);
                    //     return (T) mh[x].invokeExact(segment, offset + elementOffset + indexOffset);
                    // }
                    for (MethodInfo arrayInterfaceGetter : arrayInterfaceGetters) {
                        generateInvokeVirtualGetter(cb, classDesc, arrayInterfaceGetter, boostrapIndex++);
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
                    generateToString(cb, classDesc, concat(scalarGetters, interfaceGetters, recordGetters, arrayInterfaceGetters));
                });
        try {

            // Here are the actual pre-computed method handles to be loaded by .ldc()
            List<MethodHandle> interfaceGetHandles = interfaceGetters.stream()
                    .map(mi -> interfaceGetMethodHandleFor(mi, (GroupLayout) mi.layoutInfo().layout()))
                    .toList();

            List<MethodHandle> recordGetHandles = recordGetters.stream()
                    .map(this::recordGetMethodHandleFor)
                    .toList();

            List<MethodHandle> recordSetHandles = recordGetters.stream()
                    .map(this::recordSetMethodHandleFor)
                    .toList();

            List<MethodHandle> arrayInterfaceSetHandles = arrayInterfaceGetters.stream()
                    .map(mi -> interfaceGetMethodHandleFor(mi, (GroupLayout) mi.layoutInfo().arrayInfo().orElseThrow().elementLayout()))
                    .toList();

            List<MethodHandle> classData =
                    concat(interfaceGetHandles,
                            recordGetHandles, recordSetHandles,
                            arrayInterfaceSetHandles);

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

    // This method is using a partially initialized mapper
    private Handles handles() {

        // The types for the constructor/components
        List<MethodInfo> componentTypes = scalarGetters(type, layout);

        // There is exactly one member layout for each record component
        boolean isExhaustive = layout.memberLayouts().size() == componentTypes.size();

        // (MemorySegment, long)Object
        MethodHandle getHandle = computeGetHandle(componentTypes);

        // (MemorySegment, long, T)void
        MethodHandle setHandle = computeSetHandle(componentTypes);

        return new Handles(isExhaustive, getHandle, setHandle);
    }

    // (MemorySegment, long)Object
    private MethodHandle computeGetHandle(List<MethodInfo> componentMethods) {
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
    private MethodHandle computeSetHandle(List<MethodInfo> componentMethods) {
        // Todo: Fix me
        return null;
    }

    private static void assertMappingsCorrect(Class<?> type,
                                              GroupLayout layout,
                                              List<MethodInfo> methods) {
        var nameMappingCounts = layout.memberLayouts().stream()
                .map(MemoryLayout::name)
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // Make sure we have all components distinctly mapped
        for (MethodInfo component : methods) {
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
    }

    private static void assertTotality(Class<?> type, List<MethodInfo> accessors) {
        Set<Method> accessorMethods = accessors.stream()
                .map(MethodInfo::method)
                .collect(Collectors.toSet());

        var missing = Arrays.stream(type.getMethods())
                .filter(Predicate.not(accessorMethods::contains))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Unable to map methods: " + missing);
        }

    }

    private static List<MethodInfo> scalarGetters(Class<?> type,
                                                  GroupLayout layout) {
        return mappableGetMethods(getCandidates(type, GETTER, Class::isPrimitive), layout);
    }

    private static List<MethodInfo> interfaceGetters(Class<?> type,
                                                     GroupLayout layout) {
        return mappableGetMethods(getCandidates(type, GETTER, Class::isInterface), layout);
    }

    private static void assertNoInterfaceSetters(Class<?> type) {
        List<Method> methods = setCandidates(type, SETTER, Class::isInterface)
                .toList();

        if (!methods.isEmpty()) {
            throw new IllegalArgumentException("Setters cannot take an interface as a parameter: " + methods);
        }
    }

    private static List<MethodInfo> recordGetters(Class<?> type,
                                                  GroupLayout layout) {
        return mappableGetMethods(getCandidates(type, GETTER, Class::isRecord), layout);
    }

    private static List<MethodInfo> arrayInterfaceGetters(Class<?> type,
                                                          GroupLayout layout) {
        List<Method> methods = getCandidates(type, ARRAY, Class::isInterface).toList();

        return mappableGetMethods(methods.stream(), layout);
    }

    private static List<MethodInfo> recordSetters(Class<?> type,
                                                  GroupLayout layout) {
        return mappableSetMethods(setCandidates(type, SETTER, Class::isRecord), layout);
    }

    private static List<MethodInfo> scalarSetters(Class<?> type,
                                                  GroupLayout layout) {
        return mappableSetMethods(setCandidates(type, SETTER, Class::isPrimitive), layout);
    }

    private static List<MethodInfo> mappableGetMethods(Stream<Method> methods,
                                                       GroupLayout layout) {
        return mappableMethods0(methods, layout, Method::getReturnType);
    }

    private static List<MethodInfo> mappableSetMethods(Stream<Method> methods,
                                                       GroupLayout layout) {
        return mappableMethods0(methods, layout, m -> m.getParameterTypes()[0]);
    }

    private static List<MethodInfo> mappableMethods0(Stream<Method> methods,
                                                    GroupLayout layout,
                                                    Function<? super Method, ? extends Class<?>> typeExtractor) {

        return methods
                .map(m -> {
                    var type = typeExtractor.apply(m);
                    var elementPath = MemoryLayout.PathElement.groupElement(m.getName());

                    MemoryLayout element;
                    try {
                        element = layout.select(elementPath);
                    } catch (IllegalArgumentException i) {
                        throw new IllegalArgumentException("Unable to resolve '" + m + "' in " + layout, i);
                    }
                    var offset = layout.byteOffset(elementPath);

                    return switch (element) {
                        case ValueLayout vl -> {
                            if (!type.equals(vl.carrier())) {
                                throw new IllegalArgumentException("The type " + type + " for method " + m +
                                        "does not match " + element);
                            }
                            yield new MethodInfo(m, type, LayoutInfo.of(vl), offset);
                        }
                        case GroupLayout gl ->
                                new MethodInfo(m, type, LayoutInfo.of(gl), offset);
                        case SequenceLayout sl -> {
                            MethodInfo info = new MethodInfo(m, type, LayoutInfo.of(sl), offset);
                            int noDimensions = info.layoutInfo().arrayInfo().orElseThrow().dimensions().size();
                            if (m.getParameterCount() != noDimensions) {
                                throw new IllegalArgumentException(
                                        "Sequence layout has a dimension of " + noDimensions +
                                                " and so, the  method parameter count does not" +
                                                " match for: " + m);
                            }
                            yield info;
                        }
                        default -> throw new IllegalArgumentException("Cannot map " + element + " for " + type);
                    };

                })
                .toList();
    }

    private static Stream<Method> setCandidates(Class<?> type,
                                                ParameterTypes parameterTypes,
                                                Predicate<Class<?>> parameterTypePredicate) {
        return candidates0(type, parameterTypes)
                .filter(m -> m.getReturnType() == void.class || m.getReturnType() == Void.class)
                .filter(m -> parameterTypePredicate.test(m.getParameterTypes()[0]));
    }

    private static Stream<Method> getCandidates(Class<?> type,
                                                ParameterTypes parameterTypes,
                                                Predicate<Class<?>> returnTypePredicate) {
        return candidates0(type, parameterTypes)
                .filter(m -> m.getReturnType() != void.class && m.getReturnType() != Void.class)
                .filter(m -> returnTypePredicate.test(m.getReturnType()));
    }

    enum ParameterTypes {
        GETTER(m -> m.getParameterCount() == 0),
        SETTER(m -> m.getParameterCount() == 1),
        ARRAY(m -> m.getParameterCount() >= 1 && Arrays.stream(m.getParameters())
                .map(Parameter::getType)
                .allMatch(t -> t.equals(long.class)));

        private final Predicate<Method> predicate;

        ParameterTypes(Predicate<Method> predicate) {
            this.predicate = predicate;
        }

        Predicate<Method> predicate() {
            return predicate;
        }

    }

    private static Stream<Method> candidates0(Class<?> type,
                                              ParameterTypes parameterTypes) {
        return Arrays.stream(type.getMethods())
                .filter(parameterTypes.predicate())
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> !m.isDefault());
    }

    private MethodHandle interfaceGetMethodHandleFor(MethodInfo methodInfo,
                                                     GroupLayout groupLayout) {

        // Todo: As the offset is zero, we can cache these mappers (per type and layout)
        SegmentInterfaceMapper<?> innerMapper = new SegmentInterfaceMapper<>(
                lookup,
                methodInfo.type(),
                groupLayout,
                0, // The actual offset is added later at invocation
                depth + 1);

        return innerMapper.getHandle();
    }

    private MethodHandle recordGetMethodHandleFor(MethodInfo methodInfo) {

        // Todo: As the offset is zero, we can cache these mappers (per type and layout)
        SegmentMapper<?> innerMapper = new SegmentRecordMapper<>(lookup,
                MapperUtil.castToRecordClass(methodInfo.type),
                (GroupLayout) methodInfo.layoutInfo().layout(),
                0, // The actual offset is added later at invocation
                depth + 1);

        return innerMapper.getHandle()
                .asType(MethodType.methodType(Object.class, MemorySegment.class, long.class));
    }

    private MethodHandle recordSetMethodHandleFor(MethodInfo methodInfo) {

        // Todo: As the offset is zero, we can cache these mappers (per type and layout)
        SegmentMapper<?> innerMapper = new SegmentRecordMapper<>(lookup,
                MapperUtil.castToRecordClass(methodInfo.type),
                (GroupLayout) methodInfo.layoutInfo().layout(),
                0, // The actual offset is added later at invocation
                depth + 1);

        return innerMapper.setHandle();
    }

    private void generateScalarGetter(ClassBuilder cb,
                                      ClassDesc classDesc,
                                      MethodInfo info) {

        String name = info.method().getName();
        ClassDesc returnDesc = desc(info.type());
        ScalarInfo scalarInfo = info.layoutInfo().scalarInfo().orElseThrow();

        var getDesc = MethodTypeDesc.of(returnDesc, scalarInfo.valueLayoutDesc(), CD_long);

        cb.withMethodBody(name, MethodTypeDesc.of(returnDesc), ACC_PUBLIC, cob -> {
                    cob.aload(0)
                            .getfield(classDesc, SEGMENT_FIELD_NAME, MEMORY_SEGMENT_CLASS_DESC)
                            .getstatic(VALUE_LAYOUTS_CLASS_DESC, scalarInfo.memberName(), scalarInfo.valueLayoutDesc());
                    offsetBlock(cob, classDesc, info.offset())
                            .invokeinterface(MEMORY_SEGMENT_CLASS_DESC, "get", getDesc);
                    // ireturn(), dreturn() etc.
                    info.layoutInfo().returnOp().accept(cob);
                }
        );
    }

    private void generateScalarSetter(ClassBuilder cb,
                                      ClassDesc classDesc,
                                      MethodInfo info) {

        String name = info.method().getName();
        ClassDesc parameterDesc = desc(info.type());
        ScalarInfo scalarInfo = info.layoutInfo().scalarInfo().orElseThrow();

        var setDesc = MethodTypeDesc.of(CD_void, scalarInfo.valueLayoutDesc(), CD_long, parameterDesc);

        cb.withMethodBody(name, MethodTypeDesc.of(CD_void, parameterDesc), ACC_PUBLIC, cob -> {
                    cob.aload(0)
                            .getfield(classDesc, SEGMENT_FIELD_NAME, MEMORY_SEGMENT_CLASS_DESC)
                            .getstatic(VALUE_LAYOUTS_CLASS_DESC, scalarInfo.memberName(), scalarInfo.valueLayoutDesc());
                    offsetBlock(cob, classDesc, info.offset());
                    // iload, dload, etc.
                    info.layoutInfo().paramOp().accept(cob, 1);
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
            long factor = arrayInfo.dimensions.stream()
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

    record MethodInfo(Method method,
                      Class<?> type,
                      LayoutInfo layoutInfo,
                      long offset) {
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
                .filter(i -> i.layoutInfo().arrayInfo.isEmpty())
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

    record LayoutInfo(MemoryLayout layout,
                      Optional<ScalarInfo> scalarInfo,
                      Optional<ArrayInfo> arrayInfo,
                      Consumer<CodeBuilder> returnOp,
                      ObjIntConsumer<CodeBuilder> paramOp) {


        private static LayoutInfo of(ValueLayout layout) {
            return switch (layout) {
                // Todo: Remove boolean?
                case ValueLayout.OfBoolean bo ->
                        LayoutInfo.ofScalar(bo, "JAVA_BOOLEAN", ValueLayout.OfBoolean.class, CodeBuilder::ireturn, CodeBuilder::iload);
                case ValueLayout.OfByte by ->
                        LayoutInfo.ofScalar(by, "JAVA_BYTE", ValueLayout.OfByte.class, CodeBuilder::ireturn, CodeBuilder::iload);
                case ValueLayout.OfShort sh ->
                        LayoutInfo.ofScalar(sh, "JAVA_SHORT", ValueLayout.OfShort.class, CodeBuilder::ireturn, CodeBuilder::iload);
                case ValueLayout.OfChar ch ->
                        LayoutInfo.ofScalar(ch, "JAVA_CHAR", ValueLayout.OfChar.class, CodeBuilder::ireturn, CodeBuilder::iload);
                case ValueLayout.OfInt in ->
                        LayoutInfo.ofScalar(in, "JAVA_INT", ValueLayout.OfInt.class, CodeBuilder::ireturn, CodeBuilder::iload);
                case ValueLayout.OfFloat fl ->
                        LayoutInfo.ofScalar(fl, "JAVA_FLOAT", ValueLayout.OfFloat.class, CodeBuilder::freturn, CodeBuilder::fload);
                case ValueLayout.OfLong lo ->
                        LayoutInfo.ofScalar(lo, "JAVA_LONG", ValueLayout.OfLong.class, CodeBuilder::lreturn, CodeBuilder::lload);
                case ValueLayout.OfDouble db ->
                        LayoutInfo.ofScalar(db, "JAVA_DOUBLE", ValueLayout.OfDouble.class, CodeBuilder::dreturn, CodeBuilder::dload);
                default ->
                        throw new IllegalArgumentException("Unable to map to a LayoutInfo: " + layout);
            };
        }

        static LayoutInfo of(GroupLayout layout) {
            return new LayoutInfo(layout, Optional.empty(), Optional.empty(), CodeBuilder::areturn, CodeBuilder::aload);
        }

        static LayoutInfo of(SequenceLayout layout) {
            return new LayoutInfo(layout, Optional.empty(), Optional.of(ArrayInfo.of(layout)), CodeBuilder::areturn, CodeBuilder::aload);
        }

        private static <T extends ValueLayout> LayoutInfo ofScalar(T layout,
                                                                   String memberName,
                                                                   Class<T> layoutType,
                                                                   Consumer<CodeBuilder> returnOp,
                                                                   ObjIntConsumer<CodeBuilder> paramOp) {
            return new LayoutInfo(layout, Optional.of(new ScalarInfo(memberName, desc(layoutType))), Optional.empty(), returnOp, paramOp);
        }

    }

    record ScalarInfo(String memberName,
                      ClassDesc valueLayoutDesc ){}

    record ArrayInfo(MemoryLayout elementLayout,
                     List<Long> dimensions) {

        static ArrayInfo of(SequenceLayout layout) {
            return recurse(new ArrayInfo(layout, new ArrayList<>()));
        }

        private static ArrayInfo recurse(ArrayInfo info) {
            if (!(info.elementLayout instanceof SequenceLayout sl)) {
                // We are done. Create an immutable record
                return new ArrayInfo(info.elementLayout(), List.copyOf(info.dimensions()));
            }
            info.dimensions().add(sl.elementCount());
            return recurse(new ArrayInfo(sl.elementLayout(), info.dimensions()));
        }

    }

    private static ClassDesc desc(Class<?> clazz) {
        return clazz.describeConstable()
                .orElseThrow();
    }

    /**
     * This class models composed record mappers.
     *
     * @param lookup       to use for reflective operations
     * @param type         new type to map to/from
     * @param layout       original layout
     * @param isExhaustive if mapping is exhaustive (always false)
     * @param getHandle    for get operations
     * @param setHandle    for set operations
     * @param toMapper     a function that goes from T to R
     * @param fromMapper   a function that goes from R to T
     * @param <T>          original mapper type
     * @param <R>          composed mapper type
     */
    // Records have trusted instance fields.
    @ValueBased
    record Mapped<T, R>(
            @Override MethodHandles.Lookup lookup,
            @Override Class<R> type,
            @Override GroupLayout layout,
            @Override boolean isExhaustive,
            @Override MethodHandle getHandle,
            @Override MethodHandle setHandle,
            Function<? super T, ? extends R> toMapper,
            Function<? super R, ? extends T> fromMapper
    ) implements SegmentMapper<R>, HasLookup {

        Mapped(MethodHandles.Lookup lookup,
               Class<R> type,
               GroupLayout layout,
               boolean isExhaustive,
               MethodHandle getHandle,
               MethodHandle setHandle,
               Function<? super T, ? extends R> toMapper,
               Function<? super R, ? extends T> fromMapper
        ) {
            this.lookup = lookup;
            this.type = type;
            this.layout = layout;
            this.isExhaustive = isExhaustive;
            this.toMapper = toMapper;
            this.fromMapper = fromMapper;
            MethodHandle toMh = findVirtual("mapTo").bindTo(this);
            this.getHandle = MethodHandles.filterReturnValue(getHandle, toMh);
            MethodHandle fromMh = findVirtual("mapFrom").bindTo(this);
            if (setHandle != null) {
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
                    false, // There is no way to evaluate exhaustiveness
                    original.getHandle(),
                    original.setHandle(),
                    toMapper,
                    fromMapper
            );
        }

        private static MethodHandle findVirtual(String name) {
            try {
                var mt = MethodType.methodType(Object.class, Object.class);
                return LOOKUP.findVirtual(Mapped.class, name, mt);
            } catch (ReflectiveOperationException e) {
                // Should not happen
                throw new InternalError(e);
            }
        }

    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static <T> List<T> concat(List<T> ... other) {
        return Arrays.stream(other)
                .reduce(new ArrayList<>(), (a, b) -> {
                    a.addAll(b);
                    return a;
                });
    }

    public static <T> SegmentInterfaceMapper<T> create(MethodHandles.Lookup lookup,
                                                       Class<T> type,
                                                       GroupLayout layout) {
        return new SegmentInterfaceMapper<>(lookup, type, layout, 0, 0);
    }
}