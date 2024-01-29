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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestJepExamplesMapperRecord
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.mapper.SegmentMapper;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

final class TestJepExamplesMapperRecord {

    private static final
    StructLayout POINT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );


    public record Point(int x, int y) {
    }


    @Test
    void point() {
        MemorySegment segment = Arena.ofAuto().allocateFrom(JAVA_INT, 3, 4);

        // Automatically creates a mapper that can read and write Point records from/to segments.
        SegmentMapper<Point> mapper = SegmentMapper.ofRecord(Point.class, POINT);

        Point point = mapper.get(segment);
        // Point[x=3, y=4]

        mapper.set(segment, new Point(-1, -2));
        // segment = -1, -2
        //          |-----|
        //          Updated

        assertEquals(new Point(3, 4), point);
    }

    public record Line(Point begin, Point end) {
    }

    @Test
    void line() {

        StructLayout LINE = MemoryLayout.structLayout(
                POINT.withName("begin"),
                POINT.withName("end"));

        MemorySegment segment = Arena.ofAuto().allocateFrom(JAVA_INT, 3, 4, 5, 6);

        SegmentMapper<Line> mapper =
                SegmentMapper.ofRecord(Line.class, LINE);

        Line line = mapper.get(segment);
        // Line[begin=Point[x=3, y=4], end=Point[x=5, y=6]]

        mapper.set(segment, new Line(new Point(-1, -2), new Point(-3, -4)));
        // segment = -1, -2, -3, -4
        //           |------------|
        //              Updated
    }


}
