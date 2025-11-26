package com.example.Photo

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MineContainerActivity : AppCompatActivity() {
    private val TAG = "MineContainer"
    private lateinit var sp: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初始化SharedPreferences，获取登录状态
        sp = getSharedPreferences("user_info", MODE_PRIVATE)
        val isLogin = sp.getBoolean("is_login", false)

        try {
            // 根据登录状态跳转对应页面
            val targetIntent = if (isLogin) {
                Intent(this, LoginMineActivity::class.java)
            } else {
                Intent(this, MineActivity::class.java)
            }
            // 校验目标页面是否存在（避免启动失败）
            if (targetIntent.resolveActivity(packageManager) != null) {
                startActivity(targetIntent)
                // 跳转后销毁容器Activity（这行保留，容器仅做中转）
                finish()
            } else {
                throw Exception("目标页面不存在，请检查Activity注册")
            }
        } catch (e: Exception) {
            Log.e(TAG, "跳转我的页面失败：${e.message}", e)
            Toast.makeText(this, "我的页面加载失败：${e.message}", Toast.LENGTH_SHORT).show()
            finish() // 捕获异常后销毁容器，返回主页面
        }
    }
}