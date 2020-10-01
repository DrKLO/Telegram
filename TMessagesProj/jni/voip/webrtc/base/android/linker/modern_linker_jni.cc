// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Uses android_dlopen_ext() to share relocations.

// This source code *cannot* depend on anything from base/ or the C++
// STL, to keep the final library small, and avoid ugly dependency issues.

#include "base/android/linker/modern_linker_jni.h"

#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <limits.h>
#include <link.h>
#include <stddef.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <limits>
#include <memory>

#include <android/dlext.h>
#include "base/android/linker/linker_jni.h"

// From //base/posix/eintr_wrapper.h, but we don't want to depend on //base.
#define HANDLE_EINTR(x)                                     \
  ({                                                        \
    decltype(x) eintr_wrapper_result;                       \
    do {                                                    \
      eintr_wrapper_result = (x);                           \
    } while (eintr_wrapper_result == -1 && errno == EINTR); \
    eintr_wrapper_result;                                   \
  })

// Not defined on all platforms. As this linker is only supported on ARM32/64,
// x86/x86_64 and MIPS, page size is always 4k.
#if !defined(PAGE_SIZE)
#define PAGE_SIZE (1 << 12)
#define PAGE_MASK (~(PAGE_SIZE - 1))
#endif

#define PAGE_START(x) ((x)&PAGE_MASK)
#define PAGE_END(x) PAGE_START((x) + (PAGE_SIZE - 1))

extern "C" {
// <android/dlext.h> does not declare android_dlopen_ext() if __ANDROID_API__
// is smaller than 21, so declare it here as a weak function. This will allow
// detecting its availability at runtime. For API level 21 or higher, the
// attribute is ignored due to the previous declaration.
void* android_dlopen_ext(const char*, int, const android_dlextinfo*)
    __attribute__((weak_import));

// This function is exported by the dynamic linker but never declared in any
// official header for some architecture/version combinations.
int dl_iterate_phdr(int (*cb)(dl_phdr_info* info, size_t size, void* data),
                    void* data) __attribute__((weak_import));
}  // extern "C"

namespace chromium_android_linker {
namespace {

// Record of the Java VM passed to JNI_OnLoad().
static JavaVM* s_java_vm = nullptr;

// Helper class for anonymous memory mapping.
class ScopedAnonymousMmap {
 public:
  static ScopedAnonymousMmap ReserveAtAddress(void* address, size_t size);

  ~ScopedAnonymousMmap() {
    if (addr_ && owned_)
      munmap(addr_, size_);
  }

  ScopedAnonymousMmap(ScopedAnonymousMmap&& o) {
    addr_ = o.addr_;
    size_ = o.size_;
    owned_ = o.owned_;
    o.Release();
  }

  void* address() const { return addr_; }
  size_t size() const { return size_; }
  void Release() { owned_ = false; }

 private:
  ScopedAnonymousMmap() = default;
  ScopedAnonymousMmap(void* addr, size_t size) : addr_(addr), size_(size) {}

 private:
  bool owned_ = true;
  void* addr_ = nullptr;
  size_t size_ = 0;

  // Move only.
  ScopedAnonymousMmap(const ScopedAnonymousMmap&) = delete;
  ScopedAnonymousMmap& operator=(const ScopedAnonymousMmap&) = delete;
};

// Reserves an address space range, starting at |address|.
// If successful, returns a valid mapping, otherwise returns an empty one.
ScopedAnonymousMmap ScopedAnonymousMmap::ReserveAtAddress(void* address,
                                                          size_t size) {
  void* actual_address =
      mmap(address, size, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  if (actual_address == MAP_FAILED) {
    LOG_INFO("mmap failed: %s", strerror(errno));
    return {};
  }

  if (actual_address && actual_address != address) {
    LOG_ERROR("Failed to obtain fixed address for load");
    return {};
  }

  return {actual_address, size};
}

// Starting with API level 26, the following functions from
// libandroid.so should be used to create shared memory regions.
//
// This ensures compatibility with post-Q versions of Android that may not rely
// on ashmem for shared memory.
//
// This is heavily inspired from //third_party/ashmem/ashmem-dev.c, which we
// cannot reference directly to avoid increasing binary size. Also, we don't
// need to support API level <26.
//
// *Not* threadsafe.
struct SharedMemoryFunctions {
  SharedMemoryFunctions() {
    library_handle = dlopen("libandroid.so", RTLD_NOW);
    create = reinterpret_cast<CreateFunction>(
        dlsym(library_handle, "ASharedMemory_create"));
    set_protection = reinterpret_cast<SetProtectionFunction>(
        dlsym(library_handle, "ASharedMemory_setProt"));

    if (!create || !set_protection)
      LOG_ERROR("Cannot get the shared memory functions from libandroid");
  }

  ~SharedMemoryFunctions() {
    if (library_handle)
      dlclose(library_handle);
  }

  typedef int (*CreateFunction)(const char*, size_t);
  typedef int (*SetProtectionFunction)(int fd, int prot);

  CreateFunction create;
  SetProtectionFunction set_protection;

  void* library_handle;
};

// Metadata about a library loaded at a given |address|.
struct LoadedLibraryMetadata {
  explicit LoadedLibraryMetadata(void* address)
      : load_address(address),
        load_size(0),
        min_vaddr(0),
        relro_start(0),
        relro_size(0) {}

  const void* load_address;

  size_t load_size;
  size_t min_vaddr;
  size_t relro_start;
  size_t relro_size;
};

// android_dlopen_ext() wrapper.
// Returns false if no android_dlopen_ext() is available, otherwise true with
// the return value from android_dlopen_ext() in |status|.
bool AndroidDlopenExt(const char* filename,
                      int flag,
                      const android_dlextinfo& extinfo,
                      void** status) {
  if (!android_dlopen_ext) {
    LOG_ERROR("android_dlopen_ext is not found");
    return false;
  }

  LOG_INFO(
      "android_dlopen_ext:"
      " flags=0x%llx, reserved_addr=%p, reserved_size=%d",
      static_cast<long long>(extinfo.flags), extinfo.reserved_addr,
      static_cast<int>(extinfo.reserved_size));

  *status = android_dlopen_ext(filename, flag, &extinfo);
  return true;
}

// Callback for dl_iterate_phdr(). Read phdrs to identify whether or not
// this library's load address matches the |load_address| passed in
// |data|. If yes, fills the metadata we care about in |data|.
//
// A non-zero return value terminates iteration.
int FindLoadedLibraryMetadata(dl_phdr_info* info,
                              size_t size UNUSED,
                              void* data) {
  auto* metadata = reinterpret_cast<LoadedLibraryMetadata*>(data);

  // Use max and min vaddr to compute the library's load size.
  auto min_vaddr = std::numeric_limits<ElfW(Addr)>::max();
  ElfW(Addr) max_vaddr = 0;

  ElfW(Addr) min_relro_vaddr = ~0;
  ElfW(Addr) max_relro_vaddr = 0;

  bool is_matching = false;
  for (int i = 0; i < info->dlpi_phnum; ++i) {
    const ElfW(Phdr)* phdr = &info->dlpi_phdr[i];
    switch (phdr->p_type) {
      case PT_LOAD: {
        // See if this segment's load address matches what we passed to
        // android_dlopen_ext as extinfo.reserved_addr.
        //
        // Here and below, the virtual address in memory is computed by
        //     address == info->dlpi_addr + program_header->p_vaddr
        // that is, the p_vaddr fields is relative to the object base address.
        // See dl_iterate_phdr(3) for details.
        void* load_addr =
            reinterpret_cast<void*>(info->dlpi_addr + phdr->p_vaddr);
        // Matching is based on the load address, since we have no idea
        // where the relro segment is.
        if (load_addr == metadata->load_address)
          is_matching = true;

        if (phdr->p_vaddr < min_vaddr)
          min_vaddr = phdr->p_vaddr;
        if (phdr->p_vaddr + phdr->p_memsz > max_vaddr)
          max_vaddr = phdr->p_vaddr + phdr->p_memsz;
      } break;
      case PT_GNU_RELRO:
        min_relro_vaddr = PAGE_START(phdr->p_vaddr);
        max_relro_vaddr = phdr->p_vaddr + phdr->p_memsz;
        break;
      default:
        break;
    }
  }

  // If this library matches what we seek, return its load size.
  if (is_matching) {
    int page_size = sysconf(_SC_PAGESIZE);
    if (page_size != PAGE_SIZE)
      abort();

    metadata->load_size = PAGE_END(max_vaddr) - PAGE_START(min_vaddr);
    metadata->min_vaddr = min_vaddr;

    metadata->relro_size =
        PAGE_END(max_relro_vaddr) - PAGE_START(min_relro_vaddr);
    metadata->relro_start = info->dlpi_addr + PAGE_START(min_relro_vaddr);

    return true;
  }

  return false;
}

// Creates an android_dlextinfo struct so that a library is loaded inside the
// space referenced by |mapping|.
std::unique_ptr<android_dlextinfo> MakeAndroidDlextinfo(
    const ScopedAnonymousMmap& mapping) {
  auto info = std::make_unique<android_dlextinfo>();
  memset(info.get(), 0, sizeof(*info));
  info->flags = ANDROID_DLEXT_RESERVED_ADDRESS;
  info->reserved_addr = mapping.address();
  info->reserved_size = mapping.size();

  return info;
}

// Copies the current relocations into a shared-memory file, and uses this file
// as the relocations.
//
// Returns true for success, and populate |fd| with the relocations's fd in this
// case.
bool CopyAndRemapRelocations(const LoadedLibraryMetadata& metadata, int* fd) {
  LOG_INFO("Entering");
  void* relro_addr = reinterpret_cast<void*>(metadata.relro_start);

  SharedMemoryFunctions fns;
  if (!fns.create)
    return false;

  int shared_mem_fd = fns.create("cr_relro", metadata.relro_size);
  if (shared_mem_fd == -1) {
    LOG_ERROR("Cannot create the shared memory file");
    return false;
  }

  int rw_flags = PROT_READ | PROT_WRITE;
  fns.set_protection(shared_mem_fd, rw_flags);

  void* relro_copy_addr = mmap(nullptr, metadata.relro_size, rw_flags,
                               MAP_SHARED, shared_mem_fd, 0);
  if (relro_copy_addr == MAP_FAILED) {
    LOG_ERROR("Cannot mmap() space for the copy");
    close(shared_mem_fd);
    return false;
  }

  memcpy(relro_copy_addr, relro_addr, metadata.relro_size);
  int retval = mprotect(relro_copy_addr, metadata.relro_size, PROT_READ);
  if (retval) {
    LOG_ERROR("Cannot call mprotect()");
    close(shared_mem_fd);
    munmap(relro_copy_addr, metadata.relro_size);
    return false;
  }

  void* new_addr =
      mremap(relro_copy_addr, metadata.relro_size, metadata.relro_size,
             MREMAP_MAYMOVE | MREMAP_FIXED, relro_addr);
  if (new_addr != relro_addr) {
    LOG_ERROR("mremap() error");
    close(shared_mem_fd);
    munmap(relro_copy_addr, metadata.relro_size);
    return false;
  }

  *fd = shared_mem_fd;
  return true;
}

// Gathers metadata about the library loaded at |addr|.
//
// Returns true for success.
bool GetLoadedLibraryMetadata(LoadedLibraryMetadata* metadata) {
  LOG_INFO("Called for %p", metadata->load_address);

  if (!dl_iterate_phdr) {
    LOG_ERROR("No dl_iterate_phdr() found");
    return false;
  }
  int status = dl_iterate_phdr(&FindLoadedLibraryMetadata, metadata);
  if (!status) {
    LOG_ERROR("Failed to find library at address %p", metadata->load_address);
    return false;
  }

  LOG_INFO("Relro start address = %p, size = %d",
           reinterpret_cast<void*>(metadata->relro_start),
           static_cast<int>(metadata->relro_size));

  return true;
}

// Resizes the address space reservation to the actual required size.
// Failure here is only a warning, as at worst this wastes virtual address
// space, not actual memory.
void ResizeMapping(const ScopedAnonymousMmap& mapping,
                   const LoadedLibraryMetadata& metadata) {
  // Trim the reservation mapping to match the library's actual size. Failure
  // to resize is not a fatal error. At worst we lose a portion of virtual
  // address space that we might otherwise have recovered. Note that trimming
  // the mapping here requires that we have already released the scoped
  // mapping.
  const uintptr_t uintptr_addr = reinterpret_cast<uintptr_t>(mapping.address());
  if (mapping.size() > metadata.load_size) {
    // Unmap the part of the reserved address space that is beyond the end of
    // the loaded library data.
    void* unmap = reinterpret_cast<void*>(uintptr_addr + metadata.load_size);
    const size_t length = mapping.size() - metadata.load_size;
    if (munmap(unmap, length) == -1) {
      LOG_ERROR("WARNING: unmap of %d bytes at %p failed: %s",
                static_cast<int>(length), unmap, strerror(errno));
    }
  } else {
    LOG_ERROR("WARNING: library reservation was too small");
  }
}

// Calls JNI_OnLoad() in the library referenced by |handle|.
// Returns true for success.
bool CallJniOnLoad(void* handle) {
  LOG_INFO("Entering");
  // Locate and if found then call the loaded library's JNI_OnLoad() function.
  using JNI_OnLoadFunctionPtr = int (*)(void* vm, void* reserved);
  auto jni_onload =
      reinterpret_cast<JNI_OnLoadFunctionPtr>(dlsym(handle, "JNI_OnLoad"));
  if (jni_onload != nullptr) {
    // Check that JNI_OnLoad returns a usable JNI version.
    int jni_version = (*jni_onload)(s_java_vm, nullptr);
    if (jni_version < JNI_VERSION_1_4) {
      LOG_ERROR("JNI version is invalid: %d", jni_version);
      return false;
    }
  }

  return true;
}

// Load the library at |path| at address |wanted_address| if possible, and
// creates a file with relro at |relocations_path|.
//
// In case of success, returns a readonly file descriptor to the relocations,
// otherwise returns -1.
int LoadCreateSharedRelocations(const String& path, void* wanted_address) {
  LOG_INFO("Entering");
  ScopedAnonymousMmap mapping = ScopedAnonymousMmap::ReserveAtAddress(
      wanted_address, kAddressSpaceReservationSize);
  if (!mapping.address())
    return -1;

  std::unique_ptr<android_dlextinfo> dlextinfo = MakeAndroidDlextinfo(mapping);
  void* handle = nullptr;
  if (!AndroidDlopenExt(path.c_str(), RTLD_NOW, *dlextinfo, &handle)) {
    LOG_ERROR("android_dlopen_ext() error");
    return -1;
  }
  if (handle == nullptr) {
    LOG_ERROR("android_dlopen_ext: %s", dlerror());
    return -1;
  }
  mapping.Release();

  LoadedLibraryMetadata metadata{mapping.address()};
  bool ok = GetLoadedLibraryMetadata(&metadata);
  int relro_fd = -1;
  if (ok) {
    ResizeMapping(mapping, metadata);
    CopyAndRemapRelocations(metadata, &relro_fd);
  }

  if (!CallJniOnLoad(handle))
    return false;

  return relro_fd;
}

// Load the library at |path| at address |wanted_address| if possible, and
// uses the relocations in |relocations_fd| if possible.
bool LoadUseSharedRelocations(const String& path,
                              void* wanted_address,
                              int relocations_fd) {
  LOG_INFO("Entering");
  ScopedAnonymousMmap mapping = ScopedAnonymousMmap::ReserveAtAddress(
      wanted_address, kAddressSpaceReservationSize);
  if (!mapping.address())
    return false;

  std::unique_ptr<android_dlextinfo> dlextinfo = MakeAndroidDlextinfo(mapping);
  void* handle = nullptr;
  if (!AndroidDlopenExt(path.c_str(), RTLD_NOW, *dlextinfo, &handle)) {
    LOG_ERROR("No android_dlopen_ext function found");
    return false;
  }
  if (handle == nullptr) {
    LOG_ERROR("android_dlopen_ext: %s", dlerror());
    return false;
  }
  mapping.Release();

  LoadedLibraryMetadata metadata{mapping.address()};
  bool ok = GetLoadedLibraryMetadata(&metadata);
  if (!ok) {
    LOG_ERROR("Cannot get library's metadata");
    return false;
  }

  ResizeMapping(mapping, metadata);
  void* shared_relro_mapping_address = mmap(
      nullptr, metadata.relro_size, PROT_READ, MAP_SHARED, relocations_fd, 0);
  if (shared_relro_mapping_address == MAP_FAILED) {
    LOG_ERROR("Cannot map the relocations");
    return false;
  }

  void* current_relro_address = reinterpret_cast<void*>(metadata.relro_start);
  int retval = memcmp(shared_relro_mapping_address, current_relro_address,
                      metadata.relro_size);
  if (!retval) {
    void* new_addr = mremap(shared_relro_mapping_address, metadata.relro_size,
                            metadata.relro_size, MREMAP_MAYMOVE | MREMAP_FIXED,
                            current_relro_address);
    if (new_addr != current_relro_address) {
      LOG_ERROR("Cannot call mremap()");
      munmap(shared_relro_mapping_address, metadata.relro_size);
      return false;
    }
  } else {
    munmap(shared_relro_mapping_address, metadata.relro_size);
    LOG_ERROR("Relocations are not identical, giving up.");
  }

  if (!CallJniOnLoad(handle))
    return false;

  return true;
}

bool LoadNoSharedRelocations(const String& path) {
  void* handle = dlopen(path.c_str(), RTLD_NOW);
  if (!handle) {
    LOG_ERROR("dlopen: %s", dlerror());
    return false;
  }

  if (!CallJniOnLoad(handle))
    return false;

  return true;
}

}  // namespace

JNI_GENERATOR_EXPORT jboolean
Java_org_chromium_base_library_1loader_ModernLinker_nativeLoadLibraryCreateRelros(
    JNIEnv* env,
    jclass clazz,
    jstring jdlopen_ext_path,
    jlong load_address,
    jobject lib_info_obj) {
  LOG_INFO("Entering");

  String library_path(env, jdlopen_ext_path);

  if (!IsValidAddress(load_address)) {
    LOG_ERROR("Invalid address 0x%llx", static_cast<long long>(load_address));
    return false;
  }
  void* address = reinterpret_cast<void*>(load_address);

  int fd = LoadCreateSharedRelocations(library_path, address);
  if (fd == -1)
    return false;

  // Note the shared RELRO fd in the supplied libinfo object. In this
  // implementation the RELRO start is set to the library's load address,
  // and the RELRO size is unused.
  const size_t cast_addr = reinterpret_cast<size_t>(address);
  s_lib_info_fields.SetRelroInfo(env, lib_info_obj, cast_addr, 0, fd);

  return true;
}

JNI_GENERATOR_EXPORT jboolean
Java_org_chromium_base_library_1loader_ModernLinker_nativeLoadLibraryUseRelros(
    JNIEnv* env,
    jclass clazz,
    jstring jdlopen_ext_path,
    jlong load_address,
    jint relro_fd) {
  LOG_INFO("Entering");

  String library_path(env, jdlopen_ext_path);

  if (!IsValidAddress(load_address)) {
    LOG_ERROR("Invalid address 0x%llx", static_cast<long long>(load_address));
    return false;
  }
  void* address = reinterpret_cast<void*>(load_address);

  return LoadUseSharedRelocations(library_path, address, relro_fd);
}

JNI_GENERATOR_EXPORT jboolean
Java_org_chromium_base_library_1loader_ModernLinker_nativeLoadLibraryNoRelros(
    JNIEnv* env,
    jclass clazz,
    jstring jdlopen_ext_path) {
  String library_path(env, jdlopen_ext_path);
  return LoadNoSharedRelocations(library_path);
}

bool ModernLinkerJNIInit(JavaVM* vm, JNIEnv* env) {
  s_java_vm = vm;
  return true;
}

}  // namespace chromium_android_linker
