// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/cpu.h"

#include <limits.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include <algorithm>
#include <utility>

#include "base/stl_util.h"

#if defined(ARCH_CPU_ARM_FAMILY) && (defined(OS_ANDROID) || defined(OS_LINUX))
#include "base/files/file_util.h"
#endif

#if defined(ARCH_CPU_X86_FAMILY)
#if defined(COMPILER_MSVC)
#include <intrin.h>
#include <immintrin.h>  // For _xgetbv()
#endif
#endif

namespace base {

#if defined(ARCH_CPU_X86_FAMILY)
namespace internal {

std::tuple<int, int, int, int> ComputeX86FamilyAndModel(
    const std::string& vendor,
    int signature) {
  int family = (signature >> 8) & 0xf;
  int model = (signature >> 4) & 0xf;
  int ext_family = 0;
  int ext_model = 0;

  // The "Intel 64 and IA-32 Architectures Developer's Manual: Vol. 2A"
  // specifies the Extended Model is defined only when the Base Family is
  // 06h or 0Fh.
  // The "AMD CPUID Specification" specifies that the Extended Model is
  // defined only when Base Family is 0Fh.
  // Both manuals define the display model as
  // {ExtendedModel[3:0],BaseModel[3:0]} in that case.
  if (family == 0xf || (family == 0x6 && vendor == "GenuineIntel")) {
    ext_model = (signature >> 16) & 0xf;
    model += ext_model << 4;
  }
  // Both the "Intel 64 and IA-32 Architectures Developer's Manual: Vol. 2A"
  // and the "AMD CPUID Specification" specify that the Extended Family is
  // defined only when the Base Family is 0Fh.
  // Both manuals define the display family as {0000b,BaseFamily[3:0]} +
  // ExtendedFamily[7:0] in that case.
  if (family == 0xf) {
    ext_family = (signature >> 20) & 0xff;
    family += ext_family;
  }

  return {family, model, ext_family, ext_model};
}

}  // namespace internal
#endif  // defined(ARCH_CPU_X86_FAMILY)

CPU::CPU()
  : signature_(0),
    type_(0),
    family_(0),
    model_(0),
    stepping_(0),
    ext_model_(0),
    ext_family_(0),
    has_mmx_(false),
    has_sse_(false),
    has_sse2_(false),
    has_sse3_(false),
    has_ssse3_(false),
    has_sse41_(false),
    has_sse42_(false),
    has_popcnt_(false),
    has_avx_(false),
    has_avx2_(false),
    has_aesni_(false),
    has_non_stop_time_stamp_counter_(false),
    is_running_in_vm_(false),
    cpu_vendor_("unknown") {
  Initialize();
}

namespace {

#if defined(ARCH_CPU_X86_FAMILY)
#if !defined(COMPILER_MSVC)

#if defined(__pic__) && defined(__i386__)

void __cpuid(int cpu_info[4], int info_type) {
  __asm__ volatile(
      "mov %%ebx, %%edi\n"
      "cpuid\n"
      "xchg %%edi, %%ebx\n"
      : "=a"(cpu_info[0]), "=D"(cpu_info[1]), "=c"(cpu_info[2]),
        "=d"(cpu_info[3])
      : "a"(info_type), "c"(0));
}

#else

void __cpuid(int cpu_info[4], int info_type) {
  __asm__ volatile("cpuid\n"
                   : "=a"(cpu_info[0]), "=b"(cpu_info[1]), "=c"(cpu_info[2]),
                     "=d"(cpu_info[3])
                   : "a"(info_type), "c"(0));
}

#endif
#endif  // !defined(COMPILER_MSVC)

// xgetbv returns the value of an Intel Extended Control Register (XCR).
// Currently only XCR0 is defined by Intel so |xcr| should always be zero.
uint64_t xgetbv(uint32_t xcr) {
#if defined(COMPILER_MSVC)
  return _xgetbv(xcr);
#else
  uint32_t eax, edx;

  __asm__ volatile (
    "xgetbv" : "=a"(eax), "=d"(edx) : "c"(xcr));
  return (static_cast<uint64_t>(edx) << 32) | eax;
#endif  // defined(COMPILER_MSVC)
}

#endif  // ARCH_CPU_X86_FAMILY

#if defined(ARCH_CPU_ARM_FAMILY) && (defined(OS_ANDROID) || defined(OS_LINUX))
std::string* CpuInfoBrand() {
  static std::string* brand = []() {
    // This function finds the value from /proc/cpuinfo under the key "model
    // name" or "Processor". "model name" is used in Linux 3.8 and later (3.7
    // and later for arm64) and is shown once per CPU. "Processor" is used in
    // earler versions and is shown only once at the top of /proc/cpuinfo
    // regardless of the number CPUs.
    const char kModelNamePrefix[] = "model name\t: ";
    const char kProcessorPrefix[] = "Processor\t: ";

    std::string contents;
    ReadFileToString(FilePath("/proc/cpuinfo"), &contents);
    DCHECK(!contents.empty());

    std::istringstream iss(contents);
    std::string line;
    while (std::getline(iss, line)) {
      if (line.compare(0, strlen(kModelNamePrefix), kModelNamePrefix) == 0)
        return new std::string(line.substr(strlen(kModelNamePrefix)));
      if (line.compare(0, strlen(kProcessorPrefix), kProcessorPrefix) == 0)
        return new std::string(line.substr(strlen(kProcessorPrefix)));
    }

    return new std::string();
  }();

  return brand;
}
#endif  // defined(ARCH_CPU_ARM_FAMILY) && (defined(OS_ANDROID) ||
        // defined(OS_LINUX))

}  // namespace

void CPU::Initialize() {
#if defined(ARCH_CPU_X86_FAMILY)
  int cpu_info[4] = {-1};
  // This array is used to temporarily hold the vendor name and then the brand
  // name. Thus it has to be big enough for both use cases. There are
  // static_asserts below for each of the use cases to make sure this array is
  // big enough.
  char cpu_string[sizeof(cpu_info) * 3 + 1];

  // __cpuid with an InfoType argument of 0 returns the number of
  // valid Ids in CPUInfo[0] and the CPU identification string in
  // the other three array elements. The CPU identification string is
  // not in linear order. The code below arranges the information
  // in a human readable form. The human readable order is CPUInfo[1] |
  // CPUInfo[3] | CPUInfo[2]. CPUInfo[2] and CPUInfo[3] are swapped
  // before using memcpy() to copy these three array elements to |cpu_string|.
  __cpuid(cpu_info, 0);
  int num_ids = cpu_info[0];
  std::swap(cpu_info[2], cpu_info[3]);
  static constexpr size_t kVendorNameSize = 3 * sizeof(cpu_info[1]);
  static_assert(kVendorNameSize < base::size(cpu_string),
                "cpu_string too small");
  memcpy(cpu_string, &cpu_info[1], kVendorNameSize);
  cpu_string[kVendorNameSize] = '\0';
  cpu_vendor_ = cpu_string;

  // Interpret CPU feature information.
  if (num_ids > 0) {
    int cpu_info7[4] = {0};
    __cpuid(cpu_info, 1);
    if (num_ids >= 7) {
      __cpuid(cpu_info7, 7);
    }
    signature_ = cpu_info[0];
    stepping_ = cpu_info[0] & 0xf;
    type_ = (cpu_info[0] >> 12) & 0x3;
    std::tie(family_, model_, ext_family_, ext_model_) =
        internal::ComputeX86FamilyAndModel(cpu_vendor_, signature_);
    has_mmx_ =   (cpu_info[3] & 0x00800000) != 0;
    has_sse_ =   (cpu_info[3] & 0x02000000) != 0;
    has_sse2_ =  (cpu_info[3] & 0x04000000) != 0;
    has_sse3_ =  (cpu_info[2] & 0x00000001) != 0;
    has_ssse3_ = (cpu_info[2] & 0x00000200) != 0;
    has_sse41_ = (cpu_info[2] & 0x00080000) != 0;
    has_sse42_ = (cpu_info[2] & 0x00100000) != 0;
    has_popcnt_ = (cpu_info[2] & 0x00800000) != 0;

    // "Hypervisor Present Bit: Bit 31 of ECX of CPUID leaf 0x1."
    // See https://lwn.net/Articles/301888/
    // This is checking for any hypervisor. Hypervisors may choose not to
    // announce themselves. Hypervisors trap CPUID and sometimes return
    // different results to underlying hardware.
    is_running_in_vm_ = (cpu_info[2] & 0x80000000) != 0;

    // AVX instructions will generate an illegal instruction exception unless
    //   a) they are supported by the CPU,
    //   b) XSAVE is supported by the CPU and
    //   c) XSAVE is enabled by the kernel.
    // See http://software.intel.com/en-us/blogs/2011/04/14/is-avx-enabled
    //
    // In addition, we have observed some crashes with the xgetbv instruction
    // even after following Intel's example code. (See crbug.com/375968.)
    // Because of that, we also test the XSAVE bit because its description in
    // the CPUID documentation suggests that it signals xgetbv support.
    has_avx_ =
        (cpu_info[2] & 0x10000000) != 0 &&
        (cpu_info[2] & 0x04000000) != 0 /* XSAVE */ &&
        (cpu_info[2] & 0x08000000) != 0 /* OSXSAVE */ &&
        (xgetbv(0) & 6) == 6 /* XSAVE enabled by kernel */;
    has_aesni_ = (cpu_info[2] & 0x02000000) != 0;
    has_avx2_ = has_avx_ && (cpu_info7[1] & 0x00000020) != 0;
  }

  // Get the brand string of the cpu.
  __cpuid(cpu_info, 0x80000000);
  const int max_parameter = cpu_info[0];

  static constexpr int kParameterStart = 0x80000002;
  static constexpr int kParameterEnd = 0x80000004;
  static constexpr int kParameterSize = kParameterEnd - kParameterStart + 1;
  static_assert(kParameterSize * sizeof(cpu_info) + 1 == base::size(cpu_string),
                "cpu_string has wrong size");

  if (max_parameter >= kParameterEnd) {
    size_t i = 0;
    for (int parameter = kParameterStart; parameter <= kParameterEnd;
         ++parameter) {
      __cpuid(cpu_info, parameter);
      memcpy(&cpu_string[i], cpu_info, sizeof(cpu_info));
      i += sizeof(cpu_info);
    }
    cpu_string[i] = '\0';
    cpu_brand_ = cpu_string;
  }

  static constexpr int kParameterContainingNonStopTimeStampCounter = 0x80000007;
  if (max_parameter >= kParameterContainingNonStopTimeStampCounter) {
    __cpuid(cpu_info, kParameterContainingNonStopTimeStampCounter);
    has_non_stop_time_stamp_counter_ = (cpu_info[3] & (1 << 8)) != 0;
  }

  if (!has_non_stop_time_stamp_counter_ && is_running_in_vm_) {
    int cpu_info_hv[4] = {};
    __cpuid(cpu_info_hv, 0x40000000);
    if (cpu_info_hv[1] == 0x7263694D &&  // Micr
        cpu_info_hv[2] == 0x666F736F &&  // osof
        cpu_info_hv[3] == 0x76482074) {  // t Hv
      // If CPUID says we have a variant TSC and a hypervisor has identified
      // itself and the hypervisor says it is Microsoft Hyper-V, then treat
      // TSC as invariant.
      //
      // Microsoft Hyper-V hypervisor reports variant TSC as there are some
      // scenarios (eg. VM live migration) where the TSC is variant, but for
      // our purposes we can treat it as invariant.
      has_non_stop_time_stamp_counter_ = true;
    }
  }
#elif defined(ARCH_CPU_ARM_FAMILY)
#if (defined(OS_ANDROID) || defined(OS_LINUX))
  cpu_brand_ = *CpuInfoBrand();
#elif defined(OS_WIN)
  // Windows makes high-resolution thread timing information available in
  // user-space.
  has_non_stop_time_stamp_counter_ = true;
#endif
#endif
}

CPU::IntelMicroArchitecture CPU::GetIntelMicroArchitecture() const {
  if (has_avx2()) return AVX2;
  if (has_avx()) return AVX;
  if (has_sse42()) return SSE42;
  if (has_sse41()) return SSE41;
  if (has_ssse3()) return SSSE3;
  if (has_sse3()) return SSE3;
  if (has_sse2()) return SSE2;
  if (has_sse()) return SSE;
  return PENTIUM;
}

}  // namespace base
