precision mediump float;
varying vec2 vTextureCoord;

uniform sampler2D sTexture; // normal texture
uniform sampler2D bTexture; // blur texture
uniform vec2 center; // width, height / 2.0

void main() {
   vec3 textColor = texture2D(sTexture, vTextureCoord).rgb;
   vec3 blurColor = texture2D(bTexture, vTextureCoord).rgb;

   float radius = center.x;
   float d = length(center - gl_FragCoord.xy) - radius;
   float t = clamp(d, 0.0, 1.0);

   vec3 color = mix(textColor, blurColor, t);
   gl_FragColor = vec4(color, 1.0);
}