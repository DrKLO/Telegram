#version 300 es

precision highp float;

layout(location = 0) in vec2 inUV;
layout(location = 1) in vec2 inPosition;
layout(location = 2) in vec2 inVelocity;
layout(location = 3) in float inTime;

out vec2 outUV;
out vec2 outPosition;
out vec2 outVelocity;
out float outTime;

out vec2 uvcenter;
out vec2 uvsize;
out float alpha;

uniform mat3 matrix;
uniform vec2 rectSize;
// uniform vec2 rectPos;

uniform float reset;
uniform float time;
uniform float deltaTime;
uniform float particlesCount;
uniform vec2 size;
uniform vec3 gridSize;
uniform float seed;
uniform float longevity;
uniform float dp;
uniform vec2 offset;

#define noiseScale   12.0
#define noiseSpeed    0.6
#define noiseMovement 3.0
#define snapDuration  0.6
#define velocityMult  0.99
#define forceMult     18.31
#define dampingMult   0.95

float rand(vec2 n) { 
	return fract(sin(dot(n,vec2(12.9898,4.1414-seed*.42)))*43758.5453);
}
float mod289(float x){return x-floor(x*(1./(289.+seed)))*(289.+seed);}
vec4 mod289(vec4 x){return x-floor(x*(1./(289.+seed)))*(289.0+seed);}
vec4 perm(vec4 x){return mod289(((x*34.)+1.)*x);}
float noise(vec3 p){
  
  vec3 a = floor(p);
  vec3 d = p - a;
  d = d * d * (3. - 2. * d);

  vec4 b = a.xxyy + vec4(0., 1., 0., 1.);
  vec4 k1 = perm(b.xyxy);
  vec4 k2 = perm(k1.xyxy + b.zzww);

  vec4 c = k2 + a.zzzz;
  vec4 k3 = perm(c);
  vec4 k4 = perm(c + 1.0);

  vec4 o3 = fract(k4 / 41.0) * d.z + fract(k3 / 41.0) * (1.0 - d.z);
  vec2 o4 = o3.yw * d.x + o3.xz * (1.0 - d.x);

  return o4.y * d.y + o4.x * (1.0 - d.y);
}
vec3 grad(vec3 p) {
  const vec2 e = vec2(.1, .0);
  return vec3(
    noise(p + e.xyy) - noise(p - e.xyy),
    noise(p + e.yxy) - noise(p - e.yxy),
    noise(p + e.yyx) - noise(p - e.yyx)
  ) / (2.0 * e.x);
}
vec3 curlNoise(vec3 p) {
  p.xy /= size;
  p.x *= (size.x / size.y);
  p.xy = fract(p.xy);
  p.xy *= noiseScale;

  const vec2 e = vec2(.01, .0);
  return grad(p).yzx - vec3(
    grad(p + e.yxy).z,
    grad(p + e.yyx).x,
    grad(p + e.xyy).y
  );
}
float modI(float a,float b) {
  return floor(a-floor((a+0.5)/b)*b+0.5);
}

float particleEaseInWindowFunction(float t) {
    return t;
}

float particleEaseInValueAt(float fraction, float t) {
    float windowSize = 0.8;

    float effectiveT = t;
    float windowStartOffset = -windowSize;
    float windowEndOffset = 1.0;

    float windowPosition = (1.0 - fraction) * windowStartOffset + fraction * windowEndOffset;
    float windowT = max(0.0, min(windowSize, effectiveT - windowPosition)) / windowSize;
    float localT = 1.0 - particleEaseInWindowFunction(windowT);

    return localT;
}

void main() {
  vec2 uv = inUV;
  vec2 position = inPosition;
  vec2 velocity = inVelocity;
  float particleTime = inTime;

  float id = float(gl_VertexID);
  if (reset > 0.) {
    uv = vec2(
        mod(id, gridSize.x),
        floor(id / gridSize.x)
    ) / gridSize.xy;
    position = (matrix * vec3(uv + .5 / gridSize.xy, 1.0)).xy;
    float direction = rand(3. * uv) * (3.14159265 * 2.0);
    velocity = vec2(cos(direction), sin(direction)) * (0.1 + rand(5. * uv) * (0.2 - 0.1)) * 210.0 * dp;
    particleTime = (0.7 + rand(uv) * (1.5 - 0.7)) / 1.15;
  }

  float effectFraction =   max(0.0, min(0.35, time)) / 0.35;
  float particleFraction = max(0.0, min(0.2, .1 + time - uv.x * 0.6)) / 0.2;
  position += velocity * deltaTime * particleFraction;
  velocity += vec2(12.0, -60.0) * deltaTime * dp * particleFraction;
  particleTime = max(0.0, particleTime - 1.2 * deltaTime * effectFraction);

  outUV = uv;
  outPosition = position;
  outVelocity = velocity;
  outTime = particleTime;

  alpha = max(0.0, min(0.55, particleTime) / 0.55);
  gl_PointSize = (gridSize.z + 1.);
  position.y = size.y - position.y;
  gl_Position = vec4((position + offset) / size * 2.0 - vec2(1.0), 0.0, 1.0);
  uvcenter = uv;
  uvsize = (vec2(gridSize.z + 1.) / rectSize.xy);
}