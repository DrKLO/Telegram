#version 300 es
precision highp float;

uniform sampler2D uCardDetailTexture;
uniform vec4 uDiamondBounds;
uniform float uDiamondAlpha;
flat in vec3 vEngravingXAxis;
flat in vec3 vEngravingYAxis;
flat in vec3 vFaceNormal;
flat in vec3 vLightDirection;
in vec3 vObjectNormal;
in vec3 vViewPosition;
in vec2 vCardPosition;
out vec4 fragmentColor;

void main() {
    vec2 cardUv = vec2(vCardPosition.x * 0.5 + 0.5,
            (0.618561 - vCardPosition.y) / 1.212122);
    vec2 uv = (cardUv - uDiamondBounds.xy) / uDiamondBounds.zw;
    // Sample before discard to preserve derivatives at the silhouette.
    vec4 sampleColor = texture(uCardDetailTexture, uv);
    if (vObjectNormal.x > -0.5 || any(lessThan(uv, vec2(0.0)))
            || any(greaterThan(uv, vec2(1.0))) || sampleColor.a <= 0.0) discard;

    // Procedural tangent-space normal map following the artwork's six main
    // facets. Restore the raised crown so each face catches light independently.
    float girdle = 223.0 / 512.0;
    float row = clamp((uv.y - girdle) / (454.0 / 512.0 - girdle), 0.0, 1.0);
    float left = mix(140.0 / 512.0, 257.5 / 512.0, row);
    float right = mix(362.0 / 512.0, 257.5 / 512.0, row);
    if (uv.y < girdle) {
        float crown = clamp((girdle - uv.y) / (girdle - 72.0 / 512.0), 0.0, 1.0);
        left = mix(left, 197.0 / 512.0, crown);
        right = mix(right, 312.0 / 512.0, crown);
    }
    float slopeX = uv.x < left ? -0.48 : (uv.x > right ? 0.48 : 0.0);
    float slopeY = uv.y < girdle ? -0.30 : 0.24;
    vec3 normal = normalize(vFaceNormal + vEngravingXAxis * slopeX
            - vEngravingYAxis * slopeY);
    vec3 viewDirection = normalize(-vViewPosition);
    vec3 halfway = normalize(viewDirection + vLightDirection);
    float diffuse = max(dot(normal, vLightDirection), 0.0);
    float shine = pow(max(dot(normal, halfway), 0.0), 52.0);
    float broadShine = pow(max(dot(normal, halfway), 0.0), 10.0);
    float fresnel = pow(1.0 - max(dot(normal, viewDirection), 0.0), 4.0);

    // Preserve the supplied texture even outside the card's moving light band.
    // The shared light adds facet glints; it never dims the entire icon in unison.
    vec3 artwork = sampleColor.rgb / max(sampleColor.a, 0.00392157);
    vec3 color = artwork * (0.94 + 0.06 * diffuse);
    color += vec3(0.90, 0.96, 1.0)
            * (0.34 * shine + 0.055 * broadShine + 0.10 * fresnel);
    // Fade premultiplied RGB and coverage together, preserving the card beneath.
    float alpha = sampleColor.a * uDiamondAlpha;
    fragmentColor = vec4(min(color, vec3(1.0)) * alpha, alpha);
}
