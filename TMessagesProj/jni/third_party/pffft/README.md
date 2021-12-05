# Notes on PFFFT
We strongly recommend to **read this file** before using PFFFT and to **always wrap** the original C library within a C++ wrapper.

[Example of PFFFT wrapper](https://cs.chromium.org/chromium/src/third_party/webrtc/modules/audio_processing/utility/pffft_wrapper.h).

## Scratch buffer
The caller can optionally provide a scratch buffer. When not provided, VLA is used to provide a thread-safe option.
However, it is recommended to write a C++ wrapper which allocates its own scratch buffer.
Note that the scratch buffer has the same memory alignment requirements of the input and output vectors.

## Output layout
PFFFT computes the forward transform with two possible output layouts:
1. ordered
2. unordered

### Ordered layout
Calling `pffft_transform_ordered` produces an array of **interleaved real and imaginary parts**.
The last Fourier coefficient is purely real and stored in the imaginary part of the DC component (which is also purely real).

### Unordered layout
Calling `pffft_transform` produces an array with a more complex structure, but in a more efficient way than `pffft_transform_ordered`.
Below, the output produced by Matlab and that produced by PFFFT are compared.
The comparison is made for a 32 point transform of a 16 sample buffer.
A 32 point transform has been chosen as this is the minimum supported by PFFFT.

Important notes:
- In Matlab the DC (Matlab index 1 [R1, I1]]) and Nyquist (Matlab index 17 [R17, I17]) values are not repeated as complex conjugates.
- In PFFFT the Nyquist real and imaginary parts ([R17, I17]) are omitted entirely.
- In PFFFT the final 8 values (4 real and 4 imaginary) are not in the same order as all of the others.
- In PFFFT all imaginary parts are stored as negatives (like second half in Matlab).

```
+-------+-----------+-------+-------+
| Index |  Matlab   | Index | PFFFT |
+-------+-----------+-------+-------+
|     1 | R1 + I1   |     0 | R1    |
|     2 | R2+ I2    |     1 | R2    |
|     3 | R3 + I3   |     2 | R3    |
|     4 | R4 + I4   |     3 | R4    |
|     5 | R5 + I5   |     4 | -I1   |
|     6 | R6 + I6   |     5 | -I2   |
|     7 | R7 + I7   |     6 | -I3   |
|     8 | R8 + I8   |     7 | -I4   |
|     9 | R9 + I9   |     8 | R5    |
|    10 | R10 + I10 |     9 | R6    |
|    11 | R11 + I11 |    10 | R7    |
|    12 | R12 + I12 |    11 | R8    |
|    13 | R13 + I13 |    12 | -I5   |
|    14 | R14 + I14 |    13 | -I6   |
|    15 | R15 + I15 |    14 | -I7   |
|    16 | R16 + I16 |    15 | -I8   |
|    17 | R17 + I17 |    16 | R9    |
|    18 | R16 - I16 |    17 | R10   |
|    19 | R15 - I15 |    18 | R11   |
|    20 | R14 - I14 |    19 | R12   |
|    21 | R13 - I13 |    20 | -I9   |
|    22 | R12 - I12 |    21 | -I10  |
|    23 | R11 - I11 |    22 | -I11  |
|    24 | R10 - I10 |    23 | -I12  |
|    25 | R9 - I9   |    24 | R13   |
|    26 | R8 - I8   |    25 | R16   |
|    27 | R7 - I7   |    26 | R15   |
|    28 | R6 - I6   |    27 | R14   |
|    29 | R5 - I5   |    28 | -I13  |
|    30 | R4 - I4   |    29 | -I16  |
|    31 | R3 - I3   |    30 | -I15  |
|    32 | R2 - I2   |    31 | -I14  |
+-------+-----------+-------+-------+
```
