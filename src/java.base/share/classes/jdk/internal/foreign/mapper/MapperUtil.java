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

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class MapperUtil {

    private MapperUtil() {
    }

    public static <T> Class<T> requireImplementableInterfaceType(Class<T> type) {
        Objects.requireNonNull(type);
        if (!type.isInterface()) {
            throw newIae(type, "not an interface");
        }
        if (type.isHidden()) {
            throw newIae(type, "a hidden interface");
        }
        if (type.isSealed()) {
            throw newIae(type, "a sealed interface");
        }
        return type;
    }

    public static <T extends Record> Class<T> requireRecordType(Class<?> type) {
        Objects.requireNonNull(type);
        if (!type.isRecord()) {
            throw newIae(type, "is not a Record");
        }
        if (type.equals(Record.class)) {
            throw newIae(type, "not a real Record");
        }
        @SuppressWarnings("unchecked")
        Class<T> result = (Class<T>) type;
        return result;
    }

    public static <T> Class<T> requireArrayType(Class<T> type) {
        Objects.requireNonNull(type);
        if (!type.isArray()) {
            throw newIae(type, "not an array");
        }
        return type;
    }

    private static IllegalArgumentException newIae(Class<?> type, String trailingInfo) {
        return new IllegalArgumentException(type.getName() + " is " + trailingInfo);
    }

    // Type to layout methods

    public static GroupLayout groupLayoutOf(Class<?> type) {
        return (GroupLayout) layoutOf(type, type.getName());
    }

    private static MemoryLayout layoutOf(Class<?> type,
                                         String name) {
        // Sequence layout
        if (type.isArray()) {
            throw newIae(type, "an array. Arrays do not have a natural layout.");
        }

        // Struct layout
        Stream<UnifiedHolder> source;
        if (type.isRecord()) {
            source = Arrays.stream(
                            requireRecordType(type)
                                    .getRecordComponents())
                    .map(MapperUtil::holderFor);
        } else if (type.isInterface()) {
            source =
                    mappableMethods(
                            requireImplementableInterfaceType(type))
                            .map(MapperUtil::holderFor);
        } else {
            throw noSuchNaturalLayout(type, name);
        }

        return setNameIfNonNull(
                MemoryLayout.structLayout(source
                        .map(h -> resolve(type, h))
                        .toArray(MemoryLayout[]::new)),
                name);
    }

    // Support methods and classes

    @SuppressWarnings("unchecked")
    private static <T extends MemoryLayout> T setNameIfNonNull(T layout,
                                                               String name) {
        return name != null
                ? (T) layout.withName(name)
                : layout;
    }

    private static IllegalArgumentException noSuchNaturalLayout(Class<?> type, String name) {
        return new IllegalArgumentException(
                "Unable to find a natural layout for '" + name + "' using " + type);
    }

    private static UnifiedHolder holderFor(Method method) {
        return new UnifiedHolder(method.getName(), method.getReturnType());
    }

    private static UnifiedHolder holderFor(RecordComponent recordComponent) {
        return new UnifiedHolder(recordComponent.getName(), recordComponent.getType());
    }

    private static UnifiedHolder holderForArrayComponent(String name,
                                                         Class<?> componentType) {
        return new UnifiedHolder(name, componentType);
    }

    private static MemoryLayout resolve(Class<?> type, UnifiedHolder h) {
        return switch (h) {
            case UnifiedHolder(var n, var t, var l)
                    when l.isPresent() -> l.get();
            case UnifiedHolder(var n, var t, var l)
                    when t.isRecord() -> layoutOf(t, n);
            case UnifiedHolder(var n, var t, var l)
                    when t.isInterface() -> layoutOf(t, n);
            default -> throw noSuchNaturalLayout(type, h.name());
        };
    }

    private record UnifiedHolder(String name,
                                 Class<?> type,
                                 Optional<ValueLayout> valueLayout) {

        public UnifiedHolder(String name, Class<?> type) {
            this(name, type, valueLayoutFor(type).map(l -> setNameIfNonNull(l, name)));
        }

    }

    private static Stream<Method> mappableMethods(Class<?> type) {
        return Arrays.stream(type.getMethods())
                .filter(f -> Modifier.isPublic(f.getModifiers()))
                // Consider only pure getters as in the record case
                .filter(f -> f.getParameterCount() == 0)
                .filter(f -> !f.getReturnType().equals(void.class));
    }

    // ValueLayout mapping

    private static Optional<ValueLayout> valueLayoutFor(Class<?> type) {
        return Optional.ofNullable(VALUE_LAYOUT_MAP.get(type));
    }

    private static final Map<Class<?>, ValueLayout> VALUE_LAYOUT_MAP =
            valueLayouts()
                    .collect(Collectors.toUnmodifiableMap(
                            ValueLayout::carrier,
                            Function.identity()));

    private static Stream<ValueLayout> valueLayouts() {
        return Arrays.stream(ValueLayout.class.getDeclaredFields())
                .filter(f -> Modifier.isPublic(f.getModifiers()))
                .filter(f -> Modifier.isStatic(f.getModifiers()))
                .filter(f -> !f.getName().endsWith("UNALIGNED"))
                .map(MapperUtil::staticFieldValueOrThrow)
                .filter(ValueLayout.class::isInstance)
                .map(ValueLayout.class::cast);
    }

    private static Object staticFieldValueOrThrow(Field f) {
        try {
            return f.get(null);
        } catch (IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }


}
