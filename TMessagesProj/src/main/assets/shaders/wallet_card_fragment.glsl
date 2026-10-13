#version 300 es
precision highp float;

uniform mat4 uModelViewMatrix;
flat in vec3 vEngravingXAxis;
flat in vec3 vEngravingYAxis;
flat in vec3 vLightDirection;
flat in vec3 vRadialReferenceHalfway;
flat in vec2 vRadialHighlightAxis;
flat in vec2 vGradientAxis;
#ifndef WALLET_EDGE_PASS
flat in vec3 vFaceNormal;
flat in vec3 vRadialShineHalfway;
flat in float vRadialLightResponse;
#endif

uniform float uCardGradientRotation;
uniform sampler2D uCardDetailTexture;
uniform sampler2D uEngravingTexture;
#ifdef WALLET_FINISH_LUT
uniform highp sampler2D uFinishLut;
#endif
#ifdef WALLET_FLECKS_PASS
uniform lowp sampler2DArray uFlecksTiles;
uniform sampler2D uFlecksTail;
flat in vec3 vFlecksRegion;
#endif
out vec4 fragmentColor;
uniform vec2 uEngravingTexel;
uniform vec3 uMainLightDirection;
uniform vec2 uQrCenter;

in vec3 vObjectPosition;
in vec3 vObjectNormal;
in vec3 vViewPosition;
in vec2 vCardPosition;

const float PI = 3.14159265359;

// Radial Blue preset captured from the WebGL material editor.
const float RADIAL_FREQUENCY = 1.40;
const float RADIAL_DETAIL = 0.30;
const float FINISH_DETAIL = 1.65;
const float ANISOTROPY = 1.00;
const float ROUGHNESS_SCALE = 0.80;
const float SPECULAR_STRENGTH = 0.80;
const float SPECULAR_MAX = 0.28;
const float DIFFUSE_STRENGTH = 1.40;
const float ENVIRONMENT_SPECULAR_STRENGTH = 1.45;
const float AMBIENT_DIFFUSE_STRENGTH = 0.70;
const float AMBIENT_DETAIL_STRENGTH = 0.50;
const float GRAZING_STRENGTH = 0.50;
const float BLUE_SATURATION = 1.16;
const float EDGE_REFLECTION_STRENGTH = 2.50;
const float STUDIO_FILL_STRENGTH = 1.00;
const float STUDIO_SOFTBOX_STRENGTH = 1.00;
const float LIGHT_INTENSITY = 1.70;
const float ENVIRONMENT_INTENSITY = 0.80;
const float EXPOSURE = 1.30;
const float QR_LIGHTING_STRENGTH = 0.95;
const float QR_ROUGHNESS_SCALE = 0.30;
const float QR_REFLECTION_STRENGTH = 2.05;
const float QR_ENGRAVING_STRENGTH = 1.00;
const float ENGRAVING_DEPTH = 2.00;
// Calibrated to the existing resting highlight; independent of light alignment.
const float RADIAL_SHINE_SLOPE = 0.103713069;
const vec2 RADIAL_SHINE_ROTATION_RESPONSE = vec2(2.0, 4.0);
const float RADIAL_SHINE_LIGHT_FALLOFF = 8.0;
const float RADIAL_HIGHLIGHT_COVERAGE = 0.71;
const float RADIAL_DIRECT_SPECULAR = 0.19;
const float RADIAL_DIRECT_SPECULAR_MAX = 0.41;
const float RADIAL_DETAIL_SPECULAR = 1.05;
const float RADIAL_DETAIL_SPECULAR_MAX = 1.03;
const float FLECKS_SPECULAR_STRENGTH = 0.84;
const float FLECKS_SPECULAR_POWER = 12.0;
const float FLECKS_SPECULAR_WHITE_BOOST = 1.00;
const float FLECKS_WHITE_RESPONSE_START = 0.75;
const float FLECKS_WHITE_RESPONSE_FULL = 0.95;
const float FLECKS_MAIN_SPECULAR_START = 0.006;
const float FLECKS_MAIN_SPECULAR_FULL = 0.08;
const vec2 FLECKS_CARD_UV_SCALE = vec2(1.0, 0.6103515625);

// Current camera-space detail lighting captured from the web Radial Blue editor.
const vec3 DETAIL_LIGHT_DIRECTION = vec3(0.12, -0.54, 0.45);
const float DETAIL_LIGHT_STRENGTH = 0.10;
const float DETAIL_LIGHT_SOFTNESS = 2.10;
// Calibrated before exposure, ACES and saturation so the resting front body
// renders as #229AF0. Using the raw linearized hex color renders too cyan.
const vec3 CARD_COLOR = vec3(0.09729573, 0.29946935, 1.1024685);
// Calibrated blue/cyan endpoints for the 2D-style sweep, around #229AF0.
const vec3 CARD_GRADIENT_DARK = vec3(0.06964265, 0.22803165, 0.74607265);
const vec3 CARD_GRADIENT_BRIGHT = vec3(0.14900434, 0.42189533, 2.1465068);

float hash(float value) {
    return fract(sin(value * 127.1) * 43758.5453);
}

vec3 fresnelSchlick(float cosine, vec3 reflectance) {
    return reflectance + (1.0 - reflectance)
            * pow(1.0 - cosine, 5.0);
}

float anisotropicDistribution(
        float normalHalf,
        float tangentHalf,
        float bitangentHalf,
        float alphaTangent,
        float alphaBitangent
) {
    float denominator =
            tangentHalf * tangentHalf / (alphaTangent * alphaTangent)
                    + bitangentHalf * bitangentHalf
                    / (alphaBitangent * alphaBitangent)
                    + normalHalf * normalHalf;
    return 1.0 / max(
            PI * alphaTangent * alphaBitangent
                    * denominator * denominator,
            0.0001
    );
}

float anisotropicVisibility(
        float normalDirection,
        float tangentDirection,
        float bitangentDirection,
        float alphaTangent,
        float alphaBitangent
) {
    float projectedRoughness = sqrt(
            tangentDirection * tangentDirection
                    * alphaTangent * alphaTangent
                    + bitangentDirection * bitangentDirection
                    * alphaBitangent * alphaBitangent
                    + normalDirection * normalDirection
    );
    return 2.0 * normalDirection / max(
            normalDirection + projectedRoughness,
            0.0001
    );
}

float colorPeak(vec3 color) {
    return max(max(color.r, color.g), color.b);
}

vec3 capSpecular(vec3 specular, float limit) {
    return specular * min(1.0, limit / max(colorPeak(specular), 0.00001));
}

float wrappedDiffuse(vec3 normal, vec3 lightDirection, float wrap) {
    return clamp((dot(normal, lightDirection) + wrap) / (1.0 + wrap), 0.0, 1.0);
}

vec3 studioEnvironment(vec3 direction, float roughness) {
    float horizon = smoothstep(-0.45, 0.72, direction.y);
    vec3 environment = mix(
            vec3(0.055, 0.062, 0.075),
            vec3(0.32, 0.36, 0.42),
            horizon
    );

    float frontalFill = pow(
            max(dot(
                    direction,
                    normalize(vec3(0.0, 0.22, 0.98))
            ), 0.0),
            mix(3.6, 1.25, roughness)
    );
    float overheadFill = pow(
            max(dot(
                    direction,
                    normalize(vec3(0.0, 0.82, 0.57))
            ), 0.0),
            mix(5.0, 1.5, roughness)
    );
    environment += vec3(0.24, 0.29, 0.36)
            * frontalFill * 0.9 * STUDIO_FILL_STRENGTH;
    environment += vec3(0.17, 0.21, 0.28)
            * overheadFill * 0.55 * STUDIO_FILL_STRENGTH;

    float keySoftbox = pow(
            max(dot(
                    direction,
                    normalize(vec3(-0.52, 0.46, 0.72))
            ), 0.0),
            mix(18.0, 3.5, roughness)
    );
    float rimSoftbox = pow(
            max(dot(
                    direction,
                    normalize(vec3(0.72, 0.12, 0.68))
            ), 0.0),
            mix(34.0, 5.5, roughness)
    );
    environment += vec3(1.0, 0.93, 0.76)
            * keySoftbox * 2.7 * STUDIO_SOFTBOX_STRENGTH;
    environment += vec3(0.62, 0.72, 0.90)
            * rimSoftbox * 1.1 * STUDIO_SOFTBOX_STRENGTH;
    return environment;
}

vec3 acesToneMap(vec3 color) {
    return clamp(
            (color * (2.51 * color + 0.03))
                    / (color * (2.43 * color + 0.59) + 0.14),
            0.0,
            1.0
    );
}

void main() {
#ifdef WALLET_FINISH_BAKE_PASS
    float index = floor(gl_FragCoord.x);
    vec2 values = gl_FragCoord.y < 1.0
            ? vec2(hash(index), hash(index * 0.37))
            : vec2(hash(index + 41.0), hash(index * 0.61));
    vec2 high = unpackHalf2x16(packHalf2x16(values));
    fragmentColor = vec4(high, values - high);
#else
    // These are separate meshes with X normals of exactly +/-1 (faces) or
    // 0 (edge). Specialize their materials so unused lighting is compiled out.
#ifdef WALLET_EDGE_PASS
    const float edgeMaterial = 1.0;
    const float face = 0.0;
#else
    const float edgeMaterial = 0.0;
    const float face = 1.0;
#endif
    float frontFace = face * step(vObjectNormal.x, -0.5);
    vec2 cardUv = vec2(
            vCardPosition.x * 0.5 + 0.5,
            0.5 - vCardPosition.y / 1.219512
    );
    // Only the insert pass samples the QR masks. The body is replaced by
    // the original, pre-tone-map material mix wherever that mask contributes.
#if defined(WALLET_QR_PASS) || defined(WALLET_FLECKS_PASS)
    const vec2 qrTexel = vec2(1.0 / 1024.0, 1.0 / 625.0);
    vec2 qrFilterTexel = max(
            qrTexel,
            fwidth(cardUv) * 0.75
    );
    vec4 cardDetailCenter = texture(uCardDetailTexture, cardUv);
    vec4 qrLeftSample = texture(
            uCardDetailTexture,
            cardUv - vec2(qrFilterTexel.x, 0.0)
    );
    vec4 qrRightSample = texture(
            uCardDetailTexture,
            cardUv + vec2(qrFilterTexel.x, 0.0)
    );
    vec4 qrTopSample = texture(
            uCardDetailTexture,
            cardUv - vec2(0.0, qrFilterTexel.y)
    );
    vec4 qrBottomSample = texture(
            uCardDetailTexture,
            cardUv + vec2(0.0, qrFilterTexel.y)
    );
    vec4 cardDetail = (
            cardDetailCenter * 4.0
                    + qrLeftSample
                    + qrRightSample
                    + qrTopSample
                    + qrBottomSample
    ) * 0.125;
    float qrMaskFootprint = max(
            qrFilterTexel.x / qrTexel.x,
            qrFilterTexel.y / qrTexel.y
    );
    float qrMaskNormalFilter = inversesqrt(
            max(qrMaskFootprint, 1.0)
    );
    vec2 qrShapeGradient = vec2(
            qrRightSample.a - qrLeftSample.a,
            qrBottomSample.a - qrTopSample.a
    );
    vec2 qrGlyphGradient = vec2(
            qrRightSample.g - qrLeftSample.g,
            qrBottomSample.g - qrTopSample.g
    );
    vec2 qrEngravingGradient = (
            qrGlyphGradient * 0.72
                    + qrShapeGradient * 0.18
    ) * QR_ENGRAVING_STRENGTH * qrMaskNormalFilter;
    float qrButton = cardDetail.a
            * frontFace * (1.0 - edgeMaterial);
    float qrGlyph = cardDetail.g
            * frontFace * (1.0 - edgeMaterial);
    float qrMaterialGradient = cardDetail.b;
#else
    const float qrButton = 0.0;
#endif

#ifdef WALLET_EDGE_PASS
    // Smooth the rounded edge analytically. The exported edge mesh contains
    // duplicated quad vertices, so relying on its per-face normals exposes
    // every segment.
    vec2 sideDelta = max(
            abs(vec2(vObjectPosition.y, vObjectPosition.z))
                    - vec2(0.484756, 0.875),
            vec2(0.0)
    );
    vec2 smoothSideDirection = normalize(
            sideDelta * sign(vec2(
                    vObjectPosition.y,
                    vObjectPosition.z
            )) + vec2(0.000001)
    );
    vec3 smoothSideNormal = vec3(
            0.0,
            smoothSideDirection.x,
            smoothSideDirection.y
    );
    vec3 objectNormal = normalize(mix(
            smoothSideNormal,
            vObjectNormal,
            face
    ));
    vec3 normal = normalize(
            mat3(uModelViewMatrix) * objectNormal
    );
#else
    vec3 normal = vFaceNormal;
#endif
    vec3 flatNormal = normal;
#ifdef WALLET_BODY_PASS
    // The small engraved region is replaced by its own bounded material pass.
    const float engraving = 0.0;
    const vec2 engravingGradient = vec2(0.0);
#else
    vec2 engravingFilterTexel = max(
            uEngravingTexel,
            fwidth(cardUv) * 0.75
    );
    float engravingCenter = texture(
            uEngravingTexture,
            cardUv
    ).a;
    float engravingTopLeft = texture(
            uEngravingTexture,
            cardUv - engravingFilterTexel
    ).a;
    float engravingTopRight = texture(
            uEngravingTexture,
            cardUv + vec2(
                    engravingFilterTexel.x,
                    -engravingFilterTexel.y
            )
    ).a;
    float engravingBottomLeft = texture(
            uEngravingTexture,
            cardUv + vec2(
                    -engravingFilterTexel.x,
                    engravingFilterTexel.y
            )
    ).a;
    float engravingBottomRight = texture(
            uEngravingTexture,
            cardUv + engravingFilterTexel
    ).a;
    float engraving = (
            engravingCenter * 4.0
                    + engravingTopLeft
                    + engravingTopRight
                    + engravingBottomLeft
                    + engravingBottomRight
    ) * 0.125 * frontFace;
    float engravingFootprint = max(
            engravingFilterTexel.x / max(uEngravingTexel.x, 0.000001),
            engravingFilterTexel.y / max(uEngravingTexel.y, 0.000001)
    );
    float engravingNormalFilter = inversesqrt(
            max(engravingFootprint, 1.0)
    );
    vec2 engravingGradient = vec2(
            engravingTopRight
                    + engravingBottomRight
                    - engravingTopLeft
                    - engravingBottomLeft,
            engravingBottomLeft
                    + engravingBottomRight
                    - engravingTopLeft
                    - engravingTopRight
    ) * 0.5 * frontFace * engravingNormalFilter;
#endif
    vec3 engravingXAxis = vEngravingXAxis;
    vec3 engravingYAxis = vEngravingYAxis;
    normal = normalize(
            normal
                    - engravingXAxis * engravingGradient.x
                    * 0.42 * ENGRAVING_DEPTH
                    + engravingYAxis * engravingGradient.y
                    * 0.42 * ENGRAVING_DEPTH
    );
    vec3 surfaceNormal = normal;
#ifdef WALLET_FLECKS_PASS
    vec2 flecksUv = cardUv * FLECKS_CARD_UV_SCALE;
    vec2 flecksDx = dFdx(flecksUv), flecksDy = dFdy(flecksUv);
    vec2 pixelDx = flecksDx * 512.0, pixelDy = flecksDy * 512.0;
    float footprintSquared = max(dot(pixelDx, pixelDx), dot(pixelDy, pixelDy));
    vec4 flecksNormalSample;
    // The small tiles and the mip tail share original levels 2 and 3.
    // Switch inside that overlap, retaining native trilinear filtering.
    if (footprintSquared >= 32.0) {
        flecksNormalSample = textureGrad(uFlecksTail, flecksUv, flecksDx, flecksDy);
    } else {
        vec2 tileUv = (flecksUv * 512.0 - vFlecksRegion.xy) / 48.0;
        flecksNormalSample = textureGrad(uFlecksTiles, vec3(tileUv, vFlecksRegion.z),
                flecksDx * (512.0 / 48.0), flecksDy * (512.0 / 48.0));
    }
    float flecksTextureAlpha = flecksNormalSample.a;
    float flecksMask = flecksTextureAlpha
            * (1.0 - smoothstep(0.02, 0.98, qrButton));
#endif

#if defined(WALLET_QR_PASS) || defined(WALLET_FLECKS_PASS)
    vec2 qrPosition = vec2(
            (cardUv.x - uQrCenter.x) * 2.0,
            -(cardUv.y - uQrCenter.y) * 1.219512
    );
    float qrRadius = length(qrPosition);
    vec2 qrRadial = qrPosition / max(qrRadius, 0.0001);
    if (qrRadius < 0.0001) {
        qrRadial = vec2(1.0, 0.0);
    }

    // Derivatives must be evaluated outside the non-uniform material branch.
    float qrFootprint = fwidth(qrRadius);
    // Keep derivative evaluation and all implicit-LOD texture reads above
    // discard, including helper fragments along the filtered insert border.
    // Reuse the front mesh so coverage, depth and card UVs match the body draw.
#endif
    float radius = length(vCardPosition);
    float radialFootprint = fwidth(radius) * RADIAL_FREQUENCY;
#ifdef WALLET_ENGRAVING_PASS
    // All texture reads and derivatives precede discard so filtered edges keep
    // the same helper-fragment results as the original full-face material.
    if (engraving <= 0.0) discard;
#endif
#ifdef WALLET_QR_PASS
    if (qrButton <= 0.0) discard;
#endif
#ifdef WALLET_FLECKS_PASS
    if (flecksMask <= 0.0) discard;
#endif
    vec2 radial = vCardPosition / max(radius, 0.0001);
    if (radius < 0.0001) {
        radial = vec2(1.0, 0.0);
    }

    // Card-space circular brushing tangent. Card X is the OBJ's Z axis and
    // card Y is the OBJ's Y axis.
    vec3 tangent = normalize(
            mat3(uModelViewMatrix)
                    * vec3(0.0, radial.x, -radial.y)
    );
    vec3 bitangent = normalize(cross(normal, tangent));

    float radialRingFrequency = 180.0 * RADIAL_FREQUENCY;
    float radialWaveFrequency = 560.0 * RADIAL_FREQUENCY;
    float ringIndex = floor(radius * radialRingFrequency);
    float radialRingFilter = 1.0 - smoothstep(
            0.35,
            1.10,
            radialFootprint * 180.0
    );
    float radialWaveFilter = 1.0 - smoothstep(
            1.15,
            3.10,
            radialFootprint * 560.0
    );
#ifdef WALLET_FINISH_LUT
    vec4 ringNoiseSample = texelFetch(uFinishLut, ivec2(int(ringIndex), 0), 0);
    vec2 ringNoise = ringNoiseSample.xy + ringNoiseSample.zw;
#else
    vec2 ringNoise = vec2(hash(ringIndex), hash(ringIndex * 0.37));
#endif
    float fineFinish = mix(
            0.5,
            ringNoise.x,
            radialRingFilter
    ) + 0.35 * sin(
            radius * radialWaveFrequency
                    + ringNoise.y * 6.2831
    ) * radialWaveFilter;
    float finishVariation = mix(
            0.0,
            fineFinish,
            FINISH_DETAIL * RADIAL_DETAIL
    );
    float radialCoarseFilter = 1.0 - smoothstep(
            0.65,
            2.4,
            radialFootprint * radialRingFrequency
    );
    float radialMachiningSlope = (
            cos(radius * radialRingFrequency + 0.45)
                    * 0.12 * radialCoarseFilter
                    + cos(
                            radius * radialWaveFrequency
                                    + ringNoise.y * 6.2831
                    ) * 0.065 * radialWaveFilter
    ) * FINISH_DETAIL * RADIAL_DETAIL
            * (1.0 - edgeMaterial)
            * (1.0 - smoothstep(0.02, 0.98, qrButton));

    float baseRoughness = mix(0.16, 0.115, ANISOTROPY)
            * ROUGHNESS_SCALE;
    float roughness = clamp(
            baseRoughness
                    + (finishVariation - 0.5) * 0.045
                    * (1.0 - edgeMaterial),
            0.045,
            0.78
    );
    roughness = mix(
            roughness,
            clamp(baseRoughness * 0.82, 0.1, 0.5),
            edgeMaterial
    );

    float aspect = mix(
            1.0,
            0.18,
            ANISOTROPY * (1.0 - edgeMaterial)
    );
    float alphaTangent = max(
            roughness * roughness * aspect,
            0.012
    );
    float alphaBitangent = max(
            roughness * roughness / aspect,
            0.012
    );

    vec3 viewDirection = normalize(-vViewPosition);
    vec3 lightDirection = vLightDirection;
    vec3 halfwayDirection = normalize(
            viewDirection + lightDirection
    );

    float normalLight = max(dot(normal, lightDirection), 0.0);
    float normalView = clamp(dot(normal, viewDirection), 0.0, 1.0);
    float normalHalf = max(dot(normal, halfwayDirection), 0.0);
    float viewHalf = clamp(
            dot(viewDirection, halfwayDirection),
            0.0, 1.0
    );

    float tangentHalf = dot(tangent, halfwayDirection);
    float bitangentHalf = dot(bitangent, halfwayDirection);
    float tangentLight = dot(tangent, lightDirection);
    float bitangentLight = dot(bitangent, lightDirection);
    float tangentView = dot(tangent, viewDirection);
    float bitangentView = dot(bitangent, viewDirection);

    // Match the 2D sweep's 20.99 degree angle and its pitch/yaw response.
    float gradientAlignment = dot(radial, vGradientAxis);
    // Smooth opposing lobes and soften their intersection like the 2D blur.
    float sweepGradient = mix(0.5, gradientAlignment * gradientAlignment,
            smoothstep(0.02, 0.22, radius));
    vec3 gradientColor = mix(CARD_GRADIENT_DARK, CARD_COLOR,
            min(sweepGradient * 2.0, 1.0));
    gradientColor = mix(gradientColor, CARD_GRADIENT_BRIGHT,
            max(sweepGradient * 2.0 - 1.0, 0.0));
    vec3 baseColor = gradientColor * 0.30 / 0.2788;
    float cardColorPeak = max(
            max(CARD_COLOR.r, CARD_COLOR.g),
            max(CARD_COLOR.b, 0.0001)
    );
    vec3 cardAccent = sqrt(clamp(
            CARD_COLOR / cardColorPeak,
            0.0,
            1.0
    ));
    vec3 edgeReflectance = mix(
            vec3(0.018),
            cardAccent * 0.78,
            0.94
    );
    vec3 reflectance = mix(
            baseColor,
            edgeReflectance * EDGE_REFLECTION_STRENGTH,
            edgeMaterial
    );
    reflectance *= mix(
            0.91,
            1.06,
            finishVariation * (1.0 - edgeMaterial)
    );
    vec3 surfaceDiffuseColor = mix(
            baseColor,
            edgeReflectance,
            edgeMaterial
    );

    float distribution = anisotropicDistribution(
            normalHalf,
            tangentHalf,
            bitangentHalf,
            alphaTangent,
            alphaBitangent
    );
    float visibility =
            anisotropicVisibility(
                    normalLight,
                    tangentLight,
                    bitangentLight,
                    alphaTangent,
                    alphaBitangent
            ) * anisotropicVisibility(
                    normalView,
                    tangentView,
                    bitangentView,
                    alphaTangent,
                    alphaBitangent
            );
    vec3 blueGrazingReflectance = mix(
            vec3(0.04),
            cardAccent,
            0.94
    );
    vec3 fresnel = mix(
            reflectance,
            blueGrazingReflectance,
            pow(1.0 - viewHalf, 5.0)
    );
    vec3 direct = distribution * visibility * fresnel;
    direct *= normalLight / max(
            4.0 * normalLight * normalView,
            0.0001
    );

    // Centered, opposing brushed lobes. The real light still drives visibility,
    // Fresnel and flecks; a stable halfway direction controls the shine shape.
    vec3 radialViewDirection = vec3(0.0, 0.0, 1.0);
    vec3 radialReferenceHalfway = vRadialReferenceHalfway;
    vec2 radialHighlightAxis = vRadialHighlightAxis;
    // Use a fixed polar angle for the brushed lobe. Width and peak energy no
    // longer collapse or explode as the real light passes through alignment.
#ifdef WALLET_EDGE_PASS
    vec3 radialShineHalfway = normalize(
            flatNormal * sqrt(1.0 - RADIAL_SHINE_SLOPE * RADIAL_SHINE_SLOPE)
                    + (engravingXAxis * radialHighlightAxis.x
                            + engravingYAxis * radialHighlightAxis.y) * RADIAL_SHINE_SLOPE
    );
#else
    vec3 radialShineHalfway = vRadialShineHalfway;
#endif
    float radialHighlightAlignment = abs(dot(radial, radialHighlightAxis));
    float radialHighlightHalfAngle =
            RADIAL_HIGHLIGHT_COVERAGE * PI * 0.5;
    float radialHighlightLobe = smoothstep(
            cos(radialHighlightHalfAngle),
            cos(radialHighlightHalfAngle * 0.28),
            radialHighlightAlignment
    );

    float radialNormalLight = normalLight;
    float radialNormalView = max(
            dot(normal, radialViewDirection),
            0.0
    );
    float radialNormalHalf = max(
            dot(normal, radialShineHalfway),
            0.0
    );
    float radialViewHalf = clamp(
            dot(radialViewDirection, radialReferenceHalfway),
            0.0, 1.0
    );
    float radialTangentHalf = dot(tangent, radialShineHalfway);
    float radialBitangentHalf = dot(bitangent, radialShineHalfway);
    float radialTangentView = dot(tangent, radialViewDirection);
    float radialBitangentView = dot(bitangent, radialViewDirection);
    float centeredRadialDistribution = anisotropicDistribution(
            radialNormalHalf,
            radialTangentHalf,
            radialBitangentHalf,
            alphaTangent,
            alphaBitangent
    );
    float centeredRadialVisibility =
            anisotropicVisibility(
                    radialNormalLight,
                    tangentLight,
                    bitangentLight,
                    alphaTangent,
                    alphaBitangent
            ) * anisotropicVisibility(
                    radialNormalView,
                    radialTangentView,
                    radialBitangentView,
                    alphaTangent,
                    alphaBitangent
            );
    vec3 centeredRadialFresnel = mix(
            reflectance,
            blueGrazingReflectance,
            pow(1.0 - radialViewHalf, 5.0)
    );

    // Independent normal-detail specular channel. Paired subpixel groove
    // slopes preserve the symmetric lobes and expose the machining without
    // relying on roughness or creating unbounded hot pixels.
    vec3 radialSlopeDirection =
            -engravingXAxis * radial.x + engravingYAxis * radial.y;
    vec3 radialTextureNormalA = normalize(
            normal + radialSlopeDirection * radialMachiningSlope
    );
    vec3 radialTextureNormalB = normalize(
            normal - radialSlopeDirection * radialMachiningSlope
    );
    float radialNormalExponent = mix(
            28.0,
            70.0,
            1.0 - roughness
    );
    float radialFlatNormalResponse =
            radialNormalLight
            * pow(
                    radialNormalHalf,
                    radialNormalExponent
            );
    float radialTextureNormalResponse = 0.5 * (
            max(dot(radialTextureNormalA, lightDirection), 0.0)
                    * pow(
                            max(dot(
                                    radialTextureNormalA,
                                    radialShineHalfway
                            ), 0.0),
                            radialNormalExponent
                    )
                    + max(dot(
                            radialTextureNormalB,
                            lightDirection
                    ), 0.0)
                    * pow(
                            max(dot(
                                    radialTextureNormalB,
                                    radialShineHalfway
                            ), 0.0),
                            radialNormalExponent
                    )
    );
    float radialTextureNormalRatio =
            radialTextureNormalResponse
                    / max(radialFlatNormalResponse, 0.0001);
    float radialDetailSpecularResponse = smoothstep(
            0.015,
            0.28,
            abs(radialTextureNormalRatio - 1.0)
    );

    vec3 centeredRadialDirect =
            centeredRadialDistribution
                    * centeredRadialVisibility
                    * centeredRadialFresnel;
    centeredRadialDirect *= radialNormalLight / max(
            4.0 * radialNormalLight * radialNormalView,
            0.0001
    );
    float centeredRadialPeak = colorPeak(centeredRadialDirect);
    centeredRadialDirect /= 1.0 + centeredRadialPeak;
    // Stabilize shape, not illumination: a broad real-light envelope dims the
    // shine as the card turns away, without changing the lobe's angular width.
    // Normalize at the existing resting pose to preserve its brightness.
#ifdef WALLET_EDGE_PASS
    float radialRestNormalHalf = sqrt(1.0 - RADIAL_SHINE_SLOPE * RADIAL_SHINE_SLOPE);
    float radialRestNormalLight = 1.0 - 2.0 * RADIAL_SHINE_SLOPE * RADIAL_SHINE_SLOPE;
    float radialLightResponse = clamp(dot(flatNormal, lightDirection), 0.0, 1.0)
            * pow(clamp(dot(flatNormal, radialReferenceHalfway), 0.0, 1.0),
                    RADIAL_SHINE_LIGHT_FALLOFF);
    float radialRestLightResponse = radialRestNormalLight
            * pow(radialRestNormalHalf, RADIAL_SHINE_LIGHT_FALLOFF);
    centeredRadialDirect *= clamp(radialLightResponse / radialRestLightResponse, 0.0, 1.0);
#else
    centeredRadialDirect *= vRadialLightResponse;
#endif
    vec3 radialBroadSpecular = centeredRadialDirect
            * radialHighlightLobe
            * RADIAL_DIRECT_SPECULAR;
    radialBroadSpecular = capSpecular(radialBroadSpecular, RADIAL_DIRECT_SPECULAR_MAX);
    vec3 radialNormalSpecular = centeredRadialDirect
            * radialHighlightLobe
            * radialDetailSpecularResponse
            * RADIAL_DETAIL_SPECULAR
            * 0.24;
    radialNormalSpecular = capSpecular(radialNormalSpecular, RADIAL_DETAIL_SPECULAR_MAX);
    direct = mix(
            direct,
            radialBroadSpecular + radialNormalSpecular,
            1.0 - edgeMaterial
    );

    // Diffuse-only secondary light. Its virtual, filtered micro-relief makes
    // Radial Blue's roughness-based machining readable without introducing a
    // second glossy lobe.
    vec3 detailLightDirection = normalize(DETAIL_LIGHT_DIRECTION);
    float detailDiffuseWrap = clamp(
            (DETAIL_LIGHT_SOFTNESS - 0.5) / 3.5,
            0.0,
            1.0
    ) * 0.8;
    vec3 detailSurfaceNormal = normalize(
            surfaceNormal
                    - engravingXAxis * radialMachiningSlope * radial.x
                    + engravingYAxis * radialMachiningSlope * radial.y
    );
    float detailSurfaceDiffuse = wrappedDiffuse(
            detailSurfaceNormal, detailLightDirection, detailDiffuseWrap
    );
    float detailFlatDiffuse = wrappedDiffuse(
            flatNormal, detailLightDirection, detailDiffuseWrap
    );
    float detailDiffuse = max(
            detailSurfaceDiffuse - detailFlatDiffuse,
            0.0
    ) * 14.0;

    vec3 reflectionDirection = reflect(-viewDirection, normal);
    vec3 environment = studioEnvironment(
            reflectionDirection,
            roughness
    );
    environment *= vec3(0.30) + cardAccent * 0.86;
    vec3 environmentFresnel = mix(
            reflectance,
            blueGrazingReflectance,
            pow(1.0 - normalView, 5.0)
    );
    vec3 indirect = environment * environmentFresnel
            * mix(0.92, 0.60, roughness)
            * ENVIRONMENT_INTENSITY
            * ENVIRONMENT_SPECULAR_STRENGTH;

    float grazing = pow(1.0 - normalView, 3.0);
    vec3 directSpecular = direct
            * LIGHT_INTENSITY
            * SPECULAR_STRENGTH;
    directSpecular = capSpecular(directSpecular, SPECULAR_MAX);
    vec3 color = indirect + directSpecular;
#ifdef WALLET_FLECKS_PASS
    float cappedDirectSpecularPeak = colorPeak(directSpecular);
    // Transparent normal-map texels have no sparkle contribution.
    if (flecksMask > 0.0) {
        vec3 flecksNormalColor = flecksNormalSample.rgb
                / max(flecksTextureAlpha, 0.00392157);
        vec3 flecksTangentNormal = normalize(
                flecksNormalColor * 2.0 - 1.0
        );
        vec3 flecksNormal = normalize(
                engravingXAxis * flecksTangentNormal.x
                        - engravingYAxis * flecksTangentNormal.y
                        + surfaceNormal * flecksTangentNormal.z
        );
        float flecksNormalSpecular =
                max(dot(flecksNormal, lightDirection), 0.0)
                        * pow(
                                max(dot(flecksNormal, halfwayDirection), 0.0),
                                FLECKS_SPECULAR_POWER
                        );
        float flecksMainHighlight = smoothstep(
                FLECKS_MAIN_SPECULAR_START,
                FLECKS_MAIN_SPECULAR_FULL,
                cappedDirectSpecularPeak
        );
        float flecksSpecularResponse = flecksMask
                * flecksMainHighlight
                * flecksNormalSpecular;
        float flecksWhiteResponse = smoothstep(
                FLECKS_WHITE_RESPONSE_START,
                FLECKS_WHITE_RESPONSE_FULL,
                flecksNormalSpecular
        );
        vec3 flecksSpecularColor = mix(
                cardAccent,
                vec3(1.0),
                flecksWhiteResponse * FLECKS_SPECULAR_WHITE_BOOST
        );
        color += flecksSpecularColor
                * flecksSpecularResponse
                * FLECKS_SPECULAR_STRENGTH;
    }
#endif
    color += surfaceDiffuseColor
            * detailDiffuse
            * 0.24
            * DETAIL_LIGHT_STRENGTH;
    color += surfaceDiffuseColor
            * max(dot(surfaceNormal, lightDirection), 0.0)
            * 0.055
            * LIGHT_INTENSITY
            * DIFFUSE_STRENGTH;
    color += surfaceDiffuseColor
            * 0.055
            * ENVIRONMENT_INTENSITY
            * AMBIENT_DIFFUSE_STRENGTH;

    float blueLuminance = dot(
            color,
            vec3(0.2126, 0.7152, 0.0722)
    );
    color = max(
            mix(vec3(blueLuminance), color, BLUE_SATURATION),
            vec3(0.0)
    );
    color += vec3(0.018, 0.18, 0.42)
            * grazing * 0.28 * GRAZING_STRENGTH;
    float unlitFinish = (finishVariation - 0.5)
            * (1.0 - edgeMaterial);
    color *= max(
            0.0,
            1.0 + unlitFinish * 0.18 * AMBIENT_DETAIL_STRENGTH
    );
    color *= 1.0 - engraving * 0.24 * ENGRAVING_DEPTH;

#if defined(WALLET_QR_PASS) || defined(WALLET_FLECKS_PASS)
    // Figma QR masks are sampled in card-face space. The gray insert gets its
    // own concentric brushed-metal response and the glyph is recessed into it.
    // The insert occupies only a small part of the front face. Its full BRDF
    // contributes exactly zero everywhere else (including the back and edge).
    if (qrButton > 0.0) {
        vec3 qrNormal = normalize(
                surfaceNormal
                        - engravingXAxis * qrEngravingGradient.x
                        * 0.42 * ENGRAVING_DEPTH
                        + engravingYAxis * qrEngravingGradient.y
                        * 0.42 * ENGRAVING_DEPTH
        );

        vec3 qrTangent = normalize(
                mat3(uModelViewMatrix)
                        * vec3(0.0, qrRadial.x, -qrRadial.y)
        );
        vec3 qrBitangent = normalize(cross(qrNormal, qrTangent));

        float qrRingIndex = floor(qrRadius * 2300.0);
        float qrRingFilter = 1.0 - smoothstep(
                0.3,
                1.1,
                qrFootprint * 2300.0
        );
        float qrWaveFilter = 1.0 - smoothstep(
                0.3,
                1.1,
                qrFootprint * 9200.0
        );
#ifdef WALLET_FINISH_LUT
        vec4 qrNoiseSample = texelFetch(uFinishLut, ivec2(int(qrRingIndex), 1), 0);
        vec2 qrRingNoise = qrNoiseSample.xy + qrNoiseSample.zw;
#else
        vec2 qrRingNoise = vec2(hash(qrRingIndex + 41.0), hash(qrRingIndex * 0.61));
#endif
        float qrFineFinish = mix(
                0.5,
                qrRingNoise.x,
                qrRingFilter
        ) + 0.28 * sin(
                qrRadius * 9200.0
                        + qrRingNoise.y * 6.2831
        ) * qrWaveFilter;
        float qrFinishVariation = mix(
                0.0,
                qrFineFinish,
                FINISH_DETAIL
        );
        float qrRoughness = clamp(
                0.105 * ROUGHNESS_SCALE * QR_ROUGHNESS_SCALE
                        + (qrFinishVariation - 0.5) * 0.038,
                0.045,
                0.22
        );
        float qrAspect = mix(1.0, 0.12, ANISOTROPY);
        float qrAlphaTangent = max(
                qrRoughness * qrRoughness * qrAspect,
                0.008
        );
        float qrAlphaBitangent = max(
                qrRoughness * qrRoughness / qrAspect,
                0.008
        );
        float qrNormalLight = max(dot(qrNormal, lightDirection), 0.0);
        float qrNormalView = max(dot(qrNormal, viewDirection), 0.0);
        // Share the card's stable shine direction so both brushed materials
        // reflect at the same angles as the card tilts or the light moves.
        float qrNormalHalf = max(dot(qrNormal, radialShineHalfway), 0.0);
        float qrTangentHalf = dot(qrTangent, radialShineHalfway);
        float qrBitangentHalf = dot(qrBitangent, radialShineHalfway);
        float qrDistribution = anisotropicDistribution(
                qrNormalHalf,
                qrTangentHalf,
                qrBitangentHalf,
                qrAlphaTangent,
                qrAlphaBitangent
        );
        float qrVisibility =
                anisotropicVisibility(
                        qrNormalLight,
                        dot(qrTangent, lightDirection),
                        dot(qrBitangent, lightDirection),
                        qrAlphaTangent,
                        qrAlphaBitangent
                ) * anisotropicVisibility(
                        qrNormalView,
                        dot(qrTangent, viewDirection),
                        dot(qrBitangent, viewDirection),
                        qrAlphaTangent,
                        qrAlphaBitangent
                );
        vec3 qrReflectance = mix(
                vec3(0.26, 0.29, 0.32),
                vec3(0.58, 0.62, 0.66),
                smoothstep(0.28, 0.95, qrMaterialGradient)
        );
        qrReflectance *= mix(0.92, 1.08, qrFinishVariation);
        vec3 qrFresnel = fresnelSchlick(viewHalf, qrReflectance);
        vec3 qrDirect = qrDistribution * qrVisibility * qrFresnel;
        qrDirect *= qrNormalLight / max(
                4.0 * qrNormalLight * qrNormalView,
                0.0001
        );
        vec3 qrReflectionDirection = reflect(-viewDirection, qrNormal);
        vec3 qrColor = studioEnvironment(
                qrReflectionDirection,
                qrRoughness
        ) * fresnelSchlick(qrNormalView, qrReflectance)
                * mix(0.72, 0.42, qrRoughness)
                * ENVIRONMENT_INTENSITY
                + qrDirect * LIGHT_INTENSITY;
        float qrDetailSurfaceDiffuse = wrappedDiffuse(
                qrNormal, detailLightDirection, detailDiffuseWrap
        );
        float qrDetailFlatDiffuse = wrappedDiffuse(
                surfaceNormal, detailLightDirection, detailDiffuseWrap
        );
        float qrDetailDiffuse = max(
                qrDetailSurfaceDiffuse - qrDetailFlatDiffuse,
                0.0
        ) * 14.0;
        vec3 qrDiffuseColor = mix(
                vec3(0.20, 0.22, 0.24),
                vec3(0.70, 0.73, 0.76),
                smoothstep(0.28, 0.95, qrMaterialGradient)
        );
        qrColor += qrDiffuseColor
                * qrDetailDiffuse
                * 0.24
                * DETAIL_LIGHT_STRENGTH;
        qrColor *= QR_REFLECTION_STRENGTH;
        qrColor *= mix(0.82, 1.08, qrMaterialGradient);
        qrColor *= 1.0
                - qrGlyph * 0.52 * QR_ENGRAVING_STRENGTH;
        qrColor *= QR_LIGHTING_STRENGTH;
        color = mix(color, qrColor, qrButton);
    }

#endif

    color = acesToneMap(color * EXPOSURE);
    color = pow(color, vec3(1.0 / 2.2));
    fragmentColor = vec4(color, 1.0);
#endif
}
