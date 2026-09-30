package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.exoplayer2.Format;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.ui.Stories.DarkThemeResourceProvider;

import java.util.ArrayList;
import java.util.Locale;

public class ChooseAudioTrackLayout {

    public final ActionBarPopupWindow.ActionBarPopupWindowLayout layout;
    private final LinearLayout buttonsLayout;
    private final DarkThemeResourceProvider resourcesProvider = new DarkThemeResourceProvider();

    public ChooseAudioTrackLayout(Context context, PopupSwipeBackLayout swipeBackLayout) {
        layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourcesProvider);
        layout.setFitItems(true);

        ActionBarMenuSubItem backItem = ActionBarMenuItem.addItem(layout, R.drawable.msg_arrow_back, getString(R.string.Back), false, resourcesProvider);
        backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());
        backItem.setColors(0xfffafafa, 0xfffafafa);
        backItem.setSelectorColor(0x0fffffff);

        View gap = new View(context);
        gap.setMinimumWidth(dp(196));
        gap.setBackgroundColor(0xff181818);
        layout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(buttonsLayout);
    }

    public boolean update(VideoPlayer player) {
        if (player == null) {
            buttonsLayout.removeAllViews();
            return false;
        }
        ArrayList<VideoPlayer.AudioTrack> tracks = player.getAudioTracks();
        if (tracks.size() < 2) {
            buttonsLayout.removeAllViews();
            return false;
        }
        while (buttonsLayout.getChildCount() > tracks.size()) {
            buttonsLayout.removeViewAt(buttonsLayout.getChildCount() - 1);
        }
        for (int i = 0; i < tracks.size(); i++) {
            VideoPlayer.AudioTrack track = tracks.get(i);
            String name = getTrackName(track.format, i + 1);
            ActionBarMenuSubItem item;
            if (i < buttonsLayout.getChildCount()) {
                item = (ActionBarMenuSubItem) buttonsLayout.getChildAt(i);
                if (!TextUtils.equals(item.getTextView().getText(), name)) {
                    item.setText(name);
                }
            } else {
                item = ActionBarMenuItem.addItem(buttonsLayout, 0, name, true, resourcesProvider);
                item.setColors(0xfffafafa, 0xfffafafa);
                item.setSelectorColor(0x0fffffff);
            }
            item.setChecked(track.selected);
            item.setOnClickListener(view -> {
                if (!CastSync.isActive()) {
                    player.selectAudioTrack(track);
                }
            });
        }
        return true;
    }

    private static String getTrackName(Format format, int number) {
        String language = format.language;
        String name = "";
        if (!TextUtils.isEmpty(language) && !"und".equals(language)) {
            name = Locale.forLanguageTag(language.replace('_', '-'))
                    .getDisplayName(LocaleController.getInstance().getCurrentLocale());
        }
        if (!TextUtils.isEmpty(format.label) && !format.label.equalsIgnoreCase(name)) {
            name = TextUtils.isEmpty(name) ? format.label : name + " · " + format.label;
        }
        return TextUtils.isEmpty(name)
                ? LocaleController.formatString(R.string.VideoPlayerAudioTrackNumber, number)
                : LocaleController.formatString(R.string.VideoPlayerAudioTrackName, number, name);
    }
}
