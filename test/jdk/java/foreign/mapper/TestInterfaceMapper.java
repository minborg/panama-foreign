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
 * @enablePreview
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.impl
 *          java.base/jdk.internal
 * @run junit/othervm -Djava.lang.foreign.mapper.debug=true TestInterfaceMapper
 */

import jdk.internal.ValueBased;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

// Todo: check wrapper classes
// Todo: Check the SegmentMapper::map operation
// Todo: Check unions
// Todo: Prevent recursive definitions (and check for this explicitly)

final class TestInterfaceMapper {

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();

    private static final double EPSILON = 1e-6;

    private static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );


    interface TestGenerics {
        List<List<Integer>> listOfListOfIntegers();
    }

    @Test
    void generics() {
        Method method = TestGenerics.class.getMethods()[0];
        Type type =  method.getGenericReturnType();
        if (type instanceof ParameterizedType pt) {
            ListType listType = listType(pt);
            System.out.println("listType = " + listType);

            System.out.println("pt.getRawType() = " + pt.getRawType());
            Type[] types = pt.getActualTypeArguments();
            System.out.println("Arrays.toString(types) = " + Arrays.toString(types));
        }
        System.out.println("type.getTypeName() = " + type.getTypeName());
        System.out.println("type.getClass().getName() = " + type.getClass().getName());
        //fail(type.toString());
    }

    static ListType listType(ParameterizedType pt) {
        return listType(new ListType(0, pt.getRawType(), pt));
    }

    static ListType listType(ListType type) {
        if (type.elementType instanceof ParameterizedType pt) {
            return listType(new ListType(type.depth+1, type.rawType(), pt.getActualTypeArguments()[0]));
        }
        return type;
    }

    record ListType(int depth, Type rawType, Type elementType){}

    @Test
    void point() {
        SegmentMapper<BaseTest.PointAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, BaseTest.PointAccessor.class, POINT_LAYOUT);
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});
        BaseTest.PointAccessor accessor = mapper.get(segment, POINT_LAYOUT.byteSize());

        assertEquals(6, accessor.x());
        assertEquals(8, accessor.y());
        assertToString(accessor, mapper, "x()=6, y()=8");

        accessor.x(1);
        accessor.y(2);

        assertEquals(1, accessor.x());
        assertEquals(1, segment.getAtIndex(JAVA_INT, 2));
        assertEquals(2, accessor.y());
        assertEquals(2, segment.getAtIndex(JAVA_INT, 3));
        assertToString(accessor, mapper, "x()=1, y()=2");

        // SegmentMapper::set
        MemorySegment dstSegment = newSegment(POINT_LAYOUT);
        mapper.set(dstSegment, accessor);
        BaseTest.assertContentEquals(BaseTest.segmentOf(1, 2), dstSegment);
    }

    @Test
    void segmentBacked() {
        SegmentMapper<BaseTest.PointAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, BaseTest.PointAccessor.class, POINT_LAYOUT);
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});
        long offset = POINT_LAYOUT.byteSize();
        BaseTest.PointAccessor accessor = mapper.get(segment, offset);

        assertSame(mapper.segment(accessor).orElseThrow(), segment);
        assertEquals(mapper.offset(accessor).orElseThrow(), offset);
    }

    GroupLayout MIXED_LAYOUT = MemoryLayout.structLayout(
            JAVA_LONG.withName("l"),
            JAVA_DOUBLE.withName("d"),
            JAVA_INT.withName("i"),
            JAVA_FLOAT.withName("f"),
            JAVA_CHAR.withName("c"),
            JAVA_SHORT.withName("s"),
            JAVA_BYTE.withName("b")
    );

    interface MixedBag {
        byte b();

        short s();

        char c();

        int i();

        float f();

        long l();

        double d();

        void b(byte b);

        void s(short s);

        void c(char c);

        void i(int i);

        void f(float f);

        void l(long l);

        void d(double d);
    }

    @Test
    void mixedBag() {
        SegmentMapper<MixedBag> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, MixedBag.class, MIXED_LAYOUT);
        try (var arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(MIXED_LAYOUT);

            MIXED_LAYOUT.varHandle(PathElement.groupElement("l")).set(segment, 0, 42L);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("d")).set(segment, 0, 123.45d);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("i")).set(segment, 0, 13);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("f")).set(segment, 0, 3.1415f);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("c")).set(segment, 0, 'B');
            MIXED_LAYOUT.varHandle(PathElement.groupElement("s")).set(segment, 0, (short) 32767);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("b")).set(segment, 0, (byte) 127);

            MixedBag accessor = mapper.get(segment);

            assertEquals(42L, accessor.l());
            assertEquals(123.45d, accessor.d(), EPSILON);
            assertEquals(13, accessor.i());
            assertEquals(3.1415f, accessor.f(), EPSILON);
            assertEquals('B', accessor.c());
            assertEquals((short) 32767, accessor.s());
            assertEquals((byte) 127, accessor.b());

            assertToString(accessor, mapper, "l()=42, d()=123.45, i()=13, f()=3.1415, c()=B, s()=32767, b()=127");

            accessor.b((byte) (accessor.b() - 1));
            accessor.s((short) (accessor.s() - 1));
            accessor.c((char) (accessor.c() - 1));
            accessor.i(accessor.i() - 1);
            accessor.f(accessor.f() - 1);
            accessor.l(accessor.l() - 1);
            accessor.d(accessor.d() - 1);

            assertToString(accessor, mapper, "l()=41, d()=122.45, i()=12, f()=2.1415, c()=A, s()=32766, b()=126");

            // SegmentMapper::set
            MemorySegment dstSegment = newSegment(MIXED_LAYOUT);
            mapper.set(dstSegment, accessor);
            MemorySegment expected = newSegment(MIXED_LAYOUT);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("l")).set(expected, 0, 41L);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("d")).set(expected, 0, 122.45d);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("i")).set(expected, 0, 12);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("f")).set(expected, 0, 2.1415f);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("c")).set(expected, 0, 'A');
            MIXED_LAYOUT.varHandle(PathElement.groupElement("s")).set(expected, 0, (short) 32766);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("b")).set(expected, 0, (byte) 126);
            BaseTest.assertContentEquals(expected, dstSegment);
        }
    }

    private static final GroupLayout LINE_LAYOUT = MemoryLayout.structLayout(
            POINT_LAYOUT.withName("begin"),
            POINT_LAYOUT.withName("end")
    );

    interface XAccessor {
        int x();
    }

    interface YAccessor {
        int y();
    }

    interface XYAccessor extends XAccessor, YAccessor {
    }

    @Test
    void xyAccessor() {
        SegmentMapper<XYAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, XYAccessor.class, POINT_LAYOUT);
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});
        XYAccessor accessor = mapper.get(segment, 0);
        assertEquals(3, accessor.x());
        assertEquals(4, accessor.y());
        assertToString(accessor, mapper, "x()=3, y()=4");
    }

    @Test
    void yAccessor() {
        SegmentMapper<YAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, YAccessor.class, POINT_LAYOUT);
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});
        YAccessor accessor = mapper.get(segment, 0);
        assertEquals(4, accessor.y());
        assertToString(accessor, mapper, "y()=4");

        // SegmentMapper::set
        MemorySegment dstSegment = newSegment(POINT_LAYOUT);
        mapper.set(dstSegment, accessor);
        // Should only affect regions mapped by a setter
        BaseTest.assertContentEquals(BaseTest.segmentOf(0, 0), dstSegment);
    }

    interface LineAccessor {
        BaseTest.PointAccessor begin();

        BaseTest.PointAccessor end();
    }

    @Test
    void line() {
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});

        SegmentMapper<LineAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, LineAccessor.class, LINE_LAYOUT);
        LineAccessor accessor = mapper.get(segment);

        var begin = accessor.begin();
        var end = accessor.end();

        assertEquals(3, begin.x());
        assertEquals(4, begin.y());
        assertEquals(6, end.x());
        assertEquals(8, end.y());

        assertToString(accessor, mapper, "begin()=PointAccessor[x()=3, y()=4], end()=PointAccessor[x()=6, y()=8]");

        begin.x(4);
        begin.y(5);
        end.x(7);
        end.y(9);

        assertEquals(4, begin.x());
        assertEquals(5, begin.y());
        assertEquals(7, end.x());
        assertEquals(9, end.y());

        // SegmentMapper::set
        MemorySegment dstSegment = newSegment(LINE_LAYOUT);
        mapper.set(dstSegment, accessor);
        BaseTest.assertContentEquals(BaseTest.segmentOf(4, 5, 7, 9), dstSegment);
    }

    interface LinesAccessor {
        LineAccessor first();
        LineAccessor second();
    }

    // This test ensures depth > 1 works
    @Test
    void lines() {
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8, 4, 5, 7, 9});

        GroupLayout linesLayout = MemoryLayout.structLayout(
                LINE_LAYOUT.withName("first"),
                LINE_LAYOUT.withName("second")
        );
        SegmentMapper<LinesAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, LinesAccessor.class, linesLayout);

        LinesAccessor accessor = mapper.get(segment);

        LineAccessor first = accessor.first();
        BaseTest.PointAccessor firstBegin = first.begin();
        BaseTest.PointAccessor firstEnd = first.end();
        LineAccessor second = accessor.second();
        BaseTest.PointAccessor secondBegin = second.begin();
        BaseTest.PointAccessor secondEnd = second.end();

        assertEquals(3, firstBegin.x());
        assertEquals(4, firstBegin.y());
        assertEquals(6, firstEnd.x());
        assertEquals(8, firstEnd.y());

        assertEquals(4, secondBegin.x());
        assertEquals(5, secondBegin.y());
        assertEquals(7, secondEnd.x());
        assertEquals(9, secondEnd.y());

        firstBegin.x(13);
        firstBegin.y(14);
        firstEnd.x(16);
        firstEnd.y(18);

        secondBegin.x(14);
        secondBegin.y(15);
        secondEnd.x(17);
        secondEnd.y(19);

        // SegmentMapper::set
        MemorySegment dstSegment = newSegment(linesLayout);
        mapper.set(dstSegment, accessor);
        BaseTest.assertContentEquals(
                BaseTest.segmentOf(13, 14, 16, 18,  14, 15, 17, 19),
                dstSegment);
    }

    interface Empty {
    }

    @Test
    void empty() {
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});

        SegmentMapper<Empty> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, Empty.class, LINE_LAYOUT);
        Empty accessor = mapper.get(segment);

        assertToString(accessor, mapper, "");

        // SegmentMapper::set
        MemorySegment dstSegment = MemorySegment.ofArray(new int[]{1});
        mapper.set(dstSegment, accessor);
        BaseTest.assertContentEquals(
                BaseTest.segmentOf(1),
                dstSegment);
    }

    interface Fail1 {
        // Setters of accessor not allowed
        void begin(BaseTest.PointAccessor pointAccessor);
    }

    // It would be dangerous to accept a class like this in Fail1::begin
    final static class MyPointAccessor implements BaseTest.PointAccessor {
        @Override public int x() { return 0; }
        @Override public int y() { return 0; }
        @Override public void x(int x) { }
        @Override public void y(int y) { }
    }
    // It would also be dangerous to provide an interface mapped to *another* segment or offset.

    @Test
    void fail1() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                SegmentMapper.ofInterface(LOCAL_LOOKUP, Fail1.class, LINE_LAYOUT)
        );
        var message = e.getMessage();

        assertTrue(message.contains("not supported"), message);
        assertTrue(message.contains(Fail1.class.getMethods()[0].toString()), message);
    }

    interface Fail2 {
        // Only one parameter is allowed
        void x(int i, Object o);
    }

    @Test
    void fail2() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                SegmentMapper.ofInterface(LOCAL_LOOKUP, Fail2.class, POINT_LAYOUT)
        );
        var message = e.getMessage();
        assertTrue(message.contains("Object"), message);
        assertTrue(message.contains(Fail2.class.getMethods()[0].toString()), message);
    }

    public record Point(int x, int y) {
    }

    public interface LineRecordAccessor {
        Point begin();

        Point end();

        void begin(Point begin);

        void end(Point end);
    }

    @Test
    void mapToRecord() {
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});

        SegmentMapper<LineRecordAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, LineRecordAccessor.class, LINE_LAYOUT);
        LineRecordAccessor accessor = mapper.get(segment);

        Point begin = accessor.begin();
        Point end = accessor.end();

        assertEquals(3, begin.x());
        assertEquals(4, begin.y());
        assertEquals(6, end.x());
        assertEquals(8, end.y());

        assertToString(accessor, mapper, "begin()=Point[x=3, y=4], end()=Point[x=6, y=8]");

        accessor.begin(new Point(1, 2));
        accessor.end(new Point(3, 4));

        assertEquals(1, accessor.begin().x());
        assertEquals(2, accessor.begin().y());
        assertEquals(3, accessor.end().x());
        assertEquals(4, accessor.end().y());

        assertToString(accessor, mapper, "begin()=Point[x=1, y=2], end()=Point[x=3, y=4]");
    }

    public interface PolygonAccessor {
        BaseTest.PointAccessor points(long index);
    }

    @Test
    void triangleInterface() {
        GroupLayout triangleLayout = MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(3, POINT_LAYOUT).withName("points")
        );
        MemorySegment segment = MemorySegment.ofArray(new int[]{1, 10, 2, 11, 3, 9});
        SegmentMapper<PolygonAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, PolygonAccessor.class, triangleLayout);
        PolygonAccessor accessor = mapper.get(segment, 0);

        BaseTest.PointAccessor p0 = accessor.points(0);
        BaseTest.PointAccessor p1 = accessor.points(1);
        BaseTest.PointAccessor p2 = accessor.points(2);

        assertEquals(1, p0.x());
        assertEquals(10, p0.y());
        assertEquals(2, p1.x());
        assertEquals(11, p1.y());
        assertEquals(3, p2.x());
        assertEquals(9, p2.y());

        assertEquals("PolygonAccessor[points()=PointAccessor[3]]", accessor.toString());
    }

    public interface PolygonAccessor2Dim {
        BaseTest.PointAccessor points(long i, long j);
    }

    @Test
    void multiDimInterface() {
        GroupLayout triangleLayout = MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(4,
                        MemoryLayout.sequenceLayout(3, POINT_LAYOUT)
                ).withName("points")
        );
        MemorySegment segment = MemorySegment.ofArray(new int[]{
                1, 10,  2, 11,  3, 9,
                2, 11,  3, 12,  4, 10,
                3, 12,  4, 13,  5, 11,
                4, 14,  5, 14,  6, 12
        });
        SegmentMapper<PolygonAccessor2Dim> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, PolygonAccessor2Dim.class, triangleLayout);
        PolygonAccessor2Dim accessor = mapper.get(segment, 0);

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                BaseTest.PointAccessor p = accessor.points(i, j);
                int index = 2 * 3 * i + 2 * j;
                int expectedX = segment.getAtIndex(JAVA_INT, index);
                int expectedY = segment.getAtIndex(JAVA_INT, index + 1);
                assertEquals(expectedX, p.x());
                assertEquals(expectedY, p.y());
            }
        }

        assertEquals("PolygonAccessor2Dim[points()=PointAccessor[4, 3]]", accessor.toString());
    }

    public interface PolygonRecordAccessor {
        Point points(long index);
        void points(long index, Point point);
    }

    @Test
    void triangleRecord() {
        GroupLayout triangleLayout = MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(3, POINT_LAYOUT).withName("points")
        );
        MemorySegment segment = MemorySegment.ofArray(new int[]{1, 10, 2, 11, 3, 9});
        SegmentMapper<PolygonRecordAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, PolygonRecordAccessor.class, triangleLayout);
        PolygonRecordAccessor accessor = mapper.get(segment, 0);

        Point p0 = accessor.points(0);
        Point p1 = accessor.points(1);
        Point p2 = accessor.points(2);

        assertThrows(IndexOutOfBoundsException.class, () ->
            accessor.points(-1)
        );

        assertThrows(IndexOutOfBoundsException.class, () ->
            accessor.points(3)
        );

        assertEquals(1, p0.x());
        assertEquals(10, p0.y());
        assertEquals(2, p1.x());
        assertEquals(11, p1.y());
        assertEquals(3, p2.x());
        assertEquals(9, p2.y());

        assertEquals("PolygonRecordAccessor[points()=Point[3]]", accessor.toString());

        accessor.points(1, new Point(-1, -2));
        Point updatedP1 = accessor.points(1);
        assertEquals(-1, updatedP1.x());
        assertEquals(-2, updatedP1.y());
    }

    interface MixedBagArray {
        byte b(long i, long j);

        short s(long i, long j);

        char c(long i, long j);

        int i(long i, long j);

        float f(long i, long j);

        long l(long i, long j);

        double d(long i, long j);

        void b(long i, long j, byte b);

        void s(long i, long j, short s);

        void c(long i, long j, char c);

        void i(long i, long j, int value);

        void f(long i, long j, float f);

        void l(long i, long j, long l);

        void d(long i, long j, double d);
    }

    GroupLayout MIXED_LAYOUT_ARRAY = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(2,
                            MemoryLayout.sequenceLayout(3, JAVA_LONG))
                    .withName("l"),
            MemoryLayout.sequenceLayout(2,
                            MemoryLayout.sequenceLayout(3, JAVA_DOUBLE))
                    .withName("d"),
            MemoryLayout.sequenceLayout(2,
                            MemoryLayout.sequenceLayout(3, JAVA_INT))
                    .withName("i"),
            MemoryLayout.sequenceLayout(2,
                            MemoryLayout.sequenceLayout(3, JAVA_FLOAT))
                    .withName("f"),
            MemoryLayout.sequenceLayout(2,
                            MemoryLayout.sequenceLayout(3, JAVA_CHAR))
                    .withName("c"),
            MemoryLayout.sequenceLayout(2,
                            MemoryLayout.sequenceLayout(3, JAVA_SHORT))
                    .withName("s"),
            MemoryLayout.sequenceLayout(2,
                            MemoryLayout.sequenceLayout(3, JAVA_BYTE))
                    .withName("b")
    );

    @Test
    void mixedBagArray() {
        SegmentMapper<MixedBagArray> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, MixedBagArray.class, MIXED_LAYOUT_ARRAY);
        try (var arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(MIXED_LAYOUT_ARRAY);

            PathElement one = PathElement.sequenceElement(1);
            MIXED_LAYOUT_ARRAY.varHandle(PathElement.groupElement("l"), one, one).set(segment, 0L, 42L);
            MIXED_LAYOUT_ARRAY.varHandle(PathElement.groupElement("d"), one, one).set(segment, 0L, 123.45d);
            MIXED_LAYOUT_ARRAY.varHandle(PathElement.groupElement("i"), one, one).set(segment, 0L, 13);
            MIXED_LAYOUT_ARRAY.varHandle(PathElement.groupElement("f"), one, one).set(segment, 0L, 3.1415f);
            MIXED_LAYOUT_ARRAY.varHandle(PathElement.groupElement("c"), one, one).set(segment, 0L, 'B');
            MIXED_LAYOUT_ARRAY.varHandle(PathElement.groupElement("s"), one, one).set(segment, 0L, (short) 32767);
            MIXED_LAYOUT_ARRAY.varHandle(PathElement.groupElement("b"), one, one).set(segment, 0L, (byte) 127);

            MixedBagArray accessor = mapper.get(segment);

            assertEquals(42L, accessor.l(1, 1));
            assertEquals(123.45d, accessor.d(1, 1), EPSILON);
            assertEquals(13, accessor.i(1, 1));
            assertEquals(3.1415f, accessor.f(1, 1), EPSILON);
            assertEquals('B', accessor.c(1, 1));
            assertEquals((short) 32767, accessor.s(1, 1));
            assertEquals((byte) 127, accessor.b(1, 1));

            assertToString(accessor, mapper, "l()=long[2, 3], d()=double[2, 3], i()=int[2, 3], f()=float[2, 3], c()=char[2, 3], s()=short[2, 3], b()=byte[2, 3]");

            // Decrease every (1, 1) value by one
            accessor.b(1, 1, (byte) (accessor.b(1, 1) - 1));
            accessor.s(1, 1, (short) (accessor.s(1, 1) - 1));
            accessor.c(1, 1, (char) (accessor.c(1, 1) - 1));
            accessor.i(1, 1, accessor.i(1, 1) - 1);
            accessor.f(1, 1, accessor.f(1, 1) - 1);
            accessor.l(1, 1, accessor.l(1, 1) - 1);
            accessor.d(1, 1, accessor.d(1, 1) - 1);

            assertEquals(41L, accessor.l(1, 1));
            assertEquals(122.45d, accessor.d(1, 1), EPSILON);
            assertEquals(12, accessor.i(1, 1));
            assertEquals(2.1415f, accessor.f(1, 1), EPSILON);
            assertEquals('A', accessor.c(1, 1));
            assertEquals((short) 32766, accessor.s(1, 1));
            assertEquals((byte) 126, accessor.b(1, 1));

            assertThrows(IndexOutOfBoundsException.class, () ->
                accessor.i(-1, 0)
            );
            assertThrows(IndexOutOfBoundsException.class, () ->
                accessor.i(0, -1)
            );
            assertThrows(IndexOutOfBoundsException.class, () ->
                accessor.i(3, 0)
            );
            assertThrows(IndexOutOfBoundsException.class, () ->
                accessor.i(0, 4)
            );

        }
    }

    public interface PointRecord2Dim {
        Point points(long i, long j);
        void points(long i , long j, Point point);
    }

    @Test
    void arrayOfRecords() {
        GroupLayout triangleLayout = MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(4,
                        MemoryLayout.sequenceLayout(3, POINT_LAYOUT)
                ).withName("points")
        );
        MemorySegment segment = MemorySegment.ofArray(new int[]{
                1, 10,  2, 11,  3, 9,
                2, 11,  3, 12,  4, 10,
                3, 12,  4, 13,  5, 11,
                4, 14,  5, 14,  6, 12
        });
        SegmentMapper<PointRecord2Dim> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, PointRecord2Dim.class, triangleLayout);
        PointRecord2Dim accessor = mapper.get(segment, 0);

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                Point p = accessor.points(i, j);
                int index = 2 * 3 * i + 2 * j;
                int expectedX = segment.getAtIndex(JAVA_INT, index);
                int expectedY = segment.getAtIndex(JAVA_INT, index + 1);
                assertEquals(expectedX, p.x());
                assertEquals(expectedY, p.y());
            }
        }

        assertEquals("PointRecord2Dim[points()=Point[4, 3]]", accessor.toString());

        accessor.points(1, 2, new Point(-1, -2));
        assertEquals(-1, accessor.points(1, 2).x());
        assertEquals(-2, accessor.points(1, 2).y());

        assertThrows(IndexOutOfBoundsException.class, () ->
                accessor.points(-1, 0)
        );
        assertThrows(IndexOutOfBoundsException.class, () ->
                accessor.points(0, -1)
        );
        assertThrows(IndexOutOfBoundsException.class, () ->
                accessor.points(4, 0)
        );
        assertThrows(IndexOutOfBoundsException.class, () ->
                accessor.points(0, 3)
        );
    }

    @Test
    void gap() {

        interface Ints {
            int first();
            void first(int first);

            int second();
            // No setter for second

            int third();
            void third(int third);
        }

        MemorySegment segment = MemorySegment.ofArray(new int[]{1, 2, 3});
        GroupLayout layout = MemoryLayout.structLayout(
                JAVA_INT.withName("first"),
                JAVA_INT.withName("second"), // Not mapped to any setter
                JAVA_INT.withName("third")
        );
        SegmentMapper<Ints> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, Ints.class, layout);
        Ints accessor = mapper.get(segment, 0);

        assertEquals(1, accessor.first());
        assertEquals(3, accessor.third());

        accessor.first(11);
        accessor.third(13);
        assertEquals(11, accessor.first());
        assertEquals(13, accessor.third());

        MemorySegment dstSegment = newSegment(layout);
        mapper.set(dstSegment, accessor);
        // Unmapped regions should not be affected by the set operation
        BaseTest.assertContentEquals(BaseTest.segmentOf(11, 0, 13), dstSegment);
    }

    static final class LongPointAccessor {

        private final BaseTest.PointAccessor pointAccessor;

        public LongPointAccessor(BaseTest.PointAccessor pointAccessor) {
            this.pointAccessor = pointAccessor;
        }

        public long x() {
            return pointAccessor.x();
        }

        public long y() {
            return pointAccessor.y();
        }

        public void x(long x) {
            pointAccessor.x(Math.toIntExact(x));
        }

        public void y(long y) {
            pointAccessor.y(Math.toIntExact(y));
        }

        @Override
        public String toString() {
            return "LongPointAccessor[" +
                    "x()=" + x() + ", "+
                    "y()=" + y() +
                    ']';
        }
    }

    @Test
    void map() {
        SegmentMapper<BaseTest.PointAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, BaseTest.PointAccessor.class, POINT_LAYOUT);
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});

        SegmentMapper<LongPointAccessor> longMapper = mapper.map(LongPointAccessor.class, LongPointAccessor::new);
        LongPointAccessor accessor = longMapper.get(segment, POINT_LAYOUT.byteSize());

        assertEquals(6L, accessor.x());
        assertEquals(8L, accessor.y());

        accessor.x(1L);
        accessor.y(2L);

        assertEquals(1L, accessor.x());
        assertEquals(1, segment.getAtIndex(JAVA_INT, 2));
        assertEquals(2L, accessor.y());
        assertEquals(2, segment.getAtIndex(JAVA_INT, 3));
        assertToString(accessor, LongPointAccessor.class, "x()=1, y()=2");

        // SegmentMapper::set
        MemorySegment dstSegment = newSegment(POINT_LAYOUT);
        assertThrows(UnsupportedOperationException.class, (() ->
                longMapper.set(dstSegment, accessor)
        ));
    }

    static final class PointAccessorImpl implements BaseTest.PointAccessor {

        private final LongPointAccessor longPointAccessor;

        public PointAccessorImpl(LongPointAccessor longPointAccessor) {
            this.longPointAccessor = longPointAccessor;
        }

        public int x() {
            return Math.toIntExact(longPointAccessor.x());
        }

        public int y() {
            return Math.toIntExact(longPointAccessor.y());
        }

        public void x(int x) {
            longPointAccessor.x(x);
        }

        public void y(int y) {
            longPointAccessor.y(Math.toIntExact(y));
        }

        @Override
        public String toString() {
            return "PointAccessor[" +
                    "x()=" + x() +
                    "y()=" + y() +
                    ']';
        }
    }

    @Test
    void map2() {
        SegmentMapper<BaseTest.PointAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, BaseTest.PointAccessor.class, POINT_LAYOUT);
        assertThrows(UnsupportedOperationException.class, () ->
                mapper.map(LongPointAccessor.class, LongPointAccessor::new, PointAccessorImpl::new)
        );
    }

    @Test
    void create() {
        try (var arena = Arena.ofConfined()) {
            SegmentMapper<BaseTest.PointAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, BaseTest.PointAccessor.class, POINT_LAYOUT);
            BaseTest.PointAccessor accessor = mapper.create(arena);
            accessor.x(3);
            accessor.y(4);
            var internalSegment = mapper.segment(accessor).orElseThrow();
            BaseTest.assertContentEquals(BaseTest.segmentOf(3,4), internalSegment);
        }
    }

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
    }

    private static class PointBeanExtender extends PointBean {

        private final BaseTest.PointAccessor accessor;

        public PointBeanExtender(BaseTest.PointAccessor accessor) {
            this.accessor = accessor;
        }

        @Override
        int x() {
            return accessor.x();
        }

        @Override
        void x(int x) {
            accessor.x(x);
        }

        @Override
        int y() {
            return accessor.y();
        }

        @Override
        void y(int y) {
            accessor.y(y);
        }
    }

    @Test
    void bean() {
        try (var arena = Arena.ofConfined()) {
            SegmentMapper<BaseTest.PointAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, BaseTest.PointAccessor.class, POINT_LAYOUT);
            SegmentMapper<PointBean> beanMapper = mapper.map(PointBean.class, PointBeanExtender::new);
            PointBean pointBean = beanMapper.create(arena);
            pointBean.x(3);
            pointBean.y(4);
            assertEquals("PointBean[x=3, y=4]", pointBean.toString());
        }
    }

    interface PointWithTrapDoor {
        int x();
        void x(int x);
        int y();
        void y(int y);
        default void trapDoor() {
            throw new UnsupportedOperationException("gotcha");
        }

    }

    @Test
    void doubleBuffered() {
        int firstInt = 0x01020304;
        int secondInt = 0x05060708;
        try (var arena = Arena.ofConfined()) {
            var targetSegment = arena.allocate(POINT_LAYOUT);
            targetSegment.setAtIndex(JAVA_INT, 0, firstInt);
            targetSegment.setAtIndex(JAVA_INT, 1, secondInt);
            SegmentMapper<PointWithTrapDoor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, PointWithTrapDoor.class, POINT_LAYOUT);
            var scratchSegment = arena.allocate(POINT_LAYOUT);
            scratchSegment.copyFrom(targetSegment);
            PointWithTrapDoor p = mapper.get(scratchSegment);
            try {
                p.y(4);
                p.trapDoor();
                mapper.set(targetSegment, 0, p);
            } catch (RuntimeException e) {
                // Will always end up here
            }
            // No update to the target segment
            assertEquals(firstInt, targetSegment.getAtIndex(JAVA_INT, 0));
            assertEquals(secondInt, targetSegment.getAtIndex(JAVA_INT, 1));
            try {
                p.y(4);
                mapper.set(targetSegment, 0, p);
            } catch (RuntimeException e) {
                // Will never end up here
            }
            // Only y shall be updated
            assertEquals(firstInt, targetSegment.getAtIndex(JAVA_INT, 0));
            assertEquals(4, targetSegment.getAtIndex(JAVA_INT, 1));
        }

    }

    interface Super {
        Object point();
    }

    interface PointAccessorWithSuper extends Super {
        // Covariant override
        Point point();
    }

    @Test
    void supertype() {
        GroupLayout groupLayout = MemoryLayout.structLayout(
                POINT_LAYOUT.withName("point")
        );
        SegmentMapper<PointAccessorWithSuper> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, PointAccessorWithSuper.class, groupLayout);
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});
        PointAccessorWithSuper accessor = mapper.get(segment, groupLayout.byteSize());

        assertEquals(new Point(6, 8), accessor.point());
        assertToString(accessor, PointAccessorWithSuper.class, "point()=Point[x=6, y=8]");

        /*

        assertEquals(1, accessor.x());
        assertEquals(1, segment.getAtIndex(JAVA_INT, 2));
        assertEquals(2, accessor.y());
        assertEquals(2, segment.getAtIndex(JAVA_INT, 3));
        assertToString(accessor, mapper.type(), Set.of("x()=1", "y()=2"));

        // SegmentMapper::set
        MemorySegment dstSegment = newSegment(POINT_LAYOUT);
        mapper.set(dstSegment, accessor);
        BaseTest.assertContentEquals(BaseTest.segmentOf(1, 2), dstSegment);*/
    }

    interface AccessorA {
        Point point();
    }

    interface A {
        AccessorA pointAccessor();
    }

    interface AccessorB extends AccessorA {
        Point point();
    }

    interface B {
        AccessorB pointAccessor();
    }

    interface C extends A, B {  }; // mapper for this

    @Test
    void mixin() {
        GroupLayout groupLayout = MemoryLayout.structLayout(
                MemoryLayout.structLayout(
                        POINT_LAYOUT.withName("point")
                ).withName("pointAccessor")
        );
        SegmentMapper<C> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, C.class, groupLayout);
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});
        C accessor = mapper.get(segment, groupLayout.byteSize());


        assertEquals(new Point(6, 8), accessor.pointAccessor().point());
        assertToString(accessor, C.class,"pointAccessor()=AccessorA[point()=Point[x=6, y=8]], pointAccessor()=AccessorB[point()=Point[x=6, y=8]]");

        /*

        assertEquals(1, accessor.x());
        assertEquals(1, segment.getAtIndex(JAVA_INT, 2));
        assertEquals(2, accessor.y());
        assertEquals(2, segment.getAtIndex(JAVA_INT, 3));
        assertToString(accessor, mapper.type(), Set.of("x()=1", "y()=2"));

        // SegmentMapper::set
        MemorySegment dstSegment = newSegment(POINT_LAYOUT);
        mapper.set(dstSegment, accessor);
        BaseTest.assertContentEquals(BaseTest.segmentOf(1, 2), dstSegment);*/
    }

    interface Generic<T extends BaseTest.PointAccessor> {
        T pointAccessor();
    }

    interface FixedGeneric extends Generic<BaseTest.PointAccessor> {}

    @Test
    void fixedGeneric() {
        GroupLayout groupLayout = MemoryLayout.structLayout(
                POINT_LAYOUT.withName("pointAccessor")
        );
        SegmentMapper<FixedGeneric> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, FixedGeneric.class, groupLayout);
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});
        Generic<?> accessor = mapper.get(segment, POINT_LAYOUT.byteSize());

        assertEquals(6, accessor.pointAccessor().x());
        assertEquals(8, accessor.pointAccessor().y());
        assertToString(accessor, mapper, "pointAccessor()=PointAccessor[x()=6, y()=8]");
    }

    @Test
    void generic() {
        GroupLayout groupLayout = MemoryLayout.structLayout(
                POINT_LAYOUT.withName("pointAccessor")
        );
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () ->
            SegmentMapper.ofInterface(LOCAL_LOOKUP, Generic.class, groupLayout)
        );
        assertTrue(iae.getMessage().contains("is directly declaring type parameters"));
        assertTrue(iae.getMessage().contains("Generic<T extends BaseTest$PointAccessor>"));
    }


    @Test
    void classProperties() {
        SegmentMapper<BaseTest.PointAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, BaseTest.PointAccessor.class, POINT_LAYOUT);
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});
        BaseTest.PointAccessor accessor = mapper.get(segment, POINT_LAYOUT.byteSize());
        Class<?> clazz = accessor.getClass();
        assertNotNull(clazz.getAnnotation(ValueBased.class), "not @ValueBased");
        assertTrue(clazz.isHidden(), "Not hidden");
        assertTrue(clazz.getName().contains("PointAccessorInterfaceMapper"));
    }

    static MemorySegment newSegment(MemoryLayout layout) {
        return Arena.ofAuto().allocate(layout);
    }

    <T> void assertToString(T o,
                            SegmentMapper<? extends T> mapper,
                            String values) {
        assertToString(o, mapper.type(), values);
    }

    <T> void assertToString(T o,
                            Class<?> mapperType,
                            String values) {
        String expected = mapperType.getSimpleName() + "[" + values + "]";
        assertEquals(expected, o.toString());
    }

}
