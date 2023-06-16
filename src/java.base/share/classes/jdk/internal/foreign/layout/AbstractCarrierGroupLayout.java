/*
 *  Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A compound layout that aggregates multiple <em>member layouts</em>. There are two ways in which member layouts
 * can be combined: if member layouts are laid out one after the other, the resulting group layout is said to be a <em>struct</em>
 * (see {@link MemoryLayout#structLayout(MemoryLayout...)}); conversely, if all member layouts are laid out at the same starting offset,
 * the resulting group layout is said to be a <em>union</em> (see {@link MemoryLayout#unionLayout(MemoryLayout...)}).
 *
 * @implSpec
 * This class is immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 19
 */
public sealed abstract class AbstractCarrierGroupLayout<T, L extends AbstractCarrierGroupLayout<T, L> & MemoryLayout>
        extends AbstractGroupLayout<L>
        permits CarrierStructLayoutImpl, CarrierUnionLayoutImpl {

    private final Class<T> carrier;
    private final MethodHandle unmarshaller;
    private final MethodHandle marshaller;

    AbstractCarrierGroupLayout(Class<T> carrier,
                               MethodHandle unmarshaller,
                               MethodHandle marshaller,
                               Kind kind,
                               List<MemoryLayout> elements,
                               long byteSize,
                               long byteAlignment,
                               long minByteAlignment,
                               Optional<String> name) {
        super(kind, elements, byteSize, byteAlignment, minByteAlignment, name);
        this.carrier = carrier;
        // Todo: Assert coordinates of unmarshaller and marshaller
        this.unmarshaller = unmarshaller;
        this.marshaller = marshaller;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(Object other) {
        return this == other ||
                other instanceof AbstractCarrierGroupLayout<?, ?> otherGroup &&
                        super.equals(other) &&
                        carrier == otherGroup.carrier &&
                        unmarshaller == otherGroup.unmarshaller &&
                        marshaller == otherGroup.marshaller;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return Objects.hash(super.hashCode(), carrier, unmarshaller, marshaller);
    }

    public final Class<T> carrier() {
        return carrier;
    }

    public final MethodHandle unmarshaller() {
        return unmarshaller;
    }

    public final MethodHandle marshaller() {
        return marshaller;
    }
}
