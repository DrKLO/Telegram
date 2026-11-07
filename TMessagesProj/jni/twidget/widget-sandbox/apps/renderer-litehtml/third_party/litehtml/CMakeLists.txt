cmake_minimum_required(VERSION 3.11)

project(litehtml LANGUAGES C CXX)

option(LITEHTML_BUILD_TESTING "enable testing for litehtml" OFF)
option(LITEHTML_ENABLE_LINT "enable linting for litehtml" ON)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

if (NOT LITEHTML_BUILD_TESTING)
# Soname
# MAJOR is incremented when symbols are removed or changed in an incompatible way
# MINOR is incremented when new symbols are added
set(PROJECT_MAJOR 0)
set(PROJECT_MINOR 0)

option(EXTERNAL_GUMBO "Link against external gumbo instead of shipping a bundled copy" OFF)

if(NOT EXTERNAL_GUMBO)
	add_subdirectory(src/gumbo)
endif()

set(SOURCE_LITEHTML
	src/codepoint.cpp
	src/css_length.cpp
	src/css_selector.cpp
	src/css_tokenizer.cpp
	src/css_parser.cpp
	src/document.cpp
	src/document_container.cpp
	src/el_anchor.cpp
	src/el_base.cpp
	src/el_before_after.cpp
	src/el_body.cpp
	src/el_break.cpp
	src/el_cdata.cpp
	src/el_comment.cpp
	src/el_div.cpp
	src/element.cpp
	src/el_font.cpp
	src/el_image.cpp
	src/el_link.cpp
	src/el_para.cpp
	src/el_script.cpp
	src/el_space.cpp
	src/el_style.cpp
	src/el_table.cpp
	src/el_td.cpp
	src/el_text.cpp
	src/el_title.cpp
	src/el_tr.cpp
	src/encodings.cpp
	src/html.cpp
	src/html_tag.cpp
	src/html_microsyntaxes.cpp
	src/iterators.cpp
	src/media_query.cpp
	src/style.cpp
	src/stylesheet.cpp
	src/table.cpp
	src/url.cpp
	src/url_path.cpp
	src/utf8_strings.cpp
	src/web_color.cpp
	src/num_cvt.cpp
	src/strtod.cpp
	src/string_id.cpp
	src/css_properties.cpp
	src/line_box.cpp
	src/css_borders.cpp
	src/render_item.cpp
	src/render_block_context.cpp
	src/render_block.cpp
	src/render_inline_context.cpp
	src/render_table.cpp
	src/render_flex.cpp
	src/render_image.cpp
	src/formatting_context.cpp
	src/flex_item.cpp
	src/flex_line.cpp
	src/background.cpp
	src/gradient.cpp
)

set(HEADER_LITEHTML
	include/litehtml.h
	include/litehtml/background.h
	include/litehtml/borders.h
	include/litehtml/codepoint.h
	include/litehtml/css_length.h
	include/litehtml/css_margins.h
	include/litehtml/css_offsets.h
	include/litehtml/css_position.h
	include/litehtml/css_selector.h
	include/litehtml/css_parser.h
	include/litehtml/css_tokenizer.h
	include/litehtml/document.h
	include/litehtml/document_container.h
	include/litehtml/el_anchor.h
	include/litehtml/el_base.h
	include/litehtml/el_before_after.h
	include/litehtml/el_body.h
	include/litehtml/el_break.h
	include/litehtml/el_cdata.h
	include/litehtml/el_comment.h
	include/litehtml/el_div.h
	include/litehtml/el_font.h
	include/litehtml/el_image.h
	include/litehtml/el_link.h
	include/litehtml/el_para.h
	include/litehtml/el_script.h
	include/litehtml/el_space.h
	include/litehtml/el_style.h
	include/litehtml/el_table.h
	include/litehtml/el_td.h
	include/litehtml/el_text.h
	include/litehtml/el_title.h
	include/litehtml/el_tr.h
	include/litehtml/element.h
	include/litehtml/encodings.h
	include/litehtml/html.h
	include/litehtml/html_tag.h
	include/litehtml/html_microsyntaxes.h
	include/litehtml/iterators.h
	include/litehtml/media_query.h
	include/litehtml/os_types.h
	include/litehtml/style.h
	include/litehtml/stylesheet.h
	include/litehtml/table.h
	include/litehtml/types.h
	include/litehtml/pixel_type.h
	include/litehtml/url.h
	include/litehtml/url_path.h
	include/litehtml/utf8_strings.h
	include/litehtml/web_color.h
	include/litehtml/num_cvt.h
	include/litehtml/css_properties.h
	include/litehtml/line_box.h
	include/litehtml/render_item.h
	include/litehtml/render_flex.h
	include/litehtml/render_image.h
	include/litehtml/render_inline.h
	include/litehtml/render_table.h
	include/litehtml/render_inline_context.h
	include/litehtml/render_block_context.h
	include/litehtml/render_block.h
	include/litehtml/master_css.h
	include/litehtml/string_id.h
	include/litehtml/formatting_context.h
	include/litehtml/flex_item.h
	include/litehtml/flex_line.h
	include/litehtml/gradient.h
	include/litehtml/font_description.h
	include/litehtml/scroll_view.h
	include/litehtml/css_values.h
)

find_program(CLANG_TIDY_EXE NAMES "clang-tidy")

if(CLANG_TIDY_EXE AND LITEHTML_ENABLE_LINT)
    set(CMAKE_CXX_CLANG_TIDY "${CLANG_TIDY_EXE};-warnings-as-errors=*")
else()
    message(WARNING "Clang-Tidy not found or linting disabled. Static analysis skipped.")
endif()

set(PROJECT_LIB_VERSION ${PROJECT_MAJOR}.${PROJECT_MINOR}.0)
set(PROJECT_SO_VERSION ${PROJECT_MAJOR})

if (MSVC)
	# warning level 4
	add_compile_options(/W4)
	add_compile_options(/permissive- /utf-8)
else()
	# additional warnings
	add_compile_options(-Wall
	                    -Wextra
						-Wpedantic
						-Wfloat-conversion
						-Wnon-virtual-dtor
						-Wold-style-cast
						-Wredundant-move
						-Woverloaded-virtual
						-Wsign-promo
						-Wfloat-equal
						-Wcast-qual
						-Wzero-as-null-pointer-constant
						-Woverloaded-virtual
						-Wredundant-decls
						)
endif()

add_library(${PROJECT_NAME} ${HEADER_LITEHTML} ${SOURCE_LITEHTML})
set_target_properties(${PROJECT_NAME} PROPERTIES
	VERSION ${PROJECT_LIB_VERSION}
	SOVERSION ${PROJECT_SO_VERSION}
	)

set_target_properties(${PROJECT_NAME} PROPERTIES
	CXX_STANDARD 17
	C_STANDARD 99
	PUBLIC_HEADER "${HEADER_LITEHTML}"
)

# Export litehtml includes.
target_include_directories(${PROJECT_NAME} PUBLIC
	$<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/src>
	$<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
	$<INSTALL_INTERFACE:include/${PROJECT_NAME}>)
target_include_directories(${PROJECT_NAME} PRIVATE include/${PROJECT_NAME})

# Gumbo
target_link_libraries(${PROJECT_NAME} PUBLIC gumbo)

# install and export
install(TARGETS ${PROJECT_NAME}
	EXPORT litehtmlTargets
	RUNTIME DESTINATION bin COMPONENT libraries
	ARCHIVE DESTINATION lib${LIB_SUFFIX} COMPONENT libraries
	LIBRARY DESTINATION lib${LIB_SUFFIX} COMPONENT libraries
	PUBLIC_HEADER DESTINATION include/litehtml
)
install(FILES cmake/litehtmlConfig.cmake DESTINATION lib${LIB_SUFFIX}/cmake/litehtml)
install(EXPORT litehtmlTargets FILE litehtmlTargets.cmake DESTINATION lib${LIB_SUFFIX}/cmake/litehtml)

# Tests

else ()
	include(ExternalProject)
	ExternalProject_Add(
			litehtml-tests
			GIT_REPOSITORY      https://github.com/litehtml/litehtml-tests.git
			GIT_TAG             2bb4d22d7d2a0916e4ec69e30d71b39bfe3b7b8a
			SOURCE_DIR          "${CMAKE_BINARY_DIR}/litehtml-tests-src"
			BINARY_DIR          "${CMAKE_BINARY_DIR}/litehtml-tests-build"
			CMAKE_ARGS          -DLITEHTML_PATH=${CMAKE_CURRENT_SOURCE_DIR}
			INSTALL_COMMAND     ""
	)
endif()
