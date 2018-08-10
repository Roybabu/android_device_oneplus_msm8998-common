/*
 * Copyright (C) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.settings.device;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;

import com.cyanogenmod.settings.device.utils.Constants;
import com.cyanogenmod.settings.device.utils.FileUtils;

public class Startup extends BroadcastReceiver {

    private static final String TAG = Startup.class.getSimpleName();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_PRE_BOOT_COMPLETED.equals(action)) {
            // Disable touchscreen gesture settings if needed
            if (!hasTouchscreenGestures()) {
                disableComponent(context, TouchscreenGestureSettings.class.getName());
            } else {
                enableComponent(context, TouchscreenGestureSettings.class.getName());
                // Restore nodes to saved preference values
                for (String pref : Constants.sGesturePrefKeys) {
                    boolean value = Constants.isPreferenceEnabled(context, pref);
                    String node = Constants.sBooleanNodePreferenceMap.get(pref);
                    // If music gestures are toggled, update values of all music gesture proc files
                    if (pref.equals(Constants.TOUCHSCREEN_MUSIC_GESTURE_KEY)) {
                        for (String music_nodes: Constants.TOUCHSCREEN_MUSIC_GESTURES_ARRAY) {
                            if (!FileUtils.writeLine(music_nodes, value ? "1" : "0")) {
                                Log.w(TAG, "Write to node " + music_nodes +
                                    " failed while restoring saved preference values");
                            }
                        }
                    }
                    else if (!FileUtils.writeLine(node, value ? "1" : "0")) {
                        Log.w(TAG, "Write to node " + node +
                            " failed while restoring saved preference values");
                    }
                }
            }

            // Disable button settings if needed
            if (!hasButtonProcs()) {
                disableComponent(context, ButtonSettings.class.getName());
            } else {
                enableComponent(context, ButtonSettings.class.getName());

                // Restore nodes to saved preference values
                for (String pref : Constants.sButtonPrefKeys) {
                    String value;
                    String node;
                    if (Constants.sStringNodePreferenceMap.containsKey(pref)) {
                        value = Constants.getPreferenceString(context, pref);
                        node = Constants.sStringNodePreferenceMap.get(pref);
                    } else {
                        value = Constants.isPreferenceEnabled(context, pref) ?
                                "1" : "0";
                        node = Constants.sBooleanNodePreferenceMap.get(pref);
                    }
                    if (!FileUtils.writeLine(node, value)) {
                        Log.w(TAG, "Write to node " + node +
                            " failed while restoring saved preference values");
                    }
                }
            }

            // Disable O-Click settings if needed
            if (!hasOClick()) {
                disableComponent(context, BluetoothInputSettings.class.getName());
                disableComponent(context, OclickService.class.getName());
            } else {
                updateOClickServiceState(context);
            }
        } else if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            if (hasOClick()) {
                updateOClickServiceState(context);
            }
        }
    }

    static  boolean hasTouchscreenGestures() {
        return new File(Constants.TOUCHSCREEN_CAMERA_NODE).exists() &&
            new File(Constants.TOUCHSCREEN_DOUBLE_SWIPE_NODE).exists() &&
            new File(Constants.TOUCHSCREEN_FLASHLIGHT_NODE).exists();
    }

    static boolean hasButtonProcs() {
        return (new File(Constants.NOTIF_SLIDER_TOP_NODE).exists() &&
            new File(Constants.NOTIF_SLIDER_MIDDLE_NODE).exists() &&
            new File(Constants.NOTIF_SLIDER_BOTTOM_NODE).exists()) ||
            new File(Constants.BUTTON_SWAP_NODE).exists();
    }

    static boolean hasOClick() {
        return Build.MODEL.equals("N1") || Build.MODEL.equals("N3");
    }

    private void disableComponent(Context context, String component) {
        ComponentName name = new ComponentName(context, component);
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(name,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private void enableComponent(Context context, String component) {
        ComponentName name = new ComponentName(context, component);
        PackageManager pm = context.getPackageManager();
        if (pm.getComponentEnabledSetting(name)
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(name,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }

    private void updateOClickServiceState(Context context) {
        BluetoothManager btManager = (BluetoothManager)
                context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = btManager.getAdapter();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean shouldStartService = adapter != null
                && adapter.getState() == BluetoothAdapter.STATE_ON
                && prefs.contains(Constants.OCLICK_DEVICE_ADDRESS_KEY);
        Intent serviceIntent = new Intent(context, OclickService.class);

        if (shouldStartService) {
            context.startService(serviceIntent);
        } else {
            context.stopService(serviceIntent);
        }
    }
}
