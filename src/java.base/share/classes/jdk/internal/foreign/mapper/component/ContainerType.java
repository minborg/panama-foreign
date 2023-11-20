package jdk.internal.foreign.mapper.component;

import java.util.List;

enum ContainerType {
    UNKNOWN, ARRAY, LIST;

    static ContainerType of(Class<?> componentType) {
        if (componentType.isArray()) {
            return ARRAY;
        }
        if (componentType.isAssignableFrom(List.class)) {
            return LIST;
        }
        return UNKNOWN;
    }
}
