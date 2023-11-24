package jdk.internal.foreign.mapper.component;

import java.lang.foreign.SequenceLayout;
import java.util.List;

enum ContainerType {
    ARRAY, LIST;

    static ContainerType of(Class<?> recordComponentType,
                            SequenceLayout sl) {
        if (recordComponentType.isArray()) {
            return ARRAY;
        }
        if (recordComponentType.isAssignableFrom(List.class)) {
            return LIST;
        }
        throw new IllegalArgumentException("Unable to map '" + sl + "' " +
                "because the component '" + recordComponentType + "' is not an array or a List.");
    }
}
