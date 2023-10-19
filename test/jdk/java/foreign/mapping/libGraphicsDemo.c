/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#include <stdio.h>
#include <stdlib.h>

#ifdef _WIN64
#define EXPORT __declspec(dllexport)
#else
#define EXPORT
#endif

typedef struct {
    int x;
    int y;
} Point;

typedef struct {
    Point begin;
    Point end;
} Line;

EXPORT const Point ORIGIN     = { .x = 0, .y = 0 };
EXPORT const Point UNIT_POINT = { .x = 1, .y = 1 };
const Line  UNIT_LINE  = { .begin = { .x = 0, .y = 0 }, .end = { .x = 1, .y = 1 } };

EXPORT Point origin(){
    return ORIGIN;
}

EXPORT Point unit_point(){
    return UNIT_POINT;
}

/*EXPORT Line unit_line(){
    return UNIT_LINE;
}*/

// Memory leak?
EXPORT Point create_point(int x, int y) {
    Point p = { .x = x, .y = y};
    return p;
}

EXPORT void set_point(int x, int y, Point* point) {
    point->x = x;
    point->y = y;
}

EXPORT Line create_line(Point begin, Point end) {
    // malloc here
    Line line = { .begin = begin, .end = end };
    return line;
}

EXPORT Point add_point(Point first, Point second) {
    Point point = { .x = first.x + second.x, .y = first.y + second.y };
    return point;
}

// Provide a string. Null-> return size
EXPORT char* to_string_point(Point point) {
    char* str = (char*)malloc(80); // Leak
    sprintf(str, "Point[x=%d, y=%d]", point.x, point.y);
    return str;
}

// Provide a string. Null-> return size
EXPORT int to_string_point(Point point, char* str, int len) {
    // Fix
    sprintf(str, "Point[x=%d, y=%d]", point.x, point.y);
    return str;
}

// Questions: polymorphism in libraries?

