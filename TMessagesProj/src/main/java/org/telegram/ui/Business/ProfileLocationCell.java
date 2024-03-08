package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingDrawable;

import java.util.Locale;

public class ProfileLocationCell extends LinearLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final LoadingDrawable thumbDrawable;
    private final ImageReceiver imageReceiver = new ImageReceiver(this);

    private final TextView textView1, textView2;

    public ProfileLocationCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setOrientation(VERTICAL);

        thumbDrawable = new LoadingDrawable();
        final int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        thumbDrawable.setColors(
            Theme.multAlpha(color, .05f),
            Theme.multAlpha(color, .15f),
            Theme.multAlpha(color, .1f),
            Theme.multAlpha(color, .3f)
        );
        thumbDrawable.strokePaint.setStrokeWidth(dp(1));

        imageReceiver.setRoundRadius(dp(4));

        textView1 = new TextView(context);
        textView1.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        addView(textView1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, LocaleController.isRTL ? 70 : 22, 10, LocaleController.isRTL ? 22 : 70, 4));

        textView2 = new TextView(context);
        textView2.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        textView2.setText(LocaleController.getString(R.string.BusinessProfileLocation));
        textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        addView(textView2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, LocaleController.isRTL ? 70 : 22, 0, LocaleController.isRTL ? 22 : 70, 8));

        setWillNotDraw(false);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == thumbDrawable || super.verifyDrawable(who);
    }

    private boolean needDivider;
    public void set(TLRPC.TL_businessLocation value, boolean divider) {
        if (value != null) {
            textView1.setText(value.address);
            if (value.geo_point != null) {
                imageReceiver.setImage(AndroidUtilities.formapMapUrl(UserConfig.selectedAccount, value.geo_point.lat, value.geo_point._long, dp(44), dp(44), false, 15, -1), "44_44", thumbDrawable, null, 0);
            } else {
                imageReceiver.setImageBitmap((Drawable) null);
            }
        }

        needDivider = divider;
        setPadding(0, 0, 0, needDivider ? 1 : 0);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        imageReceiver.setImageCoords(
            LocaleController.isRTL ? dp(16) : getWidth() - dp(44 + 16),
            dp(8),
            dp(44), dp(44)
        );
        imageReceiver.draw(canvas);

        super.onDraw(canvas);

        if (needDivider) {
            Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
            if (dividerPaint == null) dividerPaint = Theme.dividerPaint;
            canvas.drawRect(dp(LocaleController.isRTL ? 0 : 21.33f), getMeasuredHeight() - 1, getWidth() - dp(LocaleController.isRTL ? 21.33f : 0), getMeasuredHeight(), dividerPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    public static void openLocation(Activity activity, TLRPC.TL_businessLocation location) {
        if (activity == null || location == null) return;
        if (location.geo_point != null && !(location.geo_point instanceof TLRPC.TL_geoPointEmpty)) {
            try {
                String uri = String.format(Locale.ENGLISH, "geo:%f,%f", location.geo_point.lat, location.geo_point._long);
                if (!TextUtils.isEmpty(location.address)) {
                    uri += "?q=" + location.address;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                activity.startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

}
