//package com.example.Photo
//
//import android.os.Bundle
//import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//
//class MainActivity : AppCompatActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.activity_main)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//    }
//}
package com.example.Photo

import BannerAdapter
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.Photo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivityDebug"
    private lateinit var viewPager: ViewPager2
    private lateinit var indicatorContainer: LinearLayout
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    private val bannerImages = listOf(
        R.drawable.rec1,  // 替换为你的图片资源
        R.drawable.rec2,
        R.drawable.rec3
    )
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var pickPhotoLauncher: ActivityResultLauncher<Intent>
    private val handler = Handler(Looper.getMainLooper())
    private var currentPosition = 0
    private val autoScrollDelay = 3000L // 自动轮播间隔（毫秒）
    // 为你推荐的静态图片（从drawable读取）
    private val recommendImages = listOf(
        R.drawable.banner_one,
        R.drawable.banner_two,
        R.drawable.banner_th,
        R.drawable.banner_four
    )
    private lateinit var recommendContainer: LinearLayout
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            currentPosition++
            viewPager.currentItem = currentPosition
            handler.postDelayed(this, autoScrollDelay)
        }
    }
    private lateinit var rvTemplates: androidx.recyclerview.widget.RecyclerView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== onCreate 执行 ===")
        _binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
        bindAllViews()
        //轮播图
        viewPager = findViewById(R.id.viewPager)
        indicatorContainer = findViewById(R.id.indicatorContainer)

        // 初始化适配器
        val adapter = BannerAdapter(bannerImages)
        viewPager.adapter = adapter

        // 初始化指示器
        initIndicators()

        // 设置ViewPager监听
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPosition = position
                // 更新指示器状态
                updateIndicators(position % bannerImages.size)
            }
        })

        // 初始位置设置在中间（方便左右滑动）
        val initialPosition = Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2 % bannerImages.size)
        viewPager.currentItem = initialPosition
        currentPosition = initialPosition


        // 初始化启动器（仅在onCreate中执行一次）
        initLaunchers()
        // 绑定按钮点击事件（仅在onCreate中执行一次，避免重复绑定）
        bindImportButtonClick()

        // 初始化“为你推荐”静态图片
        initRecommendImages()
        rvTemplates.setOnClickListener {
            Log.d(TAG, "模板区域被点击，跳转到templateActivity")
            startActivity(Intent(this, templateActivity::class.java))
        }
        // 绑定底部导航栏事件
        bindBottomNavClick()
        // 相机按钮点击事件
        binding.btnCamera.setOnClickListener {
            checkCameraPermissionAndOpenCamera()
        }

        // 打印控件状态
        logButtonState("onCreate")
        logBottomNavState("onCreate")
    }
    /**
     * 绑定所有控件：集中执行findViewById，避免初始化顺序错误
     */
    private fun bindAllViews() {
        // 轮播图控件
        viewPager = findViewById(R.id.viewPager)
        indicatorContainer = findViewById(R.id.indicatorContainer)
        // 为你推荐控件（关键：这里完成recommendContainer的初始化）
        recommendContainer = findViewById(R.id.recommendContainer)
        rvTemplates = findViewById(R.id.rv_templates)
    }
    // 初始化指示器（底部小点）
    private fun initIndicators() {
        for (i in bannerImages.indices) {
            val indicator = ImageView(this)
            // 设置指示器大小和间距
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(4.dp, 0, 4.dp, 0)
            indicator.layoutParams = params

            // 设置指示器图片（选中和未选中状态）
            indicator.setImageResource(
                if (i == 0) R.drawable.indicator_selected
                else R.drawable.indicator_unselected
            )
            indicatorContainer.addView(indicator)
        }
    }

    // 更新指示器状态
    private fun updateIndicators(selectPosition: Int) {
        for (i in bannerImages.indices) {
            val indicator = indicatorContainer.getChildAt(i) as ImageView
            indicator.setImageResource(
                if (i == selectPosition) R.drawable.indicator_selected
                else R.drawable.indicator_unselected
            )
        }
    }

    // dp转px工具方法
    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()


    // 停止自动轮播
    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoScrollRunnable)
    }
    /**
     * 绑定底部导航栏的点击事件
     */
    private fun bindBottomNavClick() {
        Log.d(TAG, "=== 绑定底部导航栏点击事件 ===")
        Log.d(TAG, "R.id.nav_mine 的真实值：${R.id.nav_mine}")
        Log.d(TAG, "R.id.nav_edit 的真实值：${R.id.nav_edit}")
        Log.d(TAG, "R.id.nav_template 的真实值：${R.id.nav_template}")
        Log.d(TAG, "日志中我的菜单项ID：2131231057")

        binding.bottomNav.setOnItemSelectedListener { menuItem ->
            Log.d(TAG, "点击的菜单项ID：${menuItem.itemId}，标题：${menuItem.title}，是否可用：${menuItem.isEnabled}")
            when (menuItem.itemId) {
                R.id.nav_mine -> {
                    Log.d(TAG, "「我的」按钮被点击！准备跳转到MineActivity")
                    startActivity(Intent(this, MineContainerActivity::class.java))
//                    finish()
                    true
                }
                R.id.nav_edit -> {
                    Log.d(TAG, "「修图/编辑」按钮被点击！当前已在首页，弹出提示")
                    Toast.makeText(this, "已在首页", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_template -> {
                    Log.d(TAG, "「模板」按钮被点击！可在此处添加模板页面跳转逻辑")
                    startActivity(Intent(this, templateActivity::class.java))
//                    finish()
                    true
                }
                else -> {
                    Log.d(TAG, "未匹配的菜单项ID：${menuItem.itemId}，标题：${menuItem.title}")
                    false
                }
            }
        }

        binding.bottomNav.setOnItemReselectedListener {
            Log.d(TAG, "底部导航栏按钮被重复点击：ID=${it.itemId}，标题=${it.title}")
        }
    }

    /**
     * 初始化启动器（仅执行一次）
     */
    private fun initLaunchers() {
        // 权限申请启动器
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Log.d(TAG, "权限请求结果：$isGranted")
            if (isGranted) {
                Log.d(TAG, "权限已授予，显示照片选择弹窗")
                showPhotoChoiceDialog()
            } else {
                Toast.makeText(this, "请授予照片权限才能导入图片", Toast.LENGTH_SHORT).show()
            }
        }

        // 照片选择启动器
        pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, "照片选择结果：resultCode=${result.resultCode}，data=${result.data}")
            if (result.resultCode == RESULT_OK) {
                val photoUri = result.data?.data
                photoUri?.let {
                    Log.d(TAG, "选择照片成功，Uri：$it")
                    val intent = Intent(this, PhotoPreviewActivity::class.java)
                    intent.putExtra("imageUri", it.toString())
                    startActivity(intent)
                } ?: run {
                    Log.d(TAG, "选择照片失败：Uri为空")
                    Toast.makeText(this, "选择照片失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 绑定导入按钮点击事件（仅执行一次）
     */
    private fun bindImportButtonClick() {
        Log.d(TAG, "=== 绑定导入按钮点击事件 ===")
        binding.btnImportPhoto.setOnClickListener { v ->
            // 打印按钮点击的详细信息
            Log.d(TAG, "导入按钮被点击！按钮状态：可点击=${v.isClickable}，可用=${v.isEnabled}")
            // 强制设置按钮为可用状态（防止意外置灰）
            v.isEnabled = true
            v.isClickable = true
            // 检查权限
            checkPhotoPermission()
        }
    }

    /**
     * 检查照片权限（适配Android 13+）
     */
    private fun checkPhotoPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(this, permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "权限检查：$permission，是否已授予：$isGranted")

        if (isGranted) {
            showPhotoChoiceDialog()
        } else {
            Log.d(TAG, "权限未授予，启动权限申请")
            permissionLauncher.launch(permission)
        }
    }

    /**
     * 显示照片选择弹窗
     */
    private fun showPhotoChoiceDialog() {
        Log.d(TAG, "显示照片选择弹窗")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("导入照片")
            .setMessage("请选择照片来源")
            .setPositiveButton("选择单张照片") { _, _ ->
                Log.d(TAG, "用户选择：选择单张照片")
                pickSinglePhoto()
            }
            .setNeutralButton("打开相册列表") { _, _ ->
                Log.d(TAG, "用户选择：打开相册列表")
                startActivity(Intent(this, AlbumActivity::class.java))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 启动系统相册选择单张照片
     */
    private fun pickSinglePhoto() {
        Log.d(TAG, "启动系统相册选择单张照片")
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        // 强制弹出应用选择器
        val chooser = Intent.createChooser(intent, "选择照片")
        pickPhotoLauncher.launch(chooser)
    }

    /**
     * 打印导入按钮状态
     */
    private fun logButtonState(lifecycle: String) {
        val btn = binding.btnImportPhoto
        Log.d(TAG, "[$lifecycle] 导入按钮-是否为空：${btn == null}，可见性：${getVisibilityStr(btn.visibility)}，是否可用：${btn.isEnabled}，是否可点击：${btn.isClickable}")
    }
    /**
     * 检查相机权限并打开相机
     */
    private fun checkCameraPermissionAndOpenCamera() {
        if (CameraPermissionUtil.hasCameraPermission(this)) {
            // 已授予权限：直接打开相机页面
            openCameraActivity()
        } else {
            // 未授予权限：显示自定义权限弹窗
            CameraPermissionUtil.showCameraPermissionDialog(this) {
                // 点击“好”后申请权限
                CameraPermissionUtil.requestCameraPermission(this)
            }
        }
    }

    /**
     * 打开相机页面
     */
    private fun openCameraActivity() {
        val intent = Intent(this, CameraActivity::class.java)
        startActivity(intent)
    }

    /**
     * 权限申请结果回调
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CameraPermissionUtil.CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予：打开相机
                openCameraActivity()
            } else {
                // 权限被拒绝：检查是否勾选了“不再询问”
                if (!shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                    // 用户勾选了“不再询问”：引导去设置页开启
                    CameraPermissionUtil.showPermissionDeniedDialog(this)
                } else {
                    // 用户未勾选“不再询问”：提示需要权限
                    Toast.makeText(this, "请授予相机权限才能使用相机功能", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    // dp转px工具方法
    private fun dp2px(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }
    // 初始化“为你推荐”静态图片
    /**
     * 加载为你推荐的静态图片，代码设置间距
     */
    private fun initRecommendImages() {
        recommendContainer.removeAllViews()
        recommendImages.forEachIndexed { index, resId ->
            val imageView = ImageView(this).apply {
                setImageResource(resId)
                // 设置图片宽高
                val params = LinearLayout.LayoutParams(dp2px(120), dp2px(160))
                // 核心：给除最后一张外的图片设置右间距10dp，实现图片间的间隙
                if (index != recommendImages.size - 1) {
                    params.setMargins(0, 0, dp2px(10), 0) // 右间距10dp，上下左无间距
                }
                layoutParams = params
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            recommendContainer.addView(imageView)
        }
    }
    /**
     * 打印底部导航栏状态
     */
    private fun logBottomNavState(lifecycle: String) {
        val bottomNav = binding.bottomNav
        Log.d(TAG, "[$lifecycle] 底部导航栏-是否为空：${bottomNav == null}，可见性：${getVisibilityStr(bottomNav.visibility)}，菜单项数量：${bottomNav.menu.size()}")
        for (i in 0 until bottomNav.menu.size()) {
            val item = bottomNav.menu.getItem(i)
            Log.d(TAG, "[$lifecycle] 菜单项$i：ID=${item.itemId}，标题=${item.title}，是否可用=${item.isEnabled}")
        }
    }

    /**
     * 转换可见性为字符串
     */
    private fun getVisibilityStr(visibility: Int): String {
        return when (visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            View.GONE -> "GONE"
            else -> "UNKNOWN"
        }
    }

    /**
     * onResume：仅打印状态，不再重复绑定事件
     */
    override fun onResume() {
        super.onResume()
        handler.postDelayed(autoScrollRunnable, autoScrollDelay)
        Log.d(TAG, "=== onResume 执行 ===")
        // 移除重复的事件绑定，仅打印状态
        logButtonState("onResume")
        logBottomNavState("onResume")
    }

    /**
     * onDestroy：释放资源
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== onDestroy 执行 ===")
        _binding = null
    }


}

//class MainActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityMainBinding // 声明视图绑定对象
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        // 用视图绑定加载布局
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        binding.btnEnterAlbum.setOnClickListener {
//            val intent = Intent(this, AlbumActivity::class.java)
//            startActivity(intent)
//        }
//        binding.bottomNav.setOnItemSelectedListener { menuItem ->
//            when (menuItem.itemId) {
//                R.id.nav_mine -> {
//                    startActivity(Intent(this, com.example.Photo.com.example.Photo.MineActivity::class.java))
//                    true
//                }
//                // 其他导航项逻辑
//                else -> true
//            }
//        }
//        // 适配EdgeToEdge（针对当前布局的根控件）
//        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//    }
//}
//容易阻塞
//class MainActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityMainBinding
//    private val REQUEST_PHOTO_PERMISSION = 100
//    private val REQUEST_PICK_PHOTO = 101
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // 导入照片按钮点击事件
//        binding.btnImportPhoto.setOnClickListener {
//            Log.d("MainActivity", "导入按钮点击")
//            checkPhotoPermission()
//        }
//        binding.bottomNav.setOnItemSelectedListener { menuItem ->
//            when (menuItem.itemId) {
//                R.id.nav_mine -> {
//                    startActivity(Intent(this, com.example.Photo.com.example.Photo.MineActivity::class.java))
//                    true
//                }
//               // 其他导航项逻辑
//                else -> true
//            }
//       }
//    }
//
//    // 检查照片权限
//    private fun checkPhotoPermission() {
//        Log.d("MainActivity", "检查权限")
//        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            android.Manifest.permission.READ_MEDIA_IMAGES
//        } else {
//            android.Manifest.permission.READ_EXTERNAL_STORAGE
//        }
//
//        val isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
//        Log.d("MainActivity", "权限授予状态：$isGranted")
//
//        if (isGranted) {
//            showPhotoChoiceDialog()
//        } else {
//            requestPhotoPermission(permission)
//        }
//    }
//
//    // 请求照片权限（带说明）
//    private fun requestPhotoPermission(permission: String) {
//        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
//            AlertDialog.Builder(this)
//                .setTitle("需要访问照片")
//                .setMessage("为了导入图片进行编辑，请允许醒图访问您的相册")
//                .setPositiveButton("去授权") { _, _ ->
//                    ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_PHOTO_PERMISSION)
//                }
//                .setNegativeButton("取消", null)
//                .show()
//        } else {
//            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_PHOTO_PERMISSION)
//        }
//    }
//
//    // 显示选择弹窗
//    private fun showPhotoChoiceDialog() {
//        Log.d("MainActivity", "显示弹窗")
//        AlertDialog.Builder(this)
//            .setTitle("“醒图”想访问您的照片")
//            .setMessage("允许醒图读取您相册存储中的图片，用于导入图片帮助您进行后续查看、编辑处理、头像使用、意见反馈")
//            .setPositiveButton("选择照片...") { _, _ -> pickSinglePhoto() }
//            .setNeutralButton("允许访问所有照片") { _, _ ->
//                startActivity(Intent(this, AlbumActivity::class.java))
//            }
//            .setNegativeButton("不允许") { dialog, _ ->
//                dialog.dismiss()
//                Toast.makeText(this, "您已拒绝访问照片，无法导入图片", Toast.LENGTH_SHORT).show()
//            }
//            .show()
//    }
//
//    // 选择单张照片
//    private fun pickSinglePhoto() {
//        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
//        intent.type = "image/*"
//        startActivityForResult(intent, REQUEST_PICK_PHOTO)
//    }
//
//    // 权限请求结果回调
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<out String>,
//        grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == REQUEST_PHOTO_PERMISSION) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                showPhotoChoiceDialog()
//            } else {
//                Toast.makeText(this, "您已拒绝照片权限，无法导入图片", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    // 照片选择结果回调
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (resultCode == RESULT_OK && requestCode == REQUEST_PICK_PHOTO) {
//            data?.data?.let { uri ->
//                val intent = Intent(this, PhotoPreviewActivity::class.java)
//                intent.putExtra("image_uri", uri.toString())
//                startActivity(intent)
//            }
//        }
//    }
//}