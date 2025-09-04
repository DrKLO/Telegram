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
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;

public class ProfileMusicView extends View {

    private final Theme.ResourcesProvider resourcesProvider;

    private Text prevAuthor, prevTitle;
    private Text author, title;
    private final Paint iconPaint = new Paint();
    private final Paint arrowPaint = new Paint();
    private final Path arrowPath = new Path();
    private final Drawable icon;
    private boolean firstLoad;

    private final AnimatedFloat switchMusicAnimator = new AnimatedFloat(this::invalidate, 380, CubicBezierInterpolator.DEFAULT);
    private final AnimatedFloat isVisibleAnimator = new AnimatedFloat(this::onUpdateHeight, 380, CubicBezierInterpolator.EASE_OUT_QUINT);
    private Runnable animationUpdateCallback;
    private int height = 0;

    public ProfileMusicView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        isVisibleAnimator.set(true, true);
        switchMusicAnimator.set(false, true);
        height = getMaxViewHeight();

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
        firstLoad = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.EXACTLY
            ),
            MeasureSpec.makeMeasureSpec(
                height,
                MeasureSpec.EXACTLY
            )
        );
    }

    public void setAnimatedVisibility(boolean isVisible, Runnable animationUpdateCallback) {
        if (isVisible && !isVisibleAnimator.isInProgress()) {
            height = 0;
            isVisibleAnimator.set(false, true);
            switchMusicAnimator.set(false, true);
        }
        isVisibleAnimator.set(isVisible);
        this.animationUpdateCallback = animationUpdateCallback;
    }

    public boolean isVisible() {
        return isVisibleAnimator.getTargetValue() >= 1f;
    }

    public boolean isAnimating() {
        return isVisibleAnimator.isInProgress();
    }

    private void onUpdateHeight() {
        if (getLayoutParams() != null) {
            height = getAnimatedHeight();
            getLayoutParams().height = height;
            requestLayout();
            notifyLayoutUpdate();
        }
    }

    private void notifyLayoutUpdate() {
        if (animationUpdateCallback != null) {
            animationUpdateCallback.run();
            if (!isVisibleAnimator.isInProgress()) {
                animationUpdateCallback = null;
            }
        }
    }

    private int getMaxViewHeight() {
        return dp(22);
    }

    private int getAnimatedHeight() {
        if (isVisibleAnimator.get() <= 0f && !isVisibleAnimator.isInProgress()) {
            return 0;
        }
        return Math.max(1, (int) (getMaxViewHeight() * isVisibleAnimator.get()));
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
        Text newAuthor = new Text(author, 11, AndroidUtilities.bold());
        Text newTitle = new Text(title, 11);

        if (!firstLoad &&
                ((this.author != null && !this.author.getText().equals(newAuthor.getText())) ||
                        (this.title != null && !this.title.getText().equals(newTitle.getText())))) {
            switchMusicAnimator.set(false, true);
            switchMusicAnimator.set(true);
        }

        this.firstLoad = false;
        this.prevAuthor = this.author;
        this.prevTitle = this.title;
        this.author = newAuthor;
        this.title = newTitle;
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

    // private final long start = System.currentTimeMillis();

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (this.author == null || this.title == null) return;
        float visibleFactor = this.isVisibleAnimator.getValue();
        if (!isVisibleAnimator.isInProgress()) notifyLayoutUpdate();
        if (visibleFactor <= 0f) return;

        if (!ignoreRect && renderNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated()) {
            canvas.save();
            canvas.translate(0f, renderNodeTranslateY);
            canvas.scale(renderNodeScale, renderNodeScale);
            canvas.drawRenderNode(renderNode);
            canvas.restore();
        }

        // final long now = System.currentTimeMillis();
        final float cy = getMaxViewHeight() / 2f; //, hh = dp(6), w = dp(2);

//        final float h1 = lerp(0.1f, 1.0f, (float) Math.sin((now - start - 50) % 600 / 600f * Math.PI) / 2.0f + 0.5f);
//        final float h2 = lerp(0.1f, 1.0f, (float) Math.sin((now - start - 250) % 600 / 600f * Math.PI) / 2.0f + 0.5f);
//        final float h3 = lerp(0.1f, 1.0f, (float) Math.sin((now - start - 400) % 600 / 600f * Math.PI) / 2.0f + 0.5f);
//        invalidate();
//        canvas.drawRoundRect(0, cy - hh * h1, w, cy + hh * h1, w, w, iconPaint);
//        canvas.drawRoundRect(dp(4), cy - hh * h2, dp(4) + w, cy + hh * h2, w, w, iconPaint);
//        canvas.drawRoundRect(dp(8), cy - hh * h3, dp(8) + w, cy + hh * h3, w, w, iconPaint);

        float switchAnimation = switchMusicAnimator.getValue();
        if (!switchMusicAnimator.isInProgress()) {
            prevTitle = prevAuthor = null;
        }

        if (prevAuthor != null && prevTitle != null) {
            drawMusicDocument(canvas, this.author, this.title, lerp(cy - height, cy, switchAnimation));
            drawMusicDocument(canvas, this.prevAuthor, this.prevTitle, lerp(cy, cy + height, switchAnimation));
        } else {
            drawMusicDocument(canvas, this.author, this.title, cy);
        }
    }

    private void drawMusicDocument(Canvas canvas, Text author, Text title, float cy) {
        canvas.save();
        final int padding = dp(12);
        final int maxWidth = getWidth() - padding * 2;

        author.ellipsize((maxWidth - dp(35)) / 2f);
        title.ellipsize(maxWidth - author.getWidth() - dp(35));

        final float width = dp(16.6f) + author.getWidth() + title.getWidth() + dp(8);

        canvas.translate((getWidth() - width) / 2f, 0);

        final int iconSz = dp(14);
        icon.setBounds(0, (int) cy - iconSz / 2, iconSz, (int) cy + iconSz / 2);
        icon.draw(canvas);

        canvas.translate(dp(16.6f), 0);

        author.draw(canvas, 0, cy, 0xFFFFFFFF, 1.0f);
        canvas.translate(author.getWidth(), 0);
        title.draw(canvas, 0, cy, 0xFFFFFFFF, 0.85f);
        canvas.translate(title.getWidth(), 0);

        arrowPaint.setStrokeWidth(dpf2(1.16f));
        canvas.translate(dpf2(3.8f), cy);
        canvas.drawPath(arrowPath, arrowPaint);
        canvas.restore();
    }
}
