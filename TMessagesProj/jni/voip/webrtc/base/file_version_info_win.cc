// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/file_version_info_win.h"

#include <windows.h>
#include <stddef.h>

#include <utility>

#include "base/files/file_path.h"
#include "base/logging.h"
#include "base/memory/ptr_util.h"
#include "base/stl_util.h"
#include "base/strings/string_util.h"
#include "base/threading/scoped_blocking_call.h"
#include "base/win/resource_util.h"

namespace {

struct LanguageAndCodePage {
  WORD language;
  WORD code_page;
};

// Returns the \VarFileInfo\Translation value extracted from the
// VS_VERSION_INFO resource in |data|.
LanguageAndCodePage* GetTranslate(const void* data) {
  static constexpr wchar_t kTranslation[] = L"\\VarFileInfo\\Translation";
  LPVOID translate = nullptr;
  UINT dummy_size;
  if (::VerQueryValue(data, kTranslation, &translate, &dummy_size))
    return static_cast<LanguageAndCodePage*>(translate);
  return nullptr;
}

const VS_FIXEDFILEINFO& GetVsFixedFileInfo(const void* data) {
  static constexpr wchar_t kRoot[] = L"\\";
  LPVOID fixed_file_info = nullptr;
  UINT dummy_size;
  CHECK(::VerQueryValue(data, kRoot, &fixed_file_info, &dummy_size));
  return *static_cast<VS_FIXEDFILEINFO*>(fixed_file_info);
}

}  // namespace

FileVersionInfoWin::~FileVersionInfoWin() = default;

// static
std::unique_ptr<FileVersionInfo>
FileVersionInfo::CreateFileVersionInfoForModule(HMODULE module) {
  void* data;
  size_t version_info_length;
  const bool has_version_resource = base::win::GetResourceFromModule(
      module, VS_VERSION_INFO, RT_VERSION, &data, &version_info_length);
  if (!has_version_resource)
    return nullptr;

  const LanguageAndCodePage* translate = GetTranslate(data);
  if (!translate)
    return nullptr;

  return base::WrapUnique(
      new FileVersionInfoWin(data, translate->language, translate->code_page));
}

// static
std::unique_ptr<FileVersionInfo> FileVersionInfo::CreateFileVersionInfo(
    const base::FilePath& file_path) {
  return FileVersionInfoWin::CreateFileVersionInfoWin(file_path);
}

// static
std::unique_ptr<FileVersionInfoWin>
FileVersionInfoWin::CreateFileVersionInfoWin(const base::FilePath& file_path) {
  base::ScopedBlockingCall scoped_blocking_call(FROM_HERE,
                                                base::BlockingType::MAY_BLOCK);

  DWORD dummy;
  const wchar_t* path = file_path.value().c_str();
  const DWORD length = ::GetFileVersionInfoSize(path, &dummy);
  if (length == 0)
    return nullptr;

  std::vector<uint8_t> data(length, 0);

  if (!::GetFileVersionInfo(path, dummy, length, data.data()))
    return nullptr;

  const LanguageAndCodePage* translate = GetTranslate(data.data());
  if (!translate)
    return nullptr;

  return base::WrapUnique(new FileVersionInfoWin(
      std::move(data), translate->language, translate->code_page));
}

base::string16 FileVersionInfoWin::company_name() {
  return GetStringValue(STRING16_LITERAL("CompanyName"));
}

base::string16 FileVersionInfoWin::company_short_name() {
  return GetStringValue(STRING16_LITERAL("CompanyShortName"));
}

base::string16 FileVersionInfoWin::internal_name() {
  return GetStringValue(STRING16_LITERAL("InternalName"));
}

base::string16 FileVersionInfoWin::product_name() {
  return GetStringValue(STRING16_LITERAL("ProductName"));
}

base::string16 FileVersionInfoWin::product_short_name() {
  return GetStringValue(STRING16_LITERAL("ProductShortName"));
}

base::string16 FileVersionInfoWin::product_version() {
  return GetStringValue(STRING16_LITERAL("ProductVersion"));
}

base::string16 FileVersionInfoWin::file_description() {
  return GetStringValue(STRING16_LITERAL("FileDescription"));
}

base::string16 FileVersionInfoWin::file_version() {
  return GetStringValue(STRING16_LITERAL("FileVersion"));
}

base::string16 FileVersionInfoWin::original_filename() {
  return GetStringValue(STRING16_LITERAL("OriginalFilename"));
}

base::string16 FileVersionInfoWin::special_build() {
  return GetStringValue(STRING16_LITERAL("SpecialBuild"));
}

bool FileVersionInfoWin::GetValue(const base::char16* name,
                                  base::string16* value) const {
  const struct LanguageAndCodePage lang_codepages[] = {
      // Use the language and codepage from the DLL.
      {language_, code_page_},
      // Use the default language and codepage from the DLL.
      {::GetUserDefaultLangID(), code_page_},
      // Use the language from the DLL and Latin codepage (most common).
      {language_, 1252},
      // Use the default language and Latin codepage (most common).
      {::GetUserDefaultLangID(), 1252},
  };

  for (const auto& lang_codepage : lang_codepages) {
    wchar_t sub_block[MAX_PATH];
    _snwprintf_s(sub_block, MAX_PATH, MAX_PATH,
                 L"\\StringFileInfo\\%04x%04x\\%ls", lang_codepage.language,
                 lang_codepage.code_page, base::as_wcstr(name));
    LPVOID value_ptr = nullptr;
    uint32_t size;
    BOOL r = ::VerQueryValue(data_, sub_block, &value_ptr, &size);
    if (r && value_ptr && size) {
      value->assign(static_cast<base::char16*>(value_ptr), size - 1);
      return true;
    }
  }
  return false;
}

base::string16 FileVersionInfoWin::GetStringValue(
    const base::char16* name) const {
  base::string16 str;
  GetValue(name, &str);
  return str;
}

base::Version FileVersionInfoWin::GetFileVersion() const {
  return base::Version({HIWORD(fixed_file_info_.dwFileVersionMS),
                        LOWORD(fixed_file_info_.dwFileVersionMS),
                        HIWORD(fixed_file_info_.dwFileVersionLS),
                        LOWORD(fixed_file_info_.dwFileVersionLS)});
}

FileVersionInfoWin::FileVersionInfoWin(std::vector<uint8_t>&& data,
                                       WORD language,
                                       WORD code_page)
    : owned_data_(std::move(data)),
      data_(owned_data_.data()),
      language_(language),
      code_page_(code_page),
      fixed_file_info_(GetVsFixedFileInfo(data_)) {
  DCHECK(!owned_data_.empty());
}

FileVersionInfoWin::FileVersionInfoWin(void* data,
                                       WORD language,
                                       WORD code_page)
    : data_(data),
      language_(language),
      code_page_(code_page),
      fixed_file_info_(GetVsFixedFileInfo(data)) {
  DCHECK(data_);
}
