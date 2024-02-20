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
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class MapperUtil {

    private MapperUtil() {
    }

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();


    public static final MethodHandle GETTER;
    public static final MethodHandle SETTER;
    private static final MethodHandle MAP_TO;
    private static final MethodHandle MAP_FROM;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        try {
            GETTER = lookup.findStatic(MapperUtil.class, "getter0",
                    MethodType.methodType(Object.class, SegmentMapper.Getter.class, MemorySegment.class, long.class));
            SETTER = lookup.findStatic(MapperUtil.class, "setter0",
                    MethodType.methodType(void.class, SegmentMapper.Setter.class, MemorySegment.class, long.class, Object.class));

            var mt = MethodType.methodType(Object.class, Function.class, Object.class);
            MAP_TO = lookup.findStatic(MapperUtil.class, "mapTo", mt);
            MAP_FROM = lookup.findStatic(MapperUtil.class, "mapFrom", mt);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final String DEBUG =
            GetPropertyAction.privilegedGetProperty("java.lang.foreign.mapper.debug", "");

    public static boolean isDebug() {
        return !DEBUG.isEmpty();
    }

    public static <T extends Record> Class<T> requireRecordType(Class<?> type) {
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

    // Function to MethodHandle methods

    // Used reflective when obtaining a MethodHandle
    static <T> T getter0(SegmentMapper.Getter<T> getter,
                         MemorySegment segment, long offset) {
        return getter.get(segment, offset);
    }

    // Used reflective when obtaining a MethodHandle
    static <T> void setter0(SegmentMapper.Setter<T> setter,
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
                    "This one-way mapper cannot map from " + newType + " to " + originalMapper.type());
        });
    }

}
