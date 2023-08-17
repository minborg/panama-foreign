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
 * @run junit TestResizingArena
 */

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

public class TestResizingArena {

    private static final Path PATH = Paths.get("mapped_dir");
    private static final long ALLOC_SIZE = 32;

    private Arena.OfResizing arena;

    @BeforeEach
    void setup() throws IOException {
        deleteDirIfExists();
        Files.createDirectory(PATH);
        arena = Arena.ofResizing(Arena.ofConfined(), PATH);
    }

    @AfterEach
    void cleanup() throws IOException {
        if (arena.scope().isAlive()) {
            arena.close();
        }
        deleteDirIfExists();
    }

    private static void deleteDirIfExists() throws IOException {
        if (Files.exists(PATH)) {
            try (var paths = Files.walk(PATH)) {
                paths
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            }
        }
    }

    @Test
    void one() {
        var seg = arena.allocate(32, 16);
        assertEquals(ALLOC_SIZE, seg.byteSize());
        seg.set(ValueLayout.JAVA_INT, 16, Integer.MAX_VALUE);
        var resized = arena.mirror(seg, 64);

        // Test content is retained
        assertEquals(Integer.MAX_VALUE, resized.get(ValueLayout.JAVA_INT, 16));

        // Test expanded region can be used
        resized.set(ValueLayout.JAVA_INT, 48, 42);
        assertEquals(42, resized.get(ValueLayout.JAVA_INT, 48));

        // Test that both segment share memory
        resized.set(ValueLayout.JAVA_INT, 8, 13);
        assertEquals(13, seg.get(ValueLayout.JAVA_INT, 8));
    }


    @Test
    void close() {
        var seg0 = arena.allocate(Long.BYTES);
        arena.close();
        assertThrows(IllegalStateException.class, () ->
                arena.allocate(16));
    }

}
