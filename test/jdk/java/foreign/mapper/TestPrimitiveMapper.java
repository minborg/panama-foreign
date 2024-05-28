/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestPrimitiveMapper
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.mapper.PrimitiveMapper;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;


final class TestPrimitiveMapper {

    private static final byte FIRST = 0;
    private static final byte SECOND = 1;

    @ParameterizedTest
    @MethodSource("layouts")
    void testByte(ValueLayout layout, MemorySegment segment) {
        test(byte.class, layout, segment, (byte) 10);
    }

    @ParameterizedTest
    @MethodSource("layouts")
    void testShort(ValueLayout layout, MemorySegment segment) {
        test(short.class, layout, segment, (short) 10);
    }

    @ParameterizedTest
    @MethodSource("layouts")
    void testChar(ValueLayout layout, MemorySegment segment) {
        test(char.class, layout, segment, (char) 10);
    }

    @ParameterizedTest
    @MethodSource("layouts")
    void testInt(ValueLayout layout, MemorySegment segment) {
        test(int.class, layout, segment, 10);
    }

    @ParameterizedTest
    @MethodSource("layouts")
    void testLong(ValueLayout layout, MemorySegment segment) {
        test(long.class, layout, segment, 10L);
    }

    @ParameterizedTest
    @MethodSource("layouts")
    void testFloat(ValueLayout layout, MemorySegment segment) {
        test(float.class, layout, segment, 10f);
    }

    @ParameterizedTest
    @MethodSource("layouts")
    void testDouble(ValueLayout layout, MemorySegment segment) {
        test(double.class, layout, segment, 10d);
    }

    @ParameterizedTest
    @MethodSource("layouts")
    void testBoolean(ValueLayout layout, MemorySegment segment) {
        if (layout instanceof OfBoolean) {
            PrimitiveMapper<Boolean> mapper = PrimitiveMapper.of(layout, boolean.class);
            boolean first = mapper.getAtIndex(segment, 0);
            boolean second = mapper.getAtIndex(segment, 1);
            assertFalse(first);
            assertTrue(second);
        } else {
            assertThrows(IllegalArgumentException.class, () ->
                    PrimitiveMapper.of(layout, boolean.class)
            );
        }
    }


    <T> void test(Class<T> type, ValueLayout layout, MemorySegment segment, T ten) {

        // No mapping for boolean (except to boolean)
        if (layout instanceof OfBoolean) {
            assertThrows(IllegalArgumentException.class, () ->
                    PrimitiveMapper.of(layout, type)
            );
            return;
        }

        PrimitiveMapper<T> mapper = PrimitiveMapper.of(layout, type);

        // Reading
        Number first = toNumber(mapper.get(segment));
        assertEquals(FIRST, first.longValue());
        Number second = toNumber(mapper.getAtIndex(segment, 1));
        assertEquals(SECOND, second.longValue());

        // Writing
        mapper.set(segment, ten);
        assertEquals(toNumber(ten).longValue(), get(segment, layout, 0L).longValue());
    }

    static Number get(MemorySegment segment, ValueLayout layout, long offset) {
        return switch (layout) {
            case OfByte   l -> segment.get(l, offset);
            case OfShort  l -> segment.get(l, offset);
            case OfChar   l -> (int) segment.get(l, offset);
            case OfInt    l -> segment.get(l, offset);
            case OfLong   l -> segment.get(l, offset);
            case OfFloat  l -> segment.get(l, offset);
            case OfDouble l -> segment.get(l, offset);
            default -> throw new IllegalArgumentException();
        };
    }

    static Number toNumber(Object o) {
        return switch (o) {
            case Number n    -> n;
            case Character c -> (int) c;
            default          -> throw new IllegalArgumentException();
        };
    }

    static Stream<Arguments> layouts() {
        return Stream.of(
                Arguments.of(JAVA_BYTE, MemorySegment.ofArray(new byte[]{FIRST, SECOND})),
                Arguments.of(JAVA_SHORT, MemorySegment.ofArray(new short[]{FIRST, SECOND})),
                Arguments.of(JAVA_CHAR, MemorySegment.ofArray(new char[]{FIRST, SECOND})),
                Arguments.of(JAVA_INT, MemorySegment.ofArray(new int[]{FIRST, SECOND})),
                Arguments.of(JAVA_FLOAT, MemorySegment.ofArray(new float[]{FIRST, SECOND})),
                Arguments.of(JAVA_LONG, MemorySegment.ofArray(new long[]{FIRST, SECOND})),
                Arguments.of(JAVA_DOUBLE, MemorySegment.ofArray(new double[]{FIRST, SECOND})),
                Arguments.of(JAVA_BOOLEAN, MemorySegment.ofArray(new byte[]{FIRST, SECOND}))
        );
    }

}
