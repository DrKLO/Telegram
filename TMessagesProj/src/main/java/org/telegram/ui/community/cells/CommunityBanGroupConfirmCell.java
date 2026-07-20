package org.telegram.ui.community.cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class CommunityBanGroupConfirmCell extends FrameLayout {

    public final BackupImageView avatarView;
    public final TextView titleView;
    public final TextView subtitleView;

    public CommunityBanGroupConfirmCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, resourcesProvider, true);
    }

    public CommunityBanGroupConfirmCell(Context context, Theme.ResourcesProvider resourcesProvider, boolean withMinus) {
        super(context);


        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(23));
        addView(avatarView, LayoutHelper.createFrame(46, 46, Gravity.LEFT | Gravity.CENTER_VERTICAL, 21, 6, 0, 6));

        if (withMinus) {
            final View minusBg = new View(context) {
                private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint pWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint pRed = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final RectF rectF = new RectF();

                {
                    pWhite.setColor(Color.WHITE);
                    pBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    pRed.setColor(Theme.getColor(Theme.key_color_red, resourcesProvider));
                }

                @Override
                protected void onDraw(@NonNull Canvas canvas) {
                    super.onDraw(canvas);
                    rectF.set(0, 0, getWidth(), getHeight());
                    canvas.drawRoundRect(rectF, rectF.width() / 2f, rectF.height() / 2f, pBg);
                    rectF.inset(dp(1.33f), dp(1.33f));
                    canvas.drawRoundRect(rectF, rectF.width() / 2f, rectF.height() / 2f, pRed);
                    rectF.inset(dpf2(4.67f), dpf2(9.066f));
                    canvas.drawRoundRect(rectF, rectF.height() / 2f, rectF.height() / 2f, pWhite);
                }
            };
            addView(minusBg, LayoutHelper.createFrame(22.66f, 22.66f, Gravity.LEFT | Gravity.BOTTOM, 50 - 1.34f, 0, 0, 5 - 1.34f));
        }

        titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP, 80, 7, 0, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP, 80, 30.33f, 0, 0));
    }
}