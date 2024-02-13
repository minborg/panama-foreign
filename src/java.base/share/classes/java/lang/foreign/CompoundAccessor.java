/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.foreign.mapper.MapperUtil;

import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.function.Function;

/**
 * A generic compound accessor that can access compound values of type {@code T} via a
 * {@code layout} using a {@code getter} and an optional {@code setter}.
 *
 * @param layout  the group layout to use when accessing compound values
 * @param carrier the carrier class for the compound value
 * @param getter  a method handle that returns new instances of type T projected at
 *                a provided external {@code MemorySegment} at a provided {@code long} offset:
 *                <p>
 *                The getter method handle has the following characteristics:
 *                <ul>
 *                    <li>its return type is {@code T};</li>
 *                    <li>it has a leading parameter of type {@code MemorySegment}
 *                        corresponding to the memory segment to be accessed</li>
 *                    <li>it has a trailing {@code long} parameter, corresponding to
 *                        the base offset</li>
 *                </ul>
 * @param setter  a method handle that writes a provided instance of type T into
 *                a provided {@code MemorySegment} at a provided {@code long} offset:
 *                <p>
 *                The returned method handle has the following characteristics:
 *                <ul>
 *                    <li>its return type is void;</li>
 *                    <li>it has a leading parameter of type {@code MemorySegment}
 *                        corresponding to the memory segment to be accessed</li>
 *                    <li>it has a following {@code long} parameter, corresponding to
 *                        the base offset</li>
 *                    <li>it has a trailing {@code T} parameter, corresponding to
 *                        the value to set</li>
 *                </ul>
 * @param <T>     type of the carrier class
 */
// Todo: Consider the name Group(Layout)Accessor
public record CompoundAccessor<T>(GroupLayout layout,
                                  Class<T> carrier,
                                  MethodHandle getter,
                                  MethodHandle setter) {

    /**
     * Constructor.
     * <p>
     * {@inheritDoc}
     */
    public CompoundAccessor {
        Objects.requireNonNull(layout);
        Objects.requireNonNull(carrier);
        Objects.requireNonNull(getter);
        // the setter is nullable and optional

        // Todo:: assert method handle invariants


    }

    /**
     * {@return a new compound accessor that would apply the provided {@code toMapper} after
     *          performing get operations on this segment mapper and that would apply the
     *          provided {@code fromMapper} before performing set operations on the
     *          provided {@code original} compound accessor}
     * <p>
     * It should be noted that the type R can represent almost any class and is not
     * restricted to records.
     *
     * @param original   the original compound accessor
     * @param newCarrier the new type the returned compound accessor shall use
     * @param toMapper   to apply after get operations on the original accessor
     * @param fromMapper to apply before set operations on the original accessor
     * @param <T> the type of the original compound accessor
     * @param <R> the type of the new compound accessor
     */
    public static <T, R> CompoundAccessor<R> map(CompoundAccessor<T> original,
                                                 Class<R> newCarrier,
                                                 Function<? super T, ? extends R> toMapper,
                                                 Function<? super R, ? extends T> fromMapper) {
        return MapperUtil.map(original, newCarrier, toMapper, fromMapper);
    }


}
