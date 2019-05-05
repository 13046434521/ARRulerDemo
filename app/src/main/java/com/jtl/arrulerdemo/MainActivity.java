package com.jtl.arrulerdemo;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.jtl.arrulerdemo.helper.DisplayRotationHelper;
import com.jtl.arrulerdemo.helper.PermissionHelper;
import com.jtl.arrulerdemo.helper.SnackbarHelper;
import com.jtl.arrulerdemo.render.BackgroundRender;
import com.jtl.arrulerdemo.render.BackgroundRenderer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    //TAG
    private static final String TAG=MainActivity.class.getSimpleName();
    //ARCore变量
    private Session mSession;
    private boolean isInstallRequested;
    private SnackbarHelper mSnackbarHelper;
    private DisplayRotationHelper mDisplayRotationHelper;
    //Render
    private BackgroundRender mBackgroundRender;
    //UI
    private GLSurfaceView mShowGLSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initData();
    }
    //初始化相应数据
    private void initData(){
        mShowGLSurface=findViewById(R.id.gl_main_show);
        mSnackbarHelper=new SnackbarHelper();
        mDisplayRotationHelper=new DisplayRotationHelper(this);

        // Set up renderer.
        mShowGLSurface.setPreserveEGLContextOnPause(true);
        mShowGLSurface.setEGLContextClientVersion(2);//OpenGL版本为2.0
        mShowGLSurface.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        mShowGLSurface.setRenderer(this);//实现Render接口
        mShowGLSurface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);//RENDERMODE_CONTINUOUSLY渲染模式为实时渲染。
    }

    @Override
    protected void onResume() {
        super.onResume();
        // ARCore 申请相机权限操作
        if (!PermissionHelper.hasCameraPermission(this)) {
            PermissionHelper.requestCameraPermission(this);
            return;
        }
        Exception exception =null;
        String msg =null;
        //初始化Session
        if (mSession==null){
            try {
                //判断是否安装ARCore
                switch (ArCoreApk.getInstance().requestInstall(this,!isInstallRequested)){
                    case INSTALL_REQUESTED:
                        isInstallRequested=true;
                        break;
                    case INSTALLED:
                        Log.i(TAG,"已安装ARCore");
                        break;
                }
                mSession=new Session(this);
            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                msg = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                msg = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                msg = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                msg = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                msg = "Failed to create AR session";
                exception = e;
            }
            //有异常说明不支持或者没安装ARCore
            if (msg != null) {
                mSnackbarHelper.showError(this, msg);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        //该设备支持并且已安装ARCore
        try {
            //Session 恢复resume状态
            mSession.resume();
        } catch (CameraNotAvailableException e) {

            mSnackbarHelper.showError(this, "Camera not available. Please restart the app.");
            mSession = null;

            return;
        }


        mShowGLSurface.onResume();//GLSurfaceView onResume
        mDisplayRotationHelper.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSession!=null){
            //由于GLSurfaceView需要Session的数据。所以如果Session先pause会导致无法获取Session中的数据
            mShowGLSurface.onPause();//GLSurfaceView onPause
            mDisplayRotationHelper.onPause();
            mSession.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSession!=null){
            mSession.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (!PermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "该应用需要相机权限", Toast.LENGTH_LONG)
                    .show();
            if (!PermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // 直接跳至设置 修改权限
                PermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Render接口
    ///////////////////////////////////////////////////////////////////////////
    /**
     * GLSurfaceView创建时被回调，可以做一些初始化操作
     * @param gl
     * @param config
     */
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        //设置每一帧清屏颜色 传入参输为RGBA
        GLES20.glClearColor(0.1f,0.1f,0.1f,1.0f);

        mBackgroundRender=new BackgroundRender();
        mBackgroundRender.createOnGlThread(this);
    }

    /**
     * GLSurfaceView 大小改变时调用
     * @param gl
     * @param width GLSurfaceView宽
     * @param height GLSurfaceView高
     */
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        //方便 OpenGLES做 视口变换
        GLES20.glViewport(0,0,width,height);
        mDisplayRotationHelper.onSurfaceChanged(width,height);
    }

    /**
     * GLSurfaceView绘制每一帧调用，此处不在主线程中，而是在GL线程中。
     * 部分跨线程数据，需要做线程同步。不能直接更新UI(不在主线程)
     * @param gl
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        //清空彩色缓冲和深度缓冲  清空后的颜色为GLES20.glClearColor()时设置的颜色
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
        if (mSession==null){
            return;
        }
        //设置纹理ID
        mSession.setCameraTextureName(mBackgroundRender.getTextureId());
        //根据设备渲染Rotation，width，height。session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight);
        mDisplayRotationHelper.updateSessionIfNeeded(mSession);
        try {
            Frame frame=mSession.update();//获取Frame数据
            mBackgroundRender.draw(frame);//渲染frame数据
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
    }
}
