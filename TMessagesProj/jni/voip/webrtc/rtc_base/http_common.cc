/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <time.h>

#include "absl/strings/string_view.h"

#if defined(WEBRTC_WIN)
#include <windows.h>
#include <winsock2.h>
#include <ws2tcpip.h>

#define SECURITY_WIN32
#include <security.h>
#endif

#include <ctype.h>  // for isspace
#include <stdio.h>  // for sprintf

#include <utility>  // for pair
#include <vector>

#include "absl/strings/match.h"
#include "rtc_base/crypt_string.h"  // for CryptString
#include "rtc_base/http_common.h"
#include "rtc_base/logging.h"
#include "rtc_base/message_digest.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/string_utils.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/third_party/base64/base64.h"  // for Base64
#include "rtc_base/zero_memory.h"                // for ExplicitZeroMemory

namespace rtc {
namespace {
#if defined(WEBRTC_WIN) && !defined(WINUWP)
///////////////////////////////////////////////////////////////////////////////
// ConstantToLabel can be used to easily generate string names from constant
// values.  This can be useful for logging descriptive names of error messages.
// Usage:
//   const ConstantToLabel LIBRARY_ERRORS[] = {
//     KLABEL(SOME_ERROR),
//     KLABEL(SOME_OTHER_ERROR),
//     ...
//     LASTLABEL
//   }
//
//   int err = LibraryFunc();
//   LOG(LS_ERROR) << "LibraryFunc returned: "
//                 << GetErrorName(err, LIBRARY_ERRORS);
struct ConstantToLabel {
  int value;
  const char* label;
};

const char* LookupLabel(int value, const ConstantToLabel entries[]) {
  for (int i = 0; entries[i].label; ++i) {
    if (value == entries[i].value) {
      return entries[i].label;
    }
  }
  return 0;
}

std::string GetErrorName(int err, const ConstantToLabel* err_table) {
  if (err == 0)
    return "No error";

  if (err_table != 0) {
    if (const char* value = LookupLabel(err, err_table))
      return value;
  }

  char buffer[16];
  snprintf(buffer, sizeof(buffer), "0x%08x", err);
  return buffer;
}

#define KLABEL(x) \
  { x, #x }
#define LASTLABEL \
  { 0, 0 }

const ConstantToLabel SECURITY_ERRORS[] = {
    KLABEL(SEC_I_COMPLETE_AND_CONTINUE),
    KLABEL(SEC_I_COMPLETE_NEEDED),
    KLABEL(SEC_I_CONTEXT_EXPIRED),
    KLABEL(SEC_I_CONTINUE_NEEDED),
    KLABEL(SEC_I_INCOMPLETE_CREDENTIALS),
    KLABEL(SEC_I_RENEGOTIATE),
    KLABEL(SEC_E_CERT_EXPIRED),
    KLABEL(SEC_E_INCOMPLETE_MESSAGE),
    KLABEL(SEC_E_INSUFFICIENT_MEMORY),
    KLABEL(SEC_E_INTERNAL_ERROR),
    KLABEL(SEC_E_INVALID_HANDLE),
    KLABEL(SEC_E_INVALID_TOKEN),
    KLABEL(SEC_E_LOGON_DENIED),
    KLABEL(SEC_E_NO_AUTHENTICATING_AUTHORITY),
    KLABEL(SEC_E_NO_CREDENTIALS),
    KLABEL(SEC_E_NOT_OWNER),
    KLABEL(SEC_E_OK),
    KLABEL(SEC_E_SECPKG_NOT_FOUND),
    KLABEL(SEC_E_TARGET_UNKNOWN),
    KLABEL(SEC_E_UNKNOWN_CREDENTIALS),
    KLABEL(SEC_E_UNSUPPORTED_FUNCTION),
    KLABEL(SEC_E_UNTRUSTED_ROOT),
    KLABEL(SEC_E_WRONG_PRINCIPAL),
    LASTLABEL};
#undef KLABEL
#undef LASTLABEL
#endif  // defined(WEBRTC_WIN) && !defined(WINUWP)

typedef std::pair<std::string, std::string> HttpAttribute;
typedef std::vector<HttpAttribute> HttpAttributeList;

inline bool IsEndOfAttributeName(size_t pos, absl::string_view data) {
  if (pos >= data.size())
    return true;
  if (isspace(static_cast<unsigned char>(data[pos])))
    return true;
  // The reason for this complexity is that some attributes may contain trailing
  // equal signs (like base64 tokens in Negotiate auth headers)
  if ((pos + 1 < data.size()) && (data[pos] == '=') &&
      !isspace(static_cast<unsigned char>(data[pos + 1])) &&
      (data[pos + 1] != '=')) {
    return true;
  }
  return false;
}

void HttpParseAttributes(absl::string_view data,
                         HttpAttributeList& attributes) {
  size_t pos = 0;
  const size_t len = data.size();
  while (true) {
    // Skip leading whitespace
    while ((pos < len) && isspace(static_cast<unsigned char>(data[pos]))) {
      ++pos;
    }

    // End of attributes?
    if (pos >= len)
      return;

    // Find end of attribute name
    size_t start = pos;
    while (!IsEndOfAttributeName(pos, data)) {
      ++pos;
    }

    HttpAttribute attribute;
    attribute.first.assign(data.data() + start, data.data() + pos);

    // Attribute has value?
    if ((pos < len) && (data[pos] == '=')) {
      ++pos;  // Skip '='
      // Check if quoted value
      if ((pos < len) && (data[pos] == '"')) {
        while (++pos < len) {
          if (data[pos] == '"') {
            ++pos;
            break;
          }
          if ((data[pos] == '\\') && (pos + 1 < len))
            ++pos;
          attribute.second.append(1, data[pos]);
        }
      } else {
        while ((pos < len) && !isspace(static_cast<unsigned char>(data[pos])) &&
               (data[pos] != ',')) {
          attribute.second.append(1, data[pos++]);
        }
      }
    }

    attributes.push_back(attribute);
    if ((pos < len) && (data[pos] == ','))
      ++pos;  // Skip ','
  }
}

bool HttpHasAttribute(const HttpAttributeList& attributes,
                      absl::string_view name,
                      std::string* value) {
  for (HttpAttributeList::const_iterator it = attributes.begin();
       it != attributes.end(); ++it) {
    if (it->first == name) {
      if (value) {
        *value = it->second;
      }
      return true;
    }
  }
  return false;
}

bool HttpHasNthAttribute(HttpAttributeList& attributes,
                         size_t index,
                         std::string* name,
                         std::string* value) {
  if (index >= attributes.size())
    return false;

  if (name)
    *name = attributes[index].first;
  if (value)
    *value = attributes[index].second;
  return true;
}

std::string quote(absl::string_view str) {
  std::string result;
  result.push_back('"');
  for (size_t i = 0; i < str.size(); ++i) {
    if ((str[i] == '"') || (str[i] == '\\'))
      result.push_back('\\');
    result.push_back(str[i]);
  }
  result.push_back('"');
  return result;
}

#if defined(WEBRTC_WIN) && !defined(WINUWP)
struct NegotiateAuthContext : public HttpAuthContext {
  CredHandle cred;
  CtxtHandle ctx;
  size_t steps;
  bool specified_credentials;

  NegotiateAuthContext(absl::string_view auth, CredHandle c1, CtxtHandle c2)
      : HttpAuthContext(auth),
        cred(c1),
        ctx(c2),
        steps(0),
        specified_credentials(false) {}

  ~NegotiateAuthContext() override {
    DeleteSecurityContext(&ctx);
    FreeCredentialsHandle(&cred);
  }
};
#endif  // defined(WEBRTC_WIN) && !defined(WINUWP)

}  // anonymous namespace

HttpAuthResult HttpAuthenticate(absl::string_view challenge,
                                const SocketAddress& server,
                                absl::string_view method,
                                absl::string_view uri,
                                absl::string_view username,
                                const CryptString& password,
                                HttpAuthContext*& context,
                                std::string& response,
                                std::string& auth_method) {
  HttpAttributeList args;
  HttpParseAttributes(challenge, args);
  HttpHasNthAttribute(args, 0, &auth_method, nullptr);

  if (context && (context->auth_method != auth_method))
    return HAR_IGNORE;

  // BASIC
  if (absl::EqualsIgnoreCase(auth_method, "basic")) {
    if (context)
      return HAR_CREDENTIALS;  // Bad credentials
    if (username.empty())
      return HAR_CREDENTIALS;  // Missing credentials

    context = new HttpAuthContext(auth_method);

    // TODO(bugs.webrtc.org/8905): Convert sensitive to a CryptString and also
    // return response as CryptString so contents get securely deleted
    // automatically.
    // std::string decoded = username + ":" + password;
    size_t len = username.size() + password.GetLength() + 2;
    char* sensitive = new char[len];
    size_t pos = strcpyn(sensitive, len, username);
    pos += strcpyn(sensitive + pos, len - pos, ":");
    password.CopyTo(sensitive + pos, true);

    response = auth_method;
    response.append(" ");
    // TODO: create a sensitive-source version of Base64::encode
    response.append(Base64::Encode(sensitive));
    ExplicitZeroMemory(sensitive, len);
    delete[] sensitive;
    return HAR_RESPONSE;
  }

  // DIGEST
  if (absl::EqualsIgnoreCase(auth_method, "digest")) {
    if (context)
      return HAR_CREDENTIALS;  // Bad credentials
    if (username.empty())
      return HAR_CREDENTIALS;  // Missing credentials

    context = new HttpAuthContext(auth_method);

    std::string cnonce, ncount;
    char buffer[256];
    snprintf(buffer, sizeof(buffer), "%d", static_cast<int>(time(0)));
    cnonce = MD5(buffer);
    ncount = "00000001";

    std::string realm, nonce, qop, opaque;
    HttpHasAttribute(args, "realm", &realm);
    HttpHasAttribute(args, "nonce", &nonce);
    bool has_qop = HttpHasAttribute(args, "qop", &qop);
    bool has_opaque = HttpHasAttribute(args, "opaque", &opaque);

    // TODO(bugs.webrtc.org/8905): Convert sensitive to a CryptString and also
    // return response as CryptString so contents get securely deleted
    // automatically.
    // std::string A1 = username + ":" + realm + ":" + password;
    size_t len = username.size() + realm.size() + password.GetLength() + 3;
    char* sensitive = new char[len];  // A1
    size_t pos = strcpyn(sensitive, len, username);
    pos += strcpyn(sensitive + pos, len - pos, ":");
    pos += strcpyn(sensitive + pos, len - pos, realm);
    pos += strcpyn(sensitive + pos, len - pos, ":");
    password.CopyTo(sensitive + pos, true);

    std::string A2 = std::string(method) + ":" + std::string(uri);
    std::string middle;
    if (has_qop) {
      qop = "auth";
      middle = nonce + ":" + ncount + ":" + cnonce + ":" + qop;
    } else {
      middle = nonce;
    }
    std::string HA1 = MD5(sensitive);
    ExplicitZeroMemory(sensitive, len);
    delete[] sensitive;
    std::string HA2 = MD5(A2);
    std::string dig_response = MD5(HA1 + ":" + middle + ":" + HA2);

    rtc::StringBuilder ss;
    ss << auth_method;
    ss << " username=" << quote(username);
    ss << ", realm=" << quote(realm);
    ss << ", nonce=" << quote(nonce);
    ss << ", uri=" << quote(uri);
    if (has_qop) {
      ss << ", qop=" << qop;
      ss << ", nc=" << ncount;
      ss << ", cnonce=" << quote(cnonce);
    }
    ss << ", response=\"" << dig_response << "\"";
    if (has_opaque) {
      ss << ", opaque=" << quote(opaque);
    }
    response = ss.str();
    return HAR_RESPONSE;
  }

#if defined(WEBRTC_WIN) && !defined(WINUWP)
#if 1
  bool want_negotiate = absl::EqualsIgnoreCase(auth_method, "negotiate");
  bool want_ntlm = absl::EqualsIgnoreCase(auth_method, "ntlm");
  // SPNEGO & NTLM
  if (want_negotiate || want_ntlm) {
    const size_t MAX_MESSAGE = 12000, MAX_SPN = 256;
    char out_buf[MAX_MESSAGE], spn[MAX_SPN];

#if 0  // Requires funky windows versions
    DWORD len = MAX_SPN;
    if (DsMakeSpn("HTTP", server.HostAsURIString().c_str(), nullptr,
                  server.port(),
                  0, &len, spn) != ERROR_SUCCESS) {
      RTC_LOG_F(LS_WARNING) << "(Negotiate) - DsMakeSpn failed";
      return HAR_IGNORE;
    }
#else
    snprintf(spn, MAX_SPN, "HTTP/%s", server.ToString().c_str());
#endif

    SecBuffer out_sec;
    out_sec.pvBuffer = out_buf;
    out_sec.cbBuffer = sizeof(out_buf);
    out_sec.BufferType = SECBUFFER_TOKEN;

    SecBufferDesc out_buf_desc;
    out_buf_desc.ulVersion = 0;
    out_buf_desc.cBuffers = 1;
    out_buf_desc.pBuffers = &out_sec;

    const ULONG NEG_FLAGS_DEFAULT =
        // ISC_REQ_ALLOCATE_MEMORY
        ISC_REQ_CONFIDENTIALITY
        //| ISC_REQ_EXTENDED_ERROR
        //| ISC_REQ_INTEGRITY
        | ISC_REQ_REPLAY_DETECT | ISC_REQ_SEQUENCE_DETECT
        //| ISC_REQ_STREAM
        //| ISC_REQ_USE_SUPPLIED_CREDS
        ;

    ::TimeStamp lifetime;
    SECURITY_STATUS ret = S_OK;
    ULONG ret_flags = 0, flags = NEG_FLAGS_DEFAULT;

    bool specify_credentials = !username.empty();
    size_t steps = 0;

    // uint32_t now = Time();

    NegotiateAuthContext* neg = static_cast<NegotiateAuthContext*>(context);
    if (neg) {
      const size_t max_steps = 10;
      if (++neg->steps >= max_steps) {
        RTC_LOG(LS_WARNING) << "AsyncHttpsProxySocket::Authenticate(Negotiate) "
                               "too many retries";
        return HAR_ERROR;
      }
      steps = neg->steps;

      std::string challenge, decoded_challenge;
      if (HttpHasNthAttribute(args, 1, &challenge, nullptr) &&
          Base64::Decode(challenge, Base64::DO_STRICT, &decoded_challenge,
                         nullptr)) {
        SecBuffer in_sec;
        in_sec.pvBuffer = const_cast<char*>(decoded_challenge.data());
        in_sec.cbBuffer = static_cast<unsigned long>(decoded_challenge.size());
        in_sec.BufferType = SECBUFFER_TOKEN;

        SecBufferDesc in_buf_desc;
        in_buf_desc.ulVersion = 0;
        in_buf_desc.cBuffers = 1;
        in_buf_desc.pBuffers = &in_sec;

        ret = InitializeSecurityContextA(
            &neg->cred, &neg->ctx, spn, flags, 0, SECURITY_NATIVE_DREP,
            &in_buf_desc, 0, &neg->ctx, &out_buf_desc, &ret_flags, &lifetime);
        if (FAILED(ret)) {
          RTC_LOG(LS_ERROR) << "InitializeSecurityContext returned: "
                            << GetErrorName(ret, SECURITY_ERRORS);
          return HAR_ERROR;
        }
      } else if (neg->specified_credentials) {
        // Try again with default credentials
        specify_credentials = false;
        delete context;
        context = neg = 0;
      } else {
        return HAR_CREDENTIALS;
      }
    }

    if (!neg) {
      unsigned char userbuf[256], passbuf[256], domainbuf[16];
      SEC_WINNT_AUTH_IDENTITY_A auth_id, *pauth_id = 0;
      if (specify_credentials) {
        memset(&auth_id, 0, sizeof(auth_id));
        size_t len = password.GetLength() + 1;
        char* sensitive = new char[len];
        password.CopyTo(sensitive, true);
        absl::string_view::size_type pos = username.find('\\');
        if (pos == absl::string_view::npos) {
          auth_id.UserLength = static_cast<unsigned long>(
              std::min(sizeof(userbuf) - 1, username.size()));
          memcpy(userbuf, username.data(), auth_id.UserLength);
          userbuf[auth_id.UserLength] = 0;
          auth_id.DomainLength = 0;
          domainbuf[auth_id.DomainLength] = 0;
          auth_id.PasswordLength = static_cast<unsigned long>(
              std::min(sizeof(passbuf) - 1, password.GetLength()));
          memcpy(passbuf, sensitive, auth_id.PasswordLength);
          passbuf[auth_id.PasswordLength] = 0;
        } else {
          auth_id.UserLength = static_cast<unsigned long>(
              std::min(sizeof(userbuf) - 1, username.size() - pos - 1));
          memcpy(userbuf, username.data() + pos + 1, auth_id.UserLength);
          userbuf[auth_id.UserLength] = 0;
          auth_id.DomainLength =
              static_cast<unsigned long>(std::min(sizeof(domainbuf) - 1, pos));
          memcpy(domainbuf, username.data(), auth_id.DomainLength);
          domainbuf[auth_id.DomainLength] = 0;
          auth_id.PasswordLength = static_cast<unsigned long>(
              std::min(sizeof(passbuf) - 1, password.GetLength()));
          memcpy(passbuf, sensitive, auth_id.PasswordLength);
          passbuf[auth_id.PasswordLength] = 0;
        }
        ExplicitZeroMemory(sensitive, len);
        delete[] sensitive;
        auth_id.User = userbuf;
        auth_id.Domain = domainbuf;
        auth_id.Password = passbuf;
        auth_id.Flags = SEC_WINNT_AUTH_IDENTITY_ANSI;
        pauth_id = &auth_id;
        RTC_LOG(LS_VERBOSE)
            << "Negotiate protocol: Using specified credentials";
      } else {
        RTC_LOG(LS_VERBOSE) << "Negotiate protocol: Using default credentials";
      }

      CredHandle cred;
      ret = AcquireCredentialsHandleA(
          0, const_cast<char*>(want_negotiate ? NEGOSSP_NAME_A : NTLMSP_NAME_A),
          SECPKG_CRED_OUTBOUND, 0, pauth_id, 0, 0, &cred, &lifetime);
      if (ret != SEC_E_OK) {
        RTC_LOG(LS_ERROR) << "AcquireCredentialsHandle error: "
                          << GetErrorName(ret, SECURITY_ERRORS);
        return HAR_IGNORE;
      }

      // CSecBufferBundle<5, CSecBufferBase::FreeSSPI> sb_out;

      CtxtHandle ctx;
      ret = InitializeSecurityContextA(&cred, 0, spn, flags, 0,
                                       SECURITY_NATIVE_DREP, 0, 0, &ctx,
                                       &out_buf_desc, &ret_flags, &lifetime);
      if (FAILED(ret)) {
        RTC_LOG(LS_ERROR) << "InitializeSecurityContext returned: "
                          << GetErrorName(ret, SECURITY_ERRORS);
        FreeCredentialsHandle(&cred);
        return HAR_IGNORE;
      }

      RTC_DCHECK(!context);
      context = neg = new NegotiateAuthContext(auth_method, cred, ctx);
      neg->specified_credentials = specify_credentials;
      neg->steps = steps;
    }

    if ((ret == SEC_I_COMPLETE_NEEDED) ||
        (ret == SEC_I_COMPLETE_AND_CONTINUE)) {
      ret = CompleteAuthToken(&neg->ctx, &out_buf_desc);
      RTC_LOG(LS_VERBOSE) << "CompleteAuthToken returned: "
                          << GetErrorName(ret, SECURITY_ERRORS);
      if (FAILED(ret)) {
        return HAR_ERROR;
      }
    }

    std::string decoded(out_buf, out_buf + out_sec.cbBuffer);
    response = auth_method;
    response.append(" ");
    response.append(Base64::Encode(decoded));
    return HAR_RESPONSE;
  }
#endif
#endif  // defined(WEBRTC_WIN) && !defined(WINUWP)

  return HAR_IGNORE;
}

//////////////////////////////////////////////////////////////////////

}  // namespace rtc
