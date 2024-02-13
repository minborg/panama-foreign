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

import jdk.internal.foreign.layout.StructLayoutImpl;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;

/**
 * A group layout whose member layouts are laid out one after the other.
 *
 * @implSpec
 * Implementing classes are immutable, thread-safe and
 * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 22
 * @param <T> The carrier type
 */
public sealed interface StructLayout<T> extends GroupLayout<T> permits StructLayoutImpl {

    /**
     * {@return a struct layout that has a carrier of the provided {@code carrier} type
     * and using the provided {@code lookup}}
     * @param lookup to use when performing reflective analysis on the
     *                provided {@code type}
     * @param carrier to associate to this struct layout
     * @param <R> carrier type
     * @since 23
     */
    <R extends Record> StructLayout<R> withCarrier(MethodHandles.Lookup lookup, Class<R> carrier);

    /**
     * {@return a struct layout that has a carrier of the provided {@code carrier} type
     * and using {@linkplain MethodHandles#publicLookup()}}
     * @param carrier to associate to this group layout
     * @param <R> carrier type
     * @since 23
     */
    <R extends Record> StructLayout<R> withCarrier(Class<R> carrier);

    /**
     * {@return a struct layout that does not have a carrier}
     * @since 23
     */
    StructLayout<MemorySegment> withoutCarrier();

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
    <R> StructLayout<R> map(Class<R> newType,
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
    <R> StructLayout<R> map(Class<R> newType,
                            Function<? super T, ? extends R> toMapper);

//   /**
//    * A compound layout that is an aggregation of multiple, heterogeneous
//    * <em>member layouts</em> similar to {@linkplain GroupLayout} but has
//    * an associated carrier type.
//    *
//    * @implSpec
//    * This class is immutable, thread-safe and
//    * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
//    *
//    * @param <T> the carrier type
//    *
//    * @sealedGraph
//    * @since 23
//    */
//   sealed interface OfComposite<T> extends StructLayout, GroupLayout.OfComposite<T> {

//       /**
//        * {@inheritDoc}
//        */
//       <R> StructLayout.OfComposite<R> map(Class<R> newType,
//                                           Function<? super T, ? extends R> toMapper,
//                                           Function<? super R, ? extends T> fromMapper);

//       /**
//        * {@inheritDoc}
//        */
//       <R> StructLayout.OfComposite<R> map(Class<R> newType,
//                                           Function<? super T, ? extends R> toMapper);

//   }

    /**
     * {@inheritDoc}
     */
    @Override
    StructLayout<T> withName(String name);

    /**
     * {@inheritDoc}
     */
    @Override
    StructLayout<T> withoutName();

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    @Override
    StructLayout<T> withByteAlignment(long byteAlignment);
}
