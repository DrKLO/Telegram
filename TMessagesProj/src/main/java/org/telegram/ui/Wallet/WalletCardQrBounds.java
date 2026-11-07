package org.telegram.ui.Wallet;

/** Conservative scissor for the filtered QR mask, without changing its geometry or UVs. */
final class WalletCardQrBounds {
    private static final double CARD_HEIGHT = 1.219512;

    static void compute(float[] m, int width, int height,
                        float left, float top, float right, float bottom, int[] result, float[] uvFootprint) {
        compute(m, width, height, left, top, right, bottom, 1024, 625, false, result, uvFootprint);
    }

    static void compute(float[] m, int width, int height,
                        float left, float top, float right, float bottom,
                        int textureWidth, int textureHeight, boolean mipmapped,
                        int[] result, float[] uvFootprint) {
        uvFootprint[0] = uvFootprint[1] = Float.POSITIVE_INFINITY;
        result[0] = result[1] = 0;
        result[2] = width;
        result[3] = height;
        if (width <= 0 || height <= 0) return;

        // Homography from the shader's card UV to clip X, Y, W on the visible
        // negative-X face. Do not use layout/design Y: the shader's mapping is
        // slightly different from the exported mesh bounds.
        double x = -WalletCardGeometry.HALF_THICKNESS;
        double a = 2.0 * m[8], b = -CARD_HEIGHT * m[4];
        double c = x * m[0] + CARD_HEIGHT * 0.5 * m[4] - m[8] + m[12];
        double d = 2.0 * m[9], e = -CARD_HEIGHT * m[5];
        double f = x * m[1] + CARD_HEIGHT * 0.5 * m[5] - m[9] + m[13];
        double g = 2.0 * m[11], h = -CARD_HEIGHT * m[7];
        double i = x * m[3] + CARD_HEIGHT * 0.5 * m[7] - m[11] + m[15];
        double determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        if (Math.abs(determinant) < 1e-12) return;

        // Inverse: UV = (A*screenX + B*screenY + C) / denominator.
        double ua = (e * i - f * h) / determinant;
        double ub = (c * h - b * i) / determinant;
        double uc = (b * f - c * e) / determinant;
        double va = (f * g - d * i) / determinant;
        double vb = (a * i - c * g) / determinant;
        double vc = (c * d - a * f) / determinant;
        double da = (d * h - e * g) / determinant;
        double db = (b * g - a * h) / determinant;
        double dc = (a * e - b * d) / determinant;

        // Bound derivatives over the entire viewport plus helper fragments.
        // A grazing/singular projection falls back to the full front mesh.
        double sx = 1.0 + 4.0 / width, sy = 1.0 + 4.0 / height;
        double denominator = dc - Math.abs(da) * sx - Math.abs(db) * sy;
        if (denominator <= 1e-9) return;
        double denominatorSquared = denominator * denominator;
        double du = ((Math.abs(ua * db - da * ub) * sy + Math.abs(ua * dc - da * uc)) * 2.0 / width
                + (Math.abs(ub * da - db * ua) * sx + Math.abs(ub * dc - db * uc)) * 2.0 / height)
                / denominatorSquared;
        double dv = ((Math.abs(va * db - da * vb) * sy + Math.abs(va * dc - da * vc)) * 2.0 / width
                + (Math.abs(vb * da - db * va) * sx + Math.abs(vb * dc - db * vc)) * 2.0 / height)
                / denominatorSquared;
        uvFootprint[0] = (float) du;
        uvFootprint[1] = (float) dv;
        // Two source texels cover mask rasterization and bilinear support.
        // Double the derivative bound as a guard for floating-point rounding.
        double guardU = 2.0 / textureWidth + Math.max(1.0 / textureWidth, du * 1.5);
        double guardV = 2.0 / textureHeight + Math.max(1.0 / textureHeight, dv * 1.5);
        if (mipmapped) {
            // Isotropic mip selection uses the larger texel-space derivative
            // for BOTH axes. Include the coarser trilinear level's box/bilinear
            // support as well as the shader's explicit diagonal filter taps.
            double footprint = Math.max(1.0, Math.max(du * textureWidth, dv * textureHeight));
            guardU += 4.0 * footprint / textureWidth;
            guardV += 4.0 * footprint / textureHeight;
        }
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            double u = (corner & 1) == 0 ? left - guardU : right + guardU;
            double v = (corner & 2) == 0 ? top - guardV : bottom + guardV;
            double w = g * u + h * v + i;
            if (w <= 1e-9) return;
            double px = ((a * u + b * v + c) / w * 0.5 + 0.5) * width;
            double py = ((d * u + e * v + f) / w * 0.5 + 0.5) * height;
            if (!Double.isFinite(px) || !Double.isFinite(py)) return;
            minX = Math.min(minX, px);
            maxX = Math.max(maxX, px);
            minY = Math.min(minY, py);
            maxY = Math.max(maxY, py);
        }
        int x0 = (int) Math.max(0, Math.min(width, Math.floor(minX) - 2));
        int y0 = (int) Math.max(0, Math.min(height, Math.floor(minY) - 2));
        int x1 = (int) Math.max(0, Math.min(width, Math.ceil(maxX) + 2));
        int y1 = (int) Math.max(0, Math.min(height, Math.ceil(maxY) + 2));
        result[0] = x0;
        result[1] = y0;
        result[2] = Math.max(0, x1 - x0);
        result[3] = Math.max(0, y1 - y0);
    }
}
