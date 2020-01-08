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
	"bytes"
	"testing"
)

// See RFC 7539, section 2.1.1.
func TestChaChaQuarterRound(t *testing.T) {
	state := [16]uint32{0x11111111, 0x01020304, 0x9b8d6f43, 0x01234567}
	chaChaQuarterRound(&state, 0, 1, 2, 3)

	a, b, c, d := state[0], state[1], state[2], state[3]
	if a != 0xea2a92f4 || b != 0xcb1cf8ce || c != 0x4581472e || d != 0x5881c4bb {
		t.Errorf("Incorrect results: %x", state)
	}
}

// See RFC 7539, section 2.2.1.
func TestChaChaQuarterRoundState(t *testing.T) {
	state := [16]uint32{
		0x879531e0, 0xc5ecf37d, 0x516461b1, 0xc9a62f8a,
		0x44c20ef3, 0x3390af7f, 0xd9fc690b, 0x2a5f714c,
		0x53372767, 0xb00a5631, 0x974c541a, 0x359e9963,
		0x5c971061, 0x3d631689, 0x2098d9d6, 0x91dbd320,
	}
	chaChaQuarterRound(&state, 2, 7, 8, 13)

	expected := [16]uint32{
		0x879531e0, 0xc5ecf37d, 0xbdb886dc, 0xc9a62f8a,
		0x44c20ef3, 0x3390af7f, 0xd9fc690b, 0xcfacafd2,
		0xe46bea80, 0xb00a5631, 0x974c541a, 0x359e9963,
		0x5c971061, 0xccc07c79, 0x2098d9d6, 0x91dbd320,
	}
	for i := range state {
		if state[i] != expected[i] {
			t.Errorf("Mismatch at %d: %x vs %x", i, state, expected)
		}
	}
}

// See RFC 7539, section 2.3.2.
func TestChaCha20Block(t *testing.T) {
	state := [16]uint32{
		0x61707865, 0x3320646e, 0x79622d32, 0x6b206574,
		0x03020100, 0x07060504, 0x0b0a0908, 0x0f0e0d0c,
		0x13121110, 0x17161514, 0x1b1a1918, 0x1f1e1d1c,
		0x00000001, 0x09000000, 0x4a000000, 0x00000000,
	}
	out := make([]byte, 64)
	chaCha20Block(&state, out)

	expected := []byte{
		0x10, 0xf1, 0xe7, 0xe4, 0xd1, 0x3b, 0x59, 0x15,
		0x50, 0x0f, 0xdd, 0x1f, 0xa3, 0x20, 0x71, 0xc4,
		0xc7, 0xd1, 0xf4, 0xc7, 0x33, 0xc0, 0x68, 0x03,
		0x04, 0x22, 0xaa, 0x9a, 0xc3, 0xd4, 0x6c, 0x4e,
		0xd2, 0x82, 0x64, 0x46, 0x07, 0x9f, 0xaa, 0x09,
		0x14, 0xc2, 0xd7, 0x05, 0xd9, 0x8b, 0x02, 0xa2,
		0xb5, 0x12, 0x9c, 0xd1, 0xde, 0x16, 0x4e, 0xb9,
		0xcb, 0xd0, 0x83, 0xe8, 0xa2, 0x50, 0x3c, 0x4e,
	}
	if !bytes.Equal(out, expected) {
		t.Errorf("Got %x, wanted %x", out, expected)
	}
}

var chaCha20Poly1305TestVectors = []struct {
	key, input, nonce, ad, output string
}{
	{
		// See RFC 7539, section 2.8.2.
		key:    "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f",
		input:  "4c616469657320616e642047656e746c656d656e206f662074686520636c617373206f66202739393a204966204920636f756c64206f6666657220796f75206f6e6c79206f6e652074697020666f7220746865206675747572652c2073756e73637265656e20776f756c642062652069742e",
		nonce:  "070000004041424344454647",
		ad:     "50515253c0c1c2c3c4c5c6c7",
		output: "d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d63dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b3692ddbd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc3ff4def08e4b7a9de576d26586cec64b61161ae10b594f09e26a7e902ecbd0600691",
	},
	{
		// See RFC 7539, section A.5.
		key:    "1c9240a5eb55d38af333888604f6b5f0473917c1402b80099dca5cbc207075c0",
		input:  "496e7465726e65742d4472616674732061726520647261667420646f63756d656e74732076616c696420666f722061206d6178696d756d206f6620736978206d6f6e74687320616e64206d617920626520757064617465642c207265706c616365642c206f72206f62736f6c65746564206279206f7468657220646f63756d656e747320617420616e792074696d652e20497420697320696e617070726f70726961746520746f2075736520496e7465726e65742d447261667473206173207265666572656e6365206d6174657269616c206f7220746f2063697465207468656d206f74686572207468616e206173202fe2809c776f726b20696e2070726f67726573732e2fe2809d",
		nonce:  "000000000102030405060708",
		ad:     "f33388860000000000004e91",
		output: "64a0861575861af460f062c79be643bd5e805cfd345cf389f108670ac76c8cb24c6cfc18755d43eea09ee94e382d26b0bdb7b73c321b0100d4f03b7f355894cf332f830e710b97ce98c8a84abd0b948114ad176e008d33bd60f982b1ff37c8559797a06ef4f0ef61c186324e2b3506383606907b6a7c02b0f9f6157b53c867e4b9166c767b804d46a59b5216cde7a4e99040c5a40433225ee282a1b0a06c523eaf4534d7f83fa1155b0047718cbc546a0d072b04b3564eea1b422273f548271a0bb2316053fa76991955ebd63159434ecebb4e466dae5a1073a6727627097a1049e617d91d361094fa68f0ff77987130305beaba2eda04df997b714d6c6f2c29a6ad5cb4022b02709beead9d67890cbb22392336fea1851f38",
	},
}

// See draft-agl-tls-chacha20poly1305-04, section 7.
func TestChaCha20Poly1305(t *testing.T) {
	for i, tt := range chaCha20Poly1305TestVectors {
		key := decodeHexOrPanic(tt.key)
		input := decodeHexOrPanic(tt.input)
		nonce := decodeHexOrPanic(tt.nonce)
		ad := decodeHexOrPanic(tt.ad)
		output := decodeHexOrPanic(tt.output)

		aead, err := newChaCha20Poly1305(key)
		if err != nil {
			t.Fatal(err)
		}

		out, err := aead.Open(nil, nonce, output, ad)
		if err != nil {
			t.Errorf("%d. Open failed: %s", i, err)
		} else if !bytes.Equal(out, input) {
			t.Errorf("%d. Open gave %x, wanted %x", i, out, input)
		}

		out = aead.Seal(nil, nonce, input, ad)
		if !bytes.Equal(out, output) {
			t.Errorf("%d. Open gave %x, wanted %x", i, out, output)
		}

		out[0]++
		_, err = aead.Open(nil, nonce, out, ad)
		if err == nil {
			t.Errorf("%d. Open on malformed data unexpectedly succeeded", i)
		}
	}
}
