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
	"crypto/sha256"
	"crypto/subtle"
	"errors"
	"io"
)

// Zeroize Fp2
func zeroize(fp *Fp2) {
	// Zeroizing in 2 separated loops tells compiler to
	// use fast runtime.memclr()
	for i := range fp.A {
		fp.A[i] = 0
	}
	for i := range fp.B {
		fp.B[i] = 0
	}
}

// Convert the input to wire format.
//
// The output byte slice must be at least 2*bytelen(p) bytes long.
func convFp2ToBytes(output []byte, fp2 *Fp2) {
	if len(output) < 2*Params.Bytelen {
		panic("output byte slice too short")
	}
	var a Fp2
	fromMontDomain(fp2, &a)

	// convert to bytes in little endian form
	for i := 0; i < Params.Bytelen; i++ {
		// set i = j*8 + k
		tmp := i / 8
		k := uint64(i % 8)
		output[i] = byte(a.A[tmp] >> (8 * k))
		output[i+Params.Bytelen] = byte(a.B[tmp] >> (8 * k))
	}
}

// Read 2*bytelen(p) bytes into the given ExtensionFieldElement.
//
// It is an error to call this function if the input byte slice is less than 2*bytelen(p) bytes long.
func convBytesToFp2(fp2 *Fp2, input []byte) {
	if len(input) < 2*Params.Bytelen {
		panic("input byte slice too short")
	}

	for i := 0; i < Params.Bytelen; i++ {
		j := i / 8
		k := uint64(i % 8)
		fp2.A[j] |= uint64(input[i]) << (8 * k)
		fp2.B[j] |= uint64(input[i+Params.Bytelen]) << (8 * k)
	}
	toMontDomain(fp2)
}

// -----------------------------------------------------------------------------
// Functions for traversing isogeny trees acoording to strategy. Key type 'A' is
//

// Traverses isogeny tree in order to compute xR, xP, xQ and xQmP needed
// for public key generation.
func traverseTreePublicKeyA(curve *ProjectiveCurveParameters, xR, phiP, phiQ, phiR *ProjectivePoint, pub *PublicKey) {
	var points = make([]ProjectivePoint, 0, 8)
	var indices = make([]int, 0, 8)
	var i, sidx int

	cparam := CalcCurveParamsEquiv4(curve)
	phi := NewIsogeny4()
	strat := pub.params.A.IsogenyStrategy
	stratSz := len(strat)

	for j := 1; j <= stratSz; j++ {
		for i <= stratSz-j {
			points = append(points, *xR)
			indices = append(indices, i)

			k := strat[sidx]
			sidx++
			Pow2k(xR, &cparam, 2*k)
			i += int(k)
		}

		cparam = phi.GenerateCurve(xR)
		for k := 0; k < len(points); k++ {
			points[k] = phi.EvaluatePoint(&points[k])
		}

		*phiP = phi.EvaluatePoint(phiP)
		*phiQ = phi.EvaluatePoint(phiQ)
		*phiR = phi.EvaluatePoint(phiR)

		// pop xR from points
		*xR, points = points[len(points)-1], points[:len(points)-1]
		i, indices = int(indices[len(indices)-1]), indices[:len(indices)-1]
	}
}

// Traverses isogeny tree in order to compute xR needed
// for public key generation.
func traverseTreeSharedKeyA(curve *ProjectiveCurveParameters, xR *ProjectivePoint, pub *PublicKey) {
	var points = make([]ProjectivePoint, 0, 8)
	var indices = make([]int, 0, 8)
	var i, sidx int

	cparam := CalcCurveParamsEquiv4(curve)
	phi := NewIsogeny4()
	strat := pub.params.A.IsogenyStrategy
	stratSz := len(strat)

	for j := 1; j <= stratSz; j++ {
		for i <= stratSz-j {
			points = append(points, *xR)
			indices = append(indices, i)

			k := strat[sidx]
			sidx++
			Pow2k(xR, &cparam, 2*k)
			i += int(k)
		}

		cparam = phi.GenerateCurve(xR)
		for k := 0; k < len(points); k++ {
			points[k] = phi.EvaluatePoint(&points[k])
		}

		// pop xR from points
		*xR, points = points[len(points)-1], points[:len(points)-1]
		i, indices = int(indices[len(indices)-1]), indices[:len(indices)-1]
	}
}

// Traverses isogeny tree in order to compute xR, xP, xQ and xQmP needed
// for public key generation.
func traverseTreePublicKeyB(curve *ProjectiveCurveParameters, xR, phiP, phiQ, phiR *ProjectivePoint, pub *PublicKey) {
	var points = make([]ProjectivePoint, 0, 8)
	var indices = make([]int, 0, 8)
	var i, sidx int

	cparam := CalcCurveParamsEquiv3(curve)
	phi := NewIsogeny3()
	strat := pub.params.B.IsogenyStrategy
	stratSz := len(strat)

	for j := 1; j <= stratSz; j++ {
		for i <= stratSz-j {
			points = append(points, *xR)
			indices = append(indices, i)

			k := strat[sidx]
			sidx++
			Pow3k(xR, &cparam, k)
			i += int(k)
		}

		cparam = phi.GenerateCurve(xR)
		for k := 0; k < len(points); k++ {
			points[k] = phi.EvaluatePoint(&points[k])
		}

		*phiP = phi.EvaluatePoint(phiP)
		*phiQ = phi.EvaluatePoint(phiQ)
		*phiR = phi.EvaluatePoint(phiR)

		// pop xR from points
		*xR, points = points[len(points)-1], points[:len(points)-1]
		i, indices = int(indices[len(indices)-1]), indices[:len(indices)-1]
	}
}

// Traverses isogeny tree in order to compute xR, xP, xQ and xQmP needed
// for public key generation.
func traverseTreeSharedKeyB(curve *ProjectiveCurveParameters, xR *ProjectivePoint, pub *PublicKey) {
	var points = make([]ProjectivePoint, 0, 8)
	var indices = make([]int, 0, 8)
	var i, sidx int

	cparam := CalcCurveParamsEquiv3(curve)
	phi := NewIsogeny3()
	strat := pub.params.B.IsogenyStrategy
	stratSz := len(strat)

	for j := 1; j <= stratSz; j++ {
		for i <= stratSz-j {
			points = append(points, *xR)
			indices = append(indices, i)

			k := strat[sidx]
			sidx++
			Pow3k(xR, &cparam, k)
			i += int(k)
		}

		cparam = phi.GenerateCurve(xR)
		for k := 0; k < len(points); k++ {
			points[k] = phi.EvaluatePoint(&points[k])
		}

		// pop xR from points
		*xR, points = points[len(points)-1], points[:len(points)-1]
		i, indices = int(indices[len(indices)-1]), indices[:len(indices)-1]
	}
}

// Generate a public key in the 2-torsion group
func publicKeyGenA(prv *PrivateKey) (pub *PublicKey) {
	var xPA, xQA, xRA ProjectivePoint
	var xPB, xQB, xRB, xK ProjectivePoint
	var invZP, invZQ, invZR Fp2

	pub = NewPublicKey(KeyVariant_SIDH_A)
	var phi = NewIsogeny4()

	// Load points for A
	xPA = ProjectivePoint{X: prv.params.A.Affine_P, Z: prv.params.OneFp2}
	xQA = ProjectivePoint{X: prv.params.A.Affine_Q, Z: prv.params.OneFp2}
	xRA = ProjectivePoint{X: prv.params.A.Affine_R, Z: prv.params.OneFp2}

	// Load points for B
	xRB = ProjectivePoint{X: prv.params.B.Affine_R, Z: prv.params.OneFp2}
	xQB = ProjectivePoint{X: prv.params.B.Affine_Q, Z: prv.params.OneFp2}
	xPB = ProjectivePoint{X: prv.params.B.Affine_P, Z: prv.params.OneFp2}

	// Find isogeny kernel
	xK = ScalarMul3Pt(&pub.params.InitCurve, &xPA, &xQA, &xRA, prv.params.A.SecretBitLen, prv.Scalar)
	traverseTreePublicKeyA(&pub.params.InitCurve, &xK, &xPB, &xQB, &xRB, pub)

	// Secret isogeny
	phi.GenerateCurve(&xK)
	xPA = phi.EvaluatePoint(&xPB)
	xQA = phi.EvaluatePoint(&xQB)
	xRA = phi.EvaluatePoint(&xRB)
	Fp2Batch3Inv(&xPA.Z, &xQA.Z, &xRA.Z, &invZP, &invZQ, &invZR)

	mul(&pub.affine_xP, &xPA.X, &invZP)
	mul(&pub.affine_xQ, &xQA.X, &invZQ)
	mul(&pub.affine_xQmP, &xRA.X, &invZR)
	return
}

// Generate a public key in the 3-torsion group
func publicKeyGenB(prv *PrivateKey) (pub *PublicKey) {
	var xPB, xQB, xRB, xK ProjectivePoint
	var xPA, xQA, xRA ProjectivePoint
	var invZP, invZQ, invZR Fp2

	pub = NewPublicKey(prv.keyVariant)
	var phi = NewIsogeny3()

	// Load points for B
	xRB = ProjectivePoint{X: prv.params.B.Affine_R, Z: prv.params.OneFp2}
	xQB = ProjectivePoint{X: prv.params.B.Affine_Q, Z: prv.params.OneFp2}
	xPB = ProjectivePoint{X: prv.params.B.Affine_P, Z: prv.params.OneFp2}

	// Load points for A
	xPA = ProjectivePoint{X: prv.params.A.Affine_P, Z: prv.params.OneFp2}
	xQA = ProjectivePoint{X: prv.params.A.Affine_Q, Z: prv.params.OneFp2}
	xRA = ProjectivePoint{X: prv.params.A.Affine_R, Z: prv.params.OneFp2}

	xK = ScalarMul3Pt(&pub.params.InitCurve, &xPB, &xQB, &xRB, prv.params.B.SecretBitLen, prv.Scalar)
	traverseTreePublicKeyB(&pub.params.InitCurve, &xK, &xPA, &xQA, &xRA, pub)

	phi.GenerateCurve(&xK)
	xPB = phi.EvaluatePoint(&xPA)
	xQB = phi.EvaluatePoint(&xQA)
	xRB = phi.EvaluatePoint(&xRA)
	Fp2Batch3Inv(&xPB.Z, &xQB.Z, &xRB.Z, &invZP, &invZQ, &invZR)

	mul(&pub.affine_xP, &xPB.X, &invZP)
	mul(&pub.affine_xQ, &xQB.X, &invZQ)
	mul(&pub.affine_xQmP, &xRB.X, &invZR)
	return
}

// -----------------------------------------------------------------------------
// Key agreement functions
//

// Establishing shared keys in in 2-torsion group
func deriveSecretA(prv *PrivateKey, pub *PublicKey) []byte {
	var sharedSecret = make([]byte, pub.params.SharedSecretSize)
	var xP, xQ, xQmP ProjectivePoint
	var xK ProjectivePoint
	var cparam ProjectiveCurveParameters
	var phi = NewIsogeny4()
	var jInv Fp2

	// Recover curve coefficients
	RecoverCoordinateA(&cparam, &pub.affine_xP, &pub.affine_xQ, &pub.affine_xQmP)
	// C=1
	cparam.C = Params.OneFp2

	// Find kernel of the morphism
	xP = ProjectivePoint{X: pub.affine_xP, Z: pub.params.OneFp2}
	xQ = ProjectivePoint{X: pub.affine_xQ, Z: pub.params.OneFp2}
	xQmP = ProjectivePoint{X: pub.affine_xQmP, Z: pub.params.OneFp2}
	xK = ScalarMul3Pt(&cparam, &xP, &xQ, &xQmP, pub.params.A.SecretBitLen, prv.Scalar)

	// Traverse isogeny tree
	traverseTreeSharedKeyA(&cparam, &xK, pub)

	// Calculate j-invariant on isogeneus curve
	c := phi.GenerateCurve(&xK)
	RecoverCurveCoefficients4(&cparam, &c)
	Jinvariant(&cparam, &jInv)
	convFp2ToBytes(sharedSecret, &jInv)
	return sharedSecret
}

// Establishing shared keys in in 3-torsion group
func deriveSecretB(prv *PrivateKey, pub *PublicKey) []byte {
	var sharedSecret = make([]byte, pub.params.SharedSecretSize)
	var xP, xQ, xQmP ProjectivePoint
	var xK ProjectivePoint
	var cparam ProjectiveCurveParameters
	var phi = NewIsogeny3()
	var jInv Fp2

	// Recover curve A coefficient
	RecoverCoordinateA(&cparam, &pub.affine_xP, &pub.affine_xQ, &pub.affine_xQmP)
	// C=1
	cparam.C = Params.OneFp2

	// Find kernel of the morphism
	xP = ProjectivePoint{X: pub.affine_xP, Z: pub.params.OneFp2}
	xQ = ProjectivePoint{X: pub.affine_xQ, Z: pub.params.OneFp2}
	xQmP = ProjectivePoint{X: pub.affine_xQmP, Z: pub.params.OneFp2}
	xK = ScalarMul3Pt(&cparam, &xP, &xQ, &xQmP, pub.params.B.SecretBitLen, prv.Scalar)

	// Traverse isogeny tree
	traverseTreeSharedKeyB(&cparam, &xK, pub)

	// Calculate j-invariant on isogeneus curve
	c := phi.GenerateCurve(&xK)
	RecoverCurveCoefficients3(&cparam, &c)
	Jinvariant(&cparam, &jInv)
	convFp2ToBytes(sharedSecret, &jInv)
	return sharedSecret
}

func encrypt(skA *PrivateKey, pkA, pkB *PublicKey, ptext []byte) ([]byte, error) {
	if pkB.keyVariant != KeyVariant_SIKE {
		return nil, errors.New("wrong key type")
	}

	j, err := DeriveSecret(skA, pkB)
	if err != nil {
		return nil, err
	}

	if len(ptext) != pkA.params.KemSize {
		panic("Implementation error")
	}

	digest := sha256.Sum256(j)
	// Uses truncated digest (first 16-bytes)
	for i, _ := range ptext {
		digest[i] ^= ptext[i]
	}

	ret := make([]byte, pkA.Size()+len(ptext))
	copy(ret, pkA.Export())
	copy(ret[pkA.Size():], digest[:pkA.params.KemSize])
	return ret, nil
}

// NewPrivateKey initializes private key.
// Usage of this function guarantees that the object is correctly initialized.
func NewPrivateKey(v KeyVariant) *PrivateKey {
	prv := &PrivateKey{key: key{params: &Params, keyVariant: v}}
	if (v & KeyVariant_SIDH_A) == KeyVariant_SIDH_A {
		prv.Scalar = make([]byte, prv.params.A.SecretByteLen)
	} else {
		prv.Scalar = make([]byte, prv.params.B.SecretByteLen)
	}
	if v == KeyVariant_SIKE {
		prv.S = make([]byte, prv.params.MsgLen)
	}
	return prv
}

// NewPublicKey initializes public key.
// Usage of this function guarantees that the object is correctly initialized.
func NewPublicKey(v KeyVariant) *PublicKey {
	return &PublicKey{key: key{params: &Params, keyVariant: v}}
}

// Import clears content of the public key currently stored in the structure
// and imports key stored in the byte string. Returns error in case byte string
// size is wrong. Doesn't perform any validation.
func (pub *PublicKey) Import(input []byte) error {
	if len(input) != pub.Size() {
		return errors.New("sidh: input to short")
	}
	ssSz := pub.params.SharedSecretSize
	convBytesToFp2(&pub.affine_xP, input[0:ssSz])
	convBytesToFp2(&pub.affine_xQ, input[ssSz:2*ssSz])
	convBytesToFp2(&pub.affine_xQmP, input[2*ssSz:3*ssSz])
	return nil
}

// Exports currently stored key. In case structure hasn't been filled with key data
// returned byte string is filled with zeros.
func (pub *PublicKey) Export() []byte {
	output := make([]byte, pub.params.PublicKeySize)
	ssSz := pub.params.SharedSecretSize
	convFp2ToBytes(output[0:ssSz], &pub.affine_xP)
	convFp2ToBytes(output[ssSz:2*ssSz], &pub.affine_xQ)
	convFp2ToBytes(output[2*ssSz:3*ssSz], &pub.affine_xQmP)
	return output
}

// Size returns size of the public key in bytes
func (pub *PublicKey) Size() int {
	return pub.params.PublicKeySize
}

// Exports currently stored key. In case structure hasn't been filled with key data
// returned byte string is filled with zeros.
func (prv *PrivateKey) Export() []byte {
	ret := make([]byte, len(prv.Scalar)+len(prv.S))
	copy(ret, prv.S)
	copy(ret[len(prv.S):], prv.Scalar)
	return ret
}

// Size returns size of the private key in bytes
func (prv *PrivateKey) Size() int {
	tmp := len(prv.Scalar)
	if prv.keyVariant == KeyVariant_SIKE {
		tmp += int(prv.params.MsgLen)
	}
	return tmp
}

// Import clears content of the private key currently stored in the structure
// and imports key from octet string. In case of SIKE, the random value 'S'
// must be prepended to the value of actual private key (see SIKE spec for details).
// Function doesn't import public key value to PrivateKey object.
func (prv *PrivateKey) Import(input []byte) error {
	if len(input) != prv.Size() {
		return errors.New("sidh: input to short")
	}
	copy(prv.S, input[:len(prv.S)])
	copy(prv.Scalar, input[len(prv.S):])
	return nil
}

// Generates random private key for SIDH or SIKE. Generated value is
// formed as little-endian integer from key-space <2^(e2-1)..2^e2 - 1>
// for KeyVariant_A or <2^(s-1)..2^s - 1>, where s = floor(log_2(3^e3)),
// for KeyVariant_B.
//
// Returns error in case user provided RNG fails.
func (prv *PrivateKey) Generate(rand io.Reader) error {
	var err error
	var dp *DomainParams

	if (prv.keyVariant & KeyVariant_SIDH_A) == KeyVariant_SIDH_A {
		dp = &prv.params.A
	} else {
		dp = &prv.params.B
	}

	if prv.keyVariant == KeyVariant_SIKE {
		_, err = io.ReadFull(rand, prv.S)
	}

	// Private key generation takes advantage of the fact that keyspace for secret
	// key is (0, 2^x - 1), for some possitivite value of 'x' (see SIKE, 1.3.8).
	// It means that all bytes in the secret key, but the last one, can take any
	// value between <0x00,0xFF>. Similarily for the last byte, but generation
	// needs to chop off some bits, to make sure generated value is an element of
	// a key-space.
	_, err = io.ReadFull(rand, prv.Scalar)
	if err != nil {
		return err
	}
	prv.Scalar[len(prv.Scalar)-1] &= (1 << (dp.SecretBitLen % 8)) - 1
	// Make sure scalar is SecretBitLen long. SIKE spec says that key
	// space starts from 0, but I'm not confortable with having low
	// value scalars used for private keys. It is still secrure as per
	// table 5.1 in [SIKE].
	prv.Scalar[len(prv.Scalar)-1] |= 1 << ((dp.SecretBitLen % 8) - 1)
	return err
}

// Generates public key.
//
// Constant time.
func (prv *PrivateKey) GeneratePublicKey() *PublicKey {
	if (prv.keyVariant & KeyVariant_SIDH_A) == KeyVariant_SIDH_A {
		return publicKeyGenA(prv)
	}
	return publicKeyGenB(prv)
}

// Computes a shared secret which is a j-invariant. Function requires that pub has
// different KeyVariant than prv. Length of returned output is 2*ceil(log_2 P)/8),
// where P is a prime defining finite field.
//
// It's important to notice that each keypair must not be used more than once
// to calculate shared secret.
//
// Function may return error. This happens only in case provided input is invalid.
// Constant time for properly initialized private and public key.
func DeriveSecret(prv *PrivateKey, pub *PublicKey) ([]byte, error) {

	if (pub == nil) || (prv == nil) {
		return nil, errors.New("sidh: invalid arguments")
	}

	if (pub.keyVariant == prv.keyVariant) || (pub.params.Id != prv.params.Id) {
		return nil, errors.New("sidh: public and private are incompatbile")
	}

	if (prv.keyVariant & KeyVariant_SIDH_A) == KeyVariant_SIDH_A {
		return deriveSecretA(prv, pub), nil
	} else {
		return deriveSecretB(prv, pub), nil
	}
}

// Uses SIKE public key to encrypt plaintext. Requires cryptographically secure PRNG
// Returns ciphertext in case encryption succeeds. Returns error in case PRNG fails
// or wrongly formatted input was provided.
func Encrypt(rng io.Reader, pub *PublicKey, ptext []byte) ([]byte, error) {
	var ptextLen = len(ptext)
	// c1 must be security level + 64 bits (see [SIKE] 1.4 and 4.3.3)
	if ptextLen != pub.params.KemSize {
		return nil, errors.New("Unsupported message length")
	}

	skA := NewPrivateKey(KeyVariant_SIDH_A)
	err := skA.Generate(rng)
	if err != nil {
		return nil, err
	}

	pkA := skA.GeneratePublicKey()
	return encrypt(skA, pkA, pub, ptext)
}

// Uses SIKE private key to decrypt ciphertext. Returns plaintext in case
// decryption succeeds or error in case unexptected input was provided.
// Constant time
func Decrypt(prv *PrivateKey, ctext []byte) ([]byte, error) {
	var c1_len int
	n := make([]byte, prv.params.KemSize)
	pk_len := prv.params.PublicKeySize

	if prv.keyVariant != KeyVariant_SIKE {
		return nil, errors.New("wrong key type")
	}

	// ctext is a concatenation of (pubkey_A || c1=ciphertext)
	// it must be security level + 64 bits (see [SIKE] 1.4 and 4.3.3)
	c1_len = len(ctext) - pk_len
	if c1_len != int(prv.params.KemSize) {
		return nil, errors.New("wrong size of cipher text")
	}

	c0 := NewPublicKey(KeyVariant_SIDH_A)
	err := c0.Import(ctext[:pk_len])
	if err != nil {
		return nil, err
	}
	j, err := DeriveSecret(prv, c0)
	if err != nil {
		return nil, err
	}

	digest := sha256.Sum256(j)
	copy(n, digest[:])

	for i, _ := range n {
		n[i] ^= ctext[pk_len+i]
	}
	return n[:c1_len], nil
}

// Encapsulation receives the public key and generates SIKE ciphertext and shared secret.
// The generated ciphertext is used for authentication.
// The rng must be cryptographically secure PRNG.
// Error is returned in case PRNG fails or wrongly formatted input was provided.
func Encapsulate(rng io.Reader, pub *PublicKey) (ctext []byte, secret []byte, err error) {
	// Buffer for random, secret message
	ptext := make([]byte, pub.params.MsgLen)
	// SHA256 hash context object
	d := sha256.New()

	// Generate ephemeral value
	_, err = io.ReadFull(rng, ptext)
	if err != nil {
		return nil, nil, err
	}

	// Implementation uses first 28-bytes of secret
	d.Write(ptext)
	d.Write(pub.Export())
	digest := d.Sum(nil)
	// r = G(ptext||pub)
	r := digest[:pub.params.A.SecretByteLen]

	// (c0 || c1) = Enc(pkA, ptext; r)
	skA := NewPrivateKey(KeyVariant_SIDH_A)
	err = skA.Import(r)
	if err != nil {
		return nil, nil, err
	}

	pkA := skA.GeneratePublicKey()
	ctext, err = encrypt(skA, pkA, pub, ptext)
	if err != nil {
		return nil, nil, err
	}

	// K = H(ptext||(c0||c1))
	d.Reset()
	d.Write(ptext)
	d.Write(ctext)
	digest = d.Sum(digest[:0])
	return ctext, digest[:pub.params.KemSize], nil
}

// Decapsulate given the keypair and ciphertext as inputs, Decapsulate outputs a shared
// secret if plaintext verifies correctly, otherwise function outputs random value.
// Decapsulation may fail in case input is wrongly formatted.
// Constant time for properly initialized input.
func Decapsulate(prv *PrivateKey, pub *PublicKey, ctext []byte) ([]byte, error) {
	var skA = NewPrivateKey(KeyVariant_SIDH_A)
	// SHA256 hash context object
	d := sha256.New()

	m, err := Decrypt(prv, ctext)
	if err != nil {
		return nil, err
	}

	// r' = G(m'||pub)
	d.Write(m)
	d.Write(pub.Export())
	digest := d.Sum(nil)
	// Never fails
	skA.Import(digest[:pub.params.A.SecretByteLen])

	// Never fails
	pkA := skA.GeneratePublicKey()
	c0 := pkA.Export()

	d.Reset()
	if subtle.ConstantTimeCompare(c0, ctext[:len(c0)]) == 1 {
		d.Write(m)
	} else {
		// S is chosen at random when generating a key and is unknown to the other party. It
		// may seem weird, but it's correct. It is important that S is unpredictable
		// to other party. Without this check, it is possible to recover a secret, by
		// providing series of invalid ciphertexts. It is also important that in case
		//
		// See more details in "On the security of supersingular isogeny cryptosystems"
		// (S. Galbraith, et al., 2016, ePrint #859).
		d.Write(prv.S)
	}
	d.Write(ctext)
	digest = d.Sum(digest[:0])
	return digest[:pub.params.KemSize], nil
}
