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

import jdk.internal.foreign.mapper.accessor.AccessorInfo;
import jdk.internal.foreign.mapper.accessor.Accessors;
import jdk.internal.foreign.mapper.accessor.ValueType;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public abstract class AbstractSegmentMapper<T> {

    @Stable
    private final MethodHandles.Lookup lookup;
    @Stable
    private final Class<T> type;
    @Stable
    private final MemoryLayout layout;
    private final boolean leaf;
    private final MapperCache mapperCache;
    protected Accessors accessors;

    protected AbstractSegmentMapper(MethodHandles.Lookup lookup,
                                    Class<T> type,
                                    GroupLayout layout,
                                    boolean leaf,
                                    ValueType valueType,
                                    UnaryOperator<Class<T>> typeInvariantChecker,
                                    BiFunction<Class<?>, GroupLayout, Accessors> accessorFactory) {
        this.lookup = lookup;
        this.type = typeInvariantChecker.apply(type);
        this.layout = layout;
        this.leaf = leaf;
        this.mapperCache = MapperCache.of(lookup);
        this.accessors = accessorFactory.apply(type, layout);

        List<Method> unsupportedAccessors = accessors.stream(k -> !k.isSupportedFor(valueType))
                .map(AccessorInfo::method)
                .toList();
        if (!unsupportedAccessors.isEmpty()) {
            throw new IllegalArgumentException(
                    "The following accessors are not supported for " + valueType + ": " + unsupportedAccessors);
        }
        MapperUtil.assertMappingsCorrectAndTotal(type, layout, accessors);
    }

    // Custom mapper
    protected AbstractSegmentMapper(MethodHandles.Lookup lookup,
                                    Class<T> type,
                                    MemoryLayout layout) {
        this.lookup = lookup;
        this.type = type;
        this.layout = layout;
        this.leaf = false;
        this.mapperCache = null;
        this.accessors = null;
    }

    public final Class<T> type() {
        return type;
    }

    public final MemoryLayout layout() {
        return layout;
    }

    abstract MethodHandle getter();

    abstract MethodHandle setter();

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "[" +
                "lookup=" + lookup + ", " +
                "type=" + type + ", " +
                "layout=" + layout + "]";
    }

    // Protected methods

    protected final MethodHandles.Lookup lookup() {
        return lookup;
    }

    protected final Accessors accessors() {
        return accessors;
    }

    protected final MapperCache mapperCache() {
        return mapperCache;
    }

    protected final boolean isLeaf() {
        return leaf;
    }

    // Abstract methods

    // -> (MemorySegment, long)T if isLeaf()
    // -> (MemorySegment, long)Object if !isLeaf()
    protected abstract MethodHandle computeGetHandle();

    // (MemorySegment, long, T)void if isLeaf()
    // (MemorySegment, long, Object)void if !isLeaf()
    protected abstract MethodHandle computeSetHandle();

}