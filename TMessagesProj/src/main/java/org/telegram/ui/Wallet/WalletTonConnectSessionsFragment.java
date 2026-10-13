package org.telegram.ui.Wallet;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.WebFile;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MessagesSearchAdapter;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public class WalletTonConnectSessionsFragment extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    public WalletTonConnectSessionsFragment() {

    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.walletUpdate);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.walletUpdate);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.walletUpdate) {
            if (listView != null) {
                listView.adapter.update(true);
            }
        }
    }

    @Override
    public View createView(Context context) {
        fragmentView = super.createView(context);

        actionBar.setAdaptiveBackground(listView);

        listView.setSections();
        listView.setClipToPadding(false);

        return fragmentView;
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.WalletConnectedApps);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        if (!wallet.tonConnect.sessions.isEmpty()) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.WalletActiveConnections)));
        }
        for (TL_wallet.tonConnectSession session : wallet.tonConnect.sessions) {
            items.add(TONConnectionCell.Factory.of(session));
        }
        if (wallet.tonConnect.isLoadingSessions()) {
            // TODO: flickers
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.WalletConnectedAppsInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof TL_wallet.tonConnectSession) {;
            final TL_wallet.tonConnectSession session = (TL_wallet.tonConnectSession) item.object;
            openSessionSheet(getContext(), session, getResourceProvider(), done -> {
                GramWallet.getInstance(currentAccount).tonConnect.disconnectSession(session, done);
            });
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        listView.setPadding(0, 0, 0, bottom);
        listView.setClipToPadding(false);
    }

    public static void openSessionSheet(Context context, TL_wallet.tonConnectSession session, Theme.ResourcesProvider resourcesProvider, Utilities.Callback<Utilities.Callback<String /* error */>> onDelete) {
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        final FrameLayout topLayout = new FrameLayout(context);

        final ImageView closeView = new ImageView(context);
        closeView.setImageResource(R.drawable.ic_ab_close);
        closeView.setScaleType(ImageView.ScaleType.CENTER);
        closeView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider)));
        closeView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(closeView);
        topLayout.addView(closeView, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.LEFT, 4, 0, 0, 0));

        final BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(38));
        if (session.manifest != null && session.manifest.icon != null) {
            avatarView.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(session.manifest.icon)), "76_76", null, 0, session.manifest);
        }
        topLayout.addView(avatarView, LayoutHelper.createFrame(76, 76, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 32, 0, 0));

        layout.addView(topLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final TextView titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(session.manifest != null ? session.manifest.name : LocaleController.getString(R.string.WalletConnectedApp));
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 36, 16, 36, 0));

        final String domain = AndroidUtilities.getHostAuthority(session.manifest != null ? session.manifest.url : null);
        if (!TextUtils.isEmpty(domain)) {
            final TextView linkView = new TextView(context);
            linkView.setTypeface(AndroidUtilities.bold());
            linkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            linkView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            final SpannableStringBuilder ssb = new SpannableStringBuilder();
            // final Drawable verifiedIconBg = context.getResources().getDrawable(R.drawable.verified2_bg).mutate();
            // verifiedIconBg.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), PorterDuff.Mode.SRC_IN));
            // final Drawable verifiedIconFg = context.getResources().getDrawable(R.drawable.verified2_fg).mutate();
            // verifiedIconFg.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
            // final Drawable verifiedIcon = new CombinedDrawable(verifiedIconBg, verifiedIconFg);
            // ssb.append("✅");
            // verifiedIcon.setBounds(0, -dp(2), dp(16), dp(14));
            // ssb.setSpan(new ImageSpan(verifiedIcon), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            // ssb.append(" ");
            ssb.append(domain);
            linkView.setText(ssb);
            layout.addView(linkView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 36, 6, 36, 0));
        }

        final TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setText(LocaleController.getString(R.string.WalletConnectedAppInfo));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        layout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 36, 16, 36, 28));

        final ButtonWithCounterView disconnect = new ButtonWithCounterView(context, resourcesProvider).setRound();
        disconnect.setText(LocaleController.getString(R.string.WalletDisconnect));
        disconnect.setColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        layout.addView(disconnect, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, Gravity.CENTER_HORIZONTAL, 16, 0, 16, 16));

        final NewBottomSheet sheet = new NewBottomSheet(context, layout, resourcesProvider);
        sheet.show();

        closeView.setOnClickListener(v -> sheet.dismiss());
        disconnect.setOnClickListener(v -> {
            if (disconnect.isLoading()) return;
            disconnect.setLoading(true);

            onDelete.run(error -> {
                disconnect.setLoading(false);
                if (error == null) {
                    sheet.dismiss();
                } else {
                    BulletinFactory.showError(error, sheet.topBulletinContainer, resourcesProvider);
                }
            });
        });
    }

    private static class TONConnectionCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final BackupImageView imageView;
        private final LinearLayout textLayout;
        private final TextView titleView;
        private final TextView subtitleView;

        public TONConnectionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(10));
            addView(imageView, LayoutHelper.createFrame(28, 28, Gravity.CENTER_VERTICAL | Gravity.LEFT, 18, 0, 0, 0));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 62, 0, 20, 0));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            updateColors();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58), MeasureSpec.EXACTLY)
            );
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        }

        public void set(TL_wallet.tonConnectSession session) {
            if (session.manifest != null) {
                if (session.manifest.icon != null) {
                    imageView.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(session.manifest.icon)), "28_28", null, 0, session.manifest);
                }
                titleView.setText(session.manifest.name);
                subtitleView.setText(AndroidUtilities.getHostAuthority(session.manifest.url));
            }
        }

        public static class Factory extends UItem.UItemFactory<TONConnectionCell> {
            static { setup(new Factory()); }

            @Override
            public TONConnectionCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new TONConnectionCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((TONConnectionCell) view).set((TL_wallet.tonConnectSession) item.object);
            }

            public static UItem of(TL_wallet.tonConnectSession session) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.object = session;
                return item;
            }
        }
    }

}
