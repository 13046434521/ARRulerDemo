package com.jtl.arrulerdemo.render;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 作者:jtl
 * 日期:Created in 2019/5/5 15:05
 * 描述:
 * 更改:
 */
public class BackgroundRender {
    private static final String TAG = BackgroundRender.class.getSimpleName();
    private static final String VERTEXT_SHADER = "shaders/screenquad.vert";
    private static final String FRAGMENT_SHADER = "shaders/screenquad.frag";
    private int a_Position;
    private int a_TexCoord;

    private FloatBuffer quadCoords;//顶点Vertex位置（归一化之后的坐标上的四个顶点位置）
    private FloatBuffer quadTexCoords;//Texture纹理坐标

    private static final float[] QUAD_COORDS =
            new float[] {
                    -1.0f, -1.0f, -1.0f, +1.0f, +1.0f, -1.0f, +1.0f, +1.0f,
            };

    private int program=0;
    private int textureId=0;

    public void createOnGlThread(Context context) {
        loadShader(context);
        initBuffer();
        createTexture();
    }

    private void loadShader(Context context) {
        //加载并编译shader到管线中,并获取句柄
        int vertexShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEXT_SHADER);
        int fragmentShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        ShaderUtil.checkGLError(TAG, "loadShader Error");
        //创建program 绑定相应参数
        int program = GLES20.glCreateProgram();
        this.program=program;
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);//先链接后使用
        GLES20.glUseProgram(program);
        ShaderUtil.checkGLError(TAG, "loadShader Error");
        //获取参输句柄，类似于指针
        a_Position = GLES20.glGetAttribLocation(program, "a_Position");
        a_TexCoord = GLES20.glGetAttribLocation(program, "a_TexCoord");
        ShaderUtil.checkGLError(TAG, "loadShader Error");
        //删除无用shader。
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);

        //检查是否出错
        ShaderUtil.checkGLError(TAG, "loadShader Error");
    }

    //注意：ByteBuffer 是在本地环境中申请的内存。不受JVM控制。
    private void initBuffer(){
        ByteBuffer quadBuffer=ByteBuffer.allocateDirect(QUAD_COORDS.length * 4);//4个顶点，每个顶点有2个分量，每个float占4个字节
        quadBuffer.order(ByteOrder.nativeOrder());
        quadCoords= quadBuffer.asFloatBuffer();
        quadCoords.put(QUAD_COORDS);
        quadCoords.position(0);

        ByteBuffer textureBuffer=ByteBuffer.allocateDirect(4 * 2 * 4);//4个顶点，每个顶点有2个分量，每个float占4个字节
        textureBuffer.order(ByteOrder.nativeOrder());
        quadTexCoords=textureBuffer.asFloatBuffer();
    }

    private void createTexture() {
        //创建生成纹理
        int texture[] = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]); //纹理目标
        textureId=texture[0];
        //GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        //设置纹理过滤相关参数
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);//设置环绕方向S，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);//设置环绕方向T，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);//设置缩小时为双线性过滤
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);//设置放大时为双线性过滤

        //检查是否出错
        ShaderUtil.checkGLError(TAG, "createTexture Error");
    }

    public void draw(Frame frame) {
        //根据屏幕是否旋转，自动计算出uv映射坐标。
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    quadCoords,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    quadTexCoords);
        }

        if (frame.getTimestamp() == 0) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            return;
        }

        draw();
    }

    private void draw(){
        quadTexCoords.position(0);
        GLES20.glDepthMask(false);//关闭深度测试
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        GLES20.glUseProgram(program);//使用本render的program
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);//绑定纹理

        GLES20.glVertexAttribPointer(a_Position,2,GLES20.GL_FLOAT,false,0,quadCoords);
        GLES20.glVertexAttribPointer(a_TexCoord,2,GLES20.GL_FLOAT,false,0,quadTexCoords);
        GLES20.glEnableVertexAttribArray(a_Position);
        GLES20.glEnableVertexAttribArray(a_TexCoord);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);//绘制

        GLES20.glDisableVertexAttribArray(a_Position);
        GLES20.glDisableVertexAttribArray(a_TexCoord);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(true);

        //检查是否出错
        ShaderUtil.checkGLError(TAG, "onDraw Error");
    }

    public int getTextureId() {
        return textureId;
    }
}
