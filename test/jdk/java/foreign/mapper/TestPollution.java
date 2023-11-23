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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestPollution
 */
import org.junit.jupiter.api.Test;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.stream.IntStream;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

public final class TestPollution {

    public static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y"));

    public record Point(int x, int y){}
    public record Points(List<Point> points) {}

    @Test
    void reproduce() throws Throwable {
        var ctor = MethodHandles.publicLookup().findConstructor(Points.class,
                MethodType.methodType(void.class, List.class));

        System.out.println("ctor = " + ctor);
        ctor = ctor.asType(MethodType.methodType(Object.class, Point[].class));
        System.out.println("ctor = " + ctor);

        var identity = MethodHandles.publicLookup().findStatic(TestPollution.class, "identity", MethodType.methodType(Object.class, Object.class));
        identity = identity.asType(MethodType.methodType(Point[].class ,Object.class));
        ctor = MethodHandles.filterArguments(ctor, 0, identity);

        Object o =  ctor.invokeExact((Object) twoPoints());
        System.out.println("o = " + o);

        Points s = (Points) o;
        assertTrue(List.class.isAssignableFrom(s.points().getClass()));
    }

    @Test
    public void reproduce2() {

        var segment = MemorySegment.ofArray(IntStream.rangeClosed(0, 3).toArray());

        var layout = MemoryLayout.structLayout(
                MemoryLayout.sequenceLayout(2, POINT_LAYOUT).withName("points")
        );

        var mapper = SegmentMapper.ofRecord(Points.class, layout);

        System.out.println("mapper.getHandle() = " + mapper.getHandle());

        Points sequenceOfPoints = mapper.get(segment);

        Object o = sequenceOfPoints.points();
        System.out.println("o = " + o);
        System.out.println("o.getClass().isArray() = " + o.getClass().isArray());
        System.out.println("o.getClass().componentType() = " + o.getClass().componentType());

        assertEquals(new Points(List.of(new Point(1, 2), new Point(3,4))), sequenceOfPoints);
    }

    public static <T> T identity(T t) {
        return t;
    }

    public static Point aPoint() {
        return new Point(3, 4);
    }

    public static Point[] twoPoints() {
        return new Point[]{aPoint(), aPoint()};
    }

}
