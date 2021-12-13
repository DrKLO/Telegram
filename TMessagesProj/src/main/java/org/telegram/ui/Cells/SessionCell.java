/*
 * This is the source code of Telegram for Android v. 5.x.x
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
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.DotDividerSpan;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Locale;

public class SessionCell extends FrameLayout {

    private TextView nameTextView;
    private TextView onlineTextView;
    private TextView detailTextView;
    private TextView detailExTextView;
    private BackupImageView imageView;
    private AvatarDrawable avatarDrawable;
    private boolean needDivider;
    private boolean showStub;
    FlickerLoadingView globalGradient;
    LinearLayout linearLayout;

    private int currentAccount = UserConfig.selectedAccount;

    public SessionCell(Context context, int type) {
        super(context);

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1);

        if (type == 1) {
            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 15 : 49), 11, (LocaleController.isRTL ? 49 : 15), 0));

            avatarDrawable = new AvatarDrawable();
            avatarDrawable.setTextSize(AndroidUtilities.dp(10));
            imageView = new BackupImageView(context);
            imageView.setRoundRadius(AndroidUtilities.dp(10));
            addView(imageView, LayoutHelper.createFrame(20, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : 21), 13, (LocaleController.isRTL ? 21 : 0), 0));
        } else {
            imageView = new BackupImageView(context);
            imageView.setRoundRadius(AndroidUtilities.dp(10));
            addView(imageView, LayoutHelper.createFrame(42, 42, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : 16), 13, (LocaleController.isRTL ? 16 : 0), 0));

            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 15 : 72), 11, (LocaleController.isRTL ? 72 : 15), 0));
        }


        nameTextView = new TextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setLines(1);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

        onlineTextView = new TextView(context);
        onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        onlineTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);

        if (LocaleController.isRTL) {
            linearLayout.addView(onlineTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 2, 0, 0));
            linearLayout.addView(nameTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, Gravity.RIGHT | Gravity.TOP, 10, 0, 0, 0));
        } else {
            linearLayout.addView(nameTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, Gravity.LEFT | Gravity.TOP, 0, 0, 10, 0));
            linearLayout.addView(onlineTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP, 0, 2, 0, 0));
        }

        int leftMargin;
        int rightMargin;
        if (LocaleController.isRTL) {
            rightMargin = type == 0 ? 72 : 21;
            leftMargin = 21;
        } else {
            leftMargin = type == 0 ? 72 : 21;
            rightMargin = 21;
        }

        detailTextView = new TextView(context);
        detailTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        detailTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        detailTextView.setLines(1);
        detailTextView.setMaxLines(1);
        detailTextView.setSingleLine(true);
        detailTextView.setEllipsize(TextUtils.TruncateAt.END);
        detailTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(detailTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, leftMargin, 36, rightMargin, 0));

        detailExTextView = new TextView(context);
        detailExTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        detailExTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        detailExTextView.setLines(1);
        detailExTextView.setMaxLines(1);
        detailExTextView.setSingleLine(true);
        detailExTextView.setEllipsize(TextUtils.TruncateAt.END);
        detailExTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(detailExTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, leftMargin, 59, rightMargin, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(90) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setSession(TLObject object, boolean divider) {
        needDivider = divider;

        if (object instanceof TLRPC.TL_authorization) {
            TLRPC.TL_authorization session = (TLRPC.TL_authorization) object;
            imageView.setImageDrawable(createDrawable(session));

            StringBuilder stringBuilder = new StringBuilder();
            if (session.device_model.length() != 0) {
                stringBuilder.append(session.device_model);
            }
            if (stringBuilder.length() == 0) {
                if (session.platform.length() != 0) {
                    stringBuilder.append(session.platform);
                }
                if (session.system_version.length() != 0) {
                    if (session.platform.length() != 0) {
                        stringBuilder.append(" ");
                    }
                    stringBuilder.append(session.system_version);
                }
            }
            nameTextView.setText(stringBuilder);

            String timeText;
            if ((session.flags & 1) != 0) {
                setTag(Theme.key_windowBackgroundWhiteValueText);
                timeText = LocaleController.getString("Online", R.string.Online);
            } else {
                setTag(Theme.key_windowBackgroundWhiteGrayText3);
                timeText = LocaleController.stringForMessageListDate(session.date_active);
            }

            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            if (session.country.length() != 0) {
                spannableStringBuilder.append(session.country);
            }
            if (spannableStringBuilder.length() != 0) {
                DotDividerSpan dotDividerSpan = new DotDividerSpan();
                dotDividerSpan.setTopPadding(AndroidUtilities.dp(1.5f));
                spannableStringBuilder.append(" . ").setSpan(dotDividerSpan, spannableStringBuilder.length() - 2, spannableStringBuilder.length() - 1, 0);
            }
            spannableStringBuilder.append(timeText);
            detailExTextView.setText(spannableStringBuilder);

            stringBuilder = new StringBuilder();
            stringBuilder.append(session.app_name);
            stringBuilder.append(" ").append(session.app_version);

            detailTextView.setText(stringBuilder);
        } else if (object instanceof TLRPC.TL_webAuthorization) {
            TLRPC.TL_webAuthorization session = (TLRPC.TL_webAuthorization) object;
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(session.bot_id);
            nameTextView.setText(session.domain);
            String name;
            if (user != null) {
                avatarDrawable.setInfo(user);
                name = UserObject.getFirstName(user);
                imageView.setForUserOrChat(user, avatarDrawable);
            } else {
                name = "";
            }

            setTag(Theme.key_windowBackgroundWhiteGrayText3);
            onlineTextView.setText(LocaleController.stringForMessageListDate(session.date_active));
            onlineTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));

            StringBuilder stringBuilder = new StringBuilder();
            if (session.ip.length() != 0) {
                stringBuilder.append(session.ip);
            }
            if (session.region.length() != 0) {
                if (stringBuilder.length() != 0) {
                    stringBuilder.append(" ");
                }
                stringBuilder.append("— ");
                stringBuilder.append(session.region);
            }
            detailExTextView.setText(stringBuilder);

            stringBuilder = new StringBuilder();
            if (!TextUtils.isEmpty(name)) {
                stringBuilder.append(name);
            }
            if (session.browser.length() != 0 ) {
                if (stringBuilder.length() != 0) {
                    stringBuilder.append(", ");
                }
                stringBuilder.append(session.browser);
            }
            if (session.platform.length() != 0) {
                if (stringBuilder.length() != 0) {
                    stringBuilder.append(", ");
                }
                stringBuilder.append(session.platform);
            }

            detailTextView.setText(stringBuilder);
        }

        if (showStub) {
            showStub = false;
            invalidate();
        }
    }

    public static Drawable createDrawable(TLRPC.TL_authorization session) {
        String platform = session.platform.toLowerCase();
        if (platform.isEmpty()) {
            platform = session.system_version.toLowerCase();
        }
        String deviceModel = session.device_model.toLowerCase();
        int iconId;
        String colorKey;
        if (deviceModel.contains("safari")) {
            iconId = R.drawable.device_web_safari;
            colorKey = Theme.key_avatar_backgroundPink;
        } else  if (deviceModel.contains("edge")) {
            iconId = R.drawable.device_web_edge;
            colorKey = Theme.key_avatar_backgroundPink;
        } else if (deviceModel.contains("chrome")) {
            iconId = R.drawable.device_web_chrome;
            colorKey = Theme.key_avatar_backgroundPink;
        } else if (deviceModel.contains("opera")) {
            iconId = R.drawable.device_web_opera;
            colorKey = Theme.key_avatar_backgroundPink;
        } else if (deviceModel.contains("firefox")) {
            iconId = R.drawable.device_web_firefox;
            colorKey = Theme.key_avatar_backgroundPink;
        } else if (deviceModel.contains("vivaldi")) {
            iconId = R.drawable.device_web_other;
            colorKey = Theme.key_avatar_backgroundPink;
        } else if (platform.contains("ios")) {
            iconId = deviceModel.contains("ipad") ? R.drawable.device_tablet_ios : R.drawable.device_phone_ios;
            colorKey = Theme.key_avatar_backgroundBlue;
        } else if (platform.contains("windows")) {
            iconId = R.drawable.device_desktop_win;
            colorKey = Theme.key_avatar_backgroundCyan;
        } else if (platform.contains("macos")) {
            iconId = R.drawable.device_desktop_osx;
            colorKey = Theme.key_avatar_backgroundCyan;
        } else if (platform.contains("android")) {
            iconId = deviceModel.contains("tab") ? R.drawable.device_tablet_android : R.drawable.device_phone_android;
            colorKey = Theme.key_avatar_backgroundGreen;
        } else {
            if (session.app_name.toLowerCase().contains("desktop")) {
                iconId = R.drawable.device_desktop_other;
                colorKey = Theme.key_avatar_backgroundCyan;
            } else {
                iconId = R.drawable.device_web_other;
                colorKey = Theme.key_avatar_backgroundPink;
            }
        }
        Drawable iconDrawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, iconId).mutate();
        iconDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_avatar_text), PorterDuff.Mode.SRC_IN));
        CombinedDrawable combinedDrawable = new CombinedDrawable(Theme.createCircleDrawable(AndroidUtilities.dp(42), Theme.getColor(colorKey)), iconDrawable);
        return combinedDrawable;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (showStub && globalGradient != null) {
            globalGradient.updateColors();
            globalGradient.updateGradient();
            if (getParent() != null) {
                View parent = (View) getParent();
                globalGradient.setParentSize(parent.getMeasuredWidth(), parent.getMeasuredHeight(), -getX());
            }
            float y = linearLayout.getTop() + nameTextView.getTop() + AndroidUtilities.dp(12);
            float x = linearLayout.getX();

            AndroidUtilities.rectTmp.set(x, y - AndroidUtilities.dp(4), x + getMeasuredWidth() * 0.2f, y + AndroidUtilities.dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4), AndroidUtilities.dp(4), globalGradient.getPaint());

            y = linearLayout.getTop() + detailTextView.getTop() - AndroidUtilities.dp(1);
            x = linearLayout.getX();

            AndroidUtilities.rectTmp.set(x, y - AndroidUtilities.dp(4), x + getMeasuredWidth() * 0.4f, y + AndroidUtilities.dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4), AndroidUtilities.dp(4), globalGradient.getPaint());

            y = linearLayout.getTop() + detailExTextView.getTop() - AndroidUtilities.dp(1);
            x = linearLayout.getX();

            AndroidUtilities.rectTmp.set(x, y - AndroidUtilities.dp(4), x + getMeasuredWidth() * 0.3f, y + AndroidUtilities.dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4), AndroidUtilities.dp(4), globalGradient.getPaint());
            invalidate();
        }
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    public void showStub(FlickerLoadingView globalGradient) {
        this.globalGradient = globalGradient;
        showStub = true;

        Drawable iconDrawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, AndroidUtilities.isTablet() ?  R.drawable.device_tablet_android : R.drawable.device_phone_android).mutate();
        iconDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_avatar_text), PorterDuff.Mode.SRC_IN));
        CombinedDrawable combinedDrawable = new CombinedDrawable(Theme.createCircleDrawable(AndroidUtilities.dp(42), Theme.getColor(Theme.key_avatar_backgroundGreen)), iconDrawable);
        imageView.setImageDrawable(combinedDrawable);
        invalidate();
    }

    public boolean isStub() {
        return showStub;
    }
}
