package java.lang.foreign.mapper;

import jdk.internal.foreign.mapper.MapperUtil;

import java.lang.foreign.GroupLayout;
import java.lang.invoke.MethodHandles;

/**
 * A layout generator that can extract a "natural-layout" from certain
 * class type's components.
 * <p>
 * Arrays do not have a natural layout as the length of arrays is not known.
 *
 */
public final class LayoutGenerators {

    // Suppresses default constructor, ensuring non-instantiability.
    private LayoutGenerators() {}

    /**
     * {@return a {@linkplain GroupLayout} that reflects a "natural-layout" of
     *          the components in the provided interface {@code type}}
     * <p>
     * The {@linkplain GroupLayout#name() name} of the returned group layout will be
     * the same as the provided {@code type}'s {@linkplain Class#getName() name}.
     *
     * @param type to derive a group layout from
     * @param <T> the type to analyse
     * @throws IllegalArgumentException if the provided {@code type} is
     *         not an interface, is hidden or is sealed
     * @throws IllegalArgumentException if the provided {@code type} cannot be
     *         reflectively analysed
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         components for which there are no exact mapping (of names and types) in
     *         the provided {@code layout} or if the provided {@code type} is not public
     *         or if the method is otherwise unable to create a group layout as specified
     *         above
     */
    public static <T> GroupLayout ofInterface(Class<T> type) {
        MapperUtil.requireImplementableInterfaceType(type);
        return MapperUtil.groupLayoutOf(type);
    }

    /**
     * {@return a {@linkplain GroupLayout} that reflects a "natural-layout" of
     *          the components in the provided record {@code type}}
     * <p>
     * The {@linkplain GroupLayout#name() name} of the returned group layout will be
     * the same as the provided {@code type}'s {@linkplain Class#getName() name}.
     * <p>
     * Reflective analysis on the provided {@code type} will be made using the
     * {@linkplain MethodHandles.Lookup#publicLookup() public lookup}.
     *
     * @param type to derive a group layout from
     * @param <T> the type to analyse
     * @throws IllegalArgumentException if the provided {@code type} is
     *         not a true subclass of {@linkplain java.lang.Record} or if it
     *         is identical to the class {@linkplain java.lang.Record}
     * @throws IllegalArgumentException if the provided {@code type} cannot be
     *         reflectively analysed
     * @throws IllegalArgumentException if the provided interface {@code type} contains
     *         components for which there are no exact mapping (of names and types) in
     *         the provided {@code layout} or if the provided {@code type} is not public
     *         or if the method is otherwise unable to create a group layout as specified
     *         above
     */
    public static <T extends Record> GroupLayout ofRecord(Class<T> type) {
        MapperUtil.requireRecordType(type);
        return MapperUtil.groupLayoutOf(type);
    }

}
