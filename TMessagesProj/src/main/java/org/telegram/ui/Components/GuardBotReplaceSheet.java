package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public class GuardBotReplaceSheet extends BottomSheetWithRecyclerListView {

    private final LinearLayout contentLayout;
    private final ButtonWithCounterView useNewButton;
    private final ButtonWithCounterView keepCurrentButton;

    private UniversalAdapter adapter;

    private GuardBotReplaceSheet(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            int currentAccount,
            TLObject currentBot,
            TLObject newBot,
            Runnable onUseNew
    ) {
        super(context, null, false, false, false, false, ActionBarType.FADING, resourcesProvider);

        fixNavigationBar();

        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setClipChildren(false);
        contentLayout.setClipToPadding(false);

        final int avatarSize   = 60;
        final int arrowPadding = 7;

        FrameLayout avatarsRow = new FrameLayout(context);
        View currentAvatar = buildAvatar(context, currentBot, dp(avatarSize));
        View newAvatar = buildAvatar(context, newBot, dp(avatarSize));

        ImageView arrowView = new ImageView(context);
        arrowView.setImageResource(R.drawable.msg_arrow_avatar);
        arrowView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        arrowView.setScaleType(ImageView.ScaleType.CENTER);

        LinearLayout avatarLine = new LinearLayout(context);
        avatarLine.setOrientation(LinearLayout.HORIZONTAL);
        avatarLine.setGravity(Gravity.CENTER_VERTICAL);
        avatarLine.setClipChildren(false);
        avatarLine.addView(currentAvatar, LayoutHelper.createLinear(avatarSize, avatarSize));
        avatarLine.addView(arrowView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, arrowPadding, 0, arrowPadding, 0));
        avatarLine.addView(newAvatar, LayoutHelper.createLinear(avatarSize, avatarSize));
        avatarsRow.addView(avatarLine, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        contentLayout.addView(avatarsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 23, 0, 19));

        TextView titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(getString(R.string.GuardBotReplaceTitle));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        contentLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 6));

        final String currentName = DialogObject.getShortName(currentBot);
        final String newName     = DialogObject.getShortName(newBot);

        TextView subtitleView = new TextView(context);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setText(buildSubtitle(currentName, newName));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitleView.setLineSpacing(dp(2.66f), 1);
        contentLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 29));

        useNewButton = new ButtonWithCounterView(context, resourcesProvider);
        useNewButton.setRound();
        useNewButton.setText(formatString(R.string.GuardBotReplaceUseNew, newName), false);
        useNewButton.setOnClickListener(v -> {
            if (onUseNew != null) onUseNew.run();
            dismiss();
        });
        contentLayout.addView(useNewButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 0, 14, 10));

        keepCurrentButton = new ButtonWithCounterView(context, resourcesProvider);
        keepCurrentButton.setRound();
        keepCurrentButton.setNeutral();
        keepCurrentButton.setText(formatString(R.string.GuardBotReplaceKeepCurrent, currentName), false);
        keepCurrentButton.setOnClickListener(v -> dismiss());
        contentLayout.addView(keepCurrentButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 0, 14, 14));
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        adapter.update(false);
    }

    private View buildAvatar(Context context, TLObject obj, int sizePx) {
        BackupImageView view = new ProfileActivity.AvatarImageView(context);
        view.setRoundRadius(sizePx / 2);
        AvatarDrawable drawable = new AvatarDrawable();
        drawable.setInfo(obj);
        view.setImageDrawable(drawable);
        view.setLayoutParams(new FrameLayout.LayoutParams(sizePx, sizePx));
        return view;
    }

    private static CharSequence buildSubtitle(String currentName, String newName) {
        return AndroidUtilities.replaceTags(formatString(R.string.GuardBotReplaceMessage, currentName, newName));
    }

    @Override
    protected CharSequence getTitle() {
        return "";
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(-1, contentLayout));
    }

    public static void show(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            int currentAccount,
            TLObject currentBot,
            TLObject newBot,
            Runnable onUseNew
    ) {
        new GuardBotReplaceSheet(context, resourcesProvider, currentAccount, currentBot, newBot, onUseNew).show();
    }
}