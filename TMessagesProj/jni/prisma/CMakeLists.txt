get_filename_component(PRISMA_JNI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/.." ABSOLUTE)
set(PRISMA_SOURCE_DIR "${PRISMA_JNI_DIR}/third_party/libprisma")
if(NOT EXISTS "${PRISMA_SOURCE_DIR}/libprisma/SyntaxHighlighter.cpp")
    message(FATAL_ERROR "Initialize TMessagesProj/jni/third_party/libprisma")
endif()

set(BOOST_REGEX_SOURCE_DIR "${PRISMA_JNI_DIR}/third_party/boost-regex")
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
foreach(source IN ITEMS Highlight.cpp Highlight.h SyntaxHighlighter.cpp SyntaxHighlighter.h)
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
# BudgetedPosition is twice the size of a pointer, which makes a saved state up to half as big
# again, and Boost's limit is on the blocks they fill: the limit in characters stays where it was.
target_compile_definitions(prisma PRIVATE BOOST_REGEX_STANDALONE NDEBUG BOOST_REGEX_MAX_BLOCKS=1536)
target_include_directories(prisma PUBLIC
    "${PRISMA_CORE_DIR}"
    "${BOOST_REGEX_SOURCE_DIR}/include")
# Highlighting has a time limit, so its speed must not depend on the build type:
# -O0 is 4-5 times slower and -Oz 20-30% slower than -O2.
target_compile_options(prisma PRIVATE
    -O2 -fexceptions -frtti -fvisibility=hidden -fvisibility-inlines-hidden
    -include "${CMAKE_CURRENT_SOURCE_DIR}/bridge/PrismaIncludes.h")
