package org.telegram.ui.Components.Premium.boosts;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CubicBezierInterpolator;
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
import org.telegram.ui.Components.Premium.boosts.cells.SwitcherCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Premium.boosts.adapters.BoostAdapter.Item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BoostViaGiftsBottomSheet extends BottomSheetWithRecyclerListView implements SelectorBottomSheet.SelectedObjectsListener {

    private static final int BOTTOM_HEIGHT_DP = 68;

    public interface ActionListener {
        void onAddChat(List<TLObject> chats);

        void onSelectUser(List<TLObject> users);

        void onSelectCountries(List<TLObject> countries);
    }

    private final ArrayList<Item> items = new ArrayList<>();
    private final List<Integer> sliderValues = BoostRepository.isGoogleBillingAvailable() ? Arrays.asList(1, 3, 5, 7, 10, 25, 50) : Arrays.asList(1, 3, 5, 7, 10, 25, 50, 100);
    private final TLRPC.Chat currentChat;
    private final List<TLObject> selectedChats = new ArrayList<>();
    private final List<TLObject> selectedUsers = new ArrayList<>();
    private final List<TLObject> selectedCountries = new ArrayList<>();
    private final List<TLRPC.TL_premiumGiftCodeOption> giftCodeOptions = new ArrayList<>();
    private BoostAdapter adapter;
    private int selectedBoostType = BoostTypeCell.TYPE_GIVEAWAY;
    private int selectedParticipantsType = ParticipantsTypeCell.TYPE_ALL;
    private int selectedMonths = 12;
    private long selectedEndDate = BoostDialogs.getThreeDaysAfterToday();
    private int selectedSliderIndex = 2;
    private ActionBtnCell actionBtn;
    private ActionListener actionListener;
    private int top;
    private Runnable onCloseClick;
    private final TL_stories.TL_prepaidGiveaway prepaidGiveaway;
    private String additionalPrize = "";
    private boolean isAdditionalPrizeSelected;
    private boolean isShowWinnersSelected = true;
    private final Runnable hideKeyboardRunnable = () -> AndroidUtilities.hideKeyboard(recyclerListView);

    public BoostViaGiftsBottomSheet(BaseFragment fragment, boolean needFocus, boolean hasFixedSize, long dialogId, TL_stories.TL_prepaidGiveaway prepaidGiveaway) {
        super(fragment, needFocus, hasFixedSize);
        this.prepaidGiveaway = prepaidGiveaway;
        this.topPadding = 0.3f;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        useBackgroundTopPadding = false;
        backgroundPaddingLeft = 0;
        updateTitle();
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        ((ViewGroup.MarginLayoutParams) actionBar.getLayoutParams()).leftMargin = 0;
        ((ViewGroup.MarginLayoutParams) actionBar.getLayoutParams()).rightMargin = 0;

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, AndroidUtilities.dp(BOTTOM_HEIGHT_DP));
        recyclerListView.setItemAnimator(itemAnimator);
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
                    if (boostType == BoostTypeCell.TYPE_SPECIFIC_USERS) {
                        if (actionListener != null) {
                            actionListener.onSelectUser(selectedUsers);
                        }
                    } else {
                        ((BaseCell) view).markChecked(recyclerListView);
                        selectedBoostType = boostType;
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
            }
        });
        this.currentChat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        this.adapter.setItems(items, recyclerListView, sliderIndex -> {
            selectedSliderIndex = sliderIndex;
            actionBtn.updateCounter(getSelectedSliderValueWithBoosts());
            updateRows(false, false);
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

            if (selectedBoostType == BoostTypeCell.TYPE_SPECIFIC_USERS) {
                List<TLRPC.TL_premiumGiftCodeOption> options = BoostRepository.filterGiftOptions(giftCodeOptions, selectedUsers.size());
                for (int i = 0; i < options.size(); i++) {
                    TLRPC.TL_premiumGiftCodeOption option = options.get(i);
                    if (option.months == selectedMonths && selectedUsers.size() > 0) {
                        if (BoostRepository.isGoogleBillingAvailable() && BoostDialogs.checkReduceUsers(getContext(), resourcesProvider, giftCodeOptions, option)) {
                            return;
                        }
                        actionBtn.updateLoading(true);
                        BoostRepository.payGiftCode(selectedUsers, option, currentChat, fragment, result -> {
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
                if (isPreparedGiveaway()) {
                    BoostDialogs.showStartGiveawayDialog(() -> {
                        int dateInt = BoostRepository.prepareServerDate(selectedEndDate);
                        boolean onlyNewSubscribers = selectedParticipantsType == ParticipantsTypeCell.TYPE_NEW;
                        actionBtn.updateLoading(true);
                        BoostRepository.launchPreparedGiveaway(prepaidGiveaway, selectedChats, selectedCountries, currentChat, dateInt, onlyNewSubscribers, isShowWinnersSelected, isAdditionalPrizeSelected, additionalPrize,
                                result -> {
                                    dismiss();
                                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.boostByChannelCreated, currentChat, true, prepaidGiveaway), 220);
                                }, error -> {
                                    actionBtn.updateLoading(false);
                                    BoostDialogs.showToastError(getContext(), error);
                                });
                    });
                } else {
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
            }
        });
        updateActionButton(false);
        containerView.addView(actionBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, BOTTOM_HEIGHT_DP, Gravity.BOTTOM, 0, 0, 0, 0));
        loadOptions();
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
    }

    private void loadOptions() {
        BoostRepository.loadGiftOptions(currentChat, arg -> {
            giftCodeOptions.clear();
            giftCodeOptions.addAll(arg);
            updateRows(true, true);
        });
    }

    private void updateActionButton(boolean animated) {
        if (isPreparedGiveaway()) {
            actionBtn.setStartGiveAwayStyle(prepaidGiveaway.quantity * BoostRepository.giveawayBoostsPerPremium(), animated);
        } else {
            if (selectedBoostType == BoostTypeCell.TYPE_GIVEAWAY) {
                actionBtn.setStartGiveAwayStyle(getSelectedSliderValueWithBoosts(), animated);
            } else {
                actionBtn.setGiftPremiumStyle(selectedUsers.size() * BoostRepository.giveawayBoostsPerPremium(), animated, selectedUsers.size() > 0);
            }
        }
    }

    private boolean isGiveaway() {
        return selectedBoostType == BoostTypeCell.TYPE_GIVEAWAY;
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
        return sliderValues.get(selectedSliderIndex);
    }

    private int getSelectedSliderValueWithBoosts() {
        return sliderValues.get(selectedSliderIndex) * BoostRepository.giveawayBoostsPerPremium();
    }

    private boolean isPreparedGiveaway() {
        return prepaidGiveaway != null;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateRows(boolean animated, boolean notify) {
        ArrayList<Item> oldItems = new ArrayList<>(items);
        items.clear();
        items.add(Item.asHeader());
        if (isPreparedGiveaway()) {
            items.add(Item.asSingleBoost(prepaidGiveaway));
        } else {
            items.add(Item.asBoost(BoostTypeCell.TYPE_GIVEAWAY, selectedUsers.size(), null, selectedBoostType));
            items.add(Item.asBoost(BoostTypeCell.TYPE_SPECIFIC_USERS, selectedUsers.size(), selectedUsers.size() > 0 ? selectedUsers.get(0) : null, selectedBoostType));
        }
        items.add(Item.asDivider());
        if (selectedBoostType == BoostTypeCell.TYPE_GIVEAWAY) {
            if (!isPreparedGiveaway()) {
                items.add(Item.asSubTitleWithCounter(LocaleController.getString("BoostingQuantityPrizes", R.string.BoostingQuantityPrizes), getSelectedSliderValueWithBoosts()));
                items.add(Item.asSlider(sliderValues, selectedSliderIndex));
                items.add(Item.asDivider(LocaleController.getString("BoostingChooseHowMany", R.string.BoostingChooseHowMany), false));
            }
            items.add(Item.asSubTitle(LocaleController.getString("BoostingChannelsIncludedGiveaway", R.string.BoostingChannelsIncludedGiveaway)));
            if (isPreparedGiveaway()) {
                items.add(Item.asChat(currentChat, false, prepaidGiveaway.quantity * BoostRepository.giveawayBoostsPerPremium()));
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
            items.add(Item.asDivider(LocaleController.getString("BoostingChooseChannelsNeedToJoin", R.string.BoostingChooseChannelsNeedToJoin), false));
            items.add(Item.asSubTitle(LocaleController.getString("BoostingEligibleUsers", R.string.BoostingEligibleUsers)));
            items.add(Item.asParticipants(ParticipantsTypeCell.TYPE_ALL, selectedParticipantsType, true, selectedCountries));
            items.add(Item.asParticipants(ParticipantsTypeCell.TYPE_NEW, selectedParticipantsType, false, selectedCountries));
            items.add(Item.asDivider(LocaleController.getString("BoostingChooseLimitGiveaway", R.string.BoostingChooseLimitGiveaway), false));
        }

        if (!isPreparedGiveaway()) {
            items.add(Item.asSubTitle(LocaleController.getString("BoostingDurationOfPremium", R.string.BoostingDurationOfPremium)));
            List<TLRPC.TL_premiumGiftCodeOption> options = BoostRepository.filterGiftOptions(giftCodeOptions, isGiveaway() ? getSelectedSliderValue() : selectedUsers.size());
            for (int i = 0; i < options.size(); i++) {
                TLRPC.TL_premiumGiftCodeOption option = options.get(i);
                items.add(Item.asDuration(option, option.months, isGiveaway() ? getSelectedSliderValue() : selectedUsers.size(), option.amount, selectedMonths, option.currency, i != options.size() - 1));
            }
        }

        if (!isPreparedGiveaway()) {
            items.add(Item.asDivider(AndroidUtilities.replaceSingleTag(
                    LocaleController.getString("BoostingStoriesFeaturesAndTerms", R.string.BoostingStoriesFeaturesAndTerms),
                    Theme.key_chat_messageLinkIn, 0, () -> {
                        PremiumPreviewBottomSheet previewBottomSheet = new PremiumPreviewBottomSheet(getBaseFragment(), currentAccount, null, resourcesProvider);
                        previewBottomSheet.setOnDismissListener(dialog -> adapter.setPausedStars(false));
                        previewBottomSheet.setOnShowListener(dialog -> adapter.setPausedStars(true));
                        previewBottomSheet.show();
                    },
                    resourcesProvider), true));
        }

        if (selectedBoostType == BoostTypeCell.TYPE_GIVEAWAY) {
            items.add(Item.asSwitcher(LocaleController.getString("BoostingGiveawayAdditionalPrizes", R.string.BoostingGiveawayAdditionalPrizes), isAdditionalPrizeSelected, isAdditionalPrizeSelected, SwitcherCell.TYPE_ADDITION_PRIZE));

            if (isAdditionalPrizeSelected) {
                int quantity = isPreparedGiveaway() ? prepaidGiveaway.quantity : getSelectedSliderValue();
                items.add(Item.asEnterPrize(quantity));
                String months = LocaleController.formatPluralString("BoldMonths", selectedMonths);
                if (additionalPrize.isEmpty()) {
                    items.add(Item.asDivider(AndroidUtilities.replaceTags(LocaleController.formatPluralString("BoostingGiveawayAdditionPrizeCountHint", quantity, months)), false));
                } else {
                    items.add(Item.asDivider(AndroidUtilities.replaceTags(LocaleController.formatPluralString("BoostingGiveawayAdditionPrizeCountNameHint", quantity, additionalPrize, months)), false));
                }
            } else {
                items.add(Item.asDivider(LocaleController.getString("BoostingGiveawayAdditionPrizeHint", R.string.BoostingGiveawayAdditionPrizeHint), false));
            }

            items.add(Item.asSwitcher(LocaleController.getString("BoostingGiveawayShowWinners", R.string.BoostingGiveawayShowWinners), isShowWinnersSelected, false, SwitcherCell.TYPE_WINNERS));
            items.add(Item.asDivider(LocaleController.getString("BoostingGiveawayShowWinnersHint", R.string.BoostingGiveawayShowWinnersHint), false));

            items.add(Item.asSubTitle(LocaleController.getString("BoostingDateWhenGiveawayEnds", R.string.BoostingDateWhenGiveawayEnds)));
            items.add(Item.asDateEnd(selectedEndDate));

            if (!isPreparedGiveaway()) {
                items.add(Item.asDivider(LocaleController.formatPluralString("BoostingChooseRandom", getSelectedSliderValue()), false));
            } else {
                items.add(Item.asDivider(AndroidUtilities.replaceSingleTag(
                        LocaleController.formatPluralString("BoostingChooseRandom", prepaidGiveaway.quantity) + "\n\n" + LocaleController.getString("BoostingStoriesFeaturesAndTerms", R.string.BoostingStoriesFeaturesAndTerms),
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
        return selectedBoostType == BoostTypeCell.TYPE_SPECIFIC_USERS ?
                LocaleController.getString("GiftPremium", R.string.GiftPremium)
                : LocaleController.formatString("BoostingStartGiveaway", R.string.BoostingStartGiveaway);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter() {
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
            selectedBoostType = BoostTypeCell.TYPE_GIVEAWAY;
        } else {
            selectedBoostType = BoostTypeCell.TYPE_SPECIFIC_USERS;
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
