# libtwidget_litehtml.so for the Android demo: TwidgetCore with its JNI surface (platform/android/jni.cpp), plus
# widget-sandbox's C++ WidgetHost (QuickJS, the trusted validator, the sandbox engine) and its JNI bridge here.
# twidget is this file's checkout; widget-sandbox sits next to it, or at TWIDGET_WIDGET_SANDBOX.
cmake_minimum_required(VERSION 3.22)
project(twidget-android-demo C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

get_filename_component(TWIDGET_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../.." ABSOLUTE)
set(TWIDGET_WIDGET_SANDBOX "$ENV{TWIDGET_WIDGET_SANDBOX}" CACHE PATH "widget-sandbox checkout")
if(NOT TWIDGET_WIDGET_SANDBOX)
    get_filename_component(sandbox "${TWIDGET_ROOT}/../widget-sandbox" ABSOLUTE)
    set(TWIDGET_WIDGET_SANDBOX "${sandbox}" CACHE PATH "widget-sandbox checkout" FORCE)
endif()
set(TWIDGET_BUILD_TESTS OFF CACHE BOOL "" FORCE)

add_subdirectory("${TWIDGET_ROOT}" twidget)
add_subdirectory("${TWIDGET_WIDGET_SANDBOX}/packages/desktop" widget-sandbox EXCLUDE_FROM_ALL)

target_sources(twidget_litehtml PRIVATE widget_host_jni.cpp)
target_link_libraries(twidget_litehtml PRIVATE twidget-sandbox)
