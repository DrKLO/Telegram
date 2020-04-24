@
@ Written by Wilco Dijkstra, 1996. The following email exchange establishes the
@ license.
@
@ From: Wilco Dijkstra <Wilco.Dijkstra@ntlworld.com>
@ Date: Fri, Jun 24, 2011 at 3:20 AM
@ Subject: Re: sqrt routine
@ To: Kevin Ma <kma@google.com>
@ Hi Kevin,
@ Thanks for asking. Those routines are public domain (originally posted to
@ comp.sys.arm a long time ago), so you can use them freely for any purpose.
@ Cheers,
@ Wilco
@
@ ----- Original Message -----
@ From: "Kevin Ma" <kma@google.com>
@ To: <Wilco.Dijkstra@ntlworld.com>
@ Sent: Thursday, June 23, 2011 11:44 PM
@ Subject: Fwd: sqrt routine
@ Hi Wilco,
@ I saw your sqrt routine from several web sites, including
@ http://www.finesse.demon.co.uk/steven/sqrt.html.
@ Just wonder if there's any copyright information with your Successive
@ approximation routines, or if I can freely use it for any purpose.
@ Thanks.
@ Kevin

@ Minor modifications in code style for WebRTC, 2012.
@ Output is bit-exact with the reference C code in spl_sqrt_floor.c.

@ Input :             r0 32 bit unsigned integer
@ Output:             r0 = INT (SQRT (r0)), precision is 16 bits
@ Registers touched:  r1, r2
#if defined(__arm__) || defined(_M_ARM) || defined(__aarch64__)

#include "rtc_base/system/asm_defines.h"

GLOBAL_FUNCTION WebRtcSpl_SqrtFloor
.align  2
DEFINE_FUNCTION WebRtcSpl_SqrtFloor
  mov    r1, #3 << 30
  mov    r2, #1 << 30

  @ unroll for i = 0 .. 15

  cmp    r0, r2, ror #2 * 0
  subhs  r0, r0, r2, ror #2 * 0
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 1
  subhs  r0, r0, r2, ror #2 * 1
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 2
  subhs  r0, r0, r2, ror #2 * 2
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 3
  subhs  r0, r0, r2, ror #2 * 3
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 4
  subhs  r0, r0, r2, ror #2 * 4
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 5
  subhs  r0, r0, r2, ror #2 * 5
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 6
  subhs  r0, r0, r2, ror #2 * 6
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 7
  subhs  r0, r0, r2, ror #2 * 7
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 8
  subhs  r0, r0, r2, ror #2 * 8
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 9
  subhs  r0, r0, r2, ror #2 * 9
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 10
  subhs  r0, r0, r2, ror #2 * 10
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 11
  subhs  r0, r0, r2, ror #2 * 11
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 12
  subhs  r0, r0, r2, ror #2 * 12
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 13
  subhs  r0, r0, r2, ror #2 * 13
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 14
  subhs  r0, r0, r2, ror #2 * 14
  adc    r2, r1, r2, lsl #1

  cmp    r0, r2, ror #2 * 15
  subhs  r0, r0, r2, ror #2 * 15
  adc    r2, r1, r2, lsl #1

  bic    r0, r2, #3 << 30  @ for rounding add: cmp r0, r2  adc r2, #1
  bx lr

#endif
