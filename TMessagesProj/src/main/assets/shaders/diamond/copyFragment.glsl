#version 300 es
precision highp float;
in vec2 uv;uniform sampler2D image;uniform float opacity;uniform float white;out vec4 fragColor;
void main(){vec4 c=texture(image,uv);fragColor=vec4(mix(c.rgb,vec3(c.a),white),c.a)*opacity;}
