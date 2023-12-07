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
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.impl
 * @run junit/othervm --enable-preview TestInterfaceMapper
 */

import jdk.internal.classfile.AccessFlags;
import jdk.internal.classfile.AttributeMapper;
import jdk.internal.classfile.BootstrapMethodEntry;
import jdk.internal.classfile.ClassHierarchyResolver;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.CodeModel;
import jdk.internal.classfile.FieldModel;
import jdk.internal.classfile.Instruction;
import jdk.internal.classfile.Label;
import jdk.internal.classfile.MethodElement;
import jdk.internal.classfile.MethodModel;
import jdk.internal.classfile.attribute.MethodParameterInfo;
import jdk.internal.classfile.attribute.MethodParametersAttribute;
import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.constantpool.ConstantPool;
import jdk.internal.classfile.constantpool.DoubleEntry;
import jdk.internal.classfile.constantpool.DynamicConstantPoolEntry;
import jdk.internal.classfile.constantpool.FieldRefEntry;
import jdk.internal.classfile.constantpool.FloatEntry;
import jdk.internal.classfile.constantpool.IntegerEntry;
import jdk.internal.classfile.constantpool.InterfaceMethodRefEntry;
import jdk.internal.classfile.constantpool.LoadableConstantEntry;
import jdk.internal.classfile.constantpool.LongEntry;
import jdk.internal.classfile.constantpool.MethodHandleEntry;
import jdk.internal.classfile.constantpool.MethodRefEntry;
import jdk.internal.classfile.constantpool.ModuleEntry;
import jdk.internal.classfile.constantpool.NameAndTypeEntry;
import jdk.internal.classfile.constantpool.PackageEntry;
import jdk.internal.classfile.constantpool.PoolEntry;
import jdk.internal.classfile.constantpool.StringEntry;
import jdk.internal.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.classfile.instruction.ConstantInstruction;
import jdk.internal.classfile.instruction.FieldInstruction;
import jdk.internal.classfile.instruction.InvokeDynamicInstruction;
import jdk.internal.classfile.instruction.InvokeInstruction;
import jdk.internal.classfile.instruction.LineNumber;
import jdk.internal.classfile.instruction.LoadInstruction;
import jdk.internal.classfile.instruction.NopInstruction;
import jdk.internal.classfile.instruction.ReturnInstruction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.mapper.SegmentBacked;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatFactory;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.foreign.ValueLayout.*;
import static java.util.stream.Collectors.joining;
import static jdk.internal.classfile.Classfile.*;
import static org.junit.jupiter.api.Assertions.*;

// Todo: check wrapper classes

// Note: the order in which interface methods appears is unspecified.
final class TestInterfaceMapper {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final double EPSILON = 1e-6;

    private static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );

    @Test
    void point() {
        SegmentMapper<PointAccessor> mapper = SegmentMapper.ofInterface(LOOKUP, PointAccessor.class, POINT_LAYOUT);
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});
        PointAccessor accessor = mapper.get(segment, POINT_LAYOUT.byteSize());
        {
            assertEquals(6, accessor.x());
            assertEquals(8, accessor.y());
            assertToString(accessor, PointAccessor.class, Set.of("x()=6", "y()=8"));
        }
        accessor.x(1);
        accessor.y(2);
        {
            assertEquals(1, accessor.x());
            assertEquals(1, segment.getAtIndex(JAVA_INT, 2));
            assertEquals(2, accessor.y());
            assertEquals(2, segment.getAtIndex(JAVA_INT, 3));
            assertToString(accessor, mapper.type(), Set.of("x()=1", "y()=2"));
        }
    }

    GroupLayout MIXED_LAYOUT = MemoryLayout.structLayout(
            JAVA_LONG.withName("l"),
            JAVA_DOUBLE.withName("d"),
            JAVA_INT.withName("i"),
            JAVA_FLOAT.withName("f"),
            JAVA_CHAR.withName("c"),
            JAVA_SHORT.withName("s"),
            JAVA_BYTE.withName("b")
    );

    interface MixedBag {
        byte b();
        short s();
        char c();
        int i();
        float f();
        long l();
        double d();

        void b(byte b);
        void s(short s);
        void c(char c);
        void i(int i);
        void f(float f);
        void l(long l);
        void d(double d);
    }

    @Test
    void mixedBag() {
        SegmentMapper<MixedBag> mapper = SegmentMapper.ofInterface(LOOKUP, MixedBag.class, MIXED_LAYOUT);
        try (var arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(MIXED_LAYOUT);

            MIXED_LAYOUT.varHandle(PathElement.groupElement("l")).set(segment, 0, 42L);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("d")).set(segment, 0, 123.45d);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("i")).set(segment, 0, 13);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("f")).set(segment, 0, 3.1415f);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("c")).set(segment, 0, 'B');
            MIXED_LAYOUT.varHandle(PathElement.groupElement("s")).set(segment, 0, (short) 32767);
            MIXED_LAYOUT.varHandle(PathElement.groupElement("b")).set(segment, 0, (byte) 127);

            MixedBag accessor = mapper.get(segment);

            assertEquals(42L, accessor.l());
            assertEquals(123.45d, accessor.d(), EPSILON);
            assertEquals(13, accessor.i());
            assertEquals(3.1415f, accessor.f(), EPSILON);
            assertEquals('B', accessor.c());
            assertEquals((short) 32767, accessor.s());
            assertEquals((byte) 127, accessor.b());

            Set<String> set = Arrays.stream("i()=13, b()=127, s()=32767, c()=B, f()=3.1415, l()=42, d()=123.4".split(", "))
                    .collect(Collectors.toSet());
            assertToString(accessor, MixedBag.class, set);

            accessor.b((byte) (accessor.b() - 1));
            accessor.s((short) (accessor.s() - 1));
            accessor.c((char) (accessor.c() - 1));
            accessor.i(accessor.i() - 1);
            accessor.f(accessor.f() - 1);
            accessor.l(accessor.l() - 1);
            accessor.d(accessor.d() - 1);

            Set<String> set2 = Arrays.stream("i()=12, b()=126, s()=32766, c()=A, f()=2.1415, l()=41, d()=122.4".split(", "))
                    .collect(Collectors.toSet());
            assertToString(accessor, mapper.type(), set2);
        }
    }

    private static final GroupLayout LINE_LAYOUT = MemoryLayout.structLayout(
            POINT_LAYOUT.withName("begin"),
            POINT_LAYOUT.withName("end")
    );

    interface XAccessor { int x();}
    interface YAccessor { int y();}
    interface XYAccessor extends XAccessor, YAccessor {};

    @Test
    void xyAccessor() {
        SegmentMapper<XYAccessor> mapper = SegmentMapper.ofInterface(LOOKUP, XYAccessor.class, POINT_LAYOUT);
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});
        XYAccessor accessor = mapper.get(segment, 0);
        assertEquals(3, accessor.x());
        assertEquals(4, accessor.y());
        assertToString(accessor, mapper.type(), Set.of("x()=3", "y()=4"));
    }

    interface LineAccessor {
        PointAccessor begin();
        PointAccessor end();
    }

    @Test
    void line() {
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});

        SegmentMapper<LineAccessor> mapper = SegmentMapper.ofInterface(LOOKUP, LineAccessor.class, LINE_LAYOUT);
        LineAccessor accessor = mapper.get(segment);

        var begin = accessor.begin();
        var end = accessor.end();

        assertEquals(3, begin.x());
        assertEquals(4, begin.y());
        assertEquals(6, end.x());
        assertEquals(8, end.y());

        assertToString(accessor, mapper.type(), Set.of("begin()=PointAccessor[", "end()=PointAccessor["));
    }

    interface Empty {
    }

    @Test
    void empty() {
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});

        SegmentMapper<Empty> mapper = SegmentMapper.ofInterface(LOOKUP, Empty.class, LINE_LAYOUT);
        Empty accessor = mapper.get(segment);

        assertToString(accessor, mapper.type(), Set.of());
    }

    interface Fail1 {
        // Setters of accessor not allowed
        void begin(PointAccessor pointAccessor);
    }

    @Test
    void fail1() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                SegmentMapper.ofInterface(LOOKUP, Fail1.class, LINE_LAYOUT)
        );
        var message = e.getMessage();

        assertTrue(message.startsWith("Setters cannot take an interface as a parameter: "));
        assertTrue(message.contains(Fail1.class.getMethods()[0].toString()));
    }

    interface Fail2 {
        // Only one parameter is allowed
        void x(int i, Object o);
    }

    @Test
    void fail2() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                SegmentMapper.ofInterface(LOOKUP, Fail2.class, POINT_LAYOUT)
        );
        var message = e.getMessage();
        assertTrue(message.contains("Unable to map methods: ["));
        assertTrue(message.contains(Fail2.class.getMethods()[0].toString()));
    }

    public record Point(int x, int y){}

    public interface LineRecordAccessor {
        Point begin();
        Point end();
        void begin(Point begin);
        void end(Point end);
    }

    @Test
    void mapToRecord() {
        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4, 6, 8});

        SegmentMapper<LineRecordAccessor> mapper = SegmentMapper.ofInterface(LOOKUP, LineRecordAccessor.class, LINE_LAYOUT);
        LineRecordAccessor accessor = mapper.get(segment);

        Point begin = accessor.begin();
        System.out.println("begin = " + begin);
        Point end = accessor.end();
        System.out.println("end = " + end);

        assertEquals(3, begin.x());
        assertEquals(4, begin.y());
        assertEquals(6, end.x());
        assertEquals(8, end.y());

        // Records have a deterministic order
        assertToString(accessor, mapper.type(), Set.of("begin()=Point[x=3, y=4]", "end()=Point[x=6, y=8]"));

        accessor.begin(new Point(1, 2));
        accessor.end(new Point(3, 4));

        assertEquals(1, begin.x());
        assertEquals(2, begin.y());
        assertEquals(3, end.x());
        assertEquals(4, end.y());

        assertToString(accessor, mapper.type(), Set.of("begin()=Point[x=1, y=2]", "end()=Point[x=3, y=4]"));
    }

    void assertToString(Object o,
                        Class<?> clazz, Set<String> fragments) {
        String s = o.toString();
        var start = clazz.getSimpleName() + "[";
        assertTrue(s.startsWith(start), s + " does not start with " + start);
        for (var fragment:fragments) {
            assertTrue(s.contains(fragment), s + " does not contain " + fragment);
        }
        var end = "]";
        assertTrue(s.endsWith(end), s + " does not end with " + end);
    }

    //@Test
    void fromFile() throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("/Users/pminborg/dev/minborg-panama/open/test/jdk/java/foreign/mapper/TestInterfaceMapper$PointAccessorImpl.class"));
        ClassModel cm = Classfile.of().parse(bytes);
        javap(cm);
        fail();
    }

    //@Test
    void fromModel() throws IOException {

        ClassDesc genClassDesc = ClassDesc.of("SomeName");
        ClassDesc interfaceClassDesc = ClassDesc.of(PointAccessor.class.getName());
        ClassDesc recordClassDesc = ClassDesc.of(Record.class.getName());
        ClassDesc valueLayoutsClassDesc = ClassDesc.of(ValueLayout.class.getName());
        ClassDesc memorySegmentClassDesc = ClassDesc.of(MemorySegment.class.getName());

        ClassLoader loader = TestInterfaceMapper.class.getClassLoader();

        // class SomeName
        byte[] bytes = Classfile.of(ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofClassLoading(loader))).build(genClassDesc, cb -> {
            cb.withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SUPER);
            // extends Record
            cb.withSuperclass(recordClassDesc);
            // implements PointAccessor
            //cb.withInterfaces(cb.constantPool().classEntry(interfaceClassDesc));
            cb.withInterfaceSymbols(interfaceClassDesc);
            // private final MemorySegment segment;
            cb.withField("segment", memorySegmentClassDesc,ACC_PRIVATE | ACC_FINAL);
            // private final long offset;
            cb.withField("offset", CD_long, ACC_PRIVATE | ACC_FINAL);

            // Constructor <init> (MemorySegment segment)
            // public TestInterfaceMapper$PointAccessorImpl(java.lang.foreign.MemorySegment, long);
            //    descriptor: (Ljava/lang/foreign/MemorySegment;J)V
            //    flags: (0x0001) ACC_PUBLIC
            //    Code:
            //      stack=3, locals=4, args_size=3
            //         0: aload_0
            //         1: invokespecial #1                  // Method java/lang/Record."<init>":()V
            //         4: aload_0
            //         5: aload_1
            //         6: putfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
            //         9: aload_0
            //        10: lload_2
            //        11: putfield      #13                 // Field offset:J
            //        14: return
            //      LineNumberTable:
            //        line 524: 0
            //    MethodParameters:
            //      Name                           Flags
            //      segment
            //      offset
            cb.withMethodBody(ConstantDescs.INIT_NAME, MethodTypeDesc.of(CD_void, memorySegmentClassDesc, CD_long), Classfile.ACC_PUBLIC, cob ->
                    cob.aload(0)
                    // Call Record's constructor
                    .invokespecial(recordClassDesc, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, false)
                    // Set "segment"
                    .aload(0)
                    .aload(1)
                    .putfield(genClassDesc, "segment", memorySegmentClassDesc)
                    // Set "offset"
                    .aload(0)
                    .lload(2)
                    .putfield(genClassDesc, "offset", CD_long)
                    .return_());

            //    public int x();
            //    descriptor: ()I
            //    flags: (0x0001) ACC_PUBLIC
            //    Code:
            //      stack=4, locals=1, args_size=1
            //         0: aload_0
            //         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
            //         4: getstatic     #17                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
            //         7: aload_0
            //         8: getfield      #13                 // Field offset:J
            //        11: invokeinterface #23,  4           // InterfaceMethod java/lang/foreign/MemorySegment.get:(Ljava/lang/foreign/ValueLayout$OfInt;J)I
            //        16: ireturn
            //      LineNumberTable:
            //        line 529: 0
            cb.withMethodBody("x", MethodTypeDesc.of(CD_int), Classfile.ACC_PUBLIC, cob ->
                    cob.aload(0)
                            .getfield(genClassDesc, "segment", memorySegmentClassDesc)
                            .getstatic(valueLayoutsClassDesc, "JAVA_INT", desc(ValueLayout.OfInt.class))
                            .aload(0)
                            .getfield(genClassDesc, "offset", CD_long)
                            .invokeinterface(memorySegmentClassDesc, "get", MethodTypeDesc.of(CD_int, desc(ValueLayout.OfInt.class), CD_long))
                            .ireturn()
            );

            //  public int y();
            //    descriptor: ()I
            //    flags: (0x0001) ACC_PUBLIC
            //    Code:
            //      stack=6, locals=1, args_size=1
            //         0: aload_0
            //         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
            //         4: getstatic     #17                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
            //         7: aload_0
            //         8: getfield      #13                 // Field offset:J
            //        11: ldc2_w        #29                 // long 4l
            //        14: ladd
            //        15: invokeinterface #23,  4           // InterfaceMethod java/lang/foreign/MemorySegment.get:(Ljava/lang/foreign/ValueLayout$OfInt;J)I
            //        20: ireturn
            //      LineNumberTable:
            //        line 534: 0
            cb.withMethodBody("y", MethodTypeDesc.of(CD_int), Classfile.ACC_PUBLIC, cob ->
                    cob.aload(0)
                            .getfield(genClassDesc, "segment", memorySegmentClassDesc)
                            .getstatic(valueLayoutsClassDesc, "JAVA_INT", desc(ValueLayout.OfInt.class))
                            .aload(0)
                            .getfield(genClassDesc, "offset", CD_long)
                            .ldc(4L)
                            .ladd()
                            .invokeinterface(memorySegmentClassDesc, "get", MethodTypeDesc.of(CD_int, desc(ValueLayout.OfInt.class), CD_long))
                            .ireturn()
            );

            // public void x(int);
            //    descriptor: (I)V
            //    flags: (0x0001) ACC_PUBLIC
            //    Code:
            //      stack=5, locals=2, args_size=2
            //         0: aload_0
            //         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
            //         4: getstatic     #17                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
            //         7: aload_0
            //         8: getfield      #13                 // Field offset:J
            //        11: iload_1
            //        12: invokeinterface #31,  5           // InterfaceMethod java/lang/foreign/MemorySegment.set:(Ljava/lang/foreign/ValueLayout$OfInt;JI)V
            //        17: return
            //      LineNumberTable:
            //        line 539: 0
            //        line 540: 17
            cb.withMethodBody("x", MethodTypeDesc.of(CD_void, CD_int), Classfile.ACC_PUBLIC, cob ->
                    cob.aload(0)
                            .getfield(genClassDesc, "segment", memorySegmentClassDesc)
                            .getstatic(valueLayoutsClassDesc, "JAVA_INT", desc(ValueLayout.OfInt.class))
                            .aload(0)
                            .getfield(genClassDesc, "offset", CD_long)
                            .iload(1)
                            .invokeinterface(memorySegmentClassDesc, "get", MethodTypeDesc.of(CD_void, desc(ValueLayout.OfInt.class), CD_long, CD_int))
                            .return_()
            );

            //  public void y(int);
            //    descriptor: (I)V
            //    flags: (0x0001) ACC_PUBLIC
            //    Code:
            //      stack=6, locals=2, args_size=2
            //         0: aload_0
            //         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
            //         4: getstatic     #17                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
            //         7: aload_0
            //         8: getfield      #13                 // Field offset:J
            //        11: ldc2_w        #29                 // long 4l
            //        14: ladd
            //        15: iload_1
            //        16: invokeinterface #31,  5           // InterfaceMethod java/lang/foreign/MemorySegment.set:(Ljava/lang/foreign/ValueLayout$OfInt;JI)V
            //        21: return
            //      LineNumberTable:
            //        line 544: 0
            //        line 545: 21
            cb.withMethodBody("y", MethodTypeDesc.of(CD_void, CD_int), Classfile.ACC_PUBLIC, cob ->
                    cob.aload(0)
                            .getfield(genClassDesc, "segment", memorySegmentClassDesc)
                            .getstatic(valueLayoutsClassDesc, "JAVA_INT", desc(ValueLayout.OfInt.class))
                            .aload(0)
                            .getfield(genClassDesc, "offset", CD_long)
                            .ldc(4L)
                            .ladd()
                            .iload(1)
                            .invokeinterface(memorySegmentClassDesc, "get", MethodTypeDesc.of(CD_void, desc(ValueLayout.OfInt.class), CD_long, CD_int))
                            .return_()
            );

            //  public java.lang.foreign.MemorySegment segment();
            //    descriptor: ()Ljava/lang/foreign/MemorySegment;
            //    flags: (0x0001) ACC_PUBLIC
            //    Code:
            //      stack=1, locals=1, args_size=1
            //         0: aload_0
            //         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
            //         4: areturn
            //      LineNumberTable:
            //        line 524: 0
            cb.withMethodBody("segment", MethodTypeDesc.of(memorySegmentClassDesc), Classfile.ACC_PUBLIC, cob ->
                    cob.aload(0)
                            .getfield(genClassDesc, "segment", memorySegmentClassDesc)
                            .areturn()
            );

            cb.withMethodBody("offset", MethodTypeDesc.of(CD_long), Classfile.ACC_PUBLIC, cob ->
                    cob.aload(0)
                            .getfield(genClassDesc, "offset", CD_long)
                            .lreturn()
            );


            cb.withMethodBody("toString", MethodTypeDesc.of(CD_String), Classfile.ACC_PUBLIC | ACC_FINAL, cob -> {

                        DirectMethodHandleDesc bootstrap = ConstantDescs.ofCallsiteBootstrap(
                                ClassDesc.of(StringConcatFactory.class.getName()),
                                "makeConcatWithConstants",
                                CD_CallSite,
                                CD_String, CD_Object.arrayType()
                        );

                String recipe = "PointAccessor[x()=\u0001, y()=\u0001]";
                        DynamicCallSiteDesc desc = DynamicCallSiteDesc.of(
                                bootstrap,
                                "toString",
                                MethodTypeDesc.of(CD_String, CD_int, CD_int), // String, x, y
                                recipe
                        );

                        cob.aload(0)
                                .invokevirtual(genClassDesc, "x", MethodTypeDesc.of(CD_int)) // Method x:()I
                                .aload(0)
                                .invokevirtual(genClassDesc, "y", MethodTypeDesc.of(CD_int)) // Method y:()I
                                .invokedynamic(desc)
                                .areturn();
                    }
            );

/*
            cb.withMethodBody("toString", MethodTypeDesc.of(CD_String), Classfile.ACC_PUBLIC | ACC_FINAL, cob ->
                    cob.aload(0)
                            .getfield(genClassDesc, "offset", CD_long)
                            .invokestatic(ClassDesc.of(Long.class.getName()), "toString", MethodTypeDesc.of(CD_String, CD_long))
                            .areturn()
            );
*/

/*            cb.withMethodBody("toString", MethodTypeDesc.of(CD_String), Classfile.ACC_PUBLIC | ACC_FINAL, cob -> {
                        DirectMethodHandleDesc bootstrap = ConstantDescs.ofCallsiteBootstrap(
                                ClassDesc.of(ObjectMethods.class.getName()),
                                "bootstrap",
                                CD_Object,
                                CD_Class, CD_String, CD_MethodHandle.arrayType()
                        );

                        DynamicCallSiteDesc desc = DynamicCallSiteDesc.of(
                                bootstrap,
                                "toString",
                                MethodTypeDesc.of(CD_String, genClassDesc),
                                genClassDesc,
                                "segment,offset",
                                MethodHandleDesc.ofMethod(Kind.INTERFACE_VIRTUAL, genClassDesc, "segment", MethodTypeDesc.of(memorySegmentClassDesc, genClassDesc)),
                                MethodHandleDesc.ofMethod(Kind.INTERFACE_VIRTUAL, genClassDesc, "offset", MethodTypeDesc.of(CD_long, genClassDesc))
                        );

                        cob.aload(0)
                                .invokedynamic(desc)
*//*                                .invokedynamic(DynamicCallSiteDesc.of(
                                        MethodHandleDesc.ofMethod(
                                                DirectMethodHandleDesc.Kind.STATIC,
                                                ClassDesc.of(MethodHandles.lookup().lookupClass().getName()),
                                                "toString",
                                                MethodTypeDesc.of(CD_String, genClassDesc)
                                        ),
                                        "bootstrap",
                                        MethodTypeDesc.of(CD_Object, CD_MethodHandles_Lookup, CD_String, CD_MethodTypeDesc, CD_Class, CD_String, CD_MethodHandle.arrayType()),
                                        bootstrap))*//*
                                .areturn();
                    }
            );*/

            cb.withMethodBody("hashCode", MethodTypeDesc.of(CD_int), Classfile.ACC_PUBLIC | ACC_FINAL, cob ->
                    cob.aload(0)
                            .invokestatic(ClassDesc.of(System.class.getName()), "identityHashCode", MethodTypeDesc.of(CD_int, CD_Object))
                            .ireturn()
            );

            //  public boolean equals(java.lang.Object);
            //    descriptor: (Ljava/lang/Object;)Z
            //    flags: (0x0001) ACC_PUBLIC
            //    Code:
            //      stack=2, locals=2, args_size=2
            //         0: aload_0
            //         1: aload_1
            //         2: if_acmpne     9
            //         5: iconst_1
            //         6: goto          10
            //         9: iconst_0
            //        10: ireturn
            //      LineNumberTable:
            //        line 654: 0
            //      StackMapTable: number_of_entries = 2
            //        frame_type = 9 /* same */
            //        frame_type = 64 /* same_locals_1_stack_item */
            //          stack = [ int ]
            cb.withMethodBody("equals", MethodTypeDesc.of(CD_boolean, CD_Object), Classfile.ACC_PUBLIC | ACC_FINAL, cob -> {
                        Label l0 = cob.newLabel();
                        Label l1 = cob.newLabel();
                        cob.aload(0)
                                .aload(1)
                                .if_acmpne(l0)
                                .iconst_1()
                                .goto_(l1)
                                .labelBinding(l0)
                                .iconst_0()
                                .labelBinding(l1)
                                .ireturn()
                        ;
                    }
            );

        });

        Files.write(Path.of("/Users/pminborg/dev/minborg-panama/open/test/jdk/java/foreign/mapper/SomeName.class"), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        ClassModel cm = Classfile.of().parse(bytes);
        javap(cm);

        try {
            //@SuppressWarnings("unchecked")
            //Class<PointAccessor> c = (Class<PointAccessor>) MethodHandles.lookup().defineClass(bytes);

            MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClass(bytes, true);
            @SuppressWarnings("unchecked")
            Class<PointAccessor> c = (Class<PointAccessor>)lookup.lookupClass();

            MethodHandle ctor = MethodHandles.lookup().findConstructor(c, MethodType.methodType(void.class, MemorySegment.class, long.class));
            ctor = ctor.asType(ctor.type().changeReturnType(PointAccessor.class));

            var segment = MemorySegment.ofArray(new int[]{3, 4});
            PointAccessor accessor = (PointAccessor) ctor.invokeExact(segment, 0L);
            int x = accessor.x();
            System.out.println("x = " + x);
            int y = accessor.y();
            System.out.println("y = " + y);

            System.out.println("class = " + c);
            System.out.println("accessor = " + accessor);
            System.out.println("accessor.hashCode() = " + accessor.hashCode());
            System.out.println("accessor.equals(\"A\") = " + accessor.equals("A"));
            System.out.println("accessor.equals(accessor) = " + accessor.equals(accessor));
            print(accessor.segment()); // .asSlice(accessor.offset())

            PointAccessor accessor2 = new PointAccessorImpl(segment, 0L);
            System.out.println("accessor2 = " + accessor2);
            System.out.println("accessor2.hashCode() = " + accessor2.hashCode());

            MethodHandle sh = MethodHandles.publicLookup().findStatic(MemorySegment.class,
                    "copy",
                    MethodType.methodType(void.class, MemorySegment.class, long.class, MemorySegment.class, long.class, long.class));
            // ->(MS, l, MS, l)
            //           -----
            sh = MethodHandles.insertArguments(sh, 4, POINT_LAYOUT.byteSize());

            MethodHandle segExtractor = MethodHandles.lookup().findVirtual(c, "segment", MethodType.methodType(MemorySegment.class));
            MethodHandle offsetExtractor = MethodHandles.lookup().findVirtual(c, "offset", MethodType.methodType(long.class));
            // ->(T, l, MS, l)
            sh = MethodHandles.filterArguments(sh, 0, segExtractor);
            // ->(T, T, MS, l)
            sh = MethodHandles.filterArguments(sh, 1, offsetExtractor);
            // ->(MS, l, T)
            sh = MethodHandles.permuteArguments(sh, MethodType.methodType(void.class, MemorySegment.class, long.class, c), 2, 2, 0, 1);

            sh = sh.asType(MethodType.methodType(void.class, MemorySegment.class, long.class, Object.class));

            var copy = Arena.ofAuto().allocate(POINT_LAYOUT);
            sh.invokeExact(copy, 0L, (Object) accessor);
            print(copy);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        fail();
    }

    static void print(MemorySegment segment) {
        HexFormat hf = HexFormat.ofDelimiter(" ");
        String hex = hf.formatHex(segment.toArray(JAVA_BYTE));
        System.out.println(hex);
    }

    static ClassDesc desc(Class<?> clazz ) {
        return clazz.describeConstable().orElseThrow();
    }

    void javap(ClassModel cm) {
        System.out.println("  cm.minorVersion() = " + cm.minorVersion());
        System.out.println("  cm.majorVersion() = " + cm.majorVersion());
        System.out.println("  cm.flags() = " + cm.flags());
        cm.flags().flags().forEach(
                af -> System.out.println(Integer.toString(af.mask()) + af.locations())
        );
        System.out.println("  cm.thisClass() = " + cm.thisClass());
        System.out.println("  cm.superclass() = " + cm.superclass());
        System.out.println("  cm.interfaces().size() = " + cm.interfaces().size());
        System.out.println("  cm.fields().size() = " + cm.fields().size());
        System.out.println("  cm.methods().size() = " + cm.methods().size());
        System.out.println("  cm.attributes().size() = " + cm.attributes().size());

        System.out.println("  Attributes");
        System.out.println(cm);
        cm.attributes().forEach(a -> {
            AttributeMapper<?> am = a.attributeMapper();
            System.out.println(am.name());
        });

        System.out.println("Constant pool:");
        ConstantPool cp = cm.constantPool();
        for (PoolEntry pe:cp) {
            String msg = render(pe);
            String index = String.format("#%d", pe.index());
            System.out.format("%5s = %s%n", index, msg);
        }
        System.out.println();

        //   private final java.lang.foreign.MemorySegment segment;
        //    descriptor: Ljava/lang/foreign/MemorySegment;
        //    flags: (0x0012) ACC_PRIVATE, ACC_FINAL
        for (FieldModel fm : cm.fields()) {
            String flags = render(fm.flags());
            System.out.format("%s %s.%s %s;%n", flags, fm.fieldTypeSymbol().packageName(), fm.fieldTypeSymbol().displayName(), fm.fieldName().toString());
        }
        System.out.println();

        for (MethodModel me : cm.methods()) {

            //   public TestInterfaceMapper$PointAccessorImpl(java.lang.foreign.MemorySegment);
            //    descriptor: (Ljava/lang/foreign/MemorySegment;)V
            //    flags: (0x0001) ACC_PUBLIC
            //    Code:
            //      stack=2, locals=2, args_size=2
            //         0: aload_0
            //         1: invokespecial #1                  // Method java/lang/Record."<init>":()V
            //         4: aload_0
            //         5: aload_1
            //         6: putfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
            //         9: return
            //      LineNumberTable:
            //        line 51: 0
            //    MethodParameters:
            //      Name                           Flags
            //      segment

            // public int x();
            //    descriptor: ()I
            //    flags: (0x0001) ACC_PUBLIC
            //    Code:
            //      stack=4, locals=1, args_size=1
            //         0: aload_0
            //         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
            //         4: getstatic     #13                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
            //         7: lconst_0
            //         8: invokeinterface #19,  4           // InterfaceMethod java/lang/foreign/MemorySegment.get:(Ljava/lang/foreign/ValueLayout$OfInt;J)I
            //        13: ireturn
            //      LineNumberTable:
            //        line 56: 0

            String flags = render(me.flags());
            System.out.format("%s %s %s(%s);%n", flags, me.methodTypeSymbol().returnType().toString(), me.methodName().toString(), me.methodTypeSymbol().parameterList());
            System.out.format(" descriptor: %s%n", me.methodTypeSymbol().descriptorString());
            System.out.format(" flags: (0x%04x) %s%n", me.flags().flagsMask(), me.flags().flags());
            System.out.format(" Code:%n");
            int stack = -1;
            int locals = -1;
            int args_size = -1;
            for (MethodElement e : me.elements()) {
                if (e instanceof CodeModel m) {
                    stack = m.maxStack();
                    locals = m.maxLocals();
                    break;
                }
            }
            System.out.format("   stack=%d, locals=%d, args_size=%d%n", stack, locals, args_size);
            for (MethodElement e : me.elements()) {
                System.out.println(render(e));
            }
            System.out.println();


            //BootstrapMethods:
            //  0: #65 REF_invokeStatic java/lang/runtime/ObjectMethods.bootstrap:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;
            //    Method arguments:
            //      #8 TestInterfaceMapper$PointAccessorImpl
            //      #63 segment
            //      #64 REF_getField TestInterfaceMapper$PointAccessorImpl.segment:Ljava/lang/foreign/MemorySegment;
            System.out.println("BootstrapMethods:");
            for (int i = 0; i < cp.bootstrapMethodCount(); i++) {
                BootstrapMethodEntry e = cp.bootstrapMethodEntry(i);
                System.out.format("  %d: %s%n", e.bsmIndex(), e.bootstrapMethod());
                System.out.println("    Method arguments:");
                for (LoadableConstantEntry le : e.arguments()) {
                    System.out.format("      #%d %s%n", le.index(), le);
                }
            }

        }
    }

    static String render(PoolEntry pe) {
        return switch (pe) {
            case DoubleEntry de  -> render("Double", de.doubleValue() + "D");
            case FloatEntry fe   -> render("Float", fe.floatValue() + "F");
            case IntegerEntry ie -> render("Integer", ie.intValue() + "");
            case LongEntry le    -> render("Long", le.longValue() + "L");
            case Utf8Entry u     -> render("Utf8", u.stringValue());
            // case AnnotationConstantValueEntry _ -> "Annotation";

            case DynamicConstantPoolEntry _ -> "Dynamic Constant";

            case ClassEntry ce   -> renderConstantPool("Class", "#" + ce.name().index(), ce.toString());
            case StringEntry s   -> renderConstantPool("String", "#" + s.utf8().index(), s.toString());
            case MethodHandleEntry mhe ->
                    renderConstantPool("MethodHandle", mhe.kind() + ":#" + mhe.reference().index(), mhe.toString());
            case LoadableConstantEntry lc -> "Loadable Constant " + lc.getClass();

            case FieldRefEntry fr ->
                    renderConstantPool("FieldRef", "#" + fr.owner().index() + ".#" + fr.nameAndType().index(), fr.toString());

            case InterfaceMethodRefEntry imr -> renderConstantPool("InterfaceMethodRef", "#"+imr.owner().index()+".#"+imr.nameAndType().index(), imr.toString());

            case MethodRefEntry mr ->
                    renderConstantPool("Methodref", "#" + mr.owner().index() + ".#" + mr.nameAndType().index(), mr.toString());

            case ModuleEntry _ -> "Module Entry";
            case NameAndTypeEntry nat ->
                    renderConstantPool("NameAndType", "#" + nat.name().index() + ":#" + nat.type().index(), nat.toString());
            case PackageEntry _ -> "Package";

        };
    }

    static String render(MethodElement me) {
        return switch (me) {
            case AccessFlags af -> render(af);
            case CodeModel cm -> render(cm);
            case MethodParametersAttribute mp -> render(mp);
            default -> me.getClass().getName() + " : " + me.toString();
        };
    }

    static String render(MethodParameterInfo mpi) {
        return render(mpi.flags()) + " " + mpi.name().map(Utf8Entry::stringValue);
    }

    static String render(CodeModel cm) {
        int pos = 0;
        StringBuilder sb = new StringBuilder();
        for (CodeElement ce: cm.elementList()) {
            switch (ce) {
                case Instruction i -> {
                    String line = pos + ":";
                    sb.append(String.format("%6s", line)).append(" ").append(render(ce));
                    pos += i.sizeInBytes();
                }
                case LineNumber l -> sb.append("    Line number:").append(l.line());
                default -> sb.append(ce.getClass().getName()).append(" : ").append(ce);
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }
    static String render(String type,
                         String additionalParams) {
        return renderConstantPool(type, additionalParams, "");
    }

    static String renderConstantPool(String type,
                                     String additionalParams,
                                     String comment) {
        StringBuilder sb = new StringBuilder(type);
        while (sb.length() < 19) {
            sb.append(" ");
        }
        sb.append(additionalParams);
        if (!comment.isEmpty()) {
            while (sb.length() < 34) {
                sb.append(" ");
            }
            sb.append("// ");
            sb.append(comment);
        }
        return sb.toString();
    }

    private static String render(MethodParametersAttribute mp) {
        StringBuilder sb = new StringBuilder("MethodParameters:").append(System.lineSeparator());
        sb.append("  Name                            Flags").append(System.lineSeparator());
        mp.parameters().forEach(p -> {
            sb.append(String.format("  %-31s %d%n", p.name().map(Utf8Entry::stringValue).orElse(""), p.flagsMask()));
        });
        return sb.toString();
    }

    private static String render(AccessFlags flags) {
        return render(flags.flags());
    }

    private static String render(Collection<AccessFlag> flags) {
        return flags.stream()
                .map(Object::toString)
                .map(String::toLowerCase)
                .collect(joining(" "));
    }

    private static String render(CodeElement element) {
        //Object o = element.

        return switch (element) {
            case NopInstruction nop -> "nop";
            case LoadInstruction li -> render(li);
            case FieldInstruction fi ->
                    renderInstruction("putfield", "#" + fi.field().index(), fi.field().name().stringValue() + ":" + fi.field().type().stringValue());
            case InvokeInstruction ii ->
                    renderInstruction(ii.opcode().toString().toLowerCase(), "#" + ii.method().index(), ii.method().toString());
            case ReturnInstruction _ -> "return";
            case ConstantInstruction.IntrinsicConstantInstruction ic -> ic.opcode().name().toLowerCase();
/*            case ConstantInstruction ci ->
                    renderInstruction(ci.opcode().toString().toLowerCase(),)*/

            case InvokeDynamicInstruction id ->
                    renderInstruction(id.opcode().toString().toLowerCase(), "#" + id.invokedynamic().index(), id.toString());

            // 7: ldc2_w        #25                 // long 4l
            case AbstractInstruction.BoundLoadConstantInstruction bl ->
                    renderInstruction(bl.opcode().toString().toLowerCase(), "#"+bl.constantEntry().index(), bl.typeKind()+" "+bl.constantValue());

            default -> element.getClass().getName() + " : " + element.toString();
        };
    }

    //          0: aload_0
    //         1: invokespecial #1                  // Method java/lang/Record."<init>":()V
    //         4: aload_0
    //         5: aload_1
    //         6: putfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
    //         9: return

    private static String render(LoadInstruction li) {
        return li.opcode().name().toLowerCase();
    }

    static String renderInstruction(String op,
                                    String additionalParams,
                                    String comment) {
        StringBuilder sb = new StringBuilder(op);
        while (sb.length() < 16) {
            sb.append(" ");
        }
        sb.append(additionalParams);
        if (!comment.isEmpty()) {
            while (sb.length() < 31) {
                sb.append(" ");
            }
            sb.append("// ");
            sb.append(comment);
        }
        return sb.toString();
    }

    public interface PointAccessor extends SegmentBacked {
        int x();
        int y();
        void x(int x);
        void y(int y);
    }

    // @ValueBased
    public static final class PointAccessorImpl implements PointAccessor {

        private final MemorySegment segment;
        private final long offset;

        public PointAccessorImpl(MemorySegment segment, long offset) {
            this.segment = segment;
            this.offset = offset;
        }

        @Override
        public MemorySegment segment() {
            return segment;
        }

        @Override
        public long offset() {
            return offset;
        }

        @Override
        public int x() {
            return segment.get(JAVA_INT, offset);
        }

        @Override
        public int y() {
            return segment.get(JAVA_INT, offset + 4);
        }

        @Override
        public void x(int x) {
            segment.set(JAVA_INT, offset, x);
        }

        @Override
        public void y(int y) {
            segment.set(JAVA_INT, offset + 4, y);
        }

       @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public String toString() {
            return "PointAccessor[x()=" + x() + ", y()=" + y() + "]";
        }
    }

/*
WITH OFFSET

pminborg@pminborg-mac minborg-panama % javap -p -c -l -verbose  build/macosx-aarch64/test-support/jtreg_open_test_jdk_java_foreign_mapper_TestInterfaceMapper_java/classes/0/java/foreign/mapper/TestInterfaceMapper.d/TestInterfaceMapper\$PointAccessorImpl.class
Classfile /Users/pminborg/dev/minborg-panama/build/macosx-aarch64/test-support/jtreg_open_test_jdk_java_foreign_mapper_TestInterfaceMapper_java/classes/0/java/foreign/mapper/TestInterfaceMapper.d/TestInterfaceMapper$PointAccessorImpl.class
  Last modified Nov 29, 2023; size 2127 bytes
  SHA-256 checksum c5ff3f1646352268ead7c242bc43ffd9f795b0f35feb6d2a77c0ca6e092fd06b
  Compiled from "TestInterfaceMapper.java"
public final class TestInterfaceMapper$PointAccessorImpl extends java.lang.Record implements TestInterfaceMapper$PointAccessor
  minor version: 0
  major version: 66
  flags: (0x0031) ACC_PUBLIC, ACC_FINAL, ACC_SUPER
  this_class: #8                          // TestInterfaceMapper$PointAccessorImpl
  super_class: #2                         // java/lang/Record
  interfaces: 1, fields: 2, methods: 10, attributes: 5
Constant pool:
   #1 = Methodref          #2.#3          // java/lang/Record."<init>":()V
   #2 = Class              #4             // java/lang/Record
   #3 = NameAndType        #5:#6          // "<init>":()V
   #4 = Utf8               java/lang/Record
   #5 = Utf8               <init>
   #6 = Utf8               ()V
   #7 = Fieldref           #8.#9          // TestInterfaceMapper$PointAccessorImpl.segment:Ljava/lang/foreign/MemorySegment;
   #8 = Class              #10            // TestInterfaceMapper$PointAccessorImpl
   #9 = NameAndType        #11:#12        // segment:Ljava/lang/foreign/MemorySegment;
  #10 = Utf8               TestInterfaceMapper$PointAccessorImpl
  #11 = Utf8               segment
  #12 = Utf8               Ljava/lang/foreign/MemorySegment;
  #13 = Fieldref           #8.#14         // TestInterfaceMapper$PointAccessorImpl.offset:J
  #14 = NameAndType        #15:#16        // offset:J
  #15 = Utf8               offset
  #16 = Utf8               J
  #17 = Fieldref           #18.#19        // java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
  #18 = Class              #20            // java/lang/foreign/ValueLayout
  #19 = NameAndType        #21:#22        // JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
  #20 = Utf8               java/lang/foreign/ValueLayout
  #21 = Utf8               JAVA_INT
  #22 = Utf8               Ljava/lang/foreign/ValueLayout$OfInt;
  #23 = InterfaceMethodref #24.#25        // java/lang/foreign/MemorySegment.get:(Ljava/lang/foreign/ValueLayout$OfInt;J)I
  #24 = Class              #26            // java/lang/foreign/MemorySegment
  #25 = NameAndType        #27:#28        // get:(Ljava/lang/foreign/ValueLayout$OfInt;J)I
  #26 = Utf8               java/lang/foreign/MemorySegment
  #27 = Utf8               get
  #28 = Utf8               (Ljava/lang/foreign/ValueLayout$OfInt;J)I
  #29 = Long               4l
  #31 = InterfaceMethodref #24.#32        // java/lang/foreign/MemorySegment.set:(Ljava/lang/foreign/ValueLayout$OfInt;JI)V
  #32 = NameAndType        #33:#34        // set:(Ljava/lang/foreign/ValueLayout$OfInt;JI)V
  #33 = Utf8               set
  #34 = Utf8               (Ljava/lang/foreign/ValueLayout$OfInt;JI)V
  #35 = InvokeDynamic      #0:#36         // #0:toString:(LTestInterfaceMapper$PointAccessorImpl;)Ljava/lang/String;
  #36 = NameAndType        #37:#38        // toString:(LTestInterfaceMapper$PointAccessorImpl;)Ljava/lang/String;
  #37 = Utf8               toString
  #38 = Utf8               (LTestInterfaceMapper$PointAccessorImpl;)Ljava/lang/String;
  #39 = InvokeDynamic      #0:#40         // #0:hashCode:(LTestInterfaceMapper$PointAccessorImpl;)I
  #40 = NameAndType        #41:#42        // hashCode:(LTestInterfaceMapper$PointAccessorImpl;)I
  #41 = Utf8               hashCode
  #42 = Utf8               (LTestInterfaceMapper$PointAccessorImpl;)I
  #43 = InvokeDynamic      #0:#44         // #0:equals:(LTestInterfaceMapper$PointAccessorImpl;Ljava/lang/Object;)Z
  #44 = NameAndType        #45:#46        // equals:(LTestInterfaceMapper$PointAccessorImpl;Ljava/lang/Object;)Z
  #45 = Utf8               equals
  #46 = Utf8               (LTestInterfaceMapper$PointAccessorImpl;Ljava/lang/Object;)Z
  #47 = Class              #48            // TestInterfaceMapper$PointAccessor
  #48 = Utf8               TestInterfaceMapper$PointAccessor
  #49 = Utf8               (Ljava/lang/foreign/MemorySegment;J)V
  #50 = Utf8               Code
  #51 = Utf8               LineNumberTable
  #52 = Utf8               MethodParameters
  #53 = Utf8               x
  #54 = Utf8               ()I
  #55 = Utf8               y
  #56 = Utf8               (I)V
  #57 = Utf8               ()Ljava/lang/String;
  #58 = Utf8               (Ljava/lang/Object;)Z
  #59 = Utf8               ()Ljava/lang/foreign/MemorySegment;
  #60 = Utf8               ()J
  #61 = Utf8               SourceFile
  #62 = Utf8               TestInterfaceMapper.java
  #63 = Utf8               NestHost
  #64 = Class              #65            // TestInterfaceMapper
  #65 = Utf8               TestInterfaceMapper
  #66 = Utf8               Record
  #67 = Utf8               BootstrapMethods
  #68 = String             #69            // segment;offset
  #69 = Utf8               segment;offset
  #70 = MethodHandle       1:#7           // REF_getField TestInterfaceMapper$PointAccessorImpl.segment:Ljava/lang/foreign/MemorySegment;
  #71 = MethodHandle       1:#13          // REF_getField TestInterfaceMapper$PointAccessorImpl.offset:J
  #72 = MethodHandle       6:#73          // REF_invokeStatic java/lang/runtime/ObjectMethods.bootstrap:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;
  #73 = Methodref          #74.#75        // java/lang/runtime/ObjectMethods.bootstrap:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;
  #74 = Class              #76            // java/lang/runtime/ObjectMethods
  #75 = NameAndType        #77:#78        // bootstrap:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;
  #76 = Utf8               java/lang/runtime/ObjectMethods
  #77 = Utf8               bootstrap
  #78 = Utf8               (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;
  #79 = Utf8               InnerClasses
  #80 = Utf8               PointAccessorImpl
  #81 = Class              #82            // java/lang/foreign/ValueLayout$OfInt
  #82 = Utf8               java/lang/foreign/ValueLayout$OfInt
  #83 = Utf8               OfInt
  #84 = Utf8               PointAccessor
  #85 = Class              #86            // java/lang/invoke/MethodHandles$Lookup
  #86 = Utf8               java/lang/invoke/MethodHandles$Lookup
  #87 = Class              #88            // java/lang/invoke/MethodHandles
  #88 = Utf8               java/lang/invoke/MethodHandles
  #89 = Utf8               Lookup
{
  private final java.lang.foreign.MemorySegment segment;
    descriptor: Ljava/lang/foreign/MemorySegment;
    flags: (0x0012) ACC_PRIVATE, ACC_FINAL

  private final long offset;
    descriptor: J
    flags: (0x0012) ACC_PRIVATE, ACC_FINAL

  public TestInterfaceMapper$PointAccessorImpl(java.lang.foreign.MemorySegment, long);
    descriptor: (Ljava/lang/foreign/MemorySegment;J)V
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=3, locals=4, args_size=3
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Record."<init>":()V
         4: aload_0
         5: aload_1
         6: putfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
         9: aload_0
        10: lload_2
        11: putfield      #13                 // Field offset:J
        14: return
      LineNumberTable:
        line 713: 0
    MethodParameters:
      Name                           Flags
      segment
      offset

  public int x();
    descriptor: ()I
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=4, locals=1, args_size=1
         0: aload_0
         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
         4: getstatic     #17                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
         7: aload_0
         8: getfield      #13                 // Field offset:J
        11: invokeinterface #23,  4           // InterfaceMethod java/lang/foreign/MemorySegment.get:(Ljava/lang/foreign/ValueLayout$OfInt;J)I
        16: ireturn
      LineNumberTable:
        line 718: 0

  public int y();
    descriptor: ()I
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=6, locals=1, args_size=1
         0: aload_0
         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
         4: getstatic     #17                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
         7: aload_0
         8: getfield      #13                 // Field offset:J
        11: ldc2_w        #29                 // long 4l
        14: ladd
        15: invokeinterface #23,  4           // InterfaceMethod java/lang/foreign/MemorySegment.get:(Ljava/lang/foreign/ValueLayout$OfInt;J)I
        20: ireturn
      LineNumberTable:
        line 723: 0

  public void x(int);
    descriptor: (I)V
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=5, locals=2, args_size=2
         0: aload_0
         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
         4: getstatic     #17                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
         7: aload_0
         8: getfield      #13                 // Field offset:J
        11: iload_1
        12: invokeinterface #31,  5           // InterfaceMethod java/lang/foreign/MemorySegment.set:(Ljava/lang/foreign/ValueLayout$OfInt;JI)V
        17: return
      LineNumberTable:
        line 728: 0
        line 729: 17

  public void y(int);
    descriptor: (I)V
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=6, locals=2, args_size=2
         0: aload_0
         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
         4: getstatic     #17                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
         7: aload_0
         8: getfield      #13                 // Field offset:J
        11: ldc2_w        #29                 // long 4l
        14: ladd
        15: iload_1
        16: invokeinterface #31,  5           // InterfaceMethod java/lang/foreign/MemorySegment.set:(Ljava/lang/foreign/ValueLayout$OfInt;JI)V
        21: return
      LineNumberTable:
        line 733: 0
        line 734: 21

  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
    flags: (0x0011) ACC_PUBLIC, ACC_FINAL
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokedynamic #35,  0             // InvokeDynamic #0:toString:(LTestInterfaceMapper$PointAccessorImpl;)Ljava/lang/String;
         6: areturn
      LineNumberTable:
        line 713: 0

  public final int hashCode();
    descriptor: ()I
    flags: (0x0011) ACC_PUBLIC, ACC_FINAL
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokedynamic #39,  0             // InvokeDynamic #0:hashCode:(LTestInterfaceMapper$PointAccessorImpl;)I
         6: ireturn
      LineNumberTable:
        line 713: 0

  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
    flags: (0x0011) ACC_PUBLIC, ACC_FINAL
    Code:
      stack=2, locals=2, args_size=2
         0: aload_0
         1: aload_1
         2: invokedynamic #43,  0             // InvokeDynamic #0:equals:(LTestInterfaceMapper$PointAccessorImpl;Ljava/lang/Object;)Z
         7: ireturn
      LineNumberTable:
        line 713: 0

  public java.lang.foreign.MemorySegment segment();
    descriptor: ()Ljava/lang/foreign/MemorySegment;
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
         4: areturn
      LineNumberTable:
        line 713: 0

  public long offset();
    descriptor: ()J
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=2, locals=1, args_size=1
         0: aload_0
         1: getfield      #13                 // Field offset:J
         4: lreturn
      LineNumberTable:
        line 713: 0
}
SourceFile: "TestInterfaceMapper.java"
NestHost: class TestInterfaceMapper
Record:
  java.lang.foreign.MemorySegment segment;
    descriptor: Ljava/lang/foreign/MemorySegment;

  long offset;
    descriptor: J

BootstrapMethods:
  0: #72 REF_invokeStatic java/lang/runtime/ObjectMethods.bootstrap:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;
    Method arguments:
      #8 TestInterfaceMapper$PointAccessorImpl
      #68 segment;offset
      #70 REF_getField TestInterfaceMapper$PointAccessorImpl.segment:Ljava/lang/foreign/MemorySegment;
      #71 REF_getField TestInterfaceMapper$PointAccessorImpl.offset:J
InnerClasses:
  public static final #80= #8 of #64;     // PointAccessorImpl=class TestInterfaceMapper$PointAccessorImpl of class TestInterfaceMapper
  public static #83= #81 of #18;          // OfInt=class java/lang/foreign/ValueLayout$OfInt of class java/lang/foreign/ValueLayout
  public static #84= #47 of #64;          // PointAccessor=class TestInterfaceMapper$PointAccessor of class TestInterfaceMapper
  public static final #89= #85 of #87;    // Lookup=class java/lang/invoke/MethodHandles$Lookup of class java/lang/invoke/MethodHandles
 */


}
