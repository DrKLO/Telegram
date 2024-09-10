package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

public class DatabaseMigrationHint extends FrameLayout {

    LinearLayout container;
    RLottieImageView stickerView;
    TextView title;
    TextView description1;
    TextView description2;

    private final int currentAccount;

    public DatabaseMigrationHint(Context context, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        stickerView = new RLottieImageView(context);
        stickerView.setAnimation(R.raw.db_migration_placeholder, 150, 150);
        stickerView.getAnimatedDrawable().setAutoRepeat(1);
        stickerView.playAnimation();
        container.addView(stickerView, LayoutHelper.createLinear(150, 150, Gravity.CENTER_HORIZONTAL));

        title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        title.setText(LocaleController.getString(R.string.OptimizingTelegram));
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        container.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 50, 32, 50, 0));

        description1 = new TextView(context);
        description1.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        description1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description1.setText(LocaleController.getString(R.string.OptimizingTelegramDescription1));
        description1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        description1.setGravity(Gravity.CENTER_HORIZONTAL);
        container.addView(description1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 36, 20, 36, 0));

        description2 = new TextView(context);
        description2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description2.setText(LocaleController.getString(R.string.OptimizingTelegramDescription2));
        description2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        description2.setGravity(Gravity.CENTER_HORIZONTAL);
        container.addView(description2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 36, 24, 36, 0));

        addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });
    }
}
