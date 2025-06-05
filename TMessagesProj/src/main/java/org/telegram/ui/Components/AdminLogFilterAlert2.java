package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorBtnCell;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public class AdminLogFilterAlert2 extends BottomSheetWithRecyclerListView {

    private UniversalAdapter adapter;

    private TLRPC.TL_channelAdminLogEventsFilter currentFilter = new TLRPC.TL_channelAdminLogEventsFilter();
    private ArrayList<TLRPC.ChannelParticipant> currentAdmins;
    private LongSparseArray<TLRPC.User> selectedAdmins;
    private boolean isMegagroup;


    private final ButtonWithCounterView actionButton;
    private final SelectorBtnCell buttonContainer;

    public AdminLogFilterAlert2(BaseFragment fragment, TLRPC.TL_channelAdminLogEventsFilter filter, LongSparseArray<TLRPC.User> admins, boolean megagroup) {
        super(fragment.getContext(), fragment, false, false, false, true, ActionBarType.SLIDING, fragment.getResourceProvider());
        topPadding = 0.35f;
        fixNavigationBar();
        setSlidingActionBar();
        setShowHandle(true);

        if (filter != null) {
            currentFilter.join = filter.join;
            currentFilter.leave = filter.leave;
            currentFilter.invite = filter.invite;
            currentFilter.ban = filter.ban;
            currentFilter.unban = filter.unban;
            currentFilter.kick = filter.kick;
            currentFilter.unkick = filter.unkick;
            currentFilter.promote = filter.promote;
            currentFilter.demote = filter.demote;
            currentFilter.info = filter.info;
            currentFilter.settings = filter.settings;
            currentFilter.pinned = filter.pinned;
            currentFilter.edit = filter.edit;
            currentFilter.delete = filter.delete;
            currentFilter.group_call = filter.group_call;
            currentFilter.invites = filter.invites;
        } else {
            currentFilter.join = true;
            currentFilter.leave = true;
            currentFilter.invite = true;
            currentFilter.ban = true;
            currentFilter.unban = true;
            currentFilter.kick = true;
            currentFilter.unkick = true;
            currentFilter.promote = true;
            currentFilter.demote = true;
            currentFilter.info = true;
            currentFilter.settings = true;
            currentFilter.pinned = true;
            currentFilter.edit = true;
            currentFilter.delete = true;
            currentFilter.group_call = true;
            currentFilter.invites = true;
        }
        if (admins != null) {
            selectedAdmins = admins.clone();
        }
        isMegagroup = megagroup;
        adapter.update(false);

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.setOnItemClickListener((view, position, x, y) -> {
            onClick(adapter.getItem(position - 1), view, x);
        });

        buttonContainer = new SelectorBtnCell(getContext(), resourcesProvider, null);
        buttonContainer.setClickable(true);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        buttonContainer.setPadding(dp(10), dp(10), dp(10), dp(10));
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        actionButton = new ButtonWithCounterView(getContext(), resourcesProvider);
        actionButton.setText(getString(R.string.EventLogFilterApply), false);
        actionButton.setOnClickListener(v -> {
            if (currentFilter.join &&
                currentFilter.leave &&
                currentFilter.invite &&
                currentFilter.ban &&
                currentFilter.unban &&
                currentFilter.kick &&
                currentFilter.unkick &&
                currentFilter.promote &&
                currentFilter.demote &&
                currentFilter.info &&
                currentFilter.settings &&
                currentFilter.pinned &&
                currentFilter.edit &&
                currentFilter.delete &&
                currentFilter.group_call &&
                currentFilter.invites) {
                currentFilter = null;
            }
            if (selectedAdmins != null && currentAdmins != null && selectedAdmins.size() >= currentAdmins.size()) {
                selectedAdmins = null;
            }
            delegate.didSelectRights(currentFilter, selectedAdmins);
            dismiss();
        });
        buttonContainer.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        containerView.addView(buttonContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, backgroundPaddingLeft, 0, backgroundPaddingLeft, 0));

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(68));
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.EventLog);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
    }

    private final static int FILTER_SECTION_MEMBERS = 2;
    private final static int FILTER_NEW_ADMINS = 3;
    private final static int FILTER_RESTRICTIONS = 4;
    private final static int FILTER_NEW_MEMBERS = 5;
    private final static int FILTER_MEMBERS_LEFT = 6;
    private final static int FILTER_SECTION_SETTINGS = 7;
    private final static int FILTER_INFO = 8;
    private final static int FILTER_INVITES = 9;
    private final static int FILTER_CALLS = 10;
    private final static int FILTER_SECTION_MESSAGES = 11;
    private final static int FILTER_DELETE = 12;
    private final static int FILTER_EDIT = 13;
    private final static int FILTER_PIN = 14;
    private final static int BUTTON_ALL_ADMINS = 15;

    private boolean sectionMembersExpanded = false;
    private boolean sectionSettingsExpanded = false;
    private boolean sectionMessagesExpanded = false;

    private String getGroupCount(int a) {
        switch (a) {
            case 0: return (
                (currentFilter.promote || currentFilter.demote ? 1 : 0) +
                (isMegagroup && (currentFilter.kick || currentFilter.ban || currentFilter.unkick || currentFilter.unban) ? 1 : 0) +
                (currentFilter.invite || currentFilter.join ? 1 : 0) +
                (currentFilter.leave ? 1 : 0) +
                "/" + (isMegagroup ? 4 : 3)
            );
            case 1: return (
                (currentFilter.info || currentFilter.settings ? 1 : 0) +
                (currentFilter.invites ? 1 : 0) +
                (currentFilter.group_call ? 1 : 0) +
                "/3"
            );
            case 2:
            default: return (
                (currentFilter.delete ? 1 : 0) +
                (currentFilter.edit ? 1 : 0) +
                (currentFilter.pinned ? 1 : 0) +
                "/3"
            );
        }
    }

    private View.OnClickListener getGroupClick(int a) {
        return v -> {
//            saveScrollPosition();
            switch (a) {
                case 0:
                    sectionMembersExpanded = !sectionMembersExpanded;
                    break;
                case 1:
                    sectionSettingsExpanded = !sectionSettingsExpanded;
                    break;
                case 2:
                    sectionMessagesExpanded = !sectionMessagesExpanded;
                    break;
            }
            adapter.update(true);
            applyScrolledPosition();
        };
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (currentFilter == null) return;
        items.add(UItem.asHeader(getString(R.string.EventLogFilterByActions)));
        items.add(UItem.asRoundGroupCheckbox(FILTER_SECTION_MEMBERS, getString(isMegagroup ? R.string.EventLogFilterSectionMembers : R.string.EventLogFilterSectionSubscribers), getGroupCount(0)).setChecked(
            currentFilter.promote || currentFilter.demote ||
            isMegagroup && (currentFilter.kick || currentFilter.ban || currentFilter.unkick || currentFilter.unban) ||
            currentFilter.invite || currentFilter.join ||
            currentFilter.leave
        ).setCollapsed(!sectionMembersExpanded).setClickCallback(getGroupClick(0)));
        if (sectionMembersExpanded) {
            items.add(UItem.asRoundCheckbox(FILTER_NEW_ADMINS, getString(R.string.EventLogFilterSectionAdmin)).pad().setChecked(currentFilter.promote || currentFilter.demote));
            if (isMegagroup) {
                items.add(UItem.asRoundCheckbox(FILTER_RESTRICTIONS, getString(R.string.EventLogFilterNewRestrictions)).pad().setChecked(currentFilter.kick || currentFilter.ban || currentFilter.unkick || currentFilter.unban));
            }
            items.add(UItem.asRoundCheckbox(FILTER_NEW_MEMBERS, getString(isMegagroup ? R.string.EventLogFilterNewMembers : R.string.EventLogFilterNewSubscribers)).pad().setChecked(currentFilter.invite || currentFilter.join));
            items.add(UItem.asRoundCheckbox(FILTER_MEMBERS_LEFT, getString(isMegagroup ? R.string.EventLogFilterLeavingMembers2 : R.string.EventLogFilterLeavingSubscribers2)).pad().setChecked(currentFilter.leave));
        }
        items.add(UItem.asRoundGroupCheckbox(FILTER_SECTION_SETTINGS, getString(isMegagroup ? R.string.EventLogFilterSectionGroupSettings : R.string.EventLogFilterSectionChannelSettings), getGroupCount(1)).setChecked(
            currentFilter.info || currentFilter.settings ||
            currentFilter.invites ||
            currentFilter.group_call
        ).setCollapsed(!sectionSettingsExpanded).setClickCallback(getGroupClick(1)));
        if (sectionSettingsExpanded) {
            items.add(UItem.asRoundCheckbox(FILTER_INFO, getString(isMegagroup ? R.string.EventLogFilterGroupInfo : R.string.EventLogFilterChannelInfo)).pad().setChecked(currentFilter.info || currentFilter.settings));
            items.add(UItem.asRoundCheckbox(FILTER_INVITES, getString(R.string.EventLogFilterInvites)).pad().setChecked(currentFilter.invites));
            items.add(UItem.asRoundCheckbox(FILTER_CALLS, getString(R.string.EventLogFilterCalls)).pad().setChecked(currentFilter.group_call));
        }
        items.add(UItem.asRoundGroupCheckbox(FILTER_SECTION_MESSAGES, getString(R.string.EventLogFilterSectionMessages), getGroupCount(2)).setChecked(
            currentFilter.delete || currentFilter.edit || currentFilter.pinned
        ).setCollapsed(!sectionMessagesExpanded).setClickCallback(getGroupClick(2)));
        if (sectionMessagesExpanded) {
            items.add(UItem.asRoundCheckbox(FILTER_DELETE, getString(R.string.EventLogFilterDeletedMessages)).pad().setChecked(currentFilter.delete));
            items.add(UItem.asRoundCheckbox(FILTER_EDIT, getString(R.string.EventLogFilterEditedMessages)).pad().setChecked(currentFilter.edit));
            items.add(UItem.asRoundCheckbox(FILTER_PIN, getString(R.string.EventLogFilterPinnedMessages)).pad().setChecked(currentFilter.pinned));
        }
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.EventLogFilterByAdmins)));
        items.add(UItem.asRoundCheckbox(BUTTON_ALL_ADMINS, getString(R.string.EventLogFilterByAdminsAll)).setChecked((selectedAdmins == null ? 0 : selectedAdmins.size()) >= (currentAdmins == null ? 0 : currentAdmins.size())));
        if (currentAdmins != null) {
            for (int i = 0; i < currentAdmins.size(); ++i) {
                TLRPC.ChannelParticipant admin = currentAdmins.get(i);
                final long did = DialogObject.getPeerDialogId(admin.peer);
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                items.add(UItem.asUserCheckbox(-1 - i, user).pad().setChecked(selectedAdmins != null && selectedAdmins.containsKey(did)));
            }
        }
    }

    public void onClick(UItem item, View view, float x) {
        if (item == null) return;
        if (item.viewType == UniversalAdapter.VIEW_TYPE_ROUND_GROUP_CHECKBOX || item.viewType == UniversalAdapter.VIEW_TYPE_ROUND_CHECKBOX) {
//            saveScrollPosition();
            final boolean clickedGroupExpand = item.viewType == UniversalAdapter.VIEW_TYPE_ROUND_GROUP_CHECKBOX && (LocaleController.isRTL ? x < view.getMeasuredWidth() - dp(60) : x > dp(60));
            CheckBoxCell cell = (CheckBoxCell) view;
            if (!clickedGroupExpand) {
                cell.setChecked(!cell.isChecked(), true);
            }
            switch (item.id) {
                case FILTER_SECTION_MEMBERS:
                    if (clickedGroupExpand) {
                        sectionMembersExpanded = !sectionMembersExpanded;
                    } else {
                        currentFilter.promote = currentFilter.demote = currentFilter.invite = currentFilter.join = currentFilter.leave = cell.isChecked();
                        if (isMegagroup) {
                            currentFilter.kick = currentFilter.ban = currentFilter.unkick = currentFilter.unban = cell.isChecked();
                        }
                    }
                    break;
                case FILTER_NEW_ADMINS:
                    currentFilter.promote = currentFilter.demote = cell.isChecked();
                    break;
                case FILTER_RESTRICTIONS:
                    currentFilter.kick = currentFilter.ban = currentFilter.unkick = currentFilter.unban = cell.isChecked();
                    break;
                case FILTER_NEW_MEMBERS:
                    currentFilter.invite = currentFilter.join = cell.isChecked();
                    break;
                case FILTER_MEMBERS_LEFT:
                    currentFilter.leave = cell.isChecked();
                    break;
                case FILTER_SECTION_SETTINGS:
                    if (clickedGroupExpand) {
                        sectionSettingsExpanded = !sectionSettingsExpanded;
                    } else {
                        currentFilter.info = currentFilter.settings = currentFilter.invites = currentFilter.group_call = cell.isChecked();
                    }
                    break;
                case FILTER_INFO:
                    currentFilter.info = currentFilter.settings = cell.isChecked();
                    break;
                case FILTER_INVITES:
                    currentFilter.invites = cell.isChecked();
                    break;
                case FILTER_CALLS:
                    currentFilter.group_call = cell.isChecked();
                    break;
                case FILTER_SECTION_MESSAGES:
                    if (clickedGroupExpand) {
                        sectionMessagesExpanded = !sectionMessagesExpanded;
                    } else {
                        currentFilter.delete = currentFilter.edit = currentFilter.pinned = cell.isChecked();
                    }
                    break;
                case FILTER_DELETE:
                    currentFilter.delete = cell.isChecked();
                    break;
                case FILTER_EDIT:
                    currentFilter.edit = cell.isChecked();
                    break;
                case FILTER_PIN:
                    currentFilter.pinned = cell.isChecked();
                    break;
                case BUTTON_ALL_ADMINS:
                    if (selectedAdmins == null) {
                        selectedAdmins = new LongSparseArray<>();
                    }
                    selectedAdmins.clear();
                    if (cell.isChecked() && currentAdmins != null) {
                        for (TLRPC.ChannelParticipant admin : currentAdmins) {
                            final long did = DialogObject.getPeerDialogId(admin.peer);
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                            selectedAdmins.put(did, user);
                        }
                    }
                    break;
            }
            adapter.update(true);
        }
        if (item.id < 0) {
            CheckBoxCell cell = (CheckBoxCell) view;
            int index = (-item.id) - 1;
            if (index < 0 || index >= currentAdmins.size()) return;
            TLRPC.ChannelParticipant admin = currentAdmins.get(index);
            final long did = DialogObject.getPeerDialogId(admin.peer);
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
            if (selectedAdmins == null) {
                selectedAdmins = new LongSparseArray<>();
            }
            if (selectedAdmins.containsKey(did)) {
                selectedAdmins.remove(did);
                cell.setChecked(false, true);
            } else {
                selectedAdmins.put(did, user);
                cell.setChecked(true, true);
            }
            adapter.update(true);
        }
    }

    public void setCurrentAdmins(ArrayList<TLRPC.ChannelParticipant> admins) {
        currentAdmins = admins;
        if (currentAdmins != null && selectedAdmins == null) {
            selectedAdmins = new LongSparseArray<>();
            for (TLRPC.ChannelParticipant admin : currentAdmins) {
                final long did = DialogObject.getPeerDialogId(admin.peer);
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                selectedAdmins.put(did, user);
            }
        }
        if (adapter != null) {
            adapter.update(true);
        }
    }

    private AdminLogFilterAlertDelegate delegate;

    public interface AdminLogFilterAlertDelegate {
        void didSelectRights(
            TLRPC.TL_channelAdminLogEventsFilter filter,
            LongSparseArray<TLRPC.User> admins
        );
    }

    public void setAdminLogFilterAlertDelegate(AdminLogFilterAlertDelegate adminLogFilterAlertDelegate) {
        delegate = adminLogFilterAlertDelegate;
    }

    @Override
    protected void onSmoothContainerViewLayout(float ty) {
        super.onSmoothContainerViewLayout(ty);
        buttonContainer.setTranslationY(-ty);
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return !recyclerListView.canScrollVertically(-1);
    }
}
