// Copyright 2021 The BoringSSL Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//go:build ignore

// trimvectors takes an ACVP vector set file and discards all but a single test
// from each test group. This hope is that this achieves good coverage without
// having to check in megabytes worth of JSON files.
package main

import (
	"encoding/json"
	"os"
)

func main() {
	var vectorSets []any
	decoder := json.NewDecoder(os.Stdin)
	if err := decoder.Decode(&vectorSets); err != nil {
		panic(err)
	}

	// The first element is the metadata which is left unmodified.
	for i := 1; i < len(vectorSets); i++ {
		vectorSet := vectorSets[i].(map[string]any)
		testGroups := vectorSet["testGroups"].([]any)
		for _, testGroupInterface := range testGroups {
			testGroup := testGroupInterface.(map[string]any)
			tests := testGroup["tests"].([]any)

			keepIndex := 10
			if keepIndex >= len(tests) {
				keepIndex = len(tests) - 1
			}

			testGroup["tests"] = []any{tests[keepIndex]}
		}
	}

	encoder := json.NewEncoder(os.Stdout)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(vectorSets); err != nil {
		panic(err)
	}
}
