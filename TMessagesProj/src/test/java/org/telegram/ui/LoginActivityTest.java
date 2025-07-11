package org.telegram.ui;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;

import me.telegraphy.android.TdApiManager;
import me.telegraphy.android.DatabaseManager;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tdlib.TdApi;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;


@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Config.OLDEST_SDK}, application = ApplicationLoader.class, manifest = Config.NONE)
public class LoginActivityTest {

    @Mock private TdApiManager mockTdApiManager;
    // @Mock private DatabaseManager mockDatabaseManager; // DatabaseManager interactions are less critical for these tests
    @Mock private LoginActivity.PhoneView mockPhoneView;
    @Mock private LoginActivity.LoginActivitySmsView mockSmsView;
    @Mock private LoginActivity.LoginActivityPasswordView mockPasswordView;
    @Mock private LoginActivity.LoginActivityRegisterView mockRegisterView;


    @Captor private ArgumentCaptor<Integer> notificationIdCaptor;
    @Captor private ArgumentCaptor<Object[]> notificationArgsCaptor;
    @Captor private ArgumentCaptor<Bundle> bundleCaptor;


    private ActivityController<LaunchActivity> activityController;
    private LoginActivity loginActivity;
    private LaunchActivity launchActivity;
    private NotificationCenter realNotificationCenter;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Mock ApplicationLoader.applicationContext and its resources
        ApplicationLoader.applicationContext = mock(android.content.Context.class, RETURNS_DEEP_STUBS);
        android.content.res.Resources mockResources = mock(android.content.res.Resources.class, RETURNS_DEEP_STUBS);
        when(ApplicationLoader.applicationContext.getResources()).thenReturn(mockResources);
        when(mockResources.getConfiguration()).thenReturn(new android.content.res.Configuration());
        when(ApplicationLoader.applicationContext.getApplicationInfo()).thenReturn(new android.content.pm.ApplicationInfo());
        when(ApplicationLoader.applicationContext.getPackageName()).thenReturn("org.telegram.messenger");


        // Get the real NotificationCenter instance to post notifications for testing
        // LoginActivity will register itself with this real instance.
        realNotificationCenter = NotificationCenter.getInstance(UserConfig.selectedAccount); // Assuming default account

        // Since TdApiManager is a singleton accessed via getInstance(),
        // we will mock the instance it holds if LoginActivity assigns it to a field we can access.
        // If not, we will have to assume TdApiManager posts correctly and test LoginActivity's reaction.
        // For now, let's try to replace the tdApiManager field in LoginActivity after it's created.

        activityController = Robolectric.buildActivity(LaunchActivity.class);
        launchActivity = activityController.create().start().resume().get();

        loginActivity = new LoginActivity();

        // Simulate adding the fragment to the activity
        launchActivity.getSupportFragmentManager().beginTransaction().add(loginActivity, null).commitNowAllowingStateLoss();


        // Trigger fragment's lifecycle to create views and initialize managers
        loginActivity.onFragmentCreate(); // This initializes tdApiManager with TdApiManager.getInstance()
                                      // and registers with NotificationCenter

        // Now, try to replace the real TdApiManager instance in loginActivity with our mock
        // This requires tdApiManager field to be accessible (e.g. package-private or via setter)
        // If it's private and final, this is hard without PowerMock or refactoring.
        // Let's assume we can set it for testing:
        try {
            java.lang.reflect.Field tdManagerField = LoginActivity.class.getDeclaredField("tdApiManager");
            tdManagerField.setAccessible(true);
            tdManagerField.set(loginActivity, mockTdApiManager);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // If this fails, tests verifying calls *to* tdApiManager will be harder.
            // We can still test reactions to NotificationCenter events.
            System.err.println("Warning: Could not directly mock tdApiManager field. " + e.getMessage());
        }

        // Ensure views are created by getView()
        View fragmentView = loginActivity.getView();
        assertNotNull(fragmentView); // Ensures createView was called
        ShadowLooper.idleMainLooper(); // Process posted Runnables

        // Mock the behavior of TdApiManager when its methods are called
        // Set a default initial state for TdApiManager mock
        when(mockTdApiManager.getAuthorizationState()).thenReturn(new TdApi.AuthorizationStateWaitPhoneNumber());
    }

    @Test
    public void whenCreated_initialStateShouldBePhoneInput() {
    public void whenCreated_initialStateShouldBePhoneInput() {
        // Simulate the initial auth state being delivered via NotificationCenter
        // after onFragmentCreate (which happens in setUp)
        realNotificationCenter.postNotificationName(NotificationCenter.didReceiveAuthorizationStateChange, mockTdApiManager.getAuthorizationState());
        ShadowLooper.idleMainLooper();

        assertEquals("Current view should be VIEW_PHONE_INPUT", LoginActivity.VIEW_PHONE_INPUT, loginActivity.getCurrentViewNum());
        assertNotNull("Phone input view should not be null", loginActivity.views[LoginActivity.VIEW_PHONE_INPUT]);
        assertTrue("Phone input view should be visible", loginActivity.views[LoginActivity.VIEW_PHONE_INPUT].getVisibility() == View.VISIBLE);
    }

    @Test
    public void phoneView_onNextPressed_callsTdApiManagerSetAuthenticationPhoneNumber() {
        // Ensure initial state is phone input
        realNotificationCenter.postNotificationName(NotificationCenter.didReceiveAuthorizationStateChange, new TdApi.AuthorizationStateWaitPhoneNumber());
        ShadowLooper.idleMainLooper();

        LoginActivity.PhoneView phoneView = (LoginActivity.PhoneView) loginActivity.views[LoginActivity.VIEW_PHONE_INPUT];
        assertNotNull("PhoneView is null", phoneView);

        // Setup PhoneView fields - direct access for simplicity in test
        // In a real scenario, use Robolectric to find and interact with UI elements.
        EditText codeField = phoneView.findViewById(R.id.login_code_field); // Assuming IDs exist
        EditText phoneField = phoneView.findViewById(R.id.login_phone_field);

        // Fallback if IDs are not set - this is brittle and relies on internal field names.
        if (codeField == null) codeField = getInternalState(phoneView, "codeField");
        if (phoneField == null) phoneField = getInternalState(phoneView, "phoneField");

        assertNotNull("codeField is null", codeField);
        assertNotNull("phoneField is null", phoneField);

        codeField.setText("1");
        phoneField.setText("1234567890");
        setInternalState(phoneView, "currentCountry", new CountrySelectActivity.Country()); // Simplified
        ((CountrySelectActivity.Country)getInternalState(phoneView, "currentCountry")).code = "1";
        setInternalState(phoneView, "confirmedNumber", true); // Bypass popup

        phoneView.onNextPressed(null); // Trigger the action

        ArgumentCaptor<String> phoneCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TdApi.PhoneNumberAuthenticationSettings> settingsCaptor =
                ArgumentCaptor.forClass(TdApi.PhoneNumberAuthenticationSettings.class);

        verify(mockTdApiManager).setAuthenticationPhoneNumber(phoneCaptor.capture(), settingsCaptor.capture());
        assertEquals("11234567890", phoneCaptor.getValue());
    }

    @Test
    public void onAuthWaitCode_transitionsToSmsViewAndSetsParams() {
        // Store some params as if phone was submitted
        loginActivity.currentParams = new Bundle();
        loginActivity.currentParams.putString("phoneFormated", "11234567890");
        loginActivity.currentParams.putString("phone", "+1 123-456-7890");


        TdApi.AuthenticationCodeInfo codeInfo = new TdApi.AuthenticationCodeInfo();
        codeInfo.phoneNumber = "+11234567890"; // Matches currentParams for consistency
        codeInfo.type = new TdApi.AuthenticationCodeTypeSms(5);
        codeInfo.nextType = new TdApi.AuthenticationCodeTypeCall(); // Example next type
        codeInfo.timeout = 60; // seconds

        TdApi.AuthorizationStateWaitCode authState = new TdApi.AuthorizationStateWaitCode(codeInfo);

        realNotificationCenter.postNotificationName(NotificationCenter.didReceiveAuthorizationStateChange, authState);
        ShadowLooper.idleMainLooper();

        assertEquals("Should transition to VIEW_CODE_SMS", LoginActivity.VIEW_CODE_SMS, loginActivity.getCurrentViewNum());
        LoginActivity.LoginActivitySmsView smsView = (LoginActivity.LoginActivitySmsView) loginActivity.views[LoginActivity.VIEW_CODE_SMS];
        assertNotNull("SmsView should not be null", smsView);
        assertTrue("SmsView should be visible", smsView.getVisibility() == View.VISIBLE);

        // Verify that setParams was called on the smsView with correct data
        // This requires access to the params passed to smsView.setParams or verifying its state.
        Bundle viewParams = getInternalState(smsView, "currentParams");
        assertNotNull("SmsView currentParams should not be null", viewParams);
        assertEquals(5, viewParams.getInt("length"));
        assertEquals(60000, viewParams.getInt("timeout")); // timeout is in ms
        assertEquals(LoginActivity.AUTH_TYPE_SMS, viewParams.getInt("type"));
        assertEquals(LoginActivity.AUTH_TYPE_CALL, viewParams.getInt("nextType"));
        assertEquals(loginActivity.currentParams.getString("phone"), viewParams.getString("phone"));
    }

    @Test
    public void smsView_onNextPressed_callsTdApiManagerCheckAuthenticationCode() {
        // 1. Setup LoginActivity to be in VIEW_CODE_SMS state
        loginActivity.currentParams = new Bundle(); // Simulate params from phone submission
        loginActivity.currentParams.putString("phoneFormated", "11234567890");
        loginActivity.currentParams.putString("phoneHash", "testhash"); // phoneHash might still be used by view logic

        TdApi.AuthenticationCodeInfo codeInfo = new TdApi.AuthenticationCodeInfo();
        codeInfo.type = new TdApi.AuthenticationCodeTypeSms(5); // Length 5
        TdApi.AuthorizationStateWaitCode waitCodeState = new TdApi.AuthorizationStateWaitCode(codeInfo);
        realNotificationCenter.postNotificationName(NotificationCenter.didReceiveAuthorizationStateChange, waitCodeState);
        ShadowLooper.idleMainLooper();

        assertEquals(LoginActivity.VIEW_CODE_SMS, loginActivity.getCurrentViewNum());
        LoginActivity.LoginActivitySmsView smsView = (LoginActivity.LoginActivitySmsView) loginActivity.views[LoginActivity.VIEW_CODE_SMS];
        assertNotNull(smsView);

        // 2. Simulate code input and trigger onNextPressed
        // Directly calling onNextPressed on the view instance
        smsView.onNextPressed("12345"); // The code being submitted

        // 3. Verify TdApiManager.checkAuthenticationCode was called
        verify(mockTdApiManager).checkAuthenticationCode("12345");
    }

    @Test
    public void onAuthWaitPassword_transitionsToPasswordViewAndSetsParams() {
        loginActivity.currentParams = new Bundle(); // Simulate previous state
        loginActivity.currentParams.putString("phoneFormated", "11234567890");
        loginActivity.currentParams.putString("phoneHash", "somehash");
        loginActivity.currentParams.putString("code", "prevcode");


        TdApi.PasswordState passwordState = new TdApi.PasswordState(true, "password_hint", true, "recovery_pattern@example.com");
        TdApi.AuthorizationStateWaitPassword authState = new TdApi.AuthorizationStateWaitPassword(
            "password_hint", true, false, "recovery_pattern@example.com", passwordState
        );

        realNotificationCenter.postNotificationName(NotificationCenter.didReceiveAuthorizationStateChange, authState);
        ShadowLooper.idleMainLooper();

        assertEquals(LoginActivity.VIEW_PASSWORD, loginActivity.getCurrentViewNum());
        LoginActivity.LoginActivityPasswordView passwordView = (LoginActivity.LoginActivityPasswordView) loginActivity.views[LoginActivity.VIEW_PASSWORD];
        assertNotNull(passwordView);
        assertTrue(passwordView.getVisibility() == View.VISIBLE);

        Bundle viewParams = getInternalState(passwordView, "currentParams");
        assertNotNull(viewParams);
        assertTrue(viewParams.containsKey("password")); // Serialized TL_account.Password
        assertEquals("password_hint", ((TL_account.Password)TLRPC.TLClassStore.Instance().TLdeserialize(new TLRPC.TL_user(), Utilities.hexToBytes(viewParams.getString("password")), 0, false)).hint);
    }

    @Test
    public void passwordView_onNextPressed_callsTdApiManagerCheckAuthenticationPassword() {
        // Setup: Transition to Password view
        loginActivity.currentParams = new Bundle();
        TL_account.Password mockTlPassword = new TL_account.Password(); // For the view's internal logic
        mockTlPassword.current_algo = new TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow();
        SerializedData data = new SerializedData(mockTlPassword.getObjectSize());
        mockTlPassword.serializeToStream(data);
        loginActivity.currentParams.putString("password", Utilities.bytesToHex(data.toByteArray()));


        TdApi.PasswordState passwordState = new TdApi.PasswordState(true, "hint", true, "pattern");
        TdApi.AuthorizationStateWaitPassword authState = new TdApi.AuthorizationStateWaitPassword("hint", true, false, "pattern", passwordState);
        realNotificationCenter.postNotificationName(NotificationCenter.didReceiveAuthorizationStateChange, authState);
        ShadowLooper.idleMainLooper();

        LoginActivity.LoginActivityPasswordView passwordView = (LoginActivity.LoginActivityPasswordView) loginActivity.views[LoginActivity.VIEW_PASSWORD];
        assertNotNull(passwordView);

        EditTextBoldCursor passwordField = getInternalState(passwordView, "codeField");
        passwordField.setText("testpassword");
        setInternalState(passwordView, "currentPassword", mockTlPassword); // Ensure view has its expected TLRPC.Password

        passwordView.onNextPressed(null);

        verify(mockTdApiManager).checkAuthenticationPassword("testpassword");
    }

    @Test
    public void onAuthWaitRegistration_transitionsToRegisterViewAndSetsTos() {
        loginActivity.currentParams = new Bundle(); // Simulate previous state
        loginActivity.currentParams.putString("phoneFormated", "11234567890");
        loginActivity.currentParams.putString("phoneHash", "somehash");
        loginActivity.currentParams.putString("code", "prevcode");


        TdApi.TermsOfService tos = new TdApi.TermsOfService(new TdApi.FormattedText("TOS Text", new TdApi.TextEntity[0]), 18, true);
        TdApi.AuthorizationStateWaitRegistration authState = new TdApi.AuthorizationStateWaitRegistration(tos);

        realNotificationCenter.postNotificationName(NotificationCenter.didReceiveAuthorizationStateChange, authState);
        ShadowLooper.idleMainLooper();

        assertEquals(LoginActivity.VIEW_REGISTER, loginActivity.getCurrentViewNum());
        LoginActivity.LoginActivityRegisterView registerView = (LoginActivity.LoginActivityRegisterView) loginActivity.views[LoginActivity.VIEW_REGISTER];
        assertNotNull(registerView);
        assertTrue(registerView.getVisibility() == View.VISIBLE);

        TLRPC.TL_help_termsOfService viewTos = getInternalState(registerView, "currentTermsOfService");
        assertNotNull(viewTos);
        assertEquals("TOS Text", viewTos.text);
    }

    @Test
    public void registerView_onNextPressed_callsTdApiManagerRegisterUser() {
        // Setup: Transition to Register view
        loginActivity.currentParams = new Bundle();
        TdApi.TermsOfService tos = new TdApi.TermsOfService(new TdApi.FormattedText("TOS", new TdApi.TextEntity[0]), 0, false);
        TdApi.AuthorizationStateWaitRegistration authState = new TdApi.AuthorizationStateWaitRegistration(tos);
        realNotificationCenter.postNotificationName(NotificationCenter.didReceiveAuthorizationStateChange, authState);
        ShadowLooper.idleMainLooper();

        LoginActivity.LoginActivityRegisterView registerView = (LoginActivity.LoginActivityRegisterView) loginActivity.views[LoginActivity.VIEW_REGISTER];
        assertNotNull(registerView);

        EditTextBoldCursor firstNameField = getInternalState(registerView, "firstNameField");
        EditTextBoldCursor lastNameField = getInternalState(registerView, "lastNameField");
        firstNameField.setText("TestFirst");
        lastNameField.setText("TestLast");

        // Bypass TOS popup for the test by setting popup to false on the stored ToS object
        TLRPC.TL_help_termsOfService currentTos = getInternalState(registerView, "currentTermsOfService");
        if (currentTos != null) {
            currentTos.popup = false;
        } else { // If it wasn't set, create a dummy one
            currentTos = new TLRPC.TL_help_termsOfService();
            currentTos.popup = false;
            setInternalState(registerView, "currentTermsOfService", currentTos);
        }


        registerView.onNextPressed(null);

        verify(mockTdApiManager).registerUser("TestFirst", "TestLast");
    }

    @Test
    public void onAuthReady_callsOnAuthSuccessAndFinishes() {
        LoginActivity spiedLoginActivity = spy(loginActivity);
        // Manually inject mockTdApiManager into the spied instance
         try {
            java.lang.reflect.Field tdManagerField = LoginActivity.class.getDeclaredField("tdApiManager");
            tdManagerField.setAccessible(true);
            tdManagerField.set(spiedLoginActivity, mockTdApiManager);
        } catch (Exception e) {
            fail("Failed to inject mock TdApiManager into spied LoginActivity: " + e.getMessage());
        }


        TdApi.User mockTdUser = new TdApi.User();
        mockTdUser.id = 12345L;
        mockTdUser.firstName = "Test";
        mockTdUser.lastName = "User";
        mockTdUser.phoneNumber = "11234567890";
        mockTdUser.type = new TdApi.UserTypeRegular();

        when(mockTdApiManager.getCurrentUser()).thenReturn(mockTdUser); // Used by handleAuthorizationState

        TdApi.AuthorizationStateReady authStateReady = new TdApi.AuthorizationStateReady();
        realNotificationCenter.postNotificationName(NotificationCenter.didReceiveAuthorizationStateChange, authStateReady);
        ShadowLooper.idleMainLooper();

        ArgumentCaptor<TLRPC.TL_auth_authorization> authCaptor = ArgumentCaptor.forClass(TLRPC.TL_auth_authorization.class);
        // Verify that onAuthSuccess was called (it might be called with a null TLRPC.TL_auth_authorization if conversion fails or if that's how it's designed)
        // The important part is that the flow reaches onAuthSuccess.
        verify(spiedLoginActivity, atLeastOnce()).onAuthSuccess(authCaptor.capture(), anyBoolean());

        TLRPC.User capturedUser = authCaptor.getValue().user;
        assertNotNull("Captured user should not be null", capturedUser);
        assertEquals(mockTdUser.id, capturedUser.id);
        assertEquals(mockTdUser.firstName, capturedUser.first_name);

        // Further verification that finishFragment or navigation occurs would be good.
        // For now, verifying onAuthSuccess is the key interaction.
        assertTrue("LaunchActivity should be finishing or have presented a new fragment",
            launchActivity.isFinishing() || launchActivity.getSupportFragmentManager().getFragments().size() > 1);
    }

    @Test
    public void onErrorNotification_showsAlertDialog() {
        // Ensure a view is active to show the dialog on
        realNotificationCenter.postNotificationName(NotificationCenter.didReceiveAuthorizationStateChange, new TdApi.AuthorizationStateWaitPhoneNumber());
        ShadowLooper.idleMainLooper();

        TdApi.Error error = new TdApi.Error(400, "PHONE_NUMBER_INVALID");

        realNotificationCenter.postNotificationName(NotificationCenter.didFailtToReceiveAuthorizationStateChange, error);
        ShadowLooper.idleMainLooper();

        AlertDialog latestAlertDialog = AlertDialog.getLatestAlertDialog();
        assertNotNull("An AlertDialog should be shown on error", latestAlertDialog);
        assertTrue("AlertDialog should be showing", latestAlertDialog.isShowing());
        // TODO: Verify dialog title/message if possible by inspecting the dialog's views
        // This often requires a custom DialogFragment or more complex Robolectric dialog shadows.
    }

    // Helper to access internal fields for test setup. Use sparingly.
    @SuppressWarnings("unchecked")
    private <T> T getInternalState(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to get internal state: " + fieldName, e);
        }
    }
     private void setInternalState(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set internal state: " + fieldName, e);
        }
    }
}
