#pragma once

#include <string>

// Keep the two generated UniFFI headers in separate translation units: the
// upstream C++ generator repeats its C scaffolding types in each header.
namespace gram_adapter {
std::string ton_connect_crypto(const std::string &phrase, const std::string &parameters);
bool is_valid_cell_boc(const std::string &boc);
std::string message_body_hash(const std::string &boc);
}
