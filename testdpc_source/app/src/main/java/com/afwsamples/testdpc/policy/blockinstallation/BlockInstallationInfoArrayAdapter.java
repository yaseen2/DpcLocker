package com.afwsamples.testdpc.policy.blockinstallation;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import com.afwsamples.testdpc.R;
import com.afwsamples.testdpc.SecurityConfig;
import com.afwsamples.testdpc.common.ToggleComponentsArrayAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays a list of installed apps with icons and checkboxes to block installation/usage.
 */
public class BlockInstallationInfoArrayAdapter extends ToggleComponentsArrayAdapter {

    private final ComponentName mAdminComponent;

    public BlockInstallationInfoArrayAdapter(Context context, int resource,
            List<ResolveInfo> resolveInfoList, ComponentName admin) {
        super(context, resource, resolveInfoList);
        mAdminComponent = admin;
        List<Boolean> isComponentEnabledList = new ArrayList<>();
        for (ResolveInfo info : resolveInfoList) {
            isComponentEnabledList.add(isComponentEnabled(info));
        }
        setIsComponentEnabledList(isComponentEnabledList);
    }

    @Override
    public CharSequence getDisplayName(int position) {
        ApplicationInfo appInfo = getApplicationInfo(position);
        if (appInfo != null) {
            return appInfo.loadLabel(mPackageManager);
        }
        ResolveInfo item = getItem(position);
        return item != null ? item.resolvePackageName : "";
    }

    @Override
    protected ApplicationInfo getApplicationInfo(int position) {
        try {
            ResolveInfo item = getItem(position);
            if (item != null && item.resolvePackageName != null) {
                return mPackageManager.getApplicationInfo(item.resolvePackageName, 0);
            }
            return null;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Override
    protected boolean isComponentEnabled(ResolveInfo resolveInfo) {
        if (resolveInfo == null || resolveInfo.resolvePackageName == null) return false;
        String pkg = resolveInfo.resolvePackageName.trim().toLowerCase();
        try {
            boolean isSuspended = mDevicePolicyManager.isPackageSuspended(mAdminComponent, pkg);
            if (isSuspended) return true;
        } catch (Exception ignored) {
        }
        return SecurityConfig.isBlocklisted(getContext(), pkg);
    }

    @Override
    protected boolean canModifyComponent(int position) {
        return true;
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
                    ResolveInfo item = getItem(position);
                    if (item != null && item.resolvePackageName != null) {
                        String pkgName = item.resolvePackageName;
                        if (isBlocked) {
                            SecurityConfig.addToUserBlocklist(getContext(), pkgName);
                        } else {
                            SecurityConfig.removeFromUserBlocklist(getContext(), pkgName);
                        }
                    }
                }
            });
        }
        return view;
    }
}
