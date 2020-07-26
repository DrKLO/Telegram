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
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.CharacterStyle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
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

    private Object currentInfoObject;

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

    private boolean isShown;

    private boolean fromTop;

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
    public final static int ACTION_QR_SESSION_ACCEPTED = 11;
    public final static int ACTION_THEME_CHANGED = 12;
    public final static int ACTION_QUIZ_CORRECT = 13;
    public final static int ACTION_QUIZ_INCORRECT = 14;
    public final static int ACTION_FILTERS_AVAILABLE = 15;
    public final static int ACTION_DICE_INFO = 16;
    public final static int ACTION_DICE_NO_SEND_INFO = 17;
    public final static int ACTION_TEXT_INFO = 18;
    public final static int ACTION_CACHE_WAS_CLEARED = 19;
    public final static int ACTION_ADDED_TO_FOLDER = 20;
    public final static int ACTION_REMOVED_FROM_FOLDER = 21;
    public final static int ACTION_PROFILE_PHOTO_CHANGED = 22;
    public final static int ACTION_CHAT_UNARCHIVED = 23;

    private CharSequence infoText;

    public class LinkMovementMethodMy extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            try {
                boolean result;
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    CharacterStyle[] links = buffer.getSpans(widget.getSelectionStart(), widget.getSelectionEnd(), CharacterStyle.class);
                    if (links != null && links.length > 0) {
                        didPressUrl(links[0]);
                    }
                    Selection.removeSelection(buffer);
                    result = true;
                } else {
                    result = super.onTouchEvent(widget, buffer, event);
                }
                return result;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return false;
        }
    }

    public UndoView(Context context) {
        this(context, false);
    }

    public UndoView(Context context, boolean top) {
        super(context);
        fromTop = top;

        infoTextView = new TextView(context);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        infoTextView.setTextColor(Theme.getColor(Theme.key_undo_infoColor));
        infoTextView.setLinkTextColor(Theme.getColor(Theme.key_undo_cancelColor));
        infoTextView.setMovementMethod(new LinkMovementMethodMy());
        addView(infoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 45, 13, 0, 0));

        subinfoTextView = new TextView(context);
        subinfoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subinfoTextView.setTextColor(Theme.getColor(Theme.key_undo_infoColor));
        subinfoTextView.setLinkTextColor(Theme.getColor(Theme.key_undo_cancelColor));
        subinfoTextView.setHighlightColor(0);
        subinfoTextView.setSingleLine(true);
        subinfoTextView.setEllipsize(TextUtils.TruncateAt.END);
        subinfoTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
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

    public void setColors(int background, int text) {
        Theme.setDrawableColor(getBackground(), background);
        infoTextView.setTextColor(text);
        subinfoTextView.setTextColor(text);
        leftImageView.setLayerColor("info1.**", background | 0xff000000);
        leftImageView.setLayerColor("info2.**", background | 0xff000000);
    }

    private boolean isTooltipAction() {
        return currentAction == ACTION_ARCHIVE_HIDDEN || currentAction == ACTION_ARCHIVE_HINT || currentAction == ACTION_ARCHIVE_FEW_HINT ||
                currentAction == ACTION_ARCHIVE_PINNED || currentAction == ACTION_CONTACT_ADDED || currentAction == ACTION_OWNER_TRANSFERED_CHANNEL ||
                currentAction == ACTION_OWNER_TRANSFERED_GROUP || currentAction == ACTION_QUIZ_CORRECT || currentAction == ACTION_QUIZ_INCORRECT || currentAction == ACTION_CACHE_WAS_CLEARED ||
                currentAction == ACTION_ADDED_TO_FOLDER || currentAction == ACTION_REMOVED_FROM_FOLDER || currentAction == ACTION_PROFILE_PHOTO_CHANGED ||
                currentAction == ACTION_CHAT_UNARCHIVED;
    }

    private boolean hasSubInfo() {
        return currentAction == ACTION_QR_SESSION_ACCEPTED || currentAction == ACTION_ARCHIVE_HIDDEN || currentAction == ACTION_ARCHIVE_HINT || currentAction == ACTION_ARCHIVE_FEW_HINT ||
                currentAction == ACTION_QUIZ_CORRECT || currentAction == ACTION_QUIZ_INCORRECT ||
                currentAction == ACTION_ARCHIVE_PINNED && MessagesController.getInstance(currentAccount).dialogFilters.isEmpty();
    }

    public boolean isMultilineSubInfo() {
        return currentAction == ACTION_THEME_CHANGED || currentAction == ACTION_FILTERS_AVAILABLE;
    }

    public void setAdditionalTranslationY(float value) {
        additionalTranslationY = value;
    }

    public Object getCurrentInfoObject() {
        return currentInfoObject;
    }

    public void hide(boolean apply, int animated) {
        if (getVisibility() != VISIBLE || !isShown) {
            return;
        }
        currentInfoObject = null;
        isShown = false;
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
                animatorSet.playTogether(ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, (fromTop ? -1.0f : 1.0f) * (AndroidUtilities.dp(8) + undoViewHeight)));
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
            setTranslationY((fromTop ? -1.0f : 1.0f) * (AndroidUtilities.dp(8) + undoViewHeight));
            setVisibility(INVISIBLE);
        }
    }

    public void didPressUrl(CharacterStyle span) {

    }

    public void showWithAction(long did, int action, Runnable actionRunnable) {
        showWithAction(did, action, null, null, actionRunnable, null);
    }

    public void showWithAction(long did, int action, Object infoObject) {
        showWithAction(did, action, infoObject, null, null, null);
    }

    public void showWithAction(long did, int action, Runnable actionRunnable, Runnable cancelRunnable) {
        showWithAction(did, action, null, null, actionRunnable, cancelRunnable);
    }

    public void showWithAction(long did, int action, Object infoObject, Runnable actionRunnable, Runnable cancelRunnable) {
        showWithAction(did, action, infoObject, null, actionRunnable, cancelRunnable);
    }

    public void showWithAction(long did, int action, Object infoObject, Object infoObject2, Runnable actionRunnable, Runnable cancelRunnable) {
        if (currentActionRunnable != null) {
            currentActionRunnable.run();
        }
        isShown = true;
        currentActionRunnable = actionRunnable;
        currentCancelRunnable = cancelRunnable;
        currentDialogId = did;
        currentAction = action;
        timeLeft = 5000;
        currentInfoObject = infoObject;
        lastUpdateTime = SystemClock.elapsedRealtime();
        undoTextView.setText(LocaleController.getString("Undo", R.string.Undo).toUpperCase());
        undoImageView.setVisibility(VISIBLE);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);

        infoTextView.setGravity(Gravity.LEFT | Gravity.TOP);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) infoTextView.getLayoutParams();
        layoutParams.height = LayoutHelper.WRAP_CONTENT;
        layoutParams.bottomMargin = 0;

        leftImageView.setScaleType(ImageView.ScaleType.CENTER);
        FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) leftImageView.getLayoutParams();
        layoutParams2.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        layoutParams2.topMargin = layoutParams2.bottomMargin = 0;
        layoutParams2.leftMargin = AndroidUtilities.dp(3);
        layoutParams2.width = AndroidUtilities.dp(54);
        layoutParams2.height = LayoutHelper.WRAP_CONTENT;

        infoTextView.setMinHeight(0);

        if (isTooltipAction()) {
            CharSequence infoText;
            String subInfoText;
            int icon;
            int size = 36;
            boolean iconIsDrawable = false;
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
            } else if (action == ACTION_PROFILE_PHOTO_CHANGED) {
                if (did > 0) {
                    if (infoObject == null) {
                        infoText = LocaleController.getString("MainProfilePhotoSetHint", R.string.MainProfilePhotoSetHint);
                    } else {
                        infoText = LocaleController.getString("MainProfileVideoSetHint", R.string.MainProfileVideoSetHint);
                    }
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat((int) -did);
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        if (infoObject == null) {
                            infoText = LocaleController.getString("MainChannelProfilePhotoSetHint", R.string.MainChannelProfilePhotoSetHint);
                        } else {
                            infoText = LocaleController.getString("MainChannelProfileVideoSetHint", R.string.MainChannelProfileVideoSetHint);
                        }
                    } else {
                        if (infoObject == null) {
                            infoText = LocaleController.getString("MainGroupProfilePhotoSetHint", R.string.MainGroupProfilePhotoSetHint);
                        } else {
                            infoText = LocaleController.getString("MainGroupProfileVideoSetHint", R.string.MainGroupProfileVideoSetHint);
                        }
                    }
                }
                subInfoText = null;
                icon = R.raw.contact_check;
            } else if (action == ACTION_CHAT_UNARCHIVED) {
                infoText = LocaleController.getString("ChatWasMovedToMainList", R.string.ChatWasMovedToMainList);
                subInfoText = null;
                icon = R.raw.contact_check;
            } else if (action == ACTION_ARCHIVE_HIDDEN) {
                infoText = LocaleController.getString("ArchiveHidden", R.string.ArchiveHidden);
                subInfoText = LocaleController.getString("ArchiveHiddenInfo", R.string.ArchiveHiddenInfo);
                icon = R.raw.chats_swipearchive;
                size = 48;
            } else if (currentAction == ACTION_QUIZ_CORRECT) {
                infoText = LocaleController.getString("QuizWellDone", R.string.QuizWellDone);
                subInfoText = LocaleController.getString("QuizWellDoneInfo", R.string.QuizWellDoneInfo);
                icon = R.raw.wallet_congrats;
                size = 44;
            } else if (currentAction == ACTION_QUIZ_INCORRECT) {
                infoText = LocaleController.getString("QuizWrongAnswer", R.string.QuizWrongAnswer);
                subInfoText = LocaleController.getString("QuizWrongAnswerInfo", R.string.QuizWrongAnswerInfo);
                icon = R.raw.wallet_science;
                size = 44;
            } else if (action == ACTION_ARCHIVE_PINNED) {
                infoText = LocaleController.getString("ArchivePinned", R.string.ArchivePinned);
                if (MessagesController.getInstance(currentAccount).dialogFilters.isEmpty()) {
                    subInfoText = LocaleController.getString("ArchivePinnedInfo", R.string.ArchivePinnedInfo);
                } else {
                    subInfoText = null;
                }
                icon = R.raw.chats_infotip;
            } else if (action == ACTION_ADDED_TO_FOLDER || action == ACTION_REMOVED_FROM_FOLDER) {
                MessagesController.DialogFilter filter = (MessagesController.DialogFilter) infoObject2;
                if (did != 0) {
                    int lowerId = (int) did;
                    if (lowerId == 0) {
                        TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat((int) (did >> 32));
                        lowerId = encryptedChat.user_id;
                    }
                    if (lowerId > 0) {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(lowerId);
                        if (action == ACTION_ADDED_TO_FOLDER) {
                            infoText = AndroidUtilities.replaceTags(LocaleController.formatString("FilterUserAddedToExisting", R.string.FilterUserAddedToExisting, UserObject.getFirstName(user), filter.name));
                        } else {
                            infoText = AndroidUtilities.replaceTags(LocaleController.formatString("FilterUserRemovedFrom", R.string.FilterUserRemovedFrom, UserObject.getFirstName(user), filter.name));
                        }
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lowerId);
                        if (action == ACTION_ADDED_TO_FOLDER) {
                            infoText = AndroidUtilities.replaceTags(LocaleController.formatString("FilterChatAddedToExisting", R.string.FilterChatAddedToExisting, chat.title, filter.name));
                        } else {
                            infoText = AndroidUtilities.replaceTags(LocaleController.formatString("FilterChatRemovedFrom", R.string.FilterChatRemovedFrom, chat.title, filter.name));
                        }
                    }
                } else {
                    if (action == ACTION_ADDED_TO_FOLDER) {
                        infoText = AndroidUtilities.replaceTags(LocaleController.formatString("FilterChatsAddedToExisting", R.string.FilterChatsAddedToExisting, LocaleController.formatPluralString("Chats", (Integer) infoObject), filter.name));
                    } else {
                        infoText = AndroidUtilities.replaceTags(LocaleController.formatString("FilterChatsRemovedFrom", R.string.FilterChatsRemovedFrom, LocaleController.formatPluralString("Chats", (Integer) infoObject), filter.name));
                    }
                }
                subInfoText = null;
                icon = R.raw.contact_check;
                /*iconIsDrawable = true;
                if (action == ACTION_ADDED_TO_FOLDER) {
                    icon = R.drawable.toast_folder;
                } else {
                    icon = R.drawable.toast_folder_minus;
                }*/
            } else if (action == ACTION_CACHE_WAS_CLEARED) {
                infoText = this.infoText;
                subInfoText = null;
                icon = R.raw.chats_infotip;
            } else {
                if (action == ACTION_ARCHIVE_HINT) {
                    infoText = LocaleController.getString("ChatArchived", R.string.ChatArchived);
                } else {
                    infoText = LocaleController.getString("ChatsArchived", R.string.ChatsArchived);
                }
                if (MessagesController.getInstance(currentAccount).dialogFilters.isEmpty()) {
                    subInfoText = LocaleController.getString("ChatArchivedInfo", R.string.ChatArchivedInfo);
                } else {
                    subInfoText = null;
                }
                icon = R.raw.chats_infotip;
            }

            infoTextView.setText(infoText);
            if (iconIsDrawable) {
                leftImageView.setImageResource(icon);
            } else {
                leftImageView.setAnimation(icon, size, size);
            }

            if (subInfoText != null) {
                layoutParams.leftMargin = AndroidUtilities.dp(58);
                layoutParams.topMargin = AndroidUtilities.dp(6);
                layoutParams.rightMargin = 0;
                layoutParams = (FrameLayout.LayoutParams) subinfoTextView.getLayoutParams();
                layoutParams.rightMargin = 0;
                subinfoTextView.setText(subInfoText);
                subinfoTextView.setVisibility(VISIBLE);
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                infoTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            } else {
                layoutParams.leftMargin = AndroidUtilities.dp(58);
                layoutParams.topMargin = AndroidUtilities.dp(13);
                layoutParams.rightMargin = 0;
                subinfoTextView.setVisibility(GONE);
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                infoTextView.setTypeface(Typeface.DEFAULT);
            }

            undoButton.setVisibility(GONE);
            leftImageView.setVisibility(VISIBLE);

            if (!iconIsDrawable) {
                leftImageView.setProgress(0);
                leftImageView.playAnimation();
            }
        } else if (currentAction == ACTION_QR_SESSION_ACCEPTED) {
            TLRPC.TL_authorization authorization = (TLRPC.TL_authorization) infoObject;

            infoTextView.setText(LocaleController.getString("AuthAnotherClientOk", R.string.AuthAnotherClientOk));
            leftImageView.setAnimation(R.raw.contact_check, 36, 36);

            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.topMargin = AndroidUtilities.dp(6);
            subinfoTextView.setText(authorization.app_name);
            subinfoTextView.setVisibility(VISIBLE);
            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            infoTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            undoTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText2));
            undoImageView.setVisibility(GONE);
            undoButton.setVisibility(VISIBLE);
            leftImageView.setVisibility(VISIBLE);

            leftImageView.setProgress(0);
            leftImageView.playAnimation();
        } else if (currentAction == ACTION_FILTERS_AVAILABLE) {
            timeLeft = 10000;
            undoTextView.setText(LocaleController.getString("Open", R.string.Open).toUpperCase());
            infoTextView.setText(LocaleController.getString("FilterAvailableTitle", R.string.FilterAvailableTitle));
            leftImageView.setAnimation(R.raw.filter_new, 36, 36);
            int margin = (int) Math.ceil(undoTextView.getPaint().measureText(undoTextView.getText().toString())) + AndroidUtilities.dp(26);

            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.rightMargin = margin;
            layoutParams.topMargin = AndroidUtilities.dp(6);

            layoutParams = (FrameLayout.LayoutParams) subinfoTextView.getLayoutParams();
            layoutParams.rightMargin = margin;

            String text = LocaleController.getString("FilterAvailableText", R.string.FilterAvailableText);
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            int index1 = text.indexOf('*');
            int index2 = text.lastIndexOf('*');
            if (index1 >= 0 && index2 >= 0 && index1 != index2) {
                builder.replace(index2, index2 + 1, "");
                builder.replace(index1, index1 + 1, "");
                builder.setSpan(new URLSpanNoUnderline("tg://settings/folders"), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            subinfoTextView.setText(builder);
            subinfoTextView.setVisibility(VISIBLE);
            subinfoTextView.setSingleLine(false);
            subinfoTextView.setMaxLines(2);
            undoButton.setVisibility(VISIBLE);
            undoImageView.setVisibility(GONE);
            leftImageView.setVisibility(VISIBLE);

            leftImageView.setProgress(0);
            leftImageView.playAnimation();
        } else if (currentAction == ACTION_DICE_INFO || currentAction == ACTION_DICE_NO_SEND_INFO) {
            timeLeft = 4000;
            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            infoTextView.setGravity(Gravity.CENTER_VERTICAL);
            infoTextView.setMinHeight(AndroidUtilities.dp(30));
            String emoji = (String) infoObject;
            if ("\uD83C\uDFB2".equals(emoji)) {
                infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("DiceInfo2", R.string.DiceInfo2)));
                leftImageView.setImageResource(R.drawable.dice);
            } else{
                if ("\uD83C\uDFAF".equals(emoji)) {
                    infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("DartInfo", R.string.DartInfo)));
                } else {
                    String info = LocaleController.getServerString("DiceEmojiInfo_" + emoji);
                    if (!TextUtils.isEmpty(info)) {
                        infoTextView.setText(Emoji.replaceEmoji(info, infoTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                    } else {
                        infoTextView.setText(Emoji.replaceEmoji(LocaleController.formatString("DiceEmojiInfo", R.string.DiceEmojiInfo, emoji), infoTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                    }
                }
                leftImageView.setImageDrawable(Emoji.getEmojiDrawable(emoji));
                leftImageView.setScaleType(ImageView.ScaleType.FIT_XY);
                layoutParams.topMargin = AndroidUtilities.dp(14);
                layoutParams.bottomMargin = AndroidUtilities.dp(14);
                layoutParams2.leftMargin = AndroidUtilities.dp(14);
                layoutParams2.width = AndroidUtilities.dp(26);
                layoutParams2.height = AndroidUtilities.dp(26);
            }
            undoTextView.setText(LocaleController.getString("SendDice", R.string.SendDice));

            int margin;
            if (currentAction == ACTION_DICE_INFO) {
                margin = (int) Math.ceil(undoTextView.getPaint().measureText(undoTextView.getText().toString())) + AndroidUtilities.dp(26);
                undoTextView.setVisibility(VISIBLE);
                undoTextView.setTextColor(Theme.getColor(Theme.key_undo_cancelColor));
                undoImageView.setVisibility(GONE);
                undoButton.setVisibility(VISIBLE);
            } else {
                margin = AndroidUtilities.dp(8);
                undoTextView.setVisibility(GONE);
                undoButton.setVisibility(GONE);
            }

            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.rightMargin = margin;
            layoutParams.topMargin = AndroidUtilities.dp(6);
            layoutParams.bottomMargin = AndroidUtilities.dp(7);
            layoutParams.height = TableLayout.LayoutParams.MATCH_PARENT;

            subinfoTextView.setVisibility(GONE);
            leftImageView.setVisibility(VISIBLE);
        } else if (currentAction == ACTION_TEXT_INFO) {
            CharSequence info = (CharSequence) infoObject;
            timeLeft = Math.max(4000, Math.min(info.length() / 50 * 1600, 10000));
            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            infoTextView.setGravity(Gravity.CENTER_VERTICAL);
            infoTextView.setText(info);

            undoTextView.setVisibility(GONE);
            undoButton.setVisibility(GONE);
            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.rightMargin = AndroidUtilities.dp(8);
            layoutParams.topMargin = AndroidUtilities.dp(6);
            layoutParams.bottomMargin = AndroidUtilities.dp(7);
            layoutParams.height = TableLayout.LayoutParams.MATCH_PARENT;

            layoutParams2.gravity = Gravity.TOP | Gravity.LEFT;
            layoutParams2.topMargin = layoutParams2.bottomMargin = AndroidUtilities.dp(8);

            leftImageView.setVisibility(VISIBLE);
            leftImageView.setAnimation(R.raw.chats_infotip, 36, 36);
            leftImageView.setProgress(0);
            leftImageView.playAnimation();
        } else if (currentAction == ACTION_THEME_CHANGED) {
            infoTextView.setText(LocaleController.getString("ColorThemeChanged", R.string.ColorThemeChanged));
            leftImageView.setImageResource(R.drawable.toast_pallete);

            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.rightMargin = AndroidUtilities.dp(48);
            layoutParams.topMargin = AndroidUtilities.dp(6);

            layoutParams = (FrameLayout.LayoutParams) subinfoTextView.getLayoutParams();
            layoutParams.rightMargin = AndroidUtilities.dp(48);

            String text = LocaleController.getString("ColorThemeChangedInfo", R.string.ColorThemeChangedInfo);
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            int index1 = text.indexOf('*');
            int index2 = text.lastIndexOf('*');
            if (index1 >= 0 && index2 >= 0 && index1 != index2) {
                builder.replace(index2, index2 + 1, "");
                builder.replace(index1, index1 + 1, "");
                builder.setSpan(new URLSpanNoUnderline("tg://settings/themes"), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            subinfoTextView.setText(builder);
            subinfoTextView.setVisibility(VISIBLE);
            subinfoTextView.setSingleLine(false);
            subinfoTextView.setMaxLines(2);
            undoTextView.setVisibility(GONE);
            undoButton.setVisibility(VISIBLE);
            leftImageView.setVisibility(VISIBLE);
        } else if (currentAction == ACTION_ARCHIVE || currentAction == ACTION_ARCHIVE_FEW) {
            if (action == ACTION_ARCHIVE) {
                infoTextView.setText(LocaleController.getString("ChatArchived", R.string.ChatArchived));
            } else {
                infoTextView.setText(LocaleController.getString("ChatsArchived", R.string.ChatsArchived));
            }

            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.topMargin = AndroidUtilities.dp(13);
            layoutParams.rightMargin = 0;

            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            undoButton.setVisibility(VISIBLE);
            infoTextView.setTypeface(Typeface.DEFAULT);
            subinfoTextView.setVisibility(GONE);

            leftImageView.setVisibility(VISIBLE);
            leftImageView.setAnimation(R.raw.chats_archived, 36, 36);
            leftImageView.setProgress(0);
            leftImageView.playAnimation();
        } else {
            layoutParams.leftMargin = AndroidUtilities.dp(45);
            layoutParams.topMargin = AndroidUtilities.dp(13);
            layoutParams.rightMargin = 0;

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

        if (isMultilineSubInfo()) {
            ViewGroup parent = (ViewGroup) getParent();
            int width = parent.getMeasuredWidth();
            if (width == 0) {
                width = AndroidUtilities.displaySize.x;
            }
            width -= AndroidUtilities.dp(16);
            measureChildWithMargins(subinfoTextView, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0);
            undoViewHeight = subinfoTextView.getMeasuredHeight() + AndroidUtilities.dp(27 + 10);
        } else if (hasSubInfo()) {
            undoViewHeight = AndroidUtilities.dp(52);
        } else if (getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) getParent();
            int width = parent.getMeasuredWidth();
            if (width == 0) {
                width = AndroidUtilities.displaySize.x;
            }
            width -= AndroidUtilities.dp(16);
            measureChildWithMargins(infoTextView, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0);
            undoViewHeight = infoTextView.getMeasuredHeight() + AndroidUtilities.dp(currentAction == ACTION_DICE_INFO || currentAction == ACTION_DICE_NO_SEND_INFO || currentAction == ACTION_TEXT_INFO ? 14 : 28);
            if (currentAction == ACTION_TEXT_INFO) {
                undoViewHeight = Math.max(undoViewHeight, AndroidUtilities.dp(52));
            }
        }

        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            setTranslationY((fromTop ? -1.0f : 1.0f) * (AndroidUtilities.dp(8) + undoViewHeight));
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, (fromTop ? -1.0f : 1.0f) * (AndroidUtilities.dp(8) + undoViewHeight), (fromTop ? 1.0f : -1.0f) * additionalTranslationY));
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

        long newTime = SystemClock.elapsedRealtime();
        long dt = newTime - lastUpdateTime;
        timeLeft -= dt;
        lastUpdateTime = newTime;
        if (timeLeft <= 0) {
            hide(true, 1);
        }

        invalidate();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        infoTextView.invalidate();
        leftImageView.invalidate();
    }

    public void setInfoText(CharSequence text) {
        infoText = text;
    }
}
