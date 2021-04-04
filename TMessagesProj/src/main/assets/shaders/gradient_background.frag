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

vec4 ones = vec4(1.0);

float getWeight(vec2 uv, vec2 point) {
    float distance = max(length(uv - point), 0.01);
    return 1.0 / (distance * distance);
}

void main() {
    vec2 uv = gl_FragCoord.xy / u_resolution;
    vec4 weights = vec4(getWeight(uv, u_point1), getWeight(uv, u_point2), getWeight(uv, u_point3), getWeight(uv, u_point4));
    float wSum = dot(weights, ones);
    vec3 wColors = (weights.x * u_color1 + weights.y * u_color2 + weights.z * u_color3 + weights.w * u_color4) / wSum;
    wColors = pow(wColors, vec3(0.85));
    gl_FragColor = vec4(wColors.rgb, 1.0);
}