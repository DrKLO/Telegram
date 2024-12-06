precision lowp float;

varying vec2 uv;
uniform sampler2D sTexture;
uniform vec2 pixelWH;

const float kernel = 10.0;
const float weight = 1.0;

void main() {

    vec3 sum = vec3(0);
    float pixelSize = 1.0 / pixelWH.x;

    // Horizontal Blur
    vec3 accumulation = vec3(0);
    vec3 weightsum = vec3(0);
    for (float i = -kernel; i <= kernel; i++) {
        accumulation += texture2D(sTexture, uv + vec2(i * pixelSize, 0.0)).rgb * weight;
        weightsum += weight;
    }

    sum = accumulation / weightsum;

    gl_FragColor = vec4(sum, 1.0);
}