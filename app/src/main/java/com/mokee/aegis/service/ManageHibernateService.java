/*
 * Copyright (C) 2015-2016 The MoKee Open Source Project
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
 *
 */

package com.mokee.aegis.service;

import android.app.Service;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.mokee.aegis.receiver.PackagesMonitor;

import java.util.Map;

public class ManageHibernateService extends Service {

    private boolean mIsBound;

    private SharedPreferences prefs;

    private UsageStatsManager mUsageStats;

    private static final String KEY_AEGIS_HIBERNATE_WAKEUP = "aegis_hibernate_wakeup";

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context mContext, Intent intent) {
            String action = intent.getAction();
            Map<String, ?> apps = prefs.getAll();
            if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                for (Map.Entry<String, ?> entry : apps.entrySet()) {
                    if ((Boolean)entry.getValue() && !mUsageStats.isAppInactive(entry.getKey())) {
                        mUsageStats.setAppInactive(entry.getKey(), true);
                    }
                }
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                if (PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(KEY_AEGIS_HIBERNATE_WAKEUP, false)) {
                    for (Map.Entry<String, ?> entry : apps.entrySet()) {
                        if ((Boolean)entry.getValue() && mUsageStats.isAppInactive(entry.getKey())) {
                            mUsageStats.setAppInactive(entry.getKey(), false);
                        }
                    }
                }
            }
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {

        private NonStopIntentService mBoundService;

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((NonStopIntentService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
        }
    };

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mReceiver, filter);
        prefs = getSharedPreferences(PackagesMonitor.PREF_HIBERNATE, Context.MODE_PRIVATE);
        mUsageStats = getSystemService(UsageStatsManager.class);
        doBindService();
    }

    void doBindService() {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(ManageHibernateService.this,
                NonStopIntentService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        doUnbindService();
    }
}
