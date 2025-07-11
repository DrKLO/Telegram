package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;

public class ActionBarButton extends FrameLayout {
    private final TextView textView;
    public ActionBarButton(Context context) {
        super(context);
        textView = new TextView(context);
        textView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(12);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setSingleLine();
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setCompoundDrawablePadding(dp(-8));
        textView.setPadding(
            dp(4),
            dp(8),
            dp(4),
            dp(0)
        );
        addView(textView, LayoutHelper.createFrame(-1, -1, Gravity.CENTER));


        Drawable bg = Theme.createRadSelectorDrawable(
            Theme.multAlpha(Theme.getColor(Theme.key_iv_background), 0.11f),
            Theme.multAlpha(Theme.getColor(Theme.key_iv_background), 0.11f),
            12,
            12
        );
        setBackground(bg);
    }

    public TextView getTextView() {
        return textView;
    }

    public void setTextAndIcon(CharSequence text, @NonNull Drawable drawable){
        textView.setText(text);
        drawable.setBounds(0, 0, dp(24), dp(24));
        textView.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
    }

}
