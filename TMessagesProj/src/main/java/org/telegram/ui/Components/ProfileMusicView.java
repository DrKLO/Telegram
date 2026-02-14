package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ActionBar.Theme.multAlpha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;

public class ProfileMusicView extends View {

    private final Theme.ResourcesProvider resourcesProvider;
    private final PorterDuffColorFilter filterColorWhite = new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
    private final PorterDuffColorFilter filterColorBlack = new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);

    private Text author, title;
    private final Paint iconPaint = new Paint();
    private final Paint arrowPaint = new Paint();
    private final Path arrowPath = new Path();
    private final Drawable icon;

    private final RectF rect = new RectF();
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();

    private final ButtonBounce bounce = new ButtonBounce(this);

    public ProfileMusicView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        icon = context.getResources().getDrawable(R.drawable.files_music).mutate();

        arrowPaint.setStyle(Paint.Style.STROKE);
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
                dp(37),
                MeasureSpec.EXACTLY
            )
        );
    }

    private int textColor = Color.WHITE;
    private float parentExpanded;
    private int backgroundColor;
    private boolean withShadows;

    public void setColor(MessagesController.PeerColor peerColor) {
        int color1, color2;
        if (peerColor == null) {
            color1 = color2 = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
        } else {
            color1 = peerColor.getBgColor1(Theme.isCurrentThemeDark());
            color2 = peerColor.getBgColor2(Theme.isCurrentThemeDark());
        }

        if (peerColor == null) {
            backgroundColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            withShadows = true;
        } else {
            backgroundColor = Theme.adaptHSV(ColorUtils.blendARGB(color1, color2, .15f), +.04f, -.09f);
            withShadows = false;
        }
        backgroundPaint.setColor(backgroundColor);
        checkTextColor();
    }

    private void checkTextColor() {
        final boolean useBlackText = parentExpanded < 0.8f && AndroidUtilities.computePerceivedBrightness(backgroundColor) > 0.85f;
        textColor = useBlackText ? Color.BLACK : Color.WHITE;
        icon.setColorFilter(useBlackText ? filterColorBlack : filterColorWhite);
        iconPaint.setColor(textColor);
        arrowPaint.setColor(Theme.multAlpha(textColor, 0.85f));
        invalidate();
    }

    public void setParentExpanded(float expanded) {
        if (parentExpanded != expanded) {
            parentExpanded = expanded;
            checkTextColor();
            invalidate();
        }
    }



    public void setMusicDocument(TLRPC.Document document) {
        CharSequence author = getAuthor(document);
        CharSequence title = getTitle(document);
        if (TextUtils.isEmpty(author)) {
            if (TextUtils.isEmpty(title)) {
                author = getString(R.string.AudioUnknownArtist);
                title = " - " + getString(R.string.AudioUnknownTitle);
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

    private float currentHeight;

    public void updatePosition(float y, float newHeight) {
        currentHeight = newHeight;
        setTranslationY(y - dp(12));
        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        final float alpha = Utilities.clamp01((currentHeight) / dp(21));
        if (alpha <= 0) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            bounce.setPressed(rect.contains(event.getX(), event.getY()));
        } else if (event.getAction() == MotionEvent.ACTION_MOVE && bounce.isPressed()) {
            if (!rect.contains(event.getX(), event.getY())) {
                bounce.setPressed(false);
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            bounce.setPressed(false);
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (bounce.isPressed()) {
                performClick();
            }
            bounce.setPressed(false);
        }
        return bounce.isPressed();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (this.author == null || this.title == null) return;

        final float alpha = Utilities.clamp01((currentHeight) / dp(21));
        final float scale = bounce.getScale(0.02f);
        if (alpha <= 0) return;

        final int padding = dp(12);
        final int maxWidth = getWidth() - padding * 2;

        this.author.ellipsize((maxWidth - dp(35)) / 2f);
        this.title.ellipsize(maxWidth - this.author.getWidth() - dp(35));

        final float width = dp(16.6f) + this.author.getWidth() + this.title.getWidth() + dp(8);
        final float containerWidth = dp(16) + width;

        canvas.save();
        canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);

        rect.set(
            (getWidth() - containerWidth) / 2f,
            dp(10),
            (getWidth() + containerWidth) / 2f,
            dp(10) + dp(17) * alpha
        );
        if (withShadows && SharedConfig.shadowsInSections) {
            backgroundPaint.setShadowLayer(dpf2(2), 0, dpf2(0.33f), multAlpha(0x0a000000, alpha));
            strokePaint.setShadowLayer(dpf2(0.33f), 0, 0, multAlpha(0x0c000000, alpha));
            strokePaint.setColor(0);
        } else {
            backgroundPaint.setShadowLayer(0, 0, 0, 0);
        }
        int wasAlpha = backgroundPaint.getAlpha();
        backgroundPaint.setAlpha((int) (wasAlpha * alpha));
        if (withShadows && SharedConfig.shadowsInSections) {
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, strokePaint);
        }
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, backgroundPaint);
        backgroundPaint.setAlpha(wasAlpha);

        clipPath.rewind();
        clipPath.addRoundRect(rect, rect.height() / 2f, rect.height() / 2f, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);
        if (!ignoreRect && renderNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated()) {
            canvas.save();
            canvas.translate(0f, renderNodeTranslateY);
            canvas.scale(renderNodeScale, renderNodeScale);
            canvas.drawRenderNode(renderNode);
            canvas.restore();
        }

        canvas.translate((getWidth() - width) / 2f, 0);

//        final long now = System.currentTimeMillis();
        final float cy = getHeight() / 2f, hh = dp(6), w = dp(2);

        final int iconSz = dp(13);
        icon.setBounds(0, (int) cy - iconSz / 2, iconSz, (int) cy + iconSz / 2);
        icon.draw(canvas);

        canvas.translate(dp(16.6f), 0);
        this.author.draw(canvas, 0, cy, textColor, alpha);
        canvas.translate(this.author.getWidth(), 0);
        this.title.draw(canvas, 0, cy, textColor, 0.85f * alpha);
        canvas.translate(this.title.getWidth(), 0);

        arrowPaint.setStrokeWidth(dpf2(1.16f));
        canvas.translate(dpf2(4.8f), cy);
        canvas.drawPath(arrowPath, arrowPaint);

        canvas.restore();

        canvas.restore();
    }
}
