#version 300 es

precision highp float;

in vec2 uvcenter;
in vec2 uvsize;
in float alpha;

out vec4 fragColor;

uniform sampler2D tex;

void main() {
  if (alpha <= 0.0) {
    discard;
  }
  fragColor = texture(tex, uvcenter + gl_PointCoord * uvsize).rgba * alpha;
}