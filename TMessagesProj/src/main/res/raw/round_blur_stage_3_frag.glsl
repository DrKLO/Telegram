precision mediump float;

varying vec2 vTextureCoord;
uniform sampler2D sTexture;

void main() {
   gl_FragColor = vec4(1.0, 1.0, 1.0, texture2D(sTexture, vTextureCoord).a);
}