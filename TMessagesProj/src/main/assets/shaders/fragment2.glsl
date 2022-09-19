precision highp float;

uniform sampler2D u_Texture;
uniform sampler2D u_NormalMap;
uniform sampler2D u_BackgroundTexture;
uniform float f_xOffset;
uniform float f_alpha;
uniform mat4 world;
uniform vec3 modelViewVertex;

varying vec3 vNormal;
varying vec2 vUV;

vec3 cameraPosition = vec3(0, 0, 100);

vec4 a_Color = vec4(1);
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

void main() {
    vec3 vLightPosition2 = vec3(-400,400,400);
    vec3 vLightPosition3 = vec3(0,200,400);
    vec3 vLightPosition4 = vec3(0,0,100);
    vec3 vLightPositionNormal = vec3(100,-200,400);

    vec3 vNormalW = normalize(vec3(world * vec4(vNormal, 0.0)));
    vec3 vTextureNormal = normalize(texture2D(u_NormalMap, vUV + vec2(-f_xOffset, f_xOffset)).xyz * 2.0 - 1.0);

    vec3 finalNormal = normalize(vNormalW + vTextureNormal);

    vec3 color = texture2D(u_Texture, vUV ).xyz;
    vec3 viewDirectionW = normalize(cameraPosition - modelViewVertex);

    vec3 angleW = normalize(viewDirectionW + vLightPosition2);
    float specComp2 = max(0., dot(vNormalW, angleW));
    specComp2 = pow(specComp2, max(1., 128.)) * spec1;

    angleW = normalize(viewDirectionW + vLightPosition4);
    float specComp3 = max(0., dot(vNormalW, angleW));
    specComp3 = pow(specComp3, max(1., 30.)) * spec2;

    float diffuse = max(dot(vNormalW, viewDirectionW), (1.0 - u_diffuse));

    float mixValue = distance(vUV,vec2(1,0));
    vec4 gradientColorFinal = vec4(mix(gradientColor1,gradientColor2,mixValue), 1.0);

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
    gl_FragColor = mix(backgroundColor, fragColor, diffuse) * f_alpha;
}