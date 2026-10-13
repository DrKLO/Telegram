cmake_minimum_required(VERSION 3.16)
project(prisma_prebuild LANGUAGES CXX)

get_filename_component(PRISMA_JNI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/.." ABSOLUTE)
set(PRISMA_SOURCE_DIR "${PRISMA_JNI_DIR}/third_party/libprisma" CACHE PATH
    "Pinned TelegramMessenger/libprisma submodule")
if(NOT EXISTS "${PRISMA_SOURCE_DIR}/libprisma/SyntaxHighlighter.cpp")
    message(FATAL_ERROR "Initialize TMessagesProj/jni/third_party/libprisma")
endif()

set(BOOST_REGEX_SOURCE_DIR "${PRISMA_JNI_DIR}/third_party/boost-regex" CACHE PATH
    "Pinned Boost.Regex submodule")
if(NOT EXISTS "${BOOST_REGEX_SOURCE_DIR}/include/boost/regex.hpp")
    message(FATAL_ERROR "Initialize TMessagesProj/jni/third_party/boost-regex")
endif()

# Build against full source overrides without changing the submodule checkout.
set(PRISMA_CORE_DIR "${CMAKE_CURRENT_BINARY_DIR}/source")
file(MAKE_DIRECTORY "${PRISMA_CORE_DIR}")
foreach(source IN ITEMS Highlight.cpp Highlight.h LanguageTree.cpp LanguageTree.h
        SyntaxHighlighter.cpp SyntaxHighlighter.h TokenList.cpp TokenList.h UnicodeEscapes.h)
    configure_file("${PRISMA_SOURCE_DIR}/libprisma/${source}"
        "${PRISMA_CORE_DIR}/${source}" COPYONLY)
endforeach()
foreach(source IN ITEMS Highlight.cpp Highlight.h SyntaxHighlighter.cpp)
    configure_file("${CMAKE_CURRENT_SOURCE_DIR}/libprisma/${source}"
        "${PRISMA_CORE_DIR}/${source}" COPYONLY)
endforeach()

add_library(prisma STATIC
    "${PRISMA_CORE_DIR}/Highlight.cpp"
    "${PRISMA_CORE_DIR}/LanguageTree.cpp"
    "${PRISMA_CORE_DIR}/SyntaxHighlighter.cpp"
    "${PRISMA_CORE_DIR}/TokenList.cpp")
set_target_properties(prisma PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_compile_features(prisma PUBLIC cxx_std_17)
target_compile_definitions(prisma PRIVATE BOOST_REGEX_STANDALONE)
target_include_directories(prisma PUBLIC
    "${PRISMA_CORE_DIR}"
    "${BOOST_REGEX_SOURCE_DIR}/include")
target_compile_options(prisma PRIVATE
    -fexceptions -frtti -fvisibility=hidden -fvisibility-inlines-hidden
    -include "${CMAKE_CURRENT_SOURCE_DIR}/bridge/PrismaIncludes.h")
install(TARGETS prisma ARCHIVE DESTINATION lib)
