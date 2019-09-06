package com.pjinkim.arcore_data_logger;

import com.google.ar.sceneform.math.Vector3;

import java.util.ArrayList;

public class AccumulatedPointCloud {

    // properties
    private static final int BASE_CAPACITY = 100000;
    private ArrayList<Vector3> mPoints = new ArrayList<Vector3>();
    private int[] mIdentifiedIndices = new int[BASE_CAPACITY];
    private int mNumberOfFeatures = 0;


    // constructor
    public AccumulatedPointCloud() {

        // initialize properties
        for (int i = 0; i < BASE_CAPACITY; i++) {
            mIdentifiedIndices[i] = -99;
        }
    }


    // methods
    public void appendPointCloud(int pointID, float pointX, float pointY, float pointZ) {
        if (mIdentifiedIndices[pointID] != -99) {
            int existingIndex = mIdentifiedIndices[pointID];
            Vector3 point3DPosition = new Vector3(pointX, pointY, pointZ);
            mPoints.set(existingIndex, point3DPosition);
        } else {
            mIdentifiedIndices[pointID] = mNumberOfFeatures;
            Vector3 point3DPosition = new Vector3(pointX, pointY, pointZ);
            mPoints.add(point3DPosition);
            mNumberOfFeatures++;
        }
    }


    // getter and setter
    public int getNumberOfFeatures() {
        return mNumberOfFeatures;
    }
}
