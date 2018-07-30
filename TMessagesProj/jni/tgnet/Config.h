/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#ifndef CONFIG_H
#define CONFIG_H

#include <string>
#include "NativeByteBuffer.h"

class Config {

public:
    Config(int32_t instance, std::string fileName);

    NativeByteBuffer *readConfig();
    void writeConfig(NativeByteBuffer *buffer);

private:
    int32_t instanceNum;
    std::string configPath;
    std::string backupPath;
};

#endif
