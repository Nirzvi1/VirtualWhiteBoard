package com.nirzvi.virtualwhiteboard;

import android.content.res.Configuration;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;

import com.vuforia.CameraCalibration;
import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.Matrix44F;
import com.vuforia.ObjectTracker;
import com.vuforia.Renderer;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vec2I;
import com.vuforia.VideoBackgroundConfig;
import com.vuforia.VideoMode;
import com.vuforia.Vuforia;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by FIXIT on 2018-02-17.
 */

public class VuforiaManager {

    private static String LOGTAG = "VuforiaManager";

    private static Object managementLock;
    private static Object dataLock;

    private static InitVuforiaTask initTask;
    private static Runnable initCallback;
    private static LoadTrackerTask loadTask;

    private static boolean init = false;
    private static boolean started = false;
    private static boolean cameraRunning = false;

    private static ArrayList<String> fileNames;
    private static ArrayList<DataSet> dataSets;
    private static HashMap<String, double[]> trackingData;

    private static Matrix44F projectionMatrix;
    private static boolean mIsPortrait;
    private static int mScreenWidth;
    private static int mScreenHeight;

    private static Vuforia.UpdateCallbackInterface callback = new Vuforia.UpdateCallbackInterface() {
        @Override
        public void Vuforia_onUpdate(State s) {

            synchronized (dataLock) {

                int numResults = s.getNumTrackableResults();
                trackingData.clear();

                for (int i = 0; i < numResults; i++) {
                    TrackableResult result = s.getTrackableResult(i);

                    if (result != null) {

                        // Convert values into useful trackingData
                        //Angles are in radians distances are relative to the calibration image
                        //Also a timestamp is added
                        float[] data = result.getPose().getData();
                        float[][] rotation = {{data[0], data[1], data[2]},
                                {data[4], data[5], data[6]},
                                {data[8], data[9], data[10]}};

                        double thetaX = Math.atan2(rotation[2][1], rotation[2][2]);
                        double thetaY = Math.atan2(-rotation[2][0], Math.sqrt(rotation[2][1] * rotation[2][1] + rotation[2][2] * rotation[2][2]));
                        double thetaZ = Math.atan2(rotation[1][0], rotation[0][0]);

                        double[] tempVuforiaData = new double[7];

                        tempVuforiaData[0] = thetaX;
                        tempVuforiaData[1] = thetaY;
                        tempVuforiaData[2] = thetaZ;
                        tempVuforiaData[3] = data[3];
                        tempVuforiaData[4] = data[7];
                        tempVuforiaData[5] = data[11];
                        tempVuforiaData[6] = System.currentTimeMillis();

                        trackingData.put(result.getTrackable().getName(), tempVuforiaData);
                    }//if
                }//for
            }//synchronized
        }//Vuforia_onUpdate
    };

    /************************
     * START, STOP FUNCTIONS
     ************************/

    public static void init(Runnable callback) {

        initCallback = callback;

        managementLock = new Object();
        dataLock = new Object();

        fileNames = new ArrayList<>();
        dataSets = new ArrayList<>();
        trackingData = new HashMap<>();

        updateActivityOrientation();

        storeScreenDimensions();

        try {
            initTask = new InitVuforiaTask();
            initTask.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }//catch
    }//init

    private static boolean onInitDone(boolean success) {
        if (success) {
            initCallback.run();



            Log.i(LOGTAG, "Camera: " + startVuforiaCamera());
            CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

            init = true;
            return true;
        }//if

        return false;
    }//boolean

    public static void resumeVuforia() {
        if (init) {
            Vuforia.onResume();

            if (started) {
                startVuforiaCamera();
            }//if
        }//if
    }//resumeVuforia

    public static void pauseVuforia() {
        if (init) {
            if (started) {
                stopVuforiaCamera();
            }//if

            Vuforia.onPause();
        }//if
    }//pauseVuforia

    public static void destroy() {
        if (initTask != null && initTask.getStatus() != InitVuforiaTask.Status.FINISHED) {
            initTask.cancel(true);
        }//if

        if (loadTask != null && loadTask.getStatus() != LoadTrackerTask.Status.FINISHED) {
            loadTask.cancel(true);
        }//if

        initTask = null;
        loadTask = null;

        started = false;

        stopVuforiaCamera();

        synchronized (managementLock) {
            boolean unloadTrackersResult;
            boolean deinitTrackersResult;

            // Destroy the tracking data set:
            unloadTrackersResult = doUnloadTrackersData();

            // Deinitialize the trackers:
            deinitTrackersResult = doDeinitTrackers();

            // Deinitialize Vuforia SDK:
            Vuforia.deinit();

            if (!unloadTrackersResult) {
                throw new RuntimeException("Trackers Failed to Unload");
            }//if

            if (!deinitTrackersResult) {
                throw new RuntimeException("Failed to deinitialize trackers");
            }//if
        }//synch
    }//destroy

    /*******************
     * TRACKING FUNCTIONS
     *******************/

    /**
     * Initializes Vuforia's object tracker
     * @return true if successful
     */
    private static boolean doInitTrackers() {

        TrackerManager tManager = TrackerManager.getInstance();
        Tracker track = tManager.initTracker(ObjectTracker.getClassType());

        return track != null;
    }//doInitTrackers

    /**
     * De initializes Vuforia's object trackers
     * @return true if successful
     */
    private static boolean doDeinitTrackers() {

        TrackerManager tManager = TrackerManager.getInstance();
        return tManager.deinitTracker(ObjectTracker.getClassType());
    }//doDeinitTrackers

    /**
     * This gets the trackers to begin looking for any objects requested.
     * @return true if successful
     */
    private static boolean doStartTrackers() {

        Tracker track = TrackerManager.getInstance().getTracker(ObjectTracker.getClassType());

        if (track != null) {
            track.start();
        } else {
            return false;
        }//else

        return true;
    }//doStartTrackers

    /**
     * Stops tracking objects
     */
    private static void doStopTrackers() {

        Tracker objectTracker = TrackerManager.getInstance().getTracker(ObjectTracker.getClassType());

        if (objectTracker != null) {
            objectTracker.stop();
        }//if
    }//doStopTrackers

    /**
     * Add trackable datasets into Vuforia
     * Must be called before initialization
     * @param files All the files to be trackes
     */
    public static void addTrackables(String... files) {
        for (int i = 0; i < files.length; i++) {
            fileNames.add(files[i]);
        }//for
    }//addTrackables

    /**
     * This loads user defined trackingData files to tell Vuforia what to look for
     * @return true if the trackingData is successfully loaded
     */
    private static boolean doLoadTrackersData() {

        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager.getTracker(ObjectTracker.getClassType());

        if (objectTracker == null) {
            return false;
        }//if

        for (int i = 0; i < fileNames.size(); i++) {
            dataSets.add(objectTracker.createDataSet());

            if (dataSets.get(i) == null) {
                return false;
            }//if

            Log.i(LOGTAG, "Loading Dataset: " + fileNames.get(i));

            if (!dataSets.get(i).load(fileNames.get(i), STORAGE_TYPE.STORAGE_APPRESOURCE)) {
                return false;
            }//if


            if (!objectTracker.activateDataSet(dataSets.get(i))) {
                return false;
            }//if


            int numTrackables = dataSets.get(i).getNumTrackables();
            for (int count = 0; count < numTrackables; count++) {
                Trackable trackable = dataSets.get(i).getTrackable(count);

                String name = "Current Dataset: " + trackable.getName();
                trackable.setUserData(name);

                Log.d(LOGTAG, "UserData: Set the following user trackingData " + trackable.getUserData());
            }//for

        }//for


        return true;
    }//doLoadTrackersData

    /**
     * Unloads all trackable trackingData files when Vuforia is finished
     * @return true if successful
     */
    private static boolean doUnloadTrackersData() {

        // Indicate if the trackers were unloaded correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager.getTracker(ObjectTracker.getClassType());

        if (objectTracker == null) {
            return false;
        }//if

        for (int i = 0; i < dataSets.size(); i++) {
            if (dataSets.get(i) != null && dataSets.get(i).isActive()) {
                if (!objectTracker.deactivateDataSet(dataSets.get(i))) {
                    result = false;
                } else if (!objectTracker.destroyDataSet(dataSets.get(i))) {
                    result = false;
                }//else if

                dataSets.remove(i);
            }//if
        }//for

        return result;
    }//doUnloadTrackersData

    /*******************
     * CAMERA FUNCTIONS
     *******************/

    /**
     * Starts the camera
     * @return false if there is a camera error
     */
    private static boolean startVuforiaCamera() {

        if (!CameraDevice.getInstance().init(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT)) {
            return false;
        }//if

        if (!CameraDevice.getInstance().selectVideoMode(CameraDevice.MODE.MODE_DEFAULT)) {
            return false;
        }//if

        configureVideoBackground();

        if (!CameraDevice.getInstance().start()) {
            return false;
        }//if

        setProjectionMatrix();

        doStartTrackers();

        if (!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO)) {

            if (!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO)) {

                CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
            }//if
        }//if

        cameraRunning = true;
        return true;
    }//startVuforiaCamera

    /**
     * Turns off the camera
     */
    private static void stopVuforiaCamera() {

        if (cameraRunning) {
            doStopTrackers();
            CameraDevice.getInstance().stop();
            CameraDevice.getInstance().deinit();
            cameraRunning = false;
        }//if

    }//stopCamera

    public static Matrix44F getProjectionMatrix() {
        return projectionMatrix;
    }//getProjectionMatrix

    // Stores screen dimensions
    public static void storeScreenDimensions()
    {
        // Query display dimensions:
        DisplayMetrics metrics = new DisplayMetrics();
        MainActivity.fetchApp().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
    }


    // Stores the orientation depending on the current resources configuration
    public static void updateActivityOrientation()
    {
        Configuration config = MainActivity.fetchApp().getResources().getConfiguration();

        switch (config.orientation)
        {
            case Configuration.ORIENTATION_PORTRAIT:
                mIsPortrait = true;
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                mIsPortrait = false;
                break;
            case Configuration.ORIENTATION_UNDEFINED:
            default:
                break;
        }

        Log.i(LOGTAG, "Activity is in "
                + (mIsPortrait ? "PORTRAIT" : "LANDSCAPE"));
    }


    // Method for setting / updating the projection matrix for AR content
    // rendering
    public static void setProjectionMatrix()
    {
        CameraCalibration camCal = CameraDevice.getInstance()
                .getCameraCalibration();
        projectionMatrix = Tool.getProjectionGL(camCal, 10.0f, 5000.0f);
    }

    public static void configureVideoBackground()
    {
        CameraDevice cameraDevice = CameraDevice.getInstance();
        VideoMode vm = cameraDevice.getVideoMode(CameraDevice.MODE.MODE_DEFAULT);

        VideoBackgroundConfig config = new VideoBackgroundConfig();
        config.setEnabled(true);
        config.setPosition(new Vec2I(0, 0));

        int xSize = 0, ySize = 0;
        if (mIsPortrait)
        {
            xSize = (int) (vm.getHeight() * (mScreenHeight / (float) vm
                    .getWidth()));
            ySize = mScreenHeight;

            if (xSize < mScreenWidth)
            {
                xSize = mScreenWidth;
                ySize = (int) (mScreenWidth * (vm.getWidth() / (float) vm
                        .getHeight()));
            }
        } else
        {
            xSize = mScreenWidth;
            ySize = (int) (vm.getHeight() * (mScreenWidth / (float) vm
                    .getWidth()));

            if (ySize < mScreenHeight)
            {
                xSize = (int) (mScreenHeight * (vm.getWidth() / (float) vm
                        .getHeight()));
                ySize = mScreenHeight;
            }
        }

        config.setSize(new Vec2I(xSize, ySize));

        Log.i(LOGTAG, "Configure Video Background : Video (" + vm.getWidth()
                + " , " + vm.getHeight() + "), Screen (" + mScreenWidth + " , "
                + mScreenHeight + "), mSize (" + xSize + " , " + ySize + ")");

        Renderer.getInstance().setVideoBackgroundConfig(config);

    }

    /****************
     * INIT, LOAD Tasks
     ****************/

    /**
     * A background task for intializing the Vuforia sdk
     */
    private static class InitVuforiaTask extends AsyncTask<Void, Integer, Boolean> {
        // Initialize with invalid value:
        private int mProgressValue = -1;

        protected Boolean doInBackground(Void... params) {
            // Prevent the onDestroy() method to overlap with initialization:
            synchronized (managementLock) {
                Vuforia.setInitParameters(MainActivity.fetchApp(), Vuforia.GL_20, "AdaExUv/////AAAAGfR3NIO1HkxSqM8NPhlEftEycERGjkK9h5WE4CT+9THzXX4i6ST61Ep0FeZZb/Fv4YR7a8X22c1bqrVOdqLRxjiMx+iHlYmgKBmBvccL1IJpM6elq2m9ZDam7QsQJG1qZfWe+f8EY24O2o9pnH9g31brYMSpRFpPx3Vnk8l0D+sxcgtZy0WGi8vj98PX7XDGM+DOxE4Jh9wBtDeBkIY7RlBf/oxzsU9K2lZXQJJxITd2HIF7vrunk9OvHgiHtllCLRGI7UAnsp/uy1ZQOeCNx3knHXS04daqIgxDCGA+0PHSST6LXNSksCCEwxlLhYig78xfgYkNZrG8+vNl+7tpHFpzHdP4Ys9wlMdFc9whmztx");

                do {
                    // Vuforia.init() blocks until an initialization step is
                    // complete, then it proceeds to the next step and reports
                    // progress in percents (0 ... 100%).
                    // If Vuforia.init() returns -1, it indicates an error.
                    // Initialization is done when progress has reached 100%.
                    mProgressValue = Vuforia.init();

                    // Publish the progress value:
                    publishProgress(mProgressValue);

                    // We check whether the task has been canceled in the
                    // meantime (by calling AsyncTask.cancel(true)).
                    // and bail out if it has, thus stopping this thread.
                    // This is necessary as the AsyncTask will run to completion
                    // regardless of the status of the component that
                    // started is.
                } while (!isCancelled() && mProgressValue >= 0 && mProgressValue < 100);

                return (mProgressValue > 0);

            }//synchronized
        }//doInBackground

        protected void onPostExecute(Boolean result) {
            // Done initializing Vuforia, proceed to next application
            // initialization status:

            if (result) {
                Log.d(LOGTAG, "InitVuforiaTask.onPostExecute: Vuforia initialization successful");

                boolean initTrackersResult;
                initTrackersResult = doInitTrackers();

                if (initTrackersResult) {
                    try {
                        loadTask = new LoadTrackerTask();
                        loadTask.execute();
                    } catch (Exception e) {
                        String logMessage = "Loading tracking trackingData set failed";
                        Log.e(LOGTAG, logMessage);
                        onInitDone(true);
                    }//catch

                } else {
                    onInitDone(false);
                }//else

            } else {
                onInitDone(false);
            }//else
        }//onPostExecute
    }//InitVuforiaTask

    // An async task to load the tracker trackingData asynchronously.
    private static class LoadTrackerTask extends AsyncTask<Void, Integer, Boolean> {

        protected Boolean doInBackground(Void... params) {

            // Prevent the onDestroy() method to overlap:
            synchronized (managementLock) {
                // Load the tracker trackingData set:
                return doLoadTrackersData();
            }//synchronized
        }//doInBackground


        protected void onPostExecute(Boolean result) {
            Log.i(LOGTAG, "" + result);
            if (!result) {
                onInitDone(false);
            } else {
                // Hint to the virtual machine that it would be a good time to
                // run the garbage collector:

                // NOTE: This is only a hint. There is no guarantee that the
                // garbage collector will actually be run.
                System.gc();

                Vuforia.registerCallback(callback);

                started = true;
                onInitDone(true);
            }//else
        }//onPostExecute
    }//LoadTrackerTask



}
