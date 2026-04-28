package org.telegram.ui.Components.poll.attached;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.poll.PollAttachedMedia;

import java.io.File;

public class PollAttachedMediaFile extends PollAttachedMedia {
    public @Nullable final String path;
    public @Nullable final Uri uri;

    public final String name;
    public final long size;
    public final String ext;
    private final Drawable thumb;
    private final TextPaint tp;
    private final StaticLayout staticLayout;

    public PollAttachedMediaFile(@NonNull String path) {
        this.path = path;
        this.uri = null;

        final File file = new File(path);

        long size = 0;
        try {
            size = file.length();
        } catch (Throwable ignored) {}

        this.size = size;

        name = file.getName();
        String[] sp = name.split("\\.");
        ext = sp.length > 1 ? sp[sp.length - 1] : "?";

        final int res = AndroidUtilities.getThumbForNameOrMime(name, ext, false);
        if (res != 0) {
            thumb = ApplicationLoader.applicationContext.getResources().getDrawable(res);
        } else {
            thumb = null;
        }

        if (!TextUtils.isEmpty(ext)) {
            tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setTextSize(dp(13));
            tp.setTypeface(AndroidUtilities.bold());
            tp.setColor(Theme.getColor(Theme.key_files_iconText));


            CharSequence ext = TextUtils.ellipsize(this.ext, tp, dp(34), TextUtils.TruncateAt.END);
            staticLayout = new StaticLayout(ext, tp, dp(34), Layout.Alignment.ALIGN_CENTER, 1, 0, false);
        } else {
            tp = null;
            staticLayout = null;
        }
    }

    public PollAttachedMediaFile(@NonNull Uri uri) {
        this.path = null;
        this.uri = uri;

        String name = MediaController.getFileName(uri);
        if (name == null) {
            name = "?";
        }

        this.name = name;
        String[] sp = name.split("\\.");
        ext = sp.length > 1 ? sp[sp.length - 1] : "?";
        size = 0;

        final int res = AndroidUtilities.getThumbForNameOrMime(name, ext, false);
        if (res != 0) {
            thumb = ApplicationLoader.applicationContext.getResources().getDrawable(res);
        } else {
            thumb = null;
        }

        if (!TextUtils.isEmpty(ext)) {
            tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setTextSize(dp(13));
            tp.setTypeface(AndroidUtilities.bold());
            tp.setColor(Theme.getColor(Theme.key_files_iconText));


            CharSequence ext = TextUtils.ellipsize(this.ext, tp, dp(34), TextUtils.TruncateAt.END);
            staticLayout = new StaticLayout(ext, tp, dp(34), Layout.Alignment.ALIGN_CENTER, 1, 0, false);
        } else {
            tp = null;
            staticLayout = null;
        }
    }

    @Override
    protected void draw(Canvas canvas, int w, int h) {
        if (thumb != null) {
            thumb.setBounds(0, 0, w, h);
            thumb.draw(canvas);

            canvas.save();
            canvas.translate((w - dp(34)) / 2f, dp(15));
            staticLayout.draw(canvas);
            canvas.restore();
        }
    }

    public static Drawable createMessagePreviewDrawable(View view, String title, String subtitle, TLRPC.Document document, MessageObject messageObject) {
        FileInfoDrawable fileInfoDrawable = new FileInfoDrawable();

        fileInfoDrawable.titlePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        fileInfoDrawable.subtitlePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        fileInfoDrawable.radialProgress = new RadialProgress2(view);
        fileInfoDrawable.radialProgress.setCircleRadius(dp(21));
        fileInfoDrawable.radialProgress.setColorKeys(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);

        if (MessageObject.isMusicDocument(document)) {
            if (MessageObject.isDocumentHasThumb(document)) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(22), true, null, false);
                TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(44), true, thumb, true);
                fileInfoDrawable.radialProgress.setImageOverlay(image, thumb, document, messageObject);
            } else {
                String artworkUrl = MessageObject.getArtworkUrl(document, true);
                if (!TextUtils.isEmpty(artworkUrl)) {
                    fileInfoDrawable.radialProgress.setImageOverlay(artworkUrl);
                } else {
                    fileInfoDrawable.radialProgress.setImageOverlay(null, null, null);
                }
            }

            fileInfoDrawable.radialProgress.setIcon(MediaActionDrawable.ICON_PLAY, false, false);
        } else {
            fileInfoDrawable.radialProgress.setIcon(MediaActionDrawable.ICON_FILE, false, false);
        }
        fileInfoDrawable.setText(title, subtitle);

        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                fileInfoDrawable.radialProgress.onAttachedToWindow();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                fileInfoDrawable.radialProgress.onDetachedFromWindow();
            }
        });

        return fileInfoDrawable;
    }

    public static class FileInfoDrawable extends Drawable {

        private final TextPaint titlePaint;
        private final TextPaint subtitlePaint;
        public RadialProgress2 radialProgress;

        private CharSequence title = "";
        private CharSequence subtitle = "";

        private StaticLayout titleLayout;
        private StaticLayout subtitleLayout;

        private final int paddingStart;
        private final int paddingTop;
        private final int paddingEnd;
        private final int lineSpacing;

        private int lastLayoutWidth = -1;

        public FileInfoDrawable() {
            paddingStart = dp(64);
            paddingTop = dp(10.66f);
            paddingEnd = dp(12);
            lineSpacing = dp(4);

            titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            titlePaint.setTextSize(dp(15));
            titlePaint.setTypeface(AndroidUtilities.bold());

            subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            subtitlePaint.setTextSize(dp(13));
        }

        public void setText(CharSequence title, CharSequence subtitle) {
            this.title = title != null ? title : "";
            this.subtitle = subtitle != null ? subtitle : "";
            lastLayoutWidth = -1;
            titleLayout = null;
            subtitleLayout = null;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            super.onBoundsChange(bounds);
            lastLayoutWidth = -1;
            titleLayout = null;
            subtitleLayout = null;
        }

        private void ensureLayout() {
            final Rect bounds = getBounds();
            final int width = bounds.width();
            if (width <= 0) {
                return;
            }
            if (width == lastLayoutWidth && titleLayout != null && subtitleLayout != null) {
                return;
            }
            lastLayoutWidth = width;

            final int textWidth = width - paddingStart - paddingEnd;
            if (textWidth <= 0) {
                titleLayout = null;
                subtitleLayout = null;
                return;
            }

            CharSequence ellipsizedTitle = TextUtils.ellipsize(title, titlePaint, textWidth, TextUtils.TruncateAt.MIDDLE);
            CharSequence ellipsizedSubtitle = TextUtils.ellipsize(subtitle, subtitlePaint, textWidth, TextUtils.TruncateAt.END);

            titleLayout = new StaticLayout(
                    ellipsizedTitle,
                    titlePaint,
                    textWidth,
                    Layout.Alignment.ALIGN_NORMAL,
                    1.0f,
                    0.0f,
                    false
            );

            subtitleLayout = new StaticLayout(
                    ellipsizedSubtitle,
                    subtitlePaint,
                    textWidth,
                    Layout.Alignment.ALIGN_NORMAL,
                    1.0f,
                    0.0f,
                    false
            );
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            ensureLayout();

            if (titleLayout == null || subtitleLayout == null) {
                return;
            }

            final Rect bounds = getBounds();
            final float textX = bounds.left + paddingStart;
            final float titleY = bounds.top + paddingTop;
            final float subtitleY = titleY + titleLayout.getHeight() + lineSpacing;

            radialProgress.setProgressRect(
                bounds.left + dp(10),
                bounds.top + dp(9),
                bounds.left + dp(10) + dp(42),
                bounds.top + dp(9) + dp(42));

            canvas.save();
            canvas.translate(textX, titleY);
            titleLayout.draw(canvas);
            canvas.restore();

            canvas.save();
            canvas.translate(textX, subtitleY);
            subtitleLayout.draw(canvas);
            canvas.restore();

            radialProgress.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
            radialProgress.setOverrideAlpha(alpha / 255f);
            titlePaint.setAlpha(alpha);
            subtitlePaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            titlePaint.setColorFilter(colorFilter);
            subtitlePaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicHeight() {
            return paddingTop + getLineHeight(titlePaint) + lineSpacing + getLineHeight(subtitlePaint) + paddingTop;
        }

        private static int getLineHeight(TextPaint paint) {
            Paint.FontMetricsInt fm = paint.getFontMetricsInt();
            return fm.descent - fm.ascent;
        }
    }
}
