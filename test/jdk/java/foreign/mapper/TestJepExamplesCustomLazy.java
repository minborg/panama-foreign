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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestJepExamplesCustomLazy
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

final class TestJepExamplesCustomLazy {

     public static class Point {
        public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
                ValueLayout.JAVA_INT.withName("x"),
                ValueLayout.JAVA_INT.withName("y")
        );

        private static final VarHandle X_HANDLE = LAYOUT.varHandle(PathElement.groupElement("x"));
        private static final VarHandle Y_HANDLE = LAYOUT.varHandle(PathElement.groupElement("x"));

        private final MemorySegment segment;

        private Point(MemorySegment segment) {
            this.segment = segment;
        }

        public int x() {
            return (int)X_HANDLE.get(segment, 0L);
        }

        public void x(int x) {
            X_HANDLE.set(segment, 0L, x);
        }

        public int y() {
            return (int)Y_HANDLE.get(segment, 0L);
        }

        public void y(int y) {
            Y_HANDLE.set(segment, 0L, y);
        }

        public static Point allocate(Arena arena) {
            return new Point(arena.allocate(LAYOUT));
        }
    }


    @Test
    void customLazy() {
        Point point = Point.allocate(Arena.ofAuto());
        point.x(point.x() + 1);
        point.y(point.y() + 1);
    }



}
