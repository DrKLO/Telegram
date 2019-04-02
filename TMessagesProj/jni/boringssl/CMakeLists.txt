cmake_minimum_required (VERSION 2.8.11)

# Report AppleClang separately from Clang. Their version numbers are different.
# https://cmake.org/cmake/help/v3.0/policy/CMP0025.html
if(POLICY CMP0025)
  cmake_policy(SET CMP0025 NEW)
endif()

# Defer enabling C and CXX languages.
project (BoringSSL NONE)

if(WIN32)
  # On Windows, prefer cl over gcc if both are available. By default most of
  # the CMake generators prefer gcc, even on Windows.
  set(CMAKE_GENERATOR_CC cl)
endif()

include(sources.cmake)

enable_language(C)
enable_language(CXX)

if(ANDROID)
  # Android-NDK CMake files reconfigure the path and so Go and Perl won't be
  # found. However, ninja will still find them in $PATH if we just name them.
  if(NOT PERL_EXECUTABLE)
    set(PERL_EXECUTABLE "perl")
  endif()
  if(NOT GO_EXECUTABLE)
    set(GO_EXECUTABLE "go")
  endif()
else()
  find_package(Perl REQUIRED)
  find_program(GO_EXECUTABLE go)
endif()

if (NOT GO_EXECUTABLE)
  message(FATAL_ERROR "Could not find Go")
endif()

if (BORINGSSL_ALLOW_CXX_RUNTIME)
  add_definitions(-DBORINGSSL_ALLOW_CXX_RUNTIME)
endif()

if(CMAKE_CXX_COMPILER_ID MATCHES "Clang")
  set(CLANG 1)
endif()

if(CMAKE_COMPILER_IS_GNUCXX OR CLANG)
  # Note clang-cl is odd and sets both CLANG and MSVC. We base our configuration
  # primarily on our normal Clang one because the MSVC one is mostly
  # suppressions for an overaggressive -Wall.
  set(C_CXX_FLAGS "-Wall -Werror -Wformat=2 -Wsign-compare -Wmissing-field-initializers -Wwrite-strings")
  if(MSVC)
    # clang-cl sets different default warnings than clang.
    set(C_CXX_FLAGS "${C_CXX_FLAGS} -Wno-unused-parameter -fmsc-version=1900")
    # googletest suppresses warning C4996 via a pragma, but clang-cl does not
    # honor it. Suppress it here to compensate. See https://crbug.com/772117.
    set(C_CXX_FLAGS "${C_CXX_FLAGS} -Wno-deprecated-declarations")
  else()
    set(C_CXX_FLAGS "${C_CXX_FLAGS} -ggdb -fvisibility=hidden -fno-common")
  endif()

  if(CLANG)
    set(C_CXX_FLAGS "${C_CXX_FLAGS} -Wnewline-eof -fcolor-diagnostics")
  else()
    # GCC (at least 4.8.4) has a bug where it'll find unreachable free() calls
    # and declare that the code is trying to free a stack pointer.
    set(C_CXX_FLAGS "${C_CXX_FLAGS} -Wno-free-nonheap-object")
  endif()

  if(CMAKE_CXX_COMPILER_ID STREQUAL "Clang" AND
     NOT "6.0.0" VERSION_GREATER CMAKE_C_COMPILER_VERSION)
    # Clang's -Wtautological-constant-compare is far too aggressive and does not
    # account for, say, wanting the same code to work on both 32-bit and 64-bit
    # platforms.
    #
    # Note "Clang" and "AppleClang" version differently, so we check for an
    # exact match on the COMPILER_ID. As of writing, the warning is not in any
    # release of AppleClang yet.
    set(C_CXX_FLAGS "${C_CXX_FLAGS} -Wno-tautological-constant-compare -Wtautological-constant-out-of-range-compare")
  endif()

  if(CLANG OR NOT "7.0.0" VERSION_GREATER CMAKE_C_COMPILER_VERSION)
    set(C_CXX_FLAGS "${C_CXX_FLAGS} -Wimplicit-fallthrough")
  endif()

  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${C_CXX_FLAGS} -Wmissing-prototypes -Wold-style-definition -Wstrict-prototypes")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${C_CXX_FLAGS} -Wmissing-declarations")

  if(NOT MSVC)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
    if(APPLE)
      set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -stdlib=libc++")
    endif()
    if(NOT BORINGSSL_ALLOW_CXX_RUNTIME)
      set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-exceptions -fno-rtti")
    endif()
  endif()

  # In GCC, -Wmissing-declarations is the C++ spelling of -Wmissing-prototypes
  # and using the wrong one is an error. In Clang, -Wmissing-prototypes is the
  # spelling for both and -Wmissing-declarations is some other warning.
  #
  # https://gcc.gnu.org/onlinedocs/gcc-7.1.0/gcc/Warning-Options.html#Warning-Options
  # https://clang.llvm.org/docs/DiagnosticsReference.html#wmissing-prototypes
  # https://clang.llvm.org/docs/DiagnosticsReference.html#wmissing-declarations
  if(CLANG)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wmissing-prototypes")
  endif()

  if(CMAKE_COMPILER_IS_GNUCXX AND "4.8" VERSION_GREATER CMAKE_C_COMPILER_VERSION)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wno-array-bounds")
  endif()

elseif(MSVC)
  set(MSVC_DISABLED_WARNINGS_LIST
      "C4061" # enumerator 'identifier' in switch of enum 'enumeration' is not
              # explicitly handled by a case label
              # Disable this because it flags even when there is a default.
      "C4100" # 'exarg' : unreferenced formal parameter
      "C4127" # conditional expression is constant
      "C4200" # nonstandard extension used : zero-sized array in
              # struct/union.
      "C4204" # nonstandard extension used: non-constant aggregate initializer
      "C4221" # nonstandard extension used : 'identifier' : cannot be
              # initialized using address of automatic variable
      "C4242" # 'function' : conversion from 'int' to 'uint8_t',
              # possible loss of data
      "C4244" # 'function' : conversion from 'int' to 'uint8_t',
              # possible loss of data
      "C4267" # conversion from 'size_t' to 'int', possible loss of data
      "C4371" # layout of class may have changed from a previous version of the
              # compiler due to better packing of member '...'
      "C4388" # signed/unsigned mismatch
      "C4296" # '>=' : expression is always true
      "C4350" # behavior change: 'std::_Wrap_alloc...'
      "C4365" # '=' : conversion from 'size_t' to 'int',
              # signed/unsigned mismatch
      "C4389" # '!=' : signed/unsigned mismatch
      "C4464" # relative include path contains '..'
      "C4510" # 'argument' : default constructor could not be generated
      "C4512" # 'argument' : assignment operator could not be generated
      "C4514" # 'function': unreferenced inline function has been removed
      "C4548" # expression before comma has no effect; expected expression with
              # side-effect" caused by FD_* macros.
      "C4610" # struct 'argument' can never be instantiated - user defined
              # constructor required.
      "C4623" # default constructor was implicitly defined as deleted
      "C4625" # copy constructor could not be generated because a base class
              # copy constructor is inaccessible or deleted
      "C4626" # assignment operator could not be generated because a base class
              # assignment operator is inaccessible or deleted
      "C4668" # 'symbol' is not defined as a preprocessor macro, replacing with
              # '0' for 'directives'
              # Disable this because GTest uses it everywhere.
      "C4706" # assignment within conditional expression
      "C4710" # 'function': function not inlined
      "C4711" # function 'function' selected for inline expansion
      "C4800" # 'int' : forcing value to bool 'true' or 'false'
              # (performance warning)
      "C4820" # 'bytes' bytes padding added after construct 'member_name'
      "C5026" # move constructor was implicitly defined as deleted
      "C5027" # move assignment operator was implicitly defined as deleted
      )
  set(MSVC_LEVEL4_WARNINGS_LIST
      # See https://connect.microsoft.com/VisualStudio/feedback/details/1217660/warning-c4265-when-using-functional-header
      "C4265" # class has virtual functions, but destructor is not virtual
      )
  string(REPLACE "C" " -wd" MSVC_DISABLED_WARNINGS_STR
                            ${MSVC_DISABLED_WARNINGS_LIST})
  string(REPLACE "C" " -w4" MSVC_LEVEL4_WARNINGS_STR
                            ${MSVC_LEVEL4_WARNINGS_LIST})
  set(CMAKE_C_FLAGS   "-Wall -WX ${MSVC_DISABLED_WARNINGS_STR} ${MSVC_LEVEL4_WARNINGS_STR}")
  set(CMAKE_CXX_FLAGS "-Wall -WX ${MSVC_DISABLED_WARNINGS_STR} ${MSVC_LEVEL4_WARNINGS_STR}")
endif()

if(WIN32)
  add_definitions(-D_HAS_EXCEPTIONS=0)
  add_definitions(-DWIN32_LEAN_AND_MEAN)
  add_definitions(-DNOMINMAX)
  # Allow use of fopen.
  add_definitions(-D_CRT_SECURE_NO_WARNINGS)
  # VS 2017 and higher supports STL-only warning suppressions.
  add_definitions("-D_STL_EXTRA_DISABLED_WARNINGS=4774 4987")
endif()

if((CMAKE_COMPILER_IS_GNUCXX AND CMAKE_C_COMPILER_VERSION VERSION_GREATER "4.7.99") OR
   CLANG)
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wshadow")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wshadow")
endif()

if(CMAKE_COMPILER_IS_GNUCXX)
  if ((CMAKE_C_COMPILER_VERSION VERSION_GREATER "4.8.99") OR CLANG)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c11")
  else()
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99")
  endif()
endif()

# pthread_rwlock_t requires a feature flag.
if(NOT WIN32)
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D_XOPEN_SOURCE=700")
endif()

if(FUZZ)
  if(NOT CLANG)
    message(FATAL_ERROR "You need to build with Clang for fuzzing to work")
  endif()

  add_definitions(-DBORINGSSL_UNSAFE_DETERMINISTIC_MODE)
  set(RUNNER_ARGS "-deterministic")

  if(NOT NO_FUZZER_MODE)
    add_definitions(-DBORINGSSL_UNSAFE_FUZZER_MODE)
    set(RUNNER_ARGS ${RUNNER_ARGS} "-fuzzer" "-shim-config" "fuzzer_mode.json")
  endif()

  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fsanitize=address -fsanitize-coverage=edge,indirect-calls,trace-pc-guard")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fsanitize=address -fsanitize-coverage=edge,indirect-calls,trace-pc-guard")
  set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_C_FLAGS} -fsanitize=address")
  link_directories(.)
endif()

add_definitions(-DBORINGSSL_IMPLEMENTATION)

if (BUILD_SHARED_LIBS)
  add_definitions(-DBORINGSSL_SHARED_LIBRARY)
  # Enable position-independent code globally. This is needed because
  # some library targets are OBJECT libraries.
  set(CMAKE_POSITION_INDEPENDENT_CODE TRUE)
endif()

if (MSAN)
  if(NOT CLANG)
    message(FATAL_ERROR "Cannot enable MSAN unless using Clang")
  endif()

  if (ASAN)
    message(FATAL_ERROR "ASAN and MSAN are mutually exclusive")
  endif()

  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fsanitize=memory -fsanitize-memory-track-origins -fno-omit-frame-pointer")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fsanitize=memory -fsanitize-memory-track-origins -fno-omit-frame-pointer")
  set(OPENSSL_NO_ASM "1")
endif()

if (ASAN)
  if(NOT CLANG)
    message(FATAL_ERROR "Cannot enable ASAN unless using Clang")
  endif()

  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fsanitize=address -fsanitize-address-use-after-scope -fno-omit-frame-pointer")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fsanitize=address -fsanitize-address-use-after-scope -fno-omit-frame-pointer")
  set(OPENSSL_NO_ASM "1")
endif()

if(CFI)
  if(NOT CLANG)
    message(FATAL_ERROR "Cannot enable CFI unless using Clang")
  endif()

  # TODO(crbug.com/785442): Remove -fsanitize-cfi-icall-generalize-pointers.
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fsanitize=cfi -fno-sanitize-trap=cfi -fsanitize-cfi-icall-generalize-pointers -flto")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fsanitize=cfi -fno-sanitize-trap=cfi -fsanitize-cfi-icall-generalize-pointers -flto")
  # We use Chromium's copy of clang, which requires -fuse-ld=lld if building
  # with -flto. That, in turn, can't handle -ggdb.
  set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -fuse-ld=lld")
  string(REPLACE "-ggdb" "-g" CMAKE_C_FLAGS "${CMAKE_C_FLAGS}")
  string(REPLACE "-ggdb" "-g" CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}")
  # -flto causes object files to contain LLVM bitcode. Mixing those with
  # assembly output in the same static library breaks the linker.
  set(OPENSSL_NO_ASM "1")
endif()

if (GCOV)
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fprofile-arcs -ftest-coverage")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fprofile-arcs -ftest-coverage")
endif()

if(FIPS)
  add_definitions(-DBORINGSSL_FIPS)
  if(FIPS_BREAK_TEST)
    add_definitions("-DBORINGSSL_FIPS_BREAK_${FIPS_BREAK_TEST}=1")
  endif()
  # Delocate does not work for ASan and MSan builds.
  if(NOT ASAN AND NOT MSAN)
    set(FIPS_DELOCATE "1")
  endif()
endif()

# CMake's iOS support uses Apple's multiple-architecture toolchain. It takes an
# architecture list from CMAKE_OSX_ARCHITECTURES, leaves CMAKE_SYSTEM_PROCESSOR
# alone, and expects all architecture-specific logic to be conditioned within
# the source files rather than the build. This does not work for our assembly
# files, so we fix CMAKE_SYSTEM_PROCESSOR and only support single-architecture
# builds.
if (NOT OPENSSL_NO_ASM AND CMAKE_OSX_ARCHITECTURES)
  list(LENGTH CMAKE_OSX_ARCHITECTURES NUM_ARCHES)
  if (NOT ${NUM_ARCHES} EQUAL 1)
    message(FATAL_ERROR "Universal binaries not supported.")
  endif()
  list(GET CMAKE_OSX_ARCHITECTURES 0 CMAKE_SYSTEM_PROCESSOR)
endif()

if (OPENSSL_NO_ASM)
  add_definitions(-DOPENSSL_NO_ASM)
  set(ARCH "generic")
elseif (${CMAKE_SYSTEM_PROCESSOR} STREQUAL "x86_64")
  set(ARCH "x86_64")
elseif (${CMAKE_SYSTEM_PROCESSOR} STREQUAL "amd64")
  set(ARCH "x86_64")
elseif (${CMAKE_SYSTEM_PROCESSOR} STREQUAL "AMD64")
  # cmake reports AMD64 on Windows, but we might be building for 32-bit.
  if (CMAKE_CL_64)
    set(ARCH "x86_64")
  else()
    set(ARCH "x86")
  endif()
elseif (${CMAKE_SYSTEM_PROCESSOR} STREQUAL "x86")
  set(ARCH "x86")
elseif (${CMAKE_SYSTEM_PROCESSOR} STREQUAL "i386")
  set(ARCH "x86")
elseif (${CMAKE_SYSTEM_PROCESSOR} STREQUAL "i686")
  set(ARCH "x86")
elseif (${CMAKE_SYSTEM_PROCESSOR} STREQUAL "aarch64")
  set(ARCH "aarch64")
elseif (${CMAKE_SYSTEM_PROCESSOR} STREQUAL "arm64")
  set(ARCH "aarch64")
elseif (${CMAKE_SYSTEM_PROCESSOR} MATCHES "^arm*")
  set(ARCH "arm")
elseif (${CMAKE_SYSTEM_PROCESSOR} STREQUAL "mips")
  # Just to avoid the “unknown processor” error.
  set(ARCH "generic")
elseif (${CMAKE_SYSTEM_PROCESSOR} STREQUAL "ppc64le")
  set(ARCH "ppc64le")
else()
  message(FATAL_ERROR "Unknown processor:" ${CMAKE_SYSTEM_PROCESSOR})
endif()

if (ANDROID AND NOT ANDROID_NDK_REVISION AND ${ARCH} STREQUAL "arm")
  # The third-party Android-NDK CMake files somehow fail to set the -march flag
  # for assembly files. Without this flag, the compiler believes that it's
  # building for ARMv5.
  set(CMAKE_ASM_FLAGS "-march=${CMAKE_SYSTEM_PROCESSOR} ${CMAKE_ASM_FLAGS}")
endif()

if (${ARCH} STREQUAL "x86" AND APPLE AND ${CMAKE_VERSION} VERSION_LESS "3.0")
  # With CMake 2.8.x, ${CMAKE_SYSTEM_PROCESSOR} evalutes to i386 on OS X,
  # but clang defaults to 64-bit builds on OS X unless otherwise told.
  # Set ARCH to x86_64 so clang and CMake agree. This is fixed in CMake 3.
  set(ARCH "x86_64")
endif()

add_subdirectory(crypto)
add_subdirectory(third_party/fiat)

if (NOT ${CMAKE_VERSION} VERSION_LESS "3.2")
  # USES_TERMINAL is only available in CMake 3.2 or later.
  set(MAYBE_USES_TERMINAL USES_TERMINAL)
endif()
