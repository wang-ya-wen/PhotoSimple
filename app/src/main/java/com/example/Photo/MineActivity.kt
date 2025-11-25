package com.example.Photo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.Photo.databinding.ActivityMineBinding

class MineActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMineBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 点击头像跳转到登录页
        binding.ivAvatar.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // 点击“去创作作品吧”跳转到修图页面
        binding.tvCreateWork.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // 底部导航切换
        initBottomNav()
    }

    private fun initBottomNav() {
        // 设置“我的”为选中状态
        binding.bottomNav.selectedItemId = R.id.nav_mine

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_edit -> {
                    // 跳转到修图页面
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_template -> {
                    // 跳转到模板页面
                    startActivity(Intent(this, templateActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_mine -> {
                    // 当前在“我的”页面，不做处理
                    true
                }
                else -> false
            }
        }
    }
}
//package com.example.Photo
//
//import android.content.Context
//import android.content.Intent
//import android.content.SharedPreferences
//import android.os.Bundle
//import android.view.View
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import com.example.Photo.databinding.ActivityMineBinding
//
//class MineActivity : AppCompatActivity() {
//    private var _binding: ActivityMineBinding? = null
//    private val binding get() = _binding!!
//    private lateinit var sp: SharedPreferences
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        // 初始化ViewBinding和布局
//        _binding = ActivityMineBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // 初始化SharedPreferences
//        sp = getSharedPreferences("user_info", Context.MODE_PRIVATE)
//        // 初始化用户信息和头像显示逻辑
//        initUserInfo()
//
//        // 立即登录按钮点击事件：跳转到登录页
//        binding.tvLogin.setOnClickListener {
//            startActivity(Intent(this, LoginActivity1::class.java))
//        }
//
//        // 新增：退出登录按钮点击事件
//        binding.btnLogout.setOnClickListener {
//            performLogout()
//        }
//    }
//
//    /**
//     * 核心逻辑：根据登录状态控制头像、用户信息、退出按钮的显示/隐藏
//     * 未登录：隐藏头像、用户信息、退出按钮；显示登录提示
//     * 已登录：显示头像、用户信息、退出按钮；隐藏登录提示
//     */
//    private fun initUserInfo() {
//        val isLogin = sp.getBoolean("is_login", false)
//        if (isLogin) {
//            // ========== 已登录状态 ==========
//            // 1. 显示头像，并设置为固定的ban_four图片
//            binding.ivAvatar.visibility = View.VISIBLE
//            binding.ivAvatar.setImageResource(R.drawable.banner_four)
//
//            // 2. 隐藏未登录提示，显示用户信息
//            binding.tvLogin.visibility = View.GONE // 隐藏“立即登录”文字
//            binding.llUserInfo.getChildAt(1).visibility = View.GONE // 隐藏“登录查看更多”提示
//            binding.tvUserNickname.visibility = View.VISIBLE
//            binding.tvUserPhone.visibility = View.VISIBLE
//            // 新增：显示退出登录按钮
//            binding.btnLogout.visibility = View.VISIBLE
//
//            // 3. 读取并显示昵称、手机号（从SP中获取）
//            val nickname = sp.getString("nickname", "修图达人")
//            val phone = sp.getString("login_phone", "13800138000")
//            binding.tvUserNickname.text = nickname
//            binding.tvUserPhone.text = "手机号：$phone"
//        } else {
//            // ========== 未登录状态 ==========
//            // 1. 隐藏头像
//            binding.ivAvatar.visibility = View.GONE
//
//            // 2. 显示未登录提示，隐藏用户信息和退出按钮
//            binding.tvLogin.visibility = View.VISIBLE // 显示“立即登录”文字
//            binding.llUserInfo.getChildAt(1).visibility = View.VISIBLE // 显示“登录查看更多”提示
//            binding.tvUserNickname.visibility = View.GONE
//            binding.tvUserPhone.visibility = View.GONE
//            // 新增：隐藏退出登录按钮
//            binding.btnLogout.visibility = View.GONE
//        }
//    }
//
//    /**
//     * 新增：执行退出登录逻辑
//     */
//    private fun performLogout() {
//        // 1. 清除SharedPreferences中的登录状态和用户信息
//        sp.edit()
//            .remove("is_login") // 移除登录状态
//            .remove("nickname") // 移除昵称
//            .remove("login_phone") // 移除手机号
//            // 若有其他用户信息，也一并移除
//            .apply()
//
//        // 2. 弹出退出成功提示
//        Toast.makeText(this, "退出登录成功", Toast.LENGTH_SHORT).show()
//
//        // 3. 刷新页面，恢复未登录状态
//        initUserInfo()
//    }
//
//    /**
//     * 从登录页返回后，重新刷新用户信息和控件状态
//     */
//    override fun onResume() {
//        super.onResume()
//        initUserInfo()
//    }
//
//    /**
//     * 销毁时释放ViewBinding，避免内存泄漏
//     */
//    override fun onDestroy() {
//        super.onDestroy()
//        _binding = null
//    }
//}
//package com.example.Photo
//
//import android.content.Intent
//import android.content.SharedPreferences
//import android.os.Bundle
//import android.view.View
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import com.example.Photo.databinding.ActivityMineBinding
//
//class com.example.Photo.MineActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityMineBinding
//    private lateinit var sp: SharedPreferences
//    private val LOGIN_REQUEST_CODE = 1002
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityMineBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//        // 初始化SharedPreferences
//        sp = getSharedPreferences("user_info", MODE_PRIVATE)
//
//        // 检查登录状态并更新UI
//        checkLoginState()
//
//        // 点击“立即登录”
//        binding.tvLogin.setOnClickListener {
////            Toast.makeText(this, "跳转至登录页面", Toast.LENGTH_SHORT).show()
//            // 实际项目中可启动登录Activity
//            startActivity(Intent(this, LoginActivity::class.java))
//        }
//
//        // 点击“立即订阅”
//        binding.btnVipSubscribe.setOnClickListener {
//            Toast.makeText(this, "跳转至VIP订阅页面", Toast.LENGTH_SHORT).show()
//        }
//
//        // 点击“创建分身”
//        binding.tvCreateAi.setOnClickListener {
//            Toast.makeText(this, "跳转至AI分身创建页面", Toast.LENGTH_SHORT).show()
//        }
//
//        // 点击“去创作作品吧”
//        binding.tvGoCreate.setOnClickListener {
//            // 跳转至修图首页
//            startActivity(Intent(this, MainActivity::class.java))
//            finish()
//        }
//    }
//        /**
//         * 检查登录状态并更新UI
//         */
//        private fun checkLoginState() {
//            val isLogin = sp.getBoolean("is_login", false)
//            val phone = sp.getString("login_phone", "")
//            if (isLogin && phone != null) {
//                // 已登录：隐藏登录按钮，显示用户手机号
//                binding.tvLogin.visibility = View.GONE
//                binding.tvUserPhone.visibility = View.VISIBLE
//                binding.tvUserPhone.text = "当前登录：$phone"
//                // 可额外添加“退出登录”按钮逻辑
//            } else {
//                // 未登录：显示登录按钮，隐藏用户信息
//                binding.tvLogin.visibility = View.VISIBLE
//                binding.tvUserPhone.visibility = View.GONE
//            }
//        }
//
//        /**
//         * 登录页返回后的回调
//         */
//        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//            super.onActivityResult(requestCode, resultCode, data)
//            if (requestCode == LOGIN_REQUEST_CODE && resultCode == RESULT_OK) {
//                // 登录成功后刷新登录状态
//                checkLoginState()
//            }
//        }
//    }
