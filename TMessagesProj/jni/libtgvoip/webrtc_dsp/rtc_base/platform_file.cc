/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/platform_file.h"

#if defined(WEBRTC_WIN)
#define UNICODE
#include <io.h>

#include "rtc_base/stringutils.h"  // For ToUtf16
#else
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#endif

namespace rtc {

FILE* FdopenPlatformFileForWriting(PlatformFile file) {
  return FdopenPlatformFile(file, "w");
}

#if defined(WEBRTC_WIN)
const PlatformFile kInvalidPlatformFileValue = INVALID_HANDLE_VALUE;

FILE* FdopenPlatformFile(PlatformFile file, const char* modes) {
  if (file == kInvalidPlatformFileValue)
    return nullptr;
  int fd = _open_osfhandle(reinterpret_cast<intptr_t>(file), 0);
  if (fd < 0)
    return nullptr;

  return _fdopen(fd, modes);
}

bool ClosePlatformFile(PlatformFile file) {
  return CloseHandle(file) != 0;
}

bool RemoveFile(const std::string& path) {
#if defined(WEBRTC_UWP)
	return kInvalidPlatformFileValue;
#else // defined(WEBRTC_WIN)
  return ::DeleteFile(ToUtf16(path).c_str()) != 0;
#endif
}

PlatformFile OpenPlatformFile(const std::string& path) {
#if defined(WEBRTC_UWP)
	return kInvalidPlatformFileValue;
#else // defined(WEBRTC_WIN)
  return ::CreateFile(ToUtf16(path).c_str(), GENERIC_READ | GENERIC_WRITE, 0,
                      nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
#endif // defined(WEBRTC_WIN)
}

PlatformFile OpenPlatformFileReadOnly(const std::string& path) {
#if defined(WEBRTC_UWP)
	return kInvalidPlatformFileValue;
#else // defined(WEBRTC_WIN)
  return ::CreateFile(ToUtf16(path).c_str(), GENERIC_READ, FILE_SHARE_READ,
                      nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
#endif // defined(WEBRTC_WIN)
}

PlatformFile CreatePlatformFile(const std::string& path) {
#if defined(WEBRTC_UWP)
	return kInvalidPlatformFileValue;
#else // defined(WEBRTC_WIN)
  return ::CreateFile(ToUtf16(path).c_str(), GENERIC_READ | GENERIC_WRITE, 0,
                      nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
#endif // defined(WEBRTC_WIN)
}

#else  // defined(WEBRTC_WIN)

const PlatformFile kInvalidPlatformFileValue = -1;

FILE* FdopenPlatformFile(PlatformFile file, const char* modes) {
  return fdopen(file, modes);
}

bool ClosePlatformFile(PlatformFile file) {
  return close(file) == 0;
}

bool RemoveFile(const std::string& path) {
  return ::unlink(path.c_str()) == 0;
}

PlatformFile OpenPlatformFile(const std::string& path) {
  return ::open(path.c_str(), O_RDWR);
}

PlatformFile OpenPlatformFileReadOnly(const std::string& path) {
  return ::open(path.c_str(), O_RDONLY);
}

PlatformFile CreatePlatformFile(const std::string& path) {
  return ::open(path.c_str(), O_CREAT | O_TRUNC | O_RDWR, S_IRUSR | S_IWUSR);
}

#endif

}  // namespace rtc
