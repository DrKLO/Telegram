/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.TL.TLObject;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.SlideFragment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

public class ScreenSlidePageFragmentPhone extends SlideFragment implements AdapterView.OnItemSelectedListener {
    private Spinner countrySpinner;
    private EditText codeField;
    private EditText phoneField;

    private ArrayList<String> countriesArray = new ArrayList<String>();
    private HashMap<String, String> countriesMap = new HashMap<String, String>();
    private HashMap<String, String> codesMap = new HashMap<String, String>();
    private HashMap<String, String> languageMap = new HashMap<String, String>();

    private boolean ignoreSelection = false;
    private boolean ignoreOnTextChange = false;
    private boolean ignoreOnPhoneChange = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.login_phone_layout, container, false);

        Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
        TextView confirmTextView = (TextView)rootView.findViewById(R.id.login_confirm_text);
        confirmTextView.setTypeface(typeface);

        countrySpinner = (Spinner)rootView.findViewById(R.id.login_country_spinner);
        countrySpinner.setOnItemSelectedListener(this);
        codeField = (EditText)rootView.findViewById(R.id.login_county_code_field);
        codeField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (ignoreOnTextChange) {
                    ignoreOnTextChange = false;
                    return;
                }
                String text = codeField.getText().toString();
                if (text.indexOf("+") != 0) {
                    text = "+" + text;
                    codeField.setText(text);
                }
                text = text.replace("+", "");
                String country = codesMap.get(text);
                if (country != null) {
                    int index = countriesArray.indexOf(country);
                    if (index != -1) {
                        ignoreSelection = true;
                        countrySpinner.setSelection(index, false);

                        updatePhoneField();
                    }
                }
                codeField.setSelection(codeField.getText().length());
            }
        });
        codeField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    phoneField.requestFocus();
                    return true;
                }
                return false;
            }
        });
        phoneField = (EditText)rootView.findViewById(R.id.login_phone_field);
        phoneField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (ignoreOnPhoneChange) {
                    return;
                }
                if (count == 1 && after == 0 && s.length() > 1) {
                    String phoneChars = "0123456789";
                    String str = s.toString();
                    String substr = str.substring(start, start + 1);
                    if (!phoneChars.contains(substr)) {
                        ignoreOnPhoneChange = true;
                        StringBuilder builder = new StringBuilder(str);
                        int toDelete = 0;
                        for (int a = start; a >= 0; a--) {
                            substr = str.substring(a, a + 1);
                            if(phoneChars.contains(substr)) {
                                break;
                            }
                            toDelete++;
                        }
                        builder.delete(Math.max(0, start - toDelete), start + 1);
                        str = builder.toString();
                        if (PhoneFormat.strip(str).length() == 0) {
                            phoneField.setText("");
                        } else {
                            phoneField.setText(str);
                            updatePhoneField();
                        }
                        ignoreOnPhoneChange = false;
                    }
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
                updatePhoneField();
            }
        });

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getResources().getAssets().open("countries.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] args = line.split(";");
                countriesArray.add(0, args[2]);
                countriesMap.put(args[2], args[0]);
                codesMap.put(args[0], args[2]);
                languageMap.put(args[1], args[2]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collections.sort(countriesArray, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                return lhs.compareTo(rhs);
            }
        });

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(Utilities.applicationContext, R.layout.login_country_textview, countriesArray);
        dataAdapter.setDropDownViewResource(R.layout.login_country_dropdown);
        countrySpinner.setAdapter(dataAdapter);

        boolean codeProceed = false;

        if (savedInstanceState != null) {
            String code = savedInstanceState.getString("code");
            String phone = savedInstanceState.getString("phone");
            if (code != null && code.length() != 0) {
                codeField.setText(code);
                codeProceed = true;
            }
            if (phone != null && phone.length() != 0) {
                phoneField.setText("phone");
            }
        }

        if (!codeProceed) {
            String country = "RU";

            try {
                TelephonyManager telephonyManager = (TelephonyManager)Utilities.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                if (telephonyManager != null) {
                    country = telephonyManager.getSimCountryIso().toUpperCase();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (country == null || country.length() == 0) {
                try {
                    Locale current = Utilities.applicationContext.getResources().getConfiguration().locale;
                    country = current.getCountry().toUpperCase();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (country == null || country.length() == 0) {
                country = "RU";
            }

            String countryName = languageMap.get(country);
            if (countryName == null) {
                countryName = "Russia";
            }

            int index = countriesArray.indexOf(countryName);
            if (index != -1) {
                countrySpinner.setSelection(index, false);
            }
        }

        Utilities.showKeyboard(phoneField);
        phoneField.requestFocus();
        phoneField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    delegate.onNextAction();
                    return true;
                }
                return false;
            }
        });

        return rootView;
    }

    private void updatePhoneField() {
        ignoreOnPhoneChange = true;
        String codeText = codeField.getText().toString();
        String phone = PhoneFormat.Instance.format(codeText + phoneField.getText().toString());
        int idx = phone.indexOf(" ");
        if (idx != -1) {
            String resultCode = phone.substring(0, idx);
            if (!codeText.equals(resultCode)) {
                phone = PhoneFormat.Instance.format(phoneField.getText().toString());
                phoneField.setText(phone);
                phoneField.setSelection(phoneField.length());
            } else {
                phoneField.setText(phone.substring(idx));
                phoneField.setSelection(phoneField.length());
            }
        }
        ignoreOnPhoneChange = false;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if (ignoreSelection) {
            ignoreSelection = false;
            return;
        }
        ignoreOnTextChange = true;
        String str = countriesArray.get(i);
        codeField.setText("+" + countriesMap.get(str));
        updatePhoneField();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        delegate = null;
    }

    @Override
    public void onNextPressed() {
        TLRPC.TL_auth_sendCode req = new TLRPC.TL_auth_sendCode();
        String phone = "" + codeField.getText() + phoneField.getText();
        phone = phone.replace("+", "");
        req.api_hash = "5bce48dc7d331e62c955669eb7233217";
        req.api_id = 2458;
        req.sms_type = 0;
        req.phone_number = phone;
        req.lang_code = Locale.getDefault().getCountry();
        final String ph = phone;

        final HashMap<String, String> params = new HashMap<String, String>();
        params.put("phone", "" + codeField.getText() + phoneField.getText());
        params.put("phoneFormated", ph);

        delegate.needShowProgress();
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    final TLRPC.TL_auth_sentCode res = (TLRPC.TL_auth_sentCode)response;
                    params.put("phoneHash", res.phone_code_hash);
                    if (res.phone_registered) {
                        params.put("registered", "true");
                    }
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            delegate.didProceed(params);
                            delegate.needSlidePager(1, true);
                        }
                    });
                } else {
                    if (error.text != null) {
                        if (error.text.contains("PHONE_NUMBER_INVALID")) {
                            delegate.needShowAlert(Utilities.applicationContext.getString(R.string.InvalidPhoneNumber));
                        } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                            delegate.needShowAlert(Utilities.applicationContext.getString(R.string.InvalidCode));
                        } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                            delegate.needShowAlert(Utilities.applicationContext.getString(R.string.CodeExpired));
                        } else {
                            delegate.needShowAlert(error.text);
                        }
                    }
                }
                delegate.needHideProgress();
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    @Override
    public String getHeaderName() {
        return getResources().getString(R.string.YourPhone);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (phoneField.getText().toString().length() > 0) {
            outState.putString("phone", phoneField.getText().toString());
        }
        if (codeField.getText().toString().length() > 0) {
            outState.putString("code", codeField.getText().toString());
        }
    }
}
