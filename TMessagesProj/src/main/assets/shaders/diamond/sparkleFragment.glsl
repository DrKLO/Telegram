#version 300 es
precision highp float;
precision highp int;
#define saturate(x) clamp(x,0.0,1.0)
struct Vertex { vec4 position; vec4 normal; vec4 surface; };
struct Uniforms {
    mat4 model;
    mat4 projection;
    mat4 inverseModel;
    vec4 parameters; // time, refraction, brightness, sparkles
    vec4 viewport;   // width, height, optical plane count, unused
    vec4 sparkleShape; // main layer scale, contour morph, core scale, star glow scale
    vec4 sparkleHalo;  // circular glow scale, main face rotation, horizontal correction, visibility
    vec4 crownGradient;
    vec4 pavilionGradient;
    vec4 lightSweep; // diagonal position, environment phase, transmission, reserved
    vec4 facetProjection; // reference pitch cosine/sine, source units, projected top
    vec4 crownSweep;
    vec4 rightCrownSweep;
    vec4 leftCrownSweep;
    vec4 pavilionSweep;
    vec4 rightPavilionSweep;
    vec4 leftPavilionSweep;
    vec4 appearance; // palette identifier, reserved
    vec4 referenceCrownFlash;
    vec4 referencePavilionFlash;
};
struct Raster {
    vec4 position ;
    vec3 localPosition;
    vec3 normal;
    vec3 worldPosition;
    vec3 facetWeights; // table, crown, pavilion; interpolated across rounded edges
};
struct SparkleVertex { vec4 contours; vec4 material; };
struct SparkleRaster {
    vec4 position ;
    vec2 sourcePoint;
    float strength ;
    uint layer ;
};
struct SparkleAnchor { vec4 position; vec4 normal; };
uniform Uniforms u;
uniform vec4 planes[17];
uniform SparkleAnchor anchors[8];
in vec2 v_sourcePoint;
flat in float v_strength;
flat in uint v_layer;
float radialOpacity(float radius, float first, float middle) {
    if (radius <= first) { return 1.0; }
    if (radius <= middle) { return mix(1.0, 0.5, (radius-first)/(middle-first)); }
    return 0.5 * saturate((1.0-radius)/(1.0-middle));
}

out vec4 fragColor;

void main(){
SparkleRaster inputData;
inputData.sourcePoint=v_sourcePoint;
inputData.strength=v_strength;
inputData.layer=v_layer;

    float alpha = 1.0;
    vec3 color = vec3(1.0);
    if (inputData.layer == 0u) {
        float r = length(inputData.sourcePoint - vec2(-0.5, 0.2)) / 108.5;
        alpha = radialOpacity(r, 0.312, 0.624) * 0.60;
    } else if (inputData.layer == 1u) {
        float r = length(inputData.sourcePoint - vec2(-0.5, 0.9)) / 113.1;
        alpha = radialOpacity(r, 0.297, 0.503) * 0.60;
        color = vec3(0.82, 1.0, 0.902);
    } else if (inputData.layer == 2u) {
        alpha = 0.96;
    } else if (inputData.layer == 3u) {
        float r = saturate(length(inputData.sourcePoint) / length(vec2(176.0, -170.0)));
        alpha = 1.0 - r;
        color = mix(vec3(0.694, 0.969, 1.0), vec3(0.663, 0.957, 1.0), r);
    } else if (inputData.layer == 5u) {
        alpha = radialOpacity(length(inputData.sourcePoint) / length(vec2(228.9, 3.9)), 0.237, 0.623);
    }
    if (uint(u.appearance.x) == 1u) {
        color = inputData.layer == 0u ? vec3(0.635,0.749,1.0) : vec3(1.0);
        // The white source retains only the circular halo, without the extra
        // star-shaped glow used by the blue material.
        if (inputData.layer == 1u) { alpha = 0.0; }
    } else if (uint(u.appearance.x) == 2u) {
        color = inputData.layer == 1u ? vec3(0.82,0.96,1.0) : vec3(1.0);
    }
    alpha *= inputData.strength;
    fragColor = vec4(color * alpha, alpha);

}
