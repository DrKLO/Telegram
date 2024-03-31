package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.MediaActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.ViewPagerFixed;

import java.util.ArrayList;
import java.util.Collections;

public class ReportAdBottomSheet extends BottomSheet {

    private final ViewPagerFixed viewPager;
    private static final int PAGE_TYPE_OPTIONS = 0;
    private static final int PAGE_TYPE_SUB_OPTIONS = 1;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MessageObject messageObject;
    private final TLRPC.Chat chat;
    private Listener listener;

    interface Listener {
        void onReported();
        void onHidden();
        void onPremiumRequired();
    }

    public ReportAdBottomSheet(Context context, Theme.ResourcesProvider resourcesProvider, MessageObject messageObject, TLRPC.Chat chat) {
        super(context, true, resourcesProvider);
        this.messageObject = messageObject;
        this.chat = chat;
        backgroundPaint.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
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

        if (messageObject == null) {
            setReportChooseOption(null);
        }
    }

    public ReportAdBottomSheet setReportChooseOption(TLRPC.TL_channels_sponsoredMessageReportResultChooseOption chooseOption) {
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

    public ReportAdBottomSheet setListener(Listener listener) {
        this.listener = listener;
        return this;
    }

    @Override
    public void onBackPressed() {
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

    private void submitOption(CharSequence optionText, byte[] option) {
        TLRPC.TL_channels_reportSponsoredMessage req = new TLRPC.TL_channels_reportSponsoredMessage();
        req.channel = MessagesController.getInputChannel(chat);
        req.random_id = messageObject.sponsoredId;
        req.option = option;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            AndroidUtilities.runOnUIThread(() -> {
                if (response != null) {
                    if (response instanceof TLRPC.TL_channels_sponsoredMessageReportResultChooseOption) {
                        TLRPC.TL_channels_sponsoredMessageReportResultChooseOption result = (TLRPC.TL_channels_sponsoredMessageReportResultChooseOption) response;
                        int nextPosition = viewPager.currentPosition + 1;
                        viewPager.scrollToPosition(nextPosition);
                        Page nextPage = (Page) viewPager.getViewPages()[1];
                        if (nextPage != null) {
                            nextPage.setOption(result);
                            if (optionText != null) {
                                nextPage.setHeaderText(optionText);
                            }
                        }
                    } else if (response instanceof TLRPC.TL_channels_sponsoredMessageReportResultAdsHidden) {
                        if (listener != null) {
                            listener.onHidden();
                            dismiss();
                        }
                    } else if (response instanceof TLRPC.TL_channels_sponsoredMessageReportResultReported) {
                        if (listener != null) {
                            listener.onReported();
                            dismiss();
                        }
                    }
                } else if (error != null) {
                    if ("PREMIUM_ACCOUNT_REQUIRED".equals(error.text)) {
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
        TLRPC.TL_channels_sponsoredMessageReportResultChooseOption option;

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
            headerView.setText(LocaleController.getString("ReportAd", R.string.ReportAd));
            headerView.backDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            headerView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
            addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

            listView = new UniversalRecyclerView(context, currentAccount, 0, this::fillItems, this::onClick, null, resourcesProvider);
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
            this.option = option;
            listView.adapter.update(false);
        }

        public void setHeaderText(CharSequence headerText) {
            headerView.setText(headerText);
            headerView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(120), MeasureSpec.AT_MOST));
            if (listView != null) {
                listView.adapter.update(true);
            }
        }

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

            if (option != null) {
                HeaderCell headerCell = new HeaderCell(getContext(), Theme.key_windowBackgroundWhiteBlueHeader, 21, 0, 0, false, resourcesProvider);
                headerCell.setText(option.title);
                headerCell.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
                UItem headerItem = UItem.asCustom(headerCell);
                headerItem.id = -2;
                items.add(headerItem);
                height += 40;

                for (int i = 0; i < option.options.size(); i++) {
                    UItem buttonItem = new UItem(UniversalAdapter.VIEW_TYPE_RIGHT_ICON_TEXT, false);
                    buttonItem.text = option.options.get(i).text;
                    buttonItem.backgroundKey = Theme.key_dialogBackground;
                    buttonItem.iconResId = R.drawable.msg_arrowright;
                    buttonItem.id = i;
                    items.add(buttonItem);
                    height += 50;
                }
                items.get(items.size() - 1).hideDivider = true;

                if (pageType == PAGE_TYPE_OPTIONS) {
                    FrameLayout frameLayout = new FrameLayout(getContext());
                    Drawable shadowDrawable = Theme.getThemedDrawable(getContext(), R.drawable.greydivider, Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourcesProvider));
                    Drawable background = new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
                    combinedDrawable.setFullsize(true);
                    frameLayout.setBackground(combinedDrawable);
                    LinkSpanDrawable.LinksTextView textView = new LinkSpanDrawable.LinksTextView(getContext());
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    textView.setText(AndroidUtilities.replaceLinks(LocaleController.getString("ReportAdLearnMore", R.string.ReportAdLearnMore), resourcesProvider));
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
                if (option != null) {
                    TLRPC.TL_sponsoredMessageReportOption clickedOption = option.options.get(item.id);
                    if (clickedOption != null) {
                        submitOption(clickedOption.text, clickedOption.option);
                    }
                } else {
                    submitOption(item.text, null);
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
                textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
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
}
