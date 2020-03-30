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

#ifndef THIRD_PARTY_RNNOISE_SRC_KISS_FFT_H_
#define THIRD_PARTY_RNNOISE_SRC_KISS_FFT_H_

#include <array>
#include <complex>
#include <vector>

namespace rnnoise {

class KissFft {
 public:
  // Example: an FFT of length 128 has 4 factors as far as kissfft is concerned
  // (namely, 4*4*4*2).
  static const size_t kMaxFactors = 8;

  class KissFftState {
   public:
    KissFftState(int num_fft_points);
    KissFftState(const KissFftState&) = delete;
    KissFftState& operator=(const KissFftState&) = delete;
    ~KissFftState();

    const int nfft;
    const float scale;
    std::array<int16_t, 2 * kMaxFactors> factors;
    std::vector<int16_t> bitrev;
    std::vector<std::complex<float>> twiddles;
  };

  explicit KissFft(const int nfft);
  KissFft(const KissFft&) = delete;
  KissFft& operator=(const KissFft&) = delete;
  ~KissFft();
  void ForwardFft(const size_t in_size,
                  const std::complex<float>* in,
                  const size_t out_size,
                  std::complex<float>* out);
  void ReverseFft(const size_t in_size,
                  const std::complex<float>* in,
                  const size_t out_size,
                  std::complex<float>* out);

 private:
  KissFftState state_;
};

}  // namespace rnnoise

#endif  // THIRD_PARTY_RNNOISE_SRC_KISS_FFT_H_
