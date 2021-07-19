// Copyright (c) 2017, Google Inc.
//
// Permission to use, copy, modify, and/or distribute this software for any
// purpose with or without fee is hereby granted, provided that the above
// copyright notice and this permission notice appear in all copies.
//
// THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
// WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
// SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
// WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
// OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
// CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

package fipscommon

// UninitHashValue is the default hash value that we inject into the module.
// This value need only be distinct, i.e. so that we can safely
// search-and-replace it in an object file.
var UninitHashValue = [64]byte{
	0xae, 0x2c, 0xea, 0x2a, 0xbd, 0xa6, 0xf3, 0xec, 0x97, 0x7f, 0x9b, 0xf6, 0x94, 0x9a, 0xfc, 0x83, 0x68, 0x27, 0xcb, 0xa0, 0xa0, 0x9f, 0x6b, 0x6f, 0xde, 0x52, 0xcd, 0xe2, 0xcd, 0xff, 0x31, 0x80, 0xa2, 0xd4, 0xc3, 0x66, 0x0f, 0xc2, 0x6a, 0x7b, 0xf4, 0xbe, 0x39, 0xa2, 0xd7, 0x25, 0xdb, 0x21, 0x98, 0xe9, 0xd5, 0x53, 0xbf, 0x5c, 0x32, 0x06, 0x83, 0x34, 0x0c, 0x65, 0x89, 0x52, 0xbd, 0x1f,
}
