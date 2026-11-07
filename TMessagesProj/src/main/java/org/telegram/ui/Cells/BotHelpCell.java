/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileRefController;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ClipRoundedDrawable;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.util.Objects;

public class BotHelpCell extends View {

    private StaticLayout textLayout;
    private String oldText;
    private String oldManagerBotName;

    private String currentPhotoKey;

    private int width;
    private int height;
    private int textX;
    private int textY;
    public boolean wasDraw;

    private LinkSpanDrawable<ClickableSpan> pressedLink;
    private LinkSpanDrawable.LinkCollector links = new LinkSpanDrawable.LinkCollector(this);

    private BotHelpCellDelegate delegate;
    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;

    private int photoHeight;
    private ImageReceiver imageReceiver;
    private boolean isPhotoVisible;
    private boolean isTextVisible;
    private int imagePadding = dp(4);

    private boolean animating;

    public interface BotHelpCellDelegate {
        void didPressUrl(String url);
    }

    public BotHelpCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;

        imageReceiver = new ImageReceiver(this);
        imageReceiver.setInvalidateAll(true);
        imageReceiver.setCrossfadeWithOldImage(true);
        imageReceiver.setCrossfadeDuration(300);

        selectorDrawable = Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), selectorDrawableRadius = SharedConfig.bubbleRadius, SharedConfig.bubbleRadius);
        selectorDrawable.setCallback(this);
    }

    public void setDelegate(BotHelpCellDelegate botHelpCellDelegate) {
        delegate = botHelpCellDelegate;
    }

    private void resetPressedLink() {
        if (pressedLink != null) {
            pressedLink = null;
        }
        links.clear();
        invalidate();
    }

    public void setText(boolean bot, String text) {
        setText(bot, 0, text, null, null, null);
    }

    public void setText(boolean bot, long botId, String text, TLObject imageOrAnimation, TL_bots.BotInfo botInfo, String managerBotName) {
        boolean photoVisible = imageOrAnimation != null;
        boolean textVisible = !TextUtils.isEmpty(text);
        if ((text == null || text.length() == 0) && TextUtils.isEmpty(managerBotName) && !photoVisible) {
            setVisibility(GONE);
            return;
        }
        if (text == null) {
            text = "";
        }
        if (text != null && text.equals(oldText) && TextUtils.equals(oldManagerBotName, managerBotName) && isPhotoVisible == photoVisible) {
            return;
        }
        final boolean setup = TextUtils.isEmpty(text) && imageOrAnimation == null && !TextUtils.isEmpty(managerBotName) && botId != 0;
        isPhotoVisible = photoVisible || setup;
        isTextVisible = textVisible || setup;
        if (setup) {
            if (!Objects.equals(currentPhotoKey, "setup")) {
                currentPhotoKey = "setup";
                imageReceiver.setImageBitmap(new ClipRoundedDrawable(new BotIntroDrawable(getContext())));

                int topRadius = dp(SharedConfig.bubbleRadius) - dp(2), bottomRadius = dp(4);
                if (!isTextVisible) {
                    bottomRadius = topRadius;
                }
                imageReceiver.setRoundRadius(topRadius, topRadius, bottomRadius, bottomRadius);
            }
        } else if (isPhotoVisible) {
            String photoKey = FileRefController.getKeyForParentObject(botInfo);
            if (!Objects.equals(currentPhotoKey, photoKey)) {
                currentPhotoKey = photoKey;
                if (imageOrAnimation instanceof TLRPC.TL_photo) {
                    TLRPC.Photo photo = (TLRPC.Photo) imageOrAnimation;
                    imageReceiver.setImage(ImageLocation.getForPhoto(FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 400), photo), "400_400", null, "jpg", botInfo, 0);
                } else if (imageOrAnimation instanceof TLRPC.Document) {
                    TLRPC.Document doc = (TLRPC.Document) imageOrAnimation;
                    TLRPC.PhotoSize photoThumb = FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 400);
                    BitmapDrawable strippedThumb = null;
                    if (SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW) {
                        for (TLRPC.PhotoSize photoSize : doc.thumbs) {
                            if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                                strippedThumb = new BitmapDrawable(getResources(), ImageLoader.getStrippedPhotoBitmap(photoSize.bytes, "b"));
                            }
                        }
                    }
                    imageReceiver.setImage(ImageLocation.getForDocument(doc), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForDocument(MessageObject.getDocumentVideoThumb(doc), doc), null, ImageLocation.getForDocument(photoThumb, doc), "86_86_b", strippedThumb, doc.size, "mp4", botInfo, 0);
                }

                int topRadius = dp(SharedConfig.bubbleRadius) - dp(2), bottomRadius = dp(4);
                if (!isTextVisible) {
                    bottomRadius = topRadius;
                }
                imageReceiver.setRoundRadius(topRadius, topRadius, bottomRadius, bottomRadius);
            }
        }
        oldText = AndroidUtilities.getSafeString(text);
        oldManagerBotName = managerBotName;
        setVisibility(VISIBLE);
        int maxWidth;
        if (AndroidUtilities.isTablet()) {
            maxWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.7f);
        } else {
            maxWidth = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.7f);
        }
        if (isTextVisible) {
            String[] lines = text.split("\n");
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
            if (setup) {
                stringBuilder.append(AndroidUtilities.replaceTags(
                    formatString(R.string.ManagedBotChatInfo, DialogObject.getName(currentAccount, botId), managerBotName)
                ));
            } else {
                String help = getString(R.string.BotInfoTitle);
                if (bot) {
                    stringBuilder.append(help);
                    stringBuilder.append("\n\n");
                }
                for (int a = 0; a < lines.length; a++) {
                    stringBuilder.append(lines[a].trim());
                    if (a != lines.length - 1) {
                        stringBuilder.append("\n");
                    }
                }
                MessageObject.addLinks(false, stringBuilder);
                if (bot) {
                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, help.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            Emoji.replaceEmoji(stringBuilder, Theme.chat_msgTextPaint.getFontMetricsInt(), false);
            try {
                textLayout = new StaticLayout(stringBuilder, Theme.chat_msgTextPaint, maxWidth - (isPhotoVisible ? dp(5) : 0), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                width = 0;
                height = textLayout.getHeight() + dp(4 + 18);
                int count = textLayout.getLineCount();
                for (int a = 0; a < count; a++) {
                    width = (int) Math.ceil(Math.max(width, textLayout.getLineWidth(a) + textLayout.getLineLeft(a)));
                }
                if (width > maxWidth || isPhotoVisible) {
                    width = maxWidth;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (isPhotoVisible) {
            width = maxWidth;
        }
        width += dp(4 + 18);

        if (isPhotoVisible) {
            height += (photoHeight = (int) (width * 0.5625)) + dp(4); // 16:9
        }
    }

    public CharSequence getText() {
        if (textLayout == null) {
            return null;
        }
        return textLayout.getText();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        boolean result = false;
        if (textLayout != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || pressedLink != null && event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    resetPressedLink();
                    try {
                        int x2 = (int) (x - textX);
                        int y2 = (int) (y - textY);
                        int line = textLayout.getLineForVertical(y2);
                        int off = textLayout.getOffsetForHorizontal(line, x2);

                        float left = textLayout.getLineLeft(line);
                        if (left <= x2 && left + textLayout.getLineWidth(line) >= x2) {
                            Spannable buffer = (Spannable) textLayout.getText();
                            ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                            if (link.length != 0) {
                                resetPressedLink();
                                pressedLink = new LinkSpanDrawable<ClickableSpan>(link[0], resourcesProvider, x2, y2);
                                result = true;
                                try {
                                    int start = buffer.getSpanStart(link[0]);
                                    LinkPath path = pressedLink.obtainNewPath();
                                    path.setCurrentLayout(textLayout, start, 0);
                                    textLayout.getSelectionPath(start, buffer.getSpanEnd(link[0]), path);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                links.addLink(pressedLink);
                                invalidate();
                            } else {
                                resetPressedLink();
                            }
                        } else {
                            resetPressedLink();
                        }
                    } catch (Exception e) {
                        resetPressedLink();
                        FileLog.e(e);
                    }
                } else if (pressedLink != null) {
                    try {
                        ClickableSpan span = pressedLink.getSpan();
                        if (span instanceof URLSpanNoUnderline) {
                            String url = ((URLSpanNoUnderline) span).getURL();
                            if (url.startsWith("@") || url.startsWith("#") || url.startsWith("/") || url.startsWith("$")) {
                                if (delegate != null) {
                                    delegate.didPressUrl(url);
                                }
                            }
                        } else if (span instanceof URLSpan) {
                            if (delegate != null) {
                                delegate.didPressUrl(((URLSpan) span).getURL());
                            }
                        } else if (span != null) {
                            span.onClick(this);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    resetPressedLink();
                    result = true;
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                resetPressedLink();
            }
        }
        if (selectorDrawable != null) {
            if (!result && y > 0 && event.getAction() == MotionEvent.ACTION_DOWN && isClickable()) {
                selectorDrawable.setState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    selectorDrawable.setHotspot(event.getX(), event.getY());
                }
                invalidate();
                result = true;
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                selectorDrawable.setState(new int[]{});
                invalidate();
                if (!result && event.getAction() == MotionEvent.ACTION_UP) {
                    performClick();
                }
                result = true;
            }
        }
        return result || super.onTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), height + dp(8));
    }

    private Drawable selectorDrawable;
    private int selectorDrawableRadius;

    public int getSideMenuWidth() {
        return 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(getSideMenuWidth() / 2f, 0);

        int x = (getWidth() - width) / 2;
        int y = photoHeight;
        y += dp(2);
        Drawable shadowDrawable = Theme.chat_msgInMediaDrawable.getShadowDrawable();
        if (shadowDrawable != null) {
            shadowDrawable.setBounds(x, y, width + x, height + y);
            shadowDrawable.draw(canvas);
        }
        int w = AndroidUtilities.displaySize.x;
        int h = AndroidUtilities.displaySize.y;
        if (getParent() instanceof View) {
            View view = (View) getParent();
            w = view.getMeasuredWidth();
            h = view.getMeasuredHeight();
        }
        MessageDrawable drawable = (MessageDrawable) getThemedDrawable(Theme.key_drawable_msgInMedia);
        drawable.setTop((int) getY(), w, h, false, false);
        drawable.setBounds(x, 0, width + x, height);
        drawable.draw(canvas);

        if (selectorDrawable != null) {
            if (selectorDrawableRadius != SharedConfig.bubbleRadius) {
                selectorDrawableRadius = SharedConfig.bubbleRadius;
                Theme.setMaskDrawableRad(selectorDrawable, selectorDrawableRadius, selectorDrawableRadius);
            }
            selectorDrawable.setBounds(x + dp(2), dp(2), width + x - dp(2), height - dp(2));
            selectorDrawable.draw(canvas);
        }

        imageReceiver.setImageCoords(x + imagePadding, imagePadding, width - imagePadding * 2, photoHeight - imagePadding);
        imageReceiver.draw(canvas);

        Theme.chat_msgTextPaint.setColor(getThemedColor(Theme.key_chat_messageTextIn));
        Theme.chat_msgTextPaint.linkColor = getThemedColor(Theme.key_chat_messageLinkIn);
        canvas.save();
        canvas.translate(textX = dp(isPhotoVisible ? 14 : 11) + x, textY = dp(11) + y);
        if (links.draw(canvas)) {
            invalidate();
        }
        if (textLayout != null) {
            textLayout.draw(canvas);
        }
        canvas.restore();
        canvas.restore();
        wasDraw = true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
        wasDraw = false;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (textLayout != null) {
            info.setText(textLayout.getText());
        }
    }

    public boolean animating() {
        return animating;
    }

    public void setAnimating(boolean animating) {
        this.animating = animating;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private Drawable getThemedDrawable(String drawableKey) {
        Drawable drawable = resourcesProvider != null ? resourcesProvider.getDrawable(drawableKey) : null;
        return drawable != null ? drawable : Theme.getThemeDrawable(drawableKey);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == selectorDrawable || super.verifyDrawable(who);
    }

    private class BotIntroDrawable extends Drawable {

        private static final float VIEWPORT_WIDTH = 800f;
        private static final float VIEWPORT_HEIGHT = 427f;

        private final Drawable icon;
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint codePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint layerPaint = new Paint();

        private int alpha = 255;
        private ColorFilter colorFilter;

        public BotIntroDrawable(Context context) {
            icon = context.getResources().getDrawable(R.drawable.msg_folders_bots).mutate();
            icon.setColorFilter(new PorterDuffColorFilter(0xff300ab1, PorterDuff.Mode.SRC_IN));

            backgroundPaint.setShader(new RadialGradient(
                    400f, 213f, 500f,
                    0xffb694f9, 0xff6c61df,
                    Shader.TileMode.CLAMP
            ));

            codePaint.setColor(0xffffffff);
            codePaint.setStyle(Paint.Style.STROKE);
            codePaint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty() || alpha == 0) {
                return;
            }

            int layer = -1;
            if (colorFilter != null) {
                layerPaint.setColorFilter(colorFilter);
                layer = canvas.saveLayer(bounds.left, bounds.top, bounds.right, bounds.bottom, layerPaint);
            }

            float scaleX = bounds.width() / VIEWPORT_WIDTH;
            float scaleY = bounds.height() / VIEWPORT_HEIGHT;

            backgroundPaint.setAlpha(alpha);
            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(scaleX, scaleY);
            canvas.drawRect(0, 0, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, backgroundPaint);
            canvas.restore();

            codePaint.setAlpha(alpha);
            codePaint.setStrokeWidth(16.4f * Math.min(scaleX, scaleY));
            drawLine(canvas, bounds, scaleX, scaleY, 449.809f, 246.04f, 483.703f, 212.418f);
            drawLine(canvas, bounds, scaleX, scaleY, 483.703f, 212.418f, 451.291f, 179.901f);
            drawLine(canvas, bounds, scaleX, scaleY, 350.197f, 246.04f, 316.302f, 212.418f);
            drawLine(canvas, bounds, scaleX, scaleY, 316.302f, 212.418f, 348.716f, 179.901f);
            drawLine(canvas, bounds, scaleX, scaleY, 379.613f, 278f, 420.657f, 146f);

            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(scaleX, scaleY);

            drawBot(canvas, 299.339f, 60.9305f, 56f, 0.1629f);
            drawBot(canvas, 500.651f, 60.9305f, 56f, 0.1629f);
            drawBot(canvas, 579.018f, 225.477f, 56f, 0.2239f);
            drawBot(canvas, 220.979f, 225.477f, 56f, 0.2239f);

            drawBot(canvas, 548.102f, 359.18f, 52f, 0.1816f);
            drawBot(canvas, 400f, 382.172f, 52f, 0.123f);
            drawBot(canvas, 251.891f, 359.18f, 52f, 0.1816f);

            drawBot(canvas, 698.424f, 175.361f, 42f, 0.12f);
            drawBot(canvas, 572.549f, 125.064f, 42f, 0.2409f);
            drawBot(canvas, 650.19f, 61.1149f, 42f, 0.0764f);
            drawBot(canvas, 399.994f, 18.0016f, 42f, 0.1231f);
            drawBot(canvas, 149.799f, 61.1149f, 42f, 0.0753f);
            drawBot(canvas, 227.44f, 125.064f, 42f, 0.2409f);
            drawBot(canvas, 101.565f, 175.361f, 42f, 0.12f);
            drawBot(canvas, 167.057f, 316.916f, 42f, 0.15f);
            drawBot(canvas, 114.502f, 408.888f, 42f, 0.075f);
            drawBot(canvas, 678.299f, 408.888f, 42f, 0.075f);
            drawBot(canvas, 655.947f, 316.916f, 42f, 0.15f);

            canvas.restore();

            if (layer >= 0) {
                canvas.restoreToCount(layer);
            }
        }

        private void drawLine(Canvas canvas, Rect bounds, float scaleX, float scaleY, float startX, float startY, float endX, float endY) {
            canvas.drawLine(
                    bounds.left + startX * scaleX,
                    bounds.top + startY * scaleY,
                    bounds.left + endX * scaleX,
                    bounds.top + endY * scaleY,
                    codePaint
            );
        }

        private void drawBot(Canvas canvas, float centerX, float centerY, float size, float opacity) {
            int iconWidth = icon.getIntrinsicWidth();
            int iconHeight = icon.getIntrinsicHeight();
            if (iconWidth <= 0 || iconHeight <= 0) {
                return;
            }

            icon.setAlpha(Math.round(alpha * opacity));
            icon.setBounds(0, 0, iconWidth, iconHeight);

            canvas.save();
            canvas.translate(centerX, centerY);
            canvas.scale(size / iconWidth, size / iconHeight);
            canvas.translate(-iconWidth / 2f, -iconHeight / 2f);
            icon.draw(canvas);
            canvas.restore();
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(800);
        }
        @Override
        public int getIntrinsicHeight() {
            return dp(427);
        }
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
        @Override
        public void setAlpha(int alpha) {
            alpha = Math.max(0, Math.min(255, alpha));
            if (this.alpha != alpha) {
                this.alpha = alpha;
                invalidateSelf();
            }
        }
        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            if (this.colorFilter != colorFilter) {
                this.colorFilter = colorFilter;
                invalidateSelf();
            }
        }
    }
}
