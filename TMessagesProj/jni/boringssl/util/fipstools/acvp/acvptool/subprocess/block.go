package subprocess

import (
	"encoding/hex"
	"encoding/json"
	"fmt"
)

// blockCipher implements an ACVP algorithm by making requests to the subprocess
// to encrypt and decrypt with a block cipher.
type blockCipher struct {
	algo      string
	blockSize int
	hasIV     bool
	m         *Subprocess
}

type blockCipherVectorSet struct {
	Groups []blockCipherTestGroup `json:"testGroups"`
}

type blockCipherTestGroup struct {
	ID        uint64 `json:"tgId"`
	Type      string `json:"testType"`
	Direction string `json:"direction"`
	KeyBits   int    `json:"keylen"`
	Tests     []struct {
		ID            uint64 `json:"tcId"`
		PlaintextHex  string `json:"pt"`
		CiphertextHex string `json:"ct"`
		IVHex         string `json:"iv"`
		KeyHex        string `json:"key"`
	} `json:"tests"`
}

type blockCipherTestGroupResponse struct {
	ID    uint64                    `json:"tgId"`
	Tests []blockCipherTestResponse `json:"tests"`
}

type blockCipherTestResponse struct {
	ID            uint64                 `json:"tcId"`
	CiphertextHex string                 `json:"ct,omitempty"`
	PlaintextHex  string                 `json:"pt,omitempty"`
	MCTResults    []blockCipherMCTResult `json:"resultsArray,omitempty"`
}

type blockCipherMCTResult struct {
	KeyHex        string `json:"key"`
	PlaintextHex  string `json:"pt"`
	CiphertextHex string `json:"ct"`
	IVHex         string `json:"iv,omitempty"`
}

func (b *blockCipher) Process(vectorSet []byte) (interface{}, error) {
	var parsed blockCipherVectorSet
	if err := json.Unmarshal(vectorSet, &parsed); err != nil {
		return nil, err
	}

	var ret []blockCipherTestGroupResponse
	// See
	// http://usnistgov.github.io/ACVP/artifacts/draft-celi-acvp-block-ciph-00.html#rfc.section.5.2
	// for details about the tests.
	for _, group := range parsed.Groups {
		response := blockCipherTestGroupResponse{
			ID: group.ID,
		}

		var encrypt bool
		switch group.Direction {
		case "encrypt":
			encrypt = true
		case "decrypt":
			encrypt = false
		default:
			return nil, fmt.Errorf("test group %d has unknown direction %q", group.ID, group.Direction)
		}

		op := b.algo + "/encrypt"
		if !encrypt {
			op = b.algo + "/decrypt"
		}

		var mct bool
		switch group.Type {
		case "AFT":
			mct = false
		case "MCT":
			mct = true
		default:
			return nil, fmt.Errorf("test group %d has unknown type %q", group.ID, group.Type)
		}

		if group.KeyBits%8 != 0 {
			return nil, fmt.Errorf("test group %d contains non-byte-multiple key length %d", group.ID, group.KeyBits)
		}
		keyBytes := group.KeyBits / 8

		for _, test := range group.Tests {
			if len(test.KeyHex) != keyBytes*2 {
				return nil, fmt.Errorf("test case %d/%d contains key %q of length %d, but expected %d-bit key", group.ID, test.ID, test.KeyHex, len(test.KeyHex), group.KeyBits)
			}

			key, err := hex.DecodeString(test.KeyHex)
			if err != nil {
				return nil, fmt.Errorf("failed to decode hex in test case %d/%d: %s", group.ID, test.ID, err)
			}

			var inputHex string
			if encrypt {
				inputHex = test.PlaintextHex
			} else {
				inputHex = test.CiphertextHex
			}

			input, err := hex.DecodeString(inputHex)
			if err != nil {
				return nil, fmt.Errorf("failed to decode hex in test case %d/%d: %s", group.ID, test.ID, err)
			}

			if len(input)%b.blockSize != 0 {
				return nil, fmt.Errorf("test case %d/%d has input of length %d, but expected multiple of %d", group.ID, test.ID, len(input), b.blockSize)
			}

			var iv []byte
			if b.hasIV {
				if iv, err = hex.DecodeString(test.IVHex); err != nil {
					return nil, fmt.Errorf("failed to decode hex in test case %d/%d: %s", group.ID, test.ID, err)
				}
				if len(iv) != b.blockSize {
					return nil, fmt.Errorf("test case %d/%d has IV of length %d, but expected %d", group.ID, test.ID, len(iv), b.blockSize)
				}
			}

			testResp := blockCipherTestResponse{ID: test.ID}
			if !mct {
				var result [][]byte
				var err error

				if b.hasIV {
					result, err = b.m.transact(op, 1, key, input, iv)
				} else {
					result, err = b.m.transact(op, 1, key, input)
				}
				if err != nil {
					panic("block operation failed: " + err.Error())
				}

				if encrypt {
					testResp.CiphertextHex = hex.EncodeToString(result[0])
				} else {
					testResp.PlaintextHex = hex.EncodeToString(result[0])
				}
			} else {
				for i := 0; i < 100; i++ {
					var iteration blockCipherMCTResult
					iteration.KeyHex = hex.EncodeToString(key)
					if encrypt {
						iteration.PlaintextHex = hex.EncodeToString(input)
					} else {
						iteration.CiphertextHex = hex.EncodeToString(input)
					}

					var result, prevResult []byte
					if !b.hasIV {
						for j := 0; j < 1000; j++ {
							prevResult = input
							result, err := b.m.transact(op, 1, key, input)
							if err != nil {
								panic("block operation failed")
							}
							input = result[0]
						}
						result = input
					} else {
						iteration.IVHex = hex.EncodeToString(iv)

						var prevInput []byte
						for j := 0; j < 1000; j++ {
							prevResult = result
							if j > 0 {
								if encrypt {
									iv = result
								} else {
									iv = prevInput
								}
							}

							results, err := b.m.transact(op, 1, key, input, iv)
							if err != nil {
								panic("block operation failed")
							}
							result = results[0]

							prevInput = input
							if j == 0 {
								input = iv
							} else {
								input = prevResult
							}
						}
					}

					if encrypt {
						iteration.CiphertextHex = hex.EncodeToString(result)
					} else {
						iteration.PlaintextHex = hex.EncodeToString(result)
					}

					switch keyBytes {
					case 16:
						for i := range key {
							key[i] ^= result[i]
						}
					case 24:
						for i := 0; i < 8; i++ {
							key[i] ^= prevResult[i+8]
						}
						for i := range result {
							key[i+8] ^= result[i]
						}
					case 32:
						for i, b := range prevResult {
							key[i] ^= b
						}
						for i, b := range result {
							key[i+16] ^= b
						}
					default:
						panic("unhandled key length")
					}

					if !b.hasIV {
						input = result
					} else {
						iv = result
						input = prevResult
					}

					testResp.MCTResults = append(testResp.MCTResults, iteration)
				}
			}

			response.Tests = append(response.Tests, testResp)
		}

		ret = append(ret, response)
	}

	return ret, nil
}
