#version 320 es

#extension GL_OES_EGL_image_external : require
#extension GL_EXT_YUV_target : require

precision mediump float;

uniform __samplerExternal2DY2YEXT sTexture;

const mat4 YUV_TO_RGB_REC2020 = mat4(1.167808, 1.167808, 1.167808, 0, 0, -0.187877, 2.148072, 0, 1.683611, -0.652337, 0, 0, -0.914865, 0.347048, -1.147095, 1);

const mat3 REC709_XYZ =  mat3(0.4124564, 0.3575761, 0.1804375, 0.2126729, 0.7151522, 0.0721750, 0.0193339, 0.1191920, 0.9503041);
const mat3 XYZ_REC709 =  mat3(3.2404542, -1.5371385, -0.4985314, -0.9692660, 1.8760108, 0.0415560, 0.0556434, -0.2040259, 1.0572252);
const mat3 REC2020_XYZ = mat3(0.6370, 0.1446, 0.1689, 0.2627, 0.6780, 0.0593, 0.0, 0.0281, 1.0610);
const mat3 XYZ_REC2020 = mat3(1.7167, -0.3557, -0.2534, -0.6667, 1.6165, 0.0158, 0.0176, -0.0428, 0.9421);

highp vec3 sRGB_EOTF_Inv(highp vec3 x) {
  return mix(12.92 * x, 1.055 * pow(x, vec3(1.0 / 2.4)) - .055, step(.0031308, x));
}

uniform vec2 texSize;

vec4 at(vec2 uv) {
  highp vec4 srcYuv = texture(sTexture, uv);
  highp vec3 rgb_BT2020 = clamp((YUV_TO_RGB_REC2020 * srcYuv).rgb, 0., 1.);
  highp vec3 rgb_BT2020_pqexp = pow(rgb_BT2020, vec3(1.0 / 78.84375));
  highp vec3 rgb_BT2020_sceneLinear = 5000.0 * pow(max(rgb_BT2020_pqexp - 0.8359375, 0.0) / (18.8515625 - 18.6875 * rgb_BT2020_pqexp), vec3(1.0 / 0.1593017578125));
  highp vec3 rgb_BT2020_displayLinear = rgb_BT2020_sceneLinear / 100.;
  highp vec3 xyz_displayLinear = rgb_BT2020_displayLinear*REC2020_XYZ; // REC709_XYZ
  highp vec3 xyz_tonemap = xyz_displayLinear / (xyz_displayLinear.y + 1.);
  highp vec3 rgb_BT709_displayLinear = clamp( xyz_tonemap*XYZ_REC709, 0., 1.); // XYZ_REC2020
  highp vec3 rgb_BT709_sRGB = sRGB_EOTF_Inv(rgb_BT709_displayLinear);
  return vec4(rgb_BT709_sRGB, 1.);
}

// vec4 BilinearTextureSample(vec2 P) {
//   vec2 onePixel = 1. / texSize;
//   vec2 pixel = P * texSize + .5;
//   vec2 frac = fract(pixel);
//   pixel = (floor(pixel) / texSize) - onePixel / 2.;
//   return mix(
//     mix(at(pixel + vec2(0., 0.) * onePixel), at(pixel + vec2(1., 0.) * onePixel), frac.x),
//     mix(at(pixel + vec2(0., 1.) * onePixel), at(pixel + vec2(1., 1.) * onePixel), frac.x),
//     frac.y
//   );
// }

// vec4 NearestTextureSample (vec2 P) {
//   vec2 onePixel = 1. / texSize;
//   vec2 pixel = P * texSize;
//   vec2 frac = fract(pixel);
//   pixel = floor(pixel) / texSize;
//   return at(pixel + onePixel / 2.);
// }

//vec3 CubicHermite (vec3 A, vec3 B, vec3 C, vec3 D, float t) {
//    float t2 = t*t;
//    float t3 = t*t*t;
//    vec3 a = -A/2.0 + (3.0*B)/2.0 - (3.0*C)/2.0 + D/2.0;
//    vec3 b = A - (5.0*B)/2.0 + 2.0*C - D / 2.0;
//    vec3 c = -A/2.0 + C/2.0;
//    vec3 d = B;
//    return a * t3 + b * t2 + c*t + d;
//}

//vec3 BicubicHermiteTextureSample (vec2 P)
//{
//    vec2 pixel = P * texSize + .5;
//    vec2 onePixel = 1. / texSize;
//    vec2 twoPixels = 2. / texSize;
//    
//    vec2 frac = fract(pixel);
//    pixel = floor(pixel) / texSize - onePixel / 2.;
//    
//    vec3 C00 = at(pixel + vec2(-1., -1.) * onePixel).xyz;
//    vec3 C10 = at(pixel + vec2( 0., -1.) * onePixel).xyz;
//    vec3 C20 = at(pixel + vec2(-1., -1.) * onePixel).xyz;
//    vec3 C30 = at(pixel + vec2(twoPixels.x, -onePixel.y)).xyz;
//    
//    vec3 C01 = at(pixel + vec2(-onePixel.x , 0.0)).xyz;
//    vec3 C11 = at(pixel + vec2( 0.0        , 0.0)).xyz;
//    vec3 C21 = at(pixel + vec2( onePixel.x , 0.0)).xyz;
//    vec3 C31 = at(pixel + vec2( twoPixels.x, 0.0)).xyz;    
//    
//    vec3 C02 = at(pixel + vec2(-onePixel.x , onePixel.y)).xyz;
//    vec3 C12 = at(pixel + vec2( 0.0        , onePixel.y)).xyz;
//    vec3 C22 = at(pixel + vec2( onePixel.x , onePixel.y)).xyz;
//    vec3 C32 = at(pixel + vec2( twoPixels.x, onePixel.y)).xyz;    
//    
//    vec3 C03 = at(pixel + vec2(-onePixel.x , twoPixels.y)).xyz;
//    vec3 C13 = at(pixel + vec2( 0.0        , twoPixels.y)).xyz;
//    vec3 C23 = at(pixel + vec2( onePixel.x , twoPixels.y)).xyz;
//    vec3 C33 = at(pixel + vec2( twoPixels.y, twoPixels.y)).xyz;    
//    
//    vec3 CP0X = CubicHermite(C00, C10, C20, C30, frac.x);
//    vec3 CP1X = CubicHermite(C01, C11, C21, C31, frac.x);
//    vec3 CP2X = CubicHermite(C02, C12, C22, C32, frac.x);
//    vec3 CP3X = CubicHermite(C03, C13, C23, C33, frac.x);
//    
//    return CubicHermite(CP0X, CP1X, CP2X, CP3X, frac.y);
//}

vec4 TEX(vec2 uv) {
  return at(uv);
}