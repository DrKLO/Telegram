package org.telegram.messenger;

import android.app.Activity;

import com.google.android.recaptcha.Recaptcha;
import com.google.android.recaptcha.RecaptchaAction;

import org.telegram.tgnet.ConnectionsManager;

public class CaptchaController {

    private static class Request {
        private final int currentAccount;
        private final int requestToken;
        private final int generation;
        private boolean completed;

        private Request(int currentAccount, int requestToken, int generation) {
            this.currentAccount = currentAccount;
            this.requestToken = requestToken;
            this.generation = generation;
        }

        private void done(String token) {
            if (completed) {
                return;
            }
            completed = true;
            ConnectionsManager.native_receivedCaptchaResult(currentAccount, requestToken, generation, token);
        }
    }

    public static void request(int currentAccount, int requestToken, int generation, String action, String key_id) {
        final Request finalRequest = new Request(currentAccount, requestToken, generation);

        final Activity activity = AndroidUtilities.getActivity();
        if (activity == null) {
            FileLog.e("CaptchaController: no activity found");
            finalRequest.done("RECAPTCHA_FAILED_NO_ACTIVITY");
            return;
        }

        Recaptcha.getTasksClient(activity.getApplication(), key_id)
            .addOnSuccessListener(client -> {
                client.executeTask(getAction(action))
                    .addOnSuccessListener(token -> {
                        if (BuildVars.LOGS_ENABLED) {
                            final String tokenPreview = token == null
                                ? "null"
                                : token.substring(0, Math.min(24, token.length())) + "...";

                            FileLog.d("CaptchaController: got token for account=" + currentAccount
                                + ", request=" + requestToken
                                + ", generation=" + generation
                                + ", token=" + tokenPreview
                                + ", length=" + (token == null ? 0 : token.length()));
                        }

                        if (token == null) {
                            finalRequest.done("RECAPTCHA_FAILED_TOKEN_NULL");
                        } else {
                            finalRequest.done(token);
                        }
                    })
                    .addOnFailureListener(e -> {
                        FileLog.e("CaptchaController: executeTask failure", e);
                        finalRequest.done("RECAPTCHA_FAILED_TASK_EXCEPTION_" + formatException(e));
                    });
            })
            .addOnFailureListener(e -> {
                FileLog.e("CaptchaController: getTasksClient failure", e);
                finalRequest.done("RECAPTCHA_FAILED_GETCLIENT_EXCEPTION_" + formatException(e));
            });
    }

    private static RecaptchaAction getAction(String action) {
        switch (action) {
            case "login":
            case "LOGIN":
                return RecaptchaAction.LOGIN;
            case "signup":
            case "SIGNUP":
                return RecaptchaAction.SIGNUP;
            default:
                return RecaptchaAction.custom(action);
        }
    }

    private static String formatException(Exception e) {
        if (e == null) return "NULL";
        if (e.getMessage() == null) return "MSG_NULL";
        return e.getMessage().replaceAll(" ", "_").toUpperCase();
    }

}
