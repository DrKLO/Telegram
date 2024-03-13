#extension GL_OES_EGL_image_external : require

precision highp float;

uniform samplerExternalOES sTexture;

vec3 HLG_OETF_inv(vec3 x) {
    const float a = 0.17883277;
    const float b = 0.28466892;
    const float c = 0.55991073;
    return mix((x * x) / 3.0, (b + exp((x - c) / a)) / 12.0, step(0.5, x));
}

vec3 HLG_OOTF(vec3 x) {
    const float HLG_gamma = 1.2;
    const vec3 Lvec = vec3(0.2627, 0.6780, 0.0593);

    float Y = dot(Lvec, x);
    return pow(Y, HLG_gamma - 1.0) * x;
}

vec3 HLG_EOTF(vec3 x) {
    return HLG_OOTF(HLG_OETF_inv(x));
}

vec3 sRGB_EOTF(vec3 x) {
    return mix(x / 12.92, pow((x + 0.055) / 1.055, vec3(2.4)), step(0.04045, x));
}

vec3 sRGB_OETF(vec3 x) {
    return mix(12.92 * x, 1.055 * pow(x, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, x));
}

//HDR to SDR conversion method A described in ITU-R BT.2446-1
vec3 BT2446_tone_mapping_A(vec3 rgb_BT2020_linear) {
    const float L_HDR = 1000.0;
    const float rho_HDR = 1.0 + 32.0 * pow(L_HDR / 10000.0, 1.0 / 2.4);
    const float L_SDR = 100.0;
    const float rho_SDR = 1.0 + 32.0 * pow(L_SDR / 10000.0, 1.0 / 2.4);
    const vec3 Lvec = vec3(0.2627, 0.6780, 0.0593);

    vec3 rgb_prime = sRGB_OETF(rgb_BT2020_linear);

    float Y_prime = dot(Lvec, rgb_prime);

    float Y_prime_p = log(1.0 + (rho_HDR - 1.0) * Y_prime) / log(rho_HDR);
    float Y_prime_c = mix(1.0770 * Y_prime_p, mix((-1.1510 * Y_prime_p + 2.7811) * Y_prime_p - 0.6302, 0.5 * Y_prime_p + 0.5, step(0.9909, Y_prime_p)), step(0.7399, Y_prime_p));
    float Y_prime_SDR = (pow(rho_SDR, Y_prime_c) - 1.0) / (rho_SDR - 1.0);

    float f_Y_prime_SDR = Y_prime_SDR / (1.1 * Y_prime);
    float Cb_prime_TMO = f_Y_prime_SDR * (rgb_prime.b - Y_prime) / 1.8814;
    float Cr_prime_TMO = f_Y_prime_SDR * (rgb_prime.r - Y_prime) / 1.4746;
    float Y_prime_TMO = Y_prime_SDR - max(0.1 * Cr_prime_TMO, .0);

    float R_prime_TMO = Cr_prime_TMO * 1.4746 + Y_prime_TMO;
    float B_prime_TMO = Cb_prime_TMO * 1.8814 + Y_prime_TMO;
    float G_prime_TMO = (Y_prime_TMO - Lvec.x * R_prime_TMO - Lvec.z * B_prime_TMO) / Lvec.y;
    vec3 rgb_BT2020_prime_TMO = vec3(R_prime_TMO, G_prime_TMO, B_prime_TMO);

    return sRGB_EOTF(rgb_BT2020_prime_TMO);
}

vec4 transform(vec3 rgb_BT2020_hlg) {
    const mat3 BT2020_to_BT709 = mat3(1.6605, -0.1246, -0.0182, -0.5876, 1.1329, -0.1006, -0.0728, -0.0083, 1.1187);

    vec3 rgb_BT2020_linear = HLG_EOTF(rgb_BT2020_hlg);
    vec3 rgb_BT2020_linear_TMO = BT2446_tone_mapping_A(rgb_BT2020_linear);
    vec3 rgb_BT709_linear = clamp(BT2020_to_BT709 * rgb_BT2020_linear_TMO, 0.0, 1.0);
    vec3 rgb_sRGB = sRGB_OETF(rgb_BT709_linear);
    return vec4(rgb_sRGB, 1.0);
}

vec4 TEX(vec2 uv) {
    return transform(texture2D(sTexture, uv).rgb);
}
