package org.telegram.ui;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.bots.AffiliateProgramFragment.percents;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.SlideIntChooseView;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stars.StarsIntroActivity;

public class PostSuggestionsEditActivity extends BaseFragment {
    private final long currentChatId;

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private static final int done_button = 1;
    private CrossfadeDrawable doneButtonDrawable;
    private ActionBarMenuItem doneButton;

    private final boolean initialSuggestionsEnabled;
    private final long initialSuggestionsStarsCount;
    private boolean isSuggestionsEnabled;
    private long suggestionsStarsCount;

    public PostSuggestionsEditActivity(long chatId) {
        currentChatId = chatId;

        final TLRPC.Chat currentChat = getMessagesController().getChat(currentChatId);
        final TLRPC.Chat monoforumChat;
        if (currentChat != null && currentChat.linked_monoforum_id != 0) {
            monoforumChat = getMessagesController().getChat(currentChat.linked_monoforum_id);
        } else {
            monoforumChat = null;
        }

        final long stars = monoforumChat == null ? 0 : monoforumChat.send_paid_messages_stars;
        initialSuggestionsEnabled = currentChat != null && currentChat.broadcast_messages_allowed;
        initialSuggestionsStarsCount = Utilities.clamp(initialSuggestionsEnabled ? stars : 10, getMessagesController().starsPaidMessageAmountMax, 0);
        isSuggestionsEnabled = initialSuggestionsEnabled;
        suggestionsStarsCount = initialSuggestionsStarsCount;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.PostSuggestions));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackPressed()) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    processDone();
                }
            }
        });

        Drawable checkmark = context.getResources().getDrawable(R.drawable.ic_ab_done).mutate();
        checkmark.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
        doneButtonDrawable = new CrossfadeDrawable(checkmark, new CircularProgressDrawable(Theme.getColor(Theme.key_actionBarDefaultIcon)));
        doneButton = actionBar.createMenu().addItemWithWidth(done_button, doneButtonDrawable, AndroidUtilities.dp(56), LocaleController.getString(R.string.Done));
        checkDone(false);


        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listAdapter = new ListAdapter(context);
        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position == rowSuggestionsEnabled) {
                TextCheckCell cell = (TextCheckCell) view;
                boolean checked = cell.isChecked();

                isSuggestionsEnabled = !checked;

                view.setTag(isSuggestionsEnabled ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked);
                cell.setBackgroundColorAnimated(!checked, Theme.getColor(isSuggestionsEnabled ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
                updateRows();

                if (isSuggestionsEnabled) {
                    listAdapter.notifyItemRangeInserted(rowSuggestionsEnabledInfo + 1, 3);
                } else {
                    listAdapter.notifyItemRangeRemoved(rowSuggestionsEnabledInfo + 1, 3);
                }
                cell.setChecked(!checked);
                checkDone(true);
            }
        });

        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }


    private MessagesStorage.LongCallback starsCallback;
    public PostSuggestionsEditActivity setOnApplied(MessagesStorage.LongCallback stars) {
        this.starsCallback = stars;
        return this;
    }

    private void processDone() {
        if (doneButtonDrawable.getProgress() > 0f) return;
        if (!hasChanges()) {
            finishFragment();
            return;
        }

        doneButtonDrawable.animateToProgress(1f);
        final TL_stars.updatePaidMessagesPrice req = new TL_stars.updatePaidMessagesPrice();
        req.channel = getMessagesController().getInputChannel(currentChatId);
        req.send_paid_messages_stars = isSuggestionsEnabled ? suggestionsStarsCount : 0;
        req.suggestions_allowed = isSuggestionsEnabled;

        getConnectionsManager().sendRequest(req, (response, error) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (error != null) {
                    doneButtonDrawable.animateToProgress(0f);
                    // AlertsCreator.processError(currentAccount, error, PostSuggestionsEditActivity.this, req);
                    BulletinFactory.showError(error);
                    return;
                }

                TLRPC.Updates updates = (TLRPC.Updates) response;
                getMessagesController().putChats(updates.chats, false);
                getMessagesController().processUpdates(updates, false);

                if (!isFinished && !finishing) {
                    if (starsCallback != null) {
                        starsCallback.run(req.suggestions_allowed ? req.send_paid_messages_stars : -1);
                    }
                    finishFragment();
                }
            });
        });

        final TLRPC.Chat chat = getMessagesController().getChat(currentChatId);
        if (chat != null) {
            if (isSuggestionsEnabled) {
                chat.flags2 |= 65536;
                chat.broadcast_messages_allowed = true;
            } else {
                chat.flags2 &=~ 65536;
                chat.broadcast_messages_allowed = false;
            }
            getMessagesController().putChat(chat, true);

            final TLRPC.Chat mfChat = getMessagesController().getChat(chat.linked_monoforum_id);
            if (mfChat != null) {
                if (isSuggestionsEnabled) {
                    mfChat.flags2 |= 16384;
                    mfChat.send_paid_messages_stars = suggestionsStarsCount;
                } else {
                    mfChat.flags2 &=~ 16384;
                    mfChat.send_paid_messages_stars = 0;
                }
                getMessagesController().putChat(mfChat, true);
            }
        }

        if (starsCallback != null) {
            starsCallback.run(isSuggestionsEnabled ? suggestionsStarsCount : -1);
        }
    }

    private boolean hasChanges() {
        return suggestionsStarsCount != initialSuggestionsStarsCount || isSuggestionsEnabled != initialSuggestionsEnabled;
    }

    private boolean lastHasChanges = true;
    private void checkDone(boolean animated) {
        if (doneButton == null) return;

        final boolean hasChanges = hasChanges();
        if (lastHasChanges == hasChanges) {
            return;
        }

        lastHasChanges = hasChanges;
        doneButton.setEnabled(hasChanges);
        if (animated) {
            doneButton.animate().alpha(hasChanges ? 1.0f : 0.0f).scaleX(hasChanges ? 1.0f : 0.0f).scaleY(hasChanges ? 1.0f : 0.0f).setDuration(180).start();
        } else {
            doneButton.setAlpha(hasChanges ? 1.0f : 0.0f);
            doneButton.setScaleX(hasChanges ? 1.0f : 0.0f);
            doneButton.setScaleY(hasChanges ? 1.0f : 0.0f);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (hasChanges()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.UnsavedChanges));
            builder.setMessage(LocaleController.getString(R.string.MessageSuggestionsUnsavedChanges));
            builder.setPositiveButton(LocaleController.getString(R.string.ApplyTheme), (dialogInterface, i) -> processDone());
            builder.setNegativeButton(LocaleController.getString(R.string.Discard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return !hasChanges();
    }



    /* Adapter */

    private int rowCount;
    private int rowSuggestionsEnabled;
    private int rowSuggestionsEnabledInfo;
    private int rowSuggestionPriceHeader;
    private int rowSuggestionPriceSlider;
    private int rowSuggestionPriceInfo;

    private void updateRows() {
        rowCount = 0;
        rowSuggestionsEnabled = rowCount++;
        rowSuggestionsEnabledInfo = rowCount++;

        if (isSuggestionsEnabled) {
            rowSuggestionPriceHeader = rowCount++;
            rowSuggestionPriceSlider = rowCount++;
            rowSuggestionPriceInfo = rowCount++;
        } else {
            rowSuggestionPriceHeader = -1;
            rowSuggestionPriceSlider = -1;
            rowSuggestionPriceInfo = -1;
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TextCheckCell view = (TextCheckCell) holder.itemView;
                    if (position == rowSuggestionsEnabled) {
                        view.setDrawCheckRipple(true);
                        view.setTextAndCheck(LocaleController.getString(R.string.AllowPostSuggestions), isSuggestionsEnabled, false);
                        view.setTag(isSuggestionsEnabled ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked);
                        view.setBackgroundColor(Theme.getColor(isSuggestionsEnabled ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
                    }
                    break;
                }
                case 2: {
                    HeaderCell view = (HeaderCell) holder.itemView;
                    if (position == rowSuggestionPriceHeader) {
                        view.setText(LocaleController.getString(R.string.PriceForEachSuggestion));
                    }
                    break;
                }
                case 3: {
                    SlideIntChooseView cell = (SlideIntChooseView) holder.itemView;
                    if (position == rowSuggestionPriceSlider) {
                        final int[] steps = SlideIntChooseView.cut(new int[]{0, 10, 50, 100, 200, 250, 400, 500, 1000, 2500, 5000, 7500, 9000, 10_000}, (int) getMessagesController().starsPaidMessageAmountMax);
                        final SlideIntChooseView.Options options = SlideIntChooseView.Options.make(1, steps, 20, (type, val) -> {
                            if (type == 0) {
                                return StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatPluralStringComma("Stars", val), 0.66f);
                            }
                            return LocaleController.formatNumber(val, ',');
                        });
                        cell.set((int) Utilities.clamp(suggestionsStarsCount, 10000, 0), options, newValue -> {
                            suggestionsStarsCount = newValue;
                            AndroidUtilities.updateVisibleRow(listView, rowSuggestionPriceInfo);
                            checkDone(true);
                        });
                    }
                    break;
                }
                case 5: {
                    TextInfoPrivacyCell view = (TextInfoPrivacyCell) holder.itemView;
                    if (position == rowSuggestionsEnabledInfo) {
                        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
                        view.setTopPadding(12);
                        view.setBottomPadding(16);
                        view.setText(LocaleController.getString(R.string.AllowPostSuggestionsHint));
                    } else if (position == rowSuggestionPriceInfo) {
                        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
                        view.setTopPadding(12);
                        view.setBottomPadding(16);

                        final float revenuePercent = getMessagesController().starsPaidMessageCommissionPermille / 1000.0f;
                        final String income = String.valueOf((int) ((suggestionsStarsCount * revenuePercent / 1000.0 * getMessagesController().starsUsdWithdrawRate1000)) / 100.0);
                        view.setText(formatString(R.string.PostSuggestionsPriceInfo, percents(850), income));
                    }
                }
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    TextCheckCell cell = new TextCheckCell(mContext);
                    cell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
                    cell.setTypeface(AndroidUtilities.bold());
                    cell.setHeight(56);
                    view = cell;
                    break;
                }
                case 2: {
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
                case 3: {
                    SlideIntChooseView slideView = new SlideIntChooseView(mContext, resourceProvider);
                    slideView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = slideView;
                    break;
                }
                case 5:
                default: {
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                }
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == rowSuggestionsEnabled) {
                return 0;
            } else if (position == rowSuggestionPriceHeader) {
                return 2;
            } else if (position == rowSuggestionPriceSlider) {
                return 3;
            } else {
                return 5;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }
    }
}
