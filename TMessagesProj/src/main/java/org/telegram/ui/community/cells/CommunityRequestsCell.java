package org.telegram.ui.community.cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.SettingsActivity;

public class CommunityRequestsCell extends LinearLayout implements Theme.Colorable {

    private final Theme.ResourcesProvider resourcesProvider;
    private final SettingsActivity.SettingCell.Background iconBackground;
    private final FrameLayout iconLayout;
    private final ImageView iconView;
    private final LinearLayout textLayout;
    private final TextView titleView;
    private final TextView valueView;

    private final boolean mini;

    public CommunityRequestsCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, resourcesProvider, false);
    }

    public CommunityRequestsCell(Context context, Theme.ResourcesProvider resourcesProvider, boolean mini) {
        super(context);

        this.resourcesProvider = resourcesProvider;
        this.mini = mini;
        setOrientation(HORIZONTAL);

        iconLayout = new FrameLayout(context);
        iconLayout.setBackground(iconBackground = new SettingsActivity.SettingCell.Background());

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconLayout.addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.CENTER));

        textLayout = new LinearLayout(context);
        textLayout.setOrientation(VERTICAL);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        valueView = new TextView(context);
        valueView.setGravity(Gravity.CENTER);
        valueView.setMinWidth(dp(20.66f));
        valueView.setPadding(dp(6.33f), 0, dp(6.33f), 0);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        if (LocaleController.isRTL) {
            addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 20.66f, Gravity.CENTER_VERTICAL, 13.33f, 0, 0, 0));
            addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 20, 0, mini ? 12 : 16, 0));
            addView(iconLayout, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, mini ? 9 : 14, 0));
        } else {
            addView(iconLayout, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL | Gravity.LEFT, mini ? 9 : 14, 0, 0, 0));
            addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL,  mini ? 12 : 16, 0, 20, 0));
            addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 20.66f, Gravity.CENTER_VERTICAL, 0f, 0f, 13.33f, 0f));
        }
        updateColors();

        setUnreadMode(true);
    }

    private boolean mUnreadMode;

    public void setUnreadMode(boolean unreadMode) {
        if (this.mUnreadMode != unreadMode) {
            this.mUnreadMode = unreadMode;
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, unreadMode ? 13 : 16);
            valueView.setTypeface(unreadMode ? AndroidUtilities.bold() : null);
            valueView.setTextColor(Theme.getColor(unreadMode ?
                Theme.key_chats_unreadCounterText :
                Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));

            valueView.setBackground(unreadMode ? Theme.createRoundRectDrawable(dp(10.33f), Theme.getColor(Theme.key_chats_unreadCounter, resourcesProvider)) : null);
        }
    }

    @Override
    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        valueView.setTextColor(Theme.getColor(mUnreadMode ?
            Theme.key_chats_unreadCounterText :
            Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
        valueView.setBackground(mUnreadMode ? Theme.createRoundRectDrawable(dp(10.33f), Theme.getColor(Theme.key_chats_unreadCounter, resourcesProvider)) : null);

        iconBackground.setDrawBorder(resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark());
    }

    public void set(
            int iconColorTop, int iconColorBottom, int icon,
            CharSequence title,
            CharSequence value,
            boolean valueAsUnread
    ) {
        iconLayout.setVisibility(icon != 0 ? View.VISIBLE : View.GONE);
        titleView.setTranslationX(icon == 0 ? dp(2) : 0);

        iconBackground.setColor(iconColorTop, iconColorBottom);
        iconView.setImageResource(icon);
        setTitle(title);
        setValue(value);
        setUnreadMode(valueAsUnread);
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
    }

    public void setValue(CharSequence value) {
        valueView.setVisibility(!TextUtils.isEmpty(value) ? View.VISIBLE : View.GONE);
        valueView.setText(value);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(mini ? 44 : 50), MeasureSpec.EXACTLY)
        );
    }

    public static class Factory extends UItem.UItemFactory<CommunityRequestsCell> {
        static { setup(new Factory()); }

        @Override
        public CommunityRequestsCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new CommunityRequestsCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            int iconColorTop    = (int) item.longValue;
            int iconColorBottom = (int) (item.longValue >>> 32);
            ((CommunityRequestsCell) view).set(
                iconColorTop, iconColorBottom, item.iconResId,
                item.text,
                item.textValue,
                item.accent
            );
        }

        public static UItem of(int id, IconBackgroundColors iconColor, int icon, CharSequence title, CharSequence value, boolean valueAsUnread) {
            final UItem item = UItem.ofFactory(CommunityRequestsCell.Factory.class);
            item.id = id;
            item.iconResId = icon;
            item.text = title;
            item.textValue = value;
            item.longValue = ((long) iconColor.bottom << 32) | (iconColor.top & 0xFFFFFFFFL);
            item.accent = valueAsUnread;
            return item;
        }
    }
}
