#version 300 es

precision highp float;

uniform sampler2D uTexture;

in vec2 vTexCoord;
in float vAlpha;

out vec4 fragColor;

void main(void) {
    vec4 resultColor = texture(uTexture, vTexCoord * vec2(1., -1.));
    fragColor = vec4(resultColor.rgb, resultColor.a * vAlpha);
}