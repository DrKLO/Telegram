# widget-sandbox's stress kit (packages/stress, CLIENTS.md) on TwidgetCore: each widget through the desktop host
# (QuickJS, the validator, WidgetHost) and painted by the Direct2D backend on the GPU every frame, on the frame
# clock a Windows client runs; the run file goes to the kit's report tool.
#   cmake -S tools/stress -B build-stress -G Ninja -DCMAKE_BUILD_TYPE=Release && cmake --build build-stress
#   build-stress/twidget-stress [out dir] --pass measure|production && node <widget-sandbox>/packages/stress/tools/report.mjs --out <dir>/report <dir>/*.run.json
# widget-sandbox is found as twidget's own build finds it: next to twidget, or TWIDGET_WIDGET_SANDBOX.
cmake_minimum_required(VERSION 3.22)
project(twidget-stress C CXX)

if(NOT WIN32)
    message(FATAL_ERROR "the stress runner paints with Direct2D: Windows only")
endif()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(TWIDGET_WIDGET_SANDBOX "$ENV{TWIDGET_WIDGET_SANDBOX}" CACHE PATH "widget-sandbox checkout")
if(NOT TWIDGET_WIDGET_SANDBOX)
    set(TWIDGET_WIDGET_SANDBOX "${CMAKE_CURRENT_SOURCE_DIR}/../../../widget-sandbox" CACHE PATH "widget-sandbox checkout" FORCE)
endif()
set(TWIDGET_BUILD_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../.." twidget)
add_subdirectory("${TWIDGET_WIDGET_SANDBOX}/packages/desktop" widget-sandbox EXCLUDE_FROM_ALL)
target_compile_definitions(twidget-sandbox PRIVATE NOMINMAX)

# The run file names the framework tag and the client's commit.
find_package(Git QUIET)
if(GIT_FOUND)
    execute_process(COMMAND "${GIT_EXECUTABLE}" -C "${TWIDGET_WIDGET_SANDBOX}" describe --tags --always OUTPUT_VARIABLE TW_FRAMEWORK OUTPUT_STRIP_TRAILING_WHITESPACE ERROR_QUIET)
    execute_process(COMMAND "${GIT_EXECUTABLE}" -C "${CMAKE_CURRENT_SOURCE_DIR}" rev-parse --short HEAD OUTPUT_VARIABLE TW_COMMIT OUTPUT_STRIP_TRAILING_WHITESPACE ERROR_QUIET)
endif()

add_executable(twidget-stress main.cpp)
target_link_libraries(twidget-stress PRIVATE twidget::d2d twidget-sandbox d3d11 dxgi windowsapp)
target_compile_definitions(twidget-stress PRIVATE NOMINMAX
    TW_SANDBOX="${TWIDGET_WIDGET_SANDBOX}"
    TW_FRAMEWORK="${TW_FRAMEWORK}"
    TW_COMMIT="${TW_COMMIT}")
target_compile_options(twidget-stress PRIVATE /utf-8 /W3)
