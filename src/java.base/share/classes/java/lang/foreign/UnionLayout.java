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

import jdk.internal.foreign.layout.UnionLayoutImpl;

import java.lang.invoke.MethodHandles;

/**
 * A group layout whose member layouts are laid out at the same starting offset.
 *
 * @implSpec
 * Implementing classes are immutable, thread-safe and
 * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 22
 * @param <T> The carrier type
 */
public sealed interface UnionLayout<T> extends GroupLayout<T> permits UnionLayoutImpl {

    /**
     * {@return a union layout that has a carrier of the provided {@code carrier} type
     * and using the provided {@code lookup}}
     * @param lookup to use when performing reflective analysis on the
     *                provided {@code type}
     * @param carrier to associate to this struct layout
     * @param <R> carrier type
     * @since 23
     */
    <R extends Record> UnionLayout<R> withCarrier(MethodHandles.Lookup lookup, Class<R> carrier);

    /**
     * {@return a union layout that has a carrier of the provided {@code carrier} type
     * and using {@linkplain MethodHandles#publicLookup()}}
     * @param carrier to associate to this group layout
     * @param <R> carrier type
     * @since 23
     */
    <R extends Record> UnionLayout<R> withCarrier(Class<R> carrier);

    /**
     * {@return a union layout that does have a carrier}
     * @since 23
     */
    UnionLayout<MemorySegment> withoutCarrier();

//   /**
//    * A compound layout that is an aggregation of multiple, heterogeneous
//    * <em>member layouts</em> similar to {@linkplain GroupLayout} but has
//    * an associated carrier type.
//    *
//    * @implSpec
//    * This class is immutable, thread-safe and
//    * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
//    *
//    * @sealedGraph
//    * @since 23
//    */
//   sealed interface OfComposite<T> extends UnionLayout, GroupLayout.OfComposite<T> {

//       /**
//        * {@inheritDoc}
//        */
//       <R> UnionLayout.OfComposite<R> map(Class<R> newType,
//                                           Function<? super T, ? extends R> toMapper,
//                                           Function<? super R, ? extends T> fromMapper);

//       /**
//        * {@inheritDoc}
//        */
//       <R> UnionLayout.OfComposite<R> map(Class<R> newType,
//                                           Function<? super T, ? extends R> toMapper);

//   }

    /**
     * {@inheritDoc}
     */
    @Override
    UnionLayout<T> withName(String name);

    /**
     * {@inheritDoc}
     */
    @Override
    UnionLayout<T> withoutName();

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    @Override
    UnionLayout<T> withByteAlignment(long byteAlignment);
}
