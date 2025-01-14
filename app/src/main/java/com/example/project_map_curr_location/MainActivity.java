package com.example.project_map_curr_location;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.etebarian.meowbottomnavigation.MeowBottomNavigation;
import com.example.project_map_curr_location.database.DataBaseHelper;
import com.example.project_map_curr_location.domain.Moto1;
import com.example.project_map_curr_location.domain.User;
import com.example.project_map_curr_location.fragment.FakeMopedFragment;
import com.example.project_map_curr_location.fragment.MopedFragment;
import com.example.project_map_curr_location.fragment.SettingsFragment3;
import com.example.project_map_curr_location.fragment.YandexMapFragment;
import com.example.project_map_curr_location.rest.MotoApiVolley;
import com.example.project_map_curr_location.rest.UserApiVolley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {


    private static final int REQUEST_CODE_SPEECH_INPUT = 1000;
    private static final int RecordAudioRequestCode = 201;
    private FragmentManager fm = getSupportFragmentManager();
    private Fragment fragment_map;
    private Fragment fragment_settings;
    private Fragment fragment_moped;
    private Fragment curr_fragment;
    private Fragment fragment_fake_moped;

    private AppCompatEditText et_FindLocation;

    private SharedPreferences sharedPreferences;

    private MeowBottomNavigation bottomNavigation;

    private MediaPlayer sound;

    public static final int DEFAULT_UPDATE_INTERVAL = 30;
    public static final int FASTEST_UPDATE_INTERVAL = 5;
    private static final int PERMISSIONS_FINE_LOCATION = 100;
    private boolean geoStatus;
    private Thread geoThread = new MyTread();

    private List<Moto1> motorcycleArrayList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        geoStatus = true;
        SQLiteDatabase db = getBaseContext().openOrCreateDatabase("app1.db", MODE_PRIVATE, null);
        Thread geoThread = new MyTread();
        geoThread.setDaemon(true);
        geoThread.start();

        int s = loadDataInt(getString(R.string.car_or_moto));
        String status;

        if (loadDataBoolean(getString(R.string.tripStatus))) {
            if (s == 0) {
                status = "car";
            } else {
                status = "moto";
            }
        } else {
            status = "car";
        }

        if (!loadDataBoolean(getString(R.string.userLogged))) {
            new UserApiVolley(this).getNewId();
            User user = new User("", loadDataString(getString(R.string.userNickname)), "", status);
            new UserApiVolley(this).addUser(user);
            saveDataBoolean(getString(R.string.userLogged), true);
        }

        setMotorcycleArrayList();
        setBottomNavigation();
        micAndLogin();
        et_FindLocation = findViewById(R.id.et_FindLocation);
        et_FindLocation.setHint(R.string.inputAddressEditText);

        et_FindLocation.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                saveDataString(getString(R.string.findLocationEditText), String.valueOf(et_FindLocation.getText()));
            }
        });

        et_FindLocation.setText(loadDataString(getString(R.string.findLocationEditText)));

        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(1000 * DEFAULT_UPDATE_INTERVAL);
        locationRequest.setFastestInterval(1000 * FASTEST_UPDATE_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        new DataBaseHelper(this).onCreate(db);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveDataInt(getString(R.string.car_or_moto), 1);
        geoStatus = false;
        if (geoThread != null) {
            Thread dummy = geoThread;
            geoThread = null;
            dummy.interrupt();
        }

    }

    private class MyTread extends Thread {

        @Override
        public void run() {

            while (geoStatus) {
                try {

                    updateGPS();

                    if (loadDataInt(getString(R.string.car_or_moto)) == 0) {

                        int c = loadDataInt("motoCount");

                        try {
                            new MotoApiVolley(MainActivity.this).fillMoto();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        if (c < loadDataInt("motoCount")) {
                            playSoundStart();
                            saveDataInt("motoCount", c);
                        }
                    } else {
                        if (loadDataInt(getString(R.string.motoId)) != -1) {
                            new MotoApiVolley(MainActivity.this).updateMoto(
                                    loadDataInt(getString(R.string.motoId)),
                                    loadDataInt("userId"),
                                    loadDataInt(getString(R.string.actualSpeed)),
                                    loadDataFloat(getString(R.string.actualCameraPositionLat)),
                                    loadDataFloat(getString(R.string.actualCameraPositionLon)),
                                    loadDataFloat(getString(R.string.actualCameraPositionAlt)));
                        }

                    }
                    try {
                        sleep(3 * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void updateUIVAluses(Location location) {
        try {
            saveDataFloat(getString(R.string.actualCameraPositionLat), (float) location.getLatitude());
            saveDataFloat(getString(R.string.actualCameraPositionLon), (float) location.getLongitude());
            saveDataInt(getString(R.string.actualSpeed), (int) location.getSpeed());
            saveDataFloat(getString(R.string.actualCameraPositionAlt), (float) location.getAltitude());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_FINE_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateGPS();
            } else {
                Toast.makeText(MainActivity.this, "no GPS!", Toast.LENGTH_SHORT).show();
            }
        }

    }

    private void updateGPS() {
        FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, this::updateUIVAluses);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_FINE_LOCATION);
            }
        }
    }


    public void cancelTripEditText() {
        saveDataString(getString(R.string.findLocationEditText), "");
        et_FindLocation.setText("");
    }

    private void micAndLogin() {
        et_FindLocation = findViewById(R.id.et_FindLocation);
        AppCompatImageButton btn_findLocationMicro = findViewById(R.id.btn_findLocationMicro);


        btn_findLocationMicro.setOnClickListener(view -> {
            checkPermission();
            speak();
        });
    }


    private void speak() {

        Locale russian = new Locale("RU");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                russian);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Куда Вам надо?");

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (Exception ignored) {

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SPEECH_INPUT) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                String res = result.get(0);
                et_FindLocation.setText((CharSequence) res);

                saveDataString(getString(R.string.findLocationEditText), String.valueOf(res));
            }
        }


    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RecordAudioRequestCode
            );
        }
    }

    public void playSoundStart() {
        sound = MediaPlayer.create(this, R.raw.start);
        if (sound.isPlaying()) {
            sound.stop();
        }
        sound.setLooping(false);
        sound.start();
    }

    public void playSoundEnd() {
        sound = MediaPlayer.create(this, R.raw.back);
        if (sound.isPlaying()) {
            sound.stop();
        }
        sound.setLooping(false);
        sound.start();
    }

    public void loadMapForTrip() {
        curr_fragment = fragment_map;
        fm.beginTransaction().replace(R.id.frame_layout, curr_fragment).addToBackStack(null).commit();
        bottomNavigation.show(2, true);
    }

    public void loadFragment(Fragment fragment) {
        fm.beginTransaction().replace(R.id.frame_layout, fragment).addToBackStack(null).commit();
    }

    public void loadSettings() {
        curr_fragment = fragment_settings;
        fm.beginTransaction().replace(R.id.frame_layout, curr_fragment).addToBackStack(null).commit();
        bottomNavigation.show(1, true);
    }

    @Override
    public void onBackPressed() {
        if (curr_fragment == fragment_map) {
            finish();
        } else {
            curr_fragment = fragment_map;
            loadFragment(curr_fragment);
            bottomNavigation.show(2, true);
        }
    }

    public void setBottomNavigation() {
        fragment_map = new YandexMapFragment(motorcycleArrayList);
        fragment_settings = new SettingsFragment3();
        fragment_moped = new MopedFragment();
        fragment_fake_moped = new FakeMopedFragment();

        curr_fragment = fragment_map;


        loadFragment(curr_fragment);


        bottomNavigation = findViewById(R.id.bottom_navigation);
        bottomNavigation.add(new MeowBottomNavigation.Model(1, R.drawable.ic_settings));
        bottomNavigation.add(new MeowBottomNavigation.Model(2, R.drawable.ic_map));
        bottomNavigation.add(new MeowBottomNavigation.Model(3, R.drawable.ic_moped));

        bottomNavigation.setOnShowListener(item -> {

            switch (item.getId()) {
                case 1:
                    curr_fragment = fragment_settings;
                    break;
                case 2:
                    curr_fragment = fragment_map;
                    break;
                case 3:
                    if (loadDataInt(getString(R.string.car_or_moto)) == 1 || !loadDataBoolean(getString(R.string.tripStatus))) {
                        curr_fragment = fragment_fake_moped;
                    } else {
                        curr_fragment = fragment_moped;
                    }
                    break;
            }

            loadFragment(curr_fragment);
        });

        bottomNavigation.show(2, true);

        bottomNavigation.setOnClickMenuListener(item -> {

        });

        bottomNavigation.setOnReselectListener(item -> {

        });
    }

    public void setMotorcycleArrayList() {
        motorcycleArrayList = new ArrayList<>();
    }

    public void sendNotificationStatus() {
        if (loadDataBoolean(getString(R.string.notification_status))) {
            int x = loadDataInt("car_or_moto");
            CharSequence title = getText(R.string.notification_title_car);
            CharSequence desc = getText(R.string.notification_desc_car);
            if (x == 1) {
                desc = getText(R.string.notification_desc_moto);
                title = getText(R.string.notification_title_moto);
            }

            Intent intentMainActivity = new Intent(this, MainActivity.class);
            PendingIntent pIntentMainActivity = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                pIntentMainActivity = PendingIntent.getActivity(this, 0, intentMainActivity, PendingIntent.FLAG_MUTABLE);
            }

            String NOTIFICATION_CHANNEL_ID = "123";
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_settings)
                    .setContentTitle(title)
                    .setContentText(desc)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(getText(R.string.notification_big_text)))
                    .setOngoing(true)
                    .setContentIntent(pIntentMainActivity)
                    .setAutoCancel(false);


            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.notify(1, builder.build());
        }
    }

    public void cancelNotification(NotificationManagerCompat notificationManager, int id) {
        try {
            notificationManager.cancel(id);
        } catch (Exception ignored) {

        }

    }

    public void saveDataBoolean(String key, boolean b) {
        sharedPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, b);
        editor.apply();
    }

    public boolean loadDataBoolean(String key) {
        sharedPreferences = getPreferences(MODE_PRIVATE);
        return sharedPreferences.getBoolean(key, false);
    }

    public void saveDataInt(String key, int value) {
        sharedPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    public int loadDataInt(String key) {
        sharedPreferences = getPreferences(MODE_PRIVATE);
        return sharedPreferences.getInt(key, -1);
    }

    public void saveDataFloat(String key, float value) {
        sharedPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(key, value);
        editor.apply();
    }

    public float loadDataFloat(String key) {
        sharedPreferences = getPreferences(MODE_PRIVATE);
        return sharedPreferences.getFloat(key, 0.0F);
    }

    public void saveDataString(String key, String value) {
        sharedPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    public String loadDataString(String key) {
        sharedPreferences = getPreferences(MODE_PRIVATE);
        return sharedPreferences.getString(key, "");
    }

}