package com.pjinkim.arcore_data_logger;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.BufferedWriter;
import java.io.IOException;
import java.security.KeyException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ARCoreSession {

    // properties
    private static final String LOG_TAG = ARCoreSession.class.getName();
    private static final long mulSecondToNanoSecond = 1000000000;
    private long previousTimestamp = 0;

    private MainActivity mContext;

    private AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private AtomicBoolean mIsWritingFile = new AtomicBoolean(false);

    private ArFragment mArFragment;
    private ARCoreResultStreamer mFileStreamer;


    // constructor
    public ARCoreSession(@NonNull MainActivity context) {

        // initialize object and ARCore fragment
        mContext = context;
        mArFragment = (ArFragment) mContext.getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        mArFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
    }


    // methods
    public void startSession(String streamFolder) {

        // initialize text file stream
        if (streamFolder != null) {
            try {
                mFileStreamer = new ARCoreResultStreamer(mContext, streamFolder);
                mIsWritingFile.set(true);
            } catch (IOException e) {
                mContext.showToast("Cannot create file for ARCore tracking results.");
                e.printStackTrace();
            }
        }
        mIsRecording.set(true);
    }

    public void stopSession() {

        // close text file and reset variables
        if (mIsWritingFile.get()) {
            try {
                mFileStreamer.endFiles();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mIsWritingFile.set(false);
        mIsRecording.set(false);
    }

    private void onUpdateFrame(FrameTime frameTime) {

        // set some variables
        boolean isFileSaved = (mIsRecording.get() && mIsWritingFile.get());

        // obtain current ARCore information
        Frame frame = mArFragment.getArSceneView().getArFrame();
        Camera camera = frame.getCamera();

        // update ARCore measurements
        long timestamp = frame.getTimestamp();
        double updateRate = (double) mulSecondToNanoSecond / (double) (timestamp - previousTimestamp);
        previousTimestamp = timestamp;

        Pose T_gc = frame.getAndroidSensorPose();



        Log.d(LOG_TAG, "onUpdateFrame: " + T_gc.toString());
        Log.d(LOG_TAG, "onUpdateFrame: updateRate: " + updateRate);


        /*if (camera.getTrackingState() == TrackingState.TRACKING) {
            Log.d(LOG_TAG, "onUpdateFrame: " + T_gc.toString());
            Log.d(LOG_TAG, "onUpdateFrame: updateRate: " + updateRate);
        }*/




    }


    // definition of 'ARCoreResultStreamer' class
    class ARCoreResultStreamer extends FileStreamer {

        // properties
        private BufferedWriter mWriter;


        // constructor
        ARCoreResultStreamer(final Context context, final String outputFolder) throws IOException {
            super(context, outputFolder);
            addFile("ARCore_pose", "ARCore_pose.txt");
            mWriter = getFileWriter("ARCore_pose");
        }


        // methods
        public void addARCorePoseRecord(final long timestamp, final Pose devicePoseFromARCore) throws IOException, KeyException {

            // execute the block with only one thread
            synchronized (this) {

                // check 'mWriter' variable
                if (mWriter == null) {
                    throw new KeyException("File writer 'ARCore_pose' not found.");
                }

                // record timestamp and 6-DoF device pose in text file
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(timestamp);
                //
                //
                //
                stringBuilder.append('\n');
                mWriter.write(stringBuilder.toString());
            }
        }

        @Override
        public void endFiles() throws IOException {

            // execute the block with only one thread
            synchronized (this) {
                mWriter.flush();
                mWriter.close();
            }
        }
    }


    // getter and setter




}