/*Copyright (c) 2003-2004, Mark Borgerding
  Lots of modifications by Jean-Marc Valin
  Copyright (c) 2005-2007, Xiph.Org Foundation
  Copyright (c) 2008,      Xiph.Org Foundation, CSIRO
  Copyright (c) 2018,      The WebRTC project authors

  All rights reserved.

  Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
       this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice,
       this list of conditions and the following disclaimer in the
       documentation and/or other materials provided with the distribution.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
  POSSIBILITY OF SUCH DAMAGE.*/

#include "third_party/rnnoise/src/kiss_fft.h"

#include <cassert>
#include <cmath>
#include <utility>

namespace rnnoise {
namespace {

void kf_bfly2(std::complex<float>* Fout, int m, int N) {
  if (m == 1) {
    for (int i = 0; i < N; i++) {
      const std::complex<float> t = Fout[1];
      Fout[1] = Fout[0] - t;
      Fout[0] += t;
      Fout += 2;
    }
  } else {
    constexpr float tw = 0.7071067812f;
    // We know that m==4 here because the radix-2 is just after a radix-4.
    assert(m == 4);
    for (int i = 0; i < N; i++) {
      std::complex<float>* Fout2 = Fout + 4;
      std::complex<float> t = Fout2[0];

      *Fout2 = Fout[0] - t;
      Fout[0] += t;

      t.real((Fout2[1].real() + Fout2[1].imag()) * tw);
      t.imag((Fout2[1].imag() - Fout2[1].real()) * tw);
      Fout2[1] = Fout[1] - t;
      Fout[1] += t;

      t.real(Fout2[2].imag());
      t.imag(-Fout2[2].real());
      Fout2[2] = Fout[2] - t;
      Fout[2] += t;

      t.real((Fout2[3].imag() - Fout2[3].real()) * tw);
      t.imag(-(Fout2[3].imag() + Fout2[3].real()) * tw);
      Fout2[3] = Fout[3] - t;
      Fout[3] += t;
      Fout += 8;
    }
  }
}

void kf_bfly4(std::complex<float>* Fout,
              const size_t fstride,
              const KissFft::KissFftState* st,
              int m,
              int N,
              int mm) {
  assert(Fout);
  assert(st);
  if (m == 1) {
    // Degenerate case where all the twiddles are 1.
    for (int i = 0; i < N; i++) {
      std::complex<float> scratch0 = Fout[0] - Fout[2];
      Fout[0] += Fout[2];
      std::complex<float> scratch1 = Fout[1] + Fout[3];
      Fout[2] = Fout[0] - scratch1;
      Fout[0] += scratch1;
      scratch1 = Fout[1] - Fout[3];

      Fout[1].real(scratch0.real() + scratch1.imag());
      Fout[1].imag(scratch0.imag() - scratch1.real());
      Fout[3].real(scratch0.real() - scratch1.imag());
      Fout[3].imag(scratch0.imag() + scratch1.real());
      Fout += 4;
    }
  } else {
    std::complex<float> scratch[6];
    const int m2 = 2 * m;
    const int m3 = 3 * m;
    std::complex<float>* Fout_beg = Fout;
    for (int i = 0; i < N; i++) {
      Fout = Fout_beg + i * mm;
      const std::complex<float>* tw1;
      const std::complex<float>* tw2;
      const std::complex<float>* tw3;
      tw3 = tw2 = tw1 = &st->twiddles[0];
      assert(m % 4 == 0);  // |m| is guaranteed to be a multiple of 4.
      for (int j = 0; j < m; j++) {
        scratch[0] = Fout[m] * *tw1;
        scratch[1] = Fout[m2] * *tw2;
        scratch[2] = Fout[m3] * *tw3;

        scratch[5] = Fout[0] - scratch[1];
        Fout[0] += scratch[1];
        scratch[3] = scratch[0] + scratch[2];
        scratch[4] = scratch[0] - scratch[2];
        Fout[m2] = Fout[0] - scratch[3];

        tw1 += fstride;
        tw2 += fstride * 2;
        tw3 += fstride * 3;
        Fout[0] += scratch[3];

        Fout[m].real(scratch[5].real() + scratch[4].imag());
        Fout[m].imag(scratch[5].imag() - scratch[4].real());
        Fout[m3].real(scratch[5].real() - scratch[4].imag());
        Fout[m3].imag(scratch[5].imag() + scratch[4].real());
        ++Fout;
      }
    }
  }
}

void kf_bfly3(std::complex<float>* Fout,
              const size_t fstride,
              const KissFft::KissFftState* st,
              int m,
              int N,
              int mm) {
  assert(Fout);
  assert(st);
  const size_t m2 = 2 * m;
  const std::complex<float>*tw1, *tw2;
  std::complex<float> scratch[5];
  std::complex<float> epi3;

  std::complex<float>* Fout_beg = Fout;
  epi3 = st->twiddles[fstride * m];
  for (int i = 0; i < N; i++) {
    Fout = Fout_beg + i * mm;
    tw1 = tw2 = &st->twiddles[0];
    size_t k = m;
    do {
      scratch[1] = Fout[m] * *tw1;
      scratch[2] = Fout[m2] * *tw2;

      scratch[3] = scratch[1] + scratch[2];
      scratch[0] = scratch[1] - scratch[2];
      tw1 += fstride;
      tw2 += fstride * 2;

      Fout[m] = Fout[0] - 0.5f * scratch[3];

      scratch[0] *= epi3.imag();

      Fout[0] += scratch[3];

      Fout[m2].real(Fout[m].real() + scratch[0].imag());
      Fout[m2].imag(Fout[m].imag() - scratch[0].real());

      Fout[m].real(Fout[m].real() - scratch[0].imag());
      Fout[m].imag(Fout[m].imag() + scratch[0].real());

      ++Fout;
    } while (--k);
  }
}

void kf_bfly5(std::complex<float>* Fout,
              const size_t fstride,
              const KissFft::KissFftState* st,
              int m,
              int N,
              int mm) {
  assert(Fout);
  assert(st);
  std::complex<float> scratch[13];
  const std::complex<float>* tw;
  std::complex<float> ya, yb;
  std::complex<float>* const Fout_beg = Fout;

  ya = st->twiddles[fstride * m];
  yb = st->twiddles[fstride * 2 * m];
  tw = &st->twiddles[0];

  for (int i = 0; i < N; i++) {
    Fout = Fout_beg + i * mm;
    std::complex<float>* Fout0 = Fout;
    std::complex<float>* Fout1 = Fout0 + m;
    std::complex<float>* Fout2 = Fout0 + 2 * m;
    std::complex<float>* Fout3 = Fout0 + 3 * m;
    std::complex<float>* Fout4 = Fout0 + 4 * m;

    // For non-custom modes, m is guaranteed to be a multiple of 4.
    for (int u = 0; u < m; ++u) {
      scratch[0] = *Fout0;

      scratch[1] = *Fout1 * tw[u * fstride];
      scratch[2] = *Fout2 * tw[2 * u * fstride];
      scratch[3] = *Fout3 * tw[3 * u * fstride];
      scratch[4] = *Fout4 * tw[4 * u * fstride];

      scratch[7] = scratch[1] + scratch[4];
      scratch[10] = scratch[1] - scratch[4];
      scratch[8] = scratch[2] + scratch[3];
      scratch[9] = scratch[2] - scratch[3];

      Fout0->real(Fout0->real() + scratch[7].real() + scratch[8].real());
      Fout0->imag(Fout0->imag() + scratch[7].imag() + scratch[8].imag());

      scratch[5].real(scratch[0].real() + scratch[7].real() * ya.real() +
                      scratch[8].real() * yb.real());
      scratch[5].imag(scratch[0].imag() + scratch[7].imag() * ya.real() +
                      scratch[8].imag() * yb.real());

      scratch[6].real(scratch[10].imag() * ya.imag() +
                      scratch[9].imag() * yb.imag());
      scratch[6].imag(
          -(scratch[10].real() * ya.imag() + scratch[9].real() * yb.imag()));

      *Fout1 = scratch[5] - scratch[6];
      *Fout4 = scratch[5] + scratch[6];

      scratch[11].real(scratch[0].real() + scratch[7].real() * yb.real() +
                       scratch[8].real() * ya.real());
      scratch[11].imag(scratch[0].imag() + scratch[7].imag() * yb.real() +
                       scratch[8].imag() * ya.real());
      scratch[12].real(scratch[9].imag() * ya.imag() -
                       scratch[10].imag() * yb.imag());
      scratch[12].imag(scratch[10].real() * yb.imag() -
                       scratch[9].real() * ya.imag());

      *Fout2 = scratch[11] + scratch[12];
      *Fout3 = scratch[11] - scratch[12];

      ++Fout0;
      ++Fout1;
      ++Fout2;
      ++Fout3;
      ++Fout4;
    }
  }
}

void compute_bitrev_table(int base_index,
                          const size_t stride,
                          const int16_t* factors,
                          const KissFft::KissFftState* st,
                          const int16_t* bitrev_table_last,
                          int16_t* bitrev_table) {
  const int p = *factors++;  // The radix.
  const int m = *factors++;  // Stage's fft length/p.
  if (m == 1) {
    for (int j = 0; j < p; j++) {
      assert(bitrev_table <= bitrev_table_last);
      *bitrev_table = base_index + j;
      bitrev_table += stride;
    }
  } else {
    for (int j = 0; j < p; j++) {
      compute_bitrev_table(base_index, stride * p, factors, st,
                           bitrev_table_last, bitrev_table);
      bitrev_table += stride;
      base_index += m;
    }
  }
}

// Populates |facbuf| with p1, m1, p2, m2, ... where p[i] * m[i] = m[i-1] and
// m0 = n.
bool kf_factor(int n, int16_t* facbuf) {
  assert(facbuf);
  int p = 4;
  int stages = 0;
  int nbak = n;

  // Factor out powers of 4, powers of 2, then any remaining primes.
  do {
    while (n % p) {
      switch (p) {
        case 4:
          p = 2;
          break;
        case 2:
          p = 3;
          break;
        default:
          p += 2;
          break;
      }
      if (p > 32000 || p * p > n)
        p = n;  // No more factors, skip to end.
    }
    n /= p;
    if (p > 5)
      return false;
    facbuf[2 * stages] = p;
    if (p == 2 && stages > 1) {
      facbuf[2 * stages] = 4;
      facbuf[2] = 2;
    }
    stages++;
  } while (n > 1);
  n = nbak;
  // Reverse the order to get the radix 4 at the end, so we can use the
  // fast degenerate case. It turns out that reversing the order also
  // improves the noise behavior.
  for (int i = 0; i < stages / 2; i++)
    std::swap(facbuf[2 * i], facbuf[2 * (stages - i - 1)]);
  for (int i = 0; i < stages; i++) {
    n /= facbuf[2 * i];
    facbuf[2 * i + 1] = n;
  }
  return true;
}

void compute_twiddles(const int nfft, std::complex<float>* twiddles) {
  constexpr double pi = 3.14159265358979323846264338327;
  assert(twiddles);
  for (int i = 0; i < nfft; ++i) {
    const double phase = (-2 * pi / nfft) * i;
    twiddles[i].real(std::cos(phase));
    twiddles[i].imag(std::sin(phase));
  }
}

void fft_impl(const KissFft::KissFftState* st, std::complex<float>* fout) {
  assert(st);
  assert(fout);
  int m2, m;
  int p;
  int L;
  int fstride[KissFft::kMaxFactors];

  fstride[0] = 1;
  L = 0;
  do {
    p = st->factors[2 * L];
    m = st->factors[2 * L + 1];
    assert(static_cast<size_t>(L + 1) < KissFft::kMaxFactors);
    fstride[L + 1] = fstride[L] * p;
    L++;
  } while (m != 1);
  m = st->factors[2 * L - 1];
  for (int i = L - 1; i >= 0; i--) {
    if (i != 0)
      m2 = st->factors[2 * i - 1];
    else
      m2 = 1;
    switch (st->factors[2 * i]) {
      case 2:
        kf_bfly2(fout, m, fstride[i]);
        break;
      case 4:
        kf_bfly4(fout, fstride[i], st, m, fstride[i], m2);
        break;
      case 3:
        kf_bfly3(fout, fstride[i], st, m, fstride[i], m2);
        break;
      case 5:
        kf_bfly5(fout, fstride[i], st, m, fstride[i], m2);
        break;
      default:
        assert(0);
        break;
    }
    m = m2;
  }
}

}  // namespace

KissFft::KissFftState::KissFftState(int num_fft_points)
    : nfft(num_fft_points), scale(1.f / nfft) {
  // Factorize |nfft|.
  // TODO(alessiob): Handle kf_factor fails (invalid nfft).
  if (!kf_factor(nfft, factors.data()))
    assert(0);
  // Twiddles.
  twiddles.resize(nfft);
  compute_twiddles(nfft, twiddles.data());
  // Bit-reverse table.
  bitrev.resize(nfft);
  compute_bitrev_table(0, 1, factors.data(), this, &bitrev.back(),
                       bitrev.data());
}

KissFft::KissFftState::~KissFftState() = default;

KissFft::KissFft(const int nfft) : state_(nfft) {}

KissFft::~KissFft() = default;

void KissFft::ForwardFft(const size_t in_size,
                         const std::complex<float>* in,
                         const size_t out_size,
                         std::complex<float>* out) {
  assert(in);
  assert(out);
  assert(in != out);  // In-place FFT not supported.
  assert(state_.nfft == static_cast<int>(in_size));
  assert(state_.nfft == static_cast<int>(out_size));
  // Bit-reverse the input.
  for (int i = 0; i < state_.nfft; i++)
    out[state_.bitrev[i]] = state_.scale * in[i];
  fft_impl(&state_, out);
}

void KissFft::ReverseFft(const size_t in_size,
                         const std::complex<float>* in,
                         const size_t out_size,
                         std::complex<float>* out) {
  assert(in);
  assert(out);
  assert(in != out);  // In-place IFFT not supported.
  assert(state_.nfft == static_cast<int>(in_size));
  assert(state_.nfft == static_cast<int>(out_size));
  // Bit-reverse the input.
  for (int i = 0; i < state_.nfft; i++)
    out[state_.bitrev[i]] = in[i];
  for (int i = 0; i < state_.nfft; i++)
    out[i].imag(-out[i].imag());
  fft_impl(&state_, out);
  for (int i = 0; i < state_.nfft; i++)
    out[i].imag(-out[i].imag());
}

}  // namespace rnnoise
