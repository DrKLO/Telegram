package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class TimezoneSelector extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ActionBarMenuItem searchItem;
    private UniversalRecyclerView listView;
    private LinearLayout emptyView;

    private final static int search = 1;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.TimezoneTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        searchItem = actionBar.createMenu().addItem(search, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                listView.adapter.update(true);
                listView.scrollToPosition(0);
            }

            @Override
            public void onSearchCollapse() {
                searching = false;
                query = null;
                listView.adapter.update(true);
                listView.scrollToPosition(0);
            }

            @Override
            public void onTextChanged(EditText editText) {
                query = editText.getText().toString();
                listView.adapter.update(true);
                listView.scrollToPosition(0);
            }
        });
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(context, currentAccount, this::fillItems, this::onClick, null, getResourceProvider());
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
            }
        });

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setMinimumHeight(dp(500));
        BackupImageView emptyImageView = new BackupImageView(context);
        emptyImageView.getImageReceiver().setAllowLoadingOnAttachedOnly(false);
        MediaDataController.getInstance(currentAccount).setPlaceholderImage(emptyImageView, "RestrictedEmoji", "\uD83C\uDF16", "130_130");
        emptyView.addView(emptyImageView, LayoutHelper.createLinear(130, 130, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 42, 0, 12));
        TextView emptyTextView = new TextView(context);
        emptyTextView.setText(getString(R.string.TimezoneNotFound));
        emptyTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.addView(emptyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        return fragmentView = contentView;
    }

    public TimezoneSelector setValue(String timezoneKey) {
        currentTimezone = timezoneKey;
        return this;
    }

    @Override
    public boolean onFragmentCreate() {
        systemTimezone = TimezonesController.getInstance(currentAccount).getSystemTimezoneId();
        useSystem = TextUtils.equals(systemTimezone, currentTimezone);
        getNotificationCenter().addObserver(this, NotificationCenter.timezonesUpdated);
        return super.onFragmentCreate();
    }

    private Utilities.Callback<String> whenTimezoneSelected;
    public TimezoneSelector whenSelected(Utilities.Callback<String> selected) {
        whenTimezoneSelected = selected;
        return this;
    }

    private boolean searching;
    private String query;

    private String systemTimezone;
    private boolean useSystem;
    private String currentTimezone;

    private static final int BUTTON_DETECT = -1;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final boolean filter = searching && !TextUtils.isEmpty(query);
        final TimezonesController controller = TimezonesController.getInstance(currentAccount);
        if (!filter) {
            items.add(UItem.asRippleCheck(BUTTON_DETECT, getString(R.string.TimezoneDetectAutomatically)).setChecked(useSystem));
            items.add(UItem.asShadow(formatString(R.string.TimezoneDetectAutomaticallyInfo, controller.getTimezoneName(currentTimezone, true))));
            items.add(UItem.asHeader(getString(R.string.TimezoneHeader)));
        }
        boolean empty = true;
        for (int i = 0; i < controller.getTimezones().size(); ++i) {
            TLRPC.TL_timezone timezone = controller.getTimezones().get(i);
            if (filter) {
                String timezoneQuery = AndroidUtilities.translitSafe(timezone.name).toLowerCase().replace("/", " ");
                String q = AndroidUtilities.translitSafe(query).toLowerCase();
                if (!(timezoneQuery.contains(" " + q) || timezoneQuery.startsWith(q))) {
                    continue;
                }
            }
            items.add(UItem.asRadio(i, controller.getTimezoneName(timezone, false), controller.getTimezoneOffsetName(timezone)).setChecked(TextUtils.equals(timezone.id, currentTimezone)).setEnabled(!useSystem || filter));
            empty = false;
        }
        if (empty) {
            items.add(UItem.asCustom(emptyView));
        } else {
            items.add(UItem.asShadow(null));
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_DETECT) {
            useSystem = !useSystem;
            if (useSystem) {
                currentTimezone = systemTimezone;
                if (whenTimezoneSelected != null) {
                    whenTimezoneSelected.run(currentTimezone);
                }
            }
            ((TextCheckCell) view).setChecked(useSystem);
            listView.adapter.update(true);
        } else {
            if (!view.isEnabled()) return;
            final TimezonesController controller = TimezonesController.getInstance(currentAccount);
            if (item.id < 0 || item.id >= controller.getTimezones().size()) {
                return;
            }
            TLRPC.TL_timezone timezone = controller.getTimezones().get(item.id);
            useSystem = false;
            currentTimezone = timezone.id;
            if (whenTimezoneSelected != null) {
                whenTimezoneSelected.run(currentTimezone);
            }
            if (searching) {
                actionBar.closeSearchField(true);
            }
            listView.adapter.update(true);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.timezonesUpdated) {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.timezonesUpdated);
        super.onFragmentDestroy();
    }
}
