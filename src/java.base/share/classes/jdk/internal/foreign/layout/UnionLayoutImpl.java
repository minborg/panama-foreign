/*
 *  Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.internal.foreign.layout;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class UnionLayoutImpl<T> extends AbstractGroupLayout<T, UnionLayoutImpl<T>> implements UnionLayout<T> {

    private UnionLayoutImpl(List<MemoryLayout> elements, long byteSize, long byteAlignment, long minByteAlignment, Optional<String> name,
                            MethodHandles.Lookup lookup, Class<T> carrier) {
        super(Kind.UNION, elements, byteSize, byteAlignment, minByteAlignment, name, lookup, carrier);
    }

    @Override
    <R> UnionLayoutImpl<R> dup(long byteAlignment, Optional<String> name, MethodHandles.Lookup lookup, Class<R> carrier) {
        return new UnionLayoutImpl<>(memberLayouts(), byteSize(), byteAlignment, minByteAlignment, name, lookup, carrier);
    }

    @Override
    public <R extends Record> UnionLayout<R> withCarrier(Class<R> carrier) {
        return withCarrier(MethodHandles.publicLookup(), carrier);
    }

    @Override
    public <R extends Record> UnionLayout<R> withCarrier(MethodHandles.Lookup lookup, Class<R> carrier) {
        return dup(byteAlignment(), name(), lookup, carrier);
    }

    @Override
    public UnionLayout<MemorySegment> withoutCarrier() {
        return dup(byteAlignment(), name(), MethodHandles.publicLookup(), MemorySegment.class);
    }

    @Override
    public <R> StructLayout<R> map(Class<R> newType, Function<? super T, ? extends R> toMapper, Function<? super R, ? extends T> fromMapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <R> StructLayout<R> map(Class<R> newType, Function<? super T, ? extends R> toMapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    VarHandle computeVarHandle() {
        throw new UnsupportedOperationException();
    }

    public static UnionLayout<MemorySegment> of(List<MemoryLayout> elements) {
        long size = 0;
        long align = 1;
        for (MemoryLayout elem : elements) {
            size = Math.max(size, elem.byteSize());
            align = Math.max(align, elem.byteAlignment());
        }
        return new UnionLayoutImpl<>(elements, size, align, align, Optional.empty(), MethodHandles.publicLookup(), MemorySegment.class);
    }

}
