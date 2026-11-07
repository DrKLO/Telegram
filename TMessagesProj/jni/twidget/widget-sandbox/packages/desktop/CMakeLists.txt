# twidget desktop core: the QuickJS sandbox engine, trusted validator, CPU
# budget and WidgetHost as a Qt-free C++20 library (for tdesktop or any Qt/C++
# host), plus a console harness and tests. quickjs-ng comes from
# ios/Sources/CQuickJS — one engine, one shim, one set of sources on every
# platform. -DTW_BLITZ=ON adds the Blitz renderer prototype (the Rust crate in
# ../../apps/renderer-blitz built by cargo; dev tooling, see docs/blitz-experiment.md).
cmake_minimum_required(VERSION 3.22)
project(twidget-desktop C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE RelWithDebInfo)
endif()

option(TW_BLITZ "Build the Blitz renderer prototype (needs rustup's cargo)" OFF)

set(TW_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/..")
set(QJS "${TW_ROOT}/ios/Sources/CQuickJS")
if(NOT EXISTS "${QJS}/quickjs/quickjs.c")
    message(FATAL_ERROR "quickjs-ng sources not found at ${QJS}; packages/desktop must sit next to packages/ios")
endif()

add_library(twquickjs STATIC
    "${QJS}/quickjs/quickjs.c"
    "${QJS}/quickjs/dtoa.c"
    "${QJS}/quickjs/libregexp.c"
    "${QJS}/quickjs/libunicode.c"
    "${QJS}/shim/qjs_shim.c")
target_include_directories(twquickjs PUBLIC "${QJS}/include" "${QJS}/quickjs")
target_compile_definitions(twquickjs PRIVATE QUICKJS_NG_BUILD)
if(NOT MSVC)
    target_compile_definitions(twquickjs PRIVATE _GNU_SOURCE)
    # Always optimise the interpreter, also in debug builds: an -O0 quickjs-ng spends
    # ~10 KB of C stack per JS call and is several times slower.
    target_compile_options(twquickjs PRIVATE -O2 -fno-strict-aliasing -Wno-unused-parameter -Wno-sign-compare -Wno-implicit-fallthrough)
endif()
if(NOT WIN32)
    target_link_libraries(twquickjs PUBLIC m)
endif()

find_package(Threads REQUIRED)

add_library(twidget-sandbox STATIC
    sandbox/src/js_thread.cpp
    sandbox/src/quickjs_context.cpp
    sandbox/src/cpu_budget.cpp
    sandbox/src/sdk_policy.cpp
    sandbox/src/gesture_policy.cpp
    sandbox/src/resources.cpp
    sandbox/src/bundle_stamp.cpp
    sandbox/src/intl.cpp
    sandbox/src/trusted_validator.cpp
    sandbox/src/sandbox_engine.cpp
    sandbox/src/widget_host.cpp)
target_include_directories(twidget-sandbox PUBLIC sandbox/include)
target_link_libraries(twidget-sandbox PUBLIC twquickjs Threads::Threads)
if(WIN32)
    # Intl on the system's ICU (icu.dll, Windows 10 1903 and later).
    target_sources(twidget-sandbox PRIVATE sandbox/src/icu_intl.cpp)
    target_link_libraries(twidget-sandbox PUBLIC bcrypt icu)
endif()
if(NOT MSVC)
    target_compile_options(twidget-sandbox PRIVATE -Wall -Wextra)
endif()

# ───────────── Blitz renderer prototype (optional) ─────────────
# The crate in ../../apps/renderer-blitz is built for the host triple by cargo (rustup's,
# like build-apple.sh and the Android module) and linked as a static library;
# twidget-renderer-blitz is the C++ Renderer over its C ABI.
if(TW_BLITZ)
    set(TW_BLITZ_CRATE "${TW_ROOT}/../apps/renderer-blitz")
    find_program(TW_CARGO cargo HINTS /opt/homebrew/opt/rustup/bin "$ENV{HOME}/.cargo/bin")
    find_program(TW_RUSTC rustc HINTS /opt/homebrew/opt/rustup/bin "$ENV{HOME}/.cargo/bin")
    if(NOT TW_CARGO OR NOT TW_RUSTC)
        message(FATAL_ERROR "TW_BLITZ needs cargo and rustc (rustup); none found in PATH, /opt/homebrew/opt/rustup/bin or ~/.cargo/bin")
    endif()
    if(NOT TW_RUST_TARGET)
        execute_process(COMMAND "${TW_RUSTC}" -vV OUTPUT_VARIABLE rustc_info OUTPUT_STRIP_TRAILING_WHITESPACE)
        string(REGEX MATCH "host: ([^\n]+)" _ "${rustc_info}")
        set(TW_RUST_TARGET "${CMAKE_MATCH_1}" CACHE STRING "Rust target triple the Blitz library is built for")
    endif()
    set(TW_BLITZ_LIB "${TW_BLITZ_CRATE}/target/${TW_RUST_TARGET}/release/${CMAKE_STATIC_LIBRARY_PREFIX}twidget_blitz${CMAKE_STATIC_LIBRARY_SUFFIX}")
    file(GLOB TW_BLITZ_SOURCES CONFIGURE_DEPENDS "${TW_BLITZ_CRATE}/src/*.rs" "${TW_BLITZ_CRATE}/include/*.h")
    add_custom_command(OUTPUT "${TW_BLITZ_LIB}"
        COMMAND "${TW_CARGO}" build --release --lib --target "${TW_RUST_TARGET}"
        WORKING_DIRECTORY "${TW_BLITZ_CRATE}"
        DEPENDS ${TW_BLITZ_SOURCES} "${TW_BLITZ_CRATE}/Cargo.toml" "${TW_BLITZ_CRATE}/Cargo.lock"
        COMMENT "cargo build renderer-blitz (${TW_RUST_TARGET}; the first build takes a minute or two)"
        VERBATIM)
    add_custom_target(twidget-blitz-cargo DEPENDS "${TW_BLITZ_LIB}")
    add_library(twidget-blitz-rs STATIC IMPORTED)
    set_target_properties(twidget-blitz-rs PROPERTIES IMPORTED_LOCATION "${TW_BLITZ_LIB}")
    add_dependencies(twidget-blitz-rs twidget-blitz-cargo)
    # What `cargo rustc -- --print native-static-libs` lists for the crate (system fonts via CoreText / fontconfig files).
    if(APPLE)
        set_property(TARGET twidget-blitz-rs PROPERTY INTERFACE_LINK_LIBRARIES "-framework Foundation" "-framework CoreText" "-framework CoreGraphics" "-framework CoreFoundation" objc iconv)
    elseif(UNIX)
        set_property(TARGET twidget-blitz-rs PROPERTY INTERFACE_LINK_LIBRARIES ${CMAKE_DL_LIBS} Threads::Threads m)
    else()
        message(FATAL_ERROR "TW_BLITZ: the Windows link line (ws2_32, userenv, bcrypt, ntdll, dwrite…) is not wired up yet")
    endif()

    add_library(twidget-renderer-blitz STATIC renderer/src/blitz_renderer.cpp)
    target_include_directories(twidget-renderer-blitz PUBLIC renderer/include "${TW_BLITZ_CRATE}/include")
    target_link_libraries(twidget-renderer-blitz PUBLIC twidget-sandbox twidget-blitz-rs)
    target_compile_definitions(twidget-renderer-blitz PUBLIC TW_HAVE_BLITZ=1)
    if(NOT MSVC)
        target_compile_options(twidget-renderer-blitz PRIVATE -Wall -Wextra)
    endif()
endif()

# ───────────── litehtml renderer prototype (optional) ─────────────
# The C++ core in ../../apps/renderer-litehtml (vendored litehtml, shadow tree re-parsed
# per batch, CoreText/CoreGraphics backend) built by its own CMakeLists and
# linked through its C ABI, the same shape as the Blitz one; macOS only until a
# non-Apple backend (QPainter on tdesktop) exists. See docs/litehtml-renderer.md.
if(TW_LITEHTML)
    if(NOT APPLE)
        message(FATAL_ERROR "TW_LITEHTML: only the CoreText/CoreGraphics backend exists so far (renderer-litehtml/src/apple); a QPainter backend is the tdesktop path")
    endif()
    add_subdirectory("${TW_ROOT}/../apps/renderer-litehtml" twidget-litehtml EXCLUDE_FROM_ALL)
    add_library(twidget-renderer-litehtml STATIC renderer/src/litehtml_renderer.cpp)
    target_include_directories(twidget-renderer-litehtml PUBLIC renderer/include)
    target_link_libraries(twidget-renderer-litehtml PUBLIC twidget-sandbox twidget_litehtml)
    target_compile_definitions(twidget-renderer-litehtml PUBLIC TW_HAVE_LITEHTML=1)
    if(NOT MSVC)
        target_compile_options(twidget-renderer-litehtml PRIVATE -Wall -Wextra)
    endif()
endif()

set(TW_WIDGET_JS "${CMAKE_CURRENT_SOURCE_DIR}/WidgetJS")
set(TW_CORPUS "${TW_ROOT}/../corpus")
set(TW_FIXTURES "${CMAKE_CURRENT_SOURCE_DIR}/tests/fixtures")

add_executable(twidget-run harness/main.cpp)
target_link_libraries(twidget-run PRIVATE twidget-sandbox)
target_compile_definitions(twidget-run PRIVATE TW_WIDGET_JS="${TW_WIDGET_JS}" TW_CORPUS="${TW_CORPUS}")

add_executable(twidget-tests tests/test_main.cpp)
target_link_libraries(twidget-tests PRIVATE twidget-sandbox)
target_compile_definitions(twidget-tests PRIVATE TW_WIDGET_JS="${TW_WIDGET_JS}" TW_CORPUS="${TW_CORPUS}" TW_FIXTURES="${TW_FIXTURES}")

if(TW_BLITZ)
    target_link_libraries(twidget-run PRIVATE twidget-renderer-blitz)
    target_link_libraries(twidget-tests PRIVATE twidget-renderer-blitz)
endif()
if(TW_LITEHTML)
    target_link_libraries(twidget-run PRIVATE twidget-renderer-litehtml)
    target_link_libraries(twidget-tests PRIVATE twidget-renderer-litehtml)
endif()

enable_testing()
add_test(NAME sandbox COMMAND twidget-tests)
