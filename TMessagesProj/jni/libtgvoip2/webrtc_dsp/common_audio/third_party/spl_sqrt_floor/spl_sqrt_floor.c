/*
 * Written by Wilco Dijkstra, 1996. The following email exchange establishes the
 * license.
 *
 * From: Wilco Dijkstra <Wilco.Dijkstra@ntlworld.com>
 * Date: Fri, Jun 24, 2011 at 3:20 AM
 * Subject: Re: sqrt routine
 * To: Kevin Ma <kma@google.com>
 * Hi Kevin,
 * Thanks for asking. Those routines are public domain (originally posted to
 * comp.sys.arm a long time ago), so you can use them freely for any purpose.
 * Cheers,
 * Wilco
 *
 * ----- Original Message -----
 * From: "Kevin Ma" <kma@google.com>
 * To: <Wilco.Dijkstra@ntlworld.com>
 * Sent: Thursday, June 23, 2011 11:44 PM
 * Subject: Fwd: sqrt routine
 * Hi Wilco,
 * I saw your sqrt routine from several web sites, including
 * http://www.finesse.demon.co.uk/steven/sqrt.html.
 * Just wonder if there's any copyright information with your Successive
 * approximation routines, or if I can freely use it for any purpose.
 * Thanks.
 * Kevin
 */

// Minor modifications in code style for WebRTC, 2012.

#include "common_audio/third_party/spl_sqrt_floor/spl_sqrt_floor.h"

/*
 * Algorithm:
 * Successive approximation of the equation (root + delta) ^ 2 = N
 * until delta < 1. If delta < 1 we have the integer part of SQRT (N).
 * Use delta = 2^i for i = 15 .. 0.
 *
 * Output precision is 16 bits. Note for large input values (close to
 * 0x7FFFFFFF), bit 15 (the highest bit of the low 16-bit half word)
 * contains the MSB information (a non-sign value). Do with caution
 * if you need to cast the output to int16_t type.
 *
 * If the input value is negative, it returns 0.
 */

#define WEBRTC_SPL_SQRT_ITER(N)                 \
  try1 = root + (1 << (N));                     \
  if (value >= try1 << (N))                     \
  {                                             \
    value -= try1 << (N);                       \
    root |= 2 << (N);                           \
  }

int32_t WebRtcSpl_SqrtFloor(int32_t value)
{
  int32_t root = 0, try1;

  WEBRTC_SPL_SQRT_ITER (15);
  WEBRTC_SPL_SQRT_ITER (14);
  WEBRTC_SPL_SQRT_ITER (13);
  WEBRTC_SPL_SQRT_ITER (12);
  WEBRTC_SPL_SQRT_ITER (11);
  WEBRTC_SPL_SQRT_ITER (10);
  WEBRTC_SPL_SQRT_ITER ( 9);
  WEBRTC_SPL_SQRT_ITER ( 8);
  WEBRTC_SPL_SQRT_ITER ( 7);
  WEBRTC_SPL_SQRT_ITER ( 6);
  WEBRTC_SPL_SQRT_ITER ( 5);
  WEBRTC_SPL_SQRT_ITER ( 4);
  WEBRTC_SPL_SQRT_ITER ( 3);
  WEBRTC_SPL_SQRT_ITER ( 2);
  WEBRTC_SPL_SQRT_ITER ( 1);
  WEBRTC_SPL_SQRT_ITER ( 0);

  return root >> 1;
}
