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
// Code optimizations for MIPS, 2013.

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


int32_t WebRtcSpl_SqrtFloor(int32_t value)
{
  int32_t root = 0, tmp1, tmp2, tmp3, tmp4;

  __asm __volatile(
    ".set   push                                       \n\t"
    ".set   noreorder                                  \n\t"

    "lui    %[tmp1],      0x4000                       \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "sub    %[tmp3],      %[value],     %[tmp1]        \n\t"
    "lui    %[tmp1],      0x1                          \n\t"
    "or     %[tmp4],      %[root],      %[tmp1]        \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x4000         \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      14                           \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x8000         \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x2000         \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      13                           \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x4000         \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x1000         \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      12                           \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x2000         \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x800          \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      11                           \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x1000         \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x400          \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      10                           \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x800          \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x200          \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      9                            \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],       0x400         \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x100          \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      8                            \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x200          \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x80           \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      7                            \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x100          \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x40           \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      6                            \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x80           \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x20           \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      5                            \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x40           \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x10           \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      4                            \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x20           \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x8            \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      3                            \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x10           \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x4            \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      2                            \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x8            \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x2            \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "sll    %[tmp1],      1                            \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "subu   %[tmp3],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x4            \n\t"
    "movz   %[value],     %[tmp3],      %[tmp2]        \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    "addiu  %[tmp1],      $0,           0x1            \n\t"
    "addu   %[tmp1],      %[tmp1],      %[root]        \n\t"
    "slt    %[tmp2],      %[value],     %[tmp1]        \n\t"
    "ori    %[tmp4],      %[root],      0x2            \n\t"
    "movz   %[root],      %[tmp4],      %[tmp2]        \n\t"

    ".set   pop                                        \n\t"

    : [root] "+r" (root), [value] "+r" (value),
      [tmp1] "=&r" (tmp1), [tmp2] "=&r" (tmp2),
      [tmp3] "=&r" (tmp3), [tmp4] "=&r" (tmp4)
    :
  );

  return root >> 1;
}

