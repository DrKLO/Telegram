# The Qt desktop demo: widget-sandbox's corpus through its desktop host (QuickJS, the validator, WidgetHost)
# and TwidgetCore on the QPainter backend.
#   cmake -S tools/qt-demo -B build-qt -G Ninja -DCMAKE_PREFIX_PATH=<Qt 6>/<compiler> && cmake --build build-qt
# widget-sandbox is found as twidget's own build finds it: next to twidget, or TWIDGET_WIDGET_SANDBOX.
cmake_minimum_required(VERSION 3.22)
project(twidget-qt-demo C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(TWIDGET_WIDGET_SANDBOX "$ENV{TWIDGET_WIDGET_SANDBOX}" CACHE PATH "widget-sandbox checkout")
if(NOT TWIDGET_WIDGET_SANDBOX)
    set(TWIDGET_WIDGET_SANDBOX "${CMAKE_CURRENT_SOURCE_DIR}/../../../widget-sandbox" CACHE PATH "widget-sandbox checkout" FORCE)
endif()
set(TWIDGET_QT ON CACHE BOOL "" FORCE)
set(TWIDGET_BUILD_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../.." twidget)
add_subdirectory("${TWIDGET_WIDGET_SANDBOX}/packages/desktop" widget-sandbox EXCLUDE_FROM_ALL)
if(WIN32)
    # Its sources include windows.h and call std::min / std::max.
    target_compile_definitions(twidget-sandbox PRIVATE NOMINMAX)
endif()

find_package(Qt6 REQUIRED COMPONENTS Widgets)

add_executable(twidget-qt-demo WIN32 main.cpp widget_view.cpp qt_renderer.cpp)
target_link_libraries(twidget-qt-demo PRIVATE twidget::qt twidget-sandbox Qt6::Widgets)
target_compile_definitions(twidget-qt-demo PRIVATE
    TW_WIDGET_JS="${TWIDGET_WIDGET_SANDBOX}/packages/desktop/WidgetJS"
    TW_CORPUS="${TWIDGET_WIDGET_SANDBOX}/corpus")
if(MSVC)
    target_compile_options(twidget-qt-demo PRIVATE /utf-8 /W3)
    target_compile_definitions(twidget-qt-demo PRIVATE NOMINMAX)
endif()

# Windows looks for Qt's DLLs next to the executable.
if(WIN32)
    find_program(TW_WINDEPLOYQT windeployqt HINTS "${Qt6_DIR}/../../../bin")
    if(TW_WINDEPLOYQT)
        add_custom_command(TARGET twidget-qt-demo POST_BUILD
            COMMAND "${TW_WINDEPLOYQT}" --no-translations --no-compiler-runtime --no-opengl-sw "$<TARGET_FILE:twidget-qt-demo>"
            VERBATIM)
    endif()
endif()
