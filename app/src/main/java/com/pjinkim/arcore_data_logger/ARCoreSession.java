package com.pjinkim.arcore_data_logger;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Frame;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.security.KeyException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class ARCoreSession {

    // properties
    private static final String LOG_TAG = ARCoreSession.class.getName();
    private static final long mulSecondToNanoSecond = 1000000000;
    private long previousTimestamp = 0;

    private MainActivity mContext;
    private ArFragment mArFragment;
    private PointCloudNode mPointCloudNode;
    private AccumulatedPointCloud mAccumulatedPointCloud;
    private ARCoreResultStreamer mFileStreamer = null;

    private AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private AtomicBoolean mIsWritingFile = new AtomicBoolean(false);

    private int mNumberOfFeatures = 0;
    private TrackingState mTrackingState;
    private TrackingFailureReason mTrackingFailureReason;
    private double mUpdateRate = 0;


    // constructor
    public ARCoreSession(@NonNull MainActivity context) {

        // initialize object and ARCore fragment
        mContext = context;
        mArFragment = (ArFragment) mContext.getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        mArFragment.getArSceneView().getPlaneRenderer().setVisible(false);
        mArFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);

        // render 3D point cloud on the screen
        mPointCloudNode = new PointCloudNode(mContext);
        mArFragment.getArSceneView().getScene().addChild(mPointCloudNode);
        mAccumulatedPointCloud = new AccumulatedPointCloud();
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
        mArFragment.onUpdate(frameTime);
        Frame frame = mArFragment.getArSceneView().getArFrame();
        Camera camera = frame.getCamera();

        // update ARCore measurements
        long timestamp = frame.getTimestamp();
        double updateRate = (double) mulSecondToNanoSecond / (double) (timestamp - previousTimestamp);
        previousTimestamp = timestamp;

        CameraIntrinsics cameraIntrinsics = camera.getImageIntrinsics();
        TrackingState trackingState = camera.getTrackingState();
        TrackingFailureReason trackingFailureReason = camera.getTrackingFailureReason();
        Pose T_gc = frame.getAndroidSensorPose();

        float qx = T_gc.qx();
        float qy = T_gc.qy();
        float qz = T_gc.qz();
        float qw = T_gc.qw();

        float tx = T_gc.tx();
        float ty = T_gc.ty();
        float tz = T_gc.tz();

        // update 3D point cloud from ARCore
        PointCloud pointCloud = frame.acquirePointCloud();
        mPointCloudNode.visualize(pointCloud);
        int numberOfFeatures = mAccumulatedPointCloud.getNumberOfFeatures();

        //////////////////////////////////////////////////////////////////////////////////////////

        IntBuffer bufferPointID = pointCloud.getIds();
        FloatBuffer bufferPoint3D = pointCloud.getPoints();


        // display and save ARCore information
        try {
            mNumberOfFeatures = numberOfFeatures;
            mTrackingState = trackingState;
            mTrackingFailureReason = trackingFailureReason;
            mUpdateRate = updateRate;
            if (isFileSaved) {

                // 1) record ARCore 6-DoF sensor pose
                mFileStreamer.addARCorePoseRecord(timestamp, qx, qy, qz, qw, tx, ty, tz);

                // 2) record ARCore 3D point cloud only for visualization
                for (int i = 0; i < (bufferPoint3D.limit() / 4); i++) {
                    int pointID = bufferPointID.get(i);
                    float pointX = bufferPoint3D.get(i * 4);
                    float pointY = bufferPoint3D.get(i * 4 + 1);
                    float pointZ = bufferPoint3D.get(i * 4 + 2);
                    mAccumulatedPointCloud.appendPointCloud(pointID, pointX, pointY, pointZ);
                }
            }
            pointCloud.release();
        } catch (IOException | KeyException e) {
            Log.d(LOG_TAG, "onUpdateFrame: Something is wrong.");
            e.printStackTrace();
        }
    }


    // definition of 'ARCoreResultStreamer' class
    class ARCoreResultStreamer extends FileStreamer {

        // properties
        private BufferedWriter mWriterPose;
        private BufferedWriter mWriterPoint;


        // constructor
        ARCoreResultStreamer(final Context context, final String outputFolder) throws IOException {
            super(context, outputFolder);
            addFile("ARCore_sensor_pose", "ARCore_sensor_pose.txt");
            addFile("ARCore_point_cloud", "ARCore_point_cloud.txt");
            mWriterPose = getFileWriter("ARCore_sensor_pose");
            mWriterPoint = getFileWriter("ARCore_point_cloud");
        }


        // methods
        public void addARCorePoseRecord(long timestamp, float qx, float qy, float qz, float qw, float tx, float ty, float tz) throws IOException, KeyException {

            // execute the block with only one thread
            synchronized (this) {

                // record timestamp and 6-DoF device pose in text file
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(timestamp);
                stringBuilder.append(String.format(Locale.US, " %.6f %.6f %.6f %.6f %.6f %.6f %.6f", qx, qy, qz, qw, tx, ty, tz));
                stringBuilder.append(" \n");
                mWriterPose.write(stringBuilder.toString());
            }
        }


        public void addARCorePointRecord(final int pointID, final float pointX, final float pointY, final float pointZ) throws IOException, KeyException {

            // execute the block with only one thread
            synchronized (this) {

                // record 3D point cloud in text file
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(String.format(Locale.US, "%05d %.6f %.6f %.6f", pointID, pointX, pointY, pointZ));
                stringBuilder.append(" \n");
                mWriterPoint.write(stringBuilder.toString());
            }
        }


        @Override
        public void endFiles() throws IOException {

            // execute the block with only one thread
            synchronized (this) {
                mWriterPose.flush();
                mWriterPose.close();
                mWriterPoint.flush();
                mWriterPoint.close();
            }
        }
    }


    // getter and setter
    public int getNumberOfFeatures() {
        return mNumberOfFeatures;
    }

    public TrackingState getTrackingState() {
        return mTrackingState;
    }

    public TrackingFailureReason getTrackingFailureReason() {
        return mTrackingFailureReason;
    }

    public double getUpdateRate() {
        return mUpdateRate;
    }
}
