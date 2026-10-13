#version 300 es
// Both material passes must produce identical depth and sample coverage.
invariant gl_Position;

uniform mat4 uMVPMatrix;
uniform mat4 uModelViewMatrix;
uniform vec3 uMainLightDirection;
uniform float uCardGradientRotation;

flat out vec3 vEngravingXAxis;
flat out vec3 vEngravingYAxis;
flat out vec3 vLightDirection;
flat out vec3 vRadialReferenceHalfway;
flat out vec2 vRadialHighlightAxis;
flat out vec2 vGradientAxis;
#ifndef WALLET_EDGE_PASS
flat out vec3 vFaceNormal;
flat out vec3 vRadialShineHalfway;
flat out float vRadialLightResponse;
#endif


in vec3 aPosition;
in vec3 aNormal;

out vec3 vObjectPosition;
out vec3 vObjectNormal;
out vec3 vViewPosition;
out vec2 vCardPosition;

#ifdef WALLET_FLECKS_PASS
in vec3 aFlecksRegion;
in vec2 aFlecksCorner;
uniform vec2 uFlecksPadding;
flat out vec3 vFlecksRegion;
#endif

void main() {
    // These values depend only on the draw's pose/light, not on the fragment.
    // Flat varyings keep them constant across all triangles of each material.
    vEngravingXAxis = normalize(mat3(uModelViewMatrix) * vec3(0.0, 0.0, 1.0));
    vEngravingYAxis = normalize(mat3(uModelViewMatrix) * vec3(0.0, 1.0, 0.0));
    vLightDirection = normalize(uMainLightDirection);
    vRadialReferenceHalfway = normalize(vec3(0.0, 0.0, 1.0) + vLightDirection);
    const float shineSlope = 0.103713069;
    float highlightAngle = dot(vec2(dot(vRadialReferenceHalfway, vEngravingXAxis),
            dot(vRadialReferenceHalfway, vEngravingYAxis)) - vec2(shineSlope, 0.0), vec2(2.0, 4.0));
    vRadialHighlightAxis = vec2(cos(highlightAngle), sin(highlightAngle));
    float gradientAngle = (-20.99 - uCardGradientRotation) * 3.14159265359 / 180.0;
    vGradientAxis = vec2(cos(gradientAngle), sin(gradientAngle));
#ifndef WALLET_EDGE_PASS
    vFaceNormal = normalize(mat3(uModelViewMatrix) * vec3(-1.0, 0.0, 0.0));
    float restNormalHalf = sqrt(1.0 - shineSlope * shineSlope);
    vRadialShineHalfway = normalize(vFaceNormal * restNormalHalf
            + (vEngravingXAxis * vRadialHighlightAxis.x
                    + vEngravingYAxis * vRadialHighlightAxis.y) * shineSlope);
    float lightResponse = clamp(dot(vFaceNormal, vLightDirection), 0.0, 1.0)
            * pow(clamp(dot(vFaceNormal, vRadialReferenceHalfway), 0.0, 1.0), 8.0);
    float restLightResponse = (1.0 - 2.0 * shineSlope * shineSlope) * pow(restNormalHalf, 8.0);
    vRadialLightResponse = clamp(lightResponse / restLightResponse, 0.0, 1.0);
#endif
    vec3 position = aPosition;
#ifdef WALLET_FLECKS_PASS
    position.z += aFlecksCorner.x * uFlecksPadding.x * (2.0 / 512.0);
    position.y -= aFlecksCorner.y * uFlecksPadding.y * (1.219512 / 312.5);
    vFlecksRegion = aFlecksRegion;
#endif
    vec4 viewPosition = uModelViewMatrix * vec4(position, 1.0);
    vObjectPosition = position;
    vObjectNormal = aNormal;
    vViewPosition = viewPosition.xyz;
    vCardPosition = vec2(position.z, position.y);
    gl_Position = uMVPMatrix * vec4(position, 1.0);
}
