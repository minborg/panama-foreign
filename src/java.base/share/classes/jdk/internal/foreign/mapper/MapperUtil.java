/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.foreign.mapper.accessor.AccessorInfo;
import jdk.internal.foreign.mapper.accessor.Accessors;
import sun.security.action.GetPropertyAction;

import java.lang.constant.ClassDesc;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.mapper.RecordMapper;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class MapperUtil {

    private MapperUtil() {
    }

    static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();

    public static final MethodHandle GETTER;
    public static final MethodHandle SETTER;
    private static final MethodHandle MAP_TO;
    private static final MethodHandle MAP_FROM;

    final static class MemorySegmentAccessor {

        private static final Map<ValueLayout, MethodHandle> MEMORY_SEGMENT_GETTERS = new ConcurrentHashMap<>();
        private static final Map<ValueLayout, MethodHandle> MEMORY_SEGMENT_SETTERS = new ConcurrentHashMap<>();

        private MemorySegmentAccessor() {}

        public static MethodHandle getter(ValueLayout layout) {
            return clearIfTooLarge(MEMORY_SEGMENT_GETTERS)
                    .computeIfAbsent(layout, MemorySegmentAccessor::getter0);
        }

        public static MethodHandle setter(ValueLayout layout) {
            return clearIfTooLarge(MEMORY_SEGMENT_SETTERS)
                    .computeIfAbsent(layout, MemorySegmentAccessor::setter0);
        }

        private static MethodHandle getter0(ValueLayout layout) {
            try {
                MethodHandle getter = LOCAL_LOOKUP.findVirtual(MemorySegment.class, "get",
                        MethodType.methodType(layout.carrier(), interfaceType(layout), long.class));
                return MethodHandles.insertArguments(getter, 1, layout);
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("There is no MemorySegment::get operation for " + layout, e);
            }
        }

        private static MethodHandle setter0(ValueLayout layout) {
            try {
                MethodHandle getter = LOCAL_LOOKUP.findVirtual(MemorySegment.class, "set",
                        MethodType.methodType(void.class, interfaceType(layout), long.class, layout.carrier()));
                return MethodHandles.insertArguments(getter, 1, layout);
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("There is no MemorySegment::set operation for " + layout, e);
            }
        }

        private static Class<?> interfaceType(ValueLayout layout) {
            return layout.getClass().getInterfaces()[0];
        }

        private static Map<ValueLayout, MethodHandle> clearIfTooLarge(Map<ValueLayout, MethodHandle> map) {
            // For the 7 primitives, hold at most 8 variants each on average
            // This protects against DOS attacks calling with a large number of alignment variants
            if (map.size() > 7 * 8) {
                map.clear();
            }
            return map;
        }

    }

    static {

        try {
            GETTER = LOCAL_LOOKUP.findStatic(MapperUtil.class, "getter0",
                    MethodType.methodType(Object.class, RecordMapper.Getter.class, MemorySegment.class, long.class));
            SETTER = LOCAL_LOOKUP.findStatic(MapperUtil.class, "setter0",
                    MethodType.methodType(void.class, RecordMapper.Setter.class, MemorySegment.class, long.class, Object.class));

            var mt = MethodType.methodType(Object.class, Function.class, Object.class);
            MAP_TO = LOCAL_LOOKUP.findStatic(MapperUtil.class, "mapTo", mt);
            MAP_FROM = LOCAL_LOOKUP.findStatic(MapperUtil.class, "mapFrom", mt);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final String DEBUG =
            GetPropertyAction.privilegedGetProperty("java.lang.foreign.mapper.debug", "");

    public static boolean isDebug() {
        return !DEBUG.isEmpty();
    }

    public static <T extends Record> Class<T> requireRecord(Class<?> type) {
        Objects.requireNonNull(type);
        if (!type.isRecord()) {
            throw newIae(type, "is not a Record");
        }
        if (type.equals(Record.class)) {
            throw newIae(type, "not a real Record");
        }
        assertNotDeclaringTypeParameters(type);
        @SuppressWarnings("unchecked")
        Class<T> result = (Class<T>) type;
        return result;
    }

    public static <T> Class<T> requireFunctionalInterface(Class<T> type) {
        requireInterface(type);
        if (type.getAnnotation(FunctionalInterface.class) == null) {
            throw newIae(type, "is not a @FunctionalInterface");
        }
        return type;
    }

    public static <T> Class<T> requireInterface(Class<T> type) {
        Objects.requireNonNull(type);
        if (!type.isInterface()) {
            throw newIae(type, "is not an interface");
        }
        if (type.isHidden()) {
            throw newIae(type, "is hidden");
        }
        if (type.isSealed()) {
            throw newIae(type, "is sealed");
        }
        assertNotDeclaringTypeParameters(type);
        return type;
    }

    private static void assertNotDeclaringTypeParameters(Class<?> type) {
        if (type.getTypeParameters().length != 0) {
            throw newIae(type, "directly declaring type parameters: " + type.toGenericString());
        }
    }

    static IllegalArgumentException newIae(Class<?> type, String trailingInfo) {
        return new IllegalArgumentException(type.getName() + " is " + trailingInfo);
    }

    public static ClassDesc desc(Class<?> clazz) {
        return clazz.describeConstable()
                .orElseThrow();
    }

    static void assertMappingsCorrectAndTotal(Class<?> type,
                                              GroupLayout layout,
                                              Accessors accessors) {

        var nameMappingCounts = layout.memberLayouts().stream()
                .map(MemoryLayout::name)
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<AccessorInfo> allMethods = accessors.stream().toList();

        // Make sure we have all components distinctly mapped
        for (AccessorInfo component : allMethods) {
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
                .map(AccessorInfo::method)
                .collect(Collectors.toSet());

        var typeMethods = Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getAccessor)
                    .toList();

        var missing = typeMethods.stream()
                .filter(Predicate.not(accessorMethods::contains))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Unable to map methods: " + missing);
        }

    }

    static void assertReturnType(MethodHandle handle, Class<?> type, String variant) {
        Class<?> returnType = handle.type().returnType();
        if (!returnType.equals(type)) {
            throw new IllegalArgumentException(
                    "The return type of the " + variant + " " + handle + " is " + returnType + " and not " + type);
        }
    }

    static void assertParameterType(MethodHandle handle, int num, Class<?> type, String variant) {
        Class<?> parameterType = handle.type().parameterType(num);
        if (!parameterType.equals(type)) {
            throw new IllegalArgumentException(
                    "The parameter with index " + num + " of " + variant + " " + handle + " is " + parameterType + " and not " + type);
        }
    }

    // Function to MethodHandle methods

    // Used reflective when obtaining a MethodHandle
    static <T> T getter0(RecordMapper.Getter<T> getter,
                         MemorySegment segment, long offset) {
        return getter.get(segment, offset);
    }

    // Used reflective when obtaining a MethodHandle
    static <T> void setter0(RecordMapper.Setter<T> setter,
                            MemorySegment segment, long offset, T t) {
        setter.set(segment, offset, t);
    }

    // SegmentMapper mapping methods

    // Used reflective when obtaining a MethodHandle
    static <T, R> R mapTo(Function<? super T, ? extends R> toMapper,
                          T t) {
        return toMapper.apply(t);
    }

    // Used reflective when obtaining a MethodHandle
    static <T, R> T mapFrom(Function<? super R, ? extends T> fromMapper,
                            R r) {
        return fromMapper.apply(r);
    }

    // Mapping factories

    public static <T, R> SegmentMapper<R> map(SegmentMapper<T> originalMapper,
                                              Class<R> newType,
                                              Function<? super T, ? extends R> toMapper,
                                              Function<? super R, ? extends T> fromMapper) {
        MethodHandle toMh = MAP_TO.bindTo(toMapper);
        MethodHandle getter = MethodHandles.filterReturnValue(originalMapper.getter(), toMh);
        MethodHandle fromMh = MAP_FROM.bindTo(fromMapper);
        MethodHandle setter = MethodHandles.filterArguments(originalMapper.setter(), 2, fromMh);
        return new SegmentMapperImpl<>(newType, originalMapper.layout(), getter, setter);
    }

    public static <T, R> SegmentMapper<R> map(SegmentMapper<T> originalMapper,
                                             Class<R> newType,
                                             Function<? super T, ? extends R> toMapper) {
        return map(originalMapper, newType, toMapper, _ -> {
            throw new UnsupportedOperationException(
                    "This one-way mapper cannot map from " + newType + " to " + originalMapper.carrier());
        });
    }

    // SegmentMapper static factories
/*
    public static RecordMapper<String> ofString(int length) {
            RecordMapper.Getter<String> getter = MapperUtil::toStringDirect;
            RecordMapper.Setter<String> setter = MapperUtil::fromStringDirect;
            return RecordMapper.of(String.class, MemoryLayout.sequenceLayout(length, JAVA_BYTE), getter, setter);
    }

    static String toStringDirect(MemorySegment segment, long offset) {
        return segment.asSlice(offset).getString(offset);
    }

    static void fromStringDirect(MemorySegment segment, long offset, String value) {
        segment.setString(offset, value);
    }

    public static <T> RecordMapper<T> ofPrimitive(Class<T> wrapperType, ValueLayout layout) {
        Class<?> primitiveType = layout.carrier();
        Class<?> layoutInterfaceType = layout.getClass().getInterfaces()[0];
        String fromObjName = primitiveType.toString()+"Value";

        try {
            MethodHandle toObj = LOCAL_LOOKUP.findStatic(wrapperType, "valueOf", MethodType.methodType(wrapperType, primitiveType));
            MethodHandle getter = LOCAL_LOOKUP.findVirtual(MemorySegment.class, "get", MethodType.methodType(primitiveType, layoutInterfaceType, long.class));
            getter = MethodHandles.insertArguments(getter, 1, layout);
            getter = MethodHandles.filterReturnValue(getter, toObj)
                    // Todo: remove
                    .asType(MethodType.methodType(Object.class, MemorySegment.class, long.class));
            MethodHandle fromObj = LOCAL_LOOKUP.findVirtual(wrapperType, fromObjName, MethodType.methodType(primitiveType));
            // Todo: remove
            fromObj = fromObj.asType(fromObj.type().changeParameterType(0, Object.class));
            MethodHandle setter = LOCAL_LOOKUP.findVirtual(MemorySegment.class, "set", MethodType.methodType(void.class, layoutInterfaceType, long.class, primitiveType));
            setter = MethodHandles.insertArguments(setter, 1, layout);
            setter = MethodHandles.filterArguments(setter, 2, fromObj);
            return RecordMapper.of(wrapperType, layout, getter, setter);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(e);
        }
    }*/


}
