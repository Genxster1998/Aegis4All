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

package com.mokke.aegis.fragments;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.view.View;
import android.widget.TextView;

import com.android.internal.app.IAppOpsService;
import com.mokee.aegis.PacifierUtils;
import com.mokee.aegis.R;
import com.mokee.aegis.model.PacifierApps;
import com.mokee.aegis.model.PacifierApps.Callback;
import com.mokee.aegis.model.PacifierApps.PacifierApp;
import com.mokee.aegis.receiver.PackagesMonitor;
import com.mokee.aegis.utils.PmCache;
import com.mokee.cloud.misc.CloudUtils;

public final class PacifierAppsFragment extends PermissionsFrameFragment implements Callback, Preference.OnPreferenceChangeListener {

    private static final String TAG = PacifierAppsFragment.class.getName();
    private static final String PREF_CATEGORY_ALLOW_KEY = "pref_category_allow_key";
    private static final String PREF_CATEGORY_DENY_KEY = "pref_category_deny_key";

    private PacifierApps mPacifierApps;
    IBinder iBinder = ServiceManager.getService(Context.APP_OPS_SERVICE);
    private final IAppOpsService mAppOps = IAppOpsService.Stub.asInterface(iBinder);

    private PreferenceScreen screenRoot;
    private PreferenceCategory categoryAllow;
    private PreferenceCategory categoryDeny;

    private int mCurCategoryAllowResId;
    private int mCurCategoryDenyResId;

    public static Fragment newInstance() {
        return setPermissionName(new PacifierAppsFragment());
    }

    private static <T extends Fragment> T setPermissionName(T fragment) {
        Bundle arguments = new Bundle();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLoading(true /* loading */, false /* animate */);
        mCurCategoryAllowResId = R.string.pacifier_allow_list_category_title;
        mCurCategoryDenyResId = R.string.pacifier_deny_list_category_title;
        mPacifierApps = new PacifierApps(getActivity(), this, PmCache.getPmCache(getContext()), mAppOps);
        mPacifierApps.refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        mPacifierApps.refresh();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    protected void onSetEmptyText(TextView textView) {
        textView.setText(R.string.pacifier_analyzing);
    }

    @Override
    public void onPacifierAppsLoaded(PacifierApps pacifierApps) {

        getPreferenceManager().setSharedPreferencesName(PackagesMonitor.PREF_PACIFIER);
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

        if (pacifierApps.getApps().size() != 0) {
            for (final PacifierApp app : pacifierApps.getApps()) {
                String key = app.getKey();
                SwitchPreference existingPref = (SwitchPreference) screenRoot.findPreference(key);
                if (existingPref != null) {
                    existingPref.setChecked(app.getAllowed());
                    continue;
                }
                SwitchPreference pref = new SwitchPreference(mContext);
                pref.setKey(key);
                pref.setIcon(app.getIcon());
                pref.setTitle(app.getLabel());
                pref.setChecked(app.getAllowed());
                pref.setOnPreferenceChangeListener(this);
                if (app.getAllowed()) {
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
        PacifierApp app = mPacifierApps.getApp(preference.getKey());
        try {
            mAppOps.updatePacifierModeFromUid(UserHandle.myUserId(), app.getPackageName(),
                    UserHandle.myUserId(), (Boolean) newValue ? PacifierUtils.MODE_ALLOWED : PacifierUtils.MODE_ERRORED);
        } catch (RemoteException e) {
            return false;
        }
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
