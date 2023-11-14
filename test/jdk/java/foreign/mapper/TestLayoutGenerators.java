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
 * @run junit/othervm --enable-preview TestLayoutGenerators
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.mapper.LayoutGenerators;

import static org.junit.jupiter.api.Assertions.*;
import static java.lang.foreign.ValueLayout.JAVA_INT;

final class TestLayoutGenerators {

    static final GroupLayout POINT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y"));

    static final GroupLayout LINE = MemoryLayout.structLayout(
            POINT.withName("begin"),
            POINT.withName("end"));

    public record Point(int x, int y){}

    public record Line(Point begin, Point end){}

    public interface PointAccessor {
        int x();
        void x(int x);
        int y();
        void y(int x);
    }

    public interface LineAccessor {
        Point begin();
        void begin(Point begin);
        Point end();
        void end(Point end);
    }

    public static class LineBean {

        private int x;
        private int y;

        public LineBean(int x, int y) {
            this.x = x;
            this.y = y;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }
    }

    void nulls() {
        assertThrows(NullPointerException.class, () ->
                LayoutGenerators.ofRecord(null)
        );
        assertThrows(NullPointerException.class, () ->
                LayoutGenerators.ofInterface(null)
        );
    }

    // Records

    @Test
    void fromPoint() {
        GroupLayout layout = LayoutGenerators.ofRecord(Point.class);
        assertEquals(POINT.withName(Point.class.getName()), layout);
    }

    @Test
    void fromLine() {
        GroupLayout layout = LayoutGenerators.ofRecord(Line.class);
        assertEquals(LINE.withName(Line.class.getName()), layout);
    }

    @Test
    void fromLineBean() {
        assertThrows(IllegalArgumentException.class, () ->
                LayoutGenerators.ofInterface(LineBean.class)
        );
    }

    // Interfaces

    @Test
    void fromPointAccessor() {
        GroupLayout layout = LayoutGenerators.ofInterface(PointAccessor.class);
        assertEquals(POINT.withName(PointAccessor.class.getName()), layout);
    }

    @Test
    void fromLineAccessor() {
        GroupLayout layout = LayoutGenerators.ofInterface(LineAccessor.class);
        assertEquals(LINE.withName(LineAccessor.class.getName()), layout);
    }


    // Arrays components

    public record Foo(int[] nums) {}

    @Test
    void fromFoo() {
        assertThrows(IllegalArgumentException.class, () ->
                    LayoutGenerators.ofRecord(Foo.class)
                );
    }

}
