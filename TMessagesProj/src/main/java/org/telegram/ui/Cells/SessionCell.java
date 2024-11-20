/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.DotDividerSpan;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;

public class SessionCell extends FrameLayout {

    private int currentType;
    private TextView nameTextView;
    private TextView onlineTextView;
    private TextView detailTextView;
    private TextView detailExTextView;
    private BackupImageView placeholderImageView;
    private BackupImageView imageView;
    private AvatarDrawable avatarDrawable;
    private boolean needDivider;
    private boolean showStub;
    private AnimatedFloat showStubValue = new AnimatedFloat(this);
    FlickerLoadingView globalGradient;
    LinearLayout linearLayout;

    private int currentAccount = UserConfig.selectedAccount;

    public SessionCell(Context context, int type) {
        super(context);

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1);

        currentType = type;

        if (type == 1) {
            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 15 : 49), 11, (LocaleController.isRTL ? 49 : 15), 0));

            avatarDrawable = new AvatarDrawable();
            avatarDrawable.setTextSize(dp(10));

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(10));
            addView(imageView, LayoutHelper.createFrame(20, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : 21), 13, (LocaleController.isRTL ? 21 : 0), 0));
        } else {
            placeholderImageView = new BackupImageView(context);
            placeholderImageView.setRoundRadius(dp(10));
            addView(placeholderImageView, LayoutHelper.createFrame(42, 42, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : 16), 9, (LocaleController.isRTL ? 16 : 0), 0));

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(10));
            addView(imageView, LayoutHelper.createFrame(42, 42, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : 16), 9, (LocaleController.isRTL ? 16 : 0), 0));

            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 15 : 72), 6.333f, (LocaleController.isRTL ? 72 : 15), 0));
        }


        nameTextView = new TextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, type == 0 ? 15 : 16);
        nameTextView.setLines(1);
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

        onlineTextView = new TextView(context);
        onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, type == 0 ? 12 : 13);
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
        detailTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, type == 0 ? 13 : 14);
        detailTextView.setLines(1);
        detailTextView.setMaxLines(1);
        detailTextView.setSingleLine(true);
        detailTextView.setEllipsize(TextUtils.TruncateAt.END);
        detailTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(detailTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, leftMargin, type == 0 ? 28 : 36, rightMargin, 0));

        detailExTextView = new TextView(context);
        detailExTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        detailExTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, type == 0 ? 13 : 14);
        detailExTextView.setLines(1);
        detailExTextView.setMaxLines(1);
        detailExTextView.setSingleLine(true);
        detailExTextView.setEllipsize(TextUtils.TruncateAt.END);
        detailExTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(detailExTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, leftMargin, type == 0 ? 46 : 59, rightMargin, 0));
    }

    private void setContentAlpha(float alpha) {
        if (detailExTextView != null) {
            detailExTextView.setAlpha(alpha);
        }
        if (detailTextView != null) {
            detailTextView.setAlpha(alpha);
        }
        if (nameTextView != null) {
            nameTextView.setAlpha(alpha);
        }
        if (onlineTextView != null) {
            onlineTextView.setAlpha(alpha);
        }
        if (imageView != null) {
            imageView.setAlpha(alpha);
        }
        if (placeholderImageView != null) {
            placeholderImageView.setAlpha(1f - alpha);
        }
        if (linearLayout != null) {
            linearLayout.setAlpha(alpha);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(currentType == 0 ? 70 : 90) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setSession(TLObject object, boolean divider) {
        needDivider = divider;

        if (object instanceof TLRPC.TL_authorization) {
            TLRPC.TL_authorization session = (TLRPC.TL_authorization) object;
            imageView.setImageDrawable(createDrawable(42, session));

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
                timeText = LocaleController.getString(R.string.Online);
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
                dotDividerSpan.setTopPadding(dp(1.5f));
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
                avatarDrawable.setInfo(currentAccount, user);
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

    public static CombinedDrawable createDrawable(int sz, String platform) {
        TLRPC.TL_authorization auth = new TLRPC.TL_authorization();
        auth.device_model = platform;
        auth.platform = platform;
        auth.app_name = platform;
        return createDrawable(sz, auth);
    }

    public static CombinedDrawable createDrawable(int sz, TLRPC.TL_authorization session) {
        String platform = session.platform.toLowerCase();
        if (platform.isEmpty()) {
            platform = session.system_version.toLowerCase();
        }
        String deviceModel = session.device_model.toLowerCase();
        int iconId;
        int colorKey, colorKey2;
        if (deviceModel.contains("safari")) {
            iconId = R.drawable.device_web_safari;
            colorKey = Theme.key_avatar_backgroundPink;
            colorKey2 = Theme.key_avatar_background2Pink;
        } else if (deviceModel.contains("edge")) {
            iconId = R.drawable.device_web_edge;
            colorKey = Theme.key_avatar_backgroundPink;
            colorKey2 = Theme.key_avatar_background2Pink;
        } else if (deviceModel.contains("chrome")) {
            iconId = R.drawable.device_web_chrome;
            colorKey = Theme.key_avatar_backgroundPink;
            colorKey2 = Theme.key_avatar_background2Pink;
        } else if (deviceModel.contains("opera")) {
            iconId = R.drawable.device_web_opera;
            colorKey = Theme.key_avatar_backgroundPink;
            colorKey2 = Theme.key_avatar_background2Pink;
        } else if (deviceModel.contains("firefox")) {
            iconId = R.drawable.device_web_firefox;
            colorKey = Theme.key_avatar_backgroundPink;
            colorKey2 = Theme.key_avatar_background2Pink;
        } else if (deviceModel.contains("vivaldi")) {
            iconId = R.drawable.device_web_other;
            colorKey = Theme.key_avatar_backgroundPink;
            colorKey2 = Theme.key_avatar_background2Pink;
        } else if (platform.contains("ios")) {
            iconId = deviceModel.contains("ipad") ? R.drawable.device_tablet_ios : R.drawable.device_phone_ios;
            colorKey = Theme.key_avatar_backgroundBlue;
            colorKey2 = Theme.key_avatar_background2Blue;
        } else if (platform.contains("windows")) {
            iconId = R.drawable.device_desktop_win;
            colorKey = Theme.key_avatar_backgroundCyan;
            colorKey2 = Theme.key_avatar_background2Cyan;
        } else if (platform.contains("macos")) {
            iconId = R.drawable.device_desktop_osx;
            colorKey = Theme.key_avatar_backgroundCyan;
            colorKey2 = Theme.key_avatar_background2Cyan;
        } else if (platform.contains("android")) {
            iconId = deviceModel.contains("tab") ? R.drawable.device_tablet_android : R.drawable.device_phone_android;
            colorKey = Theme.key_avatar_backgroundGreen;
            colorKey2 = Theme.key_avatar_background2Green;
        } else if (platform.contains("fragment")) {
            iconId = R.drawable.fragment;
            colorKey = -1;
            colorKey2 = -1;
        } else if (platform.contains("anonymous")) {
            iconId = R.drawable.large_hidden;
            colorKey = Theme.key_avatar_backgroundBlue;
            colorKey2 = Theme.key_avatar_background2Blue;
        } else if (platform.contains("premiumbot")) {
            iconId = R.drawable.filled_star_plus;
            colorKey = Theme.key_color_yellow;
            colorKey2 = Theme.key_color_orange;
        } else if (platform.contains("ads")) {
            iconId = R.drawable.msg_channel;
            colorKey = Theme.key_avatar_backgroundPink;
            colorKey2 = Theme.key_avatar_background2Pink;
        } else if (platform.contains("api")) {
            iconId = R.drawable.filled_paid_broadcast;
            colorKey = Theme.key_avatar_backgroundGreen;
            colorKey2 = Theme.key_avatar_background2Green;
        } else if (platform.equals("?")) {
            iconId = R.drawable.msg_emoji_question;
            colorKey = -1;
            colorKey2 = -1;
        } else if (session.app_name.toLowerCase().contains("desktop")) {
            iconId = R.drawable.device_desktop_other;
            colorKey = Theme.key_avatar_backgroundCyan;
            colorKey2 = Theme.key_avatar_background2Cyan;
        } else {
            iconId = R.drawable.device_web_other;
            colorKey = Theme.key_avatar_backgroundPink;
            colorKey2 = Theme.key_avatar_background2Pink;
        }
        Drawable iconDrawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, iconId).mutate();
        iconDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_avatar_text), PorterDuff.Mode.SRC_IN));
        Drawable bgDrawable = new CircleGradientDrawable(dp(sz), colorKey == -1 ? 0xFF000000 : Theme.getColor(colorKey), colorKey2 == -1 ? 0xFF000000 : Theme.getColor(colorKey2));
        return new CombinedDrawable(bgDrawable, iconDrawable);
    }

    public static class CircleGradientDrawable extends Drawable {

        private Paint paint;
        private int size, colorTop, colorBottom;
        public CircleGradientDrawable(int size, int colorTop, int colorBottom) {
            this.size = size;
            this.colorTop = colorTop;
            this.colorBottom = colorBottom;
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new LinearGradient(0, 0, 0, size, new int[] {colorTop, colorBottom}, new float[] {0, 1}, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.drawCircle(getBounds().centerX(), getBounds().centerY(), Math.min(getBounds().width(), getBounds().height()) / 2f, paint);
        }

        @Override
        public void setAlpha(int i) {
            paint.setAlpha(i);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float stubAlpha = showStubValue.set(showStub ? 1 : 0);
        setContentAlpha(1f - stubAlpha);
        if (stubAlpha > 0 && globalGradient != null) {
            if (stubAlpha < 1f) {
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (int) (255 * stubAlpha), Canvas.ALL_SAVE_FLAG);
            }
            globalGradient.updateColors();
            globalGradient.updateGradient();
            if (getParent() != null) {
                View parent = (View) getParent();
                globalGradient.setParentSize(parent.getMeasuredWidth(), parent.getMeasuredHeight(), -getX());
            }
            float y = linearLayout.getTop() + nameTextView.getTop() + dp(12);
            float x = linearLayout.getX();

            AndroidUtilities.rectTmp.set(x, y - dp(4), x + getMeasuredWidth() * 0.2f, y + dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), globalGradient.getPaint());

            y = linearLayout.getTop() + detailTextView.getTop() - dp(1);
            x = linearLayout.getX();

            AndroidUtilities.rectTmp.set(x, y - dp(4), x + getMeasuredWidth() * 0.4f, y + dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), globalGradient.getPaint());

            y = linearLayout.getTop() + detailExTextView.getTop() - dp(1);
            x = linearLayout.getX();

            AndroidUtilities.rectTmp.set(x, y - dp(4), x + getMeasuredWidth() * 0.3f, y + dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), globalGradient.getPaint());
            invalidate();

            if (stubAlpha < 1f) {
                canvas.restore();
            }
        }
        if (needDivider) {
            int margin = currentType == 1 ? 49 : 72;
            canvas.drawLine(LocaleController.isRTL ? 0 : dp(margin), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(margin) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    public void showStub(FlickerLoadingView globalGradient) {
        this.globalGradient = globalGradient;
        showStub = true;

        Drawable iconDrawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, AndroidUtilities.isTablet() ? R.drawable.device_tablet_android : R.drawable.device_phone_android).mutate();
        iconDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_avatar_text), PorterDuff.Mode.SRC_IN));
        CombinedDrawable combinedDrawable = new CombinedDrawable(Theme.createCircleDrawable(dp(42), Theme.getColor(Theme.key_avatar_backgroundGreen)), iconDrawable);
        if (placeholderImageView != null) {
            placeholderImageView.setImageDrawable(combinedDrawable);
        } else {
            imageView.setImageDrawable(combinedDrawable);
        }
        invalidate();
    }

    public boolean isStub() {
        return showStub;
    }
}
