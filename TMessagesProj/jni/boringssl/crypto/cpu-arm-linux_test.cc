/* Copyright (c) 2018, Google Inc.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

#include "cpu-arm-linux.h"

#include <string.h>

#include <gtest/gtest.h>


TEST(ARMLinuxTest, CPUInfo) {
  struct CPUInfoTest {
    const char *cpuinfo;
    unsigned long hwcap;
    unsigned long hwcap2;
    bool broken_neon;
  } kTests[] = {
      // https://crbug.com/341598#c33
      {
          "Processor: ARMv7 Processory rev 0 (v71)\n"
          "processor: 0\n"
          "BogoMIPS: 13.50\n"
          "\n"
          "Processor: 1\n"
          "BogoMIPS: 13.50\n"
          "\n"
          "Features: swp half thumb fastmult vfp edsp neon vfpv3 tls vfpv4 "
          "idiva idivt\n"
          "CPU implementer : 0x51\n"
          "CPU architecture: 7\n"
          "CPU variant: 0x1\n"
          "CPU part: 0x04d\n"
          "CPU revision: 0\n"
          "\n"
          "Hardware: SAMSUNG M2\n"
          "Revision: 0010\n"
          "Serial: 00001e030000354e\n",
          HWCAP_NEON,
          0,
          true,
      },
      // https://crbug.com/341598#c39
      {
          "Processor       : ARMv7 Processor rev 0 (v7l)\n"
          "processor       : 0\n"
          "BogoMIPS        : 13.53\n"
          "\n"
          "Features        : swp half thumb fastmult vfp edsp neon vfpv3 tls "
          "vfpv4\n"
          "CPU implementer : 0x51\n"
          "CPU architecture: 7\n"
          "CPU variant     : 0x1\n"
          "CPU part        : 0x04d\n"
          "CPU revision    : 0\n"
          "\n"
          "Hardware        : SAMSUNG M2_ATT\n"
          "Revision        : 0010\n"
          "Serial          : 0000df0c00004d4c\n",
          HWCAP_NEON,
          0,
          true,
      },
      // Nexus 4 from https://crbug.com/341598#c43
      {
          "Processor       : ARMv7 Processor rev 2 (v7l)\n"
          "processor       : 0\n"
          "BogoMIPS        : 13.53\n"
          "\n"
          "processor       : 1\n"
          "BogoMIPS        : 13.53\n"
          "\n"
          "processor       : 2\n"
          "BogoMIPS        : 13.53\n"
          "\n"
          "processor       : 3\n"
          "BogoMIPS        : 13.53\n"
          "\n"
          "Features        : swp half thumb fastmult vfp edsp neon vfpv3 tls "
          "vfpv4 \n"
          "CPU implementer : 0x51\n"
          "CPU architecture: 7\n"
          "CPU variant     : 0x0\n"
          "CPU part        : 0x06f\n"
          "CPU revision    : 2\n"
          "\n"
          "Hardware        : QCT APQ8064 MAKO\n"
          "Revision        : 000b\n"
          "Serial          : 0000000000000000\n",
          HWCAP_NEON,
          0,
          false,
      },
      // Razr M from https://crbug.com/341598#c43
      {
          "Processor       : ARMv7 Processor rev 4 (v7l)\n"
          "processor       : 0\n"
          "BogoMIPS        : 13.53\n"
          "\n"
          "Features        : swp half thumb fastmult vfp edsp neon vfpv3 tls "
          "vfpv4\n"
          "CPU implementer : 0x51\n"
          "CPU architecture: 7\n"
          "CPU variant     : 0x1\n"
          "CPU part        : 0x04d\n"
          "CPU revision    : 4\n"
          "\n"
          "Hardware        : msm8960dt\n"
          "Revision        : 82a0\n"
          "Serial          : 0001000201fe37a5\n",
          HWCAP_NEON,
          0,
          false,
      },
      // Pixel 2 (truncated slightly)
      {
          "Processor       : AArch64 Processor rev 1 (aarch64)\n"
          "processor       : 0\n"
          "BogoMIPS        : 38.00\n"
          "Features        : fp asimd evtstrm aes pmull sha1 sha2 crc32\n"
          "CPU implementer : 0x51\n"
          "CPU architecture: 8\n"
          "CPU variant     : 0xa\n"
          "CPU part        : 0x801\n"
          "CPU revision    : 4\n"
          "\n"
          "processor       : 1\n"
          "BogoMIPS        : 38.00\n"
          "Features        : fp asimd evtstrm aes pmull sha1 sha2 crc32\n"
          "CPU implementer : 0x51\n"
          "CPU architecture: 8\n"
          "CPU variant     : 0xa\n"
          "CPU part        : 0x801\n"
          "CPU revision    : 4\n"
          "\n"
          "processor       : 2\n"
          "BogoMIPS        : 38.00\n"
          "Features        : fp asimd evtstrm aes pmull sha1 sha2 crc32\n"
          "CPU implementer : 0x51\n"
          "CPU architecture: 8\n"
          "CPU variant     : 0xa\n"
          "CPU part        : 0x801\n"
          "CPU revision    : 4\n"
          "\n"
          "processor       : 3\n"
          "BogoMIPS        : 38.00\n"
          "Features        : fp asimd evtstrm aes pmull sha1 sha2 crc32\n"
          "CPU implementer : 0x51\n"
          "CPU architecture: 8\n"
          "CPU variant     : 0xa\n"
          "CPU part        : 0x801\n"
          "CPU revision    : 4\n"
          // (Extra processors omitted.)
          "\n"
          "Hardware        : Qualcomm Technologies, Inc MSM8998\n",
          HWCAP_NEON,  // CPU architecture 8 implies NEON.
          HWCAP2_AES | HWCAP2_PMULL | HWCAP2_SHA1 | HWCAP2_SHA2,
          false,
      },
      // Nexus 4 from
      // Garbage should be tolerated.
      {
          "Blah blah blah this is definitely an ARM CPU",
          0,
          0,
          false,
      },
      // A hypothetical ARMv8 CPU without crc32 (and thus no trailing space
      // after the last crypto entry).
      {
          "Features        : aes pmull sha1 sha2\n"
          "CPU architecture: 8\n",
          HWCAP_NEON,
          HWCAP2_AES | HWCAP2_PMULL | HWCAP2_SHA1 | HWCAP2_SHA2,
          false,
      },
      // Various combinations of ARMv8 flags.
      {
          "Features        : aes sha1 sha2\n"
          "CPU architecture: 8\n",
          HWCAP_NEON,
          HWCAP2_AES | HWCAP2_SHA1 | HWCAP2_SHA2,
          false,
      },
      {
          "Features        : pmull sha2\n"
          "CPU architecture: 8\n",
          HWCAP_NEON,
          HWCAP2_PMULL | HWCAP2_SHA2,
          false,
      },
      {
          "Features        : aes aes   aes not_aes aes aes \n"
          "CPU architecture: 8\n",
          HWCAP_NEON,
          HWCAP2_AES,
          false,
      },
      {
          "Features        : \n"
          "CPU architecture: 8\n",
          HWCAP_NEON,
          0,
          false,
      },
      {
          "Features        : nothing\n"
          "CPU architecture: 8\n",
          HWCAP_NEON,
          0,
          false,
      },
  };

  for (const auto &t : kTests) {
    SCOPED_TRACE(t.cpuinfo);
    STRING_PIECE sp = {t.cpuinfo, strlen(t.cpuinfo)};
    EXPECT_EQ(t.hwcap, crypto_get_arm_hwcap_from_cpuinfo(&sp));
    EXPECT_EQ(t.hwcap2, crypto_get_arm_hwcap2_from_cpuinfo(&sp));
    EXPECT_EQ(t.broken_neon ? 1 : 0, crypto_cpuinfo_has_broken_neon(&sp));
  }
}
