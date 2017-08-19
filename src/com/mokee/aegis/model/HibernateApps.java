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
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.ArrayMap;

import com.mokee.aegis.receiver.PackagesMonitor;
import com.mokee.aegis.utils.PmCache;
import com.mokee.cloud.misc.CloudUtils;
import com.mokee.utils.PackageUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class HibernateApps {
    private static final String LOG_TAG = "HibernateApps";
    private final Context mContext;
    private final PackageManager mPm;
    private final Callback mCallback;
    private final PmCache mCache;
    private List<HibernateApp> mHibernateApps;
    // Map (pkg|uid) -> AppPermission
    private ArrayMap<String, HibernateApp> mAppLookup;
    private boolean mRefreshing;
    private SharedPreferences mPrefs;

    public HibernateApps(Context context, Callback callback, PmCache cache) {
        mCache = cache;
        mContext = context;
        mPm = mContext.getPackageManager();
        mCallback = callback;
        mPrefs = context.getSharedPreferences(PackagesMonitor.PREF_HIBERNATE, Context.MODE_PRIVATE);
    }

    public void refresh() {
        if (!mRefreshing) {
            mRefreshing = true;
            new HibernateAppsLoader().execute();
        }
    }

    public Collection<HibernateApp> getApps() {
        return mHibernateApps;
    }

    public HibernateApp getApp(String key) {
        return mAppLookup.get(key);
    }

    private List<HibernateApp> loadHibernateApps() {
        if (!CloudUtils.Verified) return null;
        ArrayList<HibernateApp> hibernateApps = new ArrayList<>();

        for (UserHandle user : UserManager.get(mContext).getUserProfiles()) {
            List<PackageInfo> apps = mCache != null ? mCache.getPackages(user.getIdentifier(), 0)
                    : mPm.getInstalledPackagesAsUser(0, user.getIdentifier());
            for (PackageInfo app : apps) {
                if (!PackageUtils.isSystem(app.applicationInfo)) {
                    String label = app.applicationInfo.loadLabel(mPm).toString();
                    if (TextUtils.equals(label, app.packageName)) continue;
                    HibernateApp hibernateApp = new HibernateApp(app.packageName, label,
                            app.applicationInfo.loadIcon(mPm), mPrefs.getBoolean(app.packageName, false), app.applicationInfo);
                    hibernateApps.add(hibernateApp);
                }
            }
        }

        Collections.sort(hibernateApps);

        return hibernateApps;
    }

    private void createMap(List<HibernateApp> result) {
        mAppLookup = new ArrayMap<>();
        for (HibernateApp app : result) {
            mAppLookup.put(app.getKey(), app);
        }
        mHibernateApps = result;
    }

    public interface Callback {
        void onHibernateAppsLoaded(HibernateApps hibernateApps);
    }

    public static class HibernateApp implements Comparable<HibernateApp> {
        private final String mPackageName;
        private final String mLabel;
        private final Drawable mIcon;
        private final boolean mAllowed;
        private final ApplicationInfo mInfo;

        public HibernateApp(String packageName, String label, Drawable icon, boolean allowed, ApplicationInfo info) {
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
        public int compareTo(HibernateApp another) {
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

    private class HibernateAppsLoader extends AsyncTask<Void, Void, List<HibernateApp>> {

        @Override
        protected List<HibernateApp> doInBackground(Void... args) {
            return loadHibernateApps();
        }

        @Override
        protected void onPostExecute(List<HibernateApp> result) {
            mRefreshing = false;
            createMap(result);
            if (mCallback != null) {
                mCallback.onHibernateAppsLoaded(HibernateApps.this);
            }
        }
    }
}
