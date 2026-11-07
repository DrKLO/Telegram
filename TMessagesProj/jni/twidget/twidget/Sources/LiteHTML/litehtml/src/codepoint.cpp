// Copyright (C) 2020-2021 Primate Labs Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//    * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//    * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//    * Neither the names of the copyright holders nor the names of their
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

#include "codepoint.h"

#include <cstdint>

namespace
{

    bool lookup(const uint32_t* table, char c)
    {
        return table[c >> 5] & (1 << (c & 0x1f));
    }

} // namespace

namespace litehtml
{

    bool is_ascii_codepoint(char c)
    {
        return (static_cast<unsigned char>(c) < 128);
    }

    // https://datatracker.ietf.org/doc/html/rfc3986#section-2.2
    bool is_url_reserved_codepoint(char c)
    {
        static const uint32_t reserved_lookup[] = {0x00000000, 0xac009fda, 0x28000001, 0x00000000};

        if(!is_ascii_codepoint(c))
        {
            return false;
        }
        return lookup(reserved_lookup, c);
    }

    // https://datatracker.ietf.org/doc/html/rfc3986#section-3.1
    bool is_url_scheme_codepoint(char c)
    {
        static const uint32_t scheme_lookup[] = {
            0x00000000,
            0x03ff6800,
            0x07fffffe,
            0x07fffffe,
        };

        if(!is_ascii_codepoint(c))
        {
            return false;
        }
        return lookup(scheme_lookup, c);
    }

} // namespace litehtml
