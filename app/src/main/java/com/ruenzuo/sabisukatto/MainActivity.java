package com.ruenzuo.sabisukatto;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.TextView;

import com.ruenzuo.sabisukatto.media.MediaFragment;
import com.ruenzuo.sabisukatto.sabisukattokit.MediaDownloader;
import com.ruenzuo.sabisukatto.settings.SettingsFragment;
import com.twitter.sdk.android.Twitter;
import com.twitter.sdk.android.core.TwitterAuthConfig;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.fabric.sdk.android.Fabric;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.schedulers.Schedulers;

import static com.ruenzuo.sabisukatto.TwitterCredentials.TWITTER_KEY;
import static com.ruenzuo.sabisukatto.TwitterCredentials.TWITTER_SECRET;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener, MediaActivity {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
    static final int WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TwitterAuthConfig authConfig = new TwitterAuthConfig(TWITTER_KEY, TWITTER_SECRET);
        Fabric.with(this, new Twitter(authConfig));
        setContentView(R.layout.activity_main);

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(this);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, getFragment(navigation.getMenu().getItem(0).getItemId())).commit();

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                String tweet = intent.getStringExtra(Intent.EXTRA_TEXT);
                Pattern pattern = Pattern.compile("https(.*)");
                Matcher matcher = pattern.matcher(tweet);
                if (matcher.find()) {
                    String url = matcher.group(1);
                    downloadGIF(url);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(R.id.gif_download_notification);
        disposables.dispose();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, getFragment(item.getItemId()), SettingsFragment.class.getName()).commit();
        return true;
    }

    private Fragment getFragment(int menuId) {
        switch (menuId) {
            case R.id.navigation_media: return MediaFragment.newInstance();
            case R.id.navigation_settings: return SettingsFragment.newInstance();
            default: return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Fragment fragment = getSupportFragmentManager().findFragmentByTag(SettingsFragment.class.getName());
        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    attemptDownloadGIF();
                } else {
                    //TODO: Implement
                }
                return;
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void attemptDownloadGIF() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST);
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
        if (item.getText() == null) {
            //TODO: Implement
            return;
        }
        downloadGIF(item.getText().toString());
    }

    private void downloadGIF(String url) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        builder.setContentTitle(getString(R.string.gif_download))
                .setContentText(getString(R.string.download_progress))
                .setSmallIcon(R.mipmap.ic_launcher);
        builder.setProgress(0, 0, true);
        notificationManager.notify(R.id.gif_download_notification, builder.build());
        MediaDownloader mediaDownloader = new MediaDownloader(this);
        Uri clipboardUri = Uri.parse(url);
        disposables.add(mediaDownloader.downloadMedia(clipboardUri)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableCompletableObserver() {
                    @Override
                    public void onComplete() {
                        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                        builder.setContentText(getString(R.string.download_complete))
                                .setProgress(0, 0, false);
                        notificationManager.notify(R.id.gif_download_notification, builder.build());
                    }

                    @Override
                    public void onError(Throwable e) {
                        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                        builder.setContentText(getString(R.string.download_error))
                                .setProgress(0, 0, false);
                        notificationManager.notify(R.id.gif_download_notification, builder.build());
                    }
                }));
    }

}
