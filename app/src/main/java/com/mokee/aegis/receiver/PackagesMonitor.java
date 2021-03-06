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

package com.mokke.aegis.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;


import com.android.internal.app.IAppOpsService;
import com.mokee.aegis.service.ManageHibernateService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class PackagesMonitor extends BroadcastReceiver {

    Class ServiceManager = Class.forName("android.os.ServiceManager");
    @SuppressWarnings("unchecked")
    Method getService = ServiceManager.getMethod("getService", String.class);

    public static final String PREF_AUTORUN = "appops_65";
    public static final String PREF_WAKELOCK = "appops_40";
    public static final String PREF_PACIFIER = "pacifier";
    public static final String PREF_HIBERNATE = "hibernate";
    public static final String PREF_WARDEN = "warden";

    public PackagesMonitor() throws ClassNotFoundException, NoSuchMethodException {

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Intent manageHibernateService = new Intent(context, ManageHibernateService.class);
            context.startService(manageHibernateService);
        } else if (action.equals(Intent.ACTION_PACKAGE_REMOVED) && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            String packageName = intent.getData().getSchemeSpecificPart();
            if (!TextUtils.isEmpty(packageName)) {
                try {
                    Method myUserId = UserHandle.class.getMethod("myUserId",void.class);
                    IBinder iBinder = ((IBinder) getService.invoke(ServiceManager, Context.APP_OPS_SERVICE));
                    IAppOpsService mAppOps = IAppOpsService.Stub.asInterface(iBinder);
                    mAppOps.removePacifierPackageInfoFromUid(myUserId.invoke(UserHandle.class), packageName, myUserId.invoke(UserHandle.class));
                    mAppOps.removeWardenPackageInfoFromUid(myUserId.invoke(UserHandle.class), packageName,myUserId.invoke(UserHandle.class));
                } catch (RemoteException ignored) {
                } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
                context.getSharedPreferences(PREF_AUTORUN, Context.MODE_PRIVATE).edit().remove(packageName).apply();
                context.getSharedPreferences(PREF_WAKELOCK, Context.MODE_PRIVATE).edit().remove(packageName).apply();
                context.getSharedPreferences(PREF_PACIFIER, Context.MODE_PRIVATE).edit().remove(packageName).apply();
                context.getSharedPreferences(PREF_HIBERNATE, Context.MODE_PRIVATE).edit().remove(packageName).apply();
                context.getSharedPreferences(PREF_WARDEN, Context.MODE_PRIVATE).edit().remove(packageName).apply();
            }
        }
    }
}
