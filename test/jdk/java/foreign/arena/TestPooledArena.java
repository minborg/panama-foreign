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
 * @run junit TestPooledArena
 */

import org.junit.jupiter.api.*;

import java.lang.foreign.Arena;
import java.lang.foreign.Arena.OfRecording;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class TestPooledArena {

    private static final long ALLOC_SIZE = 32;

    private OfRecording ofRecording;
    private Arena.OfPooled arena;

    @BeforeEach
    void setup() {
        ofRecording = Arena.ofRecording(Arena.ofConfined());
        arena = Arena.ofPooled(ofRecording);
    }

    @Test
    void empty() {
        // Make sure we have no pre-allocated memory
        assertEquals(0, totalAlloc());
    }

    @Test
    void one() {
        var seg = arena.allocate(ALLOC_SIZE, 16);
        assertEquals(ALLOC_SIZE, totalAlloc());
        assertEquals(ALLOC_SIZE, seg.byteSize());
        arena.recycle(seg);
        var seg2 = arena.allocate(ALLOC_SIZE, 16);
        assertEquals(seg.address(), seg2.address());
    }

    @Test
    void many() {
        for (int i = 0; i < 200; i++) {
            var seg = arena.allocate(ALLOC_SIZE, 16);
            assertEquals(ALLOC_SIZE, totalAlloc());
            assertEquals(ALLOC_SIZE, seg.byteSize());
            arena.recycle(seg);
        }
    }

    @Test
    void manySequence() {
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 257; i++) {
                var seg = arena.allocate(i, 16);
                assertEquals(i, seg.byteSize());
                arena.recycle(seg);
            }
            var allocations = ofRecording.events().count();
            assertTrue(allocations <= 10);
        }
    }

    @Test
    void manyRandom() {
        long maxSize = 1 << 18;
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            var size = random.nextLong(maxSize);
            var bucket = 64 - Long.numberOfLeadingZeros(size);
            var seg = arena.allocate(size, 16);
            assertEquals(size, seg.byteSize(), "Failed for " + size);
            arena.recycle(seg);
        }
        var allocations = ofRecording.events().count();
        assertTrue(allocations < 18);
    }

    private long totalAlloc() {
        return ofRecording.events().mapToLong(OfRecording.Event::byteSize).sum();
    }

}
