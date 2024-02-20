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

package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.JAVA_INT;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED" })
public class SegmentMapperRecordTest {

    // Records
    public record Point(int x, int y){}
    public record Line(Point begin, Point end){}

    private static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );

    private static final GroupLayout LINE_LAYOUT = MemoryLayout.structLayout(
            POINT_LAYOUT.withName("begin"),
            POINT_LAYOUT.withName("end")
    );

    private static final MemorySegment SEGMENT = MemorySegment.ofArray(new int[]{3, 4});

    private static final SegmentMapper<Point> POINT_MAPPER = SegmentMapper.ofRecord(Point.class, POINT_LAYOUT);
    private static final MethodHandle POINT_MH = POINT_MAPPER.getter();
    private static final SegmentMapper<Line> LINE_MAPPER = SegmentMapper.ofRecord(Line.class, LINE_LAYOUT);

    // Todo: declare MH directly

    @Benchmark
    public int mappedPoint() {
        return POINT_MAPPER.get(SEGMENT, 0)
                .x();
    }

    @Benchmark
    public int mappedPointMh() throws Throwable {
        return ((Point) (Object) POINT_MH.invokeExact(SEGMENT, 0L))
                .x();
    }

    // A custom record that can only be read from a segment (not write)
    record MyPoint(int x, int y) {
        public MyPoint(MemorySegment segment) {
            this(Objects.requireNonNull(segment).get(JAVA_INT, 0), segment.get(JAVA_INT, 4));
        }
    }

    @Benchmark
    public int customPoint() {
        return new MyPoint(SEGMENT)
                .x();
    }

}
