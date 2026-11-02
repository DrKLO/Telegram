package org.telegram.ui.Wallet;

import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.OKLCH;

/** Direct Telegram backdrop material colors and UI transition helpers. */
final class WalletGiftPalette {
    static float[] of(TL_stars.starGiftAttributeBackdrop backdrop) {
        if (backdrop == null) return new float[]{0, 1, 1};
        float[] gift = lch(ColorUtils.blendARGB(backdrop.center_color | 0xff000000,
                backdrop.edge_color | 0xff000000, .5f));
        float[] stock = lch(0xFF088FEE);
        float degrees = (float) ((Math.toDegrees(gift[2]) + 360) % 360);
        float violet = (float) Math.exp(-Math.pow((degrees - 300) / 38, 2));
        float chroma = Math.max(.15f, Math.min(1, gift[1] * 1.5f / .155f)) * (1 + .18f * violet);
        chroma = Math.min(chroma, lch(backdrop.center_color)[1] * 1.2f / .173f);
        float darkness = Math.max(0, Math.min(1, (.6f - gift[0]) / .15f));
        float lightness = 1 + (Math.max(.1f, gift[0] - .02f) / stock[0] - 1) * darkness;
        return new float[]{gift[2] - stock[2], chroma, lightness - .025f * violet};
    }

    static float[] lch(int color) {
        double[] lch=OKLCH.rgb2oklch(OKLCH.rgb2srgbLinear(OKLCH.rgb(color)));
        return new float[]{(float)lch[0],(float)lch[1],Double.isNaN(lch[2])?0:(float)Math.toRadians(lch[2])};
    }

    private static double[] gamut(double[] lch, double peak) {
        double[] rgb=null;
        for(int i=0;i<40;i++) {
            rgb=OKLCH.oklch2rgb(lch);
            if(rgb[0]>=0&&rgb[1]>=0&&rgb[2]>=0&&rgb[0]<=peak&&rgb[1]<=peak&&rgb[2]<=peak)break;
            lch[1]*=.95;
        }
        return rgb;
    }

    static int tint(int color, float[] style, int offset) {
        int paletteOffset=10+(offset/3)*4;
        if(style.length>=18 && style[paletteOffset]>=0) {
            int rgb=color&0xffffff;
            int role=rgb==0x0285D4?2:(rgb==0x0071CC||rgb==0x088FEE)?1:(rgb==0x229AF0||rgb==0x16ABF9)?0:3;
            return (Color.alpha(color)<<24)|((int)style[paletteOffset+role]&0xffffff);
        }
        if(style[offset]==0&&style[offset+1]==1&&style[offset+2]==1)return color;
        double[] lch=OKLCH.rgb2oklch(OKLCH.rgb2srgbLinear(OKLCH.rgb(color)));
        lch[0]=Math.max(lch[0]>=.8?.8:0,lch[0]*style[offset+2]);
        lch[1]*=style[offset+1];
        lch[2]=(Double.isNaN(lch[2])?0:lch[2])+Math.toDegrees(style[offset]);
        return ColorUtils.setAlphaComponent(OKLCH.rgb(OKLCH.srgbLinear2rgb(gamut(lch,1))),Color.alpha(color));
    }

    /** Card surface tone for a backdrop, as in GramWalletCards3 paletteFor: stock lightness/chroma, gift hue. */
    /** A stock-blue UI color moved into the card's gift palette, following the reveal; stock when no gift. */
    static int cardTone(int stock, float[] style) {
        if(style==null || style.length<18) return stock;
        int[] sides=new int[2];
        for(int side=0;side<2;side++) {
            int offset=10+side*4;
            sides[side]=style[offset]<0 ? stock : surface(stock,(int)style[offset],(int)style[offset+1]);
        }
        return ColorUtils.blendARGB(sides[0],sides[1],style[8]);
    }

    static float lightness(int color) {
        return oklch(color)[0];
    }

    /** Keeps a color's OKLCH lightness within [min, max], preserving hue and chroma. */
    static int clampLightness(int color, float min, float max) {
        float[] c=oklch(color);
        if(c[0]>=min && c[0]<=max) return color;
        return (color&0xff000000)|(oklchToArgb(Math.max(min,Math.min(max,c[0])),c[1],c[2])&0xffffff);
    }

    static int surface(int stock, int center, int edge) {
        center|=0xff000000;edge|=0xff000000;
        float[] gift=oklch(ColorUtils.blendARGB(center,edge,.5f));
        float hue=(float)((Math.toDegrees(gift[2])+360)%360);
        float violet=(float)Math.exp(-Math.pow((hue-300)/38,2));
        float chroma=Math.max(.15f,Math.min(1,gift[1]*1.5f/.155f))*(1+.18f*violet);
        float lShift=-.025f*violet;
        float[] giftC=oklch(center),giftE=oklch(edge);
        float darkness=Math.max(0,Math.min(1,(.6f-(giftC[0]+giftE[0])/2)/.15f));
        float stockDarkL=oklch(0xFF088FEE)[0],stockLightL=oklch(0xFF16ABF9)[0];
        float[] ref=oklch(stock);
        float pos=Math.max(-2,Math.min(3,(ref[0]-stockDarkL)/(stockLightL-stockDarkL)));
        float own=giftE[0]-.02f+pos*(giftC[0]-giftE[0]);
        float l=ref[0]+(own-ref[0])*darkness+lShift;
        float cap=giftC[1]*1.2f*(ref[1]/.173f);
        float c=Math.min(ref[1]*chroma,cap)*(1-darkness)+giftC[1]*1.1f*darkness;
        return oklchToArgb(Math.max(.05f,Math.min(.97f,l)),c,gift[2]);
    }

    private static final android.util.LruCache<String, Integer> EDGE_2D_COLORS = new android.util.LruCache<>(128);

    /** Canvas study baseTones(), kept separate from the original GL palette calibration. */
    static int card2DEdgeTone(int stock, float[] style) {
        int fallback = stock == 0xff088fee ? 0xff2368d5 : 0xff278ff7;
        if (style == null || style.length < 18) return fallback;
        int first = style[10] < 0 ? fallback : surface2DEdge(stock, (int)style[10], (int)style[11]);
        int second = style[14] < 0 ? fallback : surface2DEdge(stock, (int)style[14], (int)style[15]);
        return ColorUtils.blendARGB(first, second, style[8]);
    }

    static int surface2DEdge(int stock, int center, int edge) {
        String key = stock + ":" + center + ":" + edge;
        Integer cached = EDGE_2D_COLORS.get(key);
        if (cached != null) return cached;
        center |= 0xff000000; edge |= 0xff000000;
        float[] gift = oklch(ColorUtils.blendARGB(center, edge, .7f));
        float[] gc = oklch(center), ge = oklch(edge), ref = oklch(stock);
        float hue = (float)((Math.toDegrees(gift[2]) + 360) % 360);
        float violet = (float)Math.exp(-Math.pow((hue - 300) / 38, 2));
        float chroma = Math.max(.15f, Math.min(1, gift[1] * 1.5f / .155f)) * (1 + .18f * violet);
        float darkness = Math.max(0, Math.min(1, (.6f - (gc[0] + ge[0]) / 2) / .15f));
        float sd = oklch(0xff088fee)[0], sl = oklch(0xff16abf9)[0];
        float pos = Math.max(-2, Math.min(3, (ref[0] - sd) / (sl - sd)));
        float own = ge[0] - .02f + pos * (gc[0] - ge[0]);
        float l = ref[0] + (own - ref[0]) * darkness - .025f * violet;
        float c = Math.min(ref[1] * chroma, gc[1] * 1.2f * (ref[1] / .173f)) * (1 - darkness) + gc[1] * 1.1f * darkness;
        float[] mine = stock == 0xff16abf9 ? oklch(ColorUtils.blendARGB(center, edge, .5f)) : ge;
        l += (mine[0] - l) * .399f; c += (mine[1] - c) * .399f;
        float h = gift[2] + (float)Math.atan2(Math.sin(mine[2] - gift[2]), Math.cos(mine[2] - gift[2])) * .399f;
        float pure = Math.max(0, Math.min(1, (gift[1] - .06f) / .055f)) * (1 - Math.max(0, Math.min(1, (gift[1] - .125f) / .015f)));
        float warm = (float)Math.exp(-Math.pow((((Math.toDegrees(h) % 360 + 360) % 360) - 70) / 35, 2));
        c *= 1 + .35f * pure * (1 - .75f * warm);
        float cap = Math.max(gc[1], ge[1]) * 1.3f * (1 - .15f * warm);
        if (c > cap && cap > 0) c += (cap - c) * .8f;
        int result = oklchToArgb(Math.max(.05f, Math.min(.97f, l - .025f * warm)), c, h);
        EDGE_2D_COLORS.put(key, result);
        return result;
    }

    private static float lin(float v) {
        return v<=.04045f?v/12.92f:(float)Math.pow((v+.055f)/1.055f,2.4);
    }

    private static float[] oklch(int argb) {
        float r=lin(Color.red(argb)/255f),g=lin(Color.green(argb)/255f),b=lin(Color.blue(argb)/255f);
        float l=(float)Math.cbrt(.4122214708f*r+.5363325363f*g+.0514459929f*b);
        float m=(float)Math.cbrt(.2119034982f*r+.6806995451f*g+.1073969566f*b);
        float s=(float)Math.cbrt(.0883024619f*r+.2817188376f*g+.6299787005f*b);
        float L=.2104542553f*l+.7936177850f*m-.0040720468f*s;
        float A=1.9779984951f*l-2.4285922050f*m+.4505937099f*s;
        float B=.0259040371f*l+.7827717662f*m-.8086757660f*s;
        return new float[]{L,(float)Math.hypot(A,B),(float)Math.atan2(B,A)};
    }

    private static float[] oklchRgb(float l, float c, float h) {
        float a=c*(float)Math.cos(h),b=c*(float)Math.sin(h);
        float l1=l+.3963377774f*a+.2158037573f*b,m1=l-.1055613458f*a-.0638541728f*b,s1=l-.0894841775f*a-1.2914855480f*b;
        float l3=l1*l1*l1,m3=m1*m1*m1,s3=s1*s1*s1;
        return new float[]{4.0767416621f*l3-3.3077115913f*m3+.2309699292f*s3,
                -1.2684380046f*l3+2.6097574011f*m3-.3413193965f*s3,
                -.0041960863f*l3-.7034186147f*m3+1.7076147010f*s3};
    }

    private static int oklchToArgb(float l, float c, float h) {
        float[] rgb=oklchRgb(l,c,h);
        for(int guard=0;guard<40&&outOfGamut(rgb);guard++){c*=.95f;rgb=oklchRgb(l,c,h);}
        return Color.rgb(enc(rgb[0]),enc(rgb[1]),enc(rgb[2]));
    }

    private static boolean outOfGamut(float[] rgb) {
        for(float v:rgb)if(v<-.001f||v>1.001f)return true;
        return false;
    }

    private static int enc(float v) {
        float x=Math.max(0,Math.min(1,v));
        float e=x<=.0031308f?12.92f*x:1.055f*(float)Math.pow(x,1/2.4)-.055f;
        return Math.max(0,Math.min(255,(int)(e*255+.5f)));
    }

    private static final float[] BLUE_MATERIAL = {0.017513231635093687f,0.28449588418006894f,1.2347647094726564f,0.012535676658153534f,0.21663006767630577f,0.8356013679504395f,0.026820781230926512f,0.400800558924675f,2.404087600708008f,0.003239999860525131f,0.1710000067949295f,0.4703999853134156f};

    /** Append exact Telegram colors while preserving the UI transition contract. */
    static void putBackdrop(float[] style, int side, TL_stars.starGiftAttributeBackdrop backdrop) {
        int i=10+side*4;
        style[i]=backdrop==null ? -1 : backdrop.center_color & 0xffffff;
        style[i+1]=backdrop==null ? -1 : backdrop.edge_color & 0xffffff;
        style[i+2]=backdrop==null ? -1 : backdrop.pattern_color & 0xffffff;
        style[i+3]=backdrop==null ? -1 : backdrop.text_color & 0xffffff;
    }

    /** Gift material gain; wallet_card_rim_fragment.glsl divides it back out. */
    private static final float GIFT_GAIN = .73f;

    private static float linear(int color, int shift) {
        float v=((color >> shift)&255)/255f;
        return v<=.04045f?v/12.92f:(float)Math.pow((v+.055f)/1.055f,2.4);
    }

    /** Tint direction of the diamond artwork's blue layer (sRGB, peak 1). */
    private static final float[] DIAMOND_TINT = {0f, .47809f, 1f};

    /** Center color's normalized chroma; fades to an equally light gray for neutral palettes. */
    private static void diamondTint(int color, float[] out, int offset) {
        float r=((color>>16)&255)/255f,g=((color>>8)&255)/255f,b=(color&255)/255f;
        float min=Math.min(r,Math.min(g,b)),chroma=Math.max(r,Math.max(g,b))-min;
        float gray=.2126f*DIAMOND_TINT[0]+.7152f*DIAMOND_TINT[1]+.0722f*DIAMOND_TINT[2];
        float k=Math.min(1f,chroma/.15f);
        out[offset]=gray+((chroma>0?(r-min)/chroma:0)-gray)*k;
        out[offset+1]=gray+((chroma>0?(g-min)/chroma:0)-gray)*k;
        out[offset+2]=gray+((chroma>0?(b-min)/chroma:0)-gray)*k;
    }

    private static final android.util.LruCache<Integer, Integer> CARD_2D_INKS = new android.util.LruCache<>(64);
    private static final int STOCK_2D_INK = makeStock2DInk();

    /** Canvas study addressInk(): preserve the stock ink's contrast against each face palette. */
    static int cardNumber2DColor(float[] style) {
        return ColorUtils.blendARGB(cardNumber2DColor(style, 0), cardNumber2DColor(style, 1),
                style == null || style.length < 18 ? 0 : style[8]);
    }

    private static int cardNumber2DColor(float[] style, int side) {
        int offset = 10 + side * 4;
        if (style == null || style.length < 18 || style[offset] < 0) return STOCK_2D_INK;
        int face = surface(0xff088fee, (int) style[offset], (int) style[offset + 1]);
        Integer cached = CARD_2D_INKS.get(face);
        if (cached != null) return cached;
        float[] sc = oklch(face);
        float ratio = (inkLuminance(0xff088fee) + .05f) / (inkLuminance(STOCK_2D_INK) + .05f);
        float ys = inkLuminance(face), target = (ys + .05f) / ratio - .05f;
        if (target < .004f) target = ys * .3f;
        float vivid = Math.max(0, Math.min(1, sc[1] / .10f)), lo = 0, hi = 1;
        for (int i = 0; i < 18; i++) {
            float lightness = (lo + hi) / 2;
            int color = oklchToArgb(lightness, inkMaxChroma(lightness, sc[2]) * vivid, sc[2]);
            if (inkLuminance(color) < target) lo = lightness; else hi = lightness;
        }
        float lightness = (lo + hi) / 2;
        int color = oklchToArgb(lightness, inkMaxChroma(lightness, sc[2]) * vivid, sc[2]);
        CARD_2D_INKS.put(face, color);
        return color;
    }

    private static int makeStock2DInk() {
        float[] base = oklch(0xff0071cc);
        float lightness = base[0] * (1 + (.443f - 1) * .221f), hue = base[2];
        float chroma = inkMaxChroma(lightness, hue), bestHue = hue;
        for (int degrees = -15; degrees <= 15; degrees++) {
            float h = hue + (float) Math.toRadians(degrees), c = inkMaxChroma(lightness, h);
            if (c > chroma + .002f) { chroma = c; bestHue = h; }
        }
        hue += (bestHue - hue) * .723f;
        return oklchToArgb(lightness, inkMaxChroma(lightness, hue), hue);
    }

    private static float inkMaxChroma(float l, float h) {
        float lo = 0, hi = .5f;
        for (int i = 0; i < 18; i++) {
            float c = (lo + hi) / 2;
            float[] rgb = oklchRgb(l, c, h);
            if (rgb[0] < 0 || rgb[1] < 0 || rgb[2] < 0 || rgb[0] > 1 || rgb[1] > 1 || rgb[2] > 1) hi = c;
            else lo = c;
        }
        return lo;
    }

    private static float inkLuminance(int color) {
        return .2126f * linear(color, 16) + .7152f * linear(color, 8) + .0722f * linear(color, 0);
    }

    /** Card number ink: pattern color lerped toward edge color (tuned in wallet-backdrop-lab). */
    private static final float CARD_NUMBER_EDGE_MIX = .2f;

    static int cardNumberColor(float[] style, int side) {
        int offset=10+side*4;
        if(style==null || style.length<18 || style[offset]<0)
            return ColorUtils.blendARGB(0xFF0285D4, 0xFF0071CC, CARD_NUMBER_EDGE_MIX);
        return cardNumberColor((int)style[offset+2], (int)style[offset+1]);
    }

    static int cardNumberColor(int pattern, int edge) {
        return ColorUtils.blendARGB(0xFF000000|pattern, 0xFF000000|edge, CARD_NUMBER_EDGE_MIX);
    }

    /** Current card number ink, following the gift reveal like tint(). */
    static int cardNumberColor(float[] style) {
        if(style==null || style.length<18) return cardNumberColor(null, 0);
        return ColorUtils.blendARGB(cardNumberColor(style, 0), cardNumberColor(style, 1), style[8]);
    }

    /** 0-23 palettes, 24-27 reveal, 28-29 pre-tone-map scale, 30-35 diamond tints, 36-41 card number ink (sRGB). */
    static float[] material(float[] style) {
        float[] result=new float[42];
        for(int side=0;side<2;side++) {
            int offset=10+side*4;
            if(style.length<18 || style[offset]<0) {
                System.arraycopy(BLUE_MATERIAL,0,result,side*12,12);
                result[28+side]=1f;
                System.arraycopy(DIAMOND_TINT,0,result,30+side*3,3);
            } else {
                result[28+side]=.6f;
                diamondTint((int)style[offset],result,30+side*3);
                // GramWalletCards3 surface tones: card color is their midpoint, dark/grazing the
                // dark tone, bright the light one. The shader's gift look is calibrated to GIFT_GAIN.
                int center=(int)style[offset],edge=(int)style[offset+1];
                int dark=surface(0xFF088FEE,center,edge),light=surface(0xFF16ABF9,center,edge);
                for(int channel=0;channel<3;channel++) {
                    float d=linear(dark,16-channel*8)*GIFT_GAIN,l=linear(light,16-channel*8)*GIFT_GAIN;
                    result[side*12+channel]=(d+l)/2;
                    result[side*12+3+channel]=d;
                    result[side*12+6+channel]=l;
                    result[side*12+9+channel]=d;
                }
            }
        }
        for(int side=0;side<2;side++) {
            int ink=cardNumberColor(style,side);
            result[36+side*3]=((ink>>16)&255)/255f;
            result[37+side*3]=((ink>>8)&255)/255f;
            result[38+side*3]=(ink&255)/255f;
        }
        System.arraycopy(style,6,result,24,4);
        return result;
    }

    static int tint(int color, float[] style) {
        return ColorUtils.blendARGB(tint(color, style, 0), tint(color, style, 3), style[8]);
    }
}
