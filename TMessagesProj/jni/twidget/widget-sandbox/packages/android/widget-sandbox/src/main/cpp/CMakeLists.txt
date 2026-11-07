# QuickJS from the TelegramMessenger/quickjs fork, the git submodule at
# packages/ios/Sources/CQuickJS/quickjs (`git submodule update --init`), built for
# Android with the same qjs_shim.c plus a JNI layer written against the shim.
# One engine, one shim, one set of sources on every platform.
cmake_minimum_required(VERSION 3.22)
project(twquickjs C)

set(QJS "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../ios/Sources/CQuickJS")
if(NOT EXISTS "${QJS}/quickjs/quickjs.c")
    message(FATAL_ERROR "QuickJS sources not found at ${QJS}/quickjs: run git submodule update --init (packages/android must sit next to packages/ios)")
endif()

add_library(twquickjs SHARED
    "${QJS}/quickjs/quickjs.c"
    "${QJS}/quickjs/dtoa.c"
    "${QJS}/quickjs/libregexp.c"
    "${QJS}/quickjs/libunicode.c"
    "${QJS}/shim/qjs_shim.c"
    quickjs_jni.c)

target_include_directories(twquickjs PRIVATE "${QJS}/include" "${QJS}/quickjs")
target_compile_definitions(twquickjs PRIVATE _GNU_SOURCE QUICKJS_NG_BUILD)
# Always optimise the interpreter, also in debug builds: an -O0 quickjs-ng spends
# ~10 KB of C stack per JS call (the iOS lesson) and is several times slower.
target_compile_options(twquickjs PRIVATE -O2 -fvisibility=hidden -fno-strict-aliasing -Wno-unused-parameter)
target_link_options(twquickjs PRIVATE -Wl,--gc-sections)
target_link_libraries(twquickjs PRIVATE log m)
