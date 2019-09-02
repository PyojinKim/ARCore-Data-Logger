package com.pjinkim.arcore_data_logger;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    // properties
    private static final String LOG_TAG = MainActivity.class.getName();
    private static final double MIN_OPENGL_VERSION = 3.0;

    private ARCoreSession mARCoreSession;

    private Handler mHandler = new Handler();
    private AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private PowerManager.WakeLock mWakeLock;

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


        // monitor ARCore results
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
            OutputDirectoryManager folder = new OutputDirectoryManager("", "R_ARCore");
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

        mStartStopButton = (Button) findViewById(R.id.button_start_stop);
        mLabelInterfaceTime = (TextView) findViewById(R.id.label_interface_time);
    }
}
