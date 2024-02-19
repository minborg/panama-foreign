/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestMappedLayout
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MappedLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Todo: Check alignment against the layout

final class TestMappedLayout {

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();

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

    // Here is some memory containing three points as specified in the C `struct point` above
    private static final MemorySegment POINT_SEGMENT = MemorySegment.ofArray(new int[]{
                    3, 4,   // Point[x=3, y=4]  ---+--- Line[begin=Point[x=3, y=4], end=Point[x=6, y=0]]
                    6, 0,   // Point[x=6, y=0]  ---|
                    9, 4})  // Point[x=9, y=4]
            .asReadOnly();

    // Here is how we can model the layout of the C structs using a StructLayout
    private static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),  // An `int` named "x", with `ByteOrder.NATIVE_ORDER` aligned at 4 bytes
            JAVA_INT.withName("y")); // Ditto but named "y"


    // We can work with "raw" memory without any abstraction using the FFM API
    @Test
    void imperativeManipulation() {
        MemorySegment segment = newCopyOf(POINT_SEGMENT);
        int index = 1;
        // Access the point at index 1 (Point[x=6, y=0])
        int x = segment.get(JAVA_INT, POINT_LAYOUT.byteSize() * index + JAVA_INT.byteSize() * 0); // 6
        int y = segment.get(JAVA_INT, POINT_LAYOUT.byteSize() * index + JAVA_INT.byteSize() * 1); // 0
        assertEquals(6, x);
        assertEquals(0, y);

        // Update the point at index 1
        segment.set(JAVA_INT, POINT_LAYOUT.byteSize() * index + JAVA_INT.byteSize() * 0, -1); // x=-1
        segment.set(JAVA_INT, POINT_LAYOUT.byteSize() * index + JAVA_INT.byteSize() * 1, -2); // y=-2
        MapperTestUtil.assertContentEquals(new int[]{3, 4, -1, -2, 9, 4}, segment);
    }

    // A slight improvement can be done using `VarHandle` access.
    // VarHandles are "type-less" and can have any coordinates and return any type.
    // (VarHandles also allows us to work with various memory semantics such as volatile access and CAS operations).
    private static final VarHandle X_HANDLE =
            POINT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("x"));

    private static final VarHandle Y_HANDLE =
            POINT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("y"));

    @Test
    void varHandles() {
        MemorySegment segment = newCopyOf(POINT_SEGMENT);
        int index = 1;
        // Access the point at index 1 (Point[x=6, y=0])
        // The VarHandles have a built-in offset for the selected member in a group layout
        int x = (int) X_HANDLE.get(segment, POINT_LAYOUT.byteSize() * index); // 6
        int y = (int) Y_HANDLE.get(segment, POINT_LAYOUT.byteSize() * index); // 0
        assertEquals(6, x);
        assertEquals(0, y);

        // Update the point at index 1
        X_HANDLE.set(segment, POINT_LAYOUT.byteSize() * index, -1);
        Y_HANDLE.set(segment, POINT_LAYOUT.byteSize() * index, -2);
        MapperTestUtil.assertContentEquals(new int[]{3, 4, -1, -2, 9, 4}, segment);
    }

   // We can improve the situation by manually coding a class that abstracts away access:
    public static final class MyPoint {

        private static final VarHandle X_HANDLE =
                POINT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("x"));

       private static final VarHandle Y_HANDLE =
               POINT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("y"));

       // Holds the memory segment we are projecting members to/from
       private final MemorySegment segment;

       public MyPoint(MemorySegment segment) {
           this.segment = Objects.requireNonNull(segment);
       }

       public int x() {
           // Note the 0L as coordinates must match exactly in type
           return (int) X_HANDLE.get(segment, 0L);
       }

       public int y() {
           return (int) Y_HANDLE.get(segment, 0L);
       }

       public void x(int x) {
           X_HANDLE.set(segment, 0L, x);
       }

       public void y(int y) {
           Y_HANDLE.set(segment, 0L, y);
       }

       @Override
       public boolean equals(Object obj) {
           return  (obj instanceof MyPoint that) &&
                   this.x() == that.x() &&
                   this.y() == that.y();
       }

       @Override
       public int hashCode() {
           int result = x();
           result = 31 * result + y();
           return result;
       }

       @Override
       public String toString() {
           return "MyPoint[" +
                   "x=" + x() +
                   ", y=" + y() +
                   ']';
       }
   }

   // Here is what a more abstract access looks like using the custom class.
   @Test
   void customClass() {
       MemorySegment segment = newCopyOf(POINT_SEGMENT);
       int index = 1;
       // This carves out a memory slice for the point at index 1
       MemorySegment slice = segment.asSlice(POINT_LAYOUT.byteSize() * index, POINT_LAYOUT);
       // Connected to a segment. Not stand-alone! A view...
       MyPoint point = new MyPoint(slice);

       assertEquals(6, point.x());
       assertEquals(0, point.y());
       assertEquals("MyPoint[x=6, y=0]", point.toString());


       point.x(-1);
       point.y(-2);
       assertEquals(-1, point.x());
       assertEquals(-2, point.y());
       // Not only has the value changed but also the backing segment
       MapperTestUtil.assertContentEquals(new int[]{3, 4, -1, -2, 9, 4}, segment);
   }

   // While custom wrappers are nice, they quickly become hard-to-read, prone to errors
   // and expensive to maintain. VarHandles have no type-safety for example.
   //
   // Imagine writing wrappers for this layout... A line that consists of points ...
    private static final GroupLayout LINE_LAYOUT = MemoryLayout.structLayout(
            POINT_LAYOUT.withName("begin"),
            POINT_LAYOUT.withName("end"));

    // So what can be done?

    // Would it not be nice if we can connect this record to native memory using the POINT_LAYOUT?
    public record Point(int x, int y) {
    }

    // Even nicer if records can nested like this
    private record Line(Point begin, Point end) {
    }


    // Enter the SegmentMapper!
    //
    // See doc-files/point.png

    @Test
    void point() {
        MemorySegment segment = newCopyOf(POINT_SEGMENT);
        // Automatically creates a mapped layout that can be used to extract/write records to native memory.
        MappedLayout<Point> layout = MemoryLayout.mappedLayout(Point.class, POINT_LAYOUT);

        // Gets the point at index 0
        // The record Point is not backed by a segment. It is not a view!
        Point point = segment.get(layout, 0);
        assertEquals(3, point.x());
        assertEquals(4, point.y());

        // Gets the point at index 1
        Point point2 = segment.getAtIndex(layout, 1);
        assertEquals(6, point2.x());
        assertEquals(0, point2.y());

        // Note that the operations on the SegmentMapper corresponds to those of the MemorySegments
        // SegmentMapper::get (composites) <-> MemorySegment::get (primitives)
        // The same is true for getAtIndex(), set(), setAtIndex(), elements()/stream(), etc.

        // Stream all the points in the backing segment
        List<Point> points = segment.elements(layout)
                .toList();

        assertEquals(List.of(new Point(3, 4), new Point(6, 0), new Point(9, 4)), points);

        assertEquals(Point.class, layout.carrier());
        assertEquals(POINT_LAYOUT, layout.targetLayout());

        segment.setAtIndex(layout, 1L, new Point(-1, -2));
        MapperTestUtil.assertContentEquals(new int[]{3, 4, -1, -2, 9, 4}, segment);
    }

    @Test
    void pointVar2() {
        MemorySegment segment = newCopyOf(POINT_SEGMENT);
        // Automatically creates a mapped layout that can be used to extract/write records to native memory.
        MappedLayout<Point> layout = POINT_LAYOUT.withCarrier(Point.class);

        // Gets the point at index 0
        // The record Point is not backed by a segment. It is not a view!
        Point point = segment.get(layout, 0);
        assertEquals(3, point.x());
        assertEquals(4, point.y());

        // Gets the point at index 1
        Point point2 = segment.getAtIndex(layout, 1);
        assertEquals(6, point2.x());
        assertEquals(0, point2.y());

        // Note that the operations on the SegmentMapper corresponds to those of the MemorySegments
        // SegmentMapper::get (composites) <-> MemorySegment::get (primitives)
        // The same is true for getAtIndex(), set(), setAtIndex(), elements()/stream(), etc.

        // Stream all the points in the backing segment
        List<Point> points = segment.elements(layout)
                .toList();

        assertEquals(List.of(new Point(3, 4), new Point(6, 0), new Point(9, 4)), points);

        assertEquals(Point.class, layout.carrier());
        assertEquals(POINT_LAYOUT, layout.targetLayout());

        segment.setAtIndex(layout, 1L, new Point(-1, -2));
        MapperTestUtil.assertContentEquals(new int[]{3, 4, -1, -2, 9, 4}, segment);
    }

    @Test
    void pointVarHandle() {
        MemorySegment segment = newCopyOf(POINT_SEGMENT);
        // Automatically creates a mapped layout that can be used to extract/write records to native memory.
        MappedLayout<Point> layout = POINT_LAYOUT.withCarrier(Point.class);
        VarHandle handle = layout.varHandle();

        Objects.requireNonNull(handle);

        // Gets the point at index 0
        // The record Point is not backed by a segment. It is not a view!
        Point point = (Point) handle.get(segment, 0);
        assertEquals(3, point.x());
        assertEquals(4, point.y());

        handle.set(segment, POINT_LAYOUT.byteSize(), new Point(-1, -2));
        MapperTestUtil.assertContentEquals(new int[]{3, 4, -1, -2, 9, 4}, segment);
    }


    // The mapper must exactly match the types! Imagine if not ... FFM is a low level library
    // However, it is very easy to map mappers.
    // Here is a record that is using "narrowed" components
    public record TinyPoint(byte x, byte y) {
    }

    // Pattern matching...

    // Lossless narrowing
    private static TinyPoint toTiny(Point point) {
        return new TinyPoint(toByteExact(point.x()), toByteExact(point.y()));
    }

    private static Point fromTiny(TinyPoint point) {
        return new Point(point.x(), point.y());
    }

    private static byte toByteExact(int value) {
        if ((byte) value != value) {
            throw new ArithmeticException("byte overflow");
        }
        return (byte) value;
    }

    @Test
    void mappedTinyPoint() {
        MemorySegment segment = newCopyOf(POINT_SEGMENT);
        MappedLayout<Point> layout = MemoryLayout.mappedLayout(Point.class, POINT_LAYOUT);
        MappedLayout<TinyPoint> tinyAccessor = layout
                .map(TinyPoint.class, TestMappedLayout::toTiny, TestMappedLayout::fromTiny);

        assertEquals(TinyPoint.class, tinyAccessor.carrier());
        assertEquals(POINT_LAYOUT, layout.targetLayout());

        TinyPoint tp = segment.get(tinyAccessor, 0L);
        assertEquals(new TinyPoint((byte) 3, (byte) 4), tp);
        tp = segment.getAtIndex(tinyAccessor, 1);
        assertEquals(new TinyPoint((byte) 6, (byte) 0), tp);

        segment.setAtIndex(tinyAccessor, 1, new TinyPoint((byte) -1, (byte) -2));
        MapperTestUtil.assertContentEquals(new int[]{3, 4, -1, -2, 9, 4}, segment);
    }

    // Records of arbitrary nesting depth are supported
    // See doc-files/line.png

    @Test
    void line() {
        MemorySegment segment = newCopyOf(POINT_SEGMENT);
        // Also stand-alone
        MappedLayout<Line> layout = MemoryLayout.mappedLayout(LOCAL_LOOKUP, Line.class, LINE_LAYOUT);

        Line point = segment.get(layout, 0);
        assertEquals(new Line(new Point(3, 4), new Point(6, 0)), point);

        segment.set(layout, POINT_LAYOUT.byteSize(), new Line(
                new Point(-3, -4),
                new Point(-6, 0)
        ));
        MapperTestUtil.assertContentEquals(new int[]{3, 4, -3, -4, -6, 0}, segment);
    }

    // Arrays are supported where the length of the arrays is taken from the
    // corresponding sequence layout. (There is no concept of a type `int[3]` in Java)
    // The SequenceBox can accommodate several memory layouts with different array lengths
    record SequenceBox(int before, int[] ints, int after) {

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SequenceBox(var otherBefore, var otherInts, var otherAfter) &&
                    before == otherBefore && Arrays.equals(ints, otherInts) && after == otherAfter;
        }

        @Override
        public int hashCode() {
            int result = before;
            result = 31 * result + Arrays.hashCode(ints);
            result = 31 * result + after;
            return result;
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
        // int[]{0, 1, 2, 3}
        var segment = MemorySegment.ofArray(IntStream.rangeClosed(0, 3).toArray());

        var targetLayout = MemoryLayout.structLayout(
                JAVA_INT.withName("before"),
                MemoryLayout.sequenceLayout(2, JAVA_INT).withName("ints"),
                JAVA_INT.withName("after")
        );

        var layout = MemoryLayout.mappedLayout(LOCAL_LOOKUP, SequenceBox.class, targetLayout);

        SequenceBox sequenceBox = segment.get(layout, 0L);

        assertEquals(new SequenceBox(0, new int[]{1, 2}, 3), sequenceBox);

        var dstSegment = newCopyOf(segment);
        dstSegment.set(layout, 0L, new SequenceBox(10, new int[]{11, 12}, 13));

        MapperTestUtil.assertContentEquals(IntStream.rangeClosed(10, 13).toArray(), dstSegment);

        assertThrows(NullPointerException.class, () -> {
            // The array is null
            dstSegment.set(layout, 0, new SequenceBox(10, null, 13));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            // The array is not of correct size
            dstSegment.set(layout, 0L, new SequenceBox(10, new int[]{11}, 13));
        });
        assertThrows(IllegalArgumentException.class, () -> {
            // The array is not of correct size
            dstSegment.set(layout, 0L, new SequenceBox(10, new int[]{11, 12, 13}, 13));
        });
    }

    // Arrays of arbitrary rank are supported

    record SequenceBox2D(int before, int[][] ints, int after) {

        @Override
        public boolean equals(Object obj) {
            return obj instanceof SequenceBox2D(var otherBefore, var otherInts, var otherAfter) &&
                    before == otherBefore &&
                    Arrays.deepEquals(ints, otherInts) &&
                    after == otherAfter;
        }

        @Override
        public int hashCode() {
            int result = before;
            result = 31 * result + Arrays.deepHashCode(ints);
            result = 31 * result + after;
            return result;
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

        var targetLayout = MemoryLayout.structLayout(
                JAVA_INT.withName("before"),
                MemoryLayout.sequenceLayout(2,
                        MemoryLayout.sequenceLayout(3, JAVA_INT)
                ).withName("ints"),
                JAVA_INT.withName("after")
        );

        var layout = MemoryLayout.mappedLayout(LOCAL_LOOKUP, SequenceBox2D.class, targetLayout);


        SequenceBox2D sequenceBox = segment.get(layout, 0L);

        assertEquals(new SequenceBox2D(0, new int[][]{{1, 2, 3}, {4, 5, 6}}, 7), sequenceBox);

        var dstSegment = newCopyOf(segment);
        dstSegment.set(layout, 0L, new SequenceBox2D(10, new int[][]{{11, 12, 13}, {14, 15, 16}}, 17));

        MapperTestUtil.assertContentEquals(IntStream.range(0, 1 + 2 * 3 + 1).map(i -> i + 10).toArray(), dstSegment);

        assertThrows(NullPointerException.class, () -> {
            // The array is null
            dstSegment.set(layout, 0L, new SequenceBox2D(10, null, 13));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            // The array is not of correct size
            dstSegment.set(layout, 0L, new SequenceBox2D(10, new int[][]{{11, 12, 13}}, 13));
        });
        assertThrows(IllegalArgumentException.class, () -> {
            // The array is not of correct size
            dstSegment.set(layout, 0L, new SequenceBox2D(10, new int[][]{{11, 12, 13}, {14, 15}}, 13));
        });
    }

    // Arrays can have record components

    record Polygon(Point[] points) {

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Polygon(var otherPoints) &&
                    Arrays.equals(points, otherPoints);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(points);
        }

        @Override
        public String toString() {
            return Arrays.toString(points);
        }
    }

    @Test
    void triangle() {

        //        y
        //
        //        |
        //        | /\
        //        | ‾‾
        // -------+------- x
        //        |
        //        |
        //        |

        var segment = MemorySegment.ofArray(new int[]{1, 1, 2, 2, 3, 1});

        GroupLayout targetLayout = MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(3, POINT_LAYOUT).withName("points")
        );

        var layout = MemoryLayout.mappedLayout(LOCAL_LOOKUP, Polygon.class, targetLayout);

        Polygon triangle = segment.get(layout, 0L);

        assertEquals(new Polygon(new Point[]{new Point(1,1), new Point(2,2), new Point(3, 1)}), triangle);

        var dstSegment = newCopyOf(segment);
        dstSegment.set(layout, 0L, new Polygon(new Point[]{new Point(11,11), new Point(12,12), new Point(13, 11)}));

        MapperTestUtil.assertContentEquals(new int[]{11, 11, 12, 12, 13, 11}, dstSegment);

        assertThrows(NullPointerException.class, () -> {
            // The array is null
            dstSegment.set(layout, 0L, new Polygon(null));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            // The array is not of correct size
            dstSegment.set(layout, 0L, new Polygon(new Point[]{new Point(1, 1), new Point(2, 2)}));
        });
        assertThrows(IllegalArgumentException.class, () -> {
            // The array is not of correct size
            Point[] points = IntStream.range(0, 4).mapToObj(_ -> new Point(1, 1)).toArray(Point[]::new);
            dstSegment.set(layout, 0L,  new Polygon(points));
        });

    }

    // Interoperability with POJOs

    private static class PointBean {

        private int x;
        private int y;

        int x() {
            return x;
        }
        void x(int x) {
            this.x = x;
        }

        int y() {
            return y;
        }
        void y(int y) {
            this.y = y;
        }

        @Override
        public String toString() {
            return "PointBean[" +
                    "x=" + x() +
                    ", y=" + y() +
                    ']';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PointBean pointBean = (PointBean) o;

            if (x != pointBean.x) return false;
            return y == pointBean.y;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            return result;
        }
    }

    static Point beanToPoint(PointBean bean) {
        return new Point(bean.x(), bean.y());
    }

    static PointBean pointToBean(Point point) {
        PointBean pointBean = new PointBean();
        pointBean.x(point.x());
        pointBean.y(point.y());
        return pointBean;
    }

    @Test
    void bean() {
        var layout = MemoryLayout.mappedLayout(LOCAL_LOOKUP, Point.class, POINT_LAYOUT);

        MappedLayout<PointBean> beanLayout = layout.map(PointBean.class, TestMappedLayout::pointToBean, TestMappedLayout::beanToPoint);

        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4});
        PointBean pointBean = segment.get(beanLayout, 0L);
        assertEquals("PointBean[x=3, y=4]", pointBean.toString());
    }

    // SegmentMapper::create is more geared towards interfaces but works for records too

    record GenericPoint<T>(int x, int y) {}

    // Generic interfaces and records need to have their generic type parameters (if any)
    // know at compile time. This applies to all extended interfaces recursively.

    @Test
    void genericRecord() {
        assertThrows(IllegalArgumentException.class, () -> {
            MemoryLayout.mappedLayout(LOCAL_LOOKUP, GenericPoint.class, POINT_LAYOUT);
        });
    }


    @Test
    void originDistances() {
        MappedLayout<Point> layout = MemoryLayout.mappedLayout(LOCAL_LOOKUP, Point.class, POINT_LAYOUT);

        double averageDistance = POINT_SEGMENT.elements(layout)
                .mapToDouble(this::originDistance)
                .average()
                .orElse(0);

        System.out.println("averageDistance = " + averageDistance);
    }

    double originDistance(Point point) {
        return Math.sqrt(point.x() * point.x() + point.y() * point.y());
    }


     // Interfaces and records must not implement (directly and/or via inheritance) more than
     // one abstract method with the same name and erased parameter types. Hence, covariant
     // overriding is not supported.

    // Todo: Add test for this



    // Future considerations and work ...

    // Consider allow mapping Lists to components
    // Unfortunately, this may be ineffective for primitive arrays
    record PolygonList(List<Point> points) {}

    // Consider allowing "escape hatches" in the form of MemorySegments
    // This will map a slice of an underlying MemorySegment.
    // Unfortunately, this means the record is still "attached" to a segment or portions thereof
    // and with the same life cycle as the original segment
    record PartialPoint(int x, MemorySegment y){}

    // Consider some method that can render a description of the mapper and
    // what it is doing. E.g. like .DOT formatted strings that can be used to
    // generate images like the point.png and line.png images:
    // Stream<String> description();

    // The same is true for interfaces
    interface PolygonAccessor {
        List<Point> points(); // This should be a lazy list
    }

    @Test
    void listOfGeneric() throws NoSuchMethodException {
        Method m = PolygonAccessor.class.getDeclaredMethod("points");
        Type gt = m.getGenericReturnType(); // java.util.List<TestRecordMapper$Point>
    }

    // Misc tests

    record BunchOfPoints(Point p0, Point p1, Point p2, Point p3,
                         Point p4, Point p5, Point p6, Point p7) {
    }

    // This test is to make sure the iterative setter works (as opposed to the composed one).
    @Test
    void bunch() {
        StructLayout targetLayout = MemoryLayout.structLayout(IntStream.range(0, 8)
                .mapToObj(i -> POINT_LAYOUT.withName("p" + i))
                .toArray(MemoryLayout[]::new));

        int noInts = (int) (targetLayout.byteSize() / JAVA_INT.byteSize());

        MemorySegment segment = MemorySegment.ofArray(IntStream.range(0, noInts).toArray());

        MappedLayout<BunchOfPoints> accessor = MemoryLayout.mappedLayout(LOCAL_LOOKUP, BunchOfPoints.class, targetLayout);

        BunchOfPoints bunchOfPoints = segment.get(accessor, 0L);

        BunchOfPoints expected = new BunchOfPoints(
                new Point(0 ,1), new Point(2, 3), new Point(4, 5), new Point(6, 7),
                new Point(8 ,9), new Point(10, 11), new Point(12, 13), new Point(14, 15)
        ) ;
        assertEquals(expected, bunchOfPoints);

        MemorySegment dstSegment = Arena.ofAuto().allocate(targetLayout);

        dstSegment.set(accessor, 0, new BunchOfPoints(
                new Point(10 ,11), new Point(12, 13), new Point(14, 15), new Point(16, 17),
                new Point(18 ,19), new Point(20, 21), new Point(22, 23), new Point(24, 25)
        ));

        MapperTestUtil.assertContentEquals(IntStream.range(0, noInts).map(i -> i + 10).toArray(), dstSegment);
    }

    @Test
    void invariantChecking() {
        MappedLayout<Point> layout = MemoryLayout.mappedLayout(LOCAL_LOOKUP, Point.class, POINT_LAYOUT);

        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});
        assertThrows(NullPointerException.class, () -> segment.get((MappedLayout<?>) null, 0L));
        assertThrows(IndexOutOfBoundsException.class, () -> segment.get(layout, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> segment.get(layout, segment.byteSize()));
        MemorySegment smallSegment = MemorySegment.ofArray(new int[]{3});
        assertThrows(IndexOutOfBoundsException.class, () -> smallSegment.get(layout, 0));
    }

    // Support methods

    private static MemorySegment newCopyOf(MemorySegment source) {
        return Arena.ofAuto()
                .allocate(source.byteSize())
                .copyFrom(source);
    }

}
