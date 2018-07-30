package org.telegram.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MrzRecognizer;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.camera.CameraView;
import org.telegram.messenger.camera.Size;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

@TargetApi(18)
public class MrzCameraActivity extends BaseFragment implements Camera.PreviewCallback{

    private TextView titleTextView;
    private TextView descriptionText;
    private CameraView cameraView;
    private HandlerThread backgroundHandlerThread=new HandlerThread("MrzCamera");
    private Handler handler;
    private TextView recognizedMrzView;

    private MrzCameraActivityDelegate delegate;
    private boolean recognized;

    public interface MrzCameraActivityDelegate {
        void didFindMrzInfo(MrzRecognizer.Result result);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        destroy(false, null);
        getParentActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Override
    public View createView(Context context) {
        getParentActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarWhiteSelector), false);
        actionBar.setCastShadows(false);
        if (!AndroidUtilities.isTablet()) {
            actionBar.showActionModeTop();
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new ViewGroup(context) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);

				cameraView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (width * 0.704f), MeasureSpec.EXACTLY));
				titleTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
				descriptionText.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.9f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));

                setMeasuredDimension(width, height);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int width = r - l;
                int height = b - t;

				int y = 0;
				cameraView.layout(0, y, cameraView.getMeasuredWidth(), y + cameraView.getMeasuredHeight());
				recognizedMrzView.setTextSize(TypedValue.COMPLEX_UNIT_PX, cameraView.getMeasuredHeight()/22);
				recognizedMrzView.setPadding(0, 0, 0, cameraView.getMeasuredHeight()/15);
				y = (int) (height * 0.65f);
				titleTextView.layout(0, y, titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
				y = (int) (height * 0.74f);
				int x = (int) (width * 0.05f);
				descriptionText.layout(x, y, x + descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
            }
        };
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        ViewGroup viewGroup = (ViewGroup) fragmentView;
        viewGroup.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        cameraView=new CameraView(context, false);
        cameraView.setDelegate(new CameraView.CameraViewDelegate(){
            @Override
            public void onCameraCreated(Camera camera){
                Camera.Parameters params=camera.getParameters();
                float evStep=params.getExposureCompensationStep();
                float maxEv=params.getMaxExposureCompensation()*evStep;
                params.setExposureCompensation(maxEv<=2f ? params.getMaxExposureCompensation() : Math.round(2f/evStep));
                camera.setParameters(params);
            }

            @Override
            public void onCameraInit(){
                startRecognizing();
            }
        });
        viewGroup.addView(cameraView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        titleTextView.setText(LocaleController.getString("PassportScanPassport", R.string.PassportScanPassport));
        viewGroup.addView(titleTextView);

        descriptionText = new TextView(context);
        descriptionText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        descriptionText.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        descriptionText.setText(LocaleController.getString("PassportScanPassportInfo", R.string.PassportScanPassportInfo));
        viewGroup.addView(descriptionText);

        recognizedMrzView=new TextView(context);
        recognizedMrzView.setTypeface(Typeface.MONOSPACE);
        recognizedMrzView.setTextColor(0xFFFFFFFF);
        recognizedMrzView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        recognizedMrzView.setBackgroundColor(0x80000000);
        recognizedMrzView.setAlpha(0);
        cameraView.addView(recognizedMrzView);

        fragmentView.setKeepScreenOn(true);

        return fragmentView;
    }

    public void setDelegate(MrzCameraActivityDelegate mrzCameraActivityDelegate) {
        delegate = mrzCameraActivityDelegate;
    }


    public void destroy(boolean async, final Runnable beforeDestroyRunnable) {
    	cameraView.destroy(async, beforeDestroyRunnable);
    	cameraView=null;
        backgroundHandlerThread.quitSafely();
    }

    public void cancel() {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recordStopped, 0);
    }

    public void hideCamera(boolean async) {
        destroy(async, null);
    }

    private void startRecognizing() {
        backgroundHandlerThread.start();
        handler = new Handler(backgroundHandlerThread.getLooper());
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (cameraView != null && !recognized && cameraView.getCameraSession() != null) {
                    cameraView.getCameraSession().setOneShotPreviewCallback(MrzCameraActivity.this);
                    AndroidUtilities.runOnUIThread(this, 500);
                }
            }
        });
    }

    @Override
    public void onPreviewFrame(final byte[] data, final Camera camera){
        handler.post(new Runnable(){
            @Override
            public void run(){
            	try{
                    Size size=cameraView.getPreviewSize();
                    final MrzRecognizer.Result res=MrzRecognizer.recognize(data, size.getWidth(), size.getHeight(), cameraView.getCameraSession().getDisplayOrientation());
                    if(res!=null && !TextUtils.isEmpty(res.firstName) && !TextUtils.isEmpty(res.lastName) && !TextUtils.isEmpty(res.number) && res.birthDay!=0 &&
                            (res.expiryDay!=0 || res.doesNotExpire) && res.gender!=MrzRecognizer.Result.GENDER_UNKNOWN){
                        recognized=true;
                        camera.stopPreview();
                        AndroidUtilities.runOnUIThread(new Runnable(){
                            @Override
                            public void run(){
                                recognizedMrzView.setText(res.rawMRZ);
                                recognizedMrzView.animate().setDuration(200).alpha(1f).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                                if(delegate!=null){
                                    delegate.didFindMrzInfo(res);
                                }
                                AndroidUtilities.runOnUIThread(new Runnable(){
                                    @Override
                                    public void run(){
                                        finishFragment();
                                    }
                                }, 1200);
                            }
                        });
                    }
                }catch(Exception ignore){}
            }
        });
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarWhiteSelector),

                new ThemeDescription(titleTextView, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(descriptionText, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6),
        };
    }
}
