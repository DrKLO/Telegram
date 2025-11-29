package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ChatListItemAnimator;
import androidx.recyclerview.widget.GridLayoutManagerFixed;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatMessageSharedResources;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.spoilers.SpoilerEffect2;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.KeyboardNotifier;

import java.util.ArrayList;

public class MessageSendPreview extends Dialog implements NotificationCenter.NotificationCenterDelegate {

    public final Context context;
    public final Theme.ResourcesProvider resourcesProvider;
    public final int currentAccount = UserConfig.selectedAccount;

    private final Rect insets = new Rect();
    private Bitmap blurBitmap;
    private BitmapShader blurBitmapShader;
    private Paint blurBitmapPaint;
    private Matrix blurMatrix;

    private boolean sent;
    private boolean opening, closing;
    private boolean openInProgress;
    private boolean firstOpenFrame;
    private boolean firstOpenFrame2;
    private float openProgress;
    private float openProgress2;

    private final FrameLayout windowView;
    private final FrameLayout containerView;
    private final FrameLayout effectsView;

    private long effectId;
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable effectDrawable;

    private int messagesContainerTopPadding;
    private final RecyclerListView chatListView;
    private final RecyclerView.Adapter adapter;
    private final GridLayoutManagerFixed chatLayoutManager;
    private final ArrayList<MessageObject> messageObjects = new ArrayList<>();
    private int messageObjectsWidth;
    private final LongSparseArray<MessageObject.GroupedMessages> groupedMessagesMap = new LongSparseArray<>();
    private ChatMessageCell mainMessageCell;
    private int mainMessageCellId;
    private int getMainMessageCellPosition() {
        return groupedMessagesMap.isEmpty() || messageObjects.size() < 10 ? 0 : messageObjects.size() % 10;
    }
    private EditTextCaption editText;
    private Paint editTextBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Utilities.Callback2<Canvas, Utilities.Callback0Return<Boolean>> drawEditText;
    private Utilities.Callback<Canvas> drawEditTextBackground;
    private ChatActivityEnterView.SendButton anchorSendButton;
    private ChatActivityEnterView.SendButton sendButton;
    private int sendButtonWidth, sendButtonRight;
    private View optionsView;
    private EmojiAnimationsOverlay effectOverlay;

    private boolean keyboardVisible;

    private float effectSelectorContainerY;
    private FrameLayout effectSelectorContainer;
    private ReactionsContainerLayout effectSelector;
    private boolean effectSelectorShown;

    private boolean layoutDone;
    public boolean allowRelayout;

    private SpoilerEffect2 spoilerEffect2;

    public MessageSendPreview(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, R.style.TransparentDialog);
        this.context = context;
        this.resourcesProvider = resourcesProvider;

        windowView = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (openProgress > 0 && blurBitmapPaint != null) {
                    blurMatrix.reset();
                    final float s = (float) getWidth() / blurBitmap.getWidth();
                    blurMatrix.postScale(s, s);
                    blurBitmapShader.setLocalMatrix(blurMatrix);

                    blurBitmapPaint.setAlpha((int) (0xFF * openProgress));
                    canvas.drawRect(0, 0, getWidth(), getHeight(), blurBitmapPaint);
                }
                super.dispatchDraw(canvas);
            }

            @Override
            public boolean dispatchKeyEventPreIme(KeyEvent event) {
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    onBackPressed();
                    return true;
                }
                return super.dispatchKeyEventPreIme(event);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (!layoutDone || allowRelayout) {
                    MessageSendPreview.this.layout();
                    layoutDone = true;
                }
            }
        };
        spoilerEffect2 = SpoilerEffect2.getInstance(SpoilerEffect2.TYPE_PREVIEW, windowView, windowView);
        windowView.setOnClickListener(v -> {
            onBackPressed();
        });
        windowView.getViewTreeObserver().addOnGlobalFocusChangeListener(new ViewTreeObserver.OnGlobalFocusChangeListener() {
            @Override
            public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                if (!focusable && newFocus instanceof EditText) {
                    AndroidUtilities.hideKeyboard(editText);
                    AndroidUtilities.runOnUIThread(() -> {
                        makeFocusable();
                        AndroidUtilities.runOnUIThread(() -> {
                            AndroidUtilities.showKeyboard(newFocus);
                            if (anchorSendButton != null) {
                                anchorSendButton.getLocationOnScreen(sendButtonInitialPosition);
//                                sendButtonInitialPosition[0] = Math.min(sendButtonInitialPosition[0] + anchorSendButton.getWidth(), AndroidUtilities.displaySize.x) - anchorSendButton.getWidth();
                                sendButtonInitialPosition[0] += anchorSendButton.getWidth() - anchorSendButton.width(anchorSendButton.getHeight()) - dp(6);
                            }
                        }, 100);
                    }, 200);
                }
            }
        });
        containerView = new SizeNotifierFrameLayout(context) {
            final int[] pos = new int[2];
            final int[] pos2 = new int[2];
            int chatListViewTy = 0;
            boolean gotDestCellPos;
            final int[] destCellPos = new int[2];
            private GradientClip clip = new GradientClip();
            private AnimatedFloat destCellY = new AnimatedFloat(0, 100, CubicBezierInterpolator.EASE_OUT_QUINT);
            private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (openInProgress && mainMessageCell != null && mainMessageCell.getCurrentPosition() == null) {
                    if (firstOpenFrame) {
                        if (editText != null) {
                            editText.setAlpha(0f);
                        }
                        firstOpenFrame = false;
                    }

                    final boolean isSticker = mainMessageCell.getMessageObject() != null && mainMessageCell.getMessageObject().type == MessageObject.TYPE_ANIMATED_STICKER;
                    final float textX = isSticker ? mainMessageCell.getPhotoImage().getImageX() : mainMessageCell.getTextX();
                    final float textY = isSticker ? mainMessageCell.getPhotoImage().getImageY() : mainMessageCell.getTextY();
                    final float messageCellTextX = chatListView.getX() + mainMessageCell.getX() + textX;
                    final float messageCellTextY = chatListView.getY() + mainMessageCell.getY() + textY;
                    final float messageCellTextSize = (mainMessageCell.getMessageObject() != null ? mainMessageCell.getMessageObject().getTextPaint() : Theme.chat_msgTextPaint).getTextSize();

                    float top, bottom;
                    float x, y, ts;
                    if (editText != null) {
                        editText.getLocationOnScreen(pos);
                        final float editTextX = pos[0] + editText.getPaddingLeft();
                        final float editTextY = pos[1] + editText.getPaddingTop() - editText.getScrollY();
                        final float editTextSize = editText.getTextSize();

                        top = pos[1];
                        bottom = pos[1] + editText.getMeasuredHeight();
                        x = AndroidUtilities.lerp(editTextX, messageCellTextX, openProgress);
                        y = AndroidUtilities.lerp(editTextY, messageCellTextY, openProgress);
                        ts = AndroidUtilities.lerp(editTextSize, messageCellTextSize, openProgress);
                    } else {
                        top = 0;
                        bottom = getHeight();
                        x = messageCellTextX;
                        y = messageCellTextY;
                        ts = messageCellTextSize;
                    }
                    final float alpha = openProgress;

                    final float clipTop = AndroidUtilities.lerp(destCell != null ? destClipTop : top, chatListView.getY() + chatListView.getHeight() * (1f - chatListView.getScaleY()), openProgress);
                    final float clipTopAlpha = AndroidUtilities.lerp(0, chatListView.canScrollVertically(-1) ? 1f : 0f, openProgress);
                    final float clipBottom = AndroidUtilities.lerp(destCell != null ? destClipBottom : bottom, chatListView.getY() + chatListView.getHeight(), openProgress);
                    final float clipBottomAlpha = AndroidUtilities.lerp(0, chatListView.canScrollVertically(1) ? 1f : 0f, openProgress);

                    canvas.saveLayerAlpha(0, clipTop + 1, getWidth(), clipBottom - 1, 0xFF, Canvas.ALL_SAVE_FLAG);

                    if (editText != null) {
                        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * (1f - alpha)), Canvas.ALL_SAVE_FLAG);
                        canvas.translate(x, y);
                        canvas.translate(-editText.getX() - editText.getPaddingLeft(), -editText.getY() - editText.getPaddingTop() + editText.getScrollY());
                        float wasAlpha = editText.getAlpha();
                        editText.setAlpha(1f);
                        if (openProgress < .001f) {
                            if (drawEditTextBackground != null) {
                                canvas.save();
                                canvas.translate(0, editText.getY());
                                canvas.saveLayerAlpha(editText.getX() + editText.getPaddingLeft(), 0, editText.getX() + editText.getPaddingLeft() + editText.getWidth() - editText.getPaddingRight(), editText.getHeight(), (int) (0xFF * (1f - openProgress / .1f)), Canvas.ALL_SAVE_FLAG);
                                drawEditTextBackground.run(canvas);
                                canvas.restore();
                                canvas.restore();
                            } else {
                                editTextBackgroundPaint.setColor(Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider));
                                editTextBackgroundPaint.setAlpha((int) (editTextBackgroundPaint.getAlpha() * (1f - openProgress / .1f)));
                                canvas.drawRect(+editText.getPaddingLeft(), editText.getY(), editText.getX() + editText.getPaddingLeft() + editText.getWidth() - editText.getPaddingRight(), editText.getY() + editText.getHeight(), editTextBackgroundPaint);
                            }
                        }
                        if (drawEditText != null) {
                            drawEditText.run(canvas, () -> {
                                canvas.save();
                                canvas.translate(editText.getX(), editText.getY() - editText.getScrollY());
                                final float s = ts / editText.getTextSize();
                                canvas.scale(s, s, editText.getPaddingLeft(), editText.getPaddingTop());
                                editText.draw(canvas);
                                canvas.restore();
                                return true;
                            });
                        }
                        editText.setAlpha(wasAlpha);
                        canvas.restore();
                    }

                    mainMessageCell.getTransitionParams().ignoreAlpha = true;
                    if (destCell == null) {
                        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
                        canvas.translate(x, y);
                        canvas.translate(-textX, -textY);
                        final float s2 = AndroidUtilities.lerp(1f, chatListView.getScaleX(), openProgress);
                        canvas.scale(s2, s2, -mainMessageCell.getX() + chatListView.getWidth(), -mainMessageCell.getY() + chatListView.getHeight());
                        final float s = ts / messageCellTextSize;
                        canvas.scale(s, s, textX, textY);
                        if (mainMessageCell.drawBackgroundInParent()) {
                            canvas.save();
                            canvas.translate(0, mainMessageCell.getPaddingTop());
                            mainMessageCell.drawBackgroundInternal(canvas, true);
                            canvas.restore();
                        }
                        mainMessageCell.draw(canvas);
                        canvas.restore();
                    } else {
                        destCell.getLocationInWindow(pos2);
                        final int chatListViewTy = (destCell.getParent() instanceof View) ? (int) ((View) destCell.getParent()).getTranslationY() : 0;
                        if (this.chatListViewTy > chatListViewTy && destCellPos[1] - pos2[1] > this.chatListViewTy) {

                        } else {
                            destCellPos[0] = pos2[0];
                            destCellPos[1] = pos2[1];
                        }
                        this.chatListViewTy = chatListViewTy;
                        float tx = AndroidUtilities.lerp(chatListView.getX() + mainMessageCell.getX(), destCellPos[0], 1f - openProgress);
                        float ty = AndroidUtilities.lerp(chatListView.getY() + mainMessageCell.getY(), destCellPos[1], 1f - openProgress);
                        canvas.save();
                        canvas.translate(tx, ty);
                        final float s2 = AndroidUtilities.lerp(1f, chatListView.getScaleX(), openProgress);
                        canvas.scale(s2, s2, -mainMessageCell.getX() + chatListView.getWidth(), -mainMessageCell.getY() + chatListView.getHeight());
                        mainMessageCell.getTransitionParams().animateChangeProgress = 1f - openProgress;
                        mainMessageCell.getTransitionParams().deltaLeft = cellDelta.left * openProgress;
                        mainMessageCell.getTransitionParams().deltaTop = cellDelta.top * openProgress;
                        mainMessageCell.getTransitionParams().deltaRight = cellDelta.right * openProgress;
                        mainMessageCell.getTransitionParams().deltaBottom = cellDelta.bottom * openProgress;
                        mainMessageCell.setTimeAlpha(1f - openProgress);
                        if (mainMessageCell.drawBackgroundInParent()) {
                            canvas.saveLayerAlpha(0, 0, destCell.getWidth(), destCell.getHeight(), (int) (0xFF * openProgress), Canvas.ALL_SAVE_FLAG);
                            canvas.translate(0, mainMessageCell.getPaddingTop());
                            mainMessageCell.drawBackgroundInternal(canvas, true);
                            canvas.restore();
                            canvas.saveLayerAlpha(0, 0, destCell.getWidth(), destCell.getHeight(), (int) (0xFF * (1f - openProgress)), Canvas.ALL_SAVE_FLAG);
                            canvas.translate(0, destCell.getPaddingTop());
                            destCell.drawBackgroundInternal(canvas, true);
                            canvas.restore();
                        }
                        mainMessageCell.draw(canvas);
                        if (mainMessageCell.getTransitionParams().animateBackgroundBoundsInner) {
                            mainMessageCell.drawNamesLayout(canvas, 1f);
                            mainMessageCell.drawTime(canvas, 1f - openProgress, true);
                        }
                        canvas.restore();
                    }

                    canvas.save();
                    AndroidUtilities.rectTmp.set(0, clipTop, getWidth(), clipTop + dp(14));
                    clip.draw(canvas, AndroidUtilities.rectTmp, true, clipTopAlpha);
                    AndroidUtilities.rectTmp.set(0, clipBottom - dp(14), getWidth(), clipBottom);
                    clip.draw(canvas, AndroidUtilities.rectTmp, false, clipBottomAlpha);
                    canvas.restore();

                    canvas.restore();
                }
                if (openInProgress) {
                    if (firstOpenFrame2) {
                        if (anchorSendButton != null) {
                            anchorSendButton.setAlpha(0f);
                        }
                        firstOpenFrame2 = false;
                    }
                    canvas.save();
                    canvas.translate(
                        AndroidUtilities.lerp(sendButtonInitialPosition[0] - (sendButton.getWidth() - sendButton.width(sendButton.getHeight())) + dp(6), sendButton.getX(), openProgress),
                        AndroidUtilities.lerp(sendButtonInitialPosition[1], sendButton.getY(), openProgress)
                    );
                    if (closing && sent) {
                        canvas.saveLayerAlpha(0, 0, sendButton.getWidth(), sendButton.getHeight(), (int) (0xFF * openProgress), Canvas.ALL_SAVE_FLAG);
                    }
                    sendButton.draw(canvas);
                    if (closing && sent) {
                        canvas.restore();
                    }
                    canvas.restore();
                }
                super.dispatchDraw(canvas);
                if (cameraRect != null) {
                    if (effectDrawable == null) {
                        effectDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, dp(24), AnimatedEmojiDrawable.CACHE_TYPE_MESSAGE_EFFECT_MINI);
                    }
                    AndroidUtilities.rectTmp2.set(
                            (int) (cameraRect.right - dp(12) - dp(24)),
                            (int) (cameraRect.bottom - dp(12) - dp(24)),
                            (int) (cameraRect.right - dp(12)),
                            (int) (cameraRect.bottom - dp(12))
                    );
                    AndroidUtilities.rectTmp.set(AndroidUtilities.rectTmp2);
                    AndroidUtilities.rectTmp.inset(-dp(12), -dp(6));
                    final float r = AndroidUtilities.rectTmp.height() / 2f;
                    backgroundPaint.setColor(0x1e000000);
                    backgroundPaint.setAlpha((int) (0x1e * effectDrawable.isNotEmpty() * openProgress));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint);
                    effectDrawable.setBounds(AndroidUtilities.rectTmp2);
                    effectDrawable.setAlpha((int) (0xFF * openProgress));
                    effectDrawable.draw(canvas);
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (openInProgress && (child == sendButton || child == mainMessageCell && mainMessageCell != null && mainMessageCell.getCurrentPosition() == null))
                    return false;
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        containerView.setClipToPadding(false);
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        if (Build.VERSION.SDK_INT >= 21) {
            windowView.setFitsSystemWindows(true);
            windowView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Insets r = insets.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars());
                        MessageSendPreview.this.insets.set(r.left, r.top, r.right, r.bottom);
                    } else {
                        MessageSendPreview.this.insets.set(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                    }
                    containerView.setPadding(MessageSendPreview.this.insets.left, MessageSendPreview.this.insets.top, MessageSendPreview.this.insets.right, MessageSendPreview.this.insets.bottom);
                    windowView.requestLayout();
                    if (Build.VERSION.SDK_INT >= 30) {
                        return WindowInsets.CONSUMED;
                    } else {
                        return insets.consumeSystemWindowInsets();
                    }
                }
            });
        }

        chatListView = new RecyclerListView(context, resourcesProvider) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int heightdown = dp(messageObjects.isEmpty() ? -6 : 48) + (optionsView == null ? 0 : optionsView.getMeasuredHeight());
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                    Math.max(0, AndroidUtilities.displaySize.y - heightdown - dp(8) - insets.top),
                    MeasureSpec.AT_MOST
                );
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);

                final int right = Math.max(sendButtonWidth, (int) -(sendButtonInitialPosition[0] + dp(7) - getMeasuredWidth()));
                final int diff = Math.max(0, messageObjectsWidth - (getMeasuredWidth() - right - dp(8 + (groupedMessagesMap.isEmpty() ? 0 : 40))));
                final float scale = (float) Math.max(1, getMeasuredWidth() - right) / Math.max(1, getMeasuredWidth() - right - dp(8) + diff);
                setPivotX(getMeasuredWidth());
                setPivotY(getMeasuredHeight());
                setScaleX(scale);
                setScaleY(scale);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                for (int a = 0; a < getChildCount(); a++) {
                    View child = getChildAt(a);
                    if (child.getTop() != 0 && child instanceof MessageCell) {
                        ((MessageCell) child).top = child.getTop();
                        ((MessageCell) child).bottom = child.getBottom();
                        ((MessageCell) child).pastId = ((MessageCell) child).getMessageObject().getId();
                    }
                }
                super.onLayout(changed, l, t, r, b);
            }

            private final ArrayList<MessageObject.GroupedMessages> drawingGroups = new ArrayList<>(10);

            private final AnimatedFloat top = new AnimatedFloat(this, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final AnimatedFloat bottom = new AnimatedFloat(this, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final GradientClip clip = new GradientClip();

            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.saveLayerAlpha(0, getScrollY() + 1, getWidth(), getScrollY() + getHeight() - 1, 0xFF, Canvas.ALL_SAVE_FLAG);
                canvas.save();
                drawChatBackgroundElements(canvas);
                super.dispatchDraw(canvas);
                drawChatForegroundElements(canvas);
                canvas.save();

                float topAlpha = top.set(canScrollVertically(-1));
                float bottomAlpha = bottom.set(canScrollVertically(1));

                AndroidUtilities.rectTmp.set(0, getScrollY(), getWidth(), getScrollY() + dp(14));
                clip.draw(canvas, AndroidUtilities.rectTmp, true, topAlpha);

                AndroidUtilities.rectTmp.set(0, getScrollY() + getHeight() - dp(14), getWidth(), getScrollY() + getHeight());
                clip.draw(canvas, AndroidUtilities.rectTmp, false, bottomAlpha);

                canvas.restore();
                canvas.restore();
                canvas.restore();
            }

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (openInProgress && (child == mainMessageCell && mainMessageCell != null && mainMessageCell.getCurrentPosition() == null || child == sendButton))
                    return false;
                if (child instanceof ChatMessageCell) {
                    ChatMessageCell cell = (ChatMessageCell) child;
                    cell.setInvalidatesParent(true);
                    cell.drawCheckBox(canvas);
                    canvas.save();
                    canvas.translate(cell.getX(), cell.getY());
                    canvas.scale(cell.getScaleX(), cell.getScaleY(), cell.getPivotX(), cell.getPivotY());
                    if (cell.drawBackgroundInParent() && cell.getCurrentPosition() == null) {
                        canvas.save();
                        canvas.translate(0, cell.getPaddingTop());
                        cell.drawBackgroundInternal(canvas, true);
                        canvas.restore();
                    }
                    canvas.restore();
                    boolean r = super.drawChild(canvas, child, drawingTime);
                    canvas.save();
                    canvas.translate(cell.getX(), cell.getY() + cell.getPaddingTop());
                    canvas.scale(cell.getScaleX(), cell.getScaleY(), cell.getPivotX(), cell.getPivotY());
                    if ((cell.getCurrentPosition() != null && ((cell.getCurrentPosition().flags & cell.captionFlag()) != 0 && (cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_LEFT) != 0 || cell.getCurrentMessagesGroup() != null && cell.getCurrentMessagesGroup().isDocuments))) {
                        cell.drawCaptionLayout(canvas, false, cell.getAlpha());
                    }
                    if ((cell.getCurrentPosition() != null && ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_BOTTOM) != 0 && (cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_LEFT) != 0 || cell.getCurrentMessagesGroup() != null && cell.getCurrentMessagesGroup().isDocuments))) {
                        cell.drawReactionsLayout(canvas, cell.getAlpha(), null);
                        cell.drawCommentLayout(canvas, cell.getAlpha());
                    }
                    if (cell.getCurrentPosition() != null) {
                        cell.drawNamesLayout(canvas, cell.getAlpha());
                    }
                    if (cell.getCurrentPosition() == null || cell.getCurrentPosition().last) {
                        cell.drawTime(canvas, cell.getAlpha(), true);
                    }
                    cell.drawOutboundsContent(canvas);
                    cell.getTransitionParams().recordDrawingStatePreview();
                    canvas.restore();
                    cell.setInvalidatesParent(false);
                    return r;
                }
                return true;
            }

            private void drawChatBackgroundElements(Canvas canvas) {
                int count = getChildCount();
                MessageObject.GroupedMessages lastDrawnGroup = null;

                for (int a = 0; a < count; a++) {
                    View child = getChildAt(a);
                    if (child instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) child;
                        MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                        if (group != null && group == lastDrawnGroup) {
                            continue;
                        }
                        lastDrawnGroup = group;
                    }
                }
                MessageObject.GroupedMessages scrimGroup = null;
                for (int k = 0; k < 3; k++) {
                    drawingGroups.clear();
                    if (k == 2 && !chatListView.isFastScrollAnimationRunning()) {
                        continue;
                    }
                    for (int i = 0; i < count; i++) {
                        View child = chatListView.getChildAt(i);
                        if (child instanceof ChatMessageCell) {
                            ChatMessageCell cell = (ChatMessageCell) child;
                            if (child.getY() > chatListView.getHeight() || child.getY() + child.getHeight() < 0) {
                                continue;
                            }
                            MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                            if (group == null || (k == 0 && group.messages.size() == 1) || (k == 1 && !group.transitionParams.drawBackgroundForDeletedItems)) {
                                continue;
                            }
                            if ((k == 0 && cell.getMessageObject().deleted) || (k == 1 && !cell.getMessageObject().deleted)) {
                                continue;
                            }
                            if ((k == 2 && !cell.willRemovedAfterAnimation()) || (k != 2 && cell.willRemovedAfterAnimation())) {
                                continue;
                            }

                            if (!drawingGroups.contains(group)) {
                                group.transitionParams.left = 0;
                                group.transitionParams.top = 0;
                                group.transitionParams.right = 0;
                                group.transitionParams.bottom = 0;

                                group.transitionParams.pinnedBotton = false;
                                group.transitionParams.pinnedTop = false;
                                group.transitionParams.cell = cell;
                                drawingGroups.add(group);
                            }

                            group.transitionParams.pinnedTop = cell.isPinnedTop();
                            group.transitionParams.pinnedBotton = cell.isPinnedBottom();

                            int left = (int) (cell.getX() + cell.getBackgroundDrawableLeft());
                            int right = (int) (cell.getX() + cell.getBackgroundDrawableRight());
                            int top = (int) (cell.getY() + cell.getPaddingTop() + cell.getBackgroundDrawableTop());
                            int bottom = (int) (cell.getY() + cell.getPaddingTop() + cell.getBackgroundDrawableBottom());

                            if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                                top -= dp(10);
                            }

                            if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                                bottom += dp(10);
                            }

                            if (cell.willRemovedAfterAnimation()) {
                                group.transitionParams.cell = cell;
                            }

                            if (group.transitionParams.top == 0 || top < group.transitionParams.top) {
                                group.transitionParams.top = top;
                            }
                            if (group.transitionParams.bottom == 0 || bottom > group.transitionParams.bottom) {
                                group.transitionParams.bottom = bottom;
                            }
                            if (group.transitionParams.left == 0 || left < group.transitionParams.left) {
                                group.transitionParams.left = left;
                            }
                            if (group.transitionParams.right == 0 || right > group.transitionParams.right) {
                                group.transitionParams.right = right;
                            }
                        }
                    }

                    for (int i = 0; i < drawingGroups.size(); i++) {
                        MessageObject.GroupedMessages group = drawingGroups.get(i);
                        if (group == scrimGroup) {
                            continue;
                        }
                        float x = group.transitionParams.cell.getNonAnimationTranslationX(true);
                        float l = (group.transitionParams.left + x + group.transitionParams.offsetLeft);
                        float t = (group.transitionParams.top + group.transitionParams.offsetTop);
                        float r = (group.transitionParams.right + x + group.transitionParams.offsetRight);
                        float b = (group.transitionParams.bottom + group.transitionParams.offsetBottom);

//                        if (!group.transitionParams.backgroundChangeBounds) {
//                            t += group.transitionParams.cell.getTranslationY();
//                            b += group.transitionParams.cell.getTranslationY();
//                        }

                        if (t < -dp(20)) {
                            t = -dp(20);
                        }

                        if (b > chatListView.getMeasuredHeight() + dp(20)) {
                            b = chatListView.getMeasuredHeight() + dp(20);
                        }

                        boolean useScale = group.transitionParams.cell.getScaleX() != 1f || group.transitionParams.cell.getScaleY() != 1f;
                        if (useScale) {
                            canvas.save();
                            canvas.scale(group.transitionParams.cell.getScaleX(), group.transitionParams.cell.getScaleY(), l + (r - l) / 2, t + (b - t) / 2);
                        }

                        group.transitionParams.cell.drawBackground(canvas, (int) l, (int) t, (int) r, (int) b, group.transitionParams.pinnedTop, group.transitionParams.pinnedBotton, false, 0);
                        group.transitionParams.cell = null;
                        group.transitionParams.drawCaptionLayout = group.hasCaption;
                        if (useScale) {
                            canvas.restore();
                            for (int ii = 0; ii < count; ii++) {
                                View child = chatListView.getChildAt(ii);
                                if (child instanceof ChatMessageCell && ((ChatMessageCell) child).getCurrentMessagesGroup() == group) {
                                    ChatMessageCell cell = ((ChatMessageCell) child);
                                    int left = cell.getLeft();
                                    int top = cell.getTop();
                                    child.setPivotX(l - left + (r - l) / 2);
                                    child.setPivotY(t - top + (b - t) / 2);
                                }
                            }
                        }
                    }
                }
            }

            private void drawChatForegroundElements(Canvas canvas) {
                int count = getChildCount();
                MessageObject.GroupedMessages lastDrawnGroup = null;

                for (int a = 0; a < count; a++) {
                    View child = getChildAt(a);
                    if (child instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) child;
                        MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                        if (group != null && group == lastDrawnGroup) {
                            continue;
                        }
                        lastDrawnGroup = group;
                        if (group == null) {
                            drawStarsPrice(canvas, cell.getBoundsLeft(), cell.getY(), cell.getBoundsRight(), cell.getY() + cell.getHeight());
                        }
                    }
                }
                MessageObject.GroupedMessages scrimGroup = null;
                for (int k = 0; k < 3; k++) {
                    drawingGroups.clear();
                    if (k == 2 && !chatListView.isFastScrollAnimationRunning()) {
                        continue;
                    }
                    for (int i = 0; i < count; i++) {
                        View child = chatListView.getChildAt(i);
                        if (child instanceof ChatMessageCell) {
                            ChatMessageCell cell = (ChatMessageCell) child;
                            if (child.getY() > chatListView.getHeight() || child.getY() + child.getHeight() < 0) {
                                continue;
                            }
                            MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                            if (group == null || (k == 0 && group.messages.size() == 1) || (k == 1 && !group.transitionParams.drawBackgroundForDeletedItems)) {
                                continue;
                            }
                            if ((k == 0 && cell.getMessageObject().deleted) || (k == 1 && !cell.getMessageObject().deleted)) {
                                continue;
                            }
                            if ((k == 2 && !cell.willRemovedAfterAnimation()) || (k != 2 && cell.willRemovedAfterAnimation())) {
                                continue;
                            }

                            if (!drawingGroups.contains(group)) {
                                group.transitionParams.left = 0;
                                group.transitionParams.top = 0;
                                group.transitionParams.right = 0;
                                group.transitionParams.bottom = 0;

                                group.transitionParams.pinnedBotton = false;
                                group.transitionParams.pinnedTop = false;
                                group.transitionParams.cell = cell;
                                drawingGroups.add(group);
                            }

                            group.transitionParams.pinnedTop = cell.isPinnedTop();
                            group.transitionParams.pinnedBotton = cell.isPinnedBottom();

                            int left = (int) (cell.getX() + cell.getBackgroundDrawableLeft());
                            int right = (int) (cell.getX() + cell.getBackgroundDrawableRight());
                            int top = (int) (cell.getY() + cell.getPaddingTop() + cell.getBackgroundDrawableTop());
                            int bottom = (int) (cell.getY() + cell.getPaddingTop() + cell.getBackgroundDrawableBottom());

                            if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                                top -= dp(10);
                            }

                            if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                                bottom += dp(10);
                            }

                            if (cell.willRemovedAfterAnimation()) {
                                group.transitionParams.cell = cell;
                            }

                            if (group.transitionParams.top == 0 || top < group.transitionParams.top) {
                                group.transitionParams.top = top;
                            }
                            if (group.transitionParams.bottom == 0 || bottom > group.transitionParams.bottom) {
                                group.transitionParams.bottom = bottom;
                            }
                            if (group.transitionParams.left == 0 || left < group.transitionParams.left) {
                                group.transitionParams.left = left;
                            }
                            if (group.transitionParams.right == 0 || right > group.transitionParams.right) {
                                group.transitionParams.right = right;
                            }
                        }
                    }

                    for (int i = 0; i < drawingGroups.size(); i++) {
                        MessageObject.GroupedMessages group = drawingGroups.get(i);
                        float x = group.transitionParams.cell.getNonAnimationTranslationX(true);
                        float l = (group.transitionParams.left + x + group.transitionParams.offsetLeft);
                        float t = (group.transitionParams.top + group.transitionParams.offsetTop);
                        float r = (group.transitionParams.right + x + group.transitionParams.offsetRight);
                        float b = (group.transitionParams.bottom + group.transitionParams.offsetBottom);
                        if (t < -dp(20)) {
                            t = -dp(20);
                        }
                        if (b > chatListView.getMeasuredHeight() + dp(20)) {
                            b = chatListView.getMeasuredHeight() + dp(20);
                        }
                        drawStarsPrice(canvas, l, t, r, b);
                        group.transitionParams.cell = null;
                    }
                }
            }
        };
        chatListView.setOnClickListener(v -> {
            onBackPressed();
        });
        chatListView.setOnItemClickListener((v, pos) -> {
            onBackPressed();
        });
        chatListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                chatListView.invalidate();
            }
        });
        ChatListItemAnimator chatListItemAnimator = new ChatListItemAnimator(null, chatListView, resourcesProvider) {

            Runnable finishRunnable;

            @Override
            public void checkIsRunning() {
//                if (scrollAnimationIndex == -1) {
//                    scrollAnimationIndex = getNotificationCenter().setAnimationInProgress(scrollAnimationIndex, allowedNotificationsDuringChatListAnimations, false);
//                }
            }

            @Override
            public void onAnimationStart() {
//                scrollAnimationIndex = getNotificationCenter().setAnimationInProgress(scrollAnimationIndex, allowedNotificationsDuringChatListAnimations, false);
                if (finishRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(finishRunnable);
                    finishRunnable = null;
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("chatItemAnimator disable notifications");
                }
//                chatActivityEnterView.getAdjustPanLayoutHelper().runDelayedAnimation();
//                chatActivityEnterView.runEmojiPanelAnimation();
            }

            @Override
            protected void onAllAnimationsDone() {
                super.onAllAnimationsDone();
                if (finishRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(finishRunnable);
                    finishRunnable = null;
                }
                AndroidUtilities.runOnUIThread(finishRunnable = () -> {
                    finishRunnable = null;
//                    if (scrollAnimationIndex != -1) {
//                        getNotificationCenter().onAnimationFinish(scrollAnimationIndex);
//                        scrollAnimationIndex = -1;
//                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("chatItemAnimator enable notifications");
                    }
                });
            }


            @Override
            public void endAnimations() {
                super.endAnimations();
                if (finishRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(finishRunnable);
                }
                AndroidUtilities.runOnUIThread(finishRunnable = () -> {
                    finishRunnable = null;
//                    if (scrollAnimationIndex != -1) {
//                        getNotificationCenter().onAnimationFinish(scrollAnimationIndex);
//                        scrollAnimationIndex = -1;
//                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("chatItemAnimator enable notifications");
                    }
                });
            }
        };
        chatListView.setItemAnimator(chatListItemAnimator);

        chatLayoutManager = new GridLayoutManagerFixed(context, 1000, LinearLayoutManager.VERTICAL, true) {

            boolean computingScroll;

            @Override
            public int computeVerticalScrollExtent(RecyclerView.State state) {
                computingScroll = true;
                int r = super.computeVerticalScrollExtent(state);
                computingScroll = false;
                return r;
            }

            @Override
            public int computeVerticalScrollOffset(RecyclerView.State state) {
                computingScroll = true;
                int r = super.computeVerticalScrollOffset(state);
                computingScroll = false;
                return r;
            }

            @Override
            public int computeVerticalScrollRange(RecyclerView.State state) {
                computingScroll = true;
                int r = super.computeVerticalScrollRange(state);
                computingScroll = false;
                return r;
            }

            @Override
            public boolean supportsPredictiveItemAnimations() {
                return true;
            }

            @Override
            public boolean shouldLayoutChildFromOpositeSide(View child) {
                if (child instanceof ChatMessageCell) {
                    return !((ChatMessageCell) child).getMessageObject().isOutOwner();
                }
                return false;
            }

            @Override
            protected boolean hasSiblingChild(int position) {
                MessageObject message = messageObjects.get(getItemCount() - 1 - position);
                MessageObject.GroupedMessages group = getValidGroupedMessage(message);
                if (group != null) {
                    MessageObject.GroupedMessagePosition pos = group.getPosition(message);
                    if (pos.minX == pos.maxX || pos.minY != pos.maxY || pos.minY == 0) {
                        return false;
                    }
                    int count = group.posArray.size();
                    for (int a = 0; a < count; a++) {
                        MessageObject.GroupedMessagePosition p = group.posArray.get(a);
                        if (p == pos) {
                            continue;
                        }
                        if (p.minY <= pos.minY && p.maxY >= pos.minY) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
        chatLayoutManager.setSpanSizeLookup(new GridLayoutManagerFixed.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                MessageObject message = messageObjects.get(messageObjects.size() - 1 - position);
                MessageObject.GroupedMessages groupedMessages = getValidGroupedMessage(message);
                if (groupedMessages != null) {
                    return groupedMessages.getPosition(message).spanSize;
                }
                return 1000;
            }
        });
        chatListView.setLayoutManager(chatLayoutManager);
        chatListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.bottom = 0;
                if (view instanceof ChatMessageCell) {
                    ChatMessageCell cell = (ChatMessageCell) view;
                    MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                    if (group != null) {
                        MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
                        if (position != null && position.siblingHeights != null) {
                            float maxHeight = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
                            int h = cell.getExtraInsetHeight();
                            for (int a = 0; a < position.siblingHeights.length; a++) {
                                h += (int) Math.ceil(maxHeight * position.siblingHeights[a]);
                            }
                            h += (position.maxY - position.minY) * Math.round(7 * AndroidUtilities.density);
                            int count = group.posArray.size();
                            for (int a = 0; a < count; a++) {
                                MessageObject.GroupedMessagePosition pos = group.posArray.get(a);
                                if (pos.minY != position.minY || pos.minX == position.minX && pos.maxX == position.maxX && pos.minY == position.minY && pos.maxY == position.maxY) {
                                    continue;
                                }
                                if (pos.minY == position.minY) {
                                    h -= (int) Math.ceil(maxHeight * pos.ph) - dp(4);
                                    break;
                                }
                            }
                            outRect.bottom = -h;
                        }
                    }
                }
            }
        });
        chatListView.setAdapter(adapter = new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                MessageCell cell = new MessageCell(context, currentAccount, true, null, resourcesProvider);
                cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                    @Override
                    public boolean canPerformActions() {
                        return false;
                    }
                });
                return new RecyclerListView.Holder(cell);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                MessageObject messageObject = messageObjects.get(getItemCount() - 1 - position);
                ChatMessageCell cell = (ChatMessageCell) holder.itemView;
                MessageObject.GroupedMessages group = getValidGroupedMessage(messageObject);
                cell.setInvalidatesParent(group != null);
                cell.setMessageObject(messageObject, group, false, false, false);
                if (position == getMainMessageCellPosition() && !messageObject.needDrawForwarded()) {
                    mainMessageCell = cell;
                    mainMessageCellId = messageObject.getId();
                }
            }

            @Override
            public int getItemCount() {
                return messageObjects.size();
            }
        });
        chatListView.setVerticalScrollBarEnabled(false);
        chatListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        containerView.addView(chatListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        effectsView = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                effectOverlay.draw(canvas);

                float progress = effectOverlay.getProgress();
                if (progress != -2) {
                    sendButton.setLoading(progress >= 0f && progress < 1f, ChatActivityEnterView.SendButton.INFINITE_LOADING);
                }

//                if (effectSelector != null) {
//                    effectSelector.setPaused(effectOverlay.hasPlaying(), true);
//                }
                if (!effectOverlay.isIdle()) {
                    invalidate();
                }
            }
        };
        windowView.addView(effectsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        effectOverlay = new EmojiAnimationsOverlay(effectsView, currentAccount) {
            int[] messagePos = new int[2];
            @Override
            protected void layoutObject(EmojiAnimationsOverlay.DrawingObject object) {
                if (object == null) return;
                if (cameraRect != null) {
                    object.viewFound = true;
                    float sz = getFilterWidth() * AndroidUtilities.density / 1.3f;
                    object.lastW = sz / 3f;
                    object.lastH = sz / 3f;
                    object.lastX = Utilities.clamp(cameraRect.right - sz * .75f, AndroidUtilities.displaySize.x - sz, 0);
                    object.lastY = cameraRect.bottom - sz / 2f;
                } else if (mainMessageCell != null && mainMessageCell.isAttachedToWindow() && mainMessageCell.getMessageObject() != null && mainMessageCell.getMessageObject().getId() == mainMessageCellId) {
                    mainMessageCell.getLocationOnScreen(messagePos);
                    object.viewFound = true;
                    float sz = getFilterWidth() * AndroidUtilities.density / 1.3f;
                    object.lastW = sz / 3f;
                    object.lastH = sz / 3f;
                    object.lastX = Utilities.clamp(messagePos[0] + mainMessageCell.getTimeX() * chatListView.getScaleX() - sz / 2f, AndroidUtilities.displaySize.x - sz, 0);
                    object.lastY = messagePos[1] + mainMessageCell.getTimeY() * chatListView.getScaleY() - sz / 2f;
                }
            }
        };
    }

    @Override
    public void onBackPressed() {
        if (keyboardVisible) {
            AndroidUtilities.hideKeyboard(getCurrentFocus());
            keyboardVisible = false;
            return;
        }
        if (effectSelector != null && effectSelector.getReactionsWindow() != null) {
            if (!effectSelector.getReactionsWindow().transition) {
                effectSelector.getReactionsWindow().dismiss();
            }
            return;
        }
        sentEffect = true;
        super.onBackPressed();
    }

    private class MessageCell extends ChatMessageCell {
        public MessageCell(Context context, int currentAccount, boolean canDrawBackgroundInParent, ChatMessageSharedResources sharedResources, Theme.ResourcesProvider resourcesProvider) {
            super(context, currentAccount, canDrawBackgroundInParent, sharedResources, resourcesProvider);
        }

        @Override
        protected SpoilerEffect2 makeSpoilerEffect() {
            return SpoilerEffect2.getInstance(SpoilerEffect2.TYPE_PREVIEW, this, windowView);
        }

        @Override
        public boolean isPressed() {
            return false;
        }

        public int top = Integer.MAX_VALUE;
        public int bottom = Integer.MAX_VALUE;
        private int pastId = -1;
        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (transitionParams.animateBackgroundBoundsInner && top != 0 && this.top != Integer.MAX_VALUE && bottom != 0 && this.bottom != Integer.MAX_VALUE && pastId == (getMessageObject() == null ? 0 : getMessageObject().getId())) {
//                if (!scrolledToLast) {
//                } else {
//                    setTranslationY(-(bottom - this.bottom));
//                }
                if (!scrolledToLast) {
                    setTranslationY(-(top - this.top));
                    animate().translationY(0).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                } else {

                }

                this.top = getTop();
                this.bottom = getBottom();
                pastId = getMessageObject() == null ? 0 : getMessageObject().getId();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setWindowAnimations(R.style.DialogNoAnimation);
        setContentView(windowView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.FILL;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        if (Build.VERSION.SDK_INT >= 21) {
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION |
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
        }
        params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(params);

        windowView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_VISIBLE);
        AndroidUtilities.setLightNavigationBar(windowView, !Theme.isCurrentThemeDark());
    }

    public void setMessageObjects(ArrayList<MessageObject> messageObjects) {
        for (int i = 0; i < messageObjects.size(); ++i) {
            MessageObject msg = messageObjects.get(i);
            if (msg.hasValidGroupId()) {
                MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(msg.getGroupIdForUse());
                if (groupedMessages == null) {
                    groupedMessages = new MessageObject.GroupedMessages();
                    groupedMessages.reversed = false;
                    groupedMessages.groupId = msg.getGroupId();
                    groupedMessagesMap.put(groupedMessages.groupId, groupedMessages);
                }
                if (groupedMessages.getPosition(msg) == null) {
                    boolean found = false;
                    for (int j = 0; j < groupedMessages.messages.size(); ++j) {
                        if (groupedMessages.messages.get(j).getId() == msg.getId()) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        groupedMessages.messages.add(msg);
                    }
                }
            } else if (msg.getGroupIdForUse() != 0) {
                msg.messageOwner.grouped_id = 0;
                msg.localSentGroupId = 0;
            }
        }
        for (int i = 0; i < groupedMessagesMap.size(); ++i) {
            groupedMessagesMap.valueAt(i).calculate();
        }
        this.messageObjects.addAll(messageObjects);
        for (int i = 0; i < this.messageObjects.size(); ++i) {
            messageObjectsWidth = Math.max(messageObjectsWidth, getWidthForMessage(this.messageObjects.get(i)));
        }
        chatListView.getAdapter().notifyDataSetChanged();
        final int itemsCount = chatListView.getAdapter().getItemCount();
        chatLayoutManager.scrollToPositionWithOffset(itemsCount > 10 ? itemsCount % 10 : 0, dp(12), true);
    }

    private RectF cameraRect;
    public void setCameraTexture(TextureView textureView) {
        if (textureView == null) return;
        cameraRect = new RectF();
        int[] pos = new int[2];
        textureView.getLocationOnScreen(pos);
        cameraRect.set(pos[0], pos[1], pos[0] + textureView.getWidth(), pos[1] + textureView.getHeight());
    }

    public void setEditText(
        EditTextCaption editText,
        Utilities.Callback2<Canvas, Utilities.Callback0Return<Boolean>> drawEditText,
        Utilities.Callback<Canvas> drawEditTextBackground
    ) {
        this.editText = editText;
        this.drawEditText = drawEditText;
        this.drawEditTextBackground = drawEditTextBackground;
    }

    public void setSendButton(ChatActivityEnterView.SendButton sendButton, boolean fillWhenClose, View.OnClickListener onClick) {
        this.anchorSendButton = sendButton;
        anchorSendButton.getLocationOnScreen(sendButtonInitialPosition);
//        sendButtonInitialPosition[0] = Math.min(sendButtonInitialPosition[0] + anchorSendButton.getWidth(), AndroidUtilities.displaySize.x) - anchorSendButton.getWidth();
        this.sendButton = new ChatActivityEnterView.SendButton(getContext(), sendButton.resId, resourcesProvider) {
            @Override
            public boolean isInScheduleMode() {
                return sendButton.isInScheduleMode();
            }
            @Override
            public boolean isOpen() {
                return (fillWhenClose ? !dismissing : true) || super.isOpen();
            }
            @Override
            public boolean isInactive() {
                return sendButton.isInactive();
            }
            @Override
            public boolean shouldDrawBackground() {
                return sendButton.shouldDrawBackground();
            }
            @Override
            public int getFillColor() {
                return sendButton.getFillColor();
            }
        };
        this.anchorSendButton.copyTo(this.sendButton);
        this.sendButton.open.set(sendButton.open.get(), true);
        this.sendButton.setOnClickListener(onClick);
        containerView.addView(this.sendButton, new ViewGroup.LayoutParams(sendButton.getWidth(), sendButton.getHeight()));
        sendButtonWidth = anchorSendButton.width(sendButton.getHeight());
        sendButtonInitialPosition[0] += anchorSendButton.getWidth() - anchorSendButton.width(sendButton.getHeight()) - dp(6);
    }

    public void setItemOptions(ItemOptions options) {
        optionsView = options.getLayout();
        containerView.addView(optionsView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
    }

    public void allowEffectSelector(BaseFragment fragment) {
        if (effectSelector != null || fragment == null) return;
        MessagesController.getInstance(currentAccount).getAvailableEffects();
        effectSelectorContainer = new FrameLayout(context);
        effectSelectorContainer.setClipChildren(false);
        effectSelectorContainer.setClipToPadding(false);
        effectSelectorContainer.setPadding(0, 0, 0, dp(24));
        effectSelector = new ReactionsContainerLayout(ReactionsContainerLayout.TYPE_MESSAGE_EFFECTS, null, getContext(), currentAccount, resourcesProvider) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                setPivotX(getMeasuredWidth());
                setPivotY(getMeasuredHeight());
            }
        };
        effectSelector.setClipChildren(false);
        effectSelector.setClipToPadding(false);
        effectSelector.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(22), AndroidUtilities.dp(4), AndroidUtilities.dp(22));
        effectSelector.setDelegate(new ReactionsContainerLayout.ReactionsContainerDelegate() {
            @Override
            public void onReactionClicked(View view, ReactionsLayoutInBubble.VisibleReaction visibleReaction, boolean longpress, boolean addToRecent) {
                if (visibleReaction == null || effectSelector == null)
                    return;
                final boolean premiumLocked = !UserConfig.getInstance(currentAccount).isPremium() && visibleReaction.premium;
                if (mainMessageCell != null) {
                    MessageObject messageObject = mainMessageCell.getMessageObject();
                    if (messageObject == null)
                        return;
                    final long prevEffect = messageObject.messageOwner.effect;
                    boolean clear = false;
                    if (visibleReaction.effectId == messageObject.messageOwner.effect) {
                        messageObject.messageOwner.flags2 &=~ 4;
                        messageObject.messageOwner.effect = 0;
                        clear = true;
                    } else {
                        messageObject.messageOwner.flags2 |= 4;
                        messageObject.messageOwner.effect = visibleReaction.effectId;
                    }
                    if (!premiumLocked) {
                        mainMessageCell.setMessageObject(messageObject, getValidGroupedMessage(messageObject), messageObjects.size() > 1, false, false);
                        effectSelector.setSelectedReactionAnimated(clear ? null : visibleReaction);
                        if (effectSelector.getReactionsWindow() != null && effectSelector.getReactionsWindow().getSelectAnimatedEmojiDialog() != null) {
                            effectSelector.getReactionsWindow().getSelectAnimatedEmojiDialog().setSelectedReaction(clear ? null : visibleReaction);
                            effectSelector.getReactionsWindow().containerView.invalidate();
                        }
                    }
                    effectOverlay.clear();
                    if (!clear) {
                        effectOverlay.showAnimationForCell(mainMessageCell, 0, false, false);
                    }
                    if (premiumLocked) {
                        messageObject.messageOwner.effect = prevEffect;
                        if (prevEffect == 0) {
                            messageObject.messageOwner.flags2 &=~ 4;
                        }
                    }
                    if (sendButton != null) {
                        sendButton.setEffect(messageObject.messageOwner.effect);
                    }
                    onEffectChange(messageObject.messageOwner.effect);
                } else if (cameraRect != null) {
                    boolean clear = false;
                    if (visibleReaction.effectId == effectId) {
                        effectId = 0;
                        clear = true;
                    } else {
                        effectId = visibleReaction.effectId;
                    }
                    if (sendButton != null) {
                        sendButton.setEffect(effectId);
                    }
                    onEffectChange(effectId);
                    if (!premiumLocked) {
                        TLRPC.TL_availableEffect effect = effectId == 0 ? null : MessagesController.getInstance(currentAccount).getEffect(effectId);
                        if (effectDrawable != null) {
                            if (effectId == 0 || effect == null) {
                                effectDrawable.set((Drawable) null, true);
                            } else {
                                effectDrawable.set(Emoji.getEmojiDrawable(effect.emoticon), true);
                            }
                        }
                        effectSelector.setSelectedReactionAnimated(clear ? null : visibleReaction);
                        if (effectSelector.getReactionsWindow() != null && effectSelector.getReactionsWindow().getSelectAnimatedEmojiDialog() != null) {
                            effectSelector.getReactionsWindow().getSelectAnimatedEmojiDialog().setSelectedReaction(clear ? null : visibleReaction);
                            effectSelector.getReactionsWindow().containerView.invalidate();
                        }
                    }
                    effectOverlay.clear();
                    if (!clear) {
                        TLRPC.TL_message message = new TLRPC.TL_message();
                        message.effect = effectId;
                        if (effectId != 0) {
                            message.flags2 |= 4;
                        }
                        MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
                        effectOverlay.createDrawingObject(null, 0, null, messageObject, 0, false, false, 0, 0, true);
                    }
                }
                if (premiumLocked && fragment != null) {
                    BulletinFactory.of(containerView, resourcesProvider)
                        .createSimpleBulletin(R.raw.star_premium_2, AndroidUtilities.premiumText(LocaleController.getString(R.string.AnimatedEffectPremium), () -> {
                            BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                            params.transitionFromLeft = true;
                            params.allowNestedScroll = false;
                            fragment.showAsSheet(new PremiumPreviewFragment("effect"), params);
                        }))
                        .show();
                }
                effectsView.invalidate();
            }
        });
        effectSelector.setTop(false);
        effectSelector.setClipChildren(false);
        effectSelector.setClipToPadding(false);
        effectSelector.setVisibility(View.VISIBLE);
        effectSelector.setHint(LocaleController.getString(R.string.AddEffectMessageHint));
        effectSelector.setBubbleOffset(dp(-25));
        effectSelector.setMiniBubblesOffset(dp(2));
        containerView.addView(effectSelectorContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 300, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
        effectSelectorContainer.addView(effectSelector, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 72 + 22 + 22, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 0));
        effectSelector.setScaleY(.4f);
        effectSelector.setScaleX(.4f);
        effectSelector.setAlpha(0f);
        if (MessagesController.getInstance(currentAccount).hasAvailableEffects()) {
            showEffectSelector();
        } else {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.availableEffectsUpdate);
        }
        if (effectSelector != null) {
            effectSelector.setPaused(true, true);
        }

        new KeyboardNotifier(windowView, keyboardHeight -> {
            keyboardVisible = keyboardHeight - insets.bottom > dp(20);
            float newY = keyboardVisible ? Math.min(effectSelectorContainerY, windowView.getHeight() - keyboardHeight - effectSelectorContainer.getMeasuredHeight()) : effectSelectorContainerY;
            effectSelectorContainer.animate().translationY(newY - effectSelectorContainer.getTop()).setDuration(AdjustPanLayoutHelper.keyboardDuration).setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator).start();
        });
    }

    public void setEffectId(long effectId) {
        this.effectId = effectId;
        final int position = getMainMessageCellPosition();
        MessageObject messageObject = position >= 0 && position < messageObjects.size() ? messageObjects.get(position) : null;
        if (messageObject != null) {
            messageObject.messageOwner.flags2 |= 4;
            messageObject.messageOwner.effect = effectId;
        }
        if (effectSelector != null) {
            TLRPC.TL_availableEffect effect = MessagesController.getInstance(currentAccount).getEffect(effectId);
            if (effect != null) {
                effectSelector.setSelectedReactionAnimated(ReactionsLayoutInBubble.VisibleReaction.fromTL(effect));
            }
        }
    }

    public void showEffectSelector() {
        if (effectSelectorShown) return;
        layoutDone = false;
        effectSelectorShown = true;
        effectSelector.setMessage(null, null, true);
        effectSelector.animate().scaleY(1f).scaleX(1f).alpha(1f).setDuration(420).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        effectSelector.startEnterAnimation(false);
    }

    private boolean sentEffect;
    public long getSelectedEffect() {
        if (sentEffect || effectSelector == null)
            return 0;
        if (cameraRect != null) {
            sentEffect = true;
            return effectId;
        } else if (mainMessageCell != null) {
            MessageObject messageObject = mainMessageCell.getMessageObject();
            if (messageObject == null)
                return 0;
            if ((messageObject.messageOwner.flags2 & 4) == 0) {
                return 0;
            }
            sentEffect = true;
            return messageObject.messageOwner.effect;
        }
        return 0;
    }

    protected void onEffectChange(long effectId) {

    }

    public void hideEffectSelector() {
        if (effectSelector == null) return;
        if (!effectSelectorShown) return;
        effectSelector.dismissWindow();
        if (effectSelector.getReactionsWindow() != null && effectSelector.getReactionsWindow().containerView != null) {
            effectSelector.getReactionsWindow().containerView.animate().alpha(0).setDuration(180).start();
        }
        effectSelector.animate().alpha(0.01f).translationY(-dp(12)).scaleX(.6f).scaleY(.6f).setDuration(180).start();
    }

    private final int[] sendButtonInitialPosition = new int[2];
    private void layout() {
        if (windowView.getWidth() <= 0)
            return;

        int[] pos = new int[2];
        anchorSendButton.getLocationOnScreen(pos);
//        pos[0] = Math.min(pos[0] + anchorSendButton.getWidth(), AndroidUtilities.displaySize.x) - anchorSendButton.getWidth();
        pos[0] += anchorSendButton.getWidth() - anchorSendButton.width() - dp(6);

        sendButtonInitialPosition[0] = pos[0];
        sendButtonInitialPosition[1] = pos[1];

        final int heightup = chatListView.getMeasuredHeight() - sendButton.getHeight() + (effectSelector != null ? dp(320) : 0);
        final int top = insets.top + dp(8);

        final int heightdown = dp(messageObjects.isEmpty() ? -6 : 48) + (optionsView == null ? 0 : optionsView.getMeasuredHeight());
        final int bottom = containerView.getMeasuredHeight() - dp(8) - insets.bottom;
        if (pos[1] + heightdown > bottom) {
            pos[1] = bottom - heightdown;
        }
        if (pos[1] - heightup < top) {
            pos[1] = top + heightup;
        }
        if (pos[1] + anchorSendButton.getHeight() + heightdown > bottom) {
            pos[1] = bottom - heightdown - anchorSendButton.getHeight();
        }

        sendButton.setX(pos[0] - (sendButton.getWidth() - sendButton.width()) + dp(6));
        sendButton.setY(pos[1]);

        chatListView.setX(pos[0] + dp(7) - chatListView.getMeasuredWidth());
        if (layoutDone) {
            chatListView.animate().translationY(pos[1] + sendButton.getHeight() - chatListView.getMeasuredHeight() - chatListView.getTop()).setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR).setDuration(ChatListItemAnimator.DEFAULT_DURATION).start();
        } else {
            chatListView.setY(pos[1] + sendButton.getHeight() - chatListView.getMeasuredHeight());
        }

        if (optionsView != null) {
            optionsView.setX(pos[0] + dp(7) - optionsView.getMeasuredWidth());
            optionsView.setY(pos[1] + (messageObjects.isEmpty() ? -dp(6) : sendButton.getHeight()));
        }

        if (effectSelectorContainer != null) {
            effectSelectorContainer.setX(Math.max(0, pos[0] + sendButton.width() - effectSelectorContainer.getMeasuredWidth() - dp(6)));
            if (cameraRect != null) {
                effectSelectorContainer.setY(effectSelectorContainerY = Math.max(insets.top, cameraRect.top - effectSelectorContainer.getMeasuredWidth()));
                if (effectSelector != null) {
                    effectSelector.setY(Math.max(insets.top, cameraRect.top - dp(24) - effectSelector.getMeasuredHeight()));
                }
            } else {
                final float y = pos[1] + sendButton.getHeight() - chatListView.getMeasuredHeight();
                effectSelectorContainer.setY(effectSelectorContainerY = Math.max(insets.top, y - effectSelectorContainer.getMeasuredHeight()) + dp(24));
                if (effectSelector != null) {
                    effectSelector.setY(Math.max(0, y - effectSelector.getMeasuredHeight() - effectSelectorContainerY));
                }
            }
        }
    }

    private boolean focusable;
    public void makeFocusable() {
        if (focusable) return;
        try {
            Window window = getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            params.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            window.setAttributes(params);
            focusable = true;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean isShowing() {
        return !dismissing;
    }

    private boolean dismissing = false;

    @Override
    public void show() {
        if (!AndroidUtilities.isSafeToShow(getContext())) return;
        SpoilerEffect2.pause(SpoilerEffect2.TYPE_DEFAULT, true);
        super.show();
        prepareBlur(null);
        if (effectsView != null) {
            effectsView.bringToFront();
        }
        animateOpenTo(true, null);
    }

    private ChatMessageCell destCell;
    private float destClipTop, destClipBottom;
    private Rect cellDelta = new Rect();
    private VisiblePart fromPart;

    private static class VisiblePart {

        private int childPosition;
        private int visibleHeight;
        private int visibleParent;
        private float visibleParentOffset;
        private float visibleTop;
        private int blurredViewTopOffset;
        private int blurredViewBottomOffset;
        public int parentWidth;
        public int parentHeight;

        public static VisiblePart of(ChatMessageCell cell) {
            VisiblePart part = new VisiblePart();
            part.childPosition = cell.childPosition;
            part.visibleHeight = cell.visibleHeight;
            part.visibleParent = cell.visibleParent;
            part.parentWidth = cell.parentWidth;
            part.parentHeight = cell.parentHeight;
            part.visibleTop = cell.visibleTop;
            part.visibleParentOffset = cell.visibleParentOffset;
            part.blurredViewTopOffset = cell.blurredViewTopOffset;
            part.blurredViewBottomOffset = cell.blurredViewBottomOffset;
            return part;
        }

        public void lerpTo(ChatMessageCell to, float t, ChatMessageCell dest) {
            dest.setVisiblePart(
                AndroidUtilities.lerp(childPosition, to.childPosition, t),
                AndroidUtilities.lerp(visibleHeight, to.visibleHeight, t),
                AndroidUtilities.lerp(visibleParent, to.visibleParent, t),
                AndroidUtilities.lerp(visibleParentOffset, to.visibleParentOffset, t),
                AndroidUtilities.lerp(visibleTop, to.visibleTop, t),
                AndroidUtilities.lerp(parentWidth, to.parentWidth, t),
                AndroidUtilities.lerp(parentHeight, to.parentHeight, t),
                AndroidUtilities.lerp(blurredViewTopOffset, to.blurredViewTopOffset, t),
                AndroidUtilities.lerp(blurredViewBottomOffset, to.blurredViewBottomOffset, t)
            );
        }
    }

    public void dismissInto(ChatMessageCell cell, float clipTop, float clipBottom) {
        if (dismissing) return;
        sent = true;
        dismissing = true;
        if (sendButton != null) {
            sendButton.invalidate();
        }
        if (anchorSendButton != null) {
            anchorSendButton.invalidate();
        }
        if (mainMessageCell != null && cell != null) {
            destCell = cell;
            destCell.setVisibility(View.INVISIBLE);
            destClipTop = clipTop;
            destClipBottom = clipBottom;

            mainMessageCell.isChat = destCell.isChat;
            mainMessageCell.isThreadChat = destCell.isThreadChat;
            mainMessageCell.isSavedChat = destCell.isSavedChat;
            mainMessageCell.isBot = destCell.isBot;
            mainMessageCell.isForum = destCell.isForum;
            // mainMessageCell.isMonoForum = destCell.isMonoForum;
            mainMessageCell.isForumGeneral = destCell.isForumGeneral;
            mainMessageCell.setMessageObject(cell.getMessageObject(), null, cell.isPinnedBottom(), cell.isPinnedTop(), cell.isFirstInChat());

            final ChatMessageCell.TransitionParams params = mainMessageCell.getTransitionParams();
            params.animateChange = mainMessageCell.getTransitionParams().animateChange();
            params.animateChangeProgress = 0f;

            boolean widthChanged = mainMessageCell.getTransitionParams().lastDrawingBackgroundRect.left != mainMessageCell.getBackgroundDrawableLeft();
            if (widthChanged || params.lastDrawingBackgroundRect.top != mainMessageCell.getBackgroundDrawableTop() || params.lastDrawingBackgroundRect.bottom != mainMessageCell.getBackgroundDrawableBottom()) {
                cellDelta.bottom = -(mainMessageCell.getBackgroundDrawableBottom() - params.lastDrawingBackgroundRect.bottom);
                cellDelta.top = -(mainMessageCell.getBackgroundDrawableTop() - params.lastDrawingBackgroundRect.top);
                if (cell.getMessageObject().isOutOwner()) {
                    cellDelta.left = -(mainMessageCell.getBackgroundDrawableLeft() - params.lastDrawingBackgroundRect.left);
                    cellDelta.right = 0;
                } else {
                    cellDelta.left = 0;
                    cellDelta.right = mainMessageCell.getBackgroundDrawableRight() - params.lastDrawingBackgroundRect.right;
                }
                params.animateBackgroundBoundsInner = true;
                params.animateBackgroundWidth = widthChanged;
            }

            fromPart = VisiblePart.of(mainMessageCell);
        }
        animateOpenTo(false, () -> {
            SpoilerEffect2.pause(SpoilerEffect2.TYPE_DEFAULT, false);
            if (spoilerEffect2 != null) {
                spoilerEffect2.detach(windowView);
            }
            AndroidUtilities.runOnUIThread(super::dismiss);
        });
        windowView.invalidate();

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.availableEffectsUpdate);
    }

    public void dismiss(boolean sent) {
        this.sent = sent;
        dismiss();
    }

    public void dismissInstant() {
        if (dismissing) return;
        dismissing = true;

        SpoilerEffect2.pause(SpoilerEffect2.TYPE_DEFAULT, false);
        if (spoilerEffect2 != null) {
            spoilerEffect2.detach(windowView);
        }
        super.dismiss();

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.availableEffectsUpdate);
    }

    @Override
    public void dismiss() {
        if (dismissing) return;
        dismissing = true;
        if (sendButton != null) {
            sendButton.invalidate();
        }
        if (anchorSendButton != null) {
            anchorSendButton.invalidate();
        }
        animateOpenTo(false, () -> {
            SpoilerEffect2.pause(SpoilerEffect2.TYPE_DEFAULT, false);
            if (spoilerEffect2 != null) {
                spoilerEffect2.detach(windowView);
            }
            AndroidUtilities.runOnUIThread(super::dismiss);
        });
        windowView.invalidate();

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.availableEffectsUpdate);
    }

    private ValueAnimator openAnimator;
    private void animateOpenTo(boolean open, Runnable after) {
        if (openAnimator != null) {
            openAnimator.cancel();
        }

        final boolean animateOptions = open && optionsView != null && optionsView instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout;
        if (animateOptions) {
            ActionBarPopupWindow.startAnimation((ActionBarPopupWindow.ActionBarPopupWindowLayout) optionsView);
        }
        if (!open) {
            hideEffectSelector();
        }
        openInProgress = true;
        opening = open;
        closing = !open;
        chatListView.invalidate();
        firstOpenFrame = true;
        firstOpenFrame2 = true;
        openAnimator = ValueAnimator.ofFloat(openProgress, open ? 1 : 0);
        openAnimator.addUpdateListener(anm -> {
            openProgress = (float) anm.getAnimatedValue();
            effectsView.setAlpha(openProgress);
            chatListView.setAlpha(openProgress);
            if (!animateOptions && optionsView != null) {
                optionsView.setAlpha(openProgress);
            }
            windowView.invalidate();
            containerView.invalidate();
        });
        openAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                openProgress = open ? 1 : 0;
                firstOpenFrame = false;
                firstOpenFrame2 = false;
                effectsView.setAlpha(openProgress);
                if (open) {
                    openInProgress = false;
                    opening = false;
                    closing = false;
                }
                if (editText != null) {
                    editText.setAlpha(1f);
                }
                if (destCell != null) {
                    destCell.setVisibility(View.VISIBLE);
                }
                if (anchorSendButton != null && !sent) {
                    anchorSendButton.setAlpha(1f);
                }
                if (!open && sendButton != null) {
                    sendButton.setAlpha(0f);
                }
                if (!animateOptions && optionsView != null) {
                    optionsView.setAlpha(openProgress);
                }
                chatListView.invalidate();
                chatListView.setAlpha(openProgress);
                windowView.invalidate();
                containerView.invalidate();
                if (after != null) {
                    if (!open && destCell != null && destCell.isAttachedToWindow()) {
                        destCell.post(after);
                    } else if (!open && editText != null && editText.isAttachedToWindow()) {
                        editText.post(after);
                    } else {
                        AndroidUtilities.runOnUIThread(after);
                    }
                }
            }
        });
        final long duration = 350;
        openAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        openAnimator.setDuration(duration);
        openAnimator.start();
    }

    private void prepareBlur(View withoutView) {
        if (withoutView != null) {
            withoutView.setVisibility(View.INVISIBLE);
        }
        final float oldAlpha = anchorSendButton.getAlpha();
        if (anchorSendButton != null) {
            anchorSendButton.setAlpha(0.0f);
        }
        AndroidUtilities.makeGlobalBlurBitmap(bitmap -> {
            if (anchorSendButton != null) {
                anchorSendButton.setAlpha(oldAlpha);
            }
            if (withoutView != null) {
                withoutView.setVisibility(View.VISIBLE);
            }
            blurBitmap = bitmap;

            blurBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            blurBitmapPaint.setShader(blurBitmapShader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            ColorMatrix colorMatrix = new ColorMatrix();
            AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? .08f : +.25f);
            AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? -.02f : -.07f);
            blurBitmapPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            blurMatrix = new Matrix();
        }, 14);
    }

    public void updateColors() {

    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.availableEffectsUpdate) {
            if (MessagesController.getInstance(currentAccount).hasAvailableEffects()) {
                showEffectSelector();
            }
        }
    }


    private MessageObject.GroupedMessages getValidGroupedMessage(MessageObject message) {
        MessageObject.GroupedMessages groupedMessages = null;
        if (message.getGroupId() != 0) {
            groupedMessages = groupedMessagesMap.get(message.getGroupId());
            if (groupedMessages != null && (groupedMessages.messages.size() <= 1 || groupedMessages.getPosition(message) == null)) {
                groupedMessages = null;
            }
        }
        return groupedMessages;
    }

    private boolean scrolledToLast;
    public void scrollTo(boolean last) {
        if (chatListView == null || chatListView.getAdapter() == null || chatLayoutManager == null) return;
        final int itemsCount = chatListView.getAdapter().getItemCount();
        final int pos = last ? (itemsCount > 10 ? itemsCount % 10 : 0) : itemsCount - 1;
        chatLayoutManager.scrollToPositionWithOffset(pos, dp(12), last);
        scrolledToLast = last;
    }

    // assumed that messageObject is the same pointer
    public void changeMessage(MessageObject messageObject) {
        MessageObject.GroupedMessages group = getValidGroupedMessage(messageObject);
        if (group != null) {
            group.calculate();
            for (MessageObject msg : group.messages) {
                changeMessageInternal(msg);
            }
        } else {
            changeMessageInternal(messageObject);
        }
    }

    public void changeMessageInternal(MessageObject messageObject) {
        if (chatListView == null) return;

        ChatMessageCell messageCell = null;
        for (int i = 0; i < chatListView.getChildCount(); ++i) {
            View child = chatListView.getChildAt(i);
            if (child instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) child;
                if (cell.getMessageObject() == messageObject) {
                    messageCell = cell;
                    break;
                }
            }
        }
        int position = -1;
        for (int i = 0; i < messageObjects.size(); ++i) {
            if (messageObjects.get(i) == messageObject) {
                position = messageObjects.size() - 1 - i;
            }
        }
        if (messageCell == null) {
            chatListView.getAdapter().notifyItemChanged(position);
            return;
        }

        final ChatMessageCell cell = messageCell;
        messageObject.forceUpdate = true;
        cell.setMessageObject(messageObject, cell.getCurrentMessagesGroup(), cell.isPinnedBottom(), cell.isPinnedTop(), cell.isFirstInChat());
        chatListView.getAdapter().notifyItemChanged(position);
    }

    private ChatMessageCell dummyMessageCell;
    private int getWidthForMessage(MessageObject object) {
        if (getContext() == null) {
            return 0;
        }
        if (dummyMessageCell == null) {
            dummyMessageCell = new ChatMessageCell(getContext(), currentAccount, true, null, resourcesProvider);
        }
        dummyMessageCell.isChat = false;
        dummyMessageCell.isSavedChat = false;
        dummyMessageCell.isSavedPreviewChat = false;
        dummyMessageCell.isBot = false;
        dummyMessageCell.isMegagroup = false;
        return dummyMessageCell.computeWidth(object, groupedMessagesMap.get(object.getGroupId()));
    }

    private Text buttonText;
    private Paint buttonBgPaint;

    public void setStars(long stars) {
        buttonText = stars <= 0 ? null : new Text(StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatPluralStringComma("UnlockPaidContent", (int) stars), .7f), 14, AndroidUtilities.bold());
        if (buttonBgPaint == null) {
            buttonBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            buttonBgPaint.setColor(0x40000000);
        }
        chatListView.invalidate();
        for (int i = 0; i < messageObjects.size(); ++i) {
            MessageObject msg = messageObjects.get(i);
            if (msg != null && msg.messageOwner != null && msg.messageOwner.media != null) {
                msg.messageOwner.media.spoiler = stars > 0;
            }
        }
        adapter.notifyDataSetChanged();
    }

    public void drawStarsPrice(Canvas canvas, float l, float t, float r, float b) {
        if (buttonText == null || buttonBgPaint == null) return;
        final float cx = (l + r) / 2f, cy = (t + b) / 2f;

        final float buttonWidth = dp(14 + 14) + buttonText.getCurrentWidth();
        final float buttonHeight = dp(32);
        AndroidUtilities.rectTmp.set(
            cx - buttonWidth / 2f,
            cy - buttonHeight / 2f,
            cx + buttonWidth / 2f,
            cy + buttonHeight / 2f
        );
        canvas.save();
        canvas.drawRoundRect(AndroidUtilities.rectTmp, buttonHeight / 2f, buttonHeight / 2f, buttonBgPaint);
        buttonText.draw(canvas, cx - buttonWidth / 2f + dp(14), cy, 0xFFFFFFFF, 1f);
        canvas.restore();
    }

}
