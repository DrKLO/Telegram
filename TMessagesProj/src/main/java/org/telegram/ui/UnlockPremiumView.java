package org.telegram.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumButtonView;

public class UnlockPremiumView extends FrameLayout {

    public static final int TYPE_STICKERS = 0;
    public static final int TYPE_REACTIONS = 1;
    public final PremiumButtonView premiumButtonView;

    public UnlockPremiumView(@NonNull Context context, int type, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        LinearLayout linearLayout = new LinearLayout(context);
        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        linearLayout.setOrientation(LinearLayout.VERTICAL);


        TextView descriptionTextView = new TextView(context);
        descriptionTextView.setTextColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 100));
        descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descriptionTextView.setGravity(Gravity.CENTER);
        if (type == TYPE_STICKERS) {
            descriptionTextView.setText(LocaleController.getString(R.string.UnlockPremiumStickersDescription));
        } else if (type == TYPE_REACTIONS) {
            descriptionTextView.setText(LocaleController.getString(R.string.UnlockPremiumReactionsDescription));
        }
        linearLayout.addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 17, 17, 16));

        premiumButtonView = new PremiumButtonView(context, false, resourcesProvider);

        String text;
        if (type == TYPE_STICKERS) {
            text = LocaleController.getString(R.string.UnlockPremiumStickers);
        } else {
            text = LocaleController.getString(R.string.UnlockPremiumReactions);
        }
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder.append("d ").setSpan(new ColoredImageSpan(ContextCompat.getDrawable(context, R.drawable.msg_premium_normal)), 0, 1, 0);
        spannableStringBuilder.append(text);
        premiumButtonView.buttonTextView.setText(spannableStringBuilder);
        linearLayout.addView(premiumButtonView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 0, 16, 16));

    }


}
