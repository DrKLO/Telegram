uniform mat4 uMVPMatrix;

attribute vec2 a_TexCoordinate;
attribute vec3 a_Normal;
attribute vec3 vPosition;

varying vec3 vNormal;
varying vec2 vUV;
varying vec3 modelViewVertex;

void main() {
   vUV = a_TexCoordinate;
   vNormal = a_Normal;
   gl_Position = uMVPMatrix * vec4(vPosition, 1.0);
   modelViewVertex = vec3(gl_Position);
}