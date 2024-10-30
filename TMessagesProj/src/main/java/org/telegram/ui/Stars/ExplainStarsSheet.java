package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Dialog;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.scheduler.RequirementsWatcher;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.DialogsBotsAdapter;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.GLIcon.Icon3D;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StarAppsSheet;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;

public class ExplainStarsSheet extends BottomSheetWithRecyclerListView {

    private UniversalAdapter adapter;

    private LinearLayout headerView;
    private FrameLayout buttonContainer;

    public ExplainStarsSheet(Context context) {
        super(context, null, false, false, false, null);
        topPadding = .1f;

        fixNavigationBar();

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        headerView = new LinearLayout(context);
        headerView.setOrientation(LinearLayout.VERTICAL);

        FrameLayout topView = new FrameLayout(context);
        topView.setClipChildren(false);
        topView.setClipToPadding(false);

        StarParticlesView particlesView = StarsIntroActivity.makeParticlesView(context, 70, 0);
        topView.addView(particlesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        GLIconTextureView iconView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE, Icon3D.TYPE_GOLDEN_STAR);
        iconView.mRenderer.colorKey1 = Theme.key_starsGradient1;
        iconView.mRenderer.colorKey2 = Theme.key_starsGradient2;
        iconView.mRenderer.updateColors();
        iconView.setStarParticlesView(particlesView);
        topView.addView(iconView, LayoutHelper.createFrame(170, 170, Gravity.CENTER, 0, 32, 0, 24));
        iconView.setPaused(false);

        headerView.addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 150));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(getString(R.string.ExplainStarsTitle));
        headerView.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 0));

        TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setText(getString(R.string.ExplainStarsTitle2));
        headerView.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT/*(int) Math.ceil(HintView2.cutInFancyHalf(subtitleView.getText(), subtitleView.getPaint()) / AndroidUtilities.density)*/, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 9, 16, 18));

        buttonContainer = new FrameLayout(context);
        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.ExplainStarsButton), false);
        button.setOnClickListener(v -> dismiss());
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 10, 10, 10, 10));
        buttonContainer.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        adapter.update(false);
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.ExplainStarsTitle);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider) {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return false;
            }
        };
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(headerView));
        items.add(FeatureCell.Factory.of(R.drawable.msg_gift_premium, getString(R.string.ExplainStarsFeature1Title), getString(R.string.ExplainStarsFeature1Text)));
        items.add(FeatureCell.Factory.of(R.drawable.msg_bot, getString(R.string.ExplainStarsFeature2Title), AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.ExplainStarsFeature2Text), () -> {
            new StarAppsSheet(getContext()).show();
        }), true)));
        items.add(FeatureCell.Factory.of(R.drawable.menu_unlock, getString(R.string.ExplainStarsFeature3Title), getString(R.string.ExplainStarsFeature3Text)));
        items.add(FeatureCell.Factory.of(R.drawable.menu_feature_paid, getString(R.string.ExplainStarsFeature4Title), getString(R.string.ExplainStarsFeature4Text)));
        items.add(UItem.asSpace(dp(10 + 48 + 10)));
    }

    public static class FeatureCell extends LinearLayout {

        private final ImageView imageView;
        private final LinearLayout textLayout;
        private final TextView titleView;
        private final LinkSpanDrawable.LinksTextView subtitleView;

        public FeatureCell(Context context) {
            super(context);

            setOrientation(HORIZONTAL);

            setPadding(dp(32), 0, dp(32), dp(12));

            imageView = new ImageView(context);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createLinear(24, 24, Gravity.TOP | Gravity.LEFT, 0, 6, 16, 0));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(VERTICAL);

            titleView = new TextView(context);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 0, 0, 0, 3));

            subtitleView = new LinkSpanDrawable.LinksTextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            subtitleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn));
            textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL));

            addView(textLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));
        }

        public void set(int iconResId, CharSequence title, CharSequence text) {
            imageView.setImageResource(iconResId);
            titleView.setText(title);
            subtitleView.setText(text);
        }

        public static class Factory extends UItem.UItemFactory<FeatureCell> {
            static { setup(new Factory()); }

            @Override
            public FeatureCell createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new FeatureCell(context);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                ((FeatureCell) view).set(
                    item.intValue, item.text, item.subtext
                );
            }

            public static UItem of(int iconResId, CharSequence title, CharSequence text) {
                UItem item = UItem.ofFactory(Factory.class);
                item.selectable = false;
                item.intValue = iconResId;
                item.text = title;
                item.subtext = text;
                return item;
            }

        }
    }


}