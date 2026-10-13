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
out vec3 v_localPosition;
out vec3 v_normal;
out vec3 v_worldPosition;
out vec3 v_facetWeights;
layout(location=0) in vec4 a_position; layout(location=1) in vec4 a_normal; layout(location=2) in vec4 a_surface;

void main(){

    Vertex v = Vertex(a_position,a_normal,a_surface);
    vec4 world = u.model * v.position;
    Raster result;
    result.position = u.projection * world;
    result.localPosition = v.position.xyz;
    result.normal = normalize((u.model * v.normal).xyz);
    result.worldPosition = world.xyz;
    result.facetWeights = v.surface.z == 3.0 ? vec3(v.surface.xy, 1.0-v.surface.x-v.surface.y)
        : vec3(v.surface.z == 0.0, v.surface.z == 1.0, v.surface.z == 2.0);
    gl_Position=result.position; gl_Position.z=gl_Position.z*2.0-gl_Position.w;
v_localPosition=result.localPosition;
v_normal=result.normal;
v_worldPosition=result.worldPosition;
v_facetWeights=result.facetWeights;

}
