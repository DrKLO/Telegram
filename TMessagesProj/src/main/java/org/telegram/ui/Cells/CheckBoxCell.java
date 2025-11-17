/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;

public class CheckBoxCell extends FrameLayout {

    public final static int
            TYPE_CHECK_BOX_DEFAULT = 1,
            TYPE_CHECK_BOX_ENTER_PHONE = 2,
            TYPE_CHECK_BOX_UNKNOWN = 3,
            TYPE_CHECK_BOX_ROUND = 4,
            TYPE_CHECK_BOX_URL = 5,
            TYPE_CHECK_BOX_USER_GROUP = 6,
            TYPE_CHECK_BOX_USER = 7,
            TYPE_CHECK_BOX_ROUND_GROUP = 8;

    public int itemId;

    private final Theme.ResourcesProvider resourcesProvider;
    private LinkSpanDrawable.LinksTextView linksTextView;
    private AnimatedTextView animatedTextView;
    private View textView;
    private final TextView valueTextView;
    private final View checkBox;
    private CheckBoxSquare checkBoxSquare;
    private CheckBox2 checkBoxRound;
    private View collapsedArrow;
    private CollapseButton collapseButton;
    private BackupImageView avatarImageView;
    private AvatarDrawable avatarDrawable;

    private final int currentType;
    private final int checkBoxSize;
    private boolean needDivider;
    private boolean isMultiline;
    private boolean textAnimated;

    public CheckBoxCell(Context context, int type) {
        this(context, type, 17, null);
    }

    public CheckBoxCell(Context context, int type, Theme.ResourcesProvider resourcesProvider) {
        this(context, type, 17, resourcesProvider);
    }

    public CheckBoxCell(Context context, int type, int padding, Theme.ResourcesProvider resourcesProvider) {
        this(context, type, padding, false, resourcesProvider);
    }

    public CheckBoxCell(Context context, int type, int padding, boolean textAnimated, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.currentType = type;
        this.textAnimated = textAnimated;

        if (textAnimated) {
            animatedTextView = new AnimatedTextView(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    updateCollapseArrowTranslation();
                }
            };
            NotificationCenter.listenEmojiLoading(animatedTextView);
            animatedTextView.setEllipsizeByGradient(true);
            animatedTextView.setRightPadding(dp(8));
            animatedTextView.getDrawable().setHacks(true, true, false);
            animatedTextView.setTag(getThemedColor(type == TYPE_CHECK_BOX_DEFAULT || type == TYPE_CHECK_BOX_URL ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText));
            animatedTextView.setTextSize(dp(16));
            if (type == TYPE_CHECK_BOX_USER) {
                animatedTextView.setTypeface(AndroidUtilities.bold());
            }
            if (type == TYPE_CHECK_BOX_UNKNOWN) {
                animatedTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                addView(animatedTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 29, 0, 0, 0));
                animatedTextView.setPadding(0, 0, 0, dp(3));
            } else {
                animatedTextView.setRightPadding(dp(padding));
                animatedTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
                if (type == TYPE_CHECK_BOX_ENTER_PHONE) {
                    addView(animatedTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, (LocaleController.isRTL ? 8 : 29), 0, (LocaleController.isRTL ? 29 : 8), 0));
                } else {
                    int offset = isCheckboxRound() ? 56 : 46;
                    if (type == TYPE_CHECK_BOX_USER) {
                        offset += 39;
                    }
                    addView(animatedTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, (LocaleController.isRTL ? padding : offset + (padding - 17)), 0, (LocaleController.isRTL ? offset + (padding - 17) : padding), 0));
                }
            }
            textView = animatedTextView;
        } else {
            linksTextView = new LinkSpanDrawable.LinksTextView(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    updateCollapseArrowTranslation();
                }

                @Override
                public void setText(CharSequence text, BufferType type) {
                    text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), false);
                    super.setText(text, type);
                }
            };
            NotificationCenter.listenEmojiLoading(linksTextView);
            linksTextView.setTag(getThemedColor(type == TYPE_CHECK_BOX_DEFAULT || type == TYPE_CHECK_BOX_URL ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText));
            linksTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            linksTextView.setLines(1);
            linksTextView.setMaxLines(1);
            linksTextView.setSingleLine(true);
            linksTextView.setEllipsize(TextUtils.TruncateAt.END);
            if (type == TYPE_CHECK_BOX_USER) {
                linksTextView.setTypeface(AndroidUtilities.bold());
            }
            if (type == TYPE_CHECK_BOX_UNKNOWN) {
                linksTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                addView(linksTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 29, 0, 0, 0));
                linksTextView.setPadding(0, 0, 0, dp(3));
            } else {
                linksTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
                if (type == TYPE_CHECK_BOX_ENTER_PHONE) {
                    addView(linksTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, (LocaleController.isRTL ? 8 : 29), 0, (LocaleController.isRTL ? 29 : 8), 0));
                } else {
                    int offset = isCheckboxRound() ? 56 : 46;
                    if (type == TYPE_CHECK_BOX_USER) {
                        offset += 39;
                    }
                    addView(linksTextView, LayoutHelper.createFrame(isCheckboxRound() ? LayoutHelper.WRAP_CONTENT : LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, (LocaleController.isRTL ? padding : offset + (padding - 17)), 0, (LocaleController.isRTL ? offset + (padding - 17) : padding), 0));
                }
            }
            textView = linksTextView;
        }

        valueTextView = new TextView(context);
        valueTextView.setTag(type == TYPE_CHECK_BOX_DEFAULT || type == TYPE_CHECK_BOX_URL ? Theme.key_dialogTextBlue : Theme.key_windowBackgroundWhiteValueText);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, padding, 0, padding, 0));

        if (isCheckboxRound()) {
            checkBox = checkBoxRound = new CheckBox2(context, 21, resourcesProvider);
            checkBoxRound.setDrawUnchecked(true);
            checkBoxRound.setChecked(true, false);
            checkBoxRound.setDrawBackgroundAsArc(10);
            checkBoxSize = 21;
            addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : padding), 16, (LocaleController.isRTL ? padding : 0), 0));
        } else {
            checkBox = checkBoxSquare = new CheckBoxSquare(context, type == TYPE_CHECK_BOX_DEFAULT || type == TYPE_CHECK_BOX_URL, resourcesProvider);
            checkBoxSize = 18;
            if (type == TYPE_CHECK_BOX_URL) {
                addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, (LocaleController.isRTL ? 0 : padding), 0, (LocaleController.isRTL ? padding : 0), 0));
            } else if (type == TYPE_CHECK_BOX_UNKNOWN) {
                addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, Gravity.LEFT | Gravity.TOP, 0, 15, 0, 0));
            } else if (type == TYPE_CHECK_BOX_ENTER_PHONE) {
                addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 0, 15, 0, 0));
            } else {
                addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : padding), 16, (LocaleController.isRTL ? padding : 0), 0));
            }
        }

        if (type == TYPE_CHECK_BOX_USER_GROUP) {
            collapseButton = new CollapseButton(context, R.drawable.msg_folders_groups);
            addView(collapseButton, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL, padding, 0, padding - 11, 0));
        } else if (type == TYPE_CHECK_BOX_ROUND_GROUP) {
            collapseButton = new CollapseButton(context, 0);
            addView(collapseButton, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL, padding, 0, padding - 11, 0));
        } else if (type == TYPE_CHECK_BOX_USER) {
            avatarDrawable = new AvatarDrawable();
            avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(dp(17));
            addView(avatarImageView, LayoutHelper.createFrameRelatively(34, 34, Gravity.START | Gravity.CENTER_VERTICAL, 56, 0, 0, 0));
        }

        updateTextColor();
    }

    public boolean isCheckboxRound() {
        return currentType == TYPE_CHECK_BOX_ROUND || currentType == TYPE_CHECK_BOX_ROUND_GROUP || currentType == TYPE_CHECK_BOX_USER_GROUP || currentType == TYPE_CHECK_BOX_USER;
    }

    public void allowMultiline() {
        if (textAnimated) {
            return;
        }
        linksTextView.setLines(3);
        linksTextView.setMaxLines(3);
        linksTextView.setSingleLine(false);
    }

    public void updateTextColor() {
        if (textAnimated) {
            animatedTextView.setTextColor(getThemedColor(currentType == TYPE_CHECK_BOX_DEFAULT || currentType == TYPE_CHECK_BOX_URL ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText));
        } else {
            linksTextView.setTextColor(getThemedColor(currentType == TYPE_CHECK_BOX_DEFAULT || currentType == TYPE_CHECK_BOX_URL ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText));
            linksTextView.setLinkTextColor(getThemedColor(currentType == TYPE_CHECK_BOX_DEFAULT || currentType == TYPE_CHECK_BOX_URL ? Theme.key_dialogTextLink : Theme.key_windowBackgroundWhiteLinkText));
        }
        valueTextView.setTextColor(getThemedColor(currentType == TYPE_CHECK_BOX_DEFAULT || currentType == TYPE_CHECK_BOX_URL ? Theme.key_dialogTextBlue : Theme.key_windowBackgroundWhiteValueText));
    }

    private View click1Container, click2Container;
    public void setOnSectionsClickListener(OnClickListener onTextClick, OnClickListener onCheckboxClick) {
        if (onTextClick == null) {
            if (click1Container != null) {
                removeView(click1Container);
                click1Container = null;
            }
        } else {
            if (click1Container == null) {
                click1Container = new View(getContext());
                click1Container.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                addView(click1Container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            }
            click1Container.setOnClickListener(onTextClick);
        }

        if (onCheckboxClick == null) {
            if (click2Container != null) {
                removeView(click2Container);
                click2Container = null;
            }
        } else {
            if (click2Container == null) {
                click2Container = new View(getContext());
                addView(click2Container, LayoutHelper.createFrame(56, LayoutHelper.MATCH_PARENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            }
            click2Container.setOnClickListener(onCheckboxClick);
        }
    }

    public void setCollapsed(Boolean collapsed) {
        if (collapsed == null) {
            if (collapsedArrow != null) {
                removeView(collapsedArrow);
                collapsedArrow = null;
            }
        } else {
            if (collapsedArrow == null) {
                collapsedArrow = new View(getContext());
                Drawable drawable = getContext().getResources().getDrawable(R.drawable.arrow_more).mutate();
                drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
                collapsedArrow.setBackground(drawable);
                addView(collapsedArrow, LayoutHelper.createFrame(16, 16, Gravity.CENTER_VERTICAL));
            }

            updateCollapseArrowTranslation();
            collapsedArrow.animate().cancel();
            collapsedArrow.animate().rotation(collapsed ? 0 : 180).setDuration(340).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
    }

    private void updateCollapseArrowTranslation() {
        if (collapsedArrow == null) {
            return;
        }

        float textWidth = 0;
        try {
            textWidth = textView.getMeasuredWidth();
        } catch (Exception e) {}

        float translateX;
        if (LocaleController.isRTL) {
            translateX = textView.getRight() - textWidth - dp(20);
        } else {
            translateX = textView.getLeft() + textWidth + dp(4);
        }
        collapsedArrow.setTranslationX(translateX);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (currentType == TYPE_CHECK_BOX_UNKNOWN) {
            valueTextView.measure(MeasureSpec.makeMeasureSpec(dp(10), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.EXACTLY));
            textView.measure(MeasureSpec.makeMeasureSpec(width - dp(34), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.AT_MOST));
            checkBox.measure(MeasureSpec.makeMeasureSpec(dp(checkBoxSize), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(checkBoxSize), MeasureSpec.EXACTLY));

            setMeasuredDimension(textView.getMeasuredWidth() + dp(29), dp(50));
        } else if (isMultiline) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        } else {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(50) + (needDivider ? 1 : 0));

            int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - dp(isCheckboxRound() ? 60 : 34);
            if (textAnimated) {
                availableWidth += (int) animatedTextView.getRightPadding();
            }
            if (currentType == TYPE_CHECK_BOX_USER) {
                availableWidth -= dp(34);
            }
            if (valueTextView.getLayoutParams() instanceof MarginLayoutParams) {
                availableWidth -= ((MarginLayoutParams) valueTextView.getLayoutParams()).rightMargin;
            }

            int takenSpace = 0;
            valueTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth / 2, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
            takenSpace += valueTextView.getMeasuredWidth();
            if (collapseButton != null) {
                collapseButton.measure(MeasureSpec.makeMeasureSpec(availableWidth / 2, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
                takenSpace += collapseButton.getMeasuredWidth() - dp(11);
            }
            if (textView.getLayoutParams().width == LayoutHelper.MATCH_PARENT) {
                textView.measure(MeasureSpec.makeMeasureSpec(availableWidth - (int) Math.abs(textView.getTranslationX()) - takenSpace - dp(8), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST));
            } else {
                textView.measure(MeasureSpec.makeMeasureSpec(availableWidth - (int) Math.abs(textView.getTranslationX()) - takenSpace - dp(8), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST));
            }
            if (avatarImageView != null) {
                avatarImageView.measure(MeasureSpec.makeMeasureSpec(dp(34), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(34), MeasureSpec.EXACTLY));
            }
            checkBox.measure(MeasureSpec.makeMeasureSpec(dp(checkBoxSize), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(checkBoxSize), MeasureSpec.EXACTLY));
        }

        if (click1Container != null) {
            MarginLayoutParams margin = (MarginLayoutParams) click1Container.getLayoutParams();
            click1Container.measure(MeasureSpec.makeMeasureSpec(width - margin.leftMargin - margin.rightMargin, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.EXACTLY));
        }
        if (click2Container != null) {
            click2Container.measure(MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.EXACTLY));
        }
        if (collapsedArrow != null) {
            collapsedArrow.measure(
                    MeasureSpec.makeMeasureSpec(dp(16), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(16), MeasureSpec.EXACTLY)
            );
        }
    }

    public void setTextColor(int color) {
        if (textAnimated) {
            animatedTextView.setTextColor(color);
        } else {
            linksTextView.setTextColor(color);
        }
    }

    public void setText(CharSequence text, String value, boolean checked, boolean divider) {
        setText(text, value, checked, divider, false);
    }

    public void setText(CharSequence text, String value, boolean checked, boolean divider, boolean animated) {
        if (textAnimated) {
            text = Emoji.replaceEmoji(text, animatedTextView.getPaint().getFontMetricsInt(), false);
            animatedTextView.setText(text, animated);
        } else {
            linksTextView.setText(text);
        }
        if (checkBoxRound != null) {
            checkBoxRound.setChecked(checked, animated);
        } else {
            checkBoxSquare.setChecked(checked, animated);
        }
        valueTextView.setText(value);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setUserOrChat(TLObject userOrChat) {
        avatarDrawable.setInfo(userOrChat);
        avatarImageView.setForUserOrChat(userOrChat, avatarDrawable);
        CharSequence name;
        if (userOrChat instanceof TLRPC.User) {
            name = UserObject.getUserName((TLRPC.User) userOrChat);
        } else {
            name = ContactsController.formatName(userOrChat);
        }
        if (userOrChat instanceof TLRPC.User && ((TLRPC.User) userOrChat).id == MessagesController.getInstance(UserConfig.selectedAccount).telegramAntispamUserId) {
            name = LocaleController.getString(R.string.ChannelAntiSpamUser);
        }
        if (textAnimated) {
            name = Emoji.replaceEmoji(name, animatedTextView.getPaint().getFontMetricsInt(), false);
            animatedTextView.setText(name);
        } else {
            linksTextView.setText(name);
        }
    }

    public void setPad(int pad) {
        int offset = dp(pad * 40 * (LocaleController.isRTL ? -1 : 1));
        if (checkBox != null) {
            checkBox.setTranslationX(offset);
        }
        textView.setTranslationX(offset);
        if (avatarImageView != null) {
            avatarImageView.setTranslationX(offset);
        }
        if (click1Container != null) {
            click1Container.setTranslationX(offset);
        }
        if (click2Container != null) {
            click2Container.setTranslationX(offset);
        }
    }

    public void setNeedDivider(boolean needDivider) {
        this.needDivider = needDivider;
    }

    public void setMultiline(boolean value) {
        if (textAnimated) {
            return;
        }
        isMultiline = value;
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        LayoutParams layoutParams1 = (LayoutParams) checkBox.getLayoutParams();
        if (isMultiline) {
            linksTextView.setLines(0);
            linksTextView.setMaxLines(0);
            linksTextView.setSingleLine(false);
            linksTextView.setEllipsize(null);
            if (currentType != TYPE_CHECK_BOX_URL) {
//                layoutParams.height = LayoutParams.WRAP_CONTENT;
//                layoutParams.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP;
//                layoutParams.topMargin = dp(14);
//                layoutParams.bottomMargin = dp(10);
            }
        } else {
            linksTextView.setLines(1);
            linksTextView.setMaxLines(1);
            linksTextView.setSingleLine(true);
            linksTextView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setPadding(0, 0, 0, 0);

            layoutParams.height = LayoutParams.MATCH_PARENT;
            layoutParams.topMargin = 0;
            layoutParams1.topMargin = dp(15);
        }
        textView.setLayoutParams(layoutParams);
        checkBox.setLayoutParams(layoutParams1);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        textView.setAlpha(enabled ? 1.0f : 0.5f);
        valueTextView.setAlpha(enabled ? 1.0f : 0.5f);
        checkBox.setAlpha(enabled ? 1.0f : 0.5f);
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBoxRound != null) {
            checkBoxRound.setChecked(checked, animated);
        } else {
            checkBoxSquare.setChecked(checked, animated);
        }
    }

    public boolean isChecked() {
        if (checkBoxRound != null) {
            return checkBoxRound.isChecked();
        } else {
            return checkBoxSquare.isChecked();
        }
    }

    public TextView getTextView() {
        return linksTextView;
    }

    public AnimatedTextView getAnimatedTextView() {
        return animatedTextView;
    }

    public TextView getValueTextView() {
        return valueTextView;
    }

    public View getCheckBoxView() {
        return checkBox;
    }

    public void setCheckBoxColor(int background, int background1, int check) {
        if (checkBoxRound != null) {
            checkBoxRound.setColor(background, background, check);
        }
    }

    public CheckBox2 getCheckBoxRound() {
        return checkBoxRound;
    }

    public void setSquareCheckBoxColor(int uncheckedColor, int checkedColor, int checkColor) {
        if (checkBoxSquare != null) {
            checkBoxSquare.setColors(uncheckedColor, checkedColor, checkColor);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            int offset = dp(isCheckboxRound() ? 60 : 20) + (int) Math.abs(textView.getTranslationX());
            if (currentType == TYPE_CHECK_BOX_USER) {
                offset += dp(39);
            }
            Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : null;
            if (paint == null) {
                paint = Theme.dividerPaint;
            }
            canvas.drawLine(LocaleController.isRTL ? 0 : offset, getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? offset : 0), getMeasuredHeight() - 1, paint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.CheckBox");
        info.setCheckable(true);
        if (animatedTextView != null) {
            info.setText(animatedTextView.getText());
        } else if (linksTextView != null) {
            info.setText(linksTextView.getText());
        }
        info.setChecked(isChecked());
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void setIcon(int icon) {
        checkBoxRound.setIcon(icon);
    }

    public boolean hasIcon() {
        return checkBoxRound.hasIcon();
    }

    public void setCollapseButton(boolean collapsed, CharSequence text, View.OnClickListener onClick) {
        if (collapseButton != null) {
            collapseButton.set(collapsed, text);
            if (onClick != null) {
                collapseButton.setOnClickListener(onClick);
            }
        }
    }

    public class CollapseButton extends LinearLayout {

        @Nullable
        private ImageView iconView;
        private final AnimatedTextView textView;
        private final View collapsedArrow;

        @SuppressLint("UseCompatLoadingForDrawables")
        public CollapseButton(@NonNull Context context, int iconResId) {
            super(context);

            final int color = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);

            if (iconResId != 0) {
                iconView = new ImageView(context);
                iconView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                iconView.setImageResource(iconResId);
            }

            textView = new AnimatedTextView(context, false, true, false);
            textView.setTextSize(dp(13));
            textView.setTextColor(color);
            textView.setIncludeFontPadding(false);
            textView.setTypeface(AndroidUtilities.bold());

            collapsedArrow = new View(context);
            Drawable drawable = getContext().getResources().getDrawable(R.drawable.arrow_more).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            collapsedArrow.setBackground(drawable);

            if (LocaleController.isRTL) {
                addView(collapsedArrow, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 11, 0, 3, 0));
                addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 16, Gravity.CENTER_VERTICAL, 0, 0, iconView == null ? 11 : 3, 0));
                if (iconView != null) {
                    addView(iconView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 11, 0));
                }
            } else {
                if (iconView != null) {
                    addView(iconView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 11, 0, 3, 0));
                }
                addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 16, Gravity.CENTER_VERTICAL, iconView == null ? 11 : 0, 0, 3, 0));
                addView(collapsedArrow, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 11, 0));
            }

            setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), 16, 16));
            setClickable(true);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY));
        }

        public void set(boolean collapsed, CharSequence text) {
            textView.cancelAnimation();
            textView.setText(text);
            collapsedArrow.animate().cancel();
            collapsedArrow.animate().rotation(collapsed ? 0 : 180).setDuration(340).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
    }
}
