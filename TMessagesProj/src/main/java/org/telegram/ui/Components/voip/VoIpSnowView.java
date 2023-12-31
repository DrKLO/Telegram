package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.SnowflakesEffect;

public class VoIpSnowView extends View {
    private SnowflakesEffect snowflakesEffect;
    private boolean isPaused;

    public VoIpSnowView(Context context) {
        super(context);
    }

    {
        if (LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS) && Theme.getEventType() == 0) {
            snowflakesEffect = new SnowflakesEffect(0);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isPaused) {
            return;
        }
        if (snowflakesEffect != null) {
            snowflakesEffect.onDraw(this, canvas);
        }
    }

    public void setState(boolean isPaused) {
        this.isPaused = isPaused;
        invalidate();
    }
}
