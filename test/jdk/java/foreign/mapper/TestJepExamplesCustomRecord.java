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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestJepExamplesCustomRecord
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.mapper.RecordMapper;
import java.lang.foreign.mapper.SegmentMapper;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

final class TestJepExamplesCustomRecord {

    private static final
    StructLayout POINT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );


    public record Point(int x, int y) {
    }


    record TinyPoint(byte x, byte y) { }

    // Lossless narrowing
    TinyPoint toTiny(Point point) {
        return new TinyPoint(toByteExact(point.x()), toByteExact(point.y()));
    }

    Point fromTiny(TinyPoint point) {
        return new Point(point.x(), point.y());
    }

    private static byte toByteExact(int value) {
        if ((byte) value != value) {
            throw new ArithmeticException("byte overflow");
        }
        return (byte) value;
    }


    @Test
    void tiny() {
        MemorySegment segment = Arena.ofAuto().allocateFrom(JAVA_INT, 3, 4);
        // Original mapper
        RecordMapper<Point> mapper =
                RecordMapper.ofRecord(Point.class, POINT);

        // Secondary-stage mapper
        SegmentMapper<TinyPoint> tinyMapper =
                mapper.map(TinyPoint.class, this::toTiny, this::fromTiny);

        TinyPoint tp = tinyMapper.get(segment);
        assertEquals(new TinyPoint((byte) 3, (byte) 4), tp);
    }

    // https://man7.org/linux/man-pages/man3/dlinfo.3.html
    //
    // typedef struct {
    //                      char *dls_name;            /* Name of library search
    //                                                    path directory */
    //                      unsigned int dls_flags;    /* Indicates where this
    //                                                    directory came from */
    //                  } Dl_serpath;


    public record DlSerPath(MemorySegment dlsName, int dlsFlags){

        String dlsNameAsString() {
            return dlsName.getString(0);
        }

    }

    public static final
    StructLayout DL_SERPATH = MemoryLayout.structLayout(
            ADDRESS.withName("dlsName").withTargetLayout(MemoryLayout.sequenceLayout(Integer.MAX_VALUE, JAVA_BYTE)),
            JAVA_INT.withName("dlsFlags")
    );


    @Test
    void dlSearchPath() {
        // Initialize the segment
        Arena arena = Arena.ofAuto();
        String text = "Abc";
        MemorySegment textSegment = arena.allocateFrom(text);
        MemorySegment segment = arena.allocate(DL_SERPATH);
        segment.set(ADDRESS, 0, textSegment);
        segment.set(JAVA_INT, ADDRESS.byteSize(), 0);

        RecordMapper<DlSerPath> mapper = RecordMapper.ofRecord(DlSerPath.class, DL_SERPATH);

        DlSerPath dlSerPath = mapper.get(segment);

        assertEquals("Abc", dlSerPath.dlsNameAsString());
        assertEquals(0, dlSerPath.dlsFlags());
    }

}
