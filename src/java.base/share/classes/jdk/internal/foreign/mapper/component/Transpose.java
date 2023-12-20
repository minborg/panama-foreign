/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestSegmentRecordMapperGetOperations
 */
// options: --enable-preview -source ${jdk.version} -Xlint:preview

package jdk.internal.foreign.mapper.component;

import jdk.internal.util.ImmutableBitSetPredicate;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

/**
 * A small utility class that allows method handles of type
 * (X0 ,long, X1, ...)R to be converted to method handles of the
 * same type, but where the value for the long parameter will
 * be transposed by a certain value.
 * <p>
 * As transposition is going to be used for every component except the
 * first, we cache likely transpose method handles.
 */
public final class Transpose {

    // Transposing offsets

    // Todo: Replace with Computed Constant
    private static final Map<Long, MethodHandle> TRANSPOSERS = new ConcurrentHashMap<>();
    private static final IntPredicate IS_CACHED;

    static {
        BitSet set = new BitSet();
        // These are the offsets for which transpose handles are cached
        // {1, 2, 3, 4, 6, 8, 12, 16, 24, 32}
        IntStream.rangeClosed(1, 4)
                .flatMap(i -> IntStream.of(i,
                        i * Short.BYTES,
                        i * Integer.BYTES,
                        i * Long.BYTES))
                .forEach(set::set);
        IS_CACHED = ImmutableBitSetPredicate.of(set);
    }

    public static MethodHandle transposeOffset(MethodHandle mh,
                                               long offset) {
        return offset == 0
                ? mh // Nothing to do
                // (X0, long, ...)R -> (X0, long, ...)R
                : MethodHandles.filterArguments(mh, 1, transposing(offset));
    }

    private static MethodHandle transposing(long offset) {
        return isCached(offset)
                ? TRANSPOSERS.computeIfAbsent(offset, Transpose::transposing0)
                : transposing0(offset);
    }

    private static MethodHandle transposing0(long offset) {
        // (long, long)long -> (long)long
        return MethodHandles.insertArguments(Util.SUM_LONG, 1, offset);
    }

    private static boolean isCached(long offset) {
        return offset < Integer.MAX_VALUE - 1 &&
                IS_CACHED.test((int) offset);
    }

}
