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


#include "common/linux/google_crashdump_uploader.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>

#include <iostream>

#include "common/using_std_string.h"

namespace google_breakpad {

GoogleCrashdumpUploader::GoogleCrashdumpUploader(const string& product,
                                                 const string& version,
                                                 const string& guid,
                                                 const string& ptime,
                                                 const string& ctime,
                                                 const string& email,
                                                 const string& comments,
                                                 const string& minidump_pathname,
                                                 const string& crash_server,
                                                 const string& proxy_host,
                                                 const string& proxy_userpassword) {
  LibcurlWrapper* http_layer = new LibcurlWrapper();
  Init(product,
       version,
       guid,
       ptime,
       ctime,
       email,
       comments,
       minidump_pathname,
       crash_server,
       proxy_host,
       proxy_userpassword,
       http_layer);
}

GoogleCrashdumpUploader::GoogleCrashdumpUploader(const string& product,
                                                 const string& version,
                                                 const string& guid,
                                                 const string& ptime,
                                                 const string& ctime,
                                                 const string& email,
                                                 const string& comments,
                                                 const string& minidump_pathname,
                                                 const string& crash_server,
                                                 const string& proxy_host,
                                                 const string& proxy_userpassword,
                                                 LibcurlWrapper* http_layer) {
  Init(product,
       version,
       guid,
       ptime,
       ctime,
       email,
       comments,
       minidump_pathname,
       crash_server,
       proxy_host,
       proxy_userpassword,
       http_layer);
}

void GoogleCrashdumpUploader::Init(const string& product,
                                   const string& version,
                                   const string& guid,
                                   const string& ptime,
                                   const string& ctime,
                                   const string& email,
                                   const string& comments,
                                   const string& minidump_pathname,
                                   const string& crash_server,
                                   const string& proxy_host,
                                   const string& proxy_userpassword,
                                   LibcurlWrapper* http_layer) {
  product_ = product;
  version_ = version;
  guid_ = guid;
  ptime_ = ptime;
  ctime_ = ctime;
  email_ = email;
  comments_ = comments;
  http_layer_.reset(http_layer);

  crash_server_ = crash_server;
  proxy_host_ = proxy_host;
  proxy_userpassword_ = proxy_userpassword;
  minidump_pathname_ = minidump_pathname;
  std::cout << "Uploader initializing";
  std::cout << "\tProduct: " << product_;
  std::cout << "\tVersion: " << version_;
  std::cout << "\tGUID: " << guid_;
  if (!ptime_.empty()) {
    std::cout << "\tProcess uptime: " << ptime_;
  }
  if (!ctime_.empty()) {
    std::cout << "\tCumulative Process uptime: " << ctime_;
  }
  if (!email_.empty()) {
    std::cout << "\tEmail: " << email_;
  }
  if (!comments_.empty()) {
    std::cout << "\tComments: " << comments_;
  }
}

bool GoogleCrashdumpUploader::CheckRequiredParametersArePresent() {
  string error_text;
  if (product_.empty()) {
    error_text.append("\nProduct name must be specified.");
  }

  if (version_.empty()) {
    error_text.append("\nProduct version must be specified.");
  }

  if (guid_.empty()) {
    error_text.append("\nClient ID must be specified.");
  }

  if (minidump_pathname_.empty()) {
    error_text.append("\nMinidump pathname must be specified.");
  }

  if (!error_text.empty()) {
    std::cout << error_text;
    return false;
  }
  return true;

}

bool GoogleCrashdumpUploader::Upload(int* http_status_code,
                                     string* http_response_header,
                                     string* http_response_body) {
  bool ok = http_layer_->Init();
  if (!ok) {
    std::cout << "http layer init failed";
    return ok;
  }

  if (!CheckRequiredParametersArePresent()) {
    return false;
  }

  struct stat st;
  int err = stat(minidump_pathname_.c_str(), &st);
  if (err) {
    std::cout << minidump_pathname_ << " could not be found";
    return false;
  }

  parameters_["prod"] = product_;
  parameters_["ver"] = version_;
  parameters_["guid"] = guid_;
  parameters_["ptime"] = ptime_;
  parameters_["ctime"] = ctime_;
  parameters_["email"] = email_;
  parameters_["comments_"] = comments_;
  if (!http_layer_->AddFile(minidump_pathname_,
                            "upload_file_minidump")) {
    return false;
  }
  std::cout << "Sending request to " << crash_server_;
  return http_layer_->SendRequest(crash_server_,
                                  parameters_,
                                  http_status_code,
                                  http_response_header,
                                  http_response_body);
}
}
