package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.FilledTabsView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SimpleThemeDescription;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Stories.StoriesUtilities;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PeerColorActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private final boolean isChannel;
    private final long dialogId;

    private FrameLayout contentView;
    private ColoredActionBar colorBar;

    public static final int PAGE_NAME = 0;
    public static final int PAGE_PROFILE = 1;

    public Page namePage;
    public Page profilePage;

    public Page getCurrentPage() {
        return viewPager.getCurrentPosition() == 0 ? namePage : profilePage;
    }

    public boolean loading;

    private class Page extends FrameLayout {

        private ProfilePreview profilePreview;

        private RecyclerListView listView;
        private RecyclerView.Adapter listAdapter;
        private FrameLayout buttonContainer;
        private ButtonWithCounterView button;
        private PeerColorGrid peerColorPicker;

        private int selectedColor = -1;
        private long selectedEmoji = 0;
        private ThemePreviewMessagesCell messagesCellPreview;
        private SetReplyIconCell setReplyIconCell;

        private CharSequence buttonLocked, buttonUnlocked;

        int previewRow = -1;
        int colorPickerRow = -1;
        int infoRow = -1;
        int iconRow = -1;
        int info2Row = -1;
        int buttonRow = -1;
        int clearRow = -1;
        int shadowRow = -1;
        int rowCount;

        private static final int VIEW_TYPE_MESSAGE = 0;
        private static final int VIEW_TYPE_COLOR_PICKER = 1;
        private static final int VIEW_TYPE_INFO = 2;
        private static final int VIEW_TYPE_ICON = 3;
        private static final int VIEW_TYPE_BUTTONPAD = 5;
        private static final int VIEW_TYPE_TEXT = 6;

        private final int type;
        public Page(Context context, int type) {
            super(context);
            this.type = type;

            if (type == PAGE_PROFILE) {
                if (dialogId < 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                    selectedColor = ChatObject.getProfileColorId(chat);
                    selectedEmoji = ChatObject.getProfileEmojiId(chat);
                } else {
                    TLRPC.User user = getUserConfig().getCurrentUser();
                    selectedColor = UserObject.getProfileColorId(user);
                    selectedEmoji = UserObject.getProfileEmojiId(user);
                }
            } else {
                if (dialogId < 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                    selectedColor = ChatObject.getColorId(chat);
                    selectedEmoji = ChatObject.getEmojiId(chat);
                } else {
                    TLRPC.User user = getUserConfig().getCurrentUser();
                    selectedColor = UserObject.getColorId(user);
                    selectedEmoji = UserObject.getEmojiId(user);
                }
            }

            listView = new RecyclerListView(getContext(), getResourceProvider()) {
                @Override
                protected void onMeasure(int widthSpec, int heightSpec) {
                    super.onMeasure(widthSpec, heightSpec);
                    updateButtonY();
                }

                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    super.onLayout(changed, l, t, r, b);
                    updateButtonY();
                }
            };
            ((DefaultItemAnimator) listView.getItemAnimator()).setSupportsChangeAnimations(false);
            listView.setLayoutManager(new LinearLayoutManager(getContext()));
            listView.setAdapter(listAdapter = new RecyclerListView.SelectionAdapter() {
                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return holder.getItemViewType() == VIEW_TYPE_ICON || holder.getItemViewType() == VIEW_TYPE_TEXT;
                }

                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    View view;
                    switch (viewType) {
                        case VIEW_TYPE_MESSAGE:
                            ThemePreviewMessagesCell messagesCell = messagesCellPreview = new ThemePreviewMessagesCell(getContext(), parentLayout, ThemePreviewMessagesCell.TYPE_PEER_COLOR, dialogId, resourceProvider);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                messagesCell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                            }
                            messagesCell.fragment = PeerColorActivity.this;
                            view = messagesCell;
                            break;
                        default:
                        case VIEW_TYPE_INFO:
                            TextInfoPrivacyCell cell = new TextInfoPrivacyCell(getContext(), getResourceProvider());
                            view = cell;
                            break;
                        case VIEW_TYPE_COLOR_PICKER:
                            PeerColorGrid colorPicker = peerColorPicker = new PeerColorGrid(getContext(), type, currentAccount, resourceProvider);
                            colorPicker.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                            colorPicker.setSelected(selectedColor, false);
                            colorPicker.setOnColorClick(colorId -> {
                                selectedColor = colorId;
                                colorPicker.setSelected(colorId, true);
                                updateMessages();
                                if (setReplyIconCell != null) {
                                    setReplyIconCell.invalidate();
                                }
                                if (type == PAGE_PROFILE && colorBar != null) {
                                    colorBar.setColor(currentAccount, selectedColor, true);
                                }
                                if (profilePreview != null) {
                                    profilePreview.setColor(selectedColor, true);
                                }
                                if (profilePage != null && profilePage.profilePreview != null && namePage != null) {
                                    profilePage.profilePreview.overrideAvatarColor(namePage.selectedColor);
                                }
                                checkResetColorButton();
                            });
                            view = colorPicker;
                            break;
                        case VIEW_TYPE_BUTTONPAD:
                            view = new View(getContext()) {
                                @Override
                                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(14 + 48 + 14), MeasureSpec.EXACTLY));
                                }
                            };
                            break;
                        case VIEW_TYPE_ICON:
                            SetReplyIconCell setcell = setReplyIconCell = new SetReplyIconCell(getContext());
                            setcell.update(false);
                            view = setcell;
                            break;
                        case VIEW_TYPE_TEXT:
                            TextCell textCell = new TextCell(getContext(), getResourceProvider());
                            textCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                            view = textCell;
                            break;
                        case 4:
                            view = new View(getContext()) {
                                @Override
                                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                    super.onMeasure(
                                            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                                            MeasureSpec.makeMeasureSpec(dp(16), MeasureSpec.EXACTLY)
                                    );
                                }
                            };
                            view.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                            break;
                    }
                    return new RecyclerListView.Holder(view);
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    switch (getItemViewType(position)) {
                        case VIEW_TYPE_INFO:
                            TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                            if (position == infoRow) {
                                if (type == PAGE_NAME) {
                                    cell.setText(LocaleController.getString(isChannel ? R.string.ChannelColorHint : R.string.UserColorHint));
                                } else {
                                    cell.setText(LocaleController.getString(isChannel ? R.string.ChannelProfileHint : R.string.UserProfileHint));
                                }
                                cell.setBackground(Theme.getThemedDrawableByKey(getContext(), clearRow >= 0 ? R.drawable.greydivider : R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                            } else if (position == shadowRow) {
                                cell.setText("");
                                cell.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                            }
                            break;
                        case VIEW_TYPE_TEXT:
                            TextCell textCell = (TextCell) holder.itemView;
                            textCell.updateColors();
                            textCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                            if (position == clearRow) {
                                textCell.setText(LocaleController.getString(isChannel ? R.string.ChannelProfileColorReset : R.string.UserProfileColorReset), false);
                            }
                            break;
                    }
                }

                @Override
                public int getItemCount() {
                    return rowCount;
                }

                @Override
                public int getItemViewType(int position) {
                    if (position == previewRow) {
                        return VIEW_TYPE_MESSAGE;
                    }
                    if (position == infoRow || position == info2Row || position == shadowRow) {
                        return VIEW_TYPE_INFO;
                    }
                    if (position == colorPickerRow) {
                        return VIEW_TYPE_COLOR_PICKER;
                    }
                    if (position == iconRow) {
                        return VIEW_TYPE_ICON;
                    }
                    if (position == buttonRow) {
                        return VIEW_TYPE_BUTTONPAD;
                    }
                    if (position == clearRow) {
                        return VIEW_TYPE_TEXT;
                    }
                    if (position == getItemCount() - 1) {
                        return 4;
                    }
                    return VIEW_TYPE_INFO;
                }
            });
            listView.setOnItemClickListener((view, position) -> {
                if (view instanceof SetReplyIconCell) {
                    showSelectStatusDialog((SetReplyIconCell) view);
                } else if (position == clearRow) {
                    selectedColor = -1;
                    selectedEmoji = 0;
                    if (peerColorPicker != null) {
                        peerColorPicker.setSelected(selectedColor, true);
                    }
                    updateMessages();
                    if (type == PAGE_PROFILE) {
                        namePage.updateMessages();
                    }
                    if (setReplyIconCell != null) {
                        setReplyIconCell.update(true);
                    }
                    if (type == PAGE_PROFILE && colorBar != null) {
                        colorBar.setColor(currentAccount, selectedColor, true);
                    }
                    if (profilePreview != null) {
                        profilePreview.setColor(selectedColor, true);
                        profilePreview.setEmoji(selectedEmoji, true);
                    }
                    if (profilePage != null && profilePage.profilePreview != null && namePage != null) {
                        profilePage.profilePreview.overrideAvatarColor(namePage.selectedColor);
                    }
                    checkResetColorButton();
                }
            });
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            buttonContainer = new FrameLayout(getContext());
            buttonContainer.setPadding(dp(14), dp(14), dp(14), dp(14));
            buttonContainer.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

            SpannableStringBuilder buttonLock = new SpannableStringBuilder("l");
            buttonLock.setSpan(new ColoredImageSpan(R.drawable.msg_mini_lock2), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            buttonUnlocked = LocaleController.getString(isChannel ? R.string.ChannelColorApply : R.string.UserColorApplyIcon);
            buttonLocked = new SpannableStringBuilder(buttonLock).append(" ").append(buttonUnlocked);

            button = new ButtonWithCounterView(getContext(), getResourceProvider());
            button.text.setHacks(true, true, true);
            button.setText(isChannel ? buttonUnlocked : (!getUserConfig().isPremium() ? buttonLocked : buttonUnlocked), false);
            button.setOnClickListener(v -> buttonClick());
            buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));

            addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    updateButtonY();
                }
            });
            DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
            itemAnimator.setDurations(350);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setSupportsChangeAnimations(false);
            listView.setItemAnimator(itemAnimator);

            if (type == PAGE_PROFILE) {
                profilePreview = new ProfilePreview(getContext(), currentAccount, dialogId, resourceProvider);
                profilePreview.setColor(selectedColor, false);
                profilePreview.setEmoji(selectedEmoji, false);
                addView(profilePreview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));
            }

            updateColors();
            updateRows();

            setWillNotDraw(false);
        }

        private int actionBarHeight;

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (getParentLayout() != null) {
                getParentLayout().drawHeaderShadow(canvas, actionBarHeight);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (type == PAGE_NAME) {
                actionBarHeight = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
                ((MarginLayoutParams) listView.getLayoutParams()).topMargin = actionBarHeight;
            } else {
                actionBarHeight = dp(144) + AndroidUtilities.statusBarHeight;
                ((MarginLayoutParams) listView.getLayoutParams()).topMargin = actionBarHeight;
                ((MarginLayoutParams) profilePreview.getLayoutParams()).height = actionBarHeight;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        public boolean hasUnsavedChanged() {
            if (isChannel) {
                final TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                if (chat == null) return false;
                if (type == PAGE_NAME) {
                    return !(selectedColor == ChatObject.getColorId(chat) && selectedEmoji == ChatObject.getEmojiId(chat));
                } else {
                    return !(selectedColor == ChatObject.getProfileColorId(chat) && selectedEmoji == ChatObject.getProfileEmojiId(chat));
                }
            } else {
                final TLRPC.User me = getUserConfig().getCurrentUser();
                if (me == null) return false;
                if (type == PAGE_NAME) {
                    return !(selectedColor == UserObject.getColorId(me) && selectedEmoji == UserObject.getEmojiId(me));
                } else {
                    return !(selectedColor == UserObject.getProfileColorId(me) && selectedEmoji == UserObject.getProfileEmojiId(me));
                }
            }
        }

        private void updateButtonY() {
            if (buttonContainer == null) {
                return;
            }
            final int lastPosition = listAdapter.getItemCount() - 1;
            boolean foundLastPosition = false;
            int maxTop = 0;
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                final int position = listView.getChildAdapterPosition(child);
                if (position != RecyclerListView.NO_POSITION && position <= lastPosition) {
                    maxTop = Math.max(maxTop, child.getTop());
                    if (position == lastPosition) {
                        foundLastPosition = true;
                    }
                }
            }
            if (!foundLastPosition) {
                maxTop = listView.getMeasuredHeight();
            }
            buttonContainer.setTranslationY(Math.max(0, maxTop - (listView.getMeasuredHeight() - dp(14 + 48 + 14))));
        }


        private class SetReplyIconCell extends FrameLayout {

            private TextView textView;
            private Text offText;
            private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable imageDrawable;

            public SetReplyIconCell(Context context) {
                super(context);

                setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

                textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                if (type == PAGE_NAME) {
                    textView.setText(LocaleController.getString(isChannel ? R.string.ChannelReplyIcon : R.string.UserReplyIcon));
                } else {
                    textView.setText(LocaleController.getString(isChannel ? R.string.ChannelProfileIcon : R.string.UserProfileIcon));
                }
                addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 20, 0, 20, 0));

                imageDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, false, dp(24), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
            }

            public void updateColors() {
                textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            }

            public void update(boolean animated) {
                if (selectedEmoji != 0) {
                    imageDrawable.set(selectedEmoji, animated);
                    offText = null;
                } else {
                    imageDrawable.set((Drawable) null, animated);
                    if (offText == null) {
                        offText = new Text(LocaleController.getString(isChannel ? R.string.ChannelReplyIconOff : R.string.UserReplyIconOff), 16);
                    }
                }
            }

            public void updateImageBounds() {
                imageDrawable.setBounds(
                    LocaleController.isRTL ? dp(21) : getWidth() - imageDrawable.getIntrinsicWidth() - dp(21),
                    (getHeight() - imageDrawable.getIntrinsicHeight()) / 2,
                    LocaleController.isRTL ? dp(21) + imageDrawable.getIntrinsicWidth() : getWidth() - dp(21),
                    (getHeight() + imageDrawable.getIntrinsicHeight()) / 2
                );
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                updateImageBounds();
                imageDrawable.setColor(getColor());
                if (offText != null) {
                    offText.draw(canvas, getMeasuredWidth() - offText.getWidth() - dp(19), getMeasuredHeight() / 2f, getThemedColor(Theme.key_windowBackgroundWhiteBlueText4), 1f);
                } else {
                    imageDrawable.draw(canvas);
                }
            }

            public int getColor() {
                if (selectedColor < 0) {
                    if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) > .8f) {
                        return Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider);
                    } else if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) < .2f) {
                        return Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultTitle, resourceProvider), .5f);
                    } else {
                        return Theme.blendOver(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider), Theme.multAlpha(adaptProfileEmojiColor(Theme.getColor(Theme.key_actionBarDefault, resourceProvider)), .7f));
                    }
                } else if (selectedColor < 7) {
                    return getThemedColor(Theme.keys_avatar_nameInMessage[selectedColor]);
                } else {
                    MessagesController.PeerColors peerColors = type == PAGE_NAME ? MessagesController.getInstance(currentAccount).peerColors : MessagesController.getInstance(currentAccount).profilePeerColors;
                    if (peerColors != null) {
                        MessagesController.PeerColor color = peerColors.getColor(selectedColor);
                        if (color != null) {
                            return color.getColor1();
                        }
                    }
                }
                return getThemedColor(Theme.keys_avatar_nameInMessage[0]);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.EXACTLY)
                );
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                imageDrawable.detach();
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                imageDrawable.attach();
            }
        }

        private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow selectAnimatedEmojiDialog;
        public void showSelectStatusDialog(SetReplyIconCell cell) {
            if (selectAnimatedEmojiDialog != null || cell == null) {
                return;
            }
            final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
            int xoff = 0, yoff = 0;

            AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable scrimDrawable = null;
            View scrimDrawableParent = null;
            final int popupHeight = (int) Math.min(AndroidUtilities.dp(410 - 16 - 64), AndroidUtilities.displaySize.y * .75f);
            final int popupWidth = (int) Math.min(dp(340 - 16), AndroidUtilities.displaySize.x * .95f);
            if (cell != null) {
                scrimDrawable = cell.imageDrawable;
                scrimDrawableParent = cell;
                if (cell.imageDrawable != null) {
                    cell.imageDrawable.play();
                    cell.updateImageBounds();
                    AndroidUtilities.rectTmp2.set(cell.imageDrawable.getBounds());
                    if (type == PAGE_NAME) {
                        yoff = -AndroidUtilities.rectTmp2.centerY() + dp(12) - popupHeight;
                    } else {
                        yoff = -(cell.getHeight() - AndroidUtilities.rectTmp2.centerY()) - AndroidUtilities.dp(16);
                    }
                    xoff = AndroidUtilities.rectTmp2.centerX() - (AndroidUtilities.displaySize.x - popupWidth);
                }
            }
            SelectAnimatedEmojiDialog popupLayout = new SelectAnimatedEmojiDialog(PeerColorActivity.this, getContext(), true, xoff, type == PAGE_NAME ? SelectAnimatedEmojiDialog.TYPE_SET_REPLY_ICON : SelectAnimatedEmojiDialog.TYPE_SET_REPLY_ICON_BOTTOM, true, getResourceProvider(), type == PAGE_NAME ? 24 : 16, cell.getColor()) {
                @Override
                protected void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, Integer until) {
                    selectedEmoji = documentId == null ? 0 : documentId;
                    if (cell != null) {
                        cell.update(true);
                    }
                    if (profilePreview != null) {
                        profilePreview.setEmoji(selectedEmoji, true);
                    }
                    updateMessages();
                    if (popup[0] != null) {
                        selectAnimatedEmojiDialog = null;
                        popup[0].dismiss();
                    }
                }

                @Override
                protected float getScrimDrawableTranslationY() {
                    return 0;
                }
            };
            popupLayout.useAccentForPlus = true;
            popupLayout.setSelected(selectedEmoji == 0 ? null : selectedEmoji);
            popupLayout.setSaveState(3);
            popupLayout.setScrimDrawable(scrimDrawable, scrimDrawableParent);
            popup[0] = selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                @Override
                public void dismiss() {
                    super.dismiss();
                    selectAnimatedEmojiDialog = null;
                }
            };
            popup[0].showAsDropDown(cell, 0, yoff, Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT));
            popup[0].dimBehind();
        }

        public void checkResetColorButton() {
            if (type != PAGE_PROFILE) {
                return;
            }
            final int wasIndex = clearRow;
            updateRows();
            if (wasIndex >= 0 && clearRow < 0) {
                listAdapter.notifyItemRangeRemoved(wasIndex, 2);
            } else if (wasIndex < 0 && clearRow >= 0) {
                listAdapter.notifyItemRangeInserted(clearRow, 2);
            }
        }

        private void updateRows() {
            rowCount = 0;
            if (type == PAGE_NAME) {
                previewRow = rowCount++;
            }
            colorPickerRow = rowCount++;
            iconRow = rowCount++;
            infoRow = rowCount++;
            if (type == PAGE_PROFILE && selectedColor >= 0) {
                clearRow = rowCount++;
                shadowRow = rowCount++;
            } else {
                clearRow = -1;
                shadowRow = -1;
            }
            buttonRow = rowCount++;
        }

        private void updateMessages() {
            if (messagesCellPreview != null) {
                ChatMessageCell[] cells = messagesCellPreview.getCells();
                for (int i = 0; i < cells.length; ++i) {
                    if (cells[i] != null) {
                        MessageObject msg = cells[i].getMessageObject();
                        if (msg != null) {
                            if (peerColorPicker != null) {
                                msg.overrideLinkColor = peerColorPicker.getColorId();
                            }
                            msg.overrideLinkEmoji = selectedEmoji;
                            cells[i].setAvatar(msg);
                            cells[i].invalidate();
                        }
                    }
                }
            }
        }

        public void updateColors() {
            listView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            if (type == PAGE_PROFILE && colorBar != null) {
                colorBar.setColor(currentAccount, selectedColor, true);
            }
            if (button != null) {
                button.updateColors();
            }
            if (messagesCellPreview != null) {
                messagesCellPreview.invalidate();
            }
            if (profilePreview != null) {
                profilePreview.setColor(selectedColor, false);
            }
            buttonContainer.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            AndroidUtilities.forEachViews(listView, view -> {
                if (view instanceof PeerColorGrid) {
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    ((PeerColorGrid) view).updateColors();
                } else if (view instanceof TextCell) {
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    ((TextCell) view).updateColors();
                } else if (view instanceof SetReplyIconCell) {
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    ((SetReplyIconCell) view).updateColors();
                }
            });
        }

        public void premiumChanged() {
            if (button != null && !isChannel) {
                button.setText(!getUserConfig().isPremium() ? buttonLocked : buttonUnlocked, true);
            }
        }
    }

    private Theme.ResourcesProvider parentResourcesProvider;
    private final SparseIntArray currentColors = new SparseIntArray();
    private final Theme.MessageDrawable msgInDrawable, msgInDrawableSelected;

    public void updateThemeColors() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String dayThemeName = preferences.getString("lastDayTheme", "Blue");
        if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
            dayThemeName = "Blue";
        }
        String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
        if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
            nightThemeName = "Dark Blue";
        }
        Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
        if (dayThemeName.equals(nightThemeName)) {
            if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                dayThemeName = "Blue";
            } else {
                nightThemeName = "Dark Blue";
            }
        }

        if (isDark) {
            themeInfo = Theme.getTheme(nightThemeName);
        } else {
            themeInfo = Theme.getTheme(dayThemeName);
        }

        currentColors.clear();
        final String[] wallpaperLink = new String[1];
        final SparseIntArray themeColors;
        if (themeInfo.assetName != null) {
            themeColors = Theme.getThemeFileValues(null, themeInfo.assetName, wallpaperLink);
        } else {
            themeColors = Theme.getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink);
        }
        int[] defaultColors = Theme.getDefaultColors();
        if (defaultColors != null) {
            for (int i = 0; i < defaultColors.length; ++i) {
                currentColors.put(i, defaultColors[i]);
            }
        }
        for (int i = 0; i < themeColors.size(); ++i) {
            currentColors.put(themeColors.keyAt(i), themeColors.valueAt(i));
        }
        Theme.ThemeAccent accent = themeInfo.getAccent(false);
        if (accent != null) {
            accent.fillAccentColors(themeColors, currentColors);
        }

        if (namePage != null && namePage.messagesCellPreview != null) {
            Theme.BackgroundDrawableSettings bg = Theme.createBackgroundDrawable(themeInfo, currentColors, wallpaperLink[0], 0, true);
            namePage.messagesCellPreview.setOverrideBackground(bg.themedWallpaper != null ? bg.themedWallpaper : bg.wallpaper);
        }
    }

    public PeerColorActivity(long dialogId) {
        super();

        this.dialogId = dialogId;
        this.isChannel = dialogId != 0;

        resourceProvider = new Theme.ResourcesProvider() {
            @Override
            public int getColor(int key) {
                int index = currentColors.indexOfKey(key);
                if (index >= 0) {
                    return currentColors.valueAt(index);
                }
                if (parentResourcesProvider != null) {
                    return parentResourcesProvider.getColor(key);
                }
                return Theme.getColor(key);
            }

            @Override
            public Drawable getDrawable(String drawableKey) {
                if (drawableKey.equals(Theme.key_drawable_msgIn)) {
                    return msgInDrawable;
                }
                if (drawableKey.equals(Theme.key_drawable_msgInSelected)) {
                    return msgInDrawableSelected;
                }
                if (parentResourcesProvider != null) {
                    return parentResourcesProvider.getDrawable(drawableKey);
                }
                return Theme.getThemeDrawable(drawableKey);
            }

            @Override
            public Paint getPaint(String paintKey) {
                return Theme.ResourcesProvider.super.getPaint(paintKey);
            }

            @Override
            public boolean isDark() {
                return isDark;
            }
        };
        msgInDrawable = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, false, false, resourceProvider);
        msgInDrawableSelected = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, false, true, resourceProvider);
    }

    @Override
    public void setResourceProvider(Theme.ResourcesProvider resourceProvider) {
        parentResourcesProvider = resourceProvider;
    }

    private boolean startAtProfile;
    public PeerColorActivity startOnProfile() {
        this.startAtProfile = true;
        return this;
    }

    private BaseFragment bulletinFragment;
    public PeerColorActivity setOnApplied(BaseFragment bulletinFragment) {
        this.bulletinFragment = bulletinFragment;
        return this;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return dp(62);
            }

            @Override
            public boolean clipWithGradient(int tag) {
                return true;
            }
        });
        getMediaDataController().loadReplyIcons();
        if (MessagesController.getInstance(currentAccount).peerColors == null && BuildVars.DEBUG_PRIVATE_VERSION) {
            MessagesController.getInstance(currentAccount).loadAppConfig(true);
        }
        return super.onFragmentCreate();
    }

    private ViewPagerFixed viewPager;

    private ImageView backButton;
    private ImageView dayNightItem;

    private FrameLayout actionBarContainer;
    private FilledTabsView tabsView;
    private SimpleTextView titleView;

    @Override
    public View createView(Context context) {

        namePage = new Page(context, PAGE_NAME);
        profilePage = new Page(context, PAGE_PROFILE);

        actionBar.setCastShadows(false);
        actionBar.setVisibility(View.GONE);
        actionBar.setAllowOverlayTitle(false);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (actionBarContainer != null) {
                    ((MarginLayoutParams) actionBarContainer.getLayoutParams()).height = ActionBar.getCurrentActionBarHeight();
                    ((MarginLayoutParams) actionBarContainer.getLayoutParams()).topMargin = AndroidUtilities.statusBarHeight;
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        frameLayout.setFitsSystemWindows(true);

        colorBar = new ColoredActionBar(context, resourceProvider) {
            @Override
            protected void onUpdateColor() {
                updateLightStatusBar();
                updateActionBarButtonsColor();
                if (tabsView != null) {
                    tabsView.setBackgroundColor(getTabsViewBackgroundColor());
                }
            }

            private int lastBtnColor = 0;
            public void updateActionBarButtonsColor() {
                final int btnColor = getActionBarButtonColor();
                if (lastBtnColor != btnColor) {
                    if (backButton != null) {
                        lastBtnColor = btnColor;
                        backButton.setColorFilter(new PorterDuffColorFilter(btnColor, PorterDuff.Mode.SRC_IN));
                    }
                    if (dayNightItem != null) {
                        lastBtnColor = btnColor;
                        dayNightItem.setColorFilter(new PorterDuffColorFilter(btnColor, PorterDuff.Mode.SRC_IN));
                    }
                }
            }
        };
        if (profilePage != null) {
            colorBar.setColor(currentAccount, profilePage.selectedColor, false);
        }
        frameLayout.addView(colorBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        viewPager = new ViewPagerFixed(context) {
            @Override
            protected void onTabAnimationUpdate(boolean manual) {
                tabsView.setSelected(viewPager.getPositionAnimated());
                colorBar.setProgressToGradient(viewPager.getPositionAnimated());
            }
        };
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 2;
            }

            @Override
            public View createView(int viewType) {
                if (viewType == PAGE_NAME) return namePage;
                if (viewType == PAGE_PROFILE) return profilePage;
                return null;
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }

            @Override
            public void bindView(View view, int position, int viewType) {

            }
        });
        frameLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        actionBarContainer = new FrameLayout(context);
        frameLayout.addView(actionBarContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        if (!isChannel) {
            tabsView = new FilledTabsView(context);
            tabsView.setTabs(
                    LocaleController.getString(isChannel ? R.string.ChannelColorTabName : R.string.UserColorTabName),
                    LocaleController.getString(isChannel ? R.string.ChannelColorTabProfile : R.string.UserColorTabProfile)
            );
            tabsView.onTabSelected(tab -> {
                if (viewPager != null) {
                    viewPager.scrollToPosition(tab);
                }
            });
            actionBarContainer.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.CENTER));
        } else {
            titleView = new SimpleTextView(context);
            titleView.setText(LocaleController.getString(R.string.ChannelColorTitle2));
            titleView.setEllipsizeByGradient(true);
            titleView.setTextSize(20);
            titleView.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
            titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            actionBarContainer.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 72, 0, 72, 0));
        }

        if (startAtProfile) {
            viewPager.setPosition(1);
            if (tabsView != null) {
                tabsView.setSelected(1);
            }
            if (colorBar != null) {
                colorBar.setProgressToGradient(1f);
                updateLightStatusBar();
            }
        }

        backButton = new ImageView(context);
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarWhiteSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        backButton.setImageResource(R.drawable.ic_ab_back);
        backButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        backButton.setOnClickListener(v -> {
            if (onBackPressed()) {
                finishFragment();
            }
        });
        actionBarContainer.addView(backButton, LayoutHelper.createFrame(54, 54, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        sunDrawable = new RLottieDrawable(R.raw.sun, "" + R.raw.sun, dp(28), dp(28), true, null);
        sunDrawable.setPlayInDirectionOfCustomEndFrame(true);
        if (!isDark) {
            sunDrawable.setCustomEndFrame(0);
            sunDrawable.setCurrentFrame(0);
        } else {
            sunDrawable.setCurrentFrame(35);
            sunDrawable.setCustomEndFrame(36);
        }
        sunDrawable.beginApplyLayerColors();
        int color = Theme.getColor(Theme.key_chats_menuName);
        sunDrawable.setLayerColor("Sunny.**", color);
        sunDrawable.setLayerColor("Path 6.**", color);
        sunDrawable.setLayerColor("Path.**", color);
        sunDrawable.setLayerColor("Path 5.**", color);
        sunDrawable.commitApplyLayerColors();

        dayNightItem = new ImageView(context);
        dayNightItem.setScaleType(ImageView.ScaleType.CENTER);
        dayNightItem.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarWhiteSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        dayNightItem.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        dayNightItem.setOnClickListener(v -> {
            toggleTheme();
        });
        actionBarContainer.addView(dayNightItem, LayoutHelper.createFrame(54, 54, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        dayNightItem.setImageDrawable(sunDrawable);

        colorBar.updateColors();

        fragmentView = contentView = frameLayout;

        return contentView;
    }

    private boolean isDark = Theme.isCurrentThemeDark();
    private RLottieDrawable sunDrawable;

    public boolean hasUnsavedChanged() {
        return namePage.hasUnsavedChanged() || profilePage.hasUnsavedChanged();
    }

    private void setLoading(boolean loading) {
        if (namePage != null && namePage.button != null) {
            namePage.button.setLoading(loading);
        }
        if (profilePage != null && profilePage.button != null) {
            profilePage.button.setLoading(loading);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (!isChannel && hasUnsavedChanged() && getUserConfig().isPremium()) {
            showUnsavedAlert();
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (!isChannel && hasUnsavedChanged() && getUserConfig().isPremium()) {
            return false;
        }
        return super.isSwipeBackEnabled(event);
    }

    private void showUnsavedAlert() {
        if (getVisibleDialog() != null) {
            return;
        }
        AlertDialog alertDialog = new AlertDialog.Builder(getContext(), getResourceProvider())
            .setTitle(LocaleController.getString(isChannel ? R.string.ChannelColorUnsaved : R.string.UserColorUnsaved))
            .setMessage(LocaleController.getString(isChannel ? R.string.ChannelColorUnsavedMessage : R.string.UserColorUnsavedMessage))
            .setNegativeButton(LocaleController.getString(R.string.Dismiss), (di, w) -> {
                finishFragment();
            })
            .setPositiveButton(LocaleController.getString(R.string.ApplyTheme), (di, w) -> {
                buttonClick();
            })
            .create();
        showDialog(alertDialog);
        ((TextView) alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)).setTextColor(getThemedColor(Theme.key_text_RedBold));
    }

    private void buttonClick() {
        if (loading) {
            return;
        }
        if (isChannel) {
            finishFragment();
        } else {
            if (!getUserConfig().isPremium()) {
                showDialog(new PremiumFeatureBottomSheet(PeerColorActivity.this, PremiumPreviewFragment.PREMIUM_FEATURE_NAME_COLOR, true));
                return;
            }
        }

        apply();
        finishFragment();
        showBulletin();
    }

    private boolean applyingName, applyingProfile;
    private boolean applying;
    private void apply() {
        if (applying || !isChannel && !getUserConfig().isPremium()) {
            return;
        }

        if (isChannel) {
            finishFragment();
        } else {
            final TLRPC.User me = getUserConfig().getCurrentUser();
            if (me.color == null) {
                me.color = new TLRPC.TL_peerColor();
                me.color.color = (int) (me.id % 7);
            }
            if (namePage.selectedColor != UserObject.getColorId(me) || namePage.selectedEmoji != UserObject.getEmojiId(me)) {
                applyingName = true;
                TLRPC.TL_account_updateColor req = new TLRPC.TL_account_updateColor();
                me.flags2 |= 256;
                me.color.flags |= 1;
                req.flags |= 4;
                req.color = me.color.color = namePage.selectedColor;
                if (namePage.selectedEmoji != 0) {
                    req.flags |= 1;
                    me.color.flags |= 2;
                    req.background_emoji_id = me.color.background_emoji_id = namePage.selectedEmoji;
                } else {
                    me.color.flags &=~ 2;
                    me.color.background_emoji_id = 0;
                }
                getConnectionsManager().sendRequest(req, null);
            }
            if (profilePage.selectedColor != UserObject.getProfileColorId(me) || profilePage.selectedEmoji != UserObject.getProfileEmojiId(me)) {
                applyingProfile = true;
                if (me.profile_color == null) {
                    me.profile_color = new TLRPC.TL_peerColor();
                }
                TLRPC.TL_account_updateColor req = new TLRPC.TL_account_updateColor();
                req.for_profile = true;
                me.flags2 |= 512;
                if (profilePage.selectedColor < 0) {
                    me.profile_color.flags &=~ 1;
                } else {
                    me.profile_color.flags |= 1;
                    req.flags |= 4;
                    req.color = me.profile_color.color = profilePage.selectedColor;
                }
                if (profilePage.selectedEmoji != 0) {
                    req.flags |= 1;
                    me.profile_color.flags |= 2;
                    req.background_emoji_id = me.profile_color.background_emoji_id = profilePage.selectedEmoji;
                } else {
                    me.profile_color.flags &=~ 2;
                    me.profile_color.background_emoji_id = 0;
                }
                getConnectionsManager().sendRequest(req, null);
            }
            getMessagesController().putUser(me, false);
            getUserConfig().saveConfig(true);
            finishFragment();
            showBulletin();
        }
        applying = true;
        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
    }

    private void showBulletin() {
        if (bulletinFragment != null) {
            if (applyingName && (!applyingProfile || getCurrentPage() == namePage)) {
                BulletinFactory.of(bulletinFragment).createSimpleBulletin(
                    PeerColorDrawable.from(currentAccount, namePage.selectedColor),
                    LocaleController.getString(isChannel ? R.string.ChannelColorApplied : R.string.UserColorApplied)
                ).show();
            } else if (applyingProfile && (!applyingName || getCurrentPage() == profilePage)) {
                if (profilePage.selectedColor < 0) {
                    if (profilePage.selectedEmoji != 0) {
                        BulletinFactory.of(bulletinFragment).createStaticEmojiBulletin(
                            AnimatedEmojiDrawable.findDocument(currentAccount, profilePage.selectedEmoji),
                            LocaleController.getString(isChannel ? R.string.ChannelProfileColorEmojiApplied : R.string.UserProfileColorEmojiApplied)
                        ).show();
                    } else {
                        BulletinFactory.of(bulletinFragment).createSimpleBulletin(
                            R.raw.contact_check,
                            LocaleController.getString(isChannel ? R.string.ChannelProfileColorResetApplied : R.string.UserProfileColorResetApplied)
                        ).show();
                    }
                } else {
                    BulletinFactory.of(bulletinFragment).createSimpleBulletin(
                        PeerColorDrawable.fromProfile(currentAccount, profilePage.selectedColor),
                        LocaleController.getString(isChannel ? R.string.ChannelProfileColorApplied : R.string.UserProfileColorApplied)
                    ).show();
                }
            }
            bulletinFragment = null;
        }
    }

    @Override
    public void onFragmentClosed() {
        super.onFragmentClosed();
        Bulletin.removeDelegate(this);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
    }

    private List<TLRPC.TL_availableReaction> getAvailableReactions() {
        return getMediaDataController().getReactionsList();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return SimpleThemeDescription.createThemeDescriptions(this::updateColors,
                Theme.key_windowBackgroundWhite,
                Theme.key_windowBackgroundWhiteBlackText,
                Theme.key_windowBackgroundWhiteGrayText2,
                Theme.key_listSelector,
                Theme.key_windowBackgroundGray,
                Theme.key_windowBackgroundWhiteGrayText4,
                Theme.key_text_RedRegular,
                Theme.key_windowBackgroundChecked,
                Theme.key_windowBackgroundCheckText,
                Theme.key_switchTrackBlue,
                Theme.key_switchTrackBlueChecked,
                Theme.key_switchTrackBlueThumb,
                Theme.key_switchTrackBlueThumbChecked
        );
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateColors() {
        contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        if (titleView != null) {
            titleView.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        }
        namePage.updateColors();
        profilePage.updateColors();
        if (colorBar != null) {
            colorBar.updateColors();
        }
        setNavigationBarColor(getNavigationBarColor());
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount) return;
        if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            namePage.premiumChanged();
            profilePage.premiumChanged();
        }
    }

    public static class LevelLock extends Drawable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final Text text;
        private final float lockScale = .875f;
        private final Drawable lock;
        private final PremiumGradient.PremiumGradientTools gradientTools;

        public LevelLock(Context context, int lvl, Theme.ResourcesProvider resourcesProvider) {
            this(context, false, lvl, resourcesProvider);
        }

        public LevelLock(Context context, boolean plus, int lvl, Theme.ResourcesProvider resourcesProvider) {
            this.resourcesProvider = resourcesProvider;
            text = new Text(LocaleController.formatPluralString(plus ? "BoostLevelPlus" : "BoostLevel", lvl), 12, AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            lock = context.getResources().getDrawable(R.drawable.mini_switch_lock).mutate();
            lock.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            gradientTools = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradient1, Theme.key_premiumGradient2, -1, -1, -1, resourcesProvider);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int left = getBounds().left;
            int cy = getBounds().centerY();

            AndroidUtilities.rectTmp.set(left, cy - getIntrinsicHeight() / 2f, left + getIntrinsicWidth(), cy + getIntrinsicHeight() / 2f);
            gradientTools.gradientMatrix(AndroidUtilities.rectTmp);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(10), dp(10), gradientTools.paint);

            lock.setBounds(
                left + dp(3.33f),
                (int) (cy - lock.getIntrinsicHeight() * lockScale / 2f),
                (int) (left + dp(3.33f) + lock.getIntrinsicWidth() * lockScale),
                (int) (cy + lock.getIntrinsicHeight() * lockScale / 2f)
            );
            lock.draw(canvas);

            text.draw(canvas, left + dp(3.66f) + lock.getIntrinsicWidth() * lockScale, cy, Color.WHITE, 1f);
        }

        @Override
        public void setAlpha(int alpha) {}
        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return (int) (dp(3.66f + 6) + lock.getIntrinsicWidth() * lockScale + text.getWidth());
        }

        @Override
        public int getIntrinsicHeight() {
            return dp(18.33f);
        }
    }

    public static class ChangeNameColorCell extends View {
        private final int currentAccount;
        private final boolean isChannelOrGroup;
        private final boolean isGroup;
        private final Theme.ResourcesProvider resourcesProvider;

        private final Drawable drawable;
        private final Text buttonText;
        private LevelLock lock;

        private final Paint userTextBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Text userText;
        private int userTextColorKey = -1;
        private boolean needDivider;

        private PeerColorDrawable color1Drawable;
        private PeerColorDrawable color2Drawable;

        public ChangeNameColorCell(int currentAccount, long dialogId, Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            MessagesController mc = MessagesController.getInstance(currentAccount);
            TLRPC.Chat chat = mc.getChat(-dialogId);

            this.currentAccount = currentAccount;
            this.isChannelOrGroup = dialogId < 0;
            this.isGroup = isChannelOrGroup && !ChatObject.isChannelAndNotMegaGroup(chat);
            this.resourcesProvider = resourcesProvider;

            drawable = context.getResources().getDrawable(R.drawable.menu_edit_appearance).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), PorterDuff.Mode.SRC_IN));
            CharSequence button = LocaleController.getString(isChannelOrGroup ? (isGroup ? R.string.ChangeGroupAppearance : R.string.ChangeChannelNameColor2) : R.string.ChangeUserNameColor);
            if (isChannelOrGroup && !isGroup && MessagesController.getInstance(currentAccount).getMainSettings().getInt("boostingappearance", 0) < 3) {
                int minlvl = Integer.MAX_VALUE, maxlvl = 0;
                if (mc.peerColors != null) {
                    minlvl = Math.min(minlvl, mc.peerColors.maxLevel());
                    maxlvl = Math.max(maxlvl, mc.peerColors.maxLevel());
                    minlvl = Math.min(minlvl, mc.peerColors.minLevel());
                    maxlvl = Math.max(maxlvl, mc.peerColors.minLevel());
                }
                minlvl = Math.min(minlvl, mc.channelBgIconLevelMin);
                maxlvl = Math.min(maxlvl, mc.channelBgIconLevelMin);
                if (mc.profilePeerColors != null) {
                    minlvl = Math.min(minlvl, mc.profilePeerColors.maxLevel());
                    maxlvl = Math.max(maxlvl, mc.profilePeerColors.maxLevel());
                    minlvl = Math.min(minlvl, mc.profilePeerColors.minLevel());
                    maxlvl = Math.max(maxlvl, mc.profilePeerColors.minLevel());
                }
                minlvl = Math.min(minlvl, mc.channelProfileIconLevelMin);
                maxlvl = Math.max(maxlvl, mc.channelProfileIconLevelMin);
                minlvl = Math.min(minlvl, mc.channelEmojiStatusLevelMin);
                maxlvl = Math.max(maxlvl, mc.channelEmojiStatusLevelMin);
                minlvl = Math.min(minlvl, mc.channelWallpaperLevelMin);
                maxlvl = Math.max(maxlvl, mc.channelWallpaperLevelMin);
                minlvl = Math.min(minlvl, mc.channelCustomWallpaperLevelMin);
                maxlvl = Math.max(maxlvl, mc.channelCustomWallpaperLevelMin);
                int currentLevel = chat == null ? 0 : chat.level;
                if (currentLevel < maxlvl) {
                    lock = new LevelLock(context, true, Math.max(currentLevel, minlvl), resourcesProvider);
                }
            }
            if (isChannelOrGroup && lock == null) {
                button = TextCell.applyNewSpan(button);
            }
            buttonText = new Text(button, 16);
            updateColors();
        }

        public void updateColors() {
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(isChannelOrGroup ? Theme.key_windowBackgroundWhiteGrayIcon : Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), PorterDuff.Mode.SRC_IN));
            buttonText.setColor(Theme.getColor(isChannelOrGroup ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider));

            if (userText != null && userTextBackgroundPaint != null && userTextColorKey != -1) {
                final int color = Theme.getColor(userTextColorKey, resourcesProvider);
                userText.setColor(color);
                userTextBackgroundPaint.setColor(Theme.multAlpha(color, .10f));
            }
        }

        public void set(TLRPC.Chat chat, boolean divider) {
            if (chat == null) {
                return;
            }
            needDivider = divider;
            CharSequence text = chat.title;
            text = Emoji.replaceEmoji(text, Theme.chat_msgTextPaint.getFontMetricsInt(), false);
            userText = new Text(text, 13, AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            final int color;
            int colorId = ChatObject.getColorId(chat);
            if (colorId < 7) {
                color = Theme.getColor(userTextColorKey = Theme.keys_avatar_nameInMessage[colorId], resourcesProvider);
            } else {
                MessagesController.PeerColors peerColors = MessagesController.getInstance(UserConfig.selectedAccount).peerColors;
                MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
                if (peerColor != null) {
                    userTextColorKey = -1;
                    color = peerColor.getColor1();
                } else {
                    color = Theme.getColor(userTextColorKey = Theme.keys_avatar_nameInMessage[0], resourcesProvider);
                }
            }
            userText.setColor(color);
            userTextBackgroundPaint.setColor(Theme.multAlpha(color, .10f));

            color1Drawable = PeerColorDrawable.from(currentAccount, colorId).setRadius(dp(11));
            color2Drawable = ChatObject.getProfileColorId(chat) >= 0 ? PeerColorDrawable.fromProfile(currentAccount, ChatObject.getProfileColorId(chat)).setRadius(dp(11)) : null;
        }

        public void set(TLRPC.User user) {
            if (user == null) {
                return;
            }
            String name = user.first_name == null ? "" : user.first_name.trim();
            int index = name.indexOf(" ");
            if (index > 0) {
                name = name.substring(0, index);
            }
            CharSequence text = name;
            text = Emoji.replaceEmoji(text, Theme.chat_msgTextPaint.getFontMetricsInt(), false);
            userText = new Text(text, 13, AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            final int color;
            int colorId = UserObject.getColorId(user);
            if (colorId < 7) {
                color = Theme.getColor(userTextColorKey = Theme.keys_avatar_nameInMessage[colorId], resourcesProvider);
            } else {
                MessagesController.PeerColors peerColors = MessagesController.getInstance(UserConfig.selectedAccount).peerColors;
                MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
                if (peerColor != null) {
                    userTextColorKey = -1;
                    color = peerColor.getColor1();
                } else {
                    color = Theme.getColor(userTextColorKey = Theme.keys_avatar_nameInMessage[0], resourcesProvider);
                }
            }
            userText.setColor(color);
            userTextBackgroundPaint.setColor(Theme.multAlpha(color, .10f));

            color1Drawable = PeerColorDrawable.from(currentAccount, colorId).setRadius(dp(11));
            color2Drawable = UserObject.getProfileColorId(user) >= 0 ? PeerColorDrawable.fromProfile(currentAccount, UserObject.getProfileColorId(user)).setRadius(dp(11)) : null;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(50) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }

        private int rtl(int x) {
            return LocaleController.isRTL ? getMeasuredWidth() - x : x;
        }
        private float rtl(float x) {
            return LocaleController.isRTL ? getMeasuredWidth() - x : x;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            drawable.setBounds(
                rtl(dp(64) / 2) - drawable.getIntrinsicWidth() / 2,
                getMeasuredHeight() / 2 - drawable.getIntrinsicHeight() / 2,
                rtl(dp(64) / 2) + drawable.getIntrinsicWidth() / 2,
                getMeasuredHeight() / 2 + drawable.getIntrinsicHeight() / 2
            );
            drawable.draw(canvas);
            buttonText.ellipsize(getMeasuredWidth() - dp(64 + 7 + 100) - (lock != null ? lock.getIntrinsicWidth() + dp(8) : 0));
            float textX = LocaleController.isRTL ? getMeasuredWidth() - buttonText.getWidth() - dp(64 + 7) : dp(64 + 7);
            buttonText.draw(canvas, textX, getMeasuredHeight() / 2f);
            if (lock != null) {
                int x = (int) (textX + buttonText.getWidth() + dp(6));
                lock.setBounds(x, 0, x, getHeight());
                lock.draw(canvas);
            }

            if (isGroup && color2Drawable != null) {
                int x = LocaleController.isRTL ? dp(24 + 16 + 18) : getMeasuredWidth() - dp(24);
                color2Drawable.setBounds(x - dp(11), (getMeasuredHeight() - dp(11)) / 2, x, (getMeasuredHeight() + dp(11)) / 2);
                color2Drawable.stroke(dpf2(3), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                color2Drawable.draw(canvas);
            } else if (color1Drawable != null && color2Drawable != null) {

                int x = LocaleController.isRTL ? dp(24 + 16 + 18) : getMeasuredWidth() - dp(24);
                color2Drawable.setBounds(x - dp(11), (getMeasuredHeight() - dp(11)) / 2, x, (getMeasuredHeight() + dp(11)) / 2);
                color2Drawable.stroke(dpf2(3), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                color2Drawable.draw(canvas);

                x -= dp(18);
                color1Drawable.setBounds(x - dp(11), (getMeasuredHeight() - dp(11)) / 2, x, (getMeasuredHeight() + dp(11)) / 2);
                color1Drawable.stroke(dpf2(3), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                color1Drawable.draw(canvas);

            } else if (userText != null && !isGroup) {
                final int maxWidth = (int) (getMeasuredWidth() - dp(64 + 7 + 15 + 9 + 9 + 12) - Math.min(buttonText.getWidth() + (lock == null ? 0 : lock.getIntrinsicWidth() + dp(6 + 6)), getMeasuredWidth() - dp(64 + 100)));
                final int w = (int) Math.min(userText.getWidth(), maxWidth);

                AndroidUtilities.rectTmp.set(
                        LocaleController.isRTL ? dp(15) : getMeasuredWidth() - dp(15 + 9 + 9) - w,
                        (getMeasuredHeight() - dp(22)) / 2f,
                        LocaleController.isRTL ? dp(15 + 9 + 9) + w : getMeasuredWidth() - dp(15),
                        (getMeasuredHeight() + dp(22)) / 2f
                );
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(12), dp(12), userTextBackgroundPaint);

                userText
                    .ellipsize(maxWidth)
                    .draw(canvas, LocaleController.isRTL ? dp(15 + 9) : getMeasuredWidth() - dp(15 + 9) - w, getMeasuredHeight() / 2f);
            }

            if (needDivider) {
                Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : null;
                if (paint == null) {
                    paint = Theme.dividerPaint;
                }
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(64), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(64) : 0), getMeasuredHeight() - 1, paint);
            }
        }
    }

    public static class PeerColorGrid extends View {
        private final Theme.ResourcesProvider resourcesProvider;
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        { backgroundPaint.setStyle(Paint.Style.STROKE); }

        public class ColorButton {
            private final Paint paint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint paint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path circlePath = new Path();
            private final Path color2Path = new Path();
            private boolean hasColor2, hasColor3;

            private final ButtonBounce bounce = new ButtonBounce(PeerColorGrid.this);

            public ColorButton() {}

            public void set(int color) {
                hasColor2 = hasColor3 = false;
                paint1.setColor(color);
            }

            public void set(int color1, int color2) {
                hasColor2 = true;
                hasColor3 = false;
                paint1.setColor(color1);
                paint2.setColor(color2);
            }

            public void set(MessagesController.PeerColor color) {
                if (color == null) {
                    return;
                }
                final boolean dark = resourcesProvider == null ? Theme.isCurrentThemeDark() : resourcesProvider.isDark();
                if (type == PAGE_NAME) {
                    if (dark && color.hasColor2() && !color.hasColor3()) {
                        paint1.setColor(color.getColor(1, resourcesProvider));
                        paint2.setColor(color.getColor(0, resourcesProvider));
                    } else {
                        paint1.setColor(color.getColor(0, resourcesProvider));
                        paint2.setColor(color.getColor(1, resourcesProvider));
                    }
                    paint3.setColor(color.getColor(2, resourcesProvider));
                    hasColor2 = color.hasColor2(dark);
                    hasColor3 = color.hasColor3(dark);
                } else {
                    paint1.setColor(color.getColor(0, resourcesProvider));
                    paint2.setColor(color.hasColor6(dark) ? color.getColor(1, resourcesProvider) : color.getColor(0, resourcesProvider));
                    hasColor2 = color.hasColor6(dark);
                    hasColor3 = false;
                }
            }

            private boolean selected;
            private final AnimatedFloat selectedT = new AnimatedFloat(PeerColorGrid.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            public void setSelected(boolean selected, boolean animated) {
                this.selected = selected;
                if (!animated) {
                    selectedT.set(selected, true);
                }
                invalidate();
            }

            public int id;
            private final RectF bounds = new RectF();
            public final RectF clickBounds = new RectF();
            public void layout(RectF bounds) {
                this.bounds.set(bounds);
            }
            public void layoutClickBounds(RectF bounds) {
                this.clickBounds.set(bounds);
            }

            protected void draw(Canvas canvas) {
                canvas.save();
                final float s = bounce.getScale(.05f);
                canvas.scale(s, s, bounds.centerX(), bounds.centerY());

                canvas.save();
                circlePath.rewind();
                circlePath.addCircle(bounds.centerX(), bounds.centerY(), Math.min(bounds.height() / 2f, bounds.width() / 2f), Path.Direction.CW);
                canvas.clipPath(circlePath);
                canvas.drawPaint(paint1);
                if (hasColor2) {
                    color2Path.rewind();
                    color2Path.moveTo(bounds.right, bounds.top);
                    color2Path.lineTo(bounds.right, bounds.bottom);
                    color2Path.lineTo(bounds.left, bounds.bottom);
                    color2Path.close();
                    canvas.drawPath(color2Path, paint2);
                }
                canvas.restore();

                if (hasColor3) {
                    canvas.save();
                    final float color3Size = (bounds.width() * .315f);
                    AndroidUtilities.rectTmp.set(
                        bounds.centerX() - color3Size / 2f,
                        bounds.centerY() - color3Size / 2f,
                        bounds.centerX() + color3Size / 2f,
                        bounds.centerY() + color3Size / 2f
                    );
                    canvas.rotate(45f, bounds.centerX(), bounds.centerY());
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(2.33f), dp(2.33f), paint3);
                    canvas.restore();
                }

                final float selectT = selectedT.set(selected);

                if (selectT > 0) {
                    backgroundPaint.setStrokeWidth(dpf2(2));
                    backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    canvas.drawCircle(
                        bounds.centerX(), bounds.centerY(),
                        Math.min(bounds.height() / 2f, bounds.width() / 2f) + backgroundPaint.getStrokeWidth() * AndroidUtilities.lerp(.5f, -2f, selectT),
                        backgroundPaint
                    );
                }

                canvas.restore();
            }

            private boolean pressed;
            public boolean isPressed() {
                return pressed;
            }

            public void setPressed(boolean pressed) {
                bounce.setPressed(this.pressed = pressed);
            }
        }

        private final int type;
        private final int currentAccount;

        private ColorButton[] buttons;

        public PeerColorGrid(Context context, int type, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.type = type;
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;
        }

        public void updateColors() {
            if (buttons == null) return;
            final MessagesController mc = MessagesController.getInstance(currentAccount);
            final MessagesController.PeerColors peerColors = type == PAGE_NAME ? mc.peerColors : mc.profilePeerColors;
            for (int i = 0; i < buttons.length; ++i) {
                if (i < 7 && type == PAGE_NAME) {
                    buttons[i].id = order[i];
                    buttons[i].set(Theme.getColor(Theme.keys_avatar_nameInMessage[order[i]], resourcesProvider));
                } else {
                    final int id = i;
                    if (peerColors != null && id >= 0 && id < peerColors.colors.size()) {
                        buttons[i].id = peerColors.colors.get(id).id;
                        buttons[i].set(peerColors.colors.get(id));
                    }
                }
            }
            invalidate();
        }
        final int[] order = new int[] { 5, 3, 1, 0, 2, 4, 6 };

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);

            final MessagesController mc = MessagesController.getInstance(currentAccount);
            final MessagesController.PeerColors peerColors = type == PAGE_NAME ? mc.peerColors : mc.profilePeerColors;
            final int colorsCount = peerColors == null ? 0 : peerColors.colors.size();
            final int columns = type == PAGE_NAME ? 7 : 8;

            final float iconSize = Math.min(dp(38 + 16), width / (columns + (columns + 1) * .28947f));
            final float horizontalSeparator = Math.min(iconSize * .28947f, dp(8));
            final float verticalSeparator = Math.min(iconSize * .315789474f, dp(11.33f));

            final int rows = colorsCount / columns;
            final int height = (int) (iconSize * rows + verticalSeparator * (rows + 1));

            setMeasuredDimension(width, height);

            if (buttons == null || buttons.length != colorsCount) {
                buttons = new ColorButton[colorsCount];
                for (int i = 0; i < colorsCount; ++i) {
                    buttons[i] = new ColorButton();
                    if (peerColors != null && i >= 0 && i < peerColors.colors.size()) {
                        buttons[i].id = peerColors.colors.get(i).id;
                        buttons[i].set(peerColors.colors.get(i));
                    }
                }
            }
            final float itemsWidth = iconSize * columns + horizontalSeparator * (columns + 1);
            final float startX = (width - itemsWidth) / 2f + horizontalSeparator;
            if (buttons != null) {
                float x = startX, y = verticalSeparator;
                for (int i = 0; i < buttons.length; ++i) {
                    AndroidUtilities.rectTmp.set(x, y, x + iconSize, y + iconSize);
                    buttons[i].layout(AndroidUtilities.rectTmp);
                    AndroidUtilities.rectTmp.inset(-horizontalSeparator / 2, -verticalSeparator / 2);
                    buttons[i].layoutClickBounds(AndroidUtilities.rectTmp);
                    buttons[i].setSelected(buttons[i].id == selectedColorId, false);

                    if (i % columns == (columns - 1)) {
                        x = startX;
                        y += iconSize + verticalSeparator;
                    } else {
                        x += iconSize + horizontalSeparator;
                    }
                }
            }
        }

        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean needDivider = true;
        public void setDivider(boolean needDivider) {
            this.needDivider = needDivider;
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (buttons != null) {
                for (int i = 0; i < buttons.length; ++i) {
                    buttons[i].draw(canvas);
                }
            }
            if (needDivider) {
                dividerPaint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
                canvas.drawRect(dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - dp(21), getMeasuredHeight(), dividerPaint);
            }
        }

        private int selectedColorId = 0;
        public void setSelected(int colorId, boolean animated) {
            selectedColorId = colorId;
            if (buttons != null) {
                for (int i = 0; i < buttons.length; ++i) {
                    buttons[i].setSelected(buttons[i].id == colorId, animated);
                }
            }
        }
        public int getColorId() {
            return selectedColorId;
        }

        private Utilities.Callback<Integer> onColorClick;
        public void setOnColorClick(Utilities.Callback<Integer> onColorClick) {
            this.onColorClick = onColorClick;
        }

        private ColorButton pressedButton;
        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            ColorButton button = null;
            if (buttons != null) {
                for (int i = 0; i < buttons.length; ++i) {
                    if (buttons[i].clickBounds.contains(event.getX(), event.getY())) {
                        button = buttons[i];
                        break;
                    }
                }
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressedButton = button;
                if (button != null) {
                    button.setPressed(true);
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (pressedButton != button) {
                    if (pressedButton != null) {
                        pressedButton.setPressed(false);
                    }
                    if (button != null) {
                        button.setPressed(true);
                    }
                    if (pressedButton != null && button != null) {
                        if (onColorClick != null) {
                            onColorClick.run(button.id);
                        }
                    }
                    pressedButton = button;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (event.getAction() == MotionEvent.ACTION_UP && pressedButton != null) {
                    if (onColorClick != null) {
                        onColorClick.run(pressedButton.id);
                    }
                }
                if (buttons != null) {
                    for (int i = 0; i < buttons.length; ++i) {
                        buttons[i].setPressed(false);
                    }
                }
                pressedButton = null;
            }
            return true;
        }
    }

    public static class PeerColorSpan extends ReplacementSpan {
        private int size = dp(21);
        public PeerColorDrawable drawable;

        public PeerColorSpan(boolean profile, int currentAccount, int colorId) {
            drawable = profile ? PeerColorDrawable.fromProfile(currentAccount, colorId) : PeerColorDrawable.from(currentAccount, colorId);
        }

        public PeerColorSpan setSize(int sz) {
            if (drawable != null) {
                drawable.setRadius(sz / 2f);
                size = sz;
            }
            return this;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            return dp(3) + size + dp(3);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            if (drawable != null) {
                int cy = (top + bottom) / 2;
                drawable.setBounds((int) (x + dp(3)), cy - size, (int) (x + dp(5) + size), cy + size);
                drawable.draw(canvas);
            }
        }
    }

    public static class PeerColorDrawable extends Drawable {

        public static PeerColorDrawable from(int currentAccount, int colorId) {
            if (colorId < 7) {
                return new PeerColorDrawable(Theme.getColor(Theme.keys_avatar_nameInMessage[colorId]), Theme.getColor(Theme.keys_avatar_nameInMessage[colorId]), Theme.getColor(Theme.keys_avatar_nameInMessage[colorId]));
            }
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
            MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
            return from(peerColor, false);
        }

        public static PeerColorDrawable fromProfile(int currentAccount, int colorId) {
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).profilePeerColors;
            MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
            return from(peerColor, true);
        }

        public static PeerColorDrawable from(MessagesController.PeerColor peerColor, boolean fromProfile) {
            if (peerColor == null) {
                return new PeerColorDrawable(0, 0, 0);
            }
            return new PeerColorDrawable(peerColor.getColor1(), !fromProfile || peerColor.hasColor6(Theme.isCurrentThemeDark()) ? peerColor.getColor2() : peerColor.getColor1(), fromProfile ? peerColor.getColor1() : peerColor.getColor3());
        }

        private float radius = dpf2(21.333f / 2f);

        public PeerColorDrawable setRadius(float r) {
            this.radius = r;
            initPath();
            return this;
        }

        public PeerColorDrawable stroke(float width, int color) {
            if (strokePaint == null) {
                strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                strokePaint.setStyle(Paint.Style.STROKE);
            }
            strokePaint.setStrokeWidth(width);
            strokePaint.setColor(color);
            return this;
        }

        private final boolean hasColor3;
        private Paint strokePaint;
        private final Paint color1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint color2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint color3Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path color2Path = new Path();
        private final Path clipCirclePath = new Path();

        public PeerColorDrawable(int color1, int color2, int color3) {
            hasColor3 = color3 != color1;
            color1Paint.setColor(color1);
            color2Paint.setColor(color2);
            color3Paint.setColor(color3);

            initPath();
        }

        private void initPath() {
            clipCirclePath.rewind();
            clipCirclePath.addCircle(radius, radius, radius, Path.Direction.CW);
            color2Path.rewind();
            color2Path.moveTo(radius * 2, 0);
            color2Path.lineTo(radius * 2, radius * 2);
            color2Path.lineTo(0, radius * 2);
            color2Path.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.save();
            canvas.translate(getBounds().centerX() - radius, getBounds().centerY() - radius);
            if (strokePaint != null) {
                canvas.drawCircle(radius, radius, radius, strokePaint);
            }
            canvas.clipPath(clipCirclePath);
            canvas.drawPaint(color1Paint);
            canvas.drawPath(color2Path, color2Paint);
            if (hasColor3) {
                AndroidUtilities.rectTmp.set(radius - dp(3.66f), radius - dp(3.66f), radius + dp(3.66f), radius + dp(3.66f));
                canvas.rotate(45, radius, radius);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(2.33f), dp(2.33f), color3Paint);
            }
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicHeight() {
            return (int) (radius * 2);
        }

        @Override
        public int getIntrinsicWidth() {
            return (int) (radius * 2);
        }
    }

    public static class ColoredActionBar extends View {

        private int defaultColor;
        private final Theme.ResourcesProvider resourcesProvider;

        public ColoredActionBar(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            defaultColor = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
            setColor(-1, -1, false);
        }

        public void setColor(int currentAccount, int colorId, boolean animated) {
            isDefault = false;
            if (colorId < 0 || currentAccount < 0) {
                isDefault = true;
                color1 = color2 = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
            } else {
                MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).profilePeerColors;
                MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
                if (peerColor != null) {
                    final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
                    color1 = peerColor.getBgColor1(isDark);
                    color2 = peerColor.getBgColor2(isDark);
                } else {
                    isDefault = true;
                    color1 = color2 = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
                }
            }
            if (!animated) {
                color1Animated.set(color1, true);
                color2Animated.set(color2, true);
            }
            invalidate();
        }

        private float progressToGradient = 0;
        public void setProgressToGradient(float progress) {
            if (Math.abs(progressToGradient - progress) > 0.001f) {
                progressToGradient = progress;
                onUpdateColor();
                invalidate();
            }
        }

        public boolean isDefault;
        public int color1, color2;
        private final AnimatedColor color1Animated = new AnimatedColor(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedColor color2Animated = new AnimatedColor(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

        private int backgroundGradientColor1, backgroundGradientColor2, backgroundGradientHeight;
        private LinearGradient backgroundGradient;
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        protected void onUpdateColor() {

        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            final int color1 = color1Animated.set(this.color1);
            final int color2 = color2Animated.set(this.color2);
            if (backgroundGradient == null || backgroundGradientColor1 != color1 || backgroundGradientColor2 != color2 || backgroundGradientHeight != getHeight()) {
                backgroundGradient = new LinearGradient(0, 0, 0, backgroundGradientHeight = getHeight(), new int[] { backgroundGradientColor2 = color2, backgroundGradientColor1 = color1 }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
                backgroundPaint.setShader(backgroundGradient);
                onUpdateColor();
            }
            if (progressToGradient < 1) {
                canvas.drawColor(defaultColor);
            }
            if (progressToGradient > 0) {
                backgroundPaint.setAlpha((int) (0xFF * progressToGradient));
                canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
            }
        }

        protected boolean ignoreMeasure;

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, ignoreMeasure ? heightMeasureSpec : MeasureSpec.makeMeasureSpec(AndroidUtilities.statusBarHeight + dp(144), MeasureSpec.EXACTLY));
        }

        public void updateColors() {
            defaultColor = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
            onUpdateColor();
            invalidate();
        }

        public int getColor() {
            return ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider), ColorUtils.blendARGB(color1Animated.get(), color2Animated.get(), .75f), progressToGradient);
        }

        public int getActionBarButtonColor() {
            return ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider), isDefault ? Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider) : Color.WHITE, progressToGradient);
        }

        public int getTabsViewBackgroundColor() {
            return (
                ColorUtils.blendARGB(
                    AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider)) > .721f ?
                        Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider) :
                        Theme.adaptHSV(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider), +.08f, -.08f),
                    AndroidUtilities.computePerceivedBrightness(ColorUtils.blendARGB(color1Animated.get(), color2Animated.get(), .75f)) > .721f ?
                        Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider) :
                        Theme.adaptHSV(ColorUtils.blendARGB(color1Animated.get(), color2Animated.get(), .75f), +.08f, -.08f),
                    progressToGradient
                )
            );
        }
    }

    public static class ProfilePreview extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;
        private final int currentAccount;
        private final long dialogId;
        private final boolean isChannel;

        protected final ImageReceiver imageReceiver = new ImageReceiver(this);
        protected final AvatarDrawable avatarDrawable = new AvatarDrawable();
        protected final SimpleTextView titleView, subtitleView;

        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable statusEmoji;

        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, false, dp(20), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);

        private final StoriesUtilities.StoryGradientTools storyGradient = new StoriesUtilities.StoryGradientTools(this, false);

        public ProfilePreview(Context context, int currentAccount, long dialogId, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.currentAccount = currentAccount;
            this.dialogId = dialogId;
            this.resourcesProvider = resourcesProvider;
            this.isChannel = dialogId < 0;

            titleView = new SimpleTextView(context) {
                @Override
                protected void onAttachedToWindow() {
                    super.onAttachedToWindow();
                    statusEmoji.attach();
                }

                @Override
                protected void onDetachedFromWindow() {
                    super.onDetachedFromWindow();
                    statusEmoji.detach();
                }
            };
            statusEmoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(titleView, true, dp(24));
            titleView.setDrawablePadding(dp(8));
            titleView.setRightDrawable(statusEmoji);
            titleView.setTextColor(0xFFFFFFFF);
            titleView.setTextSize(20);
            titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            titleView.setScrollNonFitText(true);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 97, 0, 16, 50.33f));

            subtitleView = new SimpleTextView(context);
            subtitleView.setTextSize(14);
            subtitleView.setTextColor(0x80FFFFFF);
            subtitleView.setScrollNonFitText(true);
            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 97, 0, 16, 30.66f));

            imageReceiver.setRoundRadius(dp(54));
            CharSequence title;
            if (isChannel) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                title = chat == null ? "" : chat.title;

                avatarDrawable.setInfo(currentAccount, chat);
                imageReceiver.setForUserOrChat(chat, avatarDrawable);
            } else {
                TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
                title = UserObject.getUserName(user);

                avatarDrawable.setInfo(currentAccount, user);
                imageReceiver.setForUserOrChat(user, avatarDrawable);
            }
            try {
                title = Emoji.replaceEmoji(title, null, false);
            } catch (Exception ignore) {}

            titleView.setText(title);

            if (isChannel) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
                if (chatFull != null && chatFull.participants_count > 0) {
                    if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                        subtitleView.setText(LocaleController.formatPluralStringComma("Subscribers", chatFull.participants_count));
                    } else {
                        subtitleView.setText(LocaleController.formatPluralStringComma("Members", chatFull.participants_count));
                    }
                } else if (chat != null && chat.participants_count > 0) {
                    if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                        subtitleView.setText(LocaleController.formatPluralStringComma("Subscribers", chat.participants_count));
                    } else {
                        subtitleView.setText(LocaleController.formatPluralStringComma("Members", chat.participants_count));
                    }
                } else {
                    final boolean isPublic = ChatObject.isPublic(chat);
                    if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                        subtitleView.setText(LocaleController.getString(isPublic ? R.string.ChannelPublic : R.string.ChannelPrivate).toLowerCase());
                    } else {
                        subtitleView.setText(LocaleController.getString(isPublic ? R.string.MegaPublic : R.string.MegaPrivate).toLowerCase());
                    }
                }
            } else {
                subtitleView.setText(LocaleController.getString(R.string.Online));
            }

            setWillNotDraw(false);
        }

        public void overrideAvatarColor(int colorId) {
            final int color1, color2;
            if (colorId >= 14) {
                MessagesController messagesController = MessagesController.getInstance(UserConfig.selectedAccount);
                MessagesController.PeerColors peerColors = messagesController != null ? messagesController.peerColors : null;
                MessagesController.PeerColor peerColor = peerColors != null ? peerColors.getColor(colorId) : null;
                if (peerColor != null) {
                    final int peerColorValue = peerColor.getColor1();
                    color1 = getThemedColor(Theme.keys_avatar_background[AvatarDrawable.getPeerColorIndex(peerColorValue)]);
                    color2 = getThemedColor(Theme.keys_avatar_background2[AvatarDrawable.getPeerColorIndex(peerColorValue)]);
                } else {
                    color1 = getThemedColor(Theme.keys_avatar_background[AvatarDrawable.getColorIndex(colorId)]);
                    color2 = getThemedColor(Theme.keys_avatar_background2[AvatarDrawable.getColorIndex(colorId)]);
                }
            } else {
                color1 = getThemedColor(Theme.keys_avatar_background[AvatarDrawable.getColorIndex(colorId)]);
                color2 = getThemedColor(Theme.keys_avatar_background2[AvatarDrawable.getColorIndex(colorId)]);
            }
            avatarDrawable.setColor(color1, color2);
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            emoji.attach();
            imageReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            emoji.detach();
            imageReceiver.onDetachedFromWindow();
        }

        private int getThemedColor(int key) {
            return Theme.getColor(key, resourcesProvider);
        }

        private int lastColorId = -1;
        public void setColor(int colorId, boolean animated) {
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).profilePeerColors;
            MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(lastColorId = colorId);
            final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            if (peerColor != null) {
                emoji.setColor(adaptProfileEmojiColor(peerColor.getBgColor1(isDark)));
                statusEmoji.setColor(ColorUtils.blendARGB(peerColor.getColor(1, resourcesProvider), peerColor.hasColor6(isDark) ? peerColor.getColor(4, resourcesProvider) : peerColor.getColor(2, resourcesProvider), .5f));
                final int accentColor = ColorUtils.blendARGB(peerColor.getStoryColor1(isDark), peerColor.getStoryColor2(isDark), .5f);
                if (!Theme.hasHue(getThemedColor(Theme.key_actionBarDefault))) {
                    subtitleView.setTextColor(accentColor);
                } else {
                    subtitleView.setTextColor(Theme.changeColorAccent(getThemedColor(Theme.key_actionBarDefault), accentColor, getThemedColor(Theme.key_avatar_subtitleInProfileBlue), isDark, accentColor));
                }
                titleView.setTextColor(Color.WHITE);
            } else {
                final int emojiColor;
                if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) > .8f) {
                    emoji.setColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
                } else if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) < .2f) {
                    emoji.setColor(Theme.multAlpha(getThemedColor(Theme.key_actionBarDefaultTitle), .5f));
                } else {
                    emoji.setColor(adaptProfileEmojiColor(getThemedColor(Theme.key_actionBarDefault)));
                }
                statusEmoji.setColor(Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider));
                subtitleView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
                titleView.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
            }

            storyGradient.setColorId(colorId, animated);
            invalidate();
        }

        public void setEmoji(long docId, boolean animated) {
            if (docId == 0) {
                emoji.set((Drawable) null, animated);
            } else {
                emoji.set(docId, animated);
            }
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).profilePeerColors;
            MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(lastColorId);
            final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            if (peerColor != null) {
                emoji.setColor(adaptProfileEmojiColor(peerColor.getBgColor1(isDark)));
            } else if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) > .8f) {
                emoji.setColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
            } else if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) < .2f) {
                emoji.setColor(Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultTitle), .5f));
            } else {
                emoji.setColor(adaptProfileEmojiColor(Theme.getColor(Theme.key_actionBarDefault)));
            }
            if (peerColor != null) {
                statusEmoji.setColor(ColorUtils.blendARGB(peerColor.getColor(1, resourcesProvider), peerColor.hasColor6(isDark) ? peerColor.getColor(4, resourcesProvider) : peerColor.getColor(2, resourcesProvider), .5f));
            } else {
                statusEmoji.setColor(Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider));
            }
        }

        public void setStatusEmoji(long docId, boolean animated) {
            statusEmoji.set(docId, animated);
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).profilePeerColors;
            MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(lastColorId);
            final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            if (peerColor != null) {
                statusEmoji.setColor(ColorUtils.blendARGB(peerColor.getColor2(isDark), peerColor.hasColor6(isDark) ? peerColor.getColor5(isDark) : peerColor.getColor3(isDark), .5f));
            } else {
                statusEmoji.setColor(Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider));
            }
        }

        private final RectF rectF = new RectF();
        @Override
        protected void dispatchDraw(Canvas canvas) {
            rectF.set(dp(20.33f), getHeight() - dp(25.33f + 53.33f), dp(20.33f) + dp(53.33f), getHeight() - dp(25.33f));
            imageReceiver.setImageCoords(rectF);
            imageReceiver.draw(canvas);

            canvas.drawCircle(rectF.centerX(), rectF.centerY(), rectF.width() / 2f + dp(4), storyGradient.getPaint(rectF));

            drawProfileIconPattern(getWidth() - dp(46), getHeight(), 1f, (x, y, sz, alpha) -> {
                emoji.setAlpha((int) (0xFF * alpha));
                emoji.setBounds((int) (x - sz * .45f), (int) (y - sz * .45f), (int) (x + sz * .45f), (int) (y + sz * .45f));
                emoji.draw(canvas);
            });

            super.dispatchDraw(canvas);
        }
    }

    public static int adaptProfileEmojiColor(int color) {
        final boolean isDark = AndroidUtilities.computePerceivedBrightness(color) < .2f;
        return Theme.adaptHSV(color, +.5f, isDark ? +.28f : -.28f);
    }

    public static final float PARTICLE_SIZE_DP = 24;
    public static final int PARTICLES_COUNT = 15;
    public static final float GOLDEN_RATIO_ANGLE = 139f;
    public static final float FILL_SCALE = 1;

    public static void drawSunflowerPattern(float cx, float cy, Utilities.Callback3<Float, Float, Float> draw) {
        drawSunflowerPattern(PARTICLES_COUNT, cx, cy, 30, dp(PARTICLE_SIZE_DP) * .7f, 1.4f, GOLDEN_RATIO_ANGLE, draw);
    }

    public static void drawSunflowerPattern(int count, float cx, float cy, float anglestart, float scale, float scale2, float angle, Utilities.Callback3<Float, Float, Float> draw) {
        for (int i = 1; i <= count; ++i) {
            final float a = anglestart + i * angle;
            final float r = (float) (Math.sqrt(i * scale2) * scale);
            final float x = (float) (cx + Math.cos(a / 180f * Math.PI) * r) + (i == 3 ? .3f * scale : 0);
            final float y = (float) (cy + Math.sin(a / 180f * Math.PI) * r) + (i == 3 ? -.5f * scale : 0);
            draw.run(x, y, (float) Math.sqrt(1f - (float) i / count));
        }
    }

    private final static float[] particles = {
        -18, -24.66f, 24, .4f,
        5.33f, -53, 28, .38f,
        -4, -86, 19, .18f,
        31, -30, 21, .35f,
        12, -3, 24, .18f,
        30, -73, 19, .3f,
        43, -101, 16, .1f,
        -50, 1.33f, 20, .22f,
        -58, -33, 24, .22f,
        -35, -62, 25, .22f,
        -59, -88, 19, .18f,
        -86, -61, 19, .1f,
        -90, -14.33f, 19.66f, .18f
    };
    public static void drawProfileIconPattern(float cx, float cy, float scale, Utilities.Callback4<Float, Float, Float, Float> draw) {
        for (int i = 0; i < particles.length; i += 4) {
            draw.run(
                cx + dp(particles[i]) * scale,
                cy + dp(particles[i + 1]) * scale,
                dpf2(particles[i + 2]),
                particles[i + 3]
            );
        }
    }

    private View changeDayNightView;
    private float changeDayNightViewProgress;
    private ValueAnimator changeDayNightViewAnimator;

    @SuppressLint("NotifyDataSetChanged")
    public void toggleTheme() {
        FrameLayout decorView1 = (FrameLayout) getParentActivity().getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(decorView1.getWidth(), decorView1.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
        dayNightItem.setAlpha(0f);
        decorView1.draw(bitmapCanvas);
        dayNightItem.setAlpha(1f);

        Paint xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        xRefPaint.setColor(0xff000000);
        xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bitmapPaint.setFilterBitmap(true);
        int[] position = new int[2];
        dayNightItem.getLocationInWindow(position);
        float x = position[0];
        float y = position[1];
        float cx = x + dayNightItem.getMeasuredWidth() / 2f;
        float cy = y + dayNightItem.getMeasuredHeight() / 2f;

        float r = Math.max(bitmap.getHeight(), bitmap.getWidth()) + AndroidUtilities.navigationBarHeight;

        Shader bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        bitmapPaint.setShader(bitmapShader);
        changeDayNightView = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (isDark) {
                    if (changeDayNightViewProgress > 0f) {
                        bitmapCanvas.drawCircle(cx, cy, r * changeDayNightViewProgress, xRefPaint);
                    }
                    canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
                } else {
                    canvas.drawCircle(cx, cy, r * (1f - changeDayNightViewProgress), bitmapPaint);
                }
                canvas.save();
                canvas.translate(x, y);
                dayNightItem.draw(canvas);
                canvas.restore();
            }
        };
        changeDayNightView.setOnTouchListener((v, event) -> true);
        changeDayNightViewProgress = 0f;
        changeDayNightViewAnimator = ValueAnimator.ofFloat(0, 1f);
        changeDayNightViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean changedNavigationBarColor = false;

            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                changeDayNightViewProgress = (float) valueAnimator.getAnimatedValue();
                changeDayNightView.invalidate();
                if (!changedNavigationBarColor && changeDayNightViewProgress > .5f) {
                    changedNavigationBarColor = true;
                }
            }
        });
        changeDayNightViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (changeDayNightView != null) {
                    if (changeDayNightView.getParent() != null) {
                        ((ViewGroup) changeDayNightView.getParent()).removeView(changeDayNightView);
                    }
                    changeDayNightView = null;
                }
                changeDayNightViewAnimator = null;
                super.onAnimationEnd(animation);
            }
        });
        changeDayNightViewAnimator.setDuration(400);
        changeDayNightViewAnimator.setInterpolator(Easings.easeInOutQuad);
        changeDayNightViewAnimator.start();

        decorView1.addView(changeDayNightView, new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        AndroidUtilities.runOnUIThread(() -> {
            isDark = !isDark;
            updateThemeColors();
            setForceDark(isDark, true);
            updateColors();
        });
    }

    @Override
    public boolean isLightStatusBar() {
        if (colorBar == null) {
            return super.isLightStatusBar();
        }
        return ColorUtils.calculateLuminance(colorBar.getColor()) > 0.7f;
    }

    public void updateLightStatusBar() {
        if (getParentActivity() == null) return;
        AndroidUtilities.setLightStatusBar(getParentActivity().getWindow(), isLightStatusBar());
    }

    private boolean forceDark = isDark;
    public void setForceDark(boolean isDark, boolean playAnimation) {
        if (forceDark == isDark) {
            return;
        }
        forceDark = isDark;
        if (playAnimation) {
            sunDrawable.setCustomEndFrame(isDark ? sunDrawable.getFramesCount() : 0);
            if (sunDrawable != null) {
                sunDrawable.start();
            }
        } else {
            int frame = isDark ? sunDrawable.getFramesCount() - 1 : 0;
            sunDrawable.setCurrentFrame(frame, false, true);
            sunDrawable.setCustomEndFrame(frame);
            if (dayNightItem != null) {
                dayNightItem.invalidate();
            }
        }
    }
}