package com.example.Photo

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.Photo.databinding.ActivityCameraBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化相机线程池
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 启动相机预览
        startCamera()

        // 拍摄按钮点击事件
        binding.ivCapture.setOnClickListener {
            takePhoto()
        }

        // 切换摄像头按钮点击事件
        binding.ivSwitchCamera.setOnClickListener {
            switchCamera()
        }

        // 关闭按钮点击事件
        binding.ivClose.setOnClickListener {
            finish()
        }
    }

    /**
     * 启动相机预览
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 预览配置
            val preview = Preview.Builder()
                .build()
                .also {
                    // 关键修复：使用 PreviewView 的 createSurfaceProvider()
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            // 拍照配置
            imageCapture = ImageCapture.Builder()
                .build()

            // 默认使用后置摄像头
            var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // 解绑所有已绑定的相机
                cameraProvider.unbindAll()
                // 绑定相机生命周期
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "相机启动失败：${e.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * 拍摄照片
     */
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // 创建照片文件
        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            "${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())}.jpg"
        )

        // 照片输出配置
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // 拍摄照片
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@CameraActivity, "照片已保存：${photoFile.absolutePath}", Toast.LENGTH_SHORT).show()
                    // 可在这里跳转到照片预览页
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, "拍照失败：${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    /**
     * 切换摄像头（前置/后置）
     */
    private fun switchCamera() {
        // 简化实现：实际需重新配置CameraSelector并绑定相机
        Toast.makeText(this, "切换摄像头功能待实现", Toast.LENGTH_SHORT).show()
    }

    /**
     * 释放资源
     */
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}