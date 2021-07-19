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

package main

import (
	"bytes"
	"flag"
	"io/ioutil"
	"path/filepath"
	"testing"
)

var (
	testDataDir = flag.String("testdata", "testdata", "The path to the test data directory.")
	update      = flag.Bool("update", false, "If true, update output files rather than compare them.")
)

type delocateTest struct {
	name string
	in   []string
	out  string
}

func (test *delocateTest) Path(file string) string {
	return filepath.Join(*testDataDir, test.name, file)
}

var delocateTests = []delocateTest{
	{"generic-FileDirectives", []string{"in.s"}, "out.s"},
	{"ppc64le-GlobalEntry", []string{"in.s"}, "out.s"},
	{"ppc64le-LoadToR0", []string{"in.s"}, "out.s"},
	{"ppc64le-Sample2", []string{"in.s"}, "out.s"},
	{"ppc64le-Sample", []string{"in.s"}, "out.s"},
	{"ppc64le-TOCWithOffset", []string{"in.s"}, "out.s"},
	{"x86_64-Basic", []string{"in.s"}, "out.s"},
	{"x86_64-BSS", []string{"in.s"}, "out.s"},
	{"x86_64-GOTRewrite", []string{"in.s"}, "out.s"},
	{"x86_64-LabelRewrite", []string{"in1.s", "in2.s"}, "out.s"},
	{"x86_64-Sections", []string{"in.s"}, "out.s"},
	{"x86_64-ThreeArg", []string{"in.s"}, "out.s"},
}

func TestDelocate(t *testing.T) {
	for _, test := range delocateTests {
		t.Run(test.name, func(t *testing.T) {
			var inputs []inputFile
			for i, in := range test.in {
				inputs = append(inputs, inputFile{
					index: i,
					path:  test.Path(in),
				})
			}

			if err := parseInputs(inputs); err != nil {
				t.Fatalf("parseInputs failed: %s", err)
			}

			var buf bytes.Buffer
			if err := transform(&buf, inputs); err != nil {
				t.Fatalf("transform failed: %s", err)
			}

			if *update {
				ioutil.WriteFile(test.Path(test.out), buf.Bytes(), 0666)
			} else {
				expected, err := ioutil.ReadFile(test.Path(test.out))
				if err != nil {
					t.Fatalf("could not read %q: %s", test.Path(test.out), err)
				}
				if !bytes.Equal(buf.Bytes(), expected) {
					t.Errorf("delocated output differed. Wanted:\n%s\nGot:\n%s\n", expected, buf.Bytes())
				}
			}
		})
	}
}
