precision mediump float;

varying vec2 vTextureCoord;
uniform sampler2D sTexture;

uniform vec2 texOffset; // 1.0 / resolution

const float weight0 = 0.227027 / 2.0;
const float weight1 = 0.1945946 / 2.0;
const float weight2 = 0.1216216 / 2.0;
const float weight3 = 0.054054 / 2.0;
const float weight4 = 0.016216 / 2.0;

void main() {
   vec3 result = texture2D(sTexture, vTextureCoord).rgb * weight0;

   result += (
      texture2D(sTexture, vTextureCoord + texOffset * 1.0).rgb +
      texture2D(sTexture, vTextureCoord - texOffset * 1.0).rgb
   ) * weight1;

   result += (
      texture2D(sTexture, vTextureCoord + texOffset * 2.0).rgb +
      texture2D(sTexture, vTextureCoord - texOffset * 2.0).rgb
   ) * weight2;

   result += (
      texture2D(sTexture, vTextureCoord + texOffset * 3.0).rgb +
      texture2D(sTexture, vTextureCoord - texOffset * 3.0).rgb
   ) * weight3;

   result += (
      texture2D(sTexture, vTextureCoord + texOffset * 4.0).rgb +
      texture2D(sTexture, vTextureCoord - texOffset * 4.0).rgb
   ) * weight4;

   gl_FragColor = vec4(result, 1.0);
}