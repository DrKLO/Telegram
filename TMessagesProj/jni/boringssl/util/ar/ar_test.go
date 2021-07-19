// Copyright (c) 2018, Google Inc.
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

package ar

import (
	"bytes"
	"flag"
	"io/ioutil"
	"os"
	"path/filepath"
	"testing"
)

var testDataDir = flag.String("testdata", "testdata", "The path to the test data directory.")

type arTest struct {
	name string
	in   string
	out  map[string]string
	// allowPadding is true if the contents may have trailing newlines at end.
	// On macOS, ar calls ranlib which pads all inputs up to eight bytes with
	// newlines. Unlike ar's native padding up to two bytes, this padding is
	// included in the size field, so it is not removed when decoding.
	allowPadding bool
}

func (test *arTest) Path(file string) string {
	return filepath.Join(*testDataDir, test.name, file)
}

func removeTrailingNewlines(in []byte) []byte {
	for len(in) > 0 && in[len(in)-1] == '\n' {
		in = in[:len(in)-1]
	}
	return in
}

var arTests = []arTest{
	{
		"linux",
		"libsample.a",
		map[string]string{
			"foo.c.o": "foo.c.o",
			"bar.cc.o": "bar.cc.o",
		},
		false,
	},
	{
		"mac",
		"libsample.a",
		map[string]string{
			"foo.c.o": "foo.c.o",
			"bar.cc.o": "bar.cc.o",
		},
		true,
	},
	{
		"windows",
		"sample.lib",
		map[string]string{
			"CMakeFiles\\sample.dir\\foo.c.obj": "foo.c.obj",
			"CMakeFiles\\sample.dir\\bar.cc.obj": "bar.cc.obj",
		},
		false,
	},
}

func TestAR(t *testing.T) {
	for _, test := range arTests {
		t.Run(test.name, func(t *testing.T) {
			in, err := os.Open(test.Path(test.in))
			if err != nil {
				t.Fatalf("opening input failed: %s", err)
			}
			defer in.Close()

			ret, err := ParseAR(in)
			if err != nil {
				t.Fatalf("reading input failed: %s", err)
			}

			for file, contentsPath := range test.out {
				expected, err := ioutil.ReadFile(test.Path(contentsPath))
				if err != nil {
					t.Fatalf("error reading %s: %s", contentsPath, err)
				}
				got, ok := ret[file]
				if test.allowPadding {
					got = removeTrailingNewlines(got)
					expected = removeTrailingNewlines(got)
				}
				if !ok {
					t.Errorf("file %s missing from output", file)
				} else if !bytes.Equal(got, expected) {
					t.Errorf("contents for file %s did not match", file)
				}
			}

			for file, _ := range ret {
				if _, ok := test.out[file]; !ok {
					t.Errorf("output contained unexpected file %q", file)
				}
			}
		})
	}
}
