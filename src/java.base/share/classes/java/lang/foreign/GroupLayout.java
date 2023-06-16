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
package java.lang.foreign;

import java.lang.invoke.MethodHandle;
import java.util.List;

import jdk.internal.javac.PreviewFeature;

/**
 * A compound layout that is an aggregation of multiple, heterogeneous <em>member layouts</em>. There are two ways in which member layouts
 * can be combined: if member layouts are laid out one after the other, the resulting group layout is a
 * {@linkplain StructLayout struct layout}; conversely, if all member layouts are laid out at the same starting offset,
 * the resulting group layout is a {@linkplain UnionLayout union layout}.
 *
 * @implSpec
 * This class is immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface GroupLayout
        extends MemoryLayout
        permits StructLayout, UnionLayout, GroupLayout.OfClass {

    /**
     * {@return the member layouts of this group layout}
     *
     * @apiNote the order in which member layouts are returned is the same order in which member layouts have
     * been passed to one of the group layout factory methods (see {@link MemoryLayout#structLayout(MemoryLayout...)},
     * {@link MemoryLayout#unionLayout(MemoryLayout...)}).
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
     * @throws IllegalArgumentException if {@code byteAlignment} is less than {@code M}, where {@code M} is the maximum alignment
     * constraint in any of the member layouts associated with this group layout.
     */
    @Override
    GroupLayout withByteAlignment(long byteAlignment);

    /**
     * {@return a new group layout with the provided {@code recordType} as a carrier}
     *
     * @param recordType to use as a carrier
     * @param <R>        carrier record type
     * @throws IllegalArgumentException if the provided {@code recordType} is
     *                                  {@link java.lang.Record} or if there is at least one
     *                                  record component that cannot be matched to a member layout.
     */
    <R extends Record> GroupLayout.OfClass<R> withRecord(Class<R> recordType);

    /**
     * {@return a new group layout with the provided {@code interfaceType} as a carrier}
     *
     * @param interfaceType to use as a carrier
     * @param <I>           carrier interface type
     * @throws IllegalArgumentException if there is at least one abstract method
     *                                  that cannot be matched to a member layout.
     */
    <I> GroupLayout.OfClass<I> withInterface(Class<I> interfaceType);

    /**
     * {@return a new group layout with the provided {@code type} as a carrier}
     *
     * @param type to use as a carrier
     * @param unmarshaller to be used when unmarshalling (deserializing) values of type T
     * @param marshaller   to be used when marshalling (serializing) values of type T
     * @param <T>          carrier type
     */
    <T> GroupLayout.OfClass<T> withCarrier(Class<T> type,
                                           MethodHandle unmarshaller,
                                           MethodHandle marshaller);

    /**
     * {@return a new struct layout with the provided {@code type} as a carrier}
     *
     * @param type to use as a carrier
     * @param unmarshaller to be used when unmarshalling (deserializing) values of type T
     * @param marshaller   to be used when marshalling (serializing) values of type T
     * @param <T>          carrier type
     */
    <T> GroupLayout.OfClass<T> withCarrier(Class<T> type,
                                           Unmarshaller<T> unmarshaller,
                                           Marshaller<T> marshaller);

    /**
     * A group layout whose member layouts are laid out one after the other
     * and that can be converted to/from an instance of class given a MemorySegment.
     *
     * @param <T> type to convert to/from
     */
    sealed interface OfClass<T>
            permits StructLayout.OfClass, UnionLayout.OfClass {

        /**
         * {@return the carrier associated with this group layout}
         */
        Class<T> carrier();

        /**
         * {@return a group layout with no associated carrier}
         */
        GroupLayout layout();

    }

    /**
     * Represents a function that accepts a MemorySegment and an offset argument
     * and produces a result of type T by whereby T is deserialized from the
     * MemorySegment.
     *
     * @param <T> type to produce
     */
    @FunctionalInterface
    interface Unmarshaller<T> {

        /**
         * {@return a new instance of type T obtained by unmarshalling (deserializing)
         * the object from the provided {@code segment} starting at the provided
         * {@code offset}}
         *
         * @param segment from which to unmarshal an object
         * @param offset at which to start unmarshalling
         */
        T apply(MemorySegment segment, long offset);

        /**
         * {@return a new instance of type T by obtained unmarshalling (deserializing)
         * the object from the provided {@code segment} starting at position zero}
         *
         * @param segment from which to unmarshal an object
         */
        default T apply(MemorySegment segment) {
            return apply(segment, 0L);
        }

    }

    /**
     * Represents a consumer that accepts a MemorySegment, an offset argument
     * and a value of type T and returns no result whereby the T value is serialized
     * into the MemorySegment.
     * <p>
     * Unlike most other functional interfaces, {@code Marshaller}
     * operate via the side effect of modifying the provided
     * MemorySegment.
     *
     * @param <T> type to produce
     */
    @FunctionalInterface
    interface Marshaller<T> {

        /**
         * Marshals (serializes) the provided {@code value} into the provided
         * {@code segment} starting at the provided {@code offset}.
         *
         * @param segment to which a value should be marshalled
         * @param offset  at which to start marshalling
         * @param value   to marshall
         */
        void apply(MemorySegment segment, long offset, T value);

        /**
         * Marshals (serializes) the provided {@code value} into the provided
         * {@code segment} starting at position zero.
         *
         * @param segment to which a value should be marshalled
         * @param value   to marshall
         */
        default void apply(MemorySegment segment, T value) {
            apply(segment, 0L, value);
        }

    }

}
