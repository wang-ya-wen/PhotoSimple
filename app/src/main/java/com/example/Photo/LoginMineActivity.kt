package com.example.Photo

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.Photo.databinding.ActivityLoginMineBinding

class LoginMineActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginMineBinding
    private lateinit var sp: SharedPreferences
    private val FIXED_PHONE = "13418860476"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 绑定布局
        binding = ActivityLoginMineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化SharedPreferences
        sp = getSharedPreferences("user_info", MODE_PRIVATE)

        // 渲染用户信息
        renderUserInfo()

        // 退出登录按钮点击事件
        binding.btnLogout.setOnClickListener {
            showLogoutConfirmDialog()
        }

        // 初始化底部导航栏：和修图/模板页逻辑完全一致
        initBottomNav()
    }

    /**
     * 渲染用户信息
     */
    private fun renderUserInfo() {
        val nickname = sp.getString("nickname", "用户1341886")
        binding.tvNickname.text = nickname
        binding.tvPhone.text = FIXED_PHONE
        // 初始化数据统计
        binding.tvFollow.text = sp.getInt("follow", 0).toString()
        binding.tvFans.text = sp.getInt("fans", 0).toString()
        binding.tvWorks.text = sp.getInt("usage", 0).toString()
    }

    /**
     * 初始化底部导航栏：和修图/模板页的跳转逻辑统一
     */
    private fun initBottomNav() {
        // 默认选中“我的”选项，和其他页面的选中逻辑对应
        binding.bottomNav.selectedItemId = R.id.nav_mine

        // 导航栏点击事件：和修图/模板页完全一致的跳转
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_edit -> {
                    // 跳转到修图页面（和修图页的自身跳转逻辑一致）
                    startActivity(Intent(this, MainActivity::class.java))
                    // 若修图页是单例，可加flag：intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    finish() // 关闭当前“我的”页面，避免返回栈冗余
                    return@setOnItemSelectedListener true
                }
                R.id.nav_template -> {
                    // 跳转到模板页面（和模板页的自身跳转逻辑一致）
                    startActivity(Intent(this, templateActivity::class.java))
                    finish()
                    return@setOnItemSelectedListener true
                }
                R.id.nav_mine -> {
                    // 已经在“我的”页面，不做处理
                    return@setOnItemSelectedListener true
                }
                else -> {
                    return@setOnItemSelectedListener false
                }
            }
        }
    }

    /**
     * 退出登录确认弹窗
     */
    private fun showLogoutConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("确认退出")
            .setMessage("确定要退出当前账号吗？")
            .setPositiveButton("退出") { _, _ ->
                // 清除登录状态
                sp.edit()
                    .putBoolean("is_login", false)
                    .remove("nickname")
                    .remove("follow")
                    .remove("fans")
                    .remove("usage")
                    .apply()
                // 跳回未登录的个人中心
                startActivity(Intent(this, MineContainerActivity::class.java))
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}