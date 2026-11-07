# The litehtml renderer core at the repo root, built for Android: its own
# CMakeLists detects ANDROID and produces libtwidget_litehtml.so (JNI glue
# included). One core, one set of sources on every platform.
cmake_minimum_required(VERSION 3.22)
project(twidget-litehtml-android C CXX)

set(CORE "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../renderer-litehtml")
if(NOT EXISTS "${CORE}/CMakeLists.txt")
    message(FATAL_ERROR "renderer-litehtml not found at ${CORE}; apps/android must sit next to it")
endif()
add_subdirectory("${CORE}" twidget-litehtml)
