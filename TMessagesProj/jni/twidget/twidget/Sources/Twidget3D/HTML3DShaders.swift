let html3DShaderSource = #"""
#include <metal_stdlib>
using namespace metal;

#define RECIPROCAL_PI 0.3183098861837907

struct FrameUniforms {
    float4x4 view;
    float4x4 projection;
    float4x4 shadowViewProjection;
    float4 cameraPosition;
    float4 ambient;
    float4 hemiSky;
    float4 hemiGround;
    float4 hemiDirection;
    float4 directionalColor;
    float4 directionalDirection;
    float4 shadowParams;
    float4 shadowSize;
    float4 fogColor;
    float4 fogParams;
    float4 viewport;
    float4 lightPosition[4];
    float4 lightColor[4];
    float4 lightDirection[4];
    float4 lightCone[4];
    int4 counts;
};

struct DrawUniforms {
    float4x4 base;
    float4 color;
    float4 emissive;
    float4 specular;
    float4 params;
    uint4 layout;
};

constant uint FlagTransparent = 1u;
constant uint FlagVertexColors = 2u;
constant uint FlagFlatShading = 4u;
constant uint FlagFog = 64u;
constant uint FlagReceiveShadow = 512u;

struct VertexOut {
    float4 position [[position]];
    float3 worldPosition;
    float3 worldNormal;
    float4 color;
    float4 shadowCoord;
    float viewDepth;
};

struct PointOut {
    float4 position [[position]];
    float4 color;
    float viewDepth;
    float size [[point_size]];
};

static float4x4 modelMatrix(constant DrawUniforms& draw, device const float4x4* instances, uint iid) {
    return draw.base * instances[iid];
}

static float4 vertexColor(constant DrawUniforms& draw, device const float* colors, device const float* instanceColors, uint vid, uint iid) {
    float4 c = float4(1.0);
    uint size = draw.layout.y;
    if (size == 3u) {
        c = float4(colors[vid * 3u], colors[vid * 3u + 1u], colors[vid * 3u + 2u], 1.0);
    } else if (size == 4u) {
        c = float4(colors[vid * 4u], colors[vid * 4u + 1u], colors[vid * 4u + 2u], colors[vid * 4u + 3u]);
    }
    if (draw.layout.z != 0u) {
        c.rgb *= float3(instanceColors[iid * 3u], instanceColors[iid * 3u + 1u], instanceColors[iid * 3u + 2u]);
    }
    return c;
}

vertex VertexOut html3DVertex(uint vid [[vertex_id]], uint iid [[instance_id]],
                              device const float* positions [[buffer(0)]],
                              device const float* normals [[buffer(1)]],
                              device const float* colors [[buffer(2)]],
                              device const float4x4* instances [[buffer(3)]],
                              device const float* instanceColors [[buffer(4)]],
                              constant DrawUniforms& draw [[buffer(5)]],
                              constant FrameUniforms& frame [[buffer(6)]]) {
    float4x4 model = modelMatrix(draw, instances, iid);
    float4 world = model * float4(positions[vid * 3u], positions[vid * 3u + 1u], positions[vid * 3u + 2u], 1.0);
    float3 normal = float3(0.0, 0.0, 1.0);
    if (draw.layout.x != 0u) {
        float3 n = float3(normals[vid * 3u], normals[vid * 3u + 1u], normals[vid * 3u + 2u]);
        float3 c0 = model[0].xyz, c1 = model[1].xyz, c2 = model[2].xyz;
        float3x3 cofactor = float3x3(cross(c1, c2), cross(c2, c0), cross(c0, c1));
        normal = cofactor * n;
    }
    float4 viewPosition = frame.view * world;
    VertexOut out;
    out.position = frame.projection * viewPosition;
    out.worldPosition = world.xyz;
    out.worldNormal = normal;
    out.color = vertexColor(draw, colors, instanceColors, vid, iid);
    float3 shadowNormal = length(normal) > 0.0 ? normalize(normal) : float3(0.0);
    out.shadowCoord = frame.shadowViewProjection * float4(world.xyz + shadowNormal * frame.shadowParams.y, 1.0);
    out.viewDepth = -viewPosition.z;
    return out;
}

vertex PointOut html3DPointVertex(uint vid [[vertex_id]], uint iid [[instance_id]],
                                  device const float* positions [[buffer(0)]],
                                  device const float* normals [[buffer(1)]],
                                  device const float* colors [[buffer(2)]],
                                  device const float4x4* instances [[buffer(3)]],
                                  device const float* instanceColors [[buffer(4)]],
                                  constant DrawUniforms& draw [[buffer(5)]],
                                  constant FrameUniforms& frame [[buffer(6)]]) {
    float4x4 model = modelMatrix(draw, instances, iid);
    float4 viewPosition = frame.view * (model * float4(positions[vid * 3u], positions[vid * 3u + 1u], positions[vid * 3u + 2u], 1.0));
    PointOut out;
    out.position = frame.projection * viewPosition;
    out.color = vertexColor(draw, colors, instanceColors, vid, iid);
    out.viewDepth = -viewPosition.z;
    out.size = clamp(draw.params.w * frame.viewport.z, 1.0, 64.0 * frame.viewport.z);
    return out;
}

vertex float4 html3DShadowVertex(uint vid [[vertex_id]], uint iid [[instance_id]],
                                 device const float* positions [[buffer(0)]],
                                 device const float4x4* instances [[buffer(3)]],
                                 constant DrawUniforms& draw [[buffer(5)]],
                                 constant FrameUniforms& frame [[buffer(6)]]) {
    float4x4 model = modelMatrix(draw, instances, iid);
    return frame.shadowViewProjection * (model * float4(positions[vid * 3u], positions[vid * 3u + 1u], positions[vid * 3u + 2u], 1.0));
}

static float3 schlick(float3 f0, float f90, float dotVH) {
    float fresnel = exp2((-5.55473 * dotVH - 6.98316) * dotVH);
    return f0 * (1.0 - fresnel) + (f90 * fresnel);
}

static float3 blinnPhong(float3 lightDir, float3 viewDir, float3 normal, float3 specularColor, float shininess) {
    float3 halfDir = normalize(lightDir + viewDir);
    float dotNH = saturate(dot(normal, halfDir));
    float dotVH = saturate(dot(viewDir, halfDir));
    float3 f = schlick(specularColor, 1.0, dotVH);
    float d = RECIPROCAL_PI * (shininess * 0.5 + 1.0) * pow(dotNH, shininess);
    return f * (0.25 * d);
}

static float3 ggx(float3 lightDir, float3 viewDir, float3 normal, float3 f0, float roughness) {
    float alpha = roughness * roughness;
    float3 halfDir = normalize(lightDir + viewDir);
    float dotNL = saturate(dot(normal, lightDir));
    float dotNV = saturate(dot(normal, viewDir));
    float dotNH = saturate(dot(normal, halfDir));
    float dotVH = saturate(dot(viewDir, halfDir));
    float3 f = schlick(f0, 1.0, dotVH);
    float a2 = alpha * alpha;
    float gv = dotNL * sqrt(a2 + (1.0 - a2) * dotNV * dotNV);
    float gl = dotNV * sqrt(a2 + (1.0 - a2) * dotNL * dotNL);
    float v = 0.5 / max(gv + gl, 1e-6);
    float denom = dotNH * dotNH * (a2 - 1.0) + 1.0;
    float d = RECIPROCAL_PI * a2 / (denom * denom);
    return f * (v * d);
}

static float distanceAttenuation(float lightDistance, float cutoff, float decay) {
    float falloff = 1.0 / max(pow(lightDistance, decay), 0.01);
    if (cutoff > 0.0) {
        float ratio = lightDistance / cutoff;
        falloff *= pow(saturate(1.0 - ratio * ratio * ratio * ratio), 2.0);
    }
    return falloff;
}

static float shadowFactor(float4 shadowCoord, constant FrameUniforms& frame, depth2d<float> shadowMap, sampler shadowSampler) {
    float3 coord = shadowCoord.xyz / shadowCoord.w;
    float2 uv = float2(coord.x * 0.5 + 0.5, 0.5 - coord.y * 0.5);
    float depth = coord.z + frame.shadowParams.x;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0 || depth > 1.0 || depth < 0.0) {
        return 1.0;
    }
    float texel = frame.shadowSize.y * max(frame.shadowParams.z, 1.0);
    float sum = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            sum += shadowMap.sample_compare(shadowSampler, uv + float2(float(x), float(y)) * texel, depth);
        }
    }
    return sum / 9.0;
}

static float3 toonIrradiance(float dotNL) {
    float coord = dotNL * 0.5 + 0.5;
    return mix(float3(0.7), float3(1.0), smoothstep(0.69, 0.71, coord));
}

static float4 applyFog(float4 color, float viewDepth, constant FrameUniforms& frame, uint flags) {
    if ((flags & FlagFog) == 0u || frame.fogColor.w < 0.5) {
        return color;
    }
    float factor;
    if (frame.fogColor.w < 1.5) {
        factor = smoothstep(frame.fogParams.x, frame.fogParams.y, viewDepth);
    } else {
        float density = frame.fogParams.z;
        factor = 1.0 - exp(-density * density * viewDepth * viewDepth);
    }
    color.rgb = mix(color.rgb, frame.fogColor.rgb, factor);
    return color;
}

fragment float4 html3DFragment(VertexOut in [[stage_in]], bool front [[front_facing]],
                               constant DrawUniforms& draw [[buffer(0)]],
                               constant FrameUniforms& frame [[buffer(1)]],
                               depth2d<float> shadowMap [[texture(0)]],
                               sampler shadowSampler [[sampler(0)]]) {
    uint flags = draw.layout.w;
    uint type = uint(draw.emissive.w + 0.5);
    float4 albedo = draw.color;
    if ((flags & FlagVertexColors) != 0u) {
        albedo *= in.color;
    } else if (draw.layout.z != 0u) {
        albedo.rgb *= in.color.rgb;
    }
    float opacity = (flags & FlagTransparent) != 0u ? albedo.a : 1.0;

    float3 normal;
    if ((flags & FlagFlatShading) != 0u || draw.layout.x == 0u) {
        normal = normalize(cross(dfdy(in.worldPosition), dfdx(in.worldPosition)));
    } else {
        normal = normalize(in.worldNormal) * (front ? 1.0 : -1.0);
    }

    if (type == 4u) {
        float3 viewNormal = normalize((frame.view * float4(normal, 0.0)).xyz);
        return float4(viewNormal * 0.5 + 0.5, opacity);
    }
    if (type == 0u) {
        return applyFog(float4(albedo.rgb + draw.emissive.rgb, opacity), in.viewDepth, frame, flags);
    }

    bool orthographic = frame.projection[3][3] > 0.5;
    float3 viewDir = orthographic ? normalize(float3(frame.view[0][2], frame.view[1][2], frame.view[2][2])) : normalize(frame.cameraPosition.xyz - in.worldPosition);

    float3 diffuseColor = albedo.rgb;
    float3 specularColor = draw.specular.rgb;
    float roughness = 1.0;
    if (type == 3u) {
        float metalness = draw.params.y;
        roughness = max(draw.params.x, 0.0525);
        float3 dxy = max(abs(dfdx(normal)), abs(dfdy(normal)));
        roughness = min(roughness + max(max(dxy.x, dxy.y), dxy.z), 1.0);
        specularColor = mix(float3(0.04), diffuseColor, metalness);
        diffuseColor *= (1.0 - metalness);
    }

    float3 directDiffuse = float3(0.0);
    float3 directSpecular = float3(0.0);

    if (frame.directionalColor.w > 0.5) {
        float3 lightDir = normalize(frame.directionalDirection.xyz);
        float dotNL = saturate(dot(normal, lightDir));
        float shadow = 1.0;
        if ((flags & FlagReceiveShadow) != 0u && frame.shadowParams.w > 0.5) {
            shadow = shadowFactor(in.shadowCoord, frame, shadowMap, shadowSampler);
        }
        float3 irradiance = (type == 5u ? toonIrradiance(dot(normal, lightDir)) : float3(dotNL)) * frame.directionalColor.rgb * shadow;
        directDiffuse += irradiance * diffuseColor * RECIPROCAL_PI;
        if (type == 2u) {
            directSpecular += irradiance * blinnPhong(lightDir, viewDir, normal, specularColor, draw.specular.w);
        } else if (type == 3u) {
            directSpecular += irradiance * ggx(lightDir, viewDir, normal, specularColor, roughness);
        }
    }

    for (int i = 0; i < frame.counts.x; i++) {
        float3 toLight = frame.lightPosition[i].xyz - in.worldPosition;
        float lightDistance = length(toLight);
        float3 lightDir = toLight / max(lightDistance, 1e-5);
        float attenuation = distanceAttenuation(lightDistance, frame.lightPosition[i].w, frame.lightColor[i].w);
        if (frame.lightDirection[i].w > 0.5) {
            float angleCos = dot(lightDir, normalize(frame.lightDirection[i].xyz));
            attenuation *= smoothstep(frame.lightCone[i].x, frame.lightCone[i].y, angleCos);
        }
        if (attenuation <= 0.0) {
            continue;
        }
        float dotNL = saturate(dot(normal, lightDir));
        float3 irradiance = (type == 5u ? toonIrradiance(dot(normal, lightDir)) : float3(dotNL)) * frame.lightColor[i].rgb * attenuation;
        directDiffuse += irradiance * diffuseColor * RECIPROCAL_PI;
        if (type == 2u) {
            directSpecular += irradiance * blinnPhong(lightDir, viewDir, normal, specularColor, draw.specular.w);
        } else if (type == 3u) {
            directSpecular += irradiance * ggx(lightDir, viewDir, normal, specularColor, roughness);
        }
    }

    float3 indirect = frame.ambient.rgb;
    if (frame.hemiSky.w > 0.5) {
        float weight = 0.5 * dot(normal, normalize(frame.hemiDirection.xyz)) + 0.5;
        indirect += mix(frame.hemiGround.rgb, frame.hemiSky.rgb, weight);
    }
    float3 indirectDiffuse = indirect * diffuseColor * RECIPROCAL_PI;

    float3 outgoing = directDiffuse + indirectDiffuse + directSpecular + draw.emissive.rgb;
    return applyFog(float4(outgoing, opacity), in.viewDepth, frame, flags);
}

fragment float4 html3DPointFragment(PointOut in [[stage_in]], float2 coord [[point_coord]],
                                    constant DrawUniforms& draw [[buffer(0)]],
                                    constant FrameUniforms& frame [[buffer(1)]]) {
    uint flags = draw.layout.w;
    float4 color = draw.color;
    if ((flags & FlagVertexColors) != 0u || draw.layout.z != 0u) {
        color.rgb *= in.color.rgb;
    }
    float opacity = (flags & FlagTransparent) != 0u ? color.a : 1.0;
    return applyFog(float4(color.rgb, opacity), in.viewDepth, frame, flags);
}
"""#
