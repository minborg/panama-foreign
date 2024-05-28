package java.lang.foreign.mapper;

import jdk.internal.foreign.mapper.InternalNaturalLayout;
import jdk.internal.foreign.mapper.MapperUtil;

import java.lang.foreign.GroupLayout;
import java.lang.invoke.MethodHandles;

/**
 * A natural layout class that can extract a "natural-layout" from certain
 * class type's components.
 * <p>
 * Arrays do not have a natural layout as the length of arrays is not known.
 *
 */
public final class NaturalLayout {

    // Suppresses default constructor, ensuring non-instantiability.
    private NaturalLayout() {}

    /**
     * {@return a {@linkplain GroupLayout} that reflects the "natural-layout" of
     *          the components in the provided record {@code type}}
     * <p>
     * Reflective analysis on the provided {@code type} will be made using the
     * MethodHandles.Lookup.publicLookup().
     *
     * @param type to derive a group layout from
     * @param <T> the type to analyse
     * @throws IllegalArgumentException if the provided {@code type} is
     *         not a true subclass of {@linkplain java.lang.Record} or if it
     *         is identical to the class {@linkplain java.lang.Record}
     * @throws IllegalArgumentException if the provided {@code type} cannot be
     *         reflectively analysed
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         components for which there are no natural layout (e.g. arrays)
     *         or if the provided {@code type} is not public
     */
    public static <T extends Record> GroupLayout ofRecord(Class<T> type) {
        MapperUtil.requireRecord(type);
        return InternalNaturalLayout.groupLayoutOf(type);
    }

}
