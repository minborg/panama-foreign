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

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * This class contains constructs possible to generate by an external tool such as jextract.
 */
public final class GeneratedCode {

    private GeneratedCode() {
    }

    // Layouts

    /*
     * typedef struct {
     *     int x;
     *     int y;
     * } Point;
     */
    public static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );

    /*
     * typedef struct {
     *     Point begin;
     *     Point end;
     * } Line;
     */
    public static final GroupLayout LINE_LAYOUT = MemoryLayout.structLayout(
            POINT_LAYOUT.withName("begin"),
            POINT_LAYOUT.withName("end")
    );

    // Records

    public record Point(int x, int y) {
    }

    public record Line(Point begin, Point end) {
    }

    // Interfaces

    @FunctionalInterface
    public interface Origin {

        Point origin();

    }

    @FunctionalInterface
    public interface UnitPoint {

        Point unitPoint();

    }

    @FunctionalInterface
    public interface CreatePoint {

        Point createPoint(int x, int y);

    }

    @FunctionalInterface
    public interface CreateLine {

        Line createLine(Point begin, Point end);

    }

    @FunctionalInterface
    public interface AddPoint {

        Point addPoint(Point first, Point second);

    }

    @FunctionalInterface
    public interface ToStingPoint {

        String toStringPoint(Point point);

    }

    // Composed interfaces.  Is this something we'd like to support?

    public interface PointOperations extends
            Origin,
            UnitPoint,
            CreatePoint,
            CreateLine,
            AddPoint,
            ToStingPoint {
    }

    // Variants

    @FunctionalInterface
    public interface OriginAsSegment {

        MemorySegment origin();

    }

    @FunctionalInterface
    public interface CreateLineAsSegmentWithSegments {

        MemorySegment createLine(MemorySegment begin, MemorySegment end);

    }


}
