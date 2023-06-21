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
public sealed interface GroupLayout extends MemoryLayout permits StructLayout, UnionLayout {

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
     * Represents {@code get()} (unmarshall/deserialize) and {@code set()} (marshall/serialize)
     * operations for certain Java classes (such as records and interfaces) where instances of these
     * types can be read and written from/to a MemorySegment at certain offsets.
     *
     * @param <T> type to map
     */
    interface Mapper<T> {

        /**
         * {@return a {@link MethodHandle} representing the "get" operation for this mapper.  The MethodHandle
         * has the coordinates {@code (MemorySegment, long)T} where the long coordinate represents an offset
         * into the MemorySegment}
         */
        MethodHandle getterHandle();

        /**
         * {@return a {@link MethodHandle} representing the "set" operation for this mapper.  The MethodHandle
         * has the coordinates {@code (MemorySegment, long, T)void} where the long coordinate represents an offset
         * into the MemorySegment}
         */
        MethodHandle setterHandle();

        /**
         * {@return a new instance of type T obtained by unmarshalling (deserializing)
         * the object from the provided {@code segment} starting at the provided
         * {@code offset}}
         *
         * @param segment from which to get an object
         * @param offset at which to start unmarshalling
         */
        @SuppressWarnings("unchecked")
        default T get(MemorySegment segment, long offset) {
            try {
                return (T) getterHandle().invokeExact(segment, offset);
            } catch (Throwable t) {
                throw new IllegalArgumentException(t);
            }
        }

        /**
         * {@return a new instance of type T by obtained unmarshalling (deserializing)
         * the object from the provided {@code segment} starting at position zero}
         *
         * @param segment from which to get an object
         */
        default T get(MemorySegment segment) {
            return get(segment, 0L);
        }

        /**
         * Sets (marshals/serializes) the provided {@code value} into the provided
         * {@code segment} starting at the provided {@code offset}.
         *
         * @param segment to which a value should be marshalled
         * @param offset  at which to start marshalling
         * @param value   to marshall
         */
        default void set(MemorySegment segment, long offset, T value) {
            try {
                setterHandle().invokeExact(segment, offset, value);
            } catch (Throwable e) {
                throw new IllegalArgumentException(e);
            }
        }

        /**
         * Sets (marshals/serializes) the provided {@code value} into the provided
         * {@code segment} starting at position zero.
         *
         * @param segment to which a value should be marshalled
         * @param value   to marshall
         */
        default void set(MemorySegment segment, T value) {
            set(segment, 0L, value);
        }

    }

    /**
     * {@return to doc}
     * @param recordType t
     * @param <R> r
     */
    static <R extends Record> Mapper<R> recordMapper(Class<R> recordType) {
        // Implicit null check
        if (recordType.equals(Record.class)) {
            throw new IllegalArgumentException();
        }
        return null;
    }

    /**
     * {@return to doc}
     * @param interfaceType i
     * @param <I> i
     */
    static <I> Mapper<I> interfaceMapper(Class<I> interfaceType) {
        // Implicit null check
        if (!interfaceType.isInterface()) {
            throw new IllegalArgumentException();
        }
        return null;
    }

}
