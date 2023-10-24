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
 * @library ../
 * @build NativeTestHelper
 * @run junit/othervm TestMapping
 */

import static java.lang.foreign.ValueLayout.JAVA_INT;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.function.Function;

public class TestMapping extends NativeTestHelper {

    static {
        System.loadLibrary("GraphicsDemo");
    }

    /**
     * Given these C definitions in libGraphicsDemo.c:
     *
     * typedef struct {
     *     int x;
     *     int y;
     * } Point;
     *
     * const Point ORIGIN     = { .x = 0, .y = 0 };
     *
     * EXPORT Point origin(){
     *     return ORIGIN;
     * }
     *
     */

    static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );

    public record Point(int x, int y) {}

    public record Line(Point begin, Point end) {}

    public interface Marker {
        default void greeting() {
            System.out.println("Marker says hello");
        }
    }

    @FunctionalInterface
    public interface A extends Marker {
        int getpid();
    }

    @Test
    public void testPidSimple() {
        A a = LinkerAdditions.downcall(A.class);

        System.out.println("** SIMPLE **");

        System.out.println("a = " + a);

        int pid = a.getpid();
        System.out.println("pid = " + pid);

        System.out.println("a.getClass() = " + a.getClass());
        System.out.println("a.toString() = " + a.toString());
        System.out.println("a.hashCode() = " + a.hashCode());
        System.out.println("a.equals(\"Olle\") = " + a.equals("Olle"));

        a.greeting();
    }

    @Test
    public void testPid() {
        MemorySegment address = Linker.nativeLinker().defaultLookup().find("getpid").orElseThrow();
        MethodType methodType = MethodType.methodType(int.class);
        A a = LinkerAdditions.downcall(A.class, methodType, address, FunctionDescriptor.of(JAVA_INT));

        System.out.println("** NORMAL **");

        System.out.println("a = " + a);

        int pid = a.getpid();
        System.out.println("pid = " + pid);

        System.out.println("a.getClass() = " + a.getClass());
        System.out.println("a.toString() = " + a.toString());
        System.out.println("a.hashCode() = " + a.hashCode());
        System.out.println("a.equals(\"Olle\") = " + a.equals("Olle"));

        a.greeting();

        throw new AssertionError();
    }

    //@Test
    public void testOrigin() {

        @FunctionalInterface
        interface Origin {  Point origin(); }

        Origin downcall = LinkerAdditions.downcall(Origin.class, FunctionDescriptor.of(POINT_LAYOUT));

        Point expected = new Point(0, 0);

        Point actual = downcall.origin();

        assertEquals(expected, actual);
    }


    //@Test
    public void testOrigin2() {

        @FunctionalInterface
        interface Origin {  Point origin(); }

        Origin downcall = LinkerAdditions.downcall(Origin.class, FunctionDescriptor.of(POINT_LAYOUT));

        Point expected = new Point(0, 0);

        Point actual = downcall.origin();

        assertEquals(expected, actual);
    }

    //@Test
    public void testLine() {

        @FunctionalInterface
        interface CreateLine { Line createLine(Point begin, Point end); }


        @FunctionalInterface
        interface CreateLineVirtual {
            Line createLine(MemorySegment address, Point begin, Point end);
        }

        Optional<MemorySegment> createLine = Linker.nativeLinker().defaultLookup().find("create_line");

        CreateLine downcall = LinkerAdditions.downcall(CreateLine.class,
                FunctionDescriptor.of(GeneratedCode.LINE_LAYOUT, POINT_LAYOUT, POINT_LAYOUT));

        Line expected = new Line(new Point(1, 1), new Point(10, 5));

        Line actual = downcall.createLine(new Point(1, 1), new Point(10, 5));

        assertEquals(expected, actual);
    }

    //@Test
    public void function() {

/*        @FunctionalInterface
        interface Mapper<T> {
            Function<Integer, T> mapper();
        }*/

        // Function<Integer, Long> fun(char* prt);

        // MethodType.methodType(....); // Covers generics

        var mapper = LinkerAdditions.downcall(Function.class,
                FunctionDescriptor.of(GeneratedCode.LINE_LAYOUT, POINT_LAYOUT, POINT_LAYOUT));

        var f = (Function<String, String>)String::toLowerCase;


    }



}
