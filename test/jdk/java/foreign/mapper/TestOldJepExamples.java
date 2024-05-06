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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestOldJepExamples
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.mapper.RecordMapper;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.VarHandle;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

final class TestOldJepExamples {

    // Suppose we want to work with native memory in the form:
    //
    // struct point {
    //    int32_t x;
    //    int32_t y;
    // };
    //
    // struct line {
    //    struct point begin;
    //    struct point end;
    // };


    private static final
    StructLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),  // An `int` named "x", with `ByteOrder.NATIVE_ORDER` aligned at 4 bytes
            JAVA_INT.withName("y")); // Ditto but named "y"


    private static final
    StructLayout LINE_LAYOUT = MemoryLayout.structLayout(
            POINT_LAYOUT.withName("begin"),
            POINT_LAYOUT.withName("end"));


    Arena arena;
    MemorySegment segment;

    public record Point(int x, int y) {
    }

    public record Line(Point begin, Point end) {
    }

    static Point getPoint(MemorySegment segment, long offset) {
        return new Point(
                segment.get(JAVA_INT, offset),
                segment.get(JAVA_INT, offset + JAVA_INT.byteSize())
        );
    }

    static Point getPointAtIndex(MemorySegment segment, long index) {
        return getPoint(segment, Math.multiplyExact(POINT_LAYOUT.byteSize(), index));
    }

    static void set(MemorySegment segment, long offset, Point point) {
        segment.set(JAVA_INT, offset, point.x());
        segment.set(JAVA_INT, offset + JAVA_INT.byteSize(), point.y());
    }

    static void setAtIndex(MemorySegment segment, long index, Point point) {
        set(segment, Math.multiplyExact(POINT_LAYOUT.byteSize(), index), point);
    }

    @BeforeEach
    void setup() {
        arena = Arena.ofShared();
        segment = arena.allocateFrom(JAVA_INT,
                3, 4,   // Point[x=3, y=4]  ---+--- Line[begin=Point[x=3, y=4], end=Point[x=6, y=0]]
                6, 0,   // Point[x=6, y=0]  ---|

                9, 4,   // Point[x=9, y=4]  ---+--- Line[begin=Point[x=9, y=4], end=Point[x=0, y=5]]
                0, 5);  // Point[x=0, y=5]  ---|
    }

    @AfterEach
    void shutDown() {
        arena.close();
    }

    @Test
    void customPointRecordMapper() {
        Point point = getPointAtIndex(segment, 1);
        // Point[x=6, y=0]

        setAtIndex(segment, 1, new Point(-1, -2));
        // segment = 3, 4, -1, -2, 9, 4, 0, 5
        //                 |-----|
        //                 Updated

        assertEquals(new Point(6, 0), point);
        assertEquals(-1, arena.allocateFrom(JAVA_INT, 3, 4, -1, -2, 9, 4, 0, 5).mismatch(segment));
    }

    static Line getLine(MemorySegment segment, long offset) {
        return new Line(
                getPoint(segment, offset),
                getPoint(segment, offset + POINT_LAYOUT.byteSize())
        );
    }

    static Line getLineAtIndex(MemorySegment segment, long index) {
        return getLine(segment, Math.multiplyExact(LINE_LAYOUT.byteSize(), index));
    }

    static void set(MemorySegment segment, long offset, Line line) {
        set(segment, offset, line.begin());
        set(segment, offset + POINT_LAYOUT.byteSize(), line.end());
    }

    static void setAtIndex(MemorySegment segment, long index, Line line) {
        set(segment, Math.multiplyExact(LINE_LAYOUT.byteSize(), index), line);
    }

    @Test
    void customLineRecordMapper() {
        Line line = getLineAtIndex(segment, 1);
        // Line[begin=Point[x=9, y=4], end=Point[x=0, y=5]]

        setAtIndex(segment, 1, new Line(new Point(-1, -2), new Point(-3, -4)));
        // segment = 3, 4, 6, 0, -1, -2, -3, -4
        //                       |------------|
        //                           Updated

        assertEquals(new Line(new Point(9, 4), new Point(0, 5)), line);
        assertEquals(-1, arena.allocateFrom(JAVA_INT, 3, 4, 6, 0, -1, -2, -3, -4).mismatch(segment));
    }


    public interface PointAccessor {
        int x();

        void x(int x);

        int y();

        void y(int y);
    }

    public static final
    class MyPointAccessor implements PointAccessor {

        private static final VarHandle X_HANDLE =
                POINT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("x"));

        private static final VarHandle Y_HANDLE =
                POINT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("y"));

        // Holds the memory segment we are projecting members to/from
        private final MemorySegment segment;

        // Holds the offset in the segment where we project values
        private final long offset;

        private MyPointAccessor(MemorySegment segment, long offset) {
            this.segment = Objects.requireNonNull(segment);
            this.offset = Objects.checkFromIndexSize(offset, POINT_LAYOUT.byteSize(), segment.byteSize());
            if (!SomeUtil.checkAlignment(segment, offset, POINT_LAYOUT)) {
                throw new IllegalArgumentException("Segment and offset not aligned to " + POINT_LAYOUT);
            }
        }

        @Override
        public int x() {
            return (int) X_HANDLE.get(segment, offset);
        }

        @Override
        public int y() {
            return (int) Y_HANDLE.get(segment, offset);
        }

        @Override
        public void x(int x) {
            X_HANDLE.set(segment, offset, x);
        }

        @Override
        public void y(int y) {
            Y_HANDLE.set(segment, offset, y);
        }

        @java.lang.Override
        public boolean equals(Object obj) {
            return (obj instanceof MyPointAccessor that) &&
                    this.x() == that.x() &&
                    this.y() == that.y();
        }

        @java.lang.Override
        public int hashCode() {
            int result = x();
            result = 31 * result + y();
            return result;
        }

        @java.lang.Override
        public String toString() {
            return "PointAccessor[" +
                    "x()=" + x() +
                    ", y()=" + y() +
                    ']';
        }

        static PointAccessor get(MemorySegment segment, long offset) {
            return new MyPointAccessor(segment, offset);
        }

        static PointAccessor getAtIndex(MemorySegment segment, long index) {
            return get(segment, Math.multiplyExact(index, POINT_LAYOUT.byteSize()));
        }

    }

    private static final class SomeUtil {
        static boolean checkAlignment(MemorySegment segment, long offset, MemoryLayout layout) {
            return true;
        }
    }

    @Test
    void customInterfaceClass() {
        // Connected to a segment. Not stand-alone! A view...
        PointAccessor pointAccessor = MyPointAccessor.getAtIndex(segment, 1);

        assertEquals(6, pointAccessor.x());
        assertEquals(0, pointAccessor.y());
        assertEquals("PointAccessor[x()=6, y()=0]", pointAccessor.toString());

        // Partial update of a point
        pointAccessor.y(-2);
        // segment = 3, 4, 6, -2, 9, 4, 0, 5
        //                   |--|
        //                  Updated

        assertEquals(-2, pointAccessor.y());

        // Not only has the value changed but also the backing segment
        assertEquals(-1, arena.allocateFrom(JAVA_INT, 3, 4, 6, -2, 9, 4, 0, 5).mismatch(segment));

    }

    // MAPPER

    @Test
    void pointRecordMapper() {
        // Automatically creates a mapper that can read and write Point records from/to segments.
        RecordMapper<Point> mapper =
                RecordMapper.ofRecord(Point.class, POINT_LAYOUT);

        Point point = mapper.getAtIndex(segment, 1);
        // Point[x=6, y=0]

        mapper.setAtIndex(segment, 1, new Point(-1, -2));
        // segment = 3, 4, -1, -2, 9, 4, 0, 5
        //                 |-----|
        //                 Updated

        assertEquals(new Point(6, 0), point);
        assertEquals(-1, arena.allocateFrom(JAVA_INT, 3, 4, -1, -2, 9, 4, 0, 5).mismatch(segment));
    }

    @Test
    void lineRecordMapper() {

        RecordMapper<Line> mapper =
                RecordMapper.ofRecord(Line.class, LINE_LAYOUT);

        Line line = mapper.getAtIndex(segment, 1);
        // Line[begin=Point[x=9, y=4], end=Point[x=0, y=5]]

        mapper.setAtIndex(segment, 1, new Line(new Point(-1, -2), new Point(-3, -4)));
        // segment = 3, 4, 6, 0, -1, -2, -3, -4
        //                       |------------|
        //                           Updated

        assertEquals(new Line(new Point(9,4),  new Point(0, 5)), line);
        assertEquals(-1, arena.allocateFrom(JAVA_INT, 3, 4, 6, 0, -1, -2, -3, -4).mismatch(segment));
    }

    record TinyPoint(byte x, byte y) { }

    // Lossless narrowing
    TinyPoint toTiny(Point point) {
        return new TinyPoint(toByteExact(point.x()), toByteExact(point.y()));
    }

    Point fromTiny(TinyPoint point) {
        return new Point(point.x(), point.y());
    }

    private static byte toByteExact(int value) {
        if ((byte) value != value) {
            throw new ArithmeticException("byte overflow");
        }
        return (byte) value;
    }

    @Test
    void narrowing() {
        // Original mapper
        RecordMapper<Point> mapper =
                RecordMapper.ofRecord(Point.class, POINT_LAYOUT);

        // Secondary-stage mapper
        SegmentMapper<TinyPoint> tinyMapper =
                mapper.map(TinyPoint.class, this::toTiny, this::fromTiny);

        TinyPoint tp = tinyMapper.get(segment);
        assertEquals(new TinyPoint((byte) 3, (byte) 4), tp);
    }

    double distanceToOrigin(Point point) {
        return Math.sqrt(point.x() * point.x() + point.y() * point.y());
    }

    @Test
    void stream() {
        RecordMapper<Point> mapper =
                RecordMapper.ofRecord(Point.class, POINT_LAYOUT);

        // Calculate the average distance from each point
        // to the origin.
        double averageDistance = mapper.stream(segment)
                .mapToDouble(this::distanceToOrigin)
                .average()
                .orElse(0);

        assertEquals(6.462214450449026, averageDistance, 1E-8);

    }

}
