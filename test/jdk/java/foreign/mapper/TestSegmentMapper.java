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
 * @run junit/othervm --enable-preview TestSegmentMapper
 */
// options: --enable-preview -source ${jdk.version} -Xlint:preview

import org.junit.jupiter.api.Test;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandles;
// import java.lang.foreign.mapper.SegmentMapper;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

final class TestSegmentMapper {

    static final MemorySegment SEGMENT = MemorySegment.ofArray(new int[]{3, 4, 6, 8}).asReadOnly();

    static final GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));

    record Point(int x, int y){}

    static final GroupLayout LINE = MemoryLayout.structLayout(POINT.withName("begin"), POINT.withName("end"));

    record Line(Point begin, Point end){}

    public interface PointAccessor {
        int x();
        void x(int x);
        int y();
        void y(int x);
    }

    @Test
    void point() {
        SegmentMapper<Point> mapper = SegmentMapper.ofRecord(MethodHandles.lookup(), Point.class, POINT);
        assertTrue(mapper.isExhaustive());

        Point point = mapper.get(SEGMENT);
        assertEquals(new Point(3,4), point);
        point = mapper.get(SEGMENT, 8);
        assertEquals(new Point(6,8), point);
        point = mapper.getAtIndex(SEGMENT, 1);
        assertEquals(new Point(6,8), point);
    }

    @Test
    void mappedPoint() {
        SegmentMapper<Point> mapper = SegmentMapper.ofRecord(MethodHandles.lookup(), Point.class, POINT);
    }

    @Test
    void line() {
        SegmentMapper<Line> mapper = SegmentMapper.ofRecord(MethodHandles.lookup(), Line.class, LINE);
        assertTrue(mapper.isExhaustive());

        Line point = mapper.get(SEGMENT);
        assertEquals(new Line(new Point(3,4), new Point(6, 8)), point);
    }

    @Test
    void InterfaceApi() {
        SegmentMapper<PointAccessor> mapper = SegmentMapper.ofInterface(PointAccessor.class, POINT);
    }

}
