/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/ssl_adapter.h"

#include "rtc_base/openssl_adapter.h"

///////////////////////////////////////////////////////////////////////////////

namespace rtc {

SSLAdapterFactory* SSLAdapterFactory::Create() {
  return new OpenSSLAdapterFactory();
}

SSLAdapter* SSLAdapter::Create(AsyncSocket* socket) {
  return new OpenSSLAdapter(socket);
}

///////////////////////////////////////////////////////////////////////////////

bool InitializeSSL() {
  return OpenSSLAdapter::InitializeSSL();
}

bool CleanupSSL() {
  return OpenSSLAdapter::CleanupSSL();
}

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc
