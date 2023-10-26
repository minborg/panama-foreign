import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

public final class NativeSupport {

    private NativeSupport() {}

    public static <T> T link(Class<T> type,
                             SymbolLookup symbolLookup,
                             Function<String, LinkInfo> infoLookup) {
    return null;
    }

    interface Builder<T> {

        Builder<T> withDescription(String name, MemoryLayout resLayout, MemoryLayout... argLayouts);
        Builder<T> withVoidDescription(String name, MemoryLayout... argLayouts);
        Builder<T> withDescription(String name, FunctionDescriptor descriptor);
        Builder<T> withOptions(String name, Linker.Option... options);
        Builder<T> withOptions(Linker.Option... options);

        T build();

    }

    interface Builder2<T> {

        interface Configurator<T> {
            Configurator<T> description(MemoryLayout resLayout, MemoryLayout... argLayouts);
            Configurator<T> descriptionVoid(MemoryLayout... argLayouts);
            Configurator<T> options(Linker.Option... options);
            Builder2<T> and();
        }

        Builder2<T> withOptions(Linker.Option... options);
        Configurator<T> with(String name);

        T build();

    }

    static <T> Builder<T> builder(Class<T> type, SymbolLookup symbolLookup) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(symbolLookup);
        return new BuilderImpl<>(type, symbolLookup);
    }

    static <T> Builder2<T> builder2(Class<T> type, SymbolLookup symbolLookup) {
        return null;
    }


    public record LinkInfo(FunctionDescriptor descriptor, Linker.Option... options){

        public static LinkInfo of(FunctionDescriptor descriptor, Linker.Option... options) {
            return new LinkInfo(descriptor, options);
        }

    }

    private static final class BuilderImpl<T> implements Builder<T> {

        private final Class<T> type;
        private final SymbolLookup symbolLookup;
        private Linker.Option[] generalOptions;
        private final Map<String, FunctionDescriptor> descriptors;
        private final Map<String, Linker.Option[]> options;
        private final Map<String, Method> methods;

        public BuilderImpl(Class<T> type, SymbolLookup symbolLookup) {
            this.type = type;
            this.symbolLookup = symbolLookup;
            this.generalOptions = null;
            this.descriptors = new HashMap<>();
            this.options = new HashMap<>();
            if (!type.isInterface()) {
                throw new IllegalArgumentException("Not an interface: "+type);
            }
            if (!type.isSealed()) {
                throw new IllegalArgumentException("Sealed interface: "+type);
            }
            this.methods = Arrays.stream(type.getMethods())
                    .filter(m -> !Modifier.isStatic(m.getModifiers()))
                    .filter(not(Method::isDefault))
                    .collect(Collectors.toUnmodifiableMap(Method::getName, Function.identity(), (a, b) -> {
                        throw new IllegalArgumentException("Duplicate methods: " + a);
                    }));
        }

        @Override
        public Builder<T> withDescription(String name, MemoryLayout resLayout, MemoryLayout... argLayouts) {
            descriptors.put(requireNameExists(name), FunctionDescriptor.of(resLayout, argLayouts));
            return this;
        }

        @Override
        public Builder<T> withVoidDescription(String name, MemoryLayout... argLayouts) {
            descriptors.put(requireNameExists(name), FunctionDescriptor.ofVoid(argLayouts));
            return this;
        }

        @Override
        public Builder<T> withDescription(String name, FunctionDescriptor descriptor) {
            descriptors.put(requireNameExists(name), descriptor);
            return this;
        }

        @Override
        public Builder<T> withOptions(String name, Linker.Option... options) {
            this.options.put(requireNameExists(name), options);
            return this;
        }

        @Override
        public Builder<T> withOptions(Linker.Option... options) {
            this.generalOptions = options;
            return this;
        }

        @Override
        public T build() {
            Set<String> missingMethods = new HashSet<>(methods.keySet());
            missingMethods.removeAll(descriptors.keySet());
            if (!missingMethods.isEmpty()) {
                throw new IllegalStateException("Missing methods: " + missingMethods);
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "Builder for " + type;
        }

        private String requireNameExists(String name) {
            Objects.requireNonNull(name);
            if (!methods.containsKey(name)) {
                throw new IllegalArgumentException("There is no public instance method named '" + name + "' in the interface: " + type);
            }
            return name;
        }

    }


}
