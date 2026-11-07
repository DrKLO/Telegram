# The macOS demo: widget-sandbox's corpus through its desktop host (QuickJS, the validator, WidgetHost)
# and TwidgetCore on CoreGraphics, with the compositor's layers on Core Animation.
#   cmake -S tools/macos-demo -B build-macos -G Ninja && cmake --build build-macos
#   open build-macos/twidget-macos-demo.app        (or -G Xcode to work on it in Xcode)
# widget-sandbox is found as twidget's own build finds it: next to twidget, or TWIDGET_WIDGET_SANDBOX.
cmake_minimum_required(VERSION 3.22)
set(CMAKE_OSX_DEPLOYMENT_TARGET 13.3 CACHE STRING "")
project(twidget-macos-demo C CXX OBJCXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_OBJCXX_STANDARD 20)

set(TWIDGET_WIDGET_SANDBOX "$ENV{TWIDGET_WIDGET_SANDBOX}" CACHE PATH "widget-sandbox checkout")
if(NOT TWIDGET_WIDGET_SANDBOX)
    set(TWIDGET_WIDGET_SANDBOX "${CMAKE_CURRENT_SOURCE_DIR}/../../../widget-sandbox" CACHE PATH "widget-sandbox checkout" FORCE)
endif()
set(TWIDGET_BUILD_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../.." twidget)
add_subdirectory("${TWIDGET_WIDGET_SANDBOX}/packages/desktop" widget-sandbox EXCLUDE_FROM_ALL)

add_executable(twidget-macos-demo MACOSX_BUNDLE main.mm widget_view.mm mac_renderer.cpp)
target_link_libraries(twidget-macos-demo PRIVATE twidget_litehtml twidget-sandbox "-framework AppKit" "-framework QuartzCore")
target_compile_definitions(twidget-macos-demo PRIVATE
    TW_WIDGET_JS="${TWIDGET_WIDGET_SANDBOX}/packages/desktop/WidgetJS"
    TW_CORPUS="${TWIDGET_WIDGET_SANDBOX}/corpus")
target_compile_options(twidget-macos-demo PRIVATE -Wall -Wextra -Wno-unused-parameter $<$<COMPILE_LANGUAGE:OBJCXX>:-fobjc-arc>)
set_target_properties(twidget-macos-demo PROPERTIES
    MACOSX_BUNDLE_GUI_IDENTIFIER org.telegram.twidget.macos-demo
    MACOSX_BUNDLE_BUNDLE_NAME "twidget demo")
