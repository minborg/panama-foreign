package jdk.internal.foreign.mapper;

import java.lang.invoke.MethodHandles;

/**
 * Trait interface providing lookup.
 */
interface HasLookup {
    MethodHandles.Lookup lookup();
}
