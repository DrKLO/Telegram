package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
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
import org.telegram.messenger.ChatObject;
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
import org.telegram.ui.Components.LinkActionView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Stars.StarsIntroActivity;

import java.util.ArrayList;

public class PostSuggestionsEditActivity extends BaseFragment {
    private final long currentChatId;

    private SlideIntChooseView slideView;
    private LinkActionView linkView;
    private UniversalRecyclerView listView;

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
        initialSuggestionsStarsCount = Utilities.clamp(initialSuggestionsEnabled ? stars : getMessagesController().config.starsPaidMessagesChannelAmountDefault.get(), getMessagesController().starsPaidMessageAmountMax, 0);
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
                    if (onBackPressed(true)) {
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
        doneButton = actionBar.createMenu().addItemWithWidth(done_button, doneButtonDrawable, dp(56), LocaleController.getString(R.string.Done));
        checkDone(false);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        slideView = new SlideIntChooseView(context, resourceProvider);
        slideView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        linkView = new LinkActionView(context, this, null, currentChatId, true, true);
        linkView.setPadding(dp(16), dp(12), dp(16), 0);
        linkView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        linkView.hideRevokeOption(true);
        linkView.setUsers(0, null);

        listView = new UniversalRecyclerView(context, currentAccount, classGuid, this::fillItems, this::onItemClick, null, resourceProvider);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView(getString(R.string.AllowPostSuggestionsHint2), R.raw.bubble));
        items.add(UItem.asCheck(1, getString(R.string.AllowPostSuggestions)).setChecked(isSuggestionsEnabled));
        items.add(UItem.asShadow(2, null));

        if (isSuggestionsEnabled) {
            items.add(UItem.asHeader(getString(R.string.PriceForEachSuggestion)));
            final int[] steps = SlideIntChooseView.cut(new int[]{ 0, 10, 50, 100, 200, 250, 400, 500, 1000, 2500, 5000, 7500, 9000, 10_000 }, (int) getMessagesController().starsPaidMessageAmountMax);
            final SlideIntChooseView.Options options = SlideIntChooseView.Options.make(1, steps, 20, (type, val) -> {
                if (type == 0) {
                    return StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatPluralStringComma("Stars", val), 0.66f);
                }
                return LocaleController.formatNumber(val, ',');
            });
            slideView.set((int) Utilities.clamp(suggestionsStarsCount, 10000, 0), options, newValue -> {
                suggestionsStarsCount = newValue;
                final View view = listView.findViewByItemId(4);
                if (view instanceof TextInfoPrivacyCell && ((TextInfoPrivacyCell) view).getFixedSize() <= 0 && suggestionsStarsCount > 0) {
                    ((TextInfoPrivacyCell) view).setText(getIncomeInfo());
                } else {
                    listView.adapter.update(true);
                }
                checkDone(true);
            });
            items.add(UItem.asCustom(3, slideView));
            items.add(UItem.asShadow(4, suggestionsStarsCount > 0 ? getIncomeInfo() : null));

            final TLRPC.Chat chat = getMessagesController().getChat(currentChatId);
            if (chat != null && !TextUtils.isEmpty(ChatObject.getPublicUsername(chat))) {
                linkView.setLink(
                    getMessagesController().linkPrefix + "/" + ChatObject.getPublicUsername(chat) + "?direct"
                );
                items.add(UItem.asHeader(getString(R.string.ChannelLinkDirectMessages)));
                items.add(UItem.asCustom(5, linkView));
//                items.add(UItem.asShadow(6, null));
            }
        }
    }

    private CharSequence getIncomeInfo() {
        final int percent = getMessagesController().starsPaidMessageCommissionPermille;
        final float revenuePercent = percent / 1000.0f;
        final String income = String.valueOf((int) ((suggestionsStarsCount * revenuePercent / 1000.0 * getMessagesController().starsUsdWithdrawRate1000)) / 100.0);
        return formatString(R.string.PostSuggestionsPriceInfo2, percents(percent), income);
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            TextCheckCell cell = (TextCheckCell) view;
            cell.setChecked(isSuggestionsEnabled = !cell.isChecked());

            listView.adapter.update(true);
            checkDone(true);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
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
    public boolean onBackPressed(boolean invoked) {
        if (hasChanges()) {
            if (invoked) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString(R.string.UnsavedChanges));
                builder.setMessage(LocaleController.getString(R.string.MessageSuggestionsUnsavedChanges));
                builder.setPositiveButton(LocaleController.getString(R.string.ApplyTheme), (dialogInterface, i) -> processDone());
                builder.setNegativeButton(LocaleController.getString(R.string.Discard), (dialog, which) -> finishFragment());
                showDialog(builder.create());
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return !hasChanges();
    }
}
