package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.CountDownTimer;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorBtnCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorUserCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ReassignBoostBottomSheet extends BottomSheetWithRecyclerListView {
    private static final int
            HOLDER_TYPE_HEADER = 0,
            HOLDER_TYPE_DIVIDER = 1,
            HOLDER_TYPE_SUBTITLE = 2,
            HOLDER_TYPE_USER = 3;

    private static final int CONTENT_VIEWS_COUNT = 3;
    private static final int CONTAINER_HEIGHT_DP = 64;

    public static ReassignBoostBottomSheet show(BaseFragment fragment, TL_stories.TL_premium_myBoosts myBoosts, TLRPC.Chat currentChat) {
        ReassignBoostBottomSheet bottomSheet = new ReassignBoostBottomSheet(fragment, myBoosts, currentChat);
        bottomSheet.show();
        return bottomSheet;
    }

    private final List<TL_stories.TL_myBoost> selectedBoosts = new ArrayList<>();
    private final List<TL_stories.TL_myBoost> allUsedBoosts = new ArrayList<>();
    private final TL_stories.TL_premium_myBoosts myBoosts;
    private final TLRPC.Chat currentChat;

    private final SelectorBtnCell buttonContainer;
    private final ButtonWithCounterView actionButton;
    private TopCell topCell;
    private CountDownTimer timer;

    public ReassignBoostBottomSheet(BaseFragment fragment, TL_stories.TL_premium_myBoosts myBoosts, TLRPC.Chat currentChat) {
        super(fragment, false, false);
        this.topPadding = 0.3f;
        this.myBoosts = myBoosts;
        this.currentChat = currentChat;

        for (TL_stories.TL_myBoost myBoost : myBoosts.my_boosts) {
            if (myBoost.peer != null && DialogObject.getPeerDialogId(myBoost.peer) != -currentChat.id) {
                allUsedBoosts.add(myBoost);
            }
        }

        buttonContainer = new SelectorBtnCell(getContext(), resourcesProvider, recyclerListView);
        buttonContainer.setClickable(true);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        buttonContainer.setPadding(dp(8), dp(8), dp(8), dp(8));
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        actionButton = new GradientButtonWithCounterView(getContext(), true, resourcesProvider);
        actionButton.withCounterIcon();
        actionButton.setCounterColor(0xFF9874fc);
        actionButton.setOnClickListener(view -> {
            if (selectedBoosts.isEmpty()) {
                return;
            }
            if (actionButton.isLoading()) {
                return;
            }
            actionButton.setLoading(true);
            List<Integer> slots = new ArrayList<>();
            HashSet<Long> uniqueChannelIds = new HashSet<>();
            for (TL_stories.TL_myBoost selectedBoost : selectedBoosts) {
                slots.add(selectedBoost.slot);
                uniqueChannelIds.add(DialogObject.getPeerDialogId(selectedBoost.peer));
            }
            BoostRepository.applyBoost(currentChat.id, slots, result -> {
                MessagesController.getInstance(currentAccount).getBoostsController().getBoostsStats(-currentChat.id, tlPremiumBoostsStatus -> {
                    dismiss();
                    NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.boostedChannelByUser, result, slots.size(), uniqueChannelIds.size(), tlPremiumBoostsStatus);
                });
            }, error -> {
                actionButton.setLoading(false);
                BoostDialogs.showToastError(getContext(), error);
            });
        });

        buttonContainer.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        containerView.addView(buttonContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, AndroidUtilities.dp(CONTAINER_HEIGHT_DP));
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (view instanceof SelectorUserCell) {
                SelectorUserCell cell = ((SelectorUserCell) view);
                if (cell.getBoost().cooldown_until_date > 0) {
                    SpannableStringBuilder text = AndroidUtilities.replaceTags(LocaleController.formatPluralString("BoostingWaitWarningPlural", BoostRepository.boostsPerSentGift()));
                    BulletinFactory.of(container, resourcesProvider).createSimpleBulletin(R.raw.chats_infotip, text, 5).show(true);
                    return;
                }
                if (selectedBoosts.contains(cell.getBoost())) {
                    selectedBoosts.remove(cell.getBoost());
                } else {
                    selectedBoosts.add(cell.getBoost());
                }
                cell.setChecked(selectedBoosts.contains(cell.getBoost()), true);
                updateActionButton(true);
                topCell.showBoosts(selectedBoosts, currentChat);
            }
        });

        fixNavigationBar();
        updateTitle();
        updateActionButton(false);
        Bulletin.addDelegate(container, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int tag) {
                return AndroidUtilities.statusBarHeight;
            }
        });
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        timer = new CountDownTimer(Long.MAX_VALUE, 1000) {
            public void onTick(long millisUntilFinished) {
                List<TL_stories.TL_myBoost> updates = new ArrayList<>(allUsedBoosts.size());
                for (TL_stories.TL_myBoost boost : allUsedBoosts) {
                    if (boost.cooldown_until_date > 0) {
                        updates.add(boost);
                    }
                    if (boost.cooldown_until_date * 1000L < System.currentTimeMillis()) {
                        boost.cooldown_until_date = 0;
                    }
                }
                if (updates.isEmpty()) {
                    return;
                }
                for (int i = 0; i < recyclerListView.getChildCount(); ++i) {
                    View child = recyclerListView.getChildAt(i);
                    if (child instanceof SelectorUserCell) {
                        SelectorUserCell cell = (SelectorUserCell) child;
                        if (updates.contains(cell.getBoost())) {
                            cell.updateTimer();
                        }
                    }
                }
            }

            public void onFinish() {

            }
        };
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        timer.cancel();
    }

    @Override
    public void onOpenAnimationEnd() {
        timer.start();
    }

    private void updateActionButton(boolean animated) {
        actionButton.setShowZero(false);
        if (selectedBoosts.size() > 1) {
            actionButton.setText(LocaleController.getString(R.string.BoostingReassignBoosts), animated);
        } else {
            actionButton.setText(LocaleController.getString(R.string.BoostingReassignBoost), animated);
        }
        actionButton.setCount(selectedBoosts.size(), animated);
        actionButton.setEnabled(selectedBoosts.size() > 0);
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.BoostingReassignBoost);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return new RecyclerListView.SelectionAdapter() {

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == HOLDER_TYPE_USER;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                Context context = parent.getContext();
                switch (viewType) {
                    case HOLDER_TYPE_HEADER:
                        TopCell cell = new TopCell(context);
                        cell.showBoosts(selectedBoosts, currentChat);
                        view = cell;
                        break;
                    case HOLDER_TYPE_DIVIDER:
                        view = new ShadowSectionCell(context, 12, Theme.getColor(Theme.key_windowBackgroundGray));
                        break;
                    case HOLDER_TYPE_SUBTITLE:
                        view = new HeaderCell(context, 22);
                        break;
                    case HOLDER_TYPE_USER:
                        view = new SelectorUserCell(context, true, resourcesProvider, true);
                        break;
                    default:
                        view = new View(context);
                }
                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder.getItemViewType() == HOLDER_TYPE_USER) {
                    TL_stories.TL_myBoost boost = allUsedBoosts.get(position - CONTENT_VIEWS_COUNT);
                    SelectorUserCell userCell = (SelectorUserCell) holder.itemView;
                    userCell.setBoost(boost);
                    userCell.setChecked(selectedBoosts.contains(boost), false);
                } else if (holder.getItemViewType() == HOLDER_TYPE_SUBTITLE) {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setTextSize(15);
                    cell.setPadding(0, 0, 0, AndroidUtilities.dp(2));
                    cell.setText(LocaleController.getString(R.string.BoostingRemoveBoostFrom));
                } else if (holder.getItemViewType() == HOLDER_TYPE_HEADER) {
                    topCell = (TopCell) holder.itemView;
                    topCell.setData(currentChat, ReassignBoostBottomSheet.this);
                }
            }

            @Override
            public int getItemViewType(int position) {
                switch (position) {
                    case 0:
                        return HOLDER_TYPE_HEADER;
                    case 1:
                        return HOLDER_TYPE_DIVIDER;
                    case 2:
                        return HOLDER_TYPE_SUBTITLE;
                    default:
                        return HOLDER_TYPE_USER;
                }
            }

            @Override
            public int getItemCount() {
                return CONTENT_VIEWS_COUNT + allUsedBoosts.size();
            }
        };
    }

    private static class TopCell extends LinearLayout {

        private final List<TLRPC.Chat> addedChats = new ArrayList<>();
        private final AvatarHolderView toAvatar;
        private final ArrowView arrowView;
        private final FrameLayout avatarsContainer;
        private final FrameLayout avatarsWrapper;
        private final TextView description;

        public TopCell(Context context) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);
            setClipChildren(false);
            avatarsContainer = new FrameLayout(getContext());
            avatarsContainer.setClipChildren(false);
            avatarsWrapper = new FrameLayout(getContext());
            avatarsWrapper.setClipChildren(false);

            avatarsContainer.addView(avatarsWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 70, 0, 0, 0, 0, 0));

            arrowView = new ArrowView(context);
            avatarsContainer.addView(arrowView, LayoutHelper.createFrame(24, 24, Gravity.CENTER));

            toAvatar = new AvatarHolderView(context);
            toAvatar.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            avatarsContainer.addView(toAvatar, LayoutHelper.createFrame(70, 70, Gravity.CENTER));

            addView(avatarsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 70, 0, 15, 0, 0));

            TextView title = new TextView(context);
            title.setTypeface(AndroidUtilities.bold());
            title.setText(LocaleController.getString(R.string.BoostingReassignBoost));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 15, 0, 7));

            description = new LinkSpanDrawable.LinksTextView(getContext());
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            description.setGravity(Gravity.CENTER_HORIZONTAL);
            description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));

            description.setLineSpacing(description.getLineSpacingExtra(), description.getLineSpacingMultiplier() * 1.1f);
            addView(description, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 28, 0, 28, 18));
        }

        public void setData(TLRPC.Chat chat, BottomSheet bottomSheet) {
            try {
                String replacer = "%3$s";
                SpannableStringBuilder text = AndroidUtilities.replaceTags(LocaleController.formatPluralString("BoostingReassignBoostTextPluralWithLink", BoostRepository.boostsPerSentGift(), chat == null ? "" : chat.title, replacer));
                SpannableStringBuilder link = AndroidUtilities.replaceSingleTag(
                        getString("BoostingReassignBoostTextLink", R.string.BoostingReassignBoostTextLink),
                        Theme.key_chat_messageLinkIn, REPLACING_TAG_TYPE_LINKBOLD,
                        () -> {
                            bottomSheet.dismiss();
                            NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.didStartedMultiGiftsSelector);
                            AndroidUtilities.runOnUIThread(UserSelectorBottomSheet::open, 220);
                        });
                int indexOfReplacer = TextUtils.indexOf(text, replacer);
                text.replace(indexOfReplacer, indexOfReplacer + replacer.length(), link);
                description.setText(text, TextView.BufferType.EDITABLE);
                description.post(() -> {
                    try {
                        int linkLine = description.getLayout().getLineForOffset(indexOfReplacer);
                        if (linkLine == 0) {
                            description.getEditableText().insert(indexOfReplacer, "\n");
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        public void showBoosts(List<TL_stories.TL_myBoost> selectedBoosts, TLRPC.Chat currentChat) {
            List<TLRPC.Chat> selectedChats = new ArrayList<>(selectedBoosts.size());
            for (TL_stories.TL_myBoost selectedBoost : selectedBoosts) {
                selectedChats.add(MessagesController.getInstance(UserConfig.selectedAccount).getChat(-DialogObject.getPeerDialogId(selectedBoost.peer)));
            }
            showChats(selectedChats, currentChat);
        }

        public void showChats(List<TLRPC.Chat> selectedChats, TLRPC.Chat currentChat) {
            List<TLRPC.Chat> chatsToRemove = new ArrayList<>();
            List<TLRPC.Chat> chatsToAdd = new ArrayList<>();
            int duration = 200;
            CubicBezierInterpolator interpolator = CubicBezierInterpolator.DEFAULT;

            for (TLRPC.Chat selectedChat : selectedChats) {
                if (!addedChats.contains(selectedChat)) {
                    chatsToAdd.add(selectedChat);
                }
            }

            for (TLRPC.Chat addedChat : addedChats) {
                if (!selectedChats.contains(addedChat)) {
                    chatsToRemove.add(addedChat);
                }
            }

            List<AvatarHolderView> allViews = new ArrayList<>();
            for (int i = 0; i < avatarsWrapper.getChildCount(); i++) {
                AvatarHolderView child = (AvatarHolderView) avatarsWrapper.getChildAt(i);
                if (child.getTag() == null) {
                    allViews.add(child);
                }
            }

            for (TLRPC.Chat chat : chatsToAdd) {
                AvatarHolderView avatar = new AvatarHolderView(getContext());
                avatar.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                avatar.setChat(chat);
                int childCount = allViews.size();
                avatarsWrapper.addView(avatar, 0, LayoutHelper.createFrame(70, 70, Gravity.CENTER));
                avatar.setTranslationX(-childCount * dp(23));
                avatar.setAlpha(0f);
                avatar.setScaleX(0.1f);
                avatar.setScaleY(0.1f);
                avatar.animate().alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(interpolator).setDuration(duration).start();
                if (childCount == 0) {
                    avatar.boostIconView.setScaleY(1f);
                    avatar.boostIconView.setScaleX(1f);
                    avatar.boostIconView.setAlpha(1f);
                }
            }

            for (TLRPC.Chat chat : chatsToRemove) {
                AvatarHolderView removedAvatar = null;

                for (AvatarHolderView view : allViews) {
                    if (view.chat == chat) {
                        removedAvatar = view;
                        break;
                    }
                }

                if (removedAvatar != null) {
                    final AvatarHolderView finalRemovedAvatar = removedAvatar;
                    finalRemovedAvatar.setTag("REMOVED");
                    finalRemovedAvatar.animate()
                            .alpha(0f).translationXBy(dp(23))
                            .scaleX(0.1f).scaleY(0.1f)
                            .setInterpolator(interpolator)
                            .setDuration(duration).setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    finalRemovedAvatar.setLayerType(View.LAYER_TYPE_NONE, null);
                                    avatarsWrapper.removeView(finalRemovedAvatar);
                                }
                            }).start();
                    int pos = 0;
                    for (AvatarHolderView view : allViews) {
                        int childCount = allViews.size() - 1;
                        if (view != finalRemovedAvatar) {
                            pos++;
                            childCount -= pos;
                            view.animate().translationX(-childCount * dp(23))
                                    .setInterpolator(interpolator)
                                    .setDuration(duration).start();
                        }
                    }
                    if (allViews.get(allViews.size() - 1) == finalRemovedAvatar && allViews.size() > 1) {
                        allViews.get(allViews.size() - 2).boostIconView.setScaleY(0.1f);
                        allViews.get(allViews.size() - 2).boostIconView.setScaleX(0.1f);
                        allViews.get(allViews.size() - 2).boostIconView.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(duration).setInterpolator(interpolator).start();
                    }
                }
            }
            if (toAvatar.chat == null) {
                toAvatar.setChat(currentChat);
            }
            addedChats.removeAll(chatsToRemove);
            addedChats.addAll(chatsToAdd);

            avatarsContainer.animate().cancel();
            if (addedChats.isEmpty() || addedChats.size() == 1) {
                avatarsContainer.animate().setInterpolator(interpolator).translationX(0).setDuration(duration).start();
            } else {
                int count = addedChats.size() - 1;
                avatarsContainer.animate().setInterpolator(interpolator).translationX(dp(23 / 2f) * count).setDuration(duration).start();
            }

            toAvatar.animate().cancel();
            avatarsWrapper.animate().cancel();
            if (addedChats.isEmpty()) {
                avatarsWrapper.animate().setInterpolator(interpolator).translationX(0).setDuration(duration).start();
                toAvatar.animate().setInterpolator(interpolator).translationX(0).setDuration(duration).start();
            } else {
                avatarsWrapper.animate().setInterpolator(interpolator).translationX(-dp(30 + 18)).setDuration(duration).start();
                toAvatar.animate().setInterpolator(interpolator).translationX(dp(30 + 18)).setDuration(duration).start();
            }
        }
    }

    private static class ArrowView extends FrameLayout {

        public ArrowView(Context context) {
            super(context);
            ImageView arrowImage = new ImageView(getContext());
            arrowImage.setImageResource(R.drawable.msg_arrow_avatar);
            arrowImage.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7));
            addView(arrowImage);
        }
    }

    private static class AvatarHolderView extends FrameLayout {
        private final BackupImageView imageView;
        private final BoostIconView boostIconView;
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public TLRPC.Chat chat;
        AvatarDrawable fromAvatarDrawable = new AvatarDrawable();

        public AvatarHolderView(Context context) {
            super(context);
            imageView = new BackupImageView(getContext());
            imageView.setRoundRadius(AndroidUtilities.dp(30));
            boostIconView = new BoostIconView(context);
            boostIconView.setAlpha(0f);
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 5, 5, 5, 5));
            addView(boostIconView, LayoutHelper.createFrame(28, 28, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 0, 3));
            bgPaint.setColor(Theme.getColor(Theme.key_dialogBackground));
        }

        public void setChat(TLRPC.Chat chat) {
            this.chat = chat;
            fromAvatarDrawable.setInfo(chat);
            imageView.setForUserOrChat(chat, fromAvatarDrawable);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            canvas.drawCircle(getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, (getMeasuredHeight() / 2f) - dp(2f), bgPaint);
            super.dispatchDraw(canvas);
        }
    }

    private static class BoostIconView extends View {

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Drawable boostDrawable = ContextCompat.getDrawable(getContext(), R.drawable.mini_boost_remove);

        public BoostIconView(Context context) {
            super(context);
            paint.setColor(Theme.getColor(Theme.key_dialogBackground));
        }

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
    }
}
