#pragma once

// Upstream uses these declarations through transitive Boost includes. Make them
// explicit when compiling against the standalone Boost.Regex distribution.
#include <cassert>
#include <cstdint>
#include <cstring>
#include <iterator>
#include <memory>
#include <string_view>
