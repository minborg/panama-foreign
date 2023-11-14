package jdk.internal.foreign.mapper;

import java.lang.invoke.MethodHandles;

interface HasLookup {
    MethodHandles.Lookup lookup();
}
