import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

public interface Mapper<T> {

    Class<T> type();

    GroupLayout layout();

    default T get(MemorySegment segment) {
        return get(segment, 0);
    }

    default T getAtIndex(MemorySegment segment, long index) {
        return get(segment, layout().byteSize() * index);
    }

    T get(MemorySegment segment, long offset);

    interface OfInterface<T> extends Mapper<T> {
    }

    interface OfRecord<T extends Record> extends Mapper<T> {

        default void set(MemorySegment segment, T t) {
            set(segment, 0, t);
        }

        default void setAtIndex(MemorySegment segment, long index, T t) {
            set(segment, layout().byteSize() * index, t);
        }

        void set(MemorySegment segment, long offset, T t);

    }

    static <T> Mapper.OfInterface<T> ofInterface(Class<T> type, GroupLayout layout) {
        Objects.requireNonNull(type);
        if (!type.isInterface()) {
            throw new IllegalArgumentException(type.getName() + " is not an interface");
        }

        if (type.isHidden()) {
            throw new IllegalArgumentException(type.getName() + " is a hidden interface");
        }

        if (type.isSealed()) {
            throw new IllegalArgumentException(type.getName() + " is a sealed interface");
        }
        Objects.requireNonNull(layout);

        throw new UnsupportedOperationException();
    }

    static <T extends Record> Mapper.OfRecord<T> ofRecord(Class<T> type, GroupLayout layout) {
        Objects.requireNonNull(type);
        if (type.equals(Record.class)) {
            throw new IllegalArgumentException(type.getName() + " is not a real Record");
        }
        Objects.requireNonNull(layout);
        throw new UnsupportedOperationException();
    }

}
