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

// Interface for working with isogenies.
type isogeny interface {
	// Given a torsion point on a curve computes isogenous curve.
	// Returns curve coefficients (A:C), so that E_(A/C) = E_(A/C)/<P>,
	// where P is a provided projective point. Sets also isogeny constants
	// that are needed for isogeny evaluation.
	GenerateCurve(*ProjectivePoint) CurveCoefficientsEquiv
	// Evaluates isogeny at caller provided point. Requires isogeny curve constants
	// to be earlier computed by GenerateCurve.
	EvaluatePoint(*ProjectivePoint) ProjectivePoint
}

// Stores isogeny 3 curve constants
type isogeny3 struct {
	K1 Fp2
	K2 Fp2
}

// Stores isogeny 4 curve constants
type isogeny4 struct {
	isogeny3
	K3 Fp2
}

// Constructs isogeny3 objects
func NewIsogeny3() isogeny {
	return &isogeny3{}
}

// Constructs isogeny4 objects
func NewIsogeny4() isogeny {
	return &isogeny4{}
}

// Helper function for RightToLeftLadder(). Returns A+2C / 4.
func calcAplus2Over4(cparams *ProjectiveCurveParameters) (ret Fp2) {
	var tmp Fp2

	// 2C
	add(&tmp, &cparams.C, &cparams.C)
	// A+2C
	add(&ret, &cparams.A, &tmp)
	// 1/4C
	add(&tmp, &tmp, &tmp)
	inv(&tmp, &tmp)
	// A+2C/4C
	mul(&ret, &ret, &tmp)
	return
}

// Converts values in x.A and x.B to Montgomery domain
// x.A = x.A * R mod p
// x.B = x.B * R mod p
// Performs v = v*R^2*R^(-1) mod p, for both x.A and x.B
func toMontDomain(x *Fp2) {
	var aRR FpX2

	// convert to montgomery domain
	fpMul(&aRR, &x.A, &R2) // = a*R*R
	fpMontRdc(&x.A, &aRR)  // = a*R mod p
	fpMul(&aRR, &x.B, &R2)
	fpMontRdc(&x.B, &aRR)
}

// Converts values in x.A and x.B from Montgomery domain
// a = x.A mod p
// b = x.B mod p
//
// After returning from the call x is not modified.
func fromMontDomain(x *Fp2, out *Fp2) {
	var aR FpX2

	// convert from montgomery domain
	copy(aR[:], x.A[:])
	fpMontRdc(&out.A, &aR) // = a mod p in [0, 2p)
	fpRdcP(&out.A)         // = a mod p in [0, p)
	for i := range aR {
		aR[i] = 0
	}
	copy(aR[:], x.B[:])
	fpMontRdc(&out.B, &aR)
	fpRdcP(&out.B)
}

// Computes j-invariant for a curve y2=x3+A/Cx+x with A,C in F_(p^2). Result
// is returned in 'j'. Implementation corresponds to Algorithm 9 from SIKE.
func Jinvariant(cparams *ProjectiveCurveParameters, j *Fp2) {
	var t0, t1 Fp2

	sqr(j, &cparams.A)   // j  = A^2
	sqr(&t1, &cparams.C) // t1 = C^2
	add(&t0, &t1, &t1)   // t0 = t1 + t1
	sub(&t0, j, &t0)     // t0 = j - t0
	sub(&t0, &t0, &t1)   // t0 = t0 - t1
	sub(j, &t0, &t1)     // t0 = t0 - t1
	sqr(&t1, &t1)        // t1 = t1^2
	mul(j, j, &t1)       // j = j * t1
	add(&t0, &t0, &t0)   // t0 = t0 + t0
	add(&t0, &t0, &t0)   // t0 = t0 + t0
	sqr(&t1, &t0)        // t1 = t0^2
	mul(&t0, &t0, &t1)   // t0 = t0 * t1
	add(&t0, &t0, &t0)   // t0 = t0 + t0
	add(&t0, &t0, &t0)   // t0 = t0 + t0
	inv(j, j)            // j  = 1/j
	mul(j, &t0, j)       // j  = t0 * j
}

// Given affine points x(P), x(Q) and x(Q-P) in a extension field F_{p^2}, function
// recorvers projective coordinate A of a curve. This is Algorithm 10 from SIKE.
func RecoverCoordinateA(curve *ProjectiveCurveParameters, xp, xq, xr *Fp2) {
	var t0, t1 Fp2

	add(&t1, xp, xq)                        // t1 = Xp + Xq
	mul(&t0, xp, xq)                        // t0 = Xp * Xq
	mul(&curve.A, xr, &t1)                  // A  = X(q-p) * t1
	add(&curve.A, &curve.A, &t0)            // A  = A + t0
	mul(&t0, &t0, xr)                       // t0 = t0 * X(q-p)
	sub(&curve.A, &curve.A, &Params.OneFp2) // A  = A - 1
	add(&t0, &t0, &t0)                      // t0 = t0 + t0
	add(&t1, &t1, xr)                       // t1 = t1 + X(q-p)
	add(&t0, &t0, &t0)                      // t0 = t0 + t0
	sqr(&curve.A, &curve.A)                 // A  = A^2
	inv(&t0, &t0)                           // t0 = 1/t0
	mul(&curve.A, &curve.A, &t0)            // A  = A * t0
	sub(&curve.A, &curve.A, &t1)            // A  = A - t1
}

// Computes equivalence (A:C) ~ (A+2C : A-2C)
func CalcCurveParamsEquiv3(cparams *ProjectiveCurveParameters) CurveCoefficientsEquiv {
	var coef CurveCoefficientsEquiv
	var c2 Fp2

	add(&c2, &cparams.C, &cparams.C)
	// A24p = A+2*C
	add(&coef.A, &cparams.A, &c2)
	// A24m = A-2*C
	sub(&coef.C, &cparams.A, &c2)
	return coef
}

// Computes equivalence (A:C) ~ (A+2C : 4C)
func CalcCurveParamsEquiv4(cparams *ProjectiveCurveParameters) CurveCoefficientsEquiv {
	var coefEq CurveCoefficientsEquiv

	add(&coefEq.C, &cparams.C, &cparams.C)
	// A24p = A+2C
	add(&coefEq.A, &cparams.A, &coefEq.C)
	// C24 = 4*C
	add(&coefEq.C, &coefEq.C, &coefEq.C)
	return coefEq
}

// Recovers (A:C) curve parameters from projectively equivalent (A+2C:A-2C).
func RecoverCurveCoefficients3(cparams *ProjectiveCurveParameters, coefEq *CurveCoefficientsEquiv) {
	add(&cparams.A, &coefEq.A, &coefEq.C)
	// cparams.A = 2*(A+2C+A-2C) = 4A
	add(&cparams.A, &cparams.A, &cparams.A)
	// cparams.C = (A+2C-A+2C) = 4C
	sub(&cparams.C, &coefEq.A, &coefEq.C)
	return
}

// Recovers (A:C) curve parameters from projectively equivalent (A+2C:4C).
func RecoverCurveCoefficients4(cparams *ProjectiveCurveParameters, coefEq *CurveCoefficientsEquiv) {
	// cparams.C = (4C)*1/2=2C
	mul(&cparams.C, &coefEq.C, &Params.HalfFp2)
	// cparams.A = A+2C - 2C = A
	sub(&cparams.A, &coefEq.A, &cparams.C)
	// cparams.C = 2C * 1/2 = C
	mul(&cparams.C, &cparams.C, &Params.HalfFp2)
	return
}

// Combined coordinate doubling and differential addition. Takes projective points
// P,Q,Q-P and (A+2C)/4C curve E coefficient. Returns 2*P and P+Q calculated on E.
// Function is used only by RightToLeftLadder. Corresponds to Algorithm 5 of SIKE
func xDbladd(P, Q, QmP *ProjectivePoint, a24 *Fp2) (dblP, PaQ ProjectivePoint) {
	var t0, t1, t2 Fp2
	xQmP, zQmP := &QmP.X, &QmP.Z
	xPaQ, zPaQ := &PaQ.X, &PaQ.Z
	x2P, z2P := &dblP.X, &dblP.Z
	xP, zP := &P.X, &P.Z
	xQ, zQ := &Q.X, &Q.Z

	add(&t0, xP, zP)      // t0   = Xp+Zp
	sub(&t1, xP, zP)      // t1   = Xp-Zp
	sqr(x2P, &t0)         // 2P.X = t0^2
	sub(&t2, xQ, zQ)      // t2   = Xq-Zq
	add(xPaQ, xQ, zQ)     // Xp+q = Xq+Zq
	mul(&t0, &t0, &t2)    // t0   = t0 * t2
	mul(z2P, &t1, &t1)    // 2P.Z = t1 * t1
	mul(&t1, &t1, xPaQ)   // t1   = t1 * Xp+q
	sub(&t2, x2P, z2P)    // t2   = 2P.X - 2P.Z
	mul(x2P, x2P, z2P)    // 2P.X = 2P.X * 2P.Z
	mul(xPaQ, a24, &t2)   // Xp+q = A24 * t2
	sub(zPaQ, &t0, &t1)   // Zp+q = t0 - t1
	add(z2P, xPaQ, z2P)   // 2P.Z = Xp+q + 2P.Z
	add(xPaQ, &t0, &t1)   // Xp+q = t0 + t1
	mul(z2P, z2P, &t2)    // 2P.Z = 2P.Z * t2
	sqr(zPaQ, zPaQ)       // Zp+q = Zp+q ^ 2
	sqr(xPaQ, xPaQ)       // Xp+q = Xp+q ^ 2
	mul(zPaQ, xQmP, zPaQ) // Zp+q = Xq-p * Zp+q
	mul(xPaQ, zQmP, xPaQ) // Xp+q = Zq-p * Xp+q
	return
}

// Given the curve parameters, xP = x(P), computes xP = x([2^k]P)
// Safe to overlap xP, x2P.
func Pow2k(xP *ProjectivePoint, params *CurveCoefficientsEquiv, k uint32) {
	var t0, t1 Fp2

	x, z := &xP.X, &xP.Z
	for i := uint32(0); i < k; i++ {
		sub(&t0, x, z)           // t0  = Xp - Zp
		add(&t1, x, z)           // t1  = Xp + Zp
		sqr(&t0, &t0)            // t0  = t0 ^ 2
		sqr(&t1, &t1)            // t1  = t1 ^ 2
		mul(z, &params.C, &t0)   // Z2p = C24 * t0
		mul(x, z, &t1)           // X2p = Z2p * t1
		sub(&t1, &t1, &t0)       // t1  = t1 - t0
		mul(&t0, &params.A, &t1) // t0  = A24+ * t1
		add(z, z, &t0)           // Z2p = Z2p + t0
		mul(z, z, &t1)           // Zp  = Z2p * t1
	}
}

// Given the curve parameters, xP = x(P), and k >= 0, compute xP = x([3^k]P).
//
// Safe to overlap xP, xR.
func Pow3k(xP *ProjectivePoint, params *CurveCoefficientsEquiv, k uint32) {
	var t0, t1, t2, t3, t4, t5, t6 Fp2

	x, z := &xP.X, &xP.Z
	for i := uint32(0); i < k; i++ {
		sub(&t0, x, z)           // t0  = Xp - Zp
		sqr(&t2, &t0)            // t2  = t0^2
		add(&t1, x, z)           // t1  = Xp + Zp
		sqr(&t3, &t1)            // t3  = t1^2
		add(&t4, &t1, &t0)       // t4  = t1 + t0
		sub(&t0, &t1, &t0)       // t0  = t1 - t0
		sqr(&t1, &t4)            // t1  = t4^2
		sub(&t1, &t1, &t3)       // t1  = t1 - t3
		sub(&t1, &t1, &t2)       // t1  = t1 - t2
		mul(&t5, &t3, &params.A) // t5  = t3 * A24+
		mul(&t3, &t3, &t5)       // t3  = t5 * t3
		mul(&t6, &t2, &params.C) // t6  = t2 * A24-
		mul(&t2, &t2, &t6)       // t2  = t2 * t6
		sub(&t3, &t2, &t3)       // t3  = t2 - t3
		sub(&t2, &t5, &t6)       // t2  = t5 - t6
		mul(&t1, &t2, &t1)       // t1  = t2 * t1
		add(&t2, &t3, &t1)       // t2  = t3 + t1
		sqr(&t2, &t2)            // t2  = t2^2
		mul(x, &t2, &t4)         // X3p = t2 * t4
		sub(&t1, &t3, &t1)       // t1  = t3 - t1
		sqr(&t1, &t1)            // t1  = t1^2
		mul(z, &t1, &t0)         // Z3p = t1 * t0
	}
}

// Set (y1, y2, y3)  = (1/x1, 1/x2, 1/x3).
//
// All xi, yi must be distinct.
func Fp2Batch3Inv(x1, x2, x3, y1, y2, y3 *Fp2) {
	var x1x2, t Fp2

	mul(&x1x2, x1, x2) // x1*x2
	mul(&t, &x1x2, x3) // 1/(x1*x2*x3)
	inv(&t, &t)
	mul(y1, &t, x2) // 1/x1
	mul(y1, y1, x3)
	mul(y2, &t, x1) // 1/x2
	mul(y2, y2, x3)
	mul(y3, &t, &x1x2) // 1/x3
}

// ScalarMul3Pt is a right-to-left point multiplication that given the
// x-coordinate of P, Q and P-Q calculates the x-coordinate of R=Q+[scalar]P.
// nbits must be smaller or equal to len(scalar).
func ScalarMul3Pt(cparams *ProjectiveCurveParameters, P, Q, PmQ *ProjectivePoint, nbits uint, scalar []uint8) ProjectivePoint {
	var R0, R2, R1 ProjectivePoint
	aPlus2Over4 := calcAplus2Over4(cparams)
	R1 = *P
	R2 = *PmQ
	R0 = *Q

	// Iterate over the bits of the scalar, bottom to top
	prevBit := uint8(0)
	for i := uint(0); i < nbits; i++ {
		bit := (scalar[i>>3] >> (i & 7) & 1)
		swap := prevBit ^ bit
		prevBit = bit
		condSwap(&R1.X, &R1.Z, &R2.X, &R2.Z, swap)
		R0, R2 = xDbladd(&R0, &R2, &R1, &aPlus2Over4)
	}
	condSwap(&R1.X, &R1.Z, &R2.X, &R2.Z, prevBit)
	return R1
}

// Given a three-torsion point p = x(PB) on the curve E_(A:C), construct the
// three-isogeny phi : E_(A:C) -> E_(A:C)/<P_3> = E_(A':C').
//
// Input: (XP_3: ZP_3), where P_3 has exact order 3 on E_A/C
// Output: * Curve coordinates (A' + 2C', A' - 2C') corresponding to E_A'/C' = A_E/C/<P3>
//         * isogeny phi with constants in F_p^2
func (phi *isogeny3) GenerateCurve(p *ProjectivePoint) CurveCoefficientsEquiv {
	var t0, t1, t2, t3, t4 Fp2
	var coefEq CurveCoefficientsEquiv
	var K1, K2 = &phi.K1, &phi.K2

	sub(K1, &p.X, &p.Z)            // K1 = XP3 - ZP3
	sqr(&t0, K1)                   // t0 = K1^2
	add(K2, &p.X, &p.Z)            // K2 = XP3 + ZP3
	sqr(&t1, K2)                   // t1 = K2^2
	add(&t2, &t0, &t1)             // t2 = t0 + t1
	add(&t3, K1, K2)               // t3 = K1 + K2
	sqr(&t3, &t3)                  // t3 = t3^2
	sub(&t3, &t3, &t2)             // t3 = t3 - t2
	add(&t2, &t1, &t3)             // t2 = t1 + t3
	add(&t3, &t3, &t0)             // t3 = t3 + t0
	add(&t4, &t3, &t0)             // t4 = t3 + t0
	add(&t4, &t4, &t4)             // t4 = t4 + t4
	add(&t4, &t1, &t4)             // t4 = t1 + t4
	mul(&coefEq.C, &t2, &t4)       // A24m = t2 * t4
	add(&t4, &t1, &t2)             // t4 = t1 + t2
	add(&t4, &t4, &t4)             // t4 = t4 + t4
	add(&t4, &t0, &t4)             // t4 = t0 + t4
	mul(&t4, &t3, &t4)             // t4 = t3 * t4
	sub(&t0, &t4, &coefEq.C)       // t0 = t4 - A24m
	add(&coefEq.A, &coefEq.C, &t0) // A24p = A24m + t0
	return coefEq
}

// Given a 3-isogeny phi and a point pB = x(PB), compute x(QB), the x-coordinate
// of the image QB = phi(PB) of PB under phi : E_(A:C) -> E_(A':C').
//
// The output xQ = x(Q) is then a point on the curve E_(A':C'); the curve
// parameters are returned by the GenerateCurve function used to construct phi.
func (phi *isogeny3) EvaluatePoint(p *ProjectivePoint) ProjectivePoint {
	var t0, t1, t2 Fp2
	var q ProjectivePoint
	var K1, K2 = &phi.K1, &phi.K2
	var px, pz = &p.X, &p.Z

	add(&t0, px, pz)   // t0 = XQ + ZQ
	sub(&t1, px, pz)   // t1 = XQ - ZQ
	mul(&t0, K1, &t0)  // t2 = K1 * t0
	mul(&t1, K2, &t1)  // t1 = K2 * t1
	add(&t2, &t0, &t1) // t2 = t0 + t1
	sub(&t0, &t1, &t0) // t0 = t1 - t0
	sqr(&t2, &t2)      // t2 = t2 ^ 2
	sqr(&t0, &t0)      // t0 = t0 ^ 2
	mul(&q.X, px, &t2) // XQ'= XQ * t2
	mul(&q.Z, pz, &t0) // ZQ'= ZQ * t0
	return q
}

// Given a four-torsion point p = x(PB) on the curve E_(A:C), construct the
// four-isogeny phi : E_(A:C) -> E_(A:C)/<P_4> = E_(A':C').
//
// Input: (XP_4: ZP_4), where P_4 has exact order 4 on E_A/C
// Output: * Curve coordinates (A' + 2C', 4C') corresponding to E_A'/C' = A_E/C/<P4>
//         * isogeny phi with constants in F_p^2
func (phi *isogeny4) GenerateCurve(p *ProjectivePoint) CurveCoefficientsEquiv {
	var coefEq CurveCoefficientsEquiv
	var xp4, zp4 = &p.X, &p.Z
	var K1, K2, K3 = &phi.K1, &phi.K2, &phi.K3

	sub(K2, xp4, zp4)
	add(K3, xp4, zp4)
	sqr(K1, zp4)
	add(K1, K1, K1)
	sqr(&coefEq.C, K1)
	add(K1, K1, K1)
	sqr(&coefEq.A, xp4)
	add(&coefEq.A, &coefEq.A, &coefEq.A)
	sqr(&coefEq.A, &coefEq.A)
	return coefEq
}

// Given a 4-isogeny phi and a point xP = x(P), compute x(Q), the x-coordinate
// of the image Q = phi(P) of P under phi : E_(A:C) -> E_(A':C').
//
// Input: isogeny returned by GenerateCurve and point q=(Qx,Qz) from E0_A/C
// Output: Corresponding point q from E1_A'/C', where E1 is 4-isogenous to E0
func (phi *isogeny4) EvaluatePoint(p *ProjectivePoint) ProjectivePoint {
	var t0, t1 Fp2
	var q = *p
	var xq, zq = &q.X, &q.Z
	var K1, K2, K3 = &phi.K1, &phi.K2, &phi.K3

	add(&t0, xq, zq)
	sub(&t1, xq, zq)
	mul(xq, &t0, K2)
	mul(zq, &t1, K3)
	mul(&t0, &t0, &t1)
	mul(&t0, &t0, K1)
	add(&t1, xq, zq)
	sub(zq, xq, zq)
	sqr(&t1, &t1)
	sqr(zq, zq)
	add(xq, &t0, &t1)
	sub(&t0, zq, &t0)
	mul(xq, xq, &t1)
	mul(zq, zq, &t0)
	return q
}
