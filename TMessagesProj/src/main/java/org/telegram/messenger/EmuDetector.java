package org.telegram.messenger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class EmuDetector {

    static class Property {
        public String name;
        public String seek_value;

        public Property(String name, String seek_value) {
            this.name = name;
            this.seek_value = seek_value;
        }
    }

    public interface OnEmulatorDetectorListener {
        void onResult(boolean isEmulator);
    }

    private enum EmulatorTypes {
        GENY, ANDY, NOX, BLUE, PIPES, X86
    }

    private static final String[] PHONE_NUMBERS = {
            "15555215554", "15555215556", "15555215558", "15555215560", "15555215562", "15555215564",
            "15555215566", "15555215568", "15555215570", "15555215572", "15555215574", "15555215576",
            "15555215578", "15555215580", "15555215582", "15555215584"
    };

    private static final String[] DEVICE_IDS = {
            "000000000000000",
            "e21833235b6eef10",
            "012345678912345"
    };

    private static final String[] IMSI_IDS = {
            "310260000000000"
    };

    private static final String[] GENY_FILES = {
            "/dev/socket/genyd",
            "/dev/socket/baseband_genyd"
    };

    private static final String[] QEMU_DRIVERS = {"goldfish"};

    private static final String[] PIPES = {
            "/dev/socket/qemud",
            "/dev/qemu_pipe"
    };

    private static final String[] X86_FILES = {
            "ueventd.android_x86.rc",
            "x86.prop",
            "ueventd.ttVM_x86.rc",
            "init.ttVM_x86.rc",
            "fstab.ttVM_x86",
            "fstab.vbox86",
            "init.vbox86.rc",
            "ueventd.vbox86.rc"
    };

    private static final String[] ANDY_FILES = {
            "fstab.andy",
            "ueventd.andy.rc"
    };

    private static final String[] NOX_FILES = {
            "fstab.nox",
            "init.nox.rc",
            "ueventd.nox.rc",
            "/BigNoxGameHD",
            "/YSLauncher"
    };

    private static final String[] BLUE_FILES = {
            "/Android/data/com.bluestacks.home",
            "/Android/data/com.bluestacks.settings"
    };

    private static final Property[] PROPERTIES = {
            new Property("init.svc.qemud", null),
            new Property("init.svc.qemu-props", null),
            new Property("qemu.hw.mainkeys", null),
            new Property("qemu.sf.fake_camera", null),
            new Property("qemu.sf.lcd_density", null),
            new Property("ro.bootloader", "unknown"),
            new Property("ro.bootmode", "unknown"),
            new Property("ro.hardware", "goldfish"),
            new Property("ro.kernel.android.qemud", null),
            new Property("ro.kernel.qemu.gles", null),
            new Property("ro.kernel.qemu", "1"),
            new Property("ro.product.device", "generic"),
            new Property("ro.product.model", "sdk"),
            new Property("ro.product.name", "sdk"),
            new Property("ro.serialno", null)
    };

    private static final String IP = "10.0.2.15";

    private static final int MIN_PROPERTIES_THRESHOLD = 0x5;

    private final Context mContext;
    private boolean isTelephony = false;
    private boolean isCheckPackage = true;
    private List<String> mListPackageName = new ArrayList<>();

    private boolean detected;
    private boolean detectResult;

    @SuppressLint("StaticFieldLeak") //Since we use application context now this won't leak memory anymore. This is only to please Lint
    private static EmuDetector mEmulatorDetector;

    public static EmuDetector with(Context pContext) {
        if (pContext == null) {
            throw new IllegalArgumentException("Context must not be null.");
        }
        if (mEmulatorDetector == null) {
            mEmulatorDetector = new EmuDetector(pContext.getApplicationContext());
        }
        return mEmulatorDetector;
    }

    private EmuDetector(Context pContext) {
        mContext = pContext;
        mListPackageName.add("com.google.android.launcher.layouts.genymotion");
        mListPackageName.add("com.bluestacks");
        mListPackageName.add("com.bignox.app");
        mListPackageName.add("com.vphone.launcher");
    }

    public boolean isCheckTelephony() {
        return isTelephony;
    }

    public boolean isCheckPackage() {
        return isCheckPackage;
    }

    public EmuDetector setCheckTelephony(boolean telephony) {
        this.isTelephony = telephony;
        return this;
    }

    public EmuDetector setCheckPackage(boolean chkPackage) {
        this.isCheckPackage = chkPackage;
        return this;
    }

    public EmuDetector addPackageName(String pPackageName) {
        this.mListPackageName.add(pPackageName);
        return this;
    }

    public EmuDetector addPackageName(List<String> pListPackageName) {
        this.mListPackageName.addAll(pListPackageName);
        return this;
    }

    public boolean detect() {
        if (detected) {
            return detectResult;
        }
        try {
            detected = true;
            if (!detectResult) {
                detectResult = checkBasic();
            }
            if (!detectResult) {
                detectResult = checkAdvanced();
            }
            if (!detectResult) {
                detectResult = checkPackageName();
            }
            if (!detectResult) {
                detectResult = EmuInputDevicesDetector.detect();
            }
            return detectResult;
        } catch (Exception ignore) {

        }
        return false;
    }

    private boolean checkBasic() {
        boolean result =
                Build.BOARD.toLowerCase().contains("nox")
                        || Build.BOOTLOADER.toLowerCase().contains("nox")
                        || Build.FINGERPRINT.startsWith("generic")
                        || Build.MODEL.toLowerCase().contains("google_sdk")
                        || Build.MODEL.toLowerCase().contains("droid4x")
                        || Build.MODEL.toLowerCase().contains("emulator")
                        || Build.MODEL.contains("Android SDK built for x86")
                        || Build.MANUFACTURER.toLowerCase().contains("genymotion")
                        || Build.HARDWARE.toLowerCase().contains("goldfish")
                        || Build.HARDWARE.toLowerCase().contains("vbox86")
                        || Build.HARDWARE.toLowerCase().contains("android_x86")
                        || Build.HARDWARE.toLowerCase().contains("nox")
                        || Build.HARDWARE.toLowerCase().contains("ranchu")
                        || Build.PRODUCT.equals("sdk")
                        || Build.PRODUCT.equals("google_sdk")
                        || Build.PRODUCT.equals("sdk_x86")
                        || Build.PRODUCT.equals("vbox86p")
                        || Build.PRODUCT.toLowerCase().contains("nox")
                        || Build.SERIAL.toLowerCase().contains("nox");

        if (result) {
            return true;
        }
        result |= Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic");
        if (result) {
            return true;
        }
        result |= "google_sdk".equals(Build.PRODUCT);
        return result;
    }

    private boolean checkAdvanced() {
        return checkTelephony()
                || checkFiles(GENY_FILES, EmulatorTypes.GENY)
                || checkFiles(ANDY_FILES, EmulatorTypes.ANDY)
                || checkFiles(NOX_FILES, EmulatorTypes.NOX)
                || checkFiles(BLUE_FILES, EmulatorTypes.BLUE)
                || checkQEmuDrivers()
                || checkFiles(PIPES, EmulatorTypes.PIPES)
                || checkIp()
                || (checkQEmuProps() && checkFiles(X86_FILES, EmulatorTypes.X86));
    }

    private boolean checkPackageName() {
        if (!isCheckPackage || mListPackageName.isEmpty()) {
            return false;
        }
        final PackageManager packageManager = mContext.getPackageManager();
        for (final String pkgName : mListPackageName) {
            final Intent tryIntent = packageManager.getLaunchIntentForPackage(pkgName);
            if (tryIntent != null) {
                final List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(tryIntent, PackageManager.MATCH_DEFAULT_ONLY);
                if (!resolveInfos.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkTelephony() {
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED && this.isTelephony && isSupportTelePhony() && (checkPhoneNumber() || checkDeviceId() || checkImsi() || checkOperatorNameAndroid());
    }

    private boolean checkPhoneNumber() {
        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        @SuppressLint("HardwareIds") String phoneNumber = telephonyManager.getLine1Number();

        for (String number : PHONE_NUMBERS) {
            if (number.equalsIgnoreCase(phoneNumber)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkDeviceId() {
        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        @SuppressLint("HardwareIds") String deviceId = telephonyManager.getDeviceId();

        for (String known_deviceId : DEVICE_IDS) {
            if (known_deviceId.equalsIgnoreCase(deviceId)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkImsi() {
        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        @SuppressLint("HardwareIds") String imsi = telephonyManager.getSubscriberId();

        for (String known_imsi : IMSI_IDS) {
            if (known_imsi.equalsIgnoreCase(imsi)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkOperatorNameAndroid() {
        String operatorName = ((TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE)).getNetworkOperatorName();
        return operatorName.equalsIgnoreCase("android");
    }

    private boolean checkQEmuDrivers() {
        for (File drivers_file : new File[]{new File("/proc/tty/drivers"), new File("/proc/cpuinfo")}) {
            if (drivers_file.exists() && drivers_file.canRead()) {
                byte[] data = new byte[1024];
                try {
                    InputStream is = new FileInputStream(drivers_file);
                    is.read(data);
                    is.close();
                } catch (Exception exception) {
                    exception.printStackTrace();
                }

                String driver_data = new String(data);
                for (String known_qemu_driver : QEMU_DRIVERS) {
                    if (driver_data.contains(known_qemu_driver)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean checkFiles(String[] targets, EmulatorTypes type) { //TODO scoped storage
        for (String pipe : targets) {
            File qemu_file;
            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                if ((pipe.contains("/") && type == EmulatorTypes.NOX) || type == EmulatorTypes.BLUE) {
                    qemu_file = new File(Environment.getExternalStorageDirectory() + pipe);
                } else {
                    qemu_file = new File(pipe);
                }
            } else {
                qemu_file = new File(pipe);
            }
            if (qemu_file.exists()) {
                return true;
            }
        }
        return false;
    }

    private boolean checkQEmuProps() {
        int found_props = 0;

        for (Property property : PROPERTIES) {
            String property_value = getProp(mContext, property.name);
            if ((property.seek_value == null) && (property_value != null)) {
                found_props++;
            }
            if ((property.seek_value != null)
                    && (property_value.contains(property.seek_value))) {
                found_props++;
            }
        }

        return found_props >= MIN_PROPERTIES_THRESHOLD;
    }

    private boolean checkIp() {
        boolean ipDetected = false;
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
            String[] args = {"/system/bin/netcfg"};
            StringBuilder stringBuilder = new StringBuilder();
            try {
                ProcessBuilder builder = new ProcessBuilder(args);
                builder.directory(new File("/system/bin/"));
                builder.redirectErrorStream(true);
                Process process = builder.start();
                InputStream in = process.getInputStream();
                byte[] re = new byte[1024];
                while (in.read(re) != -1) {
                    stringBuilder.append(new String(re));
                }
                in.close();

            } catch (Exception ex) {
                // empty catch
            }

            String netData = stringBuilder.toString();

            if (!TextUtils.isEmpty(netData)) {
                String[] array = netData.split("\n");

                for (String lan :
                        array) {
                    if ((lan.contains("wlan0") || lan.contains("tunl0") || lan.contains("eth0")) && lan.contains(IP)) {
                        ipDetected = true;
                        break;
                    }
                }

            }
        }
        return ipDetected;
    }

    private String getProp(Context context, String property) {
        try {
            ClassLoader classLoader = context.getClassLoader();
            Class<?> systemProperties = classLoader.loadClass("android.os.SystemProperties");

            Method get = systemProperties.getMethod("get", String.class);

            Object[] params = new Object[1];
            params[0] = property;

            return (String) get.invoke(systemProperties, params);
        } catch (Exception exception) {
            // empty catch
        }
        return null;
    }

    private boolean isSupportTelePhony() {
        PackageManager packageManager = mContext.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }
}
