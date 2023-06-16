/*
 *  Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Optional;

public final class CarrierStructLayoutImpl<T>
        extends AbstractCarrierGroupLayout<T, CarrierStructLayoutImpl<T>>
        implements StructLayout.OfClass<T> {

    private CarrierStructLayoutImpl(Class<T> carrier,
                                    MethodHandle unmarshaller,
                                    MethodHandle marshaller,
                                    List<MemoryLayout> elements,
                                    long byteSize,
                                    long byteAlignment,
                                    long minByteAlignment,
                                    Optional<String> name) {
        super(carrier, unmarshaller, marshaller, Kind.STRUCT, elements, byteSize, byteAlignment, minByteAlignment, name);
    }

    @Override
    CarrierStructLayoutImpl<T> dup(long byteAlignment, Optional<String> name) {
        return new CarrierStructLayoutImpl<>(carrier(), unmarshaller(), marshaller(), memberLayouts(), byteSize(), byteAlignment, minByteAlignment, name);
    }

    public static <T> StructLayout.OfClass<T> of(Class<T> carrier,
                                                 MethodHandle unmarshaller,
                                                 MethodHandle marshaller,
                                                 List<MemoryLayout> elements) {
        // Todo: Break out this logic in a separate method and share with StructLayoutImpl
        long size = 0;
        long align = 1;
        for (MemoryLayout elem : elements) {
            if (size % elem.byteAlignment() != 0) {
                throw new IllegalArgumentException("Invalid alignment constraint for member layout: " + elem);
            }
            size = Math.addExact(size, elem.byteSize());
            align = Math.max(align, elem.byteAlignment());
        }
        return new CarrierStructLayoutImpl<>(carrier, unmarshaller, marshaller, elements, size, align, align, Optional.empty());
    }

}
