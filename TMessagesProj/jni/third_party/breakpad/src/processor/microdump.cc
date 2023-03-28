// Copyright (c) 2014 Google Inc.
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

// microdump.cc: A microdump reader.
//
// See microdump.h for documentation.

#include "google_breakpad/processor/microdump.h"

#include <stdio.h>
#include <string.h>

#include <memory>
#include <sstream>
#include <string>
#include <vector>

#include "google_breakpad/common/minidump_cpu_arm.h"
#include "google_breakpad/processor/code_module.h"
#include "processor/basic_code_module.h"
#include "processor/linked_ptr.h"
#include "processor/logging.h"
#include "processor/range_map-inl.h"

namespace {
static const char kGoogleBreakpadKey[] = "google-breakpad";
static const char kMicrodumpBegin[] = "-----BEGIN BREAKPAD MICRODUMP-----";
static const char kMicrodumpEnd[] = "-----END BREAKPAD MICRODUMP-----";
static const char kOsKey[] = ": O ";
static const char kCpuKey[] = ": C ";
static const char kMmapKey[] = ": M ";
static const char kStackKey[] = ": S ";
static const char kStackFirstLineKey[] = ": S 0 ";
static const char kArmArchitecture[] = "arm";
static const char kArm64Architecture[] = "arm64";

template<typename T>
T HexStrToL(const string& str) {
  uint64_t res = 0;
  std::istringstream ss(str);
  ss >> std::hex >> res;
  return static_cast<T>(res);
}

std::vector<uint8_t> ParseHexBuf(const string& str) {
  std::vector<uint8_t> buf;
  for (size_t i = 0; i < str.length(); i += 2) {
    buf.push_back(HexStrToL<uint8_t>(str.substr(i, 2)));
  }
  return buf;
}

}  // namespace

namespace google_breakpad {

//
// MicrodumpModules
//

void MicrodumpModules::Add(const CodeModule* module) {
  linked_ptr<const CodeModule> module_ptr(module);
  if (!map_->StoreRange(module->base_address(), module->size(), module_ptr)) {
    BPLOG(ERROR) << "Module " << module->code_file() <<
                    " could not be stored";
  }
}


//
// MicrodumpContext
//

void MicrodumpContext::SetContextARM(MDRawContextARM* arm) {
  DumpContext::SetContextFlags(MD_CONTEXT_ARM);
  DumpContext::SetContextARM(arm);
  valid_ = true;
}

void MicrodumpContext::SetContextARM64(MDRawContextARM64* arm64) {
  DumpContext::SetContextFlags(MD_CONTEXT_ARM64);
  DumpContext::SetContextARM64(arm64);
  valid_ = true;
}


//
// MicrodumpMemoryRegion
//

MicrodumpMemoryRegion::MicrodumpMemoryRegion() : base_address_(0) { }

void MicrodumpMemoryRegion::Init(uint64_t base_address,
                                 const std::vector<uint8_t>& contents) {
  base_address_ = base_address;
  contents_ = contents;
}

uint64_t MicrodumpMemoryRegion::GetBase() const { return base_address_; }

uint32_t MicrodumpMemoryRegion::GetSize() const { return contents_.size(); }

bool MicrodumpMemoryRegion::GetMemoryAtAddress(uint64_t address,
                                               uint8_t* value) const {
  return GetMemoryLittleEndian(address, value);
}

bool MicrodumpMemoryRegion::GetMemoryAtAddress(uint64_t address,
                                               uint16_t* value) const {
  return GetMemoryLittleEndian(address, value);
}

bool MicrodumpMemoryRegion::GetMemoryAtAddress(uint64_t address,
                                               uint32_t* value) const {
  return GetMemoryLittleEndian(address, value);
}

bool MicrodumpMemoryRegion::GetMemoryAtAddress(uint64_t address,
                                               uint64_t* value) const {
  return GetMemoryLittleEndian(address, value);
}

template<typename ValueType>
bool MicrodumpMemoryRegion::GetMemoryLittleEndian(uint64_t address,
                                                  ValueType* value) const {
  if (address < base_address_ ||
      address - base_address_ + sizeof(ValueType) > contents_.size())
    return false;
  ValueType v = 0;
  uint64_t start = address - base_address_;
  // The loop condition is odd, but it's correct for size_t.
  for (size_t i = sizeof(ValueType) - 1; i < sizeof(ValueType); i--)
    v = (v << 8) | static_cast<uint8_t>(contents_[start + i]);
  *value = v;
  return true;
}

void MicrodumpMemoryRegion::Print() const {
  // Not reached, just needed to honor the base class contract.
  assert(false);
}

//
// Microdump
//
Microdump::Microdump(const string& contents)
  : context_(new MicrodumpContext()),
    stack_region_(new MicrodumpMemoryRegion()),
    modules_(new MicrodumpModules()),
    system_info_(new SystemInfo()) {
  assert(!contents.empty());

  bool in_microdump = false;
  string line;
  uint64_t stack_start = 0;
  std::vector<uint8_t> stack_content;
  string arch;

  std::istringstream stream(contents);
  while (std::getline(stream, line)) {
    if (line.find(kGoogleBreakpadKey) == string::npos) {
      continue;
    }
    if (line.find(kMicrodumpBegin) != string::npos) {
      in_microdump = true;
      continue;
    }
    if (line.find(kMicrodumpEnd) != string::npos) {
      break;
    }

    if (!in_microdump) {
      continue;
    }

    size_t pos;
    if ((pos = line.find(kOsKey)) != string::npos) {
      string os_str(line, pos + strlen(kOsKey));
      std::istringstream os_tokens(os_str);
      string os_id;
      string num_cpus;
      string os_version;
      // This reflect the actual HW arch and might not match the arch emulated
      // for the execution (e.g., running a 32-bit binary on a 64-bit cpu).
      string hw_arch;

      os_tokens >> os_id;
      os_tokens >> arch;
      os_tokens >> num_cpus;
      os_tokens >> hw_arch;
      std::getline(os_tokens, os_version);
      os_version.erase(0, 1);  // remove leading space.

      system_info_->cpu = hw_arch;
      system_info_->cpu_count = HexStrToL<uint8_t>(num_cpus);
      system_info_->os_version = os_version;

      if (os_id == "L") {
        system_info_->os = "Linux";
        system_info_->os_short = "linux";
      } else if (os_id == "A") {
        system_info_->os = "Android";
        system_info_->os_short = "android";
      }

      // OS line also contains release and version for future use.
    } else if ((pos = line.find(kStackKey)) != string::npos) {
      if (line.find(kStackFirstLineKey) != string::npos) {
        // The first line of the stack (S 0 stack header) provides the value of
        // the stack pointer, the start address of the stack being dumped and
        // the length of the stack. We could use it in future to double check
        // that we received all the stack as expected.
        continue;
      }
      string stack_str(line, pos + strlen(kStackKey));
      std::istringstream stack_tokens(stack_str);
      string start_addr_str;
      string raw_content;
      stack_tokens >> start_addr_str;
      stack_tokens >> raw_content;
      uint64_t start_addr = HexStrToL<uint64_t>(start_addr_str);

      if (stack_start != 0) {
        // Verify that the stack chunks in the microdump are contiguous.
        assert(start_addr == stack_start + stack_content.size());
      } else {
        stack_start = start_addr;
      }
      std::vector<uint8_t> chunk = ParseHexBuf(raw_content);
      stack_content.insert(stack_content.end(), chunk.begin(), chunk.end());

    } else if ((pos = line.find(kCpuKey)) != string::npos) {
      string cpu_state_str(line, pos + strlen(kCpuKey));
      std::vector<uint8_t> cpu_state_raw = ParseHexBuf(cpu_state_str);
      if (strcmp(arch.c_str(), kArmArchitecture) == 0) {
        if (cpu_state_raw.size() != sizeof(MDRawContextARM)) {
          std::cerr << "Malformed CPU context. Got " << cpu_state_raw.size() <<
              " bytes instead of " << sizeof(MDRawContextARM) << std::endl;
          continue;
        }
        MDRawContextARM* arm = new MDRawContextARM();
        memcpy(arm, &cpu_state_raw[0], cpu_state_raw.size());
        context_->SetContextARM(arm);
      } else if (strcmp(arch.c_str(), kArm64Architecture) == 0) {
        if (cpu_state_raw.size() != sizeof(MDRawContextARM64)) {
          std::cerr << "Malformed CPU context. Got " << cpu_state_raw.size() <<
              " bytes instead of " << sizeof(MDRawContextARM64) << std::endl;
          continue;
        }
        MDRawContextARM64* arm = new MDRawContextARM64();
        memcpy(arm, &cpu_state_raw[0], cpu_state_raw.size());
        context_->SetContextARM64(arm);
      } else {
        std::cerr << "Unsupported architecture: " << arch << std::endl;
      }
    } else if ((pos = line.find(kMmapKey)) != string::npos) {
      string mmap_line(line, pos + strlen(kMmapKey));
      std::istringstream mmap_tokens(mmap_line);
      string addr, offset, size, identifier, filename;
      mmap_tokens >> addr;
      mmap_tokens >> offset;
      mmap_tokens >> size;
      mmap_tokens >> identifier;
      mmap_tokens >> filename;

      modules_->Add(new BasicCodeModule(
          HexStrToL<uint64_t>(addr),  // base_address
          HexStrToL<uint64_t>(size),  // size
          filename,                   // code_file
          identifier,                 // code_identifier
          filename,                   // debug_file
          identifier,                 // debug_identifier
          ""));                       // version
    }
  }
  stack_region_->Init(stack_start, stack_content);
}

}  // namespace google_breakpad

