/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_CRYPT_STRING_H_
#define RTC_BASE_CRYPT_STRING_H_

#include <string.h>

#include <memory>
#include <string>
#include <vector>

namespace rtc {

class CryptStringImpl {
 public:
  virtual ~CryptStringImpl() {}
  virtual size_t GetLength() const = 0;
  virtual void CopyTo(char* dest, bool nullterminate) const = 0;
  virtual std::string UrlEncode() const = 0;
  virtual CryptStringImpl* Copy() const = 0;
  virtual void CopyRawTo(std::vector<unsigned char>* dest) const = 0;
};

class EmptyCryptStringImpl : public CryptStringImpl {
 public:
  ~EmptyCryptStringImpl() override {}
  size_t GetLength() const override;
  void CopyTo(char* dest, bool nullterminate) const override;
  std::string UrlEncode() const override;
  CryptStringImpl* Copy() const override;
  void CopyRawTo(std::vector<unsigned char>* dest) const override;
};

class CryptString {
 public:
  CryptString();
  size_t GetLength() const { return impl_->GetLength(); }
  void CopyTo(char* dest, bool nullterminate) const {
    impl_->CopyTo(dest, nullterminate);
  }
  CryptString(const CryptString& other);
  explicit CryptString(const CryptStringImpl& impl);
  ~CryptString();
  CryptString& operator=(const CryptString& other) {
    if (this != &other) {
      impl_.reset(other.impl_->Copy());
    }
    return *this;
  }
  void Clear() { impl_.reset(new EmptyCryptStringImpl()); }
  std::string UrlEncode() const { return impl_->UrlEncode(); }
  void CopyRawTo(std::vector<unsigned char>* dest) const {
    return impl_->CopyRawTo(dest);
  }

 private:
  std::unique_ptr<const CryptStringImpl> impl_;
};

}  // namespace rtc

#endif  // RTC_BASE_CRYPT_STRING_H_
