package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.Premium.PremiumPreviewBottomSheet;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Objects;

public class ArchiveSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private ListAdapter adapter;

    private boolean changed = false;
    private TLRPC.TL_globalPrivacySettings settings;
    
    private int shiftDp = -3;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("ArchiveSettings"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutAnimation(null);
        listView.setAdapter(adapter = new ListAdapter());
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            ItemInner item = items.get(position);
            if (item.id == 1) {
                settings.keep_archived_unmuted = !settings.keep_archived_unmuted;
                ((TextCheckCell) view).setChecked(settings.keep_archived_unmuted);
                changed = true;
            } else if (item.id == 4) {
                settings.keep_archived_folders = !settings.keep_archived_folders;
                ((TextCheckCell) view).setChecked(settings.keep_archived_folders);
                changed = true;
            } else if (item.id == 7) {
                if (!getUserConfig().isPremium() && !getMessagesController().autoarchiveAvailable && !settings.archive_and_mute_new_noncontact_peers) {
                    final Bulletin.SimpleLayout layout = new Bulletin.SimpleLayout(getContext(), getResourceProvider());
                    layout.textView.setText(AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.UnlockPremium), Theme.key_undo_cancelColor, 0, () -> {
                        presentFragment(new PremiumPreviewFragment("settings"));
                    }));
                    layout.textView.setSingleLine(false);
                    layout.textView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
                    layout.imageView.setImageResource(R.drawable.msg_settings_premium);
                    Bulletin.make(this, layout, 3500).show();

                    AndroidUtilities.shakeViewSpring(view, shiftDp = -shiftDp);
                    BotWebViewVibrationEffect.APP_ERROR.vibrate();
                    return;
                }
                settings.archive_and_mute_new_noncontact_peers = !settings.archive_and_mute_new_noncontact_peers;
                ((TextCheckCell) view).setChecked(settings.archive_and_mute_new_noncontact_peers);
                changed = true;
            }
        });

        getContactsController().loadGlobalPrivacySetting();
        settings = getContactsController().getGlobalPrivacySettings();
        if (settings == null) {
            settings = new TLRPC.TL_globalPrivacySettings();
        }
        updateItems(false);

        return fragmentView;
    }

    private final ArrayList<ItemInner> oldItems = new ArrayList<>(), items = new ArrayList<>();

    private void updateItems(boolean animated) {
        oldItems.clear();
        oldItems.addAll(items);
        items.clear();

        items.add(new ItemInner(VIEW_TYPE_HEADER, 0, LocaleController.getString("ArchiveSettingUnmutedFolders")));
        items.add(new ItemInner(VIEW_TYPE_CHECK, 1, LocaleController.getString("ArchiveSettingUnmutedFoldersCheck")));
        items.add(new ItemInner(VIEW_TYPE_SHADOW, 2, LocaleController.getString("ArchiveSettingUnmutedFoldersInfo")));

        final boolean hasFolders = getMessagesController().getDialogFilters().size() > 1;
        if (hasFolders) {
            items.add(new ItemInner(VIEW_TYPE_HEADER, 3, LocaleController.getString("ArchiveSettingUnmutedChats")));
            items.add(new ItemInner(VIEW_TYPE_CHECK, 4, LocaleController.getString("ArchiveSettingUnmutedChatsCheck")));
            items.add(new ItemInner(VIEW_TYPE_SHADOW, 5, LocaleController.getString("ArchiveSettingUnmutedChatsInfo")));
        }

        items.add(new ItemInner(VIEW_TYPE_HEADER, 6, LocaleController.getString("NewChatsFromNonContacts")));
        items.add(new ItemInner(VIEW_TYPE_CHECK, 7, LocaleController.getString("NewChatsFromNonContactsCheck")));
        items.add(new ItemInner(VIEW_TYPE_SHADOW, 8, LocaleController.getString("ArchiveAndMuteInfo")));

        if (adapter == null) {
            return;
        }

        if (animated) {
            adapter.setItems(oldItems, items);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    private final static int VIEW_TYPE_HEADER = 0;
    private final static int VIEW_TYPE_CHECK = 1;
    private final static int VIEW_TYPE_SHADOW = 2;

    private static class ItemInner extends AdapterWithDiffUtils.Item {
        public CharSequence text;
        public int id;
        public ItemInner(int viewType, int id, CharSequence text) {
            super(viewType, false);
            this.id = id;
            this.text = text;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemInner item = (ItemInner) o;
            return id == item.id && Objects.equals(text, item.text);
        }
    }

    private class ListAdapter extends AdapterWithDiffUtils {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_HEADER) {
                view = new HeaderCell(getContext());
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == VIEW_TYPE_CHECK) {
                view = new TextCheckCell(getContext());
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            } else {
                view = new TextInfoPrivacyCell(getContext());
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= items.size()) {
                return;
            }
            ItemInner item = items.get(position);
            final boolean divider = position + 1 < items.size() && items.get(position + 1).viewType == item.viewType;
            if (holder.getItemViewType() == VIEW_TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText(item.text);
            } else if (holder.getItemViewType() == VIEW_TYPE_SHADOW) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                if (TextUtils.isEmpty(item.text)) {
                    cell.setFixedSize(12);
                    cell.setText(null);
                } else {
                    cell.setFixedSize(0);
                    cell.setText(item.text);
                }
                if (divider) {
                    cell.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                } else {
                    cell.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                }
            } else if (holder.getItemViewType() == VIEW_TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                boolean checked;
                if (item.id == 1) {
                    checked = settings.keep_archived_unmuted;
                    cell.setCheckBoxIcon(0);
                } else if (item.id == 4) {
                    checked = settings.keep_archived_folders;
                    cell.setCheckBoxIcon(0);
                } else if (item.id == 7) {
                    checked = settings.archive_and_mute_new_noncontact_peers;
                    cell.setCheckBoxIcon(getUserConfig().isPremium() || getMessagesController().autoarchiveAvailable ? 0 : R.drawable.permission_locked);
                } else {
                    return;
                }
                cell.setTextAndCheck(item.text, checked, divider);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != VIEW_TYPE_SHADOW;
        }

        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= items.size()) {
                return 0;
            }
            return items.get(position).viewType;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.privacyRulesUpdated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.privacyRulesUpdated);
        super.onFragmentDestroy();

        if (changed) {
            TLRPC.TL_account_setGlobalPrivacySettings req = new TLRPC.TL_account_setGlobalPrivacySettings();
            req.settings = settings;
            getConnectionsManager().sendRequest(req, (response, error) -> {});
            changed = false;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.privacyRulesUpdated) {
            settings = getContactsController().getGlobalPrivacySettings();
            if (settings == null) {
                settings = new TLRPC.TL_globalPrivacySettings();
            }
            if (listView != null) {
                for (int i = 0; i < listView.getChildCount(); ++i) {
                    View child = listView.getChildAt(i);
                    int position = listView.getChildAdapterPosition(child);
                    if (position < 0 || position >= items.size()) {
                        continue;
                    }
                    ItemInner item = items.get(position);
                    if (item.id == 1) {
                        ((TextCheckCell) child).setChecked(settings.keep_archived_unmuted);
                    } else if (item.id == 4) {
                        ((TextCheckCell) child).setChecked(settings.keep_archived_folders);
                    } else if (item.id == 7) {
                        ((TextCheckCell) child).setChecked(settings.archive_and_mute_new_noncontact_peers);
                    }
                }
            }
            changed = false;
        } else if (id == NotificationCenter.dialogFiltersUpdated) {
            updateItems(true);
        }
    }
}
