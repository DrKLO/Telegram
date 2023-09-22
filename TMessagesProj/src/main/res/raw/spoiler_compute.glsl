#version 310 es

layout(local_size_x = 256) in; // This can be adjusted

struct Particle {
  vec2 position;
  vec2 velocity;
  float time;
  float duration;
};

layout(std430, binding = 0) buffer ParticleBuffer {
  Particle particles[];
};

uniform float deltaTime;
uniform vec2 size;

void main() {
  uint id = gl_GlobalInvocationID.x;
  
  if (id >= uint(particles.length()))
    return;

  Particle p = particles[id];

  p.time += deltaTime * p.duration;
  if (p.time >= 1.0) {
    p.time = 0.0;
  }

  p.velocity += vec2(.5, .5);
  p.velocity *= .99;
  
  p.position += p.velocity;
  p.position = fract(p.position / size) * size;

  particles[id] = p;
}
