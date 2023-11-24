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
import java.lang.foreign.mapper.LayoutGenerators;

import static org.junit.jupiter.api.Assertions.*;

final class TestLayoutGenerators extends BaseTest {

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
        assertEquals(POINT_LAYOUT.withName(Point.class.getName()), layout);
    }

    @Test
    void fromLine() {
        GroupLayout layout = LayoutGenerators.ofRecord(Line.class);
        assertEquals(LINE_LAYOUT.withName(Line.class.getName()), layout);
    }

    @Test
    void fromPoints() {
        assertThrows(IllegalArgumentException.class, () ->
                LayoutGenerators.ofRecord(Points.class)
        );
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
        assertEquals(POINT_LAYOUT.withName(PointAccessor.class.getName()), layout);
    }

    @Test
    void fromLineAccessor() {
        GroupLayout layout = LayoutGenerators.ofInterface(LineAccessor.class);
        assertEquals(LINE_LAYOUT.withName(LineAccessor.class.getName()), layout);
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
