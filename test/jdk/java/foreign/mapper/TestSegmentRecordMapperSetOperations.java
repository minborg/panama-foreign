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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestSegmentRecordMapperSetOperations
 */

import org.junit.jupiter.api.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

final class TestSegmentRecordMapperSetOperations extends BaseTest {

    private static final Point POINT = new Point(3, 4);

    private Arena arena;
    private MemorySegment pointSegment;

    @BeforeEach
    void setup() {
        arena = Arena.ofConfined();
        pointSegment = arena.allocate(POINT_LAYOUT);
    }

    @AfterEach
    void shutdown() {
        arena.close();
    }

    public record Empty(){}

    @Test
    void empty() {
        var mapper = SegmentMapper.ofRecord(Empty.class, POINT_LAYOUT);
        mapper.set(pointSegment, 0, new Empty());
        assertContentEquals(segmentOf(0, 0), pointSegment);
    }

    @Test
    void point() {
        POINT_MAPPER.set(pointSegment, POINT);
        assertContentEquals(segmentOf(3, 4), pointSegment);
        var segment = arena.allocate(POINT_LAYOUT, 3);
        POINT_MAPPER.set(segment, 2 * Integer.BYTES, POINT);
        POINT_MAPPER.setAtIndex(segment, 2, new Point(1,1));
        assertContentEquals(segmentOf(0, 0, 3 ,4, 1, 1), segment);
    }

    @Test
    void mappedTinyPoint() {
        SegmentMapper<Point> mapper = SegmentMapper.ofRecord(Point.class, POINT_LAYOUT);
        SegmentMapper<TinyPoint> tinyMapper =
                mapper.map(TinyPoint.class,
                        p -> new TinyPoint((byte) p.x(), (byte) p.y()),
                        t -> new Point(t.x(), t.y()));

        tinyMapper.set(pointSegment, new TinyPoint((byte) 3, (byte) 4));
        assertContentEquals(segmentOf(3, 4), pointSegment);
    }

    @Test
    void line() {
        SegmentMapper<Line> mapper = SegmentMapper.ofRecord(Line.class, LINE_LAYOUT);
        var segment = arena.allocate(LINE_LAYOUT);
        mapper.set(segment, new Line(new Point(3, 4), new Point(6, 0)));
        assertContentEquals(segmentOf(3, 4, 6, 0), segment);
    }

    public record SequenceBox(int before, int[] ints, int after) {

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SequenceBox other &&
                    before == other.before &&
                    Arrays.equals(ints, other.ints) &&
                    after == other.after;
        }

        @Override
        public String toString() {
            return "SequenceBox[before=" + before +
                    ", ints=" + Arrays.toString(ints) +
                    ", after=" + after + "]";
        }
    }

    @Test
    public void testSequenceBox() {

        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("before"),
                MemoryLayout.sequenceLayout(2, JAVA_INT).withName("ints"),
                JAVA_INT.withName("after")
        );

        var mapper = SegmentMapper.ofRecord(SequenceBox.class, layout);
        var segment = arena.allocate(layout);
        mapper.set(segment, new SequenceBox(0, new int[]{1, 2}, 3));

        assertContentEquals(segmentOf(0,1,2,3), segment);
    }

    public record SequenceListBox(int before, List<Integer> ints, int after) {}

    @Test
    public void testSequenceListBox() {

        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("before"),
                MemoryLayout.sequenceLayout(2, JAVA_INT).withName("ints"),
                JAVA_INT.withName("after")
        );

        var mapper = SegmentMapper.ofRecord(SequenceListBox.class, layout);
        var segment = arena.allocate(layout);
        mapper.set(segment, new SequenceListBox(0, List.of(1,2), 3));

        assertContentEquals(segmentOf(0,1,2,3), segment);
    }

    public record SequenceListPoint(int before, List<Point> points, int after) {}

    @Test
    public void testSequenceListPoint() {

        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("before"),
                MemoryLayout.sequenceLayout(2, POINT_LAYOUT).withName("points"),
                JAVA_INT.withName("after")
        );

        var mapper = SegmentMapper.ofRecord(SequenceListPoint.class, layout);
        var segment = arena.allocate(layout);
        mapper.set(segment, new SequenceListPoint(0, List.of(new Point(1, 2), new Point(3, 4)), 5));

        assertContentEquals(segmentOf(0, 1, 2, 3, 4, 5), segment);
    }

    public record SequenceArrayPoint(int before, Point[] points, int after) {}

    @Test
    public void testSequenceArrayPoint() {

        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("before"),
                MemoryLayout.sequenceLayout(2, POINT_LAYOUT).withName("points"),
                JAVA_INT.withName("after")
        );

        var mapper = SegmentMapper.ofRecord(SequenceArrayPoint.class, layout);
        var segment = arena.allocate(layout);
        mapper.set(segment, new SequenceArrayPoint(0, new Point[]{new Point(1, 2), new Point(3, 4)}, 5));

        assertContentEquals(segmentOf(0, 1, 2, 3, 4, 5), segment);
    }

    @Test
    void genericTypesRecord() {
        record Foo(int i, List<String> list, int j) {}
        Type gt = Foo.class.getRecordComponents()[1].getGenericType();

        if (gt instanceof ParameterizedType pt) {
            assertArrayEquals(new Type[]{String.class}, pt.getActualTypeArguments());
        } else {
            fail("No generic type");
        }
    }


    @Test
    void exceptions() {

    }

    private static int[] segmentOf(int... ints) {
        return ints;
    }

    private static void assertContentEquals(int[] expected, MemorySegment actual) {
        assertContentEquals(MemorySegment.ofArray(expected), actual);
    }

    private static void assertContentEquals(MemorySegment expected, MemorySegment actual) {
        if (expected.mismatch(actual) != -1) {
            HexFormat hexFormat = HexFormat.ofDelimiter(" ");
            fail("Expected '" + hexFormat.formatHex(expected.toArray(JAVA_BYTE)) +
                    "' but got '" + hexFormat.formatHex(actual.toArray(JAVA_BYTE))+"'");
        }
    }

}
