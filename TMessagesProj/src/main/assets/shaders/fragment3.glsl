precision highp float;

uniform sampler2D u_Texture;
uniform sampler2D u_NormalMap;
uniform sampler2D u_BackgroundTexture;
uniform float f_xOffset;
uniform float f_alpha;

varying vec3 vNormal;
varying vec2 vUV;
varying vec3 modelViewVertex;

vec3 cameraPosition = vec3(0, 0, 2000);

vec4 a_Color = vec4(1);
uniform float u_diffuse;
uniform float normalSpec;
uniform vec3 gradientColor1;
uniform vec3 gradientColor2;
uniform vec3 normalSpecColor;
uniform vec3 specColor;
uniform vec2 resolution;
uniform vec4 gradientPosition;
uniform int modelIndex;
uniform bool night;
uniform float time;
uniform mat4 world;

void main() {
    vec2 uv = vUV;
    if (modelIndex == 2) {
        uv *= 2.0;
        uv = fract(uv);
    }
    uv.x = 1.0 - uv.x;

    float diagonal = ((uv.x + uv.y) / 2.0 - .15) / .6;
    vec3 baseColor;
    if (modelIndex == 0) {
        baseColor = mix(
            vec3(0.95686, 0.47451, 0.93725),
            vec3(0.46274, 0.49411, 0.9960),
            diagonal
        );
    } else if (modelIndex == 3) {
        baseColor = mix(
            vec3(0.95686, 0.47451, 0.93725),
            vec3(0.46274, 0.49411, 0.9960),
            diagonal
        );
        baseColor = mix(baseColor, vec3(1.0), .3);
    } else if (modelIndex == 1) {
        baseColor = mix(
            vec3(0.67059, 0.25490, 0.80000),
            vec3(0.39608, 0.18824, 0.98039),
            diagonal
        );
    } else {
        baseColor = mix(
            vec3(0.91373, 0.62353, 0.99608),
            vec3(0.67451, 0.58824, 1.00000),
            clamp((uv.y - .2) / .6, 0.0, 1.0)
        );

        baseColor = mix(baseColor, vec3(1.0), .1 + .45 * texture2D(u_Texture, vUV).a);
        if (night) {
            baseColor = mix(baseColor, vec3(.0), .06);
        }
    }

    vec3 pos = modelViewVertex / 100.0 + .5;
    vec3 norm = normalize(vec3(world * vec4(vNormal, 0.0)));

    vec3 flecksLightPos = vec3(.5, .5, .5);
    vec3 flecksLightDir = normalize(flecksLightPos - pos);
    vec3 flecksReflectDir = reflect(-flecksLightDir, norm);
    float flecksSpec = pow(max(dot(normalize(vec3(0.0) - pos), flecksReflectDir), 0.0), 8.0);
    vec3 flecksNormal = normalize(texture2D(u_NormalMap, (uv * 1.3 + vec2(.02, .06) * time) * 2.0).xyz * 2.0 - 1.0);
    float flecks = max(flecksNormal.x, flecksNormal.y) * flecksSpec;
    norm += flecksSpec * flecksNormal;
    norm = normalize(norm);

    vec3 lightPos = vec3(-3., -3., 20.);
    vec3 lightDir = normalize(lightPos - pos);
    float diffuse = max(dot(norm, lightDir), 0.0);

    float spec = 0.0;

    lightPos = vec3(-3., -3., .5);
    spec += 2.0 * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 2.0);

    lightPos = vec3(-3., .5, 30.);
    spec += (modelIndex == 1 ? 1.5 : 0.5) * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 32.0);

//    lightPos = vec3(3., .5, .5);
//    spec += pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 32.0);

    if (modelIndex != 0) {
        spec *= .25;
    }

    vec3 color = baseColor;
    color *= .94 + .22 * diffuse;
    color = mix(color, vec3(1.0), spec);
//    color = mix(color, vec3(1.0), 0.35 * flecks);

    gl_FragColor = vec4(color, 1.0);
}