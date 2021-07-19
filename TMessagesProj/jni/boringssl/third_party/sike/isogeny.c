/********************************************************************************************
* SIDH: an efficient supersingular isogeny cryptography library
*
* Abstract: elliptic curve and isogeny functions
*********************************************************************************************/
#include "utils.h"
#include "isogeny.h"
#include "fpx.h"

static void xDBL(const point_proj_t P, point_proj_t Q, const f2elm_t A24plus, const f2elm_t C24)
{ // Doubling of a Montgomery point in projective coordinates (X:Z).
  // Input: projective Montgomery x-coordinates P = (X1:Z1), where x1=X1/Z1 and Montgomery curve constants A+2C and 4C.
  // Output: projective Montgomery x-coordinates Q = 2*P = (X2:Z2).
    f2elm_t t0, t1;

    sike_fp2sub(P->X, P->Z, t0);                         // t0 = X1-Z1
    sike_fp2add(P->X, P->Z, t1);                         // t1 = X1+Z1
    sike_fp2sqr_mont(t0, t0);                            // t0 = (X1-Z1)^2
    sike_fp2sqr_mont(t1, t1);                            // t1 = (X1+Z1)^2
    sike_fp2mul_mont(C24, t0, Q->Z);                     // Z2 = C24*(X1-Z1)^2
    sike_fp2mul_mont(t1, Q->Z, Q->X);                    // X2 = C24*(X1-Z1)^2*(X1+Z1)^2
    sike_fp2sub(t1, t0, t1);                             // t1 = (X1+Z1)^2-(X1-Z1)^2
    sike_fp2mul_mont(A24plus, t1, t0);                   // t0 = A24plus*[(X1+Z1)^2-(X1-Z1)^2]
    sike_fp2add(Q->Z, t0, Q->Z);                         // Z2 = A24plus*[(X1+Z1)^2-(X1-Z1)^2] + C24*(X1-Z1)^2
    sike_fp2mul_mont(Q->Z, t1, Q->Z);                    // Z2 = [A24plus*[(X1+Z1)^2-(X1-Z1)^2] + C24*(X1-Z1)^2]*[(X1+Z1)^2-(X1-Z1)^2]
}

void sike_xDBLe(const point_proj_t P, point_proj_t Q, const f2elm_t A24plus, const f2elm_t C24, size_t e)
{ // Computes [2^e](X:Z) on Montgomery curve with projective constant via e repeated doublings.
  // Input: projective Montgomery x-coordinates P = (XP:ZP), such that xP=XP/ZP and Montgomery curve constants A+2C and 4C.
  // Output: projective Montgomery x-coordinates Q <- (2^e)*P.

    memmove(Q, P, sizeof(*P));
    for (size_t i = 0; i < e; i++) {
        xDBL(Q, Q, A24plus, C24);
    }
}

void sike_get_4_isog(const point_proj_t P, f2elm_t A24plus, f2elm_t C24, f2elm_t* coeff)
{ // Computes the corresponding 4-isogeny of a projective Montgomery point (X4:Z4) of order 4.
  // Input:  projective point of order four P = (X4:Z4).
  // Output: the 4-isogenous Montgomery curve with projective coefficients A+2C/4C and the 3 coefficients
  //         that are used to evaluate the isogeny at a point in eval_4_isog().

    sike_fp2sub(P->X, P->Z, coeff[1]);                   // coeff[1] = X4-Z4
    sike_fp2add(P->X, P->Z, coeff[2]);                   // coeff[2] = X4+Z4
    sike_fp2sqr_mont(P->Z, coeff[0]);                    // coeff[0] = Z4^2
    sike_fp2add(coeff[0], coeff[0], coeff[0]);           // coeff[0] = 2*Z4^2
    sike_fp2sqr_mont(coeff[0], C24);                     // C24 = 4*Z4^4
    sike_fp2add(coeff[0], coeff[0], coeff[0]);           // coeff[0] = 4*Z4^2
    sike_fp2sqr_mont(P->X, A24plus);                     // A24plus = X4^2
    sike_fp2add(A24plus, A24plus, A24plus);              // A24plus = 2*X4^2
    sike_fp2sqr_mont(A24plus, A24plus);                  // A24plus = 4*X4^4
}

void sike_eval_4_isog(point_proj_t P, f2elm_t* coeff)
{ // Evaluates the isogeny at the point (X:Z) in the domain of the isogeny, given a 4-isogeny phi defined
  // by the 3 coefficients in coeff (computed in the function get_4_isog()).
  // Inputs: the coefficients defining the isogeny, and the projective point P = (X:Z).
  // Output: the projective point P = phi(P) = (X:Z) in the codomain.
    f2elm_t t0, t1;

    sike_fp2add(P->X, P->Z, t0);                         // t0 = X+Z
    sike_fp2sub(P->X, P->Z, t1);                         // t1 = X-Z
    sike_fp2mul_mont(t0, coeff[1], P->X);                // X = (X+Z)*coeff[1]
    sike_fp2mul_mont(t1, coeff[2], P->Z);                // Z = (X-Z)*coeff[2]
    sike_fp2mul_mont(t0, t1, t0);                        // t0 = (X+Z)*(X-Z)
    sike_fp2mul_mont(t0, coeff[0], t0);                  // t0 = coeff[0]*(X+Z)*(X-Z)
    sike_fp2add(P->X, P->Z, t1);                         // t1 = (X-Z)*coeff[2] + (X+Z)*coeff[1]
    sike_fp2sub(P->X, P->Z, P->Z);                       // Z = (X-Z)*coeff[2] - (X+Z)*coeff[1]
    sike_fp2sqr_mont(t1, t1);                            // t1 = [(X-Z)*coeff[2] + (X+Z)*coeff[1]]^2
    sike_fp2sqr_mont(P->Z, P->Z);                        // Z = [(X-Z)*coeff[2] - (X+Z)*coeff[1]]^2
    sike_fp2add(t1, t0, P->X);                           // X = coeff[0]*(X+Z)*(X-Z) + [(X-Z)*coeff[2] + (X+Z)*coeff[1]]^2
    sike_fp2sub(P->Z, t0, t0);                           // t0 = [(X-Z)*coeff[2] - (X+Z)*coeff[1]]^2 - coeff[0]*(X+Z)*(X-Z)
    sike_fp2mul_mont(P->X, t1, P->X);                    // Xfinal
    sike_fp2mul_mont(P->Z, t0, P->Z);                    // Zfinal
}


void sike_xTPL(const point_proj_t P, point_proj_t Q, const f2elm_t A24minus, const f2elm_t A24plus)
{ // Tripling of a Montgomery point in projective coordinates (X:Z).
  // Input: projective Montgomery x-coordinates P = (X:Z), where x=X/Z and Montgomery curve constants A24plus = A+2C and A24minus = A-2C.
  // Output: projective Montgomery x-coordinates Q = 3*P = (X3:Z3).
    f2elm_t t0, t1, t2, t3, t4, t5, t6;

    sike_fp2sub(P->X, P->Z, t0);                         // t0 = X-Z
    sike_fp2sqr_mont(t0, t2);                            // t2 = (X-Z)^2
    sike_fp2add(P->X, P->Z, t1);                         // t1 = X+Z
    sike_fp2sqr_mont(t1, t3);                            // t3 = (X+Z)^2
    sike_fp2add(t0, t1, t4);                             // t4 = 2*X
    sike_fp2sub(t1, t0, t0);                             // t0 = 2*Z
    sike_fp2sqr_mont(t4, t1);                            // t1 = 4*X^2
    sike_fp2sub(t1, t3, t1);                             // t1 = 4*X^2 - (X+Z)^2
    sike_fp2sub(t1, t2, t1);                             // t1 = 4*X^2 - (X+Z)^2 - (X-Z)^2
    sike_fp2mul_mont(t3, A24plus, t5);                   // t5 = A24plus*(X+Z)^2
    sike_fp2mul_mont(t3, t5, t3);                        // t3 = A24plus*(X+Z)^3
    sike_fp2mul_mont(A24minus, t2, t6);                  // t6 = A24minus*(X-Z)^2
    sike_fp2mul_mont(t2, t6, t2);                        // t2 = A24minus*(X-Z)^3
    sike_fp2sub(t2, t3, t3);                             // t3 = A24minus*(X-Z)^3 - coeff*(X+Z)^3
    sike_fp2sub(t5, t6, t2);                             // t2 = A24plus*(X+Z)^2 - A24minus*(X-Z)^2
    sike_fp2mul_mont(t1, t2, t1);                        // t1 = [4*X^2 - (X+Z)^2 - (X-Z)^2]*[A24plus*(X+Z)^2 - A24minus*(X-Z)^2]
    sike_fp2add(t3, t1, t2);                             // t2 = [4*X^2 - (X+Z)^2 - (X-Z)^2]*[A24plus*(X+Z)^2 - A24minus*(X-Z)^2] + A24minus*(X-Z)^3 - coeff*(X+Z)^3
    sike_fp2sqr_mont(t2, t2);                            // t2 = t2^2
    sike_fp2mul_mont(t4, t2, Q->X);                      // X3 = 2*X*t2
    sike_fp2sub(t3, t1, t1);                             // t1 = A24minus*(X-Z)^3 - A24plus*(X+Z)^3 - [4*X^2 - (X+Z)^2 - (X-Z)^2]*[A24plus*(X+Z)^2 - A24minus*(X-Z)^2]
    sike_fp2sqr_mont(t1, t1);                            // t1 = t1^2
    sike_fp2mul_mont(t0, t1, Q->Z);                      // Z3 = 2*Z*t1
}

void sike_xTPLe(const point_proj_t P, point_proj_t Q, const f2elm_t A24minus, const f2elm_t A24plus, size_t e)
{ // Computes [3^e](X:Z) on Montgomery curve with projective constant via e repeated triplings.
  // Input: projective Montgomery x-coordinates P = (XP:ZP), such that xP=XP/ZP and Montgomery curve constants A24plus = A+2C and A24minus = A-2C.
  // Output: projective Montgomery x-coordinates Q <- (3^e)*P.
    memmove(Q, P, sizeof(*P));
    for (size_t i = 0; i < e; i++) {
        sike_xTPL(Q, Q, A24minus, A24plus);
    }
}

void sike_get_3_isog(const point_proj_t P, f2elm_t A24minus, f2elm_t A24plus, f2elm_t* coeff)
{ // Computes the corresponding 3-isogeny of a projective Montgomery point (X3:Z3) of order 3.
  // Input:  projective point of order three P = (X3:Z3).
  // Output: the 3-isogenous Montgomery curve with projective coefficient A/C.
    f2elm_t t0, t1, t2, t3, t4;

    sike_fp2sub(P->X, P->Z, coeff[0]);                   // coeff0 = X-Z
    sike_fp2sqr_mont(coeff[0], t0);                      // t0 = (X-Z)^2
    sike_fp2add(P->X, P->Z, coeff[1]);                   // coeff1 = X+Z
    sike_fp2sqr_mont(coeff[1], t1);                      // t1 = (X+Z)^2
    sike_fp2add(t0, t1, t2);                             // t2 = (X+Z)^2 + (X-Z)^2
    sike_fp2add(coeff[0], coeff[1], t3);                 // t3 = 2*X
    sike_fp2sqr_mont(t3, t3);                            // t3 = 4*X^2
    sike_fp2sub(t3, t2, t3);                             // t3 = 4*X^2 - (X+Z)^2 - (X-Z)^2
    sike_fp2add(t1, t3, t2);                             // t2 = 4*X^2 - (X-Z)^2
    sike_fp2add(t3, t0, t3);                             // t3 = 4*X^2 - (X+Z)^2
    sike_fp2add(t0, t3, t4);                             // t4 = 4*X^2 - (X+Z)^2 + (X-Z)^2
    sike_fp2add(t4, t4, t4);                             // t4 = 2(4*X^2 - (X+Z)^2 + (X-Z)^2)
    sike_fp2add(t1, t4, t4);                             // t4 = 8*X^2 - (X+Z)^2 + 2*(X-Z)^2
    sike_fp2mul_mont(t2, t4, A24minus);                  // A24minus = [4*X^2 - (X-Z)^2]*[8*X^2 - (X+Z)^2 + 2*(X-Z)^2]
    sike_fp2add(t1, t2, t4);                             // t4 = 4*X^2 + (X+Z)^2 - (X-Z)^2
    sike_fp2add(t4, t4, t4);                             // t4 = 2(4*X^2 + (X+Z)^2 - (X-Z)^2)
    sike_fp2add(t0, t4, t4);                             // t4 = 8*X^2 + 2*(X+Z)^2 - (X-Z)^2
    sike_fp2mul_mont(t3, t4, t4);                        // t4 = [4*X^2 - (X+Z)^2]*[8*X^2 + 2*(X+Z)^2 - (X-Z)^2]
    sike_fp2sub(t4, A24minus, t0);                       // t0 = [4*X^2 - (X+Z)^2]*[8*X^2 + 2*(X+Z)^2 - (X-Z)^2] - [4*X^2 - (X-Z)^2]*[8*X^2 - (X+Z)^2 + 2*(X-Z)^2]
    sike_fp2add(A24minus, t0, A24plus);                  // A24plus = 8*X^2 - (X+Z)^2 + 2*(X-Z)^2
}


void sike_eval_3_isog(point_proj_t Q, f2elm_t* coeff)
{ // Computes the 3-isogeny R=phi(X:Z), given projective point (X3:Z3) of order 3 on a Montgomery curve and
  // a point P with 2 coefficients in coeff (computed in the function get_3_isog()).
  // Inputs: projective points P = (X3:Z3) and Q = (X:Z).
  // Output: the projective point Q <- phi(Q) = (X3:Z3).
    f2elm_t t0, t1, t2;

    sike_fp2add(Q->X, Q->Z, t0);                       // t0 = X+Z
    sike_fp2sub(Q->X, Q->Z, t1);                       // t1 = X-Z
    sike_fp2mul_mont(t0, coeff[0], t0);                // t0 = coeff0*(X+Z)
    sike_fp2mul_mont(t1, coeff[1], t1);                // t1 = coeff1*(X-Z)
    sike_fp2add(t0, t1, t2);                           // t2 = coeff0*(X+Z) + coeff1*(X-Z)
    sike_fp2sub(t1, t0, t0);                           // t0 = coeff1*(X-Z) - coeff0*(X+Z)
    sike_fp2sqr_mont(t2, t2);                          // t2 = [coeff0*(X+Z) + coeff1*(X-Z)]^2
    sike_fp2sqr_mont(t0, t0);                          // t0 = [coeff1*(X-Z) - coeff0*(X+Z)]^2
    sike_fp2mul_mont(Q->X, t2, Q->X);                  // X3final = X*[coeff0*(X+Z) + coeff1*(X-Z)]^2
    sike_fp2mul_mont(Q->Z, t0, Q->Z);                  // Z3final = Z*[coeff1*(X-Z) - coeff0*(X+Z)]^2
}


void sike_inv_3_way(f2elm_t z1, f2elm_t z2, f2elm_t z3)
{ // 3-way simultaneous inversion
  // Input:  z1,z2,z3
  // Output: 1/z1,1/z2,1/z3 (override inputs).
    f2elm_t t0, t1, t2, t3;

    sike_fp2mul_mont(z1, z2, t0);                      // t0 = z1*z2
    sike_fp2mul_mont(z3, t0, t1);                      // t1 = z1*z2*z3
    sike_fp2inv_mont(t1);                              // t1 = 1/(z1*z2*z3)
    sike_fp2mul_mont(z3, t1, t2);                      // t2 = 1/(z1*z2)
    sike_fp2mul_mont(t2, z2, t3);                      // t3 = 1/z1
    sike_fp2mul_mont(t2, z1, z2);                      // z2 = 1/z2
    sike_fp2mul_mont(t0, t1, z3);                      // z3 = 1/z3
    sike_fp2copy(t3, z1);                              // z1 = 1/z1
}


void sike_get_A(const f2elm_t xP, const f2elm_t xQ, const f2elm_t xR, f2elm_t A)
{ // Given the x-coordinates of P, Q, and R, returns the value A corresponding to the Montgomery curve E_A: y^2=x^3+A*x^2+x such that R=Q-P on E_A.
  // Input:  the x-coordinates xP, xQ, and xR of the points P, Q and R.
  // Output: the coefficient A corresponding to the curve E_A: y^2=x^3+A*x^2+x.
    f2elm_t t0, t1, one = F2ELM_INIT;

    extern const struct params_t sike_params;
    sike_fpcopy(sike_params.mont_one, one->c0);
    sike_fp2add(xP, xQ, t1);                           // t1 = xP+xQ
    sike_fp2mul_mont(xP, xQ, t0);                      // t0 = xP*xQ
    sike_fp2mul_mont(xR, t1, A);                       // A = xR*t1
    sike_fp2add(t0, A, A);                             // A = A+t0
    sike_fp2mul_mont(t0, xR, t0);                      // t0 = t0*xR
    sike_fp2sub(A, one, A);                            // A = A-1
    sike_fp2add(t0, t0, t0);                           // t0 = t0+t0
    sike_fp2add(t1, xR, t1);                           // t1 = t1+xR
    sike_fp2add(t0, t0, t0);                           // t0 = t0+t0
    sike_fp2sqr_mont(A, A);                            // A = A^2
    sike_fp2inv_mont(t0);                              // t0 = 1/t0
    sike_fp2mul_mont(A, t0, A);                        // A = A*t0
    sike_fp2sub(A, t1, A);                             // Afinal = A-t1
}


void sike_j_inv(const f2elm_t A, const f2elm_t C, f2elm_t jinv)
{ // Computes the j-invariant of a Montgomery curve with projective constant.
  // Input: A,C in GF(p^2).
  // Output: j=256*(A^2-3*C^2)^3/(C^4*(A^2-4*C^2)), which is the j-invariant of the Montgomery curve B*y^2=x^3+(A/C)*x^2+x or (equivalently) j-invariant of B'*y^2=C*x^3+A*x^2+C*x.
    f2elm_t t0, t1;

    sike_fp2sqr_mont(A, jinv);                           // jinv = A^2
    sike_fp2sqr_mont(C, t1);                             // t1 = C^2
    sike_fp2add(t1, t1, t0);                             // t0 = t1+t1
    sike_fp2sub(jinv, t0, t0);                           // t0 = jinv-t0
    sike_fp2sub(t0, t1, t0);                             // t0 = t0-t1
    sike_fp2sub(t0, t1, jinv);                           // jinv = t0-t1
    sike_fp2sqr_mont(t1, t1);                            // t1 = t1^2
    sike_fp2mul_mont(jinv, t1, jinv);                    // jinv = jinv*t1
    sike_fp2add(t0, t0, t0);                             // t0 = t0+t0
    sike_fp2add(t0, t0, t0);                             // t0 = t0+t0
    sike_fp2sqr_mont(t0, t1);                            // t1 = t0^2
    sike_fp2mul_mont(t0, t1, t0);                        // t0 = t0*t1
    sike_fp2add(t0, t0, t0);                             // t0 = t0+t0
    sike_fp2add(t0, t0, t0);                             // t0 = t0+t0
    sike_fp2inv_mont(jinv);                              // jinv = 1/jinv
    sike_fp2mul_mont(jinv, t0, jinv);                    // jinv = t0*jinv
}


void sike_xDBLADD(point_proj_t P, point_proj_t Q, const f2elm_t xPQ, const f2elm_t A24)
{ // Simultaneous doubling and differential addition.
  // Input: projective Montgomery points P=(XP:ZP) and Q=(XQ:ZQ) such that xP=XP/ZP and xQ=XQ/ZQ, affine difference xPQ=x(P-Q) and Montgomery curve constant A24=(A+2)/4.
  // Output: projective Montgomery points P <- 2*P = (X2P:Z2P) such that x(2P)=X2P/Z2P, and Q <- P+Q = (XQP:ZQP) such that = x(Q+P)=XQP/ZQP.
    f2elm_t t0, t1, t2;

    sike_fp2add(P->X, P->Z, t0);                         // t0 = XP+ZP
    sike_fp2sub(P->X, P->Z, t1);                         // t1 = XP-ZP
    sike_fp2sqr_mont(t0, P->X);                          // XP = (XP+ZP)^2
    sike_fp2sub(Q->X, Q->Z, t2);                         // t2 = XQ-ZQ
    sike_fp2correction(t2);
    sike_fp2add(Q->X, Q->Z, Q->X);                       // XQ = XQ+ZQ
    sike_fp2mul_mont(t0, t2, t0);                        // t0 = (XP+ZP)*(XQ-ZQ)
    sike_fp2sqr_mont(t1, P->Z);                          // ZP = (XP-ZP)^2
    sike_fp2mul_mont(t1, Q->X, t1);                      // t1 = (XP-ZP)*(XQ+ZQ)
    sike_fp2sub(P->X, P->Z, t2);                         // t2 = (XP+ZP)^2-(XP-ZP)^2
    sike_fp2mul_mont(P->X, P->Z, P->X);                  // XP = (XP+ZP)^2*(XP-ZP)^2
    sike_fp2mul_mont(t2, A24, Q->X);                     // XQ = A24*[(XP+ZP)^2-(XP-ZP)^2]
    sike_fp2sub(t0, t1, Q->Z);                           // ZQ = (XP+ZP)*(XQ-ZQ)-(XP-ZP)*(XQ+ZQ)
    sike_fp2add(Q->X, P->Z, P->Z);                       // ZP = A24*[(XP+ZP)^2-(XP-ZP)^2]+(XP-ZP)^2
    sike_fp2add(t0, t1, Q->X);                           // XQ = (XP+ZP)*(XQ-ZQ)+(XP-ZP)*(XQ+ZQ)
    sike_fp2mul_mont(P->Z, t2, P->Z);                    // ZP = [A24*[(XP+ZP)^2-(XP-ZP)^2]+(XP-ZP)^2]*[(XP+ZP)^2-(XP-ZP)^2]
    sike_fp2sqr_mont(Q->Z, Q->Z);                        // ZQ = [(XP+ZP)*(XQ-ZQ)-(XP-ZP)*(XQ+ZQ)]^2
    sike_fp2sqr_mont(Q->X, Q->X);                        // XQ = [(XP+ZP)*(XQ-ZQ)+(XP-ZP)*(XQ+ZQ)]^2
    sike_fp2mul_mont(Q->Z, xPQ, Q->Z);                   // ZQ = xPQ*[(XP+ZP)*(XQ-ZQ)-(XP-ZP)*(XQ+ZQ)]^2
}
