package jdk.internal.foreign;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.util.function.Function;

public sealed class AbstractLayoutTransformer<T extends MemoryLayout> implements LayoutTransformer<T> {

    private final Function<? super T, ? extends MemoryLayout> op;

    public AbstractLayoutTransformer(Function<? super T, ? extends MemoryLayout> op) {
        this.op = op;
    }

    @Override
    public MemoryLayout transform(T t) {
        return op.apply(t);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + op + "]";
    }

    // Concrete Implementations

    static final class OfMemoryLayoutImpl
            extends AbstractLayoutTransformer<MemoryLayout>
            implements OfMemoryLayout {

        public OfMemoryLayoutImpl(Function<? super MemoryLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfSequenceLayoutImpl
            extends AbstractLayoutTransformer<SequenceLayout>
            implements OfSequenceLayout {

        public OfSequenceLayoutImpl(Function<? super SequenceLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfGroupLayoutImpl
            extends AbstractLayoutTransformer<GroupLayout>
            implements OfGroupLayout {

        public OfGroupLayoutImpl(Function<? super GroupLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfStructLayoutImpl
            extends AbstractLayoutTransformer<StructLayout>
            implements OfStructLayout {

        public OfStructLayoutImpl(Function<? super StructLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfUnionLayoutImpl
            extends AbstractLayoutTransformer<UnionLayout>
            implements OfUnionLayout {

        public OfUnionLayoutImpl(Function<? super UnionLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfPaddingLayoutImpl
            extends AbstractLayoutTransformer<PaddingLayout>
            implements OfPaddingLayout {

        public OfPaddingLayoutImpl(Function<? super PaddingLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfValueLayoutImpl
            extends AbstractLayoutTransformer<ValueLayout>
            implements OfValueLayout {

        public OfValueLayoutImpl(Function<? super ValueLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfBooleanImpl
            extends AbstractLayoutTransformer<ValueLayout.OfBoolean>
            implements OfBoolean {

        public OfBooleanImpl(Function<? super ValueLayout.OfBoolean, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfByteImpl
            extends AbstractLayoutTransformer<ValueLayout.OfByte>
            implements OfByte {

        public OfByteImpl(Function<? super ValueLayout.OfByte, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfCharImpl
            extends AbstractLayoutTransformer<ValueLayout.OfChar>
            implements OfChar {

        public OfCharImpl(Function<? super ValueLayout.OfChar, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfShortImpl
            extends AbstractLayoutTransformer<ValueLayout.OfShort>
            implements OfShort {

        public OfShortImpl(Function<? super ValueLayout.OfShort, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfIntImpl
            extends AbstractLayoutTransformer<ValueLayout.OfInt>
            implements OfInt {

        public OfIntImpl(Function<? super ValueLayout.OfInt, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfFloatImpl
            extends AbstractLayoutTransformer<ValueLayout.OfFloat>
            implements OfFloat {

        public OfFloatImpl(Function<? super ValueLayout.OfFloat, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfLongImpl
            extends AbstractLayoutTransformer<ValueLayout.OfLong>
            implements OfLong {

        public OfLongImpl(Function<? super ValueLayout.OfLong, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfDoubleImpl
            extends AbstractLayoutTransformer<ValueLayout.OfDouble>
            implements OfDouble {

        public OfDoubleImpl(Function<? super ValueLayout.OfDouble, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfAddressLayoutImpl
            extends AbstractLayoutTransformer<java.lang.foreign.AddressLayout>
            implements OfAddressLayout {

        public OfAddressLayoutImpl(Function<? super java.lang.foreign.AddressLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

}
