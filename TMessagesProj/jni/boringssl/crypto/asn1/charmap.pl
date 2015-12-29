#!/usr/local/bin/perl -w

# Written by Dr Stephen N Henson (steve@openssl.org) for the OpenSSL project
# 2000.
#
# ====================================================================
# Copyright (c) 2000 The OpenSSL Project.  All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
#
# 1. Redistributions of source code must retain the above copyright
#    notice, this list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright
#    notice, this list of conditions and the following disclaimer in
#    the documentation and/or other materials provided with the
#    distribution.
#
# 3. All advertising materials mentioning features or use of this
#    software must display the following acknowledgment:
#    "This product includes software developed by the OpenSSL Project
#    for use in the OpenSSL Toolkit. (http://www.OpenSSL.org/)"
#
# 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
#    endorse or promote products derived from this software without
#    prior written permission. For written permission, please contact
#    licensing@OpenSSL.org.
#
# 5. Products derived from this software may not be called "OpenSSL"
#    nor may "OpenSSL" appear in their names without prior written
#    permission of the OpenSSL Project.
#
# 6. Redistributions of any form whatsoever must retain the following
#    acknowledgment:
#    "This product includes software developed by the OpenSSL Project
#    for use in the OpenSSL Toolkit (http://www.OpenSSL.org/)"
#
# THIS SOFTWARE IS PROVIDED BY THE OpenSSL PROJECT ``AS IS'' AND ANY
# EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
# PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
# ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
# NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
# LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
# HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
# STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
# ====================================================================
#
# This product includes cryptographic software written by Eric Young
# (eay@cryptsoft.com).  This product includes software written by Tim
# Hudson (tjh@cryptsoft.com).

use strict;

my ($i, @arr);

# Set up an array with the type of ASCII characters
# Each set bit represents a character property.

# RFC2253 character properties
my $RFC2253_ESC = 1;	# Character escaped with \
my $ESC_CTRL	= 2;	# Escaped control character
# These are used with RFC1779 quoting using "
my $NOESC_QUOTE	= 8;	# Not escaped if quoted
my $PSTRING_CHAR = 0x10;	# Valid PrintableString character
my $RFC2253_FIRST_ESC = 0x20; # Escaped with \ if first character
my $RFC2253_LAST_ESC = 0x40;  # Escaped with \ if last character

for($i = 0; $i < 128; $i++) {
	# Set the RFC2253 escape characters (control)
	$arr[$i] = 0;
	if(($i < 32) || ($i > 126)) {
		$arr[$i] |= $ESC_CTRL;
	}

	# Some PrintableString characters
	if(		   ( ( $i >= ord("a")) && ( $i <= ord("z")) )
			|| (  ( $i >= ord("A")) && ( $i <= ord("Z")) )
			|| (  ( $i >= ord("0")) && ( $i <= ord("9")) )  ) {
		$arr[$i] |= $PSTRING_CHAR;
	}
}

# Now setup the rest

# Remaining RFC2253 escaped characters

$arr[ord(" ")] |= $NOESC_QUOTE | $RFC2253_FIRST_ESC | $RFC2253_LAST_ESC;
$arr[ord("#")] |= $NOESC_QUOTE | $RFC2253_FIRST_ESC;

$arr[ord(",")] |= $NOESC_QUOTE | $RFC2253_ESC;
$arr[ord("+")] |= $NOESC_QUOTE | $RFC2253_ESC;
$arr[ord("\"")] |= $RFC2253_ESC;
$arr[ord("\\")] |= $RFC2253_ESC;
$arr[ord("<")] |= $NOESC_QUOTE | $RFC2253_ESC;
$arr[ord(">")] |= $NOESC_QUOTE | $RFC2253_ESC;
$arr[ord(";")] |= $NOESC_QUOTE | $RFC2253_ESC;

# Remaining PrintableString characters

$arr[ord(" ")] |= $PSTRING_CHAR;
$arr[ord("'")] |= $PSTRING_CHAR;
$arr[ord("(")] |= $PSTRING_CHAR;
$arr[ord(")")] |= $PSTRING_CHAR;
$arr[ord("+")] |= $PSTRING_CHAR;
$arr[ord(",")] |= $PSTRING_CHAR;
$arr[ord("-")] |= $PSTRING_CHAR;
$arr[ord(".")] |= $PSTRING_CHAR;
$arr[ord("/")] |= $PSTRING_CHAR;
$arr[ord(":")] |= $PSTRING_CHAR;
$arr[ord("=")] |= $PSTRING_CHAR;
$arr[ord("?")] |= $PSTRING_CHAR;

# Now generate the C code

print <<EOF;
/* Auto generated with chartype.pl script.
 * Mask of various character properties
 */

static const unsigned char char_type[] = {
EOF

for($i = 0; $i < 128; $i++) {
	print("\n") if($i && (($i % 16) == 0));
	printf("%2d", $arr[$i]);
	print(",") if ($i != 127);
}
print("\n};\n\n");

