#extension GL_OES_EGL_image_external : require

precision lowp float;

varying vec2 uv;
uniform samplerExternalOES sTexture;
uniform vec2 pixelWH;

void main() {
    float r = 2.;
    vec2 d = 1.0 / (pixelWH / 8.0); //step == 0 ? 1. / (texSz / sz) : 1. / sz * r;
    vec2 st = d / r;
    vec4 col = vec4(0.);
    float count = 0.;
    for (float x = -d.x; x < d.x; x += st.x)
    for (float y = -d.y; y < d.y; y += st.y)
    {
        col += texture2D(sTexture, uv + vec2(x, y));
        count++;
    }
    gl_FragColor = vec4((col / count).rgb, 1.);
}