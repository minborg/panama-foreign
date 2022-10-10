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
     * Recursively transforms the provided {@code layout} whereby the elements that can be
     * cast to the provided {@code type} (i.e. matches the type) will be replaced
     * by applying the provided {@code mapper}.
     * <p>
     * For example, the following snippet will recursively transform the provided {@code layout} to a layout containing
     * only value layouts that are in big endian byte order:
     * {@snippet lang = java:
     *     MemoryLayout bigEndiand = transform(layout, ValueLayout.class, vl -> vl.withOrder(ByteOrder.BIG_ENDIAN));
     * }
     * <p>
     * In this other example, the following snippet will recursively transform the provided {@code layout} so that
     * all sequence layouts will be converted (flattened) to struct layouts:
     * {@snippet lang = java:
     *     Function<SequenceLayout, MemoryLayout> mapper = sl-> {
     *         MemoryLayout[] memoryLayouts = new MemoryLayout[(int)sl.elementCount()];
     *         Arrays.fill(memoryLayouts,sl.elementLayout());
     *         return MemoryLayout.structLayout(memoryLayouts);
     *     };
     *     MemoryLayout flattened = transform(layout, SequenceLayout.class, mapper);
     * }
     *
     * @param layout the original layout to be transformed
     * @param type   the type(s) that shall be replaced
     * @param mapper to apply (must not return {@code null} when invoked)
     * @return the transformed layout
     * @param <T> type to transform
     */
    public static <T extends MemoryLayout> MemoryLayout transform(MemoryLayout layout,
                                                                  Class<T> type,
                                                                  Function<? super T, ? extends MemoryLayout> mapper) {
        Objects.requireNonNull(layout);
        Objects.requireNonNull(type);
        Objects.requireNonNull(mapper);

        if (type.isInstance(layout)) {
            layout = Objects.requireNonNull(mapper.apply(type.cast(layout)));
        }
        if (layout instanceof StructLayout structLayout) {
            return copyNameIfPresent(structLayout,
                    transformContentAndCopyAligment(type, mapper, structLayout, MemoryLayout::structLayout));
        }
        if (layout instanceof UnionLayout unionLayout) {
            return copyNameIfPresent(unionLayout,
                    transformContentAndCopyAligment(type, mapper, unionLayout, MemoryLayout::unionLayout));
        }
        if (layout instanceof SequenceLayout sequenceLayout) {
            return copyNameIfPresent(sequenceLayout,
                    MemoryLayout.sequenceLayout(sequenceLayout.elementCount(), transform(sequenceLayout.elementLayout(), type, mapper))
                            .withBitAlignment(sequenceLayout.bitAlignment()));
        }
        return layout;
    }

    /**
     * Recursively transforms the provided {@code layout} by applying the provided {@code mapper} on all members.
     *
     * @param layout the original layout to be transformed
     * @param mapper to apply (must not return {@code null} when invoked)
     * @return the transformed layout
     * @see #transform(MemoryLayout, Class, Function)
     */
    public static MemoryLayout transform(MemoryLayout layout,
                                         Function<? super MemoryLayout, ? extends MemoryLayout> mapper) {
        return transform(layout, MemoryLayout.class, mapper);
    }

    @SuppressWarnings("unchecked")
    private static <T extends MemoryLayout, G extends GroupLayout>
    G transformContentAndCopyAligment(Class<T> type,
                                      Function<? super T, ? extends MemoryLayout> mapper,
                                      G original,
                                      Function<MemoryLayout[], G> constructor) {
        return (G) constructor.apply(original.memberLayouts().stream()
                        .map(ml -> transform(ml, type, mapper))
                        .toArray(MemoryLayout[]::new))
                .withBitAlignment(original.bitAlignment());
    }

    private static MemoryLayout copyNameIfPresent(MemoryLayout source,
                                                  MemoryLayout target) {
        return source.name()
                .map(target::withName)
                .orElse(target);
    }

}
