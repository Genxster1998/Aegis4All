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

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.mokee.aegis.utils.PmCache;
import com.mokee.cloud.misc.CloudUtils;
import com.mokee.utils.PackageUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PermissionApps {
    private static final String LOG_TAG = "PermissionApps";
    private static final String[] WHITE_LIST = {"com.cyanogenmod.lockclock"};
    private final Context mContext;
    private final SparseArray<String> mRequestPermissionGroups;
    private final PackageManager mPm;
    private final Callback mCallback;
    private final PmCache mCache;
    private List<PermissionApp> mPermApps;
    // Map (pkg|uid) -> AppPermission
    private ArrayMap<String, PermissionApp> mAppLookup;
    private boolean mRefreshing;

    public PermissionApps(Context context, SparseArray<String> groups, Callback callback, PmCache cache) {
        mCache = cache;
        mContext = context;
        mRequestPermissionGroups = groups;
        mPm = mContext.getPackageManager();
        mCallback = callback;
    }

    public void refresh() {
        if (!mRefreshing) {
            mRefreshing = true;
            new PermissionAppsLoader().execute();
        }
    }

    public Collection<PermissionApp> getApps() {
        return mPermApps;
    }

    public PermissionApp getApp(String key) {
        return mAppLookup.get(key);
    }

    private List<PermissionApp> loadPermissionApps() {
        if (!CloudUtils.Verified) return null;
        ArrayList<PermissionApp> permApps = new ArrayList<>();
        for (UserHandle user : UserManager.get(mContext).getUserProfiles()) {
            List<PackageInfo> apps = mCache != null ? mCache.getPackages(user.getIdentifier(), PackageManager.GET_PERMISSIONS)
                    : mPm.getInstalledPackagesAsUser(PackageManager.GET_PERMISSIONS,
                    user.getIdentifier());
            AppOpsManager mAppOpsManager = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);
            for (PackageInfo app : apps) {
                if (app.requestedPermissions == null) {
                    continue;
                }
                SparseBooleanArray requestPermissionStatus = new SparseBooleanArray();

                for (int index = 0; index < mRequestPermissionGroups.size(); index++) {
                    int key = mRequestPermissionGroups.keyAt(index);
                    boolean permissionExists = Arrays.asList(app.requestedPermissions).contains(mRequestPermissionGroups.get(key));
                    if (permissionExists && !PackageUtils.isSystem(app.applicationInfo) || permissionExists && PackageUtils.isSystem(app.applicationInfo) && Arrays.asList(WHITE_LIST).contains(app.packageName)) {
                        int mode = mAppOpsManager.checkOp(key, app.applicationInfo.uid, app.packageName);
                        requestPermissionStatus.put(key, AppOpsManager.MODE_ALLOWED == mode);
                    }
                }
                if (requestPermissionStatus.size() > 0) {
                    String label = app.applicationInfo.loadLabel(mPm).toString();
                    PermissionApp permApp = new PermissionApp(app.packageName,
                            label, app.applicationInfo.loadIcon(mPm), requestPermissionStatus, app.applicationInfo);
                    permApps.add(permApp);
                }
            }
        }

        Collections.sort(permApps);

        return permApps;
    }

    private void createMap(List<PermissionApp> result) {
        mAppLookup = new ArrayMap<>();
        for (PermissionApp app : result) {
            mAppLookup.put(app.getKey(), app);
        }
        mPermApps = result;
    }


    public interface Callback {
        void onPermissionsLoaded(PermissionApps permissionApps);
    }

    public static class PermissionApp implements Comparable<PermissionApp> {
        private final String mPackageName;
        private final String mLabel;
        private final Drawable mIcon;
        private final SparseBooleanArray mRequestPermissionStatus;
        private final ApplicationInfo mInfo;

        public PermissionApp(String packageName, String label, Drawable icon, SparseBooleanArray requestPermissionStatus, ApplicationInfo info) {
            mPackageName = packageName;
            mLabel = label;
            mIcon = icon;
            mRequestPermissionStatus = requestPermissionStatus;
            mInfo = info;
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

        public SparseBooleanArray getRequestPermissionStatus() {
            return mRequestPermissionStatus;
        }

        public String getPackageName() {
            return mPackageName;
        }

        @Override
        public int compareTo(PermissionApp another) {
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

    private class PermissionAppsLoader extends AsyncTask<Void, Void, List<PermissionApp>> {

        @Override
        protected List<PermissionApp> doInBackground(Void... args) {
            return loadPermissionApps();
        }

        @Override
        protected void onPostExecute(List<PermissionApp> result) {
            mRefreshing = false;
            createMap(result);
            if (mCallback != null) {
                mCallback.onPermissionsLoaded(PermissionApps.this);
            }
        }
    }
}
