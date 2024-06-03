package org.telegram.ui.ActionBar;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.Utilities;

// https://gist.github.com/dkaraush/65d19d61396f5f3cd8ba7d1b4b3c9432
public class OKLCH {

    public static final double[] XYZtoLMS_M = new double[] {
        0.8190224379967030, 0.3619062600528904, -0.1288737815209879,
        0.0329836539323885, 0.9292868615863434, 0.0361446663506424,
        0.0481771893596242, 0.2642395317527308, 0.6335478284694309
    };
    public static final double[] LMStoXYZ_M = new double[] {
        1.2268798758459243, -0.5578149944602171,  0.2813910456659647,
        -0.0405757452148008,  1.1122868032803170, -0.0717110580655164,
        -0.0763729366746601, -0.4214933324022432,  1.5869240198367816
    };
    public static final double[] LMStoLab_M = new double[] {
        0.2104542683093140,  0.7936177747023054, -0.0040720430116193,
        1.9779985324311684, -2.4285922420485799,  0.4505937096174110,
        0.0259040424655478,  0.7827717124575296, -0.8086757549230774
    };
    public static final double[] LabtoLMS_M = new double[] {
        1.,  0.3963377773761749,  0.2158037573099136,
        1., -0.1055613458156586, -0.0638541728258133,
        1., -0.0894841775298119, -1.2914855480194092,
    };
    public static final double[] toXYZ_M = new double[] {
        0.41239079926595934, 0.357584339383878,   0.1804807884018343,
        0.21263900587151027, 0.715168678767756,   0.07219231536073371,
        0.01933081871559182, 0.11919477979462598, 0.9505321522496607
    };
    public static final double[] fromXYZ_M = new double[] {
        3.2409699419045226,  -1.537383177570094,   -0.4986107602930034,
        -0.9692436362808796,   1.8759675015077202,   0.04155505740717559,
        0.05563007969699366, -0.20397695888897652,  1.0569715142428786
    };

    public static double[] oklch2oklab(double[] lch) {
        final double L = lch[0];
        final double C = lch[1];
        final double H = lch[2];
        return new double[] {
            L,
            Double.isNaN(H) ? 0 : C * Math.cos(H * Math.PI / 180),
            Double.isNaN(H) ? 0 : C * Math.sin(H * Math.PI / 180)
        };
    }
    public static double[] oklab2oklch(double[] lab) {
        final double L = lab[0];
        final double A = lab[1];
        final double B = lab[2];
        return new double[] {
            L,
            Math.sqrt(Math.pow(A, 2) + Math.pow(B, 2)),
            Math.abs(A) < 0.0002 && Math.abs(B) < 0.0002 ? Double.NaN : (((Math.atan2(B, A) * 180) / Math.PI % 360) + 360) % 360
        };
    }

    public static double[] rgb2srgbLinear(double[] rgb) {
        double[] rgbLinear = new double[3];
        for (int i = 0; i < rgb.length; ++i) {
            rgbLinear[i] = Math.abs(rgb[i]) <= 0.04045 ?
                rgb[i] / 12.92 :
                (rgb[i] < 0 ? -1 : 1) * (Math.pow((Math.abs(rgb[i]) + 0.055) / 1.055, 2.4));
        }
        return rgbLinear;
    }
    public static double[] srgbLinear2rgb(double[] rgbLinear) {
        double[] rgb = new double[3];
        for (int i = 0; i < rgbLinear.length; ++i) {
            rgb[i] = Math.abs(rgbLinear[i]) > 0.0031308 ?
                (rgbLinear[i] < 0 ? -1 : 1) * (1.055 * Math.pow(Math.abs(rgbLinear[i]), (1 / 2.4)) - 0.055) :
                12.92 * rgbLinear[i];
        }
        return rgb;
    }

    public static double[] oklab2xyz(double[] lab) {
        final double[] LMS = multiply(LabtoLMS_M, lab);
        for (int i = 0; i < LMS.length; ++i) {
            LMS[i] = Math.pow(LMS[i], 3.0);
        }
        return multiply(LMStoXYZ_M, LMS);
    }
    public static double[] xyz2oklab(double[] xyz) {
        final double[] LMSg = multiply(XYZtoLMS_M, xyz);
        for (int i = 0; i < LMSg.length; ++i) {
            LMSg[i] = Math.cbrt(LMSg[i]);
        }
        return multiply(LMStoLab_M, LMSg);
    }

    public static double[] xyz2rgbLinear(double[] xyz) {
        return multiply(fromXYZ_M, xyz);
    }
    public static double[] rgbLinear2xyz(double[] rgb) {
        return multiply(toXYZ_M, rgb);
    }

    public static double[] oklch2rgb(double[] lch) {
        return xyz2rgbLinear(oklab2xyz(oklch2oklab(lch)));
    }

    public static double[] rgb2oklch(double[] rgb) {
        return oklab2oklch(xyz2oklab(rgbLinear2xyz(rgb)));
    }

    public static double[] rgb(int color) {
        return new double[] {
            Color.red(color) /   255.0,
            Color.green(color) / 255.0,
            Color.blue(color) /  255.0
        };
    }
    public static int rgb(double[] color) {
        return Color.rgb(
            (int) Math.round(Utilities.clamp(color[0], 1.0, 0.0) * 255.0),
            (int) Math.round(Utilities.clamp(color[1], 1.0, 0.0) * 255.0),
            (int) Math.round(Utilities.clamp(color[2], 1.0, 0.0) * 255.0)
        );
    }

    // takes hue from `hueColor` color and applies to `baseColor`
    public static int adapt(int baseColor, int hueColor) {
        double[] hueoklch = rgb2oklch(rgb(hueColor));
        double[] oklch = rgb2oklch(rgb(baseColor));
        oklch[2] = hueoklch[2];
        if (Double.isNaN(hueoklch[2]) || hueoklch[1] < .08f) {
            oklch[1] = hueoklch[1];
            if (!Theme.isCurrentThemeDark() && oklch[0] < .8f) {
                oklch[0] = Utilities.clamp(oklch[0] - .1, 1, 0);
            }
        }
        return ColorUtils.setAlphaComponent(rgb(oklch2rgb(oklch)), Color.alpha(baseColor));
    }

    public static int adapt(int color, double c, double l) {
        double[] oklch = rgb2oklch(rgb(color));
        oklch[0] = Utilities.clamp(oklch[0] + l, 1.0, 0.0);
        oklch[1] = Utilities.clamp(oklch[1] + c, 1.0, 0.0);
        return ColorUtils.setAlphaComponent(rgb(oklch2rgb(oklch)), Color.alpha(color));
    }

    private static double[] multiply(double[] mat3, double[] vec3) {
        return new double[] {
            mat3[0] * vec3[0] + mat3[1] * vec3[1] + mat3[2] * vec3[2],
            mat3[3] * vec3[0] + mat3[4] * vec3[1] + mat3[5] * vec3[2],
            mat3[6] * vec3[0] + mat3[7] * vec3[1] + mat3[8] * vec3[2]
        };
    }

}
