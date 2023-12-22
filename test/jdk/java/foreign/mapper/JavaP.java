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
 * @enablePreview
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.impl
 * @run junit/othervm -Djava.lang.foreign.mapper.debug= JavaP
 */

import jdk.internal.classfile.AccessFlags;
import jdk.internal.classfile.AttributeMapper;
import jdk.internal.classfile.BootstrapMethodEntry;
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
import java.lang.foreign.MemorySegment;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Gatherer;

import static java.lang.foreign.ValueLayout.*;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.*;

final class JavaP {

    // Todo: Remove
    @SuppressWarnings("unchecked")
    static <T> Gatherer<T, ?, T> coalesce(
            BiPredicate<? super T, ? super T> mergeCondition,
            BinaryOperator<T> merger) {
        return Gatherer.ofSequential(
                () -> (T[]) new Object[1],
                (current, element, downstream) -> {
                    if (current[0] == null) {
                        current[0] = element;
                    } else {
                        if (mergeCondition.test(current[0], element)) {
                            current[0] = merger.apply(current[0], element);
                        } else {
                            var x = current[0];
                            current[0] = element;
                            return downstream.push(x);
                        }
                    }
                    return true;
                },
                (current, downstream) -> {
                    if (current[0] != null) {
                        downstream.push(current[0]);
                    }
                }
        );
    }

    // Todo: Remove
    @Test
    void dedupCars() {

        var dedup = "AAABCCCAABCC".chars()
                .mapToObj(i -> (char) i)
                .gather(coalesce((a, b) -> a == b, (a, b) -> a))
                .map(Object::toString)
                .collect(Collectors.joining());

        assertEquals("ABCABC", dedup);
    }


    //@Test
    void fromFile() throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("/Users/pminborg/dev/minborg-panama/open/test/jdk/java/foreign/mapper/TestInterfaceMapper$PointAccessorImpl.class"));
        ClassModel cm = Classfile.of().parse(bytes);
        javap(cm);
        fail();
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
        for (PoolEntry pe : cp) {
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
            case DoubleEntry de -> render("Double", de.doubleValue() + "D");
            case FloatEntry fe -> render("Float", fe.floatValue() + "F");
            case IntegerEntry ie -> render("Integer", ie.intValue() + "");
            case LongEntry le -> render("Long", le.longValue() + "L");
            case Utf8Entry u -> render("Utf8", u.stringValue());
            // case AnnotationConstantValueEntry _ -> "Annotation";

            case DynamicConstantPoolEntry _ -> "Dynamic Constant";

            case ClassEntry ce -> renderConstantPool("Class", "#" + ce.name().index(), ce.toString());
            case StringEntry s -> renderConstantPool("String", "#" + s.utf8().index(), s.toString());
            case MethodHandleEntry mhe ->
                    renderConstantPool("MethodHandle", mhe.kind() + ":#" + mhe.reference().index(), mhe.toString());
            case LoadableConstantEntry lc -> "Loadable Constant " + lc.getClass();

            case FieldRefEntry fr ->
                    renderConstantPool("FieldRef", "#" + fr.owner().index() + ".#" + fr.nameAndType().index(), fr.toString());

            case InterfaceMethodRefEntry imr ->
                    renderConstantPool("InterfaceMethodRef", "#" + imr.owner().index() + ".#" + imr.nameAndType().index(), imr.toString());

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
        for (CodeElement ce : cm.elementList()) {
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
            case NopInstruction _ -> "nop";
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
                    renderInstruction(bl.opcode().toString().toLowerCase(), "#" + bl.constantEntry().index(), bl.typeKind() + " " + bl.constantValue());

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


    // @ValueBased
    public static final class PointAccessorImpl2 implements BaseTest.PointAccessor {

        private final MemorySegment segment;
        private final long offset;

        public PointAccessorImpl2(MemorySegment segment, long offset) {
            this.segment = segment;
            this.offset = offset;
        }

        public MemorySegment $segment$() {
            return segment;
        }

        public long $offset$() {
            return offset;
        }

        @java.lang.Override
        public int x() {
            return segment.get(JAVA_INT, offset);
        }

        @java.lang.Override
        public int y() {
            return segment.get(JAVA_INT, offset + 4);
        }

        @java.lang.Override
        public void x(int x) {
            segment.set(JAVA_INT, offset, x);
        }

        @java.lang.Override
        public void y(int y) {
            segment.set(JAVA_INT, offset + 4, y);
        }

        @java.lang.Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @java.lang.Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @java.lang.Override
        public String toString() {
            return "PointAccessor[x()=" + x() + ", y()=" + y() + "]";
        }

        // dim 3, 4
        // reduce(2, 3) -> 3 * 8 + 2 * 8 * 4
        public long reduce(long i1, long i2) {
            long offset = Objects.checkIndex(i1, 3) * (8 * 4) +
                    Objects.checkIndex(i2, 4) * 8;
            return offset;
        }

        /*
         0: lload_0
         1: ldc2_w        #52                 // long 3l
         4: invokestatic  #54                 // Method java/util/Objects.checkIndex:(JJ)J
         7: ldc2_w        #60                 // long 32l
        10: lmul
        11: lload_2
        12: ldc2_w        #29                 // long 4l
        15: invokestatic  #54                 // Method java/util/Objects.checkIndex:(JJ)J
        18: ldc2_w        #62                 // long 8l
        21: lmul
        22: ladd
        23: lstore        4
        25: lload         4
        27: lreturn
         */

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
