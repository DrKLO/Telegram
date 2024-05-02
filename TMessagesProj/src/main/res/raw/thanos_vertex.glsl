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
uniform float scale;
uniform float uvOffset;

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
    velocity = vec2(cos(direction), sin(direction)) * (0.1 + rand(5. * uv) * (0.2 - 0.1)) * 260.0 * scale * dp;
    particleTime = (0.7 + rand(uv) * (1.5 - 0.7)) / 1.15;
  }

  float effectFraction =   max(0.0, min(0.35, time)) / 0.35;
  float particleFraction = max(0.0, min(0.2, .1 + time - uv.x * uvOffset)) / 0.2;
  position += velocity * deltaTime * particleFraction;
  velocity += vec2(19.0 * (velocity.x > 0.0 ? 1.0 : -1.0) * (1.0 - effectFraction), -65.0) * deltaTime * dp * particleFraction;
  particleTime = max(0.0, particleTime - 1.2 * deltaTime * particleFraction);

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