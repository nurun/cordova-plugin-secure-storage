package com.crypho.plugins;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Hashtable;

public class SecureStorage extends CordovaPlugin {
    private static final String TAG = "SecureStorage";

    private static final boolean SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

    private static final String MSG_NOT_SUPPORTED = "API 21 (Android 5.0 Lollipop) is required. This device is running API " + Build.VERSION.SDK_INT;
    private static final String MSG_DEVICE_NOT_SECURE = "Device is not secure";

    private Hashtable<String, DatabaseManager> SERVICE_STORAGE = new Hashtable<String, DatabaseManager>();
    private String INIT_SERVICE;
    private String INIT_PACKAGENAME;
    private volatile CallbackContext initContext, secureDeviceContext;
    private volatile boolean initContextRunning = false;

    @Override
    public void onResume(boolean multitasking) {
        if (secureDeviceContext != null) {
            if (isDeviceSecure()) {
                secureDeviceContext.success();
            } else {
                secureDeviceContext.error(MSG_DEVICE_NOT_SECURE);
            }
            secureDeviceContext = null;
        }

        if (initContext != null && !initContextRunning) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    initContextRunning = true;
                    try {
                        String alias = service2alias(INIT_SERVICE);
                        if (!RSA.isEntryAvailable(alias)) {
                            //Solves Issue #96. The RSA key may have been deleted by changing the lock type.
                            getStorage(INIT_SERVICE).clear();
                            RSA.createKeyPair(getContext(), alias);
                        }
                        initSuccess(initContext);
                    } catch (Exception e) {
                        Log.e(TAG, "Init failed :", e);
                        initContext.error(e.getMessage());
                    } finally {
                        initContext = null;
                        initContextRunning = false;
                    }
                }
            });
        }
    }

    @Override
    public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        if (!SUPPORTED) {
            Log.w(TAG, MSG_NOT_SUPPORTED);
            callbackContext.error(MSG_NOT_SUPPORTED);
            return false;
        }
        if ("init".equals(action)) {
            String service = args.getString(0);
            JSONObject options = args.getJSONObject(1);
            String packageName = options.optString("packageName", getContext().getPackageName());

            Context ctx = null;

            // Solves #151. By default, we use our own ApplicationContext
            // If packageName is provided, we try to get the Context of another Application with that packageName
            try {
                ctx = getPackageContext(packageName);
            } catch (Exception e) {
                // This will fail if the application with given packageName is not installed
                // OR if we do not have required permissions and cause a security violation
                Log.e(TAG, "Init failed :", e);
                callbackContext.error(e.getMessage());
            }

            INIT_PACKAGENAME = ctx.getPackageName();
            String alias = service2alias(service);
            INIT_SERVICE = service;


            if (!isDeviceSecure()) {
                Log.e(TAG, MSG_DEVICE_NOT_SECURE);
                callbackContext.error(MSG_DEVICE_NOT_SECURE);
            } else if (!RSA.isEntryAvailable(alias)) {
                initContext = callbackContext;
                unlockCredentials();
            } else {
                DatabaseManager databaseManager = new DatabaseManager(ctx, service, INIT_PACKAGENAME + "secure_storage");
                SERVICE_STORAGE.put(service, databaseManager);
                initSuccess(callbackContext);
            }

            return true;
        }
        if ("set".equals(action)) {
            final String service = args.getString(0);
            final String key = args.getString(1);
            final String value = args.getString(2);
            final String adata = service;

            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        getStorage(service).set(key, value);
                    } catch (Exception e) {
                        Log.e(TAG, "Encrypt failed :", e);
                        callbackContext.error(e.getMessage());
                    }
                    callbackContext.success(key);
                }
            });
            return true;
        }
        if ("get".equals(action)) {
            final String service = args.getString(0);
            final String key = args.getString(1);
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    String value = getStorage(service).get(key);
                    callbackContext.success(value);
                }
            });
            return true;
        }
        if ("secureDevice".equals(action)) {
            secureDeviceContext = callbackContext;
            unlockCredentials();
            return true;
        }
        if ("remove".equals(action)) {
            String service = args.getString(0);
            String key = args.getString(1);
            getStorage(service).remove(key);
            callbackContext.success(key);
            return true;
        }
        if ("keys".equals(action)) {
            String service = args.getString(0);
            callbackContext.success(new JSONArray(getStorage(service).keys()));
            return true;
        }
        if ("clear".equals(action)) {
            String service = args.getString(0);
            getStorage(service).clear();
            callbackContext.success();
            return true;
        }
        return false;
    }

    private boolean isDeviceSecure() {
        KeyguardManager keyguardManager = (KeyguardManager) (getContext().getSystemService(Context.KEYGUARD_SERVICE));
        try {
            Method isSecure = null;
            isSecure = keyguardManager.getClass().getMethod("isDeviceSecure");
            return ((Boolean) isSecure.invoke(keyguardManager)).booleanValue();
        } catch (Exception e) {
            return keyguardManager.isKeyguardSecure();
        }
    }

    private String service2alias(String service) {
        String res = INIT_PACKAGENAME + "." + service;
        return res;
    }

    private DatabaseManager getStorage(String service) {
        return SERVICE_STORAGE.get(service);
    }

    private void initSuccess(CallbackContext context) {
        context.success();
    }

    private void unlockCredentials() {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                Intent intent = new Intent("com.android.credentials.UNLOCK");
                startActivity(intent);
            }
        });
    }

    private Context getContext() {
        return cordova.getActivity().getApplicationContext();
    }

    private Context getPackageContext(String packageName) throws Exception {
        Context pkgContext = null;

        Context context = getContext();
        if (context.getPackageName().equals(packageName)) {
            pkgContext = context;
        } else {
            pkgContext = context.createPackageContext(packageName, 0);
        }

        return pkgContext;
    }

    private void startActivity(Intent intent) {
        cordova.getActivity().startActivity(intent);
    }

}
