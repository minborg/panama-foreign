package jdk.internal.foreign.mapper;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.mapper.PrimitiveMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;

import static java.lang.invoke.MethodHandles.*;
import static jdk.internal.foreign.mapper.MapperUtil.LOCAL_LOOKUP;

// Todo: Once "longVal instanceof int" (for example) becomes available, we should do that instead.

// see https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.5-320
public final class PrimitiveMapperUtil {

    private PrimitiveMapperUtil() {}

    enum Mappable {
        BYTE(byte.class),
        SHORT(short.class),
        CHAR(char.class),
        INT(int.class),
        LONG(long.class),
        FLOAT(float.class),
        DOUBLE(double.class),
        BOOLEAN(boolean.class);

        Mappable(Class<?> carrier) {
            this.carrier = carrier;
        }

        private final Class<?> carrier;

        Class<?> carrier() {
            return carrier;
        }

        static Mappable forPrimitive(Class<?> carrier) {
            return switch (carrier) {
                case Class<?> c when c.equals(byte.class) -> BYTE;
                case Class<?> c when c.equals(short.class) -> SHORT;
                case Class<?> c when c.equals(char.class) -> CHAR;
                case Class<?> c when c.equals(int.class) -> INT;
                case Class<?> c when c.equals(long.class) -> LONG;
                case Class<?> c when c.equals(float.class) -> FLOAT;
                case Class<?> c when c.equals(double.class) -> DOUBLE;
                case Class<?> c when c.equals(boolean.class) -> BOOLEAN;
                default -> throw new IllegalArgumentException(carrier.toString());
            };
        }

    }

    private static final MethodHandle[][] handles = computeHandles();

    public static <T> PrimitiveMapper<T> of(ValueLayout l, Class<T> t) {
        return of(l, getter(l, t), setter(l, t));
    }

    private static MethodHandle getter(ValueLayout from, Class<?> to) {
        assertValidLayout(from, from.carrier());
        MethodHandle getter = MapperUtil.MemorySegmentAccessor.getter(from);
        return from.carrier() == to
                ? getter
                : filterReturnValue(getter, handle(from.carrier(), to));
    }

    private static MethodHandle setter(ValueLayout from, Class<?> to) {
        assertValidLayout(from, from.carrier());
        MethodHandle getter = MapperUtil.MemorySegmentAccessor.setter(from);
        return from.carrier() == to
                ? getter
                : filterArgument2(getter, handle(to, from.carrier()));
    }

    static MethodHandle handle(Class<?> from, Class<?> to) {
        MethodHandle h = handles[Mappable.forPrimitive(from).ordinal()][Mappable.forPrimitive(to).ordinal()];
        if (h == null) {
            throw new IllegalArgumentException("Cannot convert " + from + " to " + to);
        }
        return h;
    }

    private static void assertValidLayout(ValueLayout layout, Class<?> type) {
        if (layout instanceof AddressLayout) {
            throw noMapper(layout, type);
        }
        if (layout instanceof ValueLayout.OfBoolean) {
            if (layout.carrier() != type) {
                throw noMapper(layout, type);
            }
        }
    }

    private static MethodHandle filterArgument2(MethodHandle handle, MethodHandle filter) {
        return MethodHandles.filterArguments(handle, 2, filter);
    }

    private static ArithmeticException overflow(String name, Object value) {
        return new ArithmeticException(name + " overflow:" + value);
    }

    private static IllegalArgumentException noMapper(ValueLayout layout, Class<?> target) {
        return new IllegalArgumentException("There is no primitive mapper from " + layout + " to " + target);
    }

    @SuppressWarnings("unchecked")
    private static <T> PrimitiveMapper<T> of(ValueLayout layout,
                                             MethodHandle getter,
                                             MethodHandle setter) {
        return (PrimitiveMapper<T>) new SegmentMapperImpl<>(layout.carrier(), layout, getter, setter);
    }


    static MethodHandle[][] computeHandles() {
        MethodHandle[][] result = new MethodHandle[Mappable.values().length][Mappable.values().length];
        for (Mappable from:Mappable.values()) {
            for (Mappable to:Mappable.values()) {
                MethodHandle handle;
                try {
                    if (from == to) {
                        // identity
                        handle = null; // Will never be used
                    } else if (from == Mappable.BOOLEAN || to == Mappable.BOOLEAN) {
                        // N/A
                        handle = null;
                    } else if (from.ordinal() < to.ordinal()) {
                        // widen
                        handle = MethodHandles.identity(from.carrier());
                        handle = handle.asType(handle.type().changeReturnType(to.carrier()));
                    } else {
                        // narrow
                        // Todo: exact
                        handle = MethodHandles.identity(from.carrier());
                        handle = handle.asType(handle.type().changeReturnType(to.carrier()));
                    }
                } catch (WrongMethodTypeException e) {

                    try {
                        handle = LOCAL_LOOKUP.findStatic(PrimitiveMapperUtil.class, "to_"+to.carrier.toString(), MethodType.methodType(to.carrier(), from.carrier()));
                    } catch (ReflectiveOperationException re) {
                        throw new ExceptionInInitializerError("unable to look up " + to.carrier() + " to_" + to.carrier().toString() + "(" + from.carrier() + ")");
                    }
                }
                result[from.ordinal()][to.ordinal()] = handle;
            }
        }
        return result;
    }


    // byte

    static byte to_byte(short value) {
        if ((byte)value != value) {
            throw overflow("byte", value);
        }
        return (byte)value;
    }

    static byte to_byte(char value) {
        if ((byte)value != value) {
            throw overflow("byte", value);
        }
        return (byte)value;
    }

    static byte to_byte(int value) {
        if ((byte)value != value) {
            throw overflow("byte", value);
        }
        return (byte)value;
    }

    static byte to_byte(long value) {
        if ((byte)value != value) {
            throw overflow("byte", value);
        }
        return (byte)value;
    }

    static byte to_byte(float value) {
        if ((byte)value != value) {
            throw overflow("byte", value);
        }
        return (byte)value;
    }

    static byte to_byte(double value) {
        if ((byte)value != value) {
            throw overflow("byte", value);
        }
        return (byte)value;
    }


    // short

    static short to_short(char value) {
        if ((short)value != value) {
            throw overflow("short", value);
        }
        return (short)value;
    }

    static short to_short(int value) {
        if ((short)value != value) {
            throw overflow("short", value);
        }
        return (short)value;
    }

    static short to_short(long value) {
        if ((short)value != value) {
            throw overflow("short", value);
        }
        return (short)value;
    }

    static short to_short(float value) {
        if ((short)value != value) {
            throw overflow("short", value);
        }
        return (short)value;
    }

    static short to_short(double value) {
        if ((short)value != value) {
            throw overflow("short", value);
        }
        return (short)value;
    }

    // char

    static char to_char(byte value) {
        return (char) value;
    }

    static char to_char(short value) {
        return (char) value;
    }

    static char to_char(int value) {
        return (char) value;
    }

    static char to_char(long value) {
        return (char) value;
    }

    static char to_char(float value) {
        return (char) value;
    }

    static char to_char(double value) {
        return (char) value;
    }


    // int

    static int to_int(long value) {
        if ((int)value != value) {
            throw overflow("int", value);
        }
        return (int)value;
    }

    static int to_int(float value) {
        if ((int)value != value) {
            throw overflow("int", value);
        }
        return (int)value;
    }

    static int to_int(double value) {
        if ((int)value != value) {
            throw overflow("int", value);
        }
        return (int)value;
    }

    // long

    static long to_long(float value) {
        if ((long)value != value) {
            throw overflow("double", value);
        }
        return (long)value;
    }

    static long to_long(double value) {
        if ((long)value != value) {
            throw overflow("double", value);
        }
        return (long)value;
    }

    // float

    static float to_float(double value) {
        if ((float)value != value) {
            throw overflow("float", value);
        }
        return (float)value;
    }



}
