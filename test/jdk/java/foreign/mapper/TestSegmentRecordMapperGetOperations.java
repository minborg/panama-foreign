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

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.Objects;
import java.util.stream.IntStream;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

final class TestSegmentRecordMapperGetOperations extends BaseTest {

    @Test
    void point() {
        SegmentMapper<Point> mapper = SegmentMapper.ofRecord(Point.class, POINT_LAYOUT);

        Point point = mapper.get(POINT_SEGMENT);
        assertEquals(new Point(3,4), point);
        point = mapper.get(POINT_SEGMENT, 8);
        assertEquals(new Point(6,0), point);
        point = mapper.getAtIndex(POINT_SEGMENT, 1);
        assertEquals(new Point(6,0), point);
    }

    @Test
    void mappedTinyPoint() {
        SegmentMapper<Point> mapper = SegmentMapper.ofRecord(Point.class, POINT_LAYOUT);
        SegmentMapper<TinyPoint> tinyMapper =
                mapper.map(TinyPoint.class,
                           p -> new TinyPoint((byte) p.x(), (byte) p.y()),
                           t -> new Point(t.x(), t.y()));
        assertEquals(TinyPoint.class, tinyMapper.type());
        assertEquals(POINT_LAYOUT, tinyMapper.layout());

        TinyPoint tp = tinyMapper.get(POINT_SEGMENT);
        assertEquals(new TinyPoint((byte) 3, (byte) 4), tp);
        tp = tinyMapper.getAtIndex(POINT_SEGMENT, 1);
        assertEquals(new TinyPoint((byte) 6, (byte) 0), tp);
    }

    @Test
    void line() {
        SegmentMapper<Line> mapper = SegmentMapper.ofRecord(Line.class, LINE_LAYOUT);

        Line point = mapper.get(POINT_SEGMENT);
        assertEquals(new Line(new Point(3,4), new Point(6, 0)), point);
    }


    // Records

    public record FlippedPoint(int y, int x) {
    }

    public record PointUnion(Point normal, FlippedPoint flipped) {
        static final GroupLayout LAYOUT = MemoryLayout.unionLayout(
                POINT_LAYOUT.withName("normal"),
                POINT_LAYOUT.withName("flipped")
        );
    }

    public record PointUnionUnion(PointUnion left, PointUnion right) {
        static final GroupLayout LAYOUT = MemoryLayout.unionLayout(
                PointUnion.LAYOUT.withName("left"),
                PointUnion.LAYOUT.withName("right")
        );
    }


    public record LongPoint(long x, long y) {
    }

    // Manually declared function

    static final class PointMapper implements SegmentMapper<Point> {

        @Override
        public Point get(MemorySegment segment, long offset) {
            return new Point(segment.get(JAVA_INT, offset), segment.get(JAVA_INT, offset + 4L));
        }

        @Override
        public Point get(MemorySegment segment) {
            return get(segment, 0);
        }

        @Override
        public Class<Point> type() {
            return Point.class;
        }

        @Override
        public GroupLayout layout() {
            return POINT_LAYOUT;
        }

        @Override
        public MethodHandle getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MethodHandle setHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <R> SegmentMapper<R> map(Class<R> newType,
                                        Function<? super Point, ? extends R> toMapper,
                                        Function<? super R, ? extends Point> fromMapper) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <R> SegmentMapper<R> map(Class<R> newType, Function<? super Point, ? extends R> toMapper) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    public void testCustomPoint() {
        test(POINT_SEGMENT, new PointMapper(), new Point(3, 4));
    }

    @Test
    public void testPointMapper() {
        test(POINT_SEGMENT, POINT_MAPPER, new Point(3, 4));
    }

    @Test
    public void testPointMapperUnderflow() {
        assertThrows(IndexOutOfBoundsException.class, () ->
                POINT_MAPPER.get(MemorySegment.ofArray(new int[]{1})));
    }

    public record StringPoint(String x, String y){}

    @Test
    public void testLongPointTypeMismatch() {
        // This should fail as the types `int` and `String` cannot be mapped
        assertThrows(IllegalArgumentException.class, () ->
            SegmentMapper.ofRecord(StringPoint.class, POINT_LAYOUT)
        );
    }

    @Test
    public void testEmptyRecord() {
        var mapper = SegmentMapper.ofRecord(Empty.class, POINT_LAYOUT);
        Empty empty = mapper.get(POINT_SEGMENT);
        assertEquals(new Empty(), empty);
    }

    public record Unmatched(int foo){}

    @Test
    public void noMapping() {
        assertThrows(IllegalArgumentException.class, () ->
                SegmentMapper.ofRecord(Unmatched.class, POINT_LAYOUT)
        );
    }

    @Test
    public void testFlippedPointMapper() {
        test(POINT_SEGMENT, SegmentMapper.ofRecord(FlippedPoint.class, POINT_LAYOUT), new FlippedPoint(4, 3));
    }

    // Line

    @Test
    public void testLineMapper() {
        test(POINT_SEGMENT, SegmentMapper.ofRecord(Line.class, LINE_LAYOUT), new Line(new Point(3, 4), new Point(6, 0)));
    }

    // Union
    @Test
    public void testUnion() {
        test(POINT_SEGMENT, SegmentMapper.ofRecord(PointUnion.class, PointUnion.LAYOUT),
                new PointUnion(
                        new Point(3, 4),
                        new FlippedPoint(4, 3))
        );
    }

    // Union of Union
    @Test
    public void testUnionUnion() {
        test(POINT_SEGMENT, SegmentMapper.ofRecord(PointUnionUnion.class, PointUnionUnion.LAYOUT),
                new PointUnionUnion(
                        new PointUnion(
                                new Point(3, 4),
                                new FlippedPoint(4, 3)),
                        new PointUnion(
                                new Point(3, 4),
                                new FlippedPoint(4, 3))
                ));
    }

    // Test Padding
    @Test
    public void testPadding() {
        GroupLayout paddedPointLayout = MemoryLayout.structLayout(
                MemoryLayout.paddingLayout(Integer.BYTES * 2).withName("padding"),
                JAVA_INT.withName("x"),
                JAVA_INT.withName("y"));
        test(POINT_SEGMENT, SegmentMapper.ofRecord(Point.class, paddedPointLayout), new Point(6, 0));
    }

    @Test
    public void testStream() {
        List<Point> points = POINT_MAPPER.stream(POINT_SEGMENT).toList();
        assertEquals(List.of(new Point(3, 4), new Point(6, 0), new Point(0, 0)), points);
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

        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(layout);

            layout.varHandle(PathElement.groupElement("by")).set(segment, 0L, (byte) 1);
            layout.varHandle(PathElement.groupElement("bo")).set(segment, 0L, true);
            layout.varHandle(PathElement.groupElement("sh")).set(segment, 0L, (short) 1);
            layout.varHandle(PathElement.groupElement("ch")).set(segment, 0L, 'a');
            layout.varHandle(PathElement.groupElement("in")).set(segment, 0L, 1);
            layout.varHandle(PathElement.groupElement("lo")).set(segment, 0L, 1L);
            layout.varHandle(PathElement.groupElement("fl")).set(segment, 0L, 1f);
            layout.varHandle(PathElement.groupElement("dl")).set(segment, 0L, 1d);

            var mapper = SegmentMapper.ofRecord(Types.class, layout);
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
    }

    // Float80, From https://github.com/graalvm/sulong/blob/db830610d6ffbdab9678eef359a9f915e6ad2ee8/projects/com.oracle.truffle.llvm.types/src/com/oracle/truffle/llvm/types/floating/LLVM80BitFloat.java

    public record Float80(short exponent, long fraction){}

    @Test
    public void testFloat80() {

        short exponent = (short) 3;
        long fraction = 23423423L;

        var layout = MemoryLayout.structLayout(
                JAVA_SHORT.withName("exponent"),
                JAVA_LONG_UNALIGNED.withName("fraction")
        );

        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(layout);

            layout.varHandle(PathElement.groupElement("exponent")).set(segment, 0L, exponent);
            layout.varHandle(PathElement.groupElement("fraction")).set(segment, 0L, fraction);

            var mapper = SegmentMapper.ofRecord(Float80.class, layout);
            Float80 float80 = mapper.get(segment);
            assertEquals(new Float80(exponent, fraction), float80);
        }
    }

    @Test
    public void testToString() {
        var toString = POINT_MAPPER.toString();
        assertTrue(toString.contains("lookup=" + MethodHandles.publicLookup()));
        assertTrue(toString.contains("type=" + Point.class));
        assertTrue(toString.contains("layout=" + POINT_LAYOUT));
    }

    public record BytePoint(byte x, byte y) {}
    @Test
    public void testByte() {
        testPointType(new BytePoint((byte)3, (byte)4), new byte[]{3, 4}, JAVA_BYTE);
    }

    public record BooleanPoint(boolean x, boolean y) {}
    @Test
    public void testBoolean() {
        testPointType(new BooleanPoint(false, true), new byte[]{0, 1}, JAVA_BOOLEAN);
    }

    public record ShortPoint(short x, short y) {}
    @Test
    public void testShort() {
        testPointType(new ShortPoint((short)3, (short)4), new short[]{3, 4}, JAVA_SHORT);
    }

    public record CharPoint(char x, char y) {}
    @Test
    public void testChar() {
        testPointType(new CharPoint('d', 'e'), new char[]{'d', 'e'}, JAVA_CHAR);
    }

    public record IntPoint(int x, int y) {}
    @Test
    public void testInt() {
        testPointType(new IntPoint(3, 4), new int[]{3, 4}, JAVA_INT);
    }

    @Test
    public void testLong() {
        testPointType(new LongPoint(3L, 4L), new long[]{3L, 4L}, JAVA_LONG);
    }

    public record FloatPoint(float x, float y) {}
    @Test
    public void testFloat() {
        testPointType(new FloatPoint(3.0f, 4.0f), new float[]{3.0f, 4.0f}, JAVA_FLOAT);
    }

    public record DoublePoint(double x, double y){}
    @Test
    public void testDouble() {
        testPointType(new DoublePoint(3.0d, 4.0d), new double[]{3.0d, 4.0d}, JAVA_DOUBLE);
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

        var segment = MemorySegment.ofArray(IntStream.rangeClosed(0, 3).toArray());

        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("before"),
                MemoryLayout.sequenceLayout(2, JAVA_INT).withName("ints"),
                JAVA_INT.withName("after")
        );

        var mapper = SegmentMapper.ofRecord(SequenceBox.class, layout);

        SequenceBox sequenceBox = mapper.get(segment);

        assertEquals(new SequenceBox(0, new int[]{1, 2}, 3), sequenceBox);
    }

    public record SequenceListBox(int before, List<Integer> ints, int after) {}

    @Test
    public void testSequenceListBox() {

        var segment = MemorySegment.ofArray(IntStream.rangeClosed(0, 3).toArray());

        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("before"),
                MemoryLayout.sequenceLayout(2, JAVA_INT).withName("ints"),
                JAVA_INT.withName("after")
        );

        var mapper = SegmentMapper.ofRecord(SequenceListBox.class, layout);

        SequenceListBox sequenceBox = mapper.get(segment);

        assertEquals(new SequenceListBox(0, List.of(1, 2), 3), sequenceBox);
    }

    public record PureArray(int[] ints) {

        @Override
        public boolean equals(Object obj) {
            return obj instanceof PureArray other &&
                    Arrays.equals(ints, other.ints);
        }

        @Override
        public String toString() {
            return "PureArray[ints=" + Arrays.toString(ints) + "]";
        }
    }

    @Test
    public void testPureArray() {
        GroupLayout layout =
                MemoryLayout.structLayout(
                        MemoryLayout.sequenceLayout(8, JAVA_INT)
                                .withName("ints"));

        var segment = MemorySegment.ofArray(IntStream.range(0, 8).toArray());

        var mapper = SegmentMapper.ofRecord(PureArray.class, layout);

        PureArray pureArray = mapper.get(segment);

        assertEquals(new PureArray(new int[]{0, 1, 2, 3, 4, 5, 6, 7}), pureArray);
    }

    public record SequenceOfPoints(int before, Point[] points, int after) {

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SequenceOfPoints other &&
                    before == other.before &&
                    Arrays.equals(points, other.points) &&
                    after == other.after;
        }

        @Override
        public String toString() {
            return "SequenceOfPoints[before=" + before +
                    ", points=" + Arrays.toString(points) +
                    ", after=" + after + "]";
        }

    }

    @Test
    public void testSequenceOfPoints() {

        var segment = MemorySegment.ofArray(IntStream.rangeClosed(0, 5).toArray());

        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("before"),
                MemoryLayout.sequenceLayout(2, POINT_LAYOUT).withName("points"),
                JAVA_INT.withName("after")
        );

        var mapper = SegmentMapper.ofRecord(SequenceOfPoints.class, layout);

        SequenceOfPoints sequenceOfPoints = mapper.get(segment);

        assertEquals(new SequenceOfPoints(0, new Point[]{new Point(1, 2), new Point(3,4)}, 5), sequenceOfPoints);
    }

    @Test
    public void testSequenceListPoints() {

        var segment = MemorySegment.ofArray(IntStream.rangeClosed(0, 5).toArray());

        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("before"),
                MemoryLayout.sequenceLayout(2, POINT_LAYOUT).withName("points"),
                JAVA_INT.withName("after")
        );

        var mapper = SegmentMapper.ofRecord(SequenceListPoint.class, layout);

        SequenceListPoint sequenceOfPoints = mapper.get(segment);

        assertEquals(new SequenceListPoint(0, List.of(new Point(1, 2), new Point(3,4)), 5), sequenceOfPoints);
    }

    @Test
    public void testPoints() {

        var segment = MemorySegment.ofArray(IntStream.rangeClosed(0, 3).toArray());

        var layout = MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(2, POINT_LAYOUT).withName("points")
        );

        var mapper = SegmentMapper.ofRecord(Points.class, layout);

        Points sequenceOfPoints = mapper.get(segment);

        assertEquals(new Points(List.of(new Point(0, 1), new Point(2,3))), sequenceOfPoints);
    }

    @Test
    public void testPointSet() {

        var segment = MemorySegment.ofArray(IntStream.rangeClosed(0, 3).toArray());

        var layout = MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(2, POINT_LAYOUT).withName("points")
        );

        var mapper = SegmentMapper.ofRecord(PointSet.class, layout);

        PointSet sequenceOfPoints = mapper.get(segment);

        assertEquals(new PointSet(Set.of(new Point(0, 1), new Point(2,3))), sequenceOfPoints);
    }

    @Test
    public void streaming() {
        var segment = MemorySegment.ofArray(new int[]{-1, 2, 3, 4, 5, -2});
        var s2 = segment.asSlice(4, 16);

        var list = POINT_MAPPER.stream(s2)
                .toList();

        assertEquals(List.of(new Point(2, 3), new Point(4, 5)), list);
    }

    @Test
    public void testPointSequence() {

        var segment = MemorySegment.ofArray(new int[]{-1, 2, 3, 4, 5, -2});

        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("before"),
                MemoryLayout.sequenceLayout(2, POINT_LAYOUT).withName("points"),
                JAVA_INT.withName("after")
        );

        var mapper = SegmentMapper.ofRecord(SequenceOfPoints.class, layout);

        SequenceOfPoints sequenceOfPoints = mapper.get(segment);

        assertEquals(new SequenceOfPoints(-1, new Point[]{new Point(2, 3), new Point(4, 5)}, -2), sequenceOfPoints);
    }

    private record Foo(int x){}
    @Test
    public void testConstructorAccessibility() {
        assertThrows(IllegalArgumentException.class, () ->
                SegmentMapper.ofRecord(Foo.class, POINT_LAYOUT)
        );
    }

    @Test
    public void testMhComposition() throws Throwable {
        var lookup = MethodHandles.lookup();
        var ctor = lookup.findConstructor(Point.class, MethodType.methodType(void.class, int.class, int.class));

        var extractorType = MethodType.methodType(int.class, ValueLayout.OfInt.class, long.class);

        var xVh = lookup.findVirtual(MemorySegment.class, "get", extractorType);
        // (MemorySegment, OfInt, long) -> (MemorySegment, long)
        var xVh2 = MethodHandles.insertArguments(xVh, 1, JAVA_INT);
        // (MemorySegment, long) -> (MemorySegment)
        var xVh3 = MethodHandles.insertArguments(xVh2, 1, 0L);

        var yVh = lookup.findVirtual(MemorySegment.class, "get", extractorType);
        // (MemorySegment, OfInt, long) -> (MemorySegment, long)
        var yVh2 = MethodHandles.insertArguments(yVh, 1, JAVA_INT);
        // (MemorySegment, long) -> (MemorySegment)
        var yVh3 = MethodHandles.insertArguments(yVh2, 1, 4L);

        assertEquals(3, (int) xVh3.invokeExact(POINT_SEGMENT));
        assertEquals(4, (int) yVh3.invokeExact(POINT_SEGMENT));

        var expected = new Point(3, 4);

        var p = ctor.invokeWithArguments((int) xVh3.invokeExact(POINT_SEGMENT), (int) yVh3.invokeExact(POINT_SEGMENT));
        assertEquals(expected, p);

        var ctorFilter = MethodHandles.filterArguments(ctor, 0, xVh3);
        var ctorFilter2 = MethodHandles.filterArguments(ctorFilter, 1, yVh3);

        var pf = (Point) ctorFilter2.invokeExact(POINT_SEGMENT, POINT_SEGMENT);
        assertEquals(expected, pf);

        var mt = MethodType.methodType(Point.class, MemorySegment.class);
        var mh = MethodHandles.permuteArguments(ctorFilter2, mt, 0, 0);

        // Finally, we have a MethodHandle MemorySegment -> Point
        Point point = (Point) mh.invokeExact(POINT_SEGMENT);
        assertEquals(expected, point);
    }

    public record NarrowedPoint(byte x, byte y) {}

    @Test
    public void testNarrowingExplicit() {

        SegmentMapper<NarrowedPoint> narrowingMapper = POINT_MAPPER
                                .map(NarrowedPoint.class, p -> new NarrowedPoint((byte) p.x(), (byte) p.y()));

        NarrowedPoint narrowedPoint = narrowingMapper.get(POINT_SEGMENT);
        assertEquals(new NarrowedPoint((byte) 3, (byte) 4), narrowedPoint);
    }

    @Test
    public void inspectPoint() {
        String view = POINT_MAPPER
                .get(POINT_SEGMENT)
                .toString();

        assertEquals("Point[x=3, y=4]", view);
    }

    @Test
    public void inspectMemory() {
        try (var arena = Arena.ofConfined()) {
            MemorySegment memorySegment = arena.allocate(64 + 4);
            memorySegment.setString(0, "The quick brown fox jumped over the lazy dog\nSecond line\t:here");
            HexFormat format = HexFormat.ofDelimiter(" ").withUpperCase();
            String hex = format.formatHex(memorySegment.toArray(JAVA_BYTE));

            String expected = "54 68 65 20 71 75 69 63 6B 20 62 72 6F 77 6E 20 66 6F 78 20 6A 75 6D 70 65 64 20 6F 76 65 72 20 74 68 65 20 6C 61 7A 79 20 64 6F 67 0A 53 65 63 6F 6E 64 20 6C 69 6E 65 09 3A 68 65 72 65 00 00 00 00 00 00";
            assertEquals(expected, hex);
        }
    }

    @Test
    public void recordClassItself() {
        assertThrows(IllegalArgumentException.class, () ->
                SegmentMapper.ofRecord(Record.class, POINT_LAYOUT)
        );
    }

    public record LinkedNode(MemorySegment next, int value){

        @Override
        public boolean equals(Object obj) {
            return obj instanceof LinkedNode(var next, var value) &&
                    this.next == next &&
                    this.value == value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(next, value);
        }

    }

    @Test
    public void linkedNode() {

        var rawLayout = MemoryLayout.structLayout(ADDRESS, JAVA_INT);

        var layout = MemoryLayout.structLayout(
                ADDRESS.withName("next").withTargetLayout(rawLayout),
                JAVA_INT.withName("value")
        );

        VarHandle next = layout.varHandle(PathElement.groupElement("next"));
        VarHandle value = layout.varHandle(PathElement.groupElement("value"));

        MemorySegment first;
        MemorySegment second;
        try (var arena = Arena.ofConfined()) {
            first = arena.allocate(layout);
            value.set(first, 0L, 41);
            second = arena.allocate(layout);
            value.set(second, 0L, 42);
            next.set(first, 0L, second);

            var mapper = SegmentMapper.ofRecord(LinkedNode.class, layout);

            LinkedNode actualFirst = mapper.get(first);
            assertEquals(41, actualFirst.value());
            assertEquals(second, actualFirst.next());

            LinkedNode actualSecond = mapper.get(actualFirst.next());
            assertEquals(42, actualSecond.value());
            assertEquals(MemorySegment.NULL, actualSecond.next());
        }

    }

    public record TreeNode(MemorySegment[] children, int value){

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TreeNode(var children, var value) &&
                    Arrays.equals(this.children, children) &&
                    this.value == value;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(children) + value;
        }

        @Override
        public String toString() {
            return "TreeNode[children=" + Arrays.toString(children) + ", value=" + value + "]";
        }
    }

    @Test
    public void TreeNode() {

        var rawLayout = MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(3, ADDRESS),
                JAVA_INT
        );

        var layout = MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(
                        3,
                        ADDRESS.withTargetLayout(rawLayout)
                ).withName("children"),
                JAVA_INT.withName("value")
        );

        VarHandle child = layout.varHandle(PathElement.groupElement("children"), PathElement.sequenceElement());
        VarHandle value = layout.varHandle(PathElement.groupElement("value"));

        MemorySegment root;
        MemorySegment firstChild;
        MemorySegment secondChild;
        try (var arena = Arena.ofConfined()) {
            root = arena.allocate(layout);
            value.set(root, 0L, 100);
            firstChild = arena.allocate(layout);
            value.set(firstChild, 0L, 41);
            secondChild = arena.allocate(layout);
            value.set(secondChild, 0L, 42);
            child.set(root, 0L, 0, firstChild);
            child.set(root, 0L, 1, secondChild);

            var mapper = SegmentMapper.ofRecord(TreeNode.class, layout);

            TreeNode actualRoot = mapper.get(root);
            assertEquals(100, actualRoot.value());

            TreeNode actualFirstChild = mapper.get(actualRoot.children()[0]);
            TreeNode actualSecondChild = mapper.get(actualRoot.children()[1]);

            assertEquals(firstChild, actualRoot.children()[0]);
            assertEquals(secondChild, actualRoot.children()[1]);
            assertEquals(MemorySegment.NULL, actualRoot.children()[2]);

            assertEquals(41, actualFirstChild.value());
            for (int i = 0; i < 3; i++) {
                assertEquals(MemorySegment.NULL, actualFirstChild.children()[i]);
            }
            assertEquals(42, actualSecondChild.value());
            for (int i = 0; i < 3; i++) {
                assertEquals(MemorySegment.NULL, actualSecondChild.children()[i]);
            }
        }

    }

    @Test
    public void paddingLayout() {
        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("x"),
                MemoryLayout.paddingLayout(Integer.SIZE).withName("y")
        );

        assertThrows(IllegalArgumentException.class, () ->
                SegmentMapper.ofRecord(Point.class, layout)
        );

    }

    public record SingleValue(int x) {}

    @Test
    public void nonDistinctUnusedNames() {
        // Tests that a name must not be unique in the MemoryLayout if it is unused
        // by any record component
        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("z"), // Not used
                JAVA_INT.withName("z"), // Not used
                JAVA_INT.withName("x"), // Used
                JAVA_INT.withName("y")  // Used
        );

        var mapper = SegmentMapper.ofRecord(Point.class, layout);

        Point point = mapper.get(POINT_SEGMENT);
        assertEquals(new Point(6, 0), point);
    }

    @Test
    public void nonDistinctUsedNames() {
        // Tests that a name must be unique in the MemoryLayout if it is used
        // by a record component
        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("x"), // Used
                JAVA_INT.withName("x")  // Used
        );

        assertThrows(IllegalArgumentException.class, () ->
                SegmentMapper.ofRecord(SingleValue.class, layout)
        );
    }

    public record Recurse(Recurse recurse){}
    @Test
    public void recursiveDefinition() {
        var layout = MemoryLayout.structLayout(
                MemoryLayout.structLayout(
                        MemoryLayout.structLayout(
                                MemoryLayout.structLayout(JAVA_INT)
                        ).withName("recurse")
                ).withName("recurse")
        );

        try {
            SegmentMapper.ofRecord(Recurse.class, layout);
            fail("No IllegalArgumentException detected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("same type"));
        }
    }

    private record PrivatePoint(int x, int y){}

    @Test
    public void privateClass() {
        var mapper = SegmentMapper.ofRecord(MethodHandles.lookup(), PrivatePoint.class, POINT_LAYOUT);
        PrivatePoint point = mapper.get(POINT_SEGMENT);
        assertEquals(new PrivatePoint(3, 4), point);
    }

    static public <R extends Record> void testPointType(R expected,
                                                        Object array,
                                                        ValueLayout valueLayout) {
        testType(expected, array, valueLayout, "x", "y");
    }

    @SuppressWarnings("unchecked")
    static public <R extends Record> void testType(R expected,
                                                   Object array,
                                                   ValueLayout valueLayout,
                                                   String... names) {

        MemorySegment segment = switch (array) {
            case byte[] a -> MemorySegment.ofArray(a);
            case short[] a -> MemorySegment.ofArray(a);
            case char[] a -> MemorySegment.ofArray(a);
            case int[] a -> MemorySegment.ofArray(a);
            case long[] a -> MemorySegment.ofArray(a);
            case float[] a -> MemorySegment.ofArray(a);
            case double[] a -> MemorySegment.ofArray(a);
            default -> throw new IllegalArgumentException("Unknown array type: " + array);
        };

        StructLayout layout = MemoryLayout.structLayout(Arrays.stream(names)
                .map(valueLayout::withName)
                .toArray(MemoryLayout[]::new));

        Class<R> type = (Class<R>) expected.getClass();
        SegmentMapper<R> mapper = SegmentMapper.ofRecord(type, layout);
        R actual = mapper.get(segment);
        assertEquals(expected, actual);
    }


    public <T> void test(MemorySegment segment,
                         SegmentMapper<T> mapper,
                         T expected) {

        T actual = mapper.get(segment);
        assertEquals(expected, actual);
    }

}
