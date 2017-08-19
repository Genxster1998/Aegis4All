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

package com.mokee.aegis.fragments;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.util.SparseArray;
import android.view.View;
import android.widget.TextView;

import com.mokee.aegis.R;
import com.mokee.aegis.model.PermissionApps;
import com.mokee.aegis.model.PermissionApps.Callback;
import com.mokee.aegis.model.PermissionApps.PermissionApp;
import com.mokee.aegis.utils.PmCache;
import com.mokee.cloud.misc.CloudUtils;

public final class PermissionAppsFragment extends PermissionsFrameFragment implements Callback, Preference.OnPreferenceChangeListener {

    private static final String TAG = PermissionAppsFragment.class.getName();
    private static final String PREF_CATEGORY_ALLOW_KEY = "pref_category_allow_key";
    private static final String PREF_CATEGORY_DENY_KEY = "pref_category_deny_key";
    private static final String APP_OP_MODE = "app_op_mode";

    private PermissionApps mPermissionApps;
    private AppOpsManager mAppOpsManager;

    private PreferenceScreen screenRoot;
    private PreferenceCategory categoryAllow;
    private PreferenceCategory categoryDeny;

    private int mCurAppOpMode;

    private int mCurCategoryAllowResId;
    private int mCurCategoryDenyResId;

    public static Fragment newInstance(int mode) {
        return setPermissionName(new PermissionAppsFragment(), mode);
    }

    private static <T extends Fragment> T setPermissionName(T fragment, int mode) {
        Bundle arguments = new Bundle();
        arguments.putInt(APP_OP_MODE, mode);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAppOpsManager = (AppOpsManager) getActivity().getSystemService(Context.APP_OPS_SERVICE);
        setLoading(true /* loading */, false /* animate */);
        mCurAppOpMode = getArguments().getInt(APP_OP_MODE);
        final SparseArray<String> groups = new SparseArray<String>();
        switch (mCurAppOpMode) {
            case AppOpsManager.OP_WAKE_LOCK:
                mCurCategoryAllowResId = R.string.wakelock_allow_list_category_title;
                mCurCategoryDenyResId = R.string.wakelock_deny_list_category_title;
                groups.put(AppOpsManager.OP_WAKE_LOCK, Manifest.permission.WAKE_LOCK);
                break;
        }
        mPermissionApps = new PermissionApps(getActivity(), groups, this, PmCache.getPmCache(getContext()));
        mPermissionApps.refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        mPermissionApps.refresh();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    protected void onSetEmptyText(TextView textView) {
        textView.setText(R.string.no_apps);
    }

    @Override
    public void onPermissionsLoaded(PermissionApps permissionApps) {

        getPreferenceManager().setSharedPreferencesName("appops_" + mCurAppOpMode);
        Context mContext = getPreferenceManager().getContext();

        if (mContext == null) {
            return;
        }

        screenRoot = getPreferenceScreen();

        categoryAllow = (PreferenceCategory) screenRoot.findPreference(PREF_CATEGORY_ALLOW_KEY);
        if (categoryAllow == null) {
            categoryAllow = new PreferenceCategory(mContext);
            categoryAllow.setKey(PREF_CATEGORY_ALLOW_KEY);
            categoryAllow.setTitle(mCurCategoryAllowResId);
            screenRoot.addPreference(categoryAllow);
        }

        categoryDeny = (PreferenceCategory) screenRoot.findPreference(PREF_CATEGORY_DENY_KEY);
        if (categoryDeny == null) {
            categoryDeny = new PreferenceCategory(mContext);
            categoryDeny.setKey(PREF_CATEGORY_DENY_KEY);
            categoryDeny.setTitle(mCurCategoryDenyResId);
            screenRoot.addPreference(categoryDeny);
        }

        if (!CloudUtils.Verified) return;

        if (permissionApps.getApps().size() != 0) {
            for (final PermissionApp app : permissionApps.getApps()) {
                boolean isChecked = app.getRequestPermissionStatus().get(mCurAppOpMode);
                String key = app.getKey();
                SwitchPreference existingPref = (SwitchPreference) screenRoot.findPreference(key);
                if (existingPref != null) {
                    existingPref.setChecked(isChecked);
                    continue;
                }
                boolean exists = false;
                for (int index = 0; index < app.getRequestPermissionStatus().size(); index++) {
                    if (app.getRequestPermissionStatus().keyAt(index) == mCurAppOpMode) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) continue;
                SwitchPreference pref = new SwitchPreference(mContext);
                pref.setKey(key);
                pref.setIcon(app.getIcon());
                pref.setTitle(app.getLabel());
                pref.setChecked(isChecked);
                pref.setOnPreferenceChangeListener(this);
                if (isChecked) {
                    categoryAllow.addPreference(pref);
                } else {
                    categoryDeny.addPreference(pref);
                }
            }
            if (categoryAllow.getPreferenceCount() == 0) {
                screenRoot.removePreference(categoryAllow);
            }
            if (categoryDeny.getPreferenceCount() == 0) {
                screenRoot.removePreference(categoryDeny);
            }
        } else {
            screenRoot.removeAll();
        }

        setLoading(false /* loading */, true /* animate */);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        PermissionApp app = mPermissionApps.getApp(preference.getKey());
        mAppOpsManager.setMode(mCurAppOpMode, app.getUid(),
                app.getPackageName(), (Boolean) newValue ? AppOpsManager.MODE_ALLOWED : mAppOpsManager.MODE_IGNORED);
        if (!(Boolean) newValue) {
            categoryAllow.removePreference(preference);
            categoryDeny.addPreference(preference);
        } else {
            categoryDeny.removePreference(preference);
            categoryAllow.addPreference(preference);
        }
        if (categoryAllow.getPreferenceCount() == 0) {
            screenRoot.removePreference(categoryAllow);
        } else {
            if (screenRoot.findPreference(PREF_CATEGORY_ALLOW_KEY) == null || categoryAllow.getPreferenceCount() == 1 && (Boolean) newValue) {
                screenRoot.addPreference(categoryAllow);
                if (screenRoot.findPreference(PREF_CATEGORY_DENY_KEY) != null) {
                    screenRoot.removePreference(categoryDeny);
                    screenRoot.addPreference(categoryDeny);
                }
            }
        }
        if (categoryDeny.getPreferenceCount() == 0) {
            screenRoot.removePreference(categoryDeny);
        } else {
            if (screenRoot.findPreference(PREF_CATEGORY_DENY_KEY) == null) {
                screenRoot.addPreference(categoryDeny);
            }
        }
        return true;
    }

}
