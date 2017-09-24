package com.dm.material.dashboard.candybar.tasks;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.danimahardhika.android.helpers.core.FileHelper;
import com.dm.material.dashboard.candybar.R;
import com.dm.material.dashboard.candybar.activities.CandyBarMainActivity;
import com.dm.material.dashboard.candybar.applications.CandyBarApplication;
import com.dm.material.dashboard.candybar.databases.Database;
import com.dm.material.dashboard.candybar.fragments.RequestFragment;
import com.dm.material.dashboard.candybar.fragments.dialog.IntentChooserFragment;
import com.dm.material.dashboard.candybar.helpers.DeviceHelper;
import com.dm.material.dashboard.candybar.items.Request;
import com.dm.material.dashboard.candybar.preferences.Preferences;
import com.dm.material.dashboard.candybar.utils.LogUtil;
import com.dm.material.dashboard.candybar.utils.listeners.RequestListener;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;

/*
 * CandyBar - Material Dashboard
 *
 * Copyright (c) 2014-2016 Dani Mahardhika
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

public class IconRequestBuilderTask extends AsyncTask<Void, Void, Boolean> {

    private WeakReference<Context> mContext;
    private WeakReference<IconRequestBuilderCallback> mCallback;
    private String mEmailBody;
    private LogUtil.Error mError;

    private IconRequestBuilderTask(Context context) {
        mContext = new WeakReference<>(context);
    }

    public IconRequestBuilderTask callback(IconRequestBuilderCallback callback) {
        mCallback = new WeakReference<>(callback);
        return this;
    }

    public AsyncTask start() {
        return start(SERIAL_EXECUTOR);
    }

    public AsyncTask start(@NonNull Executor executor) {
        return executeOnExecutor(executor);
    }

    public static IconRequestBuilderTask prepare(@NonNull Context context) {
        return new IconRequestBuilderTask(context);
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        while (!isCancelled()) {
            try {
                Thread.sleep(1);
                if (RequestFragment.sSelectedRequests == null) {
                    mError = LogUtil.Error.ICON_REQUEST_NULL;
                    return false;
                }

                if (CandyBarApplication.sRequestProperty == null) {
                    mError = LogUtil.Error.ICON_REQUEST_PROPERTY_NULL;
                    return false;
                }

                if (CandyBarApplication.sRequestProperty.getComponentName() == null) {
                    mError = LogUtil.Error.ICON_REQUEST_PROPERTY_COMPONENT_NULL;
                    return false;
                }

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(DeviceHelper.getDeviceInfo(mContext.get()));

                if (Preferences.get(mContext.get()).isPremiumRequest()) {
                    if (CandyBarApplication.sRequestProperty.getOrderId() != null) {
                        stringBuilder.append("Order Id: ")
                                .append(CandyBarApplication.sRequestProperty.getOrderId());
                    }

                    if (CandyBarApplication.sRequestProperty.getProductId() != null) {
                        stringBuilder.append("\nProduct Id: ")
                                .append(CandyBarApplication.sRequestProperty.getProductId());
                    }
                }

                for (int i = 0; i < RequestFragment.sSelectedRequests.size(); i++) {
                    Request request = CandyBarMainActivity.sMissedApps.get(RequestFragment.sSelectedRequests.get(i));
                    Database.get(mContext.get()).addRequest(null, request);

                    if (Preferences.get(mContext.get()).isPremiumRequest()) {
                        Request premiumRequest = Request.Builder()
                                .name(request.getName())
                                .activity(request.getActivity())
                                .productId(CandyBarApplication.sRequestProperty.getProductId())
                                .orderId(CandyBarApplication.sRequestProperty.getOrderId())
                                .build();
                        Database.get(mContext.get()).addPremiumRequest(null, premiumRequest);
                    }

                    if (CandyBarApplication.getConfiguration().isIncludeIconRequestToEmailBody()) {
                        stringBuilder.append("\n\n")
                                .append(request.getName())
                                .append("\n")
                                .append(request.getActivity())
                                .append("\n")
                                .append("https://play.google.com/store/apps/details?id=")
                                .append(request.getPackageName());
                    }
                }

                mEmailBody = stringBuilder.toString();
                return true;
            } catch (Exception e) {
                CandyBarApplication.sRequestProperty = null;
                RequestFragment.sSelectedRequests = null;
                LogUtil.e(Log.getStackTraceString(e));
                return false;
            }
        }
        return false;
    }

    @Override
    protected void onPostExecute(Boolean aBoolean) {
        super.onPostExecute(aBoolean);
        if (aBoolean) {
            try {
                if (mCallback != null && mCallback.get() != null)
                    mCallback.get().onFinished();

                RequestListener listener = (RequestListener) mContext;
                listener.onRequestBuilt(getIntent(CandyBarApplication.sRequestProperty
                                .getComponentName(), mEmailBody),
                        IntentChooserFragment.ICON_REQUEST);
            } catch (Exception e) {
                LogUtil.e(Log.getStackTraceString(e));
            }
        } else {
            if (mError != null) {
                LogUtil.e(mError.getMessage());
                mError.showToast(mContext.get());
            }
        }
    }

    @Nullable
    private Intent getIntent(ComponentName name, String emailBody) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent = addIntentExtra(intent, emailBody);
            intent.setComponent(name);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        } catch (IllegalArgumentException e) {
            try {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent = addIntentExtra(intent, emailBody);
                return intent;
            } catch (ActivityNotFoundException e1) {
                LogUtil.e(Log.getStackTraceString(e1));
            }
        }
        return null;
    }

    private Intent addIntentExtra(@NonNull Intent intent, String emailBody) {
        intent.setType("message/rfc822");

        if (CandyBarApplication.sZipPath != null) {
            File zip = new File(CandyBarApplication.sZipPath);
            if (zip.exists()) {
                Uri uri = FileHelper.getUriFromFile(mContext.get(), mContext.get().getPackageName(), zip);
                if (uri == null) uri = Uri.fromFile(zip);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }

        String subject = Preferences.get(mContext.get()).isPremiumRequest() ?
                "Premium Icon Request " : "Icon Request ";
        subject += mContext.get().getResources().getString(R.string.app_name);

        intent.putExtra(Intent.EXTRA_EMAIL,
                new String[]{mContext.get().getResources().getString(R.string.dev_email)});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, emailBody);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }

    public interface IconRequestBuilderCallback {
        void onFinished();
    }
}
