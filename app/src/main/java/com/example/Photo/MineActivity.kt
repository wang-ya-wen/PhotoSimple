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
