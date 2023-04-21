package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;


@SuppressWarnings("FieldCanBeLocal")
public class HintView extends FrameLayout {

    public static final int TYPE_SEARCH_AS_LIST = 3;
    public static final int TYPE_COMMON = 4;
    public static final int TYPE_POLL_VOTE = 5;

    public TextView textView;
    private ImageView imageView;
    private ImageView arrowImageView;
    private ChatMessageCell messageCell;
    private View currentView;
    private AnimatorSet animatorSet;
    private Runnable hideRunnable;
    private int currentType;
    private boolean isTopArrow;
    private String overrideText;
    private int shownY;
    private float translationY;
    private float extraTranslationY;

    private int bottomOffset;
    private long showingDuration = 2000;
    private final Theme.ResourcesProvider resourcesProvider;

    public HintView(Context context, int type) {
        this(context, type, false, null);
    }

    public HintView(Context context, int type, boolean topArrow) {
        this(context, type, topArrow, null);
    }

    public HintView(Context context, int type, Theme.ResourcesProvider resourcesProvider) {
        this(context, type, false, resourcesProvider);
    }

    public HintView(Context context, int type, boolean topArrow, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        currentType = type;
        isTopArrow = topArrow;

        textView = new CorrectlyMeasuringTextView(context);
        textView.setTextColor(getThemedColor(Theme.key_chat_gifSaveHintText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setMaxLines(2);
        if (type == 7 || type == 8 || type == 9) {
            textView.setMaxWidth(AndroidUtilities.dp(310));
        } else if (type == 4) {
            textView.setMaxWidth(AndroidUtilities.dp(280));
        } else {
            textView.setMaxWidth(AndroidUtilities.dp(250));
        }
        if (currentType == TYPE_SEARCH_AS_LIST) {
            textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            textView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(5), getThemedColor(Theme.key_chat_gifSaveHintBackground)));
            textView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 30, Gravity.LEFT | Gravity.TOP, 0, topArrow ? 6 : 0, 0, topArrow ? 0 : 6));
        } else {
            textView.setGravity(Gravity.LEFT | Gravity.TOP);
            textView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(6), getThemedColor(Theme.key_chat_gifSaveHintBackground)));
            textView.setPadding(AndroidUtilities.dp(currentType == 0 ? 54 : 8), AndroidUtilities.dp(7), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, topArrow ? 6 : 0, 0, topArrow ? 0 : 6));
        }

        if (type == 0) {
            textView.setText(LocaleController.getString("AutoplayVideoInfo", R.string.AutoplayVideoInfo));

            imageView = new ImageView(context);
            imageView.setImageResource(R.drawable.tooltip_sound);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_gifSaveHintText), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(38, 34, Gravity.LEFT | Gravity.TOP, 7, 7, 0, 0));
        }

        arrowImageView = new ImageView(context);
        arrowImageView.setImageResource(topArrow ? R.drawable.tooltip_arrow_up : R.drawable.tooltip_arrow);
        arrowImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_gifSaveHintBackground), PorterDuff.Mode.MULTIPLY));
        addView(arrowImageView, LayoutHelper.createFrame(14, 6, Gravity.LEFT | (topArrow ? Gravity.TOP : Gravity.BOTTOM), 0, 0, 0, 0));
    }

    public void setBackgroundColor(int background, int text) {
        textView.setTextColor(text);
        arrowImageView.setColorFilter(new PorterDuffColorFilter(background, PorterDuff.Mode.MULTIPLY));
        textView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(currentType == 7 || currentType == 8 ? 6 : 3), background));
    }

    public void setOverrideText(String text) {
        overrideText = text;
        textView.setText(text);
        if (messageCell != null) {
            ChatMessageCell cell = messageCell;
            messageCell = null;
            showForMessageCell(cell, false);
        }
    }

    public void setExtraTranslationY(float value) {
        extraTranslationY = value;
        setTranslationY(extraTranslationY + translationY);
    }

    public float getBaseTranslationY() {
        return translationY;
    }

    public boolean showForMessageCell(ChatMessageCell cell, boolean animated) {
        return showForMessageCell(cell, null, 0, 0, animated);
    }

    public boolean showForMessageCell(ChatMessageCell cell, Object object, int x, int y, boolean animated) {
        if (currentType == TYPE_POLL_VOTE && y == shownY && messageCell == cell || currentType != TYPE_POLL_VOTE && (currentType == 0 && getTag() != null || messageCell == cell)) {
            return false;
        }
        if (hideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            hideRunnable = null;
        }
        int[] position = new int[2];
        cell.getLocationInWindow(position);
        int top = position[1];
        View p = (View) getParent();
        p.getLocationInWindow(position);
        top -= position[1];

        View parentView = (View) cell.getParent();
        int centerX;
        if (currentType == 0) {
            ImageReceiver imageReceiver = cell.getPhotoImage();
            top += imageReceiver.getImageY();
            int height = (int) imageReceiver.getImageHeight();
            int bottom = top + height;
            int parentHeight = parentView.getMeasuredHeight();
            if (top <= getMeasuredHeight() + AndroidUtilities.dp(10) || bottom > parentHeight + height / 4) {
                return false;
            }
            centerX = cell.getNoSoundIconCenterX();
        } else if (currentType == TYPE_POLL_VOTE) {
            Integer count = (Integer) object;
            centerX = x;
            top += y;
            shownY = y;
            if (count == -1) {
                textView.setText(LocaleController.getString("PollSelectOption", R.string.PollSelectOption));
            } else {
                if (cell.getMessageObject().isQuiz()) {
                    if (count == 0) {
                        textView.setText(LocaleController.getString("NoVotesQuiz", R.string.NoVotesQuiz));
                    } else {
                        textView.setText(LocaleController.formatPluralString("Answer", count));
                    }
                } else {
                    if (count == 0) {
                        textView.setText(LocaleController.getString("NoVotes", R.string.NoVotes));
                    } else {
                        textView.setText(LocaleController.formatPluralString("Vote", count));
                    }
                }
            }
            measure(MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST));
        } else {
            MessageObject messageObject = cell.getMessageObject();
            if (overrideText == null) {
                textView.setText(LocaleController.getString("HidAccount", R.string.HidAccount));
            } else {
                textView.setText(overrideText);
            }
            measure(MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST));

            TLRPC.User user = cell.getCurrentUser();
            if (user != null && user.id == 0) {
                top += (cell.getMeasuredHeight() - Math.max(0, cell.getBottom() - parentView.getMeasuredHeight()) - AndroidUtilities.dp(50));
            } else {
                top += AndroidUtilities.dp(22);
                if (!messageObject.isOutOwner() && cell.isDrawNameLayout()) {
                    top += AndroidUtilities.dp(20);
                }
                if (!messageObject.shouldDrawWithoutBackground() && cell.isDrawTopic()) {
                    top += AndroidUtilities.dp(5) + cell.getDrawTopicHeight();
                }
            }
            if (!isTopArrow && top <= getMeasuredHeight() + AndroidUtilities.dp(10)) {
                return false;
            }
            centerX = cell.getForwardNameCenterX();
        }

        int parentWidth = parentView.getMeasuredWidth();
        if (isTopArrow) {
            setTranslationY(extraTranslationY + (translationY = AndroidUtilities.dp(44)));
        } else {
            setTranslationY(extraTranslationY + (translationY = top - getMeasuredHeight()));
        }
        int iconX = cell.getLeft() + centerX;
        int left = AndroidUtilities.dp(19);
        if (currentType == TYPE_POLL_VOTE) {
            int offset = Math.max(0, centerX - getMeasuredWidth() / 2 - AndroidUtilities.dp(19.1f));
            setTranslationX(offset);
            left += offset;
        } else if (iconX > parentView.getMeasuredWidth() / 2) {
            int offset = parentWidth - getMeasuredWidth() - AndroidUtilities.dp(38);
            setTranslationX(offset);
            left += offset;
        } else {
            setTranslationX(0);
        }
        float arrowX = cell.getLeft() + centerX - left - arrowImageView.getMeasuredWidth() / 2;
        arrowImageView.setTranslationX(arrowX);
        if (iconX > parentView.getMeasuredWidth() / 2) {
            if (arrowX < AndroidUtilities.dp(10)) {
                float diff = arrowX - AndroidUtilities.dp(10);
                setTranslationX(getTranslationX() + diff);
                arrowImageView.setTranslationX(arrowX - diff);
            }
        } else {
            if (arrowX > getMeasuredWidth() - AndroidUtilities.dp(14 + 10)) {
                float diff = arrowX - getMeasuredWidth() + AndroidUtilities.dp(14 + 10);
                setTranslationX(diff);
                arrowImageView.setTranslationX(arrowX - diff);
            } else if (arrowX < AndroidUtilities.dp(10)) {
                float diff = arrowX - AndroidUtilities.dp(10);
                setTranslationX(getTranslationX() + diff);
                arrowImageView.setTranslationX(arrowX - diff);
            }
        }

        messageCell = cell;
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }

        setTag(1);
        setVisibility(VISIBLE);
        if (animated) {
            animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f, 1.0f)
            );
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet = null;
                    AndroidUtilities.runOnUIThread(hideRunnable = () -> hide(), currentType == 0 ? 10000 : 2000);
                }
            });
            animatorSet.setDuration(300);
            animatorSet.start();
        } else {
            setAlpha(1.0f);
        }

        return true;
    }

    public boolean showForView(View view, boolean animated) {
        if (currentView == view || getTag() != null) {
            if (getTag() != null) {
                updatePosition(view);
            }
            return false;
        }
        if (hideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            hideRunnable = null;
        }
        updatePosition(view);

        currentView = view;
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }

        setTag(1);
        setVisibility(VISIBLE);
        if (animated) {
            animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f, 1.0f)
            );
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet = null;
                    AndroidUtilities.runOnUIThread(hideRunnable = () -> hide(), showingDuration);
                }
            });
            animatorSet.setDuration(300);
            animatorSet.start();
        } else {
            setAlpha(1.0f);
        }

        return true;
    }

    private void updatePosition(View view) {
        measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.AT_MOST));

        int[] position = new int[2];
        view.getLocationInWindow(position);

        int top = position[1] - AndroidUtilities.dp(4);

        if (currentType == 4) {
            top += AndroidUtilities.dp(4);
        } else if (currentType == 6) {
            top += view.getMeasuredHeight() + getMeasuredHeight() + AndroidUtilities.dp(10);
        } else if (currentType == 7 || currentType == 8 && isTopArrow) {
            top += view.getMeasuredHeight() + getMeasuredHeight() + AndroidUtilities.dp(8);
        } else if (currentType == 8) {
            top -= AndroidUtilities.dp(10);
        }

        int centerX;
        if (currentType == 8 && isTopArrow) {
            if (view instanceof SimpleTextView) {
                SimpleTextView textView = (SimpleTextView) view;
                Drawable drawable = textView.getRightDrawable();
                centerX = position[0] + (drawable != null ? drawable.getBounds().centerX() : textView.getTextWidth() / 2) - AndroidUtilities.dp(8);
            } else if (view instanceof TextView) {
                TextView textView = (TextView) view;
                centerX = position[0] + textView.getMeasuredWidth() - AndroidUtilities.dp(16.5f);
            } else {
                centerX = position[0];
            }
        } else if (currentType == TYPE_SEARCH_AS_LIST) {
            centerX = position[0];
        } else {
            centerX = position[0] + view.getMeasuredWidth() / 2;
        }

        View parentView = (View) getParent();
        parentView.getLocationInWindow(position);
        centerX -= position[0];
        top -= position[1];

        top -= bottomOffset;

        int parentWidth = parentView.getMeasuredWidth();
        if (isTopArrow && currentType != 6 && currentType != 7 && currentType != 8) {
            setTranslationY(extraTranslationY + (translationY = AndroidUtilities.dp(44)));
        } else {
            setTranslationY(extraTranslationY + (translationY = top - getMeasuredHeight()));
        }
        int offset;

        int leftMargin = 0;
        int rightMargin = 0;
        if (getLayoutParams() instanceof MarginLayoutParams) {
            leftMargin = ((MarginLayoutParams) getLayoutParams()).leftMargin;
            rightMargin = ((MarginLayoutParams) getLayoutParams()).rightMargin;
        }
        if (currentType == 8 && !isTopArrow) {
            offset = (parentWidth - leftMargin - rightMargin - getMeasuredWidth()) / 2;
        } else if (centerX > parentView.getMeasuredWidth() / 2) {
            if (currentType == TYPE_SEARCH_AS_LIST) {
                offset = (int) (parentWidth - getMeasuredWidth() * 1.5f);
                if (offset < 0) {
                    offset = 0;
                }
            } else {
                offset = parentWidth - getMeasuredWidth() - (leftMargin + rightMargin);
            }
        } else {
            if (currentType == TYPE_SEARCH_AS_LIST) {
                offset = centerX - getMeasuredWidth() / 2 - arrowImageView.getMeasuredWidth();
                if (offset < 0) {
                    offset = 0;
                }
            } else {
                offset = 0;
            }
        }
        setTranslationX(offset);
        float arrowX = centerX - (leftMargin + offset) - arrowImageView.getMeasuredWidth() / 2f;
        if (currentType == 7) {
            arrowX += AndroidUtilities.dp(2);
        }
        arrowImageView.setTranslationX(arrowX);
        if (centerX > parentView.getMeasuredWidth() / 2) {
            if (arrowX < AndroidUtilities.dp(10)) {
                float diff = arrowX - AndroidUtilities.dp(10);
                setTranslationX(getTranslationX() + diff);
                arrowImageView.setTranslationX(arrowX - diff);
            }
        } else {
            if (arrowX > getMeasuredWidth() - AndroidUtilities.dp(14 + 10)) {
                float diff = arrowX - getMeasuredWidth() + AndroidUtilities.dp(14 + 10);
                setTranslationX(diff);
                arrowImageView.setTranslationX(arrowX - diff);
            } else if (arrowX < AndroidUtilities.dp(10)) {
                float diff = arrowX - AndroidUtilities.dp(10);
                setTranslationX(getTranslationX() + diff);
                arrowImageView.setTranslationX(arrowX - diff);
            }
        }
    }

    public void hide() {
        hide(true);
    }

    public void hide(boolean animate) {
        if (getTag() == null) {
            return;
        }
        setTag(null);
        if (hideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            hideRunnable = null;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }
        if (animate) {
            animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f)
            );
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(View.INVISIBLE);
                    currentView = null;
                    messageCell = null;
                    animatorSet = null;
                }
            });
            animatorSet.setDuration(300);
            animatorSet.start();
        } else {
            setVisibility(View.INVISIBLE);
            currentView = null;
            messageCell = null;
            animatorSet = null;
        }
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }

    public ChatMessageCell getMessageCell() {
        return messageCell;
    }

    public void setShowingDuration(long showingDuration) {
        this.showingDuration = showingDuration;
    }

    public void setBottomOffset(int offset) {
        this.bottomOffset = offset;
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
