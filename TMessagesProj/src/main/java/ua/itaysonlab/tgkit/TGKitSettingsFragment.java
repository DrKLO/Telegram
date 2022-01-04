package ua.itaysonlab.tgkit;

import android.content.Context;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import ua.itaysonlab.catogram.preferences.BasePreferencesEntry;
import ua.itaysonlab.catogram.prefviews.StickerSliderCell;
import ua.itaysonlab.tgkit.preference.TGKitCategory;
import ua.itaysonlab.tgkit.preference.TGKitPreference;
import ua.itaysonlab.tgkit.preference.TGKitSettings;
import ua.itaysonlab.tgkit.preference.types.TGKitHeaderRow;
import ua.itaysonlab.tgkit.preference.types.TGKitListPreference;
import ua.itaysonlab.tgkit.preference.types.TGKitSectionRow;
import ua.itaysonlab.tgkit.preference.types.TGKitSettingsCellRow;
import ua.itaysonlab.tgkit.preference.types.TGKitSliderPreference;
import ua.itaysonlab.tgkit.preference.types.TGKitSwitchPreference;
import ua.itaysonlab.tgkit.preference.types.TGKitTextDetailRow;
import ua.itaysonlab.tgkit.preference.types.TGKitTextIconRow;

public class TGKitSettingsFragment extends BaseFragment {
    private final TGKitSettings settings;
    private final SparseArray<TGKitPreference> positions = new SparseArray<>();
    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private int rowCount;

    public TGKitSettingsFragment(BasePreferencesEntry entry) {
        super();
        this.settings = entry.getProcessedPrefs(this);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;
        initSettings();

        return true;
    }

    private void initSettings() {
        for (TGKitCategory category : settings.categories) {
            positions.put(rowCount++, new TGKitHeaderRow(category.name));
            for (TGKitPreference preference : category.preferences) {
                positions.put(rowCount++, preference);
            }
            positions.put(rowCount++, new TGKitSectionRow());
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(settings.name);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position, x, y) -> {
            TGKitPreference pref = positions.get(position);
            if (pref instanceof TGKitSwitchPreference) {
                ((TGKitSwitchPreference) pref).contract.toggleValue();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(((TGKitSwitchPreference) pref).contract.getPreferenceValue());
                }
            } else if (pref instanceof TGKitTextIconRow) {
                TGKitTextIconRow preference = ((TGKitTextIconRow) pref);
                if (preference.listener != null) preference.listener.onClick(this);
            } else if (pref instanceof TGKitSettingsCellRow) {
                TGKitSettingsCellRow preference = ((TGKitSettingsCellRow) pref);
                if (preference.listener != null) preference.listener.onClick(this);
            } else if (pref instanceof TGKitListPreference) {
                TGKitListPreference preference = ((TGKitListPreference) pref);
                preference.callActionHueta(this, getParentActivity(), view, x, y, () -> {
                    if (view instanceof TextDetailSettingsCell)
                        ((TextDetailSettingsCell) view).setTextAndValue(preference.title, preference.getContract().getValue(), preference.getDivider());
                });
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        return themeDescriptions;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        ListAdapter(Context context) {
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
                    holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case 1: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setCanDisable(false);

                    TGKitSettingsCellRow pref = (TGKitSettingsCellRow) positions.get(position);
                    textCell.setTextColor(pref.textColor);
                    textCell.setText(pref.title, pref.divider);

                    break;
                }
                case 2: {
                    ((HeaderCell) holder.itemView).setText(positions.get(position).title);
                    break;
                }
                case 3: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    TGKitSwitchPreference pref = (TGKitSwitchPreference) positions.get(position);
                    if (pref.summary != null) {
                        checkCell.setTextAndValueAndCheck(pref.title, pref.summary, pref.contract.getPreferenceValue(), true, pref.divider);
                    } else {
                        checkCell.setTextAndCheck(pref.title, pref.contract.getPreferenceValue(), pref.divider);
                    }
                    break;
                }
                case 4: {
                    TextDetailSettingsCell settingsCell = (TextDetailSettingsCell) holder.itemView;
                    TGKitTextDetailRow pref = (TGKitTextDetailRow) positions.get(position);
                    settingsCell.setMultilineDetail(true);
                    settingsCell.setTextAndValue(pref.title, pref.detail, pref.divider);
                    break;
                }
                case 5: {
                    TextCell cell = (TextCell) holder.itemView;
                    ((TGKitTextIconRow) positions.get(position)).bindCell(cell);
                    break;
                }
                case 6: {
                    ((StickerSliderCell) holder.itemView).setContract(((TGKitSliderPreference) positions.get(position)).contract);
                    break;
                }
                case 7: {
                    TextDetailSettingsCell settingsCell = (TextDetailSettingsCell) holder.itemView;
                    TGKitListPreference pref = (TGKitListPreference) positions.get(position);
                    settingsCell.setMultilineDetail(true);
                    settingsCell.setTextAndValue(pref.title, pref.getContract().getValue(), pref.getDivider());
                    break;
                }
                case 8: {
                    ((TextInfoPrivacyCell) holder.itemView).setText(positions.get(position).title);
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            if (viewType == 3) {
                int position = holder.getAdapterPosition();
                TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                checkCell.setChecked(((TGKitSwitchPreference) positions.get(position)).contract.getPreferenceValue());
            } else if (viewType == 7) {
                int position = holder.getAdapterPosition();
                TextDetailSettingsCell checkCell = (TextDetailSettingsCell) holder.itemView;
                TGKitListPreference pref = ((TGKitListPreference) positions.get(position));
                checkCell.setTextAndValue(pref.title, pref.getContract().getValue(), pref.getDivider());
            }
        }

        public boolean isRowEnabled(int position) {
            return positions.get(position).getType().enabled;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return isRowEnabled(holder.getAdapterPosition());
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 1:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                case 7:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 5:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                    view = new StickerSliderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 8:
                    view = new TextInfoPrivacyCell(mContext);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            return positions.get(position).getType().adapterType;
        }
    }
}