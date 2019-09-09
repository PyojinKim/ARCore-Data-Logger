package com.pjinkim.arcore_data_logger;

import com.google.ar.sceneform.math.Vector3;

import java.util.ArrayList;

public class AccumulatedPointCloud {

    // properties
    private static final int BASE_CAPACITY = 100000;
    private ArrayList<Vector3> mPoints = new ArrayList<Vector3>();
    private ArrayList<Vector3> mColors = new ArrayList<Vector3>();
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
    public void appendPointCloud(int pointID, float pointX, float pointY, float pointZ, float r, float g, float b) {
        if (mIdentifiedIndices[pointID] != -99) {
            int existingIndex = mIdentifiedIndices[pointID];
            Vector3 pointPosition = new Vector3(pointX, pointY, pointZ);
            Vector3 pointColor = new Vector3(r, g, b);
            mPoints.set(existingIndex, pointPosition);
            mColors.set(existingIndex, pointColor);
        } else {
            mIdentifiedIndices[pointID] = mNumberOfFeatures;
            Vector3 pointPosition = new Vector3(pointX, pointY, pointZ);
            Vector3 pointColor = new Vector3(r, g, b);
            mPoints.add(pointPosition);
            mColors.add(pointColor);
            mNumberOfFeatures++;
        }
    }


    // getter and setter
    public int getNumberOfFeatures() {
        return mNumberOfFeatures;
    }

    public ArrayList<Vector3> getPoints() {
        return mPoints;
    }

    public ArrayList<Vector3> getColors() {
        return mColors;
    }
}
