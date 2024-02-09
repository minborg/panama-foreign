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
 * @run junit/othervm --enable-preview TestNaturalLayout
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.mapper.NaturalLayout;

import static org.junit.jupiter.api.Assertions.*;

final class TestNaturalLayout extends BaseTest {

    @Test
    void nulls() {
        assertThrows(NullPointerException.class, () ->
                NaturalLayout.ofRecord(null)
        );
    }

    // Records

    @Test
    void fromPoint() {
        GroupLayout layout = NaturalLayout.ofRecord(Point.class);
        assertEquals(POINT_LAYOUT, layout);
    }

    @Test
    void fromLine() {
        GroupLayout layout = NaturalLayout.ofRecord(Line.class);
        assertEquals(LINE_LAYOUT, layout);
    }

    @Test
    void fromPoints() {
        assertThrows(IllegalArgumentException.class, () ->
                NaturalLayout.ofRecord(Points.class)
        );
    }


    // Arrays components

    // The size of an array is not baked into the type system in Java
    // So, there is no `int[4] nums' for example.
    //
    // One alternative would be to return an unboud sequence layout

    public record Foo(int[] nums) {}

    @Test
    void fromFoo() {
        assertThrows(IllegalArgumentException.class, () ->
                    NaturalLayout.ofRecord(Foo.class)
                );
    }


}
