package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class WebPagePreviewView extends FrameLayout {
    private final Theme.ResourcesProvider resourcesProvider;
    private final int currentAccount;

    private final ReplyMessageLine line = new ReplyMessageLine(this);
    private final RectF rectF = new RectF();
    private final RectF lineRect = new RectF();
    private final Path clipPath = new Path();

    public WebPagePreviewView(Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount) {
        super(context);
        setWillNotDraw(false);
        this.resourcesProvider = resourcesProvider;
        this.currentAccount = currentAccount;

        line.color1 = line.color2 = line.color3 = Theme.getColor(Theme.key_telegram_color_text, resourcesProvider);
        line.backgroundColor = Theme.multAlpha(line.color1, 0.10f);
        line.hasColor2 = line.hasColor3 = false;
        line.resetAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rectF.set(0, 0, getWidth(), getHeight());
        line.radii[0] = line.radii[1] = line.radii[6] = line.radii[7] = dp(10);
        line.radii[2] = line.radii[3] = line.radii[4] = line.radii[5] = dp(10);
        clipPath.rewind();
        clipPath.addRoundRect(rectF, line.radii, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);
        line.drawBackground(canvas, rectF, 1f);
        lineRect.set(0, 0, dp(3), getHeight());
        line.drawLine(canvas, lineRect);
        canvas.restore();
    }

    public void setWebPage(TLRPC.WebPage webPage) {
        removeAllViews();

        final boolean hasThumb = webPage.photo != null;

        final LinearLayout textBlock = new LinearLayout(getContext());
        textBlock.setOrientation(LinearLayout.VERTICAL);

        if (webPage.site_name != null) {
            final TextView siteNameView = new TextView(getContext());
            siteNameView.setTypeface(AndroidUtilities.bold());
            siteNameView.setText(webPage.site_name);
            siteNameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            siteNameView.setTextColor(Theme.getColor(Theme.key_telegram_color_text, resourcesProvider));
            siteNameView.setSingleLine(true);
            siteNameView.setEllipsize(TextUtils.TruncateAt.END);
            textBlock.addView(siteNameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));
        }

        if (webPage.title != null) {
            final TextView titleView = new TextView(getContext());
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setText(webPage.title);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            textBlock.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));
        }

        if (webPage.description != null) {
            final TextView descView = new TextView(getContext());
            descView.setText(webPage.description);
            descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            descView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            descView.setMaxLines(4);
            descView.setEllipsize(TextUtils.TruncateAt.END);
            textBlock.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        addView(textBlock, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP,
                0, 0, hasThumb ? 56 : 0, 0));

        if (hasThumb) {
            final BackupImageView thumbView = new BackupImageView(getContext());
            thumbView.setRoundRadius(dp(6));
            thumbView.setBackground(Theme.createRoundRectDrawable(dp(6), Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider), 0.08f)));
            addView(thumbView, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 5, 0, 1));

            final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(webPage.photo.sizes, 40);
            final TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(webPage.photo.sizes, dp(36), false, thumb, true);
            thumbView.setImage(
                    ImageLocation.getForObject(size, webPage.photo), "48_48",
                    ImageLocation.getForObject(thumb, webPage.photo), "48_48_b", null, 0, 1, webPage);
        }

        setPadding(dp(10), dp(2), dp(7), dp(6));
    }

    public static boolean hasPreview(TLRPC.WebPage webPage) {
        return webPage != null && (webPage.site_name != null || webPage.title != null || webPage.description != null || webPage.photo != null || webPage.document != null);
    }
}