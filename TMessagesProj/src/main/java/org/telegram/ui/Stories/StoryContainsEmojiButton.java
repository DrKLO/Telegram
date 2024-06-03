package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.FileRefController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmojiPacksAlert;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.TypefaceSpan;

import java.util.ArrayList;

public class StoryContainsEmojiButton extends View {

    private final Theme.ResourcesProvider resourcesProvider;

    private final TextPaint textPaint;
    private final ColorFilter colorFilter;
    private StaticLayout layout;
    private float layoutLeft, layoutWidth;
    private AnimatedEmojiSpan.EmojiGroupedSpans stack;

    private final LoadingDrawable loadingDrawable;
    private final Path loadingPath;

    private ArrayList<TLRPC.StickerSetCovered> sets;
    private ArrayList<TLRPC.InputStickerSet> inputSets;
    private TLRPC.Vector vector;
    private boolean emoji, stickers;
    private Object parentObject;
    private float loadT;

    private int lastContentWidth;
    private CharSequence toSetText;

    private int shiftDp = -12;

    public StoryContainsEmojiButton(Context context, int account, TLObject object, Object parentObject, boolean requestStickers, ArrayList<TLRPC.InputStickerSet> captionEmojiSets, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setMinimumWidth(AndroidUtilities.dp(196));
        setPadding(dp(13), dp(8), dp(13), dp(8));
        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 0, 8));
        setClickable(true);

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(dp(13));
        textPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
        colorFilter = new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), PorterDuff.Mode.SRC_IN);

        loadingDrawable = new LoadingDrawable(resourcesProvider);
        loadingDrawable.setCallback(this);
        loadingDrawable.setColors(Theme.multAlpha(0xffffffff, .2f), Theme.multAlpha(0xffffffff, .05f));
        loadingDrawable.usePath(loadingPath = new Path());
        loadingDrawable.setRadiiDp(4);

        load(account, requestStickers, object, captionEmojiSets, parentObject);
    }

    public EmojiPacksAlert getAlert() {
        if (inputSets == null) {
            AndroidUtilities.shakeViewSpring(this, shiftDp = -shiftDp);
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            return null;
        }
        return new EmojiPacksAlert(null, getContext(), resourcesProvider, inputSets);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == loadingDrawable || super.verifyDrawable(who);
    }

    public void setText(CharSequence text) {
        if (getMeasuredWidth() <= 0) {
            toSetText = text;
            return;
        }

        final int contentWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        if (contentWidth <= 0) {
            toSetText = text;
            return;
        }
        layout = new StaticLayout(text, textPaint, contentWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        layoutLeft = layout.getLineCount() > 0 ? layout.getLineLeft(0) : 0;
        layoutWidth = layout.getLineCount() > 0 ? layout.getLineWidth(0) : 0;
        stack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, stack, layout);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final boolean exactly = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY;

        final int height = getPaddingTop() + AndroidUtilities.lerp(dp(29), layout == null ? dp(29) : layout.getHeight(), loadT) + getPaddingBottom();
        setMeasuredDimension(exactly ? MeasureSpec.getSize(widthMeasureSpec) : getMinimumWidth(), height);

        final int contentWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        if (exactly && (toSetText != null || layout != null && lastContentWidth != contentWidth)) {
            setText(toSetText != null ? toSetText : layout.getText());
            toSetText = null;
            lastContentWidth = contentWidth;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (loadT < 1) {
            loadingDrawable.setAlpha((int) (0xFF * (1f - loadT)));
            loadingPath.rewind();
            loadingPath.addRect(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getPaddingTop() + dp(12), Path.Direction.CW);
            loadingPath.addRect(getPaddingLeft(), getPaddingTop() + dp(12 + 4), getPaddingLeft() + (getMeasuredWidth() - getPaddingRight() - getPaddingLeft()) * .46f, getPaddingTop() + dp(12 + 4 + 12), Path.Direction.CW);
//            loadingDrawable.setBounds(getPaddingLeft(), (getMeasuredHeight() - dp(16)) / 2, getMeasuredWidth() - getPaddingRight(), (getMeasuredHeight() + dp(16)) / 2);
            loadingDrawable.draw(canvas);
            invalidate();
        }

        if (layout != null && loadT > 0) {
            canvas.save();
            canvas.translate(getPaddingLeft() - (LocaleController.isRTL ? 0 : layoutLeft), getPaddingTop());
            textPaint.setAlpha((int) (0xFF * loadT));
            layout.draw(canvas);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, layout, stack, 0, null, 0, 0, 0, loadT, colorFilter);
            canvas.restore();
        }
    }

    private static Object lastRequestParentObject;
    private static TLRPC.Vector lastResponse;

    public void load(int currentAccount, boolean requestStickers, TLObject obj, ArrayList<TLRPC.InputStickerSet> additionalEmojiSets, Object parentObject) {
        final boolean animate[] = new boolean[] { true };
        this.parentObject = parentObject;
//        final RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
//            if (response != null) {
//                if (sets.size() == 1 && sets.get(0).set != null) {
//                    set(sets.get(0));
//                } else {
//                    set(sets.size());
//                }
//                animateLoad(animate[0]);
//            }
//        });
        if (requestStickers) {
            this.sets = new ArrayList<>();
            this.inputSets = new ArrayList<>();
            emoji = false;
            stickers = false;

            final TLRPC.TL_messages_getAttachedStickers req = new TLRPC.TL_messages_getAttachedStickers();
            if (obj instanceof TLRPC.Photo) {
                TLRPC.Photo photo = (TLRPC.Photo) obj;
                TLRPC.TL_inputStickeredMediaPhoto inputStickeredMediaPhoto = new TLRPC.TL_inputStickeredMediaPhoto();
                inputStickeredMediaPhoto.id = new TLRPC.TL_inputPhoto();
                inputStickeredMediaPhoto.id.id = photo.id;
                inputStickeredMediaPhoto.id.access_hash = photo.access_hash;
                inputStickeredMediaPhoto.id.file_reference = photo.file_reference;
                if (inputStickeredMediaPhoto.id.file_reference == null) {
                    inputStickeredMediaPhoto.id.file_reference = new byte[0];
                }
                req.media = inputStickeredMediaPhoto;
            } else if (obj instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) obj;
                TLRPC.TL_inputStickeredMediaDocument inputStickeredMediaDocument = new TLRPC.TL_inputStickeredMediaDocument();
                inputStickeredMediaDocument.id = new TLRPC.TL_inputDocument();
                inputStickeredMediaDocument.id.id = document.id;
                inputStickeredMediaDocument.id.access_hash = document.access_hash;
                inputStickeredMediaDocument.id.file_reference = document.file_reference;
                if (inputStickeredMediaDocument.id.file_reference == null) {
                    inputStickeredMediaDocument.id.file_reference = new byte[0];
                }
                req.media = inputStickeredMediaDocument;
            }
            final RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response == null) {
                    return;
                }
                TLRPC.Vector vector = this.vector = (TLRPC.Vector) response;
                lastRequestParentObject = parentObject;
                lastResponse = vector;
                for (int i = 0; i < vector.objects.size(); ++i) {
                    TLRPC.StickerSetCovered setCovered = (TLRPC.StickerSetCovered) vector.objects.get(i);
                    sets.add(setCovered);
                    if (setCovered.set != null) {
                        inputSets.add(MediaDataController.getInputStickerSet(setCovered.set));
                        if (setCovered.set.emojis) {
                            emoji = true;
                        } else if (!setCovered.set.masks) {
                            stickers = true;
                        }
                    }
                }
                final int count = (additionalEmojiSets != null ? additionalEmojiSets.size() : 0) + (sets == null ? 0 : sets.size());
                if (inputSets != null && additionalEmojiSets != null && !additionalEmojiSets.isEmpty()) {
                    for (int i = 0; i < additionalEmojiSets.size(); ++i) {
                        TLRPC.InputStickerSet inputStickerSet = additionalEmojiSets.get(i);
                        long id = inputStickerSet.id;
                        boolean found = false;
                        for (int j = 0; j < inputSets.size(); ++j) {
                            if (inputSets.get(j).id == id) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            inputSets.add(inputStickerSet);
                        }
                    }
                    emoji = true;
                    this.vector = null;
                }
                if (count == 1) {
                    if (sets.size() >= 1) {
                        set(sets.get(0));
                    } else if (additionalEmojiSets != null && additionalEmojiSets.size() >= 1) {
                        animate[0] = false;
                        TLRPC.InputStickerSet inputSet = additionalEmojiSets.get(0);
                        MediaDataController.getInstance(currentAccount).getStickerSet(inputSet, 0, false, set -> {
                           set(set);
                           animateLoad(false);
                        });
                        return;
                    } else {
                        set(0);
                    }
                } else {
                    set(count);
                }
                animateLoad(animate[0]);
            });
            if (lastRequestParentObject == parentObject && lastResponse != null) {
                animate[0] = false;
                requestDelegate.run(lastResponse, null);
                return;
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error != null && FileRefController.isFileRefError(error.text) && parentObject != null) {
                    FileRefController.getInstance(currentAccount).requestReference(parentObject, req, requestDelegate);
                    return;
                }
                requestDelegate.run(response, error);
            });
        } else {
            emoji = true;
            stickers = false;
            this.inputSets = new ArrayList<>();
            inputSets.addAll(additionalEmojiSets);
            if (inputSets.size() == 1) {
                MediaDataController.getInstance(currentAccount).getStickerSet(inputSets.get(0), 0, false, set -> {
                    set(set);
                    animateLoad(true);
                });
            } else {
                set(inputSets.size());
                animateLoad(false);
            }
        }
    }

    private void set(TLRPC.TL_messages_stickerSet set) {
        if (set == null) {
            return;
        }
        SpannableString pack = new SpannableString("x " + set.set.title);
        pack.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chat_messageLinkIn, loadingDrawable.resourcesProvider)), 0, pack.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        pack.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, pack.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TLRPC.Document document = null;
        ArrayList<TLRPC.Document> documents = set.documents;
        for (int i = 0; i < documents.size(); ++i) {
            if (documents.get(i).id == set.set.thumb_document_id) {
                document = documents.get(i);
                break;
            }
        }
        if (document == null && !documents.isEmpty()) {
            document = documents.get(0);
        }
        CharSequence packString = pack;
        if (document != null) {
            pack.setSpan(new AnimatedEmojiSpan(document, textPaint.getFontMetricsInt()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            packString = pack.subSequence(2, pack.length());
        }
        String string;
        if (emoji && stickers) {
            string = LocaleController.getString(R.string.StoryContainsStickersEmojiFrom);
        } else if (emoji) {
            string = LocaleController.getString(R.string.StoryContainsEmojiFrom);
        } else {
            string = LocaleController.getString(R.string.StoryContainsStickersFrom);
        }
        setText(AndroidUtilities.replaceCharSequence("%s", string, packString));
    }

    private void set(TLRPC.StickerSetCovered set) {
        SpannableString pack = new SpannableString("x " + set.set.title);
        pack.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chat_messageLinkIn, loadingDrawable.resourcesProvider)), 0, pack.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        pack.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, pack.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TLRPC.Document document = set.cover;
        if (document == null && set instanceof TLRPC.TL_stickerSetFullCovered) {
            ArrayList<TLRPC.Document> documents = ((TLRPC.TL_stickerSetFullCovered) set).documents;
            for (int i = 0; i < documents.size(); ++i) {
                if (documents.get(i).id == set.set.thumb_document_id) {
                    document = documents.get(i);
                }
            }
            if (document == null && !documents.isEmpty()) {
                document = documents.get(0);
            }
        }
        CharSequence packString = pack;
        if (document != null) {
            pack.setSpan(new AnimatedEmojiSpan(document, textPaint.getFontMetricsInt()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            packString = pack.subSequence(2, pack.length());
        }
        String string;
        if (emoji && stickers) {
            string = LocaleController.getString(R.string.StoryContainsStickersEmojiFrom);
        } else if (emoji) {
            string = LocaleController.getString(R.string.StoryContainsEmojiFrom);
        } else {
            string = LocaleController.getString(R.string.StoryContainsStickersFrom);
        }
        setText(AndroidUtilities.replaceCharSequence("%s", string, packString));
    }

    private void set(int setsCount) {
        if (emoji && stickers) {
            setText(AndroidUtilities.replaceSingleTag(LocaleController.formatPluralString("StoryContainsStickersEmoji", setsCount), 0, Theme.getColor(Theme.key_chat_messageLinkIn, loadingDrawable.resourcesProvider), null));
        } else if (emoji) {
            setText(AndroidUtilities.replaceSingleTag(LocaleController.formatPluralString("StoryContainsEmoji", setsCount), 0, Theme.getColor(Theme.key_chat_messageLinkIn, loadingDrawable.resourcesProvider), null));
        } else {
            setText(AndroidUtilities.replaceSingleTag(LocaleController.formatPluralString("StoryContainsStickers", setsCount), 0, Theme.getColor(Theme.key_chat_messageLinkIn, loadingDrawable.resourcesProvider), null));
        }
    }

    private ValueAnimator loadAnimator;
    private void animateLoad(boolean animated) {
        if (loadAnimator != null) {
            loadAnimator.cancel();
        }
        if (animated) {
            loadAnimator = ValueAnimator.ofFloat(loadT, 1f);
            final boolean remeasure = layout == null || Math.abs(getMeasuredHeight() - (getPaddingTop() + layout.getHeight() + getPaddingBottom())) > dp(3);
            loadAnimator.addUpdateListener(anm -> {
                loadT = (float) anm.getAnimatedValue();
                invalidate();
                if (remeasure) {
                    requestLayout();
                }
            });
            loadAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            loadAnimator.setStartDelay(150);
            loadAnimator.setDuration(400);
            loadAnimator.start();
        } else {
            loadT = 1f;
            invalidate();
            post(this::requestLayout);
        }
    }
}
