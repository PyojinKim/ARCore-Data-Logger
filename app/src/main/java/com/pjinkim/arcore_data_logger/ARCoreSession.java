package com.pjinkim.arcore_data_logger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.security.KeyException;
import java.util.ArrayList;
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
    private WorldToScreenTranslator mWorldToScreenTranslator;
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
        mWorldToScreenTranslator = new WorldToScreenTranslator();
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

        // save ARCore 3D point cloud only for visualization
        ArrayList<Vector3> pointsPosition = mAccumulatedPointCloud.getPoints();
        ArrayList<Vector3> pointsColor = mAccumulatedPointCloud.getColors();
        for (int i = 0; i < pointsPosition.size(); i++) {
            Vector3 currentPointPosition = pointsPosition.get(i);
            Vector3 currentPointColor = pointsColor.get(i);
            float pointX = currentPointPosition.x;
            float pointY = currentPointPosition.y;
            float pointZ = currentPointPosition.z;
            float r = currentPointColor.x;
            float g = currentPointColor.y;
            float b = currentPointColor.z;
            try {
                mFileStreamer.addARCorePointRecord(pointX, pointY, pointZ, r, g, b);
            } catch (IOException | KeyException e) {
                Log.d(LOG_TAG, "stopSession: Something is wrong.");
                e.printStackTrace();
            }
        }

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
        IntBuffer bufferPointID = pointCloud.getIds();
        FloatBuffer bufferPoint3D = pointCloud.getPoints();
        mPointCloudNode.visualize(pointCloud);
        int numberOfFeatures = mAccumulatedPointCloud.getNumberOfFeatures();
        pointCloud.release();

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
                Image imageFrame = frame.acquireCameraImage();
                Bitmap imageBitmap = imageToBitmap(imageFrame);
                imageFrame.close();
                for (int i = 0; i < (bufferPoint3D.limit() / 4); i++) {

                    // check each point's confidence level
                    float pointConfidence = bufferPoint3D.get(i * 4 + 3);
                    if (pointConfidence < 0.5) {
                        continue;
                    }

                    // obtain point ID and XYZ world position
                    int pointID = bufferPointID.get(i);
                    float pointX = bufferPoint3D.get(i * 4);
                    float pointY = bufferPoint3D.get(i * 4 + 1);
                    float pointZ = bufferPoint3D.get(i * 4 + 2);

                    // get each point RGB color information
                    float[] worldPosition = new float[]{pointX, pointY, pointZ};
                    Vector3 pointColor = getScreenPixel(worldPosition, imageBitmap);
                    if (pointColor == null) {
                        continue;
                    }

                    // append each point position and color information
                    mAccumulatedPointCloud.appendPointCloud(pointID, pointX, pointY, pointZ, pointColor.x, pointColor.y, pointColor.z);
                }
            }
        } catch (IOException | KeyException | NotYetAvailableException e) {
            Log.d(LOG_TAG, "onUpdateFrame: Something is wrong.");
            e.printStackTrace();
        }
    }


    private Bitmap imageToBitmap (Image image) {
        int width = image.getWidth();
        int height = image.getHeight();

        byte[] nv21;
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        nv21 = new byte[ySize + uSize + vSize];

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, os);
        byte[] jpegByteArray = os.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);

        Matrix matrix = new Matrix();
        matrix.setRotate(90);

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }


    private Vector3 getScreenPixel(float[] worldPosition, Bitmap imageBitmap) throws NotYetAvailableException {

        // clip to screen space (ViewMatrix * ProjectionMatrix * Anchor Matrix)
        double[] pos2D = mWorldToScreenTranslator.worldToScreen(imageBitmap.getWidth(), imageBitmap.getHeight(), mArFragment.getArSceneView().getArFrame().getCamera(), worldPosition);

        // check if inside the screen
        if ((pos2D[0] < 0) || (pos2D[0] > imageBitmap.getWidth()) || (pos2D[1] < 0) || (pos2D[1] > imageBitmap.getHeight())) {
            return null;
        }

        int pixel = imageBitmap.getPixel((int) pos2D[0], (int) pos2D[1]);
        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);
        Vector3 pointColor = new Vector3(r, g, b);

        return pointColor;
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


        public void addARCorePointRecord(final float pointX, final float pointY, final float pointZ, final float r, final float g, final float b) throws IOException, KeyException {

            // execute the block with only one thread
            synchronized (this) {

                // record 3D point cloud in text file
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(String.format(Locale.US, "%.6f %.6f %.6f %.2f %.2f %.2f", pointX, pointY, pointZ, r, g, b));
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
