#! /usr/bin/env perl
#
# April 2019
#
# Abstract: field arithmetic in aarch64 assembly for SIDH/p434

$flavour = shift;
$output  = shift;
if ($flavour =~ /\./) { $output = $flavour; undef $flavour; }

$0 =~ m/(.*[\/\\])[^\/\\]+$/; $dir=$1;
( $xlate="${dir}arm-xlate.pl" and -f $xlate ) or
( $xlate="${dir}../../../crypto/perlasm/arm-xlate.pl" and -f $xlate) or
die "can't locate arm-xlate.pl";

open OUT,"| \"$^X\" \"$xlate\" $flavour \"$output\"";
*STDOUT=*OUT;

$PREFIX="sike";

$code.=<<___;
.section  .rodata

# p434 x 2
.Lp434x2:
    .quad  0xFFFFFFFFFFFFFFFE, 0xFFFFFFFFFFFFFFFF
    .quad  0xFB82ECF5C5FFFFFF, 0xF78CB8F062B15D47
    .quad  0xD9F8BFAD038A40AC, 0x0004683E4E2EE688

# p434 + 1
.Lp434p1:
    .quad  0xFDC1767AE3000000, 0x7BC65C783158AEA3
    .quad  0x6CFC5FD681C52056, 0x0002341F27177344

.text
___

# Computes C0-C2 = A0 * (B0-B1)
# Inputs remain intact
sub mul64x128 {
    my ($A0,$B0,$B1,$C0,$C1,$C2,$T0,$T1)=@_;
    my $body=<<___;
        mul     $T1, $A0, $B0
        umulh   $B0, $A0, $B0
        adds    $C0, $C0, $C2
        adc     $C1, $C1, xzr

        mul     $T0, $A0, $B1
        umulh   $B1, $A0, $B1
        adds    $C0, $C0, $T1
        adcs    $C1, $C1, $B0
        adc     $C2, xzr, xzr

        adds    $C1, $C1, $T0
        adc     $C2, $C2, $B1
___
    return $body;
}

# Computes C0-C4 = A0 * (B0-B3)
# Inputs remain intact
sub mul64x256 {
    my ($A0,$B0,$B1,$B2,$B3,$C0,$C1,$C2,$C3,$C4,$T0,$T1,$T2)=@_;
    my $body=<<___;
        mul     $C0, $A0, $B0    // C0
        umulh   $T0, $A0, $B0

        mul     $C1, $A0, $B1
        umulh   $T1, $A0, $B1
        adds    $C1, $C1, $T0    // C1
        adc     $T0, xzr, xzr

        mul     $C2, $A0, $B2
        umulh   $T2, $A0, $B2
        adds    $T1, $T0, $T1
        adcs    $C2, $C2, $T1    // C2
        adc     $T0, xzr, xzr

        mul     $C3, $A0, $B3
        umulh   $C4, $A0, $B3
        adds    $T2, $T0, $T2
        adcs    $C3, $C3, $T2    // C3
        adc     $C4, $C4, xzr    // C4
___
    return $body;
}

# Computes C0-C4 = (A0-A1) * (B0-B3)
# Inputs remain intact
sub mul128x256 {
    my ($A0,$A1,$B0,$B1,$B2,$B3,$C0,$C1,$C2,$C3,$C4,$C5,$T0,$T1,$T2,$T3)=@_;
    my $body=<<___;
        mul     $C0, $A0, $B0  // C0
        umulh   $C3, $A0, $B0

        mul     $C1, $A0, $B1
        umulh   $C2, $A0, $B1

        mul     $T0, $A1, $B0
        umulh   $T1, $A1, $B0
        adds    $C1, $C1, $C3
        adc     $C2, $C2, xzr

        mul     $T2, $A0, $B2
        umulh   $T3, $A0, $B2
        adds    $C1, $C1, $T0  // C1
        adcs    $C2, $C2, $T1
        adc     $C3, xzr, xzr

        mul     $T0, $A1, $B1
        umulh   $T1, $A1, $B1
        adds    $C2, $C2, $T2
        adcs    $C3, $C3, $T3
        adc     $C4, xzr, xzr

        mul     $T2, $A0, $B3
        umulh   $T3, $A0, $B3
        adds    $C2, $C2, $T0  // C2
        adcs    $C3, $C3, $T1
        adc     $C4, $C4, xzr

        mul     $T0, $A1, $B2
        umulh   $T1, $A1, $B2
        adds    $C3, $C3, $T2
        adcs    $C4, $C4, $T3
        adc     $C5, xzr, xzr

        mul     $T2, $A1, $B3
        umulh   $T3, $A1, $B3
        adds    $C3, $C3, $T0  // C3
        adcs    $C4, $C4, $T1
        adc     $C5, $C5, xzr
        adds    $C4, $C4, $T2  // C4
        adc     $C5, $C5, $T3  // C5

___
    return $body;
}

# Computes C0-C5 = (A0-A2) * (B0-B2)
# Inputs remain intact
sub mul192 {
    my ($A0,$A1,$A2,$B0,$B1,$B2,$C0,$C1,$C2,$C3,$C4,$C5,$T0,$T1,$T2,$T3)=@_;
    my $body=<<___;

        // A0 * B0
        mul     $C0, $A0, $B0  // C0
        umulh   $C3, $A0, $B0

        // A0 * B1
        mul     $C1, $A0, $B1
        umulh   $C2, $A0, $B1

        // A1 * B0
        mul     $T0, $A1, $B0
        umulh   $T1, $A1, $B0
        adds    $C1, $C1, $C3
        adc     $C2, $C2, xzr

        // A0 * B2
        mul     $T2, $A0, $B2
        umulh   $T3, $A0, $B2
        adds    $C1, $C1, $T0  // C1
        adcs    $C2, $C2, $T1
        adc     $C3, xzr, xzr

        // A2 * B0
        mul     $T0, $A2, $B0
        umulh   $C4, $A2, $B0
        adds    $C2, $C2, $T2
        adcs    $C3, $C3, $C4
        adc     $C4, xzr, xzr

        // A1 * B1
        mul     $T2, $A1, $B1
        umulh   $T1, $A1, $B1
        adds    $C2, $C2, $T0
        adcs    $C3, $C3, $T3
        adc     $C4, $C4, xzr

        // A1 * B2
        mul     $T0, $A1, $B2
        umulh   $T3, $A1, $B2
        adds    $C2, $C2, $T2 // C2
        adcs    $C3, $C3, $T1
        adc     $C4, $C4, xzr

        // A2 * B1
        mul     $T2, $A2, $B1
        umulh   $T1, $A2, $B1
        adds    $C3, $C3, $T0
        adcs    $C4, $C4, $T3
        adc     $C5, xzr, xzr

        // A2 * B2
        mul     $T0, $A2, $B2
        umulh   $T3, $A2, $B2
        adds    $C3, $C3, $T2 // C3
        adcs    $C4, $C4, $T1
        adc     $C5, $C5, xzr

        adds    $C4, $C4, $T0 // C4
        adc     $C5, $C5, $T3 // C5
___
    return $body;
}
sub mul256_karatsuba {
    my ($M,$A0,$A1,$A2,$A3,$B0,$B1,$B2,$B3,$C0,$C1,$C2,$C3,$C4,$C5,$C6,$C7,$T0,$T1)=@_;
    # (AH+AL) x (BH+BL), low part
    my $mul_low=&mul64x128($A1, $C6, $T1, $C3, $C4, $C5, $C7, $A0);
    # AL x BL
    my $mul_albl=&mul64x128($A1, $B0, $B1, $C1, $T1, $C7, $C6, $A0);
    # AH x BH
    my $mul_ahbh=&mul64x128($A3, $B2, $B3, $A1, $C6, $B0, $B1, $A2);
    my $body=<<___;
        // A0-A1 <- AH + AL, T0 <- mask
        adds    $A0, $A0, $A2
        adcs    $A1, $A1, $A3
        adc     $T0, xzr, xzr

        // C6, T1 <- BH + BL, C7 <- mask
        adds    $C6, $B0, $B2
        adcs    $T1, $B1, $B3
        adc     $C7, xzr, xzr

        // C0-C1 <- masked (BH + BL)
        sub     $C2, xzr, $T0
        sub     $C3, xzr, $C7
        and     $C0, $C6, $C2
        and     $C1, $T1, $C2

        // C4-C5 <- masked (AH + AL), T0 <- combined carry
        and     $C4, $A0, $C3
        and     $C5, $A1, $C3
        mul     $C2, $A0, $C6
        mul     $C3, $A0, $T1
        and     $T0, $T0, $C7

        // C0-C1, T0 <- (AH+AL) x (BH+BL), part 1
        adds    $C0, $C4, $C0
        umulh   $C4, $A0, $T1
        adcs    $C1, $C5, $C1
        umulh   $C5, $A0, $C6
        adc     $T0, $T0, xzr

        // C2-C5 <- (AH+AL) x (BH+BL), low part
        $mul_low
        ldp     $A0, $A1, [$M,#0]

        // C2-C5, T0 <- (AH+AL) x (BH+BL), final part
        adds    $C4, $C0, $C4
        umulh   $C7, $A0, $B0
        umulh   $T1, $A0, $B1
        adcs    $C5, $C1, $C5
        mul     $C0, $A0, $B0
        mul     $C1, $A0, $B1
        adc     $T0, $T0, xzr

        // C0-C1, T1, C7 <- AL x BL
        $mul_albl

        // C2-C5, T0 <- (AH+AL) x (BH+BL) - ALxBL
        mul     $A0, $A2, $B2
        umulh   $B0, $A2, $B2
        subs    $C2, $C2, $C0
        sbcs    $C3, $C3, $C1
        sbcs    $C4, $C4, $T1
        mul     $A1, $A2, $B3
        umulh   $C6, $A2, $B3
        sbcs    $C5, $C5, $C7
        sbc     $T0, $T0, xzr

        // A0, A1, C6, B0 <- AH x BH
        $mul_ahbh

        // C2-C5, T0 <- (AH+AL) x (BH+BL) - ALxBL - AHxBH
        subs    $C2, $C2, $A0
        sbcs    $C3, $C3, $A1
        sbcs    $C4, $C4, $C6
        sbcs    $C5, $C5, $B0
        sbc     $T0, $T0, xzr

        adds    $C2, $C2, $T1
        adcs    $C3, $C3, $C7
        adcs    $C4, $C4, $A0
        adcs    $C5, $C5, $A1
        adcs    $C6, $T0, $C6
        adc     $C7, $B0, xzr
___
    return $body;
}

# 512-bit integer multiplication using Karatsuba (two levels),
# Comba (lower level).
# Operation: c [x2] = a [x0] * b [x1]
sub mul {
    # (AH+AL) x (BH+BL), low part
    my $mul_kc_low=&mul256_karatsuba(
        "x2",                                           # M0
        "x3","x4","x5","x6",                            # A0-A3
        "x10","x11","x12","x13",                        # B0-B3
        "x8","x9","x19","x20","x21","x22","x23","x24",  # C0-C7
        "x25","x26");                                   # TMP
    # AL x BL
    my $mul_albl=&mul256_karatsuba(
        "x0",                                           # M0f
        "x3","x4","x5","x6",                            # A0-A3
        "x10","x11","x12","x13",                        # B0-B3
        "x21","x22","x23","x24","x25","x26","x27","x28",# C0-C7
        "x8","x9");                                     # TMP
    # AH x BH
    my $mul_ahbh=&mul192(
        "x3","x4","x5",                                 # A0-A2
        "x10","x11","x12",                              # B0-B2
        "x21","x22","x23","x24","x25","x26",            # C0-C5
        "x8","x9","x27","x28");                         # TMP

    my $body=<<___;
        .global ${PREFIX}_mpmul
        .align 4
        ${PREFIX}_mpmul:
        stp     x29, x30, [sp,#-96]!
        add     x29, sp, #0
        stp     x19, x20, [sp,#16]
        stp     x21, x22, [sp,#32]
        stp     x23, x24, [sp,#48]
        stp     x25, x26, [sp,#64]
        stp     x27, x28, [sp,#80]

        ldp      x3,  x4, [x0]
        ldp      x5,  x6, [x0,#16]
        ldp      x7,  x8, [x0,#32]
        ldr      x9,      [x0,#48]
        ldp     x10, x11, [x1,#0]
        ldp     x12, x13, [x1,#16]
        ldp     x14, x15, [x1,#32]
        ldr     x16,      [x1,#48]

        // x3-x7 <- AH + AL, x7 <- carry
        adds    x3, x3, x7
        adcs    x4, x4, x8
        adcs    x5, x5, x9
        adcs    x6, x6, xzr
        adc     x7, xzr, xzr

        // x10-x13 <- BH + BL, x8 <- carry
        adds    x10, x10, x14
        adcs    x11, x11, x15
        adcs    x12, x12, x16
        adcs    x13, x13, xzr
        adc     x8, xzr, xzr

        // x9 <- combined carry
        and      x9, x7, x8
        // x7-x8 <- mask
        sub      x7, xzr, x7
        sub      x8, xzr, x8

        // x15-x19 <- masked (BH + BL)
        and     x14, x10, x7
        and     x15, x11, x7
        and     x16, x12, x7
        and     x17, x13, x7

        // x20-x23 <- masked (AH + AL)
        and     x20, x3, x8
        and     x21, x4, x8
        and     x22, x5, x8
        and     x23, x6, x8

        // x15-x19, x7 <- masked (AH+AL) + masked (BH+BL), step 1
        adds    x14, x14, x20
        adcs    x15, x15, x21
        adcs    x16, x16, x22
        adcs    x17, x17, x23
        adc     x7, x9, xzr

        // x8-x9,x19,x20-x24 <- (AH+AL) x (BH+BL), low part
        stp     x3, x4, [x2,#0]
        $mul_kc_low

        // x15-x19, x7 <- (AH+AL) x (BH+BL), final step
        adds    x14, x14, x21
        adcs    x15, x15, x22
        adcs    x16, x16, x23
        adcs    x17, x17, x24
        adc     x7, x7, xzr

        // Load AL
        ldp     x3, x4, [x0]
        ldp     x5, x6, [x0,#16]
        // Load BL
        ldp     x10, x11, [x1,#0]
        ldp     x12, x13, [x1,#16]

        // Temporarily store x8 in x2
        stp     x8, x9, [x2,#0]
        // x21-x28 <- AL x BL
        $mul_albl
        // Restore x8
        ldp     x8, x9, [x2,#0]

        // x8-x10,x20,x15-x17,x19 <- maskd (AH+AL) x (BH+BL) - ALxBL
        subs    x8, x8, x21
        sbcs    x9, x9, x22
        sbcs    x19, x19, x23
        sbcs    x20, x20, x24
        sbcs    x14, x14, x25
        sbcs    x15, x15, x26
        sbcs    x16, x16, x27
        sbcs    x17, x17, x28
        sbc     x7, x7, xzr

        // Store ALxBL, low
        stp     x21, x22, [x2]
        stp     x23, x24, [x2,#16]

        // Load AH
        ldp     x3, x4, [x0,#32]
        ldr     x5,     [x0,#48]
        // Load BH
        ldp     x10, x11, [x1,#32]
        ldr     x12,      [x1,#48]

        adds     x8,  x8, x25
        adcs     x9,  x9, x26
        adcs    x19, x19, x27
        adcs    x20, x20, x28
        adc     x1, xzr, xzr

        add     x0, x0, #32
        // Temporarily store x8,x9 in x2
        stp     x8,x9, [x2,#32]
        // x21-x28 <- AH x BH
        $mul_ahbh
        // Restore x8,x9
        ldp     x8,x9, [x2,#32]

        neg     x1, x1

        // x8-x9,x19,x20,x14-x17 <- (AH+AL) x (BH+BL) - ALxBL - AHxBH
        subs    x8, x8, x21
        sbcs    x9, x9, x22
        sbcs    x19, x19, x23
        sbcs    x20, x20, x24
        sbcs    x14, x14, x25
        sbcs    x15, x15, x26
        sbcs    x16, x16, xzr
        sbcs    x17, x17, xzr
        sbc     x7, x7, xzr

        // Store (AH+AL) x (BH+BL) - ALxBL - AHxBH, low
        stp      x8,  x9, [x2,#32]
        stp     x19, x20, [x2,#48]

        adds     x1,  x1, #1
        adcs    x14, x14, x21
        adcs    x15, x15, x22
        adcs    x16, x16, x23
        adcs    x17, x17, x24
        adcs    x25,  x7, x25
        adc     x26, x26, xzr

        stp     x14, x15, [x2,#64]
        stp     x16, x17, [x2,#80]
        stp     x25, x26, [x2,#96]

        ldp     x19, x20, [x29,#16]
        ldp     x21, x22, [x29,#32]
        ldp     x23, x24, [x29,#48]
        ldp     x25, x26, [x29,#64]
        ldp     x27, x28, [x29,#80]
        ldp     x29, x30, [sp],#96
        ret
___
    return $body;
}
$code.=&mul();

#  Montgomery reduction
#  Based on method described in Faz-Hernandez et al. https://eprint.iacr.org/2017/1015
#  Operation: mc [x1] = ma [x0]
#  NOTE: ma=mc is not allowed
sub rdc {
    my $mul01=&mul128x256(
        "x2","x3",                     # A0-A1
        "x23","x24","x25","x26",       # B0-B3
        "x4","x5","x6","x7","x8","x9", # C0-C5
        "x10","x11","x27","x28");      # TMP
    my $mul23=&mul128x256(
        "x2","x10",                    # A0-A1
        "x23","x24","x25","x26",       # B0-B3
        "x4","x5","x6","x7","x8","x9", # C0-C5
        "x0","x3","x27","x28");        # TMP
    my $mul45=&mul128x256(
        "x11","x12",                   # A0-A1
        "x23","x24","x25","x26",       # B0-B3
        "x4","x5","x6","x7","x8","x9", # C0-C5
        "x10","x3","x27","x28");       # TMP
    my $mul67=&mul64x256(
        "x13",                         # A0
        "x23","x24","x25","x26",       # B0-B3
        "x4","x5","x6","x7","x8",      # C0-C4
        "x10","x27","x28");            # TMP
    my $body=<<___;
    .global ${PREFIX}_fprdc
    .align 4
    ${PREFIX}_fprdc:
        stp     x29, x30, [sp, #-96]!
        add     x29, sp, xzr
        stp     x19, x20, [sp,#16]
        stp     x21, x22, [sp,#32]
        stp     x23, x24, [sp,#48]
        stp     x25, x26, [sp,#64]
        stp     x27, x28, [sp,#80]

        ldp     x2, x3, [x0,#0]       // a[0-1]

        // Load the prime constant
        adrp    x26, :pg_hi21:.Lp434p1
        add     x26, x26, :lo12:.Lp434p1
        ldp     x23, x24, [x26, #0x0]
        ldp     x25, x26, [x26,#0x10]

        // a[0-1] * p434+1
        $mul01

        ldp     x10, x11, [x0, #0x18]
        ldp     x12, x13, [x0, #0x28]
        ldp     x14, x15, [x0, #0x38]
        ldp     x16, x17, [x0, #0x48]
        ldp     x19, x20, [x0, #0x58]
        ldr     x21,      [x0, #0x68]

        adds     x10, x10, x4
        adcs     x11, x11, x5
        adcs     x12, x12, x6
        adcs     x13, x13, x7
        adcs     x14, x14, x8
        adcs     x15, x15, x9
        adcs     x22, x16, xzr
        adcs     x17, x17, xzr
        adcs     x19, x19, xzr
        adcs     x20, x20, xzr
        adc      x21, x21, xzr

        ldr      x2,  [x0,#0x10]       // a[2]
        // a[2-3] * p434+1
        $mul23

        adds    x12, x12, x4
        adcs    x13, x13, x5
        adcs    x14, x14, x6
        adcs    x15, x15, x7
        adcs    x16, x22, x8
        adcs    x17, x17, x9
        adcs    x22, x19, xzr
        adcs    x20, x20, xzr
        adc     x21, x21, xzr

        $mul45
        adds    x14, x14, x4
        adcs    x15, x15, x5
        adcs    x16, x16, x6
        adcs    x17, x17, x7
        adcs    x19, x22, x8
        adcs    x20, x20, x9
        adc     x22, x21, xzr

        stp     x14, x15, [x1, #0x0]     // C0, C1

        $mul67
        adds    x16, x16, x4
        adcs    x17, x17, x5
        adcs    x19, x19, x6
        adcs    x20, x20, x7
        adc     x21, x22, x8

        str     x16,       [x1, #0x10]
        stp     x17, x19,  [x1, #0x18]
        stp     x20, x21,  [x1, #0x28]

        ldp     x19, x20, [x29,#16]
        ldp     x21, x22, [x29,#32]
        ldp     x23, x24, [x29,#48]
        ldp     x25, x26, [x29,#64]
        ldp     x27, x28, [x29,#80]
        ldp     x29, x30, [sp],#96
        ret
___
}
$code.=&rdc();

#  Field addition
#  Operation: c [x2] = a [x0] + b [x1]
$code.=<<___;
    .global ${PREFIX}_fpadd
    .align 4
    ${PREFIX}_fpadd:
        stp     x29,x30, [sp,#-16]!
        add     x29, sp, #0

        ldp     x3, x4,   [x0,#0]
        ldp     x5, x6,   [x0,#16]
        ldp     x7, x8,   [x0,#32]
        ldr     x9,       [x0,#48]
        ldp     x11, x12, [x1,#0]
        ldp     x13, x14, [x1,#16]
        ldp     x15, x16, [x1,#32]
        ldr     x17,      [x1,#48]

        // Add a + b
        adds    x3, x3, x11
        adcs    x4, x4, x12
        adcs    x5, x5, x13
        adcs    x6, x6, x14
        adcs    x7, x7, x15
        adcs    x8, x8, x16
        adc     x9, x9, x17

        //  Subtract 2xp434
        adrp    x17, :pg_hi21:.Lp434x2
        add     x17, x17, :lo12:.Lp434x2
        ldp     x11, x12, [x17, #0]
        ldp     x13, x14, [x17, #16]
        ldp     x15, x16, [x17, #32]
        subs    x3, x3, x11
        sbcs    x4, x4, x12
        sbcs    x5, x5, x12
        sbcs    x6, x6, x13
        sbcs    x7, x7, x14
        sbcs    x8, x8, x15
        sbcs    x9, x9, x16
        sbc     x0, xzr, xzr    // x0 can be reused now

        // Add 2xp434 anded with the mask in x0
        and     x11, x11, x0
        and     x12, x12, x0
        and     x13, x13, x0
        and     x14, x14, x0
        and     x15, x15, x0
        and     x16, x16, x0

        adds    x3, x3, x11
        adcs    x4, x4, x12
        adcs    x5, x5, x12
        adcs    x6, x6, x13
        adcs    x7, x7, x14
        adcs    x8, x8, x15
        adc     x9, x9, x16

        stp     x3, x4,  [x2,#0]
        stp     x5, x6,  [x2,#16]
        stp     x7, x8,  [x2,#32]
        str     x9,      [x2,#48]

        ldp     x29, x30, [sp],#16
        ret
___

#  Field subtraction
#  Operation: c [x2] = a [x0] - b [x1]
$code.=<<___;
    .global ${PREFIX}_fpsub
    .align 4
    ${PREFIX}_fpsub:
        stp     x29, x30, [sp,#-16]!
        add     x29, sp, #0

        ldp     x3, x4,   [x0,#0]
        ldp     x5, x6,   [x0,#16]
        ldp     x7, x8,   [x0,#32]
        ldr     x9,       [x0,#48]
        ldp     x11, x12, [x1,#0]
        ldp     x13, x14, [x1,#16]
        ldp     x15, x16, [x1,#32]
        ldr     x17,      [x1,#48]

        // Subtract a - b
        subs    x3, x3, x11
        sbcs    x4, x4, x12
        sbcs    x5, x5, x13
        sbcs    x6, x6, x14
        sbcs    x7, x7, x15
        sbcs    x8, x8, x16
        sbcs    x9, x9, x17
        sbc     x0, xzr, xzr

        // Add 2xp434 anded with the mask in x0
        adrp    x17, :pg_hi21:.Lp434x2
        add     x17, x17, :lo12:.Lp434x2

        // First half
        ldp     x11, x12, [x17, #0]
        ldp     x13, x14, [x17, #16]
        ldp     x15, x16, [x17, #32]

        // Add 2xp434 anded with the mask in x0
        and     x11, x11, x0
        and     x12, x12, x0
        and     x13, x13, x0
        and     x14, x14, x0
        and     x15, x15, x0
        and     x16, x16, x0

        adds    x3, x3, x11
        adcs    x4, x4, x12
        adcs    x5, x5, x12
        adcs    x6, x6, x13
        adcs    x7, x7, x14
        adcs    x8, x8, x15
        adc     x9, x9, x16

        stp     x3, x4,  [x2,#0]
        stp     x5, x6,  [x2,#16]
        stp     x7, x8,  [x2,#32]
        str     x9,      [x2,#48]

        ldp     x29, x30, [sp],#16
        ret
___

# 434-bit multiprecision addition
# Operation: c [x2] = a [x0] + b [x1]
$code.=<<___;
    .global ${PREFIX}_mpadd_asm
    .align 4
    ${PREFIX}_mpadd_asm:
        stp     x29, x30, [sp,#-16]!
        add     x29, sp, #0

        ldp     x3, x4,   [x0,#0]
        ldp     x5, x6,   [x0,#16]
        ldp     x7, x8,   [x0,#32]
        ldr     x9,       [x0,#48]
        ldp     x11, x12, [x1,#0]
        ldp     x13, x14, [x1,#16]
        ldp     x15, x16, [x1,#32]
        ldr     x17,      [x1,#48]

        adds    x3, x3, x11
        adcs    x4, x4, x12
        adcs    x5, x5, x13
        adcs    x6, x6, x14
        adcs    x7, x7, x15
        adcs    x8, x8, x16
        adc     x9, x9, x17

        stp     x3, x4,   [x2,#0]
        stp     x5, x6,   [x2,#16]
        stp     x7, x8,   [x2,#32]
        str     x9,       [x2,#48]

        ldp     x29, x30, [sp],#16
        ret
___

# 2x434-bit multiprecision subtraction
# Operation: c [x2] = a [x0] - b [x1].
# Returns borrow mask
$code.=<<___;
    .global ${PREFIX}_mpsubx2_asm
    .align 4
    ${PREFIX}_mpsubx2_asm:
        stp     x29, x30, [sp,#-16]!
        add     x29, sp, #0

        ldp     x3, x4,   [x0,#0]
        ldp     x5, x6,   [x0,#16]
        ldp     x11, x12, [x1,#0]
        ldp     x13, x14, [x1,#16]
        subs    x3, x3, x11
        sbcs    x4, x4, x12
        sbcs    x5, x5, x13
        sbcs    x6, x6, x14
        ldp     x7, x8,   [x0,#32]
        ldp     x9, x10,  [x0,#48]
        ldp     x11, x12, [x1,#32]
        ldp     x13, x14, [x1,#48]
        sbcs    x7, x7, x11
        sbcs    x8, x8, x12
        sbcs    x9, x9, x13
        sbcs    x10, x10, x14

        stp     x3, x4,   [x2,#0]
        stp     x5, x6,   [x2,#16]
        stp     x7, x8,   [x2,#32]
        stp     x9, x10,  [x2,#48]

        ldp     x3, x4,   [x0,#64]
        ldp     x5, x6,   [x0,#80]
        ldp     x11, x12, [x1,#64]
        ldp     x13, x14, [x1,#80]
        sbcs    x3, x3, x11
        sbcs    x4, x4, x12
        sbcs    x5, x5, x13
        sbcs    x6, x6, x14
        ldp     x7, x8,   [x0,#96]
        ldp     x11, x12, [x1,#96]
        sbcs    x7, x7, x11
        sbcs    x8, x8, x12
        sbc     x0, xzr, xzr

        stp     x3, x4,   [x2,#64]
        stp     x5, x6,   [x2,#80]
        stp     x7, x8,   [x2,#96]

        ldp     x29, x30, [sp],#16
        ret
___


# Double 2x434-bit multiprecision subtraction
# Operation: c [x2] = c [x2] - a [x0] - b [x1]
$code.=<<___;
    .global ${PREFIX}_mpdblsubx2_asm
    .align 4
    ${PREFIX}_mpdblsubx2_asm:
        stp     x29, x30, [sp, #-16]!
        add     x29, sp, #0

        ldp     x3, x4,   [x2, #0]
        ldp     x5, x6,   [x2,#16]
        ldp     x7, x8,   [x2,#32]

        ldp     x11, x12, [x0, #0]
        ldp     x13, x14, [x0,#16]
        ldp     x15, x16, [x0,#32]

        subs    x3, x3, x11
        sbcs    x4, x4, x12
        sbcs    x5, x5, x13
        sbcs    x6, x6, x14
        sbcs    x7, x7, x15
        sbcs    x8, x8, x16

        // x9 stores carry
        adc     x9, xzr, xzr

        ldp     x11, x12, [x1, #0]
        ldp     x13, x14, [x1,#16]
        ldp     x15, x16, [x1,#32]
        subs    x3, x3, x11
        sbcs    x4, x4, x12
        sbcs    x5, x5, x13
        sbcs    x6, x6, x14
        sbcs    x7, x7, x15
        sbcs    x8, x8, x16
        adc     x9, x9, xzr

        stp     x3, x4,   [x2, #0]
        stp     x5, x6,   [x2,#16]
        stp     x7, x8,   [x2,#32]

        ldp     x3, x4,   [x2,#48]
        ldp     x5, x6,   [x2,#64]
        ldp     x7, x8,   [x2,#80]

        ldp     x11, x12, [x0,#48]
        ldp     x13, x14, [x0,#64]
        ldp     x15, x16, [x0,#80]

        // x9 = 2 - x9
        neg     x9, x9
        add     x9, x9, #2

        subs    x3, x3, x9
        sbcs    x3, x3, x11
        sbcs    x4, x4, x12
        sbcs    x5, x5, x13
        sbcs    x6, x6, x14
        sbcs    x7, x7, x15
        sbcs    x8, x8, x16
        adc     x9, xzr, xzr

        ldp     x11, x12, [x1,#48]
        ldp     x13, x14, [x1,#64]
        ldp     x15, x16, [x1,#80]
        subs    x3, x3, x11
        sbcs    x4, x4, x12
        sbcs    x5, x5, x13
        sbcs    x6, x6, x14
        sbcs    x7, x7, x15
        sbcs    x8, x8, x16
        adc     x9, x9, xzr

        stp     x3, x4,   [x2,#48]
        stp     x5, x6,   [x2,#64]
        stp     x7, x8,   [x2,#80]

        ldp      x3,  x4, [x2,#96]
        ldp     x11, x12, [x0,#96]
        ldp     x13, x14, [x1,#96]

        // x9 = 2 - x9
        neg     x9, x9
        add     x9, x9, #2

        subs    x3, x3, x9
        sbcs    x3, x3, x11
        sbcs    x4, x4, x12
        subs    x3, x3, x13
        sbc     x4, x4, x14
        stp     x3, x4,   [x2,#96]

        ldp     x29, x30, [sp],#16
        ret
___

foreach (split("\n",$code)) {
  s/\`([^\`]*)\`/eval($1)/ge;
  print $_,"\n";
}

close STDOUT;
