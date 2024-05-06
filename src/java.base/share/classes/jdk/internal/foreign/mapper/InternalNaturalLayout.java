/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class InternalNaturalLayout {

    // Suppresses default constructor, ensuring non-instantiability.
    private InternalNaturalLayout() {}

    // Type to layout methods

    public static GroupLayout groupLayoutOf(Class<?> type) {
        return (GroupLayout) layoutOf(type, null);
    }

    private static MemoryLayout layoutOf(Class<?> type,
                                         String name) {
        // Sequence layout
        if (type.isArray()) {
            throw MapperUtil.newIae(type, "an array. Arrays do not have a natural layout.");
        }

        // Struct layout
        Stream<UnifiedHolder> source;
        if (type.isRecord()) {
            source = Arrays.stream(
                            MapperUtil.requireRecord(type)
                                    .getRecordComponents())
                    .map(InternalNaturalLayout::holderFor);
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
        return new UnifiedHolder(method.getName(), method.getParameterTypes()[0]);
    }

    private static UnifiedHolder holderFor(RecordComponent recordComponent) {
        return new UnifiedHolder(recordComponent.getName(), recordComponent.getType());
    }

    private static MemoryLayout resolve(Class<?> type, UnifiedHolder h) {
        return switch (h) {
            case UnifiedHolder(var _, var _, var l) when l.isPresent()   -> l.get();
            case UnifiedHolder(var n, var t, var _) when t.isRecord()    -> layoutOf(t, n);
            case UnifiedHolder(var n, var t, var _) when t.isInterface() -> layoutOf(t, n);
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
                .filter(m -> !m.isDefault())
                .filter(f -> f.getReturnType().equals(void.class))
                .map(InternalNaturalLayout::requirePureGetter);
    }

    private static Method requirePureGetter(Method method) {
        if (method.getParameterCount() != 1) {
            throw new IllegalArgumentException("Array accessors do not correspond to any natural layout: "+method);
        }
        return method;
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
                .map(InternalNaturalLayout::staticFieldValueOrThrow)
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
