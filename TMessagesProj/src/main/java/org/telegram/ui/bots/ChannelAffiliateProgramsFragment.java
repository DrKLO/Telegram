package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatSpannable;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarsIntroActivity.formatStarsAmount;
import static org.telegram.ui.Stars.StarsIntroActivity.formatStarsAmountShort;
import static org.telegram.ui.Stars.StarsIntroActivity.replaceStarsWithPlain;
import static org.telegram.ui.bots.AffiliateProgramFragment.percents;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_payments;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.GLIcon.Icon3D;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.FilterCreateActivity;
import org.telegram.ui.GradientHeaderActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.BotStarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public class ChannelAffiliateProgramsFragment extends GradientHeaderActivity implements NotificationCenter.NotificationCenterDelegate {

    public final long dialogId;

    private FrameLayout aboveTitleView;
    private GLIconTextureView iconTextureView;
    private View emptyLayout;

    public ChannelAffiliateProgramsFragment(long dialogId) {
        this.dialogId = dialogId;

        setWhiteBackground(true);
        setMinusHeaderHeight(dp(60));
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.channelConnectedBotsUpdate);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.channelSuggestedBotsUpdate);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.channelConnectedBotsUpdate);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.channelSuggestedBotsUpdate);
    }

    @Override
    public View createView(Context context) {
        useFillLastLayoutManager = false;
        particlesViewHeight = dp(32 + 190 + 16);
        emptyLayout = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY));
            }
        };
        emptyLayout.setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray));

        super.createView(context);

        aboveTitleView = new FrameLayout(context);
        aboveTitleView.setClickable(true);
        iconTextureView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE, Icon3D.TYPE_DEAL);
        iconTextureView.mRenderer.colorKey1 = Theme.key_starsGradient1;
        iconTextureView.mRenderer.colorKey2 = Theme.key_starsGradient2;
        iconTextureView.mRenderer.updateColors();
        iconTextureView.setStarParticlesView(particlesView);
        aboveTitleView.addView(iconTextureView, LayoutHelper.createFrame(190, 190, Gravity.CENTER, 0, 32, 0, 12));
        configureHeader(getString(R.string.ChannelAffiliateProgramTitle), AndroidUtilities.replaceTags(getString(R.string.ChannelAffiliateProgramText)), aboveTitleView, null);

        listView.setOnItemClickListener((view, position) -> {
            if (adapter == null) return;
            UItem item = adapter.getItem(position);
            if (item.object instanceof TL_payments.starRefProgram) {
                showConnectAffiliateAlert(context, currentAccount, ((TL_payments.starRefProgram) item.object), dialogId, resourceProvider, false);
            } else if (item.object instanceof TL_payments.connectedBotStarRef) {
                showShareAffiliateAlert(context, currentAccount, ((TL_payments.connectedBotStarRef) item.object), dialogId, resourceProvider);
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (adapter == null) return false;
            UItem item = adapter.getItem(position);
            if (item.object instanceof TL_payments.connectedBotStarRef) {
                TL_payments.connectedBotStarRef bot = (TL_payments.connectedBotStarRef) item.object;
                TLRPC.User botUser = MessagesController.getInstance(currentAccount).getUser(bot.bot_id);
                ItemOptions.makeOptions(this, view)
                    .addIf(botUser.bot_has_main_app, R.drawable.msg_bot, getString(R.string.ProfileBotOpenApp), () -> {
                        getMessagesController().openApp(botUser, getClassGuid());
                    })
                    .addIf(!botUser.bot_has_main_app, R.drawable.msg_bot, getString(R.string.BotWebViewOpenBot), () -> {
                        presentFragment(ChatActivity.of(bot.bot_id));
                    })
                    .add(R.drawable.msg_link2, getString(R.string.CopyLink), () -> {
                        AndroidUtilities.addToClipboard(bot.url);
                        BulletinFactory.of(this)
                            .createSimpleBulletin(R.raw.copy, getString(R.string.AffiliateProgramLinkCopiedTitle), AndroidUtilities.replaceTags(formatString(R.string.AffiliateProgramLinkCopiedText, percents(bot.commission_permille), UserObject.getUserName(botUser))))
                            .show();
                    })
//                    .addIf(bot.revoked, R.drawable.msg_leave, getString(R.string.Restore), () -> {
//                        final AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
//                        progressDialog.showDelayed(200);
//                        TL_payments.editConnectedStarRefBot req = new TL_payments.editConnectedStarRefBot();
//                        req.link = bot.url;
//                        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
//                        req.revoked = false;
//                        getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
//                            if (res instanceof TL_payments.connectedStarRefBots) {
//                                TL_payments.connectedStarRefBots r = (TL_payments.connectedStarRefBots) res;
//                                BotStarsController.getInstance(currentAccount).getChannelConnectedBots(dialogId).applyEdit(r);
//                                adapter.update(true);
//                            }
//                            progressDialog.dismiss();
//                        }));
//                    })
                    .addIf(!bot.revoked, R.drawable.msg_leave, getString(R.string.LeaveAffiliateLinkButton), true, () -> {
                        new AlertDialog.Builder(context, resourceProvider)
                            .setTitle(getString(R.string.LeaveAffiliateLink))
                            .setMessage(AndroidUtilities.replaceTags(formatString(R.string.LeaveAffiliateLinkAlert, UserObject.getUserName(botUser))))
                            .setPositiveButton(getString(R.string.LeaveAffiliateLinkButton), (d, w) -> {
                                final AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                                progressDialog.showDelayed(200);
                                TL_payments.editConnectedStarRefBot req = new TL_payments.editConnectedStarRefBot();
                                req.link = bot.url;
                                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                                req.revoked = true;
                                getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                                    if (res instanceof TL_payments.connectedStarRefBots) {
                                        TL_payments.connectedStarRefBots r = (TL_payments.connectedStarRefBots) res;
                                        BotStarsController.getInstance(currentAccount).getChannelConnectedBots(dialogId).applyEdit(r);
                                        BotStarsController.getInstance(currentAccount).getChannelSuggestedBots(dialogId).reload();
                                        adapter.update(true);
                                    }
                                    progressDialog.dismiss();
                                }));
                            })
                            .setNegativeButton(getString(R.string.Cancel), null)
                            .makeRed(AlertDialog.BUTTON_POSITIVE)
                            .show();
                    })
                    .setGravity(Gravity.RIGHT)
                    .show();
                return true;
            }
            return false;
        });
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        listView.setItemAnimator(itemAnimator);
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (isLoadingVisible() || !recyclerView.canScrollVertically(1)) {
                    BotStarsController.getInstance(currentAccount).getChannelConnectedBots(dialogId).load();
                    BotStarsController.getInstance(currentAccount).getChannelSuggestedBots(dialogId).load();
                }
            }
        });

        return fragmentView;
    }

    private boolean isLoadingVisible() {
        for (int i = 0; i < listView.getChildCount(); ++i) {
            if (listView.getChildAt(i) instanceof FlickerLoadingView)
                return true;
        }
        return false;
    }


    private UniversalAdapter adapter;
    @Override
    protected RecyclerView.Adapter<?> createAdapter() {
        return adapter = new UniversalAdapter(listView, getContext(), currentAccount, classGuid, true, this::fillItems, getResourceProvider()) {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                if (viewType == UniversalAdapter.VIEW_TYPE_ANIMATED_HEADER) {
                    HeaderCell headerCell = new HeaderCell(getContext(), Theme.key_windowBackgroundWhiteBlueHeader, 21, 0, false, resourceProvider);
                    headerCell.setHeight(40 - 15);
                    return new RecyclerListView.Holder(headerCell);
                }
                return super.onCreateViewHolder(parent, viewType);
            }
        };
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (getContext() == null) {
            return;
        }

        items.add(UItem.asFullyCustom(getHeader(getContext())));
        items.add(AffiliateProgramFragment.FeatureCell.Factory.as(R.drawable.menu_feature_reliable, getString(R.string.ChannelAffiliateProgramFeature1Title), getString(R.string.ChannelAffiliateProgramFeature1)));
        items.add(AffiliateProgramFragment.FeatureCell.Factory.as(R.drawable.menu_feature_transparent, getString(R.string.ChannelAffiliateProgramFeature2Title), getString(R.string.ChannelAffiliateProgramFeature2)));
        items.add(AffiliateProgramFragment.FeatureCell.Factory.as(R.drawable.menu_feature_simple, getString(R.string.ChannelAffiliateProgramFeature3Title), getString(R.string.ChannelAffiliateProgramFeature3)));
        items.add(UItem.asShadow(1, null));

        final BotStarsController.ChannelConnectedBots connectedBots = BotStarsController.getInstance(currentAccount).getChannelConnectedBots(dialogId);
        if (!connectedBots.bots.isEmpty() || connectedBots.count > 0) {
            items.add(UItem.asHeader(getString(R.string.ChannelAffiliateProgramMyPrograms)));
            for (int i = 0; i < connectedBots.bots.size(); ++i) {
                TL_payments.connectedBotStarRef bot = connectedBots.bots.get(i);
                items.add(BotCell.Factory.as(bot));
            }
            if (!connectedBots.endReached || connectedBots.isLoading()) {
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
            }
            items.add(UItem.asShadow(2, null));
        }

        final BotStarsController.ChannelSuggestedBots suggestedBots = BotStarsController.getInstance(currentAccount).getChannelSuggestedBots(dialogId);
        if (!suggestedBots.bots.isEmpty() || suggestedBots.count > 0) {
            items.add(HeaderSortCell.Factory.as(getString(R.string.ChannelAffiliateProgramPrograms), sortText(suggestedBots.getSort())));
            for (int i = 0; i < suggestedBots.bots.size(); ++i) {
                items.add(BotCell.Factory.as(suggestedBots.bots.get(i)));
            }
            if (!suggestedBots.endReached || suggestedBots.isLoading()) {
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
            }
            items.add(UItem.asShadow(3, null));
        }

        items.add(UItem.asCustom(emptyLayout));

    }

    private CharSequence sortText(BotStarsController.ChannelSuggestedBots.Sort sort) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append(getString(R.string.ChannelAffiliateProgramProgramsSort)).append(" ");
        SpannableString type;
        if (sort == BotStarsController.ChannelSuggestedBots.Sort.BY_PROFITABILITY) {
            type = new SpannableString(getString(R.string.ChannelAffiliateProgramProgramsSortProfitability) + "v");
        } else if (sort == BotStarsController.ChannelSuggestedBots.Sort.BY_REVENUE) {
            type = new SpannableString(getString(R.string.ChannelAffiliateProgramProgramsSortRevenue) + "v");
        } else if (sort == BotStarsController.ChannelSuggestedBots.Sort.BY_DATE) {
            type = new SpannableString(getString(R.string.ChannelAffiliateProgramProgramsSortDate) + "v");
        } else return ssb;
        ColoredImageSpan arrowSpan = new ColoredImageSpan(R.drawable.arrow_more);
        arrowSpan.useLinkPaintColor = true;
        arrowSpan.setScale(.6f, .6f);
        type.setSpan(arrowSpan, type.length() - 1, type.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        final BotStarsController.ChannelSuggestedBots suggestedBots = BotStarsController.getInstance(currentAccount).getChannelSuggestedBots(dialogId);
        type.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                ItemOptions.makeOptions(ChannelAffiliateProgramsFragment.this, widget)
                    .addChecked(
                        sort == BotStarsController.ChannelSuggestedBots.Sort.BY_DATE,
                        getString(R.string.ChannelAffiliateProgramProgramsSortDate),
                        () -> suggestedBots.setSort(BotStarsController.ChannelSuggestedBots.Sort.BY_DATE)
                    )
                    .addChecked(
                        sort == BotStarsController.ChannelSuggestedBots.Sort.BY_REVENUE,
                        getString(R.string.ChannelAffiliateProgramProgramsSortRevenue),
                        () -> suggestedBots.setSort(BotStarsController.ChannelSuggestedBots.Sort.BY_REVENUE)
                    )
                    .addChecked(
                        sort == BotStarsController.ChannelSuggestedBots.Sort.BY_PROFITABILITY,
                        getString(R.string.ChannelAffiliateProgramProgramsSortProfitability),
                        () -> suggestedBots.setSort(BotStarsController.ChannelSuggestedBots.Sort.BY_PROFITABILITY)
                    )
                    .setGravity(Gravity.RIGHT)
                    .setDrawScrim(false)
                    .setDimAlpha(0)
                    .translate(dp(24), -dp(24))
                    .show();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setUnderlineText(false);
                ds.setColor(ds.linkColor);
            }
        }, 0, type.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.append(type);
        return ssb;
    }

    @Override
    public StarParticlesView createParticlesView() {
        return makeParticlesView(getContext(), 75, 1);
    }

    public static StarParticlesView makeParticlesView(Context context, int particlesCount, int type) {
        return new StarParticlesView(context) {
            @Override
            protected void configure() {
                super.configure();
                drawable.useGradient = true;
                drawable.useBlur = false;
                drawable.forceMaxAlpha = true;
                drawable.checkBounds = true;
                drawable.init();
            }

            @Override
            protected int getStarsRectWidth() {
                return getMeasuredWidth();
            }

            { setClipWithGradient(); }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        if (iconTextureView != null) {
            iconTextureView.setPaused(false);
            iconTextureView.setDialogVisible(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (iconTextureView != null) {
            iconTextureView.setPaused(true);
            iconTextureView.setDialogVisible(true);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.channelConnectedBotsUpdate) {
            Long did = (Long) args[0];
            if (did == dialogId) {
                if (adapter != null) {
                    adapter.update(true);
                }
                BotStarsController.getInstance(currentAccount).getChannelConnectedBots(dialogId).load();
            }
        } else if (id == NotificationCenter.channelSuggestedBotsUpdate) {
            Long did = (Long) args[0];
            if (did == dialogId) {
                if (adapter != null) {
                    adapter.update(true);
                }
            }
        }
    }

    public static class BotCell extends FrameLayout {

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;

        private final BackupImageView imageView;
        private final View linkBgView, linkFg2View;
        private final ImageView linkFgView;
        private final LinearLayout textLayout;
        private final TextView titleView;
        private final TextView textView;
        private final ImageView arrowView;

        public BotCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(46));
            addView(imageView, LayoutHelper.createFrame(46, 46, Gravity.CENTER_VERTICAL | Gravity.LEFT, 13, 0, 13, 0));

            linkBgView = new View(context);
            linkBgView.setBackground(Theme.createCircleDrawable(dp(11), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
            addView(linkBgView, LayoutHelper.createFrame(22, 22, Gravity.LEFT | Gravity.CENTER_VERTICAL, 40, 15, 0, 0));

            linkFg2View = new View(context);
            linkFg2View.setBackground(Theme.createCircleDrawable(dp(19.33f / 2), Theme.getColor(Theme.key_color_green, resourcesProvider)));
            addView(linkFg2View, LayoutHelper.createFrame(19.33f, 19.33f, Gravity.LEFT | Gravity.CENTER_VERTICAL, 41.33f, 15, 0, 0));

            linkFgView = new ImageView(context);
            linkFgView.setScaleX(0.6f);
            linkFgView.setScaleY(0.6f);
            addView(linkFgView, LayoutHelper.createFrame(19.33f, 19.33f, Gravity.LEFT | Gravity.CENTER_VERTICAL, 41.33f, 15, 0, 0));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 72 - 6, 8.66f, 16 - 6, 0));

            titleView = new TextView(context);
            titleView.setMaxLines(1);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            NotificationCenter.listenEmojiLoading(titleView);
            textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 6, 0, 24, 0));

            textView = new TextView(context);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            textLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 6, 1, 24, 0));

            arrowView = new ImageView(context);
            arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_switchTrack, resourcesProvider), PorterDuff.Mode.SRC_IN));
            arrowView.setImageResource(R.drawable.msg_arrowright);
            arrowView.setScaleType(ImageView.ScaleType.CENTER);
            addView(arrowView, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 10, 0));
        }

        private boolean needDivider;

        public void set(TL_payments.connectedBotStarRef bot, boolean showArrow, boolean needDivider) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(bot.bot_id);

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(user);
            imageView.setForUserOrChat(user, avatarDrawable);

            titleView.setText(Emoji.replaceEmoji(UserObject.getUserName(user), titleView.getPaint().getFontMetricsInt(), false));
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            if (bot.commission_permille > 0) {
                ssb.append(" d");
                FilterCreateActivity.NewSpan span = new FilterCreateActivity.NewSpan(10);
                span.setColor(Theme.getColor(Theme.key_color_green));
                span.setText(percents(bot.commission_permille));
                ssb.setSpan(span, 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (bot.duration_months == 0) {
                ssb.append(getString(R.string.Lifetime));
            } else if (bot.duration_months < 12 || bot.duration_months % 12 != 0) {
                ssb.append(formatPluralString("Months", bot.duration_months));
            } else {
                ssb.append(formatPluralString("Years", bot.duration_months / 12));
            }
            textView.setText(ssb);

            arrowView.setVisibility(showArrow ? View.VISIBLE : View.GONE);
            linkBgView.setVisibility(View.VISIBLE);
            linkFgView.setVisibility(View.VISIBLE);
            linkFg2View.setVisibility(View.VISIBLE);
            linkFg2View.setBackground(Theme.createCircleDrawable(dp(19.33f / 2), Theme.getColor(bot.revoked ? Theme.key_color_red : Theme.key_color_green, resourcesProvider)));
            linkFgView.setImageResource(bot.revoked ? R.drawable.msg_link_2 : R.drawable.msg_limit_links);
            linkFgView.setScaleX(bot.revoked ? 0.8f : 0.6f);
            linkFgView.setScaleY(bot.revoked ? 0.8f : 0.6f);

            setWillNotDraw(!(this.needDivider = needDivider));
        }

        public void set(TL_payments.starRefProgram bot, boolean showArrow, boolean needDivider) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(bot.bot_id);

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(user);
            imageView.setForUserOrChat(user, avatarDrawable);

            titleView.setText(UserObject.getUserName(user));
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            if (bot.commission_permille > 0) {
                ssb.append(" d");
                FilterCreateActivity.NewSpan span = new FilterCreateActivity.NewSpan(10);
                span.setColor(Theme.getColor(Theme.key_color_green));
                span.setText(percents(bot.commission_permille));
                ssb.setSpan(span, 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (bot.duration_months == 0) {
                ssb.append(getString(R.string.Lifetime));
            } else if (bot.duration_months < 12 || bot.duration_months % 12 != 0) {
                ssb.append(formatPluralString("Months", bot.duration_months));
            } else {
                ssb.append(formatPluralString("Years", bot.duration_months / 12));
            }
            textView.setText(ssb);

            arrowView.setVisibility(showArrow ? View.VISIBLE : View.GONE);
            linkBgView.setVisibility(View.GONE);
            linkFgView.setVisibility(View.GONE);
            linkFg2View.setVisibility(View.GONE);

            setWillNotDraw(!(this.needDivider = needDivider));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(58), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            if (needDivider) {
                canvas.drawRect(dp(72), getHeight() - 1, getWidth(), getHeight(), Theme.dividerPaint);
            }
        }

        public static class Factory extends UItem.UItemFactory<BotCell> {
            static { setup(new Factory()); }

            @Override
            public BotCell createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new BotCell(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof TL_payments.connectedBotStarRef) {
                    ((BotCell) view).set((TL_payments.connectedBotStarRef) item.object, item.red, divider);
                } else if (item.object instanceof TL_payments.starRefProgram) {
                    ((BotCell) view).set((TL_payments.starRefProgram) item.object, item.red, divider);
                }
            }

            public static UItem as(Object obj) {
                return as(obj, true);
            }

            public static UItem as(Object obj, boolean showArrow) {
                UItem item = UItem.ofFactory(Factory.class);
                item.object = obj;
                item.red = showArrow;
                return item;
            }
        }
    }

    private static class HeaderSortCell extends HeaderCell {

        private final LinkSpanDrawable.LinksTextView subtextView;

        public HeaderSortCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);

            subtextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            subtextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            subtextView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
            subtextView.setPadding(dp(4), 0, dp(4), 0);
            addView(subtextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 14 - 4, 20, 14 - 4, 0));
        }

        public void set(CharSequence text, CharSequence subtext) {
            setText(text);
            subtextView.setText(subtext);
        }

        public static class Factory extends UItem.UItemFactory<HeaderSortCell> {
            static { setup(new Factory()); }

            @Override
            public HeaderSortCell createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new HeaderSortCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((HeaderSortCell) view).set(item.text, item.subtext);
            }

            public static UItem as(CharSequence text, CharSequence sortText) {
                UItem item = UItem.ofFactory(Factory.class);
                item.text = text;
                item.subtext = sortText;
                return item;
            }

            @Override
            public boolean isClickable() {
                return false;
            }
        }
    }

    public static void showConnectAffiliateAlert(Context context, int currentAccount, TL_payments.starRefProgram bot, long dialogId, Theme.ResourcesProvider resourcesProvider, boolean forceOpenStats) {
        if (bot == null || context == null)
            return;

        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);
        final long[] selectedDialogId = new long[1];
        selectedDialogId[0] = dialogId;
        final TLRPC.User botUser = MessagesController.getInstance(currentAccount).getUser(bot.bot_id);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), dp(20), dp(16), dp(8));
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);

        FrameLayout topView = new FrameLayout(context);
        topView.setClipToPadding(false);
        topView.setClipChildren(false);

        FrameLayout fromView = new FrameLayout(context);
        fromView.setClipToPadding(false);
        fromView.setClipChildren(false);
        topView.addView(fromView, LayoutHelper.createFrame(60, 60, Gravity.CENTER_VERTICAL | Gravity.LEFT, 0, 0, 0, 0));

        BackupImageView imageView1 = new BackupImageView(context);
        imageView1.setRoundRadius(dp(30));
        AvatarDrawable avatarDrawable2 = new AvatarDrawable();
        avatarDrawable2.setInfo(botUser);
        imageView1.setForUserOrChat(botUser, avatarDrawable2);
        ScaleStateListAnimator.apply(imageView1);
        fromView.addView(imageView1, LayoutHelper.createFrame(60, 60, Gravity.FILL));

        if (bot.daily_revenue_per_user.positive()) {
            FrameLayout badge1Outer = new FrameLayout(context);
            badge1Outer.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_dialogBackground, resourcesProvider)));
            badge1Outer.setPadding(dp(1.33f), dp(1.33f), dp(1.33f), dp(1.33f));
            TextView badge1 = new TextView(context);
            badge1.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_color_green, resourcesProvider)));
            badge1.setTypeface(AndroidUtilities.bold());
            badge1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            badge1.setPadding(dp(5.33f), 0, dp(5.33f), 0);
            badge1.setTextColor(Color.WHITE);
            badge1.setGravity(Gravity.CENTER);
            ColoredImageSpan[] spans = new ColoredImageSpan[1];
            badge1.setText(StarsIntroActivity.replaceStars("⭐️ " + formatStarsAmountShort(bot.daily_revenue_per_user, 1.0f, ','), 0.75f, spans));
            badge1Outer.addView(badge1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 15.66f));
            fromView.addView(badge1Outer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, -4));
        }

        ImageView arrowView = new ImageView(context);
        arrowView.setImageResource(R.drawable.msg_arrow_avatar);
        arrowView.setScaleType(ImageView.ScaleType.CENTER);
        arrowView.setTranslationX(-dp(8.33f / 4));
        arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7, resourcesProvider), PorterDuff.Mode.SRC_IN));
        topView.addView(arrowView, LayoutHelper.createFrame(36, 60, Gravity.CENTER, 60, 0, 60, 0));

        FrameLayout toView = new FrameLayout(context);
        toView.setClipToPadding(false);
        toView.setClipChildren(false);
        topView.addView(toView, LayoutHelper.createFrame(60, 60, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 5.66f, 0));

        BackupImageView imageView2 = new BackupImageView(context);
        imageView2.setRoundRadius(dp(30));
        toView.addView(imageView2, LayoutHelper.createFrame(60, 60, Gravity.FILL));

        FrameLayout badge2Outer = new FrameLayout(context);
        badge2Outer.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_dialogBackground, resourcesProvider)));
        badge2Outer.setPadding(dp(1.33f), dp(1.33f), dp(1.33f), dp(1.33f));
        TextView badge2 = new TextView(context);
        badge2.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        badge2.setTypeface(AndroidUtilities.bold());
        badge2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        badge2.setPadding(dp(5.33f), 0, dp(5.33f), 0);
        badge2.setTextColor(Color.WHITE);
        badge2.setGravity(Gravity.CENTER);
        SpannableString sb = new SpannableString("s " + percents(bot.commission_permille));
        ColoredImageSpan span = new ColoredImageSpan(R.drawable.msg_link_1);
        span.setScale(.65f, .65f);
        span.spaceScaleX = 0.7f;
        span.translate(dp(-2), dp(0));
        sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        badge2.setText(sb);
        badge2Outer.addView(badge2, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 15.66f));
        toView.addView(badge2Outer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, -4));

        linearLayout.addView(topView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        TextView titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(getString(R.string.ChannelAffiliateProgramJoinTitle));
        titleView.setTypeface(AndroidUtilities.bold());
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 21, 0, 9));

        LinearLayout botChip = new LinearLayout(context);
        botChip.setOrientation(LinearLayout.HORIZONTAL);
        botChip.setBackground(Theme.createRoundRectDrawable(dp(28), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider)));
        TextView botChipTextView = new TextView(context);
        botChipTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        botChipTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        botChipTextView.setText(LocaleController.formatString(R.string.ChannelAffiliateProgramJoinViewBot, DialogObject.getName(currentAccount, bot.bot_id)));
        botChip.addView(botChipTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
        ImageView botArrowView = new ImageView(context);
        botArrowView.setScaleType(ImageView.ScaleType.CENTER);
        botArrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider), PorterDuff.Mode.SRC_IN));
        botArrowView.setImageResource(R.drawable.settings_arrow);
        botArrowView.setScaleX(1.2f);
        botArrowView.setScaleY(1.2f);
        botChip.addView(botArrowView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 5, 0, 8, 0));
        linearLayout.addView(botChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_HORIZONTAL, 4, 0, 4, 0));
        ScaleStateListAnimator.apply(botChip);

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER);
        NotificationCenter.listenEmojiLoading(textView);
        SpannableString revenueStars = new SpannableString(formatStarsAmountShort(bot.daily_revenue_per_user, 0.95f, ','));
        revenueStars.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, revenueStars.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(StarsIntroActivity.replaceStarsWithPlain(formatSpannable(R.string.ChannelAffiliateProgramJoinRevenue, revenueStars), .725f));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 20));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER);
        NotificationCenter.listenEmojiLoading(textView);
        textView.setText(Emoji.replaceEmoji(AndroidUtilities.replaceTags(formatString(R.string.ChannelAffiliateProgramJoinText, UserObject.getUserName(botUser), percents(bot.commission_permille), bot.duration_months <= 0 ? getString(R.string.ChannelAffiliateProgramJoinText_Lifetime) : bot.duration_months < 12 || bot.duration_months % 12 != 0 ? formatPluralString("ChannelAffiliateProgramJoinText_Months", bot.duration_months) : formatPluralString("ChannelAffiliateProgramJoinText_Years", bot.duration_months / 12))), textView.getPaint().getFontMetricsInt(), false));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 22));

        BackupImageView chipImageView;
        TextView chipTextView;
        LinearLayout chipLayout;
        if (dialogId >= 0) {
            TextView sendToTextView = new TextView(context);
            sendToTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            sendToTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            sendToTextView.setGravity(Gravity.CENTER);
            sendToTextView.setText(getString(R.string.ChannelAffiliateProgramLinkSendTo));
            linearLayout.addView(sendToTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 0, 20, 0));

            chipLayout = new LinearLayout(context);
            chipLayout.setOrientation(LinearLayout.HORIZONTAL);
            chipLayout.setBackground(Theme.createRoundRectDrawable(dp(28), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider)));
            chipLayout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(28), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider), Theme.blendOver(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider), Theme.getColor(Theme.key_listSelector, resourcesProvider))));
            chipImageView = new BackupImageView(context);
            chipImageView.setRoundRadius(dp(14));
            chipLayout.addView(chipImageView, LayoutHelper.createLinear(28, 28));
            chipTextView = new TextView(context);
            chipTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            chipTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            chipLayout.addView(chipTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));
            ImageView selectView = new ImageView(context);
            selectView.setScaleType(ImageView.ScaleType.CENTER);
            selectView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider), PorterDuff.Mode.SRC_IN));
            selectView.setImageResource(R.drawable.arrows_select);
            chipLayout.addView(selectView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 2, 0, 5, 0));
            linearLayout.addView(chipLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_HORIZONTAL, 0, 11, 0, 20));
        } else {
            chipLayout = null;
            chipTextView = null;
            chipImageView = null;
        }

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.ChannelAffiliateProgramJoinButton), false);
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        LinkSpanDrawable.LinksTextView infoTextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        infoTextView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.ChannelAffiliateProgramJoinButtonInfo), () -> {
            Browser.openUrl(context, getString(R.string.ChannelAffiliateProgramJoinButtonInfoLink));
        }));
        infoTextView.setGravity(Gravity.CENTER);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        infoTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
        infoTextView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        linearLayout.addView(infoTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 14, 14, 14, 6));

        b.setCustomView(linearLayout);

        BottomSheet sheet = b.create();
        imageView1.setOnClickListener(v -> {
            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment != null) {
                sheet.dismiss();
                lastFragment.presentFragment(ProfileActivity.of(bot.bot_id));
            }
        });
        button.setOnClickListener(v -> {
            if (button.isLoading()) return;
            button.setLoading(true);
            final long finalDialogId = selectedDialogId[0];
            TL_payments.connectStarRefBot req = new TL_payments.connectStarRefBot();
            req.bot = MessagesController.getInstance(currentAccount).getInputUser(bot.bot_id);
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(finalDialogId);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                button.setLoading(false);
                if (res instanceof TL_payments.connectedStarRefBots) {
                    TL_payments.connectedStarRefBots r = (TL_payments.connectedStarRefBots) res;
                    BotStarsController.getInstance(currentAccount).getChannelConnectedBots(finalDialogId).apply(r);
                    sheet.dismiss();
                    TL_payments.connectedBotStarRef connectedBot = null;
                    for (int i = 0; i < r.connected_bots.size(); ++i) {
                        TL_payments.connectedBotStarRef c = r.connected_bots.get(i);
                        if (c.bot_id == bot.bot_id) {
                            connectedBot = c;
                            break;
                        }
                    }
                    if (dialogId != finalDialogId || forceOpenStats) {
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null && (!(lastFragment instanceof ChannelAffiliateProgramsFragment) || ((ChannelAffiliateProgramsFragment) lastFragment).dialogId != finalDialogId)) {
                            lastFragment.presentFragment(new ChannelAffiliateProgramsFragment(finalDialogId));
                        }
                    }
                    if (connectedBot != null) {
                        BotStarsController.getInstance(currentAccount).getChannelSuggestedBots(finalDialogId).remove(connectedBot.bot_id);
                        BottomSheet shareSheet = showShareAffiliateAlert(context, currentAccount, connectedBot, finalDialogId, resourcesProvider);
                        BulletinFactory.of(shareSheet.topBulletinContainer, resourcesProvider)
                            .createUsersBulletin(botUser, getString(R.string.AffiliateProgramJoinedTitle), getString(R.string.AffiliateProgramJoinedText))
                            .show();
                    }
                } else if (err != null) {
                    BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider).showForError(err);
                }
            }));
        });
        sheet.setOnDismissListener(d -> {});
        Runnable updateDialog = () -> {
            if (selectedDialogId[0] >= 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(selectedDialogId[0]);
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                avatarDrawable.setInfo(user);
                imageView2.setForUserOrChat(user, avatarDrawable);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-selectedDialogId[0]);
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                avatarDrawable.setInfo(chat);
                imageView2.setForUserOrChat(chat, avatarDrawable);
            }
            if (selectedDialogId[0] >= 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(selectedDialogId[0]);
                if (chipImageView != null) {
                    AvatarDrawable avatarDrawable1 = new AvatarDrawable();
                    avatarDrawable1.setInfo(user);
                    chipImageView.setForUserOrChat(user, avatarDrawable1);
                }
                if (chipTextView != null) {
                    chipTextView.setText(UserObject.getUserName(user));
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-selectedDialogId[0]);
                if (chipImageView != null) {
                    AvatarDrawable avatarDrawable1 = new AvatarDrawable();
                    avatarDrawable1.setInfo(chat);
                    chipImageView.setForUserOrChat(chat, avatarDrawable1);
                }
                if (chipTextView != null) {
                    chipTextView.setText(chat == null ? "" : chat.title);
                }
            }
        };
        updateDialog.run();
        if (chipLayout != null) {
            BotStarsController.getInstance(currentAccount).loadAdminedBots();
            BotStarsController.getInstance(currentAccount).loadAdminedChannels();
            final View chip = chipLayout;
            chipLayout.setOnClickListener(v -> {
                ArrayList<TLObject> chats = BotStarsController.getInstance(currentAccount).getAdmined();
                chats.add(0, UserConfig.getInstance(currentAccount).getCurrentUser());

                ItemOptions i = ItemOptions.makeOptions(sheet.getContainerView(), resourcesProvider, chip);
                for (TLObject obj : chats) {
                    long did;
                    if (obj instanceof TLRPC.User) {
                        did = ((TLRPC.User) obj).id;
                    } else if (obj instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat) obj;
                        if (!ChatObject.isChannelAndNotMegaGroup(chat))
                            continue;
                        did = -chat.id;
                    } else continue;
                    i.addChat(obj, did == selectedDialogId[0], () -> {
                        selectedDialogId[0] = did;
                        updateDialog.run();
                    });
                }
                i.setDrawScrim(false)
                    .setDimAlpha(0)
                    .setGravity(Gravity.RIGHT)
                    .translate(dp(24), 0)
                    .show();
            });
        }

        botChip.setOnClickListener(v -> {
            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment != null) {
                sheet.dismiss();
                Bundle args = new Bundle();
                args.putLong("user_id", bot.bot_id);
                lastFragment.presentFragment(new ChatActivity(args) {
                    @Override
                    public void onFragmentDestroy() {
                        super.onFragmentDestroy();
                        sheet.makeAttached(null);
                        sheet.show();
                    }
                });
            }
        });

        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        sheet.show();
    }

    public static BottomSheet showShareAffiliateAlert(Context context, int currentAccount, TL_payments.connectedBotStarRef bot, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        if (bot == null || context == null)
            return null;

        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);
        final TLRPC.User botUser = MessagesController.getInstance(currentAccount).getUser(bot.bot_id);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), dp(20), dp(16), dp(8));
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);

        FrameLayout topView = new FrameLayout(context);

        View linkBgView = new View(context);
        linkBgView.setBackground(Theme.createCircleDrawable(dp(40), Theme.getColor(bot.revoked ? Theme.key_color_red : Theme.key_featuredStickers_addButton, resourcesProvider)));
        topView.addView(linkBgView, LayoutHelper.createFrame(80, 80, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));

        ImageView linkFgView = new ImageView(context);
        linkFgView.setScaleType(ImageView.ScaleType.CENTER);
        linkFgView.setImageResource(bot.revoked ? R.drawable.msg_link_2 : R.drawable.msg_limit_links);
        linkFgView.setScaleX(bot.revoked ? 2 : 1.8f);
        linkFgView.setScaleY(bot.revoked ? 2 : 1.8f);
        topView.addView(linkFgView, LayoutHelper.createFrame(80, 80, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));

        if (bot.participants > 0) {
            FrameLayout countLayout = new FrameLayout(context);
            countLayout.setBackground(Theme.createRoundRectDrawable(dp(50), Theme.getColor(Theme.key_dialogBackground, resourcesProvider)));
            topView.addView(countLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 66, 0, 0));

            TextView textView = new TextView(context);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            textView.setBackground(Theme.createRoundRectDrawable(dp(19 / 2.0f), Theme.getColor(bot.revoked ? Theme.key_color_red : Theme.key_color_green, resourcesProvider)));
            textView.setTextColor(0xFFFFFFFF);
            textView.setPadding(dp(6.66f), 0, dp(6.66f), 0);
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            ssb.append("s ");
            ColoredImageSpan groupSpan = new ColoredImageSpan(R.drawable.mini_reply_user);
            groupSpan.setScale(0.937f, 0.937f);
            groupSpan.translate(-dp(1.33f), dp(1));
            groupSpan.spaceScaleX = .8f;
            ssb.setSpan(groupSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(String.valueOf(bot.participants));
            textView.setText(ssb);
            textView.setGravity(Gravity.CENTER);
            countLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 19, Gravity.FILL, 1.33f, 1.33f, 1.33f, 1.33f));
        }

        linearLayout.addView(topView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        TextView titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(getString(R.string.ChannelAffiliateProgramLinkTitle));
        titleView.setTypeface(AndroidUtilities.bold());
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 16, 20, 9.33f));

        LinearLayout botChip = new LinearLayout(context);
        botChip.setOrientation(LinearLayout.HORIZONTAL);
        botChip.setBackground(Theme.createRoundRectDrawable(dp(28), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider)));
        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(14));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        botChip.addView(imageView, LayoutHelper.createLinear(28, 28));
        TextView botChipTextView = new TextView(context);
        botChipTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        botChipTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        botChipTextView.setText(DialogObject.getName(currentAccount, bot.bot_id));
        avatarDrawable.setInfo(botUser);
        imageView.setForUserOrChat(botUser, avatarDrawable);
        botChip.addView(botChipTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));
        ImageView arrowView = new ImageView(context);
        arrowView.setScaleType(ImageView.ScaleType.CENTER);
        arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider), PorterDuff.Mode.SRC_IN));
        arrowView.setImageResource(R.drawable.settings_arrow);
        arrowView.setScaleX(1.2f);
        arrowView.setScaleY(1.2f);
        botChip.addView(arrowView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 5, 0, 8, 0));
        linearLayout.addView(botChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_HORIZONTAL, 4, 0, 4, 0));
        ScaleStateListAnimator.apply(botChip);

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER);
        if (bot.revoked) {
            textView.setText(AndroidUtilities.replaceTags(getString(R.string.ChannelAffiliateProgramLinkTextRevoked)));
        } else if (dialogId < 0) {
            textView.setText(AndroidUtilities.replaceTags(formatString(R.string.ChannelAffiliateProgramLinkTextChannel, percents(bot.commission_permille), UserObject.getUserName(botUser), bot.duration_months <= 0 ? getString(R.string.ChannelAffiliateProgramJoinText_Lifetime) : bot.duration_months < 12 || bot.duration_months % 12 != 0 ? formatPluralString("ChannelAffiliateProgramJoinText_Months", bot.duration_months) : formatPluralString("ChannelAffiliateProgramJoinText_Years", bot.duration_months / 12))));
        } else {
            textView.setText(AndroidUtilities.replaceTags(formatString(R.string.ChannelAffiliateProgramLinkTextUser, percents(bot.commission_permille), UserObject.getUserName(botUser), bot.duration_months <= 0 ? getString(R.string.ChannelAffiliateProgramJoinText_Lifetime) : bot.duration_months < 12 || bot.duration_months % 12 != 0 ? formatPluralString("ChannelAffiliateProgramJoinText_Months", bot.duration_months) : formatPluralString("ChannelAffiliateProgramJoinText_Years", bot.duration_months / 12))));
        }
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 19, 20, 18));

        LinearLayout chipLayout = null;
        if (!bot.revoked) {
            TextView sendToTextView = new TextView(context);
            sendToTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            sendToTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            sendToTextView.setGravity(Gravity.CENTER);
            sendToTextView.setText(getString(R.string.ChannelAffiliateProgramLinkSendTo));
            linearLayout.addView(sendToTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 0, 20, 0));

            chipLayout = new LinearLayout(context);
            chipLayout.setOrientation(LinearLayout.HORIZONTAL);
            chipLayout.setBackground(Theme.createRoundRectDrawable(dp(28), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider)));
            imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(14));
            avatarDrawable = new AvatarDrawable();
            chipLayout.addView(imageView, LayoutHelper.createLinear(28, 28));
            TextView chipTextView = new TextView(context);
            chipTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            chipTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            if (dialogId >= 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                avatarDrawable.setInfo(user);
                imageView.setForUserOrChat(user, avatarDrawable);
                chipTextView.setText(UserObject.getUserName(user));
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                avatarDrawable.setInfo(chat);
                imageView.setForUserOrChat(chat, avatarDrawable);
                chipTextView.setText(chat == null ? "" : chat.title);
            }
            chipLayout.addView(chipTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));
            ImageView selectView = new ImageView(context);
            selectView.setScaleType(ImageView.ScaleType.CENTER);
            selectView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider), PorterDuff.Mode.SRC_IN));
            selectView.setImageResource(R.drawable.arrows_select);
            chipLayout.addView(selectView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 2, 0, 5, 0));
            linearLayout.addView(chipLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_HORIZONTAL, 0, 9, 0, 22));
        }

        TextView linkView = new TextView(context);
        linkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        linkView.setGravity(Gravity.CENTER);
        linkView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        linkView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider), Theme.blendOver(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider), Theme.getColor(Theme.key_listSelector, resourcesProvider))));
        linkView.setPadding(dp(16), dp(14.66f), dp(16), dp(14.66f));
        linkView.setText(bot.url != null && bot.url.startsWith("https://") ? bot.url.substring(8) : bot.url);
        linearLayout.addView(linkView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 0, 0, 0, 12));

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        if (!bot.revoked) {
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            ssb.append("c ");
            ColoredImageSpan copySpan = new ColoredImageSpan(R.drawable.msg_copy_filled);
            ssb.setSpan(copySpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(getString(R.string.ChannelAffiliateProgramLinkCopy));
            button.setText(ssb, false);
        } else {
            button.setText(getString(R.string.ChannelAffiliateProgramLinkRejoin), false);
        }
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        LinkSpanDrawable.LinksTextView infoTextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        infoTextView.setText(bot.participants <= 0 ? formatString(R.string.ChannelAffiliateProgramLinkOpenedNone, UserObject.getUserName(botUser)) : formatPluralString("ChannelAffiliateProgramLinkOpened", (int) bot.participants, UserObject.getUserName(botUser)));
        infoTextView.setGravity(Gravity.CENTER);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        infoTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
        infoTextView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        linearLayout.addView(infoTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 14, 12, 14, 2));

        b.setCustomView(linearLayout);

        BottomSheet sheet = b.create();

        Runnable copy = () -> {
            AndroidUtilities.addToClipboard(bot.url);
            BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider)
                .createSimpleBulletin(R.raw.copy, getString(R.string.AffiliateProgramLinkCopiedTitle), AndroidUtilities.replaceTags(formatString(R.string.AffiliateProgramLinkCopiedText, percents(bot.commission_permille), UserObject.getUserName(botUser))))
                .show();
        };
        if (!bot.revoked) {
            linkView.setOnClickListener(v -> copy.run());
        }
        button.setOnClickListener(v -> {
            if (bot.revoked) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(bot.bot_id);
                if (user != null) {
                    MessagesController.getInstance(currentAccount).loadFullUser(user, 0, true, info -> AndroidUtilities.runOnUIThread(() -> {
                        if (info != null && info.starref_program != null) {
                            sheet.dismiss();
                            ChannelAffiliateProgramsFragment.showConnectAffiliateAlert(context, currentAccount, info.starref_program, dialogId, resourcesProvider, true);
                        }
                    }));
                }
            } else {
                copy.run();
            }
        });
        sheet.setOnDismissListener(d -> {

        });

        if (chipLayout != null) {
            BotStarsController.getInstance(currentAccount).loadAdminedBots();
            BotStarsController.getInstance(currentAccount).loadAdminedChannels();
            final View chip = chipLayout;
            chipLayout.setOnClickListener(v -> {
                ArrayList<TLObject> chats = BotStarsController.getInstance(currentAccount).getAdmined();
                chats.add(0, UserConfig.getInstance(currentAccount).getCurrentUser());

                ItemOptions i = ItemOptions.makeOptions(sheet.getContainerView(), resourcesProvider, chip);
                for (TLObject obj : chats) {
                    long did;
                    if (obj instanceof TLRPC.User) {
                        did = ((TLRPC.User) obj).id;
                    } else if (obj instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat) obj;
                        if (!ChatObject.isChannelAndNotMegaGroup(chat))
                            continue;
                        did = -chat.id;
                    } else continue;
                    i.addChat(obj, did == dialogId, () -> {
                        BotStarsController.getInstance(currentAccount).getConnectedBot(context, did, bot.bot_id, connectedBot -> {
                            if (connectedBot == null) {
                                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(bot.bot_id);
                                if (user != null) {
                                    MessagesController.getInstance(currentAccount).loadFullUser(user, 0, true, info -> AndroidUtilities.runOnUIThread(() -> {
                                        if (info != null && info.starref_program != null) {
                                            sheet.dismiss();
                                            ChannelAffiliateProgramsFragment.showConnectAffiliateAlert(context, currentAccount, info.starref_program, did, resourcesProvider, true);
                                        }
                                    }));
                                }
                            } else {
                                sheet.dismiss();
                                ChannelAffiliateProgramsFragment.showShareAffiliateAlert(context, currentAccount, connectedBot, did, resourcesProvider);
                            }
                        });
                    });
                }
                i.setDrawScrim(false)
                        .setDimAlpha(0)
                        .setGravity(Gravity.RIGHT)
                        .translate(dp(24), 0)
                        .show();
            });
        }
        botChip.setOnClickListener(v -> {
            sheet.dismiss();
            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment != null) {
                lastFragment.presentFragment(ProfileActivity.of(bot.bot_id));
            }
        });


        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (!AndroidUtilities.isTablet() && lastFragment != null && !AndroidUtilities.hasDialogOnTop(lastFragment)) {
            sheet.makeAttached(lastFragment);
        }

        sheet.show();
        return sheet;
    }
}
