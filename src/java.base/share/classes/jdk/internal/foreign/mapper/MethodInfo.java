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

import java.lang.foreign.GroupLayout;
import java.lang.reflect.Method;

// Todo: Rename this class
record MethodInfo(Key key,
                  Method method,
                  Class<?> type,
                  LayoutInfo layoutInfo,
                  long offset) {

    GroupLayout targetLayout() {
        return (GroupLayout) layoutInfo().arrayInfo()
                .map(ArrayInfo::elementLayout)
                .orElse(layoutInfo().layout());
    }

    enum Cardinality {SCALAR, ARRAY}

    enum ValueType {
        VALUE(false), INTERFACE(true), RECORD(true);

        private final boolean virtual;

        ValueType(boolean virtual) {
            this.virtual = virtual;
        }

        public boolean isVirtual() {
            return virtual;
        }
    }

    enum AccessorType {GETTER, SETTER}

    /**
     * These are the various combinations that exists.
     */
    enum Key {
        SCALAR_VALUE_GETTER(Cardinality.SCALAR, ValueType.VALUE, AccessorType.GETTER),
        SCALAR_VALUE_SETTER(Cardinality.SCALAR, ValueType.VALUE, AccessorType.SETTER),
        SCALAR_INTERFACE_GETTER(Cardinality.SCALAR, ValueType.INTERFACE, AccessorType.GETTER),
        SCALAR_INTERFACE_SETTER(Cardinality.SCALAR, ValueType.INTERFACE, AccessorType.SETTER), // Unavailable for interfaces
        SCALAR_RECORD_GETTER(Cardinality.SCALAR, ValueType.RECORD, AccessorType.GETTER),
        SCALAR_RECORD_SETTER(Cardinality.SCALAR, ValueType.RECORD, AccessorType.SETTER),
        ARRAY_VALUE_GETTER(Cardinality.ARRAY, ValueType.VALUE, AccessorType.GETTER),
        ARRAY_VALUE_SETTER(Cardinality.ARRAY, ValueType.VALUE, AccessorType.SETTER),
        ARRAY_INTERFACE_GETTER(Cardinality.ARRAY, ValueType.INTERFACE, AccessorType.GETTER),
        ARRAY_INTERFACE_SETTER(Cardinality.ARRAY, ValueType.INTERFACE, AccessorType.SETTER),   // Unavailable for interfaces
        ARRAY_RECORD_GETTER(Cardinality.ARRAY, ValueType.RECORD, AccessorType.GETTER),
        ARRAY_RECORD_SETTER(Cardinality.ARRAY, ValueType.RECORD, AccessorType.SETTER);         // Todo

        private final Cardinality cardinality;
        private final ValueType valueType;
        private final AccessorType accessorType;

        Key(Cardinality cardinality, ValueType valueType, AccessorType accessorType) {
            this.cardinality = cardinality;
            this.valueType = valueType;
            this.accessorType = accessorType;
        }

        public Cardinality cardinality() {
            return cardinality;
        }

        public ValueType valueType() {
            return valueType;
        }

        public AccessorType accessorType() {
            return accessorType;
        }

        public static Key of(Cardinality cardinality,
                             ValueType valueType,
                             AccessorType accessorType) {

            for (Key k : Key.values()) {
                if (k.cardinality == cardinality && valueType == k.valueType && accessorType == k.accessorType) {
                    return k;
                }
            }
            throw new InternalError("Should not reach here");
        }
    }

}
