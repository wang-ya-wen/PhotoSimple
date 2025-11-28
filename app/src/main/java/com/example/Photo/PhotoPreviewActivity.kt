package com.example.Photo

import android.Manifest
import android.annotation.SuppressLint
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
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestOptions
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

    // 手势相关
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var matrix: Matrix
    private lateinit var savedMatrix: Matrix
    private val startPoint = PointF()
    private var mode = NONE // 触摸模式
    private var currentScale = 1.0f // 当前缩放比例
    private var maxScale = 3.0f // 最大缩放比例
    private var minScale = 0.5f // 最小缩放比例

    // 编辑历史栈
    private val editHistoryStack = EditHistoryStack()

    // 裁剪比例配置
    private val cropRatios = mapOf(
        "1:1" to Pair(1f, 1f),
        "3:4" to Pair(3f, 4f),
        "9:16" to Pair(9f, 16f),
        "自由" to Pair(0f, 0f) // 自由裁剪
    )
    private var currentCropRatio = Pair(1f, 1f) // 默认1:1裁剪

    // 触摸模式常量
    companion object {
        private const val NONE = 0 // 无触摸
        private const val DRAG = 1 // 拖动
        private const val ZOOM = 2 // 缩放
        const val EXTRA_IMAGE_URI = "imageUri"
    }

    // 启动器
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var writePermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var pickPhotoLauncher: ActivityResultLauncher<Intent>
    private lateinit var cropResultLauncher: ActivityResultLauncher<Intent>

    // 双击返回时间戳
    private var lastClickTime = 0L

    // 缩放弹窗实例
    private var scaleDialog: AlertDialog? = null

    // 保存动作的回调（挂起函数）
    private var saveAction: (suspend () -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化矩阵
        matrix = Matrix()
        savedMatrix = Matrix()
        binding.ivPreview.imageMatrix = matrix
        binding.ivPreview.scaleType = ImageView.ScaleType.MATRIX // 必须设置为MATRIX才能用矩阵变换

        // 全屏设置
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.hide()

        // 获取传递的图片Uri
        imageUri = intent.getStringExtra(EXTRA_IMAGE_URI) ?: intent.getStringExtra("imageUri")
        Log.d(TAG, "接收的图片Uri：$imageUri")

        // 初始化所有启动器
        initLaunchers()

        // 加载图片
        loadImage()

        // 初始化手势检测器
        initGestureDetector()

        // 初始化裁剪比例选择
        initCropRatioSelector()

        // 初始化Undo/Redo按钮
        initEditHistoryButtons()

        // 绑定编辑按钮点击事件
        binding.btnEdit.setOnClickListener {
            Log.d(TAG, "编辑按钮被点击")
            showEditOptions()
        }

        // 绑定导入图片按钮点击事件
        binding.btnImport.setOnClickListener {
            Log.d(TAG, "【导入图片按钮】点击事件已触发！")
            checkPhotoPermission()
        }
    }

    /**
     * 初始化手势检测器（缩放+平移）
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initGestureDetector() {
        // 缩放手势检测器
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                mode = ZOOM
                savedMatrix.set(matrix)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                var scale = detector.scaleFactor
                val previousScale = currentScale
                currentScale *= scale

                // 限制缩放范围
                currentScale = when {
                    currentScale > maxScale -> maxScale
                    currentScale < minScale -> minScale
                    else -> currentScale
                }

                // 修正缩放比例
                scale = currentScale / previousScale

                // 应用缩放（以手势焦点为中心）
                matrix.set(savedMatrix)
                matrix.postScale(scale, scale, detector.focusX, detector.focusY)
                binding.ivPreview.imageMatrix = matrix
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                mode = NONE
                editHistoryStack.saveState(matrix)
            }
        })

        // 平移触摸监听
        binding.ivPreview.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            val currentPoint = PointF(event.x, event.y)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mode = DRAG
                    savedMatrix.set(matrix)
                    startPoint.set(currentPoint)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == DRAG) {
                        val dx = currentPoint.x - startPoint.x
                        val dy = currentPoint.y - startPoint.y
                        matrix.set(savedMatrix)
                        matrix.postTranslate(dx, dy)
                        binding.ivPreview.imageMatrix = matrix
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mode = NONE
                    editHistoryStack.saveState(matrix)
                }
            }
            return@setOnTouchListener true
        }
    }

    /**
     * 初始化裁剪比例选择器
     */
    private fun initCropRatioSelector() {
        val ratioNames = cropRatios.keys.toList()
        binding.llRatioContainer.removeAllViews()
        ratioNames.forEach { ratioName ->
            val button = Button(this).apply {
                text = ratioName
                setPadding(20, 10, 20, 10)
                setBackgroundResource(android.R.drawable.btn_default)
                setOnClickListener {
                    currentCropRatio = cropRatios[ratioName]!!
                    Toast.makeText(this@PhotoPreviewActivity, "已选择$ratioName 裁剪比例", Toast.LENGTH_SHORT).show()
                }
            }
            binding.llRatioContainer.addView(button)
        }
    }

    /**
     * 初始化Undo/Redo按钮
     */
    private fun initEditHistoryButtons() {
        // 撤销按钮
        binding.btnUndo.setOnClickListener {
            val lastMatrix = editHistoryStack.undo()
            if (lastMatrix != null) {
                matrix.set(lastMatrix)
                binding.ivPreview.imageMatrix = matrix
                currentScale = getScaleFromMatrix(lastMatrix)
                Toast.makeText(this, "已撤销", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "无撤销操作", Toast.LENGTH_SHORT).show()
            }
        }

        // 恢复按钮
        binding.btnRedo.setOnClickListener {
            val nextMatrix = editHistoryStack.redo()
            if (nextMatrix != null) {
                matrix.set(nextMatrix)
                binding.ivPreview.imageMatrix = matrix
                currentScale = getScaleFromMatrix(nextMatrix)
                Toast.makeText(this, "已恢复", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "无恢复操作", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 从矩阵中获取缩放比例
     */
    private fun getScaleFromMatrix(matrix: Matrix): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MSCALE_X]
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
     * 初始化所有启动器
     */
    private fun initLaunchers() {
        // 1. 裁剪结果启动器
        cropResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val croppedUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                Log.d(TAG, "裁剪成功，Uri：$croppedUri")
                loadImage(croppedUri)
                imageUri = croppedUri.toString()
                // 保存裁剪后的图片并返回列表
                checkWritePermission {
                    saveToAlbum(croppedUri)
                    setResult(RESULT_OK, Intent().putExtra("need_refresh", true))
                    finish()
                }
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

        // 3. 写入权限启动器
        writePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                true
            } else {
                permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
            }
            if (isGranted) {
                lifecycleScope.launch {
                    saveAction?.invoke()
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
                    loadImage(it)
                    Toast.makeText(this, "导入图片成功", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 检查写入权限并执行保存动作
     */
    private fun checkWritePermission(action: suspend () -> Unit) {
        saveAction = action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            lifecycleScope.launch {
                action()
            }
        } else {
            val isGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (isGranted) {
                lifecycleScope.launch {
                    action()
                }
            } else {
                writePermissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }
    }

    /**
     * 加载图片（支持GIF动图，统一唯一的加载方法）
     */
    private fun loadImage() {
        val targetUri = imageUri?.let { Uri.parse(it) } ?: return
        loadImage(targetUri)
    }

    private fun loadImage(uri: Uri) {
        if (isGifFile(uri)) {
            // 加载GIF动图
            Glide.with(this)
                .asGif()
                .apply(RequestOptions.diskCacheStrategyOf(com.bumptech.glide.load.engine.DiskCacheStrategy.RESOURCE))
                .load(uri)
                .into(binding.ivPreview)
        } else {
            // 加载静态图片
            Glide.with(this)
                .asBitmap()
                .load(uri)
                .into(binding.ivPreview)
        }
        // 保存初始状态到历史栈
        editHistoryStack.saveState(matrix)
    }

    /**
     * 判断Uri是否为GIF文件
     */
    private fun isGifFile(uri: Uri): Boolean {
        return try {
            val mimeType = contentResolver.getType(uri)
            mimeType == "image/gif"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查照片读取权限
     */
    private fun checkPhotoPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.READ_EXTERNAL_STORAGE
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

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

    /**
     * 显示编辑选项弹窗
     */
    private fun showEditOptions() {
        val options = arrayOf("裁剪", "删除", "旋转", "缩放", "添加水印", "基础滤镜")
        AlertDialog.Builder(this)
            .setTitle("编辑图片")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startCrop() // 修正：调用无参的startCrop
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

    /**
     * 启动裁剪（无参版，适配多比例）
     */
    private fun startCrop() {
        imageUri?.let { uriStr ->
            val sourceUri = Uri.parse(uriStr)
            // GIF裁剪提示
            if (isGifFile(sourceUri)) {
                AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("GIF动图裁剪后将转为静态图片，是否继续？")
                    .setPositiveButton("继续") { _, _ ->
                        startCropProcess(sourceUri)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                startCropProcess(sourceUri)
            }
        } ?: Toast.makeText(this, "图片Uri为空，无法裁剪", Toast.LENGTH_SHORT).show()
    }

    /**
     * 执行裁剪流程
     */
    private fun startCropProcess(sourceUri: Uri) {
        val destDir = File(getExternalFilesDir(null), "CropImages")
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, "crop_${System.currentTimeMillis()}.jpg")
        val destUri = Uri.fromFile(destFile)

        // 配置UCrop
        val uCrop = UCrop.of(sourceUri, destUri)
        // 设置裁剪比例（0f,0f为自由裁剪）
        if (currentCropRatio.first != 0f && currentCropRatio.second != 0f) {
            uCrop.withAspectRatio(currentCropRatio.first, currentCropRatio.second)
        } else {
            uCrop.useSourceImageAspectRatio()
        }
        // 裁剪配置
        uCrop.withMaxResultSize(1920, 1920)
        val options = UCrop.Options().apply {
            setCompressionQuality(90)
            setShowCropGrid(true)
            setStatusBarColor(resources.getColor(android.R.color.black, theme))
            setToolbarColor(resources.getColor(android.R.color.darker_gray, theme))
        }
        uCrop.withOptions(options)

        cropResultLauncher.launch(uCrop.getIntent(this))
    }

    /**
     * 旋转图片（适配GIF）
     */
    private fun rotateImage() {
        imageUri?.let { uriStr ->
            val sourceUri = Uri.parse(uriStr)
            if (isGifFile(sourceUri)) {
                AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("GIF动图旋转后将转为静态图片，是否继续？")
                    .setPositiveButton("继续") { _, _ ->
                        rotateStaticImage(uriStr)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                rotateStaticImage(uriStr)
            }
        }
    }

    /**
     * 旋转静态图片（保存后返回列表）
     */
    private fun rotateStaticImage(uriStr: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = loadImageBitmap(uriStr) ?: return@launch
            val matrix = Matrix().apply { postRotate(90f) }
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            checkWritePermission {
                val newUri = saveBitmapToMediaStore(rotatedBitmap)
                withContext(Dispatchers.Main) {
                    loadImage(newUri)
                    imageUri = newUri.toString()
                    sendRefreshBroadcast()
                    val tip = if (isInternalDrawable(uriStr)) "内置图片已另存为新图片" else "旋转完成"
                    Toast.makeText(this@PhotoPreviewActivity, tip, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK, Intent().putExtra("need_refresh", true))
                    finish()
                }
            }
        }
    }

    /**
     * 显示缩放弹窗
     */
    private fun showScaleDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_scale, null)
        val btnZoomIn = dialogView.findViewById<Button>(R.id.btn_zoom_in)
        val btnZoomOut = dialogView.findViewById<Button>(R.id.btn_zoom_out)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_scale)

        scaleDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("调整缩放")
            .create()

        scaleDialog?.show()

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
                scaleDialog?.dismiss()
                return@setOnClickListener
            }
            val sourceUri = imageUri?.let { Uri.parse(it) } ?: run {
                scaleDialog?.dismiss()
                return@setOnClickListener
            }

            // GIF缩放提示
            if (isGifFile(sourceUri)) {
                AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("GIF动图缩放后将转为静态图片，是否继续？")
                    .setPositiveButton("继续") { _, _ ->
                        saveScaledImage()
                    }
                    .setNegativeButton("取消") { _, _ ->
                        scaleDialog?.dismiss()
                    }
                    .show()
            } else {
                saveScaledImage()
            }
        }
    }

    /**
     * 保存缩放后的图片（保存后返回列表）
     */
    private fun saveScaledImage() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                binding.ivPreview.isDrawingCacheEnabled = true
                val scaledBitmap = Bitmap.createBitmap(binding.ivPreview.drawingCache)
                binding.ivPreview.isDrawingCacheEnabled = false

                checkWritePermission {
                    val newUri = saveBitmapToMediaStore(scaledBitmap)
                    withContext(Dispatchers.Main) {
                        loadImage(newUri)
                        imageUri = newUri.toString()
                        sendRefreshBroadcast()
                        Toast.makeText(this@PhotoPreviewActivity, "缩放完成", Toast.LENGTH_SHORT).show()
                        scaleDialog?.dismiss()
                        setResult(RESULT_OK, Intent().putExtra("need_refresh", true))
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存缩放图片失败：${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PhotoPreviewActivity, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
                    scaleDialog?.dismiss()
                }
            }
        }
    }

    /**
     * 显示水印弹窗
     */
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

    /**
     * 添加水印（适配GIF）
     */
    private fun addWatermark(text: String) {
        imageUri?.let { uriStr ->
            val sourceUri = Uri.parse(uriStr)
            if (isGifFile(sourceUri)) {
                AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("GIF动图添加水印后将转为静态图片，是否继续？")
                    .setPositiveButton("继续") { _, _ ->
                        addWatermarkToStaticImage(uriStr, text)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                addWatermarkToStaticImage(uriStr, text)
            }
        }
    }

    /**
     * 为静态图片添加水印（保存后返回列表）
     */
    private fun addWatermarkToStaticImage(uriStr: String, text: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
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

                checkWritePermission {
                    val newUri = saveBitmapToMediaStore(resultBitmap)
                    withContext(Dispatchers.Main) {
                        loadImage(newUri)
                        imageUri = newUri.toString()
                        sendRefreshBroadcast()
                        val tip = if (isInternalDrawable(uriStr)) "内置图片已添加水印并另存" else "水印添加完成"
                        Toast.makeText(this@PhotoPreviewActivity, tip, Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK, Intent().putExtra("need_refresh", true))
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "添加水印失败：${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PhotoPreviewActivity, "添加水印失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 显示滤镜弹窗
     */
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

    /**
     * 应用亮度滤镜
     */
    private fun applyBrightnessFilter() {
        applyColorFilter(ColorMatrix().apply { setScale(1.5f, 1.5f, 1.5f, 1f) })
    }

    /**
     * 应用对比度滤镜
     */
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

    /**
     * 应用颜色滤镜（适配GIF）
     */
    private fun applyColorFilter(colorMatrix: ColorMatrix) {
        imageUri?.let { uriStr ->
            val sourceUri = Uri.parse(uriStr)
            if (isGifFile(sourceUri)) {
                AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("GIF动图应用滤镜后将转为静态图片，是否继续？")
                    .setPositiveButton("继续") { _, _ ->
                        applyFilterToStaticImage(uriStr, colorMatrix)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                applyFilterToStaticImage(uriStr, colorMatrix)
            }
        }
    }

    /**
     * 为静态图片应用滤镜（保存后返回列表）
     */
    private fun applyFilterToStaticImage(uriStr: String, colorMatrix: ColorMatrix) {
        if (isInternalDrawable(uriStr)) {
            Toast.makeText(this, "内置图片仅支持预览编辑，无法保存", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = loadImageBitmap(uriStr) ?: return@launch
                val config = bitmap.config ?: Bitmap.Config.ARGB_8888
                val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, config)
                val canvas = Canvas(resultBitmap)
                val paint = Paint().apply {
                    colorFilter = ColorMatrixColorFilter(colorMatrix)
                }
                canvas.drawBitmap(bitmap, 0f, 0f, paint)

                checkWritePermission {
                    val newUri = saveBitmapToMediaStore(resultBitmap)
                    withContext(Dispatchers.Main) {
                        loadImage(newUri)
                        imageUri = newUri.toString()
                        sendRefreshBroadcast()
                        val tip = if (isInternalDrawable(uriStr)) "内置图片已添加滤镜并另存" else "滤镜应用完成"
                        Toast.makeText(this@PhotoPreviewActivity, tip, Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK, Intent().putExtra("need_refresh", true))
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "应用滤镜失败：${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PhotoPreviewActivity, "应用滤镜失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 确认删除
     */
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

    /**
     * 删除图片
     */
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

    // ===================== 工具方法 =====================
    /**
     * 判断是否为内置drawable图片
     */
    private fun isInternalDrawable(uriStr: String): Boolean {
        return uriStr.startsWith("drawable://")
    }

    /**
     * 加载图片为Bitmap（适配GIF）
     */
    private suspend fun loadImageBitmap(uriStr: String): Bitmap? {
        return if (isInternalDrawable(uriStr)) {
            loadDrawableBitmap(uriStr)
        } else {
            withContext(Dispatchers.IO) {
                val uri = Uri.parse(uriStr)
                if (isGifFile(uri)) {
                    try {
                        val gifDrawable = Glide.with(this@PhotoPreviewActivity)
                            .asGif()
                            .load(uri)
                            .submit()
                            .get() as GifDrawable
                        gifDrawable.firstFrame
                    } catch (e: Exception) {
                        Log.e(TAG, "加载GIF首帧失败：${e.message}", e)
                        null
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
            }
        }
    }

    /**
     * 加载drawable为Bitmap
     */
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
     * 保存Bitmap到媒体库
     */
    private fun saveBitmapToMediaStore(bitmap: Bitmap): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "edit_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/EditImages")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val filePath = "${Environment.getExternalStorageDirectory()}/DCIM/EditImages/edit_${System.currentTimeMillis()}.jpg"
                put(MediaStore.Images.Media.DATA, filePath)
            }
        }

        var uri: Uri? = null
        try {
            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                Log.e(TAG, "保存失败：MediaStore插入返回null")
                Toast.makeText(this, "保存失败：媒体库插入失败", Toast.LENGTH_SHORT).show()
                return saveToPrivateDir(bitmap)
            }

            val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            } ?: run {
                Log.e(TAG, "保存失败：无法打开输出流")
                Toast.makeText(this, "保存失败：输出流为空", Toast.LENGTH_SHORT).show()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }

            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "保存失败：${e.message}")
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
            uri = saveToPrivateDir(bitmap)
        }
        return uri ?: saveToPrivateDir(bitmap)
    }

    /**
     * 降级保存到应用私有目录
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
     * 保存裁剪后的图片到相册
     */
    private fun saveToAlbum(croppedUri: Uri) {
        val filePath = croppedUri.path ?: return
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

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

    /**
     * 发送刷新广播
     */
    private fun sendRefreshBroadcast() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("MEDIA_CHANGED"))
    }

    /**
     * drawable转Uri
     */
    private fun resToUri(resId: Int): Uri {
        return Uri.parse("android.resource://${packageName}/$resId")
    }

    /**
     * 重写返回键，确保手动返回也传递结果
     */
    override fun onBackPressed() {
        scaleDialog?.dismiss()
        setResult(RESULT_OK, Intent().putExtra("need_refresh", false))
        super.onBackPressed()
    }

    /**
     * 释放资源，避免内存泄漏
     */
    override fun onDestroy() {
        super.onDestroy()
        scaleDialog?.dismiss()
        saveAction = null
    }
}
//package com.example.Photo
//
//import android.Manifest
//import android.content.ContentValues
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.graphics.*
//import android.net.Uri
//import android.os.Build
//import android.os.Bundle
//import android.os.Environment
//import android.provider.MediaStore
//import android.util.Log
//import android.view.Gravity
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
//import com.bumptech.glide.Glide
//import com.bumptech.glide.load.resource.gif.GifDrawable
//import com.bumptech.glide.request.RequestOptions
//import com.example.Photo.databinding.ActivityPhotoPreviewBinding
//import com.yalantis.ucrop.UCrop
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import java.io.File
//import java.io.FileOutputStream
//import java.io.OutputStream
//
//class PhotoPreviewActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityPhotoPreviewBinding
//    private var imageUri: String? = null
//    private val TAG = "PhotoPreviewDebug"
//
//    // 启动器
//    private lateinit var permissionLauncher: ActivityResultLauncher<String>
//    private lateinit var writePermissionLauncher: ActivityResultLauncher<Array<String>>
//    private lateinit var pickPhotoLauncher: ActivityResultLauncher<Intent>
//    private lateinit var cropResultLauncher: ActivityResultLauncher<Intent>
//
//    // 双击返回时间戳
//    private var lastClickTime = 0L
//    // 缩放弹窗实例
//    private var scaleDialog: AlertDialog? = null
//
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
//        // 加载图片（使用支持GIF的重载方法）
//        loadImage()
//
//        // 绑定编辑按钮点击事件
//        binding.btnEdit.setOnClickListener {
//            Log.d(TAG, "编辑按钮被点击")
//            showEditOptions()
//        }
//
//        // 绑定导入图片按钮点击事件
//        binding.btnImport.setOnClickListener {
//            Log.d(TAG, "【导入图片按钮】点击事件已触发！")
//            checkPhotoPermission()
//        }
//
//        // 双击图片关闭预览
//        binding.ivPreview.setOnClickListener {
//            val currentTime = System.currentTimeMillis()
//            if (currentTime - lastClickTime < 300) {
//                // 普通双击返回，标记无需刷新
//                setResult(RESULT_OK, Intent().putExtra("need_refresh", false))
//                finish()
//            }
//            lastClickTime = currentTime
//        }
//    }
//
//    /**
//     * 动态添加编辑按钮（备用，若XML按钮失效）
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
//            elevation = 20f
//            isClickable = true
//            isFocusable = true
//            setOnClickListener {
//                Log.d(TAG, "编辑按钮被点击")
//                showEditOptions()
//            }
//        }
//        val rootLayout = binding.root as FrameLayout
//        rootLayout.addView(editBtn)
//        editBtn.bringToFront()
//    }
//
//    /**
//     * 动态添加导入图片按钮（备用）
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
//        importBtn.bringToFront()
//        Log.d(TAG, "【导入图片按钮】已添加并提升层级")
//    }
//
//    /**
//     * 初始化所有启动器
//     */
//    private fun initLaunchers() {
//        // 1. 裁剪结果启动器
//        cropResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == RESULT_OK) {
//                val croppedUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
//                Log.d(TAG, "裁剪成功，Uri：$croppedUri")
//                loadImage(croppedUri)
//                imageUri = croppedUri.toString()
//                // 保存裁剪后的图片并返回列表
//                checkWritePermission {
//                    saveToAlbum(croppedUri)
//                    // 裁剪保存后设置返回结果并关闭
//                    setResult(RESULT_OK, Intent().putExtra("need_refresh", true))
//                    finish()
//                }
//            } else if (result.resultCode == UCrop.RESULT_ERROR) {
//                val error = UCrop.getError(result.data!!)
//                Log.e(TAG, "裁剪失败：${error?.message}")
//                Toast.makeText(this, "裁剪失败：${error?.message}", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        // 2. 照片读取权限启动器
//        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
//            Log.d(TAG, "【读取权限请求结果】是否授予：$isGranted")
//            if (isGranted) {
//                Log.d(TAG, "【读取权限请求结果】授权成功，执行pickPhoto()")
//                pickPhoto()
//            } else {
//                Toast.makeText(this, "请授予照片权限才能导入", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        // 3. 写入权限启动器
//        writePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
//            val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                true
//            } else {
//                permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
//            }
//            if (isGranted) {
//                lifecycleScope.launch {
//                    saveAction?.invoke()
//                }
//            } else {
//                Toast.makeText(this, "需要存储权限才能保存", Toast.LENGTH_SHORT).show()
//            }
//            saveAction = null
//        }
//
//        // 4. 选择图片启动器
//        pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == RESULT_OK) {
//                val newUri = result.data?.data
//                newUri?.let {
//                    Log.d(TAG, "选择图片成功，Uri：$it")
//                    imageUri = it.toString()
//                    loadImage(it)
//                    Toast.makeText(this, "导入图片成功", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//
//    // 保存动作的回调（挂起函数）
//    private var saveAction: (suspend () -> Unit)? = null
//
//    /**
//     * 检查写入权限并执行保存动作
//     */
//    private fun checkWritePermission(action: suspend () -> Unit) {
//        saveAction = action
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            lifecycleScope.launch {
//                action()
//            }
//        } else {
//            val isGranted = ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.WRITE_EXTERNAL_STORAGE
//            ) == PackageManager.PERMISSION_GRANTED
//            if (isGranted) {
//                lifecycleScope.launch {
//                    action()
//                }
//            } else {
//                writePermissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
//            }
//        }
//    }
//
//    /**
//     * 加载图片（核心：支持GIF动图，统一唯一的加载方法）
//     * 重载1：无参数，使用当前imageUri加载
//     * 重载2：传入Uri，加载指定图片
//     */
//    private fun loadImage() {
//        val targetUri = imageUri?.let { Uri.parse(it) } ?: return
//        loadImage(targetUri)
//    }
//
//    private fun loadImage(uri: Uri) {
//        if (isGifFile(uri)) {
//            // 加载GIF动图（使用Glide）
//            Glide.with(this)
//                .asGif()
//                .apply(RequestOptions.diskCacheStrategyOf(com.bumptech.glide.load.engine.DiskCacheStrategy.RESOURCE))
//                .load(uri)
//                .into(binding.ivPreview)
//        } else {
//            // 加载静态图片
//            Glide.with(this)
//                .asBitmap()
//                .load(uri)
//                .into(binding.ivPreview)
//        }
//    }
//
//    /**
//     * 判断Uri是否为GIF文件
//     */
//    private fun isGifFile(uri: Uri): Boolean {
//        return try {
//            val mimeType = contentResolver.getType(uri)
//            mimeType == "image/gif"
//        } catch (e: Exception) {
//            false
//        }
//    }
//
//    /**
//     * 检查照片读取权限
//     */
//    private fun checkPhotoPermission() {
//        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            Manifest.permission.READ_MEDIA_IMAGES
//        } else {
//            Manifest.permission.READ_EXTERNAL_STORAGE
//        }
//        val isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
//
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
//    private fun pickPhoto() {
//        Log.d(TAG, "【选择图片】开始构建相册Intent")
//
//        val intent = Intent(Intent.ACTION_GET_CONTENT)
//        intent.type = "image/*"
//        intent.addCategory(Intent.CATEGORY_OPENABLE)
//
//        val chooser = Intent.createChooser(intent, "选择相册应用")
//
//        Log.d(TAG, "【选择图片】Intent配置完成，启动相册选择器")
//        if (chooser.resolveActivity(packageManager) != null) {
//            pickPhotoLauncher.launch(chooser)
//        } else {
//            Log.e(TAG, "【选择图片】无匹配的相册应用！")
//            Toast.makeText(this, "未找到可打开图片的应用", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    /**
//     * 显示编辑选项弹窗
//     */
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
//    /**
//     * 启动裁剪（优化：移除冗余代码，仅保留GIF判断）
//     */
//    private fun startCrop() {
//        imageUri?.let { uriStr ->
//            val sourceUri = if (uriStr.startsWith("drawable://")) {
//                resToUri(uriStr.split("://")[1].toInt())
//            } else {
//                Uri.parse(uriStr)
//            }
//            // GIF裁剪提示
//            if (isGifFile(sourceUri)) {
//                AlertDialog.Builder(this)
//                    .setTitle("提示")
//                    .setMessage("GIF动图裁剪后将转为静态图片，是否继续？")
//                    .setPositiveButton("继续") { _, _ ->
//                        startCropProcess(sourceUri)
//                    }
//                    .setNegativeButton("取消", null)
//                    .show()
//            } else {
//                startCropProcess(sourceUri)
//            }
//        }
//    }
//
//    /**
//     * 执行裁剪流程
//     */
//    private fun startCropProcess(sourceUri: Uri) {
//        val destDir = File(getExternalFilesDir(null), "CropImages")
//        if (!destDir.exists()) destDir.mkdirs()
//        val destFile = File(destDir, "crop_${System.currentTimeMillis()}.jpg")
//        val destUri = Uri.fromFile(destFile)
//
//        val cropIntent = UCrop.of(sourceUri, destUri)
//            .withAspectRatio(1f, 1f)
//            .withOptions(getUCropOptions())
//            .getIntent(this)
//        cropResultLauncher.launch(cropIntent)
//    }
//
//    /**
//     * 旋转图片（适配GIF）
//     */
//    private fun rotateImage() {
//        imageUri?.let { uriStr ->
//            val sourceUri = Uri.parse(uriStr)
//            if (isGifFile(sourceUri)) {
//                AlertDialog.Builder(this)
//                    .setTitle("提示")
//                    .setMessage("GIF动图旋转后将转为静态图片，是否继续？")
//                    .setPositiveButton("继续") { _, _ ->
//                        rotateStaticImage(uriStr)
//                    }
//                    .setNegativeButton("取消", null)
//                    .show()
//            } else {
//                rotateStaticImage(uriStr)
//            }
//        }
//    }
//
//    /**
//     * 旋转静态图片（保存后返回列表）
//     */
//    private fun rotateStaticImage(uriStr: String) {
//        lifecycleScope.launch(Dispatchers.IO) {
//            val bitmap = loadImageBitmap(uriStr) ?: return@launch
//            val matrix = Matrix().apply { postRotate(90f) }
//            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
//            checkWritePermission {
//                val newUri = saveBitmapToMediaStore(rotatedBitmap)
//                withContext(Dispatchers.Main) {
//                    loadImage(newUri)
//                    imageUri = newUri.toString()
//                    sendRefreshBroadcast()
//                    val tip = if (isInternalDrawable(uriStr)) "内置图片已另存为新图片" else "旋转完成"
//                    Toast.makeText(this@PhotoPreviewActivity, tip, Toast.LENGTH_SHORT).show()
//                    // 旋转保存后返回列表
//                    setResult(RESULT_OK, Intent().putExtra("need_refresh", true))
//                    finish()
//                }
//            }
//        }
//    }
//
//    /**
//     * 显示缩放弹窗
//     */
//    private fun showScaleDialog() {
//        val dialogView = layoutInflater.inflate(R.layout.dialog_scale, null)
//        val btnZoomIn = dialogView.findViewById<Button>(R.id.btn_zoom_in)
//        val btnZoomOut = dialogView.findViewById<Button>(R.id.btn_zoom_out)
//        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_scale)
//
//        scaleDialog = AlertDialog.Builder(this)
//            .setView(dialogView)
//            .setTitle("调整缩放")
//            .create()
//
//        scaleDialog?.show()
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
//                scaleDialog?.dismiss()
//                return@setOnClickListener
//            }
//            val sourceUri = imageUri?.let { Uri.parse(it) } ?: run {
//                scaleDialog?.dismiss()
//                return@setOnClickListener
//            }
//
//            // GIF缩放提示
//            if (isGifFile(sourceUri)) {
//                AlertDialog.Builder(this)
//                    .setTitle("提示")
//                    .setMessage("GIF动图缩放后将转为静态图片，是否继续？")
//                    .setPositiveButton("继续") { _, _ ->
//                        saveScaledImage()
//                    }
//                    .setNegativeButton("取消") { _, _ ->
//                        scaleDialog?.dismiss()
//                    }
//                    .show()
//            } else {
//                saveScaledImage()
//            }
//        }
//    }
//
//    /**
//     * 保存缩放后的图片（保存后返回列表）
//     */
//    private fun saveScaledImage() {
//        lifecycleScope.launch(Dispatchers.IO) {
//            binding.ivPreview.isDrawingCacheEnabled = true
//            val scaledBitmap = Bitmap.createBitmap(binding.ivPreview.drawingCache)
//            binding.ivPreview.isDrawingCacheEnabled = false
//
//            checkWritePermission {
//                val newUri = saveBitmapToMediaStore(scaledBitmap)
//                withContext(Dispatchers.Main) {
//                    loadImage(newUri)
//                    imageUri = newUri.toString()
//                    sendRefreshBroadcast()
//                    Toast.makeText(this@PhotoPreviewActivity, "缩放完成", Toast.LENGTH_SHORT).show()
//                    scaleDialog?.dismiss()
//                    // 缩放保存后返回列表
//                    setResult(RESULT_OK, Intent().putExtra("need_refresh", true))
//                    finish()
//                }
//            }
//        }
//    }
//
//    /**
//     * 显示水印弹窗
//     */
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
//    /**
//     * 添加水印（适配GIF）
//     */
//    private fun addWatermark(text: String) {
//        imageUri?.let { uriStr ->
//            val sourceUri = Uri.parse(uriStr)
//            if (isGifFile(sourceUri)) {
//                AlertDialog.Builder(this)
//                    .setTitle("提示")
//                    .setMessage("GIF动图添加水印后将转为静态图片，是否继续？")
//                    .setPositiveButton("继续") { _, _ ->
//                        addWatermarkToStaticImage(uriStr, text)
//                    }
//                    .setNegativeButton("取消", null)
//                    .show()
//            } else {
//                addWatermarkToStaticImage(uriStr, text)
//            }
//        }
//    }
//
//    /**
//     * 为静态图片添加水印（保存后返回列表）
//     */
//    private fun addWatermarkToStaticImage(uriStr: String, text: String) {
//        lifecycleScope.launch(Dispatchers.IO) {
//            val bitmap = loadImageBitmap(uriStr) ?: return@launch
//            val resultBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
//            val canvas = Canvas(resultBitmap)
//            val paint = Paint().apply {
//                color = Color.WHITE
//                textSize = 40f
//                isAntiAlias = true
//                strokeWidth = 2f
//                style = Paint.Style.FILL_AND_STROKE
//            }
//            canvas.drawText(text, 50f, 50f, paint)
//
//            checkWritePermission {
//                val newUri = saveBitmapToMediaStore(resultBitmap)
//                withContext(Dispatchers.Main) {
//                    loadImage(newUri)
//                    imageUri = newUri.toString()
//                    sendRefreshBroadcast()
//                    val tip = if (isInternalDrawable(uriStr)) "内置图片已添加水印并另存" else "水印添加完成"
//                    Toast.makeText(this@PhotoPreviewActivity, tip, Toast.LENGTH_SHORT).show()
//                    // 水印保存后返回列表
//                    setResult(RESULT_OK, Intent().putExtra("need_refresh", true))
//                    finish()
//                }
//            }
//        }
//    }
//
//    /**
//     * 显示滤镜弹窗
//     */
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
//    /**
//     * 应用亮度滤镜
//     */
//    private fun applyBrightnessFilter() {
//        applyColorFilter(ColorMatrix().apply { setScale(1.5f, 1.5f, 1.5f, 1f) })
//    }
//
//    /**
//     * 应用对比度滤镜
//     */
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
//    /**
//     * 应用颜色滤镜（适配GIF）
//     */
//    private fun applyColorFilter(colorMatrix: ColorMatrix) {
//        imageUri?.let { uriStr ->
//            val sourceUri = Uri.parse(uriStr)
//            if (isGifFile(sourceUri)) {
//                AlertDialog.Builder(this)
//                    .setTitle("提示")
//                    .setMessage("GIF动图应用滤镜后将转为静态图片，是否继续？")
//                    .setPositiveButton("继续") { _, _ ->
//                        applyFilterToStaticImage(uriStr, colorMatrix)
//                    }
//                    .setNegativeButton("取消", null)
//                    .show()
//            } else {
//                applyFilterToStaticImage(uriStr, colorMatrix)
//            }
//        }
//    }
//
//    /**
//     * 为静态图片应用滤镜（保存后返回列表）
//     */
//    private fun applyFilterToStaticImage(uriStr: String, colorMatrix: ColorMatrix) {
//        if (isInternalDrawable(uriStr)) {
//            Toast.makeText(this, "内置图片仅支持预览编辑，无法保存", Toast.LENGTH_SHORT).show()
//        }
//        lifecycleScope.launch(Dispatchers.IO) {
//            val bitmap = loadImageBitmap(uriStr) ?: return@launch
//            val config = bitmap.config ?: Bitmap.Config.ARGB_8888
//            val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, config)
//            val canvas = Canvas(resultBitmap)
//            val paint = Paint().apply {
//                colorFilter = ColorMatrixColorFilter(colorMatrix)
//            }
//            canvas.drawBitmap(bitmap, 0f, 0f, paint)
//
//            checkWritePermission {
//                val newUri = saveBitmapToMediaStore(resultBitmap)
//                withContext(Dispatchers.Main) {
//                    loadImage(newUri)
//                    imageUri = newUri.toString()
//                    sendRefreshBroadcast()
//                    val tip = if (isInternalDrawable(uriStr)) "内置图片已添加滤镜并另存" else "滤镜应用完成"
//                    Toast.makeText(this@PhotoPreviewActivity, tip, Toast.LENGTH_SHORT).show()
//                    // 滤镜保存后返回列表
//                    setResult(RESULT_OK, Intent().putExtra("need_refresh", true))
//                    finish()
//                }
//            }
//        }
//    }
//
//    /**
//     * 确认删除
//     */
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
//    /**
//     * 删除图片（原有逻辑已保留返回列表）
//     */
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
//    // ===================== 工具方法 =====================
//    /**
//     * 判断是否为内置drawable图片
//     */
//    private fun isInternalDrawable(uriStr: String): Boolean {
//        return uriStr.startsWith("drawable://")
//    }
//
//    /**
//     * 加载图片为Bitmap（适配GIF）
//     */
//    private suspend fun loadImageBitmap(uriStr: String): Bitmap? {
//        return if (isInternalDrawable(uriStr)) {
//            loadDrawableBitmap(uriStr)
//        } else {
//            withContext(Dispatchers.IO) {
//                val uri = Uri.parse(uriStr)
//                if (isGifFile(uri)) {
//                    try {
//                        val gifDrawable = Glide.with(this@PhotoPreviewActivity)
//                            .asGif()
//                            .load(uri)
//                            .submit()
//                            .get() as GifDrawable
//                        gifDrawable.firstFrame
//                    } catch (e: Exception) {
//                        null
//                    }
//                } else {
//                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
//                }
//            }
//        }
//    }
//
//    /**
//     * 加载drawable为Bitmap
//     */
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
//    /**
//     * 保存Bitmap到媒体库
//     */
//    private fun saveBitmapToMediaStore(bitmap: Bitmap): Uri {
//        val contentValues = ContentValues().apply {
//            put(MediaStore.Images.Media.DISPLAY_NAME, "edit_${System.currentTimeMillis()}.jpg")
//            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/EditImages")
//                put(MediaStore.Images.Media.IS_PENDING, 1)
//            } else {
//                put(MediaStore.Images.Media.DATA, "${Environment.getExternalStorageDirectory()}/DCIM/EditImages/edit_${System.currentTimeMillis()}.jpg")
//            }
//        }
//
//        var uri: Uri? = null
//        try {
//            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
//            if (uri == null) {
//                Log.e(TAG, "保存失败：MediaStore插入返回null")
//                Toast.makeText(this, "保存失败：媒体库插入失败", Toast.LENGTH_SHORT).show()
//                return saveToPrivateDir(bitmap)
//            }
//
//            val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
//            outputStream?.use {
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
//            } ?: run {
//                Log.e(TAG, "保存失败：无法打开输出流")
//                Toast.makeText(this, "保存失败：输出流为空", Toast.LENGTH_SHORT).show()
//            }
//
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                contentValues.clear()
//                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
//                contentResolver.update(uri, contentValues, null, null)
//            }
//
//            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Log.e(TAG, "保存失败：${e.message}")
//            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
//            uri = saveToPrivateDir(bitmap)
//        }
//        return uri ?: saveToPrivateDir(bitmap)
//    }
//
//    /**
//     * 降级保存到应用私有目录
//     */
//    private fun saveToPrivateDir(bitmap: Bitmap): Uri {
//        val privateDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "EditImages")
//        if (!privateDir.exists()) privateDir.mkdirs()
//        val file = File(privateDir, "edit_${System.currentTimeMillis()}.jpg")
//        try {
//            FileOutputStream(file).use {
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
//            }
//            Toast.makeText(this, "图片已保存到应用私有目录", Toast.LENGTH_SHORT).show()
//            return Uri.fromFile(file)
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Toast.makeText(this, "保存到私有目录失败", Toast.LENGTH_SHORT).show()
//            return Uri.EMPTY
//        }
//    }
//
//    /**
//     * 保存裁剪后的图片到相册
//     */
//    private fun saveToAlbum(croppedUri: Uri) {
//        val filePath = croppedUri.path ?: return
//        val file = File(filePath)
//        if (!file.exists()) {
//            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        try {
//            file.inputStream().use { inputStream ->
//                val contentValues = ContentValues().apply {
//                    put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
//                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
//                    put(MediaStore.Images.Media.SIZE, file.length())
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                        put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/EditImages")
//                        put(MediaStore.Images.Media.IS_PENDING, 1)
//                    }
//                }
//
//                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
//                uri?.let { outputUri ->
//                    contentResolver.openOutputStream(outputUri)?.use { outputStream ->
//                        inputStream.copyTo(outputStream)
//                    }
//
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                        contentValues.clear()
//                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
//                        contentResolver.update(outputUri, contentValues, null, null)
//                    }
//
//                    LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("MEDIA_CHANGED"))
//                    sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, outputUri))
//                    Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show()
//                } ?: run {
//                    Toast.makeText(this, "保存失败：媒体库插入null", Toast.LENGTH_SHORT).show()
//                }
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    /**
//     * 发送刷新广播
//     */
//    private fun sendRefreshBroadcast() {
//        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("MEDIA_CHANGED"))
//    }
//
//    /**
//     * drawable转Uri
//     */
//    private fun resToUri(resId: Int): Uri {
//        return Uri.parse("android.resource://${packageName}/$resId")
//    }
//
//    /**
//     * 获取UCrop配置
//     */
//    private fun getUCropOptions(): UCrop.Options {
//        val options = UCrop.Options()
//        options.setCompressionQuality(90)
//        options.setShowCropGrid(true)
//        return options
//    }
//
//    /**
//     * 重写返回键，确保手动返回也传递结果
//     */
//    override fun onBackPressed() {
//        scaleDialog?.dismiss()
//        setResult(RESULT_OK, Intent().putExtra("need_refresh", false))
//        super.onBackPressed()
//    }
//}