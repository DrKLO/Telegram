uniform mat4 uMVPMatrix;

attribute vec2 a_TexCoordinate;
attribute vec3 a_Normal;
attribute vec4 vPosition;

varying vec3 vNormal;
varying vec2 vUV;
varying vec3 modelViewVertex;

void main() {
   modelViewVertex = vec3(uMVPMatrix * vPosition);
   vUV = a_TexCoordinate;
   vNormal = a_Normal;
   gl_Position = uMVPMatrix * vPosition;
}