package com.nirzvi.virtualwhiteboard;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.vuforia.Device;

public class MainActivity extends AppCompatActivity {

    private static Activity publicContext;
    private VuforiaGLView glView;
    private VuforiaRenderer render;

    private static Bitmap whiteBoardBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        publicContext = this;

        VuforiaManager.init(new Runnable() {
            @Override
            public void run() {
                glView = new VuforiaGLView(MainActivity.this);
                glView.init(true, 16, 0);

                render = new VuforiaRenderer(MainActivity.this, Device.MODE.MODE_AR, false, 0.01f, 5f);
                glView.setRenderer(render);

                  setContentView(glView);

            }
        });
        VuforiaManager.addTrackables("FredTheStag.xml");


        whiteBoardBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.whiteboard).copy(Bitmap.Config.ARGB_8888, true);

    }


    public static Bitmap fetchWhiteBoardBitmap() {
        return whiteBoardBitmap;
    }

    public static void setWhiteBoardBitmap(Bitmap bit) {
        whiteBoardBitmap = bit;
    }

    @Override
    protected void onResume() {
        super.onResume();

        VuforiaManager.resumeVuforia();

        if (glView != null) {
            glView.setVisibility(View.VISIBLE);
            glView.onResume();
        }//if
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (glView != null) {
            glView.setVisibility(View.INVISIBLE);
            glView.onPause();
        }//if

        VuforiaManager.pauseVuforia();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        VuforiaManager.destroy();
    }

    @Override
    public void onConfigurationChanged(Configuration config)
    {
        super.onConfigurationChanged(config);

        VuforiaManager.updateActivityOrientation();
        VuforiaManager.storeScreenDimensions();

        VuforiaManager.configureVideoBackground();
        VuforiaManager.setProjectionMatrix();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_edit_whiteboard:
                Intent editActivity = new Intent(this, EditWhiteBoardActivity.class);
                startActivity(editActivity);
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    public static Activity fetchApp() {
        return publicContext;
    }

}
