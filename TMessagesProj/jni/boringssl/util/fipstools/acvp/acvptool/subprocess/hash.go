package subprocess

import (
	"encoding/hex"
	"encoding/json"
	"fmt"
)

// The following structures reflect the JSON of ACVP hash tests. See
// https://usnistgov.github.io/ACVP/artifacts/draft-celi-acvp-sha-00.html#test_vectors

type hashTestVectorSet struct {
	Groups []hashTestGroup `json:"testGroups"`
}

type hashTestGroup struct {
	ID    uint64 `json:"tgId"`
	Type  string `json:"testType"`
	Tests []struct {
		ID        uint64 `json:"tcId"`
		BitLength uint64 `json:"len"`
		MsgHex    string `json:"msg"`
	} `json:"tests"`
}

type hashTestGroupResponse struct {
	ID    uint64             `json:"tgId"`
	Tests []hashTestResponse `json:"tests"`
}

type hashTestResponse struct {
	ID         uint64          `json:"tcId"`
	DigestHex  string          `json:"md,omitempty"`
	MCTResults []hashMCTResult `json:"resultsArray,omitempty"`
}

type hashMCTResult struct {
	DigestHex string `json:"md"`
}

// hashPrimitive implements an ACVP algorithm by making requests to the
// subprocess to hash strings.
type hashPrimitive struct {
	// algo is the ACVP name for this algorithm and also the command name
	// given to the subprocess to hash with this hash function.
	algo string
	// size is the number of bytes of digest that the hash produces.
	size int
	m    *Subprocess
}

// hash uses the subprocess to hash msg and returns the digest.
func (h *hashPrimitive) hash(msg []byte) []byte {
	result, err := h.m.transact(h.algo, 1, msg)
	if err != nil {
		panic("hash operation failed: " + err.Error())
	}
	return result[0]
}

func (h *hashPrimitive) Process(vectorSet []byte) (interface{}, error) {
	var parsed hashTestVectorSet
	if err := json.Unmarshal(vectorSet, &parsed); err != nil {
		return nil, err
	}

	var ret []hashTestGroupResponse
	// See
	// https://usnistgov.github.io/ACVP/artifacts/draft-celi-acvp-sha-00.html#rfc.section.3
	// for details about the tests.
	for _, group := range parsed.Groups {
		response := hashTestGroupResponse{
			ID: group.ID,
		}

		for _, test := range group.Tests {
			if uint64(len(test.MsgHex))*4 != test.BitLength {
				return nil, fmt.Errorf("test case %d/%d contains hex message of length %d but specifies a bit length of %d", group.ID, test.ID, len(test.MsgHex), test.BitLength)
			}
			msg, err := hex.DecodeString(test.MsgHex)
			if err != nil {
				return nil, fmt.Errorf("failed to decode hex in test case %d/%d: %s", group.ID, test.ID, err)
			}

			// http://usnistgov.github.io/ACVP/artifacts/draft-celi-acvp-sha-00.html#rfc.section.3
			switch group.Type {
			case "AFT":
				response.Tests = append(response.Tests, hashTestResponse{
					ID:        test.ID,
					DigestHex: hex.EncodeToString(h.hash(msg)),
				})

			case "MCT":
				if len(msg) != h.size {
					return nil, fmt.Errorf("MCT test case %d/%d contains message of length %d but the digest length is %d", group.ID, test.ID, len(msg), h.size)
				}

				testResponse := hashTestResponse{ID: test.ID}

				buf := make([]byte, 3*h.size)
				var digest []byte
				for i := 0; i < 100; i++ {
					copy(buf, msg)
					copy(buf[h.size:], msg)
					copy(buf[2*h.size:], msg)
					for j := 0; j < 1000; j++ {
						digest = h.hash(buf)
						copy(buf, buf[h.size:])
						copy(buf[2*h.size:], digest)
					}

					testResponse.MCTResults = append(testResponse.MCTResults, hashMCTResult{hex.EncodeToString(digest)})
					msg = digest
				}

				response.Tests = append(response.Tests, testResponse)

			default:
				return nil, fmt.Errorf("test group %d has unknown type %q", group.ID, group.Type)
			}
		}

		ret = append(ret, response)
	}

	return ret, nil
}
