package org.telegram.ui.Components.Paint;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ShaderSet {

    private static final Map<String, Map<String, Object>> AVAILABLE_SHADERS = createMap();

    private static final String VERTEX = "vertex";
    private static final String FRAGMENT = "fragment";
    private static final String ATTRIBUTES = "attributes";
    private static final String UNIFORMS = "uniforms";

    private static final String PAINT_BRUSH_VSH =
        "precision highp float;" +

        "uniform mat4 mvpMatrix;" +
        "attribute vec4 inPosition;" +
        "attribute vec2 inTexcoord;" +
        "attribute float alpha;" +
        "varying vec2 varTexcoord;" +
        "varying float varIntensity;" +

        "void main (void) {" +
        "   gl_Position = mvpMatrix * inPosition;" +
        "   varTexcoord = inTexcoord;" +
        "   varIntensity = alpha;" +
        "}";
    private static final String PAINT_BRUSH_FSH =
        "precision highp float;" +

        "varying vec2 varTexcoord;" +
        "varying float varIntensity;" +
        "uniform sampler2D texture;" +

        "void main (void) {" +
        "   gl_FragColor = vec4(1, 1, 1, varIntensity * texture2D(texture, varTexcoord.st, 0.0).r);" +
        "}";
    private static final String PAINT_BLIT_VSH =
        "precision highp float;" +

        "uniform mat4 mvpMatrix;" +
        "attribute vec4 inPosition;" +
        "attribute vec2 inTexcoord;" +
        "varying vec2 varTexcoord;" +

        "void main (void) {" +
        "   gl_Position = mvpMatrix * inPosition;" +
        "   varTexcoord = inTexcoord;" +
        "}";
    private static final String PAINT_BLIT_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "void main (void) {" +
        "   gl_FragColor = texture2D(texture, varTexcoord.st, 0.0);" +
        "   gl_FragColor.rgb *= gl_FragColor.a;" +
        "}";
    private static final String PAINT_MASKING_BLIT_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D mask;" +
        "uniform float preview;" +
        "void main (void) {" +
        "   gl_FragColor = texture2D(texture, varTexcoord.st, 0.0) * (preview + (1.0 - preview) * texture2D(mask, varTexcoord.st, 0.0).a);" +
        "   gl_FragColor.rgb *= gl_FragColor.a;" +
        "}";
    private static final String PAINT_BLITWITHMASK_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D otexture;" +
        "uniform sampler2D mask;" +
        "uniform vec4 color;" +
        "void main (void) {" +
        "   vec4 dst = texture2D(texture, varTexcoord.st, 0.0);" +
        "   float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;" +
        "   float outAlpha = srcAlpha + dst.a * (1.0 - srcAlpha);" +
        "   gl_FragColor.rgb = (color.rgb * srcAlpha + dst.rgb * dst.a * (1.0 - srcAlpha));" +
        "   gl_FragColor.a = outAlpha;" +
        "}";
    private static final String PAINT_MASKING_BLITWITHMASK_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D otexture;" +
        "uniform sampler2D mask;" +
        "uniform vec4 color;" +
        "uniform float preview;" +
        "void main (void) {" +
        "   vec4 dst = texture2D(texture, varTexcoord.st, 0.0);" +
        "   float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;" +
        "   float outAlpha = srcAlpha + dst.a * (1.0 - srcAlpha);" +
        "   gl_FragColor = texture2D(otexture, varTexcoord.st, 0.0) * (preview + (1.0 - preview) * outAlpha);" +
        "   gl_FragColor.rgb *= gl_FragColor.a;" +
        "}";
    private static final String PAINT_COMPOSITEWITHMASK_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D mask;" +
        "uniform vec4 color;" +
        "void main(void) {" +
        "   vec4 dst = texture2D(texture, varTexcoord.st, 0.0);" +
        "   float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;" +
        "   float outAlpha = srcAlpha + dst.a * (1.0 - srcAlpha);" +
        "   gl_FragColor.rgb = (color.rgb * srcAlpha + dst.rgb * dst.a * (1.0 - srcAlpha)) / outAlpha;" +
        "   gl_FragColor.a = outAlpha;" +
        "}";
    private static final String PAINT_NONPREMULTIPLIEDBLIT_FSH =
        "precision highp float;" +

        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +

        "void main (void) {" +
        "   gl_FragColor = texture2D(texture, varTexcoord.st, 0.0);" +
        "}";

    // neon shaders
    private static final String PAINT_BRUSHLIGHT_FSH =
        "precision highp float;" +

        "varying vec2 varTexcoord;" +
        "varying float varIntensity;" +
        "uniform sampler2D texture;" +

        "void main (void) {" +
        "   vec4 f = texture2D(texture, varTexcoord.st, 0.0);" +
        "   gl_FragColor = vec4(f.r * varIntensity, f.g, f.b, 0.0);" +
        "}";
    private static final String PAINT_BLITWITHMASKLIGHT_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D mask;" +
        "uniform vec4 color;" +
        "void main (void) {" +
        "   vec4 dst = texture2D(texture, varTexcoord.st, 0.0);" +
        "   vec3 maskColor = texture2D(mask, varTexcoord.st, 0.0).rgb;" +
        "   float srcAlpha = clamp(0.78 * maskColor.r + maskColor.b + maskColor.g, 0.0, 1.0);" +
        "   vec3 borderColor = mix(color.rgb, vec3(1.0, 1.0, 1.0), 0.86);" +
        "   vec3 finalColor = mix(color.rgb, borderColor, maskColor.g);" +
        "   finalColor = mix(finalColor.rgb, vec3(1.0, 1.0, 1.0), maskColor.b);" +
        "   float outAlpha = srcAlpha + dst.a * (1.0 - srcAlpha);" +
        "   gl_FragColor.rgb = (finalColor * srcAlpha + dst.rgb * dst.a * (1.0 - srcAlpha)) / outAlpha;" +
        "   gl_FragColor.a = outAlpha;" +
        "   gl_FragColor.rgb *= gl_FragColor.a;" +
        "}";
    private static final String PAINT_COMPOSITEWITHMASKLIGHT_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D mask;" +
        "uniform vec4 color;" +
        "void main(void) {" +
        "   vec4 dst = texture2D(texture, varTexcoord.st, 0.0);" +
        "   vec3 maskColor = texture2D(mask, varTexcoord.st, 0.0).rgb;" +
        "   float srcAlpha = clamp(0.78 * maskColor.r + maskColor.b + maskColor.g, 0.0, 1.0);" +
        "   vec3 borderColor = mix(color.rgb, vec3(1.0, 1.0, 1.0), 0.86);" +
        "   vec3 finalColor = mix(color.rgb, borderColor, maskColor.g);" +
        "   finalColor = mix(finalColor.rgb, vec3(1.0, 1.0, 1.0), maskColor.b);" +
        "   float outAlpha = srcAlpha + dst.a * (1.0 - srcAlpha);" +
        "   gl_FragColor.rgb = (finalColor * srcAlpha + dst.rgb * dst.a * (1.0 - srcAlpha)) / outAlpha;" +
        "   gl_FragColor.a = outAlpha;" +
        "}";

    // eraser
    private static final String PAINT_BLITWITHMASKERASER_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D mask;" +
        "uniform vec4 color;" +
        "void main (void) {" +
        "   vec4 dst = texture2D(texture, varTexcoord.st, 0.0);" +
        "   float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;" +
        "   float outAlpha = dst.a * (1. - srcAlpha);" +
        "   gl_FragColor.rgb = dst.rgb;" +
        "   gl_FragColor.a = outAlpha;" +
        "   gl_FragColor.rgb *= gl_FragColor.a;" +
        "}";
    private static final String PAINT_MASKING_BLITWITHMASKERASER_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D otexture;" +
        "uniform sampler2D mask;" +
        "uniform vec4 color;" +
        "uniform float preview;" +
        "void main (void) {" +
        "   vec4 dst = texture2D(texture, varTexcoord.st, 0.0);" +
        "   float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;" +
        "   float outAlpha = dst.a * (1. - srcAlpha);" +
        "   gl_FragColor = texture2D(otexture, varTexcoord.st, 0.0) * (preview + (1.0 - preview) * outAlpha);" +
        "   gl_FragColor.rgb *= gl_FragColor.a;" +
        "}";
//
//
//        "precision highp float;" +
//                "varying vec2 varTexcoord;" +
//                "uniform sampler2D texture;" +
//                "uniform sampler2D otexture;" +
//                "uniform sampler2D mask;" +
//                "uniform vec4 color;" +
//                "uniform float preview;" +
//                "void main (void) {" +
//                "   vec4 dst = texture2D(texture, varTexcoord.st, 0.0);" +
//                "   float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;" +
//                "   float outAlpha = srcAlpha + dst.a * (1.0 - srcAlpha);" +
//                "   gl_FragColor = texture2D(otexture, varTexcoord.st, 0.0) * (preview + (1.0 - preview) * outAlpha);" +
//                "   gl_FragColor.rgb *= gl_FragColor.a;" +
//                "}";
    private static final String PAINT_COMPOSITEWITHMASKERASER_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D mask;" +
        "uniform vec4 color;" +
        "void main (void) {" +
        "   vec4 dst = texture2D(texture, varTexcoord.st, 0.0);" +
        "   float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;" +
        "   gl_FragColor = vec4(dst.rgb, dst.a * (1.0 - srcAlpha));" +
        "   if (gl_FragColor.a <= 0.) gl_FragColor.rgb = vec3(0.);" +
        "}";

    // blurer
    private static final String PAINT_BLITWITHMASKBLURER_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D mask;" +
        "uniform sampler2D blured;" +
        "uniform vec4 color;" +
        "void main (void) {" +
        "   vec4 dst = texture2D(texture, varTexcoord.st, 0.0);" +
        "   float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;" +
        "   float outAlpha = srcAlpha + dst.a * (1.0 - srcAlpha);" +
        "   vec4 blurColor = texture2D(blured, varTexcoord.st, 0.0);" +
        "   gl_FragColor.rgb = (blurColor.rgb * srcAlpha + dst.rgb * dst.a * (1.0 - srcAlpha)) / outAlpha;" +
        "   gl_FragColor.a = outAlpha;" +
        "   gl_FragColor.rgb *= gl_FragColor.a;" +
        "}";
    private static final String PAINT_COMPOSITEWITHMASKBLURER_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D mask;" +
        "uniform sampler2D blured;" +
        "uniform vec4 color;" +
        "void main (void) {" +
        "   vec4 dst = texture2D(texture, varTexcoord.st, 0.0);" +
        "   float srcAlpha = color.a * texture2D(mask, varTexcoord.st, 0.0).a;" +
        "   float outAlpha = srcAlpha + dst.a * (1.0 - srcAlpha);" +
        "   vec4 blurColor = texture2D(blured, varTexcoord.st, 0.0);" +
        "   gl_FragColor.rgb = (blurColor.rgb * srcAlpha + dst.rgb * dst.a * (1.0 - srcAlpha)) / outAlpha;" +
        "   gl_FragColor.a = outAlpha;" +
        "}";
    private static final String PAINT_VIDEOBLUR_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D blured;" +
        "uniform float eraser;" +
        "uniform float flipy;" +
        "uniform sampler2D mask;" +
        "void main (void) {" +
        "   vec2 uv = vec2(varTexcoord.x, flipy > 0. ? 1. - varTexcoord.y : varTexcoord.y);" +
        "   vec4 dst = texture2D(texture, uv, 0.0);" +
        "   vec4 blurColor = texture2D(blured, uv, 0.0);" +
        "   gl_FragColor = dst.a <= 0. ? vec4(0.) : vec4(blurColor.rgb, 1.) * dst.a;" +
        "   if (eraser > 0.) {" +
        "       vec4 maskColor = texture2D(mask, uv, 0.0);" +
        "       if (maskColor.a > 0.) {" +
        "           gl_FragColor.rgba *= (1. - maskColor.a);" +
        "       }" +
        "   }" +
        "}";

    // shapes
    private static final String PAINT_SHAPE_FSH =
        "precision highp float;" +
        "varying vec2 varTexcoord;" +
        "uniform sampler2D texture;" +
        "uniform sampler2D mask;" +
        "uniform bool composite;" +

        "uniform int type;" +
        "uniform vec4 color;" +
        "uniform vec2 resolution;" +
        "uniform vec2 center;" +
        "uniform vec2 radius;" +
        "uniform float thickness;" +
        "uniform float rounding;" +
        "uniform float rotation;" +
        "uniform float arrowTriangleLength;" +
        "uniform vec2 middle;" +
        "uniform bool fill;" +
        "uniform bool clear;" +

        // iq, i <3 you! https://iquilezles.org/articles/distfunctions2d/
        "float sdTriangle( in vec2 p, in vec2 p0, in vec2 p1, in vec2 p2 ) {" +
        "   vec2 e0 = p1 - p0, e1 = p2 - p1, e2 = p0 - p2, v0 = p - p0, v1 = p - p1, v2 = p - p2;" +
        "   vec2 pq0 = v0 - e0*clamp( dot(v0,e0)/dot(e0,e0), 0.0, 1.0 ), pq1 = v1 - e1*clamp( dot(v1,e1)/dot(e1,e1), 0.0, 1.0 ), pq2 = v2 - e2*clamp( dot(v2,e2)/dot(e2,e2), 0.0, 1.0 );" +
        "   float s = e0.x * e2.y - e0.y * e2.x;" +
        "   vec2 d = min( min( vec2( dot( pq0, pq0 ), s*(v0.x*e0.y-v0.y*e0.x) )," +
        "                      vec2( dot( pq1, pq1 ), s*(v1.x*e1.y-v1.y*e1.x) ))," +
        "                      vec2( dot( pq2, pq2 ), s*(v2.x*e2.y-v2.y*e2.x) ));" +
        "   return -sqrt(d.x) * sign(d.y);" +
        "}" +

        "float sdBezier(vec2 A, vec2 B, vec2 C, vec2 P) {" +
        "    vec2 a=B-A,b=A-B*2.+C,c=a*2.,d=A-P;" +
        "    vec3 k=vec3(3.*dot(a,b),2.*dot(a,a)+dot(d,b),dot(d,a))/dot(b,b);" +
        "    float p=k.y-k.x*k.x/3., p3=p*p*p, q=k.x*(2.*k.x*k.x-9.*k.y)/27.+k.z, D=q*q+4.*p3/27.;" +
        "    if (D >= 0.) {" +
        "        float z=sqrt(D);" +
        "        vec2 x=(vec2(z,-z)-q)/2., uv=sign(x)*pow(abs(x),vec2(1./3.));" +
        "        float r=clamp(uv.x+uv.y-k.x/3.,0.,1.);" +
        "        return length(d+(c+b*r)*r);" +
        "    } else {" +
        "        float v=acos(-sqrt(-27./p3)*q/2.)/3., m=cos(v), n=sin(v)*1.73205;" +
        "        vec3 t=clamp(vec3(m+m,-n-m,n-m)*sqrt(-p/3.)-k.x/3.,0.,1.);" +
        "        return min(min(length(d+(c+b*t.x)*t.x),length(d+(c+b*t.y)*t.y)),length(d+(c+b*t.z)*t.z));" +
        "    }" +
        "}" +

        "vec4 blendOver(vec4 a, vec4 b) {" +
        "    float alpha = b.a + a.a * (1. - b.a);" +
        "    if (alpha <= 0.) return vec4(0.);" +
        "    return vec4((b.rgb * b.a + a.rgb * a.a * (1. - b.a)) / alpha, alpha);" +
        "}" +

        "void main (void) {" +
        "   vec4 dst = clear ? vec4(0.) : texture2D(texture, varTexcoord.st, 0.0);" +
        "   vec2 p = varTexcoord.st * resolution - center;" +
        "   float sdf;" +
        "   vec2 pp = p;" +
        "   p *= mat2(cos(rotation), -sin(rotation), sin(rotation), cos(rotation));" +
        "   if (type == 0) {" + // CIRCLE
        "       sdf = length(p) - min(radius.x, radius.y);" +
        "   } else if (type == 1 || type == 3) {" + // ROUNDED RECT (+ BUBBLE)
        "       vec2 q = abs(p) - abs(radius) + rounding;" +
        "       sdf = min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rounding;" +
        "   } else if (type == 2) {" + // STAR
        "       float n = 5.;" +
        "       float an = 3.141593 / float(n);" +
        "       vec2  acs = vec2(cos(an), sin(an)), ecs = vec2(cos(1.), sin(1.));" +
        "       float bn = mod(atan(p.x, -p.y), 2.0 * an) - an;" +
        "       p = length(p) * vec2(cos(bn), abs(sin(bn)));" +
        "       p -= min(radius.x, radius.y) * acs;" +
        "       p += ecs*clamp( -dot(p, ecs), 0.0, min(radius.x, radius.y) * acs.y / ecs.y);" +
        "       sdf = length(p) * sign(p.x);" +
        "   } else if (type == 4) {" + // ARROW
        "       p += center;" +
        "       sdf = sdBezier(center, middle, radius, p) - thickness;" +
        "       vec2 ba = center - middle;" +
        "       float a = atan(ba.y, ba.x), g = 30. / 180. * 3.14, ar = sin(g) * arrowTriangleLength;" +
        "       vec2 ac = center + vec2(cos(a),sin(a)) * ar / 2.;" +
        "       sdf = min(sdf, max(0., sdTriangle(p, ac, ac+vec2(cos(a+3.14-g), sin(a+3.14-g))*ar, ac+vec2(cos(a+3.14+g), sin(a+3.14+g))*ar)));" +
        "       sdf += thickness;" +
        "   }" +
        "   if (type == 3) {" + // BUBBLE
        "       vec2 c = middle-center;" +
        "       float a = atan(c.x, -c.y), r = min(radius.x, radius.y) / 2.;" +
        "       float k = rounding/2., bsdf = sdTriangle(pp+center, center-vec2(cos(a),sin(a))*r, center-vec2(cos(a-3.14),sin(a-3.14))*r, middle);" +
        "       float h = max(k-abs(sdf-bsdf), 0.)/k;" +
        "       sdf = min(sdf,bsdf)-h*h*h*k*(1.0/6.0);" +
        "   }" +
        "   if (fill && sdf < 0.) {" +
        "       sdf = 0.;" +
        "   }" +
        "   vec4 c = vec4(color.rgb, color.a * (1. - clamp((abs(sdf) - thickness), 0., 2.) / 2.));" +
        "   gl_FragColor = blendOver(dst, c);" +
        "   if (!composite) {" +
        "       gl_FragColor.rgb *= gl_FragColor.a;" +
        "   }" +
        "}";

    private static Map<String, Map<String, Object>> createMap() {
        Map<String, Map<String, Object>> result = new HashMap<>();

        Map<String, Object> shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BRUSH_VSH);
        shader.put(FRAGMENT, PAINT_BRUSH_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord", "alpha"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture"});
        result.put("brush", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_BLIT_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "alpha"});
        result.put("blit", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_MASKING_BLIT_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "mask", "alpha", "preview"});
        result.put("maskingBlit", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_BLITWITHMASK_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "mask", "color"});
        result.put("blitWithMask", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_MASKING_BLITWITHMASK_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "otexture", "texture", "mask", "color", "preview"});
        result.put("blitWithMask_masking", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_COMPOSITEWITHMASK_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "mask", "color"});
        result.put("compositeWithMask", Collections.unmodifiableMap(shader));

        // blurer
        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_BLITWITHMASKBLURER_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "mask", "blured", "color"});
        result.put("blitWithMaskBlurer", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_COMPOSITEWITHMASKBLURER_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "mask", "blured", "color"});
        result.put("compositeWithMaskBlurer", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_VIDEOBLUR_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "blured", "eraser", "mask", "flipy"});
        result.put("videoBlur", Collections.unmodifiableMap(shader));

        // neon
        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BRUSH_VSH);
        shader.put(FRAGMENT, PAINT_BRUSHLIGHT_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord", "alpha"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture"});
        result.put("brushLight", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_BLITWITHMASKLIGHT_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "mask", "color"});
        result.put("blitWithMaskLight", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_COMPOSITEWITHMASKLIGHT_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "mask", "color"});
        result.put("compositeWithMaskLight", Collections.unmodifiableMap(shader));

        // eraser
        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_BLITWITHMASKERASER_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "mask", "color"});
        result.put("blitWithMaskEraser", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_MASKING_BLITWITHMASKERASER_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "otexture", "preview", "mask", "color"});
        result.put("blitWithMaskEraser_masking", Collections.unmodifiableMap(shader));

        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_COMPOSITEWITHMASKERASER_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "mask", "color"});
        result.put("compositeWithMaskEraser", Collections.unmodifiableMap(shader));

        // undo
        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_NONPREMULTIPLIEDBLIT_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture"});
        result.put("nonPremultipliedBlit", Collections.unmodifiableMap(shader));

        // shapes
        shader = new HashMap<>();
        shader.put(VERTEX, PAINT_BLIT_VSH);
        shader.put(FRAGMENT, PAINT_SHAPE_FSH);
        shader.put(ATTRIBUTES, new String[]{"inPosition", "inTexcoord"});
        shader.put(UNIFORMS, new String[]{"mvpMatrix", "texture", "mask", "clear", "color", "type", "color", "resolution", "center", "radius", "thickness", "rounding", "fill", "rotation", "middle", "arrowTriangleLength", "composite"});
        result.put("shape", Collections.unmodifiableMap(shader));

        return Collections.unmodifiableMap(result);
    }

    public static Map<String, Shader> setup() {
        Map<String, Shader> result = new HashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : AVAILABLE_SHADERS.entrySet()) {
            Map<String, Object> value = entry.getValue();

            String vertex = (String) value.get(VERTEX);
            String fragment = (String) value.get(FRAGMENT);
            String[] attributes = (String[]) value.get(ATTRIBUTES);
            String[] uniforms = (String[]) value.get(UNIFORMS);

            Shader shader = new Shader(vertex, fragment, attributes, uniforms);
            result.put(entry.getKey(), shader);
        }

        return Collections.unmodifiableMap(result);
    }
}
