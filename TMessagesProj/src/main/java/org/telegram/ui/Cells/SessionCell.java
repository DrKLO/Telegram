/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Locale;

public class SessionCell extends FrameLayout {

    private TextView nameTextView;
    private TextView onlineTextView;
    private TextView detailTextView;
    private TextView detailExTextView;
    boolean needDivider;
    private static Paint paint;

    public SessionCell(Context context) {
        super(context);

        if (paint == null) {
            paint = new Paint();
            paint.setColor(0xffd9d9d9);
            paint.setStrokeWidth(1);
        }

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1);
        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 17, 11, 11, 0));

        nameTextView = new TextView(context);
        nameTextView.setTextColor(0xff212121);
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
        detailTextView.setTextColor(0xff212121);
        detailTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        detailTextView.setLines(1);
        detailTextView.setMaxLines(1);
        detailTextView.setSingleLine(true);
        detailTextView.setEllipsize(TextUtils.TruncateAt.END);
        detailTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(detailTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 17, 36, 17, 0));

        detailExTextView = new TextView(context);
        detailExTextView.setTextColor(0xff999999);
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
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(90) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setSession(TLRPC.TL_authorization session, boolean divider) {
        needDivider = divider;

        nameTextView.setText(String.format(Locale.US, "%s %s", session.app_name, session.app_version));
        if ((session.flags & 1) != 0) {
            onlineTextView.setText(LocaleController.getString("Online", R.string.Online));
            onlineTextView.setTextColor(0xff2f8cc9);
        } else {
            onlineTextView.setText(LocaleController.stringForMessageListDate(session.date_active));
            onlineTextView.setTextColor(0xff999999);
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
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(getPaddingLeft(), getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, paint);
        }
    }
}
