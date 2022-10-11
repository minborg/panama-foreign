package jdk.internal.foreign;

import java.lang.foreign.*;
import java.util.Objects;
import java.util.function.Function;

/**
 * Temporary internal class for holding useful methods that could later be
 * exposed in some public class like java.lang.foreign.MemoryLayout
 */
public final class InternalMemoryLayout {

    private InternalMemoryLayout() {
    }

    /**
     * Recursively transforms the provided {@code layout} whereby elements will be replaced
     * by applying the provided {@code transformer}.
     * <p>
     * For example, the following snippet will recursively transform the provided {@code layout} to a layout containing
     * only value layouts that are in big endian byte order:
     * {@snippet lang = java:
     *     LayoutTransformer toBigEndian = l -> (l instanceof ValueLayout vl)
     *             ? vl.withOrder(ByteOrder.BIG_ENDIAN)
     *             : l;
     *     MemoryLayout bigEndian = transform(layout, toBigEndian);
     *}
     * or shorter:
     * {@snippet lang = java:
     *     MemoryLayout bigEndian =
     *         transform(layout, LayoutTransformer.matching(ValueLayout.class, vl -> vl.withOrder(ByteOrder.BIG_ENDIAN)));
     *}
     *
     * <p>
     * In this other example, the following snippet will recursively transform the provided {@code layout} so that
     * all sequence layouts will be converted (flattened) to struct layouts:
     * {@snippet lang = java:
     *
     *     LayoutTransformer transformer = ml -> (ml instanceof SequenceLayout sl)
     *             ? MemoryLayout.structLayout(IntStream.range(0, (int) sl.elementCount())
     *                 .mapToObj(i -> sl.elementLayout())
     *                 .toArray(MemoryLayout[]::new))
     *             : ml;
     *
     *     MemoryLayout flattened = transform(layout, transformer);
     *}
     *
     * @param layout the original layout to be transformed
     * @param transformer to apply (must not return {@code null} when invoked)
     * @return the transformed layout
     */
    public static MemoryLayout transform(MemoryLayout layout,
                                         LayoutTransformer transformer) {
        Objects.requireNonNull(layout);
        Objects.requireNonNull(transformer);

        layout = Objects.requireNonNull(transformer.transform(layout));

        if (layout instanceof StructLayout structLayout) {
            return copyNameIfPresent(structLayout,
                    transformContentAndCopyAlignment(transformer, structLayout, MemoryLayout::structLayout));
        }
        if (layout instanceof UnionLayout unionLayout) {
            return copyNameIfPresent(unionLayout,
                    transformContentAndCopyAlignment(transformer, unionLayout, MemoryLayout::unionLayout));
        }
        if (layout instanceof SequenceLayout sequenceLayout) {
            return copyNameIfPresent(sequenceLayout,
                    MemoryLayout.sequenceLayout(sequenceLayout.elementCount(), transform(sequenceLayout.elementLayout(), transformer))
                            .withBitAlignment(sequenceLayout.bitAlignment()));
        }
        return layout;
    }


    private static MemoryLayout copyNameIfPresent(MemoryLayout source,
                                                  MemoryLayout target) {
        return source.name()
                .map(target::withName)
                .orElse(target);
    }

    @SuppressWarnings("unchecked")
    private static <G extends GroupLayout>
    G transformContentAndCopyAlignment(LayoutTransformer transformer,
                                       G original,
                                       Function<MemoryLayout[], G> constructor) {
        return (G) constructor.apply(original.memberLayouts().stream()
                        .map(ml -> transform(ml, transformer))
                        .toArray(MemoryLayout[]::new))
                .withBitAlignment(original.bitAlignment());
    }

}
