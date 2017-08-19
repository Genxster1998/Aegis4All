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

package com.mokee.aegis.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.ArrayMap;

import com.android.internal.app.IAppOpsService;
import com.mokee.aegis.WardenInfo;
import com.mokee.aegis.WardenUtils;
import com.mokee.aegis.receiver.PackagesMonitor;
import com.mokee.aegis.utils.PmCache;
import com.mokee.cloud.misc.CloudUtils;
import com.mokee.utils.PackageUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class WardenApps {
    private static final String LOG_TAG = "WardenApps";
    private final Context mContext;
    private final PackageManager mPm;
    private final Callback mCallback;
    private final PmCache mCache;
    private IAppOpsService mAppOps;
    private List<WardenApp> mWardenApps;
    // Map (pkg|uid) -> AppPermission
    private ArrayMap<String, WardenApp> mAppLookup;
    private boolean mRefreshing;
    private SharedPreferences mPrefs;

    public WardenApps(Context context, Callback callback, PmCache cache, IAppOpsService appOps) {
        mCache = cache;
        mContext = context;
        mPm = mContext.getPackageManager();
        mAppOps = appOps;
        mCallback = callback;
        mPrefs = context.getSharedPreferences(PackagesMonitor.PREF_WARDEN, Context.MODE_PRIVATE);
    }

    public void refresh() {
        if (!mRefreshing) {
            mRefreshing = true;
            new WardenAppsLoader().execute();
        }
    }

    public Collection<WardenApp> getApps() {
        return mWardenApps;
    }

    public WardenApp getApp(String key) {
        return mAppLookup.get(key);
    }

    private List<WardenApp> loadWardenApps() {
        if (!CloudUtils.Verified) return null;
        ArrayList<WardenApp> wardenApps = new ArrayList<>();

        for (UserHandle user : UserManager.get(mContext).getUserProfiles()) {
            List<PackageInfo> apps = mCache != null ? mCache.getPackages(user.getIdentifier(), 0)
                    : mPm.getInstalledPackagesAsUser(0, user.getIdentifier());
            for (PackageInfo app : apps) {
                if (!PackageUtils.isSystem(app.applicationInfo)) {
                    String label = app.applicationInfo.loadLabel(mPm).toString();
                    if (TextUtils.equals(label, app.packageName)) continue;
                    boolean isAllowed;
                    try {
                        isAllowed = ((WardenInfo.PackageInfo) mAppOps.getWardenInfo(UserHandle.myUserId()).get(app.packageName)).getUidsInfo().get(UserHandle.myUserId()).getMode() == WardenUtils.MODE_ALLOWED;
                    } catch (RemoteException e) {
                        isAllowed = true;
                    } catch (NullPointerException e) {
                        isAllowed = true;
                    }
                    WardenApp wardenApp = new WardenApp(app.packageName, label,
                            app.applicationInfo.loadIcon(mPm), isAllowed, app.applicationInfo);
                    wardenApps.add(wardenApp);
                }
            }
        }

        Collections.sort(wardenApps);

        return wardenApps;
    }

    private void createMap(List<WardenApp> result) {
        mAppLookup = new ArrayMap<>();
        for (WardenApp app : result) {
            mAppLookup.put(app.getKey(), app);
        }
        mWardenApps = result;
    }

    public interface Callback {
        void onWardenAppsLoaded(WardenApps wardenApps);
    }

    public static class WardenApp implements Comparable<WardenApp> {
        private final String mPackageName;
        private final String mLabel;
        private final Drawable mIcon;
        private final boolean mAllowed;
        private final ApplicationInfo mInfo;

        public WardenApp(String packageName, String label, Drawable icon, boolean allowed, ApplicationInfo info) {
            mPackageName = packageName;
            mLabel = label;
            mIcon = icon;
            mInfo = info;
            mAllowed = allowed;
        }

        public ApplicationInfo getAppInfo() {
            return mInfo;
        }

        public String getKey() {
            return mPackageName;
        }

        public String getLabel() {
            return mLabel;
        }

        public Drawable getIcon() {
            return mIcon;
        }

        public boolean getAllowed() {
            return mAllowed;
        }

        public String getPackageName() {
            return mPackageName;
        }

        @Override
        public int compareTo(WardenApp another) {
            final int result = mLabel.compareTo(another.mLabel);
            if (result == 0) {
                // Unbadged before badged.
                return getKey().compareTo(another.getKey());
            }
            return result;
        }

        public int getUid() {
            return getAppInfo().uid;
        }
    }

    private class WardenAppsLoader extends AsyncTask<Void, Void, List<WardenApp>> {

        @Override
        protected List<WardenApp> doInBackground(Void... args) {
            return loadWardenApps();
        }

        @Override
        protected void onPostExecute(List<WardenApp> result) {
            mRefreshing = false;
            createMap(result);
            if (mCallback != null) {
                mCallback.onWardenAppsLoaded(WardenApps.this);
            }
        }
    }
}
