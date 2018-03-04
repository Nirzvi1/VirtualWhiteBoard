package com.nirzvi.virtualwhiteboard;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.EditText;

public class EditWhiteBoardActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_white_board);


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String text = ((EditText) findViewById(R.id.editText)).getText().toString();

                Bitmap bit = BitmapFactory.decodeResource(getResources(), R.drawable.whiteboard).copy(Bitmap.Config.ARGB_8888, true);

                Canvas canvas = new Canvas(bit);
                // Draw the text
                Paint textPaint = new Paint();
                textPaint.setTextSize(64);
                textPaint.setAntiAlias(true);
                textPaint.setARGB(0xff, 0x00, 0x00, 0x00);
                // draw the text centered
                canvas.drawText(text, 50, 224, textPaint);

                MainActivity.setWhiteBoardBitmap(bit);

                finish();
            }
        });
    }

}
