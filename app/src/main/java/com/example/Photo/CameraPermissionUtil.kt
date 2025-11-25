package com.example.Photo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.Photo.databinding.DialogCameraPermissionBinding

object CameraPermissionUtil {
    // 相机权限请求码
    const val CAMERA_PERMISSION_REQUEST_CODE = 1003

    /**
     * 检查相机权限是否已授予
     */
    fun hasCameraPermission(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(
                activity,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // 6.0以下默认授予权限
        }
    }

    /**
     * 显示自定义相机权限弹窗
     */
    fun showCameraPermissionDialog(activity: Activity, onAllow: () -> Unit) {
        val binding = DialogCameraPermissionBinding.inflate(activity.layoutInflater)
        val dialog = AlertDialog.Builder(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(binding.root)
            .create()

        // 点击“不允许”：关闭弹窗
        binding.btnDeny.setOnClickListener {
            dialog.dismiss()
        }

        // 点击“好”：申请权限
        binding.btnAllow.setOnClickListener {
            dialog.dismiss()
            onAllow()
        }

        dialog.show()
    }

    /**
     * 申请相机权限
     */
    fun requestCameraPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(android.Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    /**
     * 显示权限被拒绝后的引导弹窗（引导用户去设置页开启）
     */
    fun showPermissionDeniedDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("权限被拒绝")
            .setMessage("请在设置中开启相机权限，否则无法使用相机功能")
            .setPositiveButton("去设置") { _, _ ->
                // 跳转到应用设置页
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", activity.packageName, null)
                intent.data = uri
                activity.startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}