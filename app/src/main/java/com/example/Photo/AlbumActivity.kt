package com.example.Photo

import PhotoItem
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.Photo.adapter.PhotoAdapter
import com.example.Photo.databinding.ActivityAlbumBinding

//class AlbumActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityAlbumBinding

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        // 初始化ViewBinding（确保已启用ViewBinding）
//        binding = ActivityAlbumBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // 1. 设置RecyclerView的布局管理器（线性布局，垂直方向）
//        binding.rvPhotos.layoutManager = LinearLayoutManager(this)
//
//        // 2. 准备测试数据（替换为你的实际图片资源）
//        val testPhotos = listOf(
//            PhotoItem(imageRes = R.drawable.photo1),  // 确保drawable中有这些图片
//            PhotoItem(imageRes = R.drawable.photo2),
//            PhotoItem(imageRes = R.drawable.photo3)
//        )
//
//        // 3. 创建Adapter并绑定到RecyclerView
//        val adapter = PhotoAdapter(testPhotos)
//        binding.rvPhotos.adapter = adapter2025-11-19 10:52:29.530  8058-8079  HostConnection          com.example.Photo                    D  HostConnection::get() New Host Connection established 0xf47aa2b0, tid 8079

//}
//class AlbumActivity : AppCompatActivity() {
//    private lateinit var recyclerView: RecyclerView  // 直接声明RecyclerView
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_album)  // 直接加载布局
//
//        // 用findViewById获取RecyclerView
//        recyclerView = findViewById(R.id.rvPhotos)
//        // 设置LayoutManager
//        recyclerView.layoutManager = LinearLayoutManager(this)
//
//        // 准备数据+设置Adapter
//        val testPhotos = listOf(
//            PhotoItem(imageRes = R.drawable.photo1),
//            PhotoItem(imageRes = R.drawable.photo2),
//            PhotoItem(imageRes = R.drawable.photo3)
//        )
//        recyclerView.adapter = PhotoAdapter(testPhotos)
//    }
//}
import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

//11.21
class AlbumActivity : AppCompatActivity() {
    // ViewBinding
    private lateinit var binding: ActivityAlbumBinding

    // 权限请求启动器
    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                resetPagingState()
                loadMediaPage(currentPage)
            } else {
                Toast.makeText(this, "请授予照片权限以查看相册", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    // 裁剪结果启动器
    private val cropResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val croppedUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
            // 1. 插入到列表头部并局部刷新
            val newPhotoItem = PhotoItem(
                id = System.currentTimeMillis(),
                uri = croppedUri.toString(),
                isVideo = false,
                thumbnailUri = null,
                isLocalDrawable = false
            )
            mediaList.add(0, newPhotoItem)
            photoAdapter?.notifyItemInserted(0)
            binding.rvPhotos.scrollToPosition(0)

            // 2. 保存到媒体库并延迟刷新
            saveCroppedImageToMediaStore(croppedUri)
            lifecycleScope.launch(Dispatchers.Main) {
                kotlinx.coroutines.delay(300)
                resetPagingState()
                loadMediaPage(currentPage)
            }
            Toast.makeText(this, "裁剪成功", Toast.LENGTH_SHORT).show()
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = UCrop.getError(result.data!!)
            Toast.makeText(this, "裁剪失败：${error?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 预览结果启动器
    private val previewResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data?.getBooleanExtra("need_refresh", false) == true) {
            resetPagingState()
            loadMediaPage(currentPage)
        }
    }

    // 数据相关
    private val mediaList = mutableListOf<PhotoItem>()
    private var photoAdapter: PhotoAdapter? = null
    private var selectedUri: Uri? = null

    // 分页配置
    private val PAGE_SIZE = 20 // 每页加载数量
    private var currentPage = 0 // 当前页码
    private var isLoading = false // 加载中标记
    private var hasMoreData = true // 是否有更多数据

    // 广播接收者（可空类型，避免内存泄漏）
    private var broadcastReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "AlbumActivity"
        const val ACTION_MEDIA_CHANGED = "MEDIA_CHANGED" // 媒体库变化广播
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlbumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化RecyclerView
        initRecyclerView()

        // 检查权限并加载数据
        checkPermission()

        // 编辑按钮点击事件
        binding.btnEdit.setOnClickListener {
            selectedUri?.let { uri ->
                startCrop(uri)
            }
        }

        // 注册媒体库变化广播
        registerMediaBroadcast()
    }

    /**
     * 初始化RecyclerView（性能优化+分页监听）
     */
    private fun initRecyclerView() {
        photoAdapter = PhotoAdapter(
            mediaList = mediaList,
            onImageSelected = { uri ->
                selectedUri = uri
                binding.btnEdit.visibility = View.VISIBLE
            },
            onItemClick = { uri ->
                startPreview(uri)
            }
        )

        binding.rvPhotos.apply {
            layoutManager = GridLayoutManager(this@AlbumActivity, 3)
            adapter = photoAdapter
            setHasFixedSize(true) // 固定大小，优化性能
            recycledViewPool.setMaxRecycledViews(0, 30) // 复用池，减少View创建
            overScrollMode = View.OVER_SCROLL_NEVER // 关闭过度滚动

            // 分页加载监听
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy > 0 && !isLoading && hasMoreData) {
                        val layoutManager = recyclerView.layoutManager as GridLayoutManager
                        val visibleItemCount = layoutManager.childCount
                        val totalItemCount = layoutManager.itemCount
                        val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()

                        // 提前5个Item加载下一页
                        if (visibleItemCount + firstVisibleItem >= totalItemCount - 5) {
                            loadMediaPage(++currentPage)
                        }
                    }
                }
            })
        }
    }

    /**
     * 注册媒体库变化广播
     */
    private fun registerMediaBroadcast() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_MEDIA_CHANGED) {
                    resetPagingState()
                    loadMediaPage(currentPage)
                }
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver!!,
            IntentFilter(ACTION_MEDIA_CHANGED)
        )
    }

    /**
     * 检查权限（适配Android 13+）
     */
    private fun checkPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // 检查是否已授权
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            loadMediaPage(currentPage)
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    /**
     * 分页加载媒体库图片
     */
    private fun loadMediaPage(page: Int) {
        if (isLoading) return
        isLoading = true
        binding.pbLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val pageData = mutableListOf<PhotoItem>()
            val offset = page * PAGE_SIZE // 计算偏移量

            try {
                // 第一页添加本地drawable图片
                if (page == 0) {
                    val internalPhotos = listOf(
                        PhotoItem(1, "drawable://${R.drawable.photo1}", false, null, true),
                        PhotoItem(2, "drawable://${R.drawable.photo2}", false, null, true),
                        PhotoItem(3, "drawable://${R.drawable.photo3}", false, null, true)
                    )
                    pageData.addAll(internalPhotos)
                }

                // 查询设备媒体库（移除sortOrder中的LIMIT/OFFSET）
                val projection = arrayOf(MediaStore.Images.Media._ID)
                // 仅保留排序语句，删除LIMIT/OFFSET
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                val cursor: Cursor? = contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )

                cursor?.use {
                    if (it.count == 0) {
                        hasMoreData = false
                        return@use
                    }
                    val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

                    // 关键：移动Cursor到偏移量位置
                    if (it.moveToPosition(offset)) {
                        var count = 0
                        // 循环读取当前页数据（最多PAGE_SIZE条）
                        do {
                            val id = it.getLong(idColumn)
                            val mediaUri = Uri.withAppendedPath(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id.toString()
                            ).toString()
                            pageData.add(PhotoItem(id + 3, mediaUri, false, null, false))
                            count++
                        } while (it.moveToNext() && count < PAGE_SIZE)
                    } else {
                        // 偏移量超出数据总量，无更多数据
                        hasMoreData = false
                    }
                }

                // 更新UI
                withContext(Dispatchers.Main) {
                    val startPos = mediaList.size
                    mediaList.addAll(pageData)
                    photoAdapter?.notifyItemRangeInserted(startPos, pageData.size)
                    binding.pbLoading.visibility = View.GONE
                    if (!hasMoreData) {
                        binding.tvNoMoreData.visibility = View.VISIBLE
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "加载图片失败：${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.pbLoading.visibility = View.GONE
                    Toast.makeText(this@AlbumActivity, "加载图片失败", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 重置分页状态（刷新时调用）
     */
    private fun resetPagingState() {
        currentPage = 0
        isLoading = false
        hasMoreData = true
        mediaList.clear()
        photoAdapter?.notifyDataSetChanged()
        binding.tvNoMoreData.visibility = View.GONE
    }

    /**
     * 启动图片预览
     */
    private fun startPreview(uri: String) {
        val intent = Intent(this, PhotoPreviewActivity::class.java)
        intent.putExtra("imageUri", uri)
        previewResultLauncher.launch(intent)
    }

    /**
     * 启动图片裁剪
     */
    private fun startCrop(sourceUri: Uri) {
        val destDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "CropImages")
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, "crop_${System.currentTimeMillis()}.jpg")
        val destUri = Uri.fromFile(destFile)

        // 配置UCrop
        val cropIntent = UCrop.of(sourceUri, destUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1920, 1920)
            .withOptions(getUCropOptions())
            .getIntent(this)

        cropResultLauncher.launch(cropIntent)
    }

    /**
     * UCrop配置项
     */
    private fun getUCropOptions(): UCrop.Options {
        val options = UCrop.Options()
        options.setCompressionQuality(90) // 压缩质量
        options.setShowCropGrid(true) // 显示裁剪网格
        options.setStatusBarColor(resources.getColor(R.color.gray_800, theme)) // 状态栏颜色
        options.setToolbarColor(resources.getColor(R.color.gray_700, theme)) // 工具栏颜色
        return options
    }

    /**
     * 保存裁剪后的图片到媒体库
     */
    private fun saveCroppedImageToMediaStore(croppedUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            contentResolver.openInputStream(croppedUri)?.use { inputStream: InputStream ->
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "crop_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.SIZE, inputStream.available().toLong())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CropImages")
                    }
                }

                // 插入到媒体库
                val newUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                newUri?.let {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    // 通知系统扫描新文件
                    sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, newUri))
                }
            }
        }
    }

    /**
     * 销毁时释放资源（核心：防止内存泄漏）
     */
    override fun onDestroy() {
        super.onDestroy()
        // 注销广播
        broadcastReceiver?.let {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
            broadcastReceiver = null
        }
        // 释放适配器和RecyclerView
        photoAdapter = null
        binding.rvPhotos.adapter = null
    }
}
//class AlbumActivity : AppCompatActivity() {
//    private lateinit var broadcastReceiver: BroadcastReceiver
//    private lateinit var binding: ActivityAlbumBinding
//    private val REQUEST_PERMISSION = 100
//    private val mediaList = mutableListOf<PhotoItem>()
//    private var selectedUri: Uri? = null
//    private var photoAdapter: PhotoAdapter? = null // 全局适配器变量
//    private val cropResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//        if (result.resultCode == RESULT_OK) {
//            val data = result.data
//            val croppedUri = UCrop.getOutput(data!!) ?: return@registerForActivityResult
//
//            // 步骤1：手动将新图片添加到列表开头（立即显示）
//            val newPhotoItem = PhotoItem(
//                id = System.currentTimeMillis(),
//                uri = croppedUri.toString(),
//                isVideo = false,
//                thumbnailUri = null
//            )
//            mediaList.add(0, newPhotoItem)
//            photoAdapter?.notifyItemInserted(0) // 局部刷新，性能更好
//
//            // 步骤2：保存到媒体库并刷新（确保后续查询能获取）
//            saveCroppedImageToMediaStore(croppedUri)
//            lifecycleScope.launch(Dispatchers.Main) {
//                delay(300)
//                refreshMediaFromDevice()
//            }
//            Toast.makeText(this, "裁剪成功", Toast.LENGTH_SHORT).show()
//        }
//
//    }
//    // AlbumActivity中启动PhotoPreviewActivity时，用registerForActivityResult
//    private val previewResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//        if (result.resultCode == RESULT_OK && result.data?.getBooleanExtra("need_refresh", false) == true) {
//            refreshMediaFromDevice() // 收到通知后刷新
//        }
//    }
//
//    // 启动PhotoPreviewActivity的地方
//    private fun startPreview(uri: String) {
//        val intent = Intent(this, PhotoPreviewActivity::class.java)
//        intent.putExtra("image_uri", uri)
//        previewResultLauncher.launch(intent)
//    }
//    companion object {
//        const val REQUEST_CROP = 101
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityAlbumBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // 初始化适配器（仅创建一次）
//        photoAdapter = PhotoAdapter(
//            mediaList,
//            activity = this,
//            onImageSelected = { uri ->
//                selectedUri = uri
//                binding.btnEdit.visibility = View.VISIBLE
//            }
//        )
//        // 设置RecyclerView
//        binding.rvPhotos.layoutManager = GridLayoutManager(this, 3)
//        binding.rvPhotos.adapter = photoAdapter
//
//        // 检查权限
//        if (checkPermission()) {
//            refreshMediaFromDevice()
//        } else {
//            requestPermission()
//        }
//
//        // 编辑按钮点击事件
//        binding.btnEdit.setOnClickListener {
//            selectedUri?.let { uri ->
//                startCrop(uri)
//            }
//        }
//        broadcastReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                if (intent?.action == "MEDIA_CHANGED") {
//                    refreshMediaFromDevice() // 收到通知后立即刷新
//                }
//            }
//        }
//        LocalBroadcastManager.getInstance(this)
//            .registerReceiver(broadcastReceiver, IntentFilter("MEDIA_CHANGED"))
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        // 销毁时取消注册，避免内存泄漏
//        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
//    }
//
//    // 启动裁剪的方法
////    private fun startCrop(sourceUri: Uri) {
////        val destFile = File(cacheDir, "crop_${System.currentTimeMillis()}.jpg")
////        val destUri = Uri.fromFile(destFile)
////
////        UCrop.of(sourceUri, destUri)
////            .withAspectRatio(1f, 1f)
////            .withMaxResultSize(1920, 1920)
////            .start(this, REQUEST_CROP)
////    }
//    private fun startCrop(sourceUri: Uri) {
//        val destFile = File(cacheDir, "crop_${System.currentTimeMillis()}.jpg")
//        val destUri = Uri.fromFile(destFile)
//
//        val cropIntent = UCrop.of(sourceUri, destUri)
//            .withAspectRatio(1f, 1f)
//            .withMaxResultSize(1920, 1920)
//            .getIntent(this)
//
//        // 用新的回调启动裁剪，替代start(this, REQUEST_CROP)
//        cropResultLauncher.launch(cropIntent)
//    }
//    // 替换原loadMediaFromDevice，改为可重复调用的刷新方法
//    fun refreshMediaFromDevice() {
//        lifecycleScope.launch(Dispatchers.IO) {
//            mediaList.clear() // 清空旧数据
//
//            // 步骤1：添加内部drawable图片
//            val internalPhotos = listOf(
//                PhotoItem(1, "drawable://${R.drawable.photo1}", isVideo = false, null),
//                PhotoItem(2, "drawable://${R.drawable.photo2}", isVideo = false, null),
//                PhotoItem(3, "drawable://${R.drawable.photo3}", isVideo = false, null)
//            )
//            mediaList.addAll(internalPhotos)
//
//            // 步骤2：查询设备媒体库图片
//            val projection = arrayOf(MediaStore.Images.Media._ID)
//            contentResolver.query(
//                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//                projection,
//                null,
//                null,
//                "${MediaStore.Images.Media.DATE_ADDED} DESC"
//            )?.use { cursor ->
//                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
//                while (cursor.moveToNext()) {
//                    val id = cursor.getLong(idColumn)
//                    val mediaUri = Uri.withAppendedPath(
//                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//                        id.toString()
//                    ).toString()
//                    mediaList.add(PhotoItem(id + 3, mediaUri, isVideo = false, null))
//                }
//            }
//
//            // 更新UI
//            withContext(Dispatchers.Main) {
//                Log.d("AlbumDebug", "刷新后媒体数量：${mediaList.size}")
//                photoAdapter?.notifyDataSetChanged() // 通知适配器刷新
//                if (mediaList.isEmpty()) {
//                    Toast.makeText(this@AlbumActivity, "暂无图片数据", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//
//    // 权限回调中调用刷新
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<out String>,
//        grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == REQUEST_PERMISSION && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
//            refreshMediaFromDevice() // 替换原loadMediaFromDevice
//        }
//    }
//
//
//    // 检查存储权限
//    private fun checkPermission(): Boolean {
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            // Android 13+：分别检查图片和视频权限
//            ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.READ_MEDIA_IMAGES
//            ) == PackageManager.PERMISSION_GRANTED
//                    && ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.READ_MEDIA_VIDEO
//            ) == PackageManager.PERMISSION_GRANTED
//        } else {
//            // Android 12及以下：读取外部存储权限
//            ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.READ_EXTERNAL_STORAGE
//            ) == PackageManager.PERMISSION_GRANTED
//        }
//    }
//
////    // 申请权限
//    private fun requestPermission() {
//        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
//        } else {
//            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
//        }
//        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION)
//    }
//    // 补充：将裁剪后的图片写入媒体库（确保能被查询到）
////    private fun saveCroppedImageToMediaStore(croppedUri: Uri) {
////        contentResolver.openInputStream(croppedUri)?.use { inputStream ->
////            val contentValues = android.content.ContentValues().apply {
////                put(MediaStore.Images.Media.DISPLAY_NAME, "crop_${System.currentTimeMillis()}.jpg")
////                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
////                put(MediaStore.Images.Media.SIZE, inputStream.available().toLong())
////            }
////            val newUri =
////                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
////            newUri?.let {
////                contentResolver.openOutputStream(it)?.use { outputStream ->
////                    inputStream.copyTo(outputStream)
////                }
////            }
////        }
////    }
//    private fun saveCroppedImageToMediaStore(croppedUri: Uri) {
//        lifecycleScope.launch(Dispatchers.IO) {
//            contentResolver.openInputStream(croppedUri)?.use { inputStream ->
//                val contentValues = ContentValues().apply {
//                    put(MediaStore.Images.Media.DISPLAY_NAME, "crop_${System.currentTimeMillis()}.jpg")
//                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
//                    put(MediaStore.Images.Media.SIZE, inputStream.available().toLong())
//                    // Android 10+ 必须指定RELATIVE_PATH
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                        put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CropImages")
//                    }
//                }
//
//                // 插入媒体库并获取新Uri
//                val newUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
//                newUri?.let {
//                    contentResolver.openOutputStream(it)?.use { outputStream ->
//                        inputStream.copyTo(outputStream)
//                    }
//                    // 强制通知系统扫描新文件（关键：确保媒体库立即更新）
//                    sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, newUri))
//                }
//            }
//        }
//    }
//
//}

//// 1. 定义裁剪请求码
//private const val REQUEST_CROP = 101
//class AlbumActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityAlbumBinding
//    private val REQUEST_PERMISSION = 100
//    private val mediaList = mutableListOf<PhotoItem>()
//    private var selectedUri: Uri? = null
//    companion object {
//        const val REQUEST_CROP = 101 // 定义静态请求码
//    }
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityAlbumBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // 关键：先设置空Adapter，避免RecyclerView跳过布局
//        binding.rvPhotos.adapter = PhotoAdapter(
//            mediaList,
//            activity = this@AlbumActivity,
//            onImageSelected = { uri -> // 补充选中回调逻辑
//                selectedUri = uri
//                binding.btnEdit.visibility = View.VISIBLE })
//        binding.rvPhotos.layoutManager = GridLayoutManager(this, 3) // 手动设置网格布局（替代XML）
//
//        // 检查权限，无则申请
//        if (checkPermission()) {
//            loadMediaFromDevice()
//        } else {
//            requestPermission()
//        }
//        // 2. 初始化Adapter时使用该变量
//        val adapter = PhotoAdapter(mediaList, activity = this) { uri ->
//            selectedUri = uri
//            binding.btnEdit.visibility = View.VISIBLE
//        }
//        binding.rvPhotos.adapter = adapter
//
//        // 3. 编辑按钮点击事件中使用
//        binding.btnEdit.setOnClickListener {
//            selectedUri?.let { uri ->
//                startCrop(uri)
//            }
//        }
//    }
//    // 启动裁剪的方法
//    private fun startCrop(sourceUri: Uri) {
//        val destFile = File(cacheDir, "crop_${System.currentTimeMillis()}.jpg")
//        val destUri = Uri.fromFile(destFile)
//
//        UCrop.of(sourceUri, destUri)
//            .withAspectRatio(1f, 1f)
//            .withMaxResultSize(1920, 1920)
//            .start(this, REQUEST_CROP)
//    }
//    // 检查存储权限
//    private fun checkPermission(): Boolean {
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            // Android 13+：分别检查图片和视频权限
//            ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.READ_MEDIA_IMAGES
//            ) == PackageManager.PERMISSION_GRANTED
//                    && ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.READ_MEDIA_VIDEO
//            ) == PackageManager.PERMISSION_GRANTED
//        } else {
//            // Android 12及以下：读取外部存储权限
//            ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.READ_EXTERNAL_STORAGE
//            ) == PackageManager.PERMISSION_GRANTED
//        }
//    }
//
//    // 申请权限
//    private fun requestPermission() {
//        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
//        } else {
//            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
//        }
//        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION)
//    }
//
//    // 权限回调
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<out String>,
//        grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == REQUEST_PERMISSION && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
//            loadMediaFromDevice()
//        }
//    }
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == REQUEST_CROP && resultCode == RESULT_OK) {
//            // 获取裁剪后的图片Uri
//            val croppedUri = UCrop.getOutput(data!!) ?: return
//            // 更新列表（将裁剪后的Uri替换原图片）
//            updateMediaListWithCroppedUri(croppedUri)
//            // 刷新Adapter
//            binding.rvPhotos.adapter?.notifyDataSetChanged()
//            Toast.makeText(this, "裁剪成功", Toast.LENGTH_SHORT).show()
//        } else if (resultCode == UCrop.RESULT_ERROR) {
//            // 裁剪失败
//            val error = UCrop.getError(data!!)
//            Toast.makeText(this, "裁剪失败：${error?.message}", Toast.LENGTH_SHORT).show()
//        }
//    }
//    //更新媒体列表
//    private fun updateMediaListWithCroppedUri(croppedUri: Uri) {
//        // 示例：将裁剪后的图片添加到列表开头
//        mediaList.add(0, PhotoItem(
//            id = System.currentTimeMillis(),
//            uri = croppedUri.toString(),
//            isVideo = false,
//            thumbnailUri = null
//        ))
//    }
//
//
//
//    // 异步拉取设备图片和视频
//    private fun loadMediaFromDevice() {
//        lifecycleScope.launch(Dispatchers.IO) {
//            // 步骤1：先添加App内部的drawable图片（恢复之前的测试图片）
//            val internalPhotos = listOf(
//                PhotoItem(1, "drawable://${R.drawable.photo1}", isVideo = false, null),
//                PhotoItem(2, "drawable://${R.drawable.photo2}", isVideo = false, null),
//                PhotoItem(3, "drawable://${R.drawable.photo3}", isVideo = false, null)
//            )
//            mediaList.addAll(internalPhotos)
//
//            // 步骤2：再查询设备媒体库图片（保留之前的查询逻辑）
//            val projection = arrayOf(MediaStore.Images.Media._ID)
//            contentResolver.query(
//                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//                projection,
//                null,
//                null,
//                "${MediaStore.Images.Media.DATE_ADDED} DESC"
//            )?.use { cursor ->
//                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
//                while (cursor.moveToNext()) {
//                    val id = cursor.getLong(idColumn)
//                    val mediaUri = Uri.withAppendedPath(
//                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//                        id.toString()
//                    ).toString()
//                    mediaList.add(PhotoItem(id + 3, mediaUri, isVideo = false, null))
//                }
//            }
//
//            // 数据加载完成后，替换Adapter的数据源并刷新
//            withContext(Dispatchers.Main) {
//                Log.d("AlbumDebug", "最终加载的媒体数量：${mediaList.size}")
//                val currentActivity = this@AlbumActivity
//                val newAdapter = PhotoAdapter(
//                    mediaList,
//                    activity = currentActivity,
//                    onImageSelected = { uri -> // 补充选中回调
//                        selectedUri = uri
//                        binding.btnEdit.visibility = View.VISIBLE
//                    }
//                )
//                binding.rvPhotos.adapter = newAdapter
//                newAdapter.notifyDataSetChanged() // 强制刷新
//                if (mediaList.isEmpty()) {
//                    Toast.makeText(this@AlbumActivity, "暂无图片数据", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//}