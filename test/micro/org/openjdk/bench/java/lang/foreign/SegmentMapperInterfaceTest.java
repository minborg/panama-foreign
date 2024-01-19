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
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static java.lang.foreign.ValueLayout.JAVA_INT;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED" })
public class SegmentMapperInterfaceTest {

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();
    static {
        System.out.println("LOCAL_LOOKUP.toString() = " + LOCAL_LOOKUP);
        System.out.println("PointAccessor.class.getPackageName() = " + PointAccessor.class.getPackageName());
    }

    // Interface accessors
    public interface PointAccessor{ int x(); int y();}
    public interface LineAccessor{PointAccessor begin(); PointAccessor end();}

    private static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );

    private static final GroupLayout LINE_LAYOUT = MemoryLayout.structLayout(
            POINT_LAYOUT.withName("begin"),
            POINT_LAYOUT.withName("end")
    );

    private static final MemorySegment SEGMENT = MemorySegment.ofArray(new int[]{3, 4, 5, 6});

    private static final SegmentMapper<PointAccessor> POINT_ACCESSOR_MAPPER =
            SegmentMapper.ofInterface(LOCAL_LOOKUP, PointAccessor.class, POINT_LAYOUT);

    private static final MethodHandle POINT_ACCESSOR_MAPPER_HANDLE = POINT_ACCESSOR_MAPPER.getHandle();


    public static class MyPointAccessor implements PointAccessor {

        private final MemorySegment segment;
        private final long offset;

        public MyPointAccessor(MemorySegment segment, long offset) {
            this.segment = Objects.requireNonNull(segment);
            this.offset = offset;
        }

        @Override
        public int x() {
            return segment.get(JAVA_INT, offset);
        }

        @Override
        public int y() {
            return segment.get(JAVA_INT, offset);
        }
    }

    @Benchmark
    public int mappedPointAccessor() {
        return POINT_ACCESSOR_MAPPER.get(SEGMENT, 0)
                .x();
    }

    @Benchmark
    public int mappedPointAccessorMh() throws Throwable {
        return ((PointAccessor) (Object) POINT_ACCESSOR_MAPPER_HANDLE.invokeExact(SEGMENT, 0L))
                .x();
    }

    @Benchmark
    public int customPointAccessor() {
        return new MyPointAccessor(SEGMENT, 0)
                .x();
    }

    @Benchmark
    public int proxyPointAccessor() {
        return new ProxyMapper(SEGMENT, 0).get()
                .x();
    }

    private static final PointAccessor PROXY = new ProxyMapper(SEGMENT, 0).get();
    private static final PointAccessor MAPPER = POINT_ACCESSOR_MAPPER.get(SEGMENT, 0);

    @Benchmark
    public int preProxyPointAccessor() {
        return PROXY
                .x();
    }

    @Benchmark
    public int prePointAccessor() {
        return MAPPER
                .x();
    }

    private static final class ProxyMapper {

        private final MemorySegment segment;
        private final long offset;

        public ProxyMapper(MemorySegment segment, long offset) {
            this.segment = segment;
            this.offset = offset;
        }

        PointAccessor get() {
            PointAccessor pointAccessor = (PointAccessor) Proxy.newProxyInstance(
                    ProxyMapper.class.getClassLoader(),
                     new Class[]{PointAccessor.class},
                    (proxy, m, arg) ->
                        switch (m.getName()) {
                            case "toString" -> "proxy";
                            case "hashCode" -> 1;
                            case "equals" -> proxy == arg[0];
                            case "x" -> segment.get(JAVA_INT, offset);
                            case "y" -> segment.get(JAVA_INT, offset + 4);
                            default -> null;
                    });
            return pointAccessor;
        }
    }

}
