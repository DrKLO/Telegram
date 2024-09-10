package org.telegram.ui.Components.Premium;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.ChannelBoostsController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.AdminedChannelCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.ChannelColorActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChatEditActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Loadable;
import org.telegram.ui.Components.LoginOrView;
import org.telegram.ui.Components.Premium.boosts.BoostCounterView;
import org.telegram.ui.Components.Premium.boosts.BoostDialogs;
import org.telegram.ui.Components.Premium.boosts.BoostPagerBottomSheet;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;
import org.telegram.ui.Components.Premium.boosts.ReassignBoostBottomSheet;
import org.telegram.ui.Components.Reactions.ChatCustomReactionsEditActivity;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GroupColorActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.StatisticActivity;
import org.telegram.ui.Stories.ChannelBoostUtilities;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.StoryRecorder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class LimitReachedBottomSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {
    public static final int TYPE_PIN_DIALOGS = 0;
    public static final int TYPE_PUBLIC_LINKS = 2;
    public static final int TYPE_FOLDERS = 3;
    public static final int TYPE_CHATS_IN_FOLDER = 4;
    public static final int TYPE_TO0_MANY_COMMUNITIES = 5;
    public static final int TYPE_LARGE_FILE = 6;
    public static final int TYPE_ACCOUNTS = 7;

    public static final int TYPE_CAPTION = 8;
    public static final int TYPE_GIFS = 9;
    public static final int TYPE_STICKERS = 10;

    public static final int TYPE_ADD_MEMBERS_RESTRICTED = 11;
    public static final int TYPE_FOLDER_INVITES = 12;
    public static final int TYPE_SHARED_FOLDERS = 13;

    public static final int TYPE_STORIES_COUNT = 14;
    public static final int TYPE_STORIES_WEEK = 15;
    public static final int TYPE_STORIES_MONTH = 16;
    public static final int TYPE_BOOSTS = 17;
    public static final int TYPE_BOOSTS_FOR_POSTING = 18;
    public static final int TYPE_BOOSTS_FOR_USERS = 19;
    public static final int TYPE_BOOSTS_FOR_COLOR = 20;
    public static final int TYPE_BOOSTS_FOR_REACTIONS = 21;
    public static final int TYPE_BOOSTS_FOR_WALLPAPER = 22;
    public static final int TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER = 23;
    public static final int TYPE_BOOSTS_FOR_PROFILE_COLOR = 24;
    public static final int TYPE_BOOSTS_FOR_EMOJI_STATUS = 25;
    public static final int TYPE_BOOSTS_FOR_REPLY_ICON = 26;
    public static final int TYPE_BOOSTS_FOR_PROFILE_ICON = 27;
    public static final int TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK = 29;
    public static final int TYPE_BOOSTS_FOR_ADS = 30;
    public static final int TYPE_FEATURES = 31;
    public static final int TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS = 32;
    public static final int TYPE_PIN_SAVED_DIALOGS = 33;

    private boolean canSendLink;
    private int linkRow = -1;
    private long dialogId;
    private TL_stories.TL_premium_boostsStatus boostsStatus;
    private ChannelBoostsController.CanApplyBoost canApplyBoost;
    private HeaderView headerView;
    private boolean isCurrentChat;
    private boolean lockInvalidation = false;
    private ChatMessageCell chatMessageCell;

    public static String limitTypeToServerString(int type) {
        switch (type) {
            case TYPE_PIN_DIALOGS:
                return "double_limits__dialog_pinned";
            case TYPE_TO0_MANY_COMMUNITIES:
                return "double_limits__channels";
            case TYPE_PUBLIC_LINKS:
                return "double_limits__channels_public";
            case TYPE_FOLDERS:
                return "double_limits__dialog_filters";
            case TYPE_CHATS_IN_FOLDER:
                return "double_limits__dialog_filters_chats";
            case TYPE_LARGE_FILE:
                return "double_limits__upload_max_fileparts";
            case TYPE_CAPTION:
                return "double_limits__caption_length";
            case TYPE_GIFS:
                return "double_limits__saved_gifs";
            case TYPE_STICKERS:
                return "double_limits__stickers_faved";
            case TYPE_FOLDER_INVITES:
                return "double_limits__chatlist_invites";
            case TYPE_SHARED_FOLDERS:
                return "double_limits__chatlists_joined";
        }
        return null;
    }

    final int type;
    ArrayList<TLRPC.Chat> chats = new ArrayList<>();

    int rowCount;
    int headerRow = -1;
    int dividerRow = -1;
    int chatsTitleRow = -1;
    int chatStartRow = -1;
    int chatEndRow = -1;
    int loadingRow = -1;
    int emptyViewDividerRow = -1;
    int bottomRow = -1;
    int boostFeaturesStartRow = -1;
    ArrayList<BoostFeature> boostFeatures;

    private static class BoostFeature {

        public final int iconResId;
        public final int textKey;
        public final String countValue;
        public final String textKeyPlural;
        public final int countPlural;
        public boolean incremental;

        private BoostFeature(
            int iconResId,
            int textKey,
            String countValue,
            String textKeyPlural,
            int countPlural
        ) {
            this.iconResId = iconResId;
            this.textKey = textKey;
            this.countValue = countValue;
            this.textKeyPlural = textKeyPlural;
            this.countPlural = countPlural;
        }

        public static BoostFeature of(int iconResId, int textKey) {
            return new BoostFeature(iconResId, textKey, null, null, -1);
        }

        public static BoostFeature of(int iconResId, int textKey, String count) {
            return new BoostFeature(iconResId, textKey, count, null, -1);
        }

        public static BoostFeature of(int iconResId, String textKeyPlural, int count) {
            return new BoostFeature(iconResId, -1, null, textKeyPlural, count);
        }

        public boolean equals(BoostFeature that) {
            if (that == null) {
                return false;
            }
            if (incremental && countPlural > 2) {
                return true;
            }
            return (
                this.iconResId == that.iconResId &&
                this.textKey == that.textKey &&
                TextUtils.equals(this.countValue, that.countValue) &&
                TextUtils.equals(this.textKeyPlural, that.textKeyPlural) &&
                this.countPlural == that.countPlural
            );
        }

        public BoostFeature asIncremental() {
            incremental = true;
            return this;
        }

        public static boolean arraysEqual(ArrayList<BoostFeature> a, ArrayList<BoostFeature> b) {
            if (a == null && b == null) return true;
            if (a != null && b == null || a == null && b != null) return false;
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); ++i) {
                if (!a.get(i).equals(b.get(i))) {
                    return false;
                }
            }
            return true;
        }

        public static class BoostFeatureLevel extends BoostFeature {
            public final int lvl;
            public final boolean isFirst;
            public BoostFeatureLevel(int lvl) {
                this(lvl, false);
            }
            public BoostFeatureLevel(int lvl, boolean isFirst) {
                super(-1, -1, null, null, -1);
                this.lvl = lvl;
                this.isFirst = isFirst;
            }
        }
    }

    public boolean parentIsChannel;
    private int currentValue = -1;
    LimitPreviewView limitPreviewView;
    HashSet<Object> selectedChats = new HashSet<>();

    private ArrayList<TLRPC.Chat> inactiveChats = new ArrayList<>();
    private ArrayList<String> inactiveChatsSignatures = new ArrayList<>();
    private ArrayList<TLRPC.User> restrictedUsers = new ArrayList<>();
    private ArrayList<Long> premiumMessagingBlockedUsers = new ArrayList<>();
    private ArrayList<Long> premiumInviteBlockedUsers = new ArrayList<>();

    PremiumButtonView premiumButtonView;
    TextView actionBtn;
    ButtonWithCounterView boostMiniBtn;
    public Runnable onSuccessRunnable;
    public Runnable onShowPremiumScreenRunnable;
    private boolean loading = false;
    RecyclerItemsEnterAnimator enterAnimator;
    BaseFragment parentFragment;
    View divider;
    LimitParams limitParams;
    private boolean isVeryLargeFile;
    private TLRPC.Chat fromChat;
    FireworksOverlay fireworksOverlay;
    Runnable statisticClickRunnable;
    private int requiredLvl = 0;
    private ButtonWithCounterView boostToUnlockGroupBtn;

    public LimitReachedBottomSheet(BaseFragment fragment, Context context, int type, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context, fragment, false, hasFixedSize(type), false, resourcesProvider);
        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, this.resourcesProvider));
        this.parentFragment = fragment;
        this.currentAccount = currentAccount;
        this.type = type;
        updateTitle();
        updateRows();
        if (type == TYPE_PUBLIC_LINKS) {
            loadAdminedChannels();
        } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
            loadInactiveChannels();
        }
        updatePremiumButtonText();
        if (type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS || isBoostingForAdminPossible()) {
            fireworksOverlay = new FireworksOverlay(getContext());
            container.addView(fireworksOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        if (
            type == TYPE_BOOSTS_FOR_POSTING ||
            type == TYPE_BOOSTS_FOR_COLOR ||
            type == TYPE_BOOSTS_FOR_PROFILE_COLOR ||
            type == TYPE_BOOSTS_FOR_EMOJI_STATUS ||
            type == TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK ||
            type == TYPE_BOOSTS_FOR_WALLPAPER ||
            type == TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER ||
            type == TYPE_BOOSTS_FOR_REACTIONS ||
            type == TYPE_BOOSTS_FOR_REPLY_ICON ||
            type == TYPE_BOOSTS_FOR_PROFILE_ICON ||
            type == TYPE_BOOSTS_FOR_ADS ||
            type == TYPE_FEATURES
        ) {
            ((ViewGroup) premiumButtonView.getParent()).removeView(premiumButtonView);
            if (divider != null) {
                ((ViewGroup) divider.getParent()).removeView(divider);
            }
            recyclerListView.setPadding(0, 0, 0, 0);
            actionBtn = new TextView(context);
            actionBtn.setGravity(Gravity.CENTER);
            actionBtn.setEllipsize(TextUtils.TruncateAt.END);
            actionBtn.setSingleLine(true);
            actionBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            actionBtn.setTypeface(AndroidUtilities.bold());
            actionBtn.setText(premiumButtonView.getTextView().getText());
            actionBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
            actionBtn.setOnClickListener(v -> {
                AndroidUtilities.addToClipboard(getBoostLink());
                dismiss();
            });
            actionBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), 120)));
        }

        if (type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS) {
            ((ViewGroup) premiumButtonView.getParent()).removeView(premiumButtonView);
            boostToUnlockGroupBtn = new ButtonWithCounterView(context, resourcesProvider);
            boostToUnlockGroupBtn.withCounterIcon();
            boostToUnlockGroupBtn.setText(LocaleController.getString(R.string.BoostGroup), false);
            boostToUnlockGroupBtn.setOnClickListener(v -> {
                if (premiumButtonView.isShowOverlay()) {
                    premiumButtonView.overlayTextView.performClick();
                } else {
                    premiumButtonView.buttonLayout.performClick();
                }
            });
            containerView.addView(boostToUnlockGroupBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 2, 16, 12));
            containerView.post(() -> boostToUnlockGroupBtn.setCount(getNeededBoostsForUnlockGroup(), false));
        }

        if (type == TYPE_BOOSTS_FOR_USERS || type == TYPE_BOOSTS_FOR_POSTING) {
            containerView.post(() -> {
                if (ChatObject.hasAdminRights(getChat())) {
                    if (premiumButtonView.getParent() != null) {
                        ((ViewGroup) premiumButtonView.getParent()).removeView(premiumButtonView);
                    }
                    if (divider != null && divider.getParent() != null) {
                        ((ViewGroup) divider.getParent()).removeView(divider);
                    }
                    recyclerListView.setPadding(0, 0, 0, 0);
                }
            });
        }
    }

    @Override
    public void onViewCreated(FrameLayout containerView) {
        super.onViewCreated(containerView);
        Context context = containerView.getContext();

        boostMiniBtn = new ButtonWithCounterView(context, resourcesProvider);
        boostMiniBtn.setFlickeringLoading(true);
        boostMiniBtn.setText(LocaleController.getString(R.string.BoostBtn), false);
        boostMiniBtn.setOnClickListener(v -> {
            if (premiumButtonView.isShowOverlay()) {
                premiumButtonView.overlayTextView.performClick();
            } else {
                premiumButtonView.buttonLayout.performClick();
            }
        });
        premiumButtonView = new PremiumButtonView(context, true, resourcesProvider) {
            @Override
            public void invalidate() {
                if (lockInvalidation) {
                    return;
                }
                super.invalidate();
            }
        };
        ScaleStateListAnimator.apply(premiumButtonView, .02f, 1.2f);

        if (!hasFixedSize && !(
            type == TYPE_BOOSTS_FOR_POSTING ||
            type == TYPE_BOOSTS_FOR_COLOR ||
            type == TYPE_BOOSTS_FOR_PROFILE_COLOR ||
            type == TYPE_BOOSTS_FOR_EMOJI_STATUS ||
            type == TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK ||
            type == TYPE_BOOSTS_FOR_WALLPAPER ||
            type == TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER ||
            type == TYPE_BOOSTS_FOR_REACTIONS ||
            type == TYPE_BOOSTS_FOR_REPLY_ICON ||
            type == TYPE_BOOSTS_FOR_PROFILE_ICON ||
            type == TYPE_BOOSTS_FOR_ADS
        )) {
            divider = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    if (chatEndRow - chatStartRow > 1) {
                        Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
                        if (dividerPaint == null) {
                            dividerPaint = Theme.dividerPaint;
                        }
                        canvas.drawRect(0, 0, getMeasuredWidth(), 1, dividerPaint);
                    }
                }
            };
            divider.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
            containerView.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 72, Gravity.BOTTOM, 0, 0, 0, 0));
        }
        containerView.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16 + backgroundPaddingLeft / AndroidUtilities.density, 0, 16 + backgroundPaddingLeft / AndroidUtilities.density, 12));
        recyclerListView.setPadding(0, 0, 0, AndroidUtilities.dp(72));
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (view instanceof AdminedChannelCell) {
                AdminedChannelCell adminedChannelCell = ((AdminedChannelCell) view);
                TLRPC.Chat chat = adminedChannelCell.getCurrentChannel();
                if (selectedChats.contains(chat)) {
                    selectedChats.remove(chat);
                } else {
                    selectedChats.add(chat);
                }
                adminedChannelCell.setChecked(selectedChats.contains(chat), true);
                updateButton();
            } else if (view instanceof GroupCreateUserCell) {
                if (!canSendLink && type == TYPE_ADD_MEMBERS_RESTRICTED) {
                    return;
                }
                GroupCreateUserCell cell = (GroupCreateUserCell) view;
                Object object = cell.getObject();
                if (cell.isBlocked()) {
                    long dialogId;
                    if (object instanceof TLRPC.User) {
                        dialogId = ((TLRPC.User) object).id;
                    } else return;
                    showPremiumBlockedToast(cell, dialogId);
                    return;
                }
                if (selectedChats.contains(object)) {
                    selectedChats.remove(object);
                } else {
                    selectedChats.add(object);
                }
                cell.setChecked(selectedChats.contains(object), true);
                updateButton();
            }
        });
        recyclerListView.setOnItemLongClickListener((view, position) -> {
            recyclerListView.getOnItemClickListener().onItemClick(view, position);
            if (type != TYPE_BOOSTS_FOR_USERS) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            return false;
        });
        premiumButtonView.buttonLayout.setOnClickListener(v -> {
            if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                return;
            }
            if (type == TYPE_BOOSTS_FOR_USERS || type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS || isMiniBoostBtnForAdminAvailable()) {
                if (canApplyBoost.empty) {
                    if (UserConfig.getInstance(currentAccount).isPremium() && BoostRepository.isMultiBoostsAvailable()) {
                        BoostDialogs.showMoreBoostsNeeded(dialogId, this);
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
                        builder.setTitle(LocaleController.getString(R.string.PremiumNeeded));
                        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(isGroup() ? R.string.PremiumNeededForBoostingGroup : R.string.PremiumNeededForBoosting)));
                        builder.setPositiveButton(LocaleController.getString(R.string.CheckPhoneNumberYes), (dialog, which) -> {
                            parentFragment.presentFragment(new PremiumPreviewFragment(null));
                            LimitReachedBottomSheet.this.dismiss();
                            dialog.dismiss();
                        });
                        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
                        builder.show();
                    }
                } else if (canApplyBoost.canApply && canApplyBoost.replaceDialogId == 0) {
                    if (canApplyBoost.needSelector && BoostRepository.isMultiBoostsAvailable()) {
                        lockInvalidation = true;
                        limitPreviewView.invalidationEnabled = false;
                        ReassignBoostBottomSheet.show(getBaseFragment(), canApplyBoost.myBoosts, canApplyBoost.currentChat).setOnHideListener(dialog -> {
                            lockInvalidation = false;
                            limitPreviewView.invalidationEnabled = true;
                            premiumButtonView.invalidate();
                            limitPreviewView.invalidate();
                        });
                    } else {
                        boostChannel();
                    }
                } else if (canApplyBoost.canApply) {
                    FrameLayout frameLayout = new FrameLayout(getContext());
                    BackupImageView fromAvatar = new BackupImageView(getContext());
                    fromAvatar.setRoundRadius(AndroidUtilities.dp(30));
                    frameLayout.addView(fromAvatar, LayoutHelper.createFrame(60, 60));
                    frameLayout.setClipChildren(false);

                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint.setColor(Theme.getColor(Theme.key_dialogBackground));
                    Drawable boostDrawable = ContextCompat.getDrawable(getContext(), R.drawable.filled_limit_boost);
                    View boostIcon = new View(getContext()) {
                        @Override
                        protected void onDraw(Canvas canvas) {
                            float cx = getMeasuredWidth() / 2f;
                            float cy = getMeasuredHeight() / 2f;
                            canvas.drawCircle(cx, cy, getMeasuredWidth() / 2f, paint);
                            PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, getMeasuredWidth(), getMeasuredHeight(), -AndroidUtilities.dp(10), 0);
                            canvas.drawCircle(cx, cy, getMeasuredWidth() / 2f - AndroidUtilities.dp(2), PremiumGradient.getInstance().getMainGradientPaint());
                            float iconSizeHalf = AndroidUtilities.dp(18) / 2f;
                            boostDrawable.setBounds(
                                    (int) (cx - iconSizeHalf),
                                    (int) (cy - iconSizeHalf),
                                    (int) (cx + iconSizeHalf),
                                    (int) (cy + iconSizeHalf)
                            );
                            boostDrawable.draw(canvas);
                        }
                    };
                    frameLayout.addView(boostIcon, LayoutHelper.createFrame(28, 28, 0, 34, 34, 0, 0));

                    ImageView imageView = new ImageView(getContext());
                    imageView.setImageResource(R.drawable.msg_arrow_avatar);
                    imageView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
                    frameLayout.addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER));

                    BackupImageView toAvatar = new BackupImageView(getContext());
                    toAvatar.setRoundRadius(AndroidUtilities.dp(30));
                    frameLayout.addView(toAvatar, LayoutHelper.createFrame(60, 60, 0, 60 + 36, 0, 0, 0));
                    FrameLayout containerLayout = new FrameLayout(getContext());
                    containerLayout.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 60, Gravity.CENTER_HORIZONTAL));
                    containerLayout.setClipChildren(false);
                    TextView textView = new TextView(context);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        textView.setLetterSpacing(0.025f);
                    }
                    textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    containerLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 80, 24, 0));

                    AvatarDrawable fromAvatarDrawable = new AvatarDrawable();
                    TLRPC.Chat fromChat = MessagesController.getInstance(currentAccount).getChat(-canApplyBoost.replaceDialogId);
                    fromAvatarDrawable.setInfo(currentAccount, fromChat);
                    fromAvatar.setForUserOrChat(fromChat, fromAvatarDrawable);

                    AvatarDrawable toAvatarDrawable = new AvatarDrawable();
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    toAvatarDrawable.setInfo(currentAccount, chat);
                    toAvatar.setForUserOrChat(chat, toAvatarDrawable);

                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setView(containerLayout);
                    textView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ReplaceBoostChannelDescription", R.string.ReplaceBoostChannelDescription, fromChat.title, chat.title)));
                    builder.setPositiveButton(LocaleController.getString(R.string.Replace), (dialog, which) -> {
                        dialog.dismiss();
                        boostChannel();
                    });
                    builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
                    builder.show();
                } else if (canApplyBoost.floodWait != 0) {
                    BoostDialogs.showFloodWait(canApplyBoost.floodWait);
                }
                return;
            }
            if (
                type == TYPE_BOOSTS_FOR_POSTING ||
                type == TYPE_BOOSTS_FOR_COLOR ||
                type == TYPE_BOOSTS_FOR_PROFILE_COLOR ||
                type == TYPE_BOOSTS_FOR_EMOJI_STATUS ||
                type == TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK ||
                type == TYPE_BOOSTS_FOR_WALLPAPER ||
                type == TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER ||
                type == TYPE_BOOSTS_FOR_REACTIONS ||
                type == TYPE_BOOSTS_FOR_REPLY_ICON ||
                type == TYPE_BOOSTS_FOR_PROFILE_ICON ||
                type == TYPE_BOOSTS_FOR_ADS
            ) {
                AndroidUtilities.addToClipboard(getBoostLink());
                dismiss();
                return;
            }
            if (UserConfig.getInstance(currentAccount).isPremium() || MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() || isVeryLargeFile) {
                dismiss();
                return;
            }
            if (parentFragment == null) {
                return;
            }
            if (parentFragment.getVisibleDialog() != null) {
                parentFragment.getVisibleDialog().dismiss();
            }
            parentFragment.presentFragment(new PremiumPreviewFragment(limitTypeToServerString(type)));
            if (onShowPremiumScreenRunnable != null) {
                onShowPremiumScreenRunnable.run();
            }
            dismiss();
        });
        premiumButtonView.overlayTextView.setOnClickListener(v -> {
            if (type == TYPE_BOOSTS_FOR_USERS || type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS || isMiniBoostBtnForAdminAvailable()) {
                if (canApplyBoost.canApply) {
                    premiumButtonView.buttonLayout.callOnClick();
                    if (canApplyBoost.alreadyActive && canApplyBoost.boostedNow) {
                        AndroidUtilities.runOnUIThread(() -> {
                            limitPreviewView.setBoosts(boostsStatus, false);
                            limitPreviewIncreaseCurrentValue();
                        }, canApplyBoost.needSelector ? 300 : 0);
                    }
                } else {
                    if (canApplyBoost.alreadyActive && BoostRepository.isMultiBoostsAvailable() && !canApplyBoost.isMaxLvl) {
                        BoostDialogs.showMoreBoostsNeeded(dialogId, this);
                    } else {
                        dismiss();
                    }
                }
                return;
            }
            if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                if (selectedChats.isEmpty()) {
                    dismiss();
                    return;
                }
                sendInviteMessages();
                return;
            }
            if (selectedChats.isEmpty()) {
                return;
            }
            if (type == TYPE_PUBLIC_LINKS) {
                revokeSelectedLinks();
            } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
                leaveFromSelectedGroups();
            }
        });
        enterAnimator = new RecyclerItemsEnterAnimator(recyclerListView, true);
    }

    private void limitPreviewIncreaseCurrentValue() {
        limitPreviewView.increaseCurrentValue(boostsStatus.boosts,
                (boostsStatus.boosts - boostsStatus.current_level_boosts),
                (boostsStatus.next_level_boosts - boostsStatus.current_level_boosts));
    }

    private int shiftDp = -4;
    private void showPremiumBlockedToast(View view, long dialogId) {
        AndroidUtilities.shakeViewSpring(view, shiftDp = -shiftDp);
        BotWebViewVibrationEffect.APP_ERROR.vibrate();
        String username = "";
        if (dialogId >= 0) {
            username = UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(dialogId));
        }
        Bulletin bulletin;
        if (MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
            bulletin = BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createSimpleBulletin(R.raw.star_premium_2, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserBlockedNonPremium, username)));
        } else {
            bulletin = BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                .createSimpleBulletin(R.raw.star_premium_2, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserBlockedNonPremium, username)), LocaleController.getString(R.string.UserBlockedNonPremiumButton), () -> {
                    BaseFragment lastFragment = LaunchActivity.getLastFragment();
                    if (lastFragment == null) {
                        return;
                    }
                    BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                    params.transitionFromLeft = true;
                    params.allowNestedScroll = false;
                    parentFragment.showAsSheet(new PremiumPreviewFragment("noncontacts"), params);
                });
        }
        bulletin.show();
    }

    private void boostChannel(Loadable button) {
        boostChannel(button, false);
    }

    private void boostChannel(Loadable button, boolean force) {
        if (button.isLoading() && !force) {
            return;
        }
        button.setLoading(true);
        MessagesController.getInstance(currentAccount).getBoostsController().applyBoost(dialogId, canApplyBoost.slot, arg -> {
            MessagesController.getInstance(currentAccount).getBoostsController().getBoostsStats(dialogId, tlPremiumBoostsStatus -> {
                button.setLoading(false);
                if (tlPremiumBoostsStatus == null) {
                    return;
                }
                boostsStatus.boosts += 1;
                if (type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS) {
                    TLRPC.ChatFull chatFull = getChatFull();
                    if (chatFull != null) {
                        chatFull.boosts_applied++;
                    }
                }
                limitPreviewIncreaseCurrentValue();
                setBoostsStats(tlPremiumBoostsStatus, isCurrentChat);
                canApplyBoost.isMaxLvl = boostsStatus.next_level_boosts <= 0;
                canApplyBoost.boostedNow = true;
                canApplyBoost.setMyBoosts(arg);
                onBoostSuccess();
            });
        }, error -> {
            if (error.text.startsWith("FLOOD_WAIT")) {
                int seconds = Utilities.parseInt(error.text);
                if (seconds <= 5) {
                    AndroidUtilities.runOnUIThread(() -> boostChannel(button, true), seconds * 1000L);
                    return;
                } else {
                    BoostDialogs.showFloodWait(seconds);
                }
            }
            button.setLoading(false);
        });
    }

    private void boostChannel() {
        if (boostMiniBtn.isAttachedToWindow()) {
            boostChannel(boostMiniBtn);
        } else if (boostToUnlockGroupBtn != null && boostToUnlockGroupBtn.isAttachedToWindow()) {
            boostChannel(boostToUnlockGroupBtn);
        } else {
            boostChannel(premiumButtonView);
        }
    }

    private boolean onBoostSuccess() {
        NotificationCenter.getInstance(currentAccount).postNotificationNameOnUIThread(NotificationCenter.chatWasBoostedByUser, boostsStatus, canApplyBoost.copy(), dialogId);
        if (boostToUnlockGroupBtn != null) {
            int count = getNeededBoostsForUnlockGroup();
            if (count == 0) {
                NotificationCenter.getInstance(currentAccount).postNotificationNameOnUIThread(NotificationCenter.groupRestrictionsUnlockedByBoosts);
                dismiss();
                return false;
            }
            boostToUnlockGroupBtn.setCount(count, true);
        }

        TransitionSet transitionSet = new TransitionSet();
        transitionSet.addTransition(new Visibility() {
            @Override
            public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                AnimatorSet set = new AnimatorSet();
                set.playTogether(
                        ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1f),
                        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, AndroidUtilities.dp(20), 0)
                );
                set.setInterpolator(CubicBezierInterpolator.DEFAULT);
                return set;
            }

            @Override
            public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                AnimatorSet set = new AnimatorSet();
                set.playTogether(
                        ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 0f),
                        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0, -AndroidUtilities.dp(20))
                );
                set.setInterpolator(CubicBezierInterpolator.DEFAULT);
                return set;
            }
        });
        transitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
        TransitionManager.beginDelayedTransition(headerView, transitionSet);

        headerView.recreateTitleAndDescription();
        headerView.title.setText(getBoostsTitleString());
        headerView.description.setText(AndroidUtilities.replaceTags(getBoostDescriptionStringAfterBoost()));
        updateButton();
        fireworksOverlay.start();
        fireworksOverlay.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        headerView.boostCounterView.setCount(canApplyBoost.boostCount, true);
        recyclerListView.smoothScrollToPosition(0);
        if (type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS) {
            headerView.boostCounterView.setVisibility(View.GONE);
        }
        return true;
    }

    private void sendInviteMessages() {
        String link = null;
        TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(fromChat.id);
        if (chatFull == null) {
            dismiss();
            return;
        }
        if (fromChat.username != null) {
            link = "@" + fromChat.username;
        } else if (chatFull.exported_invite != null) {
            link = chatFull.exported_invite.link;
        } else {
            dismiss();
            return;
        }
        for (Object obj : selectedChats) {
            TLRPC.User user = (TLRPC.User) obj;
            SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(link, user.id, null, null, null, true, null, null, null, false, 0, null, false));
        }
        AndroidUtilities.runOnUIThread(() -> {
            BulletinFactory factory = BulletinFactory.global();
            if (factory != null) {
                if (selectedChats.size() == 1) {
                    TLRPC.User user = (TLRPC.User) selectedChats.iterator().next();
                    factory.createSimpleBulletin(R.raw.voip_invite,
                            AndroidUtilities.replaceTags(LocaleController.formatString("InviteLinkSentSingle", R.string.InviteLinkSentSingle, ContactsController.formatName(user)))
                    ).show();
                } else {
                    factory.createSimpleBulletin(R.raw.voip_invite,
                            AndroidUtilities.replaceTags(LocaleController.formatPluralString("InviteLinkSent", selectedChats.size(), selectedChats.size()))
                    ).show();
                }
            }
        });
        dismiss();
    }

    public void setRequiredLvl(int requiredLvl) {
        this.requiredLvl = requiredLvl;
    }

    public void updatePremiumButtonText() {
        if (type == TYPE_BOOSTS_FOR_USERS || type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS || isMiniBoostBtnForAdminAvailable()) {
            if (BoostRepository.isMultiBoostsAvailable()) {
                premiumButtonView.buttonTextView.setText(canApplyBoost != null && canApplyBoost.alreadyActive ?
                        LocaleController.getString(R.string.BoostingBoostAgain)
                        : LocaleController.getString(isGroup() ? R.string.BoostGroup : R.string.BoostChannel));
                if (canApplyBoost != null && canApplyBoost.isMaxLvl) {
                    premiumButtonView.buttonTextView.setText(LocaleController.getString(R.string.OK));
                }
            } else {
                premiumButtonView.buttonTextView.setText(LocaleController.getString(isGroup() ? R.string.BoostGroup : R.string.BoostChannel));
            }
        } else if (
            type == TYPE_BOOSTS_FOR_POSTING ||
            type == TYPE_BOOSTS_FOR_COLOR ||
            type == TYPE_BOOSTS_FOR_PROFILE_COLOR ||
            type == TYPE_BOOSTS_FOR_EMOJI_STATUS ||
            type == TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK ||
            type == TYPE_BOOSTS_FOR_WALLPAPER ||
            type == TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER ||
            type == TYPE_BOOSTS_FOR_REACTIONS ||
            type == TYPE_BOOSTS_FOR_REPLY_ICON ||
            type == TYPE_BOOSTS_FOR_PROFILE_ICON ||
            type == TYPE_BOOSTS_FOR_ADS
        ) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder("d ");
            spannableStringBuilder.setSpan(new ColoredImageSpan(R.drawable.msg_copy_filled), 0, 1, 0);
            spannableStringBuilder.append(LocaleController.getString(R.string.CopyLink));
            premiumButtonView.buttonTextView.setText(spannableStringBuilder);
        } else if (UserConfig.getInstance(currentAccount).isPremium() || MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() || isVeryLargeFile) {
            premiumButtonView.buttonTextView.setText(LocaleController.getString(R.string.OK));
            premiumButtonView.hideIcon();
        } else {
            premiumButtonView.buttonTextView.setText(LocaleController.getString(R.string.IncreaseLimit));
            if (limitParams != null) {
                if (limitParams.defaultLimit + 1 == limitParams.premiumLimit) {
                    premiumButtonView.setIcon(R.raw.addone_icon);
                } else if (
                        limitParams.defaultLimit != 0 && limitParams.premiumLimit != 0 &&
                                limitParams.premiumLimit / (float) limitParams.defaultLimit >= 1.6f &&
                                limitParams.premiumLimit / (float) limitParams.defaultLimit <= 2.5f
                ) {
                    premiumButtonView.setIcon(R.raw.double_icon);
                } else {
                    premiumButtonView.hideIcon();
                }
            } else {
                premiumButtonView.hideIcon();
            }
        }
    }

    private void leaveFromSelectedGroups() {
        TLRPC.User currentUser = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        for (Object obj : selectedChats) {
            chats.add((TLRPC.Chat) obj);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        builder.setTitle(LocaleController.formatPluralString("LeaveCommunities", chats.size()));
        if (chats.size() == 1) {
            TLRPC.Chat channel = chats.get(0);
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ChannelLeaveAlertWithName", R.string.ChannelLeaveAlertWithName, channel.title)));
        } else {
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ChatsLeaveAlert", R.string.ChatsLeaveAlert)));
        }
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.VoipGroupLeave), (dialogInterface, interface2) -> {
            dismiss();
            for (int i = 0; i < chats.size(); i++) {
                TLRPC.Chat chat = chats.get(i);
                MessagesController.getInstance(currentAccount).putChat(chat, false);
                MessagesController.getInstance(currentAccount).deleteParticipantFromChat(chat.id, currentUser);
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        }
    }

    private void updateButton() {
        if (type == TYPE_BOOSTS_FOR_USERS || type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS || isMiniBoostBtnForAdminAvailable()) {
            if (!canApplyBoost.canApply && !canApplyBoost.empty || canApplyBoost.boostedNow || canApplyBoost.alreadyActive) {
                if (canApplyBoost.canApply) {
                    if (BoostRepository.isMultiBoostsAvailable()) {
                        premiumButtonView.setOverlayText(LocaleController.getString(R.string.BoostingBoostAgain), true, true);
                    } else {
                        premiumButtonView.setOverlayText(LocaleController.getString(isGroup() ? R.string.BoostGroup : R.string.BoostChannel), true, true);
                    }
                    boostMiniBtn.setText(LocaleController.getString(R.string.BoostBtn), true);
                    if (boostToUnlockGroupBtn != null) {
                        boostToUnlockGroupBtn.setText(LocaleController.getString(R.string.BoostGroup), true);
                    }
                } else {
                    if (canApplyBoost.isMaxLvl) {
                        boostMiniBtn.setText(LocaleController.getString(R.string.OK), true);
                        if (boostToUnlockGroupBtn != null) {
                            boostToUnlockGroupBtn.setText(LocaleController.getString(R.string.OK), true);
                        }
                        premiumButtonView.setOverlayText(LocaleController.getString(R.string.OK), true, true);
                    } else {
                        if (BoostRepository.isMultiBoostsAvailable()) {
                            if (boostToUnlockGroupBtn != null) {
                                boostToUnlockGroupBtn.setText(LocaleController.getString(R.string.BoostGroup), true);
                            }
                            boostMiniBtn.setText(LocaleController.getString(R.string.BoostBtn), true);
                            premiumButtonView.setOverlayText(LocaleController.getString(R.string.BoostingBoostAgain), true, true);
                        } else {
                            if (boostToUnlockGroupBtn != null) {
                                boostToUnlockGroupBtn.setText(LocaleController.getString(R.string.OK), true);
                            }
                            boostMiniBtn.setText(LocaleController.getString(R.string.OK), true);
                            premiumButtonView.setOverlayText(LocaleController.getString(R.string.OK), true, true);
                        }
                    }
                }
            } else {
                if (canApplyBoost.isMaxLvl) {
                    premiumButtonView.setOverlayText(LocaleController.getString(R.string.OK), true, true);
                } else {
                    premiumButtonView.clearOverlayText();
                }
            }
        } else if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
            premiumButtonView.checkCounterView();
            if (!canSendLink) {
                premiumButtonView.setOverlayText(LocaleController.getString(R.string.Close), true, true);
            } else if (selectedChats.size() > 0) {
                premiumButtonView.setOverlayText(LocaleController.getString(R.string.SendInviteLink), true, true);
            } else {
                premiumButtonView.setOverlayText(LocaleController.getString(R.string.ActionSkip), true, true);
            }
            premiumButtonView.counterView.setCount(selectedChats.size(), true);
            premiumButtonView.invalidate();
        } else {
            if (selectedChats.size() > 0) {
                String str = null;
                if (type == TYPE_PUBLIC_LINKS) {
                    str = LocaleController.formatPluralString("RevokeLinks", selectedChats.size());
                } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
                    str = LocaleController.formatPluralString("LeaveCommunities", selectedChats.size());
                }
                premiumButtonView.setOverlayText(str, true, true);
            } else {
                premiumButtonView.clearOverlayText();
            }
        }
    }

    private static boolean hasFixedSize(int type) {
        return (
            type == TYPE_PIN_DIALOGS ||
            type == TYPE_PIN_SAVED_DIALOGS ||
            type == TYPE_FOLDERS ||
            type == TYPE_CHATS_IN_FOLDER ||
            type == TYPE_LARGE_FILE ||
            type == TYPE_ACCOUNTS ||
            type == TYPE_FOLDER_INVITES ||
            type == TYPE_SHARED_FOLDERS ||
            type == TYPE_STORIES_COUNT ||
            type == TYPE_STORIES_WEEK ||
            type == TYPE_STORIES_MONTH
        );
    }

    @Override
    public CharSequence getTitle() {
        switch (type) {
            case TYPE_FEATURES:
                return LocaleController.getString(R.string.BoostingAdditionalFeaturesTitle);
            case TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS:
                return LocaleController.getString(R.string.BoostGroup);
            case TYPE_BOOSTS_FOR_USERS:
            case TYPE_BOOSTS_FOR_ADS:
                return LocaleController.getString(isGroup() ? R.string.BoostGroup : R.string.BoostChannel);
            case TYPE_BOOSTS_FOR_POSTING:
            case TYPE_BOOSTS_FOR_COLOR:
            case TYPE_BOOSTS_FOR_WALLPAPER:
            case TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER:
            case TYPE_BOOSTS_FOR_REACTIONS:
            case TYPE_BOOSTS_FOR_EMOJI_STATUS:
            case TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK:
            case TYPE_BOOSTS_FOR_REPLY_ICON:
            case TYPE_BOOSTS_FOR_PROFILE_ICON:
            case TYPE_BOOSTS_FOR_PROFILE_COLOR:
                return LocaleController.getString(R.string.UnlockBoostChannelFeatures);
            case TYPE_ADD_MEMBERS_RESTRICTED:
                return LocaleController.getString(R.string.ChannelInviteViaLink2);
            default:
                return LocaleController.getString(R.string.LimitReached);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.boostByChannelCreated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.boostedChannelByUser);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didStartedMultiGiftsSelector);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.boostByChannelCreated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.boostedChannelByUser);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didStartedMultiGiftsSelector);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.boostByChannelCreated) {
            TLRPC.Chat chat = (TLRPC.Chat) args[0];
            boolean isGiveaway = (boolean) args[1];
            BaseFragment lastFragment = getBaseFragment().getParentLayout().getLastFragment();
            if (lastFragment instanceof ChatCustomReactionsEditActivity) {
                List<BaseFragment> fragmentStack = getBaseFragment().getParentLayout().getFragmentStack();
                BaseFragment chatEditFragment = fragmentStack.size() >= 2 ? fragmentStack.get(fragmentStack.size() - 2) : null;
                BaseFragment profileFragment = fragmentStack.size() >= 3 ? fragmentStack.get(fragmentStack.size() - 3) : null;
                BaseFragment chatFragment = fragmentStack.size() >= 4 ? fragmentStack.get(fragmentStack.size() - 4) : null;
                if (chatEditFragment instanceof ChatEditActivity) {
                    getBaseFragment().getParentLayout().removeFragmentFromStack(chatEditFragment);
                }
                dismiss();
                if (isGiveaway) {
                    if (profileFragment instanceof ProfileActivity) {
                        getBaseFragment().getParentLayout().removeFragmentFromStack(profileFragment);
                    }
                    lastFragment.finishFragment();
                    BoostDialogs.showBulletin(chatFragment, chat, true);
                } else {
                    lastFragment.finishFragment();
                    BoostDialogs.showBulletin(profileFragment, chat, false);
                }
                return;
            }
            if (lastFragment instanceof ChatActivity) {
                if (isGiveaway) {
                    BoostDialogs.showBulletin(lastFragment, chat, true);
                    return;
                }
            }
            if (lastFragment instanceof ChannelColorActivity) {
                if (isGiveaway) {
                    List<BaseFragment> fragmentStack = getBaseFragment().getParentLayout().getFragmentStack();
                    List<BaseFragment> removedFragments = new ArrayList<>();
                    BaseFragment targetFragment = null;
                    for (int i = fragmentStack.size() - 2; i >= 0; i--) {
                        BaseFragment fragment = fragmentStack.get(i);
                        if (fragment instanceof ChatActivity || fragment instanceof DialogsActivity) {
                            targetFragment = fragment;
                            break;
                        }
                        removedFragments.add(fragment);
                    }
                    if (targetFragment == null) {
                        return;
                    }
                    for (BaseFragment removedFragment : removedFragments) {
                        getBaseFragment().getParentLayout().removeFragmentFromStack(removedFragment);
                    }
                    getBaseFragment().finishFragment();
                    dismiss();
                    BoostDialogs.showBulletin(targetFragment, chat, true);
                    return;
                }
            }
            if (isGiveaway) {
                if (StoryRecorder.isVisible()) {
                    ChatActivity chatFragment = ChatActivity.of(-chat.id);
                    LaunchActivity.getLastFragment().presentFragment(chatFragment, false, false);
                    StoryRecorder.destroyInstance();
                    dismiss();
                    BoostDialogs.showBulletin(chatFragment, chat, true);
                } else {
                    List<BaseFragment> fragmentStack = getBaseFragment().getParentLayout().getFragmentStack();
                    BaseFragment chatFragment = fragmentStack.size() >= 2 ? fragmentStack.get(fragmentStack.size() - 2) : null;
                    getBaseFragment().finishFragment();
                    dismiss();
                    if (chatFragment instanceof ChatActivity) {
                        BoostDialogs.showBulletin(chatFragment, chat, true);
                    }
                }
            } else {
                if (StoryRecorder.isVisible()) {
                    ChatActivity chatFragment = ChatActivity.of(-chat.id);
                    LaunchActivity.getLastFragment().presentFragment(chatFragment, false, false);
                    StoryRecorder.destroyInstance();
                    dismiss();
                    BoostDialogs.showBulletin(chatFragment, chat, false);
                } else {
                    dismiss();
                    BoostDialogs.showBulletin(LaunchActivity.getLastFragment(), chat, false);
                }
            }
        } else if (id == NotificationCenter.boostedChannelByUser) {
            TL_stories.TL_premium_myBoosts myBoosts = (TL_stories.TL_premium_myBoosts) args[0];
            int size = (int) args[1];
            int channels = (int) args[2];
            TL_stories.TL_premium_boostsStatus tlPremiumBoostsStatus = (TL_stories.TL_premium_boostsStatus) args[3];

            if (tlPremiumBoostsStatus == null || canApplyBoost == null) {
                return;
            }

            boostsStatus.boosts += size;
            if (type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS) {
                TLRPC.ChatFull chatFull = getChatFull();
                if (chatFull != null) {
                    chatFull.boosts_applied += size;
                }
            }

            limitPreviewIncreaseCurrentValue();
            setBoostsStats(tlPremiumBoostsStatus, isCurrentChat);
            canApplyBoost.isMaxLvl = boostsStatus.next_level_boosts <= 0;
            canApplyBoost.boostedNow = true;
            canApplyBoost.setMyBoosts(myBoosts);
            if (onBoostSuccess()) {
                String str = LocaleController.formatPluralString("BoostingReassignedFromPlural", size,
                        LocaleController.formatPluralString("BoostingFromOtherChannel", channels));
                BulletinFactory bulletinFactory = BulletinFactory.of(container, resourcesProvider);
                bulletinFactory.createSimpleBulletinWithIconSize(R.raw.ic_boosts_replace, str, 30).setDuration(4000).show(true);
            }
        } else if (id == NotificationCenter.didStartedMultiGiftsSelector) {
            dismiss();
        }
    }

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_CHANNEL = 1;
    private static final int VIEW_TYPE_SHADOW = 2;
    private static final int VIEW_TYPE_HEADER_CELL = 3;
    private static final int VIEW_TYPE_USER = 4;
    private static final int VIEW_TYPE_PROGRESS = 5;
    private static final int VIEW_TYPE_SPACE16DP = 6;
    private static final int VIEW_TYPE_BOOST_LINK = 7;
    private static final int VIEW_TYPE_BOOST_FOOTER = 8;
    private static final int VIEW_TYPE_BOOST_FEATURE = 9;

    @Override
    public RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                if (type == TYPE_ADD_MEMBERS_RESTRICTED && !canSendLink) {
                    return false;
                }
                return holder.getItemViewType() == VIEW_TYPE_CHANNEL || holder.getItemViewType() == VIEW_TYPE_USER;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                Context context = parent.getContext();
                switch (viewType) {
                    case VIEW_TYPE_BOOST_FOOTER:
                        LinearLayout wrapperLayout = new LinearLayout(context);
                        wrapperLayout.setPadding(backgroundPaddingLeft + dp(6), 0, backgroundPaddingLeft + dp(6), 0);
                        wrapperLayout.setOrientation(LinearLayout.VERTICAL);

                        LoginOrView orDividerView = new LoginOrView(context);

                        TextView textView = new LinkSpanDrawable.LinksTextView(context);
                        SpannableStringBuilder text = AndroidUtilities.replaceTags(LocaleController.getString(isGroup() ? R.string.BoostingStoriesByGiftingGroup2 : R.string.BoostingStoriesByGiftingChannel2));
                        SpannableStringBuilder link = new SpannableStringBuilder(LocaleController.getString(R.string.BoostingStoriesByGiftingLink));
                        link.setSpan(new ClickableSpan() {
                            @Override
                            public void updateDrawState(@NonNull TextPaint ds) {
                                super.updateDrawState(ds);
                                ds.setUnderlineText(false);
                                ds.setColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
                            }
                            @Override
                            public void onClick(@NonNull View view) {
                                BoostPagerBottomSheet.show(getBaseFragment(), dialogId, resourcesProvider);
                            }
                        }, 0, link.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        SpannableString arrow = new SpannableString(">");
                        Drawable imageDrawable = getContext().getResources().getDrawable(R.drawable.msg_arrowright).mutate();
                        imageDrawable.setColorFilter(new PorterDuffColorFilter(Theme.key_chat_messageLinkIn, PorterDuff.Mode.SRC_IN));
                        ColoredImageSpan span = new ColoredImageSpan(imageDrawable);
                        span.setColorKey(Theme.key_chat_messageLinkIn);
                        span.setSize(dp(18));
                        span.setWidth(dp(11));
                        span.setTranslateX(-dp(5));
                        arrow.setSpan(span, 0, arrow.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        textView.setText(TextUtils.concat(text, " ", AndroidUtilities.replaceCharSequence(">", link, arrow)));
                        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                        textView.setLineSpacing(dp(3), 1.0f);
                        if (resourcesProvider instanceof DarkThemeResourceProvider) {
                            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
                        } else {
                            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                        }
                        textView.setGravity(Gravity.CENTER_HORIZONTAL);
                        textView.setOnClickListener(v -> BoostPagerBottomSheet.show(getBaseFragment(), dialogId, resourcesProvider));
                        orDividerView.setOnClickListener(v -> textView.performClick());

                        if (isMiniBoostBtnForAdminAvailable()) {
                            ButtonWithCounterView copyBtn = new ButtonWithCounterView(context, resourcesProvider);
                            copyBtn.setText(LocaleController.getString(R.string.Copy), false);
                            copyBtn.setOnClickListener(v -> {
                                AndroidUtilities.addToClipboard(getBoostLink());
                                dismiss();
                            });
                            LinearLayout linearLayout = new LinearLayout(context);
                            linearLayout.addView(boostMiniBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 1f, 0,0,0,4,0));
                            linearLayout.addView(copyBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 1f, 0,4,0,0,0));
                            wrapperLayout.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 12, 12, 12, 8));
                        } else {
                            wrapperLayout.addView(actionBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 12, 12, 12, 8));
                        }
                        wrapperLayout.addView(orDividerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, -5, 0, 0));
                        wrapperLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, -6, 12, 17));
                        view = wrapperLayout;
                        break;
                    case VIEW_TYPE_BOOST_FEATURE:
                        view = new BoostFeatureCell(context, resourcesProvider);
                        break;
                    case VIEW_TYPE_BOOST_LINK:
                        FrameLayout frameLayout = new FrameLayout(getContext());
                        frameLayout.setPadding(backgroundPaddingLeft + dp(6), 0, backgroundPaddingLeft + dp(6), 0);
                        TextView linkView = new TextView(context);

                        if (statisticClickRunnable == null && ChatObject.hasAdminRights(getChat())) {
                            statisticClickRunnable = () -> getBaseFragment().presentFragment(StatisticActivity.create(getChat()));
                        }

                        linkView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(13), AndroidUtilities.dp(statisticClickRunnable == null ? 18 : 50), AndroidUtilities.dp(13));
                        linkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                        linkView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                        linkView.setSingleLine(true);
                        frameLayout.addView(linkView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 11, 0));
                        linkView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_graySection, resourcesProvider), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_listSelector, resourcesProvider), (int) (255 * 0.3f))));
                        linkView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                        linkView.setOnClickListener(v -> {
                            AndroidUtilities.addToClipboard(getBoostLink());
                        });
                        if (statisticClickRunnable != null) {
                            ImageView imageView = new ImageView(getContext());
                            imageView.setImageResource(R.drawable.msg_stats);
                            imageView.setColorFilter(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                            imageView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
                            imageView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(20), 0, ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_listSelector, resourcesProvider), (int) (255 * 0.3f))));
                            frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 15, 0, 15, 0));
                            imageView.setOnClickListener(v -> {
                                statisticClickRunnable.run();
                                dismiss();
                            });
                        }
                        linkView.setText(getBoostLink());
                        linkView.setGravity(Gravity.CENTER);
                        view = frameLayout;
                        break;
                    default:
                    case VIEW_TYPE_HEADER:
                        view = headerView = new HeaderView(context);
                        break;
                    case VIEW_TYPE_CHANNEL:
                        view = new AdminedChannelCell(context, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AdminedChannelCell cell = (AdminedChannelCell) v.getParent();
                                final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
                                channels.add(cell.getCurrentChannel());
                                revokeLinks(channels);
                            }
                        }, true, 9);
                        break;
                    case VIEW_TYPE_SHADOW:
                        view = new ShadowSectionCell(context, 12, Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
                        break;
                    case VIEW_TYPE_HEADER_CELL:
                        view = new HeaderCell(context);
                        view.setPadding(0, 0, 0, AndroidUtilities.dp(8));
                        break;
                    case VIEW_TYPE_USER:
                        view = new GroupCreateUserCell(context, 1, 0, false);
                        view.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
                        break;
                    case VIEW_TYPE_PROGRESS:
                        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context, null);
                        flickerLoadingView.setViewType(type == TYPE_PUBLIC_LINKS ? FlickerLoadingView.LIMIT_REACHED_LINKS : FlickerLoadingView.LIMIT_REACHED_GROUPS);
                        flickerLoadingView.setIsSingleCell(true);
                        flickerLoadingView.setIgnoreHeightCheck(true);
                        flickerLoadingView.setItemsCount(10);
                        view = flickerLoadingView;
                        break;
                    case VIEW_TYPE_SPACE16DP:
                        view = new View(getContext()) {
                            @Override
                            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(16), MeasureSpec.EXACTLY));
                            }
                        };
                        break;
                }
                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                switch (holder.getItemViewType()) {
                    case VIEW_TYPE_USER:
                        GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                        if (type == TYPE_TO0_MANY_COMMUNITIES) {
                            TLRPC.Chat chat = inactiveChats.get(position - chatStartRow);
                            String signature = inactiveChatsSignatures.get(position - chatStartRow);
                            cell.setObject(chat, chat.title, signature, position != chatEndRow - 1f);
                            cell.setChecked(selectedChats.contains(chat), false);
                        } else if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                            TLRPC.User user = restrictedUsers.get(position - chatStartRow);
                            final boolean premiumBlocked = premiumMessagingBlockedUsers != null && premiumMessagingBlockedUsers.contains(user.id);
                            cell.overridePremiumBlocked(premiumBlocked, false);
                            String signature;
                            if (premiumBlocked) {
                                signature = LocaleController.getString(R.string.InvitePremiumBlockedUser);
                            } else {
                                signature = LocaleController.formatUserStatus(currentAccount, user, null, null);
                            }
                            cell.setObject(user, ContactsController.formatName(user.first_name, user.last_name), signature, position != chatEndRow - 1f);
                            cell.setChecked(selectedChats.contains(user), false);
                        }
                        break;
                    case VIEW_TYPE_CHANNEL:
                        TLRPC.Chat chat = chats.get(position - chatStartRow);
                        AdminedChannelCell adminedChannelCell = (AdminedChannelCell) holder.itemView;
                        TLRPC.Chat oldChat = adminedChannelCell.getCurrentChannel();
                        adminedChannelCell.setChannel(chat, false);
                        adminedChannelCell.setChecked(selectedChats.contains(chat), oldChat == chat);
                        break;
                    case VIEW_TYPE_HEADER_CELL:
                        HeaderCell headerCell = (HeaderCell) holder.itemView;
                        if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                            if (canSendLink) {
                                headerCell.setText(LocaleController.getString(R.string.ChannelInviteViaLink));
                            } else {
                                if (restrictedUsers.size() == 1) {
                                    headerCell.setText(LocaleController.getString(R.string.ChannelInviteViaLinkRestricted2));
                                } else {
                                    headerCell.setText(LocaleController.getString(R.string.ChannelInviteViaLinkRestricted3));
                                }
                            }
                        } else if (type == TYPE_PUBLIC_LINKS) {
                            headerCell.setText(LocaleController.getString(R.string.YourPublicCommunities));
                        } else {
                            headerCell.setText(LocaleController.getString(R.string.LastActiveCommunities));
                        }
                        break;
                    case VIEW_TYPE_BOOST_FEATURE:
                        final int index = position - boostFeaturesStartRow;
                        if (boostFeatures != null && index >= 0 && index < boostFeatures.size()) {
                            ((BoostFeatureCell) holder.itemView).set(boostFeatures.get(index));
                        }
                        break;
                }
            }

            @Override
            public int getItemViewType(int position) {
                if (headerRow == position) {
                    return VIEW_TYPE_HEADER;
                } else if (dividerRow == position) {
                    return VIEW_TYPE_SHADOW;
                } else if (chatsTitleRow == position) {
                    return VIEW_TYPE_HEADER_CELL;
                } else if (loadingRow == position) {
                    return VIEW_TYPE_PROGRESS;
                } else if (emptyViewDividerRow == position) {
                    return VIEW_TYPE_SPACE16DP;
                } else if (linkRow == position) {
                    return VIEW_TYPE_BOOST_LINK;
                } else if (bottomRow == position) {
                    return VIEW_TYPE_BOOST_FOOTER;
                }
                if (boostFeatures != null && position >= boostFeaturesStartRow && position <= boostFeaturesStartRow + boostFeatures.size()) {
                    return VIEW_TYPE_BOOST_FEATURE;
                }
                if (type == TYPE_TO0_MANY_COMMUNITIES || type == TYPE_ADD_MEMBERS_RESTRICTED) {
                    return VIEW_TYPE_USER;
                } else {
                    return VIEW_TYPE_CHANNEL;
                }
            }

            @Override
            public int getItemCount() {
                return rowCount;
            }
        };
    }

    private boolean isMiniBoostBtnForAdminAvailable() {
        return isBoostingForAdminPossible() && ChatObject.hasAdminRights(getChat());
    }

    private boolean isBoostingForAdminPossible() {
        return type == TYPE_BOOSTS_FOR_USERS
                || type == TYPE_BOOSTS_FOR_POSTING
                || type == TYPE_BOOSTS_FOR_COLOR
                || type == TYPE_BOOSTS_FOR_PROFILE_COLOR
                || type == TYPE_BOOSTS_FOR_EMOJI_STATUS
                || type == TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK
                || type == TYPE_BOOSTS_FOR_WALLPAPER
                || type == TYPE_BOOSTS_FOR_REPLY_ICON
                || type == TYPE_BOOSTS_FOR_PROFILE_ICON
                || type == TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER
                || type == TYPE_BOOSTS_FOR_ADS;
    }

    private String getBoostLink() {
        if (boostsStatus != null && !TextUtils.isEmpty(boostsStatus.boost_url)) {
            return boostsStatus.boost_url;
        }
        return ChannelBoostUtilities.createLink(currentAccount, dialogId);
    }

    public void setCurrentValue(int currentValue) {
        this.currentValue = currentValue;
    }

    public void setVeryLargeFile(boolean b) {
        isVeryLargeFile = b;
        updatePremiumButtonText();
    }

    public void setRestrictedUsers(
        TLRPC.Chat chat,
        ArrayList<TLRPC.User> userRestrictedPrivacy,
        ArrayList<Long> premiumMessagingBlockedUsers,
        ArrayList<Long> premiumInviteBlockedUsers
    ) {
        fromChat = chat;
        canSendLink = ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE);
        restrictedUsers = new ArrayList<>(userRestrictedPrivacy);
        this.premiumMessagingBlockedUsers = premiumMessagingBlockedUsers;
        this.premiumInviteBlockedUsers = premiumInviteBlockedUsers;
        selectedChats.clear();
        if (canSendLink) {
            for (TLRPC.User user : restrictedUsers) {
                if (premiumMessagingBlockedUsers == null || !premiumMessagingBlockedUsers.contains(user.id)) {
                    selectedChats.add(user);
                }
            }
        }
        updateRows();
        updateButton();

        if (
            (type == TYPE_ADD_MEMBERS_RESTRICTED && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && (premiumInviteBlockedUsers != null && !premiumInviteBlockedUsers.isEmpty() || premiumMessagingBlockedUsers != null && premiumMessagingBlockedUsers.size() >= restrictedUsers.size())) &&
            premiumInviteBlockedUsers != null && premiumMessagingBlockedUsers != null && (premiumInviteBlockedUsers.size() == 1 && premiumMessagingBlockedUsers.size() == 1 || premiumMessagingBlockedUsers.size() >= premiumInviteBlockedUsers.size())
        ) {
            if (LimitReachedBottomSheet.this.premiumButtonView != null && LimitReachedBottomSheet.this.premiumButtonView.getParent() != null) {
                ((ViewGroup) LimitReachedBottomSheet.this.premiumButtonView.getParent()).removeView(LimitReachedBottomSheet.this.premiumButtonView);
            }
            if (divider != null && divider.getParent() != null) {
                ((ViewGroup) divider.getParent()).removeView(divider);
            }
            if (recyclerListView != null) {
                recyclerListView.setPadding(0, 0, 0, 0);
            }
        }
    }

    public void setDialogId(long dialogId) {
        this.dialogId = dialogId;
        updateRows();
    }

    public void setChatMessageCell(ChatMessageCell chatMessageCell) {
        this.chatMessageCell = chatMessageCell;
    }

    public void setBoostsStats(TL_stories.TL_premium_boostsStatus boostsStatus, boolean isCurrentChat) {
        this.boostsStatus = boostsStatus;
        this.isCurrentChat = isCurrentChat;
        updateRows();
    }

    public void setCanApplyBoost(ChannelBoostsController.CanApplyBoost canApplyBoost) {
        this.canApplyBoost = canApplyBoost;
        updateButton();
        updatePremiumButtonText();
    }

    public void showStatisticButtonInLink(Runnable runnable) {
        this.statisticClickRunnable = runnable;
    }

    private class HeaderView extends LinearLayout {

        TextView title;
        TextView description;
        BoostCounterView boostCounterView;
        LinearLayout titleLinearLayout;

        @SuppressLint("SetTextI18n")
        public HeaderView(Context context) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);
            setPadding(backgroundPaddingLeft + dp(6), 0, backgroundPaddingLeft + dp(6), 0);

            limitParams = getLimitParams(type, currentAccount);
            int icon = limitParams.icon;
            String descriptionStr;
            MessagesController messagesController = MessagesController.getInstance(currentAccount);
            boolean premiumLocked = messagesController.premiumFeaturesBlocked();
            boolean isGroup = isGroup();
            if (type == TYPE_FEATURES) {
                descriptionStr = LocaleController.getString(isGroup ? R.string.BoostingAdditionalFeaturesSubtitle : R.string.BoostingAdditionalFeaturesSubtitleChannel);
            } else if (type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS) {
                descriptionStr = getBoostsDescriptionString(true);
            } else if (type == TYPE_BOOSTS_FOR_USERS) {
                if (chatMessageCell != null) {
                    int count = chatMessageCell.getMessageObject().messageOwner.from_boosts_applied;
                    TLRPC.Chat chat = getChat();
                    SpannableStringBuilder builder = new SpannableStringBuilder();
                    builder.append(LocaleController.formatPluralString("GroupBoostedByUserWithTimes", count, UserObject.getFirstName(chatMessageCell.getCurrentUser())));
                    builder.append(" ");
                    builder.append(LocaleController.formatString(R.string.GroupBoostedByUserWithDescription, chat == null ? "" : chat.title));
                    descriptionStr = builder.toString();
                } else {
                    if (getBaseFragment() instanceof GroupColorActivity) {
                        descriptionStr = LocaleController.formatPluralString("BoostingGroupBoostWhatAreBoostsDescription", BoostRepository.giveawayBoostsPerPremium());
                    } else {
                        SpannableStringBuilder builder = new SpannableStringBuilder(getBoostsDescriptionString(true));
                        if (ChatObject.hasAdminRights(getChat()) && isGroup) {
                            builder.append(" ").append(LocaleController.getString(R.string.BoostingPremiumUserCanBoostGroupWithLink));
                        }
                        descriptionStr = builder.toString();
                    }
                }
            } else if (type == TYPE_BOOSTS_FOR_POSTING) {
                if (boostsStatus.level == 0) {
                    descriptionStr = LocaleController.formatString(
                            isGroup ? R.string.GroupNeedBoostsDescription : R.string.ChannelNeedBoostsDescription,
                            LocaleController.formatPluralString("MoreBoosts", boostsStatus.next_level_boosts, boostsStatus.next_level_boosts)
                    );
                } else {
                    descriptionStr = LocaleController.formatString(
                            isGroup ? R.string.GroupNeedBoostsDescriptionNextLevel : R.string.ChannelNeedBoostsDescriptionNextLevel,
                            LocaleController.formatPluralString("MoreBoosts", boostsStatus.next_level_boosts - boostsStatus.boosts, boostsStatus.next_level_boosts - boostsStatus.boosts),
                            LocaleController.formatPluralString("BoostStories", boostsStatus.level + 1)
                    );
                }
            } else if (type == TYPE_BOOSTS_FOR_COLOR) {
                descriptionStr = LocaleController.formatString(
                        isGroup ? R.string.GroupNeedBoostsForColorDescription : R.string.ChannelNeedBoostsForColorDescription,
                        channelColorLevelMin()
                );
            } else if (type == TYPE_BOOSTS_FOR_PROFILE_COLOR) {
                descriptionStr = LocaleController.formatString(
                        isGroup ? R.string.GroupNeedBoostsForProfileColorDescription : R.string.ChannelNeedBoostsForProfileColorDescription,
                        channelColorLevelMin()
                );
            } else if (type == TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK) {
                descriptionStr = LocaleController.formatString(
                        R.string.GroupNeedBoostsForCustomEmojiPackDescription,
                        messagesController.groupEmojiStickersLevelMin
                );
            } else if (type == TYPE_BOOSTS_FOR_ADS) {
                descriptionStr = LocaleController.formatString(
                        R.string.ChannelNeedBoostsForSwitchOffAdsDescription,
                        messagesController.channelRestrictSponsoredLevelMin
                );
            } else if (type == TYPE_BOOSTS_FOR_EMOJI_STATUS) {
                descriptionStr = LocaleController.formatString(
                        isGroup ? R.string.GroupNeedBoostsForEmojiStatusDescription : R.string.ChannelNeedBoostsForEmojiStatusDescription,
                        isGroup ? messagesController.groupEmojiStatusLevelMin : messagesController.channelEmojiStatusLevelMin
                );
            } else if (type == TYPE_BOOSTS_FOR_REPLY_ICON) {
                descriptionStr = LocaleController.formatString(
                        isGroup ? R.string.GroupNeedBoostsForReplyIconDescription : R.string.ChannelNeedBoostsForReplyIconDescription,
                        messagesController.channelBgIconLevelMin
                );
            } else if (type == TYPE_BOOSTS_FOR_PROFILE_ICON) {
                descriptionStr = LocaleController.formatString(
                        isGroup ? R.string.GroupNeedBoostsForProfileIconDescription : R.string.ChannelNeedBoostsForProfileIconDescription,
                        isGroup ? messagesController.groupProfileBgIconLevelMin : messagesController.channelProfileIconLevelMin
                );
            } else if (type == TYPE_BOOSTS_FOR_WALLPAPER) {
                descriptionStr = LocaleController.formatString(
                        isGroup ? R.string.GroupNeedBoostsForWallpaperDescription : R.string.ChannelNeedBoostsForWallpaperDescription,
                        isGroup ? messagesController.groupWallpaperLevelMin : messagesController.channelWallpaperLevelMin
                );
            } else if (type == TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER) {
                descriptionStr = LocaleController.formatString(
                        isGroup ? R.string.GroupNeedBoostsForCustomWallpaperDescription : R.string.ChannelNeedBoostsForCustomWallpaperDescription,
                        isGroup ? messagesController.groupCustomWallpaperLevelMin : messagesController.channelCustomWallpaperLevelMin
                );
            } else if (type == TYPE_BOOSTS_FOR_REACTIONS) {
                descriptionStr = LocaleController.formatPluralString("ReactionReachLvlForReaction", requiredLvl, requiredLvl);
            } else if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                premiumLocked = true;
                if (!canSendLink) {
                    if (ChatObject.isChannelAndNotMegaGroup(fromChat)) {
                        if (restrictedUsers.size() == 1) {
                            descriptionStr = LocaleController.formatString("InviteChannelRestrictedUsers2One", R.string.InviteChannelRestrictedUsers2One, ContactsController.formatName(restrictedUsers.get(0)));
                        } else {
                            descriptionStr = LocaleController.formatPluralString("InviteChannelRestrictedUsers2", restrictedUsers.size(), restrictedUsers.size());
                        }
                    } else {
                        if (restrictedUsers.size() == 1) {
                            descriptionStr = LocaleController.formatString("InviteRestrictedUsers2One", R.string.InviteRestrictedUsers2One, ContactsController.formatName(restrictedUsers.get(0)));
                        } else {
                            descriptionStr = LocaleController.formatPluralString("InviteRestrictedUsers2", restrictedUsers.size(), restrictedUsers.size());
                        }
                    }
                } else {
                    if (ChatObject.isChannelAndNotMegaGroup(fromChat)) {
                        if (restrictedUsers.size() == 1) {
                            descriptionStr = LocaleController.formatString("InviteChannelRestrictedUsersOne", R.string.InviteChannelRestrictedUsersOne, ContactsController.formatName(restrictedUsers.get(0)));
                        } else {
                            descriptionStr = LocaleController.formatPluralString("InviteChannelRestrictedUsers", restrictedUsers.size(), restrictedUsers.size());
                        }
                    } else {
                        if (restrictedUsers.size() == 1) {
                            descriptionStr = LocaleController.formatString("InviteRestrictedUsersOne", R.string.InviteRestrictedUsersOne, ContactsController.formatName(restrictedUsers.get(0)));
                        } else {
                            descriptionStr = LocaleController.formatPluralString("InviteRestrictedUsers", restrictedUsers.size(), restrictedUsers.size());
                        }
                    }
                }
            } else {
                if (premiumLocked) {
                    descriptionStr = limitParams.descriptionStrLocked;
                } else {
                    descriptionStr = (UserConfig.getInstance(currentAccount).isPremium() || isVeryLargeFile) ? limitParams.descriptionStrPremium : limitParams.descriptionStr;
                }
            }
            int defaultLimit = limitParams.defaultLimit;
            int premiumLimit = limitParams.premiumLimit;
            int currentValue = LimitReachedBottomSheet.this.currentValue;
            float percent = .5f, position = .5f;

            if (type == TYPE_FOLDERS) {
                currentValue = MessagesController.getInstance(currentAccount).dialogFilters.size() - 1;
            } else if (type == TYPE_ACCOUNTS) {
                currentValue = UserConfig.getActivatedAccountsCount();
            } else if (type == TYPE_PIN_DIALOGS) {
                int pinnedCount = 0;
                ArrayList<TLRPC.Dialog> dialogs = MessagesController.getInstance(currentAccount).getDialogs(0);
                for (int a = 0, N = dialogs.size(); a < N; a++) {
                    TLRPC.Dialog dialog = dialogs.get(a);
                    if (dialog instanceof TLRPC.TL_dialogFolder) {
                        continue;
                    }
                    if (dialog.pinned) {
                        pinnedCount++;
                    }
                }
                currentValue = pinnedCount;
            }

            if (UserConfig.getInstance(currentAccount).isPremium() || isVeryLargeFile) {
                currentValue = premiumLimit;
                position = 1f;
            } else {
                if (currentValue < 0) {
                    currentValue = defaultLimit;
                }
                if (type == TYPE_ACCOUNTS) {
                    if (currentValue > defaultLimit) {
                        position = (float) (currentValue - defaultLimit) / (float) (premiumLimit - defaultLimit);
                    }
                } else {
                    position = currentValue / (float) premiumLimit;
                }
            }

            percent = defaultLimit / (float) premiumLimit;

            final boolean boostsType = (
                type == TYPE_BOOSTS_FOR_POSTING ||
                type == TYPE_BOOSTS_FOR_COLOR ||
                type == TYPE_BOOSTS_FOR_PROFILE_COLOR ||
                type == TYPE_BOOSTS_FOR_EMOJI_STATUS ||
                type == TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK ||
                type == TYPE_BOOSTS_FOR_WALLPAPER ||
                type == TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER ||
                type == TYPE_BOOSTS_FOR_USERS ||
                type == TYPE_BOOSTS_FOR_ADS ||
                type == TYPE_BOOSTS_FOR_REACTIONS ||
                type == TYPE_BOOSTS_FOR_REPLY_ICON ||
                type == TYPE_BOOSTS_FOR_PROFILE_ICON ||
                type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS
            );
            if (boostsType) {
                currentValue = 0;
            }

            if (type == TYPE_ADD_MEMBERS_RESTRICTED && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && (premiumInviteBlockedUsers != null && !premiumInviteBlockedUsers.isEmpty() || premiumMessagingBlockedUsers != null && premiumMessagingBlockedUsers.size() >= restrictedUsers.size())) {
                ArrayList<Long> userIds = premiumInviteBlockedUsers.isEmpty() ? premiumMessagingBlockedUsers : premiumInviteBlockedUsers;

                AvatarsImageView avatarsImageView = new AvatarsImageView(context, false);
                avatarsImageView.avatarsDrawable.strokeWidth = dp(3.33f);
                avatarsImageView.setSize(dp(72));
                avatarsImageView.setStepFactor(0.4f);
                final int count = Math.min(userIds.size(), 3);
                avatarsImageView.setCount(count);
                for (int i = 0; i < count; ++i) {
                    long userId = userIds.get(i);
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                    avatarsImageView.setObject(i, currentAccount, user);
                }
                avatarsImageView.commitTransition(false);
                addView(avatarsImageView, LayoutHelper.createLinear(72 + (count - 1) * 30, 72, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 13));

                TextView title = new TextView(context);
                title.setGravity(Gravity.CENTER);
                title.setTypeface(AndroidUtilities.bold());
                title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                title.setText(LocaleController.getString(R.string.InvitePremiumBlockedTitle));
                addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 9));

                TextView description = new TextView(context);
                description.setGravity(Gravity.CENTER);
                description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 19));

                final boolean andMessaging = premiumMessagingBlockedUsers != null && premiumMessagingBlockedUsers.size() >= premiumInviteBlockedUsers.size();
                final boolean onlyMessaging = premiumInviteBlockedUsers != null && premiumInviteBlockedUsers.isEmpty();
                String string;
                if (userIds.size() == 1) {
                    string = LocaleController.formatString(
                        andMessaging ? R.string.InviteMessagePremiumBlockedOne : R.string.InvitePremiumBlockedOne,
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(0)))
                    );
                } else if (userIds.size() == 2) {
                    string = LocaleController.formatString(
                        andMessaging ? R.string.InviteMessagePremiumBlockedTwo : R.string.InvitePremiumBlockedTwo,
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(0))),
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(1)))
                    );
                } else if (userIds.size() == 3) {
                    string = LocaleController.formatString(
                        andMessaging ? R.string.InviteMessagePremiumBlockedThree : R.string.InvitePremiumBlockedThree,
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(0))),
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(1))),
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(2)))
                    );
                } else {
                    string = LocaleController.formatPluralString(
                        andMessaging ? "InviteMessagePremiumBlockedMany" : "InvitePremiumBlockedMany",
                            userIds.size() - 2,
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(0))),
                        UserObject.getForcedFirstName(MessagesController.getInstance(currentAccount).getUser(userIds.get(1)))
                    );
                    avatarsImageView.setPlus(userIds.size() - 2, getThemedColor(Theme.key_dialogBackground));
                }
                description.setText(AndroidUtilities.replaceTags(string));

                PremiumButtonView premiumButtonView = new PremiumButtonView(context, false, resourcesProvider);
                ScaleStateListAnimator.apply(premiumButtonView, .02f, 1.2f);
                premiumButtonView.setButton(LocaleController.getString(R.string.InvitePremiumBlockedSubscribe), v -> {
                    if (parentFragment == null) return;
                    BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                    params.transitionFromLeft = true;
                    params.allowNestedScroll = false;
                    parentFragment.showAsSheet(new PremiumPreviewFragment("invite_privacy"), params);
                });
                addView(premiumButtonView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 4 + backgroundPaddingLeft / AndroidUtilities.density, 0, 4 + backgroundPaddingLeft / AndroidUtilities.density, 18));

                final int blockedInviting = premiumInviteBlockedUsers == null ? 0 : premiumInviteBlockedUsers.size();
                final int blockedMessaging = premiumMessagingBlockedUsers == null ? 0 : premiumMessagingBlockedUsers.size();

                if ((blockedInviting - blockedMessaging) > 0 && !(blockedInviting == 1 && blockedMessaging == 1) && canSendLink) {
                    SimpleTextView or = new SimpleTextView(context) {
                        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        @Override
                        protected void dispatchDraw(Canvas canvas) {
                            paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), .8f));
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setStrokeWidth(1);
                            final float cy = getHeight() / 2f;
                            canvas.drawLine(0, cy, getWidth() / 2f - getTextWidth() / 2f - dp(8), cy, paint);
                            canvas.drawLine(getWidth() / 2f + getTextWidth() / 2f + dp(8), cy, getWidth(), cy, paint);

                            super.dispatchDraw(canvas);
                        }
                    };
                    or.setGravity(Gravity.CENTER);
                    or.setAlignment(Layout.Alignment.ALIGN_CENTER);
                    or.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
                    or.setText(" " + LocaleController.getString(R.string.InvitePremiumBlockedOr) + " ");
                    or.setTextSize(14);
                    addView(or, LayoutHelper.createLinear(190, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 0, 12, 20));

                    title = new TextView(context);
                    title.setGravity(Gravity.CENTER);
                    title.setTypeface(AndroidUtilities.bold());
                    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                    title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    title.setText(LocaleController.getString(R.string.InviteBlockedTitle));
                    addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 9));

                    description = new TextView(context);
                    description.setGravity(Gravity.CENTER);
                    description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    if (premiumInviteBlockedUsers.size() <= 1) {
                        description.setText(LocaleController.getString(R.string.InviteBlockedOneMessage));
                    } else {
                        description.setText(LocaleController.getString(R.string.InviteBlockedManyMessage));
                    }
                    addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 19));
                }

                updatePremiumButtonText();
                return;
            } else if (type != TYPE_FEATURES) {
                limitPreviewView = new LimitPreviewView(context, icon, currentValue, premiumLimit, percent, resourcesProvider) {
                    @Override
                    public void invalidate() {
                        if (lockInvalidation) {
                            return;
                        }
                        super.invalidate();
                    }
                };
                if (boostsType) {
                    if (boostsStatus != null) {
                        limitPreviewView.setBoosts(boostsStatus, canApplyBoost != null && canApplyBoost.boostedNow);
                    }
                } else {
                    limitPreviewView.setBagePosition(position);
                    limitPreviewView.setType(type);
                    limitPreviewView.defaultCount.setVisibility(View.GONE);
                    if (premiumLocked) {
                        limitPreviewView.setPremiumLocked();
                    } else {
                        if (UserConfig.getInstance(currentAccount).isPremium() || isVeryLargeFile) {
                            limitPreviewView.premiumCount.setVisibility(View.GONE);
                            if (type == TYPE_LARGE_FILE) {
                                limitPreviewView.defaultCount.setText("2 GB");
                            } else {
                                limitPreviewView.defaultCount.setText(Integer.toString(defaultLimit));
                            }
                            limitPreviewView.defaultCount.setVisibility(View.VISIBLE);
                        }
                    }
                }

                if (type == TYPE_PUBLIC_LINKS || type == TYPE_TO0_MANY_COMMUNITIES) {
                    limitPreviewView.setDelayedAnimation();
                }

                addView(limitPreviewView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, -4, 0, -4, 0));
            }

            if (type == TYPE_FEATURES) {
                FrameLayout frameLayout = new FrameLayout(context);
                ImageView imageView = new ImageView(context);
                imageView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.large_boosts));
                frameLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
                frameLayout.setBackground(Theme.createCircleDrawable(dp(79), Theme.getColor(Theme.key_featuredStickers_addButton)));
                addView(frameLayout, LayoutHelper.createLinear(79, 79, Gravity.CENTER_HORIZONTAL, 0, 23, 0, 0));
            }

            title = new TextView(context);
            title.setTypeface(AndroidUtilities.bold());
            if (type == TYPE_FEATURES) {
                title.setText(LocaleController.getString(R.string.BoostingAdditionalFeaturesTitle));
            } else if (type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS) {
                title.setText(getBoostsTitleString());
            } else if (type == TYPE_BOOSTS_FOR_USERS) {
                if (getBaseFragment() instanceof GroupColorActivity) {
                    title.setText(LocaleController.getString(R.string.BoostingGroupBoostWhatAreBoosts));
                } else {
                    title.setText(getBoostsTitleString());
                }
            } else if (type == TYPE_BOOSTS_FOR_ADS) {
                title.setText(getBoostsTitleString());
            } else if (type == TYPE_BOOSTS_FOR_POSTING) {
                if (boostsStatus.level == 0) {
                    title.setText(LocaleController.getString(R.string.BoostingEnableStories));
                } else {
                    title.setText(LocaleController.getString(R.string.BoostingIncreaseLevel));
                }
            } else if (type == TYPE_BOOSTS_FOR_REACTIONS) {
                title.setText(LocaleController.getString(R.string.ReactionCustomReactions));
            } else if (type == TYPE_BOOSTS_FOR_COLOR) {
                title.setText(LocaleController.getString(R.string.BoostingEnableColor));
            } else if (type == TYPE_BOOSTS_FOR_PROFILE_COLOR) {
                title.setText(LocaleController.getString(R.string.BoostingEnableProfileColor));
            } else if (type == TYPE_BOOSTS_FOR_REPLY_ICON) {
                title.setText(LocaleController.getString(R.string.BoostingEnableLinkIcon));
            } else if (type == TYPE_BOOSTS_FOR_PROFILE_ICON) {
                title.setText(LocaleController.getString(R.string.BoostingEnableProfileIcon));
            } else if (type == TYPE_BOOSTS_FOR_EMOJI_STATUS) {
                title.setText(LocaleController.getString(R.string.BoostingEnableEmojiStatus));
            } else if (type == TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK) {
                title.setText(LocaleController.getString(R.string.BoostingEnableGroupEmojiPack));
            } else if (type == TYPE_BOOSTS_FOR_WALLPAPER || type == TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER) {
                title.setText(LocaleController.getString(R.string.BoostingEnableWallpaper));
            } else if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                if (canSendLink) {
                    title.setText(LocaleController.getString(R.string.ChannelInviteViaLink));
                } else {
                    title.setText(LocaleController.getString(R.string.ChannelInviteViaLinkRestricted));
                }
            } else if (type == TYPE_LARGE_FILE) {
                title.setText(LocaleController.getString(R.string.FileTooLarge));
            } else {
                title.setText(LocaleController.getString(R.string.LimitReached));
            }
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            title.setGravity(Gravity.CENTER);

            if (type == TYPE_BOOSTS_FOR_USERS || type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS || isMiniBoostBtnForAdminAvailable()) {
                boostCounterView = new BoostCounterView(context, resourcesProvider);
                boostCounterView.setCount(canApplyBoost.boostCount, false);
                if (type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS) {
                    boostCounterView.setVisibility(GONE);
                }
                if (isCurrentChat) {
                    titleLinearLayout = new LinearLayout(context);
                    titleLinearLayout.setOrientation(HORIZONTAL);
                    titleLinearLayout.setWeightSum(1f);
                    titleLinearLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1, 0));
                    titleLinearLayout.addView(boostCounterView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 2, 0, 0));
                    addView(titleLinearLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, premiumLocked ? 8 : 22, 12, 9));
                } else {
                    addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, premiumLocked ? 8 : 22, 0, 0));

                    LinearLayout rootLayout = new LinearLayout(getContext());
                    rootLayout.setOrientation(HORIZONTAL);
                    rootLayout.setClipChildren(false);

                    FrameLayout frameLayout = new FrameLayout(getContext());
                    frameLayout.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider)));
                    BackupImageView backupImageView = new BackupImageView(getContext());
                    backupImageView.setRoundRadius(AndroidUtilities.dp(14));
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    AvatarDrawable avatarDrawable = new AvatarDrawable();
                    avatarDrawable.setInfo(currentAccount, chat);
                    backupImageView.setForUserOrChat(chat, avatarDrawable);
                    frameLayout.addView(backupImageView, LayoutHelper.createFrame(28, 28));
                    TextView textView = new TextView(getContext());
                    if (chat != null) {
                        textView.setText(chat.title);
                    }
                    textView.setSingleLine(true);
                    textView.setMaxLines(1);
                    textView.setEllipsize(TextUtils.TruncateAt.END);
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                    frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 36, 0, 12, 0));

                    rootLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, Gravity.BOTTOM, 18, 0, 18, 0));

                    LayoutTransition layoutTransition = new LayoutTransition();
                    layoutTransition.setDuration(100);
                    layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
                    rootLayout.setLayoutTransition(layoutTransition);
                    rootLayout.addView(boostCounterView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, -30, 2, 18, 0));

                    addView(rootLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38, Gravity.CENTER, 0, -4, 0, 12));
                    ScaleStateListAnimator.apply(rootLayout);
                    rootLayout.setOnClickListener(v -> {
                        getBaseFragment().presentFragment(ChatActivity.of(dialogId));
                        dismiss();
                    });
                }
            } else {
                addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, premiumLocked ? 8 : 22, 0, 10));
            }
            description = new TextView(context);
            description.setText(AndroidUtilities.replaceTags(descriptionStr));
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            description.setGravity(Gravity.CENTER_HORIZONTAL);
            description.setLineSpacing(description.getLineSpacingExtra(), description.getLineSpacingMultiplier() * 1.1f);
            if (type == TYPE_BOOSTS_FOR_POSTING) {
                if (resourcesProvider instanceof DarkThemeResourceProvider) {
                    description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
                } else {
                    description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                }
            } else {
                description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            }
            if (type == TYPE_BOOSTS_FOR_USERS) {
                addView(description, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, -2, 24, 17));
            } else {
                addView(description, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, 0, 24, 24));
            }
            if (type == TYPE_FEATURES) {
                ((MarginLayoutParams) description.getLayoutParams()).bottomMargin = dp(15);
                ((MarginLayoutParams) title.getLayoutParams()).bottomMargin = dp(6);
                ((MarginLayoutParams) title.getLayoutParams()).topMargin = dp(12);
            }
            updatePremiumButtonText();
        }

        public void recreateTitleAndDescription() {
            int descriptionIndex = indexOfChild(description);

            if (isCurrentChat) {
                int titleIndex = indexOfChild(titleLinearLayout);
                removeView(titleLinearLayout);
                titleLinearLayout.removeView(title);
                titleLinearLayout.removeView(boostCounterView);

                titleLinearLayout = new LinearLayout(getContext());
                titleLinearLayout.setOrientation(HORIZONTAL);
                titleLinearLayout.setWeightSum(1f);
                titleLinearLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1, 0));
                titleLinearLayout.addView(boostCounterView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 2, 0, 0));
                addView(titleLinearLayout, titleIndex, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12 + 13, 22, 12, 9));
            } else {
                int titleIndex = indexOfChild(title);
                removeView(title);
                title = new TextView(getContext());
                title.setTypeface(AndroidUtilities.bold());
                title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                title.setGravity(Gravity.CENTER);
                addView(title, titleIndex, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 22, 0, 0));
            }

            removeView(description);
            description = new TextView(getContext());
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            description.setLineSpacing(description.getLineSpacingExtra(), description.getLineSpacingMultiplier() * 1.1f);
            description.setGravity(Gravity.CENTER_HORIZONTAL);
            description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            addView(description, descriptionIndex, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, -2, 24, 17));
        }
    }

    protected int channelColorLevelMin() {
        return 0;
    }

    private String getBoostsTitleString() {
        if (boostsStatus.next_level_boosts == 0) {
            return LocaleController.formatString("BoostsMaxLevelReached", R.string.BoostsMaxLevelReached);
        } else if (boostsStatus.level > 0 && !canApplyBoost.alreadyActive) {
            return LocaleController.getString(isGroup() ? R.string.BoostGroup : R.string.BoostChannel);
        } else if (isCurrentChat) {
            if (type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS) {
                return LocaleController.getString(isGroup() ? R.string.BoostGroup : R.string.BoostChannel);
            }
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (canApplyBoost.alreadyActive) {
                return LocaleController.formatString("YouBoostedChannel2", R.string.YouBoostedChannel2, chat.title);
            } else {
                return LocaleController.getString(isGroup() ? R.string.BoostGroup : R.string.BoostChannel);
            }
        } else {
            if (type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS) {
                return LocaleController.getString(isGroup() ? R.string.BoostGroup : R.string.BoostChannel);
            }
            if (canApplyBoost.alreadyActive) {
                return LocaleController.getString(isGroup() ? R.string.YouBoostedGroup : R.string.YouBoostedChannel);
            } else {
                return LocaleController.getString(isGroup() ? R.string.BoostingEnableStoriesForGroup : R.string.BoostingEnableStoriesForChannel);
            }
        }
    }

    private TLRPC.Chat getChat() {
        return MessagesController.getInstance(currentAccount).getChat(-dialogId);
    }

    private TLRPC.ChatFull getChatFull() {
        return MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
    }

    private boolean isGroup() {
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        return !ChatObject.isChannelAndNotMegaGroup(chat);
    }

    private String getDescriptionForRemoveRestrictions() {
        TLRPC.Chat chat = getChat();
        return LocaleController.formatPluralString("BoostingRemoveRestrictionsSubtitle", getNeededBoostsForUnlockGroup(), chat == null ? "" : chat.title);
    }

    private int getNeededBoostsForUnlockGroup() {
        TLRPC.ChatFull chatFull = getChatFull();
        return Math.max(chatFull.boosts_unrestrict - chatFull.boosts_applied, 0);
    }

    private String getBoostDescriptionStringAfterBoost() {
        String descriptionStr = null;
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        boolean isGroup = isGroup();
        if (type == TYPE_BOOSTS_FOR_COLOR) {
            descriptionStr = LocaleController.formatString(
                    isGroup ? R.string.GroupNeedBoostsForColorDescription : R.string.ChannelNeedBoostsForColorDescription,
                    channelColorLevelMin()
            );
        } else if (type == TYPE_BOOSTS_FOR_PROFILE_COLOR) {
            descriptionStr = LocaleController.formatString(
                    isGroup ? R.string.GroupNeedBoostsForProfileColorDescription : R.string.ChannelNeedBoostsForProfileColorDescription,
                    channelColorLevelMin()
            );
        } else if (type == TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK) {
            descriptionStr = LocaleController.formatString(
                    R.string.GroupNeedBoostsForCustomEmojiPackDescription,
                    messagesController.groupEmojiStickersLevelMin
            );
        } else if (type == TYPE_BOOSTS_FOR_ADS) {
            descriptionStr = LocaleController.formatString(
                    R.string.ChannelNeedBoostsForSwitchOffAdsDescription,
                    messagesController.channelRestrictSponsoredLevelMin
            );
        } else if (type == TYPE_BOOSTS_FOR_EMOJI_STATUS) {
            descriptionStr = LocaleController.formatString(
                    isGroup ? R.string.GroupNeedBoostsForEmojiStatusDescription : R.string.ChannelNeedBoostsForEmojiStatusDescription,
                    isGroup ? messagesController.groupEmojiStatusLevelMin : messagesController.channelEmojiStatusLevelMin
            );
        } else if (type == TYPE_BOOSTS_FOR_REPLY_ICON) {
            descriptionStr = LocaleController.formatString(
                    isGroup ? R.string.GroupNeedBoostsForReplyIconDescription : R.string.ChannelNeedBoostsForReplyIconDescription,
                    messagesController.channelBgIconLevelMin
            );
        } else if (type == TYPE_BOOSTS_FOR_PROFILE_ICON) {
            descriptionStr = LocaleController.formatString(
                    isGroup ? R.string.GroupNeedBoostsForProfileIconDescription : R.string.ChannelNeedBoostsForProfileIconDescription,
                    isGroup ? messagesController.groupProfileBgIconLevelMin : messagesController.channelProfileIconLevelMin
            );
        } else if (type == TYPE_BOOSTS_FOR_WALLPAPER) {
            descriptionStr = LocaleController.formatString(
                    isGroup ? R.string.GroupNeedBoostsForWallpaperDescription : R.string.ChannelNeedBoostsForWallpaperDescription,
                    isGroup ? messagesController.groupWallpaperLevelMin : messagesController.channelWallpaperLevelMin
            );
        } else if (type == TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER) {
            descriptionStr = LocaleController.formatString(
                    isGroup ? R.string.GroupNeedBoostsForCustomWallpaperDescription : R.string.ChannelNeedBoostsForCustomWallpaperDescription,
                    isGroup ? messagesController.groupCustomWallpaperLevelMin : messagesController.channelCustomWallpaperLevelMin
            );
        }
        if (descriptionStr != null) {
            return descriptionStr;
        }
        return getBoostsDescriptionString(false);
    }

    private String getBoostsDescriptionString(boolean init) {
        if (type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS) {
            return getDescriptionForRemoveRestrictions();
        }
        TLRPC.Chat channel = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        String channelTitle = channel == null ? LocaleController.getString(isGroup() ? R.string.AccDescrGroup : R.string.AccDescrChannel) : channel.title;
        boolean isZeroBoostsForNextLevel = boostsStatus.boosts == boostsStatus.current_level_boosts;
        if (isMiniBoostBtnForAdminAvailable() && boostsStatus.next_level_boosts != 0 && init) {
            return LocaleController.formatString(
                    isGroup() ? R.string.GroupNeedBoostsDescriptionForNewFeatures : R.string.ChannelNeedBoostsDescriptionForNewFeatures,
                    channelTitle,
                    LocaleController.formatPluralString("MoreBoosts", boostsStatus.next_level_boosts - boostsStatus.boosts, boostsStatus.next_level_boosts - boostsStatus.boosts)
            );
        }
        if (isZeroBoostsForNextLevel && canApplyBoost.alreadyActive) {
            if (boostsStatus.level == 1) {
                return LocaleController.formatString(isGroup() ? R.string.GroupBoostsJustReachedLevel1 : R.string.ChannelBoostsJustReachedLevel1);
            } else {
                return LocaleController.formatString(isGroup() ? R.string.GroupBoostsJustReachedLevelNext : R.string.ChannelBoostsJustReachedLevelNext,
                        boostsStatus.level,
                        LocaleController.formatPluralString("BoostStories", boostsStatus.level));
            }
        } else {
            if (canApplyBoost.alreadyActive) {
                if (boostsStatus.level == 0) {
                    return LocaleController.formatString(
                            isGroup() ? R.string.GroupNeedBoostsDescriptionForNewFeatures : R.string.ChannelNeedBoostsDescriptionForNewFeatures,
                            channelTitle,
                            LocaleController.formatPluralString("MoreBoosts", boostsStatus.next_level_boosts - boostsStatus.boosts, boostsStatus.next_level_boosts - boostsStatus.boosts)
                    );
                } else {
                    if (boostsStatus.next_level_boosts == 0) {
                        return LocaleController.formatString(isGroup() ? R.string.GroupBoostsJustReachedLevelNext : R.string.ChannelBoostsJustReachedLevelNext,
                                boostsStatus.level,
                                LocaleController.formatPluralString("BoostStories", boostsStatus.level + 1));
                    } else {
                        return LocaleController.formatString(
                                isGroup() ? R.string.GroupNeedBoostsDescriptionForNewFeatures : R.string.ChannelNeedBoostsDescriptionForNewFeatures,
                                channelTitle,
                                LocaleController.formatPluralString("MoreBoosts", boostsStatus.next_level_boosts - boostsStatus.boosts, boostsStatus.next_level_boosts - boostsStatus.boosts)
                        );
                    }
                }
            } else {
                if (boostsStatus.level == 0) {
                    return LocaleController.formatString(
                            isGroup() ? R.string.GroupNeedBoostsDescriptionForNewFeatures : R.string.ChannelNeedBoostsDescriptionForNewFeatures,
                        channelTitle,
                        LocaleController.formatPluralString("MoreBoosts", boostsStatus.next_level_boosts - boostsStatus.boosts, boostsStatus.next_level_boosts - boostsStatus.boosts)
                    );
                } else {
                    if (boostsStatus.next_level_boosts == 0) {
                        return LocaleController.formatString(isGroup() ? R.string.GroupBoostsJustReachedLevelNext : R.string.ChannelBoostsJustReachedLevelNext,
                                boostsStatus.level,
                                LocaleController.formatPluralString("BoostStories", boostsStatus.level + 1));
                    } else {
                        return LocaleController.formatString(
                                isGroup() ? R.string.GroupNeedBoostsDescriptionForNewFeatures : R.string.ChannelNeedBoostsDescriptionForNewFeatures,
                                channelTitle,
                                LocaleController.formatPluralString("MoreBoosts", boostsStatus.next_level_boosts - boostsStatus.boosts, boostsStatus.next_level_boosts - boostsStatus.boosts)
                        );
                    }
                }
            }
        }
    }

    private static LimitParams getLimitParams(int type, int currentAccount) {
        LimitParams limitParams = new LimitParams();
        if (type == TYPE_PIN_DIALOGS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).dialogFiltersPinnedLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).dialogFiltersPinnedLimitPremium;
            limitParams.icon = R.drawable.msg_limit_pin;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedPinDialogs", R.string.LimitReachedPinDialogs, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedPinDialogsPremium", R.string.LimitReachedPinDialogsPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedPinDialogsLocked", R.string.LimitReachedPinDialogsLocked, limitParams.defaultLimit);
        } else if (type == TYPE_PIN_SAVED_DIALOGS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).savedDialogsPinnedLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).savedDialogsPinnedLimitPremium;
            limitParams.icon = R.drawable.msg_limit_pin;
            limitParams.descriptionStr = LocaleController.formatString(R.string.LimitReachedPinSavedDialogs, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString(R.string.LimitReachedPinSavedDialogsPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString(R.string.LimitReachedPinSavedDialogsLocked, limitParams.defaultLimit);
        } else if (type == TYPE_PUBLIC_LINKS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).publicLinksLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).publicLinksLimitPremium;
            limitParams.icon = R.drawable.msg_limit_links;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedPublicLinks", R.string.LimitReachedPublicLinks, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedPublicLinksPremium", R.string.LimitReachedPublicLinksPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedPublicLinksLocked", R.string.LimitReachedPublicLinksLocked, limitParams.defaultLimit);
        } else if (type == TYPE_FOLDER_INVITES) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).chatlistInvitesLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).chatlistInvitesLimitPremium;
            limitParams.icon = R.drawable.msg_limit_links;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedFolderLinks", R.string.LimitReachedFolderLinks, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedFolderLinksPremium", R.string.LimitReachedFolderLinksPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedFolderLinksLocked", R.string.LimitReachedFolderLinksLocked, limitParams.defaultLimit);
        } else if (type == TYPE_SHARED_FOLDERS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).chatlistJoinedLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).chatlistJoinedLimitPremium;
            limitParams.icon = R.drawable.msg_limit_folder;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedSharedFolders", R.string.LimitReachedSharedFolders, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedSharedFoldersPremium", R.string.LimitReachedSharedFoldersPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedSharedFoldersLocked", R.string.LimitReachedSharedFoldersLocked, limitParams.defaultLimit);
        } else if (type == TYPE_FOLDERS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).dialogFiltersLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).dialogFiltersLimitPremium;
            limitParams.icon = R.drawable.msg_limit_folder;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedFolders", R.string.LimitReachedFolders, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedFoldersPremium", R.string.LimitReachedFoldersPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedFoldersLocked", R.string.LimitReachedFoldersLocked, limitParams.defaultLimit);
        } else if (type == TYPE_CHATS_IN_FOLDER) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).dialogFiltersChatsLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).dialogFiltersChatsLimitPremium;
            limitParams.icon = R.drawable.msg_limit_chats;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedChatInFolders", R.string.LimitReachedChatInFolders, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedChatInFoldersPremium", R.string.LimitReachedChatInFoldersPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedChatInFoldersLocked", R.string.LimitReachedChatInFoldersLocked, limitParams.defaultLimit);
        } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).channelsLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).channelsLimitPremium;
            limitParams.icon = R.drawable.msg_limit_groups;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedCommunities", R.string.LimitReachedCommunities, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedCommunitiesPremium", R.string.LimitReachedCommunitiesPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedCommunitiesLocked", R.string.LimitReachedCommunitiesLocked, limitParams.defaultLimit);
        } else if (type == TYPE_LARGE_FILE) {
            limitParams.defaultLimit = 100;
            limitParams.premiumLimit = 200;
            limitParams.icon = R.drawable.msg_limit_folder;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedFileSize", R.string.LimitReachedFileSize, "2 GB", "4 GB");
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedFileSizePremium", R.string.LimitReachedFileSizePremium, "4 GB");
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedFileSizeLocked", R.string.LimitReachedFileSizeLocked, "2 GB");
        } else if (type == TYPE_ACCOUNTS) {
            limitParams.defaultLimit = 3;
            limitParams.premiumLimit = 4;
            limitParams.icon = R.drawable.msg_limit_accounts;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedAccounts", R.string.LimitReachedAccounts, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedAccountsPremium", R.string.LimitReachedAccountsPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedAccountsPremium", R.string.LimitReachedAccountsPremium, limitParams.defaultLimit);
        } else if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
            limitParams.defaultLimit = 0;
            limitParams.premiumLimit = 0;
            limitParams.icon = R.drawable.msg_limit_links;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedAccounts", R.string.LimitReachedAccounts, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = "";
            limitParams.descriptionStrLocked = "";
        } else if (type == TYPE_STORIES_COUNT) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).storyExpiringLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).storyExpiringLimitPremium;
            limitParams.icon = R.drawable.msg_limit_stories;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedStoriesCount", R.string.LimitReachedStoriesCount, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedStoriesCountPremium", R.string.LimitReachedStoriesCountPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedStoriesCountPremium", R.string.LimitReachedStoriesCountPremium, limitParams.defaultLimit);
        } else if (type == TYPE_STORIES_WEEK) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).storiesSentWeeklyLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).storiesSentWeeklyLimitPremium;
            limitParams.icon = R.drawable.msg_limit_stories;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedStoriesWeekly", R.string.LimitReachedStoriesWeekly, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedStoriesWeeklyPremium", R.string.LimitReachedStoriesWeeklyPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedStoriesWeeklyPremium", R.string.LimitReachedStoriesWeeklyPremium, limitParams.defaultLimit);
        } else if (type == TYPE_STORIES_MONTH) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).storiesSentMonthlyLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).storiesSentMonthlyLimitPremium;
            limitParams.icon = R.drawable.msg_limit_stories;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedStoriesMonthly", R.string.LimitReachedStoriesMonthly, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedStoriesMonthlyPremium", R.string.LimitReachedStoriesMonthlyPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedStoriesMonthlyPremium", R.string.LimitReachedStoriesMonthlyPremium, limitParams.defaultLimit);
        } else if (type == TYPE_BOOSTS_FOR_POSTING || type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS || type == TYPE_BOOSTS_FOR_COLOR || type == TYPE_BOOSTS_FOR_PROFILE_COLOR || type == TYPE_BOOSTS_FOR_REPLY_ICON || type == TYPE_BOOSTS_FOR_PROFILE_ICON || type == TYPE_BOOSTS_FOR_EMOJI_STATUS || type == TYPE_BOOSTS_FOR_ADS || type == TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK || type == TYPE_BOOSTS_FOR_WALLPAPER || type == TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER || type == TYPE_BOOSTS_FOR_USERS || type == TYPE_BOOSTS_FOR_REACTIONS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).storiesSentMonthlyLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).storiesSentMonthlyLimitPremium;
            limitParams.icon = R.drawable.filled_limit_boost;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedStoriesMonthly", R.string.LimitReachedStoriesMonthly, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedStoriesMonthlyPremium", R.string.LimitReachedStoriesMonthlyPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedStoriesMonthlyPremium", R.string.LimitReachedStoriesMonthlyPremium, limitParams.defaultLimit);
        }
        return limitParams;
    }

    boolean loadingAdminedChannels;

    private void loadAdminedChannels() {
        loadingAdminedChannels = true;
        loading = true;
        updateRows();
        TLRPC.TL_channels_getAdminedPublicChannels req = new TLRPC.TL_channels_getAdminedPublicChannels();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingAdminedChannels = false;
            if (response != null) {
                TLRPC.TL_messages_chats res = (TLRPC.TL_messages_chats) response;
                chats.clear();
                chats.addAll(res.chats);
                loading = false;
                enterAnimator.showItemsAnimated(chatsTitleRow + 4);
                int savedTop = 0;
                for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                    if (recyclerListView.getChildAt(i) instanceof HeaderView) {
                        savedTop = recyclerListView.getChildAt(i).getTop();
                        break;
                    }
                }
                updateRows();
                if (headerRow >= 0 && savedTop != 0) {
                    ((LinearLayoutManager) recyclerListView.getLayoutManager()).scrollToPositionWithOffset(headerRow + 1, savedTop);
                }
            }

            int currentValue = Math.max(chats.size(), limitParams.defaultLimit);
            limitPreviewView.setIconValue(currentValue, false);
            limitPreviewView.setBagePosition(currentValue / (float) limitParams.premiumLimit);
            limitPreviewView.startDelayedAnimation();
        }));
    }

    private void updateRows() {
        rowCount = 0;
        dividerRow = -1;
        chatStartRow = -1;
        chatEndRow = -1;
        loadingRow = -1;
        linkRow = -1;
        emptyViewDividerRow = -1;
        boostFeaturesStartRow = -1;

        headerRow = rowCount++;
        if (
            type == TYPE_BOOSTS_FOR_USERS ||
            type == TYPE_BOOSTS_FOR_POSTING ||
            type == TYPE_BOOSTS_FOR_COLOR ||
            type == TYPE_BOOSTS_FOR_PROFILE_COLOR ||
            type == TYPE_BOOSTS_FOR_REPLY_ICON ||
            type == TYPE_BOOSTS_FOR_PROFILE_ICON ||
            type == TYPE_BOOSTS_FOR_WALLPAPER ||
            type == TYPE_BOOSTS_FOR_CUSTOM_WALLPAPER ||
            type == TYPE_BOOSTS_FOR_EMOJI_STATUS ||
            type == TYPE_BOOSTS_FOR_CUSTOM_EMOJI_PACK ||
            type == TYPE_BOOSTS_FOR_REACTIONS ||
            type == TYPE_BOOSTS_FOR_ADS
        ) {
            if (type != TYPE_BOOSTS_FOR_USERS || ChatObject.hasAdminRights(getChat())) {
                topPadding = .24f;
                linkRow = rowCount++;
                if (MessagesController.getInstance(currentAccount).giveawayGiftsPurchaseAvailable) {
                    bottomRow = rowCount++;
                }
            }
            setupBoostFeatures();
            boostFeaturesStartRow = rowCount++;
            rowCount += boostFeatures.size() - 1;
        } else if (type == TYPE_FEATURES || type == TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS) {
            topPadding = .24f;
            setupBoostFeatures();
            chatStartRow = rowCount;
            boostFeaturesStartRow = rowCount++;
            rowCount += boostFeatures.size() - 1;
            chatEndRow = rowCount;
        } else if (!hasFixedSize(type)) {
            if (type != TYPE_ADD_MEMBERS_RESTRICTED) {
                dividerRow = rowCount++;
                chatsTitleRow = rowCount++;
            } else {
                topPadding = .24f;
            }
            if (loading) {
                loadingRow = rowCount++;
            } else {
                if (
                    !(type == TYPE_ADD_MEMBERS_RESTRICTED && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && (premiumInviteBlockedUsers != null && !premiumInviteBlockedUsers.isEmpty() || premiumMessagingBlockedUsers != null && premiumMessagingBlockedUsers.size() >= restrictedUsers.size())) ||
                    !(premiumInviteBlockedUsers != null && premiumInviteBlockedUsers.size() == 1 && premiumMessagingBlockedUsers != null && premiumMessagingBlockedUsers.size() == 1 && canSendLink)
                ) {
                    chatStartRow = rowCount;
                    if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                        rowCount += restrictedUsers.size();
                    } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
                        rowCount += inactiveChats.size();
                    } else {
                        rowCount += chats.size();
                    }
                    chatEndRow = rowCount;
                }
                if (chatEndRow - chatStartRow > 1) {
                    emptyViewDividerRow = rowCount++;
                }
            }
        }
        notifyDataSetChanged();
    }


    private void revokeSelectedLinks() {
        final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
        for (Object obj : selectedChats) {
            chats.add((TLRPC.Chat) obj);
        }
        revokeLinks(channels);
    }

    private void revokeLinks(ArrayList<TLRPC.Chat> channels) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        builder.setTitle(LocaleController.formatPluralString("RevokeLinks", channels.size()));
        if (channels.size() == 1) {
            TLRPC.Chat channel = channels.get(0);
            if (parentIsChannel) {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlertChannel", R.string.RevokeLinkAlertChannel, MessagesController.getInstance(currentAccount).linkPrefix + "/" + ChatObject.getPublicUsername(channel), channel.title)));
            } else {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlert", R.string.RevokeLinkAlert, MessagesController.getInstance(currentAccount).linkPrefix + "/" + ChatObject.getPublicUsername(channel), channel.title)));
            }
        } else {
            if (parentIsChannel) {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinksAlertChannel", R.string.RevokeLinksAlertChannel)));
            } else {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinksAlert", R.string.RevokeLinksAlert)));
            }
        }
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.RevokeButton), (dialogInterface, interface2) -> {
            dismiss();
            for (int i = 0; i < channels.size(); i++) {
                TLRPC.TL_channels_updateUsername req1 = new TLRPC.TL_channels_updateUsername();
                TLRPC.Chat channel = channels.get(i);
                req1.channel = MessagesController.getInputChannel(channel);
                req1.username = "";
                ConnectionsManager.getInstance(currentAccount).sendRequest(req1, (response1, error1) -> {
                    if (response1 instanceof TLRPC.TL_boolTrue) {
                        AndroidUtilities.runOnUIThread(onSuccessRunnable);
                    }
                }, ConnectionsManager.RequestFlagInvokeAfter);
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        }
    }

    private void loadInactiveChannels() {
        loading = true;
        updateRows();
        TLRPC.TL_channels_getInactiveChannels inactiveChannelsRequest = new TLRPC.TL_channels_getInactiveChannels();
        ConnectionsManager.getInstance(currentAccount).sendRequest(inactiveChannelsRequest, ((response, error) -> {
            if (error == null) {
                final TLRPC.TL_messages_inactiveChats chats = (TLRPC.TL_messages_inactiveChats) response;
                final ArrayList<String> signatures = new ArrayList<>();
                final int count = Math.min(chats.chats.size(), chats.dates.size());
                for (int i = 0; i < count; i++) {
                    TLRPC.Chat chat = chats.chats.get(i);
                    int currentDate = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                    int date = chats.dates.get(i);
                    int daysDif = (currentDate - date) / 86400;

                    String dateFormat;
                    if (daysDif < 30) {
                        dateFormat = LocaleController.formatPluralString("Days", daysDif);
                    } else if (daysDif < 365) {
                        dateFormat = LocaleController.formatPluralString("Months", daysDif / 30);
                    } else {
                        dateFormat = LocaleController.formatPluralString("Years", daysDif / 365);
                    }
                    if (ChatObject.isMegagroup(chat)) {
                        String members = LocaleController.formatPluralString("Members", chat.participants_count);
                        signatures.add(LocaleController.formatString("InactiveChatSignature", R.string.InactiveChatSignature, members, dateFormat));
                    } else if (ChatObject.isChannel(chat)) {
                        signatures.add(LocaleController.formatString("InactiveChannelSignature", R.string.InactiveChannelSignature, dateFormat));
                    } else {
                        String members = LocaleController.formatPluralString("Members", chat.participants_count);
                        signatures.add(LocaleController.formatString("InactiveChatSignature", R.string.InactiveChatSignature, members, dateFormat));
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    inactiveChatsSignatures.clear();
                    inactiveChats.clear();
                    inactiveChatsSignatures.addAll(signatures);
                    for (int i = 0; i < count; i++) {
                        inactiveChats.add(chats.chats.get(i));
                    }
                    loading = false;
                    enterAnimator.showItemsAnimated(chatsTitleRow + 4);
                    int savedTop = 0;
                    for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                        if (recyclerListView.getChildAt(i) instanceof HeaderView) {
                            savedTop = recyclerListView.getChildAt(i).getTop();
                            break;
                        }
                    }
                    updateRows();
                    if (headerRow >= 0 && savedTop != 0) {
                        ((LinearLayoutManager) recyclerListView.getLayoutManager()).scrollToPositionWithOffset(headerRow + 1, savedTop);
                    }

                    if (limitParams == null) {
                        limitParams = getLimitParams(type, currentAccount);
                    }
                    int currentValue = Math.max(inactiveChats.size(), limitParams.defaultLimit);
                    if (limitPreviewView != null) {
                        limitPreviewView.setIconValue(currentValue, false);
                        limitPreviewView.setBagePosition(currentValue / (float) limitParams.premiumLimit);
                        limitPreviewView.startDelayedAnimation();
                    }
                });
            }
        }));
    }

    public static class LimitParams {
        int icon = 0;
        String descriptionStr = null;
        String descriptionStrPremium = null;
        String descriptionStrLocked = null;
        int defaultLimit = 0;
        int premiumLimit = 0;
    }

    private void setupBoostFeatures() {
        boostFeatures = new ArrayList<>();

        ArrayList<BoostFeature> lastFeatureList = null;
        int startLevel = 1;
        if (boostsStatus != null) {
            startLevel = boostsStatus.level + 1;
        }
        if (type == TYPE_FEATURES) {
            startLevel = 1;
        }
        int maxlvl = 10;
        final MessagesController m = MessagesController.getInstance(currentAccount);
        if (m != null) {
            maxlvl = Math.max(maxlvl, m.peerColors != null ? m.peerColors.maxLevel(isGroup()) : 0);
            maxlvl = Math.max(maxlvl, m.profilePeerColors != null ? m.profilePeerColors.maxLevel(isGroup()) : 0);
            if (isGroup()) {
                maxlvl = Math.max(maxlvl, m.groupTranscribeLevelMin);
                maxlvl = Math.max(maxlvl, m.groupWallpaperLevelMin);
                maxlvl = Math.max(maxlvl, m.groupCustomWallpaperLevelMin);
                maxlvl = Math.max(maxlvl, m.groupEmojiStatusLevelMin);
                maxlvl = Math.max(maxlvl, m.groupProfileBgIconLevelMin);
            } else {
                maxlvl = Math.max(maxlvl, m.channelBgIconLevelMin);
                maxlvl = Math.max(maxlvl, m.channelProfileIconLevelMin);
                maxlvl = Math.max(maxlvl, m.channelEmojiStatusLevelMin);
                maxlvl = Math.max(maxlvl, m.channelWallpaperLevelMin);
                maxlvl = Math.max(maxlvl, m.channelCustomWallpaperLevelMin);
                maxlvl = Math.max(maxlvl, m.channelRestrictSponsoredLevelMin);
            }
        }

        for (int lvl = startLevel; lvl <= maxlvl; ++lvl) {
            ArrayList<BoostFeature> featureList = boostFeaturesForLevel(lvl);
            if (lastFeatureList == null || !BoostFeature.arraysEqual(lastFeatureList, featureList)) {
                boostFeatures.add(new BoostFeature.BoostFeatureLevel(lvl, boostFeatures.isEmpty()));
                boostFeatures.addAll(featureList);
                lastFeatureList = featureList;
            }
        }
    }

    private ArrayList<BoostFeature> boostFeaturesForLevel(int level) {
        boolean isGroup = isGroup();
        ArrayList<BoostFeature> list = new ArrayList<>();
        final MessagesController m = MessagesController.getInstance(currentAccount);
        if (m == null) return list;
        list.add(BoostFeature.of(R.drawable.menu_feature_stories, "BoostFeatureStoriesPerDay", level).asIncremental());
        if (!isGroup) {
            list.add(BoostFeature.of(R.drawable.menu_feature_reactions, "BoostFeatureCustomReaction", level).asIncremental());
        }
        final int nameColorsAvailable = m.peerColors != null ? m.peerColors.colorsAvailable(level, false) : 0;
        final int profileColorsAvailable = m.profilePeerColors != null ? m.profilePeerColors.colorsAvailable(level, isGroup) : 0;
        if (!isGroup && nameColorsAvailable > 0) {
            list.add(BoostFeature.of(R.drawable.menu_feature_color_name, "BoostFeatureNameColor", 7));
        }
        if (!isGroup && nameColorsAvailable > 0) {
            list.add(BoostFeature.of(R.drawable.menu_feature_links, "BoostFeatureReplyColor", nameColorsAvailable));
        }
        if (!isGroup && level >= m.channelBgIconLevelMin) {
            list.add(BoostFeature.of(R.drawable.menu_feature_links2, R.string.BoostFeatureReplyIcon));
        }
        if (profileColorsAvailable > 0) {
            list.add(BoostFeature.of(R.drawable.menu_feature_color_profile, isGroup ? "BoostFeatureProfileColorGroup" : "BoostFeatureProfileColor", profileColorsAvailable));
        }
        if (isGroup && level >= m.groupEmojiStickersLevelMin) {
            list.add(BoostFeature.of(R.drawable.menu_feature_pack, R.string.BoostFeatureCustomEmojiPack));
        }
        if ((!isGroup && level >= m.channelProfileIconLevelMin) || (isGroup && level >= m.groupProfileBgIconLevelMin)) {
            list.add(BoostFeature.of(R.drawable.menu_feature_cover, isGroup ? R.string.BoostFeatureProfileIconGroup : R.string.BoostFeatureProfileIcon));
        }
        if (isGroup && level >= m.groupTranscribeLevelMin) {
            list.add(BoostFeature.of(R.drawable.menu_feature_voice, R.string.BoostFeatureVoiceToTextConversion));
        }
        if ((!isGroup && level >= m.channelEmojiStatusLevelMin) || (isGroup && level >= m.groupEmojiStatusLevelMin)) {
            list.add(BoostFeature.of(R.drawable.menu_feature_status, R.string.BoostFeatureEmojiStatuses, "1000+"));
        }
        if ((!isGroup && level >= m.channelWallpaperLevelMin) || (isGroup && level >= m.groupWallpaperLevelMin)) {
            list.add(BoostFeature.of(R.drawable.menu_feature_wallpaper, isGroup ? "BoostFeatureBackgroundGroup" : "BoostFeatureBackground", 8));
        }
        if ((!isGroup && level >= m.channelCustomWallpaperLevelMin) || (isGroup && level >= m.groupCustomWallpaperLevelMin)) {
            list.add(BoostFeature.of(R.drawable.menu_feature_custombg, isGroup ? R.string.BoostFeatureCustomBackgroundGroup : R.string.BoostFeatureCustomBackground));
        }
        if ((!isGroup && level >= m.channelRestrictSponsoredLevelMin)) {
            list.add(BoostFeature.of(R.drawable.menu_feature_noads, R.string.BoostFeatureSwitchOffAds));
        }
        Collections.reverse(list);
        return list;
    }

    private class BoostFeatureCell extends FrameLayout {
        private final Theme.ResourcesProvider resourcesProvider;

        private final ImageView imageView;
        private final SimpleTextView textView;

        private final FrameLayout levelLayout;
        private final SimpleTextView levelTextView;

        public BoostFeature feature;
        public BoostFeature.BoostFeatureLevel level;

        public BoostFeatureCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_premiumGradient1, resourcesProvider), PorterDuff.Mode.SRC_IN));
            addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 24, 0, 24, 0));

            textView = new SimpleTextView(context);
            textView.setWidthWrapContent(true);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            textView.setTextSize(14);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 30 : 60, 0, LocaleController.isRTL ? 60 : 30, 0));

            levelTextView = new SimpleTextView(context);
            levelTextView.setTextColor(Color.WHITE);
            levelTextView.setWidthWrapContent(true);
            levelTextView.setTypeface(AndroidUtilities.bold());
            levelTextView.setTextSize(14);
            levelLayout = new FrameLayout(context) {
                private final PremiumGradient.PremiumGradientTools gradientTools = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradient1, Theme.key_premiumGradient2, -1, -1, -1, resourcesProvider);
                private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                {
                    dividerPaint.setStyle(Paint.Style.STROKE);
                    dividerPaint.setStrokeWidth(1);
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    dividerPaint.setColor(Theme.getColor(Theme.key_sheet_scrollUp, resourcesProvider));
                    canvas.drawLine(dp(18), getHeight() / 2f, levelTextView.getLeft() - dp(20), getHeight() / 2f, dividerPaint);
                    canvas.drawLine(levelTextView.getRight() + dp(20), getHeight() / 2f, getWidth() - dp(18), getHeight() / 2f, dividerPaint);

                    AndroidUtilities.rectTmp.set(
                        levelTextView.getLeft() - dp(15),
                        (levelTextView.getTop() + levelTextView.getBottom() - dp(30)) / 2f,
                        levelTextView.getRight() + dp(15),
                        (levelTextView.getTop() + levelTextView.getBottom() + dp(30)) / 2f
                    );
                    canvas.save();
                    canvas.translate(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top);
                    AndroidUtilities.rectTmp.set(0, 0, AndroidUtilities.rectTmp.width(), AndroidUtilities.rectTmp.height());
                    gradientTools.gradientMatrix(AndroidUtilities.rectTmp);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(15), dp(15), gradientTools.paint);
                    canvas.restore();

                    super.dispatchDraw(canvas);
                }
            };
            levelLayout.setWillNotDraw(false);
            levelLayout.addView(levelTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            addView(levelLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        public void set(BoostFeature boostFeature) {
            if (boostFeature instanceof BoostFeature.BoostFeatureLevel) {
                level = (BoostFeature.BoostFeatureLevel) boostFeature;
                feature = null;

                imageView.setVisibility(View.GONE);
                textView.setVisibility(View.GONE);
                levelLayout.setVisibility(View.VISIBLE);
                levelTextView.setText(LocaleController.formatPluralString(level.isFirst ? "BoostLevelUnlocks" : "BoostLevel", level.lvl));
            } else if (boostFeature != null) {
                level = null;
                feature = boostFeature;
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageResource(feature.iconResId);
                textView.setVisibility(View.VISIBLE);
                if (feature.textKeyPlural != null) {
                    String key1 = feature.textKeyPlural + "_" + LocaleController.getStringParamForNumber(feature.countPlural);
                    String text = LocaleController.getString(key1);
                    if (text == null || text.startsWith("LOC_ERR")) {
                        text = LocaleController.getString(feature.textKeyPlural + "_other");
                    }
                    if (text == null) {
                        text = "";
                    }
                    int index;
                    SpannableStringBuilder ssb = new SpannableStringBuilder(text);
                    if ((index = text.indexOf("%d")) >= 0) {
                        ssb = new SpannableStringBuilder(text);
                        SpannableString boldNumber = new SpannableString(feature.countPlural + "");
                        boldNumber.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, boldNumber.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        ssb.replace(index, index + 2, boldNumber);
                    }
                    textView.setText(ssb);
                } else {
                    String text = LocaleController.getString(feature.textKey);
                    if (text == null) {
                        text = "";
                    }
                    if (feature.countValue != null) {
                        int index;
                        SpannableStringBuilder ssb = new SpannableStringBuilder(text);
                        if ((index = text.indexOf("%s")) >= 0) {
                            ssb = new SpannableStringBuilder(text);
                            SpannableString boldNumber = new SpannableString(feature.countValue);
                            boldNumber.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, boldNumber.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            ssb.replace(index, index + 2, boldNumber);
                        }
                        textView.setText(ssb);
                    } else {
                        textView.setText(text);
                    }
                }
                levelLayout.setVisibility(View.GONE);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(level != null ? 49 : 36), MeasureSpec.EXACTLY));
        }
    }

    public static LimitReachedBottomSheet openBoostsForRemoveRestrictions(BaseFragment baseFragment, TL_stories.TL_premium_boostsStatus boostsStatus, ChannelBoostsController.CanApplyBoost canApplyBoos, long dialogId, boolean inFragment) {
        if (baseFragment == null || boostsStatus == null || canApplyBoos == null || baseFragment.getContext() == null) {
            return null;
        }
        LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(baseFragment, baseFragment.getContext(), TYPE_BOOSTS_FOR_REMOVE_RESTRICTIONS, baseFragment.getCurrentAccount(), baseFragment.getResourceProvider());
        limitReachedBottomSheet.setCanApplyBoost(canApplyBoos);
        limitReachedBottomSheet.setBoostsStats(boostsStatus, true);
        limitReachedBottomSheet.setDialogId(dialogId);
        if (inFragment) {
            baseFragment.showDialog(limitReachedBottomSheet);
        } else {
            limitReachedBottomSheet.show();
        }
        return limitReachedBottomSheet;
    }

    public static void openBoostsForUsers(BaseFragment baseFragment, boolean isCurrentChat, long dialogId, ChannelBoostsController.CanApplyBoost canApplyBoost, TL_stories.TL_premium_boostsStatus boostsStatus, ChatMessageCell chatMessageCell) {
        if (baseFragment == null || canApplyBoost == null || boostsStatus == null || baseFragment.getContext() == null) {
            return;
        }
        LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(baseFragment, baseFragment.getContext(), TYPE_BOOSTS_FOR_USERS, baseFragment.getCurrentAccount(), baseFragment.getResourceProvider());
        limitReachedBottomSheet.setCanApplyBoost(canApplyBoost);
        limitReachedBottomSheet.setBoostsStats(boostsStatus, isCurrentChat);
        limitReachedBottomSheet.setDialogId(dialogId);
        limitReachedBottomSheet.setChatMessageCell(chatMessageCell);
        baseFragment.showDialog(limitReachedBottomSheet);
    }

    public static void openBoostsForPostingStories(BaseFragment baseFragment, long dialogId, ChannelBoostsController.CanApplyBoost canApplyBoost, TL_stories.TL_premium_boostsStatus boostsStatus, Runnable statisticAction) {
        if (baseFragment == null || canApplyBoost == null || boostsStatus == null || baseFragment.getContext() == null) {
            return;
        }
        LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(baseFragment, baseFragment.getContext(), LimitReachedBottomSheet.TYPE_BOOSTS_FOR_POSTING, baseFragment.getCurrentAccount(), baseFragment.getResourceProvider());
        limitReachedBottomSheet.setCanApplyBoost(canApplyBoost);
        limitReachedBottomSheet.setBoostsStats(boostsStatus, true);
        limitReachedBottomSheet.setDialogId(dialogId);
        limitReachedBottomSheet.showStatisticButtonInLink(statisticAction);
        limitReachedBottomSheet.show();
    }
}
