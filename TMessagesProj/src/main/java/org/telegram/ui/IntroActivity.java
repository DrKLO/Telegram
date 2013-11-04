/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragmentActivity;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.util.Locale;

public class IntroActivity extends SherlockFragmentActivity {
    private ViewPager viewPager;
    private ImageView topImage1;
    private ImageView topImage2;
    private View slidingView;
    private View parentSlidingView;
    private TextView startMessagingButton;
    private int lastPage = 0;
    private float density = 1;
    private boolean isRTL = false;
    private boolean justCreated = false;
    private boolean startPressed = false;
    private int[][] colors2 = new int[][] {
                new int[] {179, 179, 179},
                new int[] {247, 91, 47},
                new int[] {249, 145, 23},
                new int[] {250, 200, 0},
                new int[] {93, 195, 38},
                new int[] {47, 146, 232}
    };
    private int[] icons;
    private int[] titles;
    private int[] messages;
    int fixedFirstNum1;
    int fixedFirstNum2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.intro_layout);

        Locale locale = Locale.getDefault();
        String lang = locale.getLanguage();
        if (lang != null && lang.toLowerCase().equals("ar")) {
            isRTL = true;
            icons = new int[] {
                    R.drawable.icon5,
                    R.drawable.icon4,
                    R.drawable.icon3,
                    R.drawable.icon2,
                    R.drawable.icon1,
                    R.drawable.icon0
            };
            titles = new int[] {
                    R.string.Page6Title,
                    R.string.Page5Title,
                    R.string.Page4Title,
                    R.string.Page3Title,
                    R.string.Page2Title,
                    R.string.Page1Title
            };
            messages = new int[] {
                    R.string.Page6Message,
                    R.string.Page5Message,
                    R.string.Page4Message,
                    R.string.Page3Message,
                    R.string.Page2Message,
                    R.string.Page1Message
            };
            fixedFirstNum1 = 5;
            fixedFirstNum2 = 4;
        } else {
            icons = new int[] {
                    R.drawable.icon0,
                    R.drawable.icon1,
                    R.drawable.icon2,
                    R.drawable.icon3,
                    R.drawable.icon4,
                    R.drawable.icon5
            };
            titles = new int[] {
                    R.string.Page1Title,
                    R.string.Page2Title,
                    R.string.Page3Title,
                    R.string.Page4Title,
                    R.string.Page5Title,
                    R.string.Page6Title
            };
            messages = new int[] {
                    R.string.Page1Message,
                    R.string.Page2Message,
                    R.string.Page3Message,
                    R.string.Page4Message,
                    R.string.Page5Message,
                    R.string.Page6Message
            };
            fixedFirstNum1 = 0;
            fixedFirstNum2 = 1;
        }
        density = getResources().getDisplayMetrics().density;
        viewPager = (ViewPager)findViewById(R.id.intro_view_pager);
        slidingView = findViewById(R.id.sliding_view);
        parentSlidingView = findViewById(R.id.parent_slinding_view);
        startMessagingButton = (TextView)findViewById(R.id.start_messaging_button);
        topImage1 = (ImageView)findViewById(R.id.icon_image1);
        topImage2 = (ImageView)findViewById(R.id.icon_image2);
        topImage2.setVisibility(View.GONE);
        viewPager.setAdapter(new IntroAdapter());
        viewPager.setPageMargin(0);
        viewPager.setOffscreenPageLimit(1);
        viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                updateColors(position, positionOffset);
            }

            @Override
            public void onPageSelected(int i) {

            }

            @Override
            public void onPageScrollStateChanged(int i) {
                if (i == ViewPager.SCROLL_STATE_IDLE || i == ViewPager.SCROLL_STATE_SETTLING) {
                    if (lastPage != viewPager.getCurrentItem()) {
                        int prevPage = lastPage;
                        lastPage = viewPager.getCurrentItem();

                        final ImageView fadeoutImage;
                        final ImageView fadeinImage;
                        if (topImage1.getVisibility() == View.VISIBLE) {
                            fadeoutImage = topImage1;
                            fadeinImage = topImage2;

                        } else {
                            fadeoutImage = topImage2;
                            fadeinImage = topImage1;
                        }

                        fadeinImage.bringToFront();
                        fadeinImage.setImageResource(icons[lastPage]);
                        fadeinImage.clearAnimation();
                        fadeoutImage.clearAnimation();


                        Animation outAnimation = AnimationUtils.loadAnimation(IntroActivity.this, R.anim.icon_anim_fade_out);
                        outAnimation.setAnimationListener(new Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(Animation animation) {
                            }

                            @Override
                            public void onAnimationEnd(Animation animation) {
                                fadeoutImage.setVisibility(View.GONE);
                            }

                            @Override
                            public void onAnimationRepeat(Animation animation) {

                            }
                        });

                        Animation inAnimation = AnimationUtils.loadAnimation(IntroActivity.this, R.anim.icon_anim_fade_in);
                        inAnimation.setAnimationListener(new Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(Animation animation) {
                                fadeinImage.setVisibility(View.VISIBLE);
                            }

                            @Override
                            public void onAnimationEnd(Animation animation) {
                            }

                            @Override
                            public void onAnimationRepeat(Animation animation) {

                            }
                        });


                        fadeoutImage.startAnimation(outAnimation);
                        fadeinImage.startAnimation(inAnimation);
                    }
                }
            }
        });

        startMessagingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (startPressed) {
                    return;
                }
                startPressed = true;
                Intent intent2 = new Intent(IntroActivity.this, LoginActivity.class);
                startActivity(intent2);
                finish();
            }
        });

        justCreated = true;

        getSupportActionBar().hide();
        fixLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (justCreated) {
            if (isRTL) {
                viewPager.setCurrentItem(5);
                lastPage = 5;
            } else {
                viewPager.setCurrentItem(0);
                lastPage = 0;
            }
            justCreated = false;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void fixLayout() {
        viewPager.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                WindowManager manager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
                Display display = manager.getDefaultDisplay();
                int rotation = display.getRotation();
                density = ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density;

                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)parentSlidingView.getLayoutParams();
                FrameLayout.LayoutParams buttonParams = (FrameLayout.LayoutParams)startMessagingButton.getLayoutParams();
                if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                    params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, (int)(50 * density));
                    buttonParams.height = (int)(density * 52);
                } else {
                    params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, (int)(82 * density));
                    buttonParams.height = (int)(density * 84);
                }
                parentSlidingView.setLayoutParams(params);
                startMessagingButton.setLayoutParams(buttonParams);

                updateColors(viewPager.getCurrentItem(), 0);
                viewPager.getViewTreeObserver().removeOnPreDrawListener(this);
                return false;
            }
        });
    }

    private void updateColors(int position, float positionOffset) {
        int colorPosition = position;
        int nextColorPosition = colorPosition + 1;
        float offset = positionOffset;
        if (isRTL) {
            colorPosition = 5 - position;
            if (positionOffset != 0) {
                colorPosition--;
                offset = 1 - offset;
            }
            nextColorPosition = colorPosition + 1;
        }
        if (colorPosition >= 0) {
            int r = colors2[colorPosition][0];
            int g = colors2[colorPosition][1];
            int b = colors2[colorPosition][2];
            if (nextColorPosition < colors2.length && nextColorPosition > 0 && offset != 0) {
                r += (colors2[nextColorPosition][0] - colors2[colorPosition][0]) * offset;
                g += (colors2[nextColorPosition][1] - colors2[colorPosition][1]) * offset;
                b += (colors2[nextColorPosition][2] - colors2[colorPosition][2]) * offset;
            }
            slidingView.setBackgroundColor(colorFromRGB(r, g, b));
        }
        int width = parentSlidingView.getWidth() / 6;
        FrameLayout.LayoutParams parentParams = (FrameLayout.LayoutParams)parentSlidingView.getLayoutParams();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)slidingView.getLayoutParams();
        int posX = (int)(24 * density + position * width + positionOffset * width);
        if (position >= colors2.length - 2) {
            int missed = parentSlidingView.getWidth() - width * 6;
            posX += missed * (position == colors2.length - 2 ? positionOffset : 1);
        }
        params.width = width;
        params.setMargins(posX, params.topMargin, params.rightMargin, parentParams.bottomMargin);
        slidingView.setLayoutParams(params);
    }

    private int colorFromRGB(int red, int green, int blue) {
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    private class IntroAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return 6;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View view = View.inflate(container.getContext(), R.layout.intro_view_layout, null);
            TextView headerTextView = (TextView)view.findViewById(R.id.header_text);
            Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
            headerTextView.setTypeface(typeface);
            TextView messageTextView = (TextView)view.findViewById(R.id.message_text);
            messageTextView.setTypeface(typeface);
            container.addView(view, 0);

            headerTextView.setText(getString(titles[position]));
            messageTextView.setText(Html.fromHtml(getString(messages[position])));

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
        public void finishUpdate(View arg0) {
        }

        @Override
        public void restoreState(Parcelable arg0, ClassLoader arg1) {
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void startUpdate(View arg0) {
        }
    }
}
