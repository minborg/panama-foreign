package jdk.internal.foreign.mapper;

import java.lang.invoke.MethodHandles;

/**
 * Internal trait interface providing access to a lookup.
 */
interface HasLookup {
    MethodHandles.Lookup lookup();
}
