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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestRecordMapper
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

final class TestRecordMapper {

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();

    private static final MemorySegment POINT_SEGMENT = MemorySegment.ofArray(new int[]{
                    3, 4,
                    6, 0,
                    9, 4})
            .asReadOnly();

    private static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y"));

    record Point(int x, int y) {
    }

    private static final GroupLayout LINE_LAYOUT = MemoryLayout.structLayout(
            POINT_LAYOUT.withName("begin"),
            POINT_LAYOUT.withName("end"));

    private record Line(Point begin, Point end) {
    }

    @Test
    void point() {
        MemorySegment segment = newCopyOf(POINT_SEGMENT);
        SegmentMapper<Point> mapper = SegmentMapper.ofRecord(LOCAL_LOOKUP, Point.class, POINT_LAYOUT);
        Point point = mapper.get(segment);
        assertEquals(3, point.x());
        assertEquals(4, point.y());
        Point point2 = mapper.get(segment, POINT_LAYOUT.byteSize());
        assertEquals(6, point2.x());
        assertEquals(0, point2.y());

        List<Point> points = mapper.stream(segment)
                .toList();
        assertEquals(List.of(new Point(3, 4), new Point(6, 0), new Point(9, 4)), points);

        assertEquals(mapper.layout(), POINT_LAYOUT);
        assertEquals(mapper.type(), Point.class);

        mapper.set(segment, POINT_LAYOUT.byteSize(), new Point(-1, -2));
        MapperTestUtil.assertContentEquals(new int[]{3, 4, -1, -2, 9, 4}, segment);
    }

    public record TinyPoint(byte x, byte y) {
    }

    @Test
    void mappedTinyPoint() {
        MemorySegment segment = newCopyOf(POINT_SEGMENT);
        SegmentMapper<Point> mapper = SegmentMapper.ofRecord(LOCAL_LOOKUP, Point.class, POINT_LAYOUT);
        SegmentMapper<TinyPoint> tinyMapper =
                mapper.map(TinyPoint.class,
                        p -> new TinyPoint((byte) p.x(), (byte) p.y()),
                        t -> new Point(t.x(), t.y()));
        assertEquals(TinyPoint.class, tinyMapper.type());
        assertEquals(POINT_LAYOUT, tinyMapper.layout());

        TinyPoint tp = tinyMapper.get(segment);
        assertEquals(new TinyPoint((byte) 3, (byte) 4), tp);
        tp = tinyMapper.getAtIndex(segment, 1);
        assertEquals(new TinyPoint((byte) 6, (byte) 0), tp);

        tinyMapper.setAtIndex(segment, 1, new TinyPoint((byte) -1, (byte) -2));
        MapperTestUtil.assertContentEquals(new int[]{3, 4, -1, -2, 9, 4}, segment);
    }

    @Test
    void line() {
        MemorySegment segment = newCopyOf(POINT_SEGMENT);
        SegmentMapper<Line> mapper = SegmentMapper.ofRecord(LOCAL_LOOKUP, Line.class, LINE_LAYOUT);

        Line point = mapper.get(segment);
        assertEquals(new Line(new Point(3, 4), new Point(6, 0)), point);

        mapper.set(segment, POINT_LAYOUT.byteSize(), new Line(
                new Point(-3, -4),
                new Point(-6, 0)
        ));
        MapperTestUtil.assertContentEquals(new int[]{3, 4, -3, -4, -6, 0}, segment);
    }

    record BunchOfPoints(Point p0, Point p1, Point p2, Point p3,
                         Point p4, Point p5, Point p6, Point p7) {
    }

    // This test is to make sure the iterative setter works (as opposed to the composed one).
    @Test
    void bunch() {
        StructLayout layout = MemoryLayout.structLayout(IntStream.range(0, 8)
                .mapToObj(i -> POINT_LAYOUT.withName("p"+i))
                .toArray(MemoryLayout[]::new));

        int noInts = (int) (layout.byteSize() / JAVA_INT.byteSize());

        MemorySegment segment = MemorySegment.ofArray(IntStream.range(0, noInts).toArray());
        SegmentMapper<BunchOfPoints> mapper = SegmentMapper.ofRecord(LOCAL_LOOKUP, BunchOfPoints.class, layout);
        BunchOfPoints bunchOfPoints = mapper.get(segment);

        BunchOfPoints expected = new BunchOfPoints(
                new Point(0 ,1), new Point(2, 3), new Point(4, 5), new Point(6, 7),
                new Point(8 ,9), new Point(10, 11), new Point(12, 13), new Point(14, 15)
        ) ;
        assertEquals(expected, bunchOfPoints);

        MemorySegment dstSegment = Arena.ofAuto().allocate(layout);

        mapper.set(dstSegment, 0, new BunchOfPoints(
                new Point(10 ,11), new Point(12, 13), new Point(14, 15), new Point(16, 17),
                new Point(18 ,19), new Point(20, 21), new Point(22, 23), new Point(24, 25)
        ));

        MapperTestUtil.assertContentEquals(IntStream.range(0, noInts).map(i -> i + 10).toArray(), dstSegment);
    }

    record SequenceBox(int before, int[] ints, int after) {

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SequenceBox(var otherBefore, var otherInts, var otherAfter) &&
                    before == otherBefore && Arrays.equals(ints, otherInts) && after == otherAfter;
        }

        @Override
        public String toString() {
            return SequenceBox.class.getSimpleName() +
                    "[before=" + before +
                    ", ints=" + Arrays.toString(ints) +
                    ", after=" + after+"]";
        }

        // Accessor similar to an interface mapper array accessor
        public int ints(long i) {
            return ints[Math.toIntExact(i)];
        }

        // Convenience method
        public List<Integer> intsAsList() {
            return Arrays.stream(ints)
                    .boxed()
                    .toList();
        }

    }

    @Test
    public void testSequenceBox() {
        var segment = MemorySegment.ofArray(IntStream.rangeClosed(0, 3).toArray());

        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("before"),
                MemoryLayout.sequenceLayout(2, JAVA_INT).withName("ints"),
                JAVA_INT.withName("after")
        );

        var mapper = SegmentMapper.ofRecord(LOCAL_LOOKUP, SequenceBox.class, layout);

        SequenceBox sequenceBox = mapper.get(segment);

        assertEquals(new SequenceBox(0, new int[]{1, 2}, 3), sequenceBox);

        var dstSegment = newCopyOf(segment);
        mapper.set(dstSegment, new SequenceBox(10, new int[]{11, 12}, 13));

        MapperTestUtil.assertContentEquals(IntStream.rangeClosed(10, 13).toArray(), dstSegment);

        assertThrows(NullPointerException.class, () -> {
            // The array is null
            mapper.set(dstSegment, new SequenceBox(10, null, 13));
        });

        assertThrows(IndexOutOfBoundsException.class, () -> {
            // The array is not of correct size
            mapper.set(dstSegment, new SequenceBox(10, new int[]{11}, 13));
        });
        assertThrows(IndexOutOfBoundsException.class, () -> {
            // The array is not of correct size
            mapper.set(dstSegment, new SequenceBox(10, new int[]{11, 12, 13}, 13));
        });
    }

    record SequenceBox2D(int before, int[][] ints, int after) {

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SequenceBox2D(var otherBefore, var otherInts, var otherAfter) &&
                    before == otherBefore &&
                    Arrays.deepEquals(ints, otherInts) &&
                    after == otherAfter;
        }

        @Override
        public String toString() {
            return SequenceBox.class.getSimpleName() +
                    "[before=" + before +
                    ", ints=" + Arrays.deepToString(ints) +
                    ", after=" + after+"]";
        }

        // Accessor similar to an interface mapper array accessor
        public int ints(long i, long j) {
            return ints[Math.toIntExact(i)][Math.toIntExact(j)];
        }

        // Convenience method
        public List<List<Integer>> intsAsList() {
            return Arrays.stream(ints)
                    .map(a -> Arrays.stream(a).boxed().toList())
                    .toList();
        }

    }

    @Test
    public void testSequenceBox2D() {
        var segment = MemorySegment.ofArray(IntStream.range(0, 1 + 2 * 3 + 1).toArray());

        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("before"),
                MemoryLayout.sequenceLayout(2,
                        MemoryLayout.sequenceLayout(3, JAVA_INT)
                ).withName("ints"),
                JAVA_INT.withName("after")
        );

        var mapper = SegmentMapper.ofRecord(LOCAL_LOOKUP, SequenceBox2D.class, layout);

        SequenceBox2D sequenceBox = mapper.get(segment);

        assertEquals(new SequenceBox2D(0, new int[][]{{1, 2, 3}, {4, 5, 6}}, 7), sequenceBox);

        var dstSegment = newCopyOf(segment);
        mapper.set(dstSegment, new SequenceBox2D(10, new int[][]{{11, 12, 13}, {14, 15, 16}}, 17));

        MapperTestUtil.assertContentEquals(IntStream.range(0, 1 + 2 * 3 + 1).map(i -> i + 10).toArray(), dstSegment);

        assertThrows(NullPointerException.class, () -> {
            // The array is null
            mapper.set(dstSegment, new SequenceBox2D(10, null, 13));
        });

        assertThrows(IndexOutOfBoundsException.class, () -> {
            // The array is not of correct size
            mapper.set(dstSegment, new SequenceBox2D(10, new int[][]{{11, 12, 13}}, 13));
        });
        assertThrows(IndexOutOfBoundsException.class, () -> {
            // The array is not of correct size
            mapper.set(dstSegment, new SequenceBox2D(10, new int[][]{{11, 12, 13}, {14, 15}}, 13));
        });
    }

    // Support methods

    private static MemorySegment newCopyOf(MemorySegment source) {
        return Arena.ofAuto()
                .allocate(source.byteSize())
                .copyFrom(source);
    }

}
