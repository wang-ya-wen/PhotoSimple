package com.example.Photo

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MineContainerActivity : AppCompatActivity() {
    private lateinit var sp: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化SharedPreferences，获取登录状态
        sp = getSharedPreferences("user_info", MODE_PRIVATE)
        val isLogin = sp.getBoolean("is_login", false)

        // 根据登录状态跳转对应页面
        val targetIntent = if (isLogin) {
            Intent(this, LoginMineActivity::class.java)
        } else {
            Intent(this, MineActivity::class.java)
        }
        startActivity(targetIntent)
        // 跳转后销毁容器Activity，避免返回栈中出现空页面
        finish()
    }
}