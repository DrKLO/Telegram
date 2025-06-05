package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class ReportBottomSheet extends BottomSheet {

    private final ViewPagerFixed viewPager;
    private static final int PAGE_TYPE_OPTIONS = 0;
    private static final int PAGE_TYPE_SUB_OPTIONS = 1;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean sponsored;
    private final boolean stories;
    private final ArrayList<Integer> messageIds;
    private final byte[] sponsoredId;
    private final long dialogId;
    private Listener listener;

    interface Listener {
        default void onReported() {};
        default void onHidden() {};
        default void onPremiumRequired() {};
    }

    public ReportBottomSheet(
        Context context,
        Theme.ResourcesProvider resourcesProvider,
        boolean stories,
        long dialogId,
        int messageId
    ) {
        this(context, resourcesProvider, stories, dialogId, new ArrayList<>(Arrays.asList(messageId)));
    }

    public ReportBottomSheet(
        Context context,
        Theme.ResourcesProvider resourcesProvider,
        boolean stories,
        long dialogId,
        ArrayList<Integer> messageIds
    ) {
        this(false, context, resourcesProvider, dialogId, stories, messageIds, null);
    }

    public ReportBottomSheet(
        Context context,
        Theme.ResourcesProvider resourcesProvider,
        long dialogId,
        byte[] sponsoredId
    ) {
        this(true, context, resourcesProvider, dialogId, false, null, sponsoredId);
    }

    public ReportBottomSheet(
        final boolean sponsored,
        Context context,
        Theme.ResourcesProvider resourcesProvider,
        long dialogId,
        boolean stories,
        ArrayList<Integer> messageIds,
        byte[] sponsoredId
    ) {
        super(context, true, resourcesProvider);
        this.sponsored = sponsored;
        this.messageIds = messageIds;
        this.stories = stories;
        this.sponsoredId = sponsoredId;
        this.dialogId = dialogId;
        backgroundPaint.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        smoothKeyboardAnimationEnabled = true;
        smoothKeyboardByBottom = true;
        containerView = new ContainerView(context);
        viewPager = new ViewPagerFixed(context) {
            @Override
            protected void onTabAnimationUpdate(boolean manual) {
                super.onTabAnimationUpdate(manual);
                containerView.invalidate();
            }

            @Override
            protected boolean canScrollForward(MotionEvent e) {
                return false;
            }

            @Override
            public void onStartTracking() {
                if (getCurrentView() instanceof Page) {
                    Page page = (Page) getCurrentView();
                    if (page.editTextCell != null) {
                        AndroidUtilities.hideKeyboard(page.editTextCell);
                    }
                }
            }
        };
        viewPager.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        containerView.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 5;
            }

            @Override
            public View createView(int viewType) {
                return new Page(context);
            }

            @Override
            public int getItemViewType(int position) {
                if (position == 0) {
                    return PAGE_TYPE_OPTIONS;
                } else {
                    return PAGE_TYPE_SUB_OPTIONS;
                }
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                ((Page) view).bind(viewType);
            }

        });

        if (messageIds == null && sponsoredId == null) {
            if (sponsored) {
                setReportChooseOption((TLRPC.TL_channels_sponsoredMessageReportResultChooseOption) null);
            } else {
                setReportChooseOption((TLRPC.TL_reportResultChooseOption) null);
            }
        }
    }

    public ReportBottomSheet setReportChooseOption(TLRPC.TL_channels_sponsoredMessageReportResultChooseOption chooseOption) {
        View[] viewPages = viewPager.getViewPages();
        if (viewPages[0] instanceof Page) {
            ((Page) viewPages[0]).bind(PAGE_TYPE_OPTIONS);
            containerView.post(() -> ((Page) viewPages[0]).setOption(chooseOption));
        }
        if (viewPages[1] instanceof Page) {
            ((Page) viewPages[1]).bind(PAGE_TYPE_SUB_OPTIONS);
        }
        return this;
    }

    public ReportBottomSheet setReportChooseOption(TLRPC.TL_reportResultChooseOption chooseOption) {
        View[] viewPages = viewPager.getViewPages();
        if (viewPages[0] instanceof Page) {
            ((Page) viewPages[0]).bind(PAGE_TYPE_OPTIONS);
            containerView.post(() -> ((Page) viewPages[0]).setOption(chooseOption));
        }
        if (viewPages[1] instanceof Page) {
            ((Page) viewPages[1]).bind(PAGE_TYPE_SUB_OPTIONS);
        }
        return this;
    }

    public ReportBottomSheet setReportChooseOption(TLRPC.TL_reportResultAddComment chooseOption) {
        View[] viewPages = viewPager.getViewPages();
        if (viewPages[0] instanceof Page) {
            ((Page) viewPages[0]).bind(PAGE_TYPE_OPTIONS);
            containerView.post(() -> ((Page) viewPages[0]).setOption(chooseOption));
        }
        if (viewPages[1] instanceof Page) {
            ((Page) viewPages[1]).bind(PAGE_TYPE_SUB_OPTIONS);
        }
        return this;
    }

    public ReportBottomSheet setListener(Listener listener) {
        this.listener = listener;
        return this;
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentView() instanceof Page) {
            Page page = (Page) viewPager.getCurrentView();
            if (page.editTextCell != null) {
                AndroidUtilities.hideKeyboard(page.editTextCell);
            }
        }
        if (viewPager.getCurrentPosition() > 0) {
            viewPager.scrollToPosition(viewPager.getCurrentPosition() - 1);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected boolean canDismissWithSwipe() {
        View currentView = viewPager.getCurrentView();
        if (currentView instanceof Page) {
            return ((Page) currentView).atTop();
        }
        return true;
    }

    private void submitOption(final CharSequence optionText, final byte[] option, final String comment) {
        TLObject request;
        if (sponsored) {
            TLRPC.TL_messages_reportSponsoredMessage req = new TLRPC.TL_messages_reportSponsoredMessage();
            req.random_id = sponsoredId;
            req.option = option;
            request = req;
        } else if (stories) {
            TL_stories.TL_stories_report req = new TL_stories.TL_stories_report();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            if (messageIds != null) {
                req.id.addAll(messageIds);
            }
            req.message = comment == null ? "" : comment;
            req.option = option;
            request = req;
        } else {
            TLRPC.TL_messages_report req = new TLRPC.TL_messages_report();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            if (messageIds != null) {
                req.id.addAll(messageIds);
            }
            req.message = comment == null ? "" : comment;
            req.option = option;
            request = req;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (viewPager.getCurrentView() instanceof Page) {
                    Page page = (Page) viewPager.getCurrentView();
                    if (page.button != null) {
                        page.button.setLoading(false);
                    }
                }
                if (response != null) {
                    if (
                        response instanceof TLRPC.TL_channels_sponsoredMessageReportResultChooseOption ||
                        response instanceof TLRPC.TL_reportResultChooseOption ||
                        response instanceof TLRPC.TL_reportResultAddComment
                    ) {
                        int nextPosition = viewPager.currentPosition + 1;
                        viewPager.scrollToPosition(nextPosition);
                        Page nextPage = (Page) viewPager.getViewPages()[1];
                        if (nextPage != null) {
                            if (response instanceof TLRPC.TL_reportResultChooseOption) {
                                nextPage.setOption((TLRPC.TL_reportResultChooseOption) response);
                            } else if (response instanceof TLRPC.TL_reportResultAddComment) {
                                nextPage.setOption((TLRPC.TL_reportResultAddComment) response);
                            } else if (response instanceof TLRPC.TL_channels_sponsoredMessageReportResultChooseOption) {
                                nextPage.setOption((TLRPC.TL_channels_sponsoredMessageReportResultChooseOption) response);
                            }
                            if (optionText != null) {
                                nextPage.setHeaderText(optionText);
                            }
                        }
                    } else if (response instanceof TLRPC.TL_channels_sponsoredMessageReportResultAdsHidden) {
                        MessagesController.getInstance(currentAccount).disableAds(false);
                        if (listener != null) {
                            listener.onHidden();
                            dismiss();
                        }
                    } else if (
                        response instanceof TLRPC.TL_channels_sponsoredMessageReportResultReported ||
                        response instanceof TLRPC.TL_reportResultReported
                    ) {
                        if (listener != null) {
                            listener.onReported();
                            dismiss();
                        }
                    }
                } else if (error != null) {
                    if (!sponsored && "MESSAGE_ID_REQUIRED".equals(error.text)) {
                        ChatActivity.openReportChat(dialogId, optionText.toString(), option, comment);
                    } else if ("PREMIUM_ACCOUNT_REQUIRED".equals(error.text)) {
                        if (listener != null) {
                            listener.onPremiumRequired();
                        }
                    } else if ("AD_EXPIRED".equals(error.text)) {
                        if (listener != null) {
                            listener.onReported();
                        }
                    }
                    dismiss();
                }
            });
        });
    }

    private class ContainerView extends FrameLayout {
        private final AnimatedFloat isActionBar = new AnimatedFloat(this, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        private float top;
        private final Path path = new Path();
        private Boolean statusBarOpen;

        public ContainerView(Context context) {
            super(context);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            View[] views = viewPager.getViewPages();
            top = 0;
            for (View view : views) {
                if (view == null) {
                    continue;
                }
                final Page page = (Page) view;
                float t = Utilities.clamp(1f - Math.abs(page.getTranslationX() / (float) page.getMeasuredWidth()), 1, 0);
                top += page.top() * t;
                if (page.getVisibility() == View.VISIBLE) {
                    page.updateTops();
                }
            }
            float actionBarT = isActionBar.set(top <= AndroidUtilities.statusBarHeight ? 1f : 0f);
            float statusBarHeight = AndroidUtilities.statusBarHeight * actionBarT;
            top = Math.max(AndroidUtilities.statusBarHeight, top) - AndroidUtilities.statusBarHeight * actionBarT;
            AndroidUtilities.rectTmp.set(backgroundPaddingLeft, top, getWidth() - backgroundPaddingLeft, getHeight() + dp(8));
            final float r = AndroidUtilities.lerp(dp(14), 0, actionBarT);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint);
            canvas.save();
            path.rewind();
            path.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
            canvas.clipPath(path);
            super.dispatchDraw(canvas);
            canvas.restore();
            updateLightStatusBar(statusBarHeight > AndroidUtilities.statusBarHeight / 2f);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() < top) {
                dismiss();
                return true;
            }
            return super.dispatchTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
        }

        private void updateLightStatusBar(boolean open) {
            if (statusBarOpen != null && statusBarOpen == open) {
                return;
            }
            boolean openBgLight = AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_dialogBackground)) > .721f;
            boolean closedBgLight = AndroidUtilities.computePerceivedBrightness(Theme.blendOver(getThemedColor(Theme.key_actionBarDefault), 0x33000000)) > .721f;
            boolean isLight = (statusBarOpen = open) ? openBgLight : closedBgLight;
            AndroidUtilities.setLightStatusBar(getWindow(), isLight);
        }
    }

    private class Page extends FrameLayout {
        int pageType;

        TLRPC.TL_channels_sponsoredMessageReportResultChooseOption sponsoredOption;
        TLRPC.TL_reportResultChooseOption option;
        TLRPC.TL_reportResultAddComment commentOption;

        private final FrameLayout contentView;
        private final UniversalRecyclerView listView;
        private final BigHeaderCell headerView;

        public Page(Context context) {
            super(context);

            contentView = new FrameLayout(context);
            contentView.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
            contentView.setClipToPadding(true);
            addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

            headerView = new BigHeaderCell(context, resourcesProvider);
            headerView.setOnBackClickListener(() -> {
                if (pageType == PAGE_TYPE_OPTIONS) {
                    dismiss();
                } else {
                    onBackPressed();
                }
            });
            if (sponsored) {
                headerView.setText(LocaleController.getString(R.string.ReportAd));
            } else if (stories) {
                headerView.setText(LocaleController.getString(R.string.ReportStory));
            } else {
                headerView.setText(LocaleController.getString(R.string.Report2));
            }
            headerView.backDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            headerView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
            addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

            listView = new UniversalRecyclerView(context, currentAccount, 0, true, this::fillItems, this::onClick, null, resourcesProvider);
            listView.setClipToPadding(false);
            listView.layoutManager.setReverseLayout(true);
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    contentView.invalidate();
                    containerView.invalidate();
                }
            });
            contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        public float top() {
            float top = contentView.getPaddingTop();
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                int position = listView.layoutManager.getPosition(child);
                if (position < 0 || position >= listView.adapter.getItemCount())
                    continue;
                UItem uItem = listView.adapter.getItem(position);
                if (uItem != null && uItem.viewType == UniversalAdapter.VIEW_TYPE_SPACE) {
                    top = contentView.getPaddingTop() + child.getY();
                }
            }
            return top;
        }

        public void updateTops() {
            float top = -headerView.getHeight();
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                int position = listView.layoutManager.getPosition(child);
                UItem uItem = listView.adapter.getItem(position);
                if (uItem.viewType == UniversalAdapter.VIEW_TYPE_SPACE) {
                    top = contentView.getPaddingTop() + child.getY();
                    break;
                }
            }
            headerView.setTranslationY(Math.max(AndroidUtilities.statusBarHeight, top));
        }

        public void bind(int pageType) {
            this.pageType = pageType;
            headerView.setCloseImageVisible(pageType != PAGE_TYPE_OPTIONS);
            if (listView != null) {
                listView.adapter.update(true);
            }
        }

        public void setOption(TLRPC.TL_channels_sponsoredMessageReportResultChooseOption option) {
            this.sponsoredOption = option;
            this.option = null;
            this.commentOption = null;
            listView.adapter.update(false);
        }

        public void setOption(TLRPC.TL_reportResultChooseOption option) {
            this.sponsoredOption = null;
            this.option = option;
            this.commentOption = null;
            listView.adapter.update(false);
        }

        public void setOption(TLRPC.TL_reportResultAddComment option) {
            this.sponsoredOption = null;
            this.option = null;
            this.commentOption = option;
            listView.adapter.update(false);
            if (editTextCell != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    AndroidUtilities.showKeyboard(editTextCell.editText);
                }, 120);
            }
        }

        public void setHeaderText(CharSequence headerText) {
            headerView.setText(headerText);
            headerView.getText();
            headerView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(120), MeasureSpec.AT_MOST));
            if (listView != null) {
                listView.adapter.update(true);
            }
        }

        private EditTextCell editTextCell;
        private FrameLayout buttonContainer;
        private ButtonWithCounterView button;

        public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            int height = 0;

            if (headerView.getMeasuredHeight() <= 0) {
                headerView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(120), MeasureSpec.AT_MOST));
            }
            UItem space = UItem.asSpace(headerView.getMeasuredHeight());
            space.id = -1;
            space.transparent = true;
            items.add(space);
            height += headerView.getMeasuredHeight() / AndroidUtilities.density;

            if (sponsoredOption != null || option != null || commentOption != null) {
                if (sponsoredOption != null || option != null) {
                    HeaderCell headerCell = new HeaderCell(getContext(), Theme.key_windowBackgroundWhiteBlueHeader, 21, 0, 0, false, resourcesProvider);
                    if (sponsoredOption != null) {
                        headerCell.setText(sponsoredOption.title);
                    } else if (option != null) {
                        headerCell.setText(option.title);
                    }
                    headerCell.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
                    UItem headerItem = UItem.asCustom(headerCell);
                    headerItem.id = -2;
                    items.add(headerItem);
                    height += 40;
                }

                if (sponsoredOption != null) {
                    for (int i = 0; i < sponsoredOption.options.size(); i++) {
                        UItem buttonItem = new UItem(UniversalAdapter.VIEW_TYPE_RIGHT_ICON_TEXT, false);
                        buttonItem.text = sponsoredOption.options.get(i).text;
                        buttonItem.iconResId = R.drawable.msg_arrowright;
                        buttonItem.id = i;
                        items.add(buttonItem);
                        height += 50;
                    }
                } else if (option != null) {
                    for (int i = 0; i < option.options.size(); i++) {
                        UItem buttonItem = new UItem(UniversalAdapter.VIEW_TYPE_RIGHT_ICON_TEXT, false);
                        buttonItem.text = option.options.get(i).text;
                        buttonItem.iconResId = R.drawable.msg_arrowright;
                        buttonItem.id = i;
                        items.add(buttonItem);
                        height += 50;
                    }
                } else if (commentOption != null) {
                    if (editTextCell == null) {
                        editTextCell = new EditTextCell(getContext(), "", true, false, 1024, resourcesProvider) {
                            @Override
                            protected void onTextChanged(CharSequence newText) {
                                super.onTextChanged(newText);
                                if (button != null) {
                                    button.setEnabled(commentOption.optional || !TextUtils.isEmpty(editTextCell.getText()));
                                }
                            }
                        };
                        editTextCell.setShowLimitWhenNear(100);
                    }
                    editTextCell.editText.setHint(LocaleController.getString(commentOption.optional ? R.string.Report2CommentOptional : R.string.Report2Comment));
                    UItem item = UItem.asCustom(editTextCell);
                    item.id = -3;
                    items.add(item);
                    height += 40;

                    if (messageIds != null && !messageIds.isEmpty()) {
                        items.add(UItem.asShadow(LocaleController.getString(messageIds.size() > 1 ? R.string.Report2CommentInfoMany : R.string.Report2CommentInfo)));
                    } else if (DialogObject.isUserDialog(dialogId)) {
                        items.add(UItem.asShadow(LocaleController.getString(R.string.Report2CommentInfoUser)));
                    } else if (ChatObject.isChannelAndNotMegaGroup(MessagesController.getInstance(currentAccount).getChat(-dialogId))) {
                        items.add(UItem.asShadow(LocaleController.getString(R.string.Report2CommentInfoChannel)));
                    } else {
                        items.add(UItem.asShadow(LocaleController.getString(R.string.Report2CommentInfoGroup)));
                    }

                    if (buttonContainer == null) {
                        button = new ButtonWithCounterView(getContext(), resourcesProvider);
                        button.setText(LocaleController.getString(R.string.Report2Send), false);

                        buttonContainer = new FrameLayout(getContext());
                        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
                        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 12, 12, 12, 12));

                        View buttonShadow = new View(getContext());
                        buttonShadow.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
                        buttonContainer.addView(buttonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.TOP));
                    }
                    button.setEnabled(commentOption.optional || !TextUtils.isEmpty(editTextCell.getText()));
                    button.setOnClickListener(v -> {
                        if (!button.isEnabled() || button.isLoading()) return;
                        button.setLoading(true);
                        submitOption(headerView.getText(), commentOption.option, editTextCell.getText().toString());
                    });

                    UItem buttonItem = UItem.asCustom(buttonContainer);
                    buttonItem.id = -4;
                    items.add(buttonItem);
                    height += 12 + 48 + 12;
                }
                items.get(items.size() - 1).hideDivider = true;

                if (sponsored && pageType == PAGE_TYPE_OPTIONS) {
                    FrameLayout frameLayout = new FrameLayout(getContext());
                    Drawable shadowDrawable = Theme.getThemedDrawable(getContext(), R.drawable.greydivider, Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourcesProvider));
                    Drawable background = new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                    combinedDrawable.setFullsize(true);
                    frameLayout.setBackground(combinedDrawable);
                    LinkSpanDrawable.LinksTextView textView = new LinkSpanDrawable.LinksTextView(getContext());
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    textView.setText(AndroidUtilities.replaceLinks(LocaleController.getString(R.string.ReportAdLearnMore), resourcesProvider));
                    textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourcesProvider));
                    textView.setGravity(Gravity.CENTER);
                    frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 16, 16, 16, 16));
                    UItem bottomItem = UItem.asCustom(frameLayout);
                    bottomItem.id = -3;
                    items.add(bottomItem);
                    height += 46;
                }
            }

            if (listView != null) {
                if (containerView.getMeasuredHeight() - AndroidUtilities.statusBarHeight < AndroidUtilities.dp(height)) {
                    listView.layoutManager.setReverseLayout(false);
                } else {
                    Collections.reverse(items);
                    listView.layoutManager.setReverseLayout(true);
                }
            }
        }

        private void onClick(UItem item, View view, int position, float x, float y) {
            if (item.viewType == UniversalAdapter.VIEW_TYPE_RIGHT_ICON_TEXT) {
                if (sponsoredOption != null) {
                    TLRPC.TL_sponsoredMessageReportOption clickedOption = sponsoredOption.options.get(item.id);
                    if (clickedOption != null) {
                        submitOption(clickedOption.text, clickedOption.option, null);
                    }
                } else if (option != null) {
                    TLRPC.TL_messageReportOption clickedOption = option.options.get(item.id);
                    if (clickedOption != null) {
                        submitOption(clickedOption.text, clickedOption.option, null);
                    }
                } else if (commentOption != null) {
                    if (commentOption.option != null) {
                        submitOption(null, commentOption.option, null);
                    }
                } else {
                    submitOption(item.text, null, null);
                }
            }
        }

        public boolean atTop() {
            return !listView.canScrollVertically(-1);
        }

        private class BigHeaderCell extends FrameLayout {
            private final ImageView btnBack;
            private final TextView textView;
            public BackDrawable backDrawable;
            private Runnable onBackClickListener;

            public BigHeaderCell(Context context, Theme.ResourcesProvider resourcesProvider) {
                super(context);
                textView = new TextView(context);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                addView(textView);

                btnBack = new ImageView(context);
                btnBack.setImageDrawable(backDrawable = new BackDrawable(false));
                backDrawable.setColor(0xffffffff);
                addView(btnBack, LayoutHelper.createFrame(24, 24, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 16, 16, 16, 0));
                btnBack.setOnClickListener(e -> {
                    if (onBackClickListener != null) {
                        onBackClickListener.run();
                    }
                });

                setCloseImageVisible(true);
                setMinimumHeight(dp(56));
            }

            public void setText(CharSequence text) {
                textView.setText(text);
            }

            public CharSequence getText() {
                return textView.getText();
            }

            public void setCloseImageVisible(boolean visible) {
                btnBack.setVisibility(visible ? View.VISIBLE : View.GONE);
                textView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, LocaleController.isRTL || !visible ? 22 : 53, 14, LocaleController.isRTL && visible ? 53 : 22, 12));
            }

            public void setOnBackClickListener(Runnable onCloseClickListener) {
                this.onBackClickListener = onCloseClickListener;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    heightMeasureSpec
                );
            }
        }
    }

    public static void openChat(
        int currentAccount,
        Context context,
        long dialogId
    ) {
        open(currentAccount, context, dialogId, false, new ArrayList<>(), null, null, new byte[]{}, null, null);
    }

    public static void openChat(
        ChatActivity fragment
    ) {
        if (fragment == null) return;
        final int currentAccount = fragment.getCurrentAccount();
        final Context context = fragment.getContext();
        final long dialogId = fragment.getDialogId();
        if (context == null) return;

        open(currentAccount, context, dialogId, false, new ArrayList<>(), null, null, new byte[]{}, null, null);
    }

    public static void openChat(
        BaseFragment fragment,
        long dialogId
    ) {
        if (fragment == null) return;
        final int currentAccount = fragment.getCurrentAccount();
        final Context context = fragment.getContext();
        if (context == null) return;

        open(currentAccount, context, dialogId, false, new ArrayList<>(), null, null, new byte[]{}, null, null);
    }

    public static void openMessage(
        BaseFragment fragment,
        MessageObject message
    ) {
        if (fragment == null) return;
        final int currentAccount = fragment.getCurrentAccount();
        final Context context = fragment.getContext();
        if (context == null) return;

        final ArrayList<Integer> messageIds = new ArrayList<>(Collections.singleton(message.getId()));
        open(currentAccount, context,  message.getDialogId(), false, messageIds, BulletinFactory.of(fragment), fragment == null ? null : fragment.getResourceProvider(), new byte[]{}, null, null);
    }

    public static void openMessages(
        ChatActivity fragment,
        ArrayList<Integer> ids
    ) {
        if (fragment == null) return;
        final int currentAccount = fragment.getCurrentAccount();
        final Context context = fragment.getContext();
        final long dialogId = fragment.getDialogId();
        if (context == null) return;

        open(currentAccount, context, dialogId, false, ids, BulletinFactory.of(fragment), fragment == null ? null : fragment.getResourceProvider(), new byte[]{}, null, null);
    }

    public static void continueReport(
        ChatActivity fragment,
        byte[] option,
        String message,
        ArrayList<Integer> ids,
        Utilities.Callback<Boolean> whenDone
    ) {
        if (fragment == null) return;
        final int currentAccount = fragment.getCurrentAccount();
        final Context context = fragment.getContext();
        final long dialogId = fragment.getDialogId();
        if (context == null) return;

        open(currentAccount, context, dialogId, false, ids, BulletinFactory.of(fragment), fragment == null ? null : fragment.getResourceProvider(), option, message, whenDone);
    }

    public static void openStory(
        int currentAccount,
        Context context,
        TL_stories.StoryItem storyItem,
        BulletinFactory bulletinFactory,
        Theme.ResourcesProvider resourceProvider,
        Utilities.Callback<Boolean> whenDone
    ) {
        final ArrayList<Integer> storyIds = new ArrayList<>(Collections.singleton(storyItem.id));
        open(currentAccount, context, storyItem.dialogId, true, storyIds, bulletinFactory, resourceProvider, new byte[]{}, null, whenDone);
    }

    public static void open(
        int currentAccount,
        Context context,
        long dialogId,
        boolean stories,
        ArrayList<Integer> messageIds,
        BulletinFactory bulletinFactory,
        Theme.ResourcesProvider resourceProvider,
        final byte[] option,
        String message,
        Utilities.Callback<Boolean> whenDone
    ) {
        if (context == null || messageIds == null) return;
        final boolean[] done = new boolean[] { false };
        final TLObject request;
        if (stories) {
            TL_stories.TL_stories_report req = new TL_stories.TL_stories_report();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.id.addAll(messageIds);
            req.option = option;
            req.message = TextUtils.isEmpty(message) ? "" : message;
            request = req;
        } else {
            TLRPC.TL_messages_report req = new TLRPC.TL_messages_report();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.id.addAll(messageIds);
            req.option = option;
            req.message = TextUtils.isEmpty(message) ? "" : message;
            request = req;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
            if (response != null) {
                if (response instanceof TLRPC.TL_reportResultChooseOption || response instanceof TLRPC.TL_reportResultAddComment) {
                    AndroidUtilities.runOnUIThread(() -> {
                        final ReportBottomSheet sheet = new ReportBottomSheet(context, resourceProvider, stories, dialogId, messageIds);
                        if (response instanceof TLRPC.TL_reportResultChooseOption) {
                            sheet.setReportChooseOption((TLRPC.TL_reportResultChooseOption) response);
                        } else if (response instanceof TLRPC.TL_reportResultAddComment) {
                            sheet.setReportChooseOption((TLRPC.TL_reportResultAddComment) response);
                        }
                        sheet.setListener(new ReportBottomSheet.Listener() {
                            @Override
                            public void onReported() {
                                if (!done[0] && whenDone != null) {
                                    done[0] = true;
                                    whenDone.run(true);
                                }
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (LaunchActivity.getSafeLastFragment() == null) return;
                                    final BulletinFactory bf = bulletinFactory == null ? BulletinFactory.of(LaunchActivity.getSafeLastFragment()) : bulletinFactory;
                                    if (bf == null) return;
                                    bf
                                        .createSimpleBulletin(
                                            R.raw.msg_antispam,
                                            LocaleController.getString(R.string.ReportChatSent),
                                            LocaleController.getString(R.string.Reported2)
                                        )
                                        .setDuration(Bulletin.DURATION_PROLONG)
                                        .show();
                                }, 200);
                            }
                        });
                        sheet.setOnDismissListener(() -> {
                            if (!done[0] && whenDone != null) {
                                done[0] = true;
                                whenDone.run(false);
                            }
                        });
                        sheet.show();
                    });
                } else if (response instanceof TLRPC.TL_reportResultReported) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!done[0] && whenDone != null) {
                            done[0] = true;
                            whenDone.run(true);
                        }
                        Runnable showToast = () -> {
                            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                            if (fragment == null) return;
                            final BulletinFactory bf = BulletinFactory.of(fragment);
                            if (bf == null) return;
                            bf
                                .createSimpleBulletin(
                                        R.raw.msg_antispam,
                                        LocaleController.getString(R.string.ReportChatSent),
                                        LocaleController.getString(R.string.Reported2)
                                )
                                .setDuration(Bulletin.DURATION_PROLONG)
                                .show();
                        };
                        AndroidUtilities.runOnUIThread(showToast, 220);
                    }, 200);
                }
            }
        });
    }

    public static void openSponsored(
        ChatActivity fragment,
        MessageObject message,
        Theme.ResourcesProvider resourceProvider
    ) {
        if (fragment == null) return;
        final int currentAccount = fragment.getCurrentAccount();
        final Context context = fragment.getContext();
        final long dialogId = fragment.getDialogId();
        if (context == null) return;

        TLRPC.TL_messages_reportSponsoredMessage req = new TLRPC.TL_messages_reportSponsoredMessage();
        final byte[] sponsoredId = req.random_id = message.sponsoredId;
        req.option = new byte[]{};
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                if (response instanceof TLRPC.TL_channels_sponsoredMessageReportResultChooseOption) {
                    AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.TL_channels_sponsoredMessageReportResultChooseOption result = (TLRPC.TL_channels_sponsoredMessageReportResultChooseOption) response;
                        new ReportBottomSheet(context, resourceProvider, dialogId, sponsoredId)
                            .setReportChooseOption(result)
                            .setListener(new ReportBottomSheet.Listener() {
                                @Override
                                public void onReported() {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        BulletinFactory.of(fragment)
                                                .createAdReportedBulletin(
                                                    AndroidUtilities.replaceSingleTag(
                                                        LocaleController.getString(R.string.AdReported),
                                                        -1,
                                                        AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD,
                                                        () -> Browser.openUrl(context, "https://promote.telegram.org/guidelines"),
                                                        resourceProvider
                                                    )
                                                )
                                                .show();
                                        fragment.removeFromSponsored(message);
                                        fragment.removeMessageWithThanos(message);
                                    }, 200);
                                }

                                @Override
                                public void onHidden() {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        BulletinFactory.of(fragment)
                                            .createAdReportedBulletin(LocaleController.getString(R.string.AdHidden))
                                            .show();
                                        fragment.removeFromSponsored(message);
                                        fragment.removeMessageWithThanos(message);
                                    }, 200);
                                }

                                @Override
                                public void onPremiumRequired() {
                                    fragment.showDialog(new PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_ADS, true));
                                }
                            })
                            .show();
                    });
                } else if (response instanceof TLRPC.TL_channels_sponsoredMessageReportResultReported) {
                    AndroidUtilities.runOnUIThread(() -> {
                        BulletinFactory.of(fragment)
                            .createAdReportedBulletin(
                                AndroidUtilities.replaceSingleTag(
                                    LocaleController.getString(R.string.AdReported),
                                    -1,
                                    AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD,
                                    () -> Browser.openUrl(context, "https://promote.telegram.org/guidelines"),
                                    resourceProvider
                                )
                            )
                            .show();
                        fragment.removeFromSponsored(message);
                        fragment.removeMessageWithThanos(message);
                    }, 200);
                } else if (response instanceof TLRPC.TL_channels_sponsoredMessageReportResultAdsHidden) {
                    AndroidUtilities.runOnUIThread(() -> {
                        BulletinFactory.of(fragment)
                            .createAdReportedBulletin(LocaleController.getString(R.string.AdHidden))
                            .show();
                        MessagesController.getInstance(currentAccount).disableAds(false);
                        fragment.removeFromSponsored(message);
                        fragment.removeMessageWithThanos(message);
                    }, 200);
                }
            } else if (error != null && "AD_EXPIRED".equalsIgnoreCase(error.text)) {
                AndroidUtilities.runOnUIThread(() -> {
                    BulletinFactory.of(fragment)
                        .createAdReportedBulletin(
                            AndroidUtilities.replaceSingleTag(
                                LocaleController.getString(R.string.AdReported),
                                -1,
                                AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD,
                                () -> Browser.openUrl(context, "https://promote.telegram.org/guidelines"),
                                resourceProvider
                            )
                        )
                        .show();
                    fragment.removeFromSponsored(message);
                    fragment.removeMessageWithThanos(message);
                }, 200);
            }
        });
    }


    public static void openSponsoredPeer(
        BaseFragment fragment,
        byte[] random_id,
        Theme.ResourcesProvider resourceProvider,
        Runnable remove
    ) {
        if (fragment == null) return;
        final int currentAccount = fragment.getCurrentAccount();
        final Context context = fragment.getContext();
        if (context == null) return;

        final TLRPC.TL_messages_reportSponsoredMessage req = new TLRPC.TL_messages_reportSponsoredMessage();
        final byte[] sponsoredId = req.random_id = random_id;
        req.option = new byte[]{};
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                if (response instanceof TLRPC.TL_channels_sponsoredMessageReportResultChooseOption) {
                    AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.TL_channels_sponsoredMessageReportResultChooseOption result = (TLRPC.TL_channels_sponsoredMessageReportResultChooseOption) response;
                        new ReportBottomSheet(context, resourceProvider, 0, sponsoredId)
                            .setReportChooseOption(result)
                            .setListener(new ReportBottomSheet.Listener() {
                                @Override
                                public void onReported() {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        BulletinFactory.of(fragment)
                                            .createAdReportedBulletin(
                                                AndroidUtilities.replaceSingleTag(
                                                    LocaleController.getString(R.string.AdReported),
                                                    -1,
                                                    AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD,
                                                    () -> Browser.openUrl(context, "https://promote.telegram.org/guidelines"),
                                                    resourceProvider
                                                )
                                            )
                                            .show();
                                            AndroidUtilities.runOnUIThread(remove);
                                    }, 200);
                                }

                                @Override
                                public void onHidden() {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        BulletinFactory.of(fragment)
                                            .createAdReportedBulletin(LocaleController.getString(R.string.AdHidden))
                                            .show();
                                        AndroidUtilities.runOnUIThread(remove);
                                    }, 200);
                                }

                                @Override
                                public void onPremiumRequired() {
                                    fragment.showDialog(new PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_ADS, true));
                                }
                            })
                            .show();
                    });
                } else if (response instanceof TLRPC.TL_channels_sponsoredMessageReportResultReported) {
                    AndroidUtilities.runOnUIThread(() -> {
                        BulletinFactory.of(fragment)
                            .createAdReportedBulletin(
                                AndroidUtilities.replaceSingleTag(
                                    LocaleController.getString(R.string.AdReported),
                                    -1,
                                    AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD,
                                    () -> Browser.openUrl(context, "https://promote.telegram.org/guidelines"),
                                    resourceProvider
                                )
                            )
                            .show();
                        AndroidUtilities.runOnUIThread(remove);
                    }, 200);
                } else if (response instanceof TLRPC.TL_channels_sponsoredMessageReportResultAdsHidden) {
                    AndroidUtilities.runOnUIThread(() -> {
                        BulletinFactory.of(fragment)
                            .createAdReportedBulletin(LocaleController.getString(R.string.AdHidden))
                            .show();
                        MessagesController.getInstance(currentAccount).disableAds(false);
                        AndroidUtilities.runOnUIThread(remove);
                    }, 200);
                }
            } else if (error != null && "AD_EXPIRED".equalsIgnoreCase(error.text)) {
                AndroidUtilities.runOnUIThread(() -> {
                    BulletinFactory.of(fragment)
                        .createAdReportedBulletin(
                            AndroidUtilities.replaceSingleTag(
                                LocaleController.getString(R.string.AdReported),
                                -1,
                                AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD,
                                () -> Browser.openUrl(context, "https://promote.telegram.org/guidelines"),
                                resourceProvider
                            )
                        )
                        .show();
                    AndroidUtilities.runOnUIThread(remove);
                }, 200);
            }
        });
    }

}
