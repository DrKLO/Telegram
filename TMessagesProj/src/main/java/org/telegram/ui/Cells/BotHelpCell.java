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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileRefController;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatEditActivity;
import org.telegram.ui.Components.ClipRoundedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.LaunchActivity;

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
                imageReceiver.setImageBitmap(new ClipRoundedDrawable(getContext().getResources().getDrawable(R.drawable.setup_bot_header).mutate()));

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
        Theme.MessageDrawable drawable = (Theme.MessageDrawable) getThemedDrawable(Theme.key_drawable_msgInMedia);
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
}
