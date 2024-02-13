/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Optional;
import java.util.function.Function;

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
 * @param <T> The carrier type
 */
public sealed interface GroupLayout<T> extends MemoryLayout permits StructLayout, UnionLayout {

    /**
     * {@return a group layout that has a carrier of the provided {@code carrier} type
     * and using the provided {@code lookup}}
     * @param lookup to use when performing reflective analysis on the
     *                provided {@code type}
     * @param carrier to associate to this struct layout
     * @param <R> carrier type
     * @since 23
     */
    <R extends Record> GroupLayout<R> withCarrier(MethodHandles.Lookup lookup, Class<R> carrier);

    /**
     * {@return a group layout that has a carrier of the provided {@code carrier} type
     * and using {@linkplain MethodHandles#publicLookup()}}
     * @param carrier to associate to this group layout
     * @param <R> carrier type
     * @since 23
     */
    <R extends Record> GroupLayout<R> withCarrier(Class<R> carrier);

    /**
     * {@return a group layout that does not have a carrier}
     * @since 23
     */
    GroupLayout<MemorySegment> withoutCarrier();

    /**
     * {@return the carrier associated with this group layout}
     */
    Class<T> carrier();

    /**
     * {@return a new group layout that would apply the provided {@code toMapper} after
     *          performing get operations on a segment and that would apply the
     *          provided {@code fromMapper} before performing set operations on a
     *          segment}
     * <p>
     * It should be noted that the type R can represent almost any class and is not
     * restricted to records.
     *
     * @param  newType the new carrier type
     * @param toMapper to apply after get operations on a segment
     * @param fromMapper to apply before set operations a segment
     * @param <R> the type of the new carrier type
     * @throws UnsupportedOperationException if this GroupLayout does not have a carrier
     */
    <R> GroupLayout<R> map(Class<R> newType,
                           Function<? super T, ? extends R> toMapper,
                           Function<? super R, ? extends T> fromMapper);

    /**
     * {@return a new group layout that would apply the provided {@code toMapper} after
     *          performing get operations on a segment and that would throw an
     *          {@linkplain UnsupportedOperationException} for set operations on a
     *          segment}
     * <p>
     * It should be noted that the type R can represent almost any class and is not
     * restricted to records.
     *
     * @param  newType the new carrier type
     * @param toMapper to apply after get operations on a segment
     * @param <R> the type of the new carrier type
     * @throws UnsupportedOperationException if this GroupLayout does not have a carrier
     */
    <R> GroupLayout<R> map(Class<R> newType,
                           Function<? super T, ? extends R> toMapper);


//  /**
//   * A compound layout that is an aggregation of multiple, heterogeneous
//   * <em>member layouts</em> similar to {@linkplain GroupLayout} but has
//   * an associated carrier type.
//   *
//   * @implSpec
//   * This class is immutable, thread-safe and
//   * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
//   *
//   * @param <T> the carrier type
//   *
//   * @sealedGraph
//   * @since 23
//   */
//  sealed interface OfComposite<T> extends GroupLayout permits StructLayout.OfComposite, UnionLayout.OfComposite {

//      /**
//       * {@return the carrier associated with this value layout}
//       */
//      Class<T> carrier();

//      /**
//       * {@return a new compound layout that would apply the provided {@code toMapper} after
//       *          performing get operations on a segment and that would apply the
//       *          provided {@code fromMapper} before performing set operations on a
//       *          segment}
//       * <p>
//       * It should be noted that the type R can represent almost any class and is not
//       * restricted to records.
//       *
//       * @param  newType the new carrier type
//       * @param toMapper to apply after get operations on a segment
//       * @param fromMapper to apply before set operations a segment
//       * @param <R> the type of the new carrier type
//       */
//      <R> GroupLayout.OfComposite<R> map(Class<R> newType,
//                                         Function<? super T, ? extends R> toMapper,
//                                         Function<? super R, ? extends T> fromMapper);

//      /**
//       * {@return a new compound layout that would apply the provided {@code toMapper} after
//       *          performing get operations on a segment and that would throw an
//       *          {@linkplain UnsupportedOperationException} for set operations on a
//       *          segment}
//       * <p>
//       * It should be noted that the type R can represent almost any class and is not
//       * restricted to records.
//       *
//       * @param  newType the new carrier type
//       * @param toMapper to apply after get operations on a segment
//       * @param <R> the type of the new carrier type
//       */
//      <R> GroupLayout.OfComposite<R> map(Class<R> newType,
//                                         Function<? super T, ? extends R> toMapper);

//  }

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
    GroupLayout<T> withName(String name);

    /**
     * {@inheritDoc}
     */
    @Override
    GroupLayout<T> withoutName();

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     * @throws IllegalArgumentException if {@code byteAlignment} is less than {@code M},
     *         where {@code M} is the maximum alignment constraint in any of the
     *         member layouts associated with this group layout
     */
    @Override
    GroupLayout<T> withByteAlignment(long byteAlignment);
}
