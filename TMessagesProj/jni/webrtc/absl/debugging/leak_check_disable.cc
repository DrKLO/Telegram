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

// Disable LeakSanitizer when this file is linked in.
// This function overrides __lsan_is_turned_off from sanitizer/lsan_interface.h
extern "C" int __lsan_is_turned_off();
extern "C" int __lsan_is_turned_off() {
  return 1;
}
