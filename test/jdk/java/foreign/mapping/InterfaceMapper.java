import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemorySegment;

public interface InterfaceMapper<T> {

    Class<T> type();

    GroupLayout layout();

    default T create(MemorySegment segment) {
        return create(segment, 0);
    }

    default T createAtIndex(MemorySegment segment, long index) {
        return create(segment, layout().byteSize()*index);
    }

    T create(MemorySegment segment, long offset);


    // Add more methods and overrides

    static <T> InterfaceMapper<T> of(Class<T> type, GroupLayout layout) {
        /*
         * Verify that the Class object actually represents an
         * interface.
         */
        if (!type.isInterface()) {
            throw new IllegalArgumentException(type.getName() + " is not an interface");
        }

        if (type.isHidden()) {
            throw new IllegalArgumentException(type.getName() + " is a hidden interface");
        }

        if (type.isSealed()) {
            throw new IllegalArgumentException(type.getName() + " is a sealed interface");
        }

        return new InterfaceMapperImpl<>(type, layout);
    }


    final class InterfaceMapperImpl<T> implements InterfaceMapper<T> {

        private final Class<T> type;
        private final GroupLayout layout;

        public InterfaceMapperImpl(Class<T> type, GroupLayout layout) {
            this.type = type;
            this.layout = layout;
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public GroupLayout layout() {
            return layout;
        }

        @Override
        public T create(MemorySegment segment, long offset) {
            throw new UnsupportedOperationException();
        }
    }


}
