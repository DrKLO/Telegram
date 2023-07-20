#extension GL_OES_EGL_image_external : require

precision lowp float;

varying vec2 uv;
uniform samplerExternalOES sTexture;
uniform vec2 pixelWH;
uniform float roundRadius;
uniform float scale;
uniform float alpha;
uniform float crossfade;
uniform mat4 cameraMatrix;
uniform mat4 oppositeCameraMatrix;
uniform float shapeFrom, shapeTo, shapeT;
uniform float dual;
uniform float blur;

float modI(float a,float b) {
  return floor(a-floor((a+0.5)/b)*b+0.5);
}
float box(vec2 position, vec2 halfSize, float cornerRadius) {
  position = abs(position) - halfSize + cornerRadius;
  return length(max(position, 0.0)) + min(max(position.x, position.y), 0.0) - cornerRadius;
}
float scene() {
  vec2 p = (uv - vec2(.5)) * vec2(1., pixelWH.x / pixelWH.y);
  vec2 r = .5 * vec2(1., pixelWH.x / pixelWH.y) * scale;
  float R = min(r.x, r.y), rr = roundRadius / pixelWH.y;
  float a = modI(shapeFrom, 3.), b = modI(shapeTo, 3.);
  return box(
    p, 
    mix(abs(a-2.)<.1 ? r : vec2(R), abs(b-2.)<.1 ? r : vec2(R), shapeT),
    mix(abs(a)<.1 ? R : rr, abs(b)<.1 ? R : rr, shapeT)
  ) * pixelWH.x;
}
vec4 BilinearTextureSample(vec2 P) {
  vec2 onePixel = 1.0 / pixelWH, twoPixels = 2. * onePixel;
  vec2 pixel = P * pixelWH + .5;
  vec2 frac = fract(pixel);
  pixel = (floor(pixel) / pixelWH) - onePixel / 2.;
  return mix(
    mix(texture2D(sTexture, pixel + vec2(0., 0.) * onePixel), texture2D(sTexture, pixel + vec2(1., 0.) * onePixel), frac.x),
    mix(texture2D(sTexture, pixel + vec2(0., 1.) * onePixel), texture2D(sTexture, pixel + vec2(1., 1.) * onePixel), frac.x),
    frac.y
  );
}
vec4 makeblur() {
  vec2 S = 4. * vec2(1., pixelWH.x / pixelWH.y);
  vec2 st = fract(uv * S);
  st=st*st*(3.0-2.0*st);
  vec2 u = floor(uv * S) / S;
  return mix(
    mix(texture2D(sTexture, u + vec2(0., 0.) / S), texture2D(sTexture, u + vec2(1., 0.) / S), st.x),
    mix(texture2D(sTexture, u + vec2(0., 1.) / S), texture2D(sTexture, u + vec2(1., 1.) / S), st.x),
    st.y
  );
}
vec4 program() {
  if (scale <= 0.)
    return vec4(0.);
  float dalpha = clamp(1. - scene(), 0., 2.) / 2. * alpha;
  if (dalpha <= 0.)
    return vec4(0.);
  
  if (blur >= 1.)
    return makeblur();
  else if (blur <= 0.)
    return (dual > .5 ? BilinearTextureSample(uv) : texture2D(sTexture, uv)) * vec4(1., 1., 1., dalpha);
  else
    return mix((dual > .5 ? BilinearTextureSample(uv) : texture2D(sTexture, uv)), makeblur(), blur) * vec4(1., 1., 1., dalpha);
}
void main() {
  gl_FragColor = program();
}