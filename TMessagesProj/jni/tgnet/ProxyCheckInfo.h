/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#ifndef PROXYCHECKINFO_H
#define PROXYCHECKINFO_H

#include <sstream>
#include "Defines.h"

#ifdef ANDROID
#include <jni.h>
#endif

class ProxyCheckInfo {

public:
    ~ProxyCheckInfo();

    int32_t connectionNum = 0;
    int32_t requestToken = 0;
    std::string address;
    uint16_t port = 1080;
    std::string username;
    std::string password;
    std::string secret;
    int64_t pingId = 0;
    onRequestTimeFunc onRequestTime;
    int32_t instanceNum = 0;

#ifdef ANDROID
    jobject ptr1 = nullptr;
#endif
};


#endif
