package com.fongmi.android.tv.ui.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.ApiConfig;
import com.fongmi.android.tv.api.LiveConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivitySettingBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.impl.LiveCallback;
import com.fongmi.android.tv.impl.SiteCallback;
import com.fongmi.android.tv.net.Callback;
import com.fongmi.android.tv.net.OKHttp;
import com.fongmi.android.tv.ui.custom.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.custom.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.custom.dialog.LiveDialog;
import com.fongmi.android.tv.ui.custom.dialog.SiteDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Prefers;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Updater;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Response;

public class SettingActivity extends BaseActivity implements ConfigCallback, SiteCallback, LiveCallback {

    private final ActivityResultLauncher<String> launcherString = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> loadConfig());
    private final ActivityResultLauncher<Intent> launcherIntent = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> loadConfig());

    private ActivitySettingBinding mBinding;
    private Config config;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        mBinding.vodUrl.setText(ApiConfig.getUrl());
        mBinding.liveUrl.setText(LiveConfig.getUrl());
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        mBinding.wallpaperUrl.setText(ApiConfig.get().getWallpaper());
        mBinding.sizeText.setText(ResUtil.getStringArray(R.array.select_size)[Prefers.getSize()]);
        mBinding.scaleText.setText(ResUtil.getStringArray(R.array.select_scale)[Prefers.getScale()]);
        mBinding.renderText.setText(ResUtil.getStringArray(R.array.select_render)[Prefers.getRender()]);
        mBinding.qualityText.setText(ResUtil.getStringArray(R.array.select_quality)[Prefers.getQuality()]);
    }

    @Override
    protected void initEvent() {
        mBinding.wallpaper.setOnClickListener(view -> Notify.show("下個版本見"));
        mBinding.vodHome.setOnClickListener(view -> SiteDialog.create(this).show());
        mBinding.liveHome.setOnClickListener(view -> LiveDialog.create(this).show());
        mBinding.vod.setOnClickListener(view -> ConfigDialog.create(this).type(0).show());
        mBinding.live.setOnClickListener(view -> ConfigDialog.create(this).type(1).show());
        mBinding.vodHistory.setOnClickListener(view -> HistoryDialog.create(this).type(0).show());
        mBinding.liveHistory.setOnClickListener(view -> HistoryDialog.create(this).type(1).show());
        mBinding.version.setOnClickListener(view -> Updater.create(this).force().start());
        mBinding.wallpaperDefault.setOnClickListener(this::setWallpaperDefault);
        mBinding.wallpaperRefresh.setOnClickListener(this::setWallpaperRefresh);
        mBinding.quality.setOnClickListener(this::setQuality);
        mBinding.render.setOnClickListener(this::setRender);
        mBinding.scale.setOnClickListener(this::setScale);
        mBinding.size.setOnClickListener(this::setSize);
    }

    @Override
    public void setConfig(Config config) {
        this.config = config;
        checkPermission();
    }

    private void checkPermission() {
        if (config.getUrl().startsWith("file://") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            openSetting();
        } else if (config.getUrl().startsWith("file://") && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            launcherString.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            loadConfig();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void openSetting() {
        try {
            launcherIntent.launch(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + BuildConfig.APPLICATION_ID)));
        } catch (Exception e) {
            launcherIntent.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void loadConfig() {
        if (config.isVod()) {
            Notify.progress(this);
            mBinding.vodUrl.setText(config.getUrl());
            ApiConfig.get().clear().config(config).load(getCallback());
        } else {
            Notify.progress(this);
            mBinding.liveUrl.setText(config.getUrl());
            LiveConfig.get().clear().config(config).load(getCallback());
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                refresh(0);
            }

            @Override
            public void error(int resId) {
                refresh(resId);
            }
        };
    }

    private void refresh(int resId) {
        Notify.dismiss();
        Notify.show(resId);
        mBinding.wallpaperUrl.setText(ApiConfig.get().getWallpaper());
        if (resId != 0) config.delete();
        if (config.isLive()) return;
        RefreshEvent.history();
        RefreshEvent.video();
    }

    @Override
    public void setSite(Site item) {
        ApiConfig.get().setHome(item);
        RefreshEvent.video();
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void setQuality(View view) {
        CharSequence[] array = ResUtil.getStringArray(R.array.select_quality);
        int index = Prefers.getQuality();
        Prefers.putQuality(index = index == array.length - 1 ? 0 : ++index);
        mBinding.qualityText.setText(array[index]);
        RefreshEvent.image();
    }

    private void setRender(View view) {
        CharSequence[] array = ResUtil.getStringArray(R.array.select_render);
        int index = Prefers.getRender();
        Prefers.putRender(index = index == array.length - 1 ? 0 : ++index);
        mBinding.renderText.setText(array[index]);
    }

    private void setScale(View view) {
        CharSequence[] array = ResUtil.getStringArray(R.array.select_scale);
        int index = Prefers.getScale();
        Prefers.putScale(index = index == array.length - 1 ? 0 : ++index);
        mBinding.scaleText.setText(array[index]);
    }

    private void setSize(View view) {
        CharSequence[] array = ResUtil.getStringArray(R.array.select_size);
        int index = Prefers.getSize();
        Prefers.putSize(index = index == array.length - 1 ? 0 : ++index);
        mBinding.sizeText.setText(array[index]);
        RefreshEvent.size();
    }

    private void setWallpaperDefault(View view) {
        int index = Prefers.getWallpaper();
        Prefers.putWallpaper(index == 4 ? 1 : ++index);
        RefreshEvent.wallpaper();
    }

    private void setWallpaperRefresh(View view) {
        if (TextUtils.isEmpty(ApiConfig.get().getWallpaper())) return;
        OKHttp.newCall(ApiConfig.get().getWallpaper()).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                File file = FileUtil.write(FileUtil.getWallpaper(0), response.body().bytes());
                if (!file.exists() || file.length() == 0) return;
                Prefers.putWallpaper(0);
                RefreshEvent.wallpaper();
            }
        });
    }
}
