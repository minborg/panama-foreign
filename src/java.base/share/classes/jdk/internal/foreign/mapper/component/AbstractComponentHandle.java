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

/*
 * @test
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestSegmentRecordMapperGetOperations
 */
// options: --enable-preview -source ${jdk.version} -Xlint:preview

package jdk.internal.foreign.mapper.component;

import jdk.internal.foreign.mapper.SegmentRecordMapper;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.RecordComponent;

abstract sealed class AbstractComponentHandle<T>
        implements ComponentHandle<T>
        permits RecordGetComponentHandle, RecordSetComponentHandle {

    final MethodHandles.Lookup lookup;
    final Class<T> type;
    final GroupLayout layout;
    final long offset;
    final int depth;

    AbstractComponentHandle(MethodHandles.Lookup lookup,
                            Class<T> type,
                            GroupLayout layout,
                            long offset,
                            final int depth) {
        this.lookup = lookup;
        this.type = type;
        this.layout = layout;
        this.offset = offset;
        this.depth = depth;
    }

    @Override
    public MethodHandle handle(RecordComponent recordComponent) {
        var pathElement = MemoryLayout.PathElement.groupElement(recordComponent.getName());
        var componentLayout = layout.select(pathElement);
        var byteOffset = layout.byteOffset(pathElement) + offset;
        try {
            return switch (componentLayout) {
                case ValueLayout vl    -> handle(vl, recordComponent, byteOffset);
                case GroupLayout gl    -> handle(gl, recordComponent, byteOffset);
                case SequenceLayout sl -> handle(sl, recordComponent, byteOffset);
                case PaddingLayout _   -> throw fail(recordComponent, componentLayout);
            };
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    void assertTypesMatch(RecordComponent component,
                          Class<?> recordComponentType,
                          ValueLayout vl) {

        if (!(recordComponentType == vl.carrier())) {
            throw new IllegalArgumentException("Unable to match types because the component '" +
                    component.getName() + "' (in " + type.getName() + ") has the type of '" + component.getType() +
                    "' but the layout carrier is '" + vl.carrier() + "' (in " + layout + ")");
        }
    }

    IllegalArgumentException fail(RecordComponent component,
                                          MemoryLayout layout) {
        throw new IllegalArgumentException(
                "Unable to map " + layout + " to " + type.getName() + "." + component.getName());
    }

    static Class<? extends ValueLayout> topValueLayoutType(ValueLayout vl) {
        // All the permitted implementations OfXImpl of the ValueLayout interfaces declare
        // its main top interface OfX as the sole interface (e.g. OfIntImpl implements only OfInt directly)
        return vl.getClass().getInterfaces()[0].asSubclass(ValueLayout.class);
    }

    <R extends Record> SegmentRecordMapper<R> recordMapper(Class<R> componentType,
                                            GroupLayout gl,
                                            long byteOffset) {

        return new SegmentRecordMapper<>(lookup, componentType, gl, byteOffset, depth + 1);
    }

}
