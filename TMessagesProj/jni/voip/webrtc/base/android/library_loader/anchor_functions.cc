// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/library_loader/anchor_functions.h"

#include "base/logging.h"
#include "build/build_config.h"

#if BUILDFLAG(SUPPORTS_CODE_ORDERING)

// These functions are here to delimit the start and end of the ordered part of
// .text. They require a suitably constructed orderfile, with these functions at
// the beginning and end.
//
// These functions are weird: this is due to ICF (Identical Code Folding).
// The linker merges functions that have the same code, which would be the case
// if these functions were empty, or simple.
// Gold's flag --icf=safe will *not* alias functions when their address is used
// in code, but as of November 2017, we use the default setting that
// deduplicates function in this case as well.
//
// Thus these functions are made to be unique, using inline .word in assembly,
// or the equivalent directive depending on the architecture.
//
// Note that code |CheckOrderingSanity()| below will make sure that these
// functions are not aliased, in case the toolchain becomes really clever.
extern "C" {

// The assembler has a different syntax depending on the architecture.
#if defined(ARCH_CPU_ARMEL) || defined(ARCH_CPU_ARM64)

// These functions have a well-defined ordering in this file, see the comment
// in |IsOrderingSane()|.
void dummy_function_end_of_ordered_text() {
  asm(".word 0x21bad44d");
  asm(".word 0xb815c5b0");
}

void dummy_function_start_of_ordered_text() {
  asm(".word 0xe4a07375");
  asm(".word 0x66dda6dc");
}

#elif defined(ARCH_CPU_X86_FAMILY)

void dummy_function_end_of_ordered_text() {
  asm(".4byte 0x21bad44d");
  asm(".4byte 0xb815c5b0");
}

void dummy_function_start_of_ordered_text() {
  asm(".4byte 0xe4a07375");
  asm(".4byte 0x66dda6dc");
}

#endif

// These two symbols are defined by anchor_functions.lds and delimit the start
// and end of .text.
void linker_script_start_of_text();
void linker_script_end_of_text();

}  // extern "C"

namespace base {
namespace android {

const size_t kStartOfText =
    reinterpret_cast<size_t>(linker_script_start_of_text);
const size_t kEndOfText = reinterpret_cast<size_t>(linker_script_end_of_text);
const size_t kStartOfOrderedText =
    reinterpret_cast<size_t>(dummy_function_start_of_ordered_text);
const size_t kEndOfOrderedText =
    reinterpret_cast<size_t>(dummy_function_end_of_ordered_text);

bool AreAnchorsSane() {
  size_t here = reinterpret_cast<size_t>(&IsOrderingSane);
  return kStartOfText < here && here < kEndOfText;
}

bool IsOrderingSane() {
  // The symbols linker_script_start_of_text and linker_script_end_of_text
  // should cover all of .text, and dummy_function_start_of_ordered_text and
  // dummy_function_end_of_ordered_text should cover the ordered part of it.
  // This check is intended to catch the lack of ordering.
  //
  // Ordered text can start at the start of text, but should not cover the
  // entire range. Most addresses are distinct nonetheless as the symbols are
  // different, but linker-defined symbols have zero size and therefore the
  // start address could be the same as the address of
  // dummy_function_start_of_ordered_text.
  return AreAnchorsSane() && kStartOfOrderedText < kEndOfOrderedText &&
         kStartOfText <= kStartOfOrderedText && kEndOfOrderedText < kEndOfText;
}

}  // namespace android
}  // namespace base

#endif  // BUILDFLAG(SUPPORTS_CODE_ORDERING)
