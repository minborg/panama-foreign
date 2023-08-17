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
 * @run junit TestRecordingArena
 */

import java.lang.foreign.Arena;
import java.lang.foreign.Arena.OfRecording;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestRecordingArena {

    private OfRecording arena;

    @BeforeEach
    void setup() {
        arena = Arena.ofRecording(Arena.ofConfined());
    }

    @Test
    void empty() {
        assertEquals(0L, arena.events().count());
    }

    @Test
    void one() {
        var segment = arena.allocate(32, 16);
        assertEquals(32, segment.byteSize());
        List<OfRecording.Event> events = arena.events().toList();
        assertEquals(1, events.size());
        OfRecording.Event event = events.getFirst();
        assertEquals(32, event.byteSize());
        assertEquals(16, event.byteAlignment());
    }

    @Test
    void many() {
        Random random = new Random(42);
        List<OfRecording.Event> events = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            long byteSize = random.nextLong(1024);
            long byteAlignment = 1L << random.nextInt(6);
            events.add(new OfRecording.Event(0, 0, byteSize, byteAlignment));
            var segment = arena.allocate(byteSize, byteAlignment);
            assertEquals(byteSize, segment.byteSize());
        }
        List<OfRecording.Event> arenaEvents = arena.events().toList();
        assertEquals(events.size(), arenaEvents.size());
        long lastTime = 0;
        for (int i = 0; i < events.size(); i++) {
            assertTrue(arenaEvents.get(i).timeNs() > lastTime);
            lastTime = events.get(i).timeNs();
            assertTrue(arenaEvents.get(i).durationNs() > 0);
            assertEquals(events.get(i).byteSize(), arenaEvents.get(i).byteSize());
            assertEquals(events.get(i).byteAlignment(), arenaEvents.get(i).byteAlignment());
        }

    }

    @Test
    void close() {
        // Closing should not invalidate the recordings
        arena.allocate(32, 16);
        arena.close();
        assertEquals(1L, arena.events().count());
    }

}
