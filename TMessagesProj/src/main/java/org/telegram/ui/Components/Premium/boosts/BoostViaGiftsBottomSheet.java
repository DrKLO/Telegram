package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_payments;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumPreviewBottomSheet;
import org.telegram.ui.Components.Premium.boosts.adapters.BoostAdapter;
import org.telegram.ui.Components.Premium.boosts.cells.ActionBtnCell;
import org.telegram.ui.Components.Premium.boosts.cells.AddChannelCell;
import org.telegram.ui.Components.Premium.boosts.cells.BaseCell;
import org.telegram.ui.Components.Premium.boosts.cells.DateEndCell;
import org.telegram.ui.Components.Premium.boosts.cells.ParticipantsTypeCell;
import org.telegram.ui.Components.Premium.boosts.cells.BoostTypeCell;
import org.telegram.ui.Components.Premium.boosts.cells.DurationCell;
import org.telegram.ui.Components.Premium.boosts.cells.StarGiveawayOptionCell;
import org.telegram.ui.Components.Premium.boosts.cells.SwitcherCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Premium.boosts.adapters.BoostAdapter.Item;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BoostViaGiftsBottomSheet extends BottomSheetWithRecyclerListView implements SelectorBottomSheet.SelectedObjectsListener, NotificationCenter.NotificationCenterDelegate {

    private static final int BOTTOM_HEIGHT_DP = 68;

    public interface ActionListener {
        void onAddChat(List<TLObject> chats);

        void onSelectUser(List<TLObject> users);

        void onSelectCountries(List<TLObject> countries);
    }

    private final ArrayList<Item> items = new ArrayList<>();
    private final List<Integer> sliderValues =
        BoostRepository.isGoogleBillingAvailable() ?
            Arrays.asList(1, 3, 5, 7, 10, 25, 50) :
            Arrays.asList(1, 3, 5, 7, 10, 25, 50, 100);
    private final List<Integer> sliderStarsValues =
        BoostRepository.isGoogleBillingAvailable() ?
            Arrays.asList(1, 3, 5, 7, 10, 25, 50) :
            Arrays.asList(1, 3, 5, 7, 10, 25, 50, 100);
    private final List<Integer> starsNotExtended = Arrays.asList(750, 10_000, 50_000);
    private final TLRPC.Chat currentChat;
    private final List<TLObject> selectedChats = new ArrayList<>();
    private final List<TLObject> selectedUsers = new ArrayList<>();
    private final List<TLObject> selectedCountries = new ArrayList<>();
    private final List<TLRPC.TL_premiumGiftCodeOption> giftCodeOptions = new ArrayList<>();
    private BoostAdapter adapter;
    private int selectedBoostType = BoostTypeCell.TYPE_PREMIUM;
    private int selectedBoostSubType = BoostTypeCell.TYPE_GIVEAWAY;
    private int selectedParticipantsType = ParticipantsTypeCell.TYPE_ALL;
    private boolean starOptionsExpanded;
    private int selectedMonths = 12;
    private long selectedEndDate = BoostDialogs.getThreeDaysAfterToday();
    private int selectedSliderIndex = 2;
    private int selectedStarsSliderIndex = 2;
    private long selectedStars;
    private ActionBtnCell actionBtn;
    private ActionListener actionListener;
    private int top;
    private Runnable onCloseClick;
    private final TL_stories.PrepaidGiveaway prepaidGiveaway;
    private String additionalPrize = "";
    private boolean isAdditionalPrizeSelected;
    private boolean isShowWinnersSelected = true;
    private final Runnable hideKeyboardRunnable = () -> AndroidUtilities.hideKeyboard(recyclerListView);

    public BoostViaGiftsBottomSheet(BaseFragment fragment, boolean needFocus, boolean hasFixedSize, long dialogId, TL_stories.PrepaidGiveaway prepaidGiveaway) {
        super(fragment, needFocus, hasFixedSize);
        this.prepaidGiveaway = prepaidGiveaway;
        this.topPadding = 0.15f;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        useBackgroundTopPadding = false;
        backgroundPaddingLeft = 0;
        updateTitle();
        ((ViewGroup.MarginLayoutParams) actionBar.getLayoutParams()).leftMargin = 0;
        ((ViewGroup.MarginLayoutParams) actionBar.getLayoutParams()).rightMargin = 0;
        if (prepaidGiveaway instanceof TL_stories.TL_prepaidStarsGiveaway) {
            selectedBoostType = BoostTypeCell.TYPE_STARS;
        }
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, AndroidUtilities.dp(BOTTOM_HEIGHT_DP));
        recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(recyclerView);
                }
            }
        });
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (view instanceof SwitcherCell) {
                SwitcherCell cell = ((SwitcherCell) view);
                int type = cell.getType();
                boolean isChecked = !cell.isChecked();
                cell.setChecked(isChecked);
                if (type == SwitcherCell.TYPE_WINNERS) {
                    isShowWinnersSelected = isChecked;
                    updateRows(false, false);
                } else if (type == SwitcherCell.TYPE_ADDITION_PRIZE) {
                    cell.setDivider(isChecked);
                    isAdditionalPrizeSelected = isChecked;
                    updateRows(false, false);
                    adapter.notifyAdditionalPrizeItem(isChecked);
                    adapter.notifyAllVisibleTextDividers();
                    if (!isAdditionalPrizeSelected) {
                        AndroidUtilities.runOnUIThread(hideKeyboardRunnable, 250);
                    } else {
                        AndroidUtilities.cancelRunOnUIThread(hideKeyboardRunnable);
                    }
                }
            }
            if (view instanceof BaseCell) {
                if (view instanceof BoostTypeCell) {
                    int boostType = ((BoostTypeCell) view).getSelectedType();
                    if (boostType == BoostTypeCell.TYPE_PREMIUM || boostType == BoostTypeCell.TYPE_STARS) {
                        if (boostType == BoostTypeCell.TYPE_PREMIUM && selectedBoostType == boostType) {
                            if (actionListener != null) {
                                actionListener.onSelectUser(selectedUsers);
                            }
                            return;
                        }
                        selectedBoostType = boostType;
                        updateRows(true, true);
                        updateActionButton(true);
                        updateTitle();
                    } else if (boostType == BoostTypeCell.TYPE_SPECIFIC_USERS) {
                        if (actionListener != null) {
                            actionListener.onSelectUser(selectedUsers);
                        }
                    } else {
                        selectedBoostSubType = boostType;
                        updateRows(true, true);
                        updateActionButton(true);
                        updateTitle();
                    }
                } else {
                    ((BaseCell) view).markChecked(recyclerListView);
                }
            }
            if (view instanceof ParticipantsTypeCell) {
                int tmpParticipantsType = ((ParticipantsTypeCell) view).getSelectedType();
                if (selectedParticipantsType == tmpParticipantsType) {
                    if (actionListener != null) {
                        actionListener.onSelectCountries(selectedCountries);
                    }
                }
                selectedParticipantsType = tmpParticipantsType;
                updateRows(false, false);
            } else if (view instanceof DurationCell) {
                selectedMonths = ((TLRPC.TL_premiumGiftCodeOption) ((DurationCell) view).getGifCode()).months;
                updateRows(false, false);
                adapter.notifyAllVisibleTextDividers();
            } else if (view instanceof DateEndCell) {
                BoostDialogs.showDatePicker(fragment.getContext(), selectedEndDate, (notify, timeSec) -> {
                    selectedEndDate = timeSec * 1000L;
                    updateRows(false, true);
                }, resourcesProvider);
            } else if (view instanceof AddChannelCell) {
                if (actionListener != null) {
                    actionListener.onAddChat(selectedChats);
                }
            } else if (view instanceof StarGiveawayOptionCell) {
                StarGiveawayOptionCell cell = (StarGiveawayOptionCell) view;
                TL_stars.TL_starsGiveawayOption option = cell.getOption();
                if (option != null) {
                    selectedStars = option.stars;
                    updateRows(true, true);
                    updateActionButton(true);
                    updateTitle();
                }
                return;
            } else if (view instanceof StarsIntroActivity.ExpandView) {
                starOptionsExpanded = true;
                updateRows(true, true);
            }
        });
        this.currentChat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        this.adapter.setItems(currentChat, items, recyclerListView, sliderIndex -> {
            if (selectedBoostType == BoostTypeCell.TYPE_PREMIUM) {
                selectedSliderIndex = sliderIndex;
            } else {
                selectedStarsSliderIndex = sliderIndex;
            }
            actionBtn.updateCounter(getSelectedSliderValueWithBoosts());
            if (selectedBoostType == BoostTypeCell.TYPE_STARS) {
                updateRows(true, true);
            } else {
                updateRows(false, false);
            }
            adapter.updateBoostCounter(getSelectedSliderValueWithBoosts());
        }, deletedChat -> {
            selectedChats.remove(deletedChat);
            updateRows(true, true);
        }, text -> {
            additionalPrize = text;
            updateRows(false, false);
            updateRows(true, true);
        });
        updateRows(false, false);
        actionBtn = new ActionBtnCell(getContext(), resourcesProvider);
        actionBtn.setOnClickListener(v -> {
            if (actionBtn.isLoading()) {
                return;
            }
            if (isPreparedGiveaway()) {
                final TL_stories.TL_prepaidStarsGiveaway starsGiveaway = prepaidGiveaway instanceof TL_stories.TL_prepaidStarsGiveaway ? (TL_stories.TL_prepaidStarsGiveaway) prepaidGiveaway : null;
                final long stars = starsGiveaway != null ? starsGiveaway.stars : 0;
                BoostDialogs.showStartGiveawayDialog(() -> {
                    int dateInt = BoostRepository.prepareServerDate(selectedEndDate);
                    boolean onlyNewSubscribers = selectedParticipantsType == ParticipantsTypeCell.TYPE_NEW;
                    actionBtn.updateLoading(true);
                    BoostRepository.launchPreparedGiveaway(prepaidGiveaway, selectedChats, selectedCountries, currentChat, dateInt, onlyNewSubscribers, isShowWinnersSelected, isAdditionalPrizeSelected, prepaidGiveaway.quantity, additionalPrize,
                            result -> {
                                dismiss();
                                if (starsGiveaway != null) {
                                    final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                                    if (lastFragment != null) {
                                        final ChatActivity chatActivity = ChatActivity.of(dialogId);
                                        chatActivity.whenFullyVisible(() -> {
                                            BulletinFactory.of(chatActivity)
                                                .createSimpleBulletin(R.raw.stars_topup, getString(R.string.StarsGiveawaySentPopup), AndroidUtilities.replaceTags(LocaleController.formatPluralStringComma("StarsGiveawaySentPopupInfo", (int) stars)))
                                                .show(true);
                                        });
                                        lastFragment.presentFragment(chatActivity);
                                    }
                                } else {
                                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.boostByChannelCreated, currentChat, true, prepaidGiveaway), 220);
                                }
                            }, error -> {
                                actionBtn.updateLoading(false);
                                BoostDialogs.showToastError(getContext(), error);
                            });
                });
                return;
            } else if (selectedBoostType == BoostTypeCell.TYPE_STARS) {
                Activity activity = AndroidUtilities.findActivity(getContext());
                if (activity == null) activity = LaunchActivity.instance;
                if (activity == null || activity.isFinishing()) return;

                final TL_stars.TL_starsGiveawayOption option = getSelectedStarsOption();
                final int users = getSelectedSliderValue();
                if (option == null) return;

                actionBtn.button.setLoading(true);
                final boolean onlyNewSubscribers = selectedParticipantsType == ParticipantsTypeCell.TYPE_NEW;
                StarsController.getInstance(currentAccount).buyGiveaway(
                    activity,
                    currentChat,
                    selectedChats,
                    option,
                    users,
                    selectedCountries,
                    BoostRepository.prepareServerDate(selectedEndDate),
                    isShowWinnersSelected,
                    onlyNewSubscribers,
                    isAdditionalPrizeSelected, additionalPrize,
                    (success, error) -> {
                        actionBtn.button.setLoading(false);
                        if (getContext() == null) return;
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        FireworksOverlay fireworksOverlay = LaunchActivity.instance.getFireworksOverlay();
                        if (lastFragment == null) return;
                        if (success) {
                            dismiss();
                            ChatActivity chatActivity = ChatActivity.of(-currentChat.id);
                            lastFragment.presentFragment(chatActivity);
                            lastFragment.whenFullyVisible(() -> {
                                BulletinFactory.of(chatActivity)
                                .createSimpleBulletin(
                                    R.raw.stars_send,
                                    getString(R.string.StarsGiveawaySentPopup),
                                    AndroidUtilities.replaceTags(formatPluralStringComma("StarsGiveawaySentPopupInfo", (int) option.stars))
                                )
                                .setDuration(Bulletin.DURATION_PROLONG)
                                .show(true);
                            });
                            if (fireworksOverlay != null) {
                                fireworksOverlay.start(true);
                            }
                        } else if (error != null) {
                            dismiss();
                            BulletinFactory.of(lastFragment).createSimpleBulletin(R.raw.error, LocaleController.formatString(R.string.UnknownErrorCode, error)).show();
                        }
                    }
                );

                return;
            }

            if (selectedBoostSubType == BoostTypeCell.TYPE_SPECIFIC_USERS) {
                List<TLRPC.TL_premiumGiftCodeOption> options = BoostRepository.filterGiftOptions(giftCodeOptions, selectedUsers.size());
                for (int i = 0; i < options.size(); i++) {
                    TLRPC.TL_premiumGiftCodeOption option = options.get(i);
                    if (option.months == selectedMonths && selectedUsers.size() > 0) {
                        if (BoostRepository.isGoogleBillingAvailable() && BoostDialogs.checkReduceUsers(getContext(), resourcesProvider, giftCodeOptions, option)) {
                            return;
                        }
                        actionBtn.updateLoading(true);
                        BoostRepository.payGiftCode(selectedUsers, option, currentChat, null, fragment, result -> {
                            dismiss();
                            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.boostByChannelCreated, currentChat, false), 220);
                        }, error -> {
                            actionBtn.updateLoading(false);
                            BoostDialogs.showToastError(getContext(), error);
                        });
                        break;
                    }
                }
            } else {
                List<TLRPC.TL_premiumGiftCodeOption> options = BoostRepository.filterGiftOptions(giftCodeOptions, getSelectedSliderValue());
                for (int i = 0; i < options.size(); i++) {
                    TLRPC.TL_premiumGiftCodeOption option = options.get(i);
                    if (option.months == selectedMonths) {
                        if (BoostRepository.isGoogleBillingAvailable() && BoostDialogs.checkReduceQuantity(sliderValues, getContext(), resourcesProvider, giftCodeOptions, option, arg -> {
                            selectedSliderIndex = sliderValues.indexOf(arg.users);
                            updateRows(true, true);
                            updateActionButton(true);
                        })) {
                            return;
                        }
                        boolean onlyNewSubscribers = selectedParticipantsType == ParticipantsTypeCell.TYPE_NEW;
                        int dateInt = BoostRepository.prepareServerDate(selectedEndDate);
                        actionBtn.updateLoading(true);
                        BoostRepository.payGiveAway(selectedChats, selectedCountries, option, currentChat, dateInt, onlyNewSubscribers, fragment, isShowWinnersSelected, isAdditionalPrizeSelected, additionalPrize, result -> {
                            dismiss();
                            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.boostByChannelCreated, currentChat, true), 220);
                        }, error -> {
                            actionBtn.updateLoading(false);
                            BoostDialogs.showToastError(getContext(), error);
                        });
                        break;
                    }
                }
            }
        });
        updateActionButton(false);
        containerView.addView(actionBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, BOTTOM_HEIGHT_DP, Gravity.BOTTOM, 0, 0, 0, 0));
        loadOptions();

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starGiveawayOptionsLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starGiveawayOptionsLoaded) {
            if (recyclerListView != null && recyclerListView.isAttachedToWindow()) {
                updateRows(true, true);
            }
        }
    }

    @Override
    protected boolean needPaddingShadow() {
        return false;
    }

    public void setOnCloseClick(Runnable onCloseClick) {
        this.onCloseClick = onCloseClick;
    }

    @Override
    public void dismiss() {
        if (onCloseClick != null) {
            onCloseClick.run();
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starGiveawayOptionsLoaded);
    }

    private void loadOptions() {
        BoostRepository.loadGiftOptions(currentAccount, currentChat, arg -> {
            giftCodeOptions.clear();
            giftCodeOptions.addAll(arg);
            updateRows(true, true);
        });
    }

    private void updateActionButton(boolean animated) {
        if (isPreparedGiveaway()) {
            if (prepaidGiveaway instanceof TL_stories.TL_prepaidStarsGiveaway) {
                actionBtn.setStartGiveAwayStyle(prepaidGiveaway.quantity, animated);
            } else {
                actionBtn.setStartGiveAwayStyle(prepaidGiveaway.quantity * BoostRepository.giveawayBoostsPerPremium(), animated);
            }
        } else {
            if (selectedBoostSubType == BoostTypeCell.TYPE_GIVEAWAY) {
                actionBtn.setStartGiveAwayStyle(getSelectedSliderValueWithBoosts(), animated);
            } else {
                actionBtn.setGiftPremiumStyle(selectedUsers.size() * BoostRepository.giveawayBoostsPerPremium(), animated, selectedUsers.size() > 0);
            }
        }
    }

    private boolean isGiveaway() {
        return selectedBoostSubType == BoostTypeCell.TYPE_GIVEAWAY;
    }

    public void setActionListener(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    @Override
    protected void onPreDraw(Canvas canvas, int top, float progressToFullView) {
        this.top = top;
    }

    public int getTop() {
        return Math.max(-AndroidUtilities.dp(16), top - (actionBar.getVisibility() == View.VISIBLE ? (AndroidUtilities.statusBarHeight + AndroidUtilities.dp(16)) : 0));
    }

    private int getSelectedSliderValue() {
        if (selectedBoostType == BoostTypeCell.TYPE_PREMIUM) {
            return sliderValues.get(selectedSliderIndex);
        } else {
            final List<Integer> values = getSliderValues();
            if (selectedStarsSliderIndex < 0 || selectedStarsSliderIndex >= values.size())
                selectedStarsSliderIndex = 0;
            if (selectedStarsSliderIndex >= values.size())
                return 0;
            return values.get(selectedStarsSliderIndex);
        }
    }

    private int getSelectedSliderValueWithBoosts() {
        if (selectedBoostType == BoostTypeCell.TYPE_PREMIUM) {
            return sliderValues.get(selectedSliderIndex) * BoostRepository.giveawayBoostsPerPremium();
        } else {
            TL_stars.TL_starsGiveawayOption selectedGiveawayOption = getSelectedStarsOption();
            return (selectedGiveawayOption != null ? selectedGiveawayOption.yearly_boosts : getSelectedSliderValue() * BoostRepository.giveawayBoostsPerPremium());
        }
    }

    private long getSelectedPerUserStars() {
        final List<Long> values = getPerUserStarsValues();
        if (selectedStarsSliderIndex < 0 || selectedStarsSliderIndex >= values.size())
            selectedStarsSliderIndex = 0;
        if (selectedStarsSliderIndex >= values.size())
            return 1;
        return values.get(selectedStarsSliderIndex);
    }

    private long getSelectedPerUserStars(long stars) {
        final List<Long> values = getPerUserStarsValues(stars);
        if (values.isEmpty())
            return Math.round((float) stars / getSelectedPerUserStars());
        return values.get(Utilities.clamp(selectedStarsSliderIndex, values.size() - 1, 0));
    }

    public TL_stars.TL_starsGiveawayOption getSelectedStarsOption() {
        return getSelectedStarsOption(selectedStars);
    }

    public TL_stars.TL_starsGiveawayOption getSelectedStarsOption(long stars) {
        final ArrayList<TL_stars.TL_starsGiveawayOption> options = StarsController.getInstance(currentAccount).getGiveawayOptions();
        if (options != null) {
            for (int i = 0; i < options.size(); ++i) {
                final TL_stars.TL_starsGiveawayOption option = options.get(i);
                if (option != null && option.stars == stars) {
                    return option;
                }
            }
        }
        return null;
    }

    private List<Integer> getSliderValues() {
        if (selectedBoostType == BoostTypeCell.TYPE_PREMIUM) {
            return sliderValues;
        }
        final ArrayList<Integer> possibleCounts = new ArrayList<>();
        TL_stars.TL_starsGiveawayOption option = getSelectedStarsOption();
        if (option != null) {
            for (int i = 0; i < option.winners.size(); ++i) {
                final TL_stars.TL_starsGiveawayWinnersOption winnersOption = option.winners.get(i);
                if (option != null && !possibleCounts.contains(winnersOption.users)) {
                    possibleCounts.add(winnersOption.users);
                }
            }
        }
        return possibleCounts;
    }

    private List<Long> getPerUserStarsValues() {
        return getPerUserStarsValues(selectedStars);
    }
    private List<Long> getPerUserStarsValues(long selectedStars) {
        final ArrayList<Integer> possibleCounts = new ArrayList<>();
        final ArrayList<Long> stars = new ArrayList<>();
        TL_stars.TL_starsGiveawayOption option = getSelectedStarsOption(selectedStars);
        if (option != null) {
            for (int i = 0; i < option.winners.size(); ++i) {
                final TL_stars.TL_starsGiveawayWinnersOption winnersOption = option.winners.get(i);
                if (option != null && !possibleCounts.contains(winnersOption.users)) {
                    possibleCounts.add(winnersOption.users);
                    stars.add(winnersOption.per_user_stars);
                }
            }
        }
        return stars;
    }

    private List<Long> getStarsOptions() {
        final ArrayList<TL_stars.TL_starsGiveawayOption> options = StarsController.getInstance(currentAccount).getGiveawayOptions();
        final ArrayList<Long> possibleStars = new ArrayList<>();
        if (options != null) {
            for (int i = 0; i < options.size(); ++i) {
                final TL_stars.TL_starsGiveawayOption option = options.get(i);
                if (option != null && !possibleStars.contains(option.stars)) {
                    possibleStars.add(option.stars);
                }
            }
        }
        return possibleStars;
    }

    private boolean isPreparedGiveaway() {
        return prepaidGiveaway != null;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateRows(boolean animated, boolean notify) {
        ArrayList<Item> oldItems = new ArrayList<>(items);
        items.clear();
        items.add(Item.asHeader(selectedBoostType == BoostTypeCell.TYPE_STARS));
        if (isPreparedGiveaway()) {
            items.add(Item.asSingleBoost(prepaidGiveaway));
        } else {
            items.add(Item.asBoost(BoostTypeCell.TYPE_PREMIUM, selectedUsers.size(), null, selectedBoostType));
            items.add(Item.asBoost(BoostTypeCell.TYPE_STARS, selectedUsers.size(), null, selectedBoostType));
        }
        items.add(Item.asDivider());
        boolean isChannel = ChatObject.isChannelAndNotMegaGroup(currentChat);
        if (selectedBoostType == BoostTypeCell.TYPE_STARS) {
            if (!isPreparedGiveaway()) {
                BoostAdapter.Item header = Item.asSubTitleWithCounter(getString(R.string.BoostingStarsOptions), getSelectedSliderValueWithBoosts());
                items.add(header);
                final List<Long> starOptions = getStarsOptions();
                int optionsCount = 0;
                for (int i = 0; i < starOptions.size(); ++i) {
                    final long stars = starOptions.get(i);
                    TL_stars.TL_starsGiveawayOption option = getSelectedStarsOption(stars);
                    if (option.missingStorePrice) continue;
                    if (selectedStars == 0 && option.isDefault) {
                        selectedStars = option.stars;
                    }
                    if (option.extended && !starOptionsExpanded) continue;
                    optionsCount++;
                    items.add(Item.asOption(option, (starOptionsExpanded ? i : 2 + i), getSelectedPerUserStars(option.stars), selectedStars == option.stars, true));
                }
                if (!starOptionsExpanded && optionsCount < starOptions.size()) {
                    items.add(Item.asExpandOptions());
                }
                if (optionsCount <= 0) {
                    items.add(Item.asOption(null, 0, 1, false, true));
                    items.add(Item.asOption(null, 1, 1, false, true));
                    items.add(Item.asOption(null, 2, 1, false, false));
                }
                header.intValue = getSelectedSliderValueWithBoosts();
                items.add(Item.asDivider(getString(R.string.BoostingStarsOptionsInfo), false));

                final List<Integer> values = getSliderValues();
                if (selectedStarsSliderIndex < 0 || selectedStarsSliderIndex >= values.size()) selectedStarsSliderIndex = 0;
                if (values.size() > 1) {
                    items.add(Item.asSubTitle(getString(R.string.BoostingStarsQuantityPrizes)));
                    items.add(Item.asSlider(getSliderValues(), selectedStarsSliderIndex));
                    items.add(Item.asDivider(getString(R.string.BoostingStarsQuantityPrizesInfo), false));
                }
            } else if (prepaidGiveaway instanceof TL_stories.TL_prepaidStarsGiveaway) {
                selectedStars = ((TL_stories.TL_prepaidStarsGiveaway) prepaidGiveaway).stars;
            }

            items.add(Item.asSubTitle(getString(R.string.BoostingChannelsGroupsIncludedGiveaway)));
            if (isPreparedGiveaway()) {
                if (prepaidGiveaway instanceof TL_stories.TL_prepaidStarsGiveaway) {
                    items.add(Item.asChat(currentChat, false, prepaidGiveaway.quantity));
                } else {
                    items.add(Item.asChat(currentChat, false, prepaidGiveaway.quantity * BoostRepository.giveawayBoostsPerPremium()));
                }
            } else {
                items.add(Item.asChat(currentChat, false, getSelectedSliderValueWithBoosts()));
            }
            for (TLObject selectedChat : selectedChats) {
                if (selectedChat instanceof TLRPC.Chat) {
                    items.add(Item.asChat((TLRPC.Chat) selectedChat, true, getSelectedSliderValueWithBoosts()));
                }
                if (selectedChat instanceof TLRPC.InputPeer) {
                    items.add(Item.asPeer((TLRPC.InputPeer) selectedChat, true, getSelectedSliderValueWithBoosts()));
                }
            }
            if (selectedChats.size() < BoostRepository.giveawayAddPeersMax()) {
                items.add(Item.asAddChannel());
            }
            items.add(Item.asDivider(getString(R.string.BoostingChooseChannelsGroupsNeedToJoin), false));
            items.add(Item.asSubTitle(getString(R.string.BoostingEligibleUsers)));
            items.add(Item.asParticipants(ParticipantsTypeCell.TYPE_ALL, selectedParticipantsType, true, selectedCountries));
            items.add(Item.asParticipants(ParticipantsTypeCell.TYPE_NEW, selectedParticipantsType, false, selectedCountries));
            items.add(Item.asDivider(getString(isChannel ? R.string.BoostingChooseLimitGiveaway : R.string.BoostingChooseLimitGiveawayGroups), false));
        } else {
            if (selectedBoostSubType == BoostTypeCell.TYPE_GIVEAWAY) {
                if (!isPreparedGiveaway()) {
                    items.add(Item.asSubTitleWithCounter(getString(R.string.BoostingQuantityPrizes), getSelectedSliderValueWithBoosts()));
                    items.add(Item.asSlider(sliderValues, selectedSliderIndex));
                    items.add(Item.asDivider(getString(R.string.BoostingChooseHowMany), false));
                }
                items.add(Item.asSubTitle(getString(R.string.BoostingChannelsGroupsIncludedGiveaway)));
                if (isPreparedGiveaway()) {
                    if (prepaidGiveaway instanceof TL_stories.TL_prepaidStarsGiveaway) {
                        items.add(Item.asChat(currentChat, false, prepaidGiveaway.quantity));
                    } else {
                        items.add(Item.asChat(currentChat, false, prepaidGiveaway.quantity * BoostRepository.giveawayBoostsPerPremium()));
                    }
                } else {
                    items.add(Item.asChat(currentChat, false, getSelectedSliderValueWithBoosts()));
                }
                for (TLObject selectedChat : selectedChats) {
                    if (selectedChat instanceof TLRPC.Chat) {
                        items.add(Item.asChat((TLRPC.Chat) selectedChat, true, getSelectedSliderValueWithBoosts()));
                    }
                    if (selectedChat instanceof TLRPC.InputPeer) {
                        items.add(Item.asPeer((TLRPC.InputPeer) selectedChat, true, getSelectedSliderValueWithBoosts()));
                    }
                }
                if (selectedChats.size() < BoostRepository.giveawayAddPeersMax()) {
                    items.add(Item.asAddChannel());
                }
                items.add(Item.asDivider(getString(R.string.BoostingChooseChannelsGroupsNeedToJoin), false));
                items.add(Item.asSubTitle(getString(R.string.BoostingEligibleUsers)));
                items.add(Item.asParticipants(ParticipantsTypeCell.TYPE_ALL, selectedParticipantsType, true, selectedCountries));
                items.add(Item.asParticipants(ParticipantsTypeCell.TYPE_NEW, selectedParticipantsType, false, selectedCountries));
                items.add(Item.asDivider(getString(isChannel ? R.string.BoostingChooseLimitGiveaway : R.string.BoostingChooseLimitGiveawayGroups), false));
            }

            if (!isPreparedGiveaway()) {
                items.add(Item.asSubTitle(getString(R.string.BoostingDurationOfPremium)));
                List<TLRPC.TL_premiumGiftCodeOption> options = BoostRepository.filterGiftOptions(giftCodeOptions, isGiveaway() ? getSelectedSliderValue() : selectedUsers.size());
                for (int i = 0; i < options.size(); i++) {
                    TLRPC.TL_premiumGiftCodeOption option = options.get(i);
                    items.add(Item.asDuration(option, option.months, isGiveaway() ? getSelectedSliderValue() : selectedUsers.size(), option.amount, selectedMonths, option.currency, i != options.size() - 1));
                }
            }

            if (!isPreparedGiveaway()) {
                items.add(Item.asDivider(AndroidUtilities.replaceSingleTag(
                        getString(R.string.BoostingStoriesFeaturesAndTerms),
                        Theme.key_chat_messageLinkIn, 0, () -> {
                            PremiumPreviewBottomSheet previewBottomSheet = new PremiumPreviewBottomSheet(getBaseFragment(), currentAccount, null, resourcesProvider);
                            previewBottomSheet.setOnDismissListener(dialog -> adapter.setPausedStars(false));
                            previewBottomSheet.setOnShowListener(dialog -> adapter.setPausedStars(true));
                            previewBottomSheet.show();
                        },
                        resourcesProvider), true));
            }
        }

        if (selectedBoostType == BoostTypeCell.TYPE_STARS || selectedBoostSubType == BoostTypeCell.TYPE_GIVEAWAY) {
            items.add(Item.asSwitcher(getString(R.string.BoostingGiveawayAdditionalPrizes), isAdditionalPrizeSelected, isAdditionalPrizeSelected, SwitcherCell.TYPE_ADDITION_PRIZE));

            if (isAdditionalPrizeSelected) {
                int quantity = isPreparedGiveaway() ? prepaidGiveaway.quantity : getSelectedSliderValue();
                items.add(Item.asEnterPrize(quantity));
                String months = LocaleController.formatPluralString("BoldMonths", selectedMonths);
                if (selectedBoostType == BoostTypeCell.TYPE_STARS) {
                    if (additionalPrize.isEmpty()) {
                        items.add(Item.asDivider(AndroidUtilities.replaceTags(LocaleController.formatPluralString("BoostingStarsGiveawayAdditionPrizeCountHint", (int) selectedStars)), false));
                    } else {
                        items.add(Item.asDivider(AndroidUtilities.replaceTags(LocaleController.formatPluralString("BoostingStarsGiveawayAdditionPrizeCountNameHint", (int) selectedStars, quantity, additionalPrize)), false));
                    }
                } else {
                    if (additionalPrize.isEmpty()) {
                        items.add(Item.asDivider(AndroidUtilities.replaceTags(LocaleController.formatPluralString("BoostingGiveawayAdditionPrizeCountHint", quantity, months)), false));
                    } else {
                        items.add(Item.asDivider(AndroidUtilities.replaceTags(LocaleController.formatPluralString("BoostingGiveawayAdditionPrizeCountNameHint", quantity, additionalPrize, months)), false));
                    }
                }
            } else {
                items.add(Item.asDivider(getString(selectedBoostType == BoostTypeCell.TYPE_STARS ? R.string.BoostingStarsGiveawayAdditionPrizeHint : R.string.BoostingGiveawayAdditionPrizeHint), false));
            }

            items.add(Item.asSubTitle(getString(R.string.BoostingDateWhenGiveawayEnds)));
            items.add(Item.asDateEnd(selectedEndDate));

            if (selectedBoostType == BoostTypeCell.TYPE_STARS) {
                if (!isPreparedGiveaway()) {
                    items.add(Item.asDivider(LocaleController.formatPluralString(isChannel ? "BoostingStarsChooseRandom" : "BoostingStarsChooseRandomGroup", getSelectedSliderValue(), LocaleController.formatPluralString("BoostingStarsChooseRandomStars", (int) selectedStars)), false));
                } else {
                    items.add(Item.asDivider(LocaleController.formatPluralString(isChannel ? "BoostingStarsChooseRandom" : "BoostingStarsChooseRandomGroup", prepaidGiveaway.quantity, LocaleController.formatPluralString("BoostingStarsChooseRandomStars", (int) selectedStars)), false));
                }
            } else {
                if (!isPreparedGiveaway()) {
                    items.add(Item.asDivider(LocaleController.formatPluralString(isChannel ? "BoostingChooseRandom" : "BoostingChooseRandomGroup", getSelectedSliderValue()), false));
                } else {
                    items.add(Item.asDivider(LocaleController.formatPluralString(isChannel ? "BoostingChooseRandom" : "BoostingChooseRandomGroup", prepaidGiveaway.quantity), false));
                }
            }

            items.add(Item.asSwitcher(getString(R.string.BoostingGiveawayShowWinners), isShowWinnersSelected, false, SwitcherCell.TYPE_WINNERS));

            if (!isPreparedGiveaway()) {
                items.add(Item.asDivider(LocaleController.getString(R.string.BoostingGiveawayShowWinnersHint), false));
            } else {
                items.add(Item.asDivider(AndroidUtilities.replaceSingleTag(
                        LocaleController.getString(R.string.BoostingGiveawayShowWinnersHint) + (selectedBoostType != BoostTypeCell.TYPE_STARS ? "\n\n" + getString(R.string.BoostingStoriesFeaturesAndTerms) : ""),
                        Theme.key_chat_messageLinkIn, 0, () -> {
                            PremiumPreviewBottomSheet previewBottomSheet = new PremiumPreviewBottomSheet(getBaseFragment(), currentAccount, null, resourcesProvider);
                            previewBottomSheet.setOnDismissListener(dialog -> adapter.setPausedStars(false));
                            previewBottomSheet.setOnShowListener(dialog -> adapter.setPausedStars(true));
                            previewBottomSheet.show();
                        },
                        resourcesProvider), true));
            }
        }

        if (adapter == null) {
            return;
        }
        if (!notify) {
            return;
        }
        if (animated) {
            adapter.setItems(oldItems, items);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    protected CharSequence getTitle() {
        return selectedBoostSubType == BoostTypeCell.TYPE_SPECIFIC_USERS ?
                getString(R.string.GiftPremium)
                : LocaleController.formatString("BoostingStartGiveaway", R.string.BoostingStartGiveaway);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new BoostAdapter(resourcesProvider);
    }

    @Override
    public void onChatsSelected(List<TLRPC.Chat> chats, boolean animated) {
        selectedChats.clear();
        selectedChats.addAll(chats);
        updateRows(animated, true);
    }

    @Override
    public void onUsersSelected(List<TLRPC.User> users) {
        selectedUsers.clear();
        selectedUsers.addAll(users);
        if (users.isEmpty()) {
            selectedBoostSubType = BoostTypeCell.TYPE_GIVEAWAY;
        } else {
            selectedBoostSubType = BoostTypeCell.TYPE_SPECIFIC_USERS;
        }
        selectedSliderIndex = 0;
        updateRows(false, true);
        updateActionButton(true);
        updateTitle();
    }

    @Override
    public void onCountrySelected(List<TLRPC.TL_help_country> countries) {
        selectedCountries.clear();
        selectedCountries.addAll(countries);
        updateRows(false, true);
    }
}
