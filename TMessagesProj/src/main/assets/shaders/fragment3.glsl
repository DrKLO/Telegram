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
    vec3 vLightPosition2 = vec3(-400, 40, 400);
    vec3 vLightPosition3 = vec3(0, 200, 400);
    vec3 vLightPosition4 = vec3(0, 0, 100);
    vec3 vLightPositionNormal = vec3(100, -200, 400);

    vec3 vNormalW = normalize(vec3(world * vec4(vNormal, 0.0)));
    vec3 vTextureNormal = normalize(texture2D(u_NormalMap, vUV + vec2(-f_xOffset, f_xOffset)).xyz * 2.0 - 1.0);
    vec3 finalNormal = normalize(vNormalW + vTextureNormal);

    vec3 color = texture2D(u_Texture, vUV).xyz;
    vec3 viewDirectionW = normalize(cameraPosition - modelViewVertex);

    float border = 0.0;
    if (modelIndex == 0) {
        border = 1.0;
    } else if (modelIndex == 2) {
        border = texture2D(u_Texture, vUV).a;
    }

    float modelDiffuse = 0.1;
    if (!night && modelIndex != 1) {
        modelDiffuse = 0.01;
    }
    float diffuse = max(dot(vNormalW, viewDirectionW), (1.0 - modelDiffuse));

    vec2 uv = vUV;
    if (modelIndex == 2) {
        uv *= 2.0;
        uv = fract(uv);
        if (vUV.x > .5) {
            uv.x = 1.0 - uv.x;
        }
    }
    float mixValue = clamp(distance(uv.xy, vec2(0.0)), 0.0, 1.0);
    vec4 gradientColorFinal = vec4(mix(gradientColor1, gradientColor2, mixValue), 1.0);

    float modelNormalSpec = normalSpec, modelSpec1 = 0.0, modelSpec2 = 0.05;

    float darken = 1. - length(modelViewVertex - vec3(30., -75., 50.)) / 200.;

    if (border > 0.) {
        modelNormalSpec += border;
        modelSpec1 += border;
        modelSpec2 += border;
    }


    vec3 angleW = normalize(viewDirectionW + vLightPosition2);
    float specComp2 = max(0., dot(finalNormal, angleW));
    specComp2 = pow(specComp2, max(1., 128.)) * modelSpec1;

    angleW = normalize(viewDirectionW + vLightPosition4);
    float specComp3 = max(0., dot(finalNormal, angleW));
    specComp3 = pow(specComp3, max(1., 30.)) * modelSpec2;

    angleW = normalize(viewDirectionW + vLightPositionNormal);
    float normalSpecComp = max(0., dot(finalNormal, angleW));
    normalSpecComp = pow(normalSpecComp, max(1., 128.)) * modelNormalSpec;

    angleW = normalize(viewDirectionW + vLightPosition2);
    float normalSpecComp2 = max(0., dot(finalNormal, angleW));
    normalSpecComp2 = pow(normalSpecComp2, max(1., 128.)) * modelNormalSpec;

    vec4 normalSpecFinal = vec4(normalSpecColor, 0.0) * normalSpecComp2;
    vec4 specFinal = vec4(color, 0.0) * (specComp2 + specComp3);

//    float snap = fract((-gl_FragCoord.x / resolution.x + gl_FragCoord.y / resolution.y) / 20.0 + .2 * time) > .9 ? 1. : 0.;

    vec4 fragColor = gradientColorFinal + specFinal;
    vec4 backgroundColor = texture2D(u_BackgroundTexture, vec2(gradientPosition.x + (gl_FragCoord.x / resolution.x) * gradientPosition.y, gradientPosition.z + (1.0 - (gl_FragCoord.y / resolution.y)) * gradientPosition.w));
    vec4 color4 = mix(backgroundColor, fragColor, diffuse);
    if (night) {
        angleW = normalize(viewDirectionW + vLightPosition2);
        float normalSpecComp = max(0., dot(finalNormal, angleW));
        normalSpecComp = pow(normalSpecComp, max(1., 128.));
        if (normalSpecComp > .2 && modelIndex != 0) {
            color4.rgb += vec3(.5) * max(0., vTextureNormal.x) * normalSpecComp;
        }
        if (modelIndex == 1) {
            color4.rgb *= .9;
        } else {
            color4.rgb += vec3(1.0) * .17;
        }
    } else {
        if (modelIndex == 1) {
            if (darken > .5) {
                color4.rgb *= vec3(0.78039, 0.77254, 0.95294);
            } else {
                color4.rgb *= vec3(0.83921, 0.83529, 0.96862);
            }
        } else {
            if (darken > .5) {
                color4.rgb *= vec3(.945098, .94117, 1.0);
                color4.rgb += vec3(.06) * border;
            }
        }
    }
    gl_FragColor = color4 * f_alpha;
}