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
 * @enablePreview
 * @run junit TestMultiThreadAccess
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

final class TestMultiThreadAccess {

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();


    private static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );

    public interface PointAccessor extends SegmentMapper.Sharable {
        int x();
        void x(int x);
        int y();
        void y(int y);
    }

    @Test
    void point() throws ExecutionException, InterruptedException {
        SegmentMapper<PointAccessor> mapper = SegmentMapper.ofInterface(LOCAL_LOOKUP, PointAccessor.class, POINT_LAYOUT);
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});
        Object o = mapper.getAtIndex(segment, 1);
        PointAccessor accessor = mapper.getAtIndex(segment, 1);

        CompletableFuture.runAsync(() -> {
            accessor.acquire();
            try {
                accessor.x(-1);
                accessor.y(-2);
            } finally {
                accessor.release();
            }
        }).get();

        accessor.acquire();
        assertEquals(-1, accessor.x());
        assertEquals(-2, accessor.y());
    }

}
