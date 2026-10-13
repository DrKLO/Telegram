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
// Read the optical hull directly from uniforms. Passing this array by value
// makes mobile drivers materialize private copies for each ray query.
uniform vec4 planes[17];
uniform SparkleAnchor anchors[8];
in vec3 v_localPosition;
in vec3 v_normal;
in vec3 v_worldPosition;
in vec3 v_facetWeights;
vec3 referenceCrown(vec2 p, vec4 gradient) {
    const float stops[7] =float[7](0.0, 0.162, 0.324, 0.501, 0.667, 0.833, 1.0);
    const vec3 colors[7] =vec3[7](vec3(0.145,0.792,1.0), vec3(0.373,0.875,1.0),
        vec3(0.6,0.957,1.0), vec3(0.3,0.798,1.0), vec3(0.0,0.639,1.0),
        vec3(0.0,0.545,1.0), vec3(0.0,0.451,1.0));
    vec2 start = gradient.xy, direction = gradient.zw - start;
    float t = saturate(dot(p-start, direction) / dot(direction,direction));
    for (uint i = 1u; i < 7u; ++i) {
        if (t <= stops[i]) { return mix(colors[i-1u], colors[i], (t-stops[i-1u])/(stops[i]-stops[i-1u])); }
    }
    return colors[6];
}

vec3 lateralDepth(vec3 color, vec3 normal, float y, vec3 facetWeights,
                    float opticalLuminance) {
    float horizontalNormal = length(normal.xz);
    float side = smoothstep(0.22, 0.80, abs(normal.x) / max(horizontalNormal, 0.001));
    float shoulder = smoothstep(0.06, 0.57, y);
    float lower = smoothstep(0.12, 0.90, -y);
    float reflectedLight = smoothstep(0.28, 0.78, color.g);
    // A restrained blue-violet shadow keeps the sides distinct without
    // swallowing their cyan gradients and moving reflections.
    vec3 deepBlue = mix(vec3(0.017, 0.035, 0.90),
                          vec3(0.006, 0.11, 1.0), reflectedLight);
    vec3 sideColor = mix(deepBlue, vec3(0.22, 0.86, 1.0), shoulder * 0.69);
    sideColor = mix(sideColor, vec3(0.018, 0.39, 1.0), lower * 0.52);
    float reflectionDetail = 0.105 + opticalLuminance * 0.14
                           + smoothstep(0.70, 0.95, color.g) * 0.26;
    sideColor = mix(sideColor, color, reflectionDetail);
    float weight = side * 0.96 * (1.0 - facetWeights.x);
    return mix(color, sideColor, weight);
}

vec3 facetBarycentric(vec2 p, vec2 a, vec2 b, vec2 c) {
    vec2 v = b-a, w = c-a, q = p-a;
    float determinant = v.x*w.y - v.y*w.x;
    float y = (q.x*w.y - q.y*w.x) / determinant;
    float z = (v.x*q.y - v.y*q.x) / determinant;
    return vec3(1.0-y-z, y, z);
}

float facetCoverage(vec3 barycentric) {
    float edge = min(barycentric.x, min(barycentric.y, barycentric.z));
    float aa = max(fwidth(edge), 0.004);
    return smoothstep(-aa, aa, edge);
}

vec2 sourceFacetPoint(vec3 localPosition, vec2 outward, Uniforms u) {
    float across = dot(localPosition.xz, vec2(outward.y,-outward.x));
    float depth = dot(localPosition.xz,outward);
    float c = u.facetProjection.x, s = u.facetProjection.y;
    float y = c*localPosition.y-s*depth;
    float z = s*localPosition.y+c*depth;
    float w = u.projection[2].w*z+u.projection[3].w;
    return vec2(257.6+across/w*u.facetProjection.z,
                  103.8+(u.facetProjection.w-y/w)*u.facetProjection.z);
}

vec3 illustratedFacets(vec3 color, Raster inputData, Uniforms u) {
    vec3 localNormal = normalize((u.inverseModel * vec4(normalize(inputData.normal), 0.0)).xyz);
    // Fixed material coordinates on each broad face. The same reference camera
    // maps the authored facets and the sparkle anchors onto the current cut.
    vec2 outward = abs(localNormal.x) > abs(localNormal.z)
        ? vec2(sign(localNormal.x),0.0) : vec2(0.0,sign(localNormal.z));
    vec3 tangent = vec3(outward.y,0.0,-outward.x);
    vec2 p = sourceFacetPoint(inputData.localPosition,outward,u);
    // Fade the drawing before the diagonal facets, where the next face's
    // coordinate system takes over. No duplicated motifs along rounded edges.
    vec2 normalXZ = abs(localNormal.xz);
    float broad = 1.0-smoothstep(0.20,0.65,min(normalXZ.x,normalXZ.y)/max(max(normalXZ.x,normalXZ.y),0.0001));
    vec3 worldTangent = (u.model * vec4(tangent, 0.0)).xyz;
    vec3 n = normalize(inputData.normal);
    vec3 light = normalize(vec3(0.6*sin(u.lightSweep.y), 0.5, 1.0));
    float leftLight = pow(saturate(dot(normalize(n - worldTangent*0.38), light)), 5.0);
    float rightLight = pow(saturate(dot(normalize(n + worldTangent*0.38), light)), 5.0);
    if (inputData.facetWeights.y > 0.0) {
        // Both crown triangles meet at the same upper junction.
        const vec2 crownJunction = vec2(258.8,128.9);
        // Authored lower corners now share the same projection as the mesh.
        const vec2 crownBaseLeft = vec2(108.8,240.8);
        const vec2 crownBaseRight = vec2(406.8,240.8);
        vec3 left = facetBarycentric(p, vec2(142.1,106.3), crownJunction, crownBaseLeft);
        float leftStrength = facetCoverage(left) * (0.18 + 0.40*leftLight) * saturate(1.0-left.y);
        color = mix(color, vec3(0.79,0.988,1.0), leftStrength * inputData.facetWeights.y * broad);
        vec3 right = facetBarycentric(p, crownBaseRight, crownJunction, vec2(378.5,108.2));
        // The source is light at the left junction and dark at the right edge.
        // Use horizontal facet coordinates so the gradient turns with the gem.
        float gradient = smoothstep(crownJunction.x,crownBaseRight.x,p.x);
        vec3 tint = mix(vec3(0.28,0.83,1.0),vec3(0.025,0.43,1.0),gradient);
        tint = mix(tint,vec3(0.57,0.95,1.0),rightLight*0.24);
        color = mix(color, tint, facetCoverage(right) * 0.90 * inputData.facetWeights.y * broad);
    }
    if (inputData.facetWeights.z > 0.0) {
        // A translucent kite belongs to each broad pavilion face. Material-space
        // edges stay attached to the surface throughout a complete rotation.
        vec2 kite = vec2(dot(inputData.localPosition,tangent),inputData.localPosition.y);
        const float halfWidth = 0.245, top = -0.61, shoulder = -0.70, bottom = -1.075;
        const float upperHeight = top-shoulder, lowerHeight = shoulder-bottom;
        float upperEdge = ((top-kite.y)*halfWidth-abs(kite.x)*upperHeight)
            / length(vec2(halfWidth,upperHeight));
        float lowerEdge = ((kite.y-bottom)*halfWidth-abs(kite.x)*lowerHeight)
            / length(vec2(halfWidth,lowerHeight));
        float edge = min(upperEdge,lowerEdge);
        float aa = max(fwidth(edge),0.0015);
        float coverage = smoothstep(-aa,aa,edge);
        float illumination = saturate(dot(n,normalize(vec3(-0.65,0.5,1.0))));
        vec3 tint = mix(vec3(0.48,0.96,1.0),vec3(0.70,1.0,1.0),illumination);
        float across = saturate(0.5+kite.x/(2.0*halfWidth));
        tint *= mix(vec3(1.0),vec3(0.88,0.97,1.0),across);
        color = mix(color,tint,coverage*0.28*inputData.facetWeights.z*broad);
    }

    return color;
}

vec3 studio(vec3 d, float phase) {
    float angle = 0.85 * sin(phase);
    d.xz = vec2(cos(angle)*d.x - sin(angle)*d.z, sin(angle)*d.x + cos(angle)*d.z);
    float up = smoothstep(-0.65, 0.85, d.y);
    vec3 c = mix(vec3(0.002, 0.075, 0.88), vec3(0.19, 0.78, 1.0), up);
    float key = pow(saturate(dot(d, normalize(vec3(-0.6, 0.8, 0.7)))), 10.0);
    float stripPosition = 0.12 + 0.48 * sin(phase);
    float strip = exp(-pow((d.x + d.y * 0.42 - stripPosition) * 9.0, 2.0));
    float side = pow(saturate(dot(d, normalize(vec3(0.8, 0.2, -0.5)))), 18.0);
    c = mix(c, vec3(0.68, 0.98, 1.0), key * 0.94);
    c = mix(c, vec3(0.80, 0.99, 1.0), strip * 0.64);
    c += side * vec3(0.1, 0.3, 0.38);
    return c;
}

vec2 facetEdgeRoll(vec2 p, vec2 a, vec2 b) {
    vec2 segment = b-a;
    float edgeLength = length(segment);
    vec2 along = segment/edgeLength;
    vec2 across = vec2(-along.y,along.x);
    float distance = dot(p-a,across);
    float progress = dot(p-a,along);
    float width = max(1.6,fwidth(distance));
    float q = distance/width;
    float ends = smoothstep(0.0,5.0,progress)*(1.0-smoothstep(edgeLength-5.0,edgeLength,progress));
    return across*(q*exp(-q*q)*0.22*ends);
}

vec3 polishedNormal(Raster inputData, vec3 localNormal, Uniforms u) {
    // Only the two surface crown diagonals get this tiny optical fillet.
    // The actual outline and the internal optical hull stay unchanged.
    if (inputData.facetWeights.y <= 0.0) { return normalize(inputData.normal); }
    vec2 outward = abs(localNormal.x) > abs(localNormal.z)
        ? vec2(sign(localNormal.x),0.0) : vec2(0.0,sign(localNormal.z));
    vec2 normalXZ = abs(localNormal.xz);
    float broad = 1.0-smoothstep(0.20,0.65,min(normalXZ.x,normalXZ.y)/max(max(normalXZ.x,normalXZ.y),0.0001));
    vec2 p = sourceFacetPoint(inputData.localPosition,outward,u);
    vec2 roll = facetEdgeRoll(p,vec2(258.8,128.9),vec2(108.8,240.8))
                + facetEdgeRoll(p,vec2(258.8,128.9),vec2(406.8,240.8));
    vec3 across = vec3(outward.y,0.0,-outward.x);
    vec3 up = normalize(cross(localNormal,across));
    vec3 normal = normalize(localNormal-(across*roll.x-up*roll.y)*broad*inputData.facetWeights.y);
    return normalize((u.model*vec4(normal,0.0)).xyz);
}

float studioPanel(vec3 reflected, vec3 direction, vec2 size) {
    float alignment = dot(reflected,direction);
    vec3 horizontal = normalize(cross(vec3(0.0,1.0,0.0),direction));
    vec3 vertical = cross(direction,horizontal);
    vec2 point = vec2(dot(reflected,horizontal),dot(reflected,vertical))/max(alignment,0.15);
    // Filter the narrow reflection at small sizes and grazing angles.
    vec2 dx = dFdx(point), dy = dFdy(point);
    vec2 variance = size*size+dx*dx+dy*dy;
    float energy = size.x*size.y/sqrt(variance.x*variance.y);
    return exp(-dot(point*point,1.0/variance))*energy*smoothstep(0.15,0.40,alignment);
}

vec3 surfaceFinish(vec3 color, vec3 normal, vec3 view, Uniforms u) {
    vec3 reflected = reflect(-view,normal);
    float phase = u.lightSweep.y;
    float key = studioPanel(reflected,normalize(vec3(-0.36+0.14*sin(phase),0.90,0.72)),vec2(0.16,0.48));
    float rim = studioPanel(reflected,normalize(vec3(0.72,-0.52+0.10*cos(phase),0.18)),vec2(0.10,0.60));
    float fresnel = pow(1.0-saturate(dot(normal,view)),4.0);
    float strength = key*(0.35+0.35*fresnel)+rim*(0.16+0.30*fresnel);
    return mix(color,vec3(0.87,0.99,1.0),strength);
}

// The optical hull always has 17 planes. Constant indices avoid the expensive
// dynamically indexed uniform loop on mobile GPUs, without changing ray math.

void exitPlane(vec4 plane, vec3 origin, vec3 ray, inout float nearest,
               inout float second, inout vec3 normal, inout vec3 secondNormal) {
    float denominator = dot(plane.xyz, ray);
    if (denominator > 0.0001) {
        float t = -(dot(plane.xyz, origin) + plane.w) / denominator;
        if (t > 0.001 && t < nearest) {
            second = nearest; secondNormal = normal;
            nearest = t; normal = plane.xyz;
        } else if (t > 0.001 && t < second) {
            second = t; secondNormal = plane.xyz;
        }
    }
}

float nearestExit(vec3 origin, vec3 ray, inout vec3 normal) {
    float nearest = 1e5;
    float second = 1e5;
    vec3 secondNormal = normal;
#define TEST_EXIT(I) exitPlane(planes[I], origin, ray, nearest, second, normal, secondNormal);
    TEST_EXIT(0u) TEST_EXIT(1u) TEST_EXIT(2u) TEST_EXIT(3u) TEST_EXIT(4u) TEST_EXIT(5u)
    TEST_EXIT(6u) TEST_EXIT(7u) TEST_EXIT(8u) TEST_EXIT(9u) TEST_EXIT(10u) TEST_EXIT(11u)
    TEST_EXIT(12u) TEST_EXIT(13u) TEST_EXIT(14u) TEST_EXIT(15u) TEST_EXIT(16u)
#undef TEST_EXIT
    if (second < 1e5) {
        // The optical hull inherits a tiny edge fillet. This also filters its
        // reflected boundaries when they become thinner than a screen pixel.
        float width = max(0.008,min(0.035,fwidth(second-nearest)));
        float blend = 0.5*(1.0-smoothstep(0.0,width,second-nearest));
        normal = normalize(mix(normal,secondNormal,blend));
    }
    return nearest;
}

vec4 oppositeFacets(vec3 p, vec3 ray, Uniforms u) {
    vec3 origin = p + ray*0.004;
    vec3 normal = vec3(0.0,1.0,0.0);
    float distance = nearestExit(origin,ray,normal);
    if (distance > 100.0 || normal.y > 0.95) { return vec4(0.0); }
    vec3 hit = origin + ray*distance;
    vec3 worldNormal = (u.model*vec4(normal,0.0)).xyz;
    vec3 tangent = normalize(vec3(normal.z+0.00001,0.0,-normal.x));
    float across = dot(hit,tangent);
    vec2 authored = vec2(across*225.8, (normal.y > 0.0 ? 0.3065-hit.y : -0.54-hit.y)*239.0);
    vec3 color;
    if (normal.y > 0.0) {
        color = referenceCrown(authored,u.crownGradient);
        float response = pow(saturate(dot(worldNormal, normalize(vec3(0.7*sin(u.lightSweep.y),0.55,-1.0)))),4.0);
        color = mix(color*vec3(0.45,0.70,0.98), vec3(0.60,0.94,1.0), response*0.55);
    } else {
        // The pavilion is one connected eight-facet hull. Looking through its
        // front reveals the actual opposite facet, without view-switched motifs.
        vec3 reflected = normalize((u.model*vec4(reflect(ray,normal),0.0)).xyz);
        vec3 environment = studio(reflected,u.lightSweep.y);
        float response = pow(saturate(dot(worldNormal,normalize(vec3(-0.4,-0.55,-1.0)))),2.0);
        color = mix(vec3(0.012,0.38,1.0),vec3(0.27,0.94,1.0),response*0.65+environment.g*0.35);
        color = mix(color,environment,0.28);
        float panel = studioPanel(reflected,normalize(vec3(-0.36,0.90,0.72)),vec2(0.20,0.52));
        color = mix(color,vec3(0.72,0.98,1.0),panel*0.48);
    }
    float coverage = smoothstep(0.03,0.35,distance) * exp(-distance*0.16);
    return vec4(color,coverage);
}

float sourceBand(vec2 p, vec4 gradient) {
    vec2 direction = gradient.zw-gradient.xy;
    float t = dot(p-gradient.xy,direction)/dot(direction,direction);
    return saturate(1.0-abs(t-0.49)/0.49);
}

float facetSweep(Raster inputData, vec3 localNormal, Uniforms u) {
    vec3 tangent = normalize(vec3(localNormal.z+0.00001,0.0,-localNormal.x));
    float across = dot(inputData.localPosition,tangent)*225.8;
    float side = smoothstep(0.20,0.50,abs(normalize(inputData.normal).x));
    bool right = inputData.normal.x > 0.0;
    vec4 crown = mix(u.crownSweep, right ? u.rightCrownSweep : u.leftCrownSweep, side);
    vec4 pavilion = mix(u.pavilionSweep, right ? u.rightPavilionSweep : u.leftPavilionSweep, side);
    return sourceBand(vec2(across,(0.3065-inputData.localPosition.y)*239.0),crown)*inputData.facetWeights.y
         + sourceBand(vec2(across,(-0.54-inputData.localPosition.y)*235.0),pavilion)*inputData.facetWeights.z;
}

vec3 internalEnvironment(vec3 position, vec3 direction, float pavilion, Uniforms u) {
    vec3 worldPosition = (u.model*vec4(position,1.0)).xyz;
    vec3 worldDirection = normalize((u.model*vec4(direction,0.0)).xyz);
    // A finite studio gives each reflected facet a spatial gradient, rather
    // than a flat swatch. The lights and the optical hull share one 3D space.
    float along = dot(worldPosition,worldDirection);
    float distance = -along+sqrt(max(0.0,along*along+3.2*3.2-dot(worldPosition,worldPosition)));
    vec3 sampleValue = normalize(worldPosition+worldDirection*distance);
    if (pavilion <= 0.0) { return studio(sampleValue,u.lightSweep.y); }
    // Keep a uniform blue surround below the crown. A bright upper hemisphere
    // reflected inputData the pavilion reads as a filled tip with a horizontal meniscus.
    // Narrow studio panels retain moving reflections without filling whole facets.
    float phase = u.lightSweep.y;
    float key = studioPanel(sampleValue,normalize(vec3(-0.12+0.035*sin(phase),-0.60,0.79)),vec2(0.055,0.52));
    float rim = studioPanel(sampleValue,normalize(vec3(0.12,0.75+0.035*cos(phase),-0.65)),vec2(0.055,0.50));
    vec3 lower = mix(vec3(0.015,0.41,1.0),vec3(0.58,0.94,1.0),key*0.95);
    lower = mix(lower,vec3(0.24,0.78,1.0),rim*0.82);
    return pavilion < 1.0 ? mix(studio(sampleValue,u.lightSweep.y),lower,pavilion) : lower;
}

float reflectionTriangle(vec3 origin, vec3 ray, vec3 a, vec3 b, vec3 c,
                         out vec3 coordinates) {
    vec3 ab = b-a, ac = c-a, crossRay = cross(ray,ac);
    float determinant = dot(ab,crossRay);
    if (abs(determinant) < 0.00001) { return 1e5; }
    vec3 offset = origin-a;
    float u = dot(offset,crossRay)/determinant;
    vec3 q = cross(offset,ab);
    float v = dot(ray,q)/determinant;
    float t = dot(ac,q)/determinant;
    coordinates = vec3(1.0-u-v,u,v);
    return min(min(coordinates.x,coordinates.y),coordinates.z) >= 0.0 && t > 0.001 ? t : 1e5;
}

vec3 pavilionReflections(vec3 color, vec3 origin, vec3 ray, Uniforms u) {
    if (u.parameters.y <= 0.0) { return color; }
    const vec2 ring[8] =vec2[8](vec2(0.0,1.0),vec2(0.70710678,0.70710678),
        vec2(1.0,0.0),vec2(0.70710678,-0.70710678),vec2(0.0,-1.0),
        vec2(-0.70710678,-0.70710678),vec2(-1.0,0.0),vec2(-0.70710678,0.70710678));
    // One convex fan of reflected facets, shared by every camera orientation.
    // Clip the whole volume so adjoining facets never acquire dark seams.
    const vec3 top = vec3(0.0,-0.28,0.0), tip = vec3(0.0,-1.06,0.0);
    float entry = 0.0, exit = 1e5;
    vec3 entryNormal = vec3(0.0,1.0,0.0);
    vec3 worldRay = normalize((u.model*vec4(ray,0.0)).xyz);
    vec3 illumination = normalize(vec3(-0.55,0.65,1.0));
    vec3 sideColor = vec3(0.0);
    float sideWeight = 0.0;
    vec3 veilColor = vec3(0.0);
    float veilWeight = 0.0;
    for (uint i = 0u; i < 8u; ++i) {
        vec2 current = ring[i], next = ring[(i+1u)%8u];
        vec3 a = vec3(current.x*0.28,-0.68,current.y*0.28);
        vec3 b = vec3(next.x*0.28,-0.68,next.y*0.28);
        for (uint part = 0u; part < 2u; ++part) {
            vec3 peak = part == 0u ? top : tip;
            vec3 n = normalize(cross(a-peak,b-peak));
            if (dot(n,(a+b+peak)/3.0-vec3(0.0,-0.68,0.0)) < 0.0) { n = -n; }
            float denominator = dot(n,ray);
            float side = dot(n,origin-peak);
            if (abs(denominator) < 0.00001) {
                if (side > 0.0) { exit = -1.0; }
            } else {
                float t = -side/denominator;
                if (denominator < 0.0 && t > entry) { entry = t; entryNormal = n; }
                if (denominator > 0.0) { exit = min(exit,t); }
            }
        }
        vec3 radial = vec3(current.x,0.0,current.y);
        vec3 tangent = vec3(current.y,0.0,-current.x);
        // Broad, low-contrast echoes of the crown stretch through the volume
        // towards the same lower junction. Adjacent planes meet without dark gaps.
        // Continue the same planes above the girdle so their top fade stays
        // hidden behind the crown, including the deeper, opposite reflections.
        const float upperY = 0.20;
        const float extension = (upperY+0.12)/0.65;
        const float upperRadius = 0.64+(0.64-0.19)*extension;
        const float upperWidth = 0.265097+(0.265097-0.078701)*extension;
        vec3 upperLeft = radial*upperRadius - tangent*upperWidth + vec3(0.0,upperY,0.0);
        vec3 upperRight = radial*upperRadius + tangent*upperWidth + vec3(0.0,upperY,0.0);
        vec3 lowerLeft = radial*0.19 - tangent*0.078701 + vec3(0.0,-0.77,0.0);
        vec3 lowerRight = radial*0.19 + tangent*0.078701 + vec3(0.0,-0.77,0.0);
        vec3 ribbonCoordinates;
        float ribbon = reflectionTriangle(origin,ray,upperLeft,upperRight,lowerLeft,ribbonCoordinates);
        if (ribbon > 100.0) {
            ribbon = reflectionTriangle(origin,ray,upperRight,lowerRight,lowerLeft,ribbonCoordinates);
        }
        if (ribbon < 100.0) {
            vec3 hit = origin+ray*ribbon;
            float depth = (-0.12-hit.y)/0.65;
            float across = dot(hit,tangent);
            float right = mix(0.265097,0.078701,depth), left = -right;
            vec3 worldRadial = normalize((u.model*vec4(radial,0.0)).xyz);
            float facing = saturate(-dot(worldRadial,worldRay));
            float fade = smoothstep(0.0,0.065,upperY-hit.y)*(1.0-smoothstep(0.70,1.0,depth));
            float opacity = fade*(0.16+0.84*facing);
            float light = saturate(0.5+0.5*dot(worldRadial,illumination));
            // Broad reflected facets fill the middle with restrained diagonal
            // changes inputData tone, expressed inputData the fixed plane's own coordinates.
            float acrossPlane = saturate((across-left)/(right-left));
            float diagonal = smoothstep(-0.045,0.045,acrossPlane-0.35-depth*0.55);
            float middle = smoothstep(0.12,0.24,depth)*(1.0-smoothstep(0.50,0.64,depth));
            vec3 tint = mix(vec3(0.015,0.56,1.0),vec3(0.33,0.97,1.0),light);
            tint = mix(tint,vec3(0.42,0.97,1.0),middle*(0.32-0.20*diagonal));
            tint = mix(tint,vec3(0.015,0.46,1.0),diagonal*0.24);
            veilColor += tint*opacity;
            veilWeight += opacity;
        }
        vec3 aSide = radial*0.72 + tangent*0.055 + vec3(0.0,-0.30,0.0);
        vec3 bSide = radial*0.49 - tangent*0.105 + vec3(0.0,-0.60,0.0);
        vec3 cSide = radial*0.15 + tangent*0.020 + vec3(0.0,-0.93,0.0);
        vec3 coordinates;
        float t = reflectionTriangle(origin,ray,aSide,bSide,cSide,coordinates);
        if (t < 100.0) {
            vec3 worldRadial = normalize((u.model*vec4(radial,0.0)).xyz);
            // Long glints read at the sides; a front-facing sector must not
            // become an opaque needle inputData the middle of the stone.
            float sideFacing = 1.0-abs(dot(worldRadial,worldRay));
            float edge = min(coordinates.y,coordinates.z);
            float opacity = smoothstep(0.20,0.75,sideFacing)
                * smoothstep(0.0,0.085,edge) * smoothstep(0.0,0.035,coordinates.x);
            float light = 0.5+0.5*dot(worldRadial,illumination);
            vec3 tint = mix(vec3(0.16,0.80,1.0),vec3(0.85,1.0,1.0),light);
            sideColor += tint*opacity;
            sideWeight += opacity;
        }
    }
    float strength = saturate(u.parameters.y/0.72);
    if (veilWeight > 0.0) {
        color = mix(color,veilColor/veilWeight,(1.0-exp(-veilWeight*0.46))*strength);
    }
    if (sideWeight > 0.0) {
        color = mix(color,sideColor/sideWeight,(1.0-exp(-sideWeight*1.4))*strength);
    }
    {
        // studioPanel uses screen derivatives. Evaluate it on both sides of the
        // hull boundary; divergent execution produces dotted edges on Adreno.
        vec3 hit = origin+ray*entry;
        vec3 worldNormal = normalize((u.model*vec4(entryNormal,0.0)).xyz);
        float light = saturate(dot(worldNormal,normalize(vec3(-0.65,0.15,1.0))));
        float gleam = studioPanel(reflect(worldRay,worldNormal),
            normalize(vec3(-0.35+0.15*sin(u.lightSweep.y),0.60,0.75)),vec2(0.25,0.65));
        vec3 tint = mix(vec3(0.025,0.54,1.0),vec3(0.40,0.92,1.0),light);
        tint = mix(tint,vec3(0.72,0.99,1.0),gleam*0.16);
        float thickness = smoothstep(0.0,0.18,exit-entry);
        float fade = smoothstep(-1.07,-0.93,hit.y);
        color = mix(color,tint,0.28*thickness*fade*strength*(entry < exit ? 1.0 : 0.0));
    }
    return color;
}

vec3 interior(vec3 p, vec3 direction, float pavilion, Uniforms u) {
    vec3 accumulated = vec3(0.0);
    float weight = 0.60;
    vec3 origin = p + direction * 0.004;
    for (uint bounce = 0u; bounce < 2u; ++bounce) {
        vec3 n = vec3(0.0, 1.0, 0.0);
        float distance = nearestExit(origin, direction, n);
        if (distance > 100.0) { break; }
        vec3 hit = origin + direction * distance;
        vec3 outgoing = refract(direction, -n, 1.62);
        vec3 reflection = reflect(direction, n);
        vec3 color = internalEnvironment(hit,reflection,pavilion,u);
        if (dot(outgoing,outgoing) > 0.01) {
            // Fresnel approaches total internal reflection continuously; a hard
            // switch between the two rays made whole patches change abruptly.
            float incident = saturate(dot(direction,n));
            float transmitted = saturate(dot(outgoing,n));
            float rs = (1.62*incident-transmitted)/(1.62*incident+transmitted);
            float rp = (incident-1.62*transmitted)/(incident+1.62*transmitted);
            float reflectance = 0.5*(rs*rs+rp*rp);
            color = mix(internalEnvironment(hit,outgoing,pavilion,u),color,reflectance);
        }
        color *= exp(-vec3(0.30, 0.07, 0.006) * distance);
        accumulated += weight * color;
        weight *= 0.52;
        direction = reflection;
        origin = hit + direction * 0.004;
    }
    return accumulated;
}

vec3 materialPalette(vec3 color, vec3 facetWeights, uint appearance) {
    switch (appearance) {
        case 1u: {
            // Keep the authored icy blue inputData the middle values: interpolating
            // directly to white desaturates the crown into a cold grey.
            float tone = smoothstep(0.0,0.92,saturate(dot(color.rg,vec2(0.22,0.78))));
            vec3 shadow = mix(vec3(0.63,0.77,0.95),vec3(0.733,0.859,1.0),facetWeights.y*0.25);
            const vec3 ice = vec3(207.0,241.0,253.0) / 255.0; // #cff1fd
            vec3 upper = mix(shadow,ice,smoothstep(0.0,0.62,tone));
            upper = mix(upper,vec3(1.0),smoothstep(0.58,1.0,tone)*0.92);
            // Lift the pavilion while retaining a distinct shadow range below
            // the white crown, with a little more blue inputData its lighter facets.
            vec3 lower = mix(vec3(0.59,0.71,0.865),vec3(0.96,0.985,1.0),pow(tone,1.10));
            return mix(upper,lower,facetWeights.z);
        }
        case 2u: {
            float tone = smoothstep(0.035,0.90,saturate(dot(color.rg,vec2(0.20,0.80))));
            vec3 cool = mix(vec3(0.025,0.29,0.83),vec3(0.38,0.78,1.0),smoothstep(0.0,0.65,tone));
            vec3 upper = mix(cool,vec3(0.94,0.99,1.0),smoothstep(0.45,1.0,tone));
            vec3 lower = mix(vec3(0.025,0.34,0.92),vec3(0.90,0.99,1.0),pow(tone,2.35));
            return mix(upper,lower,facetWeights.z);
        }
        default: return color;
    }
}

void glowPlane(vec4 plane, vec3 position, vec3 ray, vec3 localNormal,
               inout float planeAlignment, inout float thickness) {
    planeAlignment = max(planeAlignment,dot(plane.xyz,localNormal));
    float denominator = dot(plane.xyz,ray);
    if (denominator > 0.0001) {
        float exit = -(dot(plane.xyz,position)+plane.w) / denominator;
        thickness = min(thickness,max(0.0,exit));
    }
}

vec3 coolInnerGlow(vec3 color, vec3 position, vec3 ray, vec3 normal, vec3 localNormal, vec3 view,
                     Uniforms u) {
    // Thin parts transmit a white light band into the stone. The optical chord
    // follows the 3D cut under every rotation, without expanding the silhouette.
    float thickness = 1e5;
    float planeAlignment = 0.0;
#define TEST_GLOW(I) glowPlane(planes[I], position, ray, localNormal, planeAlignment, thickness);
    TEST_GLOW(0u) TEST_GLOW(1u) TEST_GLOW(2u) TEST_GLOW(3u) TEST_GLOW(4u) TEST_GLOW(5u)
    TEST_GLOW(6u) TEST_GLOW(7u) TEST_GLOW(8u) TEST_GLOW(9u) TEST_GLOW(10u) TEST_GLOW(11u)
    TEST_GLOW(12u) TEST_GLOW(13u) TEST_GLOW(14u) TEST_GLOW(15u) TEST_GLOW(16u)
#undef TEST_GLOW
    // Scale the penetration with the taper: the tip gets a narrow rim,
    // rather than filling with a solid white pool as its whole depth shrinks.
    float crossSection = clamp((position.y+1.09)/0.98,0.07,1.0);
    float inner = 1.0-smoothstep(0.01,0.80*crossSection,thickness);
    float grazing = 1.0-saturate(dot(normal,view));
    float roundedEdge = smoothstep(0.0015,0.028,1.0-planeAlignment);
    float bevel = pow(grazing,3.0);
    float glow = saturate(inner*0.85 + bevel*0.85 + roundedEdge*smoothstep(0.18,0.80,grazing));
    return mix(color,vec3(0.96,1.0,1.0),glow);
}

vec4 diamondSurface(Raster inputData, Uniforms u) {
    vec3 n = normalize(inputData.normal);
    float cameraDistance = -u.projection[3].w / u.projection[2].w;
    vec3 view = normalize(vec3(0.0, 0.0, cameraDistance) - inputData.worldPosition);
    vec3 localView = (u.inverseModel * vec4(-view, 0.0)).xyz;
    vec3 localNormal = normalize((u.inverseModel * vec4(n, 0.0)).xyz);
    vec3 transmitted = refract(localView, localNormal, 1.0 / 1.62);
    // Zero internal bounces on every device; retain the existing material mapping.
    vec3 optical = vec3(0.0);
    vec3 reflection = studio(reflect(-view, n), u.lightSweep.y);
    float fresnel = 0.08 + 0.46 * pow(1.0 - saturate(dot(n, view)), 4.0);

    float height = saturate((inputData.localPosition.y + 1.05) / 1.65);
    float key = saturate(dot(n, normalize(vec3(-0.65, 0.85, 1.0))));
    float left = saturate(0.52 - inputData.worldPosition.x * 0.43);
    vec3 blue = vec3(0.008, 0.22, 1.0);
    vec3 cyan = vec3(0.29, 0.87, 1.0);
    vec3 body = mix(blue, cyan, saturate(key * 0.60 + left * 0.38));
    float verticalBand = mix(0.5 + 0.5 * sin(height * 18.0 + n.x * 3.0),1.0,inputData.facetWeights.z);
    body *= mix(vec3(0.24, 0.48, 0.96), vec3(1.0), verticalBand * 0.5 + 0.5);
    float opticalLuminance = smoothstep(0.25, 0.85, optical.g);
    // Give the pavilion a cyan body tone without changing reflection contrast
    // or the subdued lower motif. Crown and table keep their existing palette.
    vec3 opticalShadow = mix(vec3(0.005,0.13,1.0),vec3(0.015,0.36,1.0),inputData.facetWeights.z);
    vec3 opticalLight = mix(vec3(0.58,0.97,1.0),vec3(0.45,0.99,1.0),inputData.facetWeights.z);
    optical = mix(opticalShadow,opticalLight,opticalLuminance);
    float opticalWeight = u.parameters.y * mix(0.90, 0.08, inputData.facetWeights.y);
    vec3 color = mix(body, optical, opticalWeight);
    color = mix(color, reflection, fresnel);

    float crownGlow = smoothstep(0.10, 0.60, inputData.localPosition.y) * left;
    color = mix(color, vec3(0.70, 0.99, 1.0), crownGlow * 0.58);
    float crownLight = pow(saturate(dot(n, normalize(vec3(-0.38, 0.55, 1.0)))), 9.0);
    crownLight *= smoothstep(-0.03, 0.28, inputData.localPosition.y);
    color = mix(color, vec3(0.65, 0.98, 1.0), crownLight * 0.35);
    vec3 facetBase = color;
    if (inputData.facetWeights.y > 0.0) {
        float softbox = exp(-pow((inputData.worldPosition.x + 0.42) * 1.6, 2.0)
                           -pow((inputData.worldPosition.y - 0.32) * 2.2, 2.0));
        color = mix(color, vec3(0.71, 0.99, 1.0), softbox * crownLight * 0.36);
        float shadow = pow(saturate(1.0 - key), 1.4);
        color = mix(color, vec3(0.015, 0.08, 1.0), shadow * 0.7);
        vec3 tangent = normalize(vec3(localNormal.z, 0.0, -localNormal.x));
        vec2 authoredPosition = vec2(dot(inputData.localPosition, tangent) * 225.8 + n.x * 65.0,
                                         (0.3065 - inputData.localPosition.y) * 244.0);
        color = mix(referenceCrown(authoredPosition, u.crownGradient), color, 0.28);
        color = mix(facetBase, color, inputData.facetWeights.y);
    }
    if (inputData.facetWeights.z > 0.0) {
        color = mix(color,mix(vec3(0.008,0.32,1.0),optical,0.86),inputData.facetWeights.z*u.parameters.y);
    }
    float facetFlash = smoothstep(0.20, 0.78, opticalLuminance);
    float flashWeight = mix(0.24,0.08,inputData.facetWeights.y);
    color *= mix(vec3(1.0),mix(vec3(0.76, 0.82, 0.99), vec3(1.0), facetFlash),flashWeight);
    color = mix(color, vec3(0.66, 0.98, 1.0), pow(facetFlash, 2.0) * 0.24 * flashWeight);
    color = lateralDepth(color, n, inputData.localPosition.y, inputData.facetWeights, opticalLuminance);
    color = mix(color,optical,inputData.facetWeights.z*u.parameters.y*0.48);
    color = mix(color,body,inputData.facetWeights.z*pow(saturate(n.z),2.0)*0.32);
    vec3 rearRay = normalize(mix(localView,transmitted,mix(0.68,0.18,inputData.facetWeights.y)));
    // Disable the opposite-face ray independently of the lower-facet reflections.
    vec4 rear = vec4(0.0);
    float transmission = mix(0.34,0.12+u.lightSweep.z*0.65,inputData.facetWeights.y) * u.parameters.y;
    float facing = smoothstep(0.12,0.65,n.z);
    color = mix(color,rear.rgb,rear.a*transmission*facing*mix(1.0,0.12,inputData.facetWeights.y));
    if (inputData.facetWeights.z > 0.0) {
        vec3 detailRay = normalize(mix(localView,transmitted,0.16));
        color = mix(color,pavilionReflections(color,inputData.localPosition,detailRay,u),inputData.facetWeights.z);
    }
    float tipGlow = pow(saturate((-inputData.localPosition.y - 0.55) / 0.50), 2.0);
    color = mix(color, vec3(0.57, 0.96, 1.0), tipGlow * 0.20);
    // A shallow, translucent image of the table, like Layer 36.0 inputData Lottie.
    // Its optical footprint is smaller than the rounded outer shoulder.
    vec3 tableRay = localView;
    tableRay.y *= 0.72;
    if (tableRay.y > 0.001) {
        float tableHeight = -planes[0].w / planes[0].y;
        float distance = (tableHeight-inputData.localPosition.y)/tableRay.y;
        vec2 hit = (inputData.localPosition + tableRay*distance).xz;
        const float extent = 0.632;
        const float corner = 0.285;
        vec2 q = abs(hit);
        float edge = max(max(q.x,q.y)-extent, (q.x+q.y-extent-corner)*0.70710678);
        float aa = max(fwidth(edge),0.001);
        float coverage = 1.0-smoothstep(-aa,aa,edge);
        vec2 direction = tableRay.xz / max(length(tableRay.xz),0.0001);
        float depth = saturate(0.5+dot(hit,direction)/(2.0*extent));
        // Source opacity: 80.0% group opacity times a 49...65% fill gradient.
        float opacity = mix(0.39,0.52,depth);
        color = mix(color,vec3(0.765,0.988,1.0),coverage*opacity
                    *saturate(u.parameters.y/0.72)*inputData.facetWeights.y);
    }
    color = illustratedFacets(color, inputData, u);
    color = mix(color, vec3(0.63, 0.96, 1.0), 0.58 * inputData.facetWeights.x);
    float sweep = facetSweep(inputData,localNormal,u);
    float sweepCore = smoothstep(0.35,1.0,sweep);
    color = mix(color,vec3(0.592,0.953,1.0),sweep*0.20+sweepCore*sweepCore*0.62);
    color = surfaceFinish(color,polishedNormal(inputData,localNormal,u),view,u);
    color = materialPalette(color,inputData.facetWeights,uint(u.appearance.x));
    if (uint(u.appearance.x) == 2u) {
        color = coolInnerGlow(color,inputData.localPosition,localView,n,localNormal,view,u);
    }
    if (uint(u.appearance.x) == 1u) {
        // Source facet flashes stay on the physical front/side planes, including
        // their rounded transitions. They never become screen-space overlays.
        vec2 direction = normalize(localNormal.xz + vec2(0.0,0.00001));
        float side = smoothstep(0.18,0.65,abs(direction.x));
        float front = smoothstep(0.02,0.32,direction.y);
        vec3 regions = vec3(1.0-side, side*step(0.0,direction.x), side*(1.0-step(0.0,direction.x))) * front;
        float alpha = dot(regions,u.referenceCrownFlash.xyz)*inputData.facetWeights.y
                    + dot(regions,u.referencePavilionFlash.xyz)*inputData.facetWeights.z;
        float referenceFacing = smoothstep(0.0,0.2,(u.model * vec4(0.0,0.0,1.0,0.0)).z);
        color = mix(color,vec3(1.0),alpha*referenceFacing);
    }
    float whiten = u.appearance.y;
    if (whiten > 0.0) {
        float w = pow(whiten, 0.7);
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        float lift = smoothstep(0.32, 0.62, luma);
        vec3 glass = mix(vec3(0.07, 0.33, 0.95), vec3(1.0), lift);
        float rim = pow(1.0 - saturate(abs(n.z)), 2.2);
        glass = mix(glass, vec3(1.0), rim * 0.8);
        float seam = saturate(length(fwidth(n)) * 5.5);
        glass = mix(glass, vec3(1.0), seam * 0.85);
        glass = max(glass, vec3(smoothstep(0.62, 0.95, luma)));
        color = mix(color, glass, w);
        float alpha = mix(1.0, mix(0.34, 0.94, max(lift, max(rim, seam))), w);
        return vec4(saturate(color * u.parameters.z) * alpha, alpha);
    }
    return vec4(saturate(color * u.parameters.z), 1.0);
}

out vec4 fragColor;

void main(){
Raster inputData;
inputData.localPosition=v_localPosition;
inputData.normal=v_normal;
inputData.worldPosition=v_worldPosition;
inputData.facetWeights=v_facetWeights;

    fragColor = diamondSurface(inputData, u);

}
