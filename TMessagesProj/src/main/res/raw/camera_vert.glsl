uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;
attribute vec4 aPosition;
attribute vec4 aTextureCoord;
varying vec2 uv;
varying vec2 auv;
uniform float crossfade;
uniform mat4 cameraMatrix;
uniform mat4 oppositeCameraMatrix;
mat4 mix(mat4 a, mat4 b, float t) {
  return a * (1. - t) + b * t;
}
void main() {
  mat4 matrix = mix(cameraMatrix, oppositeCameraMatrix, crossfade);
  gl_Position = uMVPMatrix * matrix * aPosition;
  uv = (uSTMatrix * aTextureCoord).xy;
  auv = (uMVPMatrix * vec4((uSTMatrix * aTextureCoord).xy - .5, 0.0, 1.0) + .5).xy;
}