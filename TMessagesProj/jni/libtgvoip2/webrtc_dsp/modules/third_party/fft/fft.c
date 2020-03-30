/*
 * Copyright(c)1995,97 Mark Olesen <olesen@me.QueensU.CA>
 *    Queen's Univ at Kingston (Canada)
 *
 * Permission to use, copy, modify, and distribute this software for
 * any purpose without fee is hereby granted, provided that this
 * entire notice is included in all copies of any software which is
 * or includes a copy or modification of this software and in all
 * copies of the supporting documentation for such software.
 *
 * THIS SOFTWARE IS BEING PROVIDED "AS IS", WITHOUT ANY EXPRESS OR
 * IMPLIED WARRANTY.  IN PARTICULAR, NEITHER THE AUTHOR NOR QUEEN'S
 * UNIVERSITY AT KINGSTON MAKES ANY REPRESENTATION OR WARRANTY OF ANY
 * KIND CONCERNING THE MERCHANTABILITY OF THIS SOFTWARE OR ITS
 * FITNESS FOR ANY PARTICULAR PURPOSE.
 *
 * All of which is to say that you can do what you like with this
 * source code provided you don't try to sell it as your own and you
 * include an unaltered copy of this message (including the
 * copyright).
 *
 * It is also implicitly understood that bug fixes and improvements
 * should make their way back to the general Internet community so
 * that everyone benefits.
 *
 * Changes:
 *   Trivial type modifications by the WebRTC authors.
 */


/*
 * File:
 * WebRtcIsac_Fftn.c
 *
 * Public:
 * WebRtcIsac_Fftn / fftnf ();
 *
 * Private:
 * WebRtcIsac_Fftradix / fftradixf ();
 *
 * Descript:
 * multivariate complex Fourier transform, computed in place
 * using mixed-radix Fast Fourier Transform algorithm.
 *
 * Fortran code by:
 * RC Singleton, Stanford Research Institute, Sept. 1968
 *
 * translated by f2c (version 19950721).
 *
 * int WebRtcIsac_Fftn (int ndim, const int dims[], REAL Re[], REAL Im[],
 *     int iSign, double scaling);
 *
 * NDIM = the total number dimensions
 * DIMS = a vector of array sizes
 * if NDIM is zero then DIMS must be zero-terminated
 *
 * RE and IM hold the real and imaginary components of the data, and return
 * the resulting real and imaginary Fourier coefficients.  Multidimensional
 * data *must* be allocated contiguously.  There is no limit on the number
 * of dimensions.
 *
 * ISIGN = the sign of the complex exponential (ie, forward or inverse FFT)
 * the magnitude of ISIGN (normally 1) is used to determine the
 * correct indexing increment (see below).
 *
 * SCALING = normalizing constant by which the final result is *divided*
 * if SCALING == -1, normalize by total dimension of the transform
 * if SCALING <  -1, normalize by the square-root of the total dimension
 *
 * example:
 * tri-variate transform with Re[n1][n2][n3], Im[n1][n2][n3]
 *
 * int dims[3] = {n1,n2,n3}
 * WebRtcIsac_Fftn (3, dims, Re, Im, 1, scaling);
 *
 *-----------------------------------------------------------------------*
 * int WebRtcIsac_Fftradix (REAL Re[], REAL Im[], size_t nTotal, size_t nPass,
 *   size_t nSpan, int iSign, size_t max_factors,
 *   size_t max_perm);
 *
 * RE, IM - see above documentation
 *
 * Although there is no limit on the number of dimensions, WebRtcIsac_Fftradix() must
 * be called once for each dimension, but the calls may be in any order.
 *
 * NTOTAL = the total number of complex data values
 * NPASS  = the dimension of the current variable
 * NSPAN/NPASS = the spacing of consecutive data values while indexing the
 * current variable
 * ISIGN - see above documentation
 *
 * example:
 * tri-variate transform with Re[n1][n2][n3], Im[n1][n2][n3]
 *
 * WebRtcIsac_Fftradix (Re, Im, n1*n2*n3, n1,       n1, 1, maxf, maxp);
 * WebRtcIsac_Fftradix (Re, Im, n1*n2*n3, n2,    n1*n2, 1, maxf, maxp);
 * WebRtcIsac_Fftradix (Re, Im, n1*n2*n3, n3, n1*n2*n3, 1, maxf, maxp);
 *
 * single-variate transform,
 *    NTOTAL = N = NSPAN = (number of complex data values),
 *
 * WebRtcIsac_Fftradix (Re, Im, n, n, n, 1, maxf, maxp);
 *
 * The data can also be stored in a single array with alternating real and
 * imaginary parts, the magnitude of ISIGN is changed to 2 to give correct
 * indexing increment, and data [0] and data [1] used to pass the initial
 * addresses for the sequences of real and imaginary values,
 *
 * example:
 * REAL data [2*NTOTAL];
 * WebRtcIsac_Fftradix ( &data[0], &data[1], NTOTAL, nPass, nSpan, 2, maxf, maxp);
 *
 * for temporary allocation:
 *
 * MAX_FACTORS >= the maximum prime factor of NPASS
 * MAX_PERM >= the number of prime factors of NPASS.  In addition,
 * if the square-free portion K of NPASS has two or more prime
 * factors, then MAX_PERM >= (K-1)
 *
 * storage in FACTOR for a maximum of 15 prime factors of NPASS. if NPASS
 * has more than one square-free factor, the product of the square-free
 * factors must be <= 210 array storage for maximum prime factor of 23 the
 * following two constants should agree with the array dimensions.
 *
 *----------------------------------------------------------------------*/

#include <stdlib.h>
#include <math.h>

#include "modules/third_party/fft/fft.h"

/* double precision routine */
static int
WebRtcIsac_Fftradix (double Re[], double Im[],
                    size_t nTotal, size_t nPass, size_t nSpan, int isign,
                    int max_factors, unsigned int max_perm,
                    FFTstr *fftstate);



#ifndef M_PI
# define M_PI 3.14159265358979323846264338327950288
#endif

#ifndef SIN60
# define SIN60 0.86602540378443865 /* sin(60 deg) */
# define COS72 0.30901699437494742 /* cos(72 deg) */
# define SIN72 0.95105651629515357 /* sin(72 deg) */
#endif

# define REAL  double
# define FFTN  WebRtcIsac_Fftn
# define FFTNS  "fftn"
# define FFTRADIX WebRtcIsac_Fftradix
# define FFTRADIXS "fftradix"


int  WebRtcIsac_Fftns(unsigned int ndim, const int dims[],
                     double Re[],
                     double Im[],
                     int iSign,
                     double scaling,
                     FFTstr *fftstate)
{

  size_t nSpan, nPass, nTotal;
  unsigned int i;
  int ret, max_factors, max_perm;

  /*
   * tally the number of elements in the data array
   * and determine the number of dimensions
   */
  nTotal = 1;
  if (ndim && dims [0])
  {
    for (i = 0; i < ndim; i++)
    {
      if (dims [i] <= 0)
      {
        return -1;
      }
      nTotal *= dims [i];
    }
  }
  else
  {
    ndim = 0;
    for (i = 0; dims [i]; i++)
    {
      if (dims [i] <= 0)
      {
        return -1;
      }
      nTotal *= dims [i];
      ndim++;
    }
  }

  /* determine maximum number of factors and permuations */
#if 1
  /*
   * follow John Beale's example, just use the largest dimension and don't
   * worry about excess allocation.  May be someone else will do it?
   */
  max_factors = max_perm = 1;
  for (i = 0; i < ndim; i++)
  {
    nSpan = dims [i];
    if ((int)nSpan > max_factors)
    {
      max_factors = (int)nSpan;
    }
    if ((int)nSpan > max_perm)
    {
      max_perm = (int)nSpan;
    }
  }
#else
  /* use the constants used in the original Fortran code */
  max_factors = 23;
  max_perm = 209;
#endif
  /* loop over the dimensions: */
  nPass = 1;
  for (i = 0; i < ndim; i++)
  {
    nSpan = dims [i];
    nPass *= nSpan;
    ret = FFTRADIX (Re, Im, nTotal, nSpan, nPass, iSign,
                    max_factors, max_perm, fftstate);
    /* exit, clean-up already done */
    if (ret)
      return ret;
  }

  /* Divide through by the normalizing constant: */
  if (scaling && scaling != 1.0)
  {
    if (iSign < 0) iSign = -iSign;
    if (scaling < 0.0)
    {
      scaling = (double)nTotal;
      if (scaling < -1.0)
        scaling = sqrt (scaling);
    }
    scaling = 1.0 / scaling; /* multiply is often faster */
    for (i = 0; i < nTotal; i += iSign)
    {
      Re [i] *= scaling;
      Im [i] *= scaling;
    }
  }
  return 0;
}

/*
 * singleton's mixed radix routine
 *
 * could move allocation out to WebRtcIsac_Fftn(), but leave it here so that it's
 * possible to make this a standalone function
 */

static int   FFTRADIX (REAL Re[],
                       REAL Im[],
                       size_t nTotal,
                       size_t nPass,
                       size_t nSpan,
                       int iSign,
                       int max_factors,
                       unsigned int max_perm,
                       FFTstr *fftstate)
{
  int ii, mfactor, kspan, ispan, inc;
  int j, jc, jf, jj, k, k1, k2, k3, k4, kk, kt, nn, ns, nt;


  REAL radf;
  REAL c1, c2, c3, cd, aa, aj, ak, ajm, ajp, akm, akp;
  REAL s1, s2, s3, sd, bb, bj, bk, bjm, bjp, bkm, bkp;

  REAL *Rtmp = NULL; /* temp space for real part*/
  REAL *Itmp = NULL; /* temp space for imaginary part */
  REAL *Cos = NULL; /* Cosine values */
  REAL *Sin = NULL; /* Sine values */

  REAL s60 = SIN60;  /* sin(60 deg) */
  REAL c72 = COS72;  /* cos(72 deg) */
  REAL s72 = SIN72;  /* sin(72 deg) */
  REAL pi2 = M_PI;  /* use PI first, 2 PI later */


  fftstate->SpaceAlloced = 0;
  fftstate->MaxPermAlloced = 0;


  // initialize to avoid warnings
  k3 = c2 = c3 = s2 = s3 = 0.0;

  if (nPass < 2)
    return 0;

  /*  allocate storage */
  if (fftstate->SpaceAlloced < max_factors * sizeof (REAL))
  {
#ifdef SUN_BROKEN_REALLOC
    if (!fftstate->SpaceAlloced) /* first time */
    {
      fftstate->SpaceAlloced = max_factors * sizeof (REAL);
    }
    else
    {
#endif
      fftstate->SpaceAlloced = max_factors * sizeof (REAL);
#ifdef SUN_BROKEN_REALLOC
    }
#endif
  }
  else
  {
    /* allow full use of alloc'd space */
    max_factors = fftstate->SpaceAlloced / sizeof (REAL);
  }
  if (fftstate->MaxPermAlloced < max_perm)
  {
#ifdef SUN_BROKEN_REALLOC
    if (!fftstate->MaxPermAlloced) /* first time */
    else
#endif
      fftstate->MaxPermAlloced = max_perm;
  }
  else
  {
    /* allow full use of alloc'd space */
    max_perm = fftstate->MaxPermAlloced;
  }

  /* assign pointers */
  Rtmp = (REAL *) fftstate->Tmp0;
  Itmp = (REAL *) fftstate->Tmp1;
  Cos  = (REAL *) fftstate->Tmp2;
  Sin  = (REAL *) fftstate->Tmp3;

  /*
   * Function Body
   */
  inc = iSign;
  if (iSign < 0) {
    s72 = -s72;
    s60 = -s60;
    pi2 = -pi2;
    inc = -inc;  /* absolute value */
  }

  /* adjust for strange increments */
  nt = inc * (int)nTotal;
  ns = inc * (int)nSpan;
  kspan = ns;

  nn = nt - inc;
  jc = ns / (int)nPass;
  radf = pi2 * (double) jc;
  pi2 *= 2.0;   /* use 2 PI from here on */

  ii = 0;
  jf = 0;
  /*  determine the factors of n */
  mfactor = 0;
  k = (int)nPass;
  while (k % 16 == 0) {
    mfactor++;
    fftstate->factor [mfactor - 1] = 4;
    k /= 16;
  }
  j = 3;
  jj = 9;
  do {
    while (k % jj == 0) {
      mfactor++;
      fftstate->factor [mfactor - 1] = j;
      k /= jj;
    }
    j += 2;
    jj = j * j;
  } while (jj <= k);
  if (k <= 4) {
    kt = mfactor;
    fftstate->factor [mfactor] = k;
    if (k != 1)
      mfactor++;
  } else {
    if (k - (k / 4 << 2) == 0) {
      mfactor++;
      fftstate->factor [mfactor - 1] = 2;
      k /= 4;
    }
    kt = mfactor;
    j = 2;
    do {
      if (k % j == 0) {
        mfactor++;
        fftstate->factor [mfactor - 1] = j;
        k /= j;
      }
      j = ((j + 1) / 2 << 1) + 1;
    } while (j <= k);
  }
  if (kt) {
    j = kt;
    do {
      mfactor++;
      fftstate->factor [mfactor - 1] = fftstate->factor [j - 1];
      j--;
    } while (j);
  }

  /* test that mfactors is in range */
  if (mfactor > FFT_NFACTOR)
  {
    return -1;
  }

  /* compute fourier transform */
  for (;;) {
    sd = radf / (double) kspan;
    cd = sin(sd);
    cd = 2.0 * cd * cd;
    sd = sin(sd + sd);
    kk = 0;
    ii++;

    switch (fftstate->factor [ii - 1]) {
      case 2:
        /* transform for factor of 2 (including rotation factor) */
        kspan /= 2;
        k1 = kspan + 2;
        do {
          do {
            k2 = kk + kspan;
            ak = Re [k2];
            bk = Im [k2];
            Re [k2] = Re [kk] - ak;
            Im [k2] = Im [kk] - bk;
            Re [kk] += ak;
            Im [kk] += bk;
            kk = k2 + kspan;
          } while (kk < nn);
          kk -= nn;
        } while (kk < jc);
        if (kk >= kspan)
          goto Permute_Results_Label;  /* exit infinite loop */
        do {
          c1 = 1.0 - cd;
          s1 = sd;
          do {
            do {
              do {
                k2 = kk + kspan;
                ak = Re [kk] - Re [k2];
                bk = Im [kk] - Im [k2];
                Re [kk] += Re [k2];
                Im [kk] += Im [k2];
                Re [k2] = c1 * ak - s1 * bk;
                Im [k2] = s1 * ak + c1 * bk;
                kk = k2 + kspan;
              } while (kk < (nt-1));
              k2 = kk - nt;
              c1 = -c1;
              kk = k1 - k2;
            } while (kk > k2);
            ak = c1 - (cd * c1 + sd * s1);
            s1 = sd * c1 - cd * s1 + s1;
            c1 = 2.0 - (ak * ak + s1 * s1);
            s1 *= c1;
            c1 *= ak;
            kk += jc;
          } while (kk < k2);
          k1 += inc + inc;
          kk = (k1 - kspan + 1) / 2 + jc - 1;
        } while (kk < (jc + jc));
        break;

      case 4:   /* transform for factor of 4 */
        ispan = kspan;
        kspan /= 4;

        do {
          c1 = 1.0;
          s1 = 0.0;
          do {
            do {
              k1 = kk + kspan;
              k2 = k1 + kspan;
              k3 = k2 + kspan;
              akp = Re [kk] + Re [k2];
              akm = Re [kk] - Re [k2];
              ajp = Re [k1] + Re [k3];
              ajm = Re [k1] - Re [k3];
              bkp = Im [kk] + Im [k2];
              bkm = Im [kk] - Im [k2];
              bjp = Im [k1] + Im [k3];
              bjm = Im [k1] - Im [k3];
              Re [kk] = akp + ajp;
              Im [kk] = bkp + bjp;
              ajp = akp - ajp;
              bjp = bkp - bjp;
              if (iSign < 0) {
                akp = akm + bjm;
                bkp = bkm - ajm;
                akm -= bjm;
                bkm += ajm;
              } else {
                akp = akm - bjm;
                bkp = bkm + ajm;
                akm += bjm;
                bkm -= ajm;
              }
              /* avoid useless multiplies */
              if (s1 == 0.0) {
                Re [k1] = akp;
                Re [k2] = ajp;
                Re [k3] = akm;
                Im [k1] = bkp;
                Im [k2] = bjp;
                Im [k3] = bkm;
              } else {
                Re [k1] = akp * c1 - bkp * s1;
                Re [k2] = ajp * c2 - bjp * s2;
                Re [k3] = akm * c3 - bkm * s3;
                Im [k1] = akp * s1 + bkp * c1;
                Im [k2] = ajp * s2 + bjp * c2;
                Im [k3] = akm * s3 + bkm * c3;
              }
              kk = k3 + kspan;
            } while (kk < nt);

            c2 = c1 - (cd * c1 + sd * s1);
            s1 = sd * c1 - cd * s1 + s1;
            c1 = 2.0 - (c2 * c2 + s1 * s1);
            s1 *= c1;
            c1 *= c2;
            /* values of c2, c3, s2, s3 that will get used next time */
            c2 = c1 * c1 - s1 * s1;
            s2 = 2.0 * c1 * s1;
            c3 = c2 * c1 - s2 * s1;
            s3 = c2 * s1 + s2 * c1;
            kk = kk - nt + jc;
          } while (kk < kspan);
          kk = kk - kspan + inc;
        } while (kk < jc);
        if (kspan == jc)
          goto Permute_Results_Label;  /* exit infinite loop */
        break;

      default:
        /*  transform for odd factors */
#ifdef FFT_RADIX4
        return -1;
        break;
#else /* FFT_RADIX4 */
        k = fftstate->factor [ii - 1];
        ispan = kspan;
        kspan /= k;

        switch (k) {
          case 3: /* transform for factor of 3 (optional code) */
            do {
              do {
                k1 = kk + kspan;
                k2 = k1 + kspan;
                ak = Re [kk];
                bk = Im [kk];
                aj = Re [k1] + Re [k2];
                bj = Im [k1] + Im [k2];
                Re [kk] = ak + aj;
                Im [kk] = bk + bj;
                ak -= 0.5 * aj;
                bk -= 0.5 * bj;
                aj = (Re [k1] - Re [k2]) * s60;
                bj = (Im [k1] - Im [k2]) * s60;
                Re [k1] = ak - bj;
                Re [k2] = ak + bj;
                Im [k1] = bk + aj;
                Im [k2] = bk - aj;
                kk = k2 + kspan;
              } while (kk < (nn - 1));
              kk -= nn;
            } while (kk < kspan);
            break;

          case 5: /*  transform for factor of 5 (optional code) */
            c2 = c72 * c72 - s72 * s72;
            s2 = 2.0 * c72 * s72;
            do {
              do {
                k1 = kk + kspan;
                k2 = k1 + kspan;
                k3 = k2 + kspan;
                k4 = k3 + kspan;
                akp = Re [k1] + Re [k4];
                akm = Re [k1] - Re [k4];
                bkp = Im [k1] + Im [k4];
                bkm = Im [k1] - Im [k4];
                ajp = Re [k2] + Re [k3];
                ajm = Re [k2] - Re [k3];
                bjp = Im [k2] + Im [k3];
                bjm = Im [k2] - Im [k3];
                aa = Re [kk];
                bb = Im [kk];
                Re [kk] = aa + akp + ajp;
                Im [kk] = bb + bkp + bjp;
                ak = akp * c72 + ajp * c2 + aa;
                bk = bkp * c72 + bjp * c2 + bb;
                aj = akm * s72 + ajm * s2;
                bj = bkm * s72 + bjm * s2;
                Re [k1] = ak - bj;
                Re [k4] = ak + bj;
                Im [k1] = bk + aj;
                Im [k4] = bk - aj;
                ak = akp * c2 + ajp * c72 + aa;
                bk = bkp * c2 + bjp * c72 + bb;
                aj = akm * s2 - ajm * s72;
                bj = bkm * s2 - bjm * s72;
                Re [k2] = ak - bj;
                Re [k3] = ak + bj;
                Im [k2] = bk + aj;
                Im [k3] = bk - aj;
                kk = k4 + kspan;
              } while (kk < (nn-1));
              kk -= nn;
            } while (kk < kspan);
            break;

          default:
            if (k != jf) {
              jf = k;
              s1 = pi2 / (double) k;
              c1 = cos(s1);
              s1 = sin(s1);
              if (jf > max_factors){
                return -1;
              }
              Cos [jf - 1] = 1.0;
              Sin [jf - 1] = 0.0;
              j = 1;
              do {
                Cos [j - 1] = Cos [k - 1] * c1 + Sin [k - 1] * s1;
                Sin [j - 1] = Cos [k - 1] * s1 - Sin [k - 1] * c1;
                k--;
                Cos [k - 1] = Cos [j - 1];
                Sin [k - 1] = -Sin [j - 1];
                j++;
              } while (j < k);
            }
            do {
              do {
                k1 = kk;
                k2 = kk + ispan;
                ak = aa = Re [kk];
                bk = bb = Im [kk];
                j = 1;
                k1 += kspan;
                do {
                  k2 -= kspan;
                  j++;
                  Rtmp [j - 1] = Re [k1] + Re [k2];
                  ak += Rtmp [j - 1];
                  Itmp [j - 1] = Im [k1] + Im [k2];
                  bk += Itmp [j - 1];
                  j++;
                  Rtmp [j - 1] = Re [k1] - Re [k2];
                  Itmp [j - 1] = Im [k1] - Im [k2];
                  k1 += kspan;
                } while (k1 < k2);
                Re [kk] = ak;
                Im [kk] = bk;
                k1 = kk;
                k2 = kk + ispan;
                j = 1;
                do {
                  k1 += kspan;
                  k2 -= kspan;
                  jj = j;
                  ak = aa;
                  bk = bb;
                  aj = 0.0;
                  bj = 0.0;
                  k = 1;
                  do {
                    k++;
                    ak += Rtmp [k - 1] * Cos [jj - 1];
                    bk += Itmp [k - 1] * Cos [jj - 1];
                    k++;
                    aj += Rtmp [k - 1] * Sin [jj - 1];
                    bj += Itmp [k - 1] * Sin [jj - 1];
                    jj += j;
                    if (jj > jf) {
                      jj -= jf;
                    }
                  } while (k < jf);
                  k = jf - j;
                  Re [k1] = ak - bj;
                  Im [k1] = bk + aj;
                  Re [k2] = ak + bj;
                  Im [k2] = bk - aj;
                  j++;
                } while (j < k);
                kk += ispan;
              } while (kk < nn);
              kk -= nn;
            } while (kk < kspan);
            break;
        }

        /*  multiply by rotation factor (except for factors of 2 and 4) */
        if (ii == mfactor)
          goto Permute_Results_Label;  /* exit infinite loop */
        kk = jc;
        do {
          c2 = 1.0 - cd;
          s1 = sd;
          do {
            c1 = c2;
            s2 = s1;
            kk += kspan;
            do {
              do {
                ak = Re [kk];
                Re [kk] = c2 * ak - s2 * Im [kk];
                Im [kk] = s2 * ak + c2 * Im [kk];
                kk += ispan;
              } while (kk < nt);
              ak = s1 * s2;
              s2 = s1 * c2 + c1 * s2;
              c2 = c1 * c2 - ak;
              kk = kk - nt + kspan;
            } while (kk < ispan);
            c2 = c1 - (cd * c1 + sd * s1);
            s1 += sd * c1 - cd * s1;
            c1 = 2.0 - (c2 * c2 + s1 * s1);
            s1 *= c1;
            c2 *= c1;
            kk = kk - ispan + jc;
          } while (kk < kspan);
          kk = kk - kspan + jc + inc;
        } while (kk < (jc + jc));
        break;
#endif /* FFT_RADIX4 */
    }
  }

  /*  permute the results to normal order---done in two stages */
  /*  permutation for square factors of n */
Permute_Results_Label:
  fftstate->Perm [0] = ns;
  if (kt) {
    k = kt + kt + 1;
    if (mfactor < k)
      k--;
    j = 1;
    fftstate->Perm [k] = jc;
    do {
      fftstate->Perm [j] = fftstate->Perm [j - 1] / fftstate->factor [j - 1];
      fftstate->Perm [k - 1] = fftstate->Perm [k] * fftstate->factor [j - 1];
      j++;
      k--;
    } while (j < k);
    k3 = fftstate->Perm [k];
    kspan = fftstate->Perm [1];
    kk = jc;
    k2 = kspan;
    j = 1;
    if (nPass != nTotal) {
      /*  permutation for multivariate transform */
   Permute_Multi_Label:
      do {
        do {
          k = kk + jc;
          do {
            /* swap Re [kk] <> Re [k2], Im [kk] <> Im [k2] */
            ak = Re [kk]; Re [kk] = Re [k2]; Re [k2] = ak;
            bk = Im [kk]; Im [kk] = Im [k2]; Im [k2] = bk;
            kk += inc;
            k2 += inc;
          } while (kk < (k-1));
          kk += ns - jc;
          k2 += ns - jc;
        } while (kk < (nt-1));
        k2 = k2 - nt + kspan;
        kk = kk - nt + jc;
      } while (k2 < (ns-1));
      do {
        do {
          k2 -= fftstate->Perm [j - 1];
          j++;
          k2 = fftstate->Perm [j] + k2;
        } while (k2 > fftstate->Perm [j - 1]);
        j = 1;
        do {
          if (kk < (k2-1))
            goto Permute_Multi_Label;
          kk += jc;
          k2 += kspan;
        } while (k2 < (ns-1));
      } while (kk < (ns-1));
    } else {
      /*  permutation for single-variate transform (optional code) */
   Permute_Single_Label:
      do {
        /* swap Re [kk] <> Re [k2], Im [kk] <> Im [k2] */
        ak = Re [kk]; Re [kk] = Re [k2]; Re [k2] = ak;
        bk = Im [kk]; Im [kk] = Im [k2]; Im [k2] = bk;
        kk += inc;
        k2 += kspan;
      } while (k2 < (ns-1));
      do {
        do {
          k2 -= fftstate->Perm [j - 1];
          j++;
          k2 = fftstate->Perm [j] + k2;
        } while (k2 >= fftstate->Perm [j - 1]);
        j = 1;
        do {
          if (kk < k2)
            goto Permute_Single_Label;
          kk += inc;
          k2 += kspan;
        } while (k2 < (ns-1));
      } while (kk < (ns-1));
    }
    jc = k3;
  }

  if ((kt << 1) + 1 >= mfactor)
    return 0;
  ispan = fftstate->Perm [kt];
  /* permutation for square-free factors of n */
  j = mfactor - kt;
  fftstate->factor [j] = 1;
  do {
    fftstate->factor [j - 1] *= fftstate->factor [j];
    j--;
  } while (j != kt);
  kt++;
  nn = fftstate->factor [kt - 1] - 1;
  if (nn > (int) max_perm) {
    return -1;
  }
  j = jj = 0;
  for (;;) {
    k = kt + 1;
    k2 = fftstate->factor [kt - 1];
    kk = fftstate->factor [k - 1];
    j++;
    if (j > nn)
      break;    /* exit infinite loop */
    jj += kk;
    while (jj >= k2) {
      jj -= k2;
      k2 = kk;
      k++;
      kk = fftstate->factor [k - 1];
      jj += kk;
    }
    fftstate->Perm [j - 1] = jj;
  }
  /*  determine the permutation cycles of length greater than 1 */
  j = 0;
  for (;;) {
    do {
      j++;
      kk = fftstate->Perm [j - 1];
    } while (kk < 0);
    if (kk != j) {
      do {
        k = kk;
        kk = fftstate->Perm [k - 1];
        fftstate->Perm [k - 1] = -kk;
      } while (kk != j);
      k3 = kk;
    } else {
      fftstate->Perm [j - 1] = -j;
      if (j == nn)
        break;  /* exit infinite loop */
    }
  }
  max_factors *= inc;
  /*  reorder a and b, following the permutation cycles */
  for (;;) {
    j = k3 + 1;
    nt -= ispan;
    ii = nt - inc + 1;
    if (nt < 0)
      break;   /* exit infinite loop */
    do {
      do {
        j--;
      } while (fftstate->Perm [j - 1] < 0);
      jj = jc;
      do {
        kspan = jj;
        if (jj > max_factors) {
          kspan = max_factors;
        }
        jj -= kspan;
        k = fftstate->Perm [j - 1];
        kk = jc * k + ii + jj;
        k1 = kk + kspan - 1;
        k2 = 0;
        do {
          k2++;
          Rtmp [k2 - 1] = Re [k1];
          Itmp [k2 - 1] = Im [k1];
          k1 -= inc;
        } while (k1 != (kk-1));
        do {
          k1 = kk + kspan - 1;
          k2 = k1 - jc * (k + fftstate->Perm [k - 1]);
          k = -fftstate->Perm [k - 1];
          do {
            Re [k1] = Re [k2];
            Im [k1] = Im [k2];
            k1 -= inc;
            k2 -= inc;
          } while (k1 != (kk-1));
          kk = k2 + 1;
        } while (k != j);
        k1 = kk + kspan - 1;
        k2 = 0;
        do {
          k2++;
          Re [k1] = Rtmp [k2 - 1];
          Im [k1] = Itmp [k2 - 1];
          k1 -= inc;
        } while (k1 != (kk-1));
      } while (jj);
    } while (j != 1);
  }
  return 0;   /* exit point here */
}
/* ---------------------- end-of-file (c source) ---------------------- */

