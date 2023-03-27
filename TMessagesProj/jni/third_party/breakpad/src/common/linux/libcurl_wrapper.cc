// Copyright (c) 2009, Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

#include <dlfcn.h>

#include <iostream>
#include <string>

#include "common/linux/libcurl_wrapper.h"
#include "common/using_std_string.h"

namespace google_breakpad {
LibcurlWrapper::LibcurlWrapper()
    : init_ok_(false),
      formpost_(NULL),
      lastptr_(NULL),
      headerlist_(NULL) {
  curl_lib_ = dlopen("libcurl.so", RTLD_NOW);
  if (!curl_lib_) {
    curl_lib_ = dlopen("libcurl.so.4", RTLD_NOW);
  }
  if (!curl_lib_) {
    curl_lib_ = dlopen("libcurl.so.3", RTLD_NOW);
  }
  if (!curl_lib_) {
    std::cout << "Could not find libcurl via dlopen";
    return;
  }
  std::cout << "LibcurlWrapper init succeeded";
  init_ok_ = true;
  return;
}

LibcurlWrapper::~LibcurlWrapper() {}

bool LibcurlWrapper::SetProxy(const string& proxy_host,
                              const string& proxy_userpwd) {
  if (!init_ok_) {
    return false;
  }
  // Set proxy information if necessary.
  if (!proxy_host.empty()) {
    (*easy_setopt_)(curl_, CURLOPT_PROXY, proxy_host.c_str());
  } else {
    std::cout << "SetProxy called with empty proxy host.";
    return false;
  }
  if (!proxy_userpwd.empty()) {
    (*easy_setopt_)(curl_, CURLOPT_PROXYUSERPWD, proxy_userpwd.c_str());
  } else {
    std::cout << "SetProxy called with empty proxy username/password.";
    return false;
  }
  std::cout << "Set proxy host to " << proxy_host;
  return true;
}

bool LibcurlWrapper::AddFile(const string& upload_file_path,
                             const string& basename) {
  if (!init_ok_) {
    return false;
  }
  std::cout << "Adding " << upload_file_path << " to form upload.";
  // Add form file.
  (*formadd_)(&formpost_, &lastptr_,
              CURLFORM_COPYNAME, basename.c_str(),
              CURLFORM_FILE, upload_file_path.c_str(),
              CURLFORM_END);

  return true;
}

// Callback to get the response data from server.
static size_t WriteCallback(void *ptr, size_t size,
                            size_t nmemb, void *userp) {
  if (!userp)
    return 0;

  string *response = reinterpret_cast<string *>(userp);
  size_t real_size = size * nmemb;
  response->append(reinterpret_cast<char *>(ptr), real_size);
  return real_size;
}

bool LibcurlWrapper::SendRequest(const string& url,
                                 const std::map<string, string>& parameters,
                                 int* http_status_code,
                                 string* http_header_data,
                                 string* http_response_data) {
  (*easy_setopt_)(curl_, CURLOPT_URL, url.c_str());
  std::map<string, string>::const_iterator iter = parameters.begin();
  for (; iter != parameters.end(); ++iter)
    (*formadd_)(&formpost_, &lastptr_,
                CURLFORM_COPYNAME, iter->first.c_str(),
                CURLFORM_COPYCONTENTS, iter->second.c_str(),
                CURLFORM_END);

  (*easy_setopt_)(curl_, CURLOPT_HTTPPOST, formpost_);
  if (http_response_data != NULL) {
    http_response_data->clear();
    (*easy_setopt_)(curl_, CURLOPT_WRITEFUNCTION, WriteCallback);
    (*easy_setopt_)(curl_, CURLOPT_WRITEDATA,
                     reinterpret_cast<void *>(http_response_data));
  }
  if (http_header_data != NULL) {
    http_header_data->clear();
    (*easy_setopt_)(curl_, CURLOPT_HEADERFUNCTION, WriteCallback);
    (*easy_setopt_)(curl_, CURLOPT_HEADERDATA,
                     reinterpret_cast<void *>(http_header_data));
  }

  CURLcode err_code = CURLE_OK;
  err_code = (*easy_perform_)(curl_);
  easy_strerror_ = reinterpret_cast<const char* (*)(CURLcode)>
                       (dlsym(curl_lib_, "curl_easy_strerror"));

  if (http_status_code != NULL) {
    (*easy_getinfo_)(curl_, CURLINFO_RESPONSE_CODE, http_status_code);
  }

#ifndef NDEBUG
  if (err_code != CURLE_OK)
    fprintf(stderr, "Failed to send http request to %s, error: %s\n",
            url.c_str(),
            (*easy_strerror_)(err_code));
#endif
  if (headerlist_ != NULL) {
    (*slist_free_all_)(headerlist_);
  }

  (*easy_cleanup_)(curl_);
  if (formpost_ != NULL) {
    (*formfree_)(formpost_);
  }

  return err_code == CURLE_OK;
}

bool LibcurlWrapper::Init() {
  if (!init_ok_) {
    std::cout << "Init_OK was not true in LibcurlWrapper::Init(), check earlier log messages";
    return false;
  }

  if (!SetFunctionPointers()) {
    std::cout << "Could not find function pointers";
    init_ok_ = false;
    return false;
  }

  curl_ = (*easy_init_)();

  last_curl_error_ = "No Error";

  if (!curl_) {
    dlclose(curl_lib_);
    std::cout << "Curl initialization failed";
    return false;
  }

  // Disable 100-continue header.
  char buf[] = "Expect:";

  headerlist_ = (*slist_append_)(headerlist_, buf);
  (*easy_setopt_)(curl_, CURLOPT_HTTPHEADER, headerlist_);
  return true;
}

#define SET_AND_CHECK_FUNCTION_POINTER(var, function_name, type) \
  var = reinterpret_cast<type>(dlsym(curl_lib_, function_name)); \
  if (!var) { \
    std::cout << "Could not find libcurl function " << function_name; \
    init_ok_ = false; \
    return false; \
  }

bool LibcurlWrapper::SetFunctionPointers() {

  SET_AND_CHECK_FUNCTION_POINTER(easy_init_,
                                 "curl_easy_init",
                                 CURL*(*)());

  SET_AND_CHECK_FUNCTION_POINTER(easy_setopt_,
                                 "curl_easy_setopt",
                                 CURLcode(*)(CURL*, CURLoption, ...));

  SET_AND_CHECK_FUNCTION_POINTER(formadd_, "curl_formadd",
      CURLFORMcode(*)(curl_httppost**, curl_httppost**, ...));

  SET_AND_CHECK_FUNCTION_POINTER(slist_append_, "curl_slist_append",
      curl_slist*(*)(curl_slist*, const char*));

  SET_AND_CHECK_FUNCTION_POINTER(easy_perform_,
                                 "curl_easy_perform",
                                 CURLcode(*)(CURL*));

  SET_AND_CHECK_FUNCTION_POINTER(easy_cleanup_,
                                 "curl_easy_cleanup",
                                 void(*)(CURL*));

  SET_AND_CHECK_FUNCTION_POINTER(easy_getinfo_,
                                 "curl_easy_getinfo",
                                 CURLcode(*)(CURL *, CURLINFO info, ...));

  SET_AND_CHECK_FUNCTION_POINTER(slist_free_all_,
                                 "curl_slist_free_all",
                                 void(*)(curl_slist*));

  SET_AND_CHECK_FUNCTION_POINTER(formfree_,
                                 "curl_formfree",
                                 void(*)(curl_httppost*));
  return true;
}

}
