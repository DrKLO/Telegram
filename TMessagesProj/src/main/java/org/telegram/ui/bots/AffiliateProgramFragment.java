package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorUserCell.buildCountDownTime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.vision.Frame;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.tgnet.tl.TL_payments;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MessagesSearchAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.GLIcon.Icon3D;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TableView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.GradientHeaderActivity;
import org.telegram.ui.PremiumFeatureCell;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.ExplainStarsSheet;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AffiliateProgramFragment extends GradientHeaderActivity implements NotificationCenter.NotificationCenterDelegate {

    private final long bot_id;

    private FrameLayout aboveTitleView;
    private GLIconTextureView iconTextureView;
    private View emptyLayout;

    private LinearLayout buttonLayout;
    private ButtonWithCounterView button;
    private LinkSpanDrawable.LinksTextView buttonSubtext;

    public AffiliateProgramFragment(long bot_id) {
        this.bot_id = bot_id;

        setWhiteBackground(true);
        setMinusHeaderHeight(dp(60));
    }

    @Override
    public View createView(Context context) {
        useFillLastLayoutManager = false;
        particlesViewHeight = dp(32 + 190 + 16);
//        transactionsLayout = new StarsIntroActivity.StarsTransactionsLayout(context, currentAccount, 0, getClassGuid(), getResourceProvider());
        emptyLayout = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int firstViewHeight;
                if (AffiliateProgramFragment.this.isLandscapeMode) {
                    firstViewHeight = AffiliateProgramFragment.this.statusBarHeight + actionBar.getMeasuredHeight() - AndroidUtilities.dp(16);
                } else {
                    int h = AndroidUtilities.dp(140) + statusBarHeight;
                    if (backgroundView.getMeasuredHeight() + AndroidUtilities.dp(24) > h) {
                        h = backgroundView.getMeasuredHeight() + AndroidUtilities.dp(24);
                    }
                    firstViewHeight = h;
                }
                firstViewHeight -= 2.5f * yOffset;
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(firstViewHeight, MeasureSpec.EXACTLY));
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
        aboveTitleView.addView(iconTextureView, LayoutHelper.createFrame(190, 190, Gravity.CENTER, 0, 32, 0, 24));
        configureHeader(getString(R.string.BotAffiliateProgramTitle), getString(R.string.BotAffiliateProgramText), aboveTitleView, null);

        buttonLayout = new LinearLayout(context);
        buttonLayout.setOrientation(LinearLayout.VERTICAL);
        buttonLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        View buttonShadow = new View(context);
        buttonShadow.setBackgroundColor(getThemedColor(Theme.key_divider));
        buttonLayout.addView(buttonShadow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1.0f / AndroidUtilities.density));

        button = new ButtonWithCounterView(context, resourceProvider) {
            @Override
            protected boolean subTextSplitToWords() {
                return false;
            }
        };
        button.setText(getString(R.string.AffiliateProgramStart), false);
        button.setOnClickListener(v -> {
            if (!button.isEnabled()) return;
            final FrameLayout layout = new FrameLayout(context);
            final TableView tableView = new TableView(context, resourceProvider);
            final Runnable send = () -> {
                TL_bots.updateStarRefProgram req = new TL_bots.updateStarRefProgram();
                req.bot = getMessagesController().getInputUser(bot_id);
                req.commission_permille = program.commission_permille;
                req.duration_months = program.duration_months;
                if (program.duration_months > 0) {
                    req.flags |= 1;
                    program.duration_months |= 1;
                } else {
                    req.flags &=~ 1;
                    program.duration_months &=~ 1;
                }
                final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.showDelayed(150);
                getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    if (res instanceof TL_payments.starRefProgram) {
                        TL_payments.starRefProgram newProgram = (TL_payments.starRefProgram) res;
                        TLRPC.UserFull userFull = getMessagesController().getUserFull(bot_id);
                        if (userFull != null) {
                            userFull.starref_program = newProgram;
                            getMessagesStorage().updateUserInfo(userFull, false);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, bot_id, userFull);
                        }
                        closeToProfile(false);
                    } else if (err != null) {
                        BulletinFactory.showError(err);
                    }
                }));
            };
            tableView.addRow(getString(R.string.AffiliateProgramCommission), percents(program.commission_permille));
            tableView.addRow(getString(R.string.AffiliateProgramDuration), program.duration_months <= 0 ? getString(R.string.Infinity) : program.duration_months < 12 || program.duration_months % 12 != 0 ? LocaleController.formatPluralString("Months", program.duration_months) : LocaleController.formatPluralString("Years", program.duration_months / 12));
            layout.addView(tableView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 24, 0, 24, 0));
            new AlertDialog.Builder(context, resourceProvider)
                .setTitle(getString(R.string.AffiliateProgramAlert))
                .setMessage(getString(new_program ? R.string.AffiliateProgramStartAlertText : R.string.AffiliateProgramUpdateAlertText))
                .setView(layout)
                .setPositiveButton(getString(new_program ? R.string.AffiliateProgramStartAlertButton : R.string.AffiliateProgramUpdateAlertButton), (d, w) -> send.run())
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
        });
        buttonLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 10, 10, 10, 7));

        buttonSubtext = new LinkSpanDrawable.LinksTextView(context, resourceProvider);
        buttonSubtext.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        buttonSubtext.setLinkTextColor(getThemedColor(Theme.key_chat_messageLinkIn));
        buttonSubtext.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        buttonSubtext.setGravity(Gravity.CENTER);
        buttonLayout.addView(buttonSubtext, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 32, 1, 32, 8));
        update(false);

        ((FrameLayout) fragmentView).addView(buttonLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        listView.setPadding(0, 0, 0, dp(10 + 48 + 10 + 16));
        listView.setOnItemClickListener((view, position) -> {
            if (adapter == null) return;
            UItem item = adapter.getItem(position);
            if (item.id == BUTTON_END) {
                end();
            } else if (item.id == BUTTON_EXAMPLES) {
                presentFragment(new SuggestedAffiliateProgramsFragment(bot_id));
            }
        });
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        listView.setItemAnimator(itemAnimator);

        return fragmentView;
    }

    private void closeToProfile(boolean ended) {
        BaseFragment bulletinFragment = null;
        if (getParentLayout() != null && getParentLayout().getFragmentStack() != null) {
            INavigationLayout parentLayout = getParentLayout();
            List<BaseFragment> fragments = parentLayout.getFragmentStack();
            BaseFragment profileActivity = null;
            int index = -1;
            for (int i = fragments.size() - 1; i > 0; --i) {
                BaseFragment f = fragments.get(i);
                if (f instanceof ProfileActivity && ((ProfileActivity) f).getDialogId() == bot_id) {
                    profileActivity = f;
                    index = i;
                    break;
                }
            }
            if (profileActivity != null) {
                for (int i = fragments.size() - 1; i > index; --i) {
                    parentLayout.removeFragmentFromStack(fragments.get(i));
                }
                finishFragment();
                bulletinFragment = profileActivity;
            } else {
                finishFragment();
                bulletinFragment = parentLayout.getBackgroundFragment();
            }
        } else {
            finishFragment();
        }

        if (bulletinFragment != null) {
            if (ended) {
                BulletinFactory.of(bulletinFragment)
                    .createSimpleBulletin(R.raw.linkbroken, getString(R.string.AffiliateProgramEndedTitle), AndroidUtilities.replaceTags(getString(R.string.AffiliateProgramEndedText)))
                    .show();
            } else {
                BulletinFactory.of(bulletinFragment)
                    .createSimpleBulletin(R.raw.contact_check, getString(R.string.AffiliateProgramStartedTitle), getString(R.string.AffiliateProgramStartedText))
                    .show();
            }
        }
    }

    private void update(boolean animated) {
        button.setText(getString(new_program || program.end_date != 0 ? R.string.AffiliateProgramStart : R.string.AffiliateProgramUpdate), animated);
        AndroidUtilities.cancelRunOnUIThread(updateTimerRunnable);
        updateTimerRunnable.run();
        buttonSubtext.setText(AndroidUtilities.replaceSingleTag(getString(new_program || program.end_date != 0 ? R.string.AffiliateProgramStartInfo : R.string.AffiliateProgramUpdateInfo), () -> {
            Browser.openUrl(getContext(), getString(new_program || program.end_date != 0 ? R.string.AffiliateProgramUpdateInfoLink : R.string.AffiliateProgramStartInfoLink));
        }));
        updateEnabled();
        if (adapter != null) {
            adapter.update(animated);
        }
    }

    private final Runnable updateTimerRunnable = () -> {
        button.setSubText(this.program.end_date == 0 ? null : buildCountDownTime((this.program.end_date - getConnectionsManager().getCurrentTime()) * 1000L), true);
        if (this.program.end_date != 0 && this.attached) {
            AndroidUtilities.runOnUIThread(this.updateTimerRunnable, 1000);
        }
    };

    private void updateEnabled() {
        button.setEnabled(program.end_date == 0 && (initialProgram == null || initialProgram.commission_permille != program.commission_permille || initialProgram.duration_months != program.duration_months));
    }

    private class BulletinTextView extends TextView {
        public BulletinTextView(Context context) {
            super(context);
        }
        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            canvas.drawCircle(dp(1 + 2.5f), dp(9 + 2.5f), dp(2.5f), getPaint());
        }
    }

    private void end() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), 0, dp(24), 0);

        TextView textView = new TextView(getContext());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        textView.setText(AndroidUtilities.replaceTags(getString(R.string.AffiliateProgramStopText)));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 17));

        textView = new BulletinTextView(getContext());
        textView.setPadding(dp(15), 0, 0, 0);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        textView.setText(AndroidUtilities.replaceTags(getString(R.string.AffiliateProgramStopText1)));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 17));

        textView = new BulletinTextView(getContext());
        textView.setPadding(dp(15), 0, 0, 0);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        textView.setText(AndroidUtilities.replaceTags(getString(R.string.AffiliateProgramStopText2)));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 17));

        textView = new BulletinTextView(getContext());
        textView.setPadding(dp(15), 0, 0, 0);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        textView.setText(AndroidUtilities.replaceTags(getString(R.string.AffiliateProgramStopText3)));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        new AlertDialog.Builder(getContext(), resourceProvider)
            .setTitle(getString(R.string.AffiliateProgramAlert))
//            .setMessage(AndroidUtilities.replaceTags(getString(R.string.AffiliateProgramStopText)))
            .setView(layout)
            .setPositiveButton(getString(R.string.AffiliateProgramStopButton), (d, w) -> {
                TL_bots.updateStarRefProgram req = new TL_bots.updateStarRefProgram();
                req.bot = getMessagesController().getInputUser(bot_id);
                req.commission_permille = 0;
                final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.showDelayed(150);
                getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    if (res instanceof TL_payments.starRefProgram) {
                        TL_payments.starRefProgram newProgram = (TL_payments.starRefProgram) res;
                        TLRPC.UserFull userFull = getMessagesController().getUserFull(bot_id);
                        if (userFull != null) {
                            program.flags |= 2;
                            program.end_date = getConnectionsManager().getCurrentTime() + (getConnectionsManager().isTestBackend() ? 300 : 86400);
                            userFull.starref_program = newProgram;
                            getMessagesStorage().updateUserInfo(userFull, false);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, bot_id, userFull);
                        }
                        closeToProfile(true);
                    } else if (err != null) {
                        BulletinFactory.showError(err);
                    }
                }));
            })
            .setNegativeButton(getString(R.string.Cancel), null)
            .makeRed(AlertDialog.BUTTON_POSITIVE)
            .show();
    }

    private boolean new_program;
    private TL_payments.starRefProgram initialProgram;
    private TL_payments.starRefProgram program;

    private String[] durationTexts = null;
    private final List<Integer> durationValues = Arrays.asList(1, 3, 6, 12, 24, 36, 0);

    public TL_payments.starRefProgram getDefaultProgram() {
        final TL_payments.starRefProgram program = new TL_payments.starRefProgram();
        program.commission_permille = Utilities.clamp(50, getMessagesController().starrefMaxCommissionPermille, getMessagesController().starrefMinCommissionPermille);
        program.duration_months = 1;
        return program;
    }

    private static final int BUTTON_EXAMPLES = 2;
    private static final int BUTTON_END = 4;

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (getContext() == null) {
            return;
        }

        items.add(UItem.asFullyCustom(getHeader(getContext())));
        items.add(FeatureCell.Factory.as(R.drawable.menu_feature_premium, getString(R.string.BotAffiliateProgramFeature1Title), getString(R.string.BotAffiliateProgramFeature1)));
        items.add(FeatureCell.Factory.as(R.drawable.msg_channel, getString(R.string.BotAffiliateProgramFeature2Title), getString(R.string.BotAffiliateProgramFeature2)));
        items.add(FeatureCell.Factory.as(R.drawable.menu_feature_links2, getString(R.string.BotAffiliateProgramFeature3Title), getString(R.string.BotAffiliateProgramFeature3)));
        items.add(UItem.asShadow(1, null));

        items.add(UItem.asHeader(getString(R.string.AffiliateProgramCommission)));
        items.add(UItem.asIntSlideView(
            1,
            getMessagesController().starrefMinCommissionPermille, program.commission_permille, getMessagesController().starrefMaxCommissionPermille,
            val -> String.format(Locale.US, "%.1f%%", val / 10.0f),
            val -> { program.commission_permille = val; updateEnabled(); }
        ).setMinSliderValue(initialProgram == null ? -1 : initialProgram.commission_permille));
        items.add(UItem.asShadow(getString(R.string.AffiliateProgramCommissionInfo)));

        items.add(UItem.asHeader(getString(R.string.AffiliateProgramDuration)));
        if (durationTexts == null) {
            durationTexts = new String[durationValues.size()];
            for (int i = 0; i < durationValues.size(); ++i) {
                final int d = durationValues.get(i);
                if (d == 0) {
                    durationTexts[i] = getString(R.string.Infinity);
                } else if (d < 12 || d % 12 != 0) {
                    durationTexts[i] = formatPluralString("MonthsShort", d);
                } else {
                    durationTexts[i] = formatPluralString("YearsShort", d / 12);
                }
            }
        }
        final UItem slideView = UItem.asSlideView(durationTexts, durationValues.indexOf(program.duration_months), v -> { program.duration_months = durationValues.get(v); updateEnabled(); });
        if (initialProgram != null) {
            if (initialProgram.duration_months <= 0) {
                slideView.setMinSliderValue(durationValues.size() - 1);
            } else {
                for (int i = durationValues.size() - 1; i >= 0; --i) {
                    if (durationValues.get(i) > 0 && durationValues.get(i) <= initialProgram.duration_months) {
                        slideView.setMinSliderValue(i);
                        break;
                    }
                }
            }
        }
        items.add(slideView);
        items.add(UItem.asShadow(getString(R.string.AffiliateProgramDurationInfo)));

        items.add(ColorfulTextCell.Factory.as(BUTTON_EXAMPLES, getThemedColor(Theme.key_color_green), R.drawable.filled_earn_stars, getString(R.string.AffiliateProgramExistingProgramsTitle), getString(R.string.AffiliateProgramExistingProgramsText)));
        items.add(UItem.asShadow(3, null));

        if (!new_program && program.end_date == 0) {
            items.add(UItem.asButton(BUTTON_END, getString(R.string.AffiliateProgramStop)).red());
            items.add(UItem.asShadow(5, null));
        }

        items.add(UItem.asShadow(6, null));
        items.add(UItem.asShadow(7, null));

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

    private boolean attached;

    @Override
    public boolean onFragmentCreate() {
        attached = true;
        new_program = true;
        program = getDefaultProgram();
        initialProgram = null;
        TLRPC.UserFull userFull = getMessagesController().getUserFull(bot_id);
        if (userFull != null) {
            new_program = false;
            program = userFull.starref_program;
            if (program == null) {
                new_program = true;
                program = getDefaultProgram();
                initialProgram = null;
            } else {
                initialProgram = new TL_payments.starRefProgram();
                initialProgram.commission_permille = program.commission_permille;
                initialProgram.duration_months = program.duration_months;
            }
        } else {
            TLRPC.User user = getMessagesController().getUser(bot_id);
            if (user != null) {
                getMessagesController().loadFullUser(user, getClassGuid(), true, info -> AndroidUtilities.runOnUIThread(() -> {
                    if (info != null) {
                        new_program = false;
                        program = info.starref_program;
                        if (program == null) {
                            new_program = true;
                            program = getDefaultProgram();
                            initialProgram = null;
                        } else {
                            initialProgram = new TL_payments.starRefProgram();
                            initialProgram.commission_permille = program.commission_permille;
                            initialProgram.duration_months = program.duration_months;
                        }
                    }
                    update(true);
                }));
            }
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        attached = false;
        AndroidUtilities.cancelRunOnUIThread(updateTimerRunnable);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {

    }

    @Override
    protected View getHeader(Context context) {
        return super.getHeader(context);
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

    public static class FeatureCell extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;
        private ImageView imageView;
        private LinearLayout textLayout;
        private TextView titleView;
        private TextView textView;

        public FeatureCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.resourcesProvider = resourcesProvider;

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
            addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.LEFT, 20, 12.66f, 0, 0));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 64, 3, 24, 5 + 8.33f));

            titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 4));

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));
        }

        public void set(int iconResId, CharSequence title, CharSequence text) {
            imageView.setImageResource(iconResId);
            titleView.setText(title);
            textView.setText(text);
        }

        public static class Factory extends UItem.UItemFactory<FeatureCell> {
            static { setup(new Factory()); }

            @Override
            public FeatureCell createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new FeatureCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                ((FeatureCell) view).set(item.iconResId, item.text, item.subtext);
            }

            public static UItem as(int iconResId, CharSequence title, CharSequence text) {
                UItem item = UItem.ofFactory(Factory.class);
                item.iconResId = iconResId;
                item.text = title;
                item.subtext = text;
                return item;
            }

            @Override
            public boolean isClickable() {
                return false;
            }
        }
    }

    @Override
    public int getNavigationBarColor() {
        return getThemedColor(Theme.key_windowBackgroundWhite);
    }

    public static class ColorfulTextCell extends FrameLayout {
        private final Theme.ResourcesProvider resourcesProvider;

        private final ImageView imageView;
        private final FrameLayout.LayoutParams imageViewLayoutParams;
        private final LinearLayout textLayout;
        private final FrameLayout.LayoutParams textLayoutLayoutParams;
        private final TextView titleView;
        private final TextView textView;
        private final ImageView arrowView;
        private final TextView percentView;

        public ColorfulTextCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            imageView = new ImageView(context);
            imageView.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, imageViewLayoutParams = LayoutHelper.createFrame(28, 28, Gravity.LEFT | Gravity.TOP, 17, 14.33f, 0, 0));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            addView(textLayout, textLayoutLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 62, 10, 40, 8.66f));

            titleView = new TextView(context);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            textLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 3, 0, 0));

            arrowView = new ImageView(context);
            arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), PorterDuff.Mode.SRC_IN));
            arrowView.setImageResource(R.drawable.msg_arrowright);
            arrowView.setScaleType(ImageView.ScaleType.CENTER);
            addView(arrowView, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            percentView = new TextView(context);
            percentView.setTextColor(0xFFFFFFFF);
            percentView.setBackground(Theme.createRoundRectDrawable(dp(4), Theme.getColor(Theme.key_color_green, resourcesProvider)));
            percentView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            percentView.setTypeface(AndroidUtilities.bold());
            percentView.setPadding(dp(5), 0, dp(4), dp(2));
            percentView.setGravity(Gravity.CENTER);
            percentView.setVisibility(View.GONE);
            addView(percentView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 17, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 35.33f, 0));
        }

        public void set(int color, int iconResId, CharSequence title, CharSequence text) {
            imageView.setImageResource(iconResId);
            imageView.setBackground(Theme.createRoundRectDrawable(dp(9), color));

            titleView.setText(title);
            if (TextUtils.isEmpty(text)) {
                imageViewLayoutParams.topMargin = dp(10);
                imageViewLayoutParams.bottomMargin = dp(10);
                titleView.setTypeface(null);
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                textLayoutLayoutParams.topMargin = 0;
                textLayoutLayoutParams.bottomMargin = 0;
                textLayoutLayoutParams.gravity = Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL;
                textView.setVisibility(View.GONE);
            } else {
                imageViewLayoutParams.topMargin = dp(14.33f);
                imageViewLayoutParams.bottomMargin = dp(10);
                titleView.setTypeface(AndroidUtilities.bold());
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                textLayoutLayoutParams.topMargin = dp(10);
                textLayoutLayoutParams.bottomMargin = dp(8.66f);
                textLayoutLayoutParams.gravity = Gravity.FILL_HORIZONTAL | Gravity.TOP;
                textView.setText(text);
                textView.setVisibility(View.VISIBLE);
            }
        }

        public void setPercent(CharSequence percent) {
            if (TextUtils.isEmpty(percent)) {
                percentView.setVisibility(View.GONE);
            } else {
                percentView.setVisibility(View.VISIBLE);
                percentView.setText(percent);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }

        public static class Factory extends UItem.UItemFactory<ColorfulTextCell> {
            static { setup(new Factory()); }

            @Override
            public ColorfulTextCell createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new ColorfulTextCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                ((ColorfulTextCell) view).set(
                    item.intValue, item.iconResId,
                    item.text, item.subtext
                );
            }

            public static UItem as(int id, int color, int iconResId, CharSequence title, CharSequence text) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.intValue = color;
                item.iconResId = iconResId;
                item.text = title;
                item.subtext = text;
                return item;
            }
        }

    }

    public static CharSequence percents(int commission) {
        float f = commission / 10.0f;
        if ((int) f == f) {
            return String.format(Locale.US, "%d%%", commission / 10);
        } else {
            return String.format(Locale.US, "%.1f%%", f);
        }
    }
}
