/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * Class for mapped layout memory segment var handle view implementations.
 */
final class VarHandleMethodHandleDelegator extends VarHandle {

    private static final VarForm FORM =
            new VarForm(VarHandleMethodHandleDelegator.class, MemorySegment.class, Object.class, long.class);

    /** access size (in bytes, computed from var handle carrier type) **/
    private final long length;

    /** alignment constraint (in bytes, expressed as a bit mask) **/
    private final long alignmentMask;

    private final MethodHandle getter;
    private final MethodHandle setter;

    VarHandleMethodHandleDelegator(long length, long alignmentMask, boolean exact,
                                   MethodHandle getter, MethodHandle setter) {
        super(FORM, exact);
        this.length = length;
        this.alignmentMask = alignmentMask;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    MethodType accessModeTypeUncached(VarHandle.AccessType accessType) {
        return accessType.accessModeType(MemorySegment.class, Object.class, long.class);
    }

    @Override
    public VarHandleMethodHandleDelegator withInvokeExactBehavior() {
        return hasInvokeExactBehavior() ?
                this :
                new VarHandleMethodHandleDelegator(length, alignmentMask, true, getter, setter);
    }

    @Override
    public VarHandleMethodHandleDelegator withInvokeBehavior() {
        return !hasInvokeExactBehavior() ?
                this :
                new VarHandleMethodHandleDelegator(length, alignmentMask, false, getter, setter);
    }

    @ForceInline
    static Object get(VarHandle ob, Object obb, long base) {
        VarHandleMethodHandleDelegator handle = (VarHandleMethodHandleDelegator)ob;
        AbstractMemorySegmentImpl ms = checkAddress(obb, base, handle.length, true);
        try {
            if (ob.exact) {
                return (Object)handle.getter
                        .invokeExact((MemorySegment)ms, base);
            } else {
                return (Object)handle.getter
                        .invoke((MemorySegment)ms, base);
            }
        } catch (Throwable t) {
            throw new IllegalArgumentException(t);
        }
    }

    @ForceInline
    static void set(VarHandle ob, Object obb, long base, Object value) {
        VarHandleMethodHandleDelegator handle = (VarHandleMethodHandleDelegator)ob;
        AbstractMemorySegmentImpl ms = checkAddress(obb, base, handle.length, true);
        try {
            if (ob.exact) {
                handle.setter
                        .invokeExact((MemorySegment)ms, base, value);
            } else {
                handle.setter
                        .invoke((MemorySegment)ms, base, value);
            }
        } catch (Throwable t) {
            throw new IllegalArgumentException(t);
        }
    }

    static Object getVolatile(VarHandle ob, Object obb, long base) { throw newUnsupportedAccessMode(); }
    static void setVolatile(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }
    static Object getAcquire(VarHandle ob, Object obb, long base) { throw newUnsupportedAccessMode(); }
    static void setRelease(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }
    static Object getOpaque(VarHandle ob, Object obb, long base) { throw newUnsupportedAccessMode(); }
    static void setOpaque(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }
    static boolean compareAndSet(VarHandle ob, Object obb, long base, Object expected, Object value) { throw newUnsupportedAccessMode(); }
    static Object compareAndExchange(VarHandle ob, Object obb, long base, Object expected, Object value) { throw newUnsupportedAccessMode(); }
    static Object compareAndExchangeAcquire(VarHandle ob, Object obb, long base, Object expected, Object value) { throw newUnsupportedAccessMode(); }
    static Object compareAndExchangeRelease(VarHandle ob, Object obb, long base, Object expected, Object value) { throw newUnsupportedAccessMode(); }
    static boolean weakCompareAndSetPlain(VarHandle ob, Object obb, long base, Object expected, Object value) { throw newUnsupportedAccessMode(); }
    static boolean weakCompareAndSet(VarHandle ob, Object obb, long base, Object expected, Object value) { throw newUnsupportedAccessMode(); }
    static boolean weakCompareAndSetAcquire(VarHandle ob, Object obb, long base, Object expected, Object value) { throw newUnsupportedAccessMode(); }
    static boolean weakCompareAndSetRelease(VarHandle ob, Object obb, long base, Object expected, Object value) { throw newUnsupportedAccessMode(); }
    static Object getAndSet(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }
    static Object getAndSetAcquire(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }
    static Object getAndSetRelease(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }
    static Object getAndAdd(VarHandle ob, Object obb, long base, Object delta) { throw newUnsupportedAccessMode(); }
    static Object getAndAddAcquire(VarHandle ob, Object obb, long base, Object delta) { throw newUnsupportedAccessMode(); }
    static Object getAndAddRelease(VarHandle ob, Object obb, long base, Object delta) { throw newUnsupportedAccessMode(); }
    static Object getAndBitwiseOr(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }
    static Object getAndBitwiseOrRelease(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }
    static Object getAndBitwiseOrAcquire(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }
    static Object getAndBitwiseAndRelease(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }
    static Object getAndBitwiseAndAcquire(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }
    static Object getAndBitwiseXor(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }
    static Object getAndBitwiseXorRelease(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }
    static Object getAndBitwiseXorAcquire(VarHandle ob, Object obb, long base, Object value) { throw newUnsupportedAccessMode(); }

    static AbstractMemorySegmentImpl checkAddress(Object obb, long offset, long length, boolean ro) {
        AbstractMemorySegmentImpl oo = (AbstractMemorySegmentImpl) Objects.requireNonNull(obb);
        oo.checkAccess(offset, length, ro);
        return oo;
    }

    static UnsupportedOperationException newUnsupportedAccessMode() {
        return new UnsupportedOperationException("Unsupported access mode");
    }
}
