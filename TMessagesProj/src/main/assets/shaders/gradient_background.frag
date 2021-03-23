precision mediump float;

uniform vec2 u_resolution;

uniform vec3 u_color1;
uniform vec3 u_color2;
uniform vec3 u_color3;
uniform vec3 u_color4;

uniform vec2 u_point1;
uniform vec2 u_point2;
uniform vec2 u_point3;
uniform vec2 u_point4;

float getWeight(vec2 uv, vec2 point) {
    float distance = length(uv - point);
    distance = max(distance, 0.01);
    return 1.0 / pow(distance, 2.0);
}

// TODO agolokoz: add turbulent displacement
void main() {
    vec2 uv = gl_FragCoord.xy / u_resolution;

    float w1 = getWeight(uv, u_point1);
    float w2 = getWeight(uv, u_point2);
    float w3 = getWeight(uv, u_point3);
    float w4 = getWeight(uv, u_point4);
    float wSum = w1 + w2 + w3 + w4;

    vec3 wColors = w1 * u_color1 + w2 * u_color2 + w3 * u_color3 + w4 * u_color4;
    wColors /= wSum;
    wColors = pow(wColors, vec3(0.85));

    gl_FragColor = vec4(wColors.rgb, 1.0);
}