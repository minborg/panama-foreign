package jdk.internal.foreign.mapper.component;

import java.lang.foreign.SequenceLayout;
import java.util.List;

enum ContainerType {
    UNKNOWN, ARRAY, LIST;

    static ContainerType of(Class<?> componentType, SequenceLayout sl) {
        if (componentType.isArray()) {
            return ARRAY;
        }
        if (componentType.isAssignableFrom(List.class)) {
            return LIST;
        }
        throw new IllegalArgumentException("Unable to map '" + sl +
                "' because the component '" + componentType.getName() + " " + componentType.componentType().getName() + "' is not an array or a List.");
    }
}
