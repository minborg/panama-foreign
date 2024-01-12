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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestSegmentTable
 */

import jdk.internal.ValueBased;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.mapper.SegmentTable;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

import static java.lang.foreign.ValueLayout.JAVA_INT;

final class TestSegmentTable {

    private static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y"));

    interface Point {
        int x();
        int y();
    }

    // Convenience implementations
    record PointRecord(int x, int y) implements Point{}

    private static final SegmentTable.Stencil<Point> POINT_STENCIL = stencilOfPoint(POINT_LAYOUT);

    @Test
    void pointFromBlank() {
        SegmentTable<Point> points = POINT_STENCIL.create(Arena.ofAuto(), 3);
        points.stream()
                .forEachOrdered(this::println);

        String expected = """
                Point[x()=0, y()=0]
                Point[x()=0, y()=0]
                Point[x()=0, y()=0]
                """;

        assertEquals(expected, lines());
    }

    @Test
    void pointFromStream() {
        SegmentTable<Point> points = POINT_STENCIL.create(Arena.ofAuto(),
                IntStream.range(0, 3)
                        .mapToObj(i -> new PointRecord(i, i)));

        points.stream()
                .forEachOrdered(this::println);

        String expected = """
                Point[x()=0, y()=0]
                Point[x()=1, y()=1]
                Point[x()=2, y()=2]
                """;

        assertEquals(expected, lines());
    }

    @Test
    void pointFromSegments() {
        MemorySegment xSegment = MemorySegment.ofArray(new int[]{0, 1, 2}); // Column for x
        MemorySegment ySegment = MemorySegment.ofArray(new int[]{0, 1, 2}); // Column for y
        Map<String, MemorySegment> segmentMap = Map.of("x", xSegment, "y", ySegment);

        SegmentTable<Point> points = POINT_STENCIL.createFromFlat(segmentMap);

        points.stream()
                .forEachOrdered(this::println);

        String expected = """
                Point[x()=0, y()=0]
                Point[x()=1, y()=1]
                Point[x()=2, y()=2]
                """;

        assertEquals(expected, lines());
    }

    private static final GroupLayout LINE_LAYOUT = MemoryLayout.structLayout(
            POINT_LAYOUT.withName("begin"),
            POINT_LAYOUT.withName("end"));

    interface Line {
        Point begin();
        Point end();
    }


    @SuppressWarnings("unchecked")
    static <T> SegmentTable.Stencil<T> ofInterface(MethodHandles.Lookup lookup,
                                                   Class<T> type,
                                                   GroupLayout layout) {
        Objects.requireNonNull(lookup);
        //MapperUtil.requireImplementableInterfaceType(type);
        Objects.requireNonNull(layout);
        if (type == Point.class) {
            return (SegmentTable.Stencil<T>) stencilOfPoint(POINT_LAYOUT);
        }
        throw new UnsupportedOperationException();
    }

    static SegmentTable.Stencil<Point> stencilOfPoint(GroupLayout layout) {
        return new SegmentTable.Stencil<>() {

            @Override
            public SegmentTable.Stencil<Point> withIndices() {
                return this;
            }

            @Override
            public SegmentTable.Stencil<Point> withReadOnlyAccess() {
                return this;
            }

            @Override
            public SegmentTable<Point> create(SegmentAllocator allocator, long rows) {
                return segmentTableOfPoint(layout, rows, createSegments(allocator, rows));
            }

            @Override
            public SegmentTable<Point> create(SegmentAllocator allocator, Stream<? extends Point> elements) {
                ValueLayout.OfInt first = (ValueLayout.OfInt) layout.select(MemoryLayout.PathElement.groupElement("x"));
                ValueLayout.OfInt second = (ValueLayout.OfInt) layout.select(MemoryLayout.PathElement.groupElement("y"));
                // Materialize the stream. This is cheating...
                List<? extends Point> points = elements.toList();
                MemorySegment[] segments = createSegments(allocator, points.size());
                for (int i = 0; i < points.size(); i++) {
                    Point p = points.get(i);
                    segments[0].setAtIndex(first, i, p.x());
                    segments[1].setAtIndex(second, i, p.y());
                }
                return segmentTableOfPoint(layout, points.size(), segments);
            }

            @Override
            public SegmentTable<Point> create(Map<List<MemoryLayout.PathElement>, MemorySegment> map) {
                if (map.size() != layout.memberLayouts().size()) {
                    throw new IllegalArgumentException("Missing segment");
                }
                MemorySegment[] segments = new MemorySegment[2];
                map.forEach((key, value) -> {
                    MemoryLayout element = layout.select(key.toArray(MemoryLayout.PathElement[]::new));
                    String name= element.name().orElseThrow();
                    switch (name) {
                        case "x" -> segments[0] = value;
                        case "y" -> segments[1] = value;
                        default -> throw new IllegalArgumentException("Unknown element: " + name);
                    }
                });

                return segmentTableOfPoint(layout, segments[0].byteSize() / JAVA_INT.byteSize(), segments);
            }

            private MemorySegment[] createSegments(SegmentAllocator allocator, long rows) {
                return layout.memberLayouts().stream()
                        .map(l -> allocator.allocate(l, l.byteSize() * rows))
                        .toArray(MemorySegment[]::new);
            }

            @Override
            public SegmentTable<Point> create(Function<List<MemoryLayout.PathElement>, MemorySegment> segmentFactory) {
                MemorySegment[] segments = layout.memberLayouts().stream()
                        .map(MemoryLayout::name)
                        .map(Optional::orElseThrow)
                        .map(n -> {
                            MemoryLayout.PathElement pathElement = MemoryLayout.PathElement.groupElement(n);
                            List<MemoryLayout.PathElement> list = List.of(pathElement);
                            MemorySegment segment = segmentFactory.apply(list);
                            if (segment == null) {
                                throw new NullPointerException("Unable to lookup " + n + " using " + segmentFactory);
                            }
                            return segment;
                        })
                        .toArray(MemorySegment[]::new);

                return segmentTableOfPoint(layout, segments[0].byteSize() / JAVA_INT.byteSize(), segments);
            }
        };
    }

    static SegmentTable<Point> segmentTableOfPoint(GroupLayout layout,
                                                   long size,
                                                   MemorySegment[] segments) {

        // Check invariants such as segment lengths

        return new SegmentTable<Point>() {

            private final ValueLayout.OfInt xLayout =
                    (ValueLayout.OfInt) layout.select(MemoryLayout.PathElement.groupElement("x"));
            private final ValueLayout.OfInt yLayout =
                    (ValueLayout.OfInt) layout.select(MemoryLayout.PathElement.groupElement("y"));

            @Override
            public Class<Point> type() {
                return Point.class;
            }

            @Override
            public GroupLayout layout() {
                return layout;
            }

            @Override
            public long size() {
                return size;
            }

            @Override
            public Point get(long index) {
                return new PointImpl(segments, index);
            }

            @Override
            public void set(long index, Point point) {
                segments[0].setAtIndex(xLayout, index, point.x());
                segments[1].setAtIndex(yLayout, index, point.y());
            }

            @Override
            public Map<List<MemoryLayout.PathElement>, MemorySegment> segments() {
                return Map.of(List.of(MemoryLayout.PathElement.groupElement("x")), segments[0],
                        List.of(MemoryLayout.PathElement.groupElement("y")), segments[1]);
            }

            // Generated bytecode
            @ValueBased
            // PointImpl.class.isHidden() == true
            private static final class PointImpl implements Point {

                private final MemorySegment[] segments;
                private final long index;

                public PointImpl(MemorySegment[] segments, long index) {
                    this.segments = segments;
                    this.index = index;
                }

                @Override
                public int x() {
                    return (int) JAVA_INT.varHandle().get(segments[0], index * 4);
                }

                @Override
                public int y() {
                    return (int) JAVA_INT.varHandle().get(segments[1], index * 4);
                }

                @Override
                public String toString() {
                    return "Point[x()=" + x() + ", y()=" + y() + ']';
                }

                @Override
                public boolean equals(Object obj) {
                    return obj instanceof Point other &&
                            this.x() == other.x() &&
                            this.y() == other.y();
                }

                @Override
                public int hashCode() {
                    return Objects.hash(x(), y());
                }
            }

        };
    }

    // Enables capturing of output for the tests

    private static final String NL = System.lineSeparator();

    private StringBuilder sb;

    @BeforeEach
    void setup() {
        sb = new StringBuilder();
    }

    private void println(Object line) {
        sb.append(line).append(NL);
    }

    private String lines() {
        return sb.toString();
    }


    // Support methods

    private static MemorySegment newCopyOf(MemorySegment source) {
        return Arena.ofAuto()
                .allocate(source.byteSize())
                .copyFrom(source);
    }

}
