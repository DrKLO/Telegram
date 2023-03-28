package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MrzRecognizer;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.R;
import org.telegram.messenger.SRPHelper;
import org.telegram.messenger.SecureDocument;
import org.telegram.messenger.SecureDocumentKey;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.HintEditText;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.Components.SlideView;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.Cipher;

public class PassportActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public final static int TYPE_REQUEST = 0;
    public final static int TYPE_IDENTITY = 1;
    public final static int TYPE_ADDRESS = 2;
    public final static int TYPE_PHONE = 3;
    public final static int TYPE_EMAIL = 4;
    public final static int TYPE_PASSWORD = 5;
    public final static int TYPE_EMAIL_VERIFICATION = 6;
    public final static int TYPE_PHONE_VERIFICATION = 7;
    public final static int TYPE_MANAGE = 8;

    private final static int FIELD_NAME = 0;
    private final static int FIELD_MIDNAME = 1;
    private final static int FIELD_SURNAME = 2;
    private final static int FIELD_BIRTHDAY = 3;
    private final static int FIELD_GENDER = 4;
    private final static int FIELD_CITIZENSHIP = 5;
    private final static int FIELD_RESIDENCE = 6;
    private final static int FIELD_CARDNUMBER = 7;
    private final static int FIELD_EXPIRE = 8;
    private final static int FIELD_IDENTITY_COUNT = 9;
    private final static int FIELD_IDENTITY_NODOC_COUNT = 7;

    private final static int FIELD_NATIVE_NAME = 0;
    private final static int FIELD_NATIVE_MIDNAME = 1;
    private final static int FIELD_NATIVE_SURNAME = 2;
    private final static int FIELD_NATIVE_COUNT = 3;

    private final static int FIELD_STREET1 = 0;
    private final static int FIELD_STREET2 = 1;
    private final static int FIELD_POSTCODE = 2;
    private final static int FIELD_CITY = 3;
    private final static int FIELD_STATE = 4;
    private final static int FIELD_COUNTRY = 5;
    private final static int FIELD_ADDRESS_COUNT = 6;

    private final static int FIELD_PHONECOUNTRY = 0;
    private final static int FIELD_PHONECODE = 1;
    private final static int FIELD_PHONE = 2;

    private final static int FIELD_EMAIL = 0;

    private final static int FIELD_PASSWORD = 0;

    private final static int UPLOADING_TYPE_DOCUMENTS = 0;
    private final static int UPLOADING_TYPE_SELFIE = 1;
    private final static int UPLOADING_TYPE_FRONT = 2;
    private final static int UPLOADING_TYPE_REVERSE = 3;
    private final static int UPLOADING_TYPE_TRANSLATION = 4;

    private String initialValues;
    private int currentActivityType;
    private long currentBotId;
    private String currentPayload;
    private String currentNonce;
    private boolean useCurrentValue;
    private String currentScope;
    private String currentCallbackUrl;
    private String currentPublicKey;
    private String currentCitizeship = "";
    private String currentResidence = "";
    private String currentGender;
    private int[] currentExpireDate = new int[3];
    private TLRPC.TL_account_authorizationForm currentForm;

    private TLRPC.TL_secureRequiredType currentType;
    private TLRPC.TL_secureRequiredType currentDocumentsType;
    private ArrayList<TLRPC.TL_secureRequiredType> availableDocumentTypes;
    private TLRPC.TL_secureValue currentTypeValue;
    private TLRPC.TL_secureValue currentDocumentsTypeValue;
    private TLRPC.account_Password currentPassword;
    private TLRPC.TL_auth_sentCode currentPhoneVerification;

    private ActionBarMenuItem doneItem;
    private AnimatorSet doneItemAnimation;
    private ContextProgressView progressView;

    private TextView acceptTextView;
    private ContextProgressView progressViewButton;
    private FrameLayout bottomLayout;

    private TextSettingsCell uploadDocumentCell;
    private View extraBackgroundView;
    private View extraBackgroundView2;
    private TextDetailSettingsCell uploadFrontCell;
    private TextDetailSettingsCell uploadReverseCell;
    private TextDetailSettingsCell uploadSelfieCell;
    private TextSettingsCell uploadTranslationCell;
    private EditTextBoldCursor[] inputFields;
    private ViewGroup[] inputFieldContainers;
    private EditTextBoldCursor[] inputExtraFields;
    private ScrollView scrollView;
    private LinearLayout linearLayout2;
    private LinearLayout documentsLayout;
    private LinearLayout frontLayout;
    private LinearLayout reverseLayout;
    private LinearLayout selfieLayout;
    private LinearLayout translationLayout;
    private LinearLayout currentPhotoViewerLayout;
    private HeaderCell headerCell;
    private ArrayList<View> dividers = new ArrayList<>();
    private ShadowSectionCell sectionCell;
    private ShadowSectionCell sectionCell2;
    private TextInfoPrivacyCell bottomCell;
    private TextInfoPrivacyCell bottomCellTranslation;
    private TextInfoPrivacyCell topErrorCell;
    private TextInfoPrivacyCell nativeInfoCell;
    private TextSettingsCell scanDocumentCell;

    private int scrollHeight;

    private boolean[] nonLatinNames = new boolean[3];
    private boolean allowNonLatinName = true;

    private boolean documentOnly;

    private TextView plusTextView;

    private TextSettingsCell addDocumentCell;
    private TextSettingsCell deletePassportCell;
    private ShadowSectionCell addDocumentSectionCell;
    private LinearLayout emptyLayout;
    private ImageView emptyImageView;
    private TextView emptyTextView1;
    private TextView emptyTextView2;
    private TextView emptyTextView3;

    private EmptyTextProgressView emptyView;
    private TextInfoPrivacyCell passwordRequestTextView;
    private TextInfoPrivacyCell passwordInfoRequestTextView;
    private ImageView noPasswordImageView;
    private TextView noPasswordTextView;
    private TextView noPasswordSetTextView;
    private FrameLayout passwordAvatarContainer;
    private TextView passwordForgotButton;
    private int usingSavedPassword;
    private byte[] savedPasswordHash;
    private byte[] savedSaltedPassword;

    private String currentPicturePath;
    private ChatAttachAlert chatAttachAlert;
    private int uploadingFileType;

    private int emailCodeLength;

    private ArrayList<String> countriesArray = new ArrayList<>();
    private HashMap<String, String> countriesMap = new HashMap<>();
    private HashMap<String, String> codesMap = new HashMap<>();
    private HashMap<String, String> phoneFormatMap = new HashMap<>();
    private HashMap<String, String> languageMap;

    private boolean ignoreOnTextChange;
    private boolean ignoreOnPhoneChange;

    private static final int info_item = 1;
    private static final int done_button = 2;

    private final static int attach_photo = 0;
    private final static int attach_document = 4;

    private long secureSecretId;
    private byte[] secureSecret;
    private String currentEmail;
    private byte[] saltedPassword;

    private boolean ignoreOnFailure;
    private boolean callbackCalled;
    private PassportActivity presentAfterAnimation;

    private ArrayList<SecureDocument> documents = new ArrayList<>();
    private SecureDocument selfieDocument;
    private ArrayList<SecureDocument> translationDocuments = new ArrayList<>();
    private SecureDocument frontDocument;
    private SecureDocument reverseDocument;
    private HashMap<SecureDocument, SecureDocumentCell> documentsCells = new HashMap<>();
    private HashMap<String, SecureDocument> uploadingDocuments = new HashMap<>();
    private HashMap<TLRPC.TL_secureRequiredType, HashMap<String, String>> typesValues = new HashMap<>();
    private HashMap<TLRPC.TL_secureRequiredType, TextDetailSecureCell> typesViews = new HashMap<>();
    private HashMap<TLRPC.TL_secureRequiredType, TLRPC.TL_secureRequiredType> documentsToTypesLink = new HashMap<>();
    private HashMap<String, String> currentValues;
    private HashMap<String, String> currentDocumentValues;
    private HashMap<String, HashMap<String, String>> errorsMap = new HashMap<>();
    private HashMap<String, String> mainErrorsMap = new HashMap<>();
    private HashMap<String, String> fieldsErrors;
    private HashMap<String, String> documentsErrors;
    private HashMap<String, String> errorsValues = new HashMap<>();
    private CharSequence noAllDocumentsErrorText;
    private CharSequence noAllTranslationErrorText;

    private PassportActivityDelegate delegate;

    private boolean needActivityResult;

    private interface PassportActivityDelegate {
        void saveValue(TLRPC.TL_secureRequiredType type, String text, String json, TLRPC.TL_secureRequiredType documentType, String documentsJson, ArrayList<SecureDocument> documents, SecureDocument selfie, ArrayList<SecureDocument> translationDocuments, SecureDocument front, SecureDocument reverse, Runnable finishRunnable, ErrorRunnable errorRunnable);
        void deleteValue(TLRPC.TL_secureRequiredType type, TLRPC.TL_secureRequiredType documentType, ArrayList<TLRPC.TL_secureRequiredType> documentRequiredTypes, boolean deleteType, Runnable finishRunnable, ErrorRunnable errorRunnable);
        SecureDocument saveFile(TLRPC.TL_secureFile secureFile);
    }

    private interface ErrorRunnable {
        void onError(String error, String text);
    }

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            if (index < 0 || index >= currentPhotoViewerLayout.getChildCount()) {
                return null;
            }
            SecureDocumentCell cell = (SecureDocumentCell) currentPhotoViewerLayout.getChildAt(index);
            int[] coords = new int[2];
            cell.imageView.getLocationInWindow(coords);
            PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
            object.viewX = coords[0];
            object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
            object.parentView = currentPhotoViewerLayout;
            object.imageReceiver = cell.imageView.getImageReceiver();
            object.thumb = object.imageReceiver.getBitmapSafe();
            return object;
        }

        @Override
        public void deleteImageAtIndex(int index) {
            SecureDocument document;
            if (uploadingFileType == UPLOADING_TYPE_SELFIE) {
                document = selfieDocument;
            } else if (uploadingFileType == UPLOADING_TYPE_TRANSLATION) {
                document = translationDocuments.get(index);
            } else if (uploadingFileType == UPLOADING_TYPE_FRONT) {
                document = frontDocument;
            } else if (uploadingFileType == UPLOADING_TYPE_REVERSE) {
                document = reverseDocument;
            } else {
                document = documents.get(index);
            }
            SecureDocumentCell cell = documentsCells.remove(document);
            if (cell == null) {
                return;
            }
            String key = null;
            String hash = getDocumentHash(document);
            if (uploadingFileType == UPLOADING_TYPE_SELFIE) {
                selfieDocument = null;
                key = "selfie" + hash;
            } else if (uploadingFileType == UPLOADING_TYPE_TRANSLATION) {
                key = "translation" + hash;
            } else if (uploadingFileType == UPLOADING_TYPE_FRONT) {
                frontDocument = null;
                key = "front" + hash;
            } else if (uploadingFileType == UPLOADING_TYPE_REVERSE) {
                reverseDocument = null;
                key = "reverse" + hash;
            } else if (uploadingFileType == UPLOADING_TYPE_DOCUMENTS) {
                key = "files" + hash;
            }

            if (key != null) {
                if (documentsErrors != null) {
                    documentsErrors.remove(key);
                }
                if (errorsValues != null) {
                    errorsValues.remove(key);
                }
            }

            updateUploadText(uploadingFileType);
            currentPhotoViewerLayout.removeView(cell);
        }

        @Override
        public String getDeleteMessageString() {
            if (uploadingFileType == UPLOADING_TYPE_SELFIE) {
                return LocaleController.formatString("PassportDeleteSelfieAlert", R.string.PassportDeleteSelfieAlert);
            } else {
                return LocaleController.formatString("PassportDeleteScanAlert", R.string.PassportDeleteScanAlert);
            }
        }
    };

    public class LinkSpan extends ClickableSpan {
        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(true);
            ds.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        }

        @Override
        public void onClick(View widget) {
            Browser.openUrl(getParentActivity(), currentForm.privacy_policy_url);
        }
    }

    public class TextDetailSecureCell extends FrameLayout {

        private TextView textView;
        private TextView valueTextView;
        private ImageView checkImageView;
        private boolean needDivider;

        public TextDetailSecureCell(Context context) {
            super(context);

            int padding = currentActivityType == TYPE_MANAGE ? 21 : 51;

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? padding : 21), 10, (LocaleController.isRTL ? 21 : padding), 0));

            valueTextView = new TextView(context);
            valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setEllipsize(TextUtils.TruncateAt.END);
            valueTextView.setPadding(0, 0, 0, 0);
            addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? padding : 21), 35, (LocaleController.isRTL ? 21 : padding), 0));

            checkImageView = new ImageView(context);
            checkImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addedIcon), PorterDuff.Mode.MULTIPLY));
            checkImageView.setImageResource(R.drawable.sticker_added);
            addView(checkImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 21, 25, 21, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }

        public void setTextAndValue(String text, CharSequence value, boolean divider) {
            textView.setText(text);
            valueTextView.setText(value);
            needDivider = divider;
            setWillNotDraw(!divider);
        }

        public void setChecked(boolean checked) {
            checkImageView.setVisibility(checked ? VISIBLE : INVISIBLE);
        }

        public void setValue(CharSequence value) {
            valueTextView.setText(value);
        }

        public void setNeedDivider(boolean value) {
            needDivider = value;
            setWillNotDraw(!needDivider);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (needDivider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    public class SecureDocumentCell extends FrameLayout implements DownloadController.FileDownloadProgressListener {

        private TextView textView;
        private TextView valueTextView;
        private BackupImageView imageView;
        private RadialProgress radialProgress;

        private int buttonState;
        private SecureDocument currentSecureDocument;

        private int TAG;

        public SecureDocumentCell(Context context) {
            super(context);

            TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
            radialProgress = new RadialProgress(this);

            imageView = new BackupImageView(context);
            addView(imageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 8, 21, 0));

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 81), 10, (LocaleController.isRTL ? 81 : 21), 0));

            valueTextView = new TextView(context);
            valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setPadding(0, 0, 0, 0);
            addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 81), 35, (LocaleController.isRTL ? 81 : 21), 0));

            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + 1, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            int x = imageView.getLeft() + (imageView.getMeasuredWidth() - AndroidUtilities.dp(24)) / 2;
            int y = imageView.getTop() + (imageView.getMeasuredHeight() - AndroidUtilities.dp(24)) / 2;
            radialProgress.setProgressRect(x, y, x + AndroidUtilities.dp(24), y + AndroidUtilities.dp(24));
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            boolean result = super.drawChild(canvas, child, drawingTime);
            if (child == imageView) {
                radialProgress.draw(canvas);
            }
            return result;
        }

        public void setTextAndValueAndImage(String text, CharSequence value, SecureDocument document) {
            textView.setText(text);
            valueTextView.setText(value);
            imageView.setImage(document, "48_48");
            currentSecureDocument = document;

            updateButtonState(false);
        }

        public void setValue(CharSequence value) {
            valueTextView.setText(value);
        }

        public void updateButtonState(boolean animated) {
            String fileName = FileLoader.getAttachFileName(currentSecureDocument);
            File path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(currentSecureDocument);
            boolean fileExists = path.exists();
            if (TextUtils.isEmpty(fileName)) {
                radialProgress.setBackground(null, false, false);
                return;
            }

            if (currentSecureDocument.path != null) {
                if (currentSecureDocument.inputFile != null) {
                    DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                    radialProgress.setBackground(null, false, animated);
                    buttonState = -1;
                } else {
                    DownloadController.getInstance(currentAccount).addLoadingFileObserver(currentSecureDocument.path, this);
                    buttonState = 1;
                    Float progress = ImageLoader.getInstance().getFileProgress(currentSecureDocument.path);
                    radialProgress.setBackground(getResources().getDrawable(R.drawable.circle), true, animated);
                    radialProgress.setProgress(progress != null ? progress : 0, false);
                    invalidate();
                }
            } else {
                if (fileExists) {
                    DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                    buttonState = -1;
                    radialProgress.setBackground(null, false, animated);
                    invalidate();
                } else {
                    DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, this);
                    buttonState = 1;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    radialProgress.setBackground(getResources().getDrawable(R.drawable.circle), true, animated);
                    radialProgress.setProgress(progress != null ? progress : 0, animated);
                    invalidate();
                }
            }
        }

        @Override
        public void invalidate() {
            super.invalidate();
            textView.invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }

        @Override
        public void onFailedDownload(String fileName, boolean canceled) {
            updateButtonState(false);
        }

        @Override
        public void onSuccessDownload(String fileName) {
            radialProgress.setProgress(1, true);
            updateButtonState(true);
        }

        @Override
        public void onProgressDownload(String fileName, long downloadedSize, long totalSize) {
            radialProgress.setProgress(Math.min(1f, downloadedSize / (float) totalSize), true);
            if (buttonState != 1) {
                updateButtonState(false);
            }
        }

        @Override
        public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {
            radialProgress.setProgress(Math.min(1f, uploadedSize / (float) totalSize), true);
        }

        @Override
        public int getObserverTag() {
            return TAG;
        }
    }

    public PassportActivity(int type, long botId, String scope, String publicKey, String payload, String nonce, String callbackUrl, TLRPC.TL_account_authorizationForm form, TLRPC.account_Password accountPassword) {
        this(type, form, accountPassword, null, null, null, null, null, null);
        currentBotId = botId;
        currentPayload = payload;
        currentNonce = nonce;
        currentScope = scope;
        currentPublicKey = publicKey;
        currentCallbackUrl = callbackUrl;
        if (type == TYPE_REQUEST) {
            if (!form.errors.isEmpty()) {
                try {
                    Collections.sort(form.errors, new Comparator<TLRPC.SecureValueError>() {

                        int getErrorValue(TLRPC.SecureValueError error) {
                            if (error instanceof TLRPC.TL_secureValueError) {
                                return 0;
                            } else if (error instanceof TLRPC.TL_secureValueErrorFrontSide) {
                                return 1;
                            } else if (error instanceof TLRPC.TL_secureValueErrorReverseSide) {
                                return 2;
                            } else if (error instanceof TLRPC.TL_secureValueErrorSelfie) {
                                return 3;
                            } else if (error instanceof TLRPC.TL_secureValueErrorTranslationFile) {
                                return 4;
                            } else if (error instanceof TLRPC.TL_secureValueErrorTranslationFiles) {
                                return 5;
                            } else if (error instanceof TLRPC.TL_secureValueErrorFile) {
                                return 6;
                            } else if (error instanceof TLRPC.TL_secureValueErrorFiles) {
                                return 7;
                            } else if (error instanceof TLRPC.TL_secureValueErrorData) {
                                TLRPC.TL_secureValueErrorData errorData = (TLRPC.TL_secureValueErrorData) error;
                                return getFieldCost(errorData.field);
                            }
                            return 100;
                        }

                        @Override
                        public int compare(TLRPC.SecureValueError e1, TLRPC.SecureValueError e2) {
                            int val1 = getErrorValue(e1);
                            int val2 = getErrorValue(e2);
                            if (val1 < val2) {
                                return -1;
                            } else if (val1 > val2) {
                                return 1;
                            }
                            return 0;
                        }
                    });
                    for (int a = 0, size = form.errors.size(); a < size; a++) {
                        TLRPC.SecureValueError secureValueError = form.errors.get(a);
                        String key;
                        String description;
                        String target;

                        String field = null;
                        byte[] file_hash = null;

                        if (secureValueError instanceof TLRPC.TL_secureValueErrorFrontSide) {
                            TLRPC.TL_secureValueErrorFrontSide secureValueErrorFrontSide = (TLRPC.TL_secureValueErrorFrontSide) secureValueError;
                            key = getNameForType(secureValueErrorFrontSide.type);
                            description = secureValueErrorFrontSide.text;
                            file_hash = secureValueErrorFrontSide.file_hash;
                            target = "front";
                        } else if (secureValueError instanceof TLRPC.TL_secureValueErrorReverseSide) {
                            TLRPC.TL_secureValueErrorReverseSide secureValueErrorReverseSide = (TLRPC.TL_secureValueErrorReverseSide) secureValueError;
                            key = getNameForType(secureValueErrorReverseSide.type);
                            description = secureValueErrorReverseSide.text;
                            file_hash = secureValueErrorReverseSide.file_hash;
                            target = "reverse";
                        } else if (secureValueError instanceof TLRPC.TL_secureValueErrorSelfie) {
                            TLRPC.TL_secureValueErrorSelfie secureValueErrorSelfie = (TLRPC.TL_secureValueErrorSelfie) secureValueError;
                            key = getNameForType(secureValueErrorSelfie.type);
                            description = secureValueErrorSelfie.text;
                            file_hash = secureValueErrorSelfie.file_hash;
                            target = "selfie";
                        } else if (secureValueError instanceof TLRPC.TL_secureValueErrorTranslationFile) {
                            TLRPC.TL_secureValueErrorTranslationFile secureValueErrorTranslationFile = (TLRPC.TL_secureValueErrorTranslationFile) secureValueError;
                            key = getNameForType(secureValueErrorTranslationFile.type);
                            description = secureValueErrorTranslationFile.text;
                            file_hash = secureValueErrorTranslationFile.file_hash;
                            target = "translation";
                        } else if (secureValueError instanceof TLRPC.TL_secureValueErrorTranslationFiles) {
                            TLRPC.TL_secureValueErrorTranslationFiles secureValueErrorTranslationFiles = (TLRPC.TL_secureValueErrorTranslationFiles) secureValueError;
                            key = getNameForType(secureValueErrorTranslationFiles.type);
                            description = secureValueErrorTranslationFiles.text;
                            target = "translation";
                        } else if (secureValueError instanceof TLRPC.TL_secureValueErrorFile) {
                            TLRPC.TL_secureValueErrorFile secureValueErrorFile = (TLRPC.TL_secureValueErrorFile) secureValueError;
                            key = getNameForType(secureValueErrorFile.type);
                            description = secureValueErrorFile.text;
                            file_hash = secureValueErrorFile.file_hash;
                            target = "files";
                        } else if (secureValueError instanceof TLRPC.TL_secureValueErrorFiles) {
                            TLRPC.TL_secureValueErrorFiles secureValueErrorFiles = (TLRPC.TL_secureValueErrorFiles) secureValueError;
                            key = getNameForType(secureValueErrorFiles.type);
                            description = secureValueErrorFiles.text;
                            target = "files";
                        } else if (secureValueError instanceof TLRPC.TL_secureValueError) {
                            TLRPC.TL_secureValueError secureValueErrorAll = (TLRPC.TL_secureValueError) secureValueError;
                            key = getNameForType(secureValueErrorAll.type);
                            description = secureValueErrorAll.text;
                            file_hash = secureValueErrorAll.hash;
                            target = "error_all";
                        } else if (secureValueError instanceof TLRPC.TL_secureValueErrorData) {
                            TLRPC.TL_secureValueErrorData secureValueErrorData = (TLRPC.TL_secureValueErrorData) secureValueError;
                            boolean found = false;
                            for (int b = 0; b < form.values.size(); b++) {
                                TLRPC.TL_secureValue value = form.values.get(b);
                                if (value.data != null && Arrays.equals(value.data.data_hash, secureValueErrorData.data_hash)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                continue;
                            }
                            key = getNameForType(secureValueErrorData.type);
                            description = secureValueErrorData.text;
                            field = secureValueErrorData.field;
                            file_hash = secureValueErrorData.data_hash;
                            target = "data";
                        } else {
                            continue;
                        }
                        HashMap<String, String> vals = errorsMap.get(key);
                        if (vals == null) {
                            vals = new HashMap<>();
                            errorsMap.put(key, vals);
                            mainErrorsMap.put(key, description);
                        }
                        String hash;
                        if (file_hash != null) {
                            hash = Base64.encodeToString(file_hash, Base64.NO_WRAP);
                        } else {
                            hash = "";
                        }
                        switch (target) {
                            case "data":
                                if (field != null) {
                                    vals.put(field, description);
                                }
                                break;
                            case "files":
                                if (file_hash != null) {
                                    vals.put("files" + hash, description);
                                } else {
                                    vals.put("files_all", description);
                                }
                                break;
                            case "selfie":
                                vals.put("selfie" + hash, description);
                                break;
                            case "translation":
                                if (file_hash != null) {
                                    vals.put("translation" + hash, description);
                                } else {
                                    vals.put("translation_all", description);
                                }
                                break;
                            case "front":
                                vals.put("front" + hash, description);
                                break;
                            case "reverse":
                                vals.put("reverse" + hash, description);
                                break;
                            case "error_all":
                                vals.put("error_all", description);
                                break;
                        }
                    }
                } catch (Exception ignore) {

                }
            }
        }
    }

    public PassportActivity(int type, TLRPC.TL_account_authorizationForm form, TLRPC.account_Password accountPassword, TLRPC.TL_secureRequiredType secureType, TLRPC.TL_secureValue secureValue, TLRPC.TL_secureRequiredType secureDocumentsType, TLRPC.TL_secureValue secureDocumentsValue, HashMap<String, String> values, HashMap<String, String> documentValues) {
        super();
        currentActivityType = type;
        currentForm = form;
        currentType = secureType;
        if (currentType != null) {
            allowNonLatinName = currentType.native_names;
        }
        currentTypeValue = secureValue;
        currentDocumentsType = secureDocumentsType;
        currentDocumentsTypeValue = secureDocumentsValue;
        currentPassword = accountPassword;
        currentValues = values;
        currentDocumentValues = documentValues;
        if (currentActivityType == TYPE_PHONE) {
            permissionsItems = new ArrayList<>();
        } else if (currentActivityType == TYPE_PHONE_VERIFICATION) {
            views = new SlideView[3];
        }
        if (currentValues == null) {
            currentValues = new HashMap<>();
        }
        if (currentDocumentValues == null) {
            currentDocumentValues = new HashMap<>();
        }
        if (type == TYPE_PASSWORD) {
            if (UserConfig.getInstance(currentAccount).savedPasswordHash != null && UserConfig.getInstance(currentAccount).savedSaltedPassword != null) {
                usingSavedPassword = 1;
                savedPasswordHash = UserConfig.getInstance(currentAccount).savedPasswordHash;
                savedSaltedPassword = UserConfig.getInstance(currentAccount).savedSaltedPassword;
            }
            if (currentPassword == null) {
                loadPasswordInfo();
            } else {
                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                if (usingSavedPassword == 1) {
                    onPasswordDone(true);
                }
            }
            if (!SharedConfig.isPassportConfigLoaded()) {
                TLRPC.TL_help_getPassportConfig req = new TLRPC.TL_help_getPassportConfig();
                req.hash = SharedConfig.passportConfigHash;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response instanceof TLRPC.TL_help_passportConfig) {
                        TLRPC.TL_help_passportConfig res = (TLRPC.TL_help_passportConfig) response;
                        SharedConfig.setPassportConfig(res.countries_langs.data, res.hash);
                    } else {
                        SharedConfig.getCountryLangs();
                    }
                }));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (chatAttachAlert != null) {
            chatAttachAlert.onResume();
        }
        if (currentActivityType == TYPE_PASSWORD && inputFieldContainers != null && inputFieldContainers[FIELD_PASSWORD] != null && inputFieldContainers[FIELD_PASSWORD].getVisibility() == View.VISIBLE) {
            inputFields[FIELD_PASSWORD].requestFocus();
            AndroidUtilities.showKeyboard(inputFields[FIELD_PASSWORD]);
            AndroidUtilities.runOnUIThread(() -> {
                if (inputFieldContainers != null && inputFieldContainers[FIELD_PASSWORD] != null && inputFieldContainers[FIELD_PASSWORD].getVisibility() == View.VISIBLE) {
                    inputFields[FIELD_PASSWORD].requestFocus();
                    AndroidUtilities.showKeyboard(inputFields[FIELD_PASSWORD]);
                }
            }, 200);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (chatAttachAlert != null) {
            chatAttachAlert.onPause();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadFailed);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.twoStepPasswordChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didRemoveTwoStepPassword);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadFailed);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.twoStepPasswordChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didRemoveTwoStepPassword);
        callCallback(false);
        if (chatAttachAlert != null) {
            chatAttachAlert.dismissInternal();
            chatAttachAlert.onDestroy();
        }
        if (currentActivityType == TYPE_PHONE_VERIFICATION) {
            for (int a = 0; a < views.length; a++) {
                if (views[a] != null) {
                    views[a].onDestroyActivity();
                }
            }
            if (progressDialog != null) {
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                progressDialog = null;
            }
        }
    }

    @Override
    public View createView(Context context) {

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {

            private boolean onIdentityDone(Runnable finishRunnable, ErrorRunnable errorRunnable) {
                if (!uploadingDocuments.isEmpty() || checkFieldsForError()) {
                    return false;
                }
                if (allowNonLatinName) {
                    allowNonLatinName = false;
                    boolean error = false;
                    for (int a = 0; a < nonLatinNames.length; a++) {
                        if (nonLatinNames[a]) {
                            inputFields[a].setErrorText(LocaleController.getString("PassportUseLatinOnly", R.string.PassportUseLatinOnly));
                            if (!error) {
                                error = true;
                                String firstName = nonLatinNames[0] ? getTranslitString(inputExtraFields[FIELD_NATIVE_NAME].getText().toString()) : inputFields[FIELD_NAME].getText().toString();
                                String middleName = nonLatinNames[1] ? getTranslitString(inputExtraFields[FIELD_NATIVE_MIDNAME].getText().toString()) : inputFields[FIELD_MIDNAME].getText().toString();
                                String lastName = nonLatinNames[2] ? getTranslitString(inputExtraFields[FIELD_NATIVE_SURNAME].getText().toString()) : inputFields[FIELD_SURNAME].getText().toString();

                                if (!TextUtils.isEmpty(firstName) && !TextUtils.isEmpty(middleName) && !TextUtils.isEmpty(lastName)) {
                                    int num = a;
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setMessage(LocaleController.formatString("PassportNameCheckAlert", R.string.PassportNameCheckAlert, firstName, middleName, lastName));
                                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                    builder.setPositiveButton(LocaleController.getString("Done", R.string.Done), (dialogInterface, i) -> {
                                        inputFields[FIELD_NAME].setText(firstName);
                                        inputFields[FIELD_MIDNAME].setText(middleName);
                                        inputFields[FIELD_SURNAME].setText(lastName);
                                        showEditDoneProgress(true, true);
                                        onIdentityDone(finishRunnable, errorRunnable);
                                    });
                                    builder.setNegativeButton(LocaleController.getString("Edit", R.string.Edit), (dialogInterface, i) -> onFieldError(inputFields[num]));
                                    showDialog(builder.create());
                                } else {
                                    onFieldError(inputFields[a]);
                                }
                            }
                        }
                    }
                    if (error) {
                        return false;
                    }
                }
                if (isHasNotAnyChanges()) {
                    finishFragment();
                    return false;
                }
                JSONObject json = null;
                JSONObject documentsJson = null;
                try {
                    if (!documentOnly) {
                        HashMap<String, String> valuesToSave = new HashMap<>(currentValues);
                        if (currentType.native_names) {
                            if (nativeInfoCell.getVisibility() == View.VISIBLE) {
                                valuesToSave.put("first_name_native", inputExtraFields[FIELD_NATIVE_NAME].getText().toString());
                                valuesToSave.put("middle_name_native", inputExtraFields[FIELD_NATIVE_MIDNAME].getText().toString());
                                valuesToSave.put("last_name_native", inputExtraFields[FIELD_NATIVE_SURNAME].getText().toString());
                            } else {
                                valuesToSave.put("first_name_native", inputFields[FIELD_NATIVE_NAME].getText().toString());
                                valuesToSave.put("middle_name_native", inputFields[FIELD_NATIVE_MIDNAME].getText().toString());
                                valuesToSave.put("last_name_native", inputFields[FIELD_NATIVE_SURNAME].getText().toString());
                            }
                        }
                        valuesToSave.put("first_name", inputFields[FIELD_NAME].getText().toString());
                        valuesToSave.put("middle_name", inputFields[FIELD_MIDNAME].getText().toString());
                        valuesToSave.put("last_name", inputFields[FIELD_SURNAME].getText().toString());
                        valuesToSave.put("birth_date", inputFields[FIELD_BIRTHDAY].getText().toString());
                        valuesToSave.put("gender", currentGender);
                        valuesToSave.put("country_code", currentCitizeship);
                        valuesToSave.put("residence_country_code", currentResidence);

                        json = new JSONObject();
                        ArrayList<String> keys = new ArrayList<>(valuesToSave.keySet());
                        Collections.sort(keys, (key1, key2) -> {
                            int val1 = getFieldCost(key1);
                            int val2 = getFieldCost(key2);
                            if (val1 < val2) {
                                return -1;
                            } else if (val1 > val2) {
                                return 1;
                            }
                            return 0;
                        });
                        for (int a = 0, size = keys.size(); a < size; a++) {
                            String key = keys.get(a);
                            json.put(key, valuesToSave.get(key));
                        }
                    }

                    if (currentDocumentsType != null) {
                        HashMap<String, String> valuesToSave = new HashMap<>(currentDocumentValues);
                        valuesToSave.put("document_no", inputFields[FIELD_CARDNUMBER].getText().toString());
                        if (currentExpireDate[0] != 0) {
                            valuesToSave.put("expiry_date", String.format(Locale.US, "%02d.%02d.%d", currentExpireDate[2], currentExpireDate[1], currentExpireDate[0]));
                        } else {
                            valuesToSave.put("expiry_date", "");
                        }

                        documentsJson = new JSONObject();
                        ArrayList<String> keys = new ArrayList<>(valuesToSave.keySet());
                        Collections.sort(keys, (key1, key2) -> {
                            int val1 = getFieldCost(key1);
                            int val2 = getFieldCost(key2);
                            if (val1 < val2) {
                                return -1;
                            } else if (val1 > val2) {
                                return 1;
                            }
                            return 0;
                        });
                        for (int a = 0, size = keys.size(); a < size; a++) {
                            String key = keys.get(a);
                            documentsJson.put(key, valuesToSave.get(key));
                        }
                    }
                } catch (Exception ignore) {

                }
                if (fieldsErrors != null) {
                    fieldsErrors.clear();
                }
                if (documentsErrors != null) {
                    documentsErrors.clear();
                }
                delegate.saveValue(currentType, null, json != null ? json.toString() : null, currentDocumentsType, documentsJson != null ? documentsJson.toString() : null, null, selfieDocument, translationDocuments, frontDocument, reverseLayout != null && reverseLayout.getVisibility() == View.VISIBLE ? reverseDocument : null, finishRunnable, errorRunnable);
                return true;
            }

            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard()) {
                        return;
                    }
                    if (currentActivityType == TYPE_REQUEST || currentActivityType == TYPE_PASSWORD) {
                        callCallback(false);
                    }
                    finishFragment();
                } else if (id == info_item) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    final LinkSpanDrawable.LinksTextView message = new LinkSpanDrawable.LinksTextView(getParentActivity());
                    String str2 = LocaleController.getString("PassportInfo2", R.string.PassportInfo2);
                    SpannableStringBuilder spanned = new SpannableStringBuilder(str2);
                    int index1 = str2.indexOf('*');
                    int index2 = str2.lastIndexOf('*');
                    if (index1 != -1 && index2 != -1) {
                        spanned.replace(index2, index2 + 1, "");
                        spanned.replace(index1, index1 + 1, "");
                        spanned.setSpan(new URLSpanNoUnderline(LocaleController.getString("PassportInfoUrl", R.string.PassportInfoUrl)) {
                            @Override
                            public void onClick(View widget) {
                                dismissCurrentDialog();
                                super.onClick(widget);
                            }
                        }, index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    message.setText(spanned);
                    message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    message.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink));
                    message.setHighlightColor(Theme.getColor(Theme.key_dialogLinkSelection));
                    message.setPadding(AndroidUtilities.dp(23), 0, AndroidUtilities.dp(23), 0);
                    message.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
                    message.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setView(message);
                    builder.setTitle(LocaleController.getString("PassportInfoTitle", R.string.PassportInfoTitle));
                    builder.setNegativeButton(LocaleController.getString("Close", R.string.Close), null);
                    showDialog(builder.create());
                } else if (id == done_button) {
                    if (currentActivityType == TYPE_PASSWORD) {
                        onPasswordDone(false);
                        return;
                    }
                    if (currentActivityType == TYPE_PHONE_VERIFICATION) {
                        views[currentViewNum].onNextPressed(null);
                    } else {
                        final Runnable finishRunnable = () -> finishFragment();
                        final ErrorRunnable errorRunnable = new ErrorRunnable() {
                            @Override
                            public void onError(String error, String text) {
                                if ("PHONE_VERIFICATION_NEEDED".equals(error)) {
                                    startPhoneVerification(true, text, finishRunnable, this, delegate);
                                } else {
                                    showEditDoneProgress(true, false);
                                }
                            }
                        };

                        if (currentActivityType == TYPE_EMAIL) {
                            String value;
                            if (useCurrentValue) {
                                value = currentEmail;
                            } else {
                                if (checkFieldsForError()) {
                                    return;
                                }
                                value = inputFields[FIELD_EMAIL].getText().toString();
                            }
                            delegate.saveValue(currentType, value, null, null, null, null, null, null, null, null, finishRunnable, errorRunnable);
                        } else if (currentActivityType == TYPE_PHONE) {
                            String value;
                            if (useCurrentValue) {
                                value = UserConfig.getInstance(currentAccount).getCurrentUser().phone;
                            } else {
                                if (checkFieldsForError()) {
                                    return;
                                }
                                value = inputFields[FIELD_PHONECODE].getText().toString() + inputFields[FIELD_PHONE].getText().toString();
                            }
                            delegate.saveValue(currentType, value, null, null, null, null, null, null, null, null, finishRunnable, errorRunnable);
                        } else if (currentActivityType == TYPE_ADDRESS) {
                            if (!uploadingDocuments.isEmpty() || checkFieldsForError()) {
                                return;
                            }
                            if (isHasNotAnyChanges()) {
                                finishFragment();
                                return;
                            }
                            JSONObject json = null;
                            try {
                                if (!documentOnly) {
                                    json = new JSONObject();
                                    json.put("street_line1", inputFields[FIELD_STREET1].getText().toString());
                                    json.put("street_line2", inputFields[FIELD_STREET2].getText().toString());
                                    json.put("post_code", inputFields[FIELD_POSTCODE].getText().toString());
                                    json.put("city", inputFields[FIELD_CITY].getText().toString());
                                    json.put("state", inputFields[FIELD_STATE].getText().toString());
                                    json.put("country_code", currentCitizeship);
                                }
                            } catch (Exception ignore) {

                            }
                            if (fieldsErrors != null) {
                                fieldsErrors.clear();
                            }
                            if (documentsErrors != null) {
                                documentsErrors.clear();
                            }
                            delegate.saveValue(currentType, null, json != null ? json.toString() : null, currentDocumentsType, null, documents, selfieDocument, translationDocuments, null, null, finishRunnable, errorRunnable);
                        } else if (currentActivityType == TYPE_IDENTITY) {
                            if (!onIdentityDone(finishRunnable, errorRunnable)) {
                                return;
                            }
                        } else if (currentActivityType == TYPE_EMAIL_VERIFICATION) {
                            TLRPC.TL_account_verifyEmail req = new TLRPC.TL_account_verifyEmail();
                            req.purpose = new TLRPC.TL_emailVerifyPurposePassport();
                            TLRPC.TL_emailVerificationCode code = new TLRPC.TL_emailVerificationCode();
                            code.code = inputFields[FIELD_EMAIL].getText().toString();
                            req.verification = code;
                            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                                if (error == null) {
                                    delegate.saveValue(currentType, currentValues.get("email"), null, null, null, null, null, null, null, null, finishRunnable, errorRunnable);
                                } else {
                                    AlertsCreator.processError(currentAccount, error, PassportActivity.this, req);
                                    errorRunnable.onError(null, null);
                                }
                            }));
                            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
                        }
                        showEditDoneProgress(true, true);
                    }
                }
            }
        });

        if (currentActivityType == TYPE_PHONE_VERIFICATION) {
            fragmentView = scrollView = new ScrollView(context) {
                @Override
                protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
                    return false;
                }

                @Override
                public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                    if (currentViewNum == 1 || currentViewNum == 2 || currentViewNum == 4) {
                        rectangle.bottom += AndroidUtilities.dp(40);
                    }
                    return super.requestChildRectangleOnScreen(child, rectangle, immediate);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    scrollHeight = MeasureSpec.getSize(heightMeasureSpec) - AndroidUtilities.dp(30);
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            };
            scrollView.setFillViewport(true);
            AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, Theme.getColor(Theme.key_actionBarDefault));
        } else {
            fragmentView = new FrameLayout(context);
            FrameLayout frameLayout = (FrameLayout) fragmentView;
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

            scrollView = new ScrollView(context) {
                @Override
                protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
                    return false;
                }

                @Override
                public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                    rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
                    rectangle.top += AndroidUtilities.dp(20);
                    rectangle.bottom += AndroidUtilities.dp(50);
                    return super.requestChildRectangleOnScreen(child, rectangle, immediate);
                }
            };
            scrollView.setFillViewport(true);
            AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, Theme.getColor(Theme.key_actionBarDefault));
            frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, currentActivityType == TYPE_REQUEST ? 48 : 0));

            linearLayout2 = new LinearLayout(context);
            linearLayout2.setOrientation(LinearLayout.VERTICAL);
            scrollView.addView(linearLayout2, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (currentActivityType != TYPE_REQUEST && currentActivityType != TYPE_MANAGE) {
            ActionBarMenu menu = actionBar.createMenu();
            doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_ab_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));
            progressView = new ContextProgressView(context, 1);
            progressView.setAlpha(0.0f);
            progressView.setScaleX(0.1f);
            progressView.setScaleY(0.1f);
            progressView.setVisibility(View.INVISIBLE);
            doneItem.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            if (currentActivityType == TYPE_IDENTITY || currentActivityType == TYPE_ADDRESS) {
                if (chatAttachAlert != null) {
                    try {
                        if (chatAttachAlert.isShowing()) {
                            chatAttachAlert.dismiss();
                        }
                    } catch (Exception ignore) {

                    }
                    chatAttachAlert.onDestroy();
                    chatAttachAlert = null;
                }
            }
        }

        if (currentActivityType == TYPE_PASSWORD) {
            createPasswordInterface(context);
        } else if (currentActivityType == TYPE_REQUEST) {
            createRequestInterface(context);
        } else if (currentActivityType == TYPE_IDENTITY) {
            createIdentityInterface(context);
            fillInitialValues();
        } else if (currentActivityType == TYPE_ADDRESS) {
            createAddressInterface(context);
            fillInitialValues();
        } else if (currentActivityType == TYPE_PHONE) {
            createPhoneInterface(context);
        } else if (currentActivityType == TYPE_EMAIL) {
            createEmailInterface(context);
        } else if (currentActivityType == TYPE_EMAIL_VERIFICATION) {
            createEmailVerificationInterface(context);
        } else if (currentActivityType == TYPE_PHONE_VERIFICATION) {
            createPhoneVerificationInterface(context);
        } else if (currentActivityType == TYPE_MANAGE) {
            createManageInterface(context);
        }
        return fragmentView;
    }

    @Override
    public boolean dismissDialogOnPause(Dialog dialog) {
        return dialog != chatAttachAlert && super.dismissDialogOnPause(dialog);
    }

    @Override
    public void dismissCurrentDialog() {
        if (chatAttachAlert != null && visibleDialog == chatAttachAlert) {
            chatAttachAlert.getPhotoLayout().closeCamera(false);
            chatAttachAlert.dismissInternal();
            chatAttachAlert.getPhotoLayout().hideCamera(true);
            return;
        }
        super.dismissCurrentDialog();
    }

    private String getTranslitString(String value) {
        return LocaleController.getInstance().getTranslitString(value, true);
    }

    private int getFieldCost(String key) {
        switch (key) {
            case "first_name":
            case "first_name_native":
                return 20;
            case "middle_name":
            case "middle_name_native":
                return 21;
            case "last_name":
            case "last_name_native":
                return 22;
            case "birth_date":
                return 23;
            case "gender":
                return 24;
            case "country_code":
                return 25;
            case "residence_country_code":
                return 26;
            case "document_no":
                return 27;
            case "expiry_date":
                return 28;
            case "street_line1":
                return 29;
            case "street_line2":
                return 30;
            case "post_code":
                return 31;
            case "city":
                return 32;
            case "state":
                return 33;
        }
        return 100;
    }

    private void createPhoneVerificationInterface(Context context) {
        actionBar.setTitle(LocaleController.getString("PassportPhone", R.string.PassportPhone));

        FrameLayout frameLayout = new FrameLayout(context);
        scrollView.addView(frameLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        for (int a = 0; a < 3; a++) {
            views[a] = new PhoneConfirmationView(context, a + 2);
            views[a].setVisibility(View.GONE);
            frameLayout.addView(views[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 26 : 18, 30, AndroidUtilities.isTablet() ? 26 : 18, 0));
        }
        final Bundle params = new Bundle();
        params.putString("phone", currentValues.get("phone"));
        fillNextCodeParams(params, currentPhoneVerification, false);
    }

    private void loadPasswordInfo() {
        TLRPC.TL_account_getPassword req = new TLRPC.TL_account_getPassword();
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                currentPassword = (TLRPC.account_Password) response;
                if (!TwoStepVerificationActivity.canHandleCurrentPassword(currentPassword, false)) {
                    AlertsCreator.showUpdateAppAlert(getParentActivity(), LocaleController.getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                    return;
                }
                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                updatePasswordInterface();

                if (inputFieldContainers[FIELD_PASSWORD].getVisibility() == View.VISIBLE) {
                    inputFields[FIELD_PASSWORD].requestFocus();
                    AndroidUtilities.showKeyboard(inputFields[FIELD_PASSWORD]);
                }
                if (usingSavedPassword == 1) {
                    onPasswordDone(true);
                }
            }
        }));
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
    }

    private void createEmailVerificationInterface(Context context) {
        actionBar.setTitle(LocaleController.getString("PassportEmail", R.string.PassportEmail));

        inputFields = new EditTextBoldCursor[1];
        for (int a = 0; a < 1; a++) {
            ViewGroup container = new FrameLayout(context);
            linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
            container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            inputFields[a] = new EditTextBoldCursor(context);
            inputFields[a].setTag(a);
            inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            inputFields[a].setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            inputFields[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setBackgroundDrawable(null);
            inputFields[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setCursorSize(AndroidUtilities.dp(20));
            inputFields[a].setCursorWidth(1.5f);
            inputFields[a].setInputType(InputType.TYPE_CLASS_PHONE);

            inputFields[a].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            inputFields[a].setHint(LocaleController.getString("PassportEmailCode", R.string.PassportEmailCode));
            inputFields[a].setSelection(inputFields[a].length());
            inputFields[a].setPadding(0, 0, 0, AndroidUtilities.dp(6));
            inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 12, 21, 6));

            inputFields[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_DONE || i == EditorInfo.IME_ACTION_NEXT) {
                    doneItem.callOnClick();
                    return true;
                }
                return false;
            });

            inputFields[a].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (ignoreOnTextChange) {
                        return;
                    }
                    if (emailCodeLength != 0 && inputFields[FIELD_EMAIL].length() == emailCodeLength) {
                        doneItem.callOnClick();
                    }
                }
            });
        }

        bottomCell = new TextInfoPrivacyCell(context);
        bottomCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        bottomCell.setText(LocaleController.formatString("PassportEmailVerifyInfo", R.string.PassportEmailVerifyInfo, currentValues.get("email")));
        linearLayout2.addView(bottomCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void createPasswordInterface(Context context) {
        TLRPC.User botUser = null;
        if (currentForm != null) {
            for (int a = 0; a < currentForm.users.size(); a++) {
                TLRPC.User user = currentForm.users.get(a);
                if (user.id == currentBotId) {
                    botUser = user;
                    break;
                }
            }
        } else {
            botUser = UserConfig.getInstance(currentAccount).getCurrentUser();
        }

        FrameLayout frameLayout = (FrameLayout) fragmentView;

        actionBar.setTitle(LocaleController.getString("TelegramPassport", R.string.TelegramPassport));

        emptyView = new EmptyTextProgressView(context);
        emptyView.showProgress();
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        passwordAvatarContainer = new FrameLayout(context);
        linearLayout2.addView(passwordAvatarContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 100));

        BackupImageView avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(32));
        passwordAvatarContainer.addView(avatarImageView, LayoutHelper.createFrame(64, 64, Gravity.CENTER, 0, 8, 0, 0));

        AvatarDrawable avatarDrawable = new AvatarDrawable(botUser);
        avatarImageView.setForUserOrChat(botUser, avatarDrawable);

        passwordRequestTextView = new TextInfoPrivacyCell(context);
        passwordRequestTextView.getTextView().setGravity(Gravity.CENTER_HORIZONTAL);
        if (currentBotId == 0) {
            passwordRequestTextView.setText(LocaleController.getString("PassportSelfRequest", R.string.PassportSelfRequest));
        } else {
            passwordRequestTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("PassportRequest", R.string.PassportRequest, UserObject.getFirstName(botUser))));
        }
        ((FrameLayout.LayoutParams) passwordRequestTextView.getTextView().getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        linearLayout2.addView(passwordRequestTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 0, 21, 0));

        noPasswordImageView = new ImageView(context);
        noPasswordImageView.setImageResource(R.drawable.no_password);
        noPasswordImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        linearLayout2.addView(noPasswordImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 13, 0, 0));

        noPasswordTextView = new TextView(context);
        noPasswordTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        noPasswordTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        noPasswordTextView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(17));
        noPasswordTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        noPasswordTextView.setText(LocaleController.getString("TelegramPassportCreatePasswordInfo", R.string.TelegramPassportCreatePasswordInfo));
        linearLayout2.addView(noPasswordTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 10, 21, 0));

        noPasswordSetTextView = new TextView(context);
        noPasswordSetTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText5));
        noPasswordSetTextView.setGravity(Gravity.CENTER);
        noPasswordSetTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        noPasswordSetTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        noPasswordSetTextView.setText(LocaleController.getString("TelegramPassportCreatePassword", R.string.TelegramPassportCreatePassword));
        linearLayout2.addView(noPasswordSetTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 9, 21, 0));
        noPasswordSetTextView.setOnClickListener(v -> {
            TwoStepVerificationSetupActivity activity = new TwoStepVerificationSetupActivity(currentAccount, TwoStepVerificationSetupActivity.TYPE_CREATE_PASSWORD_STEP_1, currentPassword);
            activity.setCloseAfterSet(true);
            presentFragment(activity);
        });

        inputFields = new EditTextBoldCursor[1];
        inputFieldContainers = new ViewGroup[1];
        for (int a = 0; a < 1; a++) {
            inputFieldContainers[a] = new FrameLayout(context);
            linearLayout2.addView(inputFieldContainers[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
            inputFieldContainers[a].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            inputFields[a] = new EditTextBoldCursor(context);
            inputFields[a].setTag(a);
            inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            inputFields[a].setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            inputFields[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setBackgroundDrawable(null);
            inputFields[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setCursorSize(AndroidUtilities.dp(20));
            inputFields[a].setCursorWidth(1.5f);
            inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            inputFields[a].setMaxLines(1);
            inputFields[a].setLines(1);
            inputFields[a].setSingleLine(true);
            inputFields[a].setTransformationMethod(PasswordTransformationMethod.getInstance());
            inputFields[a].setTypeface(Typeface.DEFAULT);
            inputFields[a].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            inputFields[a].setPadding(0, 0, 0, AndroidUtilities.dp(6));
            inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            inputFieldContainers[a].addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 12, 21, 6));

            inputFields[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT || i == EditorInfo.IME_ACTION_DONE) {
                    doneItem.callOnClick();
                    return true;
                }
                return false;
            });
            inputFields[a].setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                public void onDestroyActionMode(ActionMode mode) {
                }

                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return false;
                }
            });
        }

        passwordInfoRequestTextView = new TextInfoPrivacyCell(context);
        passwordInfoRequestTextView.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        passwordInfoRequestTextView.setText(LocaleController.formatString("PassportRequestPasswordInfo", R.string.PassportRequestPasswordInfo));
        linearLayout2.addView(passwordInfoRequestTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        passwordForgotButton = new TextView(context);
        passwordForgotButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        passwordForgotButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        passwordForgotButton.setText(LocaleController.getString("ForgotPassword", R.string.ForgotPassword));
        passwordForgotButton.setPadding(0, 0, 0, 0);
        linearLayout2.addView(passwordForgotButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 30, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 0, 21, 0));
        passwordForgotButton.setOnClickListener(v -> {
            if (currentPassword.has_recovery) {
                needShowProgress();
                TLRPC.TL_auth_requestPasswordRecovery req = new TLRPC.TL_auth_requestPasswordRecovery();
                int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    needHideProgress();
                    if (error == null) {
                        final TLRPC.TL_auth_passwordRecovery res = (TLRPC.TL_auth_passwordRecovery) response;
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.formatString("RestoreEmailSent", R.string.RestoreEmailSent, res.email_pattern));
                        builder.setTitle(LocaleController.getString("RestoreEmailSentTitle", R.string.RestoreEmailSentTitle));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                            currentPassword.email_unconfirmed_pattern = res.email_pattern;
                            TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(currentAccount, TwoStepVerificationSetupActivity.TYPE_EMAIL_RECOVERY, currentPassword);
                            presentFragment(fragment);
                        });
                        Dialog dialog = showDialog(builder.create());
                        if (dialog != null) {
                            dialog.setCanceledOnTouchOutside(false);
                            dialog.setCancelable(false);
                        }
                    } else {
                        if (error.text.startsWith("FLOOD_WAIT")) {
                            int time = Utilities.parseInt(error.text);
                            String timeString;
                            if (time < 60) {
                                timeString = LocaleController.formatPluralString("Seconds", time);
                            } else {
                                timeString = LocaleController.formatPluralString("Minutes", time / 60);
                            }
                            showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                        } else {
                            showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error.text);
                        }
                    }
                }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
            } else {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                builder.setNegativeButton(LocaleController.getString("RestorePasswordResetAccount", R.string.RestorePasswordResetAccount), (dialog, which) -> Browser.openUrl(getParentActivity(), "https://telegram.org/deactivate?phone=" + UserConfig.getInstance(currentAccount).getClientPhone()));
                builder.setTitle(LocaleController.getString("RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle));
                builder.setMessage(LocaleController.getString("RestorePasswordNoEmailText", R.string.RestorePasswordNoEmailText));
                showDialog(builder.create());
            }
        });

        updatePasswordInterface();
    }

    private void onPasswordDone(final boolean saved) {
        final String textPassword;
        if (saved) {
            textPassword = null;
        } else {
            textPassword = inputFields[FIELD_PASSWORD].getText().toString();
            if (TextUtils.isEmpty(textPassword)) {
                onPasscodeError(false);
                return;
            }
            showEditDoneProgress(true, true);
        }

        Utilities.globalQueue.postRunnable(() -> {
            TLRPC.TL_account_getPasswordSettings req = new TLRPC.TL_account_getPasswordSettings();

            final byte[] x_bytes;
            if (saved) {
                x_bytes = savedPasswordHash;
            } else if (currentPassword.current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                byte[] passwordBytes = AndroidUtilities.getStringBytes(textPassword);
                TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.current_algo;
                x_bytes = SRPHelper.getX(passwordBytes, algo);
            } else {
                x_bytes = null;
            }

            RequestDelegate requestDelegate = new RequestDelegate() {

                private void openRequestInterface() {
                    if (inputFields == null) {
                        return;
                    }
                    if (!saved) {
                        UserConfig.getInstance(currentAccount).savePassword(x_bytes, saltedPassword);
                    }

                    AndroidUtilities.hideKeyboard(inputFields[FIELD_PASSWORD]);
                    ignoreOnFailure = true;
                    int type;
                    if (currentBotId == 0) {
                        type = TYPE_MANAGE;
                    } else {
                        type = TYPE_REQUEST;
                    }
                    PassportActivity activity = new PassportActivity(type, currentBotId, currentScope, currentPublicKey, currentPayload, currentNonce, currentCallbackUrl, currentForm, currentPassword);
                    activity.currentEmail = currentEmail;
                    activity.currentAccount = currentAccount;
                    activity.saltedPassword = saltedPassword;
                    activity.secureSecret = secureSecret;
                    activity.secureSecretId = secureSecretId;
                    activity.needActivityResult = needActivityResult;
                    if (parentLayout == null || !parentLayout.checkTransitionAnimation()) {
                        presentFragment(activity, true);
                    } else {
                        presentAfterAnimation = activity;
                    }
                }

                private void resetSecret() {
                    TLRPC.TL_account_updatePasswordSettings req2 = new TLRPC.TL_account_updatePasswordSettings();
                    if (currentPassword.current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                        TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.current_algo;
                        req2.password = SRPHelper.startCheck(x_bytes, currentPassword.srp_id, currentPassword.srp_B, algo);
                    }
                    req2.new_settings = new TLRPC.TL_account_passwordInputSettings();
                    req2.new_settings.new_secure_settings = new TLRPC.TL_secureSecretSettings();
                    req2.new_settings.new_secure_settings.secure_secret = new byte[0];
                    req2.new_settings.new_secure_settings.secure_algo = new TLRPC.TL_securePasswordKdfAlgoUnknown();
                    req2.new_settings.new_secure_settings.secure_secret_id = 0;
                    req2.new_settings.flags |= 4;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error != null && "SRP_ID_INVALID".equals(error.text)) {
                            TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                            ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                                if (error2 == null) {
                                    currentPassword = (TLRPC.account_Password) response2;
                                    TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                                    resetSecret();
                                }
                            }), ConnectionsManager.RequestFlagWithoutLogin);
                            return;
                        }
                        generateNewSecret();
                    }));
                }

                private void generateNewSecret() {
                    Utilities.globalQueue.postRunnable(() -> {
                        Utilities.random.setSeed(currentPassword.secure_random);

                        TLRPC.TL_account_updatePasswordSettings req1 = new TLRPC.TL_account_updatePasswordSettings();
                        if (currentPassword.current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                            TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.current_algo;
                            req1.password = SRPHelper.startCheck(x_bytes, currentPassword.srp_id, currentPassword.srp_B, algo);
                        }
                        req1.new_settings = new TLRPC.TL_account_passwordInputSettings();

                        secureSecret = getRandomSecret();
                        secureSecretId = Utilities.bytesToLong(Utilities.computeSHA256(secureSecret));
                        if (currentPassword.new_secure_algo instanceof TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000) {
                            TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000 newAlgo = (TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000) currentPassword.new_secure_algo;

                            saltedPassword = Utilities.computePBKDF2(AndroidUtilities.getStringBytes(textPassword), newAlgo.salt);
                            byte[] key = new byte[32];
                            System.arraycopy(saltedPassword, 0, key, 0, 32);
                            byte[] iv = new byte[16];
                            System.arraycopy(saltedPassword, 32, iv, 0, 16);

                            Utilities.aesCbcEncryptionByteArraySafe(secureSecret, key, iv, 0, secureSecret.length, 0, 1);

                            req1.new_settings.new_secure_settings = new TLRPC.TL_secureSecretSettings();
                            req1.new_settings.new_secure_settings.secure_algo = newAlgo;
                            req1.new_settings.new_secure_settings.secure_secret = secureSecret;
                            req1.new_settings.new_secure_settings.secure_secret_id = secureSecretId;
                            req1.new_settings.flags |= 4;
                        }
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req1, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (error != null && "SRP_ID_INVALID".equals(error.text)) {
                                TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                                ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                                    if (error2 == null) {
                                        currentPassword = (TLRPC.account_Password) response2;
                                        TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                                        generateNewSecret();
                                    }
                                }), ConnectionsManager.RequestFlagWithoutLogin);
                                return;
                            }
                            if (currentForm == null) {
                                currentForm = new TLRPC.TL_account_authorizationForm();
                            }
                            openRequestInterface();
                        }));
                    });
                }

                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    if (error != null && "SRP_ID_INVALID".equals(error.text)) {
                        TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                            if (error2 == null) {
                                currentPassword = (TLRPC.account_Password) response2;
                                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                                onPasswordDone(saved);
                            }
                        }), ConnectionsManager.RequestFlagWithoutLogin);
                        return;
                    }
                    if (error == null) {
                        Utilities.globalQueue.postRunnable(() -> {
                            TLRPC.TL_account_passwordSettings settings = (TLRPC.TL_account_passwordSettings) response;
                            byte[] secure_salt;
                            if (settings.secure_settings != null) {
                                secureSecret = settings.secure_settings.secure_secret;
                                secureSecretId = settings.secure_settings.secure_secret_id;
                                if (settings.secure_settings.secure_algo instanceof TLRPC.TL_securePasswordKdfAlgoSHA512) {
                                    TLRPC.TL_securePasswordKdfAlgoSHA512 algo = (TLRPC.TL_securePasswordKdfAlgoSHA512) settings.secure_settings.secure_algo;
                                    secure_salt = algo.salt;
                                    saltedPassword = Utilities.computeSHA512(secure_salt, AndroidUtilities.getStringBytes(textPassword), secure_salt);
                                } else if (settings.secure_settings.secure_algo instanceof TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000) {
                                    TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000 algo = (TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000) settings.secure_settings.secure_algo;
                                    secure_salt = algo.salt;
                                    saltedPassword = Utilities.computePBKDF2(AndroidUtilities.getStringBytes(textPassword), algo.salt);
                                } else if (settings.secure_settings.secure_algo instanceof TLRPC.TL_securePasswordKdfAlgoUnknown) {
                                    AndroidUtilities.runOnUIThread(() -> AlertsCreator.showUpdateAppAlert(getParentActivity(), LocaleController.getString("UpdateAppAlert", R.string.UpdateAppAlert), true));
                                    return;
                                } else {
                                    secure_salt = new byte[0];
                                }
                            } else {
                                if (currentPassword.new_secure_algo instanceof TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000) {
                                    TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000 algo = (TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000) currentPassword.new_secure_algo;
                                    secure_salt = algo.salt;
                                    saltedPassword = Utilities.computePBKDF2(AndroidUtilities.getStringBytes(textPassword), algo.salt);
                                } else {
                                    secure_salt = new byte[0];
                                }
                                secureSecret = null;
                                secureSecretId = 0;
                            }
                            AndroidUtilities.runOnUIThread(() -> {
                                currentEmail = settings.email;
                                if (saved) {
                                    saltedPassword = savedSaltedPassword;
                                }

                                if (!checkSecret(decryptSecret(secureSecret, saltedPassword), secureSecretId) || secure_salt.length == 0 || secureSecretId == 0) {
                                    if (saved) {
                                        UserConfig.getInstance(currentAccount).resetSavedPassword();
                                        usingSavedPassword = 0;
                                        updatePasswordInterface();
                                    } else {
                                        if (currentForm != null) {
                                            currentForm.values.clear();
                                            currentForm.errors.clear();
                                        }
                                        if (secureSecret == null || secureSecret.length == 0) {
                                            generateNewSecret();
                                        } else {
                                            resetSecret();
                                        }
                                    }
                                } else if (currentBotId == 0) {
                                    TLRPC.TL_account_getAllSecureValues req12 = new TLRPC.TL_account_getAllSecureValues();
                                    ConnectionsManager.getInstance(currentAccount).sendRequest(req12, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                        if (response1 != null) {
                                            currentForm = new TLRPC.TL_account_authorizationForm();
                                            TLRPC.Vector vector = (TLRPC.Vector) response1;
                                            for (int a = 0, size = vector.objects.size(); a < size; a++) {
                                                currentForm.values.add((TLRPC.TL_secureValue) vector.objects.get(a));
                                            }
                                            openRequestInterface();
                                        } else {
                                            if ("APP_VERSION_OUTDATED".equals(error1.text)) {
                                                AlertsCreator.showUpdateAppAlert(getParentActivity(), LocaleController.getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                                            } else {
                                                showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error1.text);
                                            }
                                            showEditDoneProgress(true, false);
                                        }
                                    }));
                                } else {
                                    openRequestInterface();
                                }
                            });
                        });
                    } else {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (saved) {
                                UserConfig.getInstance(currentAccount).resetSavedPassword();
                                usingSavedPassword = 0;
                                updatePasswordInterface();
                                if (inputFieldContainers != null && inputFieldContainers[FIELD_PASSWORD].getVisibility() == View.VISIBLE) {
                                    inputFields[FIELD_PASSWORD].requestFocus();
                                    AndroidUtilities.showKeyboard(inputFields[FIELD_PASSWORD]);
                                }
                            } else {
                                showEditDoneProgress(true, false);
                                if (error.text.equals("PASSWORD_HASH_INVALID")) {
                                    onPasscodeError(true);
                                } else if (error.text.startsWith("FLOOD_WAIT")) {
                                    int time = Utilities.parseInt(error.text);
                                    String timeString;
                                    if (time < 60) {
                                        timeString = LocaleController.formatPluralString("Seconds", time);
                                    } else {
                                        timeString = LocaleController.formatPluralString("Minutes", time / 60);
                                    }
                                    showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                } else {
                                    showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error.text);
                                }
                            }
                        });
                    }
                }
            };

            if (currentPassword.current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.current_algo;
                req.password = SRPHelper.startCheck(x_bytes, currentPassword.srp_id, currentPassword.srp_B, algo);
                if (req.password == null) {
                    TLRPC.TL_error error = new TLRPC.TL_error();
                    error.text = "ALGO_INVALID";
                    requestDelegate.run(null, error);
                    return;
                }
                int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
            } else {
                TLRPC.TL_error error = new TLRPC.TL_error();
                error.text = "PASSWORD_HASH_INVALID";
                requestDelegate.run(null, error);
            }
        });
    }

    private boolean isPersonalDocument(TLRPC.SecureValueType type) {
        return type instanceof TLRPC.TL_secureValueTypeDriverLicense ||
                type instanceof TLRPC.TL_secureValueTypePassport ||
                type instanceof TLRPC.TL_secureValueTypeInternalPassport ||
                type instanceof TLRPC.TL_secureValueTypeIdentityCard;
    }

    private boolean isAddressDocument(TLRPC.SecureValueType type) {
        return type instanceof TLRPC.TL_secureValueTypeUtilityBill ||
                type instanceof TLRPC.TL_secureValueTypeBankStatement ||
                type instanceof TLRPC.TL_secureValueTypePassportRegistration ||
                type instanceof TLRPC.TL_secureValueTypeTemporaryRegistration ||
                type instanceof TLRPC.TL_secureValueTypeRentalAgreement;
    }

    private void createRequestInterface(Context context) {
        TLRPC.User botUser = null;
        if (currentForm != null) {
            for (int a = 0; a < currentForm.users.size(); a++) {
                TLRPC.User user = currentForm.users.get(a);
                if (user.id == currentBotId) {
                    botUser = user;
                    break;
                }
            }
        }

        FrameLayout frameLayout = (FrameLayout) fragmentView;

        actionBar.setTitle(LocaleController.getString("TelegramPassport", R.string.TelegramPassport));

        actionBar.createMenu().addItem(info_item, R.drawable.msg_info);

        if (botUser != null) {
            FrameLayout avatarContainer = new FrameLayout(context);
            linearLayout2.addView(avatarContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 100));

            BackupImageView avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(32));
            avatarContainer.addView(avatarImageView, LayoutHelper.createFrame(64, 64, Gravity.CENTER, 0, 8, 0, 0));

            AvatarDrawable avatarDrawable = new AvatarDrawable(botUser);
            avatarImageView.setForUserOrChat(botUser, avatarDrawable);

            bottomCell = new TextInfoPrivacyCell(context);
            bottomCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
            bottomCell.setText(AndroidUtilities.replaceTags(LocaleController.formatString("PassportRequest", R.string.PassportRequest, UserObject.getFirstName(botUser))));
            bottomCell.getTextView().setGravity(Gravity.CENTER_HORIZONTAL);
            ((FrameLayout.LayoutParams) bottomCell.getTextView().getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
            linearLayout2.addView(bottomCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        headerCell = new HeaderCell(context);
        headerCell.setText(LocaleController.getString("PassportRequestedInformation", R.string.PassportRequestedInformation));
        headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout2.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (currentForm != null) {
            int size = currentForm.required_types.size();
            ArrayList<TLRPC.TL_secureRequiredType> personalDocuments = new ArrayList<>();
            ArrayList<TLRPC.TL_secureRequiredType> addressDocuments = new ArrayList<>();
            int personalCount = 0;
            int addressCount = 0;
            boolean hasPersonalInfo = false;
            boolean hasAddressInfo = false;
            for (int a = 0; a < size; a++) {
                TLRPC.SecureRequiredType secureRequiredType = currentForm.required_types.get(a);
                if (secureRequiredType instanceof TLRPC.TL_secureRequiredType) {
                    TLRPC.TL_secureRequiredType requiredType = (TLRPC.TL_secureRequiredType) secureRequiredType;
                    if (isPersonalDocument(requiredType.type)) {
                        personalDocuments.add(requiredType);
                        personalCount++;
                    } else if (isAddressDocument(requiredType.type)) {
                        addressDocuments.add(requiredType);
                        addressCount++;
                    } else if (requiredType.type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
                        hasPersonalInfo = true;
                    } else if (requiredType.type instanceof TLRPC.TL_secureValueTypeAddress) {
                        hasAddressInfo = true;
                    }
                } else if (secureRequiredType instanceof TLRPC.TL_secureRequiredTypeOneOf) {
                    TLRPC.TL_secureRequiredTypeOneOf requiredTypeOneOf = (TLRPC.TL_secureRequiredTypeOneOf) secureRequiredType;
                    if (requiredTypeOneOf.types.isEmpty()) {
                        continue;
                    }
                    TLRPC.SecureRequiredType innerType = requiredTypeOneOf.types.get(0);
                    if (!(innerType instanceof TLRPC.TL_secureRequiredType)) {
                        continue;
                    }
                    TLRPC.TL_secureRequiredType requiredType = (TLRPC.TL_secureRequiredType) innerType;

                    if (isPersonalDocument(requiredType.type)) {
                        for (int b = 0, size2 = requiredTypeOneOf.types.size(); b < size2; b++) {
                            innerType = requiredTypeOneOf.types.get(b);
                            if (!(innerType instanceof TLRPC.TL_secureRequiredType)) {
                                continue;
                            }
                            personalDocuments.add((TLRPC.TL_secureRequiredType) innerType);
                        }
                        personalCount++;
                    } else if (isAddressDocument(requiredType.type)) {
                        for (int b = 0, size2 = requiredTypeOneOf.types.size(); b < size2; b++) {
                            innerType = requiredTypeOneOf.types.get(b);
                            if (!(innerType instanceof TLRPC.TL_secureRequiredType)) {
                                continue;
                            }
                            addressDocuments.add((TLRPC.TL_secureRequiredType) innerType);
                        }
                        addressCount++;
                    }
                }
            }
            boolean separatePersonal = !hasPersonalInfo || personalCount > 1;
            boolean separateAddress = !hasAddressInfo || addressCount > 1;
            for (int a = 0; a < size; a++) {
                TLRPC.SecureRequiredType secureRequiredType = currentForm.required_types.get(a);
                ArrayList<TLRPC.TL_secureRequiredType> documentTypes;
                TLRPC.TL_secureRequiredType requiredType;
                boolean documentOnly;
                if (secureRequiredType instanceof TLRPC.TL_secureRequiredType) {
                    requiredType = (TLRPC.TL_secureRequiredType) secureRequiredType;
                    if (requiredType.type instanceof TLRPC.TL_secureValueTypePhone || requiredType.type instanceof TLRPC.TL_secureValueTypeEmail) {
                        documentTypes = null;
                        documentOnly = false;
                    } else if (requiredType.type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
                        if (separatePersonal) {
                            documentTypes = null;
                        } else {
                            documentTypes = personalDocuments;
                        }
                        documentOnly = false;
                    } else if (requiredType.type instanceof TLRPC.TL_secureValueTypeAddress) {
                        if (separateAddress) {
                            documentTypes = null;
                        } else {
                            documentTypes = addressDocuments;
                        }
                        documentOnly = false;
                    } else if (separatePersonal && isPersonalDocument(requiredType.type)) {
                        documentTypes = new ArrayList<>();
                        documentTypes.add(requiredType);
                        requiredType = new TLRPC.TL_secureRequiredType();
                        requiredType.type = new TLRPC.TL_secureValueTypePersonalDetails();
                        documentOnly = true;
                    } else if (separateAddress && isAddressDocument(requiredType.type)) {
                        documentTypes = new ArrayList<>();
                        documentTypes.add(requiredType);
                        requiredType = new TLRPC.TL_secureRequiredType();
                        requiredType.type = new TLRPC.TL_secureValueTypeAddress();
                        documentOnly = true;
                    } else {
                        continue;
                    }
                } else if (secureRequiredType instanceof TLRPC.TL_secureRequiredTypeOneOf) {
                    TLRPC.TL_secureRequiredTypeOneOf requiredTypeOneOf = (TLRPC.TL_secureRequiredTypeOneOf) secureRequiredType;
                    if (requiredTypeOneOf.types.isEmpty()) {
                        continue;
                    }
                    TLRPC.SecureRequiredType innerType = requiredTypeOneOf.types.get(0);
                    if (!(innerType instanceof TLRPC.TL_secureRequiredType)) {
                        continue;
                    }
                    requiredType = (TLRPC.TL_secureRequiredType) innerType;

                    if (separatePersonal && isPersonalDocument(requiredType.type) || separateAddress && isAddressDocument(requiredType.type)) {
                        documentTypes = new ArrayList<>();
                        for (int b = 0, size2 = requiredTypeOneOf.types.size(); b < size2; b++) {
                            innerType = requiredTypeOneOf.types.get(b);
                            if (!(innerType instanceof TLRPC.TL_secureRequiredType)) {
                                continue;
                            }
                            documentTypes.add((TLRPC.TL_secureRequiredType) innerType);
                        }
                        if (isPersonalDocument(requiredType.type)) {
                            requiredType = new TLRPC.TL_secureRequiredType();
                            requiredType.type = new TLRPC.TL_secureValueTypePersonalDetails();
                        } else {
                            requiredType = new TLRPC.TL_secureRequiredType();
                            requiredType.type = new TLRPC.TL_secureValueTypeAddress();
                        }

                        documentOnly = true;
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
                addField(context, requiredType, documentTypes, documentOnly, a == size - 1);
            }
        }

        if (botUser != null) {
            bottomCell = new TextInfoPrivacyCell(context);
            bottomCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            bottomCell.setLinkTextColorKey(Theme.key_windowBackgroundWhiteGrayText4);
            if (!TextUtils.isEmpty(currentForm.privacy_policy_url)) {
                String str2 = LocaleController.formatString("PassportPolicy", R.string.PassportPolicy, UserObject.getFirstName(botUser), botUser.username);
                SpannableStringBuilder text = new SpannableStringBuilder(str2);
                int index1 = str2.indexOf('*');
                int index2 = str2.lastIndexOf('*');
                if (index1 != -1 && index2 != -1) {
                    bottomCell.getTextView().setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
                    text.replace(index2, index2 + 1, "");
                    text.replace(index1, index1 + 1, "");
                    text.setSpan(new LinkSpan(), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                bottomCell.setText(text);
            } else {
                bottomCell.setText(AndroidUtilities.replaceTags(LocaleController.formatString("PassportNoPolicy", R.string.PassportNoPolicy, UserObject.getFirstName(botUser), botUser.username)));
            }
            bottomCell.getTextView().setHighlightColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            bottomCell.getTextView().setGravity(Gravity.CENTER_HORIZONTAL);
            linearLayout2.addView(bottomCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        bottomLayout = new FrameLayout(context);
        bottomLayout.setBackgroundDrawable(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_passport_authorizeBackground), Theme.getColor(Theme.key_passport_authorizeBackgroundSelected)));
        frameLayout.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));
        bottomLayout.setOnClickListener(view -> {

            class ValueToSend {
                TLRPC.TL_secureValue value;
                boolean selfie_required;
                boolean translation_required;

                public ValueToSend(TLRPC.TL_secureValue v, boolean s, boolean t) {
                    value = v;
                    selfie_required = s;
                    translation_required = t;
                }
            }

            ArrayList<ValueToSend> valuesToSend = new ArrayList<>();
            for (int a = 0, size = currentForm.required_types.size(); a < size; a++) {

                TLRPC.TL_secureRequiredType requiredType;

                TLRPC.SecureRequiredType secureRequiredType = currentForm.required_types.get(a);
                if (secureRequiredType instanceof TLRPC.TL_secureRequiredType) {
                    requiredType = (TLRPC.TL_secureRequiredType) secureRequiredType;
                } else if (secureRequiredType instanceof TLRPC.TL_secureRequiredTypeOneOf) {
                    TLRPC.TL_secureRequiredTypeOneOf requiredTypeOneOf = (TLRPC.TL_secureRequiredTypeOneOf) secureRequiredType;
                    if (requiredTypeOneOf.types.isEmpty()) {
                        continue;
                    }
                    secureRequiredType = requiredTypeOneOf.types.get(0);
                    if (!(secureRequiredType instanceof TLRPC.TL_secureRequiredType)) {
                        continue;
                    }
                    requiredType = (TLRPC.TL_secureRequiredType) secureRequiredType;

                    for (int b = 0, size2 = requiredTypeOneOf.types.size(); b < size2; b++) {
                        secureRequiredType = requiredTypeOneOf.types.get(b);
                        if (!(secureRequiredType instanceof TLRPC.TL_secureRequiredType)) {
                            continue;
                        }
                        TLRPC.TL_secureRequiredType innerType = (TLRPC.TL_secureRequiredType) secureRequiredType;
                        if (getValueByType(innerType, true) != null) {
                            requiredType = innerType;
                            break;
                        }
                    }
                } else {
                    continue;
                }

                TLRPC.TL_secureValue value = getValueByType(requiredType, true);
                if (value == null) {
                    Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null) {
                        v.vibrate(200);
                    }
                    AndroidUtilities.shakeView(getViewByType(requiredType));
                    return;
                }
                String key = getNameForType(requiredType.type);
                HashMap<String, String> errors = errorsMap.get(key);
                if (errors != null && !errors.isEmpty()) {
                    Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null) {
                        v.vibrate(200);
                    }
                    AndroidUtilities.shakeView(getViewByType(requiredType));
                    return;
                }
                valuesToSend.add(new ValueToSend(value, requiredType.selfie_required, requiredType.translation_required));
            }
            showEditDoneProgress(false, true);
            TLRPC.TL_account_acceptAuthorization req = new TLRPC.TL_account_acceptAuthorization();
            req.bot_id = currentBotId;
            req.scope = currentScope;
            req.public_key = currentPublicKey;
            JSONObject jsonObject = new JSONObject();
            for (int a = 0, size = valuesToSend.size(); a < size; a++) {
                ValueToSend valueToSend = valuesToSend.get(a);
                TLRPC.TL_secureValue secureValue = valueToSend.value;

                JSONObject data = new JSONObject();

                if (secureValue.plain_data != null) {
                    if (secureValue.plain_data instanceof TLRPC.TL_securePlainEmail) {
                        TLRPC.TL_securePlainEmail securePlainEmail = (TLRPC.TL_securePlainEmail) secureValue.plain_data;
                    } else if (secureValue.plain_data instanceof TLRPC.TL_securePlainPhone) {
                        TLRPC.TL_securePlainPhone securePlainPhone = (TLRPC.TL_securePlainPhone) secureValue.plain_data;
                    }
                } else {
                    try {
                        JSONObject result = new JSONObject();
                        if (secureValue.data != null) {
                            byte[] decryptedSecret = decryptValueSecret(secureValue.data.secret, secureValue.data.data_hash);

                            data.put("data_hash", Base64.encodeToString(secureValue.data.data_hash, Base64.NO_WRAP));
                            data.put("secret", Base64.encodeToString(decryptedSecret, Base64.NO_WRAP));

                            result.put("data", data);
                        }
                        if (!secureValue.files.isEmpty()) {
                            JSONArray files = new JSONArray();
                            for (int b = 0, size2 = secureValue.files.size(); b < size2; b++) {
                                TLRPC.TL_secureFile secureFile = (TLRPC.TL_secureFile) secureValue.files.get(b);
                                byte[] decryptedSecret = decryptValueSecret(secureFile.secret, secureFile.file_hash);

                                JSONObject file = new JSONObject();
                                file.put("file_hash", Base64.encodeToString(secureFile.file_hash, Base64.NO_WRAP));
                                file.put("secret", Base64.encodeToString(decryptedSecret, Base64.NO_WRAP));
                                files.put(file);
                            }
                            result.put("files", files);
                        }
                        if (secureValue.front_side instanceof TLRPC.TL_secureFile) {
                            TLRPC.TL_secureFile secureFile = (TLRPC.TL_secureFile) secureValue.front_side;
                            byte[] decryptedSecret = decryptValueSecret(secureFile.secret, secureFile.file_hash);

                            JSONObject front = new JSONObject();
                            front.put("file_hash", Base64.encodeToString(secureFile.file_hash, Base64.NO_WRAP));
                            front.put("secret", Base64.encodeToString(decryptedSecret, Base64.NO_WRAP));
                            result.put("front_side", front);
                        }
                        if (secureValue.reverse_side instanceof TLRPC.TL_secureFile) {
                            TLRPC.TL_secureFile secureFile = (TLRPC.TL_secureFile) secureValue.reverse_side;
                            byte[] decryptedSecret = decryptValueSecret(secureFile.secret, secureFile.file_hash);

                            JSONObject reverse = new JSONObject();
                            reverse.put("file_hash", Base64.encodeToString(secureFile.file_hash, Base64.NO_WRAP));
                            reverse.put("secret", Base64.encodeToString(decryptedSecret, Base64.NO_WRAP));
                            result.put("reverse_side", reverse);
                        }
                        if (valueToSend.selfie_required && secureValue.selfie instanceof TLRPC.TL_secureFile) {
                            TLRPC.TL_secureFile secureFile = (TLRPC.TL_secureFile) secureValue.selfie;
                            byte[] decryptedSecret = decryptValueSecret(secureFile.secret, secureFile.file_hash);

                            JSONObject selfie = new JSONObject();
                            selfie.put("file_hash", Base64.encodeToString(secureFile.file_hash, Base64.NO_WRAP));
                            selfie.put("secret", Base64.encodeToString(decryptedSecret, Base64.NO_WRAP));
                            result.put("selfie", selfie);
                        }
                        if (valueToSend.translation_required && !secureValue.translation.isEmpty()) {
                            JSONArray translation = new JSONArray();
                            for (int b = 0, size2 = secureValue.translation.size(); b < size2; b++) {
                                TLRPC.TL_secureFile secureFile = (TLRPC.TL_secureFile) secureValue.translation.get(b);
                                byte[] decryptedSecret = decryptValueSecret(secureFile.secret, secureFile.file_hash);

                                JSONObject file = new JSONObject();
                                file.put("file_hash", Base64.encodeToString(secureFile.file_hash, Base64.NO_WRAP));
                                file.put("secret", Base64.encodeToString(decryptedSecret, Base64.NO_WRAP));
                                translation.put(file);
                            }
                            result.put("translation", translation);
                        }
                        jsonObject.put(getNameForType(secureValue.type), result);
                    } catch (Exception ignore) {

                    }
                }

                TLRPC.TL_secureValueHash hash = new TLRPC.TL_secureValueHash();
                hash.type = secureValue.type;
                hash.hash = secureValue.hash;
                req.value_hashes.add(hash);
            }
            JSONObject result = new JSONObject();
            try {
                result.put("secure_data", jsonObject);
            } catch (Exception ignore) {

            }
            if (currentPayload != null) {
                try {
                    result.put("payload", currentPayload);
                } catch (Exception ignore) {

                }
            }
            if (currentNonce != null) {
                try {
                    result.put("nonce", currentNonce);
                } catch (Exception ignore) {

                }
            }
            String json = result.toString();

            EncryptionResult encryptionResult = encryptData(AndroidUtilities.getStringBytes(json));

            req.credentials = new TLRPC.TL_secureCredentialsEncrypted();
            req.credentials.hash = encryptionResult.fileHash;
            req.credentials.data = encryptionResult.encryptedData;
            try {
                String key = currentPublicKey.replaceAll("\\n", "").replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "");
                KeyFactory kf = KeyFactory.getInstance("RSA");
                X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(Base64.decode(key, Base64.DEFAULT));
                RSAPublicKey pubKey = (RSAPublicKey) kf.generatePublic(keySpecX509);

                Cipher c = Cipher.getInstance("RSA/NONE/OAEPWithSHA1AndMGF1Padding");
                c.init(Cipher.ENCRYPT_MODE, pubKey);
                req.credentials.secret = c.doFinal(encryptionResult.decrypyedFileSecret);
            } catch (Exception e) {
                FileLog.e(e);
            }
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    ignoreOnFailure = true;
                    callCallback(true);
                    finishFragment();
                } else {
                    showEditDoneProgress(false, false);
                    if ("APP_VERSION_OUTDATED".equals(error.text)) {
                        AlertsCreator.showUpdateAppAlert(getParentActivity(), LocaleController.getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                    } else {
                        showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error.text);
                    }
                }
            }));
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        });

        acceptTextView = new TextView(context);
        acceptTextView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        acceptTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.authorize, 0, 0, 0);
        acceptTextView.setTextColor(Theme.getColor(Theme.key_passport_authorizeText));
        acceptTextView.setText(LocaleController.getString("PassportAuthorize", R.string.PassportAuthorize));
        acceptTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        acceptTextView.setGravity(Gravity.CENTER);
        acceptTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomLayout.addView(acceptTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        progressViewButton = new ContextProgressView(context, 0);
        progressViewButton.setVisibility(View.INVISIBLE);
        bottomLayout.addView(progressViewButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        frameLayout.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 48));
    }

    private void createManageInterface(Context context) {
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        actionBar.setTitle(LocaleController.getString("TelegramPassport", R.string.TelegramPassport));

        actionBar.createMenu().addItem(info_item, R.drawable.msg_info);

        headerCell = new HeaderCell(context);
        headerCell.setText(LocaleController.getString("PassportProvidedInformation", R.string.PassportProvidedInformation));
        headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout2.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        sectionCell = new ShadowSectionCell(context);
        sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        linearLayout2.addView(sectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        addDocumentCell = new TextSettingsCell(context);
        addDocumentCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
        addDocumentCell.setText(LocaleController.getString("PassportNoDocumentsAdd", R.string.PassportNoDocumentsAdd), true);
        linearLayout2.addView(addDocumentCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        addDocumentCell.setOnClickListener(v -> openAddDocumentAlert());

        deletePassportCell = new TextSettingsCell(context);
        deletePassportCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
        deletePassportCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
        deletePassportCell.setText(LocaleController.getString("TelegramPassportDelete", R.string.TelegramPassportDelete), false);
        linearLayout2.addView(deletePassportCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        deletePassportCell.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("TelegramPassportDeleteTitle", R.string.TelegramPassportDeleteTitle));
            builder.setMessage(LocaleController.getString("TelegramPassportDeleteAlert", R.string.TelegramPassportDeleteAlert));
            builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which) -> {
                TLRPC.TL_account_deleteSecureValue req = new TLRPC.TL_account_deleteSecureValue();
                for (int a = 0; a < currentForm.values.size(); a++) {
                    req.types.add(currentForm.values.get(a).type);
                }
                needShowProgress();
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0; a < linearLayout2.getChildCount(); a++) {
                        View child = linearLayout2.getChildAt(a);
                        if (child instanceof TextDetailSecureCell) {
                            linearLayout2.removeView(child);
                            a--;
                        }
                    }
                    needHideProgress();
                    typesViews.clear();
                    typesValues.clear();
                    currentForm.values.clear();
                    updateManageVisibility();
                }));
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            AlertDialog alertDialog = builder.create();
            showDialog(alertDialog);
            TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
            }
        });

        addDocumentSectionCell = new ShadowSectionCell(context);
        addDocumentSectionCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        linearLayout2.addView(addDocumentSectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        emptyLayout = new LinearLayout(context);
        emptyLayout.setOrientation(LinearLayout.VERTICAL);
        emptyLayout.setGravity(Gravity.CENTER);
        emptyLayout.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        if (AndroidUtilities.isTablet()) {
            linearLayout2.addView(emptyLayout, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(528) - ActionBar.getCurrentActionBarHeight()));
        } else {
            linearLayout2.addView(emptyLayout, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight()));
        }

        emptyImageView = new ImageView(context);
        emptyImageView.setImageResource(R.drawable.no_passport);
        emptyImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_sessions_devicesImage), PorterDuff.Mode.MULTIPLY));
        emptyLayout.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        emptyTextView1 = new TextView(context);
        emptyTextView1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        emptyTextView1.setGravity(Gravity.CENTER);
        emptyTextView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyTextView1.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        emptyTextView1.setText(LocaleController.getString("PassportNoDocuments", R.string.PassportNoDocuments));
        emptyLayout.addView(emptyTextView1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 16, 0, 0));

        emptyTextView2 = new TextView(context);
        emptyTextView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        emptyTextView2.setGravity(Gravity.CENTER);
        emptyTextView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyTextView2.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        emptyTextView2.setText(LocaleController.getString("PassportNoDocumentsInfo", R.string.PassportNoDocumentsInfo));
        emptyLayout.addView(emptyTextView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 14, 0, 0));

        emptyTextView3 = new TextView(context);
        emptyTextView3.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        emptyTextView3.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4));
        emptyTextView3.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4), 0x20), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        emptyTextView3.setGravity(Gravity.CENTER);
        emptyTextView3.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyTextView3.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        emptyTextView3.setGravity(Gravity.CENTER);
        emptyTextView3.setText(LocaleController.getString("PassportNoDocumentsAdd", R.string.PassportNoDocumentsAdd).toUpperCase());
        emptyLayout.addView(emptyTextView3, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 30, Gravity.CENTER, 0, 12, 0, 0));
        emptyTextView3.setOnClickListener(v -> openAddDocumentAlert());

        for (int a = 0, size = currentForm.values.size(); a < size; a++) {
            TLRPC.TL_secureValue value = currentForm.values.get(a);
            TLRPC.TL_secureRequiredType requiredType;
            ArrayList<TLRPC.TL_secureRequiredType> documentTypes;
            boolean documentOnly;
            if (isPersonalDocument(value.type)) {
                documentTypes = new ArrayList<>();
                requiredType = new TLRPC.TL_secureRequiredType();
                requiredType.type = value.type;
                requiredType.selfie_required = true;
                requiredType.translation_required = true;
                documentTypes.add(requiredType);
                requiredType = new TLRPC.TL_secureRequiredType();
                requiredType.type = new TLRPC.TL_secureValueTypePersonalDetails();
                documentOnly = true;
            } else if (isAddressDocument(value.type)) {
                documentTypes = new ArrayList<>();
                requiredType = new TLRPC.TL_secureRequiredType();
                requiredType.type = value.type;
                requiredType.translation_required = true;
                documentTypes.add(requiredType);
                requiredType = new TLRPC.TL_secureRequiredType();
                requiredType.type = new TLRPC.TL_secureValueTypeAddress();
                documentOnly = true;
            } else {
                requiredType = new TLRPC.TL_secureRequiredType();
                requiredType.type = value.type;
                documentTypes = null;
                documentOnly = false;
            }
            addField(context, requiredType, documentTypes, documentOnly, a == size - 1);
        }

        updateManageVisibility();
    }

    private boolean hasNotValueForType(Class<? extends TLRPC.SecureValueType> type) {
        for (int a = 0, count = currentForm.values.size(); a < count; a++) {
            if (currentForm.values.get(a).type.getClass() == type) {
                return false;
            }
        }
        return true;
    }

    private boolean hasUnfilledValues() {
        return hasNotValueForType(TLRPC.TL_secureValueTypePhone.class) ||
                hasNotValueForType(TLRPC.TL_secureValueTypeEmail.class) ||
                hasNotValueForType(TLRPC.TL_secureValueTypePersonalDetails.class) ||
                hasNotValueForType(TLRPC.TL_secureValueTypePassport.class) ||
                hasNotValueForType(TLRPC.TL_secureValueTypeInternalPassport.class) ||
                hasNotValueForType(TLRPC.TL_secureValueTypeIdentityCard.class) ||
                hasNotValueForType(TLRPC.TL_secureValueTypeDriverLicense.class) ||
                hasNotValueForType(TLRPC.TL_secureValueTypeAddress.class) ||
                hasNotValueForType(TLRPC.TL_secureValueTypeUtilityBill.class) ||
                hasNotValueForType(TLRPC.TL_secureValueTypePassportRegistration.class) ||
                hasNotValueForType(TLRPC.TL_secureValueTypeTemporaryRegistration.class) ||
                hasNotValueForType(TLRPC.TL_secureValueTypeBankStatement.class) ||
                hasNotValueForType(TLRPC.TL_secureValueTypeRentalAgreement.class);
    }

    private void openAddDocumentAlert() {
        ArrayList<CharSequence> values = new ArrayList<>();
        final ArrayList<Class<? extends TLRPC.SecureValueType>> types = new ArrayList<>();

        if (hasNotValueForType(TLRPC.TL_secureValueTypePhone.class)) {
            values.add(LocaleController.getString("ActionBotDocumentPhone", R.string.ActionBotDocumentPhone));
            types.add(TLRPC.TL_secureValueTypePhone.class);
        }
        if (hasNotValueForType(TLRPC.TL_secureValueTypeEmail.class)) {
            values.add(LocaleController.getString("ActionBotDocumentEmail", R.string.ActionBotDocumentEmail));
            types.add(TLRPC.TL_secureValueTypeEmail.class);
        }
        if (hasNotValueForType(TLRPC.TL_secureValueTypePersonalDetails.class)) {
            values.add(LocaleController.getString("ActionBotDocumentIdentity", R.string.ActionBotDocumentIdentity));
            types.add(TLRPC.TL_secureValueTypePersonalDetails.class);
        }
        if (hasNotValueForType(TLRPC.TL_secureValueTypePassport.class)) {
            values.add(LocaleController.getString("ActionBotDocumentPassport", R.string.ActionBotDocumentPassport));
            types.add(TLRPC.TL_secureValueTypePassport.class);
        }
        if (hasNotValueForType(TLRPC.TL_secureValueTypeInternalPassport.class)) {
            values.add(LocaleController.getString("ActionBotDocumentInternalPassport", R.string.ActionBotDocumentInternalPassport));
            types.add(TLRPC.TL_secureValueTypeInternalPassport.class);
        }
        if (hasNotValueForType(TLRPC.TL_secureValueTypePassportRegistration.class)) {
            values.add(LocaleController.getString("ActionBotDocumentPassportRegistration", R.string.ActionBotDocumentPassportRegistration));
            types.add(TLRPC.TL_secureValueTypePassportRegistration.class);
        }
        if (hasNotValueForType(TLRPC.TL_secureValueTypeTemporaryRegistration.class)) {
            values.add(LocaleController.getString("ActionBotDocumentTemporaryRegistration", R.string.ActionBotDocumentTemporaryRegistration));
            types.add(TLRPC.TL_secureValueTypeTemporaryRegistration.class);
        }
        if (hasNotValueForType(TLRPC.TL_secureValueTypeIdentityCard.class)) {
            values.add(LocaleController.getString("ActionBotDocumentIdentityCard", R.string.ActionBotDocumentIdentityCard));
            types.add(TLRPC.TL_secureValueTypeIdentityCard.class);
        }
        if (hasNotValueForType(TLRPC.TL_secureValueTypeDriverLicense.class)) {
            values.add(LocaleController.getString("ActionBotDocumentDriverLicence", R.string.ActionBotDocumentDriverLicence));
            types.add(TLRPC.TL_secureValueTypeDriverLicense.class);
        }
        if (hasNotValueForType(TLRPC.TL_secureValueTypeAddress.class)) {
            values.add(LocaleController.getString("ActionBotDocumentAddress", R.string.ActionBotDocumentAddress));
            types.add(TLRPC.TL_secureValueTypeAddress.class);
        }
        if (hasNotValueForType(TLRPC.TL_secureValueTypeUtilityBill.class)) {
            values.add(LocaleController.getString("ActionBotDocumentUtilityBill", R.string.ActionBotDocumentUtilityBill));
            types.add(TLRPC.TL_secureValueTypeUtilityBill.class);
        }
        if (hasNotValueForType(TLRPC.TL_secureValueTypeBankStatement.class)) {
            values.add(LocaleController.getString("ActionBotDocumentBankStatement", R.string.ActionBotDocumentBankStatement));
            types.add(TLRPC.TL_secureValueTypeBankStatement.class);
        }
        if (hasNotValueForType(TLRPC.TL_secureValueTypeRentalAgreement.class)) {
            values.add(LocaleController.getString("ActionBotDocumentRentalAgreement", R.string.ActionBotDocumentRentalAgreement));
            types.add(TLRPC.TL_secureValueTypeRentalAgreement.class);
        }

        if (getParentActivity() == null || values.isEmpty()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("PassportNoDocumentsAdd", R.string.PassportNoDocumentsAdd));
        builder.setItems(values.toArray(new CharSequence[0]), (dialog, which) -> {
            TLRPC.TL_secureRequiredType requiredType = null;
            TLRPC.TL_secureRequiredType documentRequiredType = null;
            try {
                requiredType = new TLRPC.TL_secureRequiredType();
                requiredType.type = types.get(which).newInstance();
            } catch (Exception ignore) {

            }

            if (isPersonalDocument(requiredType.type)) {
                documentRequiredType = requiredType;
                documentRequiredType.selfie_required = true;
                documentRequiredType.translation_required = true;
                requiredType = new TLRPC.TL_secureRequiredType();
                requiredType.type = new TLRPC.TL_secureValueTypePersonalDetails();
            } else if (isAddressDocument(requiredType.type)) {
                documentRequiredType = requiredType;
                requiredType = new TLRPC.TL_secureRequiredType();
                requiredType.type = new TLRPC.TL_secureValueTypeAddress();
            }

            openTypeActivity(requiredType, documentRequiredType, new ArrayList<>(), documentRequiredType != null);
        });
        showDialog(builder.create());
    }

    private void updateManageVisibility() {
        if (currentForm.values.isEmpty()) {
            emptyLayout.setVisibility(View.VISIBLE);
            sectionCell.setVisibility(View.GONE);
            headerCell.setVisibility(View.GONE);
            addDocumentCell.setVisibility(View.GONE);
            deletePassportCell.setVisibility(View.GONE);
            addDocumentSectionCell.setVisibility(View.GONE);
        } else {
            emptyLayout.setVisibility(View.GONE);
            sectionCell.setVisibility(View.VISIBLE);
            headerCell.setVisibility(View.VISIBLE);
            deletePassportCell.setVisibility(View.VISIBLE);
            addDocumentSectionCell.setVisibility(View.VISIBLE);

            if (hasUnfilledValues()) {
                addDocumentCell.setVisibility(View.VISIBLE);
            } else {
                addDocumentCell.setVisibility(View.GONE);
            }
        }
    }

    private void callCallback(boolean success) {
        if (!callbackCalled) {
            if (!TextUtils.isEmpty(currentCallbackUrl)) {
                if (success) {
                    Browser.openUrl(getParentActivity(), Uri.parse(currentCallbackUrl + "&tg_passport=success"));
                } else if (!ignoreOnFailure && (currentActivityType == TYPE_PASSWORD || currentActivityType == TYPE_REQUEST)) {
                    Browser.openUrl(getParentActivity(), Uri.parse(currentCallbackUrl + "&tg_passport=cancel"));
                }
                callbackCalled = true;
            } else if (needActivityResult) {
                if (success || (!ignoreOnFailure && (currentActivityType == TYPE_PASSWORD || currentActivityType == TYPE_REQUEST))) {
                    getParentActivity().setResult(success ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
                }
                callbackCalled = true;
            }
        }
    }

    private void createEmailInterface(Context context) {
        actionBar.setTitle(LocaleController.getString("PassportEmail", R.string.PassportEmail));

        if (!TextUtils.isEmpty(currentEmail)) {
            TextSettingsCell settingsCell1 = new TextSettingsCell(context);
            settingsCell1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            settingsCell1.setBackgroundDrawable(Theme.getSelectorDrawable(true));
            settingsCell1.setText(LocaleController.formatString("PassportPhoneUseSame", R.string.PassportPhoneUseSame, currentEmail), false);
            linearLayout2.addView(settingsCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            settingsCell1.setOnClickListener(v -> {
                useCurrentValue = true;
                doneItem.callOnClick();
                useCurrentValue = false;
            });

            bottomCell = new TextInfoPrivacyCell(context);
            bottomCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            bottomCell.setText(LocaleController.getString("PassportPhoneUseSameEmailInfo", R.string.PassportPhoneUseSameEmailInfo));
            linearLayout2.addView(bottomCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        inputFields = new EditTextBoldCursor[1];
        for (int a = 0; a < 1; a++) {
            ViewGroup container = new FrameLayout(context);
            linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
            container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            inputFields[a] = new EditTextBoldCursor(context);
            inputFields[a].setTag(a);
            inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            inputFields[a].setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            inputFields[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setBackgroundDrawable(null);
            inputFields[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setCursorSize(AndroidUtilities.dp(20));
            inputFields[a].setCursorWidth(1.5f);
            inputFields[a].setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            inputFields[a].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            inputFields[a].setHint(LocaleController.getString("PaymentShippingEmailPlaceholder", R.string.PaymentShippingEmailPlaceholder));
            if (currentTypeValue != null && currentTypeValue.plain_data instanceof TLRPC.TL_securePlainEmail) {
                TLRPC.TL_securePlainEmail securePlainEmail = (TLRPC.TL_securePlainEmail) currentTypeValue.plain_data;
                if (!TextUtils.isEmpty(securePlainEmail.email)) {
                    inputFields[a].setText(securePlainEmail.email);
                }
            }
            inputFields[a].setSelection(inputFields[a].length());
            inputFields[a].setPadding(0, 0, 0, AndroidUtilities.dp(6));
            inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 12, 21, 6));

            inputFields[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_DONE || i == EditorInfo.IME_ACTION_NEXT) {
                    doneItem.callOnClick();
                    return true;
                }
                return false;
            });
        }

        bottomCell = new TextInfoPrivacyCell(context);
        bottomCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        bottomCell.setText(LocaleController.getString("PassportEmailUploadInfo", R.string.PassportEmailUploadInfo));
        linearLayout2.addView(bottomCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void createPhoneInterface(Context context) {
        actionBar.setTitle(LocaleController.getString("PassportPhone", R.string.PassportPhone));

        languageMap = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().getAssets().open("countries.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] args = line.split(";");
                countriesArray.add(0, args[2]);
                countriesMap.put(args[2], args[0]);
                codesMap.put(args[0], args[2]);
                if (args.length > 3) {
                    phoneFormatMap.put(args[0], args[3]);
                }
                languageMap.put(args[1], args[2]);
            }
            reader.close();
        } catch (Exception e) {
            FileLog.e(e);
        }

        Collections.sort(countriesArray, String::compareTo);

        String currentPhone = UserConfig.getInstance(currentAccount).getCurrentUser().phone;
        TextSettingsCell settingsCell1 = new TextSettingsCell(context);
        settingsCell1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        settingsCell1.setBackgroundDrawable(Theme.getSelectorDrawable(true));
        settingsCell1.setText(LocaleController.formatString("PassportPhoneUseSame", R.string.PassportPhoneUseSame, PhoneFormat.getInstance().format("+" + currentPhone)), false);
        linearLayout2.addView(settingsCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        settingsCell1.setOnClickListener(v -> {
            useCurrentValue = true;
            doneItem.callOnClick();
            useCurrentValue = false;
        });

        bottomCell = new TextInfoPrivacyCell(context);
        bottomCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        bottomCell.setText(LocaleController.getString("PassportPhoneUseSameInfo", R.string.PassportPhoneUseSameInfo));
        linearLayout2.addView(bottomCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        headerCell = new HeaderCell(context);
        headerCell.setText(LocaleController.getString("PassportPhoneUseOther", R.string.PassportPhoneUseOther));
        headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout2.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        inputFields = new EditTextBoldCursor[3];
        for (int a = 0; a < 3; a++) {

            if (a == FIELD_PHONE) {
                inputFields[a] = new HintEditText(context);
            } else {
                inputFields[a] = new EditTextBoldCursor(context);
            }

            ViewGroup container;
            if (a == FIELD_PHONECODE) {
                container = new LinearLayout(context);
                ((LinearLayout) container).setOrientation(LinearLayout.HORIZONTAL);
                linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (a == FIELD_PHONE) {
                container = (ViewGroup) inputFields[FIELD_PHONECODE].getParent();
            } else {
                container = new FrameLayout(context);
                linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }

            inputFields[a].setTag(a);
            inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            inputFields[a].setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            inputFields[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setBackgroundDrawable(null);
            inputFields[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setCursorSize(AndroidUtilities.dp(20));
            inputFields[a].setCursorWidth(1.5f);
            if (a == FIELD_PHONECOUNTRY) {
                inputFields[a].setOnTouchListener((v, event) -> {
                    if (getParentActivity() == null) {
                        return false;
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        CountrySelectActivity fragment = new CountrySelectActivity(false);
                        fragment.setCountrySelectActivityDelegate(country -> {
                            inputFields[FIELD_PHONECOUNTRY].setText(country.name);
                            int index = countriesArray.indexOf(country.name);
                            if (index != -1) {
                                ignoreOnTextChange = true;
                                String code = countriesMap.get(country.name);
                                inputFields[FIELD_PHONECODE].setText(code);
                                String hint = phoneFormatMap.get(code);
                                inputFields[FIELD_PHONE].setHintText(hint != null ? hint.replace('X', '–') : null);
                                ignoreOnTextChange = false;
                            }
                            AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(inputFields[FIELD_PHONE]), 300);
                            inputFields[FIELD_PHONE].requestFocus();
                            inputFields[FIELD_PHONE].setSelection(inputFields[FIELD_PHONE].length());
                        });
                        presentFragment(fragment);
                    }
                    return true;
                });
                inputFields[a].setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                inputFields[a].setInputType(0);
                inputFields[a].setFocusable(false);
            } else {
                inputFields[a].setInputType(InputType.TYPE_CLASS_PHONE);
                if (a == FIELD_PHONE) {
                    inputFields[a].setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                } else {
                    inputFields[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                }
            }
            inputFields[a].setSelection(inputFields[a].length());

            if (a == FIELD_PHONECODE) {
                plusTextView = new TextView(context);
                plusTextView.setText("+");
                plusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                plusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                container.addView(plusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 21, 12, 0, 6));

                inputFields[a].setPadding(AndroidUtilities.dp(10), 0, 0, 0);
                InputFilter[] inputFilters = new InputFilter[1];
                inputFilters[0] = new InputFilter.LengthFilter(5);
                inputFields[a].setFilters(inputFilters);
                inputFields[a].setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                container.addView(inputFields[a], LayoutHelper.createLinear(55, LayoutHelper.WRAP_CONTENT, 0, 12, 16, 6));
                inputFields[a].addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        if (ignoreOnTextChange) {
                            return;
                        }
                        ignoreOnTextChange = true;
                        String text = PhoneFormat.stripExceptNumbers(inputFields[FIELD_PHONECODE].getText().toString());
                        inputFields[FIELD_PHONECODE].setText(text);
                        HintEditText phoneField = (HintEditText) inputFields[FIELD_PHONE];
                        if (text.length() == 0) {
                            phoneField.setHintText(null);
                            phoneField.setHint(LocaleController.getString("PaymentShippingPhoneNumber", R.string.PaymentShippingPhoneNumber));
                            inputFields[FIELD_PHONECOUNTRY].setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                        } else {
                            String country;
                            boolean ok = false;
                            String textToSet = null;
                            if (text.length() > 4) {
                                for (int a = 4; a >= 1; a--) {
                                    String sub = text.substring(0, a);
                                    country = codesMap.get(sub);
                                    if (country != null) {
                                        ok = true;
                                        textToSet = text.substring(a) + inputFields[FIELD_PHONE].getText().toString();
                                        inputFields[FIELD_PHONECODE].setText(text = sub);
                                        break;
                                    }
                                }
                                if (!ok) {
                                    textToSet = text.substring(1) + inputFields[FIELD_PHONE].getText().toString();
                                    inputFields[FIELD_PHONECODE].setText(text = text.substring(0, 1));
                                }
                            }
                            country = codesMap.get(text);
                            boolean set = false;
                            if (country != null) {
                                int index = countriesArray.indexOf(country);
                                if (index != -1) {
                                    inputFields[FIELD_PHONECOUNTRY].setText(countriesArray.get(index));
                                    String hint = phoneFormatMap.get(text);
                                    set = true;
                                    if (hint != null) {
                                        phoneField.setHintText(hint.replace('X', '–'));
                                        phoneField.setHint(null);
                                    }
                                }
                            }
                            if (!set) {
                                phoneField.setHintText(null);
                                phoneField.setHint(LocaleController.getString("PaymentShippingPhoneNumber", R.string.PaymentShippingPhoneNumber));
                                inputFields[FIELD_PHONECOUNTRY].setText(LocaleController.getString("WrongCountry", R.string.WrongCountry));
                            }
                            if (!ok) {
                                inputFields[FIELD_PHONECODE].setSelection(inputFields[FIELD_PHONECODE].getText().length());
                            }
                            if (textToSet != null) {
                                phoneField.requestFocus();
                                phoneField.setText(textToSet);
                                phoneField.setSelection(phoneField.length());
                            }
                        }
                        ignoreOnTextChange = false;
                    }
                });
            } else if (a == FIELD_PHONE) {
                inputFields[a].setPadding(0, 0, 0, 0);
                inputFields[a].setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                inputFields[a].setHintText(null);
                inputFields[a].setHint(LocaleController.getString("PaymentShippingPhoneNumber", R.string.PaymentShippingPhoneNumber));
                container.addView(inputFields[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 21, 6));
                inputFields[a].addTextChangedListener(new TextWatcher() {
                    private int characterAction = -1;
                    private int actionPosition;

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        if (count == 0 && after == 1) {
                            characterAction = 1;
                        } else if (count == 1 && after == 0) {
                            if (s.charAt(start) == ' ' && start > 0) {
                                characterAction = 3;
                                actionPosition = start - 1;
                            } else {
                                characterAction = 2;
                            }
                        } else {
                            characterAction = -1;
                        }
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (ignoreOnPhoneChange) {
                            return;
                        }
                        HintEditText phoneField = (HintEditText) inputFields[FIELD_PHONE];
                        int start = phoneField.getSelectionStart();
                        String phoneChars = "0123456789";
                        String str = phoneField.getText().toString();
                        if (characterAction == 3) {
                            str = str.substring(0, actionPosition) + str.substring(actionPosition + 1);
                            start--;
                        }
                        StringBuilder builder = new StringBuilder(str.length());
                        for (int a = 0; a < str.length(); a++) {
                            String ch = str.substring(a, a + 1);
                            if (phoneChars.contains(ch)) {
                                builder.append(ch);
                            }
                        }
                        ignoreOnPhoneChange = true;
                        String hint = phoneField.getHintText();
                        if (hint != null) {
                            for (int a = 0; a < builder.length(); a++) {
                                if (a < hint.length()) {
                                    if (hint.charAt(a) == ' ') {
                                        builder.insert(a, ' ');
                                        a++;
                                        if (start == a && characterAction != 2 && characterAction != 3) {
                                            start++;
                                        }
                                    }
                                } else {
                                    builder.insert(a, ' ');
                                    if (start == a + 1 && characterAction != 2 && characterAction != 3) {
                                        start++;
                                    }
                                    break;
                                }
                            }
                        }
                        phoneField.setText(builder);
                        if (start >= 0) {
                            phoneField.setSelection(Math.min(start, phoneField.length()));
                        }
                        phoneField.onTextChange();
                        ignoreOnPhoneChange = false;
                    }
                });
            } else {
                inputFields[a].setPadding(0, 0, 0, AndroidUtilities.dp(6));
                inputFields[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 21, 12, 21, 6));
            }

            inputFields[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    inputFields[FIELD_PHONE].requestFocus();
                    return true;
                } else if (i == EditorInfo.IME_ACTION_DONE) {
                    doneItem.callOnClick();
                    return true;
                }
                return false;
            });
            if (a == FIELD_PHONE) {
                inputFields[a].setOnKeyListener((v, keyCode, event) -> {
                    if (keyCode == KeyEvent.KEYCODE_DEL && inputFields[FIELD_PHONE].length() == 0) {
                        inputFields[FIELD_PHONECODE].requestFocus();
                        inputFields[FIELD_PHONECODE].setSelection(inputFields[FIELD_PHONECODE].length());
                        inputFields[FIELD_PHONECODE].dispatchKeyEvent(event);
                        return true;
                    }
                    return false;
                });
            }

            if (a == FIELD_PHONECOUNTRY) {
                View divider = new View(context);
                dividers.add(divider);
                divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
                container.addView(divider, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.LEFT | Gravity.BOTTOM));
            }
        }


        String country = null;
        try {
            TelephonyManager telephonyManager = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                country = telephonyManager.getSimCountryIso().toUpperCase();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (country != null) {
            String countryName = languageMap.get(country);
            if (countryName != null) {
                int index = countriesArray.indexOf(countryName);
                if (index != -1) {
                    inputFields[FIELD_PHONECODE].setText(countriesMap.get(countryName));
                }
            }
        }

        bottomCell = new TextInfoPrivacyCell(context);
        bottomCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        bottomCell.setText(LocaleController.getString("PassportPhoneUploadInfo", R.string.PassportPhoneUploadInfo));
        linearLayout2.addView(bottomCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void createAddressInterface(Context context) {
        languageMap = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().getAssets().open("countries.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] args = line.split(";");
                languageMap.put(args[1], args[2]);
            }
            reader.close();
        } catch (Exception e) {
            FileLog.e(e);
        }

        topErrorCell = new TextInfoPrivacyCell(context);
        topErrorCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
        topErrorCell.setPadding(0, AndroidUtilities.dp(7), 0, 0);
        linearLayout2.addView(topErrorCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        checkTopErrorCell(true);

        if (currentDocumentsType != null) {
            if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeRentalAgreement) {
                actionBar.setTitle(LocaleController.getString("ActionBotDocumentRentalAgreement", R.string.ActionBotDocumentRentalAgreement));
            } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeBankStatement) {
                actionBar.setTitle(LocaleController.getString("ActionBotDocumentBankStatement", R.string.ActionBotDocumentBankStatement));
            } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeUtilityBill) {
                actionBar.setTitle(LocaleController.getString("ActionBotDocumentUtilityBill", R.string.ActionBotDocumentUtilityBill));
            } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypePassportRegistration) {
                actionBar.setTitle(LocaleController.getString("ActionBotDocumentPassportRegistration", R.string.ActionBotDocumentPassportRegistration));
            } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeTemporaryRegistration) {
                actionBar.setTitle(LocaleController.getString("ActionBotDocumentTemporaryRegistration", R.string.ActionBotDocumentTemporaryRegistration));
            }

            headerCell = new HeaderCell(context);
            headerCell.setText(LocaleController.getString("PassportDocuments", R.string.PassportDocuments));
            headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            linearLayout2.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            documentsLayout = new LinearLayout(context);
            documentsLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout2.addView(documentsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            uploadDocumentCell = new TextSettingsCell(context);
            uploadDocumentCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
            linearLayout2.addView(uploadDocumentCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            uploadDocumentCell.setOnClickListener(v -> {
                uploadingFileType = UPLOADING_TYPE_DOCUMENTS;
                openAttachMenu();
            });

            bottomCell = new TextInfoPrivacyCell(context);
            bottomCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));

            if (currentBotId != 0) {
                noAllDocumentsErrorText = LocaleController.getString("PassportAddAddressUploadInfo", R.string.PassportAddAddressUploadInfo);
            } else {
                if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeRentalAgreement) {
                    noAllDocumentsErrorText = LocaleController.getString("PassportAddAgreementInfo", R.string.PassportAddAgreementInfo);
                } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeUtilityBill) {
                    noAllDocumentsErrorText = LocaleController.getString("PassportAddBillInfo", R.string.PassportAddBillInfo);
                } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypePassportRegistration) {
                    noAllDocumentsErrorText = LocaleController.getString("PassportAddPassportRegistrationInfo", R.string.PassportAddPassportRegistrationInfo);
                } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeTemporaryRegistration) {
                    noAllDocumentsErrorText = LocaleController.getString("PassportAddTemporaryRegistrationInfo", R.string.PassportAddTemporaryRegistrationInfo);
                } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeBankStatement) {
                    noAllDocumentsErrorText = LocaleController.getString("PassportAddBankInfo", R.string.PassportAddBankInfo);
                } else {
                    noAllDocumentsErrorText = "";
                }
            }

            CharSequence text = noAllDocumentsErrorText;
            if (documentsErrors != null) {
                String errorText;
                if ((errorText = documentsErrors.get("files_all")) != null) {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(errorText);
                    stringBuilder.append("\n\n");
                    stringBuilder.append(noAllDocumentsErrorText);
                    text = stringBuilder;
                    stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteRedText3)), 0, errorText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    errorsValues.put("files_all", "");
                }
            }
            bottomCell.setText(text);
            linearLayout2.addView(bottomCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            if (currentDocumentsType.translation_required) {
                headerCell = new HeaderCell(context);
                headerCell.setText(LocaleController.getString("PassportTranslation", R.string.PassportTranslation));
                headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                linearLayout2.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                translationLayout = new LinearLayout(context);
                translationLayout.setOrientation(LinearLayout.VERTICAL);
                linearLayout2.addView(translationLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                uploadTranslationCell = new TextSettingsCell(context);
                uploadTranslationCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                linearLayout2.addView(uploadTranslationCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                uploadTranslationCell.setOnClickListener(v -> {
                    uploadingFileType = UPLOADING_TYPE_TRANSLATION;
                    openAttachMenu();
                });

                bottomCellTranslation = new TextInfoPrivacyCell(context);
                bottomCellTranslation.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));

                if (currentBotId != 0) {
                    noAllTranslationErrorText = LocaleController.getString("PassportAddTranslationUploadInfo", R.string.PassportAddTranslationUploadInfo);
                } else {
                    if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeRentalAgreement) {
                        noAllTranslationErrorText = LocaleController.getString("PassportAddTranslationAgreementInfo", R.string.PassportAddTranslationAgreementInfo);
                    } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeUtilityBill) {
                        noAllTranslationErrorText = LocaleController.getString("PassportAddTranslationBillInfo", R.string.PassportAddTranslationBillInfo);
                    } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypePassportRegistration) {
                        noAllTranslationErrorText = LocaleController.getString("PassportAddTranslationPassportRegistrationInfo", R.string.PassportAddTranslationPassportRegistrationInfo);
                    } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeTemporaryRegistration) {
                        noAllTranslationErrorText = LocaleController.getString("PassportAddTranslationTemporaryRegistrationInfo", R.string.PassportAddTranslationTemporaryRegistrationInfo);
                    } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeBankStatement) {
                        noAllTranslationErrorText = LocaleController.getString("PassportAddTranslationBankInfo", R.string.PassportAddTranslationBankInfo);
                    } else {
                        noAllTranslationErrorText = "";
                    }
                }

                text = noAllTranslationErrorText;
                if (documentsErrors != null) {
                    String errorText;
                    if ((errorText = documentsErrors.get("translation_all")) != null) {
                        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(errorText);
                        stringBuilder.append("\n\n");
                        stringBuilder.append(noAllTranslationErrorText);
                        text = stringBuilder;
                        stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteRedText3)), 0, errorText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        errorsValues.put("translation_all", "");
                    }
                }
                bottomCellTranslation.setText(text);
                linearLayout2.addView(bottomCellTranslation, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        } else {
            actionBar.setTitle(LocaleController.getString("PassportAddress", R.string.PassportAddress));
        }

        headerCell = new HeaderCell(context);
        headerCell.setText(LocaleController.getString("PassportAddressHeader", R.string.PassportAddressHeader));
        headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout2.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        inputFields = new EditTextBoldCursor[FIELD_ADDRESS_COUNT];
        for (int a = 0; a < FIELD_ADDRESS_COUNT; a++) {
            final EditTextBoldCursor field = new EditTextBoldCursor(context);
            inputFields[a] = field;

            ViewGroup container = new FrameLayout(context) {

                private StaticLayout errorLayout;
                float offsetX;

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int width = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(34);
                    errorLayout = field.getErrorLayout(width);
                    if (errorLayout != null) {
                        int lineCount = errorLayout.getLineCount();
                        if (lineCount > 1) {
                            int height = AndroidUtilities.dp(64) + (errorLayout.getLineBottom(lineCount - 1) - errorLayout.getLineBottom(0));
                            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
                        }
                        if (LocaleController.isRTL) {
                            float maxW = 0;
                            for (int a = 0; a < lineCount; a++) {
                                float l = errorLayout.getLineLeft(a);
                                if (l != 0) {
                                    offsetX = 0;
                                    break;
                                }
                                maxW = Math.max(maxW, errorLayout.getLineWidth(a));
                                if (a == lineCount - 1) {
                                    offsetX = width - maxW;
                                }
                            }
                        }
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    if (errorLayout != null) {
                        canvas.save();
                        canvas.translate(AndroidUtilities.dp(21) + offsetX, field.getLineY() + AndroidUtilities.dp(3));
                        errorLayout.draw(canvas);
                        canvas.restore();
                    }
                }
            };
            container.setWillNotDraw(false);
            linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            if (a == FIELD_ADDRESS_COUNT - 1) {
                extraBackgroundView = new View(context);
                extraBackgroundView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                linearLayout2.addView(extraBackgroundView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 6));
            }

            if (documentOnly && currentDocumentsType != null) {
                container.setVisibility(View.GONE);
                if (extraBackgroundView != null) {
                    extraBackgroundView.setVisibility(View.GONE);
                }
            }

            inputFields[a].setTag(a);
            inputFields[a].setSupportRtlHint(true);
            inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            inputFields[a].setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            inputFields[a].setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            inputFields[a].setTransformHintToHeader(true);
            inputFields[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setBackgroundDrawable(null);
            inputFields[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setCursorSize(AndroidUtilities.dp(20));
            inputFields[a].setCursorWidth(1.5f);
            inputFields[a].setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
            if (a == FIELD_COUNTRY) {
                inputFields[a].setOnTouchListener((v, event) -> {
                    if (getParentActivity() == null) {
                        return false;
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        CountrySelectActivity fragment = new CountrySelectActivity(false);
                        fragment.setCountrySelectActivityDelegate((country) -> {
                            inputFields[FIELD_COUNTRY].setText(country.name);
                            currentCitizeship = country.shortname;
                        });
                        presentFragment(fragment);
                    }
                    return true;
                });
                inputFields[a].setInputType(0);
                inputFields[a].setFocusable(false);
            } else {
                inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                inputFields[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            }
            String value;
            final String key;
            switch (a) {
                case FIELD_STREET1:
                    inputFields[a].setHintText(LocaleController.getString("PassportStreet1", R.string.PassportStreet1));
                    key = "street_line1";
                    break;
                case FIELD_STREET2:
                    inputFields[a].setHintText(LocaleController.getString("PassportStreet2", R.string.PassportStreet2));
                    key = "street_line2";
                    break;
                case FIELD_CITY:
                    inputFields[a].setHintText(LocaleController.getString("PassportCity", R.string.PassportCity));
                    key = "city";
                    break;
                case FIELD_STATE:
                    inputFields[a].setHintText(LocaleController.getString("PassportState", R.string.PassportState));
                    key = "state";
                    break;
                case FIELD_COUNTRY:
                    inputFields[a].setHintText(LocaleController.getString("PassportCountry", R.string.PassportCountry));
                    key = "country_code";
                    break;
                case FIELD_POSTCODE:
                    inputFields[a].setHintText(LocaleController.getString("PassportPostcode", R.string.PassportPostcode));
                    key = "post_code";
                    break;
                default:
                    continue;
            }
            setFieldValues(currentValues, inputFields[a], key);
            if (a == FIELD_POSTCODE) {
                inputFields[a].addTextChangedListener(new TextWatcher() {

                    private boolean ignore;

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (ignore) {
                            return;
                        }
                        ignore = true;
                        boolean error = false;
                        for (int a = 0; a < s.length(); a++) {
                            char ch = s.charAt(a);
                            if (!(ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || ch == '-' || ch == ' ')) {
                                error = true;
                                break;
                            }
                        }
                        ignore = false;
                        if (error) {
                            field.setErrorText(LocaleController.getString("PassportUseLatinOnly", R.string.PassportUseLatinOnly));
                        } else {
                            checkFieldForError(field, key, s, false);
                        }
                    }
                });
                InputFilter[] inputFilters = new InputFilter[1];
                inputFilters[0] = new InputFilter.LengthFilter(10);
                inputFields[a].setFilters(inputFilters);
            } else {
                inputFields[a].addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        checkFieldForError(field, key, s, false);
                    }
                });
            }

            inputFields[a].setSelection(inputFields[a].length());
            inputFields[a].setPadding(0, 0, 0, 0);
            inputFields[a].setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 64, Gravity.LEFT | Gravity.TOP, 21, 0, 21, 0));

            inputFields[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    int num = (Integer) textView.getTag();
                    num++;
                    if (num < inputFields.length) {
                        if (inputFields[num].isFocusable()) {
                            inputFields[num].requestFocus();
                        } else {
                            inputFields[num].dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0, 0, 0));
                            textView.clearFocus();
                            AndroidUtilities.hideKeyboard(textView);
                        }
                    }
                    return true;
                }
                return false;
            });
        }

        sectionCell = new ShadowSectionCell(context);
        linearLayout2.addView(sectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (documentOnly && currentDocumentsType != null) {
            headerCell.setVisibility(View.GONE);
            sectionCell.setVisibility(View.GONE);
        }

        if ((currentBotId != 0 || currentDocumentsType == null) && currentTypeValue != null && !documentOnly || currentDocumentsTypeValue != null) {
            if (currentDocumentsTypeValue != null) {
                addDocumentViews(currentDocumentsTypeValue.files);
                addTranslationDocumentViews(currentDocumentsTypeValue.translation);
            }
            sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));

            TextSettingsCell settingsCell1 = new TextSettingsCell(context);
            settingsCell1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
            settingsCell1.setBackgroundDrawable(Theme.getSelectorDrawable(true));
            if (currentDocumentsType == null) {
                settingsCell1.setText(LocaleController.getString("PassportDeleteInfo", R.string.PassportDeleteInfo), false);
            } else {
                settingsCell1.setText(LocaleController.getString("PassportDeleteDocument", R.string.PassportDeleteDocument), false);
            }
            linearLayout2.addView(settingsCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            settingsCell1.setOnClickListener(v -> createDocumentDeleteAlert());

            sectionCell = new ShadowSectionCell(context);
            sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            linearLayout2.addView(sectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            if (documentOnly && currentDocumentsType != null) {
                bottomCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            }
        }
        updateUploadText(UPLOADING_TYPE_DOCUMENTS);
        updateUploadText(UPLOADING_TYPE_TRANSLATION);
    }

    private void createDocumentDeleteAlert() {
        final boolean[] checks = new boolean[]{true};

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            if (!documentOnly) {
                currentValues.clear();
            }
            currentDocumentValues.clear();
            delegate.deleteValue(currentType, currentDocumentsType, availableDocumentTypes, checks[0], null, null);
            finishFragment();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        if (documentOnly && currentDocumentsType == null && currentType.type instanceof TLRPC.TL_secureValueTypeAddress) {
            builder.setMessage(LocaleController.getString("PassportDeleteAddressAlert", R.string.PassportDeleteAddressAlert));
        } else if (documentOnly && currentDocumentsType == null && currentType.type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
            builder.setMessage(LocaleController.getString("PassportDeletePersonalAlert", R.string.PassportDeletePersonalAlert));
        } else {
            builder.setMessage(LocaleController.getString("PassportDeleteDocumentAlert", R.string.PassportDeleteDocumentAlert));
        }

        if (!documentOnly && currentDocumentsType != null) {
            FrameLayout frameLayout = new FrameLayout(getParentActivity());
            CheckBoxCell cell = new CheckBoxCell(getParentActivity(), 1);
            cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            if (currentType.type instanceof TLRPC.TL_secureValueTypeAddress) {
                cell.setText(LocaleController.getString("PassportDeleteDocumentAddress", R.string.PassportDeleteDocumentAddress), "", true, false);
            } else if (currentType.type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
                cell.setText(LocaleController.getString("PassportDeleteDocumentPersonal", R.string.PassportDeleteDocumentPersonal), "", true, false);
            }
            cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
            frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT));
            cell.setOnClickListener(v -> {
                if (!v.isEnabled()) {
                    return;
                }
                CheckBoxCell cell1 = (CheckBoxCell) v;
                checks[0] = !checks[0];
                cell1.setChecked(checks[0], true);
            });
            builder.setView(frameLayout);
        }

        showDialog(builder.create());
    }

    private void onFieldError(View field) {
        if (field == null) {
            return;
        }
        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(200);
        }
        AndroidUtilities.shakeView(field);
        scrollToField(field);
    }

    private void scrollToField(View field) {
        while (field != null && linearLayout2.indexOfChild(field) < 0) {
            field = (View) field.getParent();
        }
        if (field != null) {
            scrollView.smoothScrollTo(0, field.getTop() - (scrollView.getMeasuredHeight() - field.getMeasuredHeight()) / 2);
        }
    }

    private String getDocumentHash(SecureDocument document) {
        if (document != null) {
            if (document.secureFile != null && document.secureFile.file_hash != null) {
                return Base64.encodeToString(document.secureFile.file_hash, Base64.NO_WRAP);
            } else if (document.fileHash != null) {
                return Base64.encodeToString(document.fileHash, Base64.NO_WRAP);
            }
        }
        return "";
    }

    private void checkFieldForError(EditTextBoldCursor field, String key, Editable s, boolean document) {
        String value;
        if (errorsValues != null && (value = errorsValues.get(key)) != null) {
            if (TextUtils.equals(value, s)) {
                if (fieldsErrors != null && (value = fieldsErrors.get(key)) != null) {
                    field.setErrorText(value);
                } else if (documentsErrors != null && (value = documentsErrors.get(key)) != null) {
                    field.setErrorText(value);
                }
            } else {
                field.setErrorText(null);
            }
        } else {
            field.setErrorText(null);
        }
        String errorKey = document ? "error_document_all" : "error_all";
        if (errorsValues != null && errorsValues.containsKey(errorKey)) {
            errorsValues.remove(errorKey);
            checkTopErrorCell(false);
        }
    }

    private boolean checkFieldsForError() {
        if (currentDocumentsType != null) {
            if (errorsValues.containsKey("error_all") || errorsValues.containsKey("error_document_all")) {
                onFieldError(topErrorCell);
                return true;
            }
            if (uploadDocumentCell != null) {
                if (documents.isEmpty()) {
                    onFieldError(uploadDocumentCell);
                    return true;
                } else {
                    for (int a = 0, size = documents.size(); a < size; a++) {
                        SecureDocument document = documents.get(a);
                        String key = "files" + getDocumentHash(document);
                        if (key != null && errorsValues.containsKey(key)) {
                            onFieldError(documentsCells.get(document));
                            return true;
                        }
                    }
                }
            }
            if (errorsValues.containsKey("files_all") || errorsValues.containsKey("translation_all")) {
                onFieldError(bottomCell);
                return true;
            }
            if (uploadFrontCell != null) {
                if (frontDocument == null) {
                    onFieldError(uploadFrontCell);
                    return true;
                } else {
                    String key = "front" + getDocumentHash(frontDocument);
                    if (errorsValues.containsKey(key)) {
                        onFieldError(documentsCells.get(frontDocument));
                        return true;
                    }
                }
            }
            if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeIdentityCard || currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeDriverLicense) {
                if (uploadReverseCell != null) {
                    if (reverseDocument == null) {
                        onFieldError(uploadReverseCell);
                        return true;
                    } else {
                        String key = "reverse" + getDocumentHash(reverseDocument);
                        if (errorsValues.containsKey(key)) {
                            onFieldError(documentsCells.get(reverseDocument));
                            return true;
                        }
                    }
                }
            }
            if (uploadSelfieCell != null && currentBotId != 0) {
                if (selfieDocument == null) {
                    onFieldError(uploadSelfieCell);
                    return true;
                } else {
                    String key = "selfie" + getDocumentHash(selfieDocument);
                    if (errorsValues.containsKey(key)) {
                        onFieldError(documentsCells.get(selfieDocument));
                        return true;
                    }
                }
            }
            if (uploadTranslationCell != null && currentBotId != 0) {
                if (translationDocuments.isEmpty()) {
                    onFieldError(uploadTranslationCell);
                    return true;
                } else {
                    for (int a = 0, size = translationDocuments.size(); a < size; a++) {
                        SecureDocument document = translationDocuments.get(a);
                        String key = "translation" + getDocumentHash(document);
                        if (errorsValues.containsKey(key)) {
                            onFieldError(documentsCells.get(document));
                            return true;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < 2; i++) {
            EditTextBoldCursor[] fields;
            if (i == 0) {
                fields = inputFields;
            } else {
                fields = nativeInfoCell != null && nativeInfoCell.getVisibility() == View.VISIBLE ? inputExtraFields : null;
            }
            if (fields == null) {
                continue;
            }
            for (int a = 0; a < fields.length; a++) {
                boolean error = false;
                if (fields[a].hasErrorText()) {
                    error = true;
                }
                if (!errorsValues.isEmpty()) {
                    String key;
                    if (currentType.type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
                        if (i == 0) {
                            switch (a) {
                                case FIELD_NAME:
                                    key = "first_name";
                                    break;
                                case FIELD_MIDNAME:
                                    key = "middle_name";
                                    break;
                                case FIELD_SURNAME:
                                    key = "last_name";
                                    break;
                                case FIELD_BIRTHDAY:
                                    key = "birth_date";
                                    break;
                                case FIELD_GENDER:
                                    key = "gender";
                                    break;
                                case FIELD_CITIZENSHIP:
                                    key = "country_code";
                                    break;
                                case FIELD_RESIDENCE:
                                    key = "residence_country_code";
                                    break;
                                case FIELD_CARDNUMBER:
                                    key = "document_no";
                                    break;
                                case FIELD_EXPIRE:
                                    key = "expiry_date";
                                    break;
                                default:
                                    key = null;
                                    break;
                            }
                        } else {
                            switch (a) {
                                case FIELD_NATIVE_NAME:
                                    key = "first_name_native";
                                    break;
                                case FIELD_NATIVE_MIDNAME:
                                    key = "middle_name_native";
                                    break;
                                case FIELD_NATIVE_SURNAME:
                                    key = "last_name_native";
                                    break;
                                default:
                                    key = null;
                                    break;
                            }
                        }
                    } else if (currentType.type instanceof TLRPC.TL_secureValueTypeAddress) {
                        switch (a) {
                            case FIELD_STREET1:
                                key = "street_line1";
                                break;
                            case FIELD_STREET2:
                                key = "street_line2";
                                break;
                            case FIELD_CITY:
                                key = "city";
                                break;
                            case FIELD_STATE:
                                key = "state";
                                break;
                            case FIELD_COUNTRY:
                                key = "country_code";
                                break;
                            case FIELD_POSTCODE:
                                key = "post_code";
                                break;
                            default:
                                key = null;
                                break;
                        }
                    } else {
                        key = null;
                    }
                    if (key != null) {
                        String value = errorsValues.get(key);
                        if (!TextUtils.isEmpty(value)) {
                            if (value.equals(fields[a].getText().toString())) {
                                error = true;
                            }
                        }
                    }
                }
                if (documentOnly) {
                    if (currentDocumentsType != null && a < FIELD_CARDNUMBER) {
                        continue;
                    }
                }
                if (!error) {
                    int len = fields[a].length();
                    boolean allowZeroLength = false;
                    if (currentActivityType == TYPE_IDENTITY) {
                        if (a == FIELD_EXPIRE) {
                            continue;
                        } else if (i == 0 && (a == FIELD_NAME || a == FIELD_SURNAME || a == FIELD_MIDNAME) ||
                                i == 1 && (a == FIELD_NATIVE_NAME || a == FIELD_NATIVE_MIDNAME || a == FIELD_NATIVE_SURNAME)) {
                            if (len > 255) {
                                error = true;
                            }
                            if (i == 0 && a == FIELD_MIDNAME || i == 1 && a == FIELD_NATIVE_MIDNAME) {
                                allowZeroLength = true;
                            }
                        } else if (a == FIELD_CARDNUMBER) {
                            if (len > 24) {
                                error = true;
                            }
                        }
                    } else if (currentActivityType == TYPE_ADDRESS) {
                        if (a == FIELD_STREET2) {
                            continue;
                        } else if (a == FIELD_CITY) {
                            if (len < 2) {
                                error = true;
                            }
                        } else if (a == FIELD_STATE) {
                            if ("US".equals(currentCitizeship)) {
                                if (len < 2) {
                                    error = true;
                                }
                            } else {
                                continue;
                            }
                        } else if (a == FIELD_POSTCODE) {
                            if (len < 2 || len > 10) {
                                error = true;
                            }
                        }
                    }
                    if (!error && !allowZeroLength && len == 0) {
                        error = true;
                    }
                }
                if (error) {
                    onFieldError(fields[a]);
                    return true;
                }
            }
        }
        return false;
    }

    private void createIdentityInterface(final Context context) {
        languageMap = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().getAssets().open("countries.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] args = line.split(";");
                languageMap.put(args[1], args[2]);
            }
            reader.close();
        } catch (Exception e) {
            FileLog.e(e);
        }

        topErrorCell = new TextInfoPrivacyCell(context);
        topErrorCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
        topErrorCell.setPadding(0, AndroidUtilities.dp(7), 0, 0);
        linearLayout2.addView(topErrorCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        checkTopErrorCell(true);

        if (currentDocumentsType != null) {
            headerCell = new HeaderCell(context);
            if (documentOnly) {
                headerCell.setText(LocaleController.getString("PassportDocuments", R.string.PassportDocuments));
            } else {
                headerCell.setText(LocaleController.getString("PassportRequiredDocuments", R.string.PassportRequiredDocuments));
            }
            headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            linearLayout2.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            frontLayout = new LinearLayout(context);
            frontLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout2.addView(frontLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            uploadFrontCell = new TextDetailSettingsCell(context);
            uploadFrontCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
            linearLayout2.addView(uploadFrontCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            uploadFrontCell.setOnClickListener(v -> {
                uploadingFileType = UPLOADING_TYPE_FRONT;
                openAttachMenu();
            });

            reverseLayout = new LinearLayout(context);
            reverseLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout2.addView(reverseLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            boolean divider = currentDocumentsType.selfie_required;

            uploadReverseCell = new TextDetailSettingsCell(context);
            uploadReverseCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
            uploadReverseCell.setTextAndValue(LocaleController.getString("PassportReverseSide", R.string.PassportReverseSide), LocaleController.getString("PassportReverseSideInfo", R.string.PassportReverseSideInfo), divider);
            linearLayout2.addView(uploadReverseCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            uploadReverseCell.setOnClickListener(v -> {
                uploadingFileType = UPLOADING_TYPE_REVERSE;
                openAttachMenu();
            });

            if (currentDocumentsType.selfie_required) {
                selfieLayout = new LinearLayout(context);
                selfieLayout.setOrientation(LinearLayout.VERTICAL);
                linearLayout2.addView(selfieLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                uploadSelfieCell = new TextDetailSettingsCell(context);
                uploadSelfieCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                uploadSelfieCell.setTextAndValue(LocaleController.getString("PassportSelfie", R.string.PassportSelfie), LocaleController.getString("PassportSelfieInfo", R.string.PassportSelfieInfo), currentType.translation_required);
                linearLayout2.addView(uploadSelfieCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                uploadSelfieCell.setOnClickListener(v -> {
                    uploadingFileType = UPLOADING_TYPE_SELFIE;
                    openAttachMenu();
                });
            }

            bottomCell = new TextInfoPrivacyCell(context);
            bottomCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            bottomCell.setText(LocaleController.getString("PassportPersonalUploadInfo", R.string.PassportPersonalUploadInfo));
            linearLayout2.addView(bottomCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            if (currentDocumentsType.translation_required) {
                headerCell = new HeaderCell(context);
                headerCell.setText(LocaleController.getString("PassportTranslation", R.string.PassportTranslation));
                headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                linearLayout2.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                translationLayout = new LinearLayout(context);
                translationLayout.setOrientation(LinearLayout.VERTICAL);
                linearLayout2.addView(translationLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                uploadTranslationCell = new TextSettingsCell(context);
                uploadTranslationCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                linearLayout2.addView(uploadTranslationCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                uploadTranslationCell.setOnClickListener(v -> {
                    uploadingFileType = UPLOADING_TYPE_TRANSLATION;
                    openAttachMenu();
                });

                bottomCellTranslation = new TextInfoPrivacyCell(context);
                bottomCellTranslation.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));

                if (currentBotId != 0) {
                    noAllTranslationErrorText = LocaleController.getString("PassportAddTranslationUploadInfo", R.string.PassportAddTranslationUploadInfo);
                } else {
                    if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypePassport) {
                        noAllTranslationErrorText = LocaleController.getString("PassportAddPassportInfo", R.string.PassportAddPassportInfo);
                    } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeInternalPassport) {
                        noAllTranslationErrorText = LocaleController.getString("PassportAddInternalPassportInfo", R.string.PassportAddInternalPassportInfo);
                    } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeIdentityCard) {
                        noAllTranslationErrorText = LocaleController.getString("PassportAddIdentityCardInfo", R.string.PassportAddIdentityCardInfo);
                    } else if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeDriverLicense) {
                        noAllTranslationErrorText = LocaleController.getString("PassportAddDriverLicenceInfo", R.string.PassportAddDriverLicenceInfo);
                    } else {
                        noAllTranslationErrorText = "";
                    }
                }

                CharSequence text = noAllTranslationErrorText;
                if (documentsErrors != null) {
                    String errorText;
                    if ((errorText = documentsErrors.get("translation_all")) != null) {
                        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(errorText);
                        stringBuilder.append("\n\n");
                        stringBuilder.append(noAllTranslationErrorText);
                        text = stringBuilder;
                        stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteRedText3)), 0, errorText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        errorsValues.put("translation_all", "");
                    }
                }
                bottomCellTranslation.setText(text);
                linearLayout2.addView(bottomCellTranslation, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        } else if (Build.VERSION.SDK_INT >= 18) {
            scanDocumentCell = new TextSettingsCell(context);
            scanDocumentCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
            scanDocumentCell.setText(LocaleController.getString("PassportScanPassport", R.string.PassportScanPassport), false);
            linearLayout2.addView(scanDocumentCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            scanDocumentCell.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 22);
                    return;
                }
                CameraScanActivity fragment = new CameraScanActivity(org.telegram.ui.CameraScanActivity.TYPE_MRZ);
                fragment.setDelegate(new CameraScanActivity.CameraScanActivityDelegate() {
                    @Override
                    public void didFindMrzInfo(MrzRecognizer.Result result) {
                        if (!TextUtils.isEmpty(result.firstName)) {
                            inputFields[FIELD_NAME].setText(result.firstName);
                        }
                        if (!TextUtils.isEmpty(result.middleName)) {
                            inputFields[FIELD_MIDNAME].setText(result.middleName);
                        }
                        if (!TextUtils.isEmpty(result.lastName)) {
                            inputFields[FIELD_SURNAME].setText(result.lastName);
                        }
                        if (result.gender != MrzRecognizer.Result.GENDER_UNKNOWN) {
                            switch (result.gender) {
                                case MrzRecognizer.Result.GENDER_MALE:
                                    currentGender = "male";
                                    inputFields[FIELD_GENDER].setText(LocaleController.getString("PassportMale", R.string.PassportMale));
                                    break;
                                case MrzRecognizer.Result.GENDER_FEMALE:
                                    currentGender = "female";
                                    inputFields[FIELD_GENDER].setText(LocaleController.getString("PassportFemale", R.string.PassportFemale));
                                    break;
                            }
                        }
                        if (!TextUtils.isEmpty(result.nationality)) {
                            currentCitizeship = result.nationality;
                            String country = languageMap.get(currentCitizeship);
                            if (country != null) {
                                inputFields[FIELD_CITIZENSHIP].setText(country);
                            }
                        }
                        if (!TextUtils.isEmpty(result.issuingCountry)) {
                            currentResidence = result.issuingCountry;
                            String country = languageMap.get(currentResidence);
                            if (country != null) {
                                inputFields[FIELD_RESIDENCE].setText(country);
                            }
                        }
                        if (result.birthDay > 0 && result.birthMonth > 0 && result.birthYear > 0) {
                            inputFields[FIELD_BIRTHDAY].setText(String.format(Locale.US, "%02d.%02d.%d", result.birthDay, result.birthMonth, result.birthYear));
                        }
                    }
                });
                presentFragment(fragment);
            });

            bottomCell = new TextInfoPrivacyCell(context);
            bottomCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            bottomCell.setText(LocaleController.getString("PassportScanPassportInfo", R.string.PassportScanPassportInfo));
            linearLayout2.addView(bottomCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        headerCell = new HeaderCell(context);
        if (documentOnly) {
            headerCell.setText(LocaleController.getString("PassportDocument", R.string.PassportDocument));
        } else {
            headerCell.setText(LocaleController.getString("PassportPersonal", R.string.PassportPersonal));
        }
        headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout2.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        int count = currentDocumentsType != null ? FIELD_IDENTITY_COUNT : FIELD_IDENTITY_NODOC_COUNT;
        inputFields = new EditTextBoldCursor[count];

        for (int a = 0; a < count; a++) {
            final EditTextBoldCursor field = new EditTextBoldCursor(context);
            inputFields[a] = field;

            ViewGroup container = new FrameLayout(context) {

                private StaticLayout errorLayout;
                private float offsetX;

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int width = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(34);
                    errorLayout = field.getErrorLayout(width);
                    if (errorLayout != null) {
                        int lineCount = errorLayout.getLineCount();
                        if (lineCount > 1) {
                            int height = AndroidUtilities.dp(64) + (errorLayout.getLineBottom(lineCount - 1) - errorLayout.getLineBottom(0));
                            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
                        }
                        if (LocaleController.isRTL) {
                            float maxW = 0;
                            for (int a = 0; a < lineCount; a++) {
                                float l = errorLayout.getLineLeft(a);
                                if (l != 0) {
                                    offsetX = 0;
                                    break;
                                }
                                maxW = Math.max(maxW, errorLayout.getLineWidth(a));
                                if (a == lineCount - 1) {
                                    offsetX = width - maxW;
                                }
                            }
                        }
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    if (errorLayout != null) {
                        canvas.save();
                        canvas.translate(AndroidUtilities.dp(21) + offsetX, field.getLineY() + AndroidUtilities.dp(3));
                        errorLayout.draw(canvas);
                        canvas.restore();
                    }
                }
            };
            container.setWillNotDraw(false);
            linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 64));
            container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            if (a == count - 1) {
                extraBackgroundView = new View(context);
                extraBackgroundView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                linearLayout2.addView(extraBackgroundView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 6));
            }

            if (documentOnly && currentDocumentsType != null && a < FIELD_CARDNUMBER) {
                container.setVisibility(View.GONE);
                if (extraBackgroundView != null) {
                    extraBackgroundView.setVisibility(View.GONE);
                }
            }

            inputFields[a].setTag(a);
            inputFields[a].setSupportRtlHint(true);
            inputFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            inputFields[a].setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            inputFields[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            inputFields[a].setTransformHintToHeader(true);
            inputFields[a].setBackgroundDrawable(null);
            inputFields[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputFields[a].setCursorSize(AndroidUtilities.dp(20));
            inputFields[a].setCursorWidth(1.5f);
            inputFields[a].setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
            if (a == FIELD_CITIZENSHIP || a == FIELD_RESIDENCE) {
                inputFields[a].setOnTouchListener((v, event) -> {
                    if (getParentActivity() == null) {
                        return false;
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        CountrySelectActivity fragment = new CountrySelectActivity(false);
                        fragment.setCountrySelectActivityDelegate((country) -> {
                            int field12 = (Integer) v.getTag();
                            final EditTextBoldCursor editText = inputFields[field12];
                            if (field12 == FIELD_CITIZENSHIP) {
                                currentCitizeship = country.shortname;
                            } else {
                                currentResidence = country.shortname;
                            }
                            editText.setText(country.name);
                        });
                        presentFragment(fragment);
                    }
                    return true;
                });
                inputFields[a].setInputType(0);
            } else if (a == FIELD_BIRTHDAY || a == FIELD_EXPIRE) {
                inputFields[a].setOnTouchListener((v, event) -> {
                    if (getParentActivity() == null) {
                        return false;
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        Calendar calendar = Calendar.getInstance();
                        int year = calendar.get(Calendar.YEAR);
                        int monthOfYear = calendar.get(Calendar.MONTH);
                        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
                        try {
                            final EditTextBoldCursor field1 = (EditTextBoldCursor) v;
                            int num = (Integer) field1.getTag();
                            int minYear;
                            int maxYear;
                            int currentYearDiff;
                            String title;
                            if (num == FIELD_EXPIRE) {
                                title = LocaleController.getString("PassportSelectExpiredDate", R.string.PassportSelectExpiredDate);
                                minYear = 0;
                                maxYear = 20;
                                currentYearDiff = 0;
                            } else {
                                title = LocaleController.getString("PassportSelectBithdayDate", R.string.PassportSelectBithdayDate);
                                minYear = -120;
                                maxYear = 0;
                                currentYearDiff = -18;
                            }
                            int selectedDay = -1;
                            int selectedMonth = -1;
                            int selectedYear = -1;
                            String[] args = field1.getText().toString().split("\\.");
                            if (args.length == 3) {
                                selectedDay = Utilities.parseInt(args[0]);
                                selectedMonth = Utilities.parseInt(args[1]);
                                selectedYear = Utilities.parseInt(args[2]);
                            }
                            AlertDialog.Builder builder = AlertsCreator.createDatePickerDialog(context, minYear, maxYear, currentYearDiff, selectedDay, selectedMonth, selectedYear, title, num == FIELD_EXPIRE, (year1, month, dayOfMonth1) -> {
                                if (num == FIELD_EXPIRE) {
                                    currentExpireDate[0] = year1;
                                    currentExpireDate[1] = month + 1;
                                    currentExpireDate[2] = dayOfMonth1;
                                }
                                field1.setText(String.format(Locale.US, "%02d.%02d.%d", dayOfMonth1, month + 1, year1));
                            });
                            if (num == FIELD_EXPIRE) {
                                builder.setNegativeButton(LocaleController.getString("PassportSelectNotExpire", R.string.PassportSelectNotExpire), (dialog, which) -> {
                                    currentExpireDate[0] = currentExpireDate[1] = currentExpireDate[2] = 0;
                                    field1.setText(LocaleController.getString("PassportNoExpireDate", R.string.PassportNoExpireDate));
                                });
                            }
                            showDialog(builder.create());
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    return true;
                });
                inputFields[a].setInputType(0);
                inputFields[a].setFocusable(false);
            } else if (a == FIELD_GENDER) {
                inputFields[a].setOnTouchListener((v, event) -> {
                    if (getParentActivity() == null) {
                        return false;
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("PassportSelectGender", R.string.PassportSelectGender));
                        builder.setItems(new CharSequence[]{
                                LocaleController.getString("PassportMale", R.string.PassportMale),
                                LocaleController.getString("PassportFemale", R.string.PassportFemale)
                        }, (dialogInterface, i) -> {
                            if (i == 0) {
                                currentGender = "male";
                                inputFields[FIELD_GENDER].setText(LocaleController.getString("PassportMale", R.string.PassportMale));
                            } else if (i == 1) {
                                currentGender = "female";
                                inputFields[FIELD_GENDER].setText(LocaleController.getString("PassportFemale", R.string.PassportFemale));
                            }
                        });
                        builder.setPositiveButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    }
                    return true;
                });
                inputFields[a].setInputType(0);
                inputFields[a].setFocusable(false);
            } else {
                inputFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                inputFields[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            }
            String value;
            final String key;
            HashMap<String, String> values;
            switch (a) {
                case FIELD_NAME:
                    if (currentType.native_names) {
                        inputFields[a].setHintText(LocaleController.getString("PassportNameLatin", R.string.PassportNameLatin));
                    } else {
                        inputFields[a].setHintText(LocaleController.getString("PassportName", R.string.PassportName));
                    }
                    key = "first_name";
                    values = currentValues;
                    break;
                case FIELD_MIDNAME:
                    if (currentType.native_names) {
                        inputFields[a].setHintText(LocaleController.getString("PassportMidnameLatin", R.string.PassportMidnameLatin));
                    } else {
                        inputFields[a].setHintText(LocaleController.getString("PassportMidname", R.string.PassportMidname));
                    }
                    key = "middle_name";
                    values = currentValues;
                    break;
                case FIELD_SURNAME:
                    if (currentType.native_names) {
                        inputFields[a].setHintText(LocaleController.getString("PassportSurnameLatin", R.string.PassportSurnameLatin));
                    } else {
                        inputFields[a].setHintText(LocaleController.getString("PassportSurname", R.string.PassportSurname));
                    }
                    key = "last_name";
                    values = currentValues;
                    break;
                case FIELD_BIRTHDAY:
                    inputFields[a].setHintText(LocaleController.getString("PassportBirthdate", R.string.PassportBirthdate));
                    key = "birth_date";
                    values = currentValues;
                    break;
                case FIELD_GENDER:
                    inputFields[a].setHintText(LocaleController.getString("PassportGender", R.string.PassportGender));
                    key = "gender";
                    values = currentValues;
                    break;
                case FIELD_CITIZENSHIP:
                    inputFields[a].setHintText(LocaleController.getString("PassportCitizenship", R.string.PassportCitizenship));
                    key = "country_code";
                    values = currentValues;
                    break;
                case FIELD_RESIDENCE:
                    inputFields[a].setHintText(LocaleController.getString("PassportResidence", R.string.PassportResidence));
                    key = "residence_country_code";
                    values = currentValues;
                    break;
                case FIELD_CARDNUMBER:
                    inputFields[a].setHintText(LocaleController.getString("PassportDocumentNumber", R.string.PassportDocumentNumber));
                    key = "document_no";
                    values = currentDocumentValues;
                    break;
                case FIELD_EXPIRE:
                    inputFields[a].setHintText(LocaleController.getString("PassportExpired", R.string.PassportExpired));
                    key = "expiry_date";
                    values = currentDocumentValues;
                    break;
                default:
                    continue;
            }
            setFieldValues(values, inputFields[a], key);
            inputFields[a].setSelection(inputFields[a].length());
            if (a == FIELD_NAME || a == FIELD_SURNAME || a == FIELD_MIDNAME) {
                inputFields[a].addTextChangedListener(new TextWatcher() {

                    private boolean ignore;

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (ignore) {
                            return;
                        }
                        int num = (Integer) field.getTag();
                        boolean error = false;
                        for (int a = 0; a < s.length(); a++) {
                            char ch = s.charAt(a);
                            if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == ' ' || ch == '\'' || ch == ',' || ch == '.' || ch == '&' || ch == '-' || ch == '/')) {
                                error = true;
                                break;
                            }
                        }
                        if (error && !allowNonLatinName) {
                            field.setErrorText(LocaleController.getString("PassportUseLatinOnly", R.string.PassportUseLatinOnly));
                        } else {
                            nonLatinNames[num] = error;
                            checkFieldForError(field, key, s, false);
                        }
                    }
                });
            } else {
                inputFields[a].addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        checkFieldForError(field, key, s, values == currentDocumentValues);
                        int field12 = (Integer) field.getTag();
                        final EditTextBoldCursor editText = inputFields[field12];
                        if (field12 == FIELD_RESIDENCE) {
                            checkNativeFields(true);
                        }
                    }
                });
            }

            inputFields[a].setPadding(0, 0, 0, 0);
            inputFields[a].setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            container.addView(inputFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 21, 0, 21, 0));

            inputFields[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    int num = (Integer) textView.getTag();
                    num++;
                    if (num < inputFields.length) {
                        if (inputFields[num].isFocusable()) {
                            inputFields[num].requestFocus();
                        } else {
                            inputFields[num].dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0, 0, 0));
                            textView.clearFocus();
                            AndroidUtilities.hideKeyboard(textView);
                        }
                    }
                    return true;
                }
                return false;
            });
        }

        sectionCell2 = new ShadowSectionCell(context);
        linearLayout2.addView(sectionCell2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        headerCell = new HeaderCell(context);
        headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout2.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        inputExtraFields = new EditTextBoldCursor[FIELD_NATIVE_COUNT];
        for (int a = 0; a < FIELD_NATIVE_COUNT; a++) {
            final EditTextBoldCursor field = new EditTextBoldCursor(context);
            inputExtraFields[a] = field;

            ViewGroup container = new FrameLayout(context) {

                private StaticLayout errorLayout;
                private float offsetX;

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int width = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(34);
                    errorLayout = field.getErrorLayout(width);
                    if (errorLayout != null) {
                        int lineCount = errorLayout.getLineCount();
                        if (lineCount > 1) {
                            int height = AndroidUtilities.dp(64) + (errorLayout.getLineBottom(lineCount - 1) - errorLayout.getLineBottom(0));
                            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
                        }
                        if (LocaleController.isRTL) {
                            float maxW = 0;
                            for (int a = 0; a < lineCount; a++) {
                                float l = errorLayout.getLineLeft(a);
                                if (l != 0) {
                                    offsetX = 0;
                                    break;
                                }
                                maxW = Math.max(maxW, errorLayout.getLineWidth(a));
                                if (a == lineCount - 1) {
                                    offsetX = width - maxW;
                                }
                            }
                        }
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    if (errorLayout != null) {
                        canvas.save();
                        canvas.translate(AndroidUtilities.dp(21) + offsetX, field.getLineY() + AndroidUtilities.dp(3));
                        errorLayout.draw(canvas);
                        canvas.restore();
                    }
                }
            };
            container.setWillNotDraw(false);
            linearLayout2.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 64));
            container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            if (a == FIELD_NATIVE_COUNT - 1) {
                extraBackgroundView2 = new View(context);
                extraBackgroundView2.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                linearLayout2.addView(extraBackgroundView2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 6));
            }

            inputExtraFields[a].setTag(a);
            inputExtraFields[a].setSupportRtlHint(true);
            inputExtraFields[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            inputExtraFields[a].setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            inputExtraFields[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputExtraFields[a].setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            inputExtraFields[a].setTransformHintToHeader(true);
            inputExtraFields[a].setBackgroundDrawable(null);
            inputExtraFields[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            inputExtraFields[a].setCursorSize(AndroidUtilities.dp(20));
            inputExtraFields[a].setCursorWidth(1.5f);
            inputExtraFields[a].setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
            inputExtraFields[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            inputExtraFields[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);

            String value;
            final String key;
            HashMap<String, String> values;
            switch (a) {
                case FIELD_NATIVE_NAME:
                    key = "first_name_native";
                    values = currentValues;
                    break;
                case FIELD_NATIVE_MIDNAME:
                    key = "middle_name_native";
                    values = currentValues;
                    break;
                case FIELD_NATIVE_SURNAME:
                    key = "last_name_native";
                    values = currentValues;
                    break;
                default:
                    continue;
            }
            setFieldValues(values, inputExtraFields[a], key);
            inputExtraFields[a].setSelection(inputExtraFields[a].length());
            if (a == FIELD_NATIVE_NAME || a == FIELD_NATIVE_SURNAME || a == FIELD_NATIVE_MIDNAME) {
                inputExtraFields[a].addTextChangedListener(new TextWatcher() {

                    private boolean ignore;

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (ignore) {
                            return;
                        }
                        checkFieldForError(field, key, s, false);
                    }
                });
            }

            inputExtraFields[a].setPadding(0, 0, 0, 0);
            inputExtraFields[a].setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            container.addView(inputExtraFields[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 21, 0, 21, 0));

            inputExtraFields[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    int num = (Integer) textView.getTag();
                    num++;
                    if (num < inputExtraFields.length) {
                        if (inputExtraFields[num].isFocusable()) {
                            inputExtraFields[num].requestFocus();
                        } else {
                            inputExtraFields[num].dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0, 0, 0));
                            textView.clearFocus();
                            AndroidUtilities.hideKeyboard(textView);
                        }
                    }
                    return true;
                }
                return false;
            });
        }

        nativeInfoCell = new TextInfoPrivacyCell(context);
        linearLayout2.addView(nativeInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if ((currentBotId != 0 || currentDocumentsType == null) && currentTypeValue != null && !documentOnly || currentDocumentsTypeValue != null) {
            if (currentDocumentsTypeValue != null) {
                addDocumentViews(currentDocumentsTypeValue.files);
                if (currentDocumentsTypeValue.front_side instanceof TLRPC.TL_secureFile) {
                    addDocumentViewInternal((TLRPC.TL_secureFile) currentDocumentsTypeValue.front_side, UPLOADING_TYPE_FRONT);
                }
                if (currentDocumentsTypeValue.reverse_side instanceof TLRPC.TL_secureFile) {
                    addDocumentViewInternal((TLRPC.TL_secureFile) currentDocumentsTypeValue.reverse_side, UPLOADING_TYPE_REVERSE);
                }
                if (currentDocumentsTypeValue.selfie instanceof TLRPC.TL_secureFile) {
                    addDocumentViewInternal((TLRPC.TL_secureFile) currentDocumentsTypeValue.selfie, UPLOADING_TYPE_SELFIE);
                }
                addTranslationDocumentViews(currentDocumentsTypeValue.translation);
            }

            TextSettingsCell settingsCell1 = new TextSettingsCell(context);
            settingsCell1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
            settingsCell1.setBackgroundDrawable(Theme.getSelectorDrawable(true));
            if (currentDocumentsType == null) {
                settingsCell1.setText(LocaleController.getString("PassportDeleteInfo", R.string.PassportDeleteInfo), false);
            } else {
                settingsCell1.setText(LocaleController.getString("PassportDeleteDocument", R.string.PassportDeleteDocument), false);
            }
            linearLayout2.addView(settingsCell1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            settingsCell1.setOnClickListener(v -> createDocumentDeleteAlert());

            nativeInfoCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));

            sectionCell = new ShadowSectionCell(context);
            sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            linearLayout2.addView(sectionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            nativeInfoCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        }

        updateInterfaceStringsForDocumentType();
        checkNativeFields(false);
    }

    private void updateInterfaceStringsForDocumentType() {
        if (currentDocumentsType != null) {
            actionBar.setTitle(getTextForType(currentDocumentsType.type));
        } else {
            actionBar.setTitle(LocaleController.getString("PassportPersonal", R.string.PassportPersonal));
        }
        updateUploadText(UPLOADING_TYPE_FRONT);
        updateUploadText(UPLOADING_TYPE_REVERSE);
        updateUploadText(UPLOADING_TYPE_SELFIE);
        updateUploadText(UPLOADING_TYPE_TRANSLATION);
    }

    private void updateUploadText(int type) {
        if (type == UPLOADING_TYPE_DOCUMENTS) {
            if (uploadDocumentCell == null) {
                return;
            }
            if (documents.size() >= 1) {
                uploadDocumentCell.setText(LocaleController.getString("PassportUploadAdditinalDocument", R.string.PassportUploadAdditinalDocument), false);
            } else {
                uploadDocumentCell.setText(LocaleController.getString("PassportUploadDocument", R.string.PassportUploadDocument), false);
            }
        } else if (type == UPLOADING_TYPE_SELFIE) {
            if (uploadSelfieCell == null) {
                return;
            }
            uploadSelfieCell.setVisibility(selfieDocument != null ? View.GONE : View.VISIBLE);
        } else if (type == UPLOADING_TYPE_TRANSLATION) {
            if (uploadTranslationCell == null) {
                return;
            }
            if (translationDocuments.size() >= 1) {
                uploadTranslationCell.setText(LocaleController.getString("PassportUploadAdditinalDocument", R.string.PassportUploadAdditinalDocument), false);
            } else {
                uploadTranslationCell.setText(LocaleController.getString("PassportUploadDocument", R.string.PassportUploadDocument), false);
            }
        } else if (type == UPLOADING_TYPE_FRONT) {
            if (uploadFrontCell == null) {
                return;
            }
            boolean divider = currentDocumentsType != null && (
                    currentDocumentsType.selfie_required ||
                            currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeIdentityCard ||
                            currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeDriverLicense);
            if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypePassport || currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeInternalPassport) {
                uploadFrontCell.setTextAndValue(LocaleController.getString("PassportMainPage", R.string.PassportMainPage), LocaleController.getString("PassportMainPageInfo", R.string.PassportMainPageInfo), divider);
            } else {
                uploadFrontCell.setTextAndValue(LocaleController.getString("PassportFrontSide", R.string.PassportFrontSide), LocaleController.getString("PassportFrontSideInfo", R.string.PassportFrontSideInfo), divider);
            }
            uploadFrontCell.setVisibility(frontDocument != null ? View.GONE : View.VISIBLE);
        } else if (type == UPLOADING_TYPE_REVERSE) {
            if (uploadReverseCell == null) {
                return;
            }
            if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeIdentityCard || currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeDriverLicense) {
                reverseLayout.setVisibility(View.VISIBLE);
                uploadReverseCell.setVisibility(reverseDocument != null ? View.GONE : View.VISIBLE);
            } else {
                reverseLayout.setVisibility(View.GONE);
                uploadReverseCell.setVisibility(View.GONE);
            }
        }
    }

    private void checkTopErrorCell(boolean init) {
        if (topErrorCell == null) {
            return;
        }
        SpannableStringBuilder stringBuilder = null;
        if (fieldsErrors != null && (init || errorsValues.containsKey("error_all"))) {
            String errorText = fieldsErrors.get("error_all");
            if (errorText != null) {
                stringBuilder = new SpannableStringBuilder(errorText);
                if (init) {
                    errorsValues.put("error_all", "");
                }
            }
        }
        if (documentsErrors != null && (init || errorsValues.containsKey("error_document_all"))) {
            String errorText = documentsErrors.get("error_all");
            if (errorText != null) {
                if (stringBuilder == null) {
                    stringBuilder = new SpannableStringBuilder(errorText);
                } else {
                    stringBuilder.append("\n\n").append(errorText);
                }
                if (init) {
                    errorsValues.put("error_document_all", "");
                }
            }
        }
        if (stringBuilder != null) {
            stringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteRedText3)), 0, stringBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            topErrorCell.setText(stringBuilder);
            topErrorCell.setVisibility(View.VISIBLE);
        } else if (topErrorCell.getVisibility() != View.GONE) {
            topErrorCell.setVisibility(View.GONE);
        }
    }

    private void addDocumentViewInternal(TLRPC.TL_secureFile f, int uploadingType) {
        SecureDocumentKey secureDocumentKey = getSecureDocumentKey(f.secret, f.file_hash);
        SecureDocument secureDocument = new SecureDocument(secureDocumentKey, f, null, null, null);
        addDocumentView(secureDocument, uploadingType);
    }

    private void addDocumentViews(ArrayList<TLRPC.SecureFile> files) {
        documents.clear();
        for (int a = 0, size = files.size(); a < size; a++) {
            TLRPC.SecureFile secureFile = files.get(a);
            if (secureFile instanceof TLRPC.TL_secureFile) {
                addDocumentViewInternal((TLRPC.TL_secureFile) secureFile, UPLOADING_TYPE_DOCUMENTS);
            }
        }
    }

    private void addTranslationDocumentViews(ArrayList<TLRPC.SecureFile> files) {
        translationDocuments.clear();
        for (int a = 0, size = files.size(); a < size; a++) {
            TLRPC.SecureFile secureFile = files.get(a);
            if (secureFile instanceof TLRPC.TL_secureFile) {
                addDocumentViewInternal((TLRPC.TL_secureFile) secureFile, UPLOADING_TYPE_TRANSLATION);
            }
        }
    }

    private void setFieldValues(HashMap<String, String> values, EditTextBoldCursor editText, String key) {
        String value;
        if ((value = values.get(key)) != null) {
            switch (key) {
                case "country_code": {
                    currentCitizeship = value;
                    String country = languageMap.get(currentCitizeship);
                    if (country != null) {
                        editText.setText(country);
                    }
                    break;
                }
                case "residence_country_code": {
                    currentResidence = value;
                    String country = languageMap.get(currentResidence);
                    if (country != null) {
                        editText.setText(country);
                    }
                    break;
                }
                case "gender":
                    if ("male".equals(value)) {
                        currentGender = value;
                        editText.setText(LocaleController.getString("PassportMale", R.string.PassportMale));
                    } else if ("female".equals(value)) {
                        currentGender = value;
                        editText.setText(LocaleController.getString("PassportFemale", R.string.PassportFemale));
                    }
                    break;
                case "expiry_date":
                    boolean ok = false;
                    if (!TextUtils.isEmpty(value)) {
                        String[] args = value.split("\\.");
                        if (args.length == 3) {
                            currentExpireDate[0] = Utilities.parseInt(args[2]);
                            currentExpireDate[1] = Utilities.parseInt(args[1]);
                            currentExpireDate[2] = Utilities.parseInt(args[0]);
                            editText.setText(value);
                            ok = true;
                        }
                    }
                    if (!ok) {
                        currentExpireDate[0] = currentExpireDate[1] = currentExpireDate[2] = 0;
                        editText.setText(LocaleController.getString("PassportNoExpireDate", R.string.PassportNoExpireDate));
                    }
                    break;
                default:
                    editText.setText(value);
                    break;
            }
        }
        if (fieldsErrors != null && (value = fieldsErrors.get(key)) != null) {
            editText.setErrorText(value);
            errorsValues.put(key, editText.getText().toString());
        } else if (documentsErrors != null && (value = documentsErrors.get(key)) != null) {
            editText.setErrorText(value);
            errorsValues.put(key, editText.getText().toString());
        }
    }

    private void addDocumentView(final SecureDocument document, final int type) {
        if (type == UPLOADING_TYPE_SELFIE) {
            selfieDocument = document;
            if (selfieLayout == null) {
                return;
            }
        } else if (type == UPLOADING_TYPE_TRANSLATION) {
            translationDocuments.add(document);
            if (translationLayout == null) {
                return;
            }
        } else if (type == UPLOADING_TYPE_FRONT) {
            frontDocument = document;
            if (frontLayout == null) {
                return;
            }
        } else if (type == UPLOADING_TYPE_REVERSE) {
            reverseDocument = document;
            if (reverseLayout == null) {
                return;
            }
        } else {
            documents.add(document);
            if (documentsLayout == null) {
                return;
            }
        }
        if (getParentActivity() == null) {
            return;
        }
        final SecureDocumentCell cell = new SecureDocumentCell(getParentActivity());

        String value;
        final String key;

        cell.setTag(document);
        cell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
        String text;
        documentsCells.put(document, cell);
        String hash = getDocumentHash(document);
        if (type == UPLOADING_TYPE_SELFIE) {
            text = LocaleController.getString("PassportSelfie", R.string.PassportSelfie);
            selfieLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            key = "selfie" + hash;
        } else if (type == UPLOADING_TYPE_TRANSLATION) {
            text = LocaleController.getString("AttachPhoto", R.string.AttachPhoto);
            translationLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            key = "translation" + hash;
        } else if (type == UPLOADING_TYPE_FRONT) {
            if (currentDocumentsType.type instanceof TLRPC.TL_secureValueTypePassport || currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeInternalPassport) {
                text = LocaleController.getString("PassportMainPage", R.string.PassportMainPage);
            } else {
                text = LocaleController.getString("PassportFrontSide", R.string.PassportFrontSide);
            }
            frontLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            key = "front" + hash;
        } else if (type == UPLOADING_TYPE_REVERSE) {
            text = LocaleController.getString("PassportReverseSide", R.string.PassportReverseSide);
            reverseLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            key = "reverse" + hash;
        } else {
            text = LocaleController.getString("AttachPhoto", R.string.AttachPhoto);
            documentsLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            key = "files" + hash;
        }

        if (key == null || documentsErrors == null || (value = documentsErrors.get(key)) == null) {
            value = LocaleController.formatDateForBan(document.secureFile.date);
        } else {
            cell.valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
            errorsValues.put(key, "");
        }

        cell.setTextAndValueAndImage(text, value, document);
        cell.setOnClickListener(v -> {
            uploadingFileType = type;
            if (type == UPLOADING_TYPE_SELFIE) {
                currentPhotoViewerLayout = selfieLayout;
            } else if (type == UPLOADING_TYPE_TRANSLATION) {
                currentPhotoViewerLayout = translationLayout;
            } else if (type == UPLOADING_TYPE_FRONT) {
                currentPhotoViewerLayout = frontLayout;
            } else if (type == UPLOADING_TYPE_REVERSE) {
                currentPhotoViewerLayout = reverseLayout;
            } else {
                currentPhotoViewerLayout = documentsLayout;
            }
            SecureDocument document1 = (SecureDocument) v.getTag();
            PhotoViewer.getInstance().setParentActivity(PassportActivity.this);
            if (type == UPLOADING_TYPE_SELFIE) {
                ArrayList<SecureDocument> arrayList = new ArrayList<>();
                arrayList.add(selfieDocument);
                PhotoViewer.getInstance().openPhoto(arrayList, 0, provider);
            } else if (type == UPLOADING_TYPE_FRONT) {
                ArrayList<SecureDocument> arrayList = new ArrayList<>();
                arrayList.add(frontDocument);
                PhotoViewer.getInstance().openPhoto(arrayList, 0, provider);
            } else if (type == UPLOADING_TYPE_REVERSE) {
                ArrayList<SecureDocument> arrayList = new ArrayList<>();
                arrayList.add(reverseDocument);
                PhotoViewer.getInstance().openPhoto(arrayList, 0, provider);
            } else if (type == UPLOADING_TYPE_DOCUMENTS) {
                PhotoViewer.getInstance().openPhoto(documents, documents.indexOf(document1), provider);
            } else {
                PhotoViewer.getInstance().openPhoto(translationDocuments, translationDocuments.indexOf(document1), provider);
            }
        });
        cell.setOnLongClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            if (type == UPLOADING_TYPE_SELFIE) {
                builder.setMessage(LocaleController.getString("PassportDeleteSelfie", R.string.PassportDeleteSelfie));
            } else {
                builder.setMessage(LocaleController.getString("PassportDeleteScan", R.string.PassportDeleteScan));
            }
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
                documentsCells.remove(document);
                if (type == UPLOADING_TYPE_SELFIE) {
                    selfieDocument = null;
                    selfieLayout.removeView(cell);
                } else if (type == UPLOADING_TYPE_TRANSLATION) {
                    translationDocuments.remove(document);
                    translationLayout.removeView(cell);
                } else if (type == UPLOADING_TYPE_FRONT) {
                    frontDocument = null;
                    frontLayout.removeView(cell);
                } else if (type == UPLOADING_TYPE_REVERSE) {
                    reverseDocument = null;
                    reverseLayout.removeView(cell);
                } else {
                    documents.remove(document);
                    documentsLayout.removeView(cell);
                }

                if (key != null) {
                    if (documentsErrors != null) {
                        documentsErrors.remove(key);
                    }
                    if (errorsValues != null) {
                        errorsValues.remove(key);
                    }
                }

                updateUploadText(type);
                if (document.path != null && uploadingDocuments.remove(document.path) != null) {
                    if (uploadingDocuments.isEmpty()) {
                        doneItem.setEnabled(true);
                        doneItem.setAlpha(1.0f);
                    }
                    FileLoader.getInstance(currentAccount).cancelFileUpload(document.path, false);
                }
            });
            showDialog(builder.create());
            return true;
        });
    }

    private String getNameForType(TLRPC.SecureValueType type) {
        if (type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
            return "personal_details";
        } else if (type instanceof TLRPC.TL_secureValueTypePassport) {
            return "passport";
        } else if (type instanceof TLRPC.TL_secureValueTypeInternalPassport) {
            return "internal_passport";
        } else if (type instanceof TLRPC.TL_secureValueTypeDriverLicense) {
            return "driver_license";
        } else if (type instanceof TLRPC.TL_secureValueTypeIdentityCard) {
            return "identity_card";
        } else if (type instanceof TLRPC.TL_secureValueTypeUtilityBill) {
            return "utility_bill";
        } else if (type instanceof TLRPC.TL_secureValueTypeAddress) {
            return "address";
        } else if (type instanceof TLRPC.TL_secureValueTypeBankStatement) {
            return "bank_statement";
        } else if (type instanceof TLRPC.TL_secureValueTypeRentalAgreement) {
            return "rental_agreement";
        } else if (type instanceof TLRPC.TL_secureValueTypeTemporaryRegistration) {
            return "temporary_registration";
        } else if (type instanceof TLRPC.TL_secureValueTypePassportRegistration) {
            return "passport_registration";
        } else if (type instanceof TLRPC.TL_secureValueTypeEmail) {
            return "email";
        } else if (type instanceof TLRPC.TL_secureValueTypePhone) {
            return "phone";
        }
        return "";
    }

    private TextDetailSecureCell getViewByType(TLRPC.TL_secureRequiredType requiredType) {
        TextDetailSecureCell view = typesViews.get(requiredType);
        if (view == null) {
            requiredType = documentsToTypesLink.get(requiredType);
            if (requiredType != null) {
                view = typesViews.get(requiredType);
            }
        }
        return view;
    }

    private String getTextForType(TLRPC.SecureValueType type) {
        if (type instanceof TLRPC.TL_secureValueTypePassport) {
            return LocaleController.getString("ActionBotDocumentPassport", R.string.ActionBotDocumentPassport);
        } else if (type instanceof TLRPC.TL_secureValueTypeDriverLicense) {
            return LocaleController.getString("ActionBotDocumentDriverLicence", R.string.ActionBotDocumentDriverLicence);
        } else if (type instanceof TLRPC.TL_secureValueTypeIdentityCard) {
            return LocaleController.getString("ActionBotDocumentIdentityCard", R.string.ActionBotDocumentIdentityCard);
        } else if (type instanceof TLRPC.TL_secureValueTypeUtilityBill) {
            return LocaleController.getString("ActionBotDocumentUtilityBill", R.string.ActionBotDocumentUtilityBill);
        } else if (type instanceof TLRPC.TL_secureValueTypeBankStatement) {
            return LocaleController.getString("ActionBotDocumentBankStatement", R.string.ActionBotDocumentBankStatement);
        } else if (type instanceof TLRPC.TL_secureValueTypeRentalAgreement) {
            return LocaleController.getString("ActionBotDocumentRentalAgreement", R.string.ActionBotDocumentRentalAgreement);
        } else if (type instanceof TLRPC.TL_secureValueTypeInternalPassport) {
            return LocaleController.getString("ActionBotDocumentInternalPassport", R.string.ActionBotDocumentInternalPassport);
        } else if (type instanceof TLRPC.TL_secureValueTypePassportRegistration) {
            return LocaleController.getString("ActionBotDocumentPassportRegistration", R.string.ActionBotDocumentPassportRegistration);
        } else if (type instanceof TLRPC.TL_secureValueTypeTemporaryRegistration) {
            return LocaleController.getString("ActionBotDocumentTemporaryRegistration", R.string.ActionBotDocumentTemporaryRegistration);
        } else if (type instanceof TLRPC.TL_secureValueTypePhone) {
            return LocaleController.getString("ActionBotDocumentPhone", R.string.ActionBotDocumentPhone);
        } else if (type instanceof TLRPC.TL_secureValueTypeEmail) {
            return LocaleController.getString("ActionBotDocumentEmail", R.string.ActionBotDocumentEmail);
        }
        return "";
    }

    private void setTypeValue(TLRPC.TL_secureRequiredType requiredType, String text, String json, TLRPC.TL_secureRequiredType documentRequiredType, String documentsJson, boolean documentOnly, int availableDocumentTypesCount) {
        TextDetailSecureCell view = typesViews.get(requiredType);
        if (view == null) {
            if (currentActivityType == TYPE_MANAGE) {
                ArrayList<TLRPC.TL_secureRequiredType> documentTypes = new ArrayList<>();
                if (documentRequiredType != null) {
                    documentTypes.add(documentRequiredType);
                }
                View prev = linearLayout2.getChildAt(linearLayout2.getChildCount() - 6);
                if (prev instanceof TextDetailSecureCell) {
                    ((TextDetailSecureCell) prev).setNeedDivider(true);
                }
                view = addField(getParentActivity(), requiredType, documentTypes, true, true);
                updateManageVisibility();
            } else {
                return;
            }
        }
        HashMap<String, String> values = typesValues.get(requiredType);
        HashMap<String, String> documentValues = documentRequiredType != null ? typesValues.get(documentRequiredType) : null;
        TLRPC.TL_secureValue requiredTypeValue = getValueByType(requiredType, true);
        TLRPC.TL_secureValue documentRequiredTypeValue = getValueByType(documentRequiredType, true);

        if (json != null && languageMap == null) {
            languageMap = new HashMap<>();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(ApplicationLoader.applicationContext.getResources().getAssets().open("countries.txt")));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] args = line.split(";");
                    languageMap.put(args[1], args[2]);
                }
                reader.close();
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            languageMap = null;
        }

        String value = null;
        if (text != null) {
            if (requiredType.type instanceof TLRPC.TL_secureValueTypePhone) {
                value = PhoneFormat.getInstance().format("+" + text);
            } else if (requiredType.type instanceof TLRPC.TL_secureValueTypeEmail) {
                value = text;
            }
        } else {
            StringBuilder stringBuilder = null;
            if (currentActivityType != TYPE_MANAGE && documentRequiredType != null && (!TextUtils.isEmpty(documentsJson) || documentRequiredTypeValue != null)) {
                if (stringBuilder == null) {
                    stringBuilder = new StringBuilder();
                }
                if (availableDocumentTypesCount > 1) {
                    stringBuilder.append(getTextForType(documentRequiredType.type));
                } else if (TextUtils.isEmpty(documentsJson)) {
                    stringBuilder.append(LocaleController.getString("PassportDocuments", R.string.PassportDocuments));
                }
            }
            if (json != null || documentsJson != null) {
                if (values == null) {
                    return;
                }
                values.clear();
                String[] keys = null;
                String[] documentKeys = null;
                if (requiredType.type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
                    if (currentActivityType == TYPE_REQUEST && !documentOnly || currentActivityType == TYPE_MANAGE && documentRequiredType == null) {
                        keys = new String[]{
                                "first_name",
                                "middle_name",
                                "last_name",
                                "first_name_native",
                                "middle_name_native",
                                "last_name_native",
                                "birth_date",
                                "gender",
                                "country_code",
                                "residence_country_code"
                        };
                    }
                    if (currentActivityType == TYPE_REQUEST || currentActivityType == TYPE_MANAGE && documentRequiredType != null) {
                        documentKeys = new String[]{
                                "document_no",
                                "expiry_date"
                        };
                    }
                } else if (requiredType.type instanceof TLRPC.TL_secureValueTypeAddress) {
                    if (currentActivityType == TYPE_REQUEST && !documentOnly || currentActivityType == TYPE_MANAGE && documentRequiredType == null) {
                        keys = new String[]{
                                "street_line1",
                                "street_line2",
                                "post_code",
                                "city",
                                "state",
                                "country_code"
                        };
                    }
                }
                if (keys != null || documentKeys != null) {
                    try {
                        JSONObject jsonObject = null;
                        String[] currentKeys = null;
                        for (int b = 0; b < 2; b++) {
                            if (b == 0) {
                                if (json != null) {
                                    jsonObject = new JSONObject(json);
                                    currentKeys = keys;
                                }
                            } else {
                                if (documentValues == null) {
                                    continue;
                                }
                                if (documentsJson != null) {
                                    jsonObject = new JSONObject(documentsJson);
                                    currentKeys = documentKeys;
                                }
                            }
                            if (currentKeys == null || jsonObject == null) {
                                continue;
                            }
                            try {
                                Iterator<String> iter = jsonObject.keys();
                                while (iter.hasNext()) {
                                    String key = iter.next();
                                    if (b == 0) {
                                        values.put(key, jsonObject.getString(key));
                                    } else {
                                        documentValues.put(key, jsonObject.getString(key));
                                    }
                                }
                            } catch (Throwable e) {
                                FileLog.e(e);
                            }

                            for (int a = 0; a < currentKeys.length; a++) {
                                if (jsonObject.has(currentKeys[a])) {
                                    if (stringBuilder == null) {
                                        stringBuilder = new StringBuilder();
                                    }
                                    String jsonValue = jsonObject.getString(currentKeys[a]);
                                    if (jsonValue != null) {
                                        if (!TextUtils.isEmpty(jsonValue)) {
                                            if ("first_name_native".equals(currentKeys[a]) ||
                                                    "middle_name_native".equals(currentKeys[a]) ||
                                                    "last_name_native".equals(currentKeys[a])) {
                                                continue;
                                            }
                                            if (stringBuilder.length() > 0) {
                                                if ("last_name".equals(currentKeys[a]) || "last_name_native".equals(currentKeys[a]) || "middle_name".equals(currentKeys[a]) || "middle_name_native".equals(currentKeys[a])) {
                                                    stringBuilder.append(" ");
                                                } else {
                                                    stringBuilder.append(", ");
                                                }
                                            }
                                            switch (currentKeys[a]) {
                                                case "country_code":
                                                case "residence_country_code":
                                                    String country = languageMap.get(jsonValue);
                                                    if (country != null) {
                                                        stringBuilder.append(country);
                                                    }
                                                    break;
                                                case "gender":
                                                    if ("male".equals(jsonValue)) {
                                                        stringBuilder.append(LocaleController.getString("PassportMale", R.string.PassportMale));
                                                    } else if ("female".equals(jsonValue)) {
                                                        stringBuilder.append(LocaleController.getString("PassportFemale", R.string.PassportFemale));
                                                    }
                                                    break;
                                                default:
                                                    stringBuilder.append(jsonValue);
                                                    break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ignore) {

                    }
                }
            }
            if (stringBuilder != null) {
                value = stringBuilder.toString();
            }
        }

        boolean isError = false;
        HashMap<String, String> errors = !documentOnly ? errorsMap.get(getNameForType(requiredType.type)) : null;
        HashMap<String, String> documentsErrors = documentRequiredType != null ? errorsMap.get(getNameForType(documentRequiredType.type)) : null;
        if (errors != null && errors.size() > 0 || documentsErrors != null && documentsErrors.size() > 0) {
            value = null;
            if (!documentOnly) {
                value = mainErrorsMap.get(getNameForType(requiredType.type));
            }
            if (value == null) {
                value = mainErrorsMap.get(getNameForType(documentRequiredType.type));
            }
            isError = true;
        } else {
            if (requiredType.type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
                if (TextUtils.isEmpty(value)) {
                    if (documentRequiredType == null) {
                        value = LocaleController.getString("PassportPersonalDetailsInfo", R.string.PassportPersonalDetailsInfo);
                    } else {
                        if (currentActivityType == TYPE_MANAGE) {
                            value = LocaleController.getString("PassportDocuments", R.string.PassportDocuments);
                        } else {
                            if (availableDocumentTypesCount == 1) {
                                if (documentRequiredType.type instanceof TLRPC.TL_secureValueTypePassport) {
                                    value = LocaleController.getString("PassportIdentityPassport", R.string.PassportIdentityPassport);
                                } else if (documentRequiredType.type instanceof TLRPC.TL_secureValueTypeInternalPassport) {
                                    value = LocaleController.getString("PassportIdentityInternalPassport", R.string.PassportIdentityInternalPassport);
                                } else if (documentRequiredType.type instanceof TLRPC.TL_secureValueTypeDriverLicense) {
                                    value = LocaleController.getString("PassportIdentityDriverLicence", R.string.PassportIdentityDriverLicence);
                                } else if (documentRequiredType.type instanceof TLRPC.TL_secureValueTypeIdentityCard) {
                                    value = LocaleController.getString("PassportIdentityID", R.string.PassportIdentityID);
                                }
                            } else {
                                value = LocaleController.getString("PassportIdentityDocumentInfo", R.string.PassportIdentityDocumentInfo);
                            }
                        }
                    }
                }
            } else if (requiredType.type instanceof TLRPC.TL_secureValueTypeAddress) {
                if (TextUtils.isEmpty(value)) {
                    if (documentRequiredType == null) {
                        value = LocaleController.getString("PassportAddressNoUploadInfo", R.string.PassportAddressNoUploadInfo);
                    } else {
                        if (currentActivityType == TYPE_MANAGE) {
                            value = LocaleController.getString("PassportDocuments", R.string.PassportDocuments);
                        } else {
                            if (availableDocumentTypesCount == 1) {
                                if (documentRequiredType.type instanceof TLRPC.TL_secureValueTypeRentalAgreement) {
                                    value = LocaleController.getString("PassportAddAgreementInfo", R.string.PassportAddAgreementInfo);
                                } else if (documentRequiredType.type instanceof TLRPC.TL_secureValueTypeUtilityBill) {
                                    value = LocaleController.getString("PassportAddBillInfo", R.string.PassportAddBillInfo);
                                } else if (documentRequiredType.type instanceof TLRPC.TL_secureValueTypePassportRegistration) {
                                    value = LocaleController.getString("PassportAddPassportRegistrationInfo", R.string.PassportAddPassportRegistrationInfo);
                                } else if (documentRequiredType.type instanceof TLRPC.TL_secureValueTypeTemporaryRegistration) {
                                    value = LocaleController.getString("PassportAddTemporaryRegistrationInfo", R.string.PassportAddTemporaryRegistrationInfo);
                                } else if (documentRequiredType.type instanceof TLRPC.TL_secureValueTypeBankStatement) {
                                    value = LocaleController.getString("PassportAddBankInfo", R.string.PassportAddBankInfo);
                                }
                            } else {
                                value = LocaleController.getString("PassportAddressInfo", R.string.PassportAddressInfo);
                            }
                        }
                    }
                }
            } else if (requiredType.type instanceof TLRPC.TL_secureValueTypePhone) {
                if (TextUtils.isEmpty(value)) {
                    value = LocaleController.getString("PassportPhoneInfo", R.string.PassportPhoneInfo);
                }
            } else if (requiredType.type instanceof TLRPC.TL_secureValueTypeEmail) {
                if (TextUtils.isEmpty(value)) {
                    value = LocaleController.getString("PassportEmailInfo", R.string.PassportEmailInfo);
                }
            }
        }
        view.setValue(value);
        view.valueTextView.setTextColor(Theme.getColor(isError ? Theme.key_windowBackgroundWhiteRedText3 : Theme.key_windowBackgroundWhiteGrayText2));
        view.setChecked(!isError && currentActivityType != TYPE_MANAGE && (documentOnly && documentRequiredType != null || !documentOnly && requiredTypeValue != null) && (documentRequiredType == null || documentRequiredTypeValue != null));
    }

    private void checkNativeFields(boolean byEdit) {
        if (inputExtraFields == null) {
            return;
        }
        String country = languageMap.get(currentResidence);
        HashMap<String, String> map = SharedConfig.getCountryLangs();
        String lang = map.get(currentResidence);

        if (!currentType.native_names || TextUtils.isEmpty(currentResidence) || "EN".equals(lang)) {
            if (nativeInfoCell.getVisibility() != View.GONE) {
                nativeInfoCell.setVisibility(View.GONE);
                headerCell.setVisibility(View.GONE);
                extraBackgroundView2.setVisibility(View.GONE);
                for (int a = 0; a < inputExtraFields.length; a++) {
                    ((View) inputExtraFields[a].getParent()).setVisibility(View.GONE);
                }

                if ((currentBotId != 0 || currentDocumentsType == null) && currentTypeValue != null && !documentOnly || currentDocumentsTypeValue != null) {
                    sectionCell2.setBackgroundDrawable(Theme.getThemedDrawable(getParentActivity(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                } else {
                    sectionCell2.setBackgroundDrawable(Theme.getThemedDrawable(getParentActivity(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                }
            }
        } else {
            if (nativeInfoCell.getVisibility() != View.VISIBLE) {
                nativeInfoCell.setVisibility(View.VISIBLE);
                headerCell.setVisibility(View.VISIBLE);
                extraBackgroundView2.setVisibility(View.VISIBLE);
                for (int a = 0; a < inputExtraFields.length; a++) {
                    ((View) inputExtraFields[a].getParent()).setVisibility(View.VISIBLE);
                }
                if (inputExtraFields[FIELD_NATIVE_NAME].length() == 0 && inputExtraFields[FIELD_NATIVE_MIDNAME].length() == 0 && inputExtraFields[FIELD_NATIVE_SURNAME].length() == 0) {
                    for (int a = 0; a < nonLatinNames.length; a++) {
                        if (nonLatinNames[a]) {
                            inputExtraFields[FIELD_NATIVE_NAME].setText(inputFields[FIELD_NAME].getText());
                            inputExtraFields[FIELD_NATIVE_MIDNAME].setText(inputFields[FIELD_MIDNAME].getText());
                            inputExtraFields[FIELD_NATIVE_SURNAME].setText(inputFields[FIELD_SURNAME].getText());
                            break;
                        }
                    }
                }
                sectionCell2.setBackgroundDrawable(Theme.getThemedDrawable(getParentActivity(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            }

            nativeInfoCell.setText(LocaleController.formatString("PassportNativeInfo", R.string.PassportNativeInfo, country));


            String header = lang != null ? LocaleController.getServerString("PassportLanguage_" + lang) : null;
            if (header != null) {
                headerCell.setText(LocaleController.formatString("PassportNativeHeaderLang", R.string.PassportNativeHeaderLang, header));
            } else {
                headerCell.setText(LocaleController.getString("PassportNativeHeader", R.string.PassportNativeHeader));
            }
            for (int a = 0; a < FIELD_NATIVE_COUNT; a++) {
                switch (a) {
                    case FIELD_NATIVE_NAME:
                        if (header != null) {
                            inputExtraFields[a].setHintText(LocaleController.getString("PassportName", R.string.PassportName));
                        } else {
                            inputExtraFields[a].setHintText(LocaleController.formatString("PassportNameCountry", R.string.PassportNameCountry, country));
                        }
                        break;
                    case FIELD_NATIVE_MIDNAME:
                        if (header != null) {
                            inputExtraFields[a].setHintText(LocaleController.getString("PassportMidname", R.string.PassportMidname));
                        } else {
                            inputExtraFields[a].setHintText(LocaleController.formatString("PassportMidnameCountry", R.string.PassportMidnameCountry, country));
                        }
                        break;
                    case FIELD_NATIVE_SURNAME:
                        if (header != null) {
                            inputExtraFields[a].setHintText(LocaleController.getString("PassportSurname", R.string.PassportSurname));
                        } else {
                            inputExtraFields[a].setHintText(LocaleController.formatString("PassportSurnameCountry", R.string.PassportSurnameCountry, country));
                        }
                        break;
                }
            }

            if (byEdit) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (inputExtraFields != null) {
                        scrollToField(inputExtraFields[FIELD_NATIVE_NAME]);
                    }
                });

            }
        }
    }

    private String getErrorsString(HashMap<String, String> errors, HashMap<String, String> documentErrors) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int a = 0; a < 2; a++) {
            HashMap<String, String> hashMap;
            if (a == 0) {
                hashMap = errors;
            } else {
                hashMap = documentErrors;
            }
            if (hashMap == null) {
                continue;
            }
            for (HashMap.Entry<String, String> entry : hashMap.entrySet()) {
                String value = entry.getValue();
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(", ");
                    value = value.toLowerCase();
                }
                if (value.endsWith(".")) {
                    value = value.substring(0, value.length() - 1);
                }
                stringBuilder.append(value);
            }
        }
        if (stringBuilder.length() > 0) {
            stringBuilder.append('.');
        }
        return stringBuilder.toString();
    }

    private TLRPC.TL_secureValue getValueByType(TLRPC.TL_secureRequiredType requiredType, boolean check) {
        if (requiredType == null) {
            return null;
        }
        for (int a = 0, size = currentForm.values.size(); a < size; a++) {
            TLRPC.TL_secureValue secureValue = currentForm.values.get(a);
            if (requiredType.type.getClass() == secureValue.type.getClass()) {
                if (check) {
                    if (requiredType.selfie_required) {
                        if (!(secureValue.selfie instanceof TLRPC.TL_secureFile)) {
                            return null;
                        }
                    }
                    if (requiredType.translation_required) {
                        if (secureValue.translation.isEmpty()) {
                            return null;
                        }
                    }
                    if (isAddressDocument(requiredType.type)) {
                        if (secureValue.files.isEmpty()) {
                            return null;
                        }
                    }
                    if (isPersonalDocument(requiredType.type)) {
                        if (!(secureValue.front_side instanceof TLRPC.TL_secureFile)) {
                            return null;
                        }
                    }
                    if (requiredType.type instanceof TLRPC.TL_secureValueTypeDriverLicense || requiredType.type instanceof TLRPC.TL_secureValueTypeIdentityCard) {
                        if (!(secureValue.reverse_side instanceof TLRPC.TL_secureFile)) {
                            return null;
                        }
                    }
                    if (requiredType.type instanceof TLRPC.TL_secureValueTypePersonalDetails || requiredType.type instanceof TLRPC.TL_secureValueTypeAddress) {
                        String[] keys;
                        if (requiredType.type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
                            if (requiredType.native_names) {
                                keys = new String[]{
                                        "first_name_native",
                                        "last_name_native",
                                        "birth_date",
                                        "gender",
                                        "country_code",
                                        "residence_country_code"
                                };
                            } else {
                                keys = new String[]{
                                        "first_name",
                                        "last_name",
                                        "birth_date",
                                        "gender",
                                        "country_code",
                                        "residence_country_code"
                                };
                            }
                        } else {
                            keys = new String[]{
                                    "street_line1",
                                    "street_line2",
                                    "post_code",
                                    "city",
                                    "state",
                                    "country_code"
                            };
                        }
                        try {
                            JSONObject jsonObject = new JSONObject(decryptData(secureValue.data.data, decryptValueSecret(secureValue.data.secret, secureValue.data.data_hash), secureValue.data.data_hash));
                            for (int b = 0; b < keys.length; b++) {
                                if (!jsonObject.has(keys[b]) || TextUtils.isEmpty(jsonObject.getString(keys[b]))) {
                                    return null;
                                }
                            }
                        } catch (Throwable ignore) {
                            return null;
                        }
                    }
                }
                return secureValue;
            }
        }
        return null;
    }

    private void openTypeActivity(TLRPC.TL_secureRequiredType requiredType, TLRPC.TL_secureRequiredType documentRequiredType, ArrayList<TLRPC.TL_secureRequiredType> availableDocumentTypes, boolean documentOnly) {
        int activityType = -1;
        final int availableDocumentTypesCount = availableDocumentTypes != null ? availableDocumentTypes.size() : 0;
        TLRPC.SecureValueType type = requiredType.type;
        TLRPC.SecureValueType documentType = documentRequiredType != null ? documentRequiredType.type : null;
        if (type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
            activityType = TYPE_IDENTITY;
        } else if (type instanceof TLRPC.TL_secureValueTypeAddress) {
            activityType = TYPE_ADDRESS;
        } else if (type instanceof TLRPC.TL_secureValueTypePhone) {
            activityType = TYPE_PHONE;
        } else if (type instanceof TLRPC.TL_secureValueTypeEmail) {
            activityType = TYPE_EMAIL;
        }
        if (activityType != -1) {
            HashMap<String, String> errors = !documentOnly ? errorsMap.get(getNameForType(type)) : null;
            HashMap<String, String> documentsErrors = errorsMap.get(getNameForType(documentType));
            TLRPC.TL_secureValue value = getValueByType(requiredType, false);
            TLRPC.TL_secureValue documentsValue = getValueByType(documentRequiredType, false);

            final PassportActivity activity = new PassportActivity(activityType, currentForm, currentPassword, requiredType, value, documentRequiredType, documentsValue, typesValues.get(requiredType), documentRequiredType != null ? typesValues.get(documentRequiredType) : null);
            activity.delegate = new PassportActivityDelegate() {

                private TLRPC.InputSecureFile getInputSecureFile(SecureDocument document) {
                    if (document.inputFile != null) {
                        TLRPC.TL_inputSecureFileUploaded inputSecureFileUploaded = new TLRPC.TL_inputSecureFileUploaded();
                        inputSecureFileUploaded.id = document.inputFile.id;
                        inputSecureFileUploaded.parts = document.inputFile.parts;
                        inputSecureFileUploaded.md5_checksum = document.inputFile.md5_checksum;
                        inputSecureFileUploaded.file_hash = document.fileHash;
                        inputSecureFileUploaded.secret = document.fileSecret;
                        return inputSecureFileUploaded;
                    } else {
                        TLRPC.TL_inputSecureFile inputSecureFile = new TLRPC.TL_inputSecureFile();
                        inputSecureFile.id = document.secureFile.id;
                        inputSecureFile.access_hash = document.secureFile.access_hash;
                        return inputSecureFile;
                    }
                }

                private void renameFile(SecureDocument oldDocument, TLRPC.TL_secureFile newSecureFile) {
                    File oldFile = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(oldDocument);
                    String oldKey = oldDocument.secureFile.dc_id + "_" + oldDocument.secureFile.id;
                    File newFile = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(newSecureFile);
                    String newKey = newSecureFile.dc_id + "_" + newSecureFile.id;
                    oldFile.renameTo(newFile);
                    ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, null, false);
                }

                @Override
                public void saveValue(final TLRPC.TL_secureRequiredType requiredType, final String text, final String json, final TLRPC.TL_secureRequiredType documentRequiredType, final String documentsJson, final ArrayList<SecureDocument> documents, final SecureDocument selfie, final ArrayList<SecureDocument> translationDocuments, final SecureDocument front, final SecureDocument reverse, final Runnable finishRunnable, final ErrorRunnable errorRunnable) {
                    TLRPC.TL_inputSecureValue inputSecureValue = null;

                    if (!TextUtils.isEmpty(json)) {
                        inputSecureValue = new TLRPC.TL_inputSecureValue();
                        inputSecureValue.type = requiredType.type;
                        inputSecureValue.flags |= 1;

                        EncryptionResult result = encryptData(AndroidUtilities.getStringBytes(json));
                        inputSecureValue.data = new TLRPC.TL_secureData();
                        inputSecureValue.data.data = result.encryptedData;
                        inputSecureValue.data.data_hash = result.fileHash;
                        inputSecureValue.data.secret = result.fileSecret;
                    } else if (!TextUtils.isEmpty(text)) {
                        TLRPC.SecurePlainData plainData;
                        if (type instanceof TLRPC.TL_secureValueTypeEmail) {
                            TLRPC.TL_securePlainEmail securePlainEmail = new TLRPC.TL_securePlainEmail();
                            securePlainEmail.email = text;
                            plainData = securePlainEmail;
                        } else if (type instanceof TLRPC.TL_secureValueTypePhone) {
                            TLRPC.TL_securePlainPhone securePlainPhone = new TLRPC.TL_securePlainPhone();
                            securePlainPhone.phone = text;
                            plainData = securePlainPhone;
                        } else {
                            return;
                        }
                        inputSecureValue = new TLRPC.TL_inputSecureValue();
                        inputSecureValue.type = requiredType.type;
                        inputSecureValue.flags |= 32;

                        inputSecureValue.plain_data = plainData;
                    }

                    if (!documentOnly && inputSecureValue == null) {
                        if (errorRunnable != null) {
                            errorRunnable.onError(null, null);
                        }
                        return;
                    }

                    TLRPC.TL_inputSecureValue fileInputSecureValue;
                    if (documentRequiredType != null) {
                        fileInputSecureValue = new TLRPC.TL_inputSecureValue();
                        fileInputSecureValue.type = documentRequiredType.type;

                        if (!TextUtils.isEmpty(documentsJson)) {
                            fileInputSecureValue.flags |= 1;

                            EncryptionResult result = encryptData(AndroidUtilities.getStringBytes(documentsJson));
                            fileInputSecureValue.data = new TLRPC.TL_secureData();
                            fileInputSecureValue.data.data = result.encryptedData;
                            fileInputSecureValue.data.data_hash = result.fileHash;
                            fileInputSecureValue.data.secret = result.fileSecret;
                        }

                        if (front != null) {
                            fileInputSecureValue.front_side = getInputSecureFile(front);
                            fileInputSecureValue.flags |= 2;
                        }
                        if (reverse != null) {
                            fileInputSecureValue.reverse_side = getInputSecureFile(reverse);
                            fileInputSecureValue.flags |= 4;
                        }
                        if (selfie != null) {
                            fileInputSecureValue.selfie = getInputSecureFile(selfie);
                            fileInputSecureValue.flags |= 8;
                        }
                        if (translationDocuments != null && !translationDocuments.isEmpty()) {
                            fileInputSecureValue.flags |= 64;
                            for (int a = 0, size = translationDocuments.size(); a < size; a++) {
                                fileInputSecureValue.translation.add(getInputSecureFile(translationDocuments.get(a)));
                            }
                        }
                        if (documents != null && !documents.isEmpty()) {
                            fileInputSecureValue.flags |= 16;
                            for (int a = 0, size = documents.size(); a < size; a++) {
                                fileInputSecureValue.files.add(getInputSecureFile(documents.get(a)));
                            }
                        }

                        if (documentOnly) {
                            inputSecureValue = fileInputSecureValue;
                            fileInputSecureValue = null;
                        }
                    } else {
                        fileInputSecureValue = null;
                    }

                    final PassportActivityDelegate currentDelegate = this;
                    final TLRPC.TL_inputSecureValue finalFileInputSecureValue = fileInputSecureValue;

                    final TLRPC.TL_account_saveSecureValue req = new TLRPC.TL_account_saveSecureValue();
                    req.value = inputSecureValue;
                    req.secure_secret_id = secureSecretId;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {

                        private void onResult(final TLRPC.TL_error error, final TLRPC.TL_secureValue newValue, final TLRPC.TL_secureValue newPendingValue) {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (error != null) {
                                    if (errorRunnable != null) {
                                        errorRunnable.onError(error.text, text);
                                    }
                                    AlertsCreator.processError(currentAccount, error, PassportActivity.this, req, text);
                                } else {
                                    if (documentOnly) {
                                        if (documentRequiredType != null) {
                                            removeValue(documentRequiredType);
                                        } else {
                                            removeValue(requiredType);
                                        }
                                    } else {
                                        removeValue(requiredType);
                                        removeValue(documentRequiredType);
                                    }
                                    if (newValue != null) {
                                        currentForm.values.add(newValue);
                                    }
                                    if (newPendingValue != null) {
                                        currentForm.values.add(newPendingValue);
                                    }
                                    if (documents != null && !documents.isEmpty()) {
                                        for (int a = 0, size = documents.size(); a < size; a++) {
                                            SecureDocument document = documents.get(a);
                                            if (document.inputFile != null) {
                                                for (int b = 0, size2 = newValue.files.size(); b < size2; b++) {
                                                    TLRPC.SecureFile file = newValue.files.get(b);
                                                    if (file instanceof TLRPC.TL_secureFile) {
                                                        TLRPC.TL_secureFile secureFile = (TLRPC.TL_secureFile) file;
                                                        if (Utilities.arraysEquals(document.fileSecret, 0, secureFile.secret, 0)) {
                                                            renameFile(document, secureFile);
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (selfie != null && selfie.inputFile != null && newValue.selfie instanceof TLRPC.TL_secureFile) {
                                        TLRPC.TL_secureFile secureFile = (TLRPC.TL_secureFile) newValue.selfie;
                                        if (Utilities.arraysEquals(selfie.fileSecret, 0, secureFile.secret, 0)) {
                                            renameFile(selfie, secureFile);
                                        }
                                    }
                                    if (front != null && front.inputFile != null && newValue.front_side instanceof TLRPC.TL_secureFile) {
                                        TLRPC.TL_secureFile secureFile = (TLRPC.TL_secureFile) newValue.front_side;
                                        if (Utilities.arraysEquals(front.fileSecret, 0, secureFile.secret, 0)) {
                                            renameFile(front, secureFile);
                                        }
                                    }
                                    if (reverse != null && reverse.inputFile != null && newValue.reverse_side instanceof TLRPC.TL_secureFile) {
                                        TLRPC.TL_secureFile secureFile = (TLRPC.TL_secureFile) newValue.reverse_side;
                                        if (Utilities.arraysEquals(reverse.fileSecret, 0, secureFile.secret, 0)) {
                                            renameFile(reverse, secureFile);
                                        }
                                    }
                                    if (translationDocuments != null && !translationDocuments.isEmpty()) {
                                        for (int a = 0, size = translationDocuments.size(); a < size; a++) {
                                            SecureDocument document = translationDocuments.get(a);
                                            if (document.inputFile != null) {
                                                for (int b = 0, size2 = newValue.translation.size(); b < size2; b++) {
                                                    TLRPC.SecureFile file = newValue.translation.get(b);
                                                    if (file instanceof TLRPC.TL_secureFile) {
                                                        TLRPC.TL_secureFile secureFile = (TLRPC.TL_secureFile) file;
                                                        if (Utilities.arraysEquals(document.fileSecret, 0, secureFile.secret, 0)) {
                                                            renameFile(document, secureFile);
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    setTypeValue(requiredType, text, json, documentRequiredType, documentsJson, documentOnly, availableDocumentTypesCount);
                                    if (finishRunnable != null) {
                                        finishRunnable.run();
                                    }
                                }
                            });
                        }

                        @Override
                        public void run(final TLObject response, final TLRPC.TL_error error) {
                            if (error != null) {
                                if (error.text.equals("EMAIL_VERIFICATION_NEEDED")) {
                                    TLRPC.TL_account_sendVerifyEmailCode req = new TLRPC.TL_account_sendVerifyEmailCode();
                                    req.purpose = new TLRPC.TL_emailVerifyPurposePassport();
                                    req.email = text;
                                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                        if (response1 != null) {
                                            TLRPC.TL_account_sentEmailCode res = (TLRPC.TL_account_sentEmailCode) response1;
                                            HashMap<String, String> values = new HashMap<>();
                                            values.put("email", text);
                                            values.put("pattern", res.email_pattern);
                                            PassportActivity activity1 = new PassportActivity(TYPE_EMAIL_VERIFICATION, currentForm, currentPassword, requiredType, null, null, null, values, null);
                                            activity1.currentAccount = currentAccount;
                                            activity1.emailCodeLength = res.length;
                                            activity1.saltedPassword = saltedPassword;
                                            activity1.secureSecret = secureSecret;
                                            activity1.delegate = currentDelegate;
                                            presentFragment(activity1, true);
                                        } else {
                                            showAlertWithText(LocaleController.getString("PassportEmail", R.string.PassportEmail), error1.text);
                                            if (errorRunnable != null) {
                                                errorRunnable.onError(error1.text, text);
                                            }
                                        }
                                    }));
                                    return;
                                } else if (error.text.equals("PHONE_VERIFICATION_NEEDED")) {
                                    AndroidUtilities.runOnUIThread(() -> errorRunnable.onError(error.text, text));
                                    return;
                                }
                            }
                            if (error == null && finalFileInputSecureValue != null) {
                                final TLRPC.TL_secureValue pendingValue = (TLRPC.TL_secureValue) response;
                                final TLRPC.TL_account_saveSecureValue req = new TLRPC.TL_account_saveSecureValue();
                                req.value = finalFileInputSecureValue;
                                req.secure_secret_id = secureSecretId;
                                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response12, error12) -> onResult(error12, (TLRPC.TL_secureValue) response12, pendingValue));
                            } else {
                                onResult(error, (TLRPC.TL_secureValue) response, null);
                            }
                        }
                    });
                }

                @Override
                public SecureDocument saveFile(TLRPC.TL_secureFile secureFile) {
                    String path = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + secureFile.dc_id + "_" + secureFile.id + ".jpg";
                    EncryptionResult result = createSecureDocument(path);
                    return new SecureDocument(result.secureDocumentKey, secureFile, path, result.fileHash, result.fileSecret);
                }

                @Override
                public void deleteValue(TLRPC.TL_secureRequiredType requiredType, TLRPC.TL_secureRequiredType documentRequiredType, ArrayList<TLRPC.TL_secureRequiredType> documentRequiredTypes, boolean deleteType, Runnable finishRunnable, ErrorRunnable errorRunnable) {
                    deleteValueInternal(requiredType, documentRequiredType, documentRequiredTypes, deleteType, finishRunnable, errorRunnable, documentOnly);
                }
            };
            activity.currentAccount = currentAccount;
            activity.saltedPassword = saltedPassword;
            activity.secureSecret = secureSecret;
            activity.currentBotId = currentBotId;
            activity.fieldsErrors = errors;
            activity.documentOnly = documentOnly;
            activity.documentsErrors = documentsErrors;
            activity.availableDocumentTypes = availableDocumentTypes;
            if (activityType == TYPE_EMAIL) {
                activity.currentEmail = currentEmail;
            }
            presentFragment(activity);
        }
    }

    private TLRPC.TL_secureValue removeValue(TLRPC.TL_secureRequiredType requiredType) {
        if (requiredType == null) {
            return null;
        }
        for (int a = 0, size = currentForm.values.size(); a < size; a++) {
            TLRPC.TL_secureValue secureValue = currentForm.values.get(a);
            if (requiredType.type.getClass() == secureValue.type.getClass()) {
                return currentForm.values.remove(a);
            }
        }
        return null;
    }

    private void deleteValueInternal(final TLRPC.TL_secureRequiredType requiredType, final TLRPC.TL_secureRequiredType documentRequiredType, final ArrayList<TLRPC.TL_secureRequiredType> documentRequiredTypes, final boolean deleteType, final Runnable finishRunnable, final ErrorRunnable errorRunnable, boolean documentOnly) {
        if (requiredType == null) {
            return;
        }
        TLRPC.TL_account_deleteSecureValue req = new TLRPC.TL_account_deleteSecureValue();
        if (documentOnly && documentRequiredType != null) {
            req.types.add(documentRequiredType.type);
        } else {
            if (deleteType) {
                req.types.add(requiredType.type);
            }
            if (documentRequiredType != null) {
                req.types.add(documentRequiredType.type);
            }
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                if (errorRunnable != null) {
                    errorRunnable.onError(error.text, null);
                }
                showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error.text);
            } else {
                if (documentOnly) {
                    if (documentRequiredType != null) {
                        removeValue(documentRequiredType);
                    } else {
                        removeValue(requiredType);
                    }
                } else {
                    if (deleteType) {
                        removeValue(requiredType);
                    }
                    removeValue(documentRequiredType);
                }
                if (currentActivityType == TYPE_MANAGE) {
                    TextDetailSecureCell view = typesViews.remove(requiredType);
                    if (view != null) {
                        linearLayout2.removeView(view);
                        View child = linearLayout2.getChildAt(linearLayout2.getChildCount() - 6);
                        if (child instanceof TextDetailSecureCell) {
                            ((TextDetailSecureCell) child).setNeedDivider(false);
                        }
                    }
                    updateManageVisibility();
                } else {

                    String documentJson = null;
                    TLRPC.TL_secureRequiredType documentsType = documentRequiredType;
                    if (documentsType != null && documentRequiredTypes != null && documentRequiredTypes.size() > 1) {
                        for (int a = 0, count = documentRequiredTypes.size(); a < count; a++) {
                            TLRPC.TL_secureRequiredType documentType = documentRequiredTypes.get(a);
                            TLRPC.TL_secureValue documentValue = getValueByType(documentType, false);
                            if (documentValue != null) {
                                if (documentValue.data != null) {
                                    documentJson = decryptData(documentValue.data.data, decryptValueSecret(documentValue.data.secret, documentValue.data.data_hash), documentValue.data.data_hash);
                                }
                                documentsType = documentType;
                                break;
                            }
                        }
                        if (documentsType == null) {
                            documentsType = documentRequiredTypes.get(0);
                        }
                    }

                    if (deleteType) {
                        setTypeValue(requiredType, null, null, documentsType, documentJson, documentOnly, documentRequiredTypes != null ? documentRequiredTypes.size() : 0);
                    } else {
                        String json = null;
                        TLRPC.TL_secureValue value = getValueByType(requiredType, false);
                        if (value != null && value.data != null) {
                            json = decryptData(value.data.data, decryptValueSecret(value.data.secret, value.data.data_hash), value.data.data_hash);
                        }
                        setTypeValue(requiredType, null, json, documentsType, documentJson, documentOnly, documentRequiredTypes != null ? documentRequiredTypes.size() : 0);
                    }
                }
                if (finishRunnable != null) {
                    finishRunnable.run();
                }
            }
        }));
    }

    private TextDetailSecureCell addField(Context context, final TLRPC.TL_secureRequiredType requiredType, final ArrayList<TLRPC.TL_secureRequiredType> documentRequiredTypes, boolean documentOnly, boolean last) {
        final int availableDocumentTypesCount = documentRequiredTypes != null ? documentRequiredTypes.size() : 0;
        TextDetailSecureCell view = new TextDetailSecureCell(context);
        view.setBackgroundDrawable(Theme.getSelectorDrawable(true));
        if (requiredType.type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
            String text;
            if (documentRequiredTypes == null || documentRequiredTypes.isEmpty()) {
                text = LocaleController.getString("PassportPersonalDetails", R.string.PassportPersonalDetails);
            } else if (documentOnly && documentRequiredTypes.size() == 1) {
                text = getTextForType(documentRequiredTypes.get(0).type);
            } else if (documentOnly && documentRequiredTypes.size() == 2) {
                text = LocaleController.formatString("PassportTwoDocuments", R.string.PassportTwoDocuments, getTextForType(documentRequiredTypes.get(0).type), getTextForType(documentRequiredTypes.get(1).type));
            } else {
                text = LocaleController.getString("PassportIdentityDocument", R.string.PassportIdentityDocument);
            }
            view.setTextAndValue(text, "", !last);
        } else if (requiredType.type instanceof TLRPC.TL_secureValueTypeAddress) {
            String text;
            if (documentRequiredTypes == null || documentRequiredTypes.isEmpty()) {
                text = LocaleController.getString("PassportAddress", R.string.PassportAddress);
            } else if (documentOnly && documentRequiredTypes.size() == 1) {
                text = getTextForType(documentRequiredTypes.get(0).type);
            } else if (documentOnly && documentRequiredTypes.size() == 2) {
                text = LocaleController.formatString("PassportTwoDocuments", R.string.PassportTwoDocuments, getTextForType(documentRequiredTypes.get(0).type), getTextForType(documentRequiredTypes.get(1).type));
            } else {
                text = LocaleController.getString("PassportResidentialAddress", R.string.PassportResidentialAddress);
            }
            view.setTextAndValue(text, "", !last);
        } else if (requiredType.type instanceof TLRPC.TL_secureValueTypePhone) {
            view.setTextAndValue(LocaleController.getString("PassportPhone", R.string.PassportPhone), "", !last);
        } else if (requiredType.type instanceof TLRPC.TL_secureValueTypeEmail) {
            view.setTextAndValue(LocaleController.getString("PassportEmail", R.string.PassportEmail), "", !last);
        }
        if (currentActivityType == TYPE_MANAGE) {
            linearLayout2.addView(view, linearLayout2.getChildCount() - 5, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            linearLayout2.addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        view.setOnClickListener(v -> {
            TLRPC.TL_secureRequiredType documentsType = null;
            if (documentRequiredTypes != null) {
                for (int a = 0, count = documentRequiredTypes.size(); a < count; a++) {
                    TLRPC.TL_secureRequiredType documentType = documentRequiredTypes.get(a);
                    if (getValueByType(documentType, false) != null || count == 1) {
                        documentsType = documentType;
                        break;
                    }
                }
            }
            if (requiredType.type instanceof TLRPC.TL_secureValueTypePersonalDetails || requiredType.type instanceof TLRPC.TL_secureValueTypeAddress) {
                if (documentsType == null && documentRequiredTypes != null && !documentRequiredTypes.isEmpty()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setPositiveButton(LocaleController.getString("Cancel", R.string.Cancel), null);

                    if (requiredType.type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
                        builder.setTitle(LocaleController.getString("PassportIdentityDocument", R.string.PassportIdentityDocument));
                    } else if (requiredType.type instanceof TLRPC.TL_secureValueTypeAddress) {
                        builder.setTitle(LocaleController.getString("PassportAddress", R.string.PassportAddress));
                    }

                    ArrayList<String> strings = new ArrayList<>();
                    for (int a = 0, count = documentRequiredTypes.size(); a < count; a++) {
                        TLRPC.TL_secureRequiredType documentType = documentRequiredTypes.get(a);
                        if (documentType.type instanceof TLRPC.TL_secureValueTypeDriverLicense) {
                            strings.add(LocaleController.getString("PassportAddLicence", R.string.PassportAddLicence));
                        } else if (documentType.type instanceof TLRPC.TL_secureValueTypePassport) {
                            strings.add(LocaleController.getString("PassportAddPassport", R.string.PassportAddPassport));
                        } else if (documentType.type instanceof TLRPC.TL_secureValueTypeInternalPassport) {
                            strings.add(LocaleController.getString("PassportAddInternalPassport", R.string.PassportAddInternalPassport));
                        } else if (documentType.type instanceof TLRPC.TL_secureValueTypeIdentityCard) {
                            strings.add(LocaleController.getString("PassportAddCard", R.string.PassportAddCard));
                        } else if (documentType.type instanceof TLRPC.TL_secureValueTypeUtilityBill) {
                            strings.add(LocaleController.getString("PassportAddBill", R.string.PassportAddBill));
                        } else if (documentType.type instanceof TLRPC.TL_secureValueTypeBankStatement) {
                            strings.add(LocaleController.getString("PassportAddBank", R.string.PassportAddBank));
                        } else if (documentType.type instanceof TLRPC.TL_secureValueTypeRentalAgreement) {
                            strings.add(LocaleController.getString("PassportAddAgreement", R.string.PassportAddAgreement));
                        } else if (documentType.type instanceof TLRPC.TL_secureValueTypeTemporaryRegistration) {
                            strings.add(LocaleController.getString("PassportAddTemporaryRegistration", R.string.PassportAddTemporaryRegistration));
                        } else if (documentType.type instanceof TLRPC.TL_secureValueTypePassportRegistration) {
                            strings.add(LocaleController.getString("PassportAddPassportRegistration", R.string.PassportAddPassportRegistration));
                        }
                    }

                    builder.setItems(strings.toArray(new CharSequence[0]), (dialog, which) -> openTypeActivity(requiredType, documentRequiredTypes.get(which), documentRequiredTypes, documentOnly));
                    showDialog(builder.create());
                    return;
                }
            } else {
                boolean phoneField;
                if ((phoneField = (requiredType.type instanceof TLRPC.TL_secureValueTypePhone)) || requiredType.type instanceof TLRPC.TL_secureValueTypeEmail) {
                    final TLRPC.TL_secureValue value = getValueByType(requiredType, false);
                    if (value != null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
                            needShowProgress();
                            deleteValueInternal(requiredType, null, null, true, this::needHideProgress, (error, text) -> needHideProgress(), documentOnly);
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(phoneField ? LocaleController.getString("PassportDeletePhoneAlert", R.string.PassportDeletePhoneAlert) : LocaleController.getString("PassportDeleteEmailAlert", R.string.PassportDeleteEmailAlert));
                        showDialog(builder.create());
                        return;
                    }
                }
            }
            openTypeActivity(requiredType, documentsType, documentRequiredTypes, documentOnly);
        });
        typesViews.put(requiredType, view);

        String text = null;
        String json = null;
        String documentJson = null;
        typesValues.put(requiredType, new HashMap<>());

        TLRPC.TL_secureValue value = getValueByType(requiredType, false);
        if (value != null) {
            if (value.plain_data instanceof TLRPC.TL_securePlainEmail) {
                text = ((TLRPC.TL_securePlainEmail) value.plain_data).email;
            } else if (value.plain_data instanceof TLRPC.TL_securePlainPhone) {
                text = ((TLRPC.TL_securePlainPhone) value.plain_data).phone;
            } else if (value.data != null) {
                json = decryptData(value.data.data, decryptValueSecret(value.data.secret, value.data.data_hash), value.data.data_hash);
            }
        }
        TLRPC.TL_secureRequiredType documentsType = null;
        if (documentRequiredTypes != null && !documentRequiredTypes.isEmpty()) {
            boolean found = false;
            for (int a = 0, count = documentRequiredTypes.size(); a < count; a++) {
                TLRPC.TL_secureRequiredType documentType = documentRequiredTypes.get(a);
                typesValues.put(documentType, new HashMap<>());
                documentsToTypesLink.put(documentType, requiredType);
                if (!found) {
                    TLRPC.TL_secureValue documentValue = getValueByType(documentType, false);
                    if (documentValue != null) {
                        if (documentValue.data != null) {
                            documentJson = decryptData(documentValue.data.data, decryptValueSecret(documentValue.data.secret, documentValue.data.data_hash), documentValue.data.data_hash);
                        }
                        documentsType = documentType;
                        found = true;
                    }
                }
            }
            if (documentsType == null) {
                documentsType = documentRequiredTypes.get(0);
            }
        }

        setTypeValue(requiredType, text, json, documentsType, documentJson, documentOnly, availableDocumentTypesCount);
        return view;
    }

    private static class EncryptionResult {
        byte[] fileSecret;
        byte[] decrypyedFileSecret;
        byte[] encryptedData;
        byte[] fileHash;
        SecureDocumentKey secureDocumentKey;

        public EncryptionResult(byte[] d, byte[] fs, byte[] dfs, byte[] fh, byte[] fk, byte[] fi) {
            encryptedData = d;
            fileSecret = fs;
            fileHash = fh;
            decrypyedFileSecret = dfs;
            secureDocumentKey = new SecureDocumentKey(fk, fi);
        }
    }

    private SecureDocumentKey getSecureDocumentKey(byte[] file_secret, byte[] file_hash) {
        byte[] decrypted_file_secret = decryptValueSecret(file_secret, file_hash);

        byte[] file_secret_hash = Utilities.computeSHA512(decrypted_file_secret, file_hash);
        byte[] file_key = new byte[32];
        System.arraycopy(file_secret_hash, 0, file_key, 0, 32);
        byte[] file_iv = new byte[16];
        System.arraycopy(file_secret_hash, 32, file_iv, 0, 16);

        return new SecureDocumentKey(file_key, file_iv);
    }

    private byte[] decryptSecret(byte[] secret, byte[] passwordHash) {
        if (secret == null || secret.length != 32) {
            return null;
        }
        byte[] key = new byte[32];
        System.arraycopy(passwordHash, 0, key, 0, 32);
        byte[] iv = new byte[16];
        System.arraycopy(passwordHash, 32, iv, 0, 16);

        byte[] decryptedSecret = new byte[32];
        System.arraycopy(secret, 0, decryptedSecret, 0, 32);
        Utilities.aesCbcEncryptionByteArraySafe(decryptedSecret, key, iv, 0, decryptedSecret.length, 0, 0);
        return decryptedSecret;
    }

    private byte[] decryptValueSecret(byte[] encryptedSecureValueSecret, byte[] hash) {
        if (encryptedSecureValueSecret == null || encryptedSecureValueSecret.length != 32 || hash == null || hash.length != 32) {
            return null;
        }
        byte[] key = new byte[32];
        System.arraycopy(saltedPassword, 0, key, 0, 32);
        byte[] iv = new byte[16];
        System.arraycopy(saltedPassword, 32, iv, 0, 16);

        byte[] decryptedSecret = new byte[32];
        System.arraycopy(secureSecret, 0, decryptedSecret, 0, 32);
        Utilities.aesCbcEncryptionByteArraySafe(decryptedSecret, key, iv, 0, decryptedSecret.length, 0, 0);
        if (!checkSecret(decryptedSecret, null)) {
            return null;
        }

        byte[] secret_hash = Utilities.computeSHA512(decryptedSecret, hash);
        byte[] file_secret_key = new byte[32];
        System.arraycopy(secret_hash, 0, file_secret_key, 0, 32);
        byte[] file_secret_iv = new byte[16];
        System.arraycopy(secret_hash, 32, file_secret_iv, 0, 16);

        byte[] result = new byte[32];
        System.arraycopy(encryptedSecureValueSecret, 0, result, 0, 32);
        Utilities.aesCbcEncryptionByteArraySafe(result, file_secret_key, file_secret_iv, 0, result.length, 0, 0);

        return result;
    }

    private EncryptionResult createSecureDocument(String path) {
        File file = new File(path);
        int length = (int) file.length();
        byte[] b = new byte[length];
        RandomAccessFile f = null;
        try {
            f = new RandomAccessFile(path, "rws");
            f.readFully(b);
        } catch (Exception ignore) {

        }
        EncryptionResult result = encryptData(b);
        try {
            f.seek(0);
            f.write(result.encryptedData);
            f.close();
        } catch (Exception ignore) {

        }
        return result;
    }

    private String decryptData(byte[] data, byte[] file_secret, byte[] file_hash) {
        if (data == null || file_secret == null || file_secret.length != 32 || file_hash == null || file_hash.length != 32) {
            return null;
        }
        byte[] file_secret_hash = Utilities.computeSHA512(file_secret, file_hash);
        byte[] file_key = new byte[32];
        System.arraycopy(file_secret_hash, 0, file_key, 0, 32);
        byte[] file_iv = new byte[16];
        System.arraycopy(file_secret_hash, 32, file_iv, 0, 16);

        byte[] decryptedData = new byte[data.length];
        System.arraycopy(data, 0, decryptedData, 0, data.length);
        Utilities.aesCbcEncryptionByteArraySafe(decryptedData, file_key, file_iv, 0, decryptedData.length, 0, 0);

        byte[] hash = Utilities.computeSHA256(decryptedData);
        if (!Arrays.equals(hash, file_hash)) {
            return null;
        }

        int dataOffset = decryptedData[0] & 0xff;

        return new String(decryptedData, dataOffset, decryptedData.length - dataOffset);
    }

    public static boolean checkSecret(byte[] secret, Long id) {
        if (secret == null || secret.length != 32) {
            return false;
        }
        int sum = 0;
        int a;
        for (a = 0; a < secret.length; a++) {
            sum += secret[a] & 0xff;
        }
        if (sum % 255 != 239) {
            return false;
        }

        if (id != null && Utilities.bytesToLong(Utilities.computeSHA256(secret)) != id) {
            return false;
        }

        return true;
    }

    private byte[] getRandomSecret() {
        byte[] secret = new byte[32];
        Utilities.random.nextBytes(secret);
        int sum = 0;
        int a;
        for (a = 0; a < secret.length; a++) {
            sum += secret[a] & 0xff;
        }
        sum = sum % 255;
        if (sum != 239) {
            sum = 239 - sum;

            a = Utilities.random.nextInt(32);
            int val = secret[a] & 0xff;
            val += sum;
            if (val < 255) {
                val = 255 + val;
            }
            secret[a] = (byte) (val % 255);
        }
        return secret;
    }

    private EncryptionResult encryptData(byte[] data) {
        byte[] file_secret = getRandomSecret();

        int extraLen = 32 + Utilities.random.nextInt(256 - 32 - 16);
        while ((data.length + extraLen) % 16 != 0) {
            extraLen++;
        }
        byte[] padding = new byte[extraLen];
        Utilities.random.nextBytes(padding);
        padding[0] = (byte) extraLen;
        byte[] paddedData = new byte[extraLen + data.length];
        System.arraycopy(padding, 0, paddedData, 0, extraLen);
        System.arraycopy(data, 0, paddedData, extraLen, data.length);

        byte[] file_hash = Utilities.computeSHA256(paddedData);
        byte[] file_secret_hash = Utilities.computeSHA512(file_secret, file_hash);
        byte[] file_key = new byte[32];
        System.arraycopy(file_secret_hash, 0, file_key, 0, 32);
        byte[] file_iv = new byte[16];
        System.arraycopy(file_secret_hash, 32, file_iv, 0, 16);

        Utilities.aesCbcEncryptionByteArraySafe(paddedData, file_key, file_iv, 0, paddedData.length, 0, 1);

        byte[] key = new byte[32];
        System.arraycopy(saltedPassword, 0, key, 0, 32);
        byte[] iv = new byte[16];
        System.arraycopy(saltedPassword, 32, iv, 0, 16);

        byte[] decryptedSecret = new byte[32];
        System.arraycopy(secureSecret, 0, decryptedSecret, 0, 32);
        Utilities.aesCbcEncryptionByteArraySafe(decryptedSecret, key, iv, 0, decryptedSecret.length, 0, 0);

        byte[] secret_hash = Utilities.computeSHA512(decryptedSecret, file_hash);
        byte[] file_secret_key = new byte[32];
        System.arraycopy(secret_hash, 0, file_secret_key, 0, 32);
        byte[] file_secret_iv = new byte[16];
        System.arraycopy(secret_hash, 32, file_secret_iv, 0, 16);

        byte[] encrypyed_file_secret = new byte[32];
        System.arraycopy(file_secret, 0, encrypyed_file_secret, 0, 32);
        Utilities.aesCbcEncryptionByteArraySafe(encrypyed_file_secret, file_secret_key, file_secret_iv, 0, encrypyed_file_secret.length, 0, 1);

        return new EncryptionResult(paddedData, encrypyed_file_secret, file_secret, file_hash, file_key, file_iv);
    }

    private void showAlertWithText(String title, String text) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.setTitle(title);
        builder.setMessage(text);
        showDialog(builder.create());
    }

    private void onPasscodeError(boolean clear) {
        if (getParentActivity() == null) {
            return;
        }
        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(200);
        }
        if (clear) {
            inputFields[FIELD_PASSWORD].setText("");
        }
        AndroidUtilities.shakeView(inputFields[FIELD_PASSWORD]);
    }

    private void startPhoneVerification(boolean checkPermissions, final String phone, Runnable finishRunnable, ErrorRunnable errorRunnable, final PassportActivityDelegate delegate) {
        TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
        boolean simcardAvailable = tm.getSimState() != TelephonyManager.SIM_STATE_ABSENT && tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
        boolean allowCall = true;
        if (getParentActivity() != null && Build.VERSION.SDK_INT >= 23 && simcardAvailable) {
            allowCall = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
            if (checkPermissions) {
                permissionsItems.clear();
                if (!allowCall) {
                    permissionsItems.add(Manifest.permission.READ_PHONE_STATE);
                }
                if (!permissionsItems.isEmpty()) {
                    if (getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                        builder.setMessage(LocaleController.getString("AllowReadCall", R.string.AllowReadCall));
                        permissionsDialog = showDialog(builder.create());
                    } else {
                        getParentActivity().requestPermissions(permissionsItems.toArray(new String[0]), 6);
                    }
                    pendingPhone = phone;
                    pendingErrorRunnable = errorRunnable;
                    pendingFinishRunnable = finishRunnable;
                    pendingDelegate = delegate;
                    return;
                }
            }
        }
        final TLRPC.TL_account_sendVerifyPhoneCode req = new TLRPC.TL_account_sendVerifyPhoneCode();
        req.phone_number = phone;
        req.settings = new TLRPC.TL_codeSettings();
        req.settings.allow_flashcall = simcardAvailable && allowCall;
        req.settings.allow_app_hash = PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        if (req.settings.allow_app_hash) {
            preferences.edit().putString("sms_hash", BuildVars.SMS_HASH).commit();
        } else {
            preferences.edit().remove("sms_hash").commit();
        }
        if (req.settings.allow_flashcall) {
            try {
                @SuppressLint("HardwareIds")
                String number = tm.getLine1Number();
                if (!TextUtils.isEmpty(number)) {
                    req.settings.current_number = PhoneNumberUtils.compare(phone, number);
                    if (!req.settings.current_number) {
                        req.settings.allow_flashcall = false;
                    }
                } else {
                    req.settings.current_number = false;
                }
            } catch (Exception e) {
                req.settings.allow_flashcall = false;
                FileLog.e(e);
            }
        }

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                HashMap<String, String> values = new HashMap<>();
                values.put("phone", phone);
                PassportActivity activity = new PassportActivity(TYPE_PHONE_VERIFICATION, currentForm, currentPassword, currentType, null, null, null, values, null);
                activity.currentAccount = currentAccount;
                activity.saltedPassword = saltedPassword;
                activity.secureSecret = secureSecret;
                activity.delegate = delegate;
                activity.currentPhoneVerification = (TLRPC.TL_auth_sentCode) response;
                presentFragment(activity, true);
            } else {
                AlertsCreator.processError(currentAccount, error, PassportActivity.this, req, phone);
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void updatePasswordInterface() {
        if (noPasswordImageView == null) {
            return;
        }
        if (currentPassword == null || usingSavedPassword != 0) {
            noPasswordImageView.setVisibility(View.GONE);
            noPasswordTextView.setVisibility(View.GONE);
            noPasswordSetTextView.setVisibility(View.GONE);
            passwordAvatarContainer.setVisibility(View.GONE);
            inputFieldContainers[FIELD_PASSWORD].setVisibility(View.GONE);
            doneItem.setVisibility(View.GONE);
            passwordForgotButton.setVisibility(View.GONE);
            passwordInfoRequestTextView.setVisibility(View.GONE);
            passwordRequestTextView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else if (!currentPassword.has_password) {
            passwordRequestTextView.setVisibility(View.VISIBLE);

            noPasswordImageView.setVisibility(View.VISIBLE);
            noPasswordTextView.setVisibility(View.VISIBLE);
            noPasswordSetTextView.setVisibility(View.VISIBLE);

            passwordAvatarContainer.setVisibility(View.GONE);
            inputFieldContainers[FIELD_PASSWORD].setVisibility(View.GONE);
            doneItem.setVisibility(View.GONE);
            passwordForgotButton.setVisibility(View.GONE);
            passwordInfoRequestTextView.setVisibility(View.GONE);
            passwordRequestTextView.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 25, 0, 0));
            emptyView.setVisibility(View.GONE);
        } else {
            passwordRequestTextView.setVisibility(View.VISIBLE);

            noPasswordImageView.setVisibility(View.GONE);
            noPasswordTextView.setVisibility(View.GONE);
            noPasswordSetTextView.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);

            passwordAvatarContainer.setVisibility(View.VISIBLE);
            inputFieldContainers[FIELD_PASSWORD].setVisibility(View.VISIBLE);
            doneItem.setVisibility(View.VISIBLE);
            passwordForgotButton.setVisibility(View.VISIBLE);
            passwordInfoRequestTextView.setVisibility(View.VISIBLE);
            passwordRequestTextView.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            if (inputFields != null) {
                if (currentPassword != null && !TextUtils.isEmpty(currentPassword.hint)) {
                    inputFields[FIELD_PASSWORD].setHint(currentPassword.hint);
                } else {
                    inputFields[FIELD_PASSWORD].setHint(LocaleController.getString("LoginPassword", R.string.LoginPassword));
                }
            }
        }
    }

    private void showEditDoneProgress(final boolean animateDoneItem, final boolean show) {
        if (doneItemAnimation != null) {
            doneItemAnimation.cancel();
        }
        if (animateDoneItem && doneItem != null) {
            doneItemAnimation = new AnimatorSet();
            if (show) {
                progressView.setVisibility(View.VISIBLE);
                doneItem.setEnabled(false);
                doneItemAnimation.playTogether(
                        ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(doneItem.getContentView(), View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(progressView, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(progressView, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(progressView, View.ALPHA, 1.0f));
            } else {
                doneItem.getContentView().setVisibility(View.VISIBLE);
                doneItem.setEnabled(true);
                doneItemAnimation.playTogether(
                        ObjectAnimator.ofFloat(progressView, View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(progressView, View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(progressView, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(doneItem.getContentView(), View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(doneItem.getContentView(), View.ALPHA, 1.0f));
            }
            doneItemAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        if (!show) {
                            progressView.setVisibility(View.INVISIBLE);
                        } else {
                            doneItem.getContentView().setVisibility(View.INVISIBLE);
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        doneItemAnimation = null;
                    }
                }
            });
            doneItemAnimation.setDuration(150);
            doneItemAnimation.start();
        } else if (acceptTextView != null) {
            doneItemAnimation = new AnimatorSet();
            if (show) {
                progressViewButton.setVisibility(View.VISIBLE);
                bottomLayout.setEnabled(false);
                doneItemAnimation.playTogether(
                        ObjectAnimator.ofFloat(acceptTextView, View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(acceptTextView, View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(acceptTextView, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(progressViewButton, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(progressViewButton, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(progressViewButton, View.ALPHA, 1.0f));
            } else {
                acceptTextView.setVisibility(View.VISIBLE);
                bottomLayout.setEnabled(true);
                doneItemAnimation.playTogether(
                        ObjectAnimator.ofFloat(progressViewButton, View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(progressViewButton, View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(progressViewButton, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(acceptTextView, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(acceptTextView, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(acceptTextView, View.ALPHA, 1.0f));

            }
            doneItemAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        if (!show) {
                            progressViewButton.setVisibility(View.INVISIBLE);
                        } else {
                            acceptTextView.setVisibility(View.INVISIBLE);
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        doneItemAnimation = null;
                    }
                }
            });
            doneItemAnimation.setDuration(150);
            doneItemAnimation.start();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileUploaded) {
            final String location = (String) args[0];
            SecureDocument document = uploadingDocuments.get(location);
            if (document != null) {
                document.inputFile = (TLRPC.TL_inputFile) args[1];
                uploadingDocuments.remove(location);
                if (uploadingDocuments.isEmpty()) {
                    if (doneItem != null) {
                        doneItem.setEnabled(true);
                        doneItem.setAlpha(1.0f);
                    }
                }
                if (documentsCells != null) {
                    SecureDocumentCell cell = documentsCells.get(document);
                    if (cell != null) {
                        cell.updateButtonState(true);
                    }
                }
                if (errorsValues != null && errorsValues.containsKey("error_document_all")) {
                    errorsValues.remove("error_document_all");
                    checkTopErrorCell(false);
                }
                if (document.type == UPLOADING_TYPE_DOCUMENTS) {
                    if (bottomCell != null && !TextUtils.isEmpty(noAllDocumentsErrorText)) {
                        bottomCell.setText(noAllDocumentsErrorText);
                    }
                    errorsValues.remove("files_all");
                } else if (document.type == UPLOADING_TYPE_TRANSLATION) {
                    if (bottomCellTranslation != null && !TextUtils.isEmpty(noAllTranslationErrorText)) {
                        bottomCellTranslation.setText(noAllTranslationErrorText);
                    }
                    errorsValues.remove("translation_all");
                }
            }
        } else if (id == NotificationCenter.fileUploadFailed) {

        } else if (id == NotificationCenter.twoStepPasswordChanged) {
            if (args != null && args.length > 0) {
                if (args[7] != null && inputFields[FIELD_PASSWORD] != null) {
                    inputFields[FIELD_PASSWORD].setText((String) args[7]);
                }
                if (args[6] == null) {
                    currentPassword = new TLRPC.TL_account_password();
                    currentPassword.current_algo = (TLRPC.PasswordKdfAlgo) args[1];
                    currentPassword.new_secure_algo = (TLRPC.SecurePasswordKdfAlgo) args[2];
                    currentPassword.secure_random = (byte[]) args[3];
                    currentPassword.has_recovery = !TextUtils.isEmpty((String) args[4]);
                    currentPassword.hint = (String) args[5];
                    currentPassword.srp_id = -1;
                    currentPassword.srp_B = new byte[256];
                    Utilities.random.nextBytes(currentPassword.srp_B);

                    if (inputFields[FIELD_PASSWORD] != null && inputFields[FIELD_PASSWORD].length() > 0) {
                        usingSavedPassword = 2;
                    }
                }
            } else {
                currentPassword = null;
                loadPasswordInfo();
            }
            updatePasswordInterface();
        } else if (id == NotificationCenter.didRemoveTwoStepPassword) {

        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (presentAfterAnimation != null) {
            AndroidUtilities.runOnUIThread(() -> {
                presentFragment(presentAfterAnimation, true);
                presentAfterAnimation = null;
            });
        }
        if (currentActivityType == TYPE_PASSWORD) {
            if (isOpen) {
                if (inputFieldContainers[FIELD_PASSWORD].getVisibility() == View.VISIBLE) {
                    inputFields[FIELD_PASSWORD].requestFocus();
                    AndroidUtilities.showKeyboard(inputFields[FIELD_PASSWORD]);
                }
                if (usingSavedPassword == 2) {
                    onPasswordDone(false);
                }
            }
        } else if (currentActivityType == TYPE_PHONE_VERIFICATION) {
            if (isOpen) {
                views[currentViewNum].onShow();
            }
        } else if (currentActivityType == TYPE_EMAIL) {
            if (isOpen) {
                inputFields[FIELD_EMAIL].requestFocus();
                AndroidUtilities.showKeyboard(inputFields[FIELD_EMAIL]);
            }
        } else if (currentActivityType == TYPE_EMAIL_VERIFICATION) {
            if (isOpen) {
                inputFields[FIELD_EMAIL].requestFocus();
                AndroidUtilities.showKeyboard(inputFields[FIELD_EMAIL]);
            }
        } else if (currentActivityType == TYPE_ADDRESS || currentActivityType == TYPE_IDENTITY) {
            if (Build.VERSION.SDK_INT >= 21) {
                createChatAttachView();
            }
        }
    }

    private void showAttachmentError() {
        if (getParentActivity() == null) {
            return;
        }
        Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("UnsupportedAttachment", R.string.UnsupportedAttachment), Toast.LENGTH_SHORT);
        toast.show();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 0 || requestCode == 2) {
                createChatAttachView();
                if (chatAttachAlert != null) {
                    chatAttachAlert.onActivityResultFragment(requestCode, data, currentPicturePath);
                }
                currentPicturePath = null;
            } else if (requestCode == 1) {
                if (data == null || data.getData() == null) {
                    showAttachmentError();
                    return;
                }
                ArrayList<SendMessagesHelper.SendingMediaInfo> photos = new ArrayList<>();
                SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                info.uri = data.getData();
                photos.add(info);
                processSelectedFiles(photos);
            }
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if ((currentActivityType == TYPE_IDENTITY || currentActivityType == TYPE_ADDRESS) && chatAttachAlert != null) {
            if (requestCode == 17) {
                chatAttachAlert.getPhotoLayout().checkCamera(false);
            } else if (requestCode == 21) {
                if (getParentActivity() == null) {
                    return;
                }
                if (grantResults != null && grantResults.length != 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("PermissionNoAudioVideoWithHint", R.string.PermissionNoAudioVideoWithHint));
                    builder.setNegativeButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), (dialog, which) -> {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                            getParentActivity().startActivity(intent);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    });
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                    builder.show();
                }
            } else if (requestCode == 19 && grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                processSelectedAttach(attach_photo);
            } else if (requestCode == 22 && grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (scanDocumentCell != null) {
                    scanDocumentCell.callOnClick();
                }
            }
        } else if (currentActivityType == TYPE_PHONE && requestCode == 6) {
            startPhoneVerification(false, pendingPhone, pendingFinishRunnable, pendingErrorRunnable, pendingDelegate);
        }
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        if (currentPicturePath != null) {
            args.putString("path", currentPicturePath);
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        currentPicturePath = args.getString("path");
    }

    @Override
    public boolean onBackPressed() {
        if (currentActivityType == TYPE_PHONE_VERIFICATION) {
            views[currentViewNum].onBackPressed(true);
            for (int a = 0; a < views.length; a++) {
                if (views[a] != null) {
                    views[a].onDestroyActivity();
                }
            }
        } else if (currentActivityType == TYPE_REQUEST || currentActivityType == TYPE_PASSWORD) {
            callCallback(false);
        } else if (currentActivityType == TYPE_IDENTITY || currentActivityType == TYPE_ADDRESS) {
            return !checkDiscard();
        }
        return true;
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        if (currentActivityType == TYPE_PHONE) {
            if (Build.VERSION.SDK_INT >= 23 && dialog == permissionsDialog && !permissionsItems.isEmpty()) {
                getParentActivity().requestPermissions(permissionsItems.toArray(new String[0]), 6);
            }
        }
    }

    //-----phone verification
    private String pendingPhone;
    private Runnable pendingFinishRunnable;
    private ErrorRunnable pendingErrorRunnable;
    private PassportActivityDelegate pendingDelegate;
    private int currentViewNum;
    private SlideView[] views;
    private AlertDialog progressDialog;
    private Dialog permissionsDialog;
    private ArrayList<String> permissionsItems;

    public void needShowProgress() {
        if (getParentActivity() == null || getParentActivity().isFinishing() || progressDialog != null) {
            return;
        }
        progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.setCanCancel(false);
        progressDialog.show();
    }

    public void needHideProgress() {
        if (progressDialog == null) {
            return;
        }
        try {
            progressDialog.dismiss();
        } catch (Exception e) {
            FileLog.e(e);
        }
        progressDialog = null;
    }

    public void setPage(int page, boolean animated, Bundle params) {
        if (page == 3) {
            doneItem.setVisibility(View.GONE);
        }
        final SlideView outView = views[currentViewNum];
        final SlideView newView = views[page];
        currentViewNum = page;

        newView.setParams(params, false);
        newView.onShow();

        if (animated) {
            newView.setTranslationX(AndroidUtilities.displaySize.x);
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
            animatorSet.setDuration(300);
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(outView, "translationX", -AndroidUtilities.displaySize.x),
                    ObjectAnimator.ofFloat(newView, "translationX", 0));
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    newView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    outView.setVisibility(View.GONE);
                    outView.setX(0);
                }
            });
            animatorSet.start();
        } else {
            newView.setTranslationX(0);
            newView.setVisibility(View.VISIBLE);
            if (outView != newView) {
                outView.setVisibility(View.GONE);
            }
        }
    }

    private void fillNextCodeParams(Bundle params, TLRPC.TL_auth_sentCode res, boolean animated) {
        params.putString("phoneHash", res.phone_code_hash);
        if (res.next_type instanceof TLRPC.TL_auth_codeTypeCall) {
            params.putInt("nextType", 4);
        } else if (res.next_type instanceof TLRPC.TL_auth_codeTypeFlashCall) {
            params.putInt("nextType", 3);
        } else if (res.next_type instanceof TLRPC.TL_auth_codeTypeSms) {
            params.putInt("nextType", 2);
        }
        if (res.timeout == 0) {
            res.timeout = 60;
        }
        params.putInt("timeout", res.timeout * 1000);
        if (res.type instanceof TLRPC.TL_auth_sentCodeTypeCall) {
            params.putInt("type", 4);
            params.putInt("length", res.type.length);
            setPage(2, animated, params);
        } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeFlashCall) {
            params.putInt("type", 3);
            params.putString("pattern", res.type.pattern);
            setPage(1, animated, params);
        } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeSms) {
            params.putInt("type", 2);
            params.putInt("length", res.type.length);
            setPage(0, animated, params);
        }
    }

    private void openAttachMenu() {
        if (getParentActivity() == null) {
            return;
        }
        if (uploadingFileType == UPLOADING_TYPE_DOCUMENTS && documents.size() >= 20) {
            showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("PassportUploadMaxReached", R.string.PassportUploadMaxReached, LocaleController.formatPluralString("Files", 20)));
            return;
        }
        createChatAttachView();
        chatAttachAlert.setOpenWithFrontFaceCamera(uploadingFileType == UPLOADING_TYPE_SELFIE);
        chatAttachAlert.setMaxSelectedPhotos(getMaxSelectedDocuments(), false);
        chatAttachAlert.getPhotoLayout().loadGalleryPhotos();
        if (Build.VERSION.SDK_INT == 21 || Build.VERSION.SDK_INT == 22) {
            AndroidUtilities.hideKeyboard(fragmentView.findFocus());
        }
        chatAttachAlert.init();
        showDialog(chatAttachAlert);
    }

    private void createChatAttachView() {
        if (getParentActivity() == null) {
            return;
        }
        if (chatAttachAlert == null) {
            chatAttachAlert = new ChatAttachAlert(getParentActivity(), this, false, false);
            chatAttachAlert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {

                @Override
                public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, boolean forceDocument) {
                    if (getParentActivity() == null || chatAttachAlert == null) {
                        return;
                    }
                    if (button == 8 || button == 7) {
                        if (button != 8) {
                            chatAttachAlert.dismiss(true);
                        }
                        HashMap<Object, Object> selectedPhotos = chatAttachAlert.getPhotoLayout().getSelectedPhotos();
                        ArrayList<Object> selectedPhotosOrder = chatAttachAlert.getPhotoLayout().getSelectedPhotosOrder();
                        if (!selectedPhotos.isEmpty()) {
                            ArrayList<SendMessagesHelper.SendingMediaInfo> photos = new ArrayList<>();
                            for (int a = 0; a < selectedPhotosOrder.size(); a++) {
                                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) selectedPhotos.get(selectedPhotosOrder.get(a));
                                SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                                if (photoEntry.imagePath != null) {
                                    info.path = photoEntry.imagePath;
                                } else {
                                    info.path = photoEntry.path;
                                }
                                photos.add(info);
                                photoEntry.reset();
                            }
                            processSelectedFiles(photos);
                        }
                        return;
                    } else if (chatAttachAlert != null) {
                        chatAttachAlert.dismissWithButtonClick(button);
                    }
                    processSelectedAttach(button);
                }

                @Override
                public void onCameraOpened() {
                    AndroidUtilities.hideKeyboard(fragmentView.findFocus());
                }

            });
        }
    }

    private int getMaxSelectedDocuments() {
        if (uploadingFileType == UPLOADING_TYPE_DOCUMENTS) {
            return 20 - documents.size();
        } else if (uploadingFileType == UPLOADING_TYPE_TRANSLATION) {
            return 20 - translationDocuments.size();
        } else {
            return 1;
        }
    }

    private void processSelectedAttach(int which) {
        if (which == attach_photo) {
            if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 19);
                return;
            }
            try {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                File image = AndroidUtilities.generatePicturePath();
                if (image != null) {
                    if (Build.VERSION.SDK_INT >= 24) {
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", image));
                        takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
                    }
                    currentPicturePath = image.getAbsolutePath();
                }
                startActivityForResult(takePictureIntent, 0);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
        processSelectedFiles(photos);
    }

    public void startDocumentSelectActivity() {
        try {
            Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
            if (Build.VERSION.SDK_INT >= 18) {
                photoPickerIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            photoPickerIntent.setType("*/*");
            startActivityForResult(photoPickerIntent, 21);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void didSelectFiles(ArrayList<String> files, String caption, boolean notify, int scheduleDate) {
        ArrayList<SendMessagesHelper.SendingMediaInfo> arrayList = new ArrayList<>();
        for (int a = 0, count = files.size(); a < count; a++) {
            SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
            info.path = files.get(a);
            arrayList.add(info);
        }
        processSelectedFiles(arrayList);
    }

    private void fillInitialValues() {
        if (initialValues != null) {
            return;
        }
        initialValues = getCurrentValues();
    }

    private String getCurrentValues() {
        StringBuilder values = new StringBuilder();
        for (int a = 0; a < inputFields.length; a++) {
            values.append(inputFields[a].getText()).append(",");
        }
        if (inputExtraFields != null) {
            for (int a = 0; a < inputExtraFields.length; a++) {
                values.append(inputExtraFields[a].getText()).append(",");
            }
        }
        for (int a = 0, count = documents.size(); a < count; a++) {
            values.append(documents.get(a).secureFile.id);
        }
        if (frontDocument != null) {
            values.append(frontDocument.secureFile.id);
        }
        if (reverseDocument != null) {
            values.append(reverseDocument.secureFile.id);
        }
        if (selfieDocument != null) {
            values.append(selfieDocument.secureFile.id);
        }
        for (int a = 0, count = translationDocuments.size(); a < count; a++) {
            values.append(translationDocuments.get(a).secureFile.id);
        }
        return values.toString();
    }

    private boolean isHasNotAnyChanges() {
        return initialValues == null || initialValues.equals(getCurrentValues());
    }

    private boolean checkDiscard() {
        if (isHasNotAnyChanges()) {
            return false;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setPositiveButton(LocaleController.getString("PassportDiscard", R.string.PassportDiscard), (dialog, which) -> finishFragment());
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.setTitle(LocaleController.getString("DiscardChanges", R.string.DiscardChanges));
        builder.setMessage(LocaleController.getString("PassportDiscardChanges", R.string.PassportDiscardChanges));
        showDialog(builder.create());
        return true;
    }

    private void processSelectedFiles(final ArrayList<SendMessagesHelper.SendingMediaInfo> photos) {
        if (photos.isEmpty()) {
            return;
        }
        final boolean needRecoginze;
        if (uploadingFileType == UPLOADING_TYPE_SELFIE || uploadingFileType == UPLOADING_TYPE_TRANSLATION) {
            needRecoginze = false;
        } else if (currentType.type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
            boolean allFieldsAreEmpty = true;
            for (int a = 0; a < inputFields.length; a++) {
                if (a == FIELD_CITIZENSHIP || a == FIELD_EXPIRE || a == FIELD_GENDER || a == FIELD_RESIDENCE) {
                    continue;
                }
                if (inputFields[a].length() > 0) {
                    allFieldsAreEmpty = false;
                    break;
                }
            }
            needRecoginze = allFieldsAreEmpty;
        } else {
            needRecoginze = false;
        }
        final int type = uploadingFileType;
        Utilities.globalQueue.postRunnable(() -> {
            boolean didRecognizeSuccessfully = false;
            for (int a = 0, count = Math.min(uploadingFileType == UPLOADING_TYPE_DOCUMENTS || uploadingFileType == UPLOADING_TYPE_TRANSLATION ? 20 : 1, photos.size()); a < count; a++) {
                SendMessagesHelper.SendingMediaInfo info = photos.get(a);
                Bitmap bitmap = ImageLoader.loadBitmap(info.path, info.uri, 2048, 2048, false);
                if (bitmap == null) {
                    continue;
                }
                TLRPC.PhotoSize size = ImageLoader.scaleAndSaveImage(bitmap, 2048, 2048, 89, false, 320, 320);
                if (size == null) {
                    continue;
                }
                TLRPC.TL_secureFile secureFile = new TLRPC.TL_secureFile();
                secureFile.dc_id = (int) size.location.volume_id;
                secureFile.id = size.location.local_id;
                secureFile.date = (int) (System.currentTimeMillis() / 1000);

                final SecureDocument document = delegate.saveFile(secureFile);
                document.type = type;
                AndroidUtilities.runOnUIThread(() -> {
                    if (uploadingFileType == UPLOADING_TYPE_SELFIE) {
                        if (selfieDocument != null) {
                            SecureDocumentCell cell = documentsCells.remove(selfieDocument);
                            if (cell != null) {
                                selfieLayout.removeView(cell);
                            }
                            selfieDocument = null;
                        }
                    } else if (uploadingFileType == UPLOADING_TYPE_TRANSLATION) {
                        if (translationDocuments.size() >= 20) {
                            return;
                        }
                    } else if (uploadingFileType == UPLOADING_TYPE_FRONT) {
                        if (frontDocument != null) {
                            SecureDocumentCell cell = documentsCells.remove(frontDocument);
                            if (cell != null) {
                                frontLayout.removeView(cell);
                            }
                            frontDocument = null;
                        }
                    } else if (uploadingFileType == UPLOADING_TYPE_REVERSE) {
                        if (reverseDocument != null) {
                            SecureDocumentCell cell = documentsCells.remove(reverseDocument);
                            if (cell != null) {
                                reverseLayout.removeView(cell);
                            }
                            reverseDocument = null;
                        }
                    } else if (uploadingFileType == UPLOADING_TYPE_DOCUMENTS) {
                        if (documents.size() >= 20) {
                            return;
                        }
                    }
                    uploadingDocuments.put(document.path, document);
                    doneItem.setEnabled(false);
                    doneItem.setAlpha(0.5f);
                    FileLoader.getInstance(currentAccount).uploadFile(document.path, false, true, ConnectionsManager.FileTypePhoto);
                    addDocumentView(document, type);
                    updateUploadText(type);
                });

                if (needRecoginze && !didRecognizeSuccessfully) {
                    try {
                        final MrzRecognizer.Result result = MrzRecognizer.recognize(bitmap, currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeDriverLicense);
                        if (result != null) {
                            didRecognizeSuccessfully = true;
                            AndroidUtilities.runOnUIThread(() -> {
                                if (result.type == MrzRecognizer.Result.TYPE_ID) {
                                    if (!(currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeIdentityCard)) {
                                        for (int a1 = 0, count1 = availableDocumentTypes.size(); a1 < count1; a1++) {
                                            TLRPC.TL_secureRequiredType requiredType = availableDocumentTypes.get(a1);
                                            if (requiredType.type instanceof TLRPC.TL_secureValueTypeIdentityCard) {
                                                currentDocumentsType = requiredType;
                                                updateInterfaceStringsForDocumentType();
                                                break;
                                            }
                                        }
                                    }
                                } else if (result.type == MrzRecognizer.Result.TYPE_PASSPORT) {
                                    if (!(currentDocumentsType.type instanceof TLRPC.TL_secureValueTypePassport)) {
                                        for (int a1 = 0, count1 = availableDocumentTypes.size(); a1 < count1; a1++) {
                                            TLRPC.TL_secureRequiredType requiredType = availableDocumentTypes.get(a1);
                                            if (requiredType.type instanceof TLRPC.TL_secureValueTypePassport) {
                                                currentDocumentsType = requiredType;
                                                updateInterfaceStringsForDocumentType();
                                                break;
                                            }
                                        }
                                    }
                                } else if (result.type == MrzRecognizer.Result.TYPE_INTERNAL_PASSPORT) {
                                    if (!(currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeInternalPassport)) {
                                        for (int a1 = 0, count1 = availableDocumentTypes.size(); a1 < count1; a1++) {
                                            TLRPC.TL_secureRequiredType requiredType = availableDocumentTypes.get(a1);
                                            if (requiredType.type instanceof TLRPC.TL_secureValueTypeInternalPassport) {
                                                currentDocumentsType = requiredType;
                                                updateInterfaceStringsForDocumentType();
                                                break;
                                            }
                                        }
                                    }
                                } else if (result.type == MrzRecognizer.Result.TYPE_DRIVER_LICENSE) {
                                    if (!(currentDocumentsType.type instanceof TLRPC.TL_secureValueTypeDriverLicense)) {
                                        for (int a1 = 0, count1 = availableDocumentTypes.size(); a1 < count1; a1++) {
                                            TLRPC.TL_secureRequiredType requiredType = availableDocumentTypes.get(a1);
                                            if (requiredType.type instanceof TLRPC.TL_secureValueTypeDriverLicense) {
                                                currentDocumentsType = requiredType;
                                                updateInterfaceStringsForDocumentType();
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (!TextUtils.isEmpty(result.firstName)) {
                                    inputFields[FIELD_NAME].setText(result.firstName);
                                }
                                if (!TextUtils.isEmpty(result.middleName)) {
                                    inputFields[FIELD_MIDNAME].setText(result.middleName);
                                }
                                if (!TextUtils.isEmpty(result.lastName)) {
                                    inputFields[FIELD_SURNAME].setText(result.lastName);
                                }
                                if (!TextUtils.isEmpty(result.number)) {
                                    inputFields[FIELD_CARDNUMBER].setText(result.number);
                                }
                                if (result.gender != MrzRecognizer.Result.GENDER_UNKNOWN) {
                                    switch (result.gender) {
                                        case MrzRecognizer.Result.GENDER_MALE:
                                            currentGender = "male";
                                            inputFields[FIELD_GENDER].setText(LocaleController.getString("PassportMale", R.string.PassportMale));
                                            break;
                                        case MrzRecognizer.Result.GENDER_FEMALE:
                                            currentGender = "female";
                                            inputFields[FIELD_GENDER].setText(LocaleController.getString("PassportFemale", R.string.PassportFemale));
                                            break;
                                    }
                                }
                                if (!TextUtils.isEmpty(result.nationality)) {
                                    currentCitizeship = result.nationality;
                                    String country = languageMap.get(currentCitizeship);
                                    if (country != null) {
                                        inputFields[FIELD_CITIZENSHIP].setText(country);
                                    }
                                }
                                if (!TextUtils.isEmpty(result.issuingCountry)) {
                                    currentResidence = result.issuingCountry;
                                    String country = languageMap.get(currentResidence);
                                    if (country != null) {
                                        inputFields[FIELD_RESIDENCE].setText(country);
                                    }
                                }
                                if (result.birthDay > 0 && result.birthMonth > 0 && result.birthYear > 0) {
                                    inputFields[FIELD_BIRTHDAY].setText(String.format(Locale.US, "%02d.%02d.%d", result.birthDay, result.birthMonth, result.birthYear));
                                }
                                if (result.expiryDay > 0 && result.expiryMonth > 0 && result.expiryYear > 0) {
                                    currentExpireDate[0] = result.expiryYear;
                                    currentExpireDate[1] = result.expiryMonth;
                                    currentExpireDate[2] = result.expiryDay;
                                    inputFields[FIELD_EXPIRE].setText(String.format(Locale.US, "%02d.%02d.%d", result.expiryDay, result.expiryMonth, result.expiryYear));
                                } else {
                                    currentExpireDate[0] = currentExpireDate[1] = currentExpireDate[2] = 0;
                                    inputFields[FIELD_EXPIRE].setText(LocaleController.getString("PassportNoExpireDate", R.string.PassportNoExpireDate));
                                }
                            });
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }

            }
            SharedConfig.saveConfig();
        });
    }

    public void setNeedActivityResult(boolean needActivityResult) {
        this.needActivityResult = needActivityResult;
    }

    private static class ProgressView extends View {

        private Paint paint = new Paint();
        private Paint paint2 = new Paint();
        private float progress;

        public ProgressView(Context context) {
            super(context);
            paint.setColor(Theme.getColor(Theme.key_login_progressInner));
            paint2.setColor(Theme.getColor(Theme.key_login_progressOuter));
        }

        public void setProgress(float value) {
            progress = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int start = (int) (getMeasuredWidth() * progress);
            canvas.drawRect(0, 0, start, getMeasuredHeight(), paint2);
            canvas.drawRect(start, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
        }
    }

    public class PhoneConfirmationView extends SlideView implements NotificationCenter.NotificationCenterDelegate {

        private String phone;
        private String phoneHash;
        private LinearLayout codeFieldContainer;
        private EditTextBoldCursor[] codeField;
        private TextView confirmTextView;
        private TextView titleTextView;
        private ImageView blackImageView;
        private ImageView blueImageView;
        private TextView timeText;
        private TextView problemText;
        private Bundle currentParams;
        private ProgressView progressView;

        private Timer timeTimer;
        private Timer codeTimer;
        private final Object timerSync = new Object();
        private int time = 60000;
        private int codeTime = 15000;
        private double lastCurrentTime;
        private double lastCodeTime;
        private boolean ignoreOnTextChange;
        private boolean waitingForEvent;
        private boolean nextPressed;
        private String lastError = "";
        private int verificationType;
        private int nextType;
        private String pattern = "*";
        private int length;
        private int timeout;

        public PhoneConfirmationView(Context context, final int type) {
            super(context);

            verificationType = type;
            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);

            titleTextView = new TextView(context);
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            titleTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            titleTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

            if (verificationType == 3) {
                confirmTextView.setGravity(Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
                FrameLayout frameLayout = new FrameLayout(context);
                addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

                ImageView imageView = new ImageView(context);
                imageView.setImageResource(R.drawable.phone_activate);
                if (LocaleController.isRTL) {
                    frameLayout.addView(imageView, LayoutHelper.createFrame(64, 76, Gravity.LEFT | Gravity.CENTER_VERTICAL, 2, 2, 0, 0));
                    frameLayout.addView(confirmTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 64 + 18, 0, 0, 0));
                } else {
                    frameLayout.addView(confirmTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 0, 64 + 18, 0));
                    frameLayout.addView(imageView, LayoutHelper.createFrame(64, 76, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 2, 0, 2));
                }
            } else {
                confirmTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

                FrameLayout frameLayout = new FrameLayout(context);
                addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

                if (verificationType == 1) {
                    blackImageView = new ImageView(context);
                    blackImageView.setImageResource(R.drawable.sms_devices);
                    blackImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
                    frameLayout.addView(blackImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

                    blueImageView = new ImageView(context);
                    blueImageView.setImageResource(R.drawable.sms_bubble);
                    blueImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionBackground), PorterDuff.Mode.MULTIPLY));
                    frameLayout.addView(blueImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

                    titleTextView.setText(LocaleController.getString("SentAppCodeTitle", R.string.SentAppCodeTitle));
                } else {
                    blueImageView = new ImageView(context);
                    blueImageView.setImageResource(R.drawable.sms_code);
                    blueImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionBackground), PorterDuff.Mode.MULTIPLY));
                    frameLayout.addView(blueImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

                    titleTextView.setText(LocaleController.getString("SentSmsCodeTitle", R.string.SentSmsCodeTitle));
                }
                addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 18, 0, 0));
                addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 17, 0, 0));
            }

            codeFieldContainer = new LinearLayout(context);
            codeFieldContainer.setOrientation(HORIZONTAL);
            addView(codeFieldContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, Gravity.CENTER_HORIZONTAL));
            if (verificationType == 3) {
                codeFieldContainer.setVisibility(GONE);
            }

            timeText = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.AT_MOST));
                }
            };
            timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            timeText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            if (verificationType == 3) {
                timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                addView(timeText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

                progressView = new ProgressView(context);
                timeText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                addView(progressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 3, 0, 12, 0, 0));
            } else {
                timeText.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(10));
                timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                timeText.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                addView(timeText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            }

            problemText = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.AT_MOST));
                }
            };
            problemText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            problemText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            problemText.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(10));
            problemText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            problemText.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            if (verificationType == 1) {
                problemText.setText(LocaleController.getString("DidNotGetTheCodeSms", R.string.DidNotGetTheCodeSms));
            } else {
                problemText.setText(LocaleController.getString("DidNotGetTheCode", R.string.DidNotGetTheCode));
            }
            addView(problemText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
            problemText.setOnClickListener(v -> {
                if (nextPressed) {
                    return;
                }
                boolean email = nextType == 4 && verificationType == 2 || nextType == 0;
                if (!email) {
                    resendCode();
                } else {
                    try {
                        PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                        String version = String.format(Locale.US, "%s (%d)", pInfo.versionName, pInfo.versionCode);

                        Intent mailer = new Intent(Intent.ACTION_SENDTO);
                        mailer.setData(Uri.parse("mailto:"));
                        mailer.putExtra(Intent.EXTRA_EMAIL, new String[]{"sms@telegram.org"});
                        mailer.putExtra(Intent.EXTRA_SUBJECT, "Android registration/login issue " + version + " " + phone);
                        mailer.putExtra(Intent.EXTRA_TEXT, "Phone: " + phone + "\nApp version: " + version + "\nOS version: SDK " + Build.VERSION.SDK_INT + "\nDevice Name: " + Build.MANUFACTURER + Build.MODEL + "\nLocale: " + Locale.getDefault() + "\nError: " + lastError);
                        getContext().startActivity(Intent.createChooser(mailer, "Send email..."));
                    } catch (Exception e) {
                        AlertsCreator.showSimpleAlert(PassportActivity.this, LocaleController.getString("NoMailInstalled", R.string.NoMailInstalled));
                    }
                }
            });
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (verificationType != 3 && blueImageView != null) {
                int innerHeight = blueImageView.getMeasuredHeight() + titleTextView.getMeasuredHeight() + confirmTextView.getMeasuredHeight() + AndroidUtilities.dp(18 + 17);
                int requiredHeight = AndroidUtilities.dp(80);
                int maxHeight = AndroidUtilities.dp(291);
                if (scrollHeight - innerHeight < requiredHeight) {
                    setMeasuredDimension(getMeasuredWidth(), innerHeight + requiredHeight);
                } else {
                    setMeasuredDimension(getMeasuredWidth(), Math.min(scrollHeight, maxHeight));
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (verificationType != 3 && blueImageView != null) {
                int bottom = confirmTextView.getBottom();
                int height = getMeasuredHeight() - bottom;

                int h;
                if (problemText.getVisibility() == VISIBLE) {
                    h = problemText.getMeasuredHeight();
                    t = bottom + height - h;
                    problemText.layout(problemText.getLeft(), t, problemText.getRight(), t + h);
                } else if (timeText.getVisibility() == VISIBLE) {
                    h = timeText.getMeasuredHeight();
                    t = bottom + height - h;
                    timeText.layout(timeText.getLeft(), t, timeText.getRight(), t + h);
                } else {
                    t = bottom + height;
                }

                height = t - bottom;
                h = codeFieldContainer.getMeasuredHeight();
                t = (height - h) / 2 + bottom;
                codeFieldContainer.layout(codeFieldContainer.getLeft(), t, codeFieldContainer.getRight(), t + h);
            }
        }

        private void resendCode() {
            final Bundle params = new Bundle();
            params.putString("phone", phone);

            nextPressed = true;
            needShowProgress();

            final TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
            req.phone_number = phone;
            req.phone_code_hash = phoneHash;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                nextPressed = false;
                if (error == null) {
                    fillNextCodeParams(params, (TLRPC.TL_auth_sentCode) response, true);
                } else {
                    AlertDialog dialog = (AlertDialog) AlertsCreator.processError(currentAccount, error, PassportActivity.this, req);
                    if (dialog != null && error.text.contains("PHONE_CODE_EXPIRED")) {
                        dialog.setPositiveButtonListener((dialog1, which) -> {
                            onBackPressed(true);
                            finishFragment();
                        });
                    }
                }
                needHideProgress();
            }), ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            waitingForEvent = true;
            if (verificationType == 2) {
                AndroidUtilities.setWaitingForSms(true);
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (verificationType == 3) {
                AndroidUtilities.setWaitingForCall(true);
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReceiveCall);
            }

            currentParams = params;
            phone = params.getString("phone");
            phoneHash = params.getString("phoneHash");
            timeout = time = params.getInt("timeout");
            nextType = params.getInt("nextType");
            pattern = params.getString("pattern");
            length = params.getInt("length");
            if (length == 0) {
                length = 5;
            }

            if (codeField == null || codeField.length != length) {
                codeField = new EditTextBoldCursor[length];
                for (int a = 0; a < length; a++) {
                    final int num = a;
                    codeField[a] = new EditTextBoldCursor(getContext());
                    codeField[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    codeField[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    codeField[a].setCursorSize(AndroidUtilities.dp(20));
                    codeField[a].setCursorWidth(1.5f);

                    Drawable pressedDrawable = getResources().getDrawable(R.drawable.search_dark_activated).mutate();
                    pressedDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), PorterDuff.Mode.MULTIPLY));

                    codeField[a].setBackgroundDrawable(pressedDrawable);
                    codeField[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                    codeField[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                    codeField[a].setMaxLines(1);
                    codeField[a].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    codeField[a].setPadding(0, 0, 0, 0);
                    codeField[a].setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                    if (verificationType == 3) {
                        codeField[a].setEnabled(false);
                        codeField[a].setInputType(InputType.TYPE_NULL);
                        codeField[a].setVisibility(GONE);
                    } else {
                        codeField[a].setInputType(InputType.TYPE_CLASS_PHONE);
                    }
                    codeFieldContainer.addView(codeField[a], LayoutHelper.createLinear(34, 36, Gravity.CENTER_HORIZONTAL, 0, 0, a != length - 1 ? 7 : 0, 0));
                    codeField[a].addTextChangedListener(new TextWatcher() {

                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            if (ignoreOnTextChange) {
                                return;
                            }
                            int len = s.length();
                            if (len >= 1) {
                                if (len > 1) {
                                    String text = s.toString();
                                    ignoreOnTextChange = true;
                                    for (int a = 0; a < Math.min(length - num, len); a++) {
                                        if (a == 0) {
                                            s.replace(0, len, text.substring(a, a + 1));
                                        } else {
                                            codeField[num + a].setText(text.substring(a, a + 1));
                                        }
                                    }
                                    ignoreOnTextChange = false;
                                }

                                if (num != length - 1) {
                                    codeField[num + 1].setSelection(codeField[num + 1].length());
                                    codeField[num + 1].requestFocus();
                                }
                                if ((num == length - 1 || num == length - 2 && len >= 2) && getCode().length() == length) {
                                    onNextPressed(null);
                                }
                            }
                        }
                    });
                    codeField[a].setOnKeyListener((v, keyCode, event) -> {
                        if (keyCode == KeyEvent.KEYCODE_DEL && codeField[num].length() == 0 && num > 0) {
                            codeField[num - 1].setSelection(codeField[num - 1].length());
                            codeField[num - 1].requestFocus();
                            codeField[num - 1].dispatchKeyEvent(event);
                            return true;
                        }
                        return false;
                    });
                    codeField[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                        if (i == EditorInfo.IME_ACTION_NEXT) {
                            onNextPressed(null);
                            return true;
                        }
                        return false;
                    });
                }
            } else {
                for (int a = 0; a < codeField.length; a++) {
                    codeField[a].setText("");
                }
            }

            if (progressView != null) {
                progressView.setVisibility(nextType != 0 ? VISIBLE : GONE);
            }

            if (phone == null) {
                return;
            }

            String number = PhoneFormat.getInstance().format("+" + phone);
            CharSequence str = "";
            if (verificationType == 2) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentSmsCode", R.string.SentSmsCode, LocaleController.addNbsp(number)));
            } else if (verificationType == 3) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentCallCode", R.string.SentCallCode, LocaleController.addNbsp(number)));
            } else if (verificationType == 4) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentCallOnly", R.string.SentCallOnly, LocaleController.addNbsp(number)));
            }
            confirmTextView.setText(str);

            if (verificationType != 3) {
                AndroidUtilities.showKeyboard(codeField[0]);
                codeField[0].requestFocus();
            } else {
                AndroidUtilities.hideKeyboard(codeField[0]);
            }

            destroyTimer();
            destroyCodeTimer();

            lastCurrentTime = System.currentTimeMillis();
            if (verificationType == 3 && (nextType == 4 || nextType == 2)) {
                problemText.setVisibility(GONE);
                timeText.setVisibility(VISIBLE);
                if (nextType == 4) {
                    timeText.setText(LocaleController.formatString("CallText", R.string.CallText, 1, 0));
                } else if (nextType == 2) {
                    timeText.setText(LocaleController.formatString("SmsText", R.string.SmsText, 1, 0));
                }
                createTimer();
            } else if (verificationType == 2 && (nextType == 4 || nextType == 3)) {
                timeText.setText(LocaleController.formatString("CallText", R.string.CallText, 2, 0));
                problemText.setVisibility(time < 1000 ? VISIBLE : GONE);
                timeText.setVisibility(time < 1000 ? GONE : VISIBLE);
                createTimer();
            } else if (verificationType == 4 && nextType == 2) {
                timeText.setText(LocaleController.formatString("SmsText", R.string.SmsText, 2, 0));
                problemText.setVisibility(time < 1000 ? VISIBLE : GONE);
                timeText.setVisibility(time < 1000 ? GONE : VISIBLE);
                createTimer();
            } else {
                timeText.setVisibility(GONE);
                problemText.setVisibility(GONE);
                createCodeTimer();
            }
        }

        private void createCodeTimer() {
            if (codeTimer != null) {
                return;
            }
            codeTime = 15000;
            codeTimer = new Timer();
            lastCodeTime = System.currentTimeMillis();
            codeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    AndroidUtilities.runOnUIThread(() -> {
                        double currentTime = System.currentTimeMillis();
                        double diff = currentTime - lastCodeTime;
                        lastCodeTime = currentTime;
                        codeTime -= diff;
                        if (codeTime <= 1000) {
                            problemText.setVisibility(VISIBLE);
                            timeText.setVisibility(GONE);
                            destroyCodeTimer();
                        }
                    });
                }
            }, 0, 1000);
        }

        private void destroyCodeTimer() {
            try {
                synchronized (timerSync) {
                    if (codeTimer != null) {
                        codeTimer.cancel();
                        codeTimer = null;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        private void createTimer() {
            if (timeTimer != null) {
                return;
            }
            timeTimer = new Timer();
            timeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (timeTimer == null) {
                        return;
                    }
                    final double currentTime = System.currentTimeMillis();
                    double diff = currentTime - lastCurrentTime;
                    time -= diff;
                    lastCurrentTime = currentTime;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (time >= 1000) {
                            int minutes = time / 1000 / 60;
                            int seconds = time / 1000 - minutes * 60;
                            if (nextType == 4 || nextType == 3) {
                                timeText.setText(LocaleController.formatString("CallText", R.string.CallText, minutes, seconds));
                            } else if (nextType == 2) {
                                timeText.setText(LocaleController.formatString("SmsText", R.string.SmsText, minutes, seconds));
                            }
                            if (progressView != null) {
                                progressView.setProgress(1.0f - (float) time / (float) timeout);
                            }
                        } else {
                            if (progressView != null) {
                                progressView.setProgress(1.0f);
                            }
                            destroyTimer();
                            if (verificationType == 3) {
                                AndroidUtilities.setWaitingForCall(false);
                                NotificationCenter.getGlobalInstance().removeObserver(PhoneConfirmationView.this, NotificationCenter.didReceiveCall);
                                waitingForEvent = false;
                                destroyCodeTimer();
                                resendCode();
                            } else if (verificationType == 2 || verificationType == 4) {
                                if (nextType == 4 || nextType == 2) {
                                    if (nextType == 4) {
                                        timeText.setText(LocaleController.getString("Calling", R.string.Calling));
                                    } else {
                                        timeText.setText(LocaleController.getString("SendingSms", R.string.SendingSms));
                                    }
                                    createCodeTimer();
                                    TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
                                    req.phone_number = phone;
                                    req.phone_code_hash = phoneHash;
                                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                                        if (error != null && error.text != null) {
                                            AndroidUtilities.runOnUIThread(() -> lastError = error.text);
                                        }
                                    }, ConnectionsManager.RequestFlagFailOnServerErrors);
                                } else if (nextType == 3) {
                                    AndroidUtilities.setWaitingForSms(false);
                                    NotificationCenter.getGlobalInstance().removeObserver(PhoneConfirmationView.this, NotificationCenter.didReceiveSmsCode);
                                    waitingForEvent = false;
                                    destroyCodeTimer();
                                    resendCode();
                                }
                            }
                        }
                    });
                }
            }, 0, 1000);
        }

        private void destroyTimer() {
            try {
                synchronized (timerSync) {
                    if (timeTimer != null) {
                        timeTimer.cancel();
                        timeTimer = null;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        private String getCode() {
            if (codeField == null) {
                return "";
            }
            StringBuilder codeBuilder = new StringBuilder();
            for (int a = 0; a < codeField.length; a++) {
                codeBuilder.append(PhoneFormat.stripExceptNumbers(codeField[a].getText().toString()));
            }
            return codeBuilder.toString();
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }
            if (code == null) {
                code = getCode();
            }
            if (TextUtils.isEmpty(code)) {
                AndroidUtilities.shakeView(codeFieldContainer);
                return;
            }
            nextPressed = true;
            if (verificationType == 2) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (verificationType == 3) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            showEditDoneProgress(true, true);
            final TLRPC.TL_account_verifyPhone req = new TLRPC.TL_account_verifyPhone();
            req.phone_number = phone;
            req.phone_code = code;
            req.phone_code_hash = phoneHash;
            destroyTimer();
            needShowProgress();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                needHideProgress();
                nextPressed = false;
                if (error == null) {
                    destroyTimer();
                    destroyCodeTimer();
                    delegate.saveValue(currentType, currentValues.get("phone"), null, null, null, null, null, null, null, null, PassportActivity.this::finishFragment, null);
                } else {
                    lastError = error.text;
                    if (verificationType == 3 && (nextType == 4 || nextType == 2) || verificationType == 2 && (nextType == 4 || nextType == 3) || verificationType == 4 && nextType == 2) {
                        createTimer();
                    }
                    if (verificationType == 2) {
                        AndroidUtilities.setWaitingForSms(true);
                        NotificationCenter.getGlobalInstance().addObserver(PhoneConfirmationView.this, NotificationCenter.didReceiveSmsCode);
                    } else if (verificationType == 3) {
                        AndroidUtilities.setWaitingForCall(true);
                        NotificationCenter.getGlobalInstance().addObserver(PhoneConfirmationView.this, NotificationCenter.didReceiveCall);
                    }
                    waitingForEvent = true;
                    if (verificationType != 3) {
                        AlertsCreator.processError(currentAccount, error, PassportActivity.this, req);
                    }
                    showEditDoneProgress(true, false);
                    if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                        for (int a = 0; a < codeField.length; a++) {
                            codeField[a].setText("");
                        }
                        codeField[0].requestFocus();
                    } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                        onBackPressed(true);
                        setPage(0, true, null);
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        @Override
        public boolean onBackPressed(boolean force) {
            if (!force) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("StopVerification", R.string.StopVerification));
                builder.setPositiveButton(LocaleController.getString("Continue", R.string.Continue), null);
                builder.setNegativeButton(LocaleController.getString("Stop", R.string.Stop), (dialogInterface, i) -> {
                    onBackPressed(true);
                    setPage(0, true, null);
                });
                showDialog(builder.create());
                return false;
            }

            TLRPC.TL_auth_cancelCode req = new TLRPC.TL_auth_cancelCode();
            req.phone_number = phone;
            req.phone_code_hash = phoneHash;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

            }, ConnectionsManager.RequestFlagFailOnServerErrors);

            destroyTimer();
            destroyCodeTimer();
            currentParams = null;
            if (verificationType == 2) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (verificationType == 3) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            return true;
        }

        @Override
        public void onDestroyActivity() {
            super.onDestroyActivity();
            if (verificationType == 2) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (verificationType == 3) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            destroyTimer();
            destroyCodeTimer();
        }

        @Override
        public void onShow() {
            super.onShow();
            if (codeFieldContainer != null && codeFieldContainer.getVisibility() == VISIBLE) {
                for (int a = codeField.length - 1; a >= 0; a--) {
                    if (a == 0 || codeField[a].length() != 0) {
                        codeField[a].requestFocus();
                        codeField[a].setSelection(codeField[a].length());
                        AndroidUtilities.showKeyboard(codeField[a]);
                        break;
                    }
                }
            }
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (!waitingForEvent || codeField == null) {
                return;
            }
            if (id == NotificationCenter.didReceiveSmsCode) {
                codeField[0].setText("" + args[0]);
                onNextPressed(null);
            } else if (id == NotificationCenter.didReceiveCall) {
                String num = "" + args[0];
                if (!AndroidUtilities.checkPhonePattern(pattern, num)) {
                    return;
                }
                ignoreOnTextChange = true;
                codeField[0].setText(num);
                ignoreOnTextChange = false;
                onNextPressed(null);
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(scrollView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));
        arrayList.add(new ThemeDescription(linearLayout2, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        arrayList.add(new ThemeDescription(extraBackgroundView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        if (extraBackgroundView2 != null) {
            arrayList.add(new ThemeDescription(extraBackgroundView2, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        }

        for (int a = 0; a < dividers.size(); a++) {
            arrayList.add(new ThemeDescription(dividers.get(a), ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_divider));
        }

        for (HashMap.Entry<SecureDocument, SecureDocumentCell> entry : documentsCells.entrySet()) {
            SecureDocumentCell cell = entry.getValue();
            arrayList.add(new ThemeDescription(cell, ThemeDescription.FLAG_SELECTORWHITE, new Class[]{SecureDocumentCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(cell, 0, new Class[]{SecureDocumentCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(cell, 0, new Class[]{SecureDocumentCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        }

        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_SELECTORWHITE, new Class[]{TextDetailSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(linearLayout2, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(linearLayout2, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_SELECTORWHITE, new Class[]{TextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(linearLayout2, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(linearLayout2, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_SELECTORWHITE, new Class[]{TextDetailSecureCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextDetailSecureCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextDetailSecureCell.class}, null, null, null, Theme.key_divider));
        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextDetailSecureCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailSecureCell.class}, new String[]{"checkImageView"}, null, null, null, Theme.key_featuredStickers_addedIcon));

        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(linearLayout2, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        arrayList.add(new ThemeDescription(linearLayout2, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        arrayList.add(new ThemeDescription(linearLayout2, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        if (inputFields != null) {
            for (int a = 0; a < inputFields.length; a++) {
                arrayList.add(new ThemeDescription((View) inputFields[a].getParent(), ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_HINTTEXTCOLOR | ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
                arrayList.add(new ThemeDescription(inputFields[a], ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_windowBackgroundWhiteRedText3));
            }
        } else {
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_HINTTEXTCOLOR | ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
            arrayList.add(new ThemeDescription(null, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_windowBackgroundWhiteRedText3));
        }

        if (inputExtraFields != null) {
            for (int a = 0; a < inputExtraFields.length; a++) {
                arrayList.add(new ThemeDescription((View) inputExtraFields[a].getParent(), ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
                arrayList.add(new ThemeDescription(inputExtraFields[a], ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(inputExtraFields[a], ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
                arrayList.add(new ThemeDescription(inputExtraFields[a], ThemeDescription.FLAG_HINTTEXTCOLOR | ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
                arrayList.add(new ThemeDescription(inputExtraFields[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
                arrayList.add(new ThemeDescription(inputExtraFields[a], ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
                arrayList.add(new ThemeDescription(inputExtraFields[a], ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_windowBackgroundWhiteRedText3));
            }
        }

        arrayList.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));
        arrayList.add(new ThemeDescription(noPasswordImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_messagePanelIcons));
        arrayList.add(new ThemeDescription(noPasswordTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        arrayList.add(new ThemeDescription(noPasswordSetTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText5));
        arrayList.add(new ThemeDescription(passwordForgotButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));

        arrayList.add(new ThemeDescription(plusTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        arrayList.add(new ThemeDescription(acceptTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_passport_authorizeText));
        arrayList.add(new ThemeDescription(bottomLayout, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_passport_authorizeBackground));
        arrayList.add(new ThemeDescription(bottomLayout, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_passport_authorizeBackgroundSelected));

        arrayList.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressInner2));
        arrayList.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressOuter2));
        arrayList.add(new ThemeDescription(progressViewButton, 0, null, null, null, null, Theme.key_contextProgressInner2));
        arrayList.add(new ThemeDescription(progressViewButton, 0, null, null, null, null, Theme.key_contextProgressOuter2));

        arrayList.add(new ThemeDescription(emptyImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_sessions_devicesImage));
        arrayList.add(new ThemeDescription(emptyTextView1, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        arrayList.add(new ThemeDescription(emptyTextView2, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        arrayList.add(new ThemeDescription(emptyTextView3, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));

        return arrayList;
    }
}
