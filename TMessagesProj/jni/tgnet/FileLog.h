/*
 * This is the source code of tgnet library v. 1.0
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015.
 */

#ifndef FILELOG_H
#define FILELOG_H

#include "Defines.h"

class FileLog {
public:
    static void init(std::string path);
    static void e(const char *message, ...) __attribute__((format (printf, 1, 2)));
    static void w(const char *message, ...) __attribute__((format (printf, 1, 2)));
    static void d(const char *message, ...) __attribute__((format (printf, 1, 2)));
};

#ifdef DEBUG_VERSION
#define DEBUG_E FileLog::e
#define DEBUG_W FileLog::w
#define DEBUG_D FileLog::d
#else
#define DEBUG_E
#define DEBUG_W
#define DEBUG_D
#endif

#endif
