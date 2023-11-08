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

/**
 * Provides higher-level access to memory and functions outside the Java runtime.
 * <p>
 * This package allows native memory and functions to interact via standard
 * Java records and interfaces.
 * <p>
 * More specifically, <em>components</em> of records and interfaces can be matched
 * with memory layouts, thereby providing a distinct and seamless way of operating
 * with native memory and native functions.
 * <p>
 * Determination of a record's or an interface's components are made according to the
 * following table:
 * <blockquote><table class="plain">
 * <caption style="display:none">Component Determination</caption>
 * <thead>
 * <tr>
 *     <th scope="col">Kind</th>
 *     <th scope="col">Components</th>
 *     <th scope="col">Definition</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row" style="font-weight:normal">Record</th>
 *     <td style="text-align:center;">Record Components</td>
 *     <td style="text-align:center;">type.getRecordComponents()</td></tr>
 * <tr><th scope="row" style="font-weight:normal">Interface</th>
 *     <td style="text-align:center;">Public getters</td>
 *     <td style="text-align:center;">Any type.getMethods() having a non-void return type
 *                                    and that takes no parameter</td></tr>
 * </tbody>
 * </table></blockquote>
 * <p>
 * Matching of the component's types (e.g. {@code int} and {@code long}) to
 * memory layouts must be exact and in accordance with the following table:
 * <blockquote><table class="plain">
 * <caption style="display:none">Type Matching</caption>
 * <thead>
 * <tr>
 *     <th scope="col">Type</th>
 *     <th scope="col">ValueLayout</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row" style="font-weight:normal">byte</th>
 *     <td style="text-align:center;">JAVA_BYTE</td></tr>
 *     <tr><th scope="row" style="font-weight:normal">boolean</th>
 *     <td style="text-align:center;">JAVA_BOOLEAN</td></tr>
 *     <tr><th scope="row" style="font-weight:normal">char</th>
 *     <td style="text-align:center;">JAVA_CHAR</td></tr>
 *     <tr><th scope="row" style="font-weight:normal">short</th>
 *     <td style="text-align:center;">JAVA_SHORT</td></tr>
 *     <tr><th scope="row" style="font-weight:normal">int</th>
 *     <td style="text-align:center;">JAVA_INT</td></tr>
 *     <tr><th scope="row" style="font-weight:normal">long</th>
 *     <td style="text-align:center;">JAVA_LONG</td></tr>
 *     <tr><th scope="row" style="font-weight:normal">float</th>
 *     <td style="text-align:center;">JAVA_FLOAT</td></tr>
 *     <tr><th scope="row" style="font-weight:normal">double</th>
 *     <td style="text-align:center;">JAVA_DOUBLE</td></tr>
 *     <tr><th scope="row" style="font-weight:normal">MemorySegment</th>
 *     <td style="text-align:center;">ADDRESS</td></tr>
 *     <tr><th scope="row" style="font-weight:normal">Record</th>
 *     <td style="text-align:center;">Recursive use</td></tr>
 *     <tr><th scope="row" style="font-weight:normal">Interface</th>
 *     <td style="text-align:center;">Recursive use</td></tr>
 * </tbody>
 * </table></blockquote>
 * <p>
 * Explicit handling of custom conversions (if needed) can be made directly in the
 * record or interface types. This includes boxing, unboxing, widening and narrowing.
 * Additionally, a {@linkplain java.lang.foreign.mapper.SegmentMapper} provides means
 * for composing such custom conversions using auxiliary projection types via the
 * {@linkplain java.lang.foreign.mapper.SegmentMapper#map(java.util.function.Function)}
 * functions.
 * <p>
 * *** TBI: How do we handle extra getters in an interface so that they won't be considered
 * a component? ***
 *
 *
 * @since 23
 */
package java.lang.foreign.mapper;
