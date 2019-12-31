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
// CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

// check_imported_libraries.go checks that each of its arguments only imports a
// whitelist of allowed libraries. This is used to avoid accidental dependencies
// on libstdc++.so.
package main

import (
	"debug/elf"
	"fmt"
	"os"
)

func checkImportedLibraries(path string) {
	file, err := elf.Open(path)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error opening %s: %s\n", path, err)
		os.Exit(1)
	}
	defer file.Close()

	libs, err := file.ImportedLibraries()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error reading %s: %s\n", path, err)
		os.Exit(1)
	}

	for _, lib := range libs {
		if lib != "libc.so.6" && lib != "libcrypto.so" && lib != "libpthread.so.0" {
			fmt.Printf("Invalid dependency for %s: %s\n", path, lib)
			fmt.Printf("All dependencies:\n")
			for _, lib := range libs {
				fmt.Printf("    %s\n", lib)
			}
			os.Exit(1)
		}
	}
}

func main() {
	for _, path := range os.Args[1:] {
		checkImportedLibraries(path)
	}
}
