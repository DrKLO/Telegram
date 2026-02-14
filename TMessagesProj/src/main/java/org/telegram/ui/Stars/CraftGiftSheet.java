package org.telegram.ui.Stars;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.BottomSheet;

public class CraftGiftSheet extends BottomSheet {

    private Background background;

    public CraftGiftSheet(Context context) {
        super(context, false);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(background = new Background());

        containerView = container;




    }

    private static final class Background extends Drawable {

        @Override
        public void draw(@NonNull Canvas canvas) {

        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return 0;
        }
    }

}
