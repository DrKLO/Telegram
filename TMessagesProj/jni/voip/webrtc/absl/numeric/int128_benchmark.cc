// Copyright 2017 The Abseil Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "absl/numeric/int128.h"

#include <algorithm>
#include <cstdint>
#include <random>
#include <vector>

#include "benchmark/benchmark.h"
#include "absl/base/config.h"

namespace {

constexpr size_t kSampleSize = 1000000;

std::mt19937 MakeRandomEngine() {
  std::random_device r;
  std::seed_seq seed({r(), r(), r(), r(), r(), r(), r(), r()});
  return std::mt19937(seed);
}

std::vector<std::pair<absl::uint128, absl::uint128>>
GetRandomClass128SampleUniformDivisor() {
  std::vector<std::pair<absl::uint128, absl::uint128>> values;
  std::mt19937 random = MakeRandomEngine();
  std::uniform_int_distribution<uint64_t> uniform_uint64;
  values.reserve(kSampleSize);
  for (size_t i = 0; i < kSampleSize; ++i) {
    absl::uint128 a =
        absl::MakeUint128(uniform_uint64(random), uniform_uint64(random));
    absl::uint128 b =
        absl::MakeUint128(uniform_uint64(random), uniform_uint64(random));
    values.emplace_back(std::max(a, b),
                        std::max(absl::uint128(2), std::min(a, b)));
  }
  return values;
}

void BM_DivideClass128UniformDivisor(benchmark::State& state) {
  auto values = GetRandomClass128SampleUniformDivisor();
  while (state.KeepRunningBatch(values.size())) {
    for (const auto& pair : values) {
      benchmark::DoNotOptimize(pair.first / pair.second);
    }
  }
}
BENCHMARK(BM_DivideClass128UniformDivisor);

std::vector<std::pair<absl::uint128, uint64_t>>
GetRandomClass128SampleSmallDivisor() {
  std::vector<std::pair<absl::uint128, uint64_t>> values;
  std::mt19937 random = MakeRandomEngine();
  std::uniform_int_distribution<uint64_t> uniform_uint64;
  values.reserve(kSampleSize);
  for (size_t i = 0; i < kSampleSize; ++i) {
    absl::uint128 a =
        absl::MakeUint128(uniform_uint64(random), uniform_uint64(random));
    uint64_t b = std::max(uint64_t{2}, uniform_uint64(random));
    values.emplace_back(std::max(a, absl::uint128(b)), b);
  }
  return values;
}

void BM_DivideClass128SmallDivisor(benchmark::State& state) {
  auto values = GetRandomClass128SampleSmallDivisor();
  while (state.KeepRunningBatch(values.size())) {
    for (const auto& pair : values) {
      benchmark::DoNotOptimize(pair.first / pair.second);
    }
  }
}
BENCHMARK(BM_DivideClass128SmallDivisor);

std::vector<std::pair<absl::uint128, absl::uint128>> GetRandomClass128Sample() {
  std::vector<std::pair<absl::uint128, absl::uint128>> values;
  std::mt19937 random = MakeRandomEngine();
  std::uniform_int_distribution<uint64_t> uniform_uint64;
  values.reserve(kSampleSize);
  for (size_t i = 0; i < kSampleSize; ++i) {
    values.emplace_back(
        absl::MakeUint128(uniform_uint64(random), uniform_uint64(random)),
        absl::MakeUint128(uniform_uint64(random), uniform_uint64(random)));
  }
  return values;
}

void BM_MultiplyClass128(benchmark::State& state) {
  auto values = GetRandomClass128Sample();
  while (state.KeepRunningBatch(values.size())) {
    for (const auto& pair : values) {
      benchmark::DoNotOptimize(pair.first * pair.second);
    }
  }
}
BENCHMARK(BM_MultiplyClass128);

void BM_AddClass128(benchmark::State& state) {
  auto values = GetRandomClass128Sample();
  while (state.KeepRunningBatch(values.size())) {
    for (const auto& pair : values) {
      benchmark::DoNotOptimize(pair.first + pair.second);
    }
  }
}
BENCHMARK(BM_AddClass128);

#ifdef ABSL_HAVE_INTRINSIC_INT128

// Some implementations of <random> do not support __int128 when it is
// available, so we make our own uniform_int_distribution-like type.
class UniformIntDistribution128 {
 public:
  // NOLINTNEXTLINE: mimicking std::uniform_int_distribution API
  unsigned __int128 operator()(std::mt19937& generator) {
    return (static_cast<unsigned __int128>(dist64_(generator)) << 64) |
           dist64_(generator);
  }

 private:
  std::uniform_int_distribution<uint64_t> dist64_;
};

std::vector<std::pair<unsigned __int128, unsigned __int128>>
GetRandomIntrinsic128SampleUniformDivisor() {
  std::vector<std::pair<unsigned __int128, unsigned __int128>> values;
  std::mt19937 random = MakeRandomEngine();
  UniformIntDistribution128 uniform_uint128;
  values.reserve(kSampleSize);
  for (size_t i = 0; i < kSampleSize; ++i) {
    unsigned __int128 a = uniform_uint128(random);
    unsigned __int128 b = uniform_uint128(random);
    values.emplace_back(
        std::max(a, b),
        std::max(static_cast<unsigned __int128>(2), std::min(a, b)));
  }
  return values;
}

void BM_DivideIntrinsic128UniformDivisor(benchmark::State& state) {
  auto values = GetRandomIntrinsic128SampleUniformDivisor();
  while (state.KeepRunningBatch(values.size())) {
    for (const auto& pair : values) {
      benchmark::DoNotOptimize(pair.first / pair.second);
    }
  }
}
BENCHMARK(BM_DivideIntrinsic128UniformDivisor);

std::vector<std::pair<unsigned __int128, uint64_t>>
GetRandomIntrinsic128SampleSmallDivisor() {
  std::vector<std::pair<unsigned __int128, uint64_t>> values;
  std::mt19937 random = MakeRandomEngine();
  UniformIntDistribution128 uniform_uint128;
  std::uniform_int_distribution<uint64_t> uniform_uint64;
  values.reserve(kSampleSize);
  for (size_t i = 0; i < kSampleSize; ++i) {
    unsigned __int128 a = uniform_uint128(random);
    uint64_t b = std::max(uint64_t{2}, uniform_uint64(random));
    values.emplace_back(std::max(a, static_cast<unsigned __int128>(b)), b);
  }
  return values;
}

void BM_DivideIntrinsic128SmallDivisor(benchmark::State& state) {
  auto values = GetRandomIntrinsic128SampleSmallDivisor();
  while (state.KeepRunningBatch(values.size())) {
    for (const auto& pair : values) {
      benchmark::DoNotOptimize(pair.first / pair.second);
    }
  }
}
BENCHMARK(BM_DivideIntrinsic128SmallDivisor);

std::vector<std::pair<unsigned __int128, unsigned __int128>>
      GetRandomIntrinsic128Sample() {
  std::vector<std::pair<unsigned __int128, unsigned __int128>> values;
  std::mt19937 random = MakeRandomEngine();
  UniformIntDistribution128 uniform_uint128;
  values.reserve(kSampleSize);
  for (size_t i = 0; i < kSampleSize; ++i) {
    values.emplace_back(uniform_uint128(random), uniform_uint128(random));
  }
  return values;
}

void BM_MultiplyIntrinsic128(benchmark::State& state) {
  auto values = GetRandomIntrinsic128Sample();
  while (state.KeepRunningBatch(values.size())) {
    for (const auto& pair : values) {
      benchmark::DoNotOptimize(pair.first * pair.second);
    }
  }
}
BENCHMARK(BM_MultiplyIntrinsic128);

void BM_AddIntrinsic128(benchmark::State& state) {
  auto values = GetRandomIntrinsic128Sample();
  while (state.KeepRunningBatch(values.size())) {
    for (const auto& pair : values) {
      benchmark::DoNotOptimize(pair.first + pair.second);
    }
  }
}
BENCHMARK(BM_AddIntrinsic128);

#endif  // ABSL_HAVE_INTRINSIC_INT128

}  // namespace
