/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.foreign;

import java.lang.invoke.MethodHandles;
import java.util.List;

/**
 * A compound layout that is an aggregation of multiple, heterogeneous
 * <em>member layouts</em>. There are two ways in which member layouts can be combined:
 * if member layouts are laid out one after the other, the resulting group layout is a
 * {@linkplain StructLayout struct layout}; conversely, if all member layouts are laid
 * out at the same starting offset, the resulting group layout is a
 * {@linkplain UnionLayout union layout}.
 *
 * @implSpec
 * This class is immutable, thread-safe and
 * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @sealedGraph
 * @since 22
 */
public sealed interface GroupLayout extends MemoryLayout permits StructLayout, UnionLayout {

    /**
     * {@return the member layouts of this group layout}
     *
     * @apiNote the order in which member layouts are returned is the same order in which
     *          member layouts have been passed to one of the group layout factory methods
     *          (see {@link MemoryLayout#structLayout(MemoryLayout...)} and
     *          {@link MemoryLayout#unionLayout(MemoryLayout...)}).
     */
    List<MemoryLayout> memberLayouts();

    /**
     * {@inheritDoc}
     */
    @Override
    GroupLayout withName(String name);

    /**
     * {@inheritDoc}
     */
    @Override
    GroupLayout withoutName();

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     * @throws IllegalArgumentException if {@code byteAlignment} is less than {@code M},
     *         where {@code M} is the maximum alignment constraint in any of the
     *         member layouts associated with this group layout
     */
    @Override
    GroupLayout withByteAlignment(long byteAlignment);

    /**
     * {@return a compound accessor that can be used to map {@linkplain MemorySegment memory segments}
     *          to and from the provided record {@code type} using this {@code layout}}
     * <p>
     * Reflective analysis on the provided {@code type} will be made using the
     * {@linkplain MethodHandles.Lookup#publicLookup() public lookup}.
     *
     * @param carrier class for which to map memory segment from and to
     * @param <T> the carrier type the returned accessor converts MemorySegments from and to
     * @throws IllegalArgumentException if the provided record {@code type} directly
     *         declares any generic type parameter
     * @throws IllegalArgumentException if the provided record {@code type} is
     *         {@linkplain java.lang.Record}
     * @throws IllegalArgumentException if the provided record {@code type} cannot
     *         be reflectively analysed using
     *         the {@linkplain MethodHandles.Lookup#publicLookup() public lookup}
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         components for which there are no exact mapping (of names and types) in
     *         the provided {@code layout} or if the provided {@code type} is not public or
     *         if the method is otherwise unable to create a segment mapper as specified above
     * @see #accessor(MethodHandles.Lookup, Class)
     */
    <T extends Record> CompoundAccessor<T> accessor(Class<T> carrier);

    /**
     * {@return a compound accessor that can be used to map {@linkplain MemorySegment memory segments}
     *          to and from the provided record {@code type} using this {@code layout} and
     *          and the provided {@code lookup}}
     *
     * @param lookup  to use for reflective analysis
     * @param carrier class for which to map memory segment from and to
     * @param <T> the carrier type the returned accessor converts MemorySegments from and to
     * @throws IllegalArgumentException if the provided record {@code type} directly
     *         declares any generic type parameter
     * @throws IllegalArgumentException if the provided record {@code type} is
     *         {@linkplain java.lang.Record}
     * @throws IllegalArgumentException if the provided record {@code type} cannot
     *         be reflectively analysed using
     *         the {@linkplain MethodHandles.Lookup#publicLookup() public lookup}
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         components for which there are no exact mapping (of names and types) in
     *         the provided {@code layout} or if the provided {@code type} is not public or
     *         if the method is otherwise unable to create a segment mapper as specified above
     * @see #accessor(Class)
     */
    <T extends Record> CompoundAccessor<T> accessor(MethodHandles.Lookup lookup,
                                                    Class<T> carrier);
}
