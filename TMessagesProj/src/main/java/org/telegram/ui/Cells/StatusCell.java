/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ActionBar.SimpleTextView;

public class StatusCell extends FrameLayout {

    private static final String SIGN_UP_TITLE = LocaleController.getString("SignUp", R.string.SignUp);

    private SimpleTextView titleTextView;
    private TextView signUpTextView;

    private BackupImageView avatarImageView;
    private ImageView imageView;

    private CharSequence currentTitle = "";

    public StatusCell(Context context) {
        super(context);

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(28));
        avatarImageView.setImage(null, "50_50", new AvatarDrawable());
        addView(avatarImageView, LayoutHelper.createFrame(56, 56, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, LocaleController.isRTL ? 0 : 7, 6, LocaleController.isRTL ? 7 : 0, 10));

        titleTextView = new SimpleTextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTextSize(18);
        titleTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 : 74, 0, LocaleController.isRTL ? (64) : 28 , 36));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        imageView.setVisibility(VISIBLE);
        imageView.setImageResource(R.drawable.actions_permissions);
        addView(imageView, LayoutHelper.createFrame(LayoutParams.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 10 : 22, 0, LocaleController.isRTL ? 16 : 0, 0));

        signUpTextView = new TextView(context);
        signUpTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        signUpTextView.setTextColor(Theme.getColor(Theme.key_profile_creatorIcon));
        addView(signUpTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 : 74, 44, LocaleController.isRTL ? (64) : 28, 0));

        setSignUpTitle(SIGN_UP_TITLE);
        setFocusable(true);
        update();
    }

    public void setAuthorizedState(String rating) {
        setSignUpListener(null);
        setSignUpTitle(rating);
    }

    public void setUnAuthorizedState(OnClickListener listener) {
        setSignUpListener(listener);
        setSignUpTitle(SIGN_UP_TITLE);
    }

    public void setTitle(String title) {
        if (titleTextView != null) titleTextView.setText(title);
    }

    public void update() {
        if (titleTextView == null) return;
        if (signUpTextView == null) return;

        ((LayoutParams) titleTextView.getLayoutParams()).topMargin = AndroidUtilities.dp(4);

        if (currentTitle != null) {
            titleTextView.setText(currentTitle);
        } else {
            titleTextView.setText("");
        }

        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        signUpTextView.setTextColor(Theme.getColor(Theme.key_profile_creatorIcon));
    }

    @Override
    public boolean hasOverlappingRendering() { return false; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(78) + 1, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(68), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(68) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    private void setSignUpListener(OnClickListener listener) {
        if (signUpTextView == null) return;
        signUpTextView.setOnClickListener(listener);
    }

    private void setSignUpTitle(String role) {
        if (signUpTextView == null) return;

        signUpTextView.setVisibility(role != null ? VISIBLE : GONE);
        signUpTextView.setText(role);
        if (role != null) {
            CharSequence text = signUpTextView.getText();
            int size = (int) Math.ceil(signUpTextView.getPaint().measureText(text, 0, text.length()));
            titleTextView.setPadding(LocaleController.isRTL ? size + AndroidUtilities.dp(6) : 0, 24, !LocaleController.isRTL ? size + AndroidUtilities.dp(6) : 0, 0);
        } else {
            titleTextView.setPadding(0, 0, 0, 0);
        }
    }

}