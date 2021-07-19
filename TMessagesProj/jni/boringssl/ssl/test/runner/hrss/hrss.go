package hrss

import (
	"crypto/hmac"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/binary"
	"io"
	"math/bits"
)

const (
	PublicKeySize  = modQBytes
	CiphertextSize = modQBytes
)

const (
	N         = 701
	Q         = 8192
	mod3Bytes = 140
	modQBytes = 1138
)

const (
	bitsPerWord      = bits.UintSize
	wordsPerPoly     = (N + bitsPerWord - 1) / bitsPerWord
	fullWordsPerPoly = N / bitsPerWord
	bitsInLastWord   = N % bitsPerWord
)

// poly3 represents a degree-N polynomial over GF(3). Each coefficient is
// bitsliced across the |s| and |a| arrays, like this:
//
//   s  |  a  | value
//  -----------------
//   0  |  0  | 0
//   0  |  1  | 1
//   1  |  0  | 2 (aka -1)
//   1  |  1  | <invalid>
//
// ('s' is for sign, and 'a' is just a letter.)
//
// Once bitsliced as such, the following circuits can be used to implement
// addition and multiplication mod 3:
//
//   (s3, a3) = (s1, a1) × (s2, a2)
//   s3 = (s2 ∧ a1) ⊕ (s1 ∧ a2)
//   a3 = (s1 ∧ s2) ⊕ (a1 ∧ a2)
//
//   (s3, a3) = (s1, a1) + (s2, a2)
//   t1 = ~(s1 ∨ a1)
//   t2 = ~(s2 ∨ a2)
//   s3 = (a1 ∧ a2) ⊕ (t1 ∧ s2) ⊕ (t2 ∧ s1)
//   a3 = (s1 ∧ s2) ⊕ (t1 ∧ a2) ⊕ (t2 ∧ a1)
//
// Negating a value just involves swapping s and a.
type poly3 struct {
	s [wordsPerPoly]uint
	a [wordsPerPoly]uint
}

func (p *poly3) trim() {
	p.s[wordsPerPoly-1] &= (1 << bitsInLastWord) - 1
	p.a[wordsPerPoly-1] &= (1 << bitsInLastWord) - 1
}

func (p *poly3) zero() {
	for i := range p.a {
		p.s[i] = 0
		p.a[i] = 0
	}
}

func (p *poly3) fromDiscrete(in *poly) {
	var shift uint
	s := p.s[:]
	a := p.a[:]
	s[0] = 0
	a[0] = 0

	for _, v := range in {
		s[0] >>= 1
		s[0] |= uint((v>>1)&1) << (bitsPerWord - 1)
		a[0] >>= 1
		a[0] |= uint(v&1) << (bitsPerWord - 1)
		shift++
		if shift == bitsPerWord {
			s = s[1:]
			a = a[1:]
			s[0] = 0
			a[0] = 0
			shift = 0
		}
	}

	a[0] >>= bitsPerWord - shift
	s[0] >>= bitsPerWord - shift
}

func (p *poly3) fromModQ(in *poly) int {
	var shift uint
	s := p.s[:]
	a := p.a[:]
	s[0] = 0
	a[0] = 0
	ok := 1

	for _, v := range in {
		vMod3, vOk := modQToMod3(v)
		ok &= vOk

		s[0] >>= 1
		s[0] |= uint((vMod3>>1)&1) << (bitsPerWord - 1)
		a[0] >>= 1
		a[0] |= uint(vMod3&1) << (bitsPerWord - 1)
		shift++
		if shift == bitsPerWord {
			s = s[1:]
			a = a[1:]
			s[0] = 0
			a[0] = 0
			shift = 0
		}
	}

	a[0] >>= bitsPerWord - shift
	s[0] >>= bitsPerWord - shift

	return ok
}

func (p *poly3) fromDiscreteMod3(in *poly) {
	var shift uint
	s := p.s[:]
	a := p.a[:]
	s[0] = 0
	a[0] = 0

	for _, v := range in {
		// This duplicates the 13th bit upwards to the top of the
		// uint16, essentially treating it as a sign bit and converting
		// into a signed int16. The signed value is reduced mod 3,
		// yeilding {-2, -1, 0, 1, 2}.
		v = uint16((int16(v<<3)>>3)%3) & 7

		// We want to map v thus:
		// {-2, -1, 0, 1, 2} -> {1, 2, 0, 1, 2}. We take the bottom
		// three bits and then the constants below, when shifted by
		// those three bits, perform the required mapping.
		s[0] >>= 1
		s[0] |= (0xbc >> v) << (bitsPerWord - 1)
		a[0] >>= 1
		a[0] |= (0x7a >> v) << (bitsPerWord - 1)
		shift++
		if shift == bitsPerWord {
			s = s[1:]
			a = a[1:]
			s[0] = 0
			a[0] = 0
			shift = 0
		}
	}

	a[0] >>= bitsPerWord - shift
	s[0] >>= bitsPerWord - shift
}

func (p *poly3) marshal(out []byte) {
	s := p.s[:]
	a := p.a[:]
	sw := s[0]
	aw := a[0]
	var shift int

	for i := 0; i < 700; i += 5 {
		acc, scale := 0, 1
		for j := 0; j < 5; j++ {
			v := int(aw&1) | int(sw&1)<<1
			acc += scale * v
			scale *= 3

			shift++
			if shift == bitsPerWord {
				s = s[1:]
				a = a[1:]
				sw = s[0]
				aw = a[0]
				shift = 0
			} else {
				sw >>= 1
				aw >>= 1
			}
		}

		out[0] = byte(acc)
		out = out[1:]
	}
}

func (p *poly) fromMod2(in *poly2) {
	var shift uint
	words := in[:]
	word := words[0]

	for i := range p {
		p[i] = uint16(word & 1)
		word >>= 1
		shift++
		if shift == bitsPerWord {
			words = words[1:]
			word = words[0]
			shift = 0
		}
	}
}

func (p *poly) fromMod3(in *poly3) {
	var shift uint
	s := in.s[:]
	a := in.a[:]
	sw := s[0]
	aw := a[0]

	for i := range p {
		p[i] = uint16(aw&1 | (sw&1)<<1)
		aw >>= 1
		sw >>= 1
		shift++
		if shift == bitsPerWord {
			a = a[1:]
			s = s[1:]
			aw = a[0]
			sw = s[0]
			shift = 0
		}
	}
}

func (p *poly) fromMod3ToModQ(in *poly3) {
	var shift uint
	s := in.s[:]
	a := in.a[:]
	sw := s[0]
	aw := a[0]

	for i := range p {
		p[i] = mod3ToModQ(uint16(aw&1 | (sw&1)<<1))
		aw >>= 1
		sw >>= 1
		shift++
		if shift == bitsPerWord {
			a = a[1:]
			s = s[1:]
			aw = a[0]
			sw = s[0]
			shift = 0
		}
	}
}

func lsbToAll(v uint) uint {
	return uint(int(v<<(bitsPerWord-1)) >> (bitsPerWord - 1))
}

func (p *poly3) mulConst(ms, ma uint) {
	ms = lsbToAll(ms)
	ma = lsbToAll(ma)

	for i := range p.a {
		p.s[i], p.a[i] = (ma&p.s[i])^(ms&p.a[i]), (ma&p.a[i])^(ms&p.s[i])
	}
}

func cmovWords(out, in *[wordsPerPoly]uint, mov uint) {
	for i := range out {
		out[i] = (out[i] & ^mov) | (in[i] & mov)
	}
}

func rotWords(out, in *[wordsPerPoly]uint, bits uint) {
	start := bits / bitsPerWord
	n := (N - bits) / bitsPerWord

	for i := uint(0); i < n; i++ {
		out[i] = in[start+i]
	}

	carry := in[wordsPerPoly-1]

	for i := uint(0); i < start; i++ {
		out[n+i] = carry | in[i]<<bitsInLastWord
		carry = in[i] >> (bitsPerWord - bitsInLastWord)
	}

	out[wordsPerPoly-1] = carry
}

// rotBits right-rotates the bits in |in|. bits must be a non-zero power of two
// and less than bitsPerWord.
func rotBits(out, in *[wordsPerPoly]uint, bits uint) {
	if (bits == 0 || (bits & (bits - 1)) != 0 || bits > bitsPerWord/2 || bitsInLastWord < bitsPerWord/2) {
		panic("internal error");
	}

	carry := in[wordsPerPoly-1] << (bitsPerWord - bits)

	for i := wordsPerPoly - 2; i >= 0; i-- {
		out[i] = carry | in[i]>>bits
		carry = in[i] << (bitsPerWord - bits)
	}

	out[wordsPerPoly-1] = carry>>(bitsPerWord-bitsInLastWord) | in[wordsPerPoly-1]>>bits
}

func (p *poly3) rotWords(bits uint, in *poly3) {
	rotWords(&p.s, &in.s, bits)
	rotWords(&p.a, &in.a, bits)
}

func (p *poly3) rotBits(bits uint, in *poly3) {
	rotBits(&p.s, &in.s, bits)
	rotBits(&p.a, &in.a, bits)
}

func (p *poly3) cmov(in *poly3, mov uint) {
	cmovWords(&p.s, &in.s, mov)
	cmovWords(&p.a, &in.a, mov)
}

func (p *poly3) rot(bits uint) {
	if bits > N {
		panic("invalid")
	}
	var shifted poly3

	shift := uint(9)
	for ; (1 << shift) >= bitsPerWord; shift-- {
		shifted.rotWords(1<<shift, p)
		p.cmov(&shifted, lsbToAll(bits>>shift))
	}
	for ; shift < 9; shift-- {
		shifted.rotBits(1<<shift, p)
		p.cmov(&shifted, lsbToAll(bits>>shift))
	}
}

func (p *poly3) fmadd(ms, ma uint, in *poly3) {
	ms = lsbToAll(ms)
	ma = lsbToAll(ma)

	for i := range p.a {
		products := (ma & in.s[i]) ^ (ms & in.a[i])
		producta := (ma & in.a[i]) ^ (ms & in.s[i])

		ns1Ana1 := ^p.s[i] & ^p.a[i]
		ns2Ana2 := ^products & ^producta

		p.s[i], p.a[i] = (p.a[i]&producta)^(ns1Ana1&products)^(p.s[i]&ns2Ana2), (p.s[i]&products)^(ns1Ana1&producta)^(p.a[i]&ns2Ana2)
	}
}

func (p *poly3) modPhiN() {
	factora := uint(int(p.s[wordsPerPoly-1]<<(bitsPerWord-bitsInLastWord)) >> (bitsPerWord - 1))
	factors := uint(int(p.a[wordsPerPoly-1]<<(bitsPerWord-bitsInLastWord)) >> (bitsPerWord - 1))
	ns2Ana2 := ^factors & ^factora

	for i := range p.s {
		ns1Ana1 := ^p.s[i] & ^p.a[i]
		p.s[i], p.a[i] = (p.a[i]&factora)^(ns1Ana1&factors)^(p.s[i]&ns2Ana2), (p.s[i]&factors)^(ns1Ana1&factora)^(p.a[i]&ns2Ana2)
	}
}

func (p *poly3) cswap(other *poly3, swap uint) {
	for i := range p.s {
		sums := swap & (p.s[i] ^ other.s[i])
		p.s[i] ^= sums
		other.s[i] ^= sums

		suma := swap & (p.a[i] ^ other.a[i])
		p.a[i] ^= suma
		other.a[i] ^= suma
	}
}

func (p *poly3) mulx() {
	carrys := (p.s[wordsPerPoly-1] >> (bitsInLastWord - 1)) & 1
	carrya := (p.a[wordsPerPoly-1] >> (bitsInLastWord - 1)) & 1

	for i := range p.s {
		outCarrys := p.s[i] >> (bitsPerWord - 1)
		outCarrya := p.a[i] >> (bitsPerWord - 1)
		p.s[i] <<= 1
		p.a[i] <<= 1
		p.s[i] |= carrys
		p.a[i] |= carrya
		carrys = outCarrys
		carrya = outCarrya
	}
}

func (p *poly3) divx() {
	var carrys, carrya uint

	for i := len(p.s) - 1; i >= 0; i-- {
		outCarrys := p.s[i] & 1
		outCarrya := p.a[i] & 1
		p.s[i] >>= 1
		p.a[i] >>= 1
		p.s[i] |= carrys << (bitsPerWord - 1)
		p.a[i] |= carrya << (bitsPerWord - 1)
		carrys = outCarrys
		carrya = outCarrya
	}
}

type poly2 [wordsPerPoly]uint

func (p *poly2) fromDiscrete(in *poly) {
	var shift uint
	words := p[:]
	words[0] = 0

	for _, v := range in {
		words[0] >>= 1
		words[0] |= uint(v&1) << (bitsPerWord - 1)
		shift++
		if shift == bitsPerWord {
			words = words[1:]
			words[0] = 0
			shift = 0
		}
	}

	words[0] >>= bitsPerWord - shift
}

func (p *poly2) setPhiN() {
	for i := range p {
		p[i] = ^uint(0)
	}
	p[wordsPerPoly-1] &= (1 << bitsInLastWord) - 1
}

func (p *poly2) cswap(other *poly2, swap uint) {
	for i := range p {
		sum := swap & (p[i] ^ other[i])
		p[i] ^= sum
		other[i] ^= sum
	}
}

func (p *poly2) fmadd(m uint, in *poly2) {
	m = ^(m - 1)

	for i := range p {
		p[i] ^= in[i] & m
	}
}

func (p *poly2) lshift1() {
	var carry uint
	for i := range p {
		nextCarry := p[i] >> (bitsPerWord - 1)
		p[i] <<= 1
		p[i] |= carry
		carry = nextCarry
	}
}

func (p *poly2) rshift1() {
	var carry uint
	for i := len(p) - 1; i >= 0; i-- {
		nextCarry := p[i] & 1
		p[i] >>= 1
		p[i] |= carry << (bitsPerWord - 1)
		carry = nextCarry
	}
}

func (p *poly2) rot(bits uint) {
	if bits > N {
		panic("invalid")
	}
	var shifted [wordsPerPoly]uint
	out := (*[wordsPerPoly]uint)(p)

	shift := uint(9)
	for ; (1 << shift) >= bitsPerWord; shift-- {
		rotWords(&shifted, out, 1<<shift)
		cmovWords(out, &shifted, lsbToAll(bits>>shift))
	}
	for ; shift < 9; shift-- {
		rotBits(&shifted, out, 1<<shift)
		cmovWords(out, &shifted, lsbToAll(bits>>shift))
	}
}

type poly [N]uint16

func (in *poly) marshal(out []byte) {
	p := in[:]

	for len(p) >= 8 {
		out[0] = byte(p[0])
		out[1] = byte(p[0]>>8) | byte((p[1]&0x07)<<5)
		out[2] = byte(p[1] >> 3)
		out[3] = byte(p[1]>>11) | byte((p[2]&0x3f)<<2)
		out[4] = byte(p[2]>>6) | byte((p[3]&0x01)<<7)
		out[5] = byte(p[3] >> 1)
		out[6] = byte(p[3]>>9) | byte((p[4]&0x0f)<<4)
		out[7] = byte(p[4] >> 4)
		out[8] = byte(p[4]>>12) | byte((p[5]&0x7f)<<1)
		out[9] = byte(p[5]>>7) | byte((p[6]&0x03)<<6)
		out[10] = byte(p[6] >> 2)
		out[11] = byte(p[6]>>10) | byte((p[7]&0x1f)<<3)
		out[12] = byte(p[7] >> 5)

		p = p[8:]
		out = out[13:]
	}

	// There are four remaining values.
	out[0] = byte(p[0])
	out[1] = byte(p[0]>>8) | byte((p[1]&0x07)<<5)
	out[2] = byte(p[1] >> 3)
	out[3] = byte(p[1]>>11) | byte((p[2]&0x3f)<<2)
	out[4] = byte(p[2]>>6) | byte((p[3]&0x01)<<7)
	out[5] = byte(p[3] >> 1)
	out[6] = byte(p[3] >> 9)
}

func (out *poly) unmarshal(in []byte) bool {
	p := out[:]
	for i := 0; i < 87; i++ {
		p[0] = uint16(in[0]) | uint16(in[1]&0x1f)<<8
		p[1] = uint16(in[1]>>5) | uint16(in[2])<<3 | uint16(in[3]&3)<<11
		p[2] = uint16(in[3]>>2) | uint16(in[4]&0x7f)<<6
		p[3] = uint16(in[4]>>7) | uint16(in[5])<<1 | uint16(in[6]&0xf)<<9
		p[4] = uint16(in[6]>>4) | uint16(in[7])<<4 | uint16(in[8]&1)<<12
		p[5] = uint16(in[8]>>1) | uint16(in[9]&0x3f)<<7
		p[6] = uint16(in[9]>>6) | uint16(in[10])<<2 | uint16(in[11]&7)<<10
		p[7] = uint16(in[11]>>3) | uint16(in[12])<<5

		p = p[8:]
		in = in[13:]
	}

	// There are four coefficients left over
	p[0] = uint16(in[0]) | uint16(in[1]&0x1f)<<8
	p[1] = uint16(in[1]>>5) | uint16(in[2])<<3 | uint16(in[3]&3)<<11
	p[2] = uint16(in[3]>>2) | uint16(in[4]&0x7f)<<6
	p[3] = uint16(in[4]>>7) | uint16(in[5])<<1 | uint16(in[6]&0xf)<<9

	if in[6]&0xf0 != 0 {
		return false
	}

	out[N-1] = 0
	var top int
	for _, v := range out {
		top += int(v)
	}

	out[N-1] = uint16(-top) % Q
	return true
}

func (in *poly) marshalS3(out []byte) {
	p := in[:]
	for len(p) >= 5 {
		out[0] = byte(p[0] + p[1]*3 + p[2]*9 + p[3]*27 + p[4]*81)
		out = out[1:]
		p = p[5:]
	}
}

func (out *poly) unmarshalS3(in []byte) bool {
	p := out[:]
	for i := 0; i < 140; i++ {
		c := in[0]
		if c >= 243 {
			return false
		}
		p[0] = uint16(c % 3)
		p[1] = uint16((c / 3) % 3)
		p[2] = uint16((c / 9) % 3)
		p[3] = uint16((c / 27) % 3)
		p[4] = uint16((c / 81) % 3)

		p = p[5:]
		in = in[1:]
	}

	out[N-1] = 0
	return true
}

func (p *poly) modPhiN() {
	for i := range p {
		p[i] = (p[i] + Q - p[N-1]) % Q
	}
}

func (out *poly) shortSample(in []byte) {
	//  b  a  result
	// 00 00 00
	// 00 01 01
	// 00 10 10
	// 00 11 11
	// 01 00 10
	// 01 01 00
	// 01 10 01
	// 01 11 11
	// 10 00 01
	// 10 01 10
	// 10 10 00
	// 10 11 11
	// 11 00 11
	// 11 01 11
	// 11 10 11
	// 11 11 11

	// 1111 1111 1100 1001 1101 0010 1110 0100
	//   f    f    c    9    d    2    e    4
	const lookup = uint32(0xffc9d2e4)

	p := out[:]
	for i := 0; i < 87; i++ {
		v := binary.LittleEndian.Uint32(in)
		v2 := (v & 0x55555555) + ((v >> 1) & 0x55555555)
		for j := 0; j < 8; j++ {
			p[j] = uint16(lookup >> ((v2 & 15) << 1) & 3)
			v2 >>= 4
		}
		p = p[8:]
		in = in[4:]
	}

	// There are four values remaining.
	v := binary.LittleEndian.Uint32(in)
	v2 := (v & 0x55555555) + ((v >> 1) & 0x55555555)
	for j := 0; j < 4; j++ {
		p[j] = uint16(lookup >> ((v2 & 15) << 1) & 3)
		v2 >>= 4
	}

	out[N-1] = 0
}

func (out *poly) shortSamplePlus(in []byte) {
	out.shortSample(in)

	var sum uint16
	for i := 0; i < N-1; i++ {
		sum += mod3ResultToModQ(out[i] * out[i+1])
	}

	scale := 1 + (1 & (sum >> 12))
	for i := 0; i < len(out); i += 2 {
		out[i] = (out[i] * scale) % 3
	}
}

func mul(out, scratch, a, b []uint16) {
	const schoolbookLimit = 32
	if len(a) < schoolbookLimit {
		for i := 0; i < len(a)*2; i++ {
			out[i] = 0
		}
		for i := range a {
			for j := range b {
				out[i+j] += a[i] * b[j]
			}
		}
		return
	}

	lowLen := len(a) / 2
	highLen := len(a) - lowLen
	aLow, aHigh := a[:lowLen], a[lowLen:]
	bLow, bHigh := b[:lowLen], b[lowLen:]

	for i := 0; i < lowLen; i++ {
		out[i] = aHigh[i] + aLow[i]
	}
	if highLen != lowLen {
		out[lowLen] = aHigh[lowLen]
	}

	for i := 0; i < lowLen; i++ {
		out[highLen+i] = bHigh[i] + bLow[i]
	}
	if highLen != lowLen {
		out[highLen+lowLen] = bHigh[lowLen]
	}

	mul(scratch, scratch[2*highLen:], out[:highLen], out[highLen:highLen*2])
	mul(out[lowLen*2:], scratch[2*highLen:], aHigh, bHigh)
	mul(out, scratch[2*highLen:], aLow, bLow)

	for i := 0; i < lowLen*2; i++ {
		scratch[i] -= out[i] + out[lowLen*2+i]
	}
	if lowLen != highLen {
		scratch[lowLen*2] -= out[lowLen*4]
	}

	for i := 0; i < 2*highLen; i++ {
		out[lowLen+i] += scratch[i]
	}
}

func (out *poly) mul(a, b *poly) {
	var prod, scratch [2 * N]uint16
	mul(prod[:], scratch[:], a[:], b[:])
	for i := range out {
		out[i] = (prod[i] + prod[i+N]) % Q
	}
}

func (p3 *poly3) mulMod3(x, y *poly3) {
	// (𝑥^n - 1) is a multiple of Φ(N) so we can work mod (𝑥^n - 1) here and
	// (reduce mod Φ(N) afterwards.
	x3 := *x
	y3 := *y
	s := x3.s[:]
	a := x3.a[:]
	sw := s[0]
	aw := a[0]
	p3.zero()
	var shift uint
	for i := 0; i < N; i++ {
		p3.fmadd(sw, aw, &y3)
		sw >>= 1
		aw >>= 1
		shift++
		if shift == bitsPerWord {
			s = s[1:]
			a = a[1:]
			sw = s[0]
			aw = a[0]
			shift = 0
		}
		y3.mulx()
	}
	p3.modPhiN()
}

// mod3ToModQ maps {0, 1, 2, 3} to {0, 1, Q-1, 0xffff}
// The case of n == 3 should never happen but is included so that modQToMod3
// can easily catch invalid inputs.
func mod3ToModQ(n uint16) uint16 {
	return uint16(uint64(0xffff1fff00010000) >> (16 * n))
}

// modQToMod3 maps {0, 1, Q-1} to {(0, 0), (0, 1), (1, 0)} and also returns an int
// which is one if the input is in range and zero otherwise.
func modQToMod3(n uint16) (uint16, int) {
	result := (n&3 - (n>>1)&1)
	return result, subtle.ConstantTimeEq(int32(mod3ToModQ(result)), int32(n))
}

// mod3ResultToModQ maps {0, 1, 2, 4} to {0, 1, Q-1, 1}
func mod3ResultToModQ(n uint16) uint16 {
	return ((((uint16(0x13) >> n) & 1) - 1) & 0x1fff) | ((uint16(0x12) >> n) & 1)
	//shift := (uint(0x324) >> (2 * n)) & 3
	//return uint16(uint64(0x00011fff00010000) >> (16 * shift))
}

// mulXMinus1 sets out to a×(𝑥 - 1) mod (𝑥^n - 1)
func (out *poly) mulXMinus1() {
	// Multiplying by (𝑥 - 1) means negating each coefficient and adding in
	// the value of the previous one.
	origOut700 := out[700]

	for i := N - 1; i > 0; i-- {
		out[i] = (Q - out[i] + out[i-1]) % Q
	}
	out[0] = (Q - out[0] + origOut700) % Q
}

func (out *poly) lift(a *poly) {
	// We wish to calculate a/(𝑥-1) mod Φ(N) over GF(3), where Φ(N) is the
	// Nth cyclotomic polynomial, i.e. 1 + 𝑥 + … + 𝑥^700 (since N is prime).

	// 1/(𝑥-1) has a fairly basic structure that we can exploit to speed this up:
	//
	// R.<x> = PolynomialRing(GF(3)…)
	// inv = R.cyclotomic_polynomial(1).inverse_mod(R.cyclotomic_polynomial(n))
	// list(inv)[:15]
	//   [1, 0, 2, 1, 0, 2, 1, 0, 2, 1, 0, 2, 1, 0, 2]
	//
	// This three-element pattern of coefficients repeats for the whole
	// polynomial.
	//
	// Next define the overbar operator such that z̅ = z[0] +
	// reverse(z[1:]). (Index zero of a polynomial here is the coefficient
	// of the constant term. So index one is the coefficient of 𝑥 and so
	// on.)
	//
	// A less odd way to define this is to see that z̅ negates the indexes,
	// so z̅[0] = z[-0], z̅[1] = z[-1] and so on.
	//
	// The use of z̅  is that, when working mod (𝑥^701 - 1), vz[0] = <v,
	// z̅>, vz[1] = <v, 𝑥z̅>, …. (Where <a, b> is the inner product: the sum
	// of the point-wise products.) Although we calculated the inverse mod
	// Φ(N), we can work mod (𝑥^N - 1) and reduce mod Φ(N) at the end.
	// (That's because (𝑥^N - 1) is a multiple of Φ(N).)
	//
	// When working mod (𝑥^N - 1), multiplication by 𝑥 is a right-rotation
	// of the list of coefficients.
	//
	// Thus we can consider what the pattern of z̅, 𝑥z̅, 𝑥^2z̅, … looks like:
	//
	// def reverse(xs):
	//   suffix = list(xs[1:])
	//   suffix.reverse()
	//   return [xs[0]] + suffix
	//
	// def rotate(xs):
	//   return [xs[-1]] + xs[:-1]
	//
	// zoverbar = reverse(list(inv) + [0])
	// xzoverbar = rotate(reverse(list(inv) + [0]))
	// x2zoverbar = rotate(rotate(reverse(list(inv) + [0])))
	//
	// zoverbar[:15]
	//   [1, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1]
	// xzoverbar[:15]
	//   [0, 1, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0]
	// x2zoverbar[:15]
	//   [2, 0, 1, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2]
	//
	// (For a formula for z̅, see lemma two of appendix B.)
	//
	// After the first three elements have been taken care of, all then have
	// a repeating three-element cycle. The next value (𝑥^3z̅) involves
	// three rotations of the first pattern, thus the three-element cycle
	// lines up. However, the discontinuity in the first three elements
	// obviously moves to a different position. Consider the difference
	// between 𝑥^3z̅ and z̅:
	//
	// [x-y for (x,y) in zip(zoverbar, x3zoverbar)][:15]
	//    [0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
	//
	// This pattern of differences is the same for all elements, although it
	// obviously moves right with the rotations.
	//
	// From this, we reach algorithm eight of appendix B.

	// Handle the first three elements of the inner products.
	out[0] = a[0] + a[2]
	out[1] = a[1]
	out[2] = 2*a[0] + a[2]

	// Use the repeating pattern to complete the first three inner products.
	for i := 3; i < 699; i += 3 {
		out[0] += 2*a[i] + a[i+2]
		out[1] += a[i] + 2*a[i+1]
		out[2] += a[i+1] + 2*a[i+2]
	}

	// Handle the fact that the three-element pattern doesn't fill the
	// polynomial exactly (since 701 isn't a multiple of three).
	out[2] += a[700]
	out[0] += 2 * a[699]
	out[1] += a[699] + 2*a[700]

	out[0] = out[0] % 3
	out[1] = out[1] % 3
	out[2] = out[2] % 3

	// Calculate the remaining inner products by taking advantage of the
	// fact that the pattern repeats every three cycles and the pattern of
	// differences is moves with the rotation.
	for i := 3; i < N; i++ {
		// Add twice something is the same as subtracting when working
		// mod 3. Doing it this way avoids underflow. Underflow is bad
		// because "% 3" doesn't work correctly for negative numbers
		// here since underflow will wrap to 2^16-1 and 2^16 isn't a
		// multiple of three.
		out[i] = (out[i-3] + 2*(a[i-2]+a[i-1]+a[i])) % 3
	}

	// Reduce mod Φ(N) by subtracting a multiple of out[700] from every
	// element and convert to mod Q. (See above about adding twice as
	// subtraction.)
	v := out[700] * 2
	for i := range out {
		out[i] = mod3ToModQ((out[i] + v) % 3)
	}

	out.mulXMinus1()
}

func (a *poly) cswap(b *poly, swap uint16) {
	for i := range a {
		sum := swap & (a[i] ^ b[i])
		a[i] ^= sum
		b[i] ^= sum
	}
}

func lt(a, b uint) uint {
	if a < b {
		return ^uint(0)
	}
	return 0
}

func bsMul(s1, a1, s2, a2 uint) (s3, a3 uint) {
	s3 = (a1 & s2) ^ (s1 & a2)
	a3 = (a1 & a2) ^ (s1 & s2)
	return
}

func (out *poly3) invertMod3(in *poly3) {
	// This algorithm follows algorithm 10 in the paper. (Although note that
	// the paper appears to have a bug: k should start at zero, not one.)
	// The best explanation for why it works is in the "Why it works"
	// section of
	// https://assets.onboardsecurity.com/static/downloads/NTRU/resources/NTRUTech014.pdf.
	var k uint
	degF, degG := uint(N-1), uint(N-1)

	var b, c, g poly3
	f := *in

	for i := range g.a {
		g.a[i] = ^uint(0)
	}

	b.a[0] = 1

	var f0s, f0a uint
	stillGoing := ^uint(0)
	for i := 0; i < 2*(N-1)-1; i++ {
		ss, sa := bsMul(f.s[0], f.a[0], g.s[0], g.a[0])
		ss, sa = sa&stillGoing&1, ss&stillGoing&1
		shouldSwap := ^uint(int((ss|sa)-1)>>(bitsPerWord-1)) & lt(degF, degG)
		f.cswap(&g, shouldSwap)
		b.cswap(&c, shouldSwap)
		degF, degG = (degG&shouldSwap)|(degF & ^shouldSwap), (degF&shouldSwap)|(degG&^shouldSwap)
		f.fmadd(ss, sa, &g)
		b.fmadd(ss, sa, &c)

		f.divx()
		f.s[wordsPerPoly-1] &= ((1 << bitsInLastWord) - 1) >> 1
		f.a[wordsPerPoly-1] &= ((1 << bitsInLastWord) - 1) >> 1
		c.mulx()
		c.s[0] &= ^uint(1)
		c.a[0] &= ^uint(1)

		degF--
		k += 1 & stillGoing
		f0s = (stillGoing & f.s[0]) | (^stillGoing & f0s)
		f0a = (stillGoing & f.a[0]) | (^stillGoing & f0a)
		stillGoing = ^uint(int(degF-1) >> (bitsPerWord - 1))
	}

	k -= N & lt(N, k)
	*out = b
	out.rot(k)
	out.mulConst(f0s, f0a)
	out.modPhiN()
}

func (out *poly) invertMod2(a *poly) {
	// This algorithm follows mix of algorithm 10 in the paper and the first
	// page of the PDF linked below. (Although note that the paper appears
	// to have a bug: k should start at zero, not one.) The best explanation
	// for why it works is in the "Why it works" section of
	// https://assets.onboardsecurity.com/static/downloads/NTRU/resources/NTRUTech014.pdf.
	var k uint
	degF, degG := uint(N-1), uint(N-1)

	var f poly2
	f.fromDiscrete(a)
	var b, c, g poly2
	g.setPhiN()
	b[0] = 1

	stillGoing := ^uint(0)
	for i := 0; i < 2*(N-1)-1; i++ {
		s := uint(f[0]&1) & stillGoing
		shouldSwap := ^(s - 1) & lt(degF, degG)
		f.cswap(&g, shouldSwap)
		b.cswap(&c, shouldSwap)
		degF, degG = (degG&shouldSwap)|(degF & ^shouldSwap), (degF&shouldSwap)|(degG&^shouldSwap)
		f.fmadd(s, &g)
		b.fmadd(s, &c)

		f.rshift1()
		c.lshift1()

		degF--
		k += 1 & stillGoing
		stillGoing = ^uint(int(degF-1) >> (bitsPerWord - 1))
	}

	k -= N & lt(N, k)
	b.rot(k)
	out.fromMod2(&b)
}

func (out *poly) invert(origA *poly) {
	// Inversion mod Q, which is done based on the result of inverting mod
	// 2. See the NTRU paper, page three.
	var a, tmp, tmp2, b poly
	b.invertMod2(origA)

	// Negate a.
	for i := range a {
		a[i] = Q - origA[i]
	}

	// We are working mod Q=2**13 and we need to iterate ceil(log_2(13))
	// times, which is four.
	for i := 0; i < 4; i++ {
		tmp.mul(&a, &b)
		tmp[0] += 2
		tmp2.mul(&b, &tmp)
		b = tmp2
	}

	*out = b
}

type PublicKey struct {
	h poly
}

func ParsePublicKey(in []byte) (*PublicKey, bool) {
	ret := new(PublicKey)
	if !ret.h.unmarshal(in) {
		return nil, false
	}
	return ret, true
}

func (pub *PublicKey) Marshal() []byte {
	ret := make([]byte, modQBytes)
	pub.h.marshal(ret)
	return ret
}

func (pub *PublicKey) Encap(rand io.Reader) (ciphertext []byte, sharedKey []byte) {
	var randBytes [352 + 352]byte
	if _, err := io.ReadFull(rand, randBytes[:]); err != nil {
		panic("rand failed")
	}

	var m, r poly
	m.shortSample(randBytes[:352])
	r.shortSample(randBytes[352:])

	var mBytes, rBytes [mod3Bytes]byte
	m.marshalS3(mBytes[:])
	r.marshalS3(rBytes[:])

	ciphertext = pub.owf(&m, &r)

	h := sha256.New()
	h.Write([]byte("shared key\x00"))
	h.Write(mBytes[:])
	h.Write(rBytes[:])
	h.Write(ciphertext)
	sharedKey = h.Sum(nil)

	return ciphertext, sharedKey
}

func (pub *PublicKey) owf(m, r *poly) []byte {
	for i := range r {
		r[i] = mod3ToModQ(r[i])
	}

	var mq poly
	mq.lift(m)

	var e poly
	e.mul(r, &pub.h)
	for i := range e {
		e[i] = (e[i] + mq[i]) % Q
	}

	ret := make([]byte, modQBytes)
	e.marshal(ret[:])
	return ret
}

type PrivateKey struct {
	PublicKey
	f, fp   poly3
	hInv    poly
	hmacKey [32]byte
}

func (priv *PrivateKey) Marshal() []byte {
	var ret [2*mod3Bytes + modQBytes]byte
	priv.f.marshal(ret[:])
	priv.fp.marshal(ret[mod3Bytes:])
	priv.h.marshal(ret[2*mod3Bytes:])
	return ret[:]
}

func (priv *PrivateKey) Decap(ciphertext []byte) (sharedKey []byte, ok bool) {
	if len(ciphertext) != modQBytes {
		return nil, false
	}

	var e poly
	if !e.unmarshal(ciphertext) {
		return nil, false
	}

	var f poly
	f.fromMod3ToModQ(&priv.f)

	var v1, m poly
	v1.mul(&e, &f)

	var v13 poly3
	v13.fromDiscreteMod3(&v1)
	// Note: v13 is not reduced mod phi(n).

	var m3 poly3
	m3.mulMod3(&v13, &priv.fp)
	m3.modPhiN()
	m.fromMod3(&m3)

	var mLift, delta poly
	mLift.lift(&m)
	for i := range delta {
		delta[i] = (e[i] - mLift[i] + Q) % Q
	}
	delta.mul(&delta, &priv.hInv)
	delta.modPhiN()

	var r poly3
	allOk := r.fromModQ(&delta)

	var mBytes, rBytes [mod3Bytes]byte
	m.marshalS3(mBytes[:])
	r.marshal(rBytes[:])

	var rPoly poly
	rPoly.fromMod3(&r)
	expectedCiphertext := priv.PublicKey.owf(&m, &rPoly)

	allOk &= subtle.ConstantTimeCompare(ciphertext, expectedCiphertext)

	hmacHash := hmac.New(sha256.New, priv.hmacKey[:])
	hmacHash.Write(ciphertext)
	hmacDigest := hmacHash.Sum(nil)

	h := sha256.New()
	h.Write([]byte("shared key\x00"))
	h.Write(mBytes[:])
	h.Write(rBytes[:])
	h.Write(ciphertext)
	sharedKey = h.Sum(nil)

	mask := uint8(allOk - 1)
	for i := range sharedKey {
		sharedKey[i] = (sharedKey[i] & ^mask) | (hmacDigest[i] & mask)
	}

	return sharedKey, true
}

func GenerateKey(rand io.Reader) PrivateKey {
	var randBytes [352 + 352]byte
	if _, err := io.ReadFull(rand, randBytes[:]); err != nil {
		panic("rand failed")
	}

	var f poly
	f.shortSamplePlus(randBytes[:352])
	var priv PrivateKey
	priv.f.fromDiscrete(&f)
	priv.fp.invertMod3(&priv.f)

	var g poly
	g.shortSamplePlus(randBytes[352:])

	var pgPhi1 poly
	for i := range g {
		pgPhi1[i] = mod3ToModQ(g[i])
	}
	for i := range pgPhi1 {
		pgPhi1[i] = (pgPhi1[i] * 3) % Q
	}
	pgPhi1.mulXMinus1()

	var fModQ poly
	fModQ.fromMod3ToModQ(&priv.f)

	var pfgPhi1 poly
	pfgPhi1.mul(&fModQ, &pgPhi1)

	var i poly
	i.invert(&pfgPhi1)

	priv.h.mul(&i, &pgPhi1)
	priv.h.mul(&priv.h, &pgPhi1)

	priv.hInv.mul(&i, &fModQ)
	priv.hInv.mul(&priv.hInv, &fModQ)

	return priv
}
