// Copyright 2019 The Abseil Authors.
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
#include "absl/status/status_payload_printer.h"

#include <atomic>

#include "absl/base/attributes.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace status_internal {

namespace {
// Tried constant initialized global variable but it doesn't work with Lexan
// (MSVC's `std::atomic` has trouble constant initializing).
std::atomic<StatusPayloadPrinter>& GetStatusPayloadPrinterStorage() {
  ABSL_CONST_INIT static std::atomic<StatusPayloadPrinter> instance{nullptr};
  return instance;
}
}  // namespace

void SetStatusPayloadPrinter(StatusPayloadPrinter printer) {
  GetStatusPayloadPrinterStorage().store(printer, std::memory_order_relaxed);
}

StatusPayloadPrinter GetStatusPayloadPrinter() {
  return GetStatusPayloadPrinterStorage().load(std::memory_order_relaxed);
}

}  // namespace status_internal
ABSL_NAMESPACE_END
}  // namespace absl
