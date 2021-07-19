/* Copyright (c) 2016, Google Inc.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE. */

package main

import (
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
)

var (
	boringsslDir = flag.String("boringssl", ".", "The path to the BoringSSL checkout.")
	opensslDir   = flag.String("openssl", filepath.Join("..", "openssl"), "The path to the OpenSSL checkout.")
)

func mapName(path string) string {
	path = strings.Replace(path, filepath.FromSlash("/fipsmodule/"), string(filepath.Separator), 1)
	switch filepath.ToSlash(path) {
	case "crypto/cipher_extra/asm/aes128gcmsiv-x86_64.pl", "crypto/cipher_extra/asm/chacha20_poly1305_x86_64.pl", "crypto/rand/asm/rdrand-x86_64.pl":
		return ""
	case "crypto/ec/asm/p256-x86_64-asm.pl":
		return filepath.FromSlash("crypto/ec/asm/ecp_nistz256-x86_64.pl")
	}
	return path
}

func diff(from, to string) error {
	cmd := exec.Command("diff", "-u", "--", from, to)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	err := cmd.Run()
	// diff returns exit code 1 if the files differ but it was otherwise
	// successful.
	if exitError, ok := err.(*exec.ExitError); ok && exitError.Sys().(syscall.WaitStatus).ExitStatus() == 1 {
		return nil
	}
	return err
}

func main() {
	flag.Usage = func() {
		fmt.Fprintf(os.Stderr, "Usage: diff_asm [flag...] [filter...]\n")
		fmt.Fprintf(os.Stderr, "Filter arguments limit to assembly files which match arguments.\n")
		fmt.Fprintf(os.Stderr, "If not using a filter, piping to `diffstat` may be useful.\n\n")
		flag.PrintDefaults()
	}
	flag.Parse()

	// Find all the assembly files.
	var files []string
	err := filepath.Walk(*boringsslDir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil
		}

		path, err = filepath.Rel(*boringsslDir, path)
		if err != nil {
			return err
		}

		dir := filepath.Base(filepath.Dir(path))
		if !info.IsDir() && (dir == "asm" || dir == "perlasm") && strings.HasSuffix(filepath.Base(path), ".pl") {
			files = append(files, path)
		}

		return nil
	})
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error finding assembly: %s\n", err)
		os.Exit(1)
	}

	for _, file := range files {
		opensslFile := mapName(file)
		if len(opensslFile) == 0 {
			continue
		}

		if flag.NArg() > 0 {
			var found bool
			for _, arg := range flag.Args() {
				if strings.Contains(file, arg) {
					found = true
					break
				}
			}
			if !found {
				continue
			}
		}

		if err := diff(filepath.Join(*opensslDir, opensslFile), filepath.Join(*boringsslDir, file)); err != nil {
			fmt.Fprintf(os.Stderr, "Error comparing %s: %s\n", file, err)
			os.Exit(1)
		}
	}
}
