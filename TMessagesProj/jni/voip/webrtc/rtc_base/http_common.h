/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_HTTP_COMMON_H_
#define RTC_BASE_HTTP_COMMON_H_

#include <string>

namespace rtc {

class CryptString;
class SocketAddress;

//////////////////////////////////////////////////////////////////////
// Http Authentication
//////////////////////////////////////////////////////////////////////

struct HttpAuthContext {
  std::string auth_method;
  HttpAuthContext(const std::string& auth) : auth_method(auth) {}
  virtual ~HttpAuthContext() {}
};

enum HttpAuthResult { HAR_RESPONSE, HAR_IGNORE, HAR_CREDENTIALS, HAR_ERROR };

// 'context' is used by this function to record information between calls.
// Start by passing a null pointer, then pass the same pointer each additional
// call.  When the authentication attempt is finished, delete the context.
// TODO(bugs.webrtc.org/8905): Change "response" to "ZeroOnFreeBuffer".
HttpAuthResult HttpAuthenticate(const char* challenge,
                                size_t len,
                                const SocketAddress& server,
                                const std::string& method,
                                const std::string& uri,
                                const std::string& username,
                                const CryptString& password,
                                HttpAuthContext*& context,
                                std::string& response,
                                std::string& auth_method);

//////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // RTC_BASE_HTTP_COMMON_H_
