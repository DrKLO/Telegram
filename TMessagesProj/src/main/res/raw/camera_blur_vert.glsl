uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;
attribute vec4 aPosition;
attribute vec4 aTextureCoord;
varying vec2 uv;
uniform float crossfade;
uniform mat4 cameraMatrix;
void main() {
    gl_Position = uMVPMatrix * cameraMatrix * aPosition;
    uv = (uSTMatrix * aTextureCoord).xy;
}