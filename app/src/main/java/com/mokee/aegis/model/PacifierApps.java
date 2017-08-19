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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;

import com.android.internal.app.IAppOpsService;
import com.mokee.aegis.PacifierInfo;
import com.mokee.aegis.PacifierUtils;
import com.mokee.aegis.utils.PmCache;
import com.mokee.cloud.misc.CloudUtils;
import com.mokee.utils.PackageUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PacifierApps {
    private static final String LOG_TAG = "PacifierApps";
    private final Context mContext;
    private final PackageManager mPm;
    private final Callback mCallback;
    private final PmCache mCache;
    private IAppOpsService mAppOps;
    private List<PacifierApp> mPacifierApps;
    // Map (pkg|uid) -> AppPermission
    private ArrayMap<String, PacifierApp> mAppLookup;
    private boolean mRefreshing;

    public PacifierApps(Context context, Callback callback, PmCache cache, IAppOpsService appOps) {
        mCache = cache;
        mAppOps = appOps;
        mContext = context;
        mPm = mContext.getPackageManager();
        mCallback = callback;
    }

    public void refresh() {
        if (!mRefreshing) {
            mRefreshing = true;
            new PacifierAppsLoader().execute();
        }
    }

    public Collection<PacifierApp> getApps() {
        return mPacifierApps;
    }

    public PacifierApp getApp(String key) {
        return mAppLookup.get(key);
    }

    private List<PacifierApp> loadPacifierApps() {
        if (!CloudUtils.Verified) return null;
        ArrayList<PacifierApp> pacifierApps = new ArrayList<>();
        for (UserHandle user : UserManager.get(mContext).getUserProfiles()) {
            List<PackageInfo> apps = mCache != null ? mCache.getPackages(user.getIdentifier(), 0)
                    : mPm.getInstalledPackagesAsUser(0, user.getIdentifier());
            try {
                Map<String, PacifierInfo.PackageInfo> mPackageInfo = mAppOps.getPacifierInfo(user.getIdentifier());
                for (PackageInfo app : apps) {
                    if (mPackageInfo.get(app.packageName) != null && !PackageUtils.isSystem(app.applicationInfo)) {
                        String label = app.applicationInfo.loadLabel(mPm).toString();
                        int mode = mPackageInfo.get(app.packageName).getUidsInfo().get(user.getIdentifier()).getMode();
                        PacifierApp pacifierApp = new PacifierApp(app.packageName, label,
                                app.applicationInfo.loadIcon(mPm), mode == PacifierUtils.MODE_ALLOWED, app.applicationInfo);
                        pacifierApps.add(pacifierApp);
                    }
                }
            } catch (NullPointerException e) {
            } catch (RemoteException e) {
            }
        }

        Collections.sort(pacifierApps);

        return pacifierApps;
    }

    private void createMap(List<PacifierApp> result) {
        mAppLookup = new ArrayMap<>();
        for (PacifierApp app : result) {
            mAppLookup.put(app.getKey(), app);
        }
        mPacifierApps = result;
    }


    public interface Callback {
        void onPacifierAppsLoaded(PacifierApps pacifierApps);
    }

    public static class PacifierApp implements Comparable<PacifierApp> {
        private final String mPackageName;
        private final String mLabel;
        private final Drawable mIcon;
        private final boolean mAllowed;
        private final ApplicationInfo mInfo;

        public PacifierApp(String packageName, String label, Drawable icon, boolean allowed, ApplicationInfo info) {
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
        public int compareTo(PacifierApp another) {
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

    private class PacifierAppsLoader extends AsyncTask<Void, Void, List<PacifierApp>> {

        @Override
        protected List<PacifierApp> doInBackground(Void... args) {
            return loadPacifierApps();
        }

        @Override
        protected void onPostExecute(List<PacifierApp> result) {
            mRefreshing = false;
            createMap(result);
            if (mCallback != null) {
                mCallback.onPacifierAppsLoaded(PacifierApps.this);
            }
        }
    }
}
