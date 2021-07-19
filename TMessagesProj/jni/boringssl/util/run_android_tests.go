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

package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"io/ioutil"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
)

var (
	buildDir     = flag.String("build-dir", "build", "Specifies the build directory to push.")
	adbPath      = flag.String("adb", "adb", "Specifies the adb binary to use. Defaults to looking in PATH.")
	device       = flag.String("device", "", "Specifies the device or emulator. See adb's -s argument.")
	aarch64      = flag.Bool("aarch64", false, "Build the test runners for aarch64 instead of arm.")
	arm          = flag.Int("arm", 7, "Which arm revision to build for.")
	suite        = flag.String("suite", "all", "Specifies the test suites to run (all, unit, or ssl).")
	allTestsArgs = flag.String("all-tests-args", "", "Specifies space-separated arguments to pass to all_tests.go")
	runnerArgs   = flag.String("runner-args", "", "Specifies space-separated arguments to pass to ssl/test/runner")
	jsonOutput   = flag.String("json-output", "", "The file to output JSON results to.")
)

func enableUnitTests() bool {
	return *suite == "all" || *suite == "unit"
}

func enableSSLTests() bool {
	return *suite == "all" || *suite == "ssl"
}

func adb(args ...string) error {
	if len(*device) > 0 {
		args = append([]string{"-s", *device}, args...)
	}
	cmd := exec.Command(*adbPath, args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}

func adbShell(shellCmd string) (int, error) {
	var args []string
	if len(*device) > 0 {
		args = append([]string{"-s", *device}, args...)
	}
	args = append(args, "shell")

	const delimiter = "___EXIT_CODE___"

	// Older versions of adb and Android do not preserve the exit
	// code, so work around this.
	// https://code.google.com/p/android/issues/detail?id=3254
	shellCmd += "; echo " + delimiter + " $?"
	args = append(args, shellCmd)

	cmd := exec.Command(*adbPath, args...)
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return 0, err
	}
	cmd.Stderr = os.Stderr
	if err := cmd.Start(); err != nil {
		return 0, err
	}

	var stdoutBytes bytes.Buffer
	for {
		var buf [1024]byte
		n, err := stdout.Read(buf[:])
		stdoutBytes.Write(buf[:n])
		os.Stdout.Write(buf[:n])
		if err != nil {
			break
		}
	}

	if err := cmd.Wait(); err != nil {
		return 0, err
	}

	stdoutStr := stdoutBytes.String()
	idx := strings.LastIndex(stdoutStr, delimiter)
	if idx < 0 {
		return 0, fmt.Errorf("Could not find delimiter in output.")
	}

	return strconv.Atoi(strings.TrimSpace(stdoutStr[idx+len(delimiter):]))
}

func goTool(args ...string) error {
	cmd := exec.Command("go", args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr

	cmd.Env = os.Environ()
	if *aarch64 {
		cmd.Env = append(cmd.Env, "GOARCH=arm64")
	} else {
		cmd.Env = append(cmd.Env, "GOARCH=arm")
		cmd.Env = append(cmd.Env, fmt.Sprintf("GOARM=%d", *arm))
	}
	return cmd.Run()
}

// setWorkingDirectory walks up directories as needed until the current working
// directory is the top of a BoringSSL checkout.
func setWorkingDirectory() {
	for i := 0; i < 64; i++ {
		if _, err := os.Stat("BUILDING.md"); err == nil {
			return
		}
		os.Chdir("..")
	}

	panic("Couldn't find BUILDING.md in a parent directory!")
}

type test []string

func parseTestConfig(filename string) ([]test, error) {
	in, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer in.Close()

	decoder := json.NewDecoder(in)
	var result []test
	if err := decoder.Decode(&result); err != nil {
		return nil, err
	}
	return result, nil
}

func copyFile(dst, src string) error {
	srcFile, err := os.Open(src)
	if err != nil {
		return err
	}
	defer srcFile.Close()

	srcInfo, err := srcFile.Stat()
	if err != nil {
		return err
	}

	dir := filepath.Dir(dst)
	if err := os.MkdirAll(dir, 0777); err != nil {
		return err
	}

	dstFile, err := os.OpenFile(dst, os.O_CREATE|os.O_WRONLY, srcInfo.Mode())
	if err != nil {
		return err
	}
	defer dstFile.Close()

	_, err = io.Copy(dstFile, srcFile)
	return err
}

func main() {
	flag.Parse()

	if *suite == "all" && *jsonOutput != "" {
		fmt.Printf("To use -json-output flag, select only one test suite with -suite.\n")
		os.Exit(1)
	}

	setWorkingDirectory()

	// Clear the target directory.
	if err := adb("shell", "rm -Rf /data/local/tmp/boringssl-tmp"); err != nil {
		fmt.Printf("Failed to clear target directory: %s\n", err)
		os.Exit(1)
	}

	// Stage everything in a temporary directory.
	tmpDir, err := ioutil.TempDir("", "boringssl-android")
	if err != nil {
		fmt.Printf("Error making temporary directory: %s\n", err)
		os.Exit(1)
	}
	defer os.RemoveAll(tmpDir)

	var binaries, files []string

	if enableUnitTests() {
		files = append(files,
			"util/all_tests.json",
			"BUILDING.md",
		)

		tests, err := parseTestConfig("util/all_tests.json")
		if err != nil {
			fmt.Printf("Failed to parse input: %s\n", err)
			os.Exit(1)
		}

		seenBinary := make(map[string]struct{})
		for _, test := range tests {
			if _, ok := seenBinary[test[0]]; !ok {
				binaries = append(binaries, test[0])
				seenBinary[test[0]] = struct{}{}
			}
			for _, arg := range test[1:] {
				if strings.Contains(arg, "/") {
					files = append(files, arg)
				}
			}
		}

		fmt.Printf("Building all_tests...\n")
		if err := goTool("build", "-o", filepath.Join(tmpDir, "util/all_tests"), "util/all_tests.go"); err != nil {
			fmt.Printf("Error building all_tests.go: %s\n", err)
			os.Exit(1)
		}
	}

	if enableSSLTests() {
		binaries = append(binaries, "ssl/test/bssl_shim")
		files = append(files,
			"BUILDING.md",
			"ssl/test/runner/cert.pem",
			"ssl/test/runner/channel_id_key.pem",
			"ssl/test/runner/ecdsa_p224_cert.pem",
			"ssl/test/runner/ecdsa_p224_key.pem",
			"ssl/test/runner/ecdsa_p256_cert.pem",
			"ssl/test/runner/ecdsa_p256_key.pem",
			"ssl/test/runner/ecdsa_p384_cert.pem",
			"ssl/test/runner/ecdsa_p384_key.pem",
			"ssl/test/runner/ecdsa_p521_cert.pem",
			"ssl/test/runner/ecdsa_p521_key.pem",
			"ssl/test/runner/ed25519_cert.pem",
			"ssl/test/runner/ed25519_key.pem",
			"ssl/test/runner/key.pem",
			"ssl/test/runner/rsa_1024_cert.pem",
			"ssl/test/runner/rsa_1024_key.pem",
			"ssl/test/runner/rsa_chain_cert.pem",
			"ssl/test/runner/rsa_chain_key.pem",
			"util/all_tests.json",
		)

		fmt.Printf("Building runner...\n")
		if err := goTool("test", "-c", "-o", filepath.Join(tmpDir, "ssl/test/runner/runner"), "./ssl/test/runner/"); err != nil {
			fmt.Printf("Error building runner: %s\n", err)
			os.Exit(1)
		}
	}

	fmt.Printf("Copying test binaries...\n")
	for _, binary := range binaries {
		if err := copyFile(filepath.Join(tmpDir, "build", binary), filepath.Join(*buildDir, binary)); err != nil {
			fmt.Printf("Failed to copy %s: %s\n", binary, err)
			os.Exit(1)
		}
	}

	fmt.Printf("Copying data files...\n")
	for _, file := range files {
		if err := copyFile(filepath.Join(tmpDir, file), file); err != nil {
			fmt.Printf("Failed to copy %s: %s\n", file, err)
			os.Exit(1)
		}
	}

	fmt.Printf("Uploading files...\n")
	if err := adb("push", "-p", tmpDir, "/data/local/tmp/boringssl-tmp"); err != nil {
		fmt.Printf("Failed to push runner: %s\n", err)
		os.Exit(1)
	}

	var unitTestExit int
	if enableUnitTests() {
		fmt.Printf("Running unit tests...\n")
		unitTestExit, err = adbShell(fmt.Sprintf("cd /data/local/tmp/boringssl-tmp && ./util/all_tests -json-output results.json %s", *allTestsArgs))
		if err != nil {
			fmt.Printf("Failed to run unit tests: %s\n", err)
			os.Exit(1)
		}
	}

	var sslTestExit int
	if enableSSLTests() {
		fmt.Printf("Running SSL tests...\n")
		sslTestExit, err = adbShell(fmt.Sprintf("cd /data/local/tmp/boringssl-tmp/ssl/test/runner && ./runner -json-output ../../../results.json %s", *runnerArgs))
		if err != nil {
			fmt.Printf("Failed to run SSL tests: %s\n", err)
			os.Exit(1)
		}
	}

	if *jsonOutput != "" {
		if err := adb("pull", "-p", "/data/local/tmp/boringssl-tmp/results.json", *jsonOutput); err != nil {
			fmt.Printf("Failed to extract results.json: %s\n", err)
			os.Exit(1)
		}
	}

	if unitTestExit != 0 {
		os.Exit(unitTestExit)
	}

	if sslTestExit != 0 {
		os.Exit(sslTestExit)
	}
}
