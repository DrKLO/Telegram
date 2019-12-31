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
// CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

// check_filenames.go checks that filenames are unique. Some of our consumers do
// not support multiple files with the same name in the same build target, even
// if they are in different directories.
package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

func isSourceFile(in string) bool {
	return strings.HasSuffix(in, ".c") || strings.HasSuffix(in, ".cc")
}

func main() {
	var roots = []string{
		"crypto",
		filepath.Join("third_party", "fiat"),
		"ssl",
	}

	names := make(map[string]string)
	var foundCollisions bool
	for _, root := range roots {
		err := filepath.Walk(root, func(path string, info os.FileInfo, err error) error {
			if err != nil {
				return err
			}
			if info.IsDir() {
				return nil
			}
			name := strings.ToLower(info.Name()) // Windows and macOS are case-insensitive.
			if isSourceFile(name) {
				if oldPath, ok := names[name]; ok {
					fmt.Printf("Filename collision found: %s and %s\n", path, oldPath)
					foundCollisions = true
				} else {
					names[name] = path
				}
			}
			return nil
		})
		if err != nil {
			fmt.Printf("Error traversing %s: %s\n", root, err)
			os.Exit(1)
		}
	}
	if foundCollisions {
		os.Exit(1)
	}
}
