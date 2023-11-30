package jdk.internal.foreign.mapper;

import java.lang.invoke.MethodHandle;

record Handles(boolean isExhaustive,
               MethodHandle getHandle,
               MethodHandle setHandle) {
}
