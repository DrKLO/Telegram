/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
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

    private int currentAccount = UserConfig.selectedAccount;

    public SessionCell(Context context, int type) {
        super(context);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1);


        if (type == 1) {
            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 11 : 45), 11, (LocaleController.isRTL ? 45 : 11), 0));

            avatarDrawable = new AvatarDrawable();
            avatarDrawable.setTextSize(AndroidUtilities.dp(10));

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(AndroidUtilities.dp(10));
            addView(imageView, LayoutHelper.createFrame(20, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : 17), 13, (LocaleController.isRTL ? 17 : 0), 0));
        } else {
            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 11 : 17), 11, (LocaleController.isRTL ? 17 : 11), 0));
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

        detailTextView = new TextView(context);
        detailTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        detailTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        detailTextView.setLines(1);
        detailTextView.setMaxLines(1);
        detailTextView.setSingleLine(true);
        detailTextView.setEllipsize(TextUtils.TruncateAt.END);
        detailTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(detailTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 17, 36, 17, 0));

        detailExTextView = new TextView(context);
        detailExTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        detailExTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        detailExTextView.setLines(1);
        detailExTextView.setMaxLines(1);
        detailExTextView.setSingleLine(true);
        detailExTextView.setEllipsize(TextUtils.TruncateAt.END);
        detailExTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(detailExTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 17, 59, 17, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(90) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setSession(TLObject object, boolean divider) {
        needDivider = divider;

        if (object instanceof TLRPC.TL_authorization) {
            TLRPC.TL_authorization session = (TLRPC.TL_authorization) object;
            nameTextView.setText(String.format(Locale.US, "%s %s", session.app_name, session.app_version));
            if ((session.flags & 1) != 0) {
                setTag(Theme.key_windowBackgroundWhiteValueText);
                onlineTextView.setText(LocaleController.getString("Online", R.string.Online));
                onlineTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            } else {
                setTag(Theme.key_windowBackgroundWhiteGrayText3);
                onlineTextView.setText(LocaleController.stringForMessageListDate(session.date_active));
                onlineTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
            }

            StringBuilder stringBuilder = new StringBuilder();
            if (session.ip.length() != 0) {
                stringBuilder.append(session.ip);
            }
            if (session.country.length() != 0) {
                if (stringBuilder.length() != 0) {
                    stringBuilder.append(" ");
                }
                stringBuilder.append("— ");
                stringBuilder.append(session.country);
            }
            detailExTextView.setText(stringBuilder);

            stringBuilder = new StringBuilder();
            if (session.device_model.length() != 0) {
                stringBuilder.append(session.device_model);
            }
            if (session.system_version.length() != 0 || session.platform.length() != 0) {
                if (stringBuilder.length() != 0) {
                    stringBuilder.append(", ");
                }
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

            if ((session.flags & 2) == 0) {
                if (stringBuilder.length() != 0) {
                    stringBuilder.append(", ");
                }
                stringBuilder.append(LocaleController.getString("UnofficialApp", R.string.UnofficialApp));
                stringBuilder.append(" (ID: ");
                stringBuilder.append(session.api_id);
                stringBuilder.append(")");
            }

            detailTextView.setText(stringBuilder);
        } else if (object instanceof TLRPC.TL_webAuthorization) {
            TLRPC.TL_webAuthorization session = (TLRPC.TL_webAuthorization) object;
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(session.bot_id);
            nameTextView.setText(session.domain);
            String name;
            if (user != null) {
                avatarDrawable.setInfo(user);
                name = UserObject.getFirstName(user);
                TLRPC.FileLocation currentPhoto;
                if (user.photo != null) {
                    currentPhoto = user.photo.photo_small;
                } else {
                    currentPhoto = null;
                }
                imageView.setImage(currentPhoto, "50_50", avatarDrawable);
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
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(getPaddingLeft(), getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, Theme.dividerPaint);
        }
    }
}
