#version 300 es

precision mediump float;

in vec2 vTexCoord;
in float vDisplayProgress;

uniform sampler2D uTexture;

out vec4 fragColor;

void main() {
    vec4 color = texture(uTexture, vTexCoord);
    float a;
    if(vDisplayProgress < vTexCoord.x) {
        a = 1.;
    } else {
        a = 0.;
    }
    fragColor = vec4(color.rgb, color.a * a);
}