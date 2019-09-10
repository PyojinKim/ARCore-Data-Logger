# ARCore Data Logger #

This is a simple application to capture ARCore motion estimation (Visual-Inertial Odometry) results on Android devices for offline use.
I want to play around with data from Google's Visual-Inertial Odometry (VIO) solution with ARCore framework API in Android Studio 3.4.2, API level 28 for Android devices.

![ARCore Data Logger](https://github.com/PyojinKim/ARCore-Data-Logger/blob/master/screenshot.png)

For more details, see the ARCore documentation [here](https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Frame).


## Usage Notes ##

The txt files are produced automatically after pressing Stop button.
This project is written Java under Android Studio Version 3.4.2 for Android 9.0 (API level 28) tested with Google Pixel 2 XL.
It doesn't currently check for sensor availability before logging.


## Reference Frames and Device Attitude ##

In the world (reference) coordinate space in ARCore, the Y-axis (up) always has +Y pointing up relative to gravity direction.
For the Z-axis, ARCore chooses a basis vector (0,0,-1) pointing in the direction the device camera faces and perpendicular to the gravity axis.
ARCore chooses a X-axis based on the Z- and Y-axes using the right hand rule.
When a device is held in its default (portrait) orientation, the ARCore Android sensor frame's X-axis is horizontal and points to the right, the Y-axis is vertical and points up, and the Z-axis points toward the outside of the screen face.
In this system, coordinates behind the screen have negative Z values.


## Output Format ##

I have chosen the following output formats, but they are easy to modify if you find something else more convenient.

* ARCore 6-DoF Sensor Pose (ARCore_sensor_pose.txt): `timestamp, q_x, q_y, q_z, q_w, t_x, t_y, t_z \n`
* ARCore 3D Point Cloud (ARCore_point_cloud.txt): `position_x, position_y, position_z, color_R, color_G, color_B \n`

Note that ARCore_sensor_pose.txt contains a N x 8 table, where N is the number of frames of this sequence.
Row i represents the i'th pose of the [Android Sensor Coordinate System](https://developer.android.com/guide/topics/sensors/sensors_overview#sensors-coords) in the world coordinate space for this frame.


## Offline MATLAB Visualization ##

The ability to experiment with different algorithms to process the ARCore (VIO) motion estimation results is the reason that I created this project in the first place.
I have included an example script that you can use to parse and visualize the data that comes from ARCore Data Logger.
Look under the Visualization directory to check it out.
You can run the script by typing the following in your terminal:

    run main_script.m

Here's one of the figures produced by the MATLAB script:

![Data visualization](https://github.com/PyojinKim/ARCore-Data-Logger/blob/master/data_visualization.png)

