package jdk.internal.foreign;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.Transformer;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.*;

public final class TransformerSupport {

    private TransformerSupport() {
    }

    public record TransformerImpl<T extends MemoryLayout>(
            @Override Class<T> type,
            Function<? super T, ? extends MemoryLayout> op
    ) implements Transformer<T> {

        @Override
        public MemoryLayout transform(T original) {
            return op.apply(original);
        }

        @Override
        public String toString() {
            return "TransformerImpl[type=" + type.getSimpleName() + ", op=" + op + "]";
        }
    }

    public static <T extends MemoryLayout>
    Transformer<T> transformer(Class<T> type,
                               Function<? super T, ? extends MemoryLayout> op) {
        return new TransformerImpl<>(type, op);
    }

    public static MemoryLayout transform(MemoryLayout original,
                                         Transformer<?> t) {
        // Breadth first
        MemoryLayout outer = shallowlyTransform(original, t);

        // Handle element transformation
        return switch (outer) {
            case SequenceLayout sl ->
                    copyAttributes(
                            outer,
                            MemoryLayout.sequenceLayout(sl.elementCount(), transform(sl.elementLayout(), t)));
            case GroupLayout gl ->
                    copyAttributes(
                            outer,
                            MemoryLayout.structLayout(applyRecursively(gl, t)));
            default -> outer;
        };
    }

    @SuppressWarnings("unchecked")
    private static <T extends MemoryLayout> T copyAttributes(T source, T target) {
        MemoryLayout result = target.withByteAlignment(source.byteAlignment());
        if (source.name().isPresent()) {
            result = result.withName(source.name().orElseThrow());
        } else {
            result = result.withoutName();
        }
        return (T) result;
    }

    private static MemoryLayout[] applyRecursively(GroupLayout groupLayout,
                                                   Transformer<? extends MemoryLayout> t) {
        return groupLayout.memberLayouts().stream()
                .map(l -> transform(l, t))
                .toArray(MemoryLayout[]::new);
    }

    @SuppressWarnings("unchecked")
    public static MemoryLayout shallowlyTransform(MemoryLayout original,
                                                  Transformer<?> t) {
        return switch (original) {
            case MemoryLayout ml when t.type().equals(MemoryLayout.class) ->
                    ((Transformer<MemoryLayout>) t).transform(ml);
            case SequenceLayout sl when t.type().equals(SequenceLayout.class) ->
                    ((Transformer<SequenceLayout>) t).transform(sl);
            case GroupLayout gl when t.type().equals(GroupLayout.class) ->
                    ((Transformer<GroupLayout>) t).transform(gl);
            case StructLayout sl when t.type().equals(StructLayout.class) ->
                    ((Transformer<StructLayout>) t).transform(sl);
            case UnionLayout ul when t.type().equals(UnionLayout.class) ->
                    ((Transformer<UnionLayout>) t).transform(ul);
            case PaddingLayout pl when t.type().equals(PaddingLayout.class) ->
                    ((Transformer<PaddingLayout>) t).transform(pl);
            case ValueLayout vl when t.type().equals(ValueLayout.class) ->
                    ((Transformer<ValueLayout>) t).transform(vl);
            case OfBoolean bl when t.type().equals(OfBoolean.class) ->
                    ((Transformer<OfBoolean>) t).transform(bl);
            case OfByte by when t.type().equals(OfByte.class) ->
                    ((Transformer<OfByte>) t).transform(by);
            case OfChar ch when t.type().equals(OfChar.class) ->
                    ((Transformer<OfChar>) t).transform(ch);
            case OfShort sh when t.type().equals(OfShort.class) ->
                    ((Transformer<OfShort>) t).transform(sh);
            case OfInt in when t.type().equals(OfInt.class) ->
                    ((Transformer<OfInt>) t).transform(in);
            case OfFloat fl when t.type().equals(OfFloat.class) ->
                    ((Transformer<OfFloat>) t).transform(fl);
            case OfLong lo when t.type().equals(OfLong.class) ->
                    ((Transformer<OfLong>) t).transform(lo);
            case OfDouble db when t.type().equals(OfDouble.class) ->
                    ((Transformer<OfDouble>) t).transform(db);
            case AddressLayout ad when t.type().equals(AddressLayout.class) ->
                    ((Transformer<AddressLayout>) t).transform(ad);
            // No match so just return the original layout
            default -> original;
        };
    }

}
