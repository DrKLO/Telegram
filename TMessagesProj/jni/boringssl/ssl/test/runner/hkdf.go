// Copyright (c) 2016, Google Inc.
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
// CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

package runner

import (
	"crypto/hmac"
	"hash"
)

// hkdfExtract implements HKDF-Extract from RFC 5869.
func hkdfExtract(hash func() hash.Hash, salt, ikm []byte) []byte {
	if salt == nil {
		salt = make([]byte, hash().Size())
	}
	hmac := hmac.New(hash, salt)
	hmac.Write(ikm)
	return hmac.Sum(nil)
}

// hkdfExpand implements HKDF-Expand from RFC 5869.
func hkdfExpand(hash func() hash.Hash, prk, info []byte, length int) []byte {
	hashSize := hash().Size()
	if length > 255*hashSize {
		panic("hkdfExpand: length too long")
	}
	if len(prk) < hashSize {
		panic("hkdfExpand: prk too short")
	}
	var lastBlock []byte
	counter := byte(0)
	okm := make([]byte, length)
	hmac := hmac.New(hash, prk)
	for length > 0 {
		hmac.Reset()
		counter++
		hmac.Write(lastBlock)
		hmac.Write(info)
		hmac.Write([]byte{counter})
		block := hmac.Sum(nil)
		lastBlock = block
		copy(okm[(int(counter)-1)*hashSize:], block)
		length -= hashSize
	}
	return okm
}
