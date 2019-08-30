package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

@SuppressWarnings("FieldCanBeLocal")
public class UndoView extends FrameLayout {

    private TextView infoTextView;
    private TextView subinfoTextView;
    private TextView undoTextView;
    private ImageView undoImageView;
    private RLottieImageView leftImageView;
    private LinearLayout undoButton;
    private int undoViewHeight;

    private int currentAccount = UserConfig.selectedAccount;

    private TextPaint textPaint;
    private Paint progressPaint;
    private RectF rect;

    private long timeLeft;
    private int prevSeconds;
    private String timeLeftString;
    private int textWidth;

    private int currentAction;
    private long currentDialogId;
    private Runnable currentActionRunnable;
    private Runnable currentCancelRunnable;

    private long lastUpdateTime;

    private float additionalTranslationY;

    private boolean isShowed;

    public final static int ACTION_CLEAR = 0;
    public final static int ACTION_DELETE = 1;
    public final static int ACTION_ARCHIVE = 2;
    public final static int ACTION_ARCHIVE_HINT = 3;
    public final static int ACTION_ARCHIVE_FEW = 4;
    public final static int ACTION_ARCHIVE_FEW_HINT = 5;
    public final static int ACTION_ARCHIVE_HIDDEN = 6;
    public final static int ACTION_ARCHIVE_PINNED = 7;
    public final static int ACTION_CONTACT_ADDED = 8;
    public final static int ACTION_OWNER_TRANSFERED_CHANNEL = 9;
    public final static int ACTION_OWNER_TRANSFERED_GROUP = 10;

    public UndoView(Context context) {
        super(context);

        infoTextView = new TextView(context);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        infoTextView.setTextColor(Theme.getColor(Theme.key_undo_infoColor));
        addView(infoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 45, 13, 0, 0));

        subinfoTextView = new TextView(context);
        subinfoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subinfoTextView.setTextColor(Theme.getColor(Theme.key_undo_infoColor));
        subinfoTextView.setSingleLine(true);
        subinfoTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(subinfoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 58, 27, 8, 0));

        leftImageView = new RLottieImageView(context);
        leftImageView.setScaleType(ImageView.ScaleType.CENTER);
        leftImageView.setLayerColor("info1.**", Theme.getColor(Theme.key_undo_background) | 0xff000000);
        leftImageView.setLayerColor("info2.**", Theme.getColor(Theme.key_undo_background) | 0xff000000);
        leftImageView.setLayerColor("luc12.**", Theme.getColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc11.**", Theme.getColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc10.**", Theme.getColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc9.**", Theme.getColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc8.**", Theme.getColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc7.**", Theme.getColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc6.**", Theme.getColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc5.**", Theme.getColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc4.**", Theme.getColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc3.**", Theme.getColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc2.**", Theme.getColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc1.**", Theme.getColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("Oval.**", Theme.getColor(Theme.key_undo_infoColor));
        addView(leftImageView, LayoutHelper.createFrame(54, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 3, 0, 0, 0));

        undoButton = new LinearLayout(context);
        undoButton.setOrientation(LinearLayout.HORIZONTAL);
        addView(undoButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 19, 0));
        undoButton.setOnClickListener(v -> {
            if (!canUndo()) {
                return;
            }
            hide(false, 1);
        });

        undoImageView = new ImageView(context);
        undoImageView.setImageResource(R.drawable.chats_undo);
        undoImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_undo_cancelColor), PorterDuff.Mode.MULTIPLY));
        undoButton.addView(undoImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT));

        undoTextView = new TextView(context);
        undoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        undoTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        undoTextView.setTextColor(Theme.getColor(Theme.key_undo_cancelColor));
        undoTextView.setText(LocaleController.getString("Undo", R.string.Undo));
        undoButton.addView(undoTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 6, 0, 0, 0));

        rect = new RectF(AndroidUtilities.dp(15), AndroidUtilities.dp(15), AndroidUtilities.dp(15 + 18), AndroidUtilities.dp(15 + 18));

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(AndroidUtilities.dp(2));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(Theme.getColor(Theme.key_undo_infoColor));

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textPaint.setColor(Theme.getColor(Theme.key_undo_infoColor));

        setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_undo_background)));

        setOnTouchListener((v, event) -> true);

        setVisibility(INVISIBLE);
    }

    private boolean isTooltipAction() {
        return currentAction == ACTION_ARCHIVE_HIDDEN || currentAction == ACTION_ARCHIVE_HINT || currentAction == ACTION_ARCHIVE_FEW_HINT ||
                currentAction == ACTION_ARCHIVE_PINNED || currentAction == ACTION_CONTACT_ADDED || currentAction == ACTION_OWNER_TRANSFERED_CHANNEL ||
                currentAction == ACTION_OWNER_TRANSFERED_GROUP;
    }

    private boolean hasSubInfo() {
        return currentAction == ACTION_ARCHIVE_HIDDEN || currentAction == ACTION_ARCHIVE_HINT || currentAction == ACTION_ARCHIVE_FEW_HINT || currentAction == ACTION_ARCHIVE_PINNED;
    }

    public void setAdditionalTranslationY(float value) {
        additionalTranslationY = value;
    }

    public void hide(boolean apply, int animated) {
        if (getVisibility() != VISIBLE || !isShowed) {
            return;
        }
        isShowed = false;
        if (currentActionRunnable != null) {
            if (apply) {
                currentActionRunnable.run();
            }
            currentActionRunnable = null;
        }
        if (currentCancelRunnable != null) {
            if (!apply) {
                currentCancelRunnable.run();
            }
            currentCancelRunnable = null;
        }
        if (currentAction == ACTION_CLEAR || currentAction == ACTION_DELETE) {
            MessagesController.getInstance(currentAccount).removeDialogAction(currentDialogId, currentAction == ACTION_CLEAR, apply);
        }
        if (animated != 0) {
            AnimatorSet animatorSet = new AnimatorSet();
            if (animated == 1) {
                animatorSet.playTogether(ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, AndroidUtilities.dp(8) + undoViewHeight));
                animatorSet.setDuration(250);
            } else {
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(this, View.SCALE_X, 0.8f),
                        ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.8f),
                        ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f));
                animatorSet.setDuration(180);
            }
            animatorSet.setInterpolator(new DecelerateInterpolator());
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(INVISIBLE);
                    setScaleX(1.0f);
                    setScaleY(1.0f);
                    setAlpha(1.0f);
                }
            });
            animatorSet.start();
        } else {
            setTranslationY(AndroidUtilities.dp(8) + undoViewHeight);
            setVisibility(INVISIBLE);
        }
    }

    public void showWithAction(long did, int action, Runnable actionRunnable) {
        showWithAction(did, action, null, actionRunnable, null);
    }

    public void showWithAction(long did, int action, Object infoObject) {
        showWithAction(did, action, infoObject, null, null);
    }

    public void showWithAction(long did, int action, Runnable actionRunnable, Runnable cancelRunnable) {
        showWithAction(did, action, null, actionRunnable, cancelRunnable);
    }

    public void showWithAction(long did, int action, Object infoObject, Runnable actionRunnable, Runnable cancelRunnable) {
        if (currentActionRunnable != null) {
            currentActionRunnable.run();
        }
        isShowed = true;
        currentActionRunnable = actionRunnable;
        currentCancelRunnable = cancelRunnable;
        currentDialogId = did;
        currentAction = action;
        timeLeft = 5000;
        lastUpdateTime = SystemClock.uptimeMillis();

        if (isTooltipAction()) {
            CharSequence infoText;
            String subInfoText;
            int icon;
            int size = 36;
            if (action == ACTION_OWNER_TRANSFERED_CHANNEL || action == ACTION_OWNER_TRANSFERED_GROUP) {
                TLRPC.User user = (TLRPC.User) infoObject;
                if (action == ACTION_OWNER_TRANSFERED_CHANNEL) {
                    infoText = AndroidUtilities.replaceTags(LocaleController.formatString("EditAdminTransferChannelToast", R.string.EditAdminTransferChannelToast, UserObject.getFirstName(user)));
                } else {
                    infoText = AndroidUtilities.replaceTags(LocaleController.formatString("EditAdminTransferGroupToast", R.string.EditAdminTransferGroupToast, UserObject.getFirstName(user)));
                }
                subInfoText = null;
                icon = R.raw.contact_check;
            } else if (action == ACTION_CONTACT_ADDED) {
                TLRPC.User user = (TLRPC.User) infoObject;
                infoText = LocaleController.formatString("NowInContacts", R.string.NowInContacts, UserObject.getFirstName(user));
                subInfoText = null;
                icon = R.raw.contact_check;
            } else if (action == ACTION_ARCHIVE_HIDDEN) {
                infoText = LocaleController.getString("ArchiveHidden", R.string.ArchiveHidden);
                subInfoText = LocaleController.getString("ArchiveHiddenInfo", R.string.ArchiveHiddenInfo);
                icon = R.raw.chats_swipearchive;
                size = 48;
            } else if (action == ACTION_ARCHIVE_PINNED) {
                infoText = LocaleController.getString("ArchivePinned", R.string.ArchivePinned);
                subInfoText = LocaleController.getString("ArchivePinnedInfo", R.string.ArchivePinnedInfo);
                icon = R.raw.chats_infotip;
            } else {
                if (action == ACTION_ARCHIVE_HINT) {
                    infoText = LocaleController.getString("ChatArchived", R.string.ChatArchived);
                } else {
                    infoText = LocaleController.getString("ChatsArchived", R.string.ChatsArchived);
                }
                subInfoText = LocaleController.getString("ChatArchivedInfo", R.string.ChatArchivedInfo);
                icon = R.raw.chats_infotip;
            }

            infoTextView.setText(infoText);
            leftImageView.setAnimation(icon, size, size);

            if (subInfoText != null) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) infoTextView.getLayoutParams();
                layoutParams.leftMargin = AndroidUtilities.dp(58);
                layoutParams.topMargin = AndroidUtilities.dp(6);
                subinfoTextView.setText(subInfoText);
                subinfoTextView.setVisibility(VISIBLE);
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                infoTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            } else {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) infoTextView.getLayoutParams();
                layoutParams.leftMargin = AndroidUtilities.dp(58);
                layoutParams.topMargin = AndroidUtilities.dp(13);
                subinfoTextView.setVisibility(GONE);
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                infoTextView.setTypeface(Typeface.DEFAULT);
            }

            undoButton.setVisibility(GONE);
            leftImageView.setVisibility(VISIBLE);

            leftImageView.setProgress(0);
            leftImageView.playAnimation();
        } else if (currentAction == ACTION_ARCHIVE || currentAction == ACTION_ARCHIVE_FEW) {
            if (action == ACTION_ARCHIVE) {
                infoTextView.setText(LocaleController.getString("ChatArchived", R.string.ChatArchived));
            } else {
                infoTextView.setText(LocaleController.getString("ChatsArchived", R.string.ChatsArchived));
            }

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) infoTextView.getLayoutParams();
            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.topMargin = AndroidUtilities.dp(13);

            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            undoButton.setVisibility(VISIBLE);
            infoTextView.setTypeface(Typeface.DEFAULT);
            subinfoTextView.setVisibility(GONE);

            leftImageView.setVisibility(VISIBLE);
            leftImageView.setAnimation(R.raw.chats_archived, 36, 36);
            leftImageView.setProgress(0);
            leftImageView.playAnimation();
        } else {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) infoTextView.getLayoutParams();
            layoutParams.leftMargin = AndroidUtilities.dp(45);
            layoutParams.topMargin = AndroidUtilities.dp(13);

            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            undoButton.setVisibility(VISIBLE);
            infoTextView.setTypeface(Typeface.DEFAULT);
            subinfoTextView.setVisibility(GONE);
            leftImageView.setVisibility(GONE);

            if (currentAction == ACTION_CLEAR) {
                infoTextView.setText(LocaleController.getString("HistoryClearedUndo", R.string.HistoryClearedUndo));
            } else {
                int lowerId = (int) did;
                if (lowerId < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lowerId);
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        infoTextView.setText(LocaleController.getString("ChannelDeletedUndo", R.string.ChannelDeletedUndo));
                    } else {
                        infoTextView.setText(LocaleController.getString("GroupDeletedUndo", R.string.GroupDeletedUndo));
                    }
                } else {
                    infoTextView.setText(LocaleController.getString("ChatDeletedUndo", R.string.ChatDeletedUndo));
                }
            }
            MessagesController.getInstance(currentAccount).addDialogAction(did, currentAction == ACTION_CLEAR);
        }

        AndroidUtilities.makeAccessibilityAnnouncement(infoTextView.getText() + (subinfoTextView.getVisibility() == VISIBLE ? ". " + subinfoTextView.getText() : ""));

        if (hasSubInfo()) {
            undoViewHeight = AndroidUtilities.dp(52);
        } else if (getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) getParent();
            int width = parent.getMeasuredWidth();
            if (width == 0) {
                width = AndroidUtilities.displaySize.x;
            }
            measureChildWithMargins(infoTextView, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0);
            undoViewHeight = infoTextView.getMeasuredHeight() + AndroidUtilities.dp(28);
        }

        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            setTranslationY(AndroidUtilities.dp(8) + undoViewHeight);
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, AndroidUtilities.dp(8) + undoViewHeight, -additionalTranslationY));
            animatorSet.setInterpolator(new DecelerateInterpolator());
            animatorSet.setDuration(180);
            animatorSet.start();
        }
    }

    protected boolean canUndo() {
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(undoViewHeight, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentAction == ACTION_DELETE || currentAction == ACTION_CLEAR) {
            int newSeconds = timeLeft > 0 ? (int) Math.ceil(timeLeft / 1000.0f) : 0;
            if (prevSeconds != newSeconds) {
                prevSeconds = newSeconds;
                timeLeftString = String.format("%d", Math.max(1, newSeconds));
                textWidth = (int) Math.ceil(textPaint.measureText(timeLeftString));
            }
            canvas.drawText(timeLeftString, rect.centerX() - textWidth / 2, AndroidUtilities.dp(28.2f), textPaint);
            canvas.drawArc(rect, -90, -360 * (timeLeft / 5000.0f), false, progressPaint);
        }

        long newTime = SystemClock.uptimeMillis();
        long dt = newTime - lastUpdateTime;
        timeLeft -= dt;
        lastUpdateTime = newTime;
        if (timeLeft <= 0) {
            hide(true, 1);
        }

        invalidate();
    }
}
