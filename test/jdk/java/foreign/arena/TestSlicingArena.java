/*
 *  Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */

/*
 * @test
 * @run junit TestSlicingArena
 */

import org.junit.jupiter.api.*;

import java.lang.foreign.Arena;

import static org.junit.jupiter.api.Assertions.*;

public class TestSlicingArena {

    private static final long PRE_ALLOC_SIZE = 1L << 14;
    private static final long ALLOC_SIZE = 32;

    private Arena.OfSlicing arena;

    @BeforeEach
    void setup() {
        arena = Arena.ofSlicing(Arena.ofConfined(), PRE_ALLOC_SIZE);
    }

    @Test
    void one() {
        arena.acquire();
        var seg = arena.allocate(ALLOC_SIZE, 16);
        assertEquals(ALLOC_SIZE, seg.byteSize());
        arena.release();
    }

    @Test
    void many() {
        arena.acquire();
        for (int i = 0; i < 200; i++) {
            var seg = arena.allocate(ALLOC_SIZE, 16);
            assertEquals(ALLOC_SIZE, seg.byteSize());
        }
        arena.release();
    }

}
