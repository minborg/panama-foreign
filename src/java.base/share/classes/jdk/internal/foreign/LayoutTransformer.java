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

public final class LayoutTransformer {

    private LayoutTransformer() {}

    public static MemoryLayout transform(MemoryLayout layout,
                                         Transformer<?> t) {

        // Breadth first
        MemoryLayout outer = transformFlat(t, layout);

        // Handle element transformation
        return switch (outer) {
            case SequenceLayout sl -> MemoryLayout.sequenceLayout(sl.elementCount(), transform(sl.elementLayout(), t));
            case GroupLayout gl -> MemoryLayout.structLayout(applyRecursively(gl, t));
            default -> layout;
        };
    }

    private static MemoryLayout transformFlat(Transformer<?> transformer, MemoryLayout l) {
        return switch (transformer) {
            case MemoryLayoutTransformer   t -> t.transform(l);
            case SequenceLayoutTransformer t when l instanceof SequenceLayout sl        -> t.transform(sl);
            case GroupLayoutTransformer    t when l instanceof GroupLayout gl           -> t.transform(gl);
            case StructLayoutTransformer   t when l instanceof StructLayout se          -> t.transform(se);
            case UnionLayoutTransformer    t when l instanceof UnionLayout uel          -> t.transform(uel);
            case PaddingLayoutTransformer  t when l instanceof PaddingLayout pl         -> t.transform(pl);
            case ValueLayoutTransformer    t when l instanceof ValueLayout vl           -> t.transform(vl);
            case OfBooleanTransformer      t when l instanceof ValueLayout.OfBoolean bl -> t.transform(bl);
            case OfByteTransformer         t when l instanceof ValueLayout.OfByte by    -> t.transform(by);
            case OfCharTransformer         t when l instanceof ValueLayout.OfChar ch    -> t.transform(ch);
            case OfShortTransformer        t when l instanceof ValueLayout.OfShort sh   -> t.transform(sh);
            case OfIntTransformer          t when l instanceof ValueLayout.OfInt in     -> t.transform(in);
            case OfFloatTransformer        t when l instanceof ValueLayout.OfFloat fl   -> t.transform(fl);
            case OfLongTransformer         t when l instanceof ValueLayout.OfLong lo    -> t.transform(lo);
            case OfDoubleTransformer       t when l instanceof ValueLayout.OfDouble db  -> t.transform(db);
            case AddressLayoutTransformer  t when l instanceof AddressLayout ad         -> t.transform(ad);
            // No transformation
            default -> l;
        };
    }

    private static MemoryLayout[] applyRecursively(GroupLayout groupLayout,
                                                   Transformer<? extends MemoryLayout> t) {
        return groupLayout.memberLayouts().stream()
                .map(l -> transformFlat(t, l))
                .toArray(MemoryLayout[]::new);
    }

    public sealed interface Transformer<T extends MemoryLayout> {
        MemoryLayout transform(T t);
    }

    public sealed interface MemoryLayoutTransformer extends Transformer<MemoryLayout>{
        static MemoryLayoutTransformer of(Function<? super MemoryLayout, ? extends MemoryLayout> op) {
            return new MemoryLayoutTransformerImpl(op);
        }
    }

    public sealed interface SequenceLayoutTransformer extends Transformer<SequenceLayout>{
        static SequenceLayoutTransformer of(Function<? super SequenceLayout, ? extends MemoryLayout> op) {
            return new SequenceLayoutTransformerImpl(op);
        }
    }

    public sealed interface GroupLayoutTransformer extends Transformer<GroupLayout>{
        static GroupLayoutTransformer of(Function<? super GroupLayout, ? extends MemoryLayout> op) {
            return new GroupLayoutTransformerImpl(op);
        }
    }
    public sealed interface StructLayoutTransformer extends Transformer<StructLayout>{
        static StructLayoutTransformer of(Function<? super StructLayout, ? extends MemoryLayout> op) {
            return new StructLayoutTransformerImpl(op);
        }
    }
    public sealed interface UnionLayoutTransformer extends Transformer<UnionLayout>{
        static UnionLayoutTransformer of(Function<? super UnionLayout, ? extends MemoryLayout> op) {
            return new UnionLayoutTransformerImpl(op);
        }
    }

    public sealed interface PaddingLayoutTransformer extends Transformer<PaddingLayout>{
        static PaddingLayoutTransformer of(Function<? super PaddingLayout, ? extends MemoryLayout> op) {
            return new PaddingLayoutTransformerImpl(op);
        }
    }

    public sealed interface ValueLayoutTransformer extends Transformer<ValueLayout> {
        static ValueLayoutTransformer of(Function<? super ValueLayout, ? extends MemoryLayout> op) {
            return new ValueLayoutTransformerImpl(op);
        }
    }
    public sealed interface OfBooleanTransformer extends Transformer<ValueLayout.OfBoolean> {
        static OfBooleanTransformer of(Function<? super ValueLayout.OfBoolean, ? extends MemoryLayout> op) {
            return new OfBooleanTransformerImpl(op);
        }
    }
    public sealed interface OfByteTransformer extends Transformer<ValueLayout.OfByte> {
        static OfByteTransformer of(Function<? super ValueLayout.OfByte, ? extends MemoryLayout> op) {
            return new OfByteTransformerImpl(op);
        }
    }
    public sealed interface OfCharTransformer extends Transformer<ValueLayout.OfChar> {
        static OfCharTransformer of(Function<? super ValueLayout.OfChar, ? extends MemoryLayout> op) {
            return new OfCharTransformerImpl(op);
        }
    }
    public sealed interface OfShortTransformer extends Transformer<ValueLayout.OfShort> {
        static OfShortTransformer of(Function<? super ValueLayout.OfShort, ? extends MemoryLayout> op) {
            return new OfShortTransformerImpl(op);
        }
    }
    public sealed interface OfIntTransformer extends Transformer<ValueLayout.OfInt> {
        static OfIntTransformer of(Function<? super ValueLayout.OfInt, ? extends MemoryLayout> op) {
            return new OfIntTransformerImpl(op);
        }
    }
    public sealed interface OfFloatTransformer extends Transformer<ValueLayout.OfFloat> {
        static OfFloatTransformer of(Function<? super ValueLayout.OfFloat, ? extends MemoryLayout> op) {
            return new OfFloatTransformerImpl(op);
        }
    }
    public sealed interface OfLongTransformer extends Transformer<ValueLayout.OfLong> {
        static OfLongTransformer of(Function<? super ValueLayout.OfLong, ? extends MemoryLayout> op) {
            return new OfLongTransformerImpl(op);
        }
    }
    public sealed interface OfDoubleTransformer extends Transformer<ValueLayout.OfDouble> {
        static OfDoubleTransformer of(Function<? super ValueLayout.OfDouble, ? extends MemoryLayout> op) {
            return new OfDoubleTransformerImpl(op);
        }
    }
    public sealed interface AddressLayoutTransformer extends Transformer<AddressLayout> {
        static AddressLayoutTransformer of(Function<? super AddressLayout, ? extends MemoryLayout> op) {
            return new AddressLayoutTransformerImpl(op);
        }
    }


    // Implementations

    static final class MemoryLayoutTransformerImpl
            extends AbstractTransformerImpl<MemoryLayout>
            implements MemoryLayoutTransformer {

        public MemoryLayoutTransformerImpl(Function<? super MemoryLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class SequenceLayoutTransformerImpl
            extends AbstractTransformerImpl<SequenceLayout>
            implements SequenceLayoutTransformer {

        public SequenceLayoutTransformerImpl(Function<? super SequenceLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class GroupLayoutTransformerImpl
            extends AbstractTransformerImpl<GroupLayout>
            implements GroupLayoutTransformer {

        public GroupLayoutTransformerImpl(Function<? super GroupLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class StructLayoutTransformerImpl
            extends AbstractTransformerImpl<StructLayout>
            implements StructLayoutTransformer {

        public StructLayoutTransformerImpl(Function<? super StructLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class UnionLayoutTransformerImpl
            extends AbstractTransformerImpl<UnionLayout>
            implements UnionLayoutTransformer {

        public UnionLayoutTransformerImpl(Function<? super UnionLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class PaddingLayoutTransformerImpl
            extends AbstractTransformerImpl<PaddingLayout>
            implements PaddingLayoutTransformer {

        public PaddingLayoutTransformerImpl(Function<? super PaddingLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class ValueLayoutTransformerImpl
            extends AbstractTransformerImpl<ValueLayout>
            implements ValueLayoutTransformer {

        public ValueLayoutTransformerImpl(Function<? super ValueLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfBooleanTransformerImpl
            extends AbstractTransformerImpl<ValueLayout.OfBoolean>
            implements OfBooleanTransformer {

        public OfBooleanTransformerImpl(Function<? super ValueLayout.OfBoolean, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfByteTransformerImpl
            extends AbstractTransformerImpl<ValueLayout.OfByte>
            implements OfByteTransformer {

        public OfByteTransformerImpl(Function<? super ValueLayout.OfByte, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfCharTransformerImpl
            extends AbstractTransformerImpl<ValueLayout.OfChar>
            implements OfCharTransformer {

        public OfCharTransformerImpl(Function<? super ValueLayout.OfChar, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfShortTransformerImpl
            extends AbstractTransformerImpl<ValueLayout.OfShort>
            implements OfShortTransformer {

        public OfShortTransformerImpl(Function<? super ValueLayout.OfShort, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfIntTransformerImpl
            extends AbstractTransformerImpl<ValueLayout.OfInt>
            implements OfIntTransformer {

        public OfIntTransformerImpl(Function<? super ValueLayout.OfInt, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfFloatTransformerImpl
            extends AbstractTransformerImpl<ValueLayout.OfFloat>
            implements OfFloatTransformer {

        public OfFloatTransformerImpl(Function<? super ValueLayout.OfFloat, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfLongTransformerImpl
            extends AbstractTransformerImpl<ValueLayout.OfLong>
            implements OfLongTransformer {

        public OfLongTransformerImpl(Function<? super ValueLayout.OfLong, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class OfDoubleTransformerImpl
            extends AbstractTransformerImpl<ValueLayout.OfDouble>
            implements OfDoubleTransformer {

        public OfDoubleTransformerImpl(Function<? super ValueLayout.OfDouble, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    static final class AddressLayoutTransformerImpl
            extends AbstractTransformerImpl<AddressLayout>
            implements AddressLayoutTransformer {

        public AddressLayoutTransformerImpl(Function<? super AddressLayout, ? extends MemoryLayout> op) {
            super(op);
        }
    }

    private sealed static class AbstractTransformerImpl<T extends MemoryLayout>
            implements Transformer<T> {

        private final Function<? super T, ? extends MemoryLayout> op;

        public AbstractTransformerImpl(Function<? super T, ? extends MemoryLayout> op) {
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
    }

}
