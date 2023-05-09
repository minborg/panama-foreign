/*
 *  Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
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
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */

/*
 * @test
 * @enablePreview
 * @modules java.base/jdk.internal.foreign.layout
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestRecordAccessor
 */

import jdk.internal.foreign.layout.ValueLayouts;
import org.junit.jupiter.api.*;

import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestRecordAccessor {

    private static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y"));

    private static final GroupLayout LINE_LAYOUT = MemoryLayout.structLayout(
            POINT_LAYOUT.withName("begin"),
            POINT_LAYOUT.withName("end"));

    private static final MemorySegment POINT_SEGMENT = MemorySegment.ofArray(new int[]{
            3, 4,
            6, 0,
            0, 0});

    // Records

    public record Point(int x, int y) {
    }

    public record LongPoint(long x, long y) {
    }

    public record Line(Point begin, Point end) {
    }

    // Manually declared record accessor

    static class PointMapper implements MemorySegment.Mapper<Point> {

        static final VarHandle BYTE_HANDLE = ((ValueLayouts.OfByteImpl) ValueLayout.JAVA_BYTE).accessHandle();
        static final GroupLayout LAYOUT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));

        @Override
        public Point get(MemorySegment segment, long offset) {
            BYTE_HANDLE.getVolatile(segment, offset);
            return new Point(segment.get(JAVA_INT, offset), segment.get(JAVA_INT, offset + 4));
        }

        @Override
        public void set(MemorySegment segment, long offset, Point value) {
            segment.set(JAVA_INT, offset, value.x());
            segment.set(JAVA_INT, offset + 4, value.y());
            // CAS(0, 0) instead?
            BYTE_HANDLE.setVolatile(segment, offset, BYTE_HANDLE.get(segment, offset));
        }

        @Override
        public GroupLayout layout() {
            return LAYOUT;
        }
    }

    @Test
    public void testCustomPointRecordAccessorGet() {
        var mapper = new PointMapper();
        Point first = mapper.get(POINT_SEGMENT);
        assertEquals(new Point(3, 4), first);
    }

    @Test
    public void testCustomPointRecordAccessorSet() {
        MemorySegment segment = Arena.ofAuto().allocate(POINT_LAYOUT);
        var mapper = new PointMapper();
        mapper.set(segment, new Point(3, 4));
        assertArrayEquals(new int[]{3, 4}, segment.toArray(JAVA_INT));
    }

    @Test
    public void testCustomPointMapper() {
        test(new PointMapper(), new Point(3, 4));
    }

    @Test
    public void testPointMapper() {
        test(MemorySegment.Mapper.of(Point.class, POINT_LAYOUT), new Point(3, 4));
    }

    @Test
    public void testLongPointMapper() {
        // This should fail as the types differ
        MemorySegment.Mapper.of(LongPoint.class, POINT_LAYOUT);
    }

    // Line

    @Test
    public void testLineMapper() {
        test(MemorySegment.Mapper.of(Line.class, LINE_LAYOUT), new Line(new Point(3, 4), new Point(6, 0)));
    }

    // A lot of types

    public record Types(byte by, boolean bo, short sh, char ch, int in, long lo, float fl, double dl) {
    }

    @Test
    public void testTypes() {

        // Test wrappers Integer etc.

        var layout = MemoryLayout.structLayout(
                JAVA_BYTE.withName("by"),
                JAVA_BOOLEAN.withName("bo"),
                JAVA_SHORT.withName("sh"),
                JAVA_CHAR.withName("ch"),
                JAVA_INT_UNALIGNED.withName("in"),
                JAVA_LONG_UNALIGNED.withName("lo"),
                JAVA_FLOAT_UNALIGNED.withName("fl"),
                JAVA_DOUBLE_UNALIGNED.withName("dl")
        );

        var segment = Arena.ofAuto().allocate(layout);

        layout.varHandle(PathElement.groupElement("by")).set(segment, (byte) 1);
        layout.varHandle(PathElement.groupElement("bo")).set(segment, true);
        layout.varHandle(PathElement.groupElement("sh")).set(segment, (short) 1);
        layout.varHandle(PathElement.groupElement("ch")).set(segment, 'a');
        layout.varHandle(PathElement.groupElement("in")).set(segment, 1);
        layout.varHandle(PathElement.groupElement("lo")).set(segment, 1L);
        layout.varHandle(PathElement.groupElement("fl")).set(segment, 1f);
        layout.varHandle(PathElement.groupElement("dl")).set(segment, 1d);

        var mapper = MemorySegment.Mapper.of(Types.class, layout);
        Types types = mapper.get(segment);
        assertEquals(new Types(
                (byte) 1,
                true,
                (short) 1,
                'a',
                1,
                1L,
                1.0f,
                1.0d
        ), types);
    }

    // Float80, From https://github.com/graalvm/sulong/blob/db830610d6ffbdab9678eef359a9f915e6ad2ee8/projects/com.oracle.truffle.llvm.types/src/com/oracle/truffle/llvm/types/floating/LLVM80BitFloat.java

    public record Float80(short exponent, long fraction)
            implements Comparable<Float80> {

        public static final Float80 MAX_VALUE = new Float80((short) (Short.MAX_VALUE - 1), 0xFFFF_FFFF_FFFF_FFFFL); // 1.7976931348623157e+308

        public static final int SIZE = 80;

        private static final short ALL_ONES_SHORT = (short) 0xFFFF;

        public static final Float80 POSITIVE_ZERO = new Float80((short) 0, 0);
        public static final Float80 NEGATIVE_ZERO = new Float80((short) 0x8000, 0);

        public static final Float80 POSITIVE_INFINITY = new Float80((short) (ALL_ONES_SHORT >>> 1), 1L << 63);
        public static final Float80 NEGATIVE_INFINITY = new Float80(ALL_ONES_SHORT, 1L << 63);


        public double asDouble() {
            if (POSITIVE_ZERO.equals(this)) {
                return +0;
            } else if (NEGATIVE_ZERO.equals(this)) {
                return -0;
            } else if (POSITIVE_INFINITY.equals(this)) {
                return Double.POSITIVE_INFINITY;
            } else if (NEGATIVE_INFINITY.equals(this)) {
                return Double.NEGATIVE_INFINITY;
            } else if (isNaN(this)) {
                return Double.NaN;
            } else {
                throw new UnsupportedOperationException("Todo");
                /*int doubleExponent = getUnbiasedExponent() + DoubleHelper.DOUBLE_EXPONENT_BIAS;
                long doubleFraction = (getFractionWithoutImplicitZero()) >>> (FRACTION_BIT_WIDTH - DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH);
                long shiftedSignBit = (sign() ? 1L : 0L) << DoubleHelper.DOUBLE_SIGN_POS;
                long shiftedExponent = (long) doubleExponent << DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH;
                long rawVal = doubleFraction | shiftedExponent | shiftedSignBit;
                return Double.longBitsToDouble(rawVal);*/
            }

        }

        boolean sign() {
            return (exponent & (short) (0x8000)) != 0;
        }

        @Override
        public int compareTo(Float80 o) {
            throw new UnsupportedOperationException("Todo");
        }

        public static boolean isNaN(Float80 v) {
            long lowerBits = v.fraction & 0x3FFF_FFFF_FFFF_FFFFL;
            return v.exponent == (short) (0xFFFF_FFFF) && lowerBits != 0;
        }

        public static boolean isZero(Float80 v) {
            return POSITIVE_ZERO.equals(v) || NEGATIVE_ZERO.equals(v);
        }


    }

    @Test
    public void testFloat80() {

        short exponent = (short) 3;
        long fraction = 23423423L;

        var layout = MemoryLayout.structLayout(
                JAVA_SHORT.withName("exponent"),
                JAVA_LONG_UNALIGNED.withName("fraction")
        );

        var segment = Arena.ofAuto().allocate(layout);

        layout.varHandle(PathElement.groupElement("exponent")).set(segment, exponent);
        layout.varHandle(PathElement.groupElement("fraction")).set(segment, fraction);

        var mapper = MemorySegment.Mapper.of(Float80.class, layout);
        Float80 float80 = mapper.get(segment);
        assertEquals(new Float80(exponent, fraction), float80);
    }

    @Test
    public void testLocalTime() {
        test(LocalTime.of(14, 2, 3, 41211));
    }

    @Test
    public void testLocalDate() {
        test(LocalDate.of(2023, 5, 9));
    }

    @Test
    public void testLocalDateTime() {
        test(LocalDateTime.of(
                LocalDate.of(2023, 5, 9),
                LocalTime.of(14, 2, 3, 41211)));
    }

    public <T> void test(T expected) {

        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) expected.getClass();

        MemorySegment.Mapper<T> mapper = MemorySegment.Mapper.of(type)
                .orElseThrow(() -> new IllegalArgumentException("The type " + type + " is not available"));

        test(mapper, expected);
    }

    public <T> void test(MemorySegment.Mapper<T> mapper, T expected) {
        long offset = Double.BYTES; // Largest carrier type

        System.out.println(mapper);

        var segment = Arena.ofAuto().allocate(mapper.layout().byteSize() + offset);

        // No Offset
        mapper.set(segment, expected);
        T actual = mapper.get(segment);
        assertEquals(expected, actual);
        segment.fill((byte)0);

        // Offset
        mapper.set(segment, offset, expected);
        T actualOffset = mapper.get(segment, offset);
        assertEquals(expected, actualOffset);
    }




}
