#version 320 es

#extension GL_OES_EGL_image_external : require
#extension GL_EXT_YUV_target : require

precision mediump float;

uniform __samplerExternal2DY2YEXT sTexture;

const mat4 YUV_RGB2020 = mat4(1.167808, 1.167808, 1.167808, 0, 0, -0.187877, 2.148072, 0, 1.683611, -0.652337, 0, 0, -0.914865, 0.347048, -1.147095, 1);
const mat3 BT2020_BT709 = mat3(1.6605, -0.1246, -0.0182, -0.5876, 1.1329, -0.1006, -0.0728, -0.0083, 1.1187);
const highp vec3 Lvec = vec3(0.2627, 0.6780, 0.0593);

const float sRGB_Lw = 80.0;  // sRGB Lw
const float HLG_Lw = 1000.0; // HLG Lw
const float HLG_gamma = 1.2; // HLG gamma

const float a = .17883277;
const float b = 1. - 4. * a;
const float c = .5 - a * log(4. * a);

highp vec3 HLG_OOTF_norm(highp vec3 x) {
  return pow(dot(Lvec, mix((x * x) / 3., (b + exp((x - c) / a)) / 12., step(.5, x))), HLG_gamma - 1.0) * x;
}

highp vec3 sRGB_EOTF_Inv(highp vec3 x) {
  return mix(12.92 * x, 1.055 * pow(x, vec3(1. / 2.4)) - .055, step(.0031308, x));
}

highp vec3 sRGB_EOTF(highp vec3 x) {
  return mix(x / 12.92, pow((x + 0.055) / 1.055, vec3(2.4)), step(.04045, x));
}

const float L_HDR = HLG_Lw;
const float rho_HDR = 1. + 32. * pow(L_HDR / 10000., 1. / 2.4);
const float L_SDR = sRGB_Lw;
const float rho_SDR = 1. + 32. * pow(L_SDR / 10000., 1. / 2.4);

highp vec3 BT2446_tone_mapping(highp vec3 rgb_BT2020) {
  highp vec3 rgb_BT2020_prime = sRGB_EOTF_Inv(rgb_BT2020);

  float Y_prime = dot(Lvec, rgb_BT2020_prime);

  float Y_prime_p = log(1. + (rho_HDR - 1.) * Y_prime) / log(rho_HDR);
  float Y_prime_c = mix(1.0770 * Y_prime_p, mix((-1.1510 * Y_prime_p + 2.7811) * Y_prime_p - .6302, .5 * Y_prime_p + .5, step(.9909, Y_prime_p)), step(.7399, Y_prime_p));
  float Y_prime_SDR = (pow(rho_SDR, Y_prime_c) - 1.) / (rho_SDR - 1.);

  float f_Y_prime_SDR = Y_prime_SDR / (1.1 * Y_prime);
  float Cb_prime_TMO = f_Y_prime_SDR * (rgb_BT2020_prime.b - Y_prime) / 1.8814;
  float Cr_prime_TMO = f_Y_prime_SDR * (rgb_BT2020_prime.r - Y_prime) / 1.4746;
  float Y_prime_TMO = Y_prime_SDR - max(.1 * Cr_prime_TMO, .0);

  float R_prime_TMO = Cr_prime_TMO * 1.4746 + Y_prime_TMO;
  float B_prime_TMO = Cb_prime_TMO * 1.8814 + Y_prime_TMO;
  float G_prime_TMO = (Y_prime_TMO - Lvec.x * R_prime_TMO - Lvec.z * B_prime_TMO) / Lvec.y;
  highp vec3 rgb_BT2020_prime_TMO = vec3(R_prime_TMO, G_prime_TMO, B_prime_TMO);

  return sRGB_EOTF(rgb_BT2020_prime_TMO);
}

uniform vec2 texSize;

vec4 at(vec2 uv) {
  highp vec4 srcYuv = texture(sTexture, uv);
  highp vec3 rgb_BT2020 = clamp((YUV_RGB2020 * srcYuv).rgb, 0., 1.);
  highp vec3 rgb_BT2020_displayLinear = HLG_OOTF_norm(rgb_BT2020);
  highp vec3 rgb_BT2020_displayLinear_TMO = BT2446_tone_mapping(rgb_BT2020_displayLinear);
  highp vec3 rgb_BT709_displayLinear = BT2020_BT709 * rgb_BT2020_displayLinear_TMO;
  rgb_BT709_displayLinear = clamp(rgb_BT709_displayLinear, 0., 1.);
  highp vec3 rgb_BT709_sRGB = sRGB_EOTF_Inv(rgb_BT709_displayLinear);
  return vec4(rgb_BT709_sRGB, 1.0);
}

// vec4 BilinearTextureSample(vec2 P) {
//   vec2 onePixel = 1.0 / texSize, twoPixels = 2.0 / texSize;
//   vec2 pixel = P * texSize + .5;
//   vec2 frac = fract(pixel);
//   pixel = (floor(pixel) / texSize) - onePixel / 2.;
//   return mix(
//     mix(at(pixel + vec2(0., 0.) * onePixel), at(pixel + vec2(1., 0.) * onePixel), frac.x),
//     mix(at(pixel + vec2(0., 1.) * onePixel), at(pixel + vec2(1., 1.) * onePixel), frac.x),
//     frac.y
//   );
// }

// vec3 NearestTextureSample(vec2 P) {
//     vec2 onePixel = 1.0 / texSize, twoPixels = 2.0 / texSize;
//     vec2 pixel = P * texSize;
//     vec2 frac = fract(pixel);
//     pixel = floor(pixel) / texSize;
//     return at(pixel + onePixel / 2.).xyz;
// }

// vec3 CubicHermite (vec3 A, vec3 B, vec3 C, vec3 D, float t) {
//     float t2 = t*t;
//     float t3 = t*t*t;
//     vec3 a = -A/2.0 + (3.0*B)/2.0 - (3.0*C)/2.0 + D/2.0;
//     vec3 b = A - (5.0*B)/2.0 + 2.0*C - D / 2.0;
//     vec3 c = -A/2.0 + C/2.0;
//     vec3 d = B;
//     return a*t3 + b*t2 + c*t + d;
// }

// vec3 BicubicHermiteTextureSample (vec2 P)
// {
//     vec2 pixel = P * texSize + .5;
//     vec2 onePixel = 1.0 / texSize, twoPixels = 2.0 / texSize;
//     vec2 frac = fract(pixel);
//     pixel = floor(pixel) / texSize - onePixel / 2.;
// 
//     vec3 C00 = at(pixel + vec2(-1., -1.) * onePixel).xyz;
//     vec3 C10 = at(pixel + vec2( 0., -1.) * onePixel).xyz;
//     vec3 C20 = at(pixel + vec2(-1., -1.) * onePixel).xyz;
//     vec3 C30 = at(pixel + vec2(twoPixels.x, -onePixel.y)).xyz;
// 
//     vec3 C01 = at(pixel + vec2(-onePixel.x , 0.0)).xyz;
//     vec3 C11 = at(pixel + vec2( 0.0        , 0.0)).xyz;
//     vec3 C21 = at(pixel + vec2( onePixel.x , 0.0)).xyz;
//     vec3 C31 = at(pixel + vec2( twoPixels.x, 0.0)).xyz;    
// 
//     vec3 C02 = at(pixel + vec2(-onePixel.x , onePixel.y)).xyz;
//     vec3 C12 = at(pixel + vec2( 0.0        , onePixel.y)).xyz;
//     vec3 C22 = at(pixel + vec2( onePixel.x , onePixel.y)).xyz;
//     vec3 C32 = at(pixel + vec2( twoPixels.x, onePixel.y)).xyz;    
// 
//     vec3 C03 = at(pixel + vec2(-onePixel.x , twoPixels.y)).xyz;
//     vec3 C13 = at(pixel + vec2( 0.0        , twoPixels.y)).xyz;
//     vec3 C23 = at(pixel + vec2( onePixel.x , twoPixels.y)).xyz;
//     vec3 C33 = at(pixel + vec2( twoPixels.y, twoPixels.y)).xyz;    
// 
//     vec3 CP0X = CubicHermite(C00, C10, C20, C30, frac.x);
//     vec3 CP1X = CubicHermite(C01, C11, C21, C31, frac.x);
//     vec3 CP2X = CubicHermite(C02, C12, C22, C32, frac.x);
//     vec3 CP3X = CubicHermite(C03, C13, C23, C33, frac.x);
// 
//     return CubicHermite(CP0X, CP1X, CP2X, CP3X, frac.y);
// }

vec4 TEX(vec2 uv) {
    return at(uv);
}