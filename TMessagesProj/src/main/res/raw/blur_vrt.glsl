attribute vec4 p;
attribute vec2 inputuv;
varying vec2 uv;

void main() {
    gl_Position = p;
    uv = inputuv;
}