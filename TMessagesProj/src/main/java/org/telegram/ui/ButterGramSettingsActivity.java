package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.utils.settings.BooleanSetting;
import org.telegram.utils.settings.SharedSettings;

/** ButterGram settings screen, opened from the first item of the main settings list. */
public class ButterGramSettingsActivity extends BaseFragment {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_VALUE = 2;
    private static final int TYPE_INFO = 3;

    private static final int ROW_GHOST_HEADER = 0;
    private static final int ROW_GHOST_READ = 1;
    private static final int ROW_GHOST_TYPING = 2;
    private static final int ROW_GHOST_ONLINE = 3;
    private static final int ROW_GHOST_INFO = 4;
    private static final int ROW_MESSAGES_HEADER = 5;
    private static final int ROW_SAVE_DELETED = 6;
    private static final int ROW_MESSAGES_INFO = 7;
    private static final int ROW_UI_HEADER = 8;
    private static final int ROW_HIDE_PREMIUM = 9;
    private static final int ROW_APPEARANCE = 10;
    private static final int ROW_COUNT = 11;

    private RecyclerListView listView;
    private ListAdapter adapter;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.ButterGramSettings));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> onRowClicked(position));
        frameLayout.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));
        return fragmentView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Nullable
    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Nullable
    private static BooleanSetting settingForRow(int position) {
        switch (position) {
            case ROW_GHOST_READ:
                return SharedSettings.buttergramGhostRead;
            case ROW_GHOST_TYPING:
                return SharedSettings.buttergramGhostTyping;
            case ROW_GHOST_ONLINE:
                return SharedSettings.buttergramGhostOnline;
            case ROW_SAVE_DELETED:
                return SharedSettings.buttergramSaveDeleted;
            case ROW_HIDE_PREMIUM:
                return SharedSettings.buttergramHidePremiumRows;
            default:
                return null;
        }
    }

    private void onRowClicked(int position) {
        BooleanSetting setting = settingForRow(position);
        if (setting != null) {
            setting.toggle();
            adapter.notifyItemChanged(position);
            return;
        }
        if (position == ROW_APPEARANCE) {
            presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        private ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return ROW_COUNT;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_CHECK || type == TYPE_VALUE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_HEADER) {
                view = new HeaderCell(context);
            } else if (viewType == TYPE_CHECK) {
                view = new TextCheckCell(context);
            } else if (viewType == TYPE_VALUE) {
                view = new TextSettingsCell(context);
            } else {
                view = new TextInfoPrivacyCell(context);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
            ));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == ROW_GHOST_HEADER) {
                        cell.setText(LocaleController.getString(R.string.ButterGramGhostMode));
                    } else if (position == ROW_MESSAGES_HEADER) {
                        cell.setText(LocaleController.getString(R.string.ButterGramMessages));
                    } else {
                        cell.setText(LocaleController.getString(R.string.ButterGramInterface));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    BooleanSetting setting = settingForRow(position);
                    boolean checked = setting != null && setting.get();
                    if (position == ROW_GHOST_READ) {
                        cell.setTextAndCheck(LocaleController.getString(R.string.ButterGramGhostRead), checked, true);
                    } else if (position == ROW_GHOST_TYPING) {
                        cell.setTextAndCheck(LocaleController.getString(R.string.ButterGramGhostTyping), checked, true);
                    } else if (position == ROW_GHOST_ONLINE) {
                        cell.setTextAndCheck(LocaleController.getString(R.string.ButterGramGhostOnline), checked, false);
                    } else if (position == ROW_SAVE_DELETED) {
                        cell.setTextAndCheck(LocaleController.getString(R.string.ButterGramSaveDeleted), checked, false);
                    } else {
                        cell.setTextAndCheck(LocaleController.getString(R.string.ButterGramHidePremiumRows), checked, true);
                    }
                    break;
                }
                case TYPE_VALUE: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setText(LocaleController.getString(R.string.ButterGramAppearance), false);
                    break;
                }
                default: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText(position == ROW_GHOST_INFO
                            ? LocaleController.getString(R.string.ButterGramGhostInfo)
                            : LocaleController.getString(R.string.ButterGramSaveDeletedInfo));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            switch (position) {
                case ROW_GHOST_HEADER:
                case ROW_MESSAGES_HEADER:
                case ROW_UI_HEADER:
                    return TYPE_HEADER;
                case ROW_GHOST_READ:
                case ROW_GHOST_TYPING:
                case ROW_GHOST_ONLINE:
                case ROW_SAVE_DELETED:
                case ROW_HIDE_PREMIUM:
                    return TYPE_CHECK;
                case ROW_GHOST_INFO:
                case ROW_MESSAGES_INFO:
                    return TYPE_INFO;
                default:
                    return TYPE_VALUE;
            }
        }
    }
}
