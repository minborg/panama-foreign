package jdk.internal.foreign.mapper;

import java.lang.foreign.MemorySegment;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Exposes the backing memory segment and offset for segment mapped interfaces.
 * <p>
 * The method names are chosen to minimize the risk of name collision.
 */
public interface SegmentBacked {

    /**
     * {@return the segment that backs this interface}
     */
    MemorySegment $_$_$sEgMeNt$_$_$();

    /**
     * {@return the offset in the backing segment}
     */
    long $_$_$oFfSeT$_$_$();

    // Todo: Find a better way that does not proliferate interfaces
    static Optional<MemorySegment> segment(Object source) {
        try {
            return Optional.of(
                    (MemorySegment) method(source, "$_$_$sEgMeNt$_$_$")
                            .invoke(source)
            );
        } catch (Exception e) {
            return Optional.empty();
        }
/*        return (source instanceof SegmentBacked sb)
                ? Optional.of(sb.$_$_$sEgMeNt$_$_$())
                : Optional.empty();*/
    }

    // Todo: Find a better way that does not proliferate interfaces
    static OptionalLong offset(Object source) {
        try {
            return OptionalLong.of(
                    (long) method(source, "$_$_$oFfSeT$_$_$")
                            .invoke(source)
            );
        } catch (Exception e) {
            return OptionalLong.empty();
        }

        /*
        return (source instanceof SegmentBacked sb)
                ? OptionalLong.of(sb.$_$_$oFfSeT$_$_$())
                : OptionalLong.empty();*/
    }

    private static Method method(Object source, String name) throws NoSuchMethodException {
        return source.getClass().getMethod(name);
    }

}
