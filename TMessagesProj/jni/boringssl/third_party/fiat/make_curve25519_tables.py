#!/usr/bin/env python
# coding=utf-8
# The MIT License (MIT)
#
# Copyright (c) 2015-2016 the fiat-crypto authors (see the AUTHORS file).
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import StringIO
import subprocess

# Base field Z_p
p = 2**255 - 19

def modp_inv(x):
    return pow(x, p-2, p)

# Square root of -1
modp_sqrt_m1 = pow(2, (p-1) // 4, p)

# Compute corresponding x-coordinate, with low bit corresponding to
# sign, or return None on failure
def recover_x(y, sign):
    if y >= p:
        return None
    x2 = (y*y-1) * modp_inv(d*y*y+1)
    if x2 == 0:
        if sign:
            return None
        else:
            return 0

    # Compute square root of x2
    x = pow(x2, (p+3) // 8, p)
    if (x*x - x2) % p != 0:
        x = x * modp_sqrt_m1 % p
    if (x*x - x2) % p != 0:
        return None

    if (x & 1) != sign:
        x = p - x
    return x

# Curve constant
d = -121665 * modp_inv(121666) % p

# Base point
g_y = 4 * modp_inv(5) % p
g_x = recover_x(g_y, 0)

# Points are represented as affine tuples (x, y).

def point_add(P, Q):
    x1, y1 = P
    x2, y2 = Q
    x3 = ((x1*y2 + y1*x2) * modp_inv(1 + d*x1*x2*y1*y2)) % p
    y3 = ((y1*y2 + x1*x2) * modp_inv(1 - d*x1*x2*y1*y2)) % p
    return (x3, y3)

# Computes Q = s * P
def point_mul(s, P):
    Q = (0, 1)  # Neutral element
    while s > 0:
        if s & 1:
            Q = point_add(Q, P)
        P = point_add(P, P)
        s >>= 1
    return Q

def to_bytes(x):
    ret = bytearray(32)
    for i in range(len(ret)):
        ret[i] = x % 256
        x >>= 8
    assert x == 0
    return ret

def to_ge_precomp(P):
    # typedef struct {
    #   fe_loose yplusx;
    #   fe_loose yminusx;
    #   fe_loose xy2d;
    # } ge_precomp;
    x, y = P
    return ((y + x) % p, (y - x) % p, (x * y * 2 * d) % p)

def to_base_25_5(x):
    limbs = (26, 25, 26, 25, 26, 25, 26, 25, 26, 25)
    ret = []
    for l in limbs:
        ret.append(x & ((1<<l) - 1))
        x >>= l
    assert x == 0
    return ret

def to_base_51(x):
    ret = []
    for _ in range(5):
        ret.append(x & ((1<<51) - 1))
        x >>= 51
    assert x == 0
    return ret

def to_literal(x):
    ret = "{{\n#if defined(BORINGSSL_CURVE25519_64BIT)\n"
    ret += ", ".join(map(str, to_base_51(x)))
    ret += "\n#else\n"
    ret += ", ".join(map(str, to_base_25_5(x)))
    ret += "\n#endif\n}}"
    return ret

def main():
    d2 = (2 * d) % p

    small_precomp = bytearray()
    for i in range(1, 16):
        s = (i&1) | ((i&2) << (64-1)) | ((i&4) << (128-2)) | ((i&8) << (192-3))
        P = point_mul(s, (g_x, g_y))
        small_precomp += to_bytes(P[0])
        small_precomp += to_bytes(P[1])

    large_precomp = []
    for i in range(32):
        large_precomp.append([])
        for j in range(8):
            P = point_mul((j + 1) << (i * 8), (g_x, g_y))
            large_precomp[-1].append(to_ge_precomp(P))

    bi_precomp = []
    for i in range(8):
        P = point_mul(2*i + 1, (g_x, g_y))
        bi_precomp.append(to_ge_precomp(P))


    buf = StringIO.StringIO()
    buf.write("""// The MIT License (MIT)
//
// Copyright (c) 2015-2016 the fiat-crypto authors (see the AUTHORS file).
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

// This file is generated from
//    ./make_curve25519_tables.py > curve25519_tables.h


static const fe d = """)
    buf.write(to_literal(d))
    buf.write(""";

static const fe sqrtm1 = """)
    buf.write(to_literal(modp_sqrt_m1))
    buf.write(""";

static const fe d2 = """)
    buf.write(to_literal(d2))
    buf.write(""";

#if defined(OPENSSL_SMALL)

// This block of code replaces the standard base-point table with a much smaller
// one. The standard table is 30,720 bytes while this one is just 960.
//
// This table contains 15 pairs of group elements, (x, y), where each field
// element is serialised with |fe_tobytes|. If |i| is the index of the group
// element then consider i+1 as a four-bit number: (i₀, i₁, i₂, i₃) (where i₀
// is the most significant bit). The value of the group element is then:
// (i₀×2^192 + i₁×2^128 + i₂×2^64 + i₃)G, where G is the generator.
static const uint8_t k25519SmallPrecomp[15 * 2 * 32] = {""")
    for i, b in enumerate(small_precomp):
        buf.write("0x%02x, " % b)
    buf.write("""
};

#else

// k25519Precomp[i][j] = (j+1)*256^i*B
static const ge_precomp k25519Precomp[32][8] = {
""")
    for child in large_precomp:
        buf.write("{\n")
        for val in child:
            buf.write("{\n")
            for term in val:
                buf.write(to_literal(term) + ",\n")
            buf.write("},\n")
        buf.write("},\n")
    buf.write("""};

#endif  // OPENSSL_SMALL

// Bi[i] = (2*i+1)*B
static const ge_precomp Bi[8] = {
""")
    for val in bi_precomp:
        buf.write("{\n")
        for term in val:
                buf.write(to_literal(term) + ",\n")
        buf.write("},\n")
    buf.write("""};
""")

    proc = subprocess.Popen(["clang-format"], stdin=subprocess.PIPE)
    proc.communicate(buf.getvalue())

if __name__ == "__main__":
    main()
