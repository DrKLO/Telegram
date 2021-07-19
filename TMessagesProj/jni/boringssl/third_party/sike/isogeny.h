#ifndef ISOGENY_H_
#define ISOGENY_H_

// Computes [2^e](X:Z) on Montgomery curve with projective
// constant via e repeated doublings.
void sike_xDBLe(
    const point_proj_t P, point_proj_t Q, const f2elm_t A24plus,
    const f2elm_t C24, size_t e);
// Simultaneous doubling and differential addition.
void sike_xDBLADD(
    point_proj_t P, point_proj_t Q, const f2elm_t xPQ,
    const f2elm_t A24);
// Tripling of a Montgomery point in projective coordinates (X:Z).
void sike_xTPL(
    const point_proj_t P, point_proj_t Q, const f2elm_t A24minus,
    const f2elm_t A24plus);
// Computes [3^e](X:Z) on Montgomery curve with projective constant
// via e repeated triplings.
void sike_xTPLe(
    const point_proj_t P, point_proj_t Q, const f2elm_t A24minus,
    const f2elm_t A24plus, size_t e);
// Given the x-coordinates of P, Q, and R, returns the value A
// corresponding to the Montgomery curve E_A: y^2=x^3+A*x^2+x such that R=Q-P on E_A.
void sike_get_A(
    const f2elm_t xP, const f2elm_t xQ, const f2elm_t xR, f2elm_t A);
// Computes the j-invariant of a Montgomery curve with projective constant.
void sike_j_inv(
    const f2elm_t A, const f2elm_t C, f2elm_t jinv);
// Computes the corresponding 4-isogeny of a projective Montgomery
// point (X4:Z4) of order 4.
void sike_get_4_isog(
    const point_proj_t P, f2elm_t A24plus, f2elm_t C24, f2elm_t* coeff);
// Computes the corresponding 3-isogeny of a projective Montgomery
// point (X3:Z3) of order 3.
void sike_get_3_isog(
    const point_proj_t P, f2elm_t A24minus, f2elm_t A24plus,
    f2elm_t* coeff);
// Computes the 3-isogeny R=phi(X:Z), given projective point (X3:Z3)
// of order 3 on a Montgomery curve and a point P with coefficients given in coeff.
void sike_eval_3_isog(
    point_proj_t Q, f2elm_t* coeff);
// Evaluates the isogeny at the point (X:Z) in the domain of the isogeny.
void sike_eval_4_isog(
    point_proj_t P, f2elm_t* coeff);
// 3-way simultaneous inversion
void sike_inv_3_way(
    f2elm_t z1, f2elm_t z2, f2elm_t z3);

#endif // ISOGENY_H_
