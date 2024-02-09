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
import jdk.internal.foreign.mapper.accessor.AccessorInfo;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class maintains a cache of seen sub-mappers, so they do not have to be created more
 * than once. Creating a sub-mapper is a relatively expensive operation.
 */
@ValueBased
final class MapperCache {

    private final MethodHandles.Lookup lookup;
    private final Map<CacheKey, SegmentMapper<?>> subMappers;

    private MapperCache(MethodHandles.Lookup lookup) {
        this.lookup = lookup;
        this.subMappers = new HashMap<>();
    }

    MethodHandle recordGetMethodHandleFor(AccessorInfo accessorInfo) {
        return cachedRecordMapper(accessorInfo)
                .getHandle();
    }

    MethodHandle recordSetMethodHandleFor(AccessorInfo accessorInfo) {
        return cachedRecordMapper(accessorInfo)
                .setHandle();
    }


    private SegmentMapper<?> cachedRecordMapper(AccessorInfo accessorInfo) {
        return cachedRecordMapper(accessorInfo.type(), accessorInfo.targetLayout());
    }

    SegmentMapper<?> cachedRecordMapper(Class<?> type, GroupLayout layout) {
        return subMappers.computeIfAbsent(CacheKey.of(type, layout), k ->
                new SegmentRecordMapper2<>(lookup, MapperUtil.requireRecordType(k.type()), k.layout(), true)
        );
    }

    record CacheKey(Class<?> type,
                    GroupLayout layout) {

        static CacheKey of(AccessorInfo accessorInfo) {
            return of(accessorInfo.type(), accessorInfo.targetLayout().withoutName());
        }

        static CacheKey of(Class<?> type, GroupLayout layout) {
            return new CacheKey(type, layout.withoutName());
        }

    }

    static MapperCache of(MethodHandles.Lookup lookup) {
        return new MapperCache(lookup);
    }

}
