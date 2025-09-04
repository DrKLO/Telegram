package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;

public class ProfileMusicView extends View {

    private final Theme.ResourcesProvider resourcesProvider;

    private Text author, title;
    private final Paint iconPaint = new Paint();
    private final Paint arrowPaint = new Paint();
    private final Path arrowPath = new Path();
    private final Drawable icon;

    public ProfileMusicView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        icon = context.getResources().getDrawable(R.drawable.files_music).mutate();
        icon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));

        iconPaint.setColor(0xFFFFFFFF);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setColor(Theme.multAlpha(0xFFFFFFFF, 0.85f));
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPath.moveTo(0, -dpf2(3.33f));
        arrowPath.lineTo(dpf2(3.16f), 0);
        arrowPath.lineTo(0, dpf2(3.33f));

        setColor(null);
        setText("Author", " - Title");
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.EXACTLY
            ),
            MeasureSpec.makeMeasureSpec(
                dp(22),
                MeasureSpec.EXACTLY
            )
        );
    }

    public void setColor(MessagesController.PeerColor peerColor) {
        int color1, color2;
        if (peerColor == null) {
            if (!Theme.isCurrentThemeDark()) {
                setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider));
                return;
            }
            color1 = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
            color2 = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
        } else {
            color1 = peerColor.getBgColor1(Theme.isCurrentThemeDark());
            color2 = peerColor.getBgColor2(Theme.isCurrentThemeDark());
        }
        setBackgroundColor(
            Theme.adaptHSV(ColorUtils.blendARGB(color1, color2, .25f), +.02f, -.08f)
        );
    }

    public void setMusicDocument(TLRPC.Document document) {
        CharSequence author = getAuthor(document);
        CharSequence title = getTitle(document);
        if (TextUtils.isEmpty(author)) {
            if (TextUtils.isEmpty(title)) {
                author = getString(R.string.AudioUnknownArtist);
                title = getString(R.string.AudioUnknownTitle);
            } else {
                author = "";
            }
        } else if (!TextUtils.isEmpty(title)) {
            title = " - " + title;
        } else {
            title = "";
        }
        setText(author, title);
    }

    public static CharSequence getTitle(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                String title = attribute.title;
                if (title == null || title.length() == 0) {
                    title = FileLoader.getDocumentFileName(document);
                }
                return title;
            }
        }
        String fileName = FileLoader.getDocumentFileName(document);
        if (!TextUtils.isEmpty(fileName)) {
            return fileName;
        }
//        return getString(R.string.AudioUnknownTitle);
        return null;
    }

    public static CharSequence getAuthor(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                if (!attribute.voice) {
                    return attribute.performer;
                }
            }
        }
        return null;
    }

    public void setText(CharSequence author, CharSequence title) {
        this.author = new Text(author, 11, AndroidUtilities.bold());
        this.title = new Text(title, 11);
    }

    private ProfileActivity.AvatarImageView avatarView;
    private boolean ignoreRect = false;
    private RenderNode renderNode;
    private float renderNodeScale;
    private float renderNodeTranslateY;

    public void drawingBlur(boolean drawing) {
        if (ignoreRect != drawing || renderNode != null) {
            ignoreRect = drawing;
            renderNode = null;
            avatarView = null;
            invalidate();
        }
    }

    public void drawingBlur(RenderNode renderNode, ProfileActivity.AvatarImageView avatarView, float scale, float dy) {
        this.ignoreRect = false;
        this.renderNode = renderNode;
        this.avatarView = avatarView;
        this.renderNodeScale = scale;
        this.renderNodeTranslateY = dy;
        invalidate();
    }

    private final long start = System.currentTimeMillis();

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (this.author == null || this.title == null) return;

        if (!ignoreRect && renderNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated()) {
            canvas.save();
            canvas.translate(0f, renderNodeTranslateY);
            canvas.scale(renderNodeScale, renderNodeScale);
            canvas.drawRenderNode(renderNode);
            canvas.restore();
        }

        final int padding = dp(12);
        final int maxWidth = getWidth() - padding * 2;

        this.author.ellipsize((maxWidth - dp(35)) / 2f);
        this.title.ellipsize(maxWidth - this.author.getWidth() - dp(35));

        final float width = dp(16.6f) + this.author.getWidth() + this.title.getWidth() + dp(8);

        canvas.save();
        canvas.translate((getWidth() - width) / 2f, 0);

        final long now = System.currentTimeMillis();
        final float cy = getHeight() / 2f, hh = dp(6), w = dp(2);

        final int iconSz = dp(14);
        icon.setBounds(0, (int) cy - iconSz / 2, iconSz, (int) cy + iconSz / 2);
        icon.draw(canvas);
//        final float h1 = lerp(0.1f, 1.0f, (float) Math.sin((now - start - 50) % 600 / 600f * Math.PI) / 2.0f + 0.5f);
//        final float h2 = lerp(0.1f, 1.0f, (float) Math.sin((now - start - 250) % 600 / 600f * Math.PI) / 2.0f + 0.5f);
//        final float h3 = lerp(0.1f, 1.0f, (float) Math.sin((now - start - 400) % 600 / 600f * Math.PI) / 2.0f + 0.5f);
//        invalidate();
//        canvas.drawRoundRect(0, cy - hh * h1, w, cy + hh * h1, w, w, iconPaint);
//        canvas.drawRoundRect(dp(4), cy - hh * h2, dp(4) + w, cy + hh * h2, w, w, iconPaint);
//        canvas.drawRoundRect(dp(8), cy - hh * h3, dp(8) + w, cy + hh * h3, w, w, iconPaint);

        canvas.translate(dp(16.6f), 0);
        this.author.draw(canvas, 0, cy, 0xFFFFFFFF, 1.0f);
        canvas.translate(this.author.getWidth(), 0);
        this.title.draw(canvas, 0, cy, 0xFFFFFFFF, 0.85f);
        canvas.translate(this.title.getWidth(), 0);

        arrowPaint.setStrokeWidth(dpf2(1.16f));
        canvas.translate(dpf2(3.8f), cy);
        canvas.drawPath(arrowPath, arrowPaint);

        canvas.restore();
    }
}
