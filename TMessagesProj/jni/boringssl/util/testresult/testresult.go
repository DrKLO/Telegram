/* Copyright (c) 2018, Google Inc.
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

// testresult is an implementation of Chromium's JSON test result format. See
// https://chromium.googlesource.com/chromium/src/+/master/docs/testing/json_test_results_format.md
package testresult

import (
	"encoding/json"
	"os"
	"time"
)

// Results stores the top-level test results.
type Results struct {
	Version           int               `json:"version"`
	Interrupted       bool              `json:"interrupted"`
	PathDelimiter     string            `json:"path_delimiter"`
	SecondsSinceEpoch float64           `json:"seconds_since_epoch"`
	NumFailuresByType map[string]int    `json:"num_failures_by_type"`
	Tests             map[string]Result `json:"tests"`
}

func NewResults() *Results {
	return &Results{
		Version:           3,
		PathDelimiter:     ".",
		SecondsSinceEpoch: float64(time.Now().UnixNano()) / float64(time.Second/time.Nanosecond),
		NumFailuresByType: make(map[string]int),
		Tests:             make(map[string]Result),
	}
}

func (t *Results) addResult(name, result, expected string) {
	if _, found := t.Tests[name]; found {
		panic(name)
	}
	t.Tests[name] = Result{
		Actual:       result,
		Expected:     expected,
		IsUnexpected: result != expected,
	}
	t.NumFailuresByType[result]++
}

// AddResult records a test result with the given result string. The test is a
// failure if the result is not "PASS".
func (t *Results) AddResult(name, result string) {
	t.addResult(name, result, "PASS")
}

// AddSkip marks a test as being skipped. It is not considered a failure.
func (t *Results) AddSkip(name string) {
	t.addResult(name, "SKIP", "SKIP")
}

func (t *Results) HasUnexpectedResults() bool {
	for _, r := range t.Tests {
		if r.IsUnexpected {
			return false
		}
	}
	return true
}

func (t *Results) WriteToFile(name string) error {
	file, err := os.Create(name)
	if err != nil {
		return err
	}
	defer file.Close()
	out, err := json.MarshalIndent(t, "", "  ")
	if err != nil {
		return err
	}
	_, err = file.Write(out)
	return err
}

type Result struct {
	Actual       string `json:"actual"`
	Expected     string `json:"expected"`
	IsUnexpected bool   `json:"is_unexpected"`
}
