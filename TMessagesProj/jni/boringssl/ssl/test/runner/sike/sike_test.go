// Copyright (c) 2019, Cloudflare Inc.
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

package sike

import (
	"bufio"
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"math/big"
	"strings"
	"testing"
)

var tdata = struct {
	name     string
	PrB_sidh string
	PkB_sidh string
	PrA_sidh string
	PkA_sidh string
	PkB_sike string
	PrB_sike string
}{
	name:     "P-434",
	PrA_sidh: "3A727E04EA9B7E2A766A6F846489E7E7B915263BCEED308BB10FC9",
	PkA_sidh: "9E668D1E6750ED4B91EE052C32839CA9DD2E56D52BC24DECC950AA" +
		"AD24CEED3F9049C77FE80F0B9B01E7F8DAD7833EEC2286544D6380" +
		"009C379CDD3E7517CEF5E20EB01F8231D52FC30DC61D2F63FB357F" +
		"85DC6396E8A95DB9740BD3A972C8DB7901B31F074CD3E45345CA78" +
		"F900817130E688A29A7CF0073B5C00FF2C65FBE776918EF9BD8E75" +
		"B29EF7FAB791969B60B0C5B37A8992EDEF95FA7BAC40A95DAFE02E" +
		"237301FEE9A7A43FD0B73477E8035DD12B73FAFEF18D39904DDE36" +
		"53A754F36BE1888F6607C6A7951349A414352CF31A29F2C40302DB" +
		"406C48018C905EB9DC46AFBF42A9187A9BB9E51B587622A2862DC7" +
		"D5CC598BF38ED6320FB51D8697AD3D7A72ABCC32A393F0133DA8DF" +
		"5E253D9E00B760B2DF342FCE974DCFE946CFE4727783531882800F" +
		"9E5DD594D6D5A6275EEFEF9713ED838F4A06BB34D7B8D46E0B385A" +
		"AEA1C7963601",
	PrB_sidh: "E37BFE55B43B32448F375903D8D226EC94ADBFEA1D2B3536EB987001",
	PkB_sidh: "C9F73E4497AAA3FDF9EB688135866A8A83934BA10E273B8CC3808C" +
		"F0C1F5FAB3E9BB295885881B73DEBC875670C0F51C4BB40DF5FEDE" +
		"01B8AF32D1BF10508B8C17B2734EB93B2B7F5D84A4A0F2F816E9E2" +
		"C32AC253C0B6025B124D05A87A9E2A8567930F44BAA14219B941B6" +
		"B400B4AED1D796DA12A5A9F0B8F3F5EE9DD43F64CB24A3B1719DF2" +
		"78ADF56B5F3395187829DA2319DEABF6BBD6EDA244DE2B62CC5AC2" +
		"50C1009DD1CD4712B0B37406612AD002B5E51A62B51AC9C0374D14" +
		"3ABBBD58275FAFC4A5E959C54838C2D6D9FB43B7B2609061267B6A" +
		"2E6C6D01D295C4223E0D3D7A4CDCFB28A7818A737935279751A6DD" +
		"8290FD498D1F6AD5F4FFF6BDFA536713F509DCE8047252F1E7D0DD" +
		"9FCC414C0070B5DCCE3665A21A032D7FBE749181032183AFAD240B" +
		"7E671E87FBBEC3A8CA4C11AA7A9A23AC69AE2ACF54B664DECD2775" +
		"3D63508F1B02",
	PrB_sike: "4B622DE1350119C45A9F2E2EF3DC5DF56A27FCDFCDDAF58CD69B90" +
		"3752D68C200934E160B234E49EDE247601",
	PkB_sike: "1BD0A2E81307B6F96461317DDF535ACC0E59C742627BAE60D27605" +
		"E10FAF722D22A73E184CB572A12E79DCD58C6B54FB01442114CBE9" +
		"010B6CAEC25D04C16C5E42540C1524C545B8C67614ED4183C9FA5B" +
		"D0BE45A7F89FBC770EE8E7E5E391C7EE6F35F74C29E6D9E35B1663" +
		"DA01E48E9DEB2347512D366FDE505161677055E3EF23054D276E81" +
		"7E2C57025DA1C10D2461F68617F2D11256EEE4E2D7DBDF6C8E34F3" +
		"A0FD00C625428CB41857002159DAB94267ABE42D630C6AAA91AF83" +
		"7C7A6740754EA6634C45454C51B0BB4D44C3CCCCE4B32C00901CF6" +
		"9C008D013348379B2F9837F428A01B6173584691F2A6F3A3C4CF48" +
		"7D20D261B36C8CDB1BC158E2A5162A9DA4F7A97AA0879B9897E2B6" +
		"891B672201F9AEFBF799C27B2587120AC586A511360926FB7DA8EB" +
		"F5CB5272F396AE06608422BE9792E2CE9BEF21BF55B7EFF8DC7EC8" +
		"C99910D3F800",
}

/* -------------------------------------------------------------------------
   Helpers
   -------------------------------------------------------------------------*/
// Fail if err !=nil. Display msg as an error message
func checkErr(t testing.TB, err error, msg string) {
	t.Helper()
	if err != nil {
		t.Error(msg)
	}
}

// Utility used for running same test with all registered prime fields
type MultiIdTestingFunc func(testing.TB)

// Converts string to private key
func convToPrv(s string, v KeyVariant) *PrivateKey {
	key := NewPrivateKey(v)
	hex, e := hex.DecodeString(s)
	if e != nil {
		panic("non-hex number provided")
	}
	e = key.Import(hex)
	if e != nil {
		panic("Can't import private key")
	}
	return key
}

// Converts string to public key
func convToPub(s string, v KeyVariant) *PublicKey {
	key := NewPublicKey(v)
	hex, e := hex.DecodeString(s)
	if e != nil {
		panic("non-hex number provided")
	}
	e = key.Import(hex)
	if e != nil {
		panic("Can't import public key")
	}
	return key
}

/* -------------------------------------------------------------------------
   Unit tests
   -------------------------------------------------------------------------*/
func TestKeygen(t *testing.T) {
	alicePrivate := convToPrv(tdata.PrA_sidh, KeyVariant_SIDH_A)
	bobPrivate := convToPrv(tdata.PrB_sidh, KeyVariant_SIDH_B)
	expPubA := convToPub(tdata.PkA_sidh, KeyVariant_SIDH_A)
	expPubB := convToPub(tdata.PkB_sidh, KeyVariant_SIDH_B)

	pubA := alicePrivate.GeneratePublicKey()
	pubB := bobPrivate.GeneratePublicKey()

	if !bytes.Equal(pubA.Export(), expPubA.Export()) {
		t.Fatalf("unexpected value of public key A")
	}
	if !bytes.Equal(pubB.Export(), expPubB.Export()) {
		t.Fatalf("unexpected value of public key B")
	}
}

func TestImportExport(t *testing.T) {
	var err error
	a := NewPublicKey(KeyVariant_SIDH_A)
	b := NewPublicKey(KeyVariant_SIDH_B)

	// Import keys
	a_hex, err := hex.DecodeString(tdata.PkA_sidh)
	checkErr(t, err, "invalid hex-number provided")

	err = a.Import(a_hex)
	checkErr(t, err, "import failed")

	b_hex, err := hex.DecodeString(tdata.PkB_sike)
	checkErr(t, err, "invalid hex-number provided")

	err = b.Import(b_hex)
	checkErr(t, err, "import failed")

	// Export and check if same
	if !bytes.Equal(b.Export(), b_hex) || !bytes.Equal(a.Export(), a_hex) {
		t.Fatalf("export/import failed")
	}

	if (len(b.Export()) != b.Size()) || (len(a.Export()) != a.Size()) {
		t.Fatalf("wrong size of exported keys")
	}
}

func testPrivateKeyBelowMax(t testing.TB) {
	for variant, keySz := range map[KeyVariant]*DomainParams{
		KeyVariant_SIDH_A: &Params.A,
		KeyVariant_SIDH_B: &Params.B} {

		func(v KeyVariant, dp *DomainParams) {
			var blen = int(dp.SecretByteLen)
			var prv = NewPrivateKey(v)

			// Calculate either (2^e2 - 1) or (2^s - 1); where s=ceil(log_2(3^e3)))
			maxSecertVal := big.NewInt(int64(dp.SecretBitLen))
			maxSecertVal.Exp(big.NewInt(int64(2)), maxSecertVal, nil)
			maxSecertVal.Sub(maxSecertVal, big.NewInt(1))

			// Do same test 1000 times
			for i := 0; i < 1000; i++ {
				err := prv.Generate(rand.Reader)
				checkErr(t, err, "Private key generation")

				// Convert to big-endian, as that's what expected by (*Int)SetBytes()
				secretBytes := prv.Export()
				for i := 0; i < int(blen/2); i++ {
					tmp := secretBytes[i] ^ secretBytes[blen-i-1]
					secretBytes[i] = tmp ^ secretBytes[i]
					secretBytes[blen-i-1] = tmp ^ secretBytes[blen-i-1]
				}
				prvBig := new(big.Int).SetBytes(secretBytes)
				// Check if generated key is bigger than acceptable
				if prvBig.Cmp(maxSecertVal) == 1 {
					t.Error("Generated private key is wrong")
				}
			}
		}(variant, keySz)
	}
}

func testKeyAgreement(t *testing.T, pkA, prA, pkB, prB string) {
	var e error

	// KeyPairs
	alicePublic := convToPub(pkA, KeyVariant_SIDH_A)
	bobPublic := convToPub(pkB, KeyVariant_SIDH_B)
	alicePrivate := convToPrv(prA, KeyVariant_SIDH_A)
	bobPrivate := convToPrv(prB, KeyVariant_SIDH_B)

	// Do actual test
	s1, e := DeriveSecret(bobPrivate, alicePublic)
	checkErr(t, e, "derivation s1")
	s2, e := DeriveSecret(alicePrivate, bobPublic)
	checkErr(t, e, "derivation s1")

	if !bytes.Equal(s1[:], s2[:]) {
		t.Fatalf("two shared keys: %d, %d do not match", s1, s2)
	}

	// Negative case
	dec, e := hex.DecodeString(tdata.PkA_sidh)
	if e != nil {
		t.FailNow()
	}
	dec[0] = ^dec[0]
	e = alicePublic.Import(dec)
	if e != nil {
		t.FailNow()
	}

	s1, e = DeriveSecret(bobPrivate, alicePublic)
	checkErr(t, e, "derivation of s1 failed")
	s2, e = DeriveSecret(alicePrivate, bobPublic)
	checkErr(t, e, "derivation of s2 failed")

	if bytes.Equal(s1[:], s2[:]) {
		t.Fatalf("The two shared keys: %d, %d match", s1, s2)
	}
}

func TestDerivationRoundTrip(t *testing.T) {
	var err error

	prvA := NewPrivateKey(KeyVariant_SIDH_A)
	prvB := NewPrivateKey(KeyVariant_SIDH_B)

	// Generate private keys
	err = prvA.Generate(rand.Reader)
	checkErr(t, err, "key generation failed")
	err = prvB.Generate(rand.Reader)
	checkErr(t, err, "key generation failed")

	// Generate public keys
	pubA := prvA.GeneratePublicKey()
	pubB := prvB.GeneratePublicKey()

	// Derive shared secret
	s1, err := DeriveSecret(prvB, pubA)
	checkErr(t, err, "")

	s2, err := DeriveSecret(prvA, pubB)
	checkErr(t, err, "")

	if !bytes.Equal(s1[:], s2[:]) {
		t.Fatalf("Two shared keys: \n%X, \n%X do not match", s1, s2)
	}
}

// Encrypt, Decrypt, check if input/output plaintext is the same
func testPKERoundTrip(t testing.TB, id uint8) {
	// Message to be encrypted
	var msg = make([]byte, Params.MsgLen)
	for i, _ := range msg {
		msg[i] = byte(i)
	}

	// Import keys
	pkB := NewPublicKey(KeyVariant_SIKE)
	skB := NewPrivateKey(KeyVariant_SIKE)
	pk_hex, err := hex.DecodeString(tdata.PkB_sike)
	if err != nil {
		t.Fatal(err)
	}
	sk_hex, err := hex.DecodeString(tdata.PrB_sike)
	if err != nil {
		t.Fatal(err)
	}
	if pkB.Import(pk_hex) != nil || skB.Import(sk_hex) != nil {
		t.Error("Import")
	}

	ct, err := Encrypt(rand.Reader, pkB, msg[:])
	if err != nil {
		t.Fatal(err)
	}
	pt, err := Decrypt(skB, ct)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(pt[:], msg[:]) {
		t.Errorf("Decryption failed \n got : %X\n exp : %X", pt, msg)
	}
}

// Generate key and check if can encrypt
func TestPKEKeyGeneration(t *testing.T) {
	// Message to be encrypted
	var msg = make([]byte, Params.MsgLen)
	var err error
	for i, _ := range msg {
		msg[i] = byte(i)
	}

	sk := NewPrivateKey(KeyVariant_SIKE)
	err = sk.Generate(rand.Reader)
	checkErr(t, err, "PEK key generation")
	pk := sk.GeneratePublicKey()

	// Try to encrypt
	ct, err := Encrypt(rand.Reader, pk, msg[:])
	checkErr(t, err, "PEK encryption")
	pt, err := Decrypt(sk, ct)
	checkErr(t, err, "PEK key decryption")

	if !bytes.Equal(pt[:], msg[:]) {
		t.Fatalf("Decryption failed \n got : %X\n exp : %X", pt, msg)
	}
}

func TestNegativePKE(t *testing.T) {
	var msg [40]byte
	var err error

	// Generate key
	sk := NewPrivateKey(KeyVariant_SIKE)
	err = sk.Generate(rand.Reader)
	checkErr(t, err, "key generation")

	pk := sk.GeneratePublicKey()

	// bytelen(msg) - 1
	ct, err := Encrypt(rand.Reader, pk, msg[:Params.KemSize+8-1])
	if err == nil {
		t.Fatal("Error hasn't been returned")
	}
	if ct != nil {
		t.Fatal("Ciphertext must be nil")
	}

	// KemSize - 1
	pt, err := Decrypt(sk, msg[:Params.KemSize+8-1])
	if err == nil {
		t.Fatal("Error hasn't been returned")
	}
	if pt != nil {
		t.Fatal("Ciphertext must be nil")
	}
}

func testKEMRoundTrip(t *testing.T, pkB, skB []byte) {
	// Import keys
	pk := NewPublicKey(KeyVariant_SIKE)
	sk := NewPrivateKey(KeyVariant_SIKE)
	if pk.Import(pkB) != nil || sk.Import(skB) != nil {
		t.Error("Import failed")
	}

	ct, ss_e, err := Encapsulate(rand.Reader, pk)
	if err != nil {
		t.Error("Encapsulate failed")
	}

	ss_d, err := Decapsulate(sk, pk, ct)
	if err != nil {
		t.Error("Decapsulate failed")
	}
	if !bytes.Equal(ss_e, ss_d) {
		t.Error("Shared secrets from decapsulation and encapsulation differ")
	}
}

func TestKEMRoundTrip(t *testing.T) {
	pk, err := hex.DecodeString(tdata.PkB_sike)
	checkErr(t, err, "public key B not a number")
	sk, err := hex.DecodeString(tdata.PrB_sike)
	checkErr(t, err, "private key B not a number")
	testKEMRoundTrip(t, pk, sk)
}

func TestKEMKeyGeneration(t *testing.T) {
	// Generate key
	sk := NewPrivateKey(KeyVariant_SIKE)
	checkErr(t, sk.Generate(rand.Reader), "error: key generation")
	pk := sk.GeneratePublicKey()

	// calculated shared secret
	ct, ss_e, err := Encapsulate(rand.Reader, pk)

	checkErr(t, err, "encapsulation failed")
	ss_d, err := Decapsulate(sk, pk, ct)
	checkErr(t, err, "decapsulation failed")

	if !bytes.Equal(ss_e, ss_d) {
		t.Fatalf("KEM failed \n encapsulated: %X\n decapsulated: %X", ss_d, ss_e)
	}
}

func TestNegativeKEM(t *testing.T) {
	sk := NewPrivateKey(KeyVariant_SIKE)
	checkErr(t, sk.Generate(rand.Reader), "error: key generation")
	pk := sk.GeneratePublicKey()

	ct, ss_e, err := Encapsulate(rand.Reader, pk)
	checkErr(t, err, "pre-requisite for a test failed")

	ct[0] = ct[0] - 1
	ss_d, err := Decapsulate(sk, pk, ct)
	checkErr(t, err, "decapsulation returns error when invalid ciphertext provided")

	if bytes.Equal(ss_e, ss_d) {
		// no idea how this could ever happen, but it would be very bad
		t.Error("critical error")
	}

	// Try encapsulating with SIDH key
	pkSidh := NewPublicKey(KeyVariant_SIDH_B)
	prSidh := NewPrivateKey(KeyVariant_SIDH_B)
	_, _, err = Encapsulate(rand.Reader, pkSidh)
	if err == nil {
		t.Error("encapsulation accepts SIDH public key")
	}
	// Try decapsulating with SIDH key
	_, err = Decapsulate(prSidh, pk, ct)
	if err == nil {
		t.Error("decapsulation accepts SIDH private key key")
	}
}

// In case invalid ciphertext is provided, SIKE's decapsulation must
// return same (but unpredictable) result for a given key.
func TestNegativeKEMSameWrongResult(t *testing.T) {
	sk := NewPrivateKey(KeyVariant_SIKE)
	checkErr(t, sk.Generate(rand.Reader), "error: key generation")
	pk := sk.GeneratePublicKey()

	ct, encSs, err := Encapsulate(rand.Reader, pk)
	checkErr(t, err, "pre-requisite for a test failed")

	// make ciphertext wrong
	ct[0] = ct[0] - 1
	decSs1, err := Decapsulate(sk, pk, ct)
	checkErr(t, err, "pre-requisite for a test failed")

	// second decapsulation must be done with same, but imported private key
	expSk := sk.Export()

	// creat new private key
	sk = NewPrivateKey(KeyVariant_SIKE)
	err = sk.Import(expSk)
	checkErr(t, err, "import failed")

	// try decapsulating again. ss2 must be same as ss1 and different than
	// original plaintext
	decSs2, err := Decapsulate(sk, pk, ct)
	checkErr(t, err, "pre-requisite for a test failed")

	if !bytes.Equal(decSs1, decSs2) {
		t.Error("decapsulation is insecure")
	}

	if bytes.Equal(encSs, decSs1) || bytes.Equal(encSs, decSs2) {
		// this test requires that decapsulation returns wrong result
		t.Errorf("test implementation error")
	}
}

func readAndCheckLine(r *bufio.Reader) []byte {
	// Read next line from buffer
	line, isPrefix, err := r.ReadLine()
	if err != nil || isPrefix {
		panic("Wrong format of input file")
	}

	// Function expects that line is in format "KEY = HEX_VALUE". Get
	// value, which should be a hex string
	hexst := strings.Split(string(line), "=")[1]
	hexst = strings.TrimSpace(hexst)
	// Convert value to byte string
	ret, err := hex.DecodeString(hexst)
	if err != nil {
		panic("Wrong format of input file")
	}
	return ret
}

func testKeygenSIKE(pk, sk []byte, id uint8) bool {
	// Import provided private key
	var prvKey = NewPrivateKey(KeyVariant_SIKE)
	if prvKey.Import(sk) != nil {
		panic("sike test: can't load KAT")
	}

	// Generate public key
	pubKey := prvKey.GeneratePublicKey()
	return bytes.Equal(pubKey.Export(), pk)
}

func testDecapsulation(pk, sk, ct, ssExpected []byte, id uint8) bool {
	var pubKey = NewPublicKey(KeyVariant_SIKE)
	var prvKey = NewPrivateKey(KeyVariant_SIKE)
	if pubKey.Import(pk) != nil || prvKey.Import(sk) != nil {
		panic("sike test: can't load KAT")
	}

	ssGot, err := Decapsulate(prvKey, pubKey, ct)
	if err != nil {
		panic("sike test: can't perform degcapsulation KAT")
	}

	return bytes.Equal(ssGot, ssExpected)
}

func TestKeyAgreement(t *testing.T) {
	testKeyAgreement(t, tdata.PkA_sidh, tdata.PrA_sidh, tdata.PkB_sidh, tdata.PrB_sidh)
}

// Same values as in sike_test.cc
func TestDecapsulation(t *testing.T) {
	var sk = [16 + 28]byte{
		0x04, 0x5E, 0x01, 0x42, 0xB8, 0x2F, 0xE1, 0x9A, 0x38, 0x25,
		0x92, 0xE7, 0xDC, 0xBA, 0xF7, 0x1B, 0xB1, 0xFD, 0x34, 0x42,
		0xDB, 0x02, 0xBC, 0x9D, 0x4C, 0xD0, 0x72, 0x34, 0x4D, 0xBD,
		0x06, 0xDF, 0x1C, 0x7D, 0x0A, 0x88, 0xB2, 0x50, 0xC4, 0xF6,
		0xAE, 0xE8, 0x25, 0x01,
	}

	var pk = [330]byte{
		0x6D, 0x8D, 0xF5, 0x7B, 0xCD, 0x47, 0xCA, 0xCB, 0x7A, 0x38,
		0xB7, 0xA6, 0x90, 0xB7, 0x37, 0x03, 0xD4, 0x6F, 0x27, 0x73,
		0x74, 0x17, 0x5A, 0xA4, 0x0D, 0xC6, 0x81, 0xAD, 0xDB, 0xF7,
		0x18, 0xB2, 0x3C, 0x30, 0xCF, 0xAA, 0x08, 0x11, 0x91, 0xCC,
		0x27, 0x4E, 0xF1, 0xA6, 0xB7, 0xDA, 0xD2, 0xCF, 0x99, 0x7F,
		0xF7, 0xE1, 0xD0, 0xCE, 0x00, 0xD2, 0x4B, 0xA4, 0x33, 0xB4,
		0x87, 0x01, 0x3F, 0x02, 0xF7, 0xF9, 0xDE, 0xC3, 0x60, 0x62,
		0xDA, 0x3F, 0x74, 0xA9, 0x44, 0xBE, 0x19, 0xD5, 0x03, 0x2A,
		0x79, 0x8C, 0xA7, 0xFF, 0xEA, 0xB3, 0xBB, 0xB5, 0xD4, 0x1D,
		0x8F, 0x92, 0xCE, 0x62, 0x6E, 0x99, 0x24, 0xD7, 0x57, 0xFA,
		0xCD, 0xB6, 0xE2, 0x8E, 0xFD, 0x22, 0x0E, 0x31, 0x21, 0x01,
		0x8D, 0x79, 0xF8, 0x3E, 0x27, 0xEC, 0x43, 0x40, 0xDB, 0x82,
		0xE5, 0xEB, 0x6C, 0x97, 0x66, 0x29, 0x15, 0x68, 0xB7, 0x4D,
		0x84, 0xD1, 0x8A, 0x0B, 0x12, 0x36, 0x2C, 0x0C, 0x0A, 0x6E,
		0x4E, 0xDE, 0xA5, 0x8A, 0xDE, 0x77, 0xDD, 0x70, 0x49, 0x73,
		0xAC, 0x27, 0x6D, 0x8D, 0x25, 0x9A, 0xE4, 0x25, 0xE8, 0x95,
		0x8F, 0xFE, 0x90, 0x3B, 0x00, 0x69, 0x20, 0xE8, 0x7C, 0xA5,
		0xF5, 0x79, 0xC0, 0x61, 0x51, 0x91, 0x35, 0x25, 0x3F, 0x17,
		0x2F, 0x70, 0x73, 0xF0, 0x89, 0xB5, 0xC8, 0x25, 0xB8, 0xE5,
		0x7E, 0x34, 0xDD, 0x11, 0xE5, 0xD6, 0xC3, 0xD5, 0x29, 0x89,
		0xC6, 0x2C, 0x99, 0x53, 0x1D, 0x2C, 0x77, 0xB0, 0xB6, 0xA1,
		0xBD, 0x79, 0xFB, 0x4A, 0xC2, 0x48, 0x4C, 0x62, 0x51, 0x00,
		0xE3, 0x91, 0x2A, 0xCB, 0x84, 0x03, 0x5D, 0x2D, 0xC8, 0x33,
		0xE9, 0x14, 0xBF, 0x74, 0x21, 0xBC, 0xF4, 0x76, 0xE5, 0x42,
		0xB8, 0xBD, 0xE2, 0xE7, 0x20, 0x95, 0x54, 0xF2, 0xED, 0xC0,
		0x79, 0x38, 0x1E, 0xD2, 0xEA, 0x1A, 0x63, 0x85, 0xE7, 0x3A,
		0xDA, 0xAD, 0xAB, 0x1B, 0x1E, 0x19, 0x9E, 0x73, 0xD0, 0x10,
		0x2E, 0x38, 0xAC, 0x8B, 0x00, 0x6A, 0x30, 0x2C, 0x3D, 0x70,
		0x8E, 0x39, 0x6D, 0xC0, 0x12, 0x61, 0x7D, 0x2A, 0x0A, 0x04,
		0x95, 0x8E, 0x09, 0x3C, 0x7B, 0xEC, 0x2E, 0xBC, 0xE8, 0xE8,
		0xE8, 0x37, 0x29, 0xC4, 0x7E, 0x76, 0x48, 0xB9, 0x3B, 0x72,
		0xE5, 0x99, 0x9B, 0xF9, 0xE3, 0x99, 0x72, 0x3F, 0x35, 0x29,
		0x85, 0xE0, 0xC8, 0xBF, 0xB1, 0x6B, 0xB1, 0x6E, 0x72, 0x00,
	}

	var ct = [330 + 16]byte{
		0xFF, 0xEB, 0xEF, 0x4A, 0xC0, 0x57, 0x0F, 0x26, 0xAC, 0x76,
		0xA8, 0xB0, 0xA3, 0x5D, 0x9C, 0xD9, 0x25, 0xD1, 0x7F, 0x92,
		0x5D, 0xF4, 0x23, 0x34, 0xC3, 0x03, 0x10, 0xE1, 0xB0, 0x24,
		0x9B, 0x44, 0x58, 0x26, 0x13, 0x56, 0x83, 0x43, 0x72, 0x69,
		0x28, 0x0D, 0x55, 0x07, 0x1F, 0xDB, 0xC0, 0x23, 0x34, 0x83,
		0x1A, 0x09, 0x9B, 0x80, 0x00, 0x64, 0x56, 0xDC, 0x79, 0x7A,
		0xD2, 0xCE, 0x23, 0xC9, 0x72, 0x27, 0xFC, 0x8D, 0xAB, 0xBF,
		0xD3, 0x17, 0xF6, 0x91, 0x7B, 0x15, 0x93, 0x83, 0x8A, 0x4F,
		0x6C, 0xCA, 0x4A, 0x94, 0xDA, 0xC7, 0x9D, 0xB6, 0xD6, 0xBA,
		0xBD, 0x81, 0x9A, 0x78, 0xE5, 0xE5, 0xBE, 0x17, 0xBC, 0xCB,
		0xC8, 0x23, 0x80, 0x5F, 0x75, 0xF8, 0xDB, 0x51, 0x55, 0x00,
		0x25, 0x33, 0x52, 0x64, 0xB2, 0xD6, 0xD8, 0x9A, 0x2A, 0x9E,
		0x29, 0x99, 0x13, 0x33, 0xE2, 0xA7, 0x98, 0xAC, 0xD7, 0x79,
		0x5C, 0x2F, 0xBA, 0x07, 0xC3, 0x03, 0x37, 0xD6, 0xE6, 0xB5,
		0xA1, 0xF5, 0x29, 0xB6, 0xF6, 0xC0, 0x5C, 0x44, 0x68, 0x2B,
		0x0B, 0xF5, 0x00, 0x01, 0x44, 0xD5, 0xCC, 0x23, 0xB5, 0x27,
		0x4F, 0xCA, 0xB4, 0x05, 0x01, 0xF9, 0xD4, 0x41, 0xE0, 0xE1,
		0x1E, 0xCF, 0xA9, 0xBC, 0x79, 0xD7, 0xD5, 0xF5, 0x3C, 0xE6,
		0x93, 0xF4, 0x6C, 0x84, 0x5A, 0x2C, 0x4B, 0xE4, 0x91, 0xB2,
		0xB2, 0xB8, 0xAD, 0x74, 0x9A, 0x69, 0x79, 0x4C, 0x84, 0xB7,
		0xBF, 0xF1, 0x68, 0x4B, 0xAE, 0x0F, 0x7F, 0x45, 0x3B, 0x18,
		0x3F, 0xFA, 0x00, 0x48, 0xE0, 0x3A, 0xE2, 0xC0, 0xAE, 0x00,
		0xCE, 0x90, 0x28, 0xA4, 0x1B, 0xBE, 0xCA, 0x0C, 0x21, 0x29,
		0x64, 0x30, 0x5E, 0x35, 0xAD, 0xFD, 0x83, 0x47, 0x40, 0x6D,
		0x15, 0x56, 0xFC, 0xF8, 0x5F, 0xAB, 0x81, 0xFE, 0x6B, 0xE9,
		0x6B, 0xED, 0x27, 0x35, 0x7C, 0xD8, 0x2C, 0xD4, 0xF2, 0x11,
		0xE6, 0xAF, 0xDF, 0xB8, 0x91, 0x96, 0xEB, 0xF7, 0x4C, 0x8D,
		0x70, 0x77, 0x90, 0x81, 0x00, 0x09, 0x19, 0x27, 0x8A, 0x9E,
		0xB6, 0x1A, 0xE9, 0xAC, 0x6C, 0xC9, 0xF8, 0xEA, 0xA2, 0x34,
		0xB8, 0xAC, 0xB3, 0xB3, 0x68, 0xA1, 0xB7, 0x29, 0x55, 0xCA,
		0x40, 0x23, 0x92, 0x5C, 0x0C, 0x79, 0x6B, 0xD6, 0x9F, 0x5B,
		0xD2, 0xE6, 0xAE, 0x04, 0xCB, 0xEC, 0xC7, 0x88, 0x18, 0xDB,
		0x7A, 0xE6, 0xD6, 0xC9, 0x39, 0xFD, 0x93, 0x9B, 0xC8, 0x01,
		0x6F, 0x3E, 0x6C, 0x90, 0x3E, 0x73, 0x76, 0x99, 0x7C, 0x48,
		0xDA, 0x68, 0x48, 0x80, 0x2B, 0x63,
	}
	var ssExp = [16]byte{
		0xA1, 0xF9, 0x5A, 0x67, 0xB9, 0x3D, 0x1E, 0x72, 0xE8, 0xC5,
		0x71, 0xF1, 0x4C, 0xB2, 0xAA, 0x6D,
	}

	var prvObj = NewPrivateKey(KeyVariant_SIKE)
	var pubObj = NewPublicKey(KeyVariant_SIKE)

	if pubObj.Import(pk[:]) != nil || prvObj.Import(sk[:]) != nil {
		t.Error("Can't import one of the keys")
	}

	res, _ := Decapsulate(prvObj, pubObj, ct[:])
	if !bytes.Equal(ssExp[:], res) {
		t.Error("Wrong decapsulation result")
	}
}

/* -------------------------------------------------------------------------
   Benchmarking
   -------------------------------------------------------------------------*/

func BenchmarkSidhKeyAgreement(b *testing.B) {
	// KeyPairs
	alicePublic := convToPub(tdata.PkA_sidh, KeyVariant_SIDH_A)
	alicePrivate := convToPrv(tdata.PrA_sidh, KeyVariant_SIDH_A)
	bobPublic := convToPub(tdata.PkB_sidh, KeyVariant_SIDH_B)
	bobPrivate := convToPrv(tdata.PrB_sidh, KeyVariant_SIDH_B)

	for i := 0; i < b.N; i++ {
		// Derive shared secret
		DeriveSecret(bobPrivate, alicePublic)
		DeriveSecret(alicePrivate, bobPublic)
	}
}

func BenchmarkAliceKeyGenPrv(b *testing.B) {
	prv := NewPrivateKey(KeyVariant_SIDH_A)
	for n := 0; n < b.N; n++ {
		prv.Generate(rand.Reader)
	}
}

func BenchmarkBobKeyGenPrv(b *testing.B) {
	prv := NewPrivateKey(KeyVariant_SIDH_B)
	for n := 0; n < b.N; n++ {
		prv.Generate(rand.Reader)
	}
}

func BenchmarkAliceKeyGenPub(b *testing.B) {
	prv := NewPrivateKey(KeyVariant_SIDH_A)
	prv.Generate(rand.Reader)
	for n := 0; n < b.N; n++ {
		prv.GeneratePublicKey()
	}
}

func BenchmarkBobKeyGenPub(b *testing.B) {
	prv := NewPrivateKey(KeyVariant_SIDH_B)
	prv.Generate(rand.Reader)
	for n := 0; n < b.N; n++ {
		prv.GeneratePublicKey()
	}
}

func BenchmarkSharedSecretAlice(b *testing.B) {
	aPr := convToPrv(tdata.PrA_sidh, KeyVariant_SIDH_A)
	bPk := convToPub(tdata.PkB_sike, KeyVariant_SIDH_B)
	for n := 0; n < b.N; n++ {
		DeriveSecret(aPr, bPk)
	}
}

func BenchmarkSharedSecretBob(b *testing.B) {
	// m_B = 3*randint(0,3^238)
	aPk := convToPub(tdata.PkA_sidh, KeyVariant_SIDH_A)
	bPr := convToPrv(tdata.PrB_sidh, KeyVariant_SIDH_B)
	for n := 0; n < b.N; n++ {
		DeriveSecret(bPr, aPk)
	}
}
