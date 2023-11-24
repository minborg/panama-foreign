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

final class SetComponentHandle<T>
        extends AbstractComponentHandle<T>
        implements ComponentHandle<T> {

    SetComponentHandle(MethodHandles.Lookup lookup,
                       Class<T> type,
                       GroupLayout layout,
                       long offset,
                       int depth) {
        super(lookup, type, layout, offset, depth);
    }

    @Override
    public MethodHandle handle(RecordComponent recordComponent) {
        return super.handle(recordComponent)
                .asType(SET_TYPE);
    }

    @Override
    public MethodHandle handle(ValueLayout vl,
                               RecordComponent recordComponent,
                               long byteOffset) throws NoSuchMethodException, IllegalAccessException {

        assertTypesMatch(recordComponent, recordComponent.getType(), vl);

        var mt = MethodType.methodType(void.class, topValueLayoutType(vl), long.class, vl.carrier());
        var mh = MethodHandles.publicLookup().findVirtual(MemorySegment.class, "set", mt);
        // (MemorySegment, OfX, long, x)void -> (MemorySegment, long, x)void
        mh = MethodHandles.insertArguments(mh, 1, vl);

        // (MemorySegment, long, x) -> (MemorySegment, long, x)
        mh = Transpose.transposeOffset(mh, byteOffset);

        // (Object)x
        MethodHandle extractor = lookup.unreflect(recordComponent.getAccessor());
        // (MemorySegment, long, x) -> (MemorySegment, long, Object)
        mh = MethodHandles.filterArguments(mh, 2, extractor);
        return mh;
    }

    @Override
    public MethodHandle handle(GroupLayout gl,
                               RecordComponent recordComponent,
                               long byteOffset) throws IllegalAccessException {
        // (T)x
        MethodHandle extractor = lookup.unreflect(recordComponent.getAccessor());

        // (T)Object
        extractor = extractor.asType(extractor.type().changeReturnType(Object.class));

        // (MemorySegment, long, T)
        MethodHandle mh = recordMapper(recordComponent.getType(), gl, byteOffset)
                .setHandle();

        // (MemorySegment, long, T) -> (MemorySegment, long, x)
        return MethodHandles.filterArguments(mh, 2, extractor);
    }

    @Override
    public MethodHandle handle(SequenceLayout sl,
                               RecordComponent recordComponent,
                               long byteOffset) throws NoSuchMethodException, IllegalAccessException {

        assertSequenceLayoutValid(sl);

        var recordComponentType = recordComponent.getType();
        ContainerType containerType = ContainerType.of(recordComponentType, sl);

        // (T)[x] or (T)(Set<X> | List<X>)
        MethodHandle extractor = lookup.unreflect(recordComponent.getAccessor());

        // Only single-dimensional arrays/Lists are supported
        return switch (sl.elementLayout()) {
            case ValueLayout vl -> {
                // assertTypesMatch(recordComponent, valueType, vl);

                var mt = MethodType.methodType(void.class,
                        Object.class, int.class,
                        MemorySegment.class, ValueLayout.class, long.class,
                        int.class);
                // (Object, int, MemorySegment, ValueLayout, long, int)void
                var mh = MethodHandles.publicLookup()
                        .findStatic(MemorySegment.class, "copy", mt);
                // -> (Object, int, MemorySegment, ValueLayout, long)void
                mh = MethodHandles.insertArguments(mh, 5, (int) sl.elementCount());
                // -> (Object, MemorySegment, ValueLayout, long)void
                mh = MethodHandles.insertArguments(mh, 1, 0);
                // -> (Object, MemorySegment, long)void
                mh = MethodHandles.insertArguments(mh, 2, vl);
                // -> (Object, MemorySegment, long)void
                var newMt = MethodType.methodType(void.class, MemorySegment.class, long.class, Object.class);
                // -> (MemorySegment, long, Object)void
                mh = MethodHandles.permuteArguments(mh, newMt, 2, 0, 1);

                if (containerType == ContainerType.LIST) {
                    var listToArrayType = MethodType.methodType(vl.carrier().arrayType(), topValueLayoutType(vl), List.class);
                    // (ofX, List)x[]
                    MethodHandle listToArray = Util.findStaticListToArray(listToArrayType);
                    // (List)x[] (the ofX parameter is only used to resolve the correct underlying method)
                    listToArray = MethodHandles.insertArguments(listToArray, 0, vl);
                    // (T)List<x> -> (T)[x]
                    extractor = MethodHandles.filterReturnValue(extractor, listToArray);
                    // The extractor is now of type (T)[x] regardless if we are looking at
                    // an array or a List.
                }

                // -> (MemorySegment, long, T)void
                mh = MethodHandles.filterArguments(mh, 2, extractor.asType(MethodType.methodType(Object.class, extractor.type().parameterType(0))));
                // -> (MemorySegment, long, T)void
                yield Transpose.transposeOffset(mh, byteOffset);
            }
            case GroupLayout gl when containerType == ContainerType.ARRAY -> {
                Class<?> valueType = recordComponentType.getComponentType();
                // The "local" byteOffset for the record recordComponent mapper is zero
                var componentMapper = recordMapper(valueType, gl, 0);
                try {
                    var mt = MethodType.methodType(void.class,
                            MemorySegment.class, GroupLayout.class, long.class, MethodHandle.class, Object[].class);
                    var mh = Util.findStaticFromArray(mt);
                    var mapper = componentMapper.setHandle().asType(SET_TYPE);
                    // (MemorySegment, GroupLayout, long offset, MethodHandle, Object[])void ->
                    // (MemorySegment, GroupLayout, long offset, Object[])void
                    mh = MethodHandles.insertArguments(mh, 3, mapper);
                    // (MemorySegment, GroupLayout, long offset, Object[])void ->
                    // (MemorySegment, long offset, Object[])void
                    mh = MethodHandles.insertArguments(mh, 1, gl);
                    // (MemorySegment, long offset, Object[])void ->
                    // (MemorySegment, long offset, T)void
                    mh = MethodHandles.filterArguments(mh, 2, extractor.asType(MethodType.methodType(Object[].class, extractor.type().parameterType(0))));
                    // (MemorySegment, long offset, Object[])void -> (MemorySegment, long offset, Object[])void
                    mh = Transpose.transposeOffset(mh, byteOffset);
                    // (MemorySegment, long offset, Object[])void -> (MemorySegment, long offset, Object)void
                    yield MethodHandles.explicitCastArguments(mh, SET_TYPE);
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            case GroupLayout gl -> {
                Class<?> valueType = firstGenericType(recordComponent);
                // The "local" byteOffset for the record recordComponent mapper is zero
                var componentMapper = recordMapper(valueType, gl, 0);
                try {
                    var mt = MethodType.methodType(void.class,
                            MemorySegment.class, GroupLayout.class, long.class, MethodHandle.class, List.class);
                    var mh = Util.findStaticFromList(mt);
                    var mapper = componentMapper.setHandle().asType(SET_TYPE);
                    // (MemorySegment, GroupLayout, long offset, MethodHandle, List)void ->
                    // (MemorySegment, GroupLayout, long offset, List)void
                    mh = MethodHandles.insertArguments(mh, 3, mapper);
                    // (MemorySegment, GroupLayout, long offset, List)void ->
                    // (MemorySegment, long offset, List)void
                    mh = MethodHandles.insertArguments(mh, 1, gl);
                    if (containerType.equals(ContainerType.SET)) {
                        // (T)Set<X> -> (T)List<X>
                        extractor = MethodHandles.filterReturnValue(extractor, LIST_AS_COPY_OF_SET);
                    }
                    // (MemorySegment, long offset, (List|Set))void ->
                    // (MemorySegment, long offset, T)void
                    mh = MethodHandles.filterArguments(mh, 2, extractor.asType(MethodType.methodType(List.class, extractor.type().parameterType(0))));
                    // (MemorySegment, long offset, T)void -> (MemorySegment, long offset, T)void
                    mh = Transpose.transposeOffset(mh, byteOffset);
                    // (MemorySegment, long offset, T)void -> (MemorySegment, long offset, Object)void
                    yield MethodHandles.explicitCastArguments(mh, SET_TYPE);
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            case SequenceLayout _ ->  throw new InternalError("Should not reach here");
            case PaddingLayout _ -> throw fail(recordComponent, sl);
        };

    }

}
