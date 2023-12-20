package jdk.internal.foreign.mapper.accessor;

public enum ValueType {
    VALUE(false), INTERFACE(true), RECORD(true);

    private final boolean virtual;

    ValueType(boolean virtual) {
        this.virtual = virtual;
    }

    public boolean isVirtual() {
        return virtual;
    }
}
