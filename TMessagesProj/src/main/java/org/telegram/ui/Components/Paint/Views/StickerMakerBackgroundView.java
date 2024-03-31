package org.telegram.ui.Components.Paint.Views;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

public class StickerMakerBackgroundView extends View {
    private final Paint backgroundPaint = new Paint();
    private final Path path = new Path();

    public StickerMakerBackgroundView(Context context) {
        super(context);
        backgroundPaint.setColor(Color.WHITE);
        backgroundPaint.setAlpha(40);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = dp(10);
        float width = getMeasuredWidth() - inset * 2;
        float height = getMeasuredHeight() - inset * 2;

        canvas.save();
        AndroidUtilities.rectTmp.set(inset, inset, inset + width, inset + width);
        AndroidUtilities.rectTmp.offset(0, (height - AndroidUtilities.rectTmp.height()) / 2);
        float rx = width / 7f;
        path.rewind();
        path.addRoundRect(AndroidUtilities.rectTmp, rx, rx, Path.Direction.CW);
        canvas.clipPath(path);

        int singleRectSize = dp(10);
        canvas.save();
        canvas.translate(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top);

        int rowCount = (int) (AndroidUtilities.rectTmp.width() / singleRectSize) + 1;
        int columCount = (int) (AndroidUtilities.rectTmp.height() / singleRectSize) + 1;

        for (int c = 0; c < columCount; c++) {
            canvas.save();
            for (int r = 0; r < rowCount; r++) {
                if ((r % 2 == 0 && c % 2 == 0) || (r % 2 != 0 && c % 2 != 0)) {
                    canvas.drawRect(0, 0, singleRectSize, singleRectSize, backgroundPaint);
                }
                canvas.translate(singleRectSize, 0);
            }
            canvas.restore();
            canvas.translate(0, singleRectSize);
        }
        canvas.restore();
        canvas.restore();
    }
}
