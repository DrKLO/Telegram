// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_FILE_VERSION_INFO_WIN_H_
#define BASE_FILE_VERSION_INFO_WIN_H_

#include <windows.h>

#include <stdint.h>

#include <memory>
#include <string>
#include <vector>

#include "base/base_export.h"
#include "base/file_version_info.h"
#include "base/macros.h"
#include "base/version.h"

struct tagVS_FIXEDFILEINFO;
typedef tagVS_FIXEDFILEINFO VS_FIXEDFILEINFO;

class BASE_EXPORT FileVersionInfoWin : public FileVersionInfo {
 public:
  ~FileVersionInfoWin() override;

  // Accessors to the different version properties.
  // Returns an empty string if the property is not found.
  base::string16 company_name() override;
  base::string16 company_short_name() override;
  base::string16 product_name() override;
  base::string16 product_short_name() override;
  base::string16 internal_name() override;
  base::string16 product_version() override;
  base::string16 special_build() override;
  base::string16 original_filename() override;
  base::string16 file_description() override;
  base::string16 file_version() override;

  // Lets you access other properties not covered above. |value| is only
  // modified if GetValue() returns true.
  bool GetValue(const base::char16* name, base::string16* value) const;

  // Similar to GetValue but returns a string16 (empty string if the property
  // does not exist).
  base::string16 GetStringValue(const base::char16* name) const;

  // Get file version number in dotted version format.
  base::Version GetFileVersion() const;

  // Behaves like CreateFileVersionInfo, but returns a FileVersionInfoWin.
  static std::unique_ptr<FileVersionInfoWin> CreateFileVersionInfoWin(
      const base::FilePath& file_path);

 private:
  friend FileVersionInfo;

  // |data| is a VS_VERSION_INFO resource. |language| and |code_page| are
  // extracted from the \VarFileInfo\Translation value of |data|.
  FileVersionInfoWin(std::vector<uint8_t>&& data,
                     WORD language,
                     WORD code_page);
  FileVersionInfoWin(void* data, WORD language, WORD code_page);

  const std::vector<uint8_t> owned_data_;
  const void* const data_;
  const WORD language_;
  const WORD code_page_;

  // This is a reference for a portion of |data_|.
  const VS_FIXEDFILEINFO& fixed_file_info_;

  DISALLOW_COPY_AND_ASSIGN(FileVersionInfoWin);
};

#endif  // BASE_FILE_VERSION_INFO_WIN_H_
