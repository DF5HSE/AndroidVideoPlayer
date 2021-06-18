package com.example.avp.ui;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.avp.R;
import com.example.avp.model.Model;
import com.example.avp.ui.fragments.LoginFragment;
import com.example.avp.ui.fragments.NoStoragePermissionFragment;
import com.example.avp.ui.fragments.VideoByLinkFragment;
import com.example.avp.ui.fragments.VideoFromDeviceFragment;
import com.example.avp.utils.JsonStateSaveLoader;
import com.example.avp.utils.StateSaveLoader;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import static com.example.avp.ui.Constants.PERMISSION_REQUEST_CODE;


public class MainActivity extends AppCompatActivity
        implements BottomNavigationView.OnNavigationItemSelectedListener {

    private Menu menu;
    private String currentFragment;
    private static Model model = null; // this helps not make reload

    private StateSaveLoader stateSaveLoader;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stateSaveLoader = new JsonStateSaveLoader(this);
        if (model == null) {
            model = new Model(this);
        }
        else {
            model.setActivity(this);
        }
        loadState();

        BottomNavigationView navigation = findViewById(R.id.nav_view);
        navigation.setOnNavigationItemSelectedListener(this);

        loadFragment(new LoginFragment(model));

        if (hasPermissions()) {
            loadFragment(new VideoFromDeviceFragment(model));
        } else {
            requestPermissionWithRationale();
            if (hasPermissions()) {
                loadFragment(new VideoByLinkFragment(model));
            } else {
                loadFragment(new NoStoragePermissionFragment());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //save current state
        model.saveState(stateSaveLoader);
    }

    private void loadState() {

        model.loadSavedState(stateSaveLoader);
    }

    private void restoreMenuSettings() {
        MenuItem reversedOrderItem = menu.findItem(R.id.reversed_order_sorted_by);
        reversedOrderItem.setChecked(model.getVideoListSettings().reversedOrder);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;

        getMenuInflater().inflate(R.menu.menu_main, menu);

        restoreMenuSettings();

        return true;
    }

    private void updateVideoListSettings(Constants.DisplayMode displayMode, Constants.SortParam newSortParam, boolean newReversedOrder) {
        model.updateVideoListSettings(displayMode, newSortParam, newReversedOrder);

        if (currentFragment.equals(VideoFromDeviceFragment.class.getSimpleName())) {
            loadFragment(new VideoFromDeviceFragment(model));
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case (R.id.list_view):
                updateVideoListSettings(Constants.DisplayMode.LIST, model.getVideoListSettings().sortParam, model.getVideoListSettings().reversedOrder);
                item.setChecked(true);
                model.getVideoListSettings().displayMode = Constants.DisplayMode.LIST;
                break;
            case (R.id.gallery_view):
                updateVideoListSettings(Constants.DisplayMode.GALLERY, model.getVideoListSettings().sortParam, model.getVideoListSettings().reversedOrder);
                item.setChecked(true);
                model.getVideoListSettings().displayMode = Constants.DisplayMode.GALLERY;
                break;
            case (R.id.date_taken_sorted_by):
                updateVideoListSettings(model.getVideoListSettings().displayMode, Constants.SortParam.DATE_TAKEN, model.getVideoListSettings().reversedOrder);
                item.setChecked(true);
                break;
            case (R.id.display_name_sorted_by):
                updateVideoListSettings(model.getVideoListSettings().displayMode, Constants.SortParam.NAME, model.getVideoListSettings().reversedOrder);
                item.setChecked(true);
                break;
            case (R.id.reversed_order_sorted_by):
                updateVideoListSettings(model.getVideoListSettings().displayMode, model.getVideoListSettings().sortParam, !model.getVideoListSettings().reversedOrder);
                item.setChecked(model.getVideoListSettings().reversedOrder);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean hasPermissions() {
        int res = 0;
        //string array of permissions,
        String[] permissions = new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        for (String perms : permissions) {
            res = checkCallingOrSelfPermission(perms);
            if (!(res == PackageManager.PERMISSION_GRANTED)) {
                return false;
            }
        }
        return true;
    }

    private void requestPerms() {
        String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, PERMISSION_REQUEST_CODE);
        }
    }

    public void requestPermissionWithRationale() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)) {
            final String message = "Storage permission is needed to show videos";
            Snackbar snackbar = Snackbar.make(
                    MainActivity.this.findViewById(R.id.activity_view),
                    message, Snackbar.LENGTH_LONG).setAction("GRANT", v -> requestPerms()
            );
            View snackbarLayout = snackbar.getView();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, 0);
            snackbarLayout.setLayoutParams(lp);
            snackbar.show();
        } else {
            requestPerms();
        }
    }

    private boolean checkPermissions(int requestCode, @NonNull int[] grantResults) {
        boolean allowed = true;

        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int res : grantResults) {
                // if user granted all permissions.
                allowed = allowed && (res == PackageManager.PERMISSION_GRANTED);
            }
            return allowed;
        }
        return false;
    }

    private void giveWarning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "Storage Permissions denied.", Toast.LENGTH_SHORT).show();
            } else {
                showNoStoragePermissionSnackbar();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean allowed = checkPermissions(requestCode, grantResults);

        if (allowed) {
            //user granted all permissions we can perform our task.
            loadFragment(new VideoFromDeviceFragment(model));
        } else {
            // we will give warning to user that they haven't granted permissions.
            giveWarning();
        }

    }

    public void showNoStoragePermissionSnackbar() {
        Snackbar.make(MainActivity.this.findViewById(R.id.activity_view), "Storage permission isn't granted", Snackbar.LENGTH_LONG)
                .setAction("SETTINGS", v -> {
                    openApplicationSettings();

                    Toast.makeText(getApplicationContext(),
                            "Open Permissions and grant the Storage permission",
                            Toast.LENGTH_SHORT)
                            .show();
                })
                .show();
    }

    public void openApplicationSettings() {
        Intent appSettingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(appSettingsIntent, PERMISSION_REQUEST_CODE);
    }

    private void updateCurrentFragment(Fragment fragment) {
        currentFragment = fragment.getClass().getSimpleName();
    }

    private boolean loadFragment(Fragment fragment) {
        if (fragment == null) {
            return false;
        }
        updateCurrentFragment(fragment);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment fragment = null;
        switch (item.getItemId()) {
            case R.id.navigation_video_from_device:
                if (hasPermissions()) {
                    fragment = new VideoFromDeviceFragment(model);
                } else {
                    requestPermissionWithRationale();
                    if (hasPermissions()) {
                        fragment = new VideoByLinkFragment(model);
                    } else {
                        fragment = new NoStoragePermissionFragment();
                    }
                }
                break;
            case R.id.navigation_video_by_link:
                fragment = new VideoByLinkFragment(model);
                break;
            case R.id.navigation_dashboard:
                fragment = new LoginFragment(model);
                break;
        }
        return loadFragment(fragment);
    }
}