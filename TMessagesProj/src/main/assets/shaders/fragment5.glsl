precision highp float;

varying vec3 vNormal;
varying vec2 vUV;
varying vec3 modelViewVertex;
varying float depth;

uniform int modelIndex;
uniform vec2 resolution;
uniform int type;
uniform bool night;
uniform float time;
uniform mat4 world;
uniform bool behind;

vec3 gradient(
    float x1, vec3 y1,
    float x2, vec3 y2,
    float x3, vec3 y3,

    float t
) {
    if (t < x1) return y1;
    if (t < x2) return mix(y1, y2, (t - x1) / (x2 - x1));
    if (t < x3) return mix(y2, y3, (t - x2) / (x3 - x2));
    return y3;
}

void main() {
    if (modelIndex == 0) {
        if (behind) {
            gl_FragColor = vec4(night ? RGB#2457A9 : RGB#1E69E8, 1.0);
        } else {
            vec2 pos = 3.0 * gl_FragCoord.xy / resolution;
            float t = time;
            t = fract(t / 9.0) * 9.0;
            t *= 1.8;
            if (t > 1.1) {
                discard;
            }
            float x = fract((pos.x - pos.y + 0.05 * depth) * 0.2 - t - 0.32);
            float a = x < 0.15 || x < 0.25 && x > 0.2 ? 0.4 : 0.0;
            gl_FragColor = vec4(vec3(1.0), a);
        }
    } else if (modelIndex == 1) {
        vec3 pos = modelViewVertex / 10.0 + .5;
        vec3 lightPos = vec3(-3., 20., 20.);
        vec3 lightDir = normalize(lightPos - pos);

        vec3 norm = normalize(vec3(world * vec4(vNormal, 0.0)));
        if (behind) {
            float diffuse = max(dot(norm, lightDir), 0.0);

            gl_FragColor = vec4(
                mix(
                    vec3(0.35, 0.59, 0.94),
                    vec3(0.52, 0.70, 0.97),
                    clamp(diffuse, 0.0, 1.0)
                )
            , 1.0);
        } else {
            float spec2 = 0.0;
            lightPos = vec3(0., -100., 50.0);
            spec2 += 0.5 * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 2.0);

            lightPos = vec3(0., 0., 0.0);
            spec2 += 0.4 * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 2.0);

            if (!night) {
                spec2 *= 0.5;
            }

            gl_FragColor = vec4(vec3(1.0), spec2);
        }
    } else if (modelIndex == 2) {
        vec3 pos = modelViewVertex / 10.0 + .5;
        vec3 lightPos = vec3(-8., 12., 20.);
        vec3 lightDir = normalize(lightPos - pos);

        vec3 norm = normalize(vec3(world * vec4(vNormal, 0.0)));
        float diffuse = max(dot(norm, lightDir), 0.0);

        float spec = 0.0;
        lightPos = vec3(20., -150., 50.0);
        spec += .75 * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 2.0);

        vec3 color = gradient(
            0.0, RGB#58A8F6,
            .58, RGB#8ED4FB,
            1.0, RGB#CEE8FD,

            diffuse
        );

        color += RGB#C5E5FC * spec;

        gl_FragColor = vec4(color, 1.5);
    }
}