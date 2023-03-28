/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#include "ProxyCheckInfo.h"
#include "ConnectionsManager.h"
#include "FileLog.h"

ProxyCheckInfo::~ProxyCheckInfo() {
#ifdef ANDROID
    if (ptr1 != nullptr) {
        DEBUG_DELREF("tgnet (2) request ptr1");
        jniEnv[instanceNum]->DeleteGlobalRef(ptr1);
        ptr1 = nullptr;
    }
#endif
}
