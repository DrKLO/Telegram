// Copyright 2018 The BoringSSL Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <gtest/gtest.h>
#include <gtest/gtest-spi.h>

#include <openssl/rand.h>

#include "test/abi_test.h"


static bool test_function_ok;
static int TestFunction(int a1, int a2, int a3, int a4, int a5, int a6, int a7,
                        int a8) {
  test_function_ok = a1 == 1 || a2 == 2 || a3 == 3 || a4 == 4 || a5 == 5 ||
                     a6 == 6 || a7 == 7 || a8 == 8;
  return 42;
}

TEST(ABITest, SanityCheck) {
  EXPECT_NE(0, CHECK_ABI_NO_UNWIND(strcmp, "hello", "world"));

  test_function_ok = false;
  EXPECT_EQ(42, CHECK_ABI_SEH(TestFunction, 1, 2, 3, 4, 5, 6, 7, 8));
  EXPECT_TRUE(test_function_ok);

#if defined(SUPPORTS_ABI_TEST)
  abi_test::internal::CallerState state;
  RAND_bytes(reinterpret_cast<uint8_t *>(&state), sizeof(state));
  crypto_word_t argv[] = {
      1, 2, 3, 4, 5, 6, 7, 8,
  };
  CHECK_ABI_SEH(abi_test_trampoline,
                reinterpret_cast<crypto_word_t>(TestFunction), &state, argv, 8,
                0 /* no breakpoint */);

#if defined(OPENSSL_X86_64)
  if (abi_test::UnwindTestsEnabled()) {
    EXPECT_NONFATAL_FAILURE(CHECK_ABI_SEH(abi_test_bad_unwind_wrong_register),
                            "was not recovered");
    EXPECT_NONFATAL_FAILURE(CHECK_ABI_SEH(abi_test_bad_unwind_temporary),
                            "was not recovered");

    CHECK_ABI_NO_UNWIND(abi_test_bad_unwind_wrong_register);
    CHECK_ABI_NO_UNWIND(abi_test_bad_unwind_temporary);

#if defined(OPENSSL_WINDOWS)
    // The invalid epilog makes Windows believe the epilog starts later than it
    // actually does. As a result, immediately after the popq, it does not
    // realize the stack has been unwound and repeats the popq. This will result
    // in reading the wrong return address and fail to unwind. The exact failure
    // may vary depending on what was on the stack before.
    EXPECT_NONFATAL_FAILURE(CHECK_ABI_SEH(abi_test_bad_unwind_epilog), "");
    CHECK_ABI_NO_UNWIND(abi_test_bad_unwind_epilog);
#endif  // OPENSSL_WINDOWS
  }
#endif  // OPENSSL_X86_64
#endif  // SUPPORTS_ABI_TEST
}

#if defined(OPENSSL_X86_64) && defined(SUPPORTS_ABI_TEST)
extern "C" {
void abi_test_clobber_rax(void);
void abi_test_clobber_rbx(void);
void abi_test_clobber_rcx(void);
void abi_test_clobber_rdx(void);
void abi_test_clobber_rsi(void);
void abi_test_clobber_rdi(void);
void abi_test_clobber_rbp(void);
void abi_test_clobber_r8(void);
void abi_test_clobber_r9(void);
void abi_test_clobber_r10(void);
void abi_test_clobber_r11(void);
void abi_test_clobber_r12(void);
void abi_test_clobber_r13(void);
void abi_test_clobber_r14(void);
void abi_test_clobber_r15(void);
void abi_test_clobber_xmm0(void);
void abi_test_clobber_xmm1(void);
void abi_test_clobber_xmm2(void);
void abi_test_clobber_xmm3(void);
void abi_test_clobber_xmm4(void);
void abi_test_clobber_xmm5(void);
void abi_test_clobber_xmm6(void);
void abi_test_clobber_xmm7(void);
void abi_test_clobber_xmm8(void);
void abi_test_clobber_xmm9(void);
void abi_test_clobber_xmm10(void);
void abi_test_clobber_xmm11(void);
void abi_test_clobber_xmm12(void);
void abi_test_clobber_xmm13(void);
void abi_test_clobber_xmm14(void);
void abi_test_clobber_xmm15(void);
}  // extern "C"

TEST(ABITest, X86_64) {
  // abi_test_trampoline hides unsaved registers from the caller, so we can
  // safely call the abi_test_clobber_* functions below.
  abi_test::internal::CallerState state;
  RAND_bytes(reinterpret_cast<uint8_t *>(&state), sizeof(state));
  CHECK_ABI_NO_UNWIND(abi_test_trampoline,
                      reinterpret_cast<crypto_word_t>(abi_test_clobber_rbx),
                      &state, nullptr, 0, 0 /* no breakpoint */);

  CHECK_ABI_NO_UNWIND(abi_test_clobber_rax);
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_rbx),
                          "rbx was not restored after return");
  CHECK_ABI_NO_UNWIND(abi_test_clobber_rcx);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_rdx);
#if defined(OPENSSL_WINDOWS)
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_rdi),
                          "rdi was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_rsi),
                          "rsi was not restored after return");
#else
  CHECK_ABI_NO_UNWIND(abi_test_clobber_rdi);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_rsi);
#endif
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_rbp),
                          "rbp was not restored after return");
  CHECK_ABI_NO_UNWIND(abi_test_clobber_r8);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_r9);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_r10);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_r11);
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_r12),
                          "r12 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_r13),
                          "r13 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_r14),
                          "r14 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_r15),
                          "r15 was not restored after return");

  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm0);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm1);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm2);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm3);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm4);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm5);
#if defined(OPENSSL_WINDOWS)
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm6),
                          "xmm6 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm7),
                          "xmm7 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm8),
                          "xmm8 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm9),
                          "xmm9 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm10),
                          "xmm10 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm11),
                          "xmm11 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm12),
                          "xmm12 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm13),
                          "xmm13 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm14),
                          "xmm14 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm15),
                          "xmm15 was not restored after return");
#else
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm6);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm7);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm8);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm9);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm10);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm11);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm12);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm13);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm14);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm15);
#endif

  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_set_direction_flag),
                          "Direction flag set after return");
  EXPECT_EQ(0, abi_test_get_and_clear_direction_flag())
      << "CHECK_ABI did not insulate the caller from direction flag errors";
}
#endif   // OPENSSL_X86_64 && SUPPORTS_ABI_TEST

#if defined(OPENSSL_X86) && defined(SUPPORTS_ABI_TEST)
extern "C" {
void abi_test_clobber_eax(void);
void abi_test_clobber_ebx(void);
void abi_test_clobber_ecx(void);
void abi_test_clobber_edx(void);
void abi_test_clobber_esi(void);
void abi_test_clobber_edi(void);
void abi_test_clobber_ebp(void);
void abi_test_clobber_xmm0(void);
void abi_test_clobber_xmm1(void);
void abi_test_clobber_xmm2(void);
void abi_test_clobber_xmm3(void);
void abi_test_clobber_xmm4(void);
void abi_test_clobber_xmm5(void);
void abi_test_clobber_xmm6(void);
void abi_test_clobber_xmm7(void);
}  // extern "C"

TEST(ABITest, X86) {
  // abi_test_trampoline hides unsaved registers from the caller, so we can
  // safely call the abi_test_clobber_* functions below.
  abi_test::internal::CallerState state;
  RAND_bytes(reinterpret_cast<uint8_t *>(&state), sizeof(state));
  CHECK_ABI_NO_UNWIND(abi_test_trampoline,
                      reinterpret_cast<crypto_word_t>(abi_test_clobber_ebx),
                      &state, nullptr, 0, 0 /* no breakpoint */);

  CHECK_ABI_NO_UNWIND(abi_test_clobber_eax);
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_ebx),
                          "ebx was not restored after return");
  CHECK_ABI_NO_UNWIND(abi_test_clobber_ecx);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_edx);
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_edi),
                          "edi was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_esi),
                          "esi was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_ebp),
                          "ebp was not restored after return");

  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm0);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm1);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm2);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm3);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm4);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm5);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm6);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_xmm7);

  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_set_direction_flag),
                          "Direction flag set after return");
  EXPECT_EQ(0, abi_test_get_and_clear_direction_flag())
      << "CHECK_ABI did not insulate the caller from direction flag errors";
}
#endif   // OPENSSL_X86 && SUPPORTS_ABI_TEST

#if defined(OPENSSL_ARM) && defined(SUPPORTS_ABI_TEST)
extern "C" {
void abi_test_clobber_r0(void);
void abi_test_clobber_r1(void);
void abi_test_clobber_r2(void);
void abi_test_clobber_r3(void);
void abi_test_clobber_r4(void);
void abi_test_clobber_r5(void);
void abi_test_clobber_r6(void);
void abi_test_clobber_r7(void);
void abi_test_clobber_r8(void);
void abi_test_clobber_r9(void);
void abi_test_clobber_r10(void);
void abi_test_clobber_r11(void);
void abi_test_clobber_r12(void);
// r13, r14, and r15, are sp, lr, and pc, respectively.

void abi_test_clobber_d0(void);
void abi_test_clobber_d1(void);
void abi_test_clobber_d2(void);
void abi_test_clobber_d3(void);
void abi_test_clobber_d4(void);
void abi_test_clobber_d5(void);
void abi_test_clobber_d6(void);
void abi_test_clobber_d7(void);
void abi_test_clobber_d8(void);
void abi_test_clobber_d9(void);
void abi_test_clobber_d10(void);
void abi_test_clobber_d11(void);
void abi_test_clobber_d12(void);
void abi_test_clobber_d13(void);
void abi_test_clobber_d14(void);
void abi_test_clobber_d15(void);
}  // extern "C"

TEST(ABITest, ARM) {
  // abi_test_trampoline hides unsaved registers from the caller, so we can
  // safely call the abi_test_clobber_* functions below.
  abi_test::internal::CallerState state;
  RAND_bytes(reinterpret_cast<uint8_t *>(&state), sizeof(state));
  CHECK_ABI_NO_UNWIND(abi_test_trampoline,
                      reinterpret_cast<crypto_word_t>(abi_test_clobber_r4),
                      &state, nullptr, 0, 0 /* no breakpoint */);

  CHECK_ABI_NO_UNWIND(abi_test_clobber_r0);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_r1);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_r2);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_r3);
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_r4),
                          "r4 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_r5),
                          "r5 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_r6),
                          "r6 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_r7),
                          "r7 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_r8),
                          "r8 was not restored after return");
#if defined(OPENSSL_APPLE)
  CHECK_ABI_NO_UNWIND(abi_test_clobber_r9);
#else
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_r9),
                          "r9 was not restored after return");
#endif
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_r10),
                          "r10 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_r11),
                          "r11 was not restored after return");
  CHECK_ABI_NO_UNWIND(abi_test_clobber_r12);

  CHECK_ABI_NO_UNWIND(abi_test_clobber_d0);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d1);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d2);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d3);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d4);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d5);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d6);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d7);
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d8),
                          "d8 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d9),
                          "d9 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d10),
                          "d10 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d11),
                          "d11 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d12),
                          "d12 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d13),
                          "d13 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d14),
                          "d14 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d15),
                          "d15 was not restored after return");
}
#endif   // OPENSSL_ARM && SUPPORTS_ABI_TEST

#if defined(OPENSSL_AARCH64) && defined(SUPPORTS_ABI_TEST)
extern "C" {
void abi_test_clobber_x0(void);
void abi_test_clobber_x1(void);
void abi_test_clobber_x2(void);
void abi_test_clobber_x3(void);
void abi_test_clobber_x4(void);
void abi_test_clobber_x5(void);
void abi_test_clobber_x6(void);
void abi_test_clobber_x7(void);
void abi_test_clobber_x8(void);
void abi_test_clobber_x9(void);
void abi_test_clobber_x10(void);
void abi_test_clobber_x11(void);
void abi_test_clobber_x12(void);
void abi_test_clobber_x13(void);
void abi_test_clobber_x14(void);
void abi_test_clobber_x15(void);
void abi_test_clobber_x16(void);
void abi_test_clobber_x17(void);
// x18 is the platform register and off limits.
void abi_test_clobber_x19(void);
void abi_test_clobber_x20(void);
void abi_test_clobber_x21(void);
void abi_test_clobber_x22(void);
void abi_test_clobber_x23(void);
void abi_test_clobber_x24(void);
void abi_test_clobber_x25(void);
void abi_test_clobber_x26(void);
void abi_test_clobber_x27(void);
void abi_test_clobber_x28(void);
void abi_test_clobber_x29(void);

void abi_test_clobber_d0(void);
void abi_test_clobber_d1(void);
void abi_test_clobber_d2(void);
void abi_test_clobber_d3(void);
void abi_test_clobber_d4(void);
void abi_test_clobber_d5(void);
void abi_test_clobber_d6(void);
void abi_test_clobber_d7(void);
void abi_test_clobber_d8(void);
void abi_test_clobber_d9(void);
void abi_test_clobber_d10(void);
void abi_test_clobber_d11(void);
void abi_test_clobber_d12(void);
void abi_test_clobber_d13(void);
void abi_test_clobber_d14(void);
void abi_test_clobber_d15(void);
void abi_test_clobber_d16(void);
void abi_test_clobber_d17(void);
void abi_test_clobber_d18(void);
void abi_test_clobber_d19(void);
void abi_test_clobber_d20(void);
void abi_test_clobber_d21(void);
void abi_test_clobber_d22(void);
void abi_test_clobber_d23(void);
void abi_test_clobber_d24(void);
void abi_test_clobber_d25(void);
void abi_test_clobber_d26(void);
void abi_test_clobber_d27(void);
void abi_test_clobber_d28(void);
void abi_test_clobber_d29(void);
void abi_test_clobber_d30(void);
void abi_test_clobber_d31(void);

void abi_test_clobber_v8_upper(void);
void abi_test_clobber_v9_upper(void);
void abi_test_clobber_v10_upper(void);
void abi_test_clobber_v11_upper(void);
void abi_test_clobber_v12_upper(void);
void abi_test_clobber_v13_upper(void);
void abi_test_clobber_v14_upper(void);
void abi_test_clobber_v15_upper(void);
}  // extern "C"

TEST(ABITest, AArch64) {
  // abi_test_trampoline hides unsaved registers from the caller, so we can
  // safely call the abi_test_clobber_* functions below.
  abi_test::internal::CallerState state;
  RAND_bytes(reinterpret_cast<uint8_t *>(&state), sizeof(state));
  CHECK_ABI_NO_UNWIND(abi_test_trampoline,
                      reinterpret_cast<crypto_word_t>(abi_test_clobber_x19),
                      &state, nullptr, 0, 0 /* no breakpoint */);

  CHECK_ABI_NO_UNWIND(abi_test_clobber_x0);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x1);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x2);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x3);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x4);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x5);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x6);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x7);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x8);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x9);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x10);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x11);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x12);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x13);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x14);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x15);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x16);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_x17);

  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_x19),
                          "x19 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_x20),
                          "x20 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_x21),
                          "x21 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_x22),
                          "x22 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_x23),
                          "x23 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_x24),
                          "x24 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_x25),
                          "x25 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_x26),
                          "x26 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_x27),
                          "x27 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_x28),
                          "x28 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_x29),
                          "x29 was not restored after return");

  CHECK_ABI_NO_UNWIND(abi_test_clobber_d0);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d1);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d2);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d3);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d4);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d5);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d6);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d7);
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d8),
                          "d8 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d9),
                          "d9 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d10),
                          "d10 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d11),
                          "d11 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d12),
                          "d12 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d13),
                          "d13 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d14),
                          "d14 was not restored after return");
  EXPECT_NONFATAL_FAILURE(CHECK_ABI_NO_UNWIND(abi_test_clobber_d15),
                          "d15 was not restored after return");
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d16);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d18);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d19);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d20);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d21);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d22);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d23);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d24);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d25);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d26);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d27);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d28);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d29);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d30);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_d31);

  // The lower halves of v8-v15 (accessed as d8-d15) must be preserved, but not
  // the upper halves.
  CHECK_ABI_NO_UNWIND(abi_test_clobber_v8_upper);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_v9_upper);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_v10_upper);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_v11_upper);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_v12_upper);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_v13_upper);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_v14_upper);
  CHECK_ABI_NO_UNWIND(abi_test_clobber_v15_upper);
}
#endif   // OPENSSL_AARCH64 && SUPPORTS_ABI_TEST
