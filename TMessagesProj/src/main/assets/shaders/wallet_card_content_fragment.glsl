#version 300 es
precision highp float;

uniform sampler2D uCardDetailTexture;
in vec3 vObjectNormal;
in vec2 vCardPosition;
out vec4 fragmentColor;

void main() {
    // Match FaceProjection's mesh bounds, including its asymmetric Y origin.
    vec2 uv = vec2(vCardPosition.x * 0.5 + 0.5,
            (0.618561 - vCardPosition.y) / 1.212122);
    vec4 color = texture(uCardDetailTexture, uv);
    if (vObjectNormal.x > -0.5) discard;
    // Android's canvas bitmap is already premultiplied.
    fragmentColor = color;
}
