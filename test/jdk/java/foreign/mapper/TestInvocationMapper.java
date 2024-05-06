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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestInvocationMapper
 */

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.mapper.PrimitiveMapper;
import java.lang.foreign.mapper.RecordMapper;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.function.Consumer;

import static java.lang.foreign.ValueLayout.*;

final class TestInvocationMapper {

    @FunctionalInterface
    interface StrLen {
        long strlen(MemorySegment segment);

        // Explicit allocation defined by the client
        default long strlen(String string) {
            try (Arena arena = Arena.ofConfined()) {
                return strlen(arena.allocateFrom(string));
            }
        }
    }

    @Test
    void strLen() {
        StrLen strLen = SegmentMapper.downcall(FunctionDescriptor.of(JAVA_LONG, ADDRESS), StrLen.class);
        long len = strLen.strlen("abc"); // 3

        // Sketch of under-the-hood machinery (will not work for real)
        Method method = StrLen.class.getDeclaredMethods()[0];// Finds the one and only abstract method
        String name = method.getName();
        MemoryLayout returnType = valueLayoutFor(method.getReturnType());
        MemoryLayout[] parameters = Arrays.stream(method.getParameters())
                .map(Parameter::getType)
                .map(TestInvocationMapper::valueLayoutFor)
                .toArray(MemoryLayout[]::new);

        Linker linker = Linker.nativeLinker();
        MethodHandle handle = linker.downcallHandle(
                linker.defaultLookup().find(name).orElseThrow(),
                FunctionDescriptor.of(returnType, parameters)
        );

        StrLen strLen0 = MethodHandleProxies.asInterfaceInstance(StrLen.class, handle);

        // Custom parameters using a builder pattern
/*
        StrLen strLen1 = InvocationMapper.builder(StrLen.class)
                .withLinker(Linker.nativeLinker())
                .withLookup(Linker::defaultLookup) // ???
                .withName("strlen")
                .withDescription(FunctionDescriptor.of(JAVA_LONG, ADDRESS))
                .build();
*/


        // Custom parameters using "Derived Record Creation"
        // StrLen strLen3 = InvocationMapper.of(new Config<>(StrLen.class) with {name = "slen"});

    }

    static ValueLayout valueLayoutFor(Class<?> type) {
        return null;
    }


    @FunctionalInterface
    interface StrLenAsInt {
        int strlen(MemorySegment segment);

        // Explicit allocation defined by the client
        default int strlen(String string) {
            try (Arena arena = Arena.ofConfined()) {
                return strlen(arena.allocateFrom(string));
            }
        }
    }

    @Test
    void strLenAsInt() {
        StrLenAsInt strLenAsInt = SegmentMapper.downcall(FunctionDescriptor.of(JAVA_LONG, ADDRESS), StrLenAsInt.class);

        // This will automatically apply
        PrimitiveMapper<Integer> mapper = PrimitiveMapper.of(JAVA_LONG, int.class);

        int len = strLenAsInt.strlen("abc"); // 3
    }

    @FunctionalInterface
    interface Malloc {
        MemorySegment malloc(long byteSize);
    }

    @FunctionalInterface
    interface Free {
        void free(long address);
    }

    interface MemoryOps extends Malloc, Free {

        default MemorySegment malloc(long byteSize, Arena arena) {
            MemorySegment segment = malloc(byteSize);
            return segment.reinterpret(byteSize, arena, s -> {
                try {
                    free(s.address());
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });  // size = byteSize, scope = arena.scope()
        }

        static MemoryOps of(Malloc malloc, Free free) {
            return new MemoryOpsImpl(malloc, free);
        }
    }

    record MemoryOpsImpl(Malloc malloc, Free free) implements MemoryOps {

        @Override
        public MemorySegment malloc(long byteSize) {
            return malloc.malloc(byteSize);
        }

        @Override
        public void free(long address) {
            free.free(address);
        }
    }

    @Test
    void memoryOps() {
        MemoryOps memory = MemoryOps.of(
                SegmentMapper.downcall(FunctionDescriptor.of(ADDRESS, JAVA_LONG), Malloc.class),
                SegmentMapper.downcall(FunctionDescriptor.ofVoid(JAVA_LONG), Free.class)
        );

        try (Arena arena = Arena.ofConfined()) {
            var segment = memory.malloc(16, arena);
        } // free() is invoked here
    }

    @Test
    void memoryCustom() {
        Malloc malloc = SegmentMapper.downcall(FunctionDescriptor.of(ADDRESS, JAVA_LONG), Malloc.class);
        Malloc mallocDebug = byteSize -> {
            System.out.println("byteSize = " + byteSize);
            return malloc.malloc(byteSize);
        };
        Free free = SegmentMapper.downcall(FunctionDescriptor.ofVoid(JAVA_LONG), Free.class);
        MemoryOps memory = MemoryOps.of(mallocDebug, free);
        try (Arena arena = Arena.ofConfined()) {
            var segment = memory.malloc(16, arena); // prints "byteSize = 16"
        } // free() is invoked here
    }


    public record Point(int x, int y){}
    private static  final GroupLayout POINT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y"));

    @FunctionalInterface
    interface Move {
        Point move(Point original, int deltaX, int deltaY);
    }

    @FunctionalInterface
    interface Distance {
        double distance(Point first, Point second);
    }

    interface PointLib extends Move, Distance {}

    record PointLibImpl(Move move, Distance distance) implements PointLib {

        @Override
        public Point move(Point original, int deltaX, int deltaY) {
            return move.move(original, deltaX, deltaY);
        }

        @Override
        public double distance(Point first, Point second) {
            return distance.distance(first, second);
        }
    }

    @Test
    void point() {
        PointLib pointLib = new PointLibImpl(
                SegmentMapper.downcall(FunctionDescriptor.of(POINT, POINT, JAVA_INT, JAVA_INT), Move.class),
                SegmentMapper.downcall(FunctionDescriptor.of(JAVA_DOUBLE, POINT, POINT), Distance.class)
        );

        Point origin = new Point(0, 0);
        Point unit = pointLib.move(origin, 1, 1); // Point[x=1, y=2]
        double dist = pointLib.distance(origin, unit); // sqrt(2)
    }


    interface PointLib2 {

        private RecordMapper<Point> mapper() {
            class Holder {
                static final RecordMapper<Point> MAPPER = RecordMapper.ofRecord(Point.class);
            }
            return Holder.MAPPER;
        }

        // Generated
        default Point move(Point original, int deltaX, int deltaY) {
            // Heap allocation?

            try (Arena arena = Arena.ofConfined()){
                MemorySegment segment = arena.allocateFrom(mapper(), original);
                return mapper().get(
                        move(segment, deltaX, deltaY));
            }
        }

        MemorySegment move(MemorySegment originalPtr, int deltaX, int deltaY);

        default double distance(Point first, Point second) {
            try (Arena arena = Arena.ofConfined()){
                MemorySegment segmentFirst = arena.allocateFrom(mapper(), first);
                MemorySegment segmentSecond = arena.allocateFrom(mapper(), second);
                return distance(segmentFirst, segmentSecond);
            }
        }

        double distance(MemorySegment firstPtr, MemorySegment secondPtr);

    }

    // errno

    record CaptureErrNo(int errno){}
    record CaptureGetLastError(int GetLastError){}
    static class CaptureException extends Exception {}

    static class ErrNoException extends Exception {

        private final int errNo;

        public ErrNoException(CaptureErrNo captureErrNo) {
            this.errNo = captureErrNo.errno();
        }

        int errNo() {
            return errNo;
        }

    }

    static class LastErrorException extends Exception {

        private final int lastError;

        public LastErrorException(CaptureGetLastError captureGetLastError) {
            this.lastError = captureGetLastError.GetLastError();
        }

        public int lastError() {
            return lastError;
        }
    }


    //record ErrNo<T>(T value, int errNo){};

    // User defined
    record ThreadLocalError(int value){};

    interface SystemCalls /* stuff here not shown */ {

        // https://man7.org/linux/man-pages/man2/socket.2.html

        // Declaring a method throws ErrNoException means Linker.Option.captureCallState("errno")
        // will be added by the Linker. Allocation will be made on-heap
        int socket(Consumer<ThreadLocalError> handler, int domain, int type, int protocol) throws ErrNoException;

        int open(MemorySegment pathname, int flags) throws ErrNoException;

        int socket(int domain, int type, int protocol) throws LastErrorException;


        // The "allocation problem"
        default int open(String pathname, int flags, Arena arena) throws ErrNoException {
            return open(arena.allocateFrom(pathname), flags);
        }

        default int open(String pathname, int flags) throws ErrNoException {
            try (var arena = Arena.ofConfined()) {
                return open(pathname, flags, arena);
            }
        }

        int close(int fildes) throws ErrNoException;

    }

    interface BaseSystemCalls<X extends Exception> {
        int socket(Consumer<ThreadLocalError> handler, int domain, int type, int protocol) throws X;

        int open(MemorySegment pathname, int flags) throws X;
    }

    interface WindowsSystemCalls extends BaseSystemCalls<LastErrorException> {}
    interface UnixSystemCalls extends BaseSystemCalls<ErrNoException> {}


    @FunctionalInterface
    interface CaptureHandler<T extends Record, R> {
        R apply(int result, T capture);
    }

    @FunctionalInterface
    interface ThrowingCaptureHandler<T extends Record, X extends Exception> {
        int apply(int result, T capture) throws X;
    }

    record ResultAndErrno(int result, int errno){}

    @FunctionalInterface
    interface MyCaptureHandler {
        ResultAndErrno apply(int result, CaptureErrNo capture);
    }

    @FunctionalInterface
    interface MyThrowingCaptureHandler {
        Integer apply(int result, CaptureErrNo capture) throws IOException;
    }

    interface SystemCalls3 /* stuff here not shown */ {

        // https://man7.org/linux/man-pages/man2/socket.2.html

        // Declaring a method throws ErrNoException means Linker.Option.captureCallState("errno")
        // will be added by the Linker. Allocation will be made on-heap
        ResultAndErrno socket(MyCaptureHandler handler, int domain, int type, int protocol);

        int socket(MyThrowingCaptureHandler handler, int domain, int type, int protocol) throws IOException;


        int open(MemorySegment pathname, int flags) throws ErrNoException;

        int socket(int domain, int type, int protocol) throws LastErrorException;


        // The "allocation problem"
        default int open(String pathname, int flags, Arena arena) throws ErrNoException {
            return open(arena.allocateFrom(pathname), flags);
        }

        default int open(String pathname, int flags) throws ErrNoException {
            try (var arena = Arena.ofConfined()) {
                return open(pathname, flags, arena);
            }
        }

        int close(int fildes) throws ErrNoException;

    }

    @Test
    void syscalls3() {

        CaptureHandler<CaptureErrNo, ResultAndErrno> wrapperHandler = (result, capture) -> result == 0
                ? new ResultAndErrno(result, 0)
                : new ResultAndErrno(result, capture.errno());

        ThrowingCaptureHandler<CaptureErrNo, IOException> throwerHandler = (result, capture) -> {
            if (result == 0) {
                return result;
            }
            throw new IOException();
        };


    }


    record ReturnComposite(int result, int errno){}


    static final SystemCalls SYSTEM_CALLS = null;

    @Test
    void syscalls() {

        // How to handle error no?
        try {
            long fh = SYSTEM_CALLS.open("myFile", 0);
        } catch (ErrNoException e) {
            if (e.errNo() == 42) {
                // Do some magic here
            }
        }
    }


    private static final ThreadLocal<ThreadLocalError> TL = new ThreadLocal<>();

    static int errno() {
        return TL.get().value();
    }

    @Test
    void syscalls2() {
        // How to handle error no?
        // long fh = SYSTEM_CALLS.socket(TL::set, 0, 0, 1);
        if (errno() != 0) {
            // act...
        }
    }

}
