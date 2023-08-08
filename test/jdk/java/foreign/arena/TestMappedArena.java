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
 * @run junit TestMappedArena
 */

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.file.StandardOpenOption.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestMappedArena {

    private static final Path PATH = Paths.get("mapped");
    private static final long ALLOC_SIZE = 32;
    private static final Set<OpenOption> OPTIONS = Set.of(CREATE, SPARSE, READ, WRITE);

    private Arena arena;

    @BeforeEach
    void setup() throws IOException {
        Files.deleteIfExists(PATH);
        arena = Arena.ofMapped(Arena.ofConfined(), PATH,  OPTIONS);
    }

    @Test
    void empty() {
        assertTrue(Files.exists(PATH));
    }

    @Test
    void one() {
        var seg = arena.allocate(32, 16);
        assertEquals(ALLOC_SIZE, seg.byteSize());
        assertEquals(ALLOC_SIZE, fileSize());
    }

    @Test
    void many() {
        for (int i = 0; i < 23; i++) {
            var seg = arena.allocate(ALLOC_SIZE, 16);
            assertEquals(ALLOC_SIZE, seg.byteSize());
            assertEquals(ALLOC_SIZE * (i + 1), fileSize());
        }
    }

    @Test
    void content() throws IOException {
        var seg0 = arena.allocate(Long.BYTES);
        var seg1 = arena.allocate(Long.BYTES);
        seg0.set(ValueLayout.JAVA_LONG, 0, 0x1234_5678);
        seg1.set(ValueLayout.JAVA_LONG, 0, 0x39AB_CDEF);
        try (var fc = FileChannel.open(PATH, OPTIONS)) {
            var mapped = fc.map(READ_WRITE, 0, Long.BYTES * 2, arena);
            assertEquals(0x1234_5678, mapped.get(ValueLayout.JAVA_LONG, 0));
            assertEquals(0x39AB_CDEF, mapped.get(ValueLayout.JAVA_LONG, Long.BYTES));
        }
    }

    @Test
    void close() {
        var seg0 = arena.allocate(Long.BYTES);
        arena.close();
        assertThrows(IllegalStateException.class, () ->
                arena.allocate(16));
    }

    private long fileSize() {
        try {
            return Files.size(PATH);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

}
