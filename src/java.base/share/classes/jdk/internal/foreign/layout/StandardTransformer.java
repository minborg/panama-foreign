package jdk.internal.foreign.layout;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.Transformer;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.util.function.UnaryOperator;

public record StandardTransformer(
        UnaryOperator<ValueLayout> valueTransformer,
        UnaryOperator<PaddingLayout> paddingTransformer,
        UnaryOperator<SequenceLayout> sequenceTransformer,
        UnaryOperator<StructLayout> structTransformer,
        UnaryOperator<UnionLayout> unionTransformer
) implements Transformer {

    public StandardTransformer() {
        this(
                UnaryOperator.identity(),
                UnaryOperator.identity(),
                (SequenceLayout s) -> MemoryLayout.sequenceLayout(s.elementCount(), transform(s.elementLayout())),
                (StructLayout s) -> MemoryLayout.structLayout(s.memberLayouts().toArray(MemoryLayout[]::new)),
                (UnionLayout u) -> MemoryLayout.unionLayout(u.memberLayouts().toArray(MemoryLayout[]::new))
        );
    }

    @Override
    public Transformer withValues(UnaryOperator<ValueLayout> valueTransformer) {
        return new StandardTransformer(valueTransformer, paddingTransformer, sequenceTransformer, structTransformer, unionTransformer);
    }

    @Override
    public Transformer withPaddings(UnaryOperator<PaddingLayout> paddingTransformer) {
        return new StandardTransformer(valueTransformer, paddingTransformer, sequenceTransformer, structTransformer, unionTransformer);
    }

    @Override
    public Transformer withSequences(UnaryOperator<SequenceLayout> sequenceTransformer) {
        return new StandardTransformer(valueTransformer, paddingTransformer, sequenceTransformer, structTransformer, unionTransformer);
    }

    @Override
    public Transformer withStructs(UnaryOperator<StructLayout> structTransformer) {
        return new StandardTransformer(valueTransformer, paddingTransformer, sequenceTransformer, structTransformer, unionTransformer);
    }

    @Override
    public Transformer withUnions(UnaryOperator<UnionLayout> unionTransformer) {
        return new StandardTransformer(valueTransformer, paddingTransformer, sequenceTransformer, structTransformer, unionTransformer);
    }

    @Override
    public MemoryLayout transform(MemoryLayout original) {
        return switch (original) {
            case ValueLayout valueLayout -> valueTransformer.apply(valueLayout);
            case PaddingLayout paddingLayout -> paddingTransformer.apply(paddingLayout);
            case SequenceLayout sequenceLayout -> sequenceTransformer.apply(sequenceLayout);
            case StructLayout structLayout -> structTransformer.apply(structLayout);
            case UnionLayout unionLayout -> unionTransformer.apply(unionLayout);
        };
    }
}
