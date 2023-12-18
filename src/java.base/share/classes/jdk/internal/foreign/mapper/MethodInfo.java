package jdk.internal.foreign.mapper;

import java.lang.reflect.Method;

record MethodInfo(Key key,
                  Method method,
                  Class<?> type,
                  LayoutInfo layoutInfo,
                  long offset) {

    enum Cardinality {SCALAR, ARRAY}

    enum ValueType {
        VALUE(false), INTERFACE(true), RECORD(true);

        private final boolean virtual;

        ValueType(boolean virtual) {
            this.virtual = virtual;
        }

        public boolean isVirtual() {
            return virtual;
        }
    }

    enum AccessorType {GETTER, SETTER}

    /**
     * These are the various combinations that exists.
     */
    enum Key {
        SCALAR_VALUE_GETTER(Cardinality.SCALAR, ValueType.VALUE, AccessorType.GETTER),
        SCALAR_VALUE_SETTER(Cardinality.SCALAR, ValueType.VALUE, AccessorType.SETTER),
        SCALAR_INTERFACE_GETTER(Cardinality.SCALAR, ValueType.INTERFACE, AccessorType.GETTER),
        SCALAR_INTERFACE_SETTER(Cardinality.SCALAR, ValueType.INTERFACE, AccessorType.SETTER), // Unavailable for interfaces
        SCALAR_RECORD_GETTER(Cardinality.SCALAR, ValueType.RECORD, AccessorType.GETTER),
        SCALAR_RECORD_SETTER(Cardinality.SCALAR, ValueType.RECORD, AccessorType.SETTER),
        ARRAY_VALUE_GETTER(Cardinality.ARRAY, ValueType.VALUE, AccessorType.GETTER),
        ARRAY_VALUE_SETTER(Cardinality.ARRAY, ValueType.VALUE, AccessorType.SETTER),
        ARRAY_INTERFACE_GETTER(Cardinality.ARRAY, ValueType.INTERFACE, AccessorType.GETTER),
        ARRAY_INTERFACE_SETTER(Cardinality.ARRAY, ValueType.INTERFACE, AccessorType.SETTER),   // Unavailable for interfaces
        ARRAY_RECORD_GETTER(Cardinality.ARRAY, ValueType.RECORD, AccessorType.GETTER),
        ARRAY_RECORD_SETTER(Cardinality.ARRAY, ValueType.RECORD, AccessorType.SETTER);         // Todo

        private final Cardinality cardinality;
        private final ValueType valueType;
        private final AccessorType accessorType;

        Key(Cardinality cardinality, ValueType valueType, AccessorType accessorType) {
            this.cardinality = cardinality;
            this.valueType = valueType;
            this.accessorType = accessorType;
        }

        public Cardinality cardinality() {
            return cardinality;
        }

        public ValueType valueType() {
            return valueType;
        }

        public AccessorType accessorType() {
            return accessorType;
        }

        public static Key of(Cardinality cardinality,
                             ValueType valueType,
                             AccessorType accessorType) {

            for (Key k : Key.values()) {
                if (k.cardinality == cardinality && valueType == k.valueType && accessorType == k.accessorType) {
                    return k;
                }
            }
            throw new InternalError("Should not reach here");
        }
    }

}
