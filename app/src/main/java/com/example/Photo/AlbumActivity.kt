package com.example.Photo
import PhotoItem
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.Photo.adapter.PhotoAdapter
import com.example.Photo.databinding.ActivityAlbumBinding
import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

//author wangyawen 2025-11-21
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
                        PhotoItem(System.currentTimeMillis(), "drawable://${R.drawable.photo1}", false, null, true),
                        PhotoItem(System.currentTimeMillis() + 1, "drawable://${R.drawable.photo2}", false, null, true),
                        PhotoItem(System.currentTimeMillis() + 2, "drawable://${R.drawable.photo3}", false, null, true)
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
                    // 新增：空数据判断
                    if (mediaList.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.rvPhotos.visibility = View.GONE
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                        binding.rvPhotos.visibility = View.VISIBLE
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
            try {
                contentResolver.openInputStream(croppedUri)?.use { inputStream ->
                    // 计算文件大小（避免available()返回0）
                    val fileSize = inputStream.readBytes().size.toLong()
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "crop_${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.SIZE, fileSize)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/CropImages")
                            put(MediaStore.Images.Media.IS_PENDING, 1) // 标记为待处理
                        }
                    }

                    val newUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    newUri?.let {
                        contentResolver.openOutputStream(it)?.use { outputStream ->
                            inputStream.reset() // 重置输入流（因为之前readBytes()已读取）
                            inputStream.copyTo(outputStream)
                        }
                        // 取消待处理标记（Android 10+）
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            contentResolver.update(newUri, contentValues, null, null)
                        }
                        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, newUri))
                        Log.d(TAG, "裁剪图片保存到媒体库成功：$newUri")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存裁剪图片失败：${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AlbumActivity, "保存裁剪图片失败", Toast.LENGTH_SHORT).show()
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