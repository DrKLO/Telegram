package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AvailableReactionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SimpleThemeDescription;

import java.util.ArrayList;
import java.util.List;

public class ReactionsDoubleTapManageActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private LinearLayout contentView;
    private RecyclerListView listView;
    private RecyclerView.Adapter listAdapter;

    int previewRow;
    int infoRow;
    int reactionsStartRow;
    int rowCount;

    public ReactionsDoubleTapManageActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.reactionsDidLoad);
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString("Reactions", R.string.Reactions));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        LinearLayout linaerLayout = new LinearLayout(context);
        linaerLayout.setOrientation(LinearLayout.VERTICAL);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(listAdapter = new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                switch (viewType) {
                    case 0:
                        ThemePreviewMessagesCell messagesCell = new ThemePreviewMessagesCell(context, parentLayout, ThemePreviewMessagesCell.TYPE_REACTIONS_DOUBLE_TAP);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            messagesCell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                        }
                        messagesCell.fragment = ReactionsDoubleTapManageActivity.this;
                        view = messagesCell;
                        break;
                    case 2:
                        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
                        cell.setText(LocaleController.getString("DoubleTapPreviewRational", R.string.DoubleTapPreviewRational));
                        view = cell;
                        break;
                    default:
                    case 1: {
                        view = new AvailableReactionCell(context, true);
                    }
                    break;
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                switch (getItemViewType(position)) {
                    case 1:
                        AvailableReactionCell reactionCell = (AvailableReactionCell) holder.itemView;
                        TLRPC.TL_availableReaction react = getAvailableReactions().get(position - reactionsStartRow);
                        reactionCell.bind(react, react.reaction.contains(MediaDataController.getInstance(currentAccount).getDoubleTapReaction()));
                        break;
                }
            }

            @Override
            public int getItemCount() {
                return getAvailableReactions().size();
            }

            @Override
            public int getItemViewType(int position) {
                if (position == previewRow) {
                    return 0;
                }
                if (position == infoRow) {
                    return 2;
                }
                return 1;
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof AvailableReactionCell) {
                MediaDataController.getInstance(currentAccount).setDoubleTapReaction(((AvailableReactionCell) view).react.reaction);
                AndroidUtilities.updateVisibleRows(listView);
            }
        });
        linaerLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = contentView = linaerLayout;

        updateColors();
        updateRows();

        return contentView;
    }

    private void updateRows() {
        rowCount = 0;
        previewRow = rowCount++;
        infoRow = rowCount++;
        reactionsStartRow = rowCount++;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.reactionsDidLoad);
    }

    private List<TLRPC.TL_availableReaction> getAvailableReactions() {
        return getMediaDataController().getEnabledReactionsList();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return SimpleThemeDescription.createThemeDescriptions(this::updateColors,
                Theme.key_windowBackgroundWhite,
                Theme.key_windowBackgroundWhiteBlackText,
                Theme.key_windowBackgroundWhiteGrayText2,
                Theme.key_listSelector,
                Theme.key_windowBackgroundGray,
                Theme.key_windowBackgroundWhiteGrayText4,
                Theme.key_windowBackgroundWhiteRedText4,
                Theme.key_windowBackgroundChecked,
                Theme.key_windowBackgroundCheckText,
                Theme.key_switchTrackBlue,
                Theme.key_switchTrackBlueChecked,
                Theme.key_switchTrackBlueThumb,
                Theme.key_switchTrackBlueThumbChecked
        );
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateColors() {
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listAdapter.notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount) return;
        if (id == NotificationCenter.reactionsDidLoad) {
            listAdapter.notifyDataSetChanged();
        }
    }
}