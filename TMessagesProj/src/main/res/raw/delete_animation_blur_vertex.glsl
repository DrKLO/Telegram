#version 300 es

precision highp float;

layout (location = 0) in vec2 inPosition;
layout (location = 1) in vec2 inTextCoord;
layout (location = 2) in vec2 inTargetOffset;
layout (location = 3) in vec2 inTargetHorizontalOffset;

out vec2 outPosition;
out vec2 outTextCoord;
out vec2 outTargetOffset;
out vec2 outTargetHorizontalOffset;

out vec2 vTexCoord;
out float vAlpha;

uniform int uParticlesCount;
uniform float uParticleRadius;
uniform float uMinOffset;
uniform float uMaxOffset;
uniform float uMaxHorizontalOffset;
//x - width, y - height
uniform vec2 uScreenSizePx;
//x - left, y - top, z - right, w - bottom
uniform vec4 uParticleBoundsPx;
//[0..1]
uniform float uProgress;
//1 - true
uniform float uInit;

// return [0..1]
float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

// return [-1..1]
float randNeg(vec2 co) {
    return rand(co) * 2. - 1.;
}

//point is android coordinates
vec2 toGlPosition(vec2 pointPx) {
    return vec2((pointPx.x / uScreenSizePx.x) * 2.0 - 1.0, (1.0 - (pointPx.y / uScreenSizePx.y)) * 2.0 - 1.0);
}

vec4 toGlBounds(vec4 boundsPx) {
    return vec4(toGlPosition(boundsPx.xy), toGlPosition(boundsPx.zw));
}

float toGlWidth(float sizePx) {
    return (sizePx / uScreenSizePx.x) * 2.0;
}

float toGlHeight(float sizePx) {
    return (sizePx / uScreenSizePx.y) * 2.0;
}

vec2 toGlSize(vec2 pointPx) {
    return vec2(toGlWidth(pointPx.x), toGlHeight(pointPx.y));
}

vec2 toTextureCoord(vec2 glPoint) {
    return vec2((glPoint.x + 1.0) / 2.0, 1.0 - (glPoint.y + 1.0) / 2.0);
}

void main(void) {
    vec2 position = inPosition;
    vec2 textCoord = inTextCoord;
    vec2 targetOffset = inTargetOffset;
    vec2 targetHorizontalOffset = inTargetHorizontalOffset;

    //1008
    int particlesBoundsWidthPx = int(uParticleBoundsPx.z - uParticleBoundsPx.x);
    int particlesBoundsHeightPx = int(uParticleBoundsPx.w - uParticleBoundsPx.y);
    int particlesSquarePx = particlesBoundsWidthPx * particlesBoundsHeightPx;
    int spaceBetweenPoints = particlesSquarePx / uParticlesCount;
    int pointsInRow = particlesBoundsWidthPx / spaceBetweenPoints;
    int columnReminder = particlesBoundsWidthPx - pointsInRow * spaceBetweenPoints;
    int globalXPositionPx = gl_VertexID * spaceBetweenPoints;
    // row
    int localYPx = globalXPositionPx / particlesBoundsWidthPx;
    // column
    int localXPx = (globalXPositionPx % particlesBoundsWidthPx);

    gl_PointSize = uParticleRadius;

    if (uInit > .1) {
        //base distribution
        vec2 glTopLeft = toGlPosition(uParticleBoundsPx.xy);
        vec2 glTopLeftOffset = toGlSize(vec2(localXPx, localYPx));
        //add some horizontal dispersion
        glTopLeftOffset.x = glTopLeftOffset.x + rand(vec2(localXPx, localYPx)) * toGlWidth(float(max(spaceBetweenPoints, 3)));
        position = glTopLeft + vec2(glTopLeftOffset.x, -glTopLeftOffset.y);
        textCoord = vec2(float(localXPx) / float(particlesBoundsWidthPx), 1. - float(localYPx) / float(particlesBoundsHeightPx));
        float targetXOffset = float(localXPx) / float(particlesBoundsWidthPx);
        float extraYOffset = (uMaxOffset - uMinOffset) * (1. - float(localYPx) / float(particlesBoundsHeightPx));
        float yOffsetRand = rand(vec2(float(localXPx) / float(localYPx + 1), - float(localYPx) / float(localXPx + 1)));
        extraYOffset = extraYOffset * yOffsetRand;
        float targetYOffset = uMinOffset + extraYOffset;
        targetOffset = vec2(targetXOffset, targetYOffset);
        float targetHorizontalOffsetRand = randNeg(vec2(-float(localXPx) / float(localYPx + 1), float(localYPx) / float(localXPx + 1)));
        targetHorizontalOffset.x = toGlWidth(uMaxHorizontalOffset) * targetHorizontalOffsetRand;
    }
    // visible | invisible
    float displayProgress = min(1., uProgress * 2.);
    // movind up
    float offsetProgress = min(1., uProgress * 1.) - targetOffset.x / 2.;
    offsetProgress = exp(offsetProgress) - 1.;

    vec4 glParticleBounds = toGlBounds(uParticleBoundsPx);
    float yOffset = toGlHeight(targetOffset.y);
    float xOffset = targetHorizontalOffset.x * offsetProgress;

    float displayBorderX = glParticleBounds.x + (glParticleBounds.z - glParticleBounds.x) * displayProgress;
    vec4 displayPosition = vec4(position.x + xOffset, position.y + yOffset * offsetProgress, 0.0, 1.0);

    outPosition = position;
    outTextCoord = textCoord;
    outTargetOffset = targetOffset;
    outTargetHorizontalOffset = targetHorizontalOffset;

    gl_Position = displayPosition;
    vTexCoord = textCoord;
    if (displayPosition.x < displayBorderX) {
        float alphaProgress = min(1., .5 + 1. - uProgress * 1.5);
        vAlpha = alphaProgress;
    } else {
        vAlpha = 0.;
    }
}