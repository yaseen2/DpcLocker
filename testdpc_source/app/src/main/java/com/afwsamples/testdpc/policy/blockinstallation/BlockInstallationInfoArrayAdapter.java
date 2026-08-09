/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.afwsamples.testdpc.policy.blockinstallation;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import android.content.SharedPreferences;

import com.afwsamples.testdpc.R;
import com.afwsamples.testdpc.common.ToggleComponentsArrayAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Displays a list of installed apps with icons and checkboxes to block installation/usage.
 */
public class BlockInstallationInfoArrayAdapter extends ToggleComponentsArrayAdapter {

    private final ComponentName mAdminComponent;

    public BlockInstallationInfoArrayAdapter(Context context, int resource,
            List<ResolveInfo> resolveInfoList, ComponentName admin) {
        super(context, resource, resolveInfoList);
        mAdminComponent = admin;
        setIsComponentEnabledList(createIsComponentEnabledList());
    }

    @Override
    public boolean isComponentEnabled(ResolveInfo resolveInfo) {
        try {
            boolean isSuspended = mDevicePolicyManager.isPackageSuspended(mAdminComponent, resolveInfo.resolvePackageName);
            if (isSuspended) return true;
        } catch (Exception ignored) {
        }
        SharedPreferences prefs = getContext().getSharedPreferences("proactive_package_blocklist", Context.MODE_PRIVATE);
        Set<String> set = prefs.getStringSet("blocked_packages_set", null);
        return set != null && set.contains(resolveInfo.resolvePackageName.toLowerCase());
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        if (view == null) return null;

        CheckBox checkBox = view.findViewById(R.id.enable_component_checkbox);
        if (checkBox != null) {
            checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean isBlocked = ((CheckBox) v).isChecked();
                    mIsComponentCheckedList.set(position, isBlocked);
                    String pkgName = getItem(position).resolvePackageName;
                    try {
                        mDevicePolicyManager.setPackagesSuspended(mAdminComponent, new String[]{pkgName}, isBlocked);
                        
                        SharedPreferences prefs = getContext().getSharedPreferences("proactive_package_blocklist", Context.MODE_PRIVATE);
                        Set<String> set = new HashSet<>(prefs.getStringSet("blocked_packages_set", new HashSet<String>()));
                        if (isBlocked) {
                            set.add(pkgName.toLowerCase());
                        } else {
                            set.remove(pkgName.toLowerCase());
                        }
                        prefs.edit().putStringSet("blocked_packages_set", set).apply();
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        return view;
    }

    @Override
    protected ApplicationInfo getApplicationInfo(int position) {
        try {
            return mPackageManager.getApplicationInfo(getItem(position).resolvePackageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "getApplicationInfo error: ", e);
        }
        return null;
    }

    private List<Boolean> createIsComponentEnabledList() {
        List<Boolean> isComponentEnabledList = new ArrayList<>();
        int size = getCount();
        for (int i = 0; i < size; i++) {
            isComponentEnabledList.add(isComponentEnabled(getItem(i)));
        }
        return isComponentEnabledList;
    }

    @Override
    protected boolean canModifyComponent(int position) {
        return true;
    }

    @Override
    public CharSequence getDisplayName(int position) {
        ApplicationInfo appInfo = getApplicationInfo(position);
        return appInfo != null ? mPackageManager.getApplicationLabel(appInfo) : getItem(position).resolvePackageName;
    }
}
