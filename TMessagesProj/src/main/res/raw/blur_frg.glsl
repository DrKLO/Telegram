precision mediump float;

varying highp vec2 uv;
uniform sampler2D tex;
uniform mat4 matrix;
uniform vec2 texSz;
uniform vec2 sz;
uniform int step;
uniform float flipy;

uniform float hasVideoMatrix;
uniform mat4 videoMatrix;

uniform vec4 gtop;
uniform vec4 gbottom;

vec4 at(vec2 p) {
    if (step != 0) {
        return texture2D(tex, p);
    }
    vec2 uv = (matrix * vec4(clamp(p, 0., 1.), 0., 1.)).xy;
    if (uv.x < 0. || uv.y < 0. || uv.x > 1. || uv.y > 1.) {
        return mix(gtop, gbottom, p.y);
    }
    if (hasVideoMatrix > 0.5) {
        if (flipy > .5) {
            uv.y = 1. - uv.y;
        }
        return texture2D(tex, (videoMatrix * vec4(uv, 0., 1.)).xy);
    } else {
        return texture2D(tex, uv);
    }
}

#define pow2(x) (x * x)
const float pi = 3.14;

void main() {
    float r = 2.;
    vec2 d = step == 0 ? 1. / (texSz / sz) : 1. / sz * r;
    vec2 st = d / r;
    vec4 col = vec4(0.);
    float count = 0.;
    for (float x = -d.x; x < d.x; x += st.x)
    for (float y = -d.y; y < d.y; y += st.y)
    {
        col += at(uv + vec2(x, y));
        count++;
    }
    gl_FragColor = vec4((col / count).rgb, 1.);
}