package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.utils.recorder.audio.AudioRecordingSession;
import org.telegram.utils.settings.SharedSettings;

/** Configures the second-generation voice-message recorder. */
public class AudioRecorderSettingsActivity extends BaseFragment {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_VALUE = 2;
    private static final int TYPE_INFO = 3;

    private static final int ROW_GENERAL_HEADER = 0;
    private static final int ROW_ENABLED = 1;
    private static final int ROW_NOISE_SUPPRESSION = 2;
    private static final int ROW_GENERAL_INFO = 3;
    private static final int ROW_COUNT = 4;

    private RecyclerListView listView;
    private ListAdapter adapter;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.AudioRecorderSettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
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

    private void onRowClicked(int position) {
        if (position == ROW_ENABLED) {
            SharedSettings.audioRecorderV2Enabled.toggle();
            adapter.notifyDataSetChanged();
            return;
        }
        if (!SharedSettings.audioRecorderV2Enabled.get()) {
            return;
        }
        if (position == ROW_NOISE_SUPPRESSION) {
            showNoiseSuppressionChoice();
        }
    }

    private void showNoiseSuppressionChoice() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.AudioRecorderNoiseSuppression));
        builder.setItems(new CharSequence[]{
                LocaleController.getString(R.string.AudioRecorderNoiseSuppressionOff),
                LocaleController.getString(R.string.AudioRecorderNoiseSuppressionRNNoise)
        }, (dialog, index) -> {
            SharedSettings.audioRecorderNoiseSuppression.set(index == 0
                    ? AudioRecordingSession.NoiseSuppression.DISABLED
                    : AudioRecordingSession.NoiseSuppression.RNNOISE);
            adapter.notifyItemChanged(ROW_NOISE_SUPPRESSION);
        });
        showDialog(builder.create());
    }

    private static String noiseSuppressionLabel(
            AudioRecordingSession.NoiseSuppression noiseSuppression
    ) {
        return LocaleController.getString(noiseSuppression
                == AudioRecordingSession.NoiseSuppression.RNNOISE
                ? R.string.AudioRecorderNoiseSuppressionRNNoise
                : R.string.AudioRecorderNoiseSuppressionOff);
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
            int position = holder.getAdapterPosition();
            if (position == ROW_ENABLED) {
                return true;
            }
            return SharedSettings.audioRecorderV2Enabled.get()
                    && position == ROW_NOISE_SUPPRESSION;
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
                TextSettingsCell cell = new TextSettingsCell(context);
                cell.setCanDisable(true);
                view = cell;
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
            boolean enabled = SharedSettings.audioRecorderV2Enabled.get();
            if (holder.getItemViewType() == TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText(
                        LocaleController.getString(R.string.AudioRecorderGeneral)
                );
            } else if (holder.getItemViewType() == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setTextAndCheck(
                        LocaleController.getString(R.string.AudioRecorderUseNewRecorder),
                        enabled,
                        false
                );
            } else if (holder.getItemViewType() == TYPE_VALUE) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setEnabled(enabled);
                cell.setTextAndValue(
                        LocaleController.getString(R.string.AudioRecorderNoiseSuppression),
                        noiseSuppressionLabel(
                                SharedSettings.audioRecorderNoiseSuppression.get()
                        ),
                        false
                );
            } else {
                ((TextInfoPrivacyCell) holder.itemView).setText(
                        LocaleController.getString(R.string.AudioRecorderGeneralInfo)
                );
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == ROW_GENERAL_HEADER) {
                return TYPE_HEADER;
            }
            if (position == ROW_ENABLED) {
                return TYPE_CHECK;
            }
            if (position == ROW_NOISE_SUPPRESSION) {
                return TYPE_VALUE;
            }
            return TYPE_INFO;
        }
    }
}
