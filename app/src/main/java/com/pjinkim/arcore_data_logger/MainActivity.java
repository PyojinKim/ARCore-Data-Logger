package com.pjinkim.arcore_data_logger;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;

import java.io.IOException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    // properties
    private static final String LOG_TAG = MainActivity.class.getName();
    private static final double MIN_OPENGL_VERSION = 3.0;

    private final static int REQUEST_CODE_ANDROID = 1001;
    private static String[] REQUIRED_PERMISSIONS = new String[] {
            Manifest.permission.CAMERA,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private ARCoreSession mARCoreSession;

    private Handler mHandler = new Handler();
    private AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private PowerManager.WakeLock mWakeLock;

    private TextView mLabelNumberFeatures, mLabelUpdateRate;
    private TextView mLabelTrackingStatus, mLabelTrackingFailureReason;

    private Button mStartStopButton;
    private TextView mLabelInterfaceTime;
    private Timer mInterfaceTimer = new Timer();
    private int mSecondCounter = 0;


    // Android activity lifecycle states
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // check Android and OpenGL version
        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }


        // initialize screen labels and buttons
        initializeViews();


        // setup sessions
        mARCoreSession = new ARCoreSession(this);


        // battery power setting
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sensors_data_logger:wakelocktag");
        mWakeLock.acquire();


        // monitor ARCore information
        displayARCoreInformation();
        mLabelInterfaceTime.setText(R.string.ready_title);
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_ANDROID);
        }
    }


    @Override
    protected void onDestroy() {
        if (mIsRecording.get()) {
            stopRecording();
        }
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        super.onDestroy();
    }


    // methods
    public void startStopRecording(View view) {
        if (!mIsRecording.get()) {

            // start recording sensor measurements when button is pressed
            startRecording();

            // start interface timer on display
            mSecondCounter = 0;
            mInterfaceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mSecondCounter += 1;
                    mLabelInterfaceTime.setText(interfaceIntTime(mSecondCounter));
                }
            }, 0, 1000);

        } else {

            // stop recording sensor measurements when button is pressed
            stopRecording();

            // stop interface timer on display
            mInterfaceTimer.cancel();
            mLabelInterfaceTime.setText(R.string.ready_title);
        }
    }


    private void startRecording() {

        // output directory for text files
        String outputFolder = null;
        try {
            OutputDirectoryManager folder = new OutputDirectoryManager("", "R_pjinkim_ARCore");
            outputFolder = folder.getOutputDirectory();
        } catch (IOException e) {
            Log.e(LOG_TAG, "startRecording: Cannot create output folder.");
            e.printStackTrace();
        }

        // start ARCore session
        mARCoreSession.startSession(outputFolder);
        mIsRecording.set(true);

        // update Start/Stop button UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStartStopButton.setEnabled(true);
                mStartStopButton.setText(R.string.stop_title);
            }
        });
        showToast("Recording starts!");
    }


    protected void stopRecording() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {

                // stop ARCore session
                mARCoreSession.stopSession();
                mIsRecording.set(false);

                // update screen UI and button
                showToast("Recording stops!");
                resetUI();
            }
        });
    }


    public void showToast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void resetUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLabelNumberFeatures.setText("N/A");
                mLabelTrackingStatus.setText("N/A");
                mLabelTrackingFailureReason.setText("N/A");
                mLabelUpdateRate.setText("N/A");

                mStartStopButton.setEnabled(true);
                mStartStopButton.setText(R.string.start_title);
            }
        });
    }


    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {

        // check Android version
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(LOG_TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }

        // get current OpenGL version
        String openGlVersionString = ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                .getDeviceConfigurationInfo()
                .getGlEsVersion();

        // check OpenGL version
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(LOG_TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        return true;
    }


    @Override
    public void onBackPressed() {

        // nullify back button when recording starts
        if (!mIsRecording.get()) {
            super.onBackPressed();
        }
    }


    private static boolean hasPermissions(Context context, String... permissions) {

        // check Android hardware permissions
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    private String interfaceIntTime(final int second) {

        // check second input
        if (second < 0) {
            Log.e(LOG_TAG, "interfaceIntTime: Second cannot be negative.");
            return null;
        }

        // extract hour, minute, second information from second
        int input = second;
        int hours = input / 3600;
        input = input % 3600;
        int mins = input / 60;
        int secs = input % 60;

        // return interface int time
        return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs);
    }


    private void initializeViews() {

        mLabelNumberFeatures = (TextView) findViewById(R.id.label_number_features);
        mLabelTrackingStatus = (TextView) findViewById(R.id.label_tracking_status);
        mLabelTrackingFailureReason = (TextView) findViewById(R.id.label_tracking_failure_reason);
        mLabelUpdateRate = (TextView) findViewById(R.id.label_update_rate);

        mStartStopButton = (Button) findViewById(R.id.button_start_stop);
        mLabelInterfaceTime = (TextView) findViewById(R.id.label_interface_time);
    }


    private void displayARCoreInformation() {

        // get ARCore tracking information
        int numberOfFeatures = mARCoreSession.getNumberOfFeatures();
        TrackingState trackingState = mARCoreSession.getTrackingState();
        TrackingFailureReason trackingFailureReason =  mARCoreSession.getTrackingFailureReason();
        double updateRate = mARCoreSession.getUpdateRate();

        // update current screen (activity)
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                // determine TrackingState text
                String ARCoreTrackingState = "";
                if (trackingState == TrackingState.PAUSED) {
                    ARCoreTrackingState = "PAUSED";
                } else if (trackingState == TrackingState.STOPPED) {
                    ARCoreTrackingState = "STOPPED";
                } else if (trackingState == TrackingState.TRACKING) {
                    ARCoreTrackingState = "TRACKING";
                } else {
                    ARCoreTrackingState = "ERROR?";
                }

                // determine TrackingFailureReason text
                String ARCoreTrackingFailureReason = "";
                if (trackingFailureReason == TrackingFailureReason.BAD_STATE) {
                    ARCoreTrackingFailureReason = "BAD STATE";
                } else if (trackingFailureReason == TrackingFailureReason.EXCESSIVE_MOTION) {
                    ARCoreTrackingFailureReason = "FAST MOTION";
                } else if (trackingFailureReason == TrackingFailureReason.INSUFFICIENT_FEATURES) {
                    ARCoreTrackingFailureReason = "LOW FEATURES";
                } else if (trackingFailureReason == TrackingFailureReason.INSUFFICIENT_LIGHT) {
                    ARCoreTrackingFailureReason = "LOW LIGHT";
                } else if (trackingFailureReason == TrackingFailureReason.NONE) {
                    ARCoreTrackingFailureReason = "NONE";
                } else {
                    ARCoreTrackingFailureReason = "ERROR?";
                }

                // update interface screen labels
                mLabelNumberFeatures.setText(String.format(Locale.US, "%05d", numberOfFeatures));
                mLabelTrackingStatus.setText(ARCoreTrackingState);
                mLabelTrackingFailureReason.setText(ARCoreTrackingFailureReason);
                mLabelUpdateRate.setText(String.format(Locale.US, "%.3f Hz", updateRate));
            }
        });

        // determine display update rate (100 ms)
        final long displayInterval = 100;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                displayARCoreInformation();
            }
        }, displayInterval);
    }
}
