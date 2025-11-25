//real phone
package com.example.Photo

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.lifecycle.lifecycleScope
import com.example.Photo.databinding.ActivityPhotoPreviewBinding
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class PhotoPreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoPreviewBinding
    private var imageUri: String? = null
    private val TAG = "PhotoPreviewDebug"

    // 启动器
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var writePermissionLauncher: ActivityResultLauncher<Array<String>> // 新增：写入权限启动器
    private lateinit var pickPhotoLauncher: ActivityResultLauncher<Intent>
    private lateinit var cropResultLauncher: ActivityResultLauncher<Intent>

    // 替换原有单击关闭的逻辑
    private var lastClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 全屏设置
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.hide()

        // 获取传递的图片Uri
        imageUri = intent.getStringExtra("imageUri")
        Log.d(TAG, "接收的图片Uri：$imageUri")

        // 初始化所有启动器
        initLaunchers()

        // 加载图片
        loadImage()

        // 直接绑定XML中的按钮点击事件（无需动态添加）
        binding.btnEdit.setOnClickListener {
            Log.d(TAG, "编辑按钮被点击")
            showEditOptions()
        }

        binding.btnImport.setOnClickListener {
            Log.d(TAG, "【导入图片按钮】点击事件已触发！")
            checkPhotoPermission()
        }

        // 双击图片关闭预览（避免拦截按钮）
        var lastClickTime = 0L
        binding.ivPreview.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 300) {
                finish()
            }
            lastClickTime = currentTime
        }
    }

    /**
     * 动态添加编辑按钮（备用，若XML按钮失效）
     */
    private fun addEditButton() {
        val editBtn = Button(this).apply {
            text = "编辑"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.RIGHT
                setMargins(0, 40, 20, 0)
            }
            elevation = 20f
            isClickable = true
            isFocusable = true
            setOnClickListener {
                Log.d(TAG, "编辑按钮被点击")
                showEditOptions()
            }
        }
        val rootLayout = binding.root as FrameLayout
        rootLayout.addView(editBtn)
        editBtn.bringToFront()
    }

    /**
     * 动态添加导入图片按钮（备用）
     */
    private fun addImportButton() {
        val importBtn = Button(this).apply {
            text = "导入图片"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.RIGHT
                setMargins(0, 100, 20, 0)
            }
            elevation = 20f
            isClickable = true
            isFocusable = true
            setOnClickListener {
                Log.d(TAG, "【导入图片按钮】点击事件已触发！")
                checkPhotoPermission()
            }
        }
        val rootLayout = binding.root as FrameLayout
        rootLayout.addView(importBtn)
        importBtn.bringToFront()
        Log.d(TAG, "【导入图片按钮】已添加并提升层级")
    }

    /**
     * 初始化所有启动器（新增写入权限启动器）
     */
    private fun initLaunchers() {
        // 1. 裁剪结果启动器
        cropResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val croppedUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                Log.d(TAG, "裁剪成功，Uri：$croppedUri")
                binding.ivPreview.setImageURI(croppedUri)
                imageUri = croppedUri.toString()
                // 保存裁剪后的图片（先检查写入权限）
                checkWritePermission { saveToAlbum(croppedUri) }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val error = UCrop.getError(result.data!!)
                Log.e(TAG, "裁剪失败：${error?.message}")
                Toast.makeText(this, "裁剪失败：${error?.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. 照片读取权限启动器
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Log.d(TAG, "【读取权限请求结果】是否授予：$isGranted")
            if (isGranted) {
                Log.d(TAG, "【读取权限请求结果】授权成功，执行pickPhoto()")
                pickPhoto()
            } else {
                Toast.makeText(this, "请授予照片权限才能导入", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. 写入权限启动器（新增）
        // 写入权限启动器的回调
        writePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                true
            } else {
                permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
            }
            if (isGranted) {
                // 权限通过后，在协程中执行保存动作
                lifecycleScope.launch {
                    saveAction?.invoke() // 此时invoke在协程内，可调用挂起函数
                }
            } else {
                Toast.makeText(this, "需要存储权限才能保存", Toast.LENGTH_SHORT).show()
            }
            saveAction = null
        }

        // 4. 选择图片启动器
        pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val newUri = result.data?.data
                newUri?.let {
                    Log.d(TAG, "选择图片成功，Uri：$it")
                    imageUri = it.toString()
                    loadImage()
                    Toast.makeText(this, "导入图片成功", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 新增：保存动作的回调
    // 将saveAction声明为可空的挂起函数类型
    private var saveAction: (suspend () -> Unit)? = null

    /**
     * 检查写入权限并执行保存动作
     */
    // 修改权限检查方法，接收挂起函数类型的回调
    private fun checkWritePermission(action: suspend () -> Unit) {
        saveAction = action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 直接在协程中执行保存逻辑
            lifecycleScope.launch {
                action() // 这里是协程作用域，可调用挂起函数
            }
        } else {
            val isGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (isGranted) {
                // 已授权，在协程中执行
                lifecycleScope.launch {
                    action()
                }
            } else {
                // 申请权限（权限回调后仍需在协程中执行）
                writePermissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }
    }

    /**
     * 加载图片（区分drawable和外部Uri）
     */
    private fun loadImage() {
        imageUri?.let { uriStr ->
            if (uriStr.startsWith("drawable://")) {
                val resId = uriStr.split("://")[1].toInt()
                binding.ivPreview.setImageResource(resId)
            } else {
                binding.ivPreview.setImageURI(Uri.parse(uriStr))
            }
        }
    }

    /**
     * 检查照片读取权限
     */
    private fun checkPhotoPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val isGranted = ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "【权限检查】权限类型：$permission")
        Log.d(TAG, "【权限检查】是否已授予：$isGranted")

        if (isGranted) {
            Log.d(TAG, "【权限检查】已授权，执行pickPhoto()")
            pickPhoto()
        } else {
            Log.d(TAG, "【权限检查】未授权，启动权限请求")
            permissionLauncher.launch(permission)
        }
    }

    /**
     * 启动系统相册选择图片
     */
    private fun pickPhoto() {
        Log.d(TAG, "【选择图片】开始构建相册Intent")

        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        val chooser = Intent.createChooser(intent, "选择相册应用")

        Log.d(TAG, "【选择图片】Intent配置完成，启动相册选择器")
        if (chooser.resolveActivity(packageManager) != null) {
            pickPhotoLauncher.launch(chooser)
        } else {
            Log.e(TAG, "【选择图片】无匹配的相册应用！")
            Toast.makeText(this, "未找到可打开图片的应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditOptions() {
        val options = arrayOf("裁剪", "删除", "旋转", "缩放", "添加水印", "基础滤镜")
        AlertDialog.Builder(this)
            .setTitle("编辑图片")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startCrop()
                    1 -> confirmDelete()
                    2 -> rotateImage()
                    3 -> showScaleDialog()
                    4 -> showWatermarkDialog()
                    5 -> showFilterDialog()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startCrop() {
        imageUri?.let { uriStr ->
            val sourceUri = if (uriStr.startsWith("drawable://")) {
                resToUri(uriStr.split("://")[1].toInt())
            } else {
                Uri.parse(uriStr)
            }

            val destDir = File(getExternalFilesDir(null), "CropImages")
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, "crop_${System.currentTimeMillis()}.jpg")
            val destUri = Uri.fromFile(destFile)

            val cropIntent = UCrop.of(sourceUri, destUri)
                .withAspectRatio(1f, 1f)
                .withOptions(getUCropOptions())
                .getIntent(this)
            cropResultLauncher.launch(cropIntent)
        }
    }

    private fun rotateImage() {
        imageUri?.let { uriStr ->
            lifecycleScope.launch(Dispatchers.IO) {
                val bitmap = loadImageBitmap(uriStr) ?: return@launch
                val matrix = Matrix().apply { postRotate(90f) }
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                // 保存旋转后的图片（检查写入权限）
                checkWritePermission {
                    val newUri = saveBitmapToMediaStore(rotatedBitmap)
                    withContext(Dispatchers.Main) {
                        binding.ivPreview.setImageURI(newUri)
                        imageUri = newUri.toString()
                        sendRefreshBroadcast()
                        val tip = if (isInternalDrawable(uriStr)) "内置图片已另存为新图片" else "旋转完成"
                        Toast.makeText(this@PhotoPreviewActivity, tip, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showScaleDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_scale, null)
        val btnZoomIn = dialogView.findViewById<Button>(R.id.btn_zoom_in)
        val btnZoomOut = dialogView.findViewById<Button>(R.id.btn_zoom_out)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_scale)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("调整缩放")
            .show()

        btnZoomIn.setOnClickListener {
            binding.ivPreview.scaleX *= 1.2f
            binding.ivPreview.scaleY *= 1.2f
        }

        btnZoomOut.setOnClickListener {
            binding.ivPreview.scaleX *= 0.8f
            binding.ivPreview.scaleY *= 0.8f
        }

        btnSave.setOnClickListener {
            if (isInternalDrawable(imageUri ?: "")) {
                Toast.makeText(this@PhotoPreviewActivity, "内置图片仅支持预览编辑，无法保存", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                binding.ivPreview.isDrawingCacheEnabled = true
                val scaledBitmap = Bitmap.createBitmap(binding.ivPreview.drawingCache)
                binding.ivPreview.isDrawingCacheEnabled = false
                // 保存缩放后的图片（检查写入权限）
                checkWritePermission {
                    val newUri = saveBitmapToMediaStore(scaledBitmap)
                    withContext(Dispatchers.Main) {
                        binding.ivPreview.setImageURI(newUri)
                        imageUri = newUri.toString()
                        sendRefreshBroadcast()
                        Toast.makeText(this@PhotoPreviewActivity, "缩放完成", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showWatermarkDialog() {
        val editText = EditText(this).apply {
            hint = "输入水印文字"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        AlertDialog.Builder(this)
            .setTitle("添加水印")
            .setView(editText)
            .setPositiveButton("添加") { _, _ ->
                val text = editText.text.toString()
                if (text.isNotEmpty()) addWatermark(text)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addWatermark(text: String) {
        imageUri?.let { uriStr ->
            lifecycleScope.launch(Dispatchers.IO) {
                val bitmap = loadImageBitmap(uriStr) ?: return@launch
                val resultBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(resultBitmap)
                val paint = Paint().apply {
                    color = Color.WHITE
                    textSize = 40f
                    isAntiAlias = true
                    strokeWidth = 2f
                    style = Paint.Style.FILL_AND_STROKE
                }
                canvas.drawText(text, 50f, 50f, paint)
                // 保存水印图片（检查写入权限）
                checkWritePermission {
                    val newUri = saveBitmapToMediaStore(resultBitmap)
                    withContext(Dispatchers.Main) {
                        binding.ivPreview.setImageURI(newUri)
                        imageUri = newUri.toString()
                        sendRefreshBroadcast()
                        val tip = if (isInternalDrawable(uriStr)) "内置图片已添加水印并另存" else "水印添加完成"
                        Toast.makeText(this@PhotoPreviewActivity, tip, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showFilterDialog() {
        val options = arrayOf("亮度提升", "对比度增强")
        AlertDialog.Builder(this)
            .setTitle("选择滤镜")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> applyBrightnessFilter()
                    1 -> applyContrastFilter()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applyBrightnessFilter() {
        applyColorFilter(ColorMatrix().apply { setScale(1.5f, 1.5f, 1.5f, 1f) })
    }

    private fun applyContrastFilter() {
        val contrast = 1.5f
        val matrix = ColorMatrix().apply {
            set(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, 0f,
                    0f, contrast, 0f, 0f, 0f,
                    0f, 0f, contrast, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        applyColorFilter(matrix)
    }

    private fun applyColorFilter(colorMatrix: ColorMatrix) {
        imageUri?.let { uriStr ->
            if (isInternalDrawable(uriStr)) {
                Toast.makeText(this, "内置图片仅支持预览编辑，无法保存", Toast.LENGTH_SHORT).show()
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val bitmap = loadImageBitmap(uriStr) ?: return@launch
                val config = bitmap.config ?: Bitmap.Config.ARGB_8888
                val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, config)
                val canvas = Canvas(resultBitmap)
                val paint = Paint().apply {
                    colorFilter = ColorMatrixColorFilter(colorMatrix)
                }
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                // 保存滤镜图片（检查写入权限）
                checkWritePermission {
                    val newUri = saveBitmapToMediaStore(resultBitmap)
                    withContext(Dispatchers.Main) {
                        binding.ivPreview.setImageURI(newUri)
                        imageUri = newUri.toString()
                        sendRefreshBroadcast()
                        val tip = if (isInternalDrawable(uriStr)) "内置图片已添加滤镜应用并另存" else "滤镜应用完成"
                        Toast.makeText(this@PhotoPreviewActivity, tip, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("是否要删除这张图片？")
            .setPositiveButton("删除") { _, _ ->
                deleteImage()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteImage() {
        imageUri?.let { uriStr ->
            if (uriStr.startsWith("drawable://")) {
                Toast.makeText(this, "内置图片不可删除", Toast.LENGTH_SHORT).show()
            } else {
                val uri = Uri.parse(uriStr)
                lifecycleScope.launch(Dispatchers.IO) {
                    contentResolver.delete(uri, null, null)
                    withContext(Dispatchers.Main) {
                        setResult(RESULT_OK, Intent().putExtra("need_refresh", true))
                        Toast.makeText(this@PhotoPreviewActivity, "删除成功", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("MEDIA_CHANGED"))
    }

    // ===================== 工具方法（优化保存逻辑） =====================
    private fun isInternalDrawable(uriStr: String): Boolean {
        return uriStr.startsWith("drawable://")
    }

    private suspend fun loadImageBitmap(uriStr: String): Bitmap? {
        return if (isInternalDrawable(uriStr)) {
            loadDrawableBitmap(uriStr)
        } else {
            withContext(Dispatchers.IO) {
                MediaStore.Images.Media.getBitmap(contentResolver, Uri.parse(uriStr))
            }
        }
    }

    private fun loadDrawableBitmap(uriStr: String): Bitmap? {
        return try {
            val resId = uriStr.replace("drawable://", "").toInt()
            BitmapFactory.decodeResource(resources, resId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 优化保存Bitmap到媒体库（解决权限问题+空指针异常）
     */
    private fun saveBitmapToMediaStore(bitmap: Bitmap): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "edit_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/EditImages") // 保存到相册编辑文件夹
                put(MediaStore.Images.Media.IS_PENDING, 1) // 标记为待处理
            } else {
                // Android 9及以下：设置存储路径
                put(MediaStore.Images.Media.DATA, "${Environment.getExternalStorageDirectory()}/DCIM/EditImages/edit_${System.currentTimeMillis()}.jpg")
            }
        }

        var uri: Uri? = null
        try {
            // 插入到媒体库
            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                Log.e(TAG, "保存失败：MediaStore插入返回null")
                Toast.makeText(this, "保存失败：媒体库插入失败", Toast.LENGTH_SHORT).show()
                // 降级保存到应用私有目录
                return saveToPrivateDir(bitmap)
            }

            // 写入Bitmap数据
            val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            } ?: run {
                Log.e(TAG, "保存失败：无法打开输出流")
                Toast.makeText(this, "保存失败：输出流为空", Toast.LENGTH_SHORT).show()
            }

            // Android 10+：取消待处理标记
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }

            // 通知相册刷新
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "保存失败：${e.message}")
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
            // 降级保存到应用私有目录
            uri = saveToPrivateDir(bitmap)
        }
        return uri ?: saveToPrivateDir(bitmap) // 最终兜底
    }

    /**
     * 降级保存：保存到应用私有目录（避免媒体库保存失败）
     */
    private fun saveToPrivateDir(bitmap: Bitmap): Uri {
        val privateDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "EditImages")
        if (!privateDir.exists()) privateDir.mkdirs()
        val file = File(privateDir, "edit_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            Toast.makeText(this, "图片已保存到应用私有目录", Toast.LENGTH_SHORT).show()
            return Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "保存到私有目录失败", Toast.LENGTH_SHORT).show()
            return Uri.EMPTY
        }
    }

    /**
     * 优化保存裁剪后的图片到相册
     */
    private fun saveToAlbum(croppedUri: Uri) {
        val filePath = croppedUri.path ?: return
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        checkWritePermission {
            try {
                file.inputStream().use { inputStream ->
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.SIZE, file.length())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/EditImages")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }

                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let { outputUri ->
                        contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            contentResolver.update(outputUri, contentValues, null, null)
                        }

                        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("MEDIA_CHANGED"))
                        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, outputUri))
                        Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show()
                    } ?: run {
                        Toast.makeText(this, "保存失败：媒体库插入null", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendRefreshBroadcast() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("MEDIA_CHANGED"))
    }

    private fun resToUri(resId: Int): Uri {
        return Uri.parse("android.resource://${packageName}/$resId")
    }

    private fun getUCropOptions(): UCrop.Options {
        val options = UCrop.Options()
        options.setCompressionQuality(90)
        options.setShowCropGrid(true)
        return options
    }
}
//package com.example.Photo
//
//import android.Manifest
//import android.content.ContentValues
//import android.content.Intent
//import android.graphics.*
//import android.net.Uri
//import android.os.Build
//import android.os.Bundle
//import android.os.Environment
//import android.provider.MediaStore
//import android.util.Log
//import android.view.Gravity
//import android.view.View
//import android.view.ViewGroup
//import android.view.WindowManager
//import android.widget.*
//import androidx.activity.result.ActivityResultLauncher
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.appcompat.app.AlertDialog
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.content.ContextCompat
//import androidx.localbroadcastmanager.content.LocalBroadcastManager
//import androidx.lifecycle.lifecycleScope
//import com.example.Photo.databinding.ActivityPhotoPreviewBinding
//import com.yalantis.ucrop.UCrop
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import java.io.File
//import java.io.FileOutputStream
//
//class PhotoPreviewActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityPhotoPreviewBinding
//    private var imageUri: String? = null
//    private val TAG = "PhotoPreviewDebug"
//
//    // 启动器
//    private lateinit var permissionLauncher: ActivityResultLauncher<String>
//    private lateinit var pickPhotoLauncher: ActivityResultLauncher<Intent>
//    private lateinit var cropResultLauncher: ActivityResultLauncher<Intent>
//
//    // 替换原有单击关闭的逻辑
//    private var lastClickTime = 0L
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityPhotoPreviewBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // 全屏设置
//        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
//        supportActionBar?.hide()
//
//        // 获取传递的图片Uri
//        imageUri = intent.getStringExtra("imageUri")
//        Log.d(TAG, "接收的图片Uri：$imageUri")
//
//        // 初始化所有启动器
//        initLaunchers()
//
//        // 加载图片
//        loadImage()
//
//        // 直接绑定XML中的按钮点击事件（无需动态添加）
//        binding.btnEdit.setOnClickListener {
//            Log.d(TAG, "编辑按钮被点击")
//            showEditOptions()
//        }
//
//        binding.btnImport.setOnClickListener {
//            Log.d(TAG, "【导入图片按钮】点击事件已触发！")
//            checkPhotoPermission()
//        }
//
//        // 双击图片关闭预览（避免拦截按钮）
//        var lastClickTime = 0L
//        binding.ivPreview.setOnClickListener {
//            val currentTime = System.currentTimeMillis()
//            if (currentTime - lastClickTime < 300) {
//                finish()
//            }
//            lastClickTime = currentTime
//        }
//    }
//
//    /**
//     * 动态添加编辑按钮
//     */
//    private fun addEditButton() {
//        val editBtn = Button(this).apply {
//            text = "编辑"
//            setBackgroundColor(Color.TRANSPARENT)
//            setTextColor(Color.WHITE)
//            layoutParams = FrameLayout.LayoutParams(
//                ViewGroup.LayoutParams.WRAP_CONTENT,
//                ViewGroup.LayoutParams.WRAP_CONTENT
//            ).apply {
//                gravity = Gravity.TOP or Gravity.RIGHT
//                setMargins(0, 40, 20, 0)
//            }
//            // 核心修复：提升按钮层级和点击优先级
//            elevation = 20f // 高于ImageView的层级（默认0f）
//            isClickable = true
//            isFocusable = true
//            setOnClickListener {
//                Log.d(TAG, "编辑按钮被点击")
//                showEditOptions()
//            }
//        }
//        val rootLayout = binding.root as FrameLayout
//        rootLayout.addView(editBtn)
//        editBtn.bringToFront() // 移到布局最上层
//    }
//
//    /**
//     * 动态添加导入图片按钮
//     */
//    private fun addImportButton() {
//        val importBtn = Button(this).apply {
//            text = "导入图片"
//            setBackgroundColor(Color.TRANSPARENT)
//            setTextColor(Color.WHITE)
//            layoutParams = FrameLayout.LayoutParams(
//                ViewGroup.LayoutParams.WRAP_CONTENT,
//                ViewGroup.LayoutParams.WRAP_CONTENT
//            ).apply {
//                gravity = Gravity.TOP or Gravity.RIGHT
//                setMargins(0, 100, 20, 0)
//            }
//            // 核心修复：提升按钮层级和点击优先级
//            elevation = 20f
//            isClickable = true
//            isFocusable = true
//            setOnClickListener {
//                Log.d(TAG, "【导入图片按钮】点击事件已触发！")
//                checkPhotoPermission()
//            }
//        }
//        val rootLayout = binding.root as FrameLayout
//        rootLayout.addView(importBtn)
//        importBtn.bringToFront() // 移到布局最上层
//        Log.d(TAG, "【导入图片按钮】已添加并提升层级")
//    }
//    /**
//     * 初始化所有启动器（裁剪/导入/权限）
//     */
//    private fun initLaunchers() {
//        // 1. 裁剪结果启动器
//        cropResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == RESULT_OK) {
//                val croppedUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
//                Log.d(TAG, "裁剪成功，Uri：$croppedUri")
//                binding.ivPreview.setImageURI(croppedUri)
//                imageUri = croppedUri.toString()
//                saveToAlbum(croppedUri) // 自动保存到相册
//            } else if (result.resultCode == UCrop.RESULT_ERROR) {
//                val error = UCrop.getError(result.data!!)
//                Log.e(TAG, "裁剪失败：${error?.message}")
//                Toast.makeText(this, "裁剪失败：${error?.message}", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        // 2. 照片权限启动器
//        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
//            Log.d(TAG, "【权限请求结果】是否授予：$isGranted")
//            if (isGranted) {
//                Log.d(TAG, "【权限请求结果】授权成功，执行pickPhoto()")
//                pickPhoto()
//            } else {
//                Toast.makeText(this, "请授予照片权限才能导入", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        // 3. 选择图片启动器
//        pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == RESULT_OK) {
//                val newUri = result.data?.data
//                newUri?.let {
//                    Log.d(TAG, "选择图片成功，Uri：$it")
//                    imageUri = it.toString()
//                    loadImage()
//                    Toast.makeText(this, "导入图片成功", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//
//
//
//    /**
//     * 加载图片（区分drawable和外部Uri）
//     */
//    private fun loadImage() {
//        imageUri?.let { uriStr ->
//            if (uriStr.startsWith("drawable://")) {
//                val resId = uriStr.split("://")[1].toInt()
//                binding.ivPreview.setImageResource(resId)
//            } else {
//                binding.ivPreview.setImageURI(Uri.parse(uriStr))
//            }
//        }
//    }
//
//    /**
//     * 检查照片权限
//     */
//    private fun checkPhotoPermission() {
//        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            Manifest.permission.READ_MEDIA_IMAGES
//        } else {
//            Manifest.permission.READ_EXTERNAL_STORAGE
//        }
//        val isGranted = ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
//
//        // 新增：详细日志
//        Log.d(TAG, "【权限检查】权限类型：$permission")
//        Log.d(TAG, "【权限检查】是否已授予：$isGranted")
//
//        if (isGranted) {
//            Log.d(TAG, "【权限检查】已授权，执行pickPhoto()")
//            pickPhoto()
//        } else {
//            Log.d(TAG, "【权限检查】未授权，启动权限请求")
//            permissionLauncher.launch(permission)
//        }
//    }
//
//    /**
//     * 启动系统相册选择图片
//     */
//    /**
//     * 启动系统相册选择图片
//     */
//    private fun pickPhoto() {
//        Log.d(TAG, "【选择图片】开始构建相册Intent")
//
//        // 方案：使用ACTION_GET_CONTENT + 应用选择器，兼容所有手机（删除ACTION_PICK的重复配置）
//        val intent = Intent(Intent.ACTION_GET_CONTENT)
//        intent.type = "image/*" // 仅选择图片
//        intent.addCategory(Intent.CATEGORY_OPENABLE) // 确保可打开的文件
//
//        // 强制弹出应用选择器（解决部分手机无弹窗）
//        val chooser = Intent.createChooser(intent, "选择相册应用")
//
//        Log.d(TAG, "【选择图片】Intent配置完成，启动相册选择器")
//        // 判断是否有匹配的应用
//        if (chooser.resolveActivity(packageManager) != null) {
//            pickPhotoLauncher.launch(chooser)
//        } else {
//            Log.e(TAG, "【选择图片】无匹配的相册应用！")
//            Toast.makeText(this, "未找到可打开图片的应用", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//
//    private fun showEditOptions() {
//        val options = arrayOf("裁剪", "删除", "旋转", "缩放", "添加水印", "基础滤镜")
//        AlertDialog.Builder(this)
//            .setTitle("编辑图片")
//            .setItems(options) { _, which ->
//                when (which) {
//                    0 -> startCrop()
//                    1 -> confirmDelete()
//                    2 -> rotateImage()
//                    3 -> showScaleDialog()
//                    4 -> showWatermarkDialog()
//                    5 -> showFilterDialog()
//                }
//            }
//            .setNegativeButton("取消", null)
//            .show()
//    }
//
//    private fun startCrop() {
//        imageUri?.let { uriStr ->
//            val sourceUri = if (uriStr.startsWith("drawable://")) {
//                resToUri(uriStr.split("://")[1].toInt())
//            } else {
//                Uri.parse(uriStr)
//            }
//
//            val destDir = File(getExternalFilesDir(null), "CropImages")
//            if (!destDir.exists()) destDir.mkdirs()
//            val destFile = File(destDir, "crop_${System.currentTimeMillis()}.jpg")
//            val destUri = Uri.fromFile(destFile)
//
//            val cropIntent = UCrop.of(sourceUri, destUri)
//                .withAspectRatio(1f, 1f)
//                .withOptions(getUCropOptions())
//                .getIntent(this)
//            cropResultLauncher.launch(cropIntent) // 用新启动器启动
//        }
//    }
//
//    private fun rotateImage() {
//        imageUri?.let { uriStr ->
//            lifecycleScope.launch(Dispatchers.IO) {
//                val bitmap = loadImageBitmap(uriStr) ?: return@launch
//                val matrix = Matrix().apply { postRotate(90f) }
//                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
//                val newUri = saveBitmapToMediaStore(rotatedBitmap)
//                withContext(Dispatchers.Main) {
//                    binding.ivPreview.setImageURI(newUri)
//                    imageUri = newUri.toString()
//                    sendRefreshBroadcast()
//                    val tip = if (isInternalDrawable(uriStr)) "内置图片已另存为新图片" else "旋转完成"
//                    Toast.makeText(this@PhotoPreviewActivity, tip, Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//
//    private fun showScaleDialog() {
//        val dialogView = layoutInflater.inflate(R.layout.dialog_scale, null)
//        val btnZoomIn = dialogView.findViewById<Button>(R.id.btn_zoom_in)
//        val btnZoomOut = dialogView.findViewById<Button>(R.id.btn_zoom_out)
//        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_scale)
//
//        AlertDialog.Builder(this)
//            .setView(dialogView)
//            .setTitle("调整缩放")
//            .show()
//
//        btnZoomIn.setOnClickListener {
//            binding.ivPreview.scaleX *= 1.2f
//            binding.ivPreview.scaleY *= 1.2f
//        }
//
//        btnZoomOut.setOnClickListener {
//            binding.ivPreview.scaleX *= 0.8f
//            binding.ivPreview.scaleY *= 0.8f
//        }
//
//        btnSave.setOnClickListener {
//            if (isInternalDrawable(imageUri ?: "")) {
//                Toast.makeText(this@PhotoPreviewActivity, "内置图片仅支持预览编辑，无法保存", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//            lifecycleScope.launch(Dispatchers.IO) {
//                binding.ivPreview.isDrawingCacheEnabled = true
//                val scaledBitmap = Bitmap.createBitmap(binding.ivPreview.drawingCache)
//                binding.ivPreview.isDrawingCacheEnabled = false
//                val newUri = saveBitmapToMediaStore(scaledBitmap)
//                withContext(Dispatchers.Main) {
//                    binding.ivPreview.setImageURI(newUri)
//                    imageUri = newUri.toString()
//                    sendRefreshBroadcast()
//                    Toast.makeText(this@PhotoPreviewActivity, "缩放完成", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//
//    private fun showWatermarkDialog() {
//        val editText = EditText(this).apply {
//            hint = "输入水印文字"
//            layoutParams = LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.MATCH_PARENT,
//                LinearLayout.LayoutParams.WRAP_CONTENT
//            )
//        }
//
//        AlertDialog.Builder(this)
//            .setTitle("添加水印")
//            .setView(editText)
//            .setPositiveButton("添加") { _, _ ->
//                val text = editText.text.toString()
//                if (text.isNotEmpty()) addWatermark(text)
//            }
//            .setNegativeButton("取消", null)
//            .show()
//    }
//
//    private fun addWatermark(text: String) {
//        imageUri?.let { uriStr ->
//            lifecycleScope.launch(Dispatchers.IO) {
//                val bitmap = loadImageBitmap(uriStr) ?: return@launch
//                val resultBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
//                val canvas = Canvas(resultBitmap)
//                val paint = Paint().apply {
//                    color = Color.WHITE
//                    textSize = 40f
//                    isAntiAlias = true
//                    strokeWidth = 2f
//                    style = Paint.Style.FILL_AND_STROKE
//                }
//                canvas.drawText(text, 50f, 50f, paint)
//                val newUri = saveBitmapToMediaStore(resultBitmap)
//                withContext(Dispatchers.Main) {
//                    binding.ivPreview.setImageURI(newUri)
//                    imageUri = newUri.toString()
//                    sendRefreshBroadcast()
//                    val tip = if (isInternalDrawable(uriStr)) "内置图片已添加水印并另存" else "水印添加完成"
//                    Toast.makeText(this@PhotoPreviewActivity, tip, Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//
//    private fun showFilterDialog() {
//        val options = arrayOf("亮度提升", "对比度增强")
//        AlertDialog.Builder(this)
//            .setTitle("选择滤镜")
//            .setItems(options) { _, which ->
//                when (which) {
//                    0 -> applyBrightnessFilter()
//                    1 -> applyContrastFilter()
//                }
//            }
//            .setNegativeButton("取消", null)
//            .show()
//    }
//
//    private fun applyBrightnessFilter() {
//        applyColorFilter(ColorMatrix().apply { setScale(1.5f, 1.5f, 1.5f, 1f) })
//    }
//
//    private fun applyContrastFilter() {
//        val contrast = 1.5f
//        val matrix = ColorMatrix().apply {
//            set(
//                floatArrayOf(
//                    contrast, 0f, 0f, 0f, 0f,
//                    0f, contrast, 0f, 0f, 0f,
//                    0f, 0f, contrast, 0f, 0f,
//                    0f, 0f, 0f, 1f, 0f
//                )
//            )
//        }
//        applyColorFilter(matrix)
//    }
//
//    private fun applyColorFilter(colorMatrix: ColorMatrix) {
//        imageUri?.let { uriStr ->
//            if (isInternalDrawable(uriStr)) {
//                Toast.makeText(this, "内置图片仅支持预览编辑，无法保存", Toast.LENGTH_SHORT).show()
//            }
//            lifecycleScope.launch(Dispatchers.IO) {
//                val bitmap = loadImageBitmap(uriStr) ?: return@launch
//                val config = bitmap.config ?: Bitmap.Config.ARGB_8888
//                val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, config)
//                val canvas = Canvas(resultBitmap)
//                val paint = Paint().apply {
//                    colorFilter = ColorMatrixColorFilter(colorMatrix)
//                }
//                canvas.drawBitmap(bitmap, 0f, 0f, paint)
//                val newUri = saveBitmapToMediaStore(resultBitmap)
//                withContext(Dispatchers.Main) {
//                    binding.ivPreview.setImageURI(newUri)
//                    imageUri = newUri.toString()
//                    sendRefreshBroadcast()
//                    val tip = if (isInternalDrawable(uriStr)) "内置图片已添加滤镜应用并另存" else "滤镜应用完成"
//                    Toast.makeText(this@PhotoPreviewActivity, tip, Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//
//    private fun confirmDelete() {
//        AlertDialog.Builder(this)
//            .setTitle("确认删除")
//            .setMessage("是否要删除这张图片？")
//            .setPositiveButton("删除") { _, _ ->
//                deleteImage()
//            }
//            .setNegativeButton("取消", null)
//            .show()
//    }
//
//    private fun deleteImage() {
//        imageUri?.let { uriStr ->
//            if (uriStr.startsWith("drawable://")) {
//                Toast.makeText(this, "内置图片不可删除", Toast.LENGTH_SHORT).show()
//            } else {
//                val uri = Uri.parse(uriStr)
//                lifecycleScope.launch(Dispatchers.IO) {
//                    contentResolver.delete(uri, null, null)
//                    withContext(Dispatchers.Main) {
//                        setResult(RESULT_OK, Intent().putExtra("need_refresh", true))
//                        Toast.makeText(this@PhotoPreviewActivity, "删除成功", Toast.LENGTH_SHORT).show()
//                        finish()
//                    }
//                }
//            }
//        }
//        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("MEDIA_CHANGED"))
//    }
//
//    // ===================== 工具方法（原有逻辑） =====================
//    private fun isInternalDrawable(uriStr: String): Boolean {
//        return uriStr.startsWith("drawable://")
//    }
//
//    private suspend fun loadImageBitmap(uriStr: String): Bitmap? {
//        return if (isInternalDrawable(uriStr)) {
//            loadDrawableBitmap(uriStr)
//        } else {
//            withContext(Dispatchers.IO) {
//                MediaStore.Images.Media.getBitmap(contentResolver, Uri.parse(uriStr))
//            }
//        }
//    }
//
//    private fun loadDrawableBitmap(uriStr: String): Bitmap? {
//        return try {
//            val resId = uriStr.replace("drawable://", "").toInt()
//            BitmapFactory.decodeResource(resources, resId)
//        } catch (e: Exception) {
//            e.printStackTrace()
//            null
//        }
//    }
//
//    private fun saveBitmapToMediaStore(bitmap: Bitmap): Uri {
//        val contentValues = ContentValues().apply {
//            put(MediaStore.Images.Media.DISPLAY_NAME, "edit_${System.currentTimeMillis()}.jpg")
//            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/EditImages")
//            }
//        }
//        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
//        contentResolver.openOutputStream(uri)?.use { outputStream ->
//            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
//        }
//        return uri
//    }
//
//    private fun saveToAlbum(croppedUri: Uri) {
//        val filePath = croppedUri.path ?: return
//        val file = File(filePath)
//        if (!file.exists()) {
//            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        file.inputStream().use { inputStream ->
//            val contentValues = ContentValues().apply {
//                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
//                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
//                put(MediaStore.Images.Media.SIZE, file.length())
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                    put(MediaStore.Images.Media.IS_PENDING, 1)
//                }
//            }
//
//            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
//            uri?.let { outputUri ->
//                contentResolver.openOutputStream(outputUri)?.use { outputStream ->
//                    inputStream.copyTo(outputStream)
//                }
//
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                    contentValues.clear()
//                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
//                    contentResolver.update(outputUri, contentValues, null, null)
//                }
//
//                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("MEDIA_CHANGED"))
//                Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show()
//            } ?: run {
//                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    private fun sendRefreshBroadcast() {
//        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("MEDIA_CHANGED"))
//    }
//
//    private fun resToUri(resId: Int): Uri {
//        return Uri.parse("android.resource://${packageName}/$resId")
//    }
//
//    private fun getUCropOptions(): UCrop.Options {
//        val options = UCrop.Options()
//        options.setCompressionQuality(90)
//        options.setShowCropGrid(true)
//        return options
//    }
//}