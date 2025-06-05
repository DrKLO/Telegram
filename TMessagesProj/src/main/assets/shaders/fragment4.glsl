precision highp float;

uniform sampler2D u_Texture;
uniform sampler2D u_NormalMap;
uniform sampler2D u_BackgroundTexture;
uniform float f_xOffset;
uniform float f_alpha;
uniform mat4 world;
uniform float golden;
uniform float white;

varying vec3 vNormal;
varying vec2 vUV;
varying vec3 modelViewVertex;

uniform float spec1;
uniform float spec2;
uniform float u_diffuse;
uniform float normalSpec;
uniform vec3 gradientColor1;
uniform vec3 gradientColor2;
uniform vec3 normalSpecColor;
uniform vec3 specColor;
uniform vec2 resolution;
uniform vec4 gradientPosition;
uniform int modelIndex;

vec4 premium_star() {
    vec3 cameraPosition = vec3(0, 0, 100);
    vec3 vLightPosition2 = vec3(-400,400,400);
    vec3 vLightPosition3 = vec3(0,200,400);
    vec3 vLightPosition4 = vec3(0,0,100);
    vec3 vLightPositionNormal = vec3(100,-200,400);

    vec3 vNormalW = normalize(vec3(world * vec4(vNormal, 0.0)));
    vec3 vTextureNormal = normalize(texture2D(u_NormalMap, (vUV + vec2(-f_xOffset, f_xOffset)) * 2.0).xyz * 2.0 - 1.0);

    vec3 finalNormal = normalize(vNormalW + vTextureNormal);

    vec3 color = texture2D(u_Texture, vUV).xyz;
    vec3 viewDirectionW = normalize(cameraPosition);

    vec3 angleW = normalize(viewDirectionW + vLightPosition2);
    float specComp2 = max(0., dot(vNormalW, angleW));
    specComp2 = pow(specComp2, max(1., 128.)) * spec1;

    angleW = normalize(viewDirectionW + vLightPosition4);
    float specComp3 = max(0., dot(vNormalW, angleW));
    specComp3 = pow(specComp3, max(1., 30.)) * spec2;

    float diffuse = max(dot(vNormalW, viewDirectionW), (1.0 - u_diffuse));

    float mixValue = distance(vUV,vec2(1,0));
    vec4 gradientColorFinal = vec4(mix(gradientColor1, gradientColor2, mixValue), 1.0);

    angleW = normalize(viewDirectionW + vLightPositionNormal);
    float normalSpecComp = max(0., dot(finalNormal, angleW));
    normalSpecComp = pow(normalSpecComp, max(1., 128.)) * normalSpec;

    angleW = normalize(viewDirectionW + vLightPosition2);
    float normalSpecComp2 = max(0., dot(finalNormal, angleW));
    normalSpecComp2 = pow(normalSpecComp2, max(1., 128.)) * normalSpec;

    vec4 normalSpecFinal = vec4(normalSpecColor, 0.0) * (normalSpecComp + normalSpecComp2);
    vec4 specFinal = vec4(color, 0.0) * (specComp2 + specComp3);

    vec4 fragColor = gradientColorFinal + specFinal + normalSpecFinal;
    vec4 backgroundColor = texture2D(u_BackgroundTexture, vec2(gradientPosition.x + (gl_FragCoord.x / resolution.x) * gradientPosition.y, gradientPosition.z + (1.0 - (gl_FragCoord.y / resolution.y)) * gradientPosition.w));
    return mix(backgroundColor, fragColor, diffuse) * f_alpha;
}

vec4 golden_star() {

    vec3 pos = modelViewVertex / 100.0 + .5;
    float specTexture = texture2D(u_Texture, vUV).y * clamp(vNormal.z, 0.0, 1.0);

    float gradientMix = distance(pos.xy, vec2(1, 1));
    gradientMix -= .05 * specTexture;
    gradientMix = clamp(gradientMix, 0.0, 1.0);
    vec4 color = vec4(mix(gradientColor1, gradientColor2, gradientMix), 1.0);

    vec3 norm = normalize(vec3(world * vec4(vNormal, 0.0)));

    vec3 flecksNormal = normalize(1.0 - texture2D(u_NormalMap, (vUV + .7 * vec2(-f_xOffset, f_xOffset)) * 2.0).xyz);
    float flecks = clamp(flecksNormal.x, 0.0, 1.0);

    vec3 lightPos = vec3(-3., -3., 20.);
    vec3 lightDir = normalize(lightPos - pos);
    float diffuse = max(dot(norm, lightDir), 0.0);

    float spec = 0.0;

    lightPos = vec3(-1., .7, .2);
    spec += specTexture * clamp(2.0 * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 2.0), 0.0, 1.0) / 6.0;
    lightPos = vec3(8., .7, .5);
    spec += specTexture * clamp(2.0 * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 2.0), 0.0, 1.0) / 6.0;

    float notSpecTexture = mix(1.0, 0.3, specTexture);
    lightPos = vec3(-3., -3., .5);
    spec += clamp(2.0 * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 2.0), 0.0, 1.0) / 4.0;

    lightPos = vec3(4., 3., 2.5);
    spec += clamp(1.5 * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 2.0), 0.0, 1.0) / 6.0;

    lightPos = vec3(-33., .5, 30.);
    spec += clamp(2.0 * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 2.0), 0.0, 1.0) / 12.0;

    spec = clamp(spec, 0.0, 0.7);

    lightPos = vec3(10., .5, 3.3);
    spec += mix(0.8, 1.0, specTexture) * clamp(2.0 * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 2.0), 0.0, 1.0) / 8.0;

    lightPos = vec3(-10., .5, 3.7);
    spec += mix(0.8, 1.0, specTexture) * clamp(2.0 * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 2.0), 0.0, 1.0) / 8.0;

    lightPos = vec3(.5, 12., 1.5);
    spec += mix(0.8, 1.0, specTexture) * clamp(2.0 * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 2.0), 0.0, 1.0) / 8.0;

    spec = clamp(spec, 0.0, 0.9);

    color = mix(vec4(vec3(0.0), 1.0), color, .8 + .3 * diffuse);
    vec4 specColor = vec4(mix(1.8, 2.0, specTexture) * gradientColor1, 1.0);
    color = mix(color, specColor, spec);

    float flecksSpec = 0.0;
    lightPos = vec3(1.2, -.2, .5);
    flecksSpec += clamp(2.0 * pow(max(dot(normalize(vec3(0.0) - pos), reflect(-normalize(lightPos - pos), norm)), 0.0), 2.0), 0.0, 1.0);

    color = mix(color, specColor,
        clamp(flecksSpec * abs(vNormal.z) * (flecksNormal.z), 0.2, 0.3) - .2
    );

    return mix(color * f_alpha, vec4(1.0), white);
}

void main() {
//    if (golden <= 0.0)
//        gl_FragColor = premium_star();
//    else if (golden >= 0.0)
//        gl_FragColor = golden_star();
//    else
        gl_FragColor = mix(premium_star(), golden_star(), golden);
}