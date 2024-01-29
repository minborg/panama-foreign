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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestJepExamplesComposition
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

final class TestJepExamplesComposition {


    private static final
    StructLayout POINT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );

    public record Point(int x, int y) {
    }

    static MethodHandle makePointHandle() throws NoSuchMethodException, IllegalAccessException {
        return MethodHandles.lookup()
                .findStatic(TestJepExamplesComposition.class,
                        "makePoint",
                        MethodType.methodType(MemorySegment.class, int.class, int.class));
    }

    static MemorySegment makePoint(int x, int y) {
        return MemorySegment.ofArray(new int[]{x, y});
    }


    @Test
    void composition() throws Throwable {
        //Linker linker = Linker.nativeLinker();
        //MemorySegment makePointAddr = SymbolLookup.loaderLookup().find("makePoint").get();
        //MethodHandle makePoint = linker.downcallHandle(makePointAddr,
        //        FunctionDescriptor(POINT, JAVA_INT, JAVA_INT));

        MethodHandle makePoint = makePointHandle();

        MemorySegment point = (MemorySegment)makePoint.invokeExact(1, 2);
    }

    @Test
    void composition2() throws Throwable {
        //Linker linker = Linker.nativeLinker();
        //MemorySegment makePointAddr = SymbolLookup.loaderLookup().find("makePoint").get();
        //MethodHandle makePoint = linker.downcallHandle(makePointAddr,
        //        FunctionDescriptor(POINT, JAVA_INT, JAVA_INT));

        MethodHandle makePoint = makePointHandle();

        record Point(int x, int y) { }
        SegmentMapper<Point> mapper = SegmentMapper.ofRecord(MethodHandles.lookup(), Point.class, POINT);
        MethodHandle segmentToPoint = MethodHandles.insertArguments(mapper.getHandle(), 1, 0L);
        makePoint = MethodHandles.filterReturnValue(makePoint, segmentToPoint);

        Point point = (Point)
                (Object) // Todo: remove this cast
                makePoint.invokeExact(1, 2); // Point[x=1, y=2]

        assertEquals(new Point(1, 2), point);
    }

    interface PointAccessor {
        int x();
        int y();
    }

    @Test
    void compositionOld() throws Throwable {
        MemorySegment segment = Arena.ofAuto().allocateFrom(JAVA_INT, 3, 4);

        SegmentMapper<Point> mapper = SegmentMapper.ofRecord(Point.class, POINT);

        // Method handle for the `get` operation from a SegmentMapper<Point>
        // (MemorySegment segment, long offset)Point
        MethodHandle pointGetHandle = mapper.getHandle();

        // Temp Fix
        pointGetHandle = pointGetHandle.asType(pointGetHandle.type().changeReturnType(Point.class));


        // Method handle for the `get(JAVA_INT, ...)` operation from a MemorySegment
        // (MemorySegment segment, long offset)int
        MethodHandle intGetHandle = MethodHandles.publicLookup().findVirtual(
                MemorySegment.class,
                "get",
                MethodType.methodType(int.class, ValueLayout.OfInt.class, long.class));

        intGetHandle = MethodHandles.insertArguments(intGetHandle, 1, JAVA_INT);

        record MixedBag(int firstInt, Point p){}

        // (int, Point)MixedBag
        MethodHandle ctor = MethodHandles.lookup()
                .findConstructor(
                        MixedBag.class,
                        MethodType.methodType(void.class, int.class, Point.class));

        // (int, MemorySegment, long)MixedBag
        ctor = MethodHandles.collectArguments(ctor, 1, pointGetHandle);
        // (MemorySegment, long, MemorySegment, long)MixedBag
        ctor = MethodHandles.collectArguments(ctor, 0, intGetHandle);

        // Fold the identical (MemorySegment, long) arguments into a single argument
        // (MemorySegment, long)MixedBag
        ctor = MethodHandles.permuteArguments(
                ctor,
                MethodType.methodType(MixedBag.class, MemorySegment.class, long.class),
                0, 1, 0, 1);

        MixedBag mixedBag = (MixedBag)ctor.invokeExact(segment, 0L);
        // MixedBag[firstInt=3, p=Point[x=3, y=4]]

        assertEquals(new MixedBag(3, new Point(3, 4)), mixedBag);
    }

}
