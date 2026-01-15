package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.util.Util;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.PasskeysController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MessagesSearchAdapter;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Stars.ExplainStarsSheet;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

@RequiresApi(api = 28)
public class PasskeysActivity extends BaseFragment {

    private UniversalRecyclerView listView;

    private ArrayList<TL_account.Passkey> passkeys;

    public PasskeysActivity(ArrayList<TL_account.Passkey> passkeys) {
        this.passkeys = passkeys;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.Passkey));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView(getString(R.string.PasskeyTopInfo), R.raw.passkey));
        for (int i = 0; i < passkeys.size(); ++i) {
            final TL_account.Passkey passkey = passkeys.get(i);
            items.add(PasskeyCell.Factory.of(passkey, this::openMenu));
        }
        if (passkeys.size() + 1 <= getMessagesController().config.passkeysAccountPasskeysMax.get()) {
            items.add(UItem.asButton(-1, R.drawable.menu_passkey_add, getString(R.string.PasskeyAdd)).accent());
        }
        items.add(UItem.asShadow(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.PasskeyInfo), () -> {
            showLearnSheet(getContext(), currentAccount, resourceProvider, passkeys.size() + 1 <= getMessagesController().config.passkeysAccountPasskeysMax.get());
        }), true)));
    }

    private void openMenu(View view) {
        final PasskeyCell cell = view instanceof ImageView ? (PasskeyCell) view.getParent() : (PasskeyCell) view;
        final String id = cell.id;
        int _index = -1;
        for (int i = 0; i < passkeys.size(); ++i) {
            if (id.equals(passkeys.get(i).id)) {
                _index = i;
                break;
            }
        }
        final int index = _index;
        if (index < 0 || index >= passkeys.size()) return;
        final TL_account.Passkey passkey = passkeys.get(index);
        ItemOptions.makeOptions(this, cell)
            .add(R.drawable.msg_delete, getString(R.string.Delete), true, () -> {
                new AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.PasskeyDeleteTitle))
                    .setMessage(getString(R.string.PasskeyDeleteText))
                    .setPositiveButton(getString(R.string.Delete), (di, w) -> {
                        passkeys.remove(passkey);
                        listView.adapter.update(true);

                        final TL_account.deletePasskey req = new TL_account.deletePasskey();
                        req.id = id;
                        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
                            if (res instanceof TLRPC.TL_boolFalse) {
                                BulletinFactory.of(this).showForError("FALSE");
                                passkeys.add(Utilities.clamp(index, passkeys.size(), 0), passkey);
                                listView.adapter.update(true);
                            } else if (err != null) {
                                BulletinFactory.of(this).showForError(err);
                                passkeys.add(Utilities.clamp(index, passkeys.size(), 0), passkey);
                                listView.adapter.update(true);
                            }
                        });
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .makeRed(AlertDialog.BUTTON_POSITIVE)
                    .show();
            })
            .show();
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == -1) {
            PasskeysController.create(getContext(), currentAccount, (passkey, error) -> {
                if (error != null) {
                    if ("CANCELLED".equalsIgnoreCase(error))
                        return;
                    if ("EMPTY".equalsIgnoreCase(error)) {
                        new AlertDialog.Builder(getContext())
                            .setTitle(getString(R.string.PasskeyNoOptionsTitle))
                            .setMessage(getString(R.string.PasskeyNoOptionsText))
                            .setPositiveButton(getString(R.string.OK), null)
                            .show();
                        return;
                    }
                    BulletinFactory.of(this).showForError(error, true);
                } else if (passkey != null) {
                    MessagesController.getInstance(currentAccount).removeSuggestion(0, "SETUP_PASSKEY");
                    added(passkey);
                }
            });
        } else if (item.object != null) {
            openMenu(view);
        }
    }

    public void added(TL_account.Passkey passkey) {
        passkeys.add(passkey);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }

        BulletinFactory.of(this)
            .createSimpleBulletin(R.raw.passcode_lock_close, LocaleController.getString(R.string.PasskeyAddedTitle), LocaleController.formatString(R.string.PasskeyAddedText, passkey.name))
            .setDuration(Bulletin.DURATION_PROLONG)
            .show(true);
    }

    public static class PasskeyCell extends FrameLayout {

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;

        private final FrameLayout imageBackgroundView;
        private final BackupImageView imageView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final ImageView optionsView;
        private boolean needDivider;

        public PasskeyCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            imageBackgroundView = new FrameLayout(context);
            addView(imageBackgroundView, LayoutHelper.createFrame(36, 36, Gravity.CENTER_VERTICAL | Gravity.LEFT, 18.5f, 0, 0, 0));

            imageView = new BackupImageView(context);
            imageView.setImageResource(R.drawable.msg2_permissions);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.3f), PorterDuff.Mode.SRC_IN));
            imageBackgroundView.addView(imageView, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

            titleView = TextHelper.makeTextView(context, 15, Theme.key_windowBackgroundWhiteBlackText, true);
            titleView.setSingleLine();
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 72, 8, 46, 0));

            subtitleView = TextHelper.makeTextView(context, 13, Theme.key_windowBackgroundWhiteGrayText, false);
            subtitleView.setSingleLine();
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 72, 31, 46, 0));

            optionsView = new ImageView(context);
            optionsView.setScaleType(ImageView.ScaleType.CENTER);
            optionsView.setImageResource(R.drawable.ic_ab_other);
            optionsView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
            optionsView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider)));
            addView(optionsView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 13, 0));
        }

        public String id;
        public void set(TL_account.Passkey passkey, View.OnClickListener onOptions, boolean divider) {
            this.id = passkey.id;
            if (passkey.software_emoji_id != 0) {
                imageView.setAnimatedEmojiDrawable(AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, passkey.software_emoji_id));
                imageBackgroundView.setBackground(null);
                imageView.setColorFilter(null);
                imageView.setScaleX(1.0f);
                imageView.setScaleY(1.0f);
            } else {
                imageBackgroundView.setBackground(Theme.createRoundRectDrawable(dp(4), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.04f)));
                imageView.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.3f), PorterDuff.Mode.SRC_IN));
                imageView.setImageResource(R.drawable.msg2_permissions);
                imageView.setScaleX(0.666f);
                imageView.setScaleY(0.666f);
                imageView.setAnimatedEmojiDrawable(null);
            }
            if (TextUtils.isEmpty(passkey.name)) {
                titleView.setText(getString(R.string.PasskeyUnknown));
            } else {
                titleView.setText(passkey.name);
            }
            if (passkey.last_usage_date != 0) {
                subtitleView.setText(LocaleController.formatString(R.string.PasskeyLastUsedOn, LocaleController.formatDateTime(passkey.last_usage_date, false)));
            } else {
                subtitleView.setText(LocaleController.formatString(R.string.PasskeyCreatedOn, LocaleController.formatDateTime(passkey.date, false)));
            }
            optionsView.setOnClickListener(onOptions);
            setWillNotDraw(!(needDivider = divider));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
                if (dividerPaint == null)
                    dividerPaint = Theme.dividerPaint;
                canvas.drawRect(dp(LocaleController.isRTL ? 0 : 72), getMeasuredHeight() - 1, getWidth() - dp(LocaleController.isRTL ? 72 : 0), getMeasuredHeight(), dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY));
        }


        public static class Factory extends UItem.UItemFactory<PasskeyCell> {
            static { setup(new Factory()); }

            @Override
            public PasskeyCell createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new PasskeyCell(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((PasskeyCell) view).set((TL_account.Passkey) item.object, item.clickCallback, divider);
            }

            public static UItem of(TL_account.Passkey passkey, View.OnClickListener options) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.object = passkey;
                item.clickCallback = options;
                return item;
            }
        }
    }

    @RequiresApi(api = 28)
    public static void showLearnSheet(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider, boolean withCreateButton) {
        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), 0, dp(16), dp(8));
        b.setCustomView(linearLayout);

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setAnimation(R.raw.passkey, dp(115), dp(115));
        imageView.playAnimation();
        linearLayout.addView(imageView, LayoutHelper.createLinear(115, 115, Gravity.CENTER, 0, 0, 0, 9));

        TextView title = TextHelper.makeTextView(context, 18, Theme.key_dialogTextBlack, true, resourcesProvider);
        title.setGravity(Gravity.CENTER);
        title.setText(getString(R.string.PasskeyFeatureTitle));
        linearLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 32, 0, 32, 6));

        TextView subtitle = TextHelper.makeTextView(context, 14, Theme.key_dialogTextBlack, false, resourcesProvider);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setText(getString(R.string.PasskeyFeatureSubtitle));
        linearLayout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 32, 0, 32, 24));

        ExplainStarsSheet.FeatureCell f = new ExplainStarsSheet.FeatureCell(context, ExplainStarsSheet.FeatureCell.STYLE_SHEET, resourcesProvider);
        f.set(R.drawable.msg2_permissions, getString(R.string.PasskeyFeature1Title), getString(R.string.PasskeyFeature1Subtitle));
        linearLayout.addView(f, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        f = new ExplainStarsSheet.FeatureCell(context, ExplainStarsSheet.FeatureCell.STYLE_SHEET, resourcesProvider);
        f.set(R.drawable.menu_face, getString(R.string.PasskeyFeature2Title), getString(R.string.PasskeyFeature2Subtitle));
        linearLayout.addView(f, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        f = new ExplainStarsSheet.FeatureCell(context, ExplainStarsSheet.FeatureCell.STYLE_SHEET, resourcesProvider);
        f.set(R.drawable.menu_privacy, getString(R.string.PasskeyFeature3Title), getString(R.string.PasskeyFeature3Subtitle));
        linearLayout.addView(f, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        BottomSheet sheet = b.create();

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.PasskeyFeatureButton), false);
        button.setOnClickListener(v -> {
            if (button.isLoading()) return;
            button.setLoading(true);
            PasskeysController.create(context, currentAccount, (passkey, error) -> {
                button.setLoading(false);
                if ("CANCELLED".equalsIgnoreCase(error))
                    return;
                if ("EMPTY".equalsIgnoreCase(error)) {
                    new AlertDialog.Builder(context)
                        .setTitle(getString(R.string.PasskeyNoOptionsTitle))
                        .setMessage(getString(R.string.PasskeyNoOptionsText))
                        .setPositiveButton(getString(R.string.OK), null)
                        .setOnDismissListener((di) -> {
                            sheet.dismiss();
                        })
                        .show();
                    return;
                }

                BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                if (fragment == null) return;

                if (error != null) {
                    BulletinFactory.of(sheet.topBulletinContainer, sheet.getResourcesProvider()).showForError(error);
                } else if (passkey != null) {
                    MessagesController.getInstance(currentAccount).removeSuggestion(0, "SETUP_PASSKEY");
                    if (fragment instanceof PasskeysActivity) {
                        sheet.dismiss();
                        ((PasskeysActivity) fragment).added(passkey);
                    } else if (fragment instanceof PrivacySettingsActivity) {
                        sheet.dismiss();
                        ArrayList<TL_account.Passkey> passkeys = ((PrivacySettingsActivity) fragment).currentPasskeys;
                        if (passkeys == null) passkeys = new ArrayList<>();
                        passkeys.add(passkey);
                        ((PrivacySettingsActivity) fragment).updateRows(true);
                        fragment.presentFragment(new PasskeysActivity(passkeys));
                    } else {
                        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(new TL_account.getPasskeys(), AndroidUtilities::runOnUIThread, (res, err) -> {
                            if (res != null) {
                                sheet.dismiss();
                                for (int i = 0; i < res.passkeys.size(); ++i) {
                                    if (TextUtils.equals(res.passkeys.get(i).id, passkey.id)) {
                                        res.passkeys.remove(i);
                                        i--;
                                    }
                                }
                                BaseFragment fragment2 = LaunchActivity.getSafeLastFragment();
                                if (fragment2 == null) return;
                                final PasskeysActivity activity = new PasskeysActivity(res.passkeys);
                                fragment2.presentFragment(activity);
                                AndroidUtilities.runOnUIThread(() -> activity.added(passkey), 150);
                            } else if (err != null) {
                                BulletinFactory.of(sheet.topBulletinContainer, sheet.getResourcesProvider()).showForError(error);
                            }
                        });
                    }
                }
            });
        });

        if (withCreateButton) {
            linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 0, 8));
        }

        sheet.fixNavigationBar();
        sheet.show();
    }

}
