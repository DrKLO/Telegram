// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This header defines symbols to override the same functions in the Visual C++
// CRT implementation.

#ifdef BASE_ALLOCATOR_ALLOCATOR_SHIM_OVERRIDE_UCRT_SYMBOLS_WIN_H_
#error This header is meant to be included only once by allocator_shim.cc
#endif
#define BASE_ALLOCATOR_ALLOCATOR_SHIM_OVERRIDE_UCRT_SYMBOLS_WIN_H_

#include <malloc.h>

#include <windows.h>

extern "C" {

void* (*malloc_unchecked)(size_t) = &base::allocator::UncheckedAlloc;

namespace {

int win_new_mode = 0;

}  // namespace

// This function behaves similarly to MSVC's _set_new_mode.
// If flag is 0 (default), calls to malloc will behave normally.
// If flag is 1, calls to malloc will behave like calls to new,
// and the std_new_handler will be invoked on failure.
// Returns the previous mode.
//
// Replaces _set_new_mode in ucrt\heap\new_mode.cpp
int _set_new_mode(int flag) {
  // The MS CRT calls this function early on in startup, so this serves as a low
  // overhead proof that the allocator shim is in place for this process.
  base::allocator::g_is_win_shim_layer_initialized = true;
  int old_mode = win_new_mode;
  win_new_mode = flag;

  base::allocator::SetCallNewHandlerOnMallocFailure(win_new_mode != 0);

  return old_mode;
}

// Replaces _query_new_mode in ucrt\heap\new_mode.cpp
int _query_new_mode() {
  return win_new_mode;
}

// These symbols override the CRT's implementation of the same functions.
__declspec(restrict) void* malloc(size_t size) {
  return ShimMalloc(size, nullptr);
}

void free(void* ptr) {
  ShimFree(ptr, nullptr);
}

__declspec(restrict) void* realloc(void* ptr, size_t size) {
  return ShimRealloc(ptr, size, nullptr);
}

__declspec(restrict) void* calloc(size_t n, size_t size) {
  return ShimCalloc(n, size, nullptr);
}

// _msize() is the Windows equivalent of malloc_size().
size_t _msize(void* memblock) {
  return ShimGetSizeEstimate(memblock, nullptr);
}

__declspec(restrict) void* _aligned_malloc(size_t size, size_t alignment) {
  return ShimAlignedMalloc(size, alignment, nullptr);
}

__declspec(restrict) void* _aligned_realloc(void* address,
                                            size_t size,
                                            size_t alignment) {
  return ShimAlignedRealloc(address, size, alignment, nullptr);
}

void _aligned_free(void* address) {
  ShimAlignedFree(address, nullptr);
}

// _recalloc_base is called by CRT internally.
__declspec(restrict) void* _recalloc_base(void* block,
                                          size_t count,
                                          size_t size) {
  const size_t old_block_size = (block != nullptr) ? _msize(block) : 0;
  base::CheckedNumeric<size_t> new_block_size_checked = count;
  new_block_size_checked *= size;
  const size_t new_block_size = new_block_size_checked.ValueOrDie();

  void* const new_block = realloc(block, new_block_size);

  if (new_block != nullptr && old_block_size < new_block_size) {
    memset(static_cast<char*>(new_block) + old_block_size, 0,
           new_block_size - old_block_size);
  }

  return new_block;
}

__declspec(restrict) void* _recalloc(void* block, size_t count, size_t size) {
  return _recalloc_base(block, count, size);
}

// The following uncommon _aligned_* routines are not used in Chromium and have
// been shimmed to immediately crash to ensure that implementations are added if
// uses are introduced.
__declspec(restrict) void* _aligned_recalloc(void* address,
                                             size_t num,
                                             size_t size,
                                             size_t alignment) {
  CHECK(false) << "This routine has not been implemented";
  __builtin_unreachable();
}

size_t _aligned_msize(void* address, size_t alignment, size_t offset) {
  CHECK(false) << "This routine has not been implemented";
  __builtin_unreachable();
}

__declspec(restrict) void* _aligned_offset_malloc(size_t size,
                                                  size_t alignment,
                                                  size_t offset) {
  CHECK(false) << "This routine has not been implemented";
  __builtin_unreachable();
}

__declspec(restrict) void* _aligned_offset_realloc(void* address,
                                                   size_t size,
                                                   size_t alignment,
                                                   size_t offset) {
  CHECK(false) << "This routine has not been implemented";
  __builtin_unreachable();
}

__declspec(restrict) void* _aligned_offset_recalloc(void* address,
                                                    size_t num,
                                                    size_t size,
                                                    size_t alignment,
                                                    size_t offset) {
  CHECK(false) << "This routine has not been implemented";
  __builtin_unreachable();
}

// The symbols
//   * __acrt_heap
//   * __acrt_initialize_heap
//   * __acrt_uninitialize_heap
//   * _get_heap_handle
// must be overridden all or none, as they are otherwise supplied
// by heap_handle.obj in the ucrt.lib file.
HANDLE __acrt_heap = nullptr;

bool __acrt_initialize_heap() {
  __acrt_heap = ::HeapCreate(0, 0, 0);
  return true;
}

bool __acrt_uninitialize_heap() {
  ::HeapDestroy(__acrt_heap);
  __acrt_heap = nullptr;
  return true;
}

intptr_t _get_heap_handle(void) {
  return reinterpret_cast<intptr_t>(__acrt_heap);
}

}  // extern "C"
