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

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class BaseTest {

    public static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y"));

    public static final GroupLayout LINE_LAYOUT = MemoryLayout.structLayout(
            POINT_LAYOUT.withName("begin"),
            POINT_LAYOUT.withName("end"));

    public record Empty(){}

    public record Point(int x, int y){}

    public record TinyPoint(byte x, byte y){}

    public record Line(Point begin, Point end){}

    public record SequenceListPoint(int before, List<Point> points, int after) {}

    public record Points(List<Point> points) {}

    public record PointSet(Set<Point> points) {}

    public interface PointAccessor {
        int x();
        void x(int x);
        int y();
        void y(int x);
    }

    public interface LineAccessor {
        Point begin();
        void begin(Point begin);
        Point end();
        void end(Point end);
    }

    public static final class LineBean {

        private int x;
        private int y;

        public LineBean(int x, int y) {
            this.x = x;
            this.y = y;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }

        void x(int x) {
            this.x = x;
        }

        void y(int y) {
            this.y = y;
        }

    }

    public static final MemorySegment POINT_SEGMENT = MemorySegment.ofArray(new int[]{
                    3, 4,
                    6, 0,
                    0, 0})
            .asReadOnly();

    public static final SegmentMapper<Point> POINT_MAPPER = pointMapper();

    private static SegmentMapper<Point> pointMapper() {
        try {
            return SegmentMapper.ofRecord(Point.class, POINT_LAYOUT);
        } catch (Throwable th) {
            th.printStackTrace();
            throw th;
        }
    }

    public static int[] segmentOf(int... ints) {
        return ints;
    }

    public static void assertContentEquals(int[] expected, MemorySegment actual) {
        assertContentEquals(MemorySegment.ofArray(expected), actual);
    }

    public static void assertContentEquals(MemorySegment expected, MemorySegment actual) {
        if (expected.mismatch(actual) != -1) {
            HexFormat hexFormat = HexFormat.ofDelimiter(" ");
            fail("Expected '" + hexFormat.formatHex(expected.toArray(JAVA_BYTE)) +
                    "' but got '" + hexFormat.formatHex(actual.toArray(JAVA_BYTE))+"'");
        }
    }

}
