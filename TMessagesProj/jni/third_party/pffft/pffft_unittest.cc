// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <algorithm>
#include <cmath>

#include "testing/gtest/include/gtest/gtest-death-test.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "third_party/pffft/src/fftpack.h"
#include "third_party/pffft/src/pffft.h"

namespace pffft {
namespace test {
namespace {

static constexpr int kFftSizes[] = {
    16,  32,      64,  96,  128,  160,  192,  256,  288,  384,   5 * 96, 512,
    576, 5 * 128, 800, 864, 1024, 2048, 2592, 4000, 4096, 12000, 36864};

double frand() {
  return rand() / (double)RAND_MAX;
}

void PffftValidate(int fft_size, bool complex_fft) {
  PFFFT_Setup* pffft_status =
      pffft_new_setup(fft_size, complex_fft ? PFFFT_COMPLEX : PFFFT_REAL);
  ASSERT_TRUE(pffft_status) << "FFT size (" << fft_size << ") not supported.";

  int num_floats = fft_size * (complex_fft ? 2 : 1);
  int num_bytes = num_floats * sizeof(float);
  float* ref = static_cast<float*>(pffft_aligned_malloc(num_bytes));
  float* in = static_cast<float*>(pffft_aligned_malloc(num_bytes));
  float* out = static_cast<float*>(pffft_aligned_malloc(num_bytes));
  float* tmp = static_cast<float*>(pffft_aligned_malloc(num_bytes));
  float* tmp2 = static_cast<float*>(pffft_aligned_malloc(num_bytes));

  for (int pass = 0; pass < 2; ++pass) {
    SCOPED_TRACE(pass);
    float ref_max = 0;
    int k;

    // Compute reference solution with FFTPACK.
    if (pass == 0) {
      float* fftpack_buff =
          static_cast<float*>(malloc(2 * num_bytes + 15 * sizeof(float)));
      for (k = 0; k < num_floats; ++k) {
        ref[k] = in[k] = frand() * 2 - 1;
        out[k] = 1e30;
      }

      if (!complex_fft) {
        rffti(fft_size, fftpack_buff);
        rfftf(fft_size, ref, fftpack_buff);
        // Use our ordering for real FFTs instead of the one of fftpack.
        {
          float refN = ref[fft_size - 1];
          for (k = fft_size - 2; k >= 1; --k)
            ref[k + 1] = ref[k];
          ref[1] = refN;
        }
      } else {
        cffti(fft_size, fftpack_buff);
        cfftf(fft_size, ref, fftpack_buff);
      }

      free(fftpack_buff);
    }

    for (k = 0; k < num_floats; ++k) {
      ref_max = std::max(ref_max, fabs(ref[k]));
    }

    // Pass 0: non canonical ordering of transform coefficients.
    if (pass == 0) {
      // Test forward transform, with different input / output.
      pffft_transform(pffft_status, in, tmp, nullptr, PFFFT_FORWARD);
      memcpy(tmp2, tmp, num_bytes);
      memcpy(tmp, in, num_bytes);
      pffft_transform(pffft_status, tmp, tmp, nullptr, PFFFT_FORWARD);
      for (k = 0; k < num_floats; ++k) {
        SCOPED_TRACE(k);
        EXPECT_EQ(tmp2[k], tmp[k]);
      }

      // Test reordering.
      pffft_zreorder(pffft_status, tmp, out, PFFFT_FORWARD);
      pffft_zreorder(pffft_status, out, tmp, PFFFT_BACKWARD);
      for (k = 0; k < num_floats; ++k) {
        SCOPED_TRACE(k);
        EXPECT_EQ(tmp2[k], tmp[k]);
      }
      pffft_zreorder(pffft_status, tmp, out, PFFFT_FORWARD);
    } else {
      // Pass 1: canonical ordering of transform coefficients.
      pffft_transform_ordered(pffft_status, in, tmp, nullptr, PFFFT_FORWARD);
      memcpy(tmp2, tmp, num_bytes);
      memcpy(tmp, in, num_bytes);
      pffft_transform_ordered(pffft_status, tmp, tmp, nullptr, PFFFT_FORWARD);
      for (k = 0; k < num_floats; ++k) {
        SCOPED_TRACE(k);
        EXPECT_EQ(tmp2[k], tmp[k]);
      }
      memcpy(out, tmp, num_bytes);
    }

    {
      for (k = 0; k < num_floats; ++k) {
        SCOPED_TRACE(k);
        EXPECT_NEAR(ref[k], out[k], 1e-3 * ref_max) << "Forward FFT mismatch";
      }

      if (pass == 0) {
        pffft_transform(pffft_status, tmp, out, nullptr, PFFFT_BACKWARD);
      } else {
        pffft_transform_ordered(pffft_status, tmp, out, nullptr,
                                PFFFT_BACKWARD);
      }
      memcpy(tmp2, out, num_bytes);
      memcpy(out, tmp, num_bytes);
      if (pass == 0) {
        pffft_transform(pffft_status, out, out, nullptr, PFFFT_BACKWARD);
      } else {
        pffft_transform_ordered(pffft_status, out, out, nullptr,
                                PFFFT_BACKWARD);
      }
      for (k = 0; k < num_floats; ++k) {
        assert(tmp2[k] == out[k]);
        out[k] *= 1.f / fft_size;
      }
      for (k = 0; k < num_floats; ++k) {
        SCOPED_TRACE(k);
        EXPECT_NEAR(in[k], out[k], 1e-3 * ref_max) << "Reverse FFT mismatch";
      }
    }

    // Quick test of the circular convolution in FFT domain.
    {
      float conv_err = 0, conv_max = 0;

      pffft_zreorder(pffft_status, ref, tmp, PFFFT_FORWARD);
      memset(out, 0, num_bytes);
      pffft_zconvolve_accumulate(pffft_status, ref, ref, out, 1.0);
      pffft_zreorder(pffft_status, out, tmp2, PFFFT_FORWARD);

      for (k = 0; k < num_floats; k += 2) {
        float ar = tmp[k], ai = tmp[k + 1];
        if (complex_fft || k > 0) {
          tmp[k] = ar * ar - ai * ai;
          tmp[k + 1] = 2 * ar * ai;
        } else {
          tmp[0] = ar * ar;
          tmp[1] = ai * ai;
        }
      }

      for (k = 0; k < num_floats; ++k) {
        float d = fabs(tmp[k] - tmp2[k]), e = fabs(tmp[k]);
        if (d > conv_err)
          conv_err = d;
        if (e > conv_max)
          conv_max = e;
      }
      EXPECT_LT(conv_err, 1e-5 * conv_max) << "zconvolve error";
    }
  }

  pffft_destroy_setup(pffft_status);
  pffft_aligned_free(ref);
  pffft_aligned_free(in);
  pffft_aligned_free(out);
  pffft_aligned_free(tmp);
  pffft_aligned_free(tmp2);
}

}  // namespace

TEST(PffftTest, ValidateReal) {
  for (int fft_size : kFftSizes) {
    SCOPED_TRACE(fft_size);
    if (fft_size == 16) {
      continue;
    }
    PffftValidate(fft_size, /*complex_fft=*/false);
  }
}

TEST(PffftTest, ValidateComplex) {
  for (int fft_size : kFftSizes) {
    SCOPED_TRACE(fft_size);
    PffftValidate(fft_size, /*complex_fft=*/true);
  }
}

}  // namespace test
}  // namespace pffft
