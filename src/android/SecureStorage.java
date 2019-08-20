package com.crypho.plugins;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Hashtable;

import static androidx.room.Room.databaseBuilder;

public class SecureStorage extends CordovaPlugin {
    private static final String TAG = "SecureStorage";

    private static final boolean SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

    private static final String MSG_NOT_SUPPORTED = "API 21 (Android 5.0 Lollipop) is required. This device is running API " + Build.VERSION.SDK_INT;
    private static final String MSG_DEVICE_NOT_SECURE = "Device is not secure";

    private Hashtable<String, DataDatabase> SERVICE_STORAGE = new Hashtable<String, DataDatabase>();
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
                            getStorage(INIT_SERVICE).clearAllTables();
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

            DataDatabase db = databaseBuilder(ctx, DataDatabase.class, "secure-storage").allowMainThreadQueries().fallbackToDestructiveMigration().build();
            SERVICE_STORAGE.put(service, db);

            if (!isDeviceSecure()) {
                Log.e(TAG, MSG_DEVICE_NOT_SECURE);
                callbackContext.error(MSG_DEVICE_NOT_SECURE);
            } else if (!RSA.isEntryAvailable(alias)) {
                initContext = callbackContext;
                unlockCredentials();
            } else {
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

                    int chunkSize = 50000;
                    int numberOfChunk = 0;
                    int currentPosition = 0;
                    int index = chunkSize;
                    boolean done = false;
                    while (!done) {
                        numberOfChunk++;

                        if (index >= value.length()) {
                            index = value.length();
                            done = true;
                        }

                        String valueToEncrypt = value.substring(currentPosition, index);
                        try {
                            JSONObject result = AES.encrypt(valueToEncrypt.getBytes(), adata.getBytes());
                            byte[] aes_key = Base64.decode(result.getString("key"), Base64.DEFAULT);
                            byte[] aes_key_enc = RSA.encrypt(aes_key, service2alias(service));
                            result.put("key", Base64.encodeToString(aes_key_enc, Base64.DEFAULT));

                            getStorage(service).dataDao().store(new Data(key + numberOfChunk, result.toString()));

                            currentPosition = index;
                            index += chunkSize;

                        } catch (Exception e) {
                            Log.e(TAG, "Encrypt failed :", e);
                            getStorage(service).dataDao().clearKey(key + "%");
                            callbackContext.error(e.getMessage());
                            break;
                        }
                    }

                    getStorage(service).dataChunkDao().store(new DataChunk(key, numberOfChunk));
                    callbackContext.success(key);
                }
            });
            return true;
        }
        if ("get".equals(action)) {
            final String service = args.getString(0);
            final String key = args.getString(1);

            int numberOfChunks = getStorage(service).dataChunkDao().fetch(key);

            if (numberOfChunks > 0) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        String valueToReturn = "";
                        for (int i = 1; i <= numberOfChunks; i++) {
                            String chunkValue = getStorage(service).dataDao().fetch(key + i);
                            JSONObject json = null;
                            try {
                                json = new JSONObject(chunkValue);
                                final byte[] encKey = Base64.decode(json.getString("key"), Base64.DEFAULT);
                                JSONObject data = json.getJSONObject("value");
                                final byte[] ct = Base64.decode(data.getString("ct"), Base64.DEFAULT);
                                final byte[] iv = Base64.decode(data.getString("iv"), Base64.DEFAULT);
                                final byte[] adata = Base64.decode(data.getString("adata"), Base64.DEFAULT);

                                try {
                                    byte[] decryptedKey = RSA.decrypt(encKey, service2alias(service));
                                    String decrypted = new String(AES.decrypt(ct, decryptedKey, iv, adata));
                                    valueToReturn = valueToReturn.concat(decrypted);
                                } catch (Exception e) {
                                    Log.e(TAG, "Decrypt failed :", e);
                                    callbackContext.error(e.getMessage());
                                    break;
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        callbackContext.success(valueToReturn);
                    }
                });
            } else {
                callbackContext.error("Key [" + key + "] not found.");
            }
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
            getStorage(service).dataDao().remove(key);
            getStorage(service).dataChunkDao().remove(key);
            callbackContext.success(key);
            return true;
        }
        if ("keys".equals(action)) {
            String service = args.getString(0);
            callbackContext.success(new JSONArray(getStorage(service).dataChunkDao().keys()));
            return true;
        }
        if ("clear".equals(action)) {
            String service = args.getString(0);
            getStorage(service).dataDao().clear();
            getStorage(service).dataChunkDao().clear();
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

    private DataDatabase getStorage(String service) {
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
