package org.telegram.ui.Components.voip;

import org.telegram.messenger.LiteMode;
import org.telegram.ui.Components.BlobDrawable;

public class VoipBlobDrawable extends BlobDrawable {

    public VoipBlobDrawable(int n) {
        super(n);
    }

    public VoipBlobDrawable(int n, int liteFlag) {
        super(n, liteFlag);
    }

    protected void generateBlob(float[] radius, float[] angle, int i, float muteToStaticProgress) {
        float angleDif = 360f / N * 0.05f;
        float radDif = maxRadius - minRadius;
        radius[i] = minRadius + ((Math.abs(((random.nextInt() % 100f) / 100f)) * radDif) * muteToStaticProgress);
        angle[i] = 360f / N * i + (((random.nextInt() * muteToStaticProgress) % 100f) / 100f) * angleDif;
        speed[i] = (float) (0.017 + 0.003 * (Math.abs(random.nextInt() % 100f) / 100f));
    }

    public void update(float amplitude, float speedScale, float muteToStaticProgress) {
        if (!LiteMode.isEnabled(liteFlag)) {
            return;
        }
        for (int i = 0; i < N; i++) {
            progress[i] += (speed[i] * MIN_SPEED) + amplitude * speed[i] * MAX_SPEED * speedScale;
            if (progress[i] >= 1f) {
                progress[i] = 0;
                radius[i] = radiusNext[i];
                angle[i] = angleNext[i];
                if (muteToStaticProgress < 1f) {
                    generateBlob(radiusNext, angleNext, i, muteToStaticProgress);
                } else {
                    generateBlob(radiusNext, angleNext, i);
                }
            }
        }
    }
}
