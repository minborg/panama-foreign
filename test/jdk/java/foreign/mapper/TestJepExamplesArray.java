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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestJepExamplesArray
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.mapper.SegmentMapper;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class TestJepExamplesArray {


    private static final
    StructLayout POINT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );

    public record Point(int x, int y) {
    }

    public record Triangle(Point[] points) { }

    private static final
    StructLayout TRIANGLE = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(3, POINT).withName("points")
    );

    @Test
    void array() {
        MemorySegment segment = MemorySegment.ofArray(new int[]{0, 0,  1, 3,  2, 0});

        SegmentMapper<Triangle> mapper = SegmentMapper.ofRecord(Triangle.class, TRIANGLE);

        Triangle triangle = mapper.get(segment);

        String s = Arrays.toString(triangle.points()); // Point[x=0, y=0], Point[x=1, y=3], Point[x=2, y=0]

        assertArrayEquals(new Point[]{new Point(0, 0), new Point(1, 3), new Point(2, 0)}, triangle.points());
    }

}
