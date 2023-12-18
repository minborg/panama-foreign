package jdk.internal.foreign.mapper;

import jdk.internal.classfile.CodeBuilder;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

record LayoutInfo(MemoryLayout layout,
                  Optional<ScalarInfo> scalarInfo,
                  Optional<ArrayInfo> arrayInfo,
                  Consumer<CodeBuilder> returnOp,
                  ObjIntConsumer<CodeBuilder> paramOp) {


    static LayoutInfo of(ValueLayout layout) {
        return switch (layout) {
            // Todo: Remove boolean?
            case ValueLayout.OfBoolean bo ->
                    LayoutInfo.ofScalar(bo, "JAVA_BOOLEAN", ValueLayout.OfBoolean.class, CodeBuilder::ireturn, CodeBuilder::iload);
            case ValueLayout.OfByte by ->
                    LayoutInfo.ofScalar(by, "JAVA_BYTE", ValueLayout.OfByte.class, CodeBuilder::ireturn, CodeBuilder::iload);
            case ValueLayout.OfShort sh ->
                    LayoutInfo.ofScalar(sh, "JAVA_SHORT", ValueLayout.OfShort.class, CodeBuilder::ireturn, CodeBuilder::iload);
            case ValueLayout.OfChar ch ->
                    LayoutInfo.ofScalar(ch, "JAVA_CHAR", ValueLayout.OfChar.class, CodeBuilder::ireturn, CodeBuilder::iload);
            case ValueLayout.OfInt in ->
                    LayoutInfo.ofScalar(in, "JAVA_INT", ValueLayout.OfInt.class, CodeBuilder::ireturn, CodeBuilder::iload);
            case ValueLayout.OfFloat fl ->
                    LayoutInfo.ofScalar(fl, "JAVA_FLOAT", ValueLayout.OfFloat.class, CodeBuilder::freturn, CodeBuilder::fload);
            case ValueLayout.OfLong lo ->
                    LayoutInfo.ofScalar(lo, "JAVA_LONG", ValueLayout.OfLong.class, CodeBuilder::lreturn, CodeBuilder::lload);
            case ValueLayout.OfDouble db ->
                    LayoutInfo.ofScalar(db, "JAVA_DOUBLE", ValueLayout.OfDouble.class, CodeBuilder::dreturn, CodeBuilder::dload);
            default ->
                    throw new IllegalArgumentException("Unable to map to a LayoutInfo: " + layout);
        };
    }

    static LayoutInfo of(GroupLayout layout) {
        return new LayoutInfo(layout, Optional.empty(), Optional.empty(), CodeBuilder::areturn, CodeBuilder::aload);
    }

    static LayoutInfo of(SequenceLayout layout) {
        ArrayInfo arrayInfo = ArrayInfo.of(layout);
        LayoutInfo elementLayoutInfo = (arrayInfo.elementLayout() instanceof ValueLayout vl)
                ? of(vl)
                : null;
        Optional<ScalarInfo> scalarInfo = Optional.ofNullable(elementLayoutInfo)
                .flatMap(li -> li.scalarInfo);
        return scalarInfo
                .map(_ -> new LayoutInfo(layout, scalarInfo, Optional.of(arrayInfo), elementLayoutInfo.returnOp(), elementLayoutInfo.paramOp())
                ).orElse(new LayoutInfo(layout, scalarInfo, Optional.of(arrayInfo), CodeBuilder::areturn, CodeBuilder::aload));

    }

    private static <T extends ValueLayout> LayoutInfo ofScalar(T layout,
                                                               String memberName,
                                                               Class<T> layoutType,
                                                               Consumer<CodeBuilder> returnOp,
                                                               ObjIntConsumer<CodeBuilder> paramOp) {
        return new LayoutInfo(layout, Optional.of(new ScalarInfo(memberName, SegmentInterfaceMapper.desc(layoutType))), Optional.empty(), returnOp, paramOp);
    }

}
