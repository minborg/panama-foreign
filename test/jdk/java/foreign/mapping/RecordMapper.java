import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemorySegment;

public interface RecordMapper<T extends Record> {

    Class<T> type();

    GroupLayout layout();

    default T get(MemorySegment segment) {
        return get(segment, 0);
    }

    default T getAtIndex(MemorySegment segment, long index) {
        return get(segment, layout().byteSize()*index);
    }

    T get(MemorySegment segment, long offset);

    default void set(MemorySegment segment, T t) {
        set(segment, 0, t);
    }

    default void setAtIndex(MemorySegment segment, long index, T t) {
        set(segment, layout().byteSize() * index, t);
    }

    void set(MemorySegment segment, long offset, T t);


    // Add more methods and overrides

    static <T extends Record> RecordMapper<T> of(Class<T> type, GroupLayout layout) {
        return new RecordMapperImpl<>(type, layout);
    }

    record RecordMapperImpl<T extends Record>(Class<T> type, GroupLayout layout)
            implements RecordMapper<T> {

        @Override
        public T get(MemorySegment segment, long offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(MemorySegment segment, long offset, T t) {
            throw new UnsupportedOperationException();
        }

    }


/*    sealed interface Lookup {
        <T> T find(Class<T> type);

        Lookup andThenLookup(RecordMapper<?> mapper);

        static Lookup of(Collection<RecordMapper<?>> mappers) {
            Map<Class<?>, RecordMapper<?>> map = mappers.stream()
                    .collect(Collectors.toMap(RecordMapper::type, Function.identity()));

            return new LookupImpl(map);
        }
    }

    record LookupImpl(Map<Class<?>, RecordMapper<?>> map) implements Lookup {

        @SuppressWarnings("unchecked")
        @Override
        public <T> T find(Class<T> type) {
            return (T) map.get(type);
        }

        @Override
        public Lookup andThenLookup(RecordMapper<?> mapper) {
            var newMap = new HashMap<>(map);
            newMap.put(mapper.type(), mapper);
            return new LookupImpl(newMap);
        }
    }*/


}
