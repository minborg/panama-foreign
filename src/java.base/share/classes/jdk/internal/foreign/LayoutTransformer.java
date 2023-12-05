package jdk.internal.foreign;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.util.function.Function;

public sealed interface LayoutTransformer<T extends MemoryLayout> permits
        LayoutTransformer.OfMemoryLayout,
        LayoutTransformer.OfSequenceLayout,
        LayoutTransformer.OfGroupLayout,
        LayoutTransformer.OfStructLayout,
        LayoutTransformer.OfUnionLayout,
        LayoutTransformer.OfPaddingLayout,
        LayoutTransformer.OfValueLayout,
        LayoutTransformer.OfBoolean,
        LayoutTransformer.OfByte,
        LayoutTransformer.OfChar,
        LayoutTransformer.OfShort,
        LayoutTransformer.OfInt,
        LayoutTransformer.OfFloat,
        LayoutTransformer.OfLong,
        LayoutTransformer.OfDouble,
        LayoutTransformer.OfAddressLayout,
        AbstractLayoutTransformer {

    MemoryLayout transform(T t);

    static MemoryLayout deepTransform(MemoryLayout layout,
                                      LayoutTransformer<?> t) {

        // Breadth first
        MemoryLayout outer = transformFlat(t, layout);

        // Handle element transformation
        return switch (outer) {
            case SequenceLayout sl -> MemoryLayout.sequenceLayout(sl.elementCount(), deepTransform(sl.elementLayout(), t));
            case GroupLayout gl -> MemoryLayout.structLayout(applyRecursively(gl, t));
            default -> layout;
        };
    }

    private static MemoryLayout transformFlat(LayoutTransformer<?> transformer, MemoryLayout l) {
        return switch (transformer) {
            case LayoutTransformer.OfMemoryLayout   t                                            -> t.transform(l);
            case LayoutTransformer.OfSequenceLayout t when l instanceof SequenceLayout sl        -> t.transform(sl);
            case LayoutTransformer.OfGroupLayout    t when l instanceof GroupLayout gl           -> t.transform(gl);
            case LayoutTransformer.OfStructLayout   t when l instanceof StructLayout se          -> t.transform(se);
            case LayoutTransformer.OfUnionLayout    t when l instanceof UnionLayout uel          -> t.transform(uel);
            case LayoutTransformer.OfPaddingLayout  t when l instanceof PaddingLayout pl         -> t.transform(pl);
            case LayoutTransformer.OfValueLayout    t when l instanceof ValueLayout vl           -> t.transform(vl);
            case LayoutTransformer.OfBoolean        t when l instanceof ValueLayout.OfBoolean bl -> t.transform(bl);
            case LayoutTransformer.OfByte           t when l instanceof ValueLayout.OfByte by    -> t.transform(by);
            case LayoutTransformer.OfChar           t when l instanceof ValueLayout.OfChar ch    -> t.transform(ch);
            case LayoutTransformer.OfShort          t when l instanceof ValueLayout.OfShort sh   -> t.transform(sh);
            case LayoutTransformer.OfInt            t when l instanceof ValueLayout.OfInt in     -> t.transform(in);
            case LayoutTransformer.OfFloat          t when l instanceof ValueLayout.OfFloat fl   -> t.transform(fl);
            case LayoutTransformer.OfLong           t when l instanceof ValueLayout.OfLong lo    -> t.transform(lo);
            case LayoutTransformer.OfDouble         t when l instanceof ValueLayout.OfDouble db  -> t.transform(db);
            case LayoutTransformer.OfAddressLayout  t when l instanceof AddressLayout ad         -> t.transform(ad);
            // No transformation
            default -> l;
        };
    }

    private static MemoryLayout[] applyRecursively(GroupLayout groupLayout,
                                                   LayoutTransformer<? extends MemoryLayout> t) {
        return groupLayout.memberLayouts().stream()
                .map(l -> deepTransform(l, t))
                .toArray(MemoryLayout[]::new);
    }

    sealed interface OfMemoryLayout extends LayoutTransformer<MemoryLayout>
            permits AbstractLayoutTransformer.OfMemoryLayoutImpl {
    }

    sealed interface OfSequenceLayout extends LayoutTransformer<SequenceLayout>
            permits AbstractLayoutTransformer.OfSequenceLayoutImpl {

    }
    sealed interface OfGroupLayout extends LayoutTransformer<GroupLayout>
            permits AbstractLayoutTransformer.OfGroupLayoutImpl {


    }
    sealed interface OfStructLayout extends LayoutTransformer<StructLayout>
            permits AbstractLayoutTransformer.OfStructLayoutImpl {
    }
    sealed interface OfUnionLayout extends LayoutTransformer<UnionLayout>
            permits AbstractLayoutTransformer.OfUnionLayoutImpl {
    }
    sealed interface OfPaddingLayout extends LayoutTransformer<PaddingLayout>
            permits AbstractLayoutTransformer.OfPaddingLayoutImpl {

    }
    sealed interface OfValueLayout extends LayoutTransformer<ValueLayout>
            permits AbstractLayoutTransformer.OfValueLayoutImpl {

    }
    sealed interface OfBoolean extends LayoutTransformer<ValueLayout.OfBoolean>
            permits AbstractLayoutTransformer.OfBooleanImpl {
    }
    sealed interface OfByte extends LayoutTransformer<ValueLayout.OfByte>
            permits AbstractLayoutTransformer.OfByteImpl {
    }
    sealed interface OfChar extends LayoutTransformer<ValueLayout.OfChar>
            permits AbstractLayoutTransformer.OfCharImpl {
    }
    sealed interface OfShort extends LayoutTransformer<ValueLayout.OfShort>
            permits AbstractLayoutTransformer.OfShortImpl {
    }
    sealed interface OfInt extends LayoutTransformer<ValueLayout.OfInt>
            permits AbstractLayoutTransformer.OfIntImpl {
    }
    sealed interface OfFloat extends LayoutTransformer<ValueLayout.OfFloat>
            permits AbstractLayoutTransformer.OfFloatImpl {
    }
    sealed interface OfLong extends LayoutTransformer<ValueLayout.OfLong>
            permits AbstractLayoutTransformer.OfLongImpl {
    }
    sealed interface OfDouble extends LayoutTransformer<ValueLayout.OfDouble>
            permits AbstractLayoutTransformer.OfDoubleImpl {
    }
    sealed interface OfAddressLayout extends LayoutTransformer<AddressLayout>
            permits AbstractLayoutTransformer.OfAddressLayoutImpl {
    }

    // Factories

    static OfMemoryLayout ofMemoryLayout(Function<? super MemoryLayout, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfMemoryLayoutImpl(op);
    }

    static OfSequenceLayout ofSequenceLayout(Function<? super SequenceLayout, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfSequenceLayoutImpl(op);
    }

    static OfGroupLayout ofGroupLayout(Function<? super GroupLayout, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfGroupLayoutImpl(op);
    }

    static OfStructLayout ofStructLayout(Function<? super StructLayout, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfStructLayoutImpl(op);
    }

    static OfUnionLayout ofUnionLayout(Function<? super UnionLayout, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfUnionLayoutImpl(op);
    }

    static OfPaddingLayout ofPaddingLayout(Function<? super PaddingLayout, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfPaddingLayoutImpl(op);
    }

    static OfValueLayout ofValueLayout(Function<? super ValueLayout, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfValueLayoutImpl(op);
    }

    static OfBoolean ofBoolean(Function<? super ValueLayout.OfBoolean, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfBooleanImpl(op);
    }

    static OfByte ofByte(Function<? super ValueLayout.OfByte, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfByteImpl(op);
    }

    static OfChar ofChar(Function<? super ValueLayout.OfChar, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfCharImpl(op);
    }

    static OfShort ofShort(Function<? super ValueLayout.OfShort, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfShortImpl(op);
    }

    static OfInt ofInt(Function<? super ValueLayout.OfInt, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfIntImpl(op);
    }

    static OfFloat ofFloat(Function<? super ValueLayout.OfFloat, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfFloatImpl(op);
    }

    static OfLong ofLong(Function<? super ValueLayout.OfLong, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfLongImpl(op);
    }

    static OfDouble ofDouble(Function<? super ValueLayout.OfDouble, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfDoubleImpl(op);
    }

    static OfAddressLayout ofAddress(Function<? super java.lang.foreign.AddressLayout, ? extends MemoryLayout> op) {
        return new AbstractLayoutTransformer.OfAddressLayoutImpl(op);
    }
}
