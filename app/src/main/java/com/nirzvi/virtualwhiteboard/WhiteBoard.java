package com.nirzvi.virtualwhiteboard;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Created by FIXIT on 2018-03-03.
 */

public class WhiteBoard {
    private FloatBuffer positionBuffer;
    private FloatBuffer texCoordBuffer;

    /** This will be used to pass in the texture. */
    private int mTextureUniformHandle;

    /** This will be used to pass in model position information. */
    private int mPositionHandle;

    /** This will be used to pass in model texture coordinate information. */
    private int mTextureCoordinateHandle;

    /** How many bytes per float. */
    private final int mBytesPerFloat = 4;

    /** This is a handle to our cube shading program. */
    private int mProgram;

    /** This is a handle to our texture data. */
    private int mTextureDataHandle;
    private int mMVPMatrixHandle;

    private final float[] texCoordData =
            {
                    0.0f, 1.0f,
                    0.0f, 0.0f,
                    1.0f, 1.0f,

                    0.0f, 0.0f,
                    1.0f, 0.0f,
                    1.0f, 1.0f,

            };

    protected String getVertexShader()
    {
        return "uniform mat4 uMVPMatrix;" +
                "attribute vec4 vPosition;" +
                "attribute vec2 a_TexCoord;" +
                "varying vec2 v_TexCoord;" +
                "void main() {" +
                // the matrix must be included as a modifier of gl_Position
                // Note that the uMVPMatrix factor *must be first* in order
                // for the matrix multiplication product to be correct.
                "   v_TexCoord = a_TexCoord;" +
                "  gl_Position = uMVPMatrix * vPosition;" +
                "}";
    }

    protected String getFragmentShader()
    {
        return "precision mediump float;" +
                "uniform sampler2D u_Texture;" +
                "varying vec2 v_TexCoord;" +
                "void main() {" +
                "  gl_FragColor = texture2D(u_Texture, v_TexCoord);" +
                "}";
    }

    public static int loadBoardTexture(Bitmap bitmap) {
        final int[] textureHandle = new int[1];

        GLES20.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] == 0)
        {
            throw new RuntimeException("Error generating texture name.");
        }//if

        // Bind to the texture in OpenGL
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

        // Set filtering
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        // Load the bitmap into the bound texture.
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        return textureHandle[0];
    }

    public void setPositionData(float[] positionData) {
        positionBuffer = ByteBuffer.allocateDirect(positionData.length * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
        positionBuffer.put(positionData).position(0);
    }

    //corner should be organized starting top-left, clockwise
    public void setCornerPos(float[][] cornerPos) {
        setPositionData(formatCornerPositions(cornerPos));
    }//setCornerPos

    //corner should be organized starting top-left, clockwise
    public float[] formatCornerPositions(float[][] cornerPos) {
        float[] coord = new float[18];
        float divide = 2000f;
        coord[0] = cornerPos[0][0] / divide;
        coord[1] = cornerPos[0][1] / divide;
        coord[2] = cornerPos[0][2] / divide;

        coord[3] = cornerPos[3][0] / divide;
        coord[4] = cornerPos[3][1] / divide;
        coord[5] = cornerPos[3][2] / divide;

        coord[6] = cornerPos[1][0] / divide;
        coord[7] = cornerPos[1][1] / divide;
        coord[8] = cornerPos[1][2] / divide;


        coord[9] = cornerPos[3][0] / divide;
        coord[10] = cornerPos[3][1] / divide;
        coord[11] = cornerPos[3][2] / divide;

        coord[12] = cornerPos[2][0] / divide;
        coord[13] = cornerPos[2][1] / divide;
        coord[14] = cornerPos[2][2] / divide;

        coord[15] = cornerPos[1][0] / divide;
        coord[16] = cornerPos[1][1] / divide;
        coord[17] = cornerPos[1][2] / divide;

        for (int i = 0; i < coord.length; i++) {
            coord[i] = (float) Math.round(coord[i] * 1000) / 1000f;
        }//for

        return coord;
    }//formatCornerPositions

    public void init(Bitmap bit) {
        texCoordBuffer = ByteBuffer.allocateDirect(texCoordData.length * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
        texCoordBuffer.put(texCoordData).position(0);

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER,
                getVertexShader());
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER,
                getFragmentShader());

        // create empty OpenGL ES Program
        mProgram = GLES20.glCreateProgram();

        // add the vertex shader to program
        GLES20.glAttachShader(mProgram, vertexShader);

        // add the fragment shader to program
        GLES20.glAttachShader(mProgram, fragmentShader);

        // creates OpenGL ES program executables
        GLES20.glLinkProgram(mProgram);

        GLES20.glBindAttribLocation(mProgram, 0, "vPosition");
        GLES20.glBindAttribLocation(mProgram, 1, "a_TexCoord");

        mTextureDataHandle = loadBoardTexture(bit);

    }

    public void draw(float[] mvpMatrix) {
        draw(mvpMatrix, 0);
        draw(mvpMatrix, 1);
    }

    public void draw(float[] mvpMatrix, int idx)
    {

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        // Add program to OpenGL ES environment
        GLES20.glUseProgram(mProgram);

        // get handle to vertex shader's vPosition member
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");

        // Prepare the triangle coordinate data
        positionBuffer.position(idx * 9);
        GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false, 12, positionBuffer);

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle);

        mTextureUniformHandle = GLES20.glGetUniformLocation(mProgram, "u_Texture");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgram, "a_TexCoord");

        // Set the active texture unit to texture unit 0.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // Bind the texture to this unit.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDataHandle);

        // Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
        GLES20.glUniform1i(mTextureUniformHandle, 0);

        // Pass in the texture coordinate information
        texCoordBuffer.position(idx * 6);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, 2, GLES20.GL_FLOAT, false,
                8, texCoordBuffer);

        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

        // get handle to shape's transformation matrix
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");

        // Pass the projection and view transformation to the shader
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);

        // Draw the triangle
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureCoordinateHandle);
    }

    public static int loadShader(int type, String shaderCode){

        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        int shader = GLES20.glCreateShader(type);

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        return shader;
    }
}
