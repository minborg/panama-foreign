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

import java.lang.foreign.GroupLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.RecordComponent;
import java.util.Objects;

import static jdk.internal.foreign.layout.MemoryLayoutUtil.requireNonNegative;


/**
 * Interface to model the resolving of get and set method handles.
 */
public sealed interface ComponentHandle<T>
        permits AbstractComponentHandle, RecordGetComponentHandle, InterfaceGetComponentHandle, RecordSetComponentHandle {

    MethodHandle handle(RecordComponent recordComponent);

    MethodHandle handle(ValueLayout vl,
                        RecordComponent recordComponent,
                        long byteOffset) throws NoSuchMethodException, IllegalAccessException;

    MethodHandle handle(GroupLayout gl,
                        RecordComponent recordComponent,
                        long byteOffset) throws NoSuchMethodException, IllegalAccessException;

    MethodHandle handle(SequenceLayout sl,
                        RecordComponent component,
                        long byteOffset) throws NoSuchMethodException, IllegalAccessException;

    static <T> ComponentHandle<T> ofGet(MethodHandles.Lookup lookup,
                                        Class<T> type,
                                        GroupLayout initialLayout,
                                        long offset) {
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(type);
        Objects.requireNonNull(initialLayout);
        requireNonNegative(offset);
        return new RecordGetComponentHandle<>(lookup, type, initialLayout, offset, 0);
    }

    static <T> ComponentHandle<T> ofSet(MethodHandles.Lookup lookup,
                                        Class<T> type,
                                        GroupLayout initialLayout,
                                        long offset) {
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(type);
        Objects.requireNonNull(initialLayout);
        requireNonNegative(offset);
        return new RecordSetComponentHandle<>(lookup, type, initialLayout, offset, 0);
    }

    static <T> ComponentHandle<T> ofInterfaceGet(MethodHandles.Lookup lookup,
                                                 Class<T> type,
                                                 GroupLayout initialLayout,
                                                 long offset) {
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(type);
        Objects.requireNonNull(initialLayout);
        requireNonNegative(offset);
        return new RecordGetComponentHandle<>(lookup, type, initialLayout, offset, 0);
    }


}
