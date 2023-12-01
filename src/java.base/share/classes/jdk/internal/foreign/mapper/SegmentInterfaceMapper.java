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
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.mapper.SegmentBacked;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.constant.ConstantDescs.*;
import static jdk.internal.classfile.Classfile.*;

/**
 * A record mapper that is matching components of an interface with elements in a GroupLayout.
 *
 * @param lookup       to use for reflective operations
 * @param type         record type to map to/from
 * @param layout       group layout to use for matching record components
 * @param isExhaustive if mapping is exhaustive
 * @param <T>          mapper type
 */
// Records have trusted instance fields.
@ValueBased
public record SegmentInterfaceMapper<T>(
            @Override MethodHandles.Lookup lookup,
            @Override Class<T> type,
            @Override GroupLayout layout,
            long offset,
            int depth,
            Class<T> implClass,
            @Override boolean isExhaustive,
            // (MemorySegment, long)T
            @Override MethodHandle getHandle,
            // (MemorySegment, long, T)void
            @Override MethodHandle setHandle) implements SegmentMapper<T>, HasLookup {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final Set<String> SEGMENT_BACKED_METHODS =
            Arrays.stream(SegmentBacked.class.getMethods())
                    .map(Method::getName)
                    .collect(Collectors.toSet());

    private static final ClassDesc VALUE_LAYOUTS_CLASS_DESC = desc(ValueLayout.class);
    private static final ClassDesc MEMORY_SEGMENT_CLASS_DESC = desc(MemorySegment.class);

    public SegmentInterfaceMapper(MethodHandles.Lookup lookup,
                                  Class<T> type,
                                  GroupLayout layout,
                                  long offset,
                                  int depth) {
        this(lookup, type, layout, offset, depth, null, false, null, null);
    }

    // Canonical constructor in which we ignore some of the
    // input values and derive them internally instead.
    public SegmentInterfaceMapper(MethodHandles.Lookup lookup,
                                  Class<T> type,
                                  GroupLayout layout,
                                  long offset,
                                  int depth,
                                  Class<T> implClass,        // Ignored
                                  boolean isExhaustive,      // Ignored
                                  MethodHandle getHandle,    // Ignored
                                  MethodHandle setHandle) {  // Ignored
        this.lookup = lookup;
        this.type = type;
        this.layout = layout;
        this.offset = offset;
        this.depth = depth;
        List<MethodInfo> getMethods = mappableGetMethods(type, layout);
        List<MethodInfo> setMethods = mappableSetMethods(type, layout);
        assertMappingsCorrect(type, layout, getMethods);
        assertMappingsCorrect(type, layout, setMethods);
        this.implClass = generateClass(getMethods, setMethods);
        Handles handles = handles();
        this.isExhaustive = handles.isExhaustive();
        this.getHandle = handles.getHandle();
        this.setHandle = handles.setHandle();
    }

    @Override
    public <R> SegmentMapper<R> map(Class<R> newType,
                                    Function<? super T, ? extends R> toMapper,
                                    Function<? super R, ? extends T> fromMapper) {
        return Mapped.of(this, newType, toMapper, fromMapper);
    }

    private Class<T> generateClass(List<MethodInfo> getMethods, List<MethodInfo> setMethods) {
        ClassDesc classDesc = ClassDesc.of(type.getSimpleName() + "InterfaceMapper");
        ClassDesc interfaceClassDesc = desc(type);
        ClassLoader loader = type.getClassLoader();

        byte[] bytes = Classfile.of(Classfile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofClassLoading(loader)))
                .build(classDesc, cb -> {
            // public final
            cb.withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SUPER);
            // extends Object
            cb.withSuperclass(CD_Object);
            // implements "type"
            cb.withInterfaceSymbols(interfaceClassDesc);
            // private final MemorySegment segment;
            cb.withField("segment", MEMORY_SEGMENT_CLASS_DESC,ACC_PRIVATE | ACC_FINAL);
            // private final long offset;
            cb.withField("offset", CD_long, ACC_PRIVATE | ACC_FINAL);

            // Canonical Constructor
            // xInterfaceMapper(@Override MemorySegment segment, @Override long offset) {
            //     this.segment = segment;
            //     this.offset = offset;
            // }
            cb.withMethodBody(ConstantDescs.INIT_NAME, MethodTypeDesc.of(CD_void, MEMORY_SEGMENT_CLASS_DESC, CD_long), Classfile.ACC_PUBLIC, cob ->
                    cob.aload(0)
                            // Call Object's constructor
                            .invokespecial(CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, false)
                            // Set "segment"
                            .aload(0)
                            .aload(1)
                            .putfield(classDesc, "segment", MEMORY_SEGMENT_CLASS_DESC)
                            // Set "offset"
                            .aload(0)
                            .lload(2)
                            .putfield(classDesc, "offset", CD_long)
                            .return_());

            // If the interface implements SegmentBacked then we need to add those methods
            if (SegmentBacked.class.isAssignableFrom(type)) {

                // @Override
                // MemorySegment segment() {
                //     return segment;
                // }
                cb.withMethodBody("segment", MethodTypeDesc.of(MEMORY_SEGMENT_CLASS_DESC), Classfile.ACC_PUBLIC, cob ->
                        cob.aload(0)
                                .getfield(classDesc, "segment", MEMORY_SEGMENT_CLASS_DESC)
                                .areturn()
                );

                // @Override
                // long offset() {
                //     return offset;
                // }
                cb.withMethodBody("offset", MethodTypeDesc.of(CD_long), Classfile.ACC_PUBLIC, cob ->
                        cob.aload(0)
                                .getfield(classDesc, "offset", CD_long)
                                .lreturn()
                );
            }

            for (MethodInfo method:getMethods) {
                // @Override
                // <t> gX() {
                //     return segment.get(JAVA_t, offset);
                // }
                generateGetter(cb, classDesc, method);
            }

            for (MethodInfo method : setMethods) {
                generateSetter(cb, classDesc, method);
            }

            // @Override
            // int hashCode() {
            //     return System.identityHashCode(this);
            // }
            cb.withMethodBody("hashCode", MethodTypeDesc.of(CD_int), Classfile.ACC_PUBLIC | ACC_FINAL, cob ->
                    cob.aload(0)
                            .invokestatic(desc(System.class), "identityHashCode", MethodTypeDesc.of(CD_int, CD_Object))
                            .ireturn()
            );

            // @Override
            // boolean equals(Object o) {
            //     return this == o;
            // }
            cb.withMethodBody("equals", MethodTypeDesc.of(CD_boolean, CD_Object), Classfile.ACC_PUBLIC | ACC_FINAL, cob -> {
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
            generateToString(cb, classDesc, getMethods);

        });
        try {
            @SuppressWarnings("unchecked")
            Class<T> c = (Class<T>) lookup
                    .defineHiddenClass(bytes, true)
                    .lookupClass();
            return c;
        } catch (IllegalAccessException | VerifyError e) {
            throw new IllegalArgumentException("Unable to define interface mapper proxy class for " + type, e);
        }
    }

    // Private methods and classes

    // This method is using a partially initialized mapper
    private Handles handles() {

        // The types for the constructor/components
        List<MethodInfo> componentTypes = mappableGetMethods(type, layout);

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
        for (MethodInfo component: methods) {
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


    private static List<MethodInfo> mappableGetMethods(Class<?> type,
                                                       GroupLayout layout) {
        // The types for the components
        List<Method> methods = candidates(type, 0)
                .filter(m -> m.getReturnType() != void.class && m.getReturnType() != Void.class)
                .filter(m -> !isSegmentBacked(type, m))
                .toList();

        return mappableMethods(methods, layout, Method::getReturnType);
    }

    private static boolean isSegmentBacked(Class<?> type, Method method) {
        return SegmentBacked.class.isAssignableFrom(type) &&
                SEGMENT_BACKED_METHODS.contains(method.getName());
    }

    private static List<MethodInfo> mappableSetMethods(Class<?> type,
                                                       GroupLayout layout) {
        List<Method> methods = candidates(type, 1)
                .filter(m -> m.getReturnType() == void.class || m.getReturnType() == Void.class)
                .toList();

        return mappableMethods(methods, layout, m -> m.getParameterTypes()[0]);
    }


    private static List<MethodInfo> mappableMethods(List<Method> methods,
                                                    GroupLayout layout,
                                                    Function<? super Method, ? extends Class<?>> typeExtractor) {

        return methods.stream()
                .map(m -> {
                    var type = typeExtractor.apply(m);
                    var elementPath = MemoryLayout.PathElement.groupElement(m.getName());
                    var element = layout.select(elementPath);
                    if (element instanceof ValueLayout vl) {
                        if (!type.equals(vl.carrier())) {
                            throw new IllegalArgumentException("The type " + type + " for method " + m +
                                    "does not match " + element);
                        }
                    }
                    var offset = layout.byteOffset(elementPath);
                    return new MethodInfo(m, type.describeConstable().orElseThrow(), valueLayoutInfo(element), offset);
                })
                .toList();
    }

    private static Stream<Method> candidates(Class<?> type, int paramCount) {
        return Arrays.stream(type.getMethods())
                .filter(m -> m.getParameterCount() == paramCount)
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> !m.isDefault());
    }

    private void generateGetter(ClassBuilder cb,
                                ClassDesc classDesc,
                                MethodInfo info) {

        String name = info.method().getName();
        ClassDesc returnDesc = info.desc();
        ClassDesc layoutDesc = info.valueLayoutInfo().desc();

        cb.withMethodBody(name, MethodTypeDesc.of(returnDesc), Classfile.ACC_PUBLIC, cob -> {
                    cob.aload(0)
                            .getfield(classDesc, "segment", MEMORY_SEGMENT_CLASS_DESC)
                            .getstatic(VALUE_LAYOUTS_CLASS_DESC, info.valueLayoutInfo().name(), layoutDesc)
                            .aload(0)
                            .getfield(classDesc, "offset", CD_long);
                    if (info.offset() != 0) {
                        cob.ldc(info.offset())
                                .ladd();
                    }
                    var getDesc = MethodTypeDesc.of(returnDesc, layoutDesc, CD_long);
                    cob.invokeinterface(MEMORY_SEGMENT_CLASS_DESC, "get", getDesc);
                    // lreturn(), dreturn() etc.
                    info.valueLayoutInfo().returnOp().accept(cob);
                }
        );
    }

    private void generateSetter(ClassBuilder cb,
                                ClassDesc classDesc,
                                MethodInfo info) {

        String name = info.method().getName();
        ClassDesc parameterDesc = info.desc();
        ClassDesc layoutDesc = info.valueLayoutInfo().desc();

        cb.withMethodBody(name, MethodTypeDesc.of(CD_void, parameterDesc), Classfile.ACC_PUBLIC, cob -> {
                    cob     .aload(0)
                            .getfield(classDesc, "segment", MEMORY_SEGMENT_CLASS_DESC)
                            .getstatic(VALUE_LAYOUTS_CLASS_DESC, info.valueLayoutInfo().name(), layoutDesc)
                            .aload(0)
                            .getfield(classDesc, "offset", CD_long);
                    if (info.offset() != 0) {
                        cob     .ldc(info.offset())
                                .ladd();
                    }
                    var setDesc = MethodTypeDesc.of(CD_void, layoutDesc, CD_long, parameterDesc);
                    cob     .iload(1)
                            .invokeinterface(MEMORY_SEGMENT_CLASS_DESC, "set", setDesc)
                            .return_();
                }
        );

    }

    record MethodInfo(Method method,
                      ClassDesc desc,
                      LayoutInfo valueLayoutInfo,
                      long offset) {}

    private void generateToString(ClassBuilder cb,
                                  ClassDesc classDesc,
                                  List<MethodInfo> getMethods) {
        // Foo[g0()=\u0001, g1()=\u0001, ...]
        var recipe = getMethods.stream()
                .map(m -> String.format("%s()=\u0001", m.method().getName()))
                .collect(Collectors.joining(", ", type.getSimpleName() + "[", "]"));

        DirectMethodHandleDesc bootstrap = ConstantDescs.ofCallsiteBootstrap(
                desc(StringConcatFactory.class),
                "makeConcatWithConstants",
                CD_CallSite,
                CD_String, CD_Object.arrayType()
        );

        List<ClassDesc> getDescriptions = classDescs(getMethods);

        DynamicCallSiteDesc desc = DynamicCallSiteDesc.of(
                bootstrap,
                "toString",
                MethodTypeDesc.of(CD_String, getDescriptions), // String, g0, g1, ...
                recipe
        );

        cb.withMethodBody("toString",
                MethodTypeDesc.of(CD_String),
                Classfile.ACC_PUBLIC | ACC_FINAL,
                cob -> {
                    for (int i = 0; i < getMethods.size(); i++) {
                        var name = getMethods.get(i).method().getName();
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
                .map(MethodInfo::desc)
                .toList();
    }

    record LayoutInfo(MemoryLayout layout,
                      String name,
                      ClassDesc desc,
                      Consumer<CodeBuilder> returnOp) {
    }

    private static LayoutInfo valueLayoutInfo(MemoryLayout layout) {
        return switch (layout) {
            // Todo: Remove boolean?
            case ValueLayout.OfBoolean _ -> new LayoutInfo(layout,"JAVA_BOOLEAN", desc(ValueLayout.OfBoolean.class), CodeBuilder::ireturn);
            case ValueLayout.OfByte _    -> new LayoutInfo(layout,"JAVA_BYTE", desc(ValueLayout.OfByte.class), CodeBuilder::ireturn);
            case ValueLayout.OfShort _   -> new LayoutInfo(layout,"JAVA_SHORT", desc(ValueLayout.OfShort.class), CodeBuilder::ireturn);
            case ValueLayout.OfChar _    -> new LayoutInfo(layout,"JAVA_CHAR", desc(ValueLayout.OfChar.class), CodeBuilder::ireturn);
            case ValueLayout.OfInt _     -> new LayoutInfo(layout,"JAVA_INT", desc(ValueLayout.OfInt.class), CodeBuilder::ireturn);
            case ValueLayout.OfFloat _   -> new LayoutInfo(layout,"JAVA_FLOAT", desc(ValueLayout.OfFloat.class), CodeBuilder::freturn);
            case ValueLayout.OfLong _    -> new LayoutInfo(layout,"JAVA_LONG", desc(ValueLayout.OfLong.class), CodeBuilder::lreturn);
            case ValueLayout.OfDouble _  -> new LayoutInfo(layout,"JAVA_DOUBLE", desc(ValueLayout.OfDouble.class), CodeBuilder::dreturn);
            default -> throw new IllegalArgumentException("Unable to map to a LayoutInfo: " + layout);
        };
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
    record Mapped<T, R> (
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

}