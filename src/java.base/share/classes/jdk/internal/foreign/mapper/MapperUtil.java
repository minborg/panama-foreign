/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.foreign.mapper;

import sun.security.action.GetPropertyAction;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

public final class MapperUtil {

    private MapperUtil() {
    }

    private static final String DEBUG =
            GetPropertyAction.privilegedGetProperty("java.lang.foreign.mapper.debug", "");

    public static boolean isDebug() {
        return !DEBUG.isEmpty();
    }

    public static <T> Class<T> requireImplementableInterfaceType(Class<T> type) {
        Objects.requireNonNull(type);
        if (!type.isInterface()) {
            throw newIae(type, "not an interface");
        }
        if (type.isHidden()) {
            throw newIae(type, "a hidden interface");
        }
        if (type.isSealed()) {
            throw newIae(type, "a sealed interface");
        }
        return type;
    }

    public static <T extends Record> Class<T> requireRecordType(Class<?> type) {
        Objects.requireNonNull(type);
        if (!type.isRecord()) {
            throw newIae(type, "is not a Record");
        }
        if (type.equals(Record.class)) {
            throw newIae(type, "not a real Record");
        }
        @SuppressWarnings("unchecked")
        Class<T> result = (Class<T>) type;
        return result;
    }

    static IllegalArgumentException newIae(Class<?> type, String trailingInfo) {
        return new IllegalArgumentException(type.getName() + " is " + trailingInfo);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Record> Class<T> castToRecordClass(Class<?> clazz) {
        return (Class<T>) clazz;
    }

    public static Object evalWithMemorySegmentAndOffset(MethodHandle mh, MemorySegment segment, long offset) {
        try {
            return mh.invokeExact(segment, offset);
        } catch (Throwable e) {
            throw new IllegalStateException("Unable to invoke method handle " + mh, e);
        }
    }


}
