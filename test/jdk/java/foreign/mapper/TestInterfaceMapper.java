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
import jdk.internal.classfile.ClassHierarchyResolver;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.CodeModel;
import jdk.internal.classfile.FieldModel;
import jdk.internal.classfile.Instruction;
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
import java.lang.constant.MethodTypeDesc;
import java.lang.foreign.mapper.SegmentBacked;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import static java.lang.constant.ConstantDescs.*;
import static java.util.stream.Collectors.joining;
import static jdk.internal.classfile.Classfile.*;
import static org.junit.jupiter.api.Assertions.*;

final class TestInterfaceMapper {

    //@Test
    void fromFile() throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("/Users/pminborg/dev/minborg-panama/open/test/jdk/java/foreign/mapper/TestInterfaceMapper$PointAccessorImpl.class"));
        ClassModel cm = Classfile.of().parse(bytes);
        javap(cm);
        fail();
    }

    @Test
    void fromModel() {

        ClassDesc genClassDesc = ClassDesc.of("SomeName");
        ClassDesc interfaceClassDesc = ClassDesc.of(PointAccessor.class.getName());
        ClassDesc recordClassDesc = ClassDesc.of(Record.class.getName());
        ClassDesc valueLayoutsClassDesc = ClassDesc.of(ValueLayout.class.getName());
        ClassDesc memorySegmentClassDesc = ClassDesc.of(MemorySegment.class.getName());

        ClassLoader loader = TestInterfaceMapper.class.getClassLoader();

        // class SomeName
        byte[] bytes = Classfile.of(ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofClassLoading(loader))).build(genClassDesc, cb -> {
            cb.withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC);
            // extends Record
            cb.withSuperclass(recordClassDesc);
            // implements PointAccessor
            //cb.withInterfaces(cb.constantPool().classEntry(interfaceClassDesc));
            cb.withInterfaceSymbols(interfaceClassDesc);
            // private final MemorySegment segment;
            cb.withField("segment", ClassDesc.of(MemorySegment.class.getName()),ACC_PRIVATE | ACC_FINAL);

            // Constructor <init> (MemorySegment segment)
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
            cb.withMethodBody(ConstantDescs.INIT_NAME, MethodTypeDesc.of(CD_void, memorySegmentClassDesc), Classfile.ACC_PUBLIC, cob ->
                    cob.aload(0)
                    // Call Record's constructor
                    .invokespecial(recordClassDesc, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, false)
                    // Set "segment"
                    .aload(0)
                    .aload(1)
                    .putfield(genClassDesc, "segment", memorySegmentClassDesc)
                    .return_());

            // 8: {opcode: INVOKEVIRTUAL, owner: java/lang/foreign/MemorySegment, method name: get, method type: (Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/ValueLayout$OfInt;J)I}

            //  public int x();
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

            cb.withMethodBody("x", MethodTypeDesc.of(CD_int), Classfile.ACC_PUBLIC, cob ->
                    cob.aload(0)
                            .getfield(genClassDesc, "segment", memorySegmentClassDesc)
                            .getstatic(valueLayoutsClassDesc, "JAVA_INT", desc(ValueLayout.OfInt.class))
                            .lconst_0()
                            .invokeinterface(memorySegmentClassDesc, "get", MethodTypeDesc.of(CD_int, desc(ValueLayout.OfInt.class), CD_long))
                            .ireturn()
            );

            cb.withMethodBody("y", MethodTypeDesc.of(CD_int), Classfile.ACC_PUBLIC, cob ->
                    cob.aload(0)
                            .getfield(genClassDesc, "segment", memorySegmentClassDesc)
                            .getstatic(valueLayoutsClassDesc, "JAVA_INT", desc(ValueLayout.OfInt.class))
                            .ldc(4L)
                            .invokeinterface(memorySegmentClassDesc, "get", MethodTypeDesc.of(CD_int, desc(ValueLayout.OfInt.class), CD_long))
                            .ireturn()
            );

            //   public void x(int);
            //    descriptor: (I)V
            //    flags: (0x0001) ACC_PUBLIC
            //    Code:
            //      stack=5, locals=2, args_size=2
            //         0: aload_0
            //         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
            //         4: getstatic     #13                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
            //         7: lconst_0
            //         8: iload_1
            //         9: invokeinterface #27,  5           // InterfaceMethod java/lang/foreign/MemorySegment.set:(Ljava/lang/foreign/ValueLayout$OfInt;JI)V
            //        14: return
            //      LineNumberTable:
            //        line 66: 0
            //        line 67: 14
            cb.withMethodBody("x", MethodTypeDesc.of(CD_void, CD_int), Classfile.ACC_PUBLIC, cob ->
                    cob.aload(0)
                            .getfield(genClassDesc, "segment", memorySegmentClassDesc)
                            .getstatic(valueLayoutsClassDesc, "JAVA_INT", desc(ValueLayout.OfInt.class))
                            .lconst_0()
                            .iload(1)
                            .invokeinterface(memorySegmentClassDesc, "get", MethodTypeDesc.of(CD_void, desc(ValueLayout.OfInt.class), CD_long, CD_int))
                            .return_()
            );

            cb.withMethodBody("y", MethodTypeDesc.of(CD_void, CD_int), Classfile.ACC_PUBLIC, cob ->
                    cob.aload(0)
                            .getfield(genClassDesc, "segment", memorySegmentClassDesc)
                            .getstatic(valueLayoutsClassDesc, "JAVA_INT", desc(ValueLayout.OfInt.class))
                            .ldc(4L)
                            .iload(1)
                            .invokeinterface(memorySegmentClassDesc, "get", MethodTypeDesc.of(CD_void, desc(ValueLayout.OfInt.class), CD_long, CD_int))
                            .return_()
            );



        });

        try {
            @SuppressWarnings("unchecked")
            Class<PointAccessor> c = (Class<PointAccessor>) MethodHandles.lookup().defineClass(bytes);

            MethodHandle ctor = MethodHandles.lookup().findConstructor(c, MethodType.methodType(void.class, MemorySegment.class));
            ctor = ctor.asType(ctor.type().changeReturnType(PointAccessor.class));

            var segment = MemorySegment.ofArray(new int[]{3, 4});
            PointAccessor accessor = (PointAccessor) ctor.invokeExact(segment);
            int x = accessor.x();
            System.out.println("x = " + x);
            int y = accessor.y();
            System.out.println("y = " + y);

            System.out.println("c = " + c);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        //TestInterfaceMapper.class.getClassLoader().


        ClassModel cm = Classfile.of().parse(bytes);
        javap(cm);
        fail();
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
        }
        fail();
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

    public record PointAccessorImpl(@Override MemorySegment segment, long offset)
            implements PointAccessor {

        @Override
        public int x() {
            return segment.get(ValueLayout.JAVA_INT, offset);
        }

        @Override
        public int y() {
            return segment.get(ValueLayout.JAVA_INT, offset + 4);
        }

        @Override
        public void x(int x) {
            segment.set(ValueLayout.JAVA_INT, offset, x);
        }

        @Override
        public void y(int y) {
            segment.set(ValueLayout.JAVA_INT, offset + 4, y);
        }
    }

/*
WITH OFFSET

pminborg@pminborg-mac minborg-panama % javap -p -c -l -verbose  build/macosx-aarch64/test-support/jtreg_open_test_jdk_java_foreign_mapper_TestInterfaceMapper_java/classes/0/java/foreign/mapper/TestInterfaceMapper.d/TestInterfaceMapper\$PointAccessorImpl.class
Classfile /Users/pminborg/dev/minborg-panama/build/macosx-aarch64/test-support/jtreg_open_test_jdk_java_foreign_mapper_TestInterfaceMapper_java/classes/0/java/foreign/mapper/TestInterfaceMapper.d/TestInterfaceMapper$PointAccessorImpl.class
  Last modified Nov 28, 2023; size 2127 bytes
  SHA-256 checksum 409d36b36aec9276bbb9d1192d367974bd9b52637891537bcaf4f97e5ee5e3f1
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
        line 524: 0
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
        line 529: 0

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
        line 534: 0

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
        line 539: 0
        line 540: 17

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
        line 544: 0
        line 545: 21

  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
    flags: (0x0011) ACC_PUBLIC, ACC_FINAL
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokedynamic #35,  0             // InvokeDynamic #0:toString:(LTestInterfaceMapper$PointAccessorImpl;)Ljava/lang/String;
         6: areturn
      LineNumberTable:
        line 524: 0

  public final int hashCode();
    descriptor: ()I
    flags: (0x0011) ACC_PUBLIC, ACC_FINAL
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokedynamic #39,  0             // InvokeDynamic #0:hashCode:(LTestInterfaceMapper$PointAccessorImpl;)I
         6: ireturn
      LineNumberTable:
        line 524: 0

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
        line 524: 0

  public java.lang.foreign.MemorySegment segment();
    descriptor: ()Ljava/lang/foreign/MemorySegment;
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
         4: areturn
      LineNumberTable:
        line 524: 0

  public long offset();
    descriptor: ()J
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=2, locals=1, args_size=1
         0: aload_0
         1: getfield      #13                 // Field offset:J
         4: lreturn
      LineNumberTable:
        line 524: 0
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





/*
NO OFFSET

pminborg@pminborg-mac minborg-panama % javap -p -c -l -verbose  build/macosx-aarch64/test-support/jtreg_open_test_jdk_java_foreign_mapper/classes/3/java/foreign/mapper/TestInterfaceMapper.d/TestInterfaceMapper\$PointAccessorImpl.class
Classfile /Users/pminborg/dev/minborg-panama/build/macosx-aarch64/test-support/jtreg_open_test_jdk_java_foreign_mapper/classes/3/java/foreign/mapper/TestInterfaceMapper.d/TestInterfaceMapper$PointAccessorImpl.class
  Last modified Nov 27, 2023; size 1992 bytes
  SHA-256 checksum 1480e83b8bd17436038ec4d8e4ccaec69c75448c2f62f355129c533335adeea4
  Compiled from "TestInterfaceMapper.java"
public final class TestInterfaceMapper$PointAccessorImpl extends java.lang.Record implements TestInterfaceMapper$PointAccessor
  minor version: 0
  major version: 66
  flags: (0x0031) ACC_PUBLIC, ACC_FINAL, ACC_SUPER
  this_class: #8                          // TestInterfaceMapper$PointAccessorImpl
  super_class: #2                         // java/lang/Record
  interfaces: 1, fields: 1, methods: 9, attributes: 5
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
  #13 = Fieldref           #14.#15        // java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
  #14 = Class              #16            // java/lang/foreign/ValueLayout
  #15 = NameAndType        #17:#18        // JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
  #16 = Utf8               java/lang/foreign/ValueLayout
  #17 = Utf8               JAVA_INT
  #18 = Utf8               Ljava/lang/foreign/ValueLayout$OfInt;
  #19 = InterfaceMethodref #20.#21        // java/lang/foreign/MemorySegment.get:(Ljava/lang/foreign/ValueLayout$OfInt;J)I
  #20 = Class              #22            // java/lang/foreign/MemorySegment
  #21 = NameAndType        #23:#24        // get:(Ljava/lang/foreign/ValueLayout$OfInt;J)I
  #22 = Utf8               java/lang/foreign/MemorySegment
  #23 = Utf8               get
  #24 = Utf8               (Ljava/lang/foreign/ValueLayout$OfInt;J)I
  #25 = Long               4l
  #27 = InterfaceMethodref #20.#28        // java/lang/foreign/MemorySegment.set:(Ljava/lang/foreign/ValueLayout$OfInt;JI)V
  #28 = NameAndType        #29:#30        // set:(Ljava/lang/foreign/ValueLayout$OfInt;JI)V
  #29 = Utf8               set
  #30 = Utf8               (Ljava/lang/foreign/ValueLayout$OfInt;JI)V
  #31 = InvokeDynamic      #0:#32         // #0:toString:(LTestInterfaceMapper$PointAccessorImpl;)Ljava/lang/String;
  #32 = NameAndType        #33:#34        // toString:(LTestInterfaceMapper$PointAccessorImpl;)Ljava/lang/String;
  #33 = Utf8               toString
  #34 = Utf8               (LTestInterfaceMapper$PointAccessorImpl;)Ljava/lang/String;
  #35 = InvokeDynamic      #0:#36         // #0:hashCode:(LTestInterfaceMapper$PointAccessorImpl;)I
  #36 = NameAndType        #37:#38        // hashCode:(LTestInterfaceMapper$PointAccessorImpl;)I
  #37 = Utf8               hashCode
  #38 = Utf8               (LTestInterfaceMapper$PointAccessorImpl;)I
  #39 = InvokeDynamic      #0:#40         // #0:equals:(LTestInterfaceMapper$PointAccessorImpl;Ljava/lang/Object;)Z
  #40 = NameAndType        #41:#42        // equals:(LTestInterfaceMapper$PointAccessorImpl;Ljava/lang/Object;)Z
  #41 = Utf8               equals
  #42 = Utf8               (LTestInterfaceMapper$PointAccessorImpl;Ljava/lang/Object;)Z
  #43 = Class              #44            // TestInterfaceMapper$PointAccessor
  #44 = Utf8               TestInterfaceMapper$PointAccessor
  #45 = Utf8               (Ljava/lang/foreign/MemorySegment;)V
  #46 = Utf8               Code
  #47 = Utf8               LineNumberTable
  #48 = Utf8               MethodParameters
  #49 = Utf8               x
  #50 = Utf8               ()I
  #51 = Utf8               y
  #52 = Utf8               (I)V
  #53 = Utf8               ()Ljava/lang/String;
  #54 = Utf8               (Ljava/lang/Object;)Z
  #55 = Utf8               ()Ljava/lang/foreign/MemorySegment;
  #56 = Utf8               SourceFile
  #57 = Utf8               TestInterfaceMapper.java
  #58 = Utf8               NestHost
  #59 = Class              #60            // TestInterfaceMapper
  #60 = Utf8               TestInterfaceMapper
  #61 = Utf8               Record
  #62 = Utf8               BootstrapMethods
  #63 = String             #11            // segment
  #64 = MethodHandle       1:#7           // REF_getField TestInterfaceMapper$PointAccessorImpl.segment:Ljava/lang/foreign/MemorySegment;
  #65 = MethodHandle       6:#66          // REF_invokeStatic java/lang/runtime/ObjectMethods.bootstrap:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;
  #66 = Methodref          #67.#68        // java/lang/runtime/ObjectMethods.bootstrap:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;
  #67 = Class              #69            // java/lang/runtime/ObjectMethods
  #68 = NameAndType        #70:#71        // bootstrap:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;
  #69 = Utf8               java/lang/runtime/ObjectMethods
  #70 = Utf8               bootstrap
  #71 = Utf8               (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;
  #72 = Utf8               InnerClasses
  #73 = Utf8               PointAccessorImpl
  #74 = Class              #75            // java/lang/foreign/ValueLayout$OfInt
  #75 = Utf8               java/lang/foreign/ValueLayout$OfInt
  #76 = Utf8               OfInt
  #77 = Utf8               PointAccessor
  #78 = Class              #79            // java/lang/invoke/MethodHandles$Lookup
  #79 = Utf8               java/lang/invoke/MethodHandles$Lookup
  #80 = Class              #81            // java/lang/invoke/MethodHandles
  #81 = Utf8               java/lang/invoke/MethodHandles
  #82 = Utf8               Lookup
{
  private final java.lang.foreign.MemorySegment segment;
    descriptor: Ljava/lang/foreign/MemorySegment;
    flags: (0x0012) ACC_PRIVATE, ACC_FINAL

LineNumber[line=51]
Load[OP=ALOAD_0, slot=0]
Invoke[OP=INVOKESPECIAL, m=java/lang/Record.<init>()V]
Load[OP=ALOAD_0, slot=0]
Load[OP=ALOAD_1, slot=1]
Field[OP=PUTFIELD, field=TestInterfaceMapper$PointAccessorImpl.segment:Ljava/lang/foreign/MemorySegment;]
Return[OP=RETURN]
 UnboundMethodParameterInfo[name=Optional[segment], flagsMask=0]

  public TestInterfaceMapper$PointAccessorImpl(java.lang.foreign.MemorySegment);
    descriptor: (Ljava/lang/foreign/MemorySegment;)V
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=2, locals=2, args_size=2
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Record."<init>":()V
         4: aload_0
         5: aload_1
         6: putfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
         9: return
      LineNumberTable:
        line 51: 0
    MethodParameters:
      Name                           Flags
      segment

  public int x();
    descriptor: ()I
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=4, locals=1, args_size=1
         0: aload_0
         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
         4: getstatic     #13                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
         7: lconst_0
         8: invokeinterface #19,  4           // InterfaceMethod java/lang/foreign/MemorySegment.get:(Ljava/lang/foreign/ValueLayout$OfInt;J)I
        13: ireturn
      LineNumberTable:
        line 56: 0

public ()I.x ;
 descriptor: ()I
 flags: (0x0001) [PUBLIC]
 Code:
 stack=4, locals=1, args_size=-1
public
    Line number:56
    0: aload_0
    1: putfield      #7             // segment:Ljava/lang/foreign/MemorySegment;
    4: putfield      #13            // JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
    7: lconst_0
    8: invokeinterface#19           // 11 java/lang/foreign/MemorySegment.get-(Ljava/lang/foreign/ValueLayout$OfInt;J)I
   13: return



  public int y();
    descriptor: ()I
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=4, locals=1, args_size=1
         0: aload_0
         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
         4: getstatic     #13                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
         7: ldc2_w        #25                 // long 4l
        10: invokeinterface #19,  4           // InterfaceMethod java/lang/foreign/MemorySegment.get:(Ljava/lang/foreign/ValueLayout$OfInt;J)I
        15: ireturn
      LineNumberTable:
        line 61: 0

  public void x(int);
    descriptor: (I)V
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=5, locals=2, args_size=2
         0: aload_0
         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
         4: getstatic     #13                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
         7: lconst_0
         8: iload_1
         9: invokeinterface #27,  5           // InterfaceMethod java/lang/foreign/MemorySegment.set:(Ljava/lang/foreign/ValueLayout$OfInt;JI)V
        14: return
      LineNumberTable:
        line 66: 0
        line 67: 14

  public void y(int);
    descriptor: (I)V
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=5, locals=2, args_size=2
         0: aload_0
         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
         4: getstatic     #13                 // Field java/lang/foreign/ValueLayout.JAVA_INT:Ljava/lang/foreign/ValueLayout$OfInt;
         7: ldc2_w        #25                 // long 4l
        10: iload_1
        11: invokeinterface #27,  5           // InterfaceMethod java/lang/foreign/MemorySegment.set:(Ljava/lang/foreign/ValueLayout$OfInt;JI)V
        16: return
      LineNumberTable:
        line 71: 0
        line 72: 16

  public final java.lang.String toString();
    descriptor: ()Ljava/lang/String;
    flags: (0x0011) ACC_PUBLIC, ACC_FINAL
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokedynamic #31,  0             // InvokeDynamic #0:toString:(LTestInterfaceMapper$PointAccessorImpl;)Ljava/lang/String;
         6: areturn
      LineNumberTable:
        line 51: 0

  public final int hashCode();
    descriptor: ()I
    flags: (0x0011) ACC_PUBLIC, ACC_FINAL
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokedynamic #35,  0             // InvokeDynamic #0:hashCode:(LTestInterfaceMapper$PointAccessorImpl;)I
         6: ireturn
      LineNumberTable:
        line 51: 0

  public final boolean equals(java.lang.Object);
    descriptor: (Ljava/lang/Object;)Z
    flags: (0x0011) ACC_PUBLIC, ACC_FINAL
    Code:
      stack=2, locals=2, args_size=2
         0: aload_0
         1: aload_1
         2: invokedynamic #39,  0             // InvokeDynamic #0:equals:(LTestInterfaceMapper$PointAccessorImpl;Ljava/lang/Object;)Z
         7: ireturn
      LineNumberTable:
        line 51: 0

  public java.lang.foreign.MemorySegment segment();
    descriptor: ()Ljava/lang/foreign/MemorySegment;
    flags: (0x0001) ACC_PUBLIC
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: getfield      #7                  // Field segment:Ljava/lang/foreign/MemorySegment;
         4: areturn
      LineNumberTable:
        line 51: 0
}
SourceFile: "TestInterfaceMapper.java"
NestHost: class TestInterfaceMapper
Record:
  java.lang.foreign.MemorySegment segment;
    descriptor: Ljava/lang/foreign/MemorySegment;

BootstrapMethods:
  0: #65 REF_invokeStatic java/lang/runtime/ObjectMethods.bootstrap:(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;
    Method arguments:
      #8 TestInterfaceMapper$PointAccessorImpl
      #63 segment
      #64 REF_getField TestInterfaceMapper$PointAccessorImpl.segment:Ljava/lang/foreign/MemorySegment;
InnerClasses:
  public static final #73= #8 of #59;     // PointAccessorImpl=class TestInterfaceMapper$PointAccessorImpl of class TestInterfaceMapper
  public static #76= #74 of #14;          // OfInt=class java/lang/foreign/ValueLayout$OfInt of class java/lang/foreign/ValueLayout
  public static #77= #43 of #59;          // PointAccessor=class TestInterfaceMapper$PointAccessor of class TestInterfaceMapper
  public static final #82= #78 of #80;    // Lookup=class java/lang/invoke/MethodHandles$Lookup of class java/lang/invoke/MethodHandles
 */


}
