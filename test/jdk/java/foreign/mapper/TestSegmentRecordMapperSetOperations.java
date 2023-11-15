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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestSegmentRecordMapperSetOperations
 */

import org.junit.jupiter.api.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

final class TestSegmentRecordMapperSetOperations extends BaseTest {

    private static final Point POINT = new Point(3, 4);

    private Arena arena;
    private MemorySegment pointSegment;

    @BeforeEach
    void setup() {
        arena = Arena.ofConfined();
        pointSegment = arena.allocate(POINT_LAYOUT);
    }

    @AfterEach
    void shutdown() {
        arena.close();
    }

    @Test
    void point() {
        // POINT_MAPPER.set(pointSegment, POINT);

        pointSegment.set(JAVA_INT, 0, 3);

        assertContentEquals(of(3, 4), pointSegment);
    }

    private static int[] of(int... ints) {
        return ints;
    }

    private static void assertContentEquals(int[] expected, MemorySegment actual) {
        assertContentEquals(MemorySegment.ofArray(expected), actual);
    }

    private static void assertContentEquals(MemorySegment expected, MemorySegment actual) {
        if (expected.mismatch(actual) != -1) {
            HexFormat hexFormat = HexFormat.ofDelimiter(" ");
            fail("Expected '" + hexFormat.formatHex(expected.toArray(JAVA_BYTE)) +
                    "' but got '" + hexFormat.formatHex(actual.toArray(JAVA_BYTE))+"'");
        }
    }

}
