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
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;
import java.util.List;

import static jdk.internal.foreign.mapper.component.Util.*;
import static jdk.internal.foreign.mapper.component.Util.GET_TYPE;

final class GetComponentHandle<T>
        extends AbstractComponentHandle<T>
        implements ComponentHandle<T> {

    GetComponentHandle(MethodHandles.Lookup lookup,
                       Class<T> type,
                       GroupLayout layout,
                       long offset,
                       int depth) {
        super(lookup, type, layout, offset, depth);
    }

    @Override
    public MethodHandle handle(ValueLayout vl,
                               RecordComponent recordComponent,
                               long byteOffset) throws NoSuchMethodException, IllegalAccessException {

        assertTypesMatch(recordComponent, recordComponent.getType(), vl);
        var mt = MethodType.methodType(vl.carrier(), topValueLayoutType(vl), long.class);
        var mh = MethodHandles.publicLookup().findVirtual(MemorySegment.class, "get", mt);
        // (MemorySegment, OfX, long)x -> (MemorySegment, long)x
        mh = MethodHandles.insertArguments(mh, 1, vl);

        return Transpose.transposeOffset(mh, byteOffset);
    }

    @Override
    public MethodHandle handle(GroupLayout gl,
                               RecordComponent recordComponent,
                               long byteOffset) {



        // Todo: There has to be a more general way of detecting circularity
        if (type.equals(recordComponent.getType())) {
            throw new IllegalArgumentException(
                    "A type may not use a component of the same type: " + type + " in " + gl);
        }
        // Simply return the raw MethodHandle of the recursively computed record mapper
        return recordMapper(recordComponent.getType(), gl, byteOffset)
                .getHandle();
    }

    @Override
    public MethodHandle handle(SequenceLayout sl,
                               RecordComponent recordComponent,
                               long byteOffset) throws NoSuchMethodException, IllegalAccessException {

        assertSequenceLayoutValid(sl);

        var recordComponentType = recordComponent.getType();
        ContainerType containerType = ContainerType.of(recordComponentType, sl);

        Class<?> valueType = recordComponentType.isArray()
                ? recordComponentType.getComponentType()
                : firstGenericType(recordComponent);

        // Single-dimensional arrays
        return switch (sl.elementLayout()) {
            case ValueLayout vl -> {
                var mt = MethodType.methodType(vl.carrier().arrayType(),
                        MemorySegment.class, topValueLayoutType(vl), long.class, long.class);
                var mh = findStaticToArray(mt);
                // (MemorySegment, OfX, long offset, long count)x[] -> (MemorySegment, OfX, long offset)x[]
                mh = MethodHandles.insertArguments(mh, 3, sl.elementCount());
                // (MemorySegment, OfX, long offset)x[] -> (MemorySegment, long offset)x[]
                mh = MethodHandles.insertArguments(mh, 1, vl);
                // (MemorySegment, long offset)x[] -> (MemorySegment, long offset)x[]

                if (containerType == ContainerType.LIST) {
                    // (OfX, [x])List<X>
                    MethodHandle finisher = Util.findStaticArrayToList(MethodType.methodType(
                            List.class,
                            topValueLayoutType(vl),
                            vl.carrier().arrayType()));
                    // (OfX, [x])List<X> -> ([x])List<X>
                    finisher = MethodHandles.insertArguments(finisher, 0, vl);
                    mh = MethodHandles.filterReturnValue(mh, finisher);
                }

                yield castReturnType(
                        Transpose.transposeOffset(mh, byteOffset), recordComponent.getType());
            }
            case GroupLayout gl -> {
                // The "local" byteOffset for the record recordComponent mapper is zero
                var componentMapper = recordMapper(valueType, gl, 0);

                try {
                    var mt = MethodType.methodType(Object.class.arrayType(),
                            MemorySegment.class,
                            GroupLayout.class,
                            long.class,
                            long.class,
                            Class.class,
                            MethodHandle.class);
                    var mh = findStaticToArray(mt);
                    var mapper = componentMapper.getHandle().asType(GET_TYPE);
                    // (MemorySegment, GroupLayout, long offset, long count, Class, MethodHandle) ->
                    // (MemorySegment, GroupLayout, long offset, long count, Class)
                    mh = MethodHandles.insertArguments(mh, 5, mapper);
                    // (MemorySegment, GroupLayout, long offset, long count, Class) ->
                    // (MemorySegment, GroupLayout, long offset, long count)
                    mh = MethodHandles.insertArguments(mh, 4, componentMapper.type());
                    // (MemorySegment, GroupLayout, long offset, long count) ->
                    // (MemorySegment, GroupLayout, long offset)
                    mh = MethodHandles.insertArguments(mh, 3, sl.elementCount());
                    // (MemorySegment, GroupLayout, long offset) ->
                    // (MemorySegment, long offset)
                    mh = MethodHandles.insertArguments(mh, 1, gl);
                    // (MemorySegment, long offset)Record[] -> (MemorySegment, long offset)Record[]
                    mh = Transpose.transposeOffset(mh, byteOffset);

                    if (containerType == ContainerType.LIST) {
                        // (MemorySegment, long offset)Record[] -> (MemorySegment, long offset)List
                        mh = MethodHandles.filterReturnValue(mh, LIST_OF);
                    }

                    // (MemorySegment, long offset)(Record[] | List)
                    // -> (MemorySegment, long)recordComponentType
                    mh = mh.asType(GET_TYPE.changeReturnType(recordComponent.getType()));
                    yield mh;
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            case SequenceLayout _ -> throw new InternalError("Should not reach here");
            case PaddingLayout _ -> throw fail(recordComponent, sl);
        };
    }

}
