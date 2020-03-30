/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_PLATFORM_FILE_H_
#define RTC_BASE_PLATFORM_FILE_H_

#include <stdio.h>
#include <string>

#if defined(WEBRTC_WIN)
#define UNICODE
#include <WinSock2.h>
#include <windows.h>
#endif

namespace rtc {

#if defined(WEBRTC_WIN)
typedef HANDLE PlatformFile;
#elif defined(WEBRTC_POSIX)
typedef int PlatformFile;
#else
#error Unsupported platform
#endif

extern const PlatformFile kInvalidPlatformFileValue;

// Associates a standard FILE stream with an existing PlatformFile.
// Note that after this function has returned a valid FILE stream,
// the PlatformFile should no longer be used.
FILE* FdopenPlatformFileForWriting(PlatformFile file);

// Associates a standard FILE stream with an existing PlatformFile.
// Note that after this function has returned a valid FILE stream,
// the PlatformFile should no longer be used.
FILE* FdopenPlatformFile(PlatformFile file, const char* modes);

// Closes a PlatformFile. Returns true on success, false on failure.
// Don't use ClosePlatformFile to close a file opened with FdopenPlatformFile.
// Use fclose instead.
bool ClosePlatformFile(PlatformFile file);

// Removes a file in the filesystem.
bool RemoveFile(const std::string& path);

// Opens a file for reading and writing. You might want to use base/file.h
// instead.
PlatformFile OpenPlatformFile(const std::string& path);

// Opens a file for reading only. You might want to use base/file.h
// instead.
PlatformFile OpenPlatformFileReadOnly(const std::string& path);

// Creates a new file for reading and writing. If the file already exists it
// will be overwritten. You might want to use base/file.h instead.
PlatformFile CreatePlatformFile(const std::string& path);

}  // namespace rtc

#endif  // RTC_BASE_PLATFORM_FILE_H_
