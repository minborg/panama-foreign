package java.lang.foreign.mapper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.*;
import static java.util.stream.Collectors.toMap;

/**
 * A
 */
public final class RecordMapper {

    private RecordMapper() {
    }

    // Unions info can be derived form the ML

    // Todo: Return something better: getter/setter/ more info

    static <R extends Record> Function<MemorySegment, R> recordMapper(Class<R> type,
                                                                      StructLayout layout) {
        return recordMapper0(type, layout);
    }

    /**
     * {@return a nominal layout for the provided {@code recordType}}
     *
     * @param recordType of the Record
     * @param <R>  record recordType
     */
    public static <R extends Record> StructLayout nominalLayout(Class<R> recordType) {
        return nominalLayout0(recordType, recordType.getName());
    }

    static <R extends Record> Function<MemorySegment, R> recordMapper(Class<R> recordType) {
        return recordMapper(recordType, nominalLayout(recordType));
    }

    /**
     * {@return a new record}
     *
     * @param getter   to use
     * @param newValue to set
     * @param <R>      record type
     * @param <T>      value type
     */
    public static <R extends Record, T> R with(MethodReference<R, T> getter, T newValue) {
        return null;
    }


    private static StructLayout nominalLayout0(Class<?> recordType,
                                               String name) {
        var elements = Stream.of(recordType.getRecordComponents())
                .map(RecordMapper::nominalLayout0)
                .toArray(MemoryLayout[]::new);

        return MemoryLayout.structLayout(elements)
                .withName(name);
    }

    private static MemoryLayout nominalLayout0(RecordComponent component) {

        if (component.getType().isRecord()) {
            return nominalLayout0(component.getType(), component.getName());
        }

        MemoryLayout l = switch (component.getType().getName()) {
            case "byte", "java.lang.Byte" -> JAVA_BYTE;
            case "boolean", "java.lang.Boolean" -> JAVA_BOOLEAN;
            case "short", "java.lang.Short" -> JAVA_SHORT;
            case "char", "java.lang.Character" -> JAVA_CHAR;
            case "int", "java.lang.Integer" -> JAVA_INT;
            case "long", "java.lang.Long" -> JAVA_LONG;
            case "float", "java.lang.Float" -> JAVA_FLOAT;
            case "double", "java.lang.Double" -> JAVA_DOUBLE;
            default -> throw new UnsupportedOperationException(component.toString());
        };
        return l.withName(component.getName());
    }

    private static <R extends Record> Function<MemorySegment, R> recordMapper0(Class<R> type,
                                                                               StructLayout layout) {
        Map<String, RecordComponent> components = Stream.of(type.getRecordComponents())
                .collect(toMap(RecordComponent::getName, Function.identity()));

        // System.out.println("components = " + components);

        Map<String, MemoryLayout> layouts = layout.memberLayouts().stream()
                .collect(toMap(l -> l.name().orElseThrow(), Function.identity()));

        // System.out.println("layouts = " + layouts);

        var missingComponents = components.keySet().stream()
                .filter(l -> !layouts.containsKey(l))
                .toList();

        // System.out.println("missingComponents = " + missingComponents);

        // Todo: There might be a ctor that requires less fields...

        if (!missingComponents.isEmpty()) {
            throw new IllegalArgumentException("There is no mapping for " + missingComponents);
        }

        Class<?>[] ctorParameterTypes = components.values().stream()
                        .map(RecordComponent::getType)
                                .toArray(Class<?>[]::new);

        System.out.println("ctorParameterTypes = " + Arrays.toString(ctorParameterTypes));

        try {
            Constructor<R> canonicalConstructor = type.getDeclaredConstructor(ctorParameterTypes);

            // System.out.println("canonicalConstructor = " + canonicalConstructor);

            // Todo: Use LazyArray here
            Function<MemorySegment, Object[]> extractor = ms -> {
                var handles = layouts.keySet().stream()
                        .map(n -> layout.varHandle(PathElement.groupElement(n)))
                        .toArray(VarHandle[]::new);

                Object[] parameters = new Object[handles.length];
                for (int i = 0; i < handles.length; i++) {
                    parameters[i] = handles[i].get(ms);
                }
                return parameters;
            };

            // Todo: Cache per class and layout?
            return ms -> {
                try {
                    return canonicalConstructor.newInstance(extractor.apply(ms));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Unable to extract...");
                }
            };
        } catch (NoSuchMethodException nsme) {
            throw new IllegalArgumentException("There is no constructor in " + type.getName() + " for " + ctorParameterTypes);
        }
    }



}
