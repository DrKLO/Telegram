package org.telegram.ui.Components;

import android.app.Dialog;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import androidx.core.graphics.ColorUtils;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

public class GroupCallRecordAlert extends BottomSheet {

    private ViewPager viewPager;
    private TextView positiveButton;
    private LinearLayout titlesLayout;
    private TextView[] titles;

    private float pageOffset;
    private int currentPage;

    public GroupCallRecordAlert(Context context, TLRPC.Chat chat, boolean hasVideo) {
        super(context, false);

        int color = Theme.getColor(Theme.key_voipgroup_inviteMembersBackground);
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));

        containerView = new FrameLayout(context) {

            boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                boolean isLandscape = View.MeasureSpec.getSize(widthMeasureSpec) > View.MeasureSpec.getSize(heightMeasureSpec);
                ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) positiveButton.getLayoutParams();
                if (isLandscape) {
                    marginLayoutParams.rightMargin = marginLayoutParams.leftMargin = AndroidUtilities.dp(80);
                } else {
                    marginLayoutParams.rightMargin = marginLayoutParams.leftMargin = AndroidUtilities.dp(16);
                }
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int padding = (width - AndroidUtilities.dp(200)) / 2;
                viewPager.setPadding(padding, 0, padding, 0);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(370), MeasureSpec.EXACTLY));
                measureChildWithMargins(titlesLayout, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), 0, View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), View.MeasureSpec.EXACTLY), 0);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateTitlesLayout();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setClipChildren(false);
        containerView.setBackgroundDrawable(shadowDrawable);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        TextView titleTextView = new TextView(getContext());
        if (ChatObject.isChannelOrGiga(chat)) {
            titleTextView.setText(LocaleController.getString("VoipChannelRecordVoiceChat", R.string.VoipChannelRecordVoiceChat));
        } else {
            titleTextView.setText(LocaleController.getString("VoipRecordVoiceChat", R.string.VoipRecordVoiceChat));
        }
        titleTextView.setTextColor(0xffffffff);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        containerView.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 29, 24, 0));

        TextView infoTextView = new TextView(getContext());
        infoTextView.setText(LocaleController.getString("VoipRecordVoiceChatInfo", R.string.VoipRecordVoiceChatInfo));
        infoTextView.setTextColor(0xffffffff);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        infoTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        containerView.addView(infoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 62, 24, 0));

        titles = new TextView[3];

        viewPager = new ViewPager(context);
        viewPager.setClipChildren(false);
        viewPager.setOffscreenPageLimit(4);
        viewPager.setClipToPadding(false);
        AndroidUtilities.setViewPagerEdgeEffectColor(viewPager, 0x7f000000);
        viewPager.setAdapter(new Adapter());
        viewPager.setPageMargin(0);
        containerView.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL, 0, 100, 0, 130));
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                currentPage = position;
                pageOffset = positionOffset;
                updateTitlesLayout();
            }

            @Override
            public void onPageSelected(int i) {

            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        View leftView = new View(getContext());
        leftView.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{color, 0}));
        containerView.addView(leftView, LayoutHelper.createFrame(120, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 100, 0, 130));

        View rightView = new View(getContext());
        rightView.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0, color}));
        containerView.addView(rightView, LayoutHelper.createFrame(120, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP, 0, 100, 0, 130));

        positiveButton = new TextView(getContext()) {

            private Paint[] gradientPaint = new Paint[titles.length];
            {
                for (int a = 0; a < gradientPaint.length; a++) {
                    gradientPaint[a] = new Paint(Paint.ANTI_ALIAS_FLAG);
                }
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                for (int a = 0; a < gradientPaint.length; a++) {
                    int color1;
                    int color2;
                    int color3;
                    if (a == 0) {
                        color1 = 0xff57A4FE;
                        color2 = 0xff766EE9;
                        color3 = 0;
                    } else if (a == 1) {
                        color1 = 0xff77E55C;
                        color2 = 0xff56C7FE;
                        color3 = 0;
                    } else {
                        color1 = 0xff766EE9;
                        color2 = 0xffF05459;
                        color3 = 0xffE4A756;
                    }
                    Shader gradient;
                    if (color3 != 0) {
                        gradient = new LinearGradient(0, 0, getMeasuredWidth(), 0, new int[]{color1, color2, color3}, null, Shader.TileMode.CLAMP);
                    } else {
                        gradient = new LinearGradient(0, 0, getMeasuredWidth(), 0, new int[]{color1, color2}, null, Shader.TileMode.CLAMP);
                    }
                    gradientPaint[a].setShader(gradient);
                }
            }


            @Override
            protected void onDraw(Canvas canvas) {
                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                gradientPaint[currentPage].setAlpha(255);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6), AndroidUtilities.dp(6), gradientPaint[currentPage]);
                if (pageOffset > 0 && currentPage + 1 < gradientPaint.length) {
                    gradientPaint[currentPage + 1].setAlpha((int) (255 * pageOffset));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6), AndroidUtilities.dp(6), gradientPaint[currentPage + 1]);
                }
                super.onDraw(canvas);
            }
        };
        positiveButton.setMinWidth(AndroidUtilities.dp(64));
        positiveButton.setTag(Dialog.BUTTON_POSITIVE);
        positiveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        positiveButton.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
        positiveButton.setGravity(Gravity.CENTER);
        positiveButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        positiveButton.setText(LocaleController.getString("VoipRecordStart", R.string.VoipRecordStart));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            positiveButton.setForeground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_nameText), (int) (255 * 0.3f))));
        }
        positiveButton.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        positiveButton.setOnClickListener(view -> {
            onStartRecord(currentPage);
            dismiss();
        });

        containerView.addView(positiveButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 0, 0, 0, 64));

        titlesLayout = new LinearLayout(context);
        containerView.addView(titlesLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 64, Gravity.BOTTOM));

        for (int a = 0; a < titles.length; a++) {
            titles[a] = new TextView(context);
            titles[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            titles[a].setTextColor(0xffffffff);
            titles[a].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titles[a].setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
            titles[a].setGravity(Gravity.CENTER_VERTICAL);
            titles[a].setSingleLine(true);
            titlesLayout.addView(titles[a], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
            if (a == 0) {
                titles[a].setText(LocaleController.getString("VoipRecordAudio", R.string.VoipRecordAudio));
            } else if (a == 1) {
                titles[a].setText(LocaleController.getString("VoipRecordPortrait", R.string.VoipRecordPortrait));
            } else {
                titles[a].setText(LocaleController.getString("VoipRecordLandscape", R.string.VoipRecordLandscape));
            }
            int num = a;
            titles[a].setOnClickListener(view -> viewPager.setCurrentItem(num, true));
        }
        if (hasVideo) {
            viewPager.setCurrentItem(1);
        }
    }

    private void updateTitlesLayout() {
        View current = titles[currentPage];
        View next = currentPage < titles.length - 1 ? titles[currentPage + 1] : null;
        float cx = containerView.getMeasuredWidth() / 2;
        float currentCx = current.getLeft() + current.getMeasuredWidth() / 2;
        float tx = containerView.getMeasuredWidth() / 2 - currentCx;
        if (next != null) {
            float nextCx = next.getLeft() + next.getMeasuredWidth() / 2;
            tx -= (nextCx - currentCx) * pageOffset;
        }
        for (int a = 0; a < titles.length; a++) {
            float alpha;
            float scale;
            if (a < currentPage || a > currentPage + 1) {
                alpha = 0.7f;
                scale = 0.9f;
            } else if (a == currentPage) {
                alpha = 1.0f - 0.3f * pageOffset;
                scale = 1.0f - 0.1f * pageOffset;
            } else {
                alpha = 0.7f + 0.3f * pageOffset;
                scale = 0.9f + 0.1f * pageOffset;
            }
            titles[a].setAlpha(alpha);
            titles[a].setScaleX(scale);
            titles[a].setScaleY(scale);
        }
        titlesLayout.setTranslationX(tx);
        positiveButton.invalidate();
    }

    protected void onStartRecord(int type) {

    }

    private class Adapter extends PagerAdapter {
        @Override
        public int getCount() {
            return titles.length;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View view;

            ImageView imageView = new ImageView(getContext());
            imageView.setTag(position);
            imageView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            imageView.setLayoutParams(new ViewGroup.LayoutParams(AndroidUtilities.dp(200), ViewGroup.LayoutParams.MATCH_PARENT));
            view = imageView;
            int res;
            if (position == 0) {
                res = R.raw.record_audio;
            } else if (position == 1) {
                res =  R.raw.record_video_p;
            } else {
                res =  R.raw.record_video_l;
            }
            String svg = RLottieDrawable.readRes(null, res);
            SvgHelper.SvgDrawable drawable = SvgHelper.getDrawable(svg);
            drawable.setAspectFill(false);
            imageView.setImageDrawable(drawable);
            if (view.getParent() != null) {
                ViewGroup parent = (ViewGroup) view.getParent();
                parent.removeView(view);
            }
            container.addView(view, 0);
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view.equals(object);
        }

        @Override
        public void restoreState(Parcelable arg0, ClassLoader arg1) {
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }
}
