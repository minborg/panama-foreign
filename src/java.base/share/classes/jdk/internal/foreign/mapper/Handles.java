package jdk.internal.foreign.mapper;

import java.lang.invoke.MethodHandle;

record Handles(MethodHandle getHandle,
               MethodHandle setHandle) {
}
