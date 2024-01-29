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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestJepExamplesMapperLazy
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandles;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

final class TestJepExamplesMapperLazy {

    private static final
    StructLayout POINT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );

    private static final
    StructLayout LINE = MemoryLayout.structLayout(
            POINT.withName("begin"),
            POINT.withName("end"));

    interface Point {
        int x();
        int y();
        void x(int x);
        void y(int y);
    }

    @Test
    void point() {
        MemorySegment segment = Arena.ofAuto().allocateFrom(JAVA_INT, 3, 4);

        SegmentMapper<Point> mapper =
                SegmentMapper.ofInterface(MethodHandles.lookup(), Point.class, POINT);

        Point point = mapper.get(segment);

        assertEquals(3, point.x());
        assertEquals(4, point.y());
        assertEquals("Point[x()=3, y()=4]", point.toString());

        // Partial update of a point
        point.y(-2);
        // segment = 3, -2
        //            |--|
        //           Updated
    }

    interface Line {
        Point begin();
        Point end();
    }

    @Test
    void line() {
        MemorySegment segment = Arena.ofAuto().allocateFrom(JAVA_INT, 3, 4, 5, 6);

        SegmentMapper<Line> mapper =
                SegmentMapper.ofInterface(MethodHandles.lookup(), Line.class, LINE);

        Line line = mapper.get(segment);

        // Partially update the line in the segment
        line.begin().y(-2);

        //          begin  end
        //         |-----|-----|
        //           x  y  x  y
        // segment = 3, -2, 6, 0
        //             |--|
        //            Updated
    }

}
