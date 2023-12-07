#version 300 es

in vec2 inPosition;
in vec2 inTextCoord;

out vec2 vTexCoord;
out float vDisplayProgress;

uniform float uProgress;

void main() {
    gl_Position = vec4(inPosition, vec2(1., 1.));
    vec2 coord = (inPosition.xy + vec2(1.0)) * 0.5;
    vTexCoord = inTextCoord;

    float displayProgress = min(1., uProgress * 2.);
    vDisplayProgress = min(1., uProgress * 2.);
}