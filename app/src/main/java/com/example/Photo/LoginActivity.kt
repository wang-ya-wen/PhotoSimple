package com.example.Photo

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.Photo.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var sp: SharedPreferences
    // 固定的手机号和验证码
    private val FIXED_PHONE = "13418860476"
    private val FIXED_CODE = "123456"
    // 倒计时相关参数
    private val COUNTDOWN_TIME = 60000L // 60秒，单位毫秒
    private val COUNTDOWN_INTERVAL = 1000L // 1秒刷新一次
    private var countDownTimer: CountDownTimer? = null // 倒计时器

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化SharedPreferences，用于保存登录状态
        sp = getSharedPreferences("user_info", MODE_PRIVATE)

        // 1. 默认填充固定手机号
        binding.etPhone.setText(FIXED_PHONE)
        // 光标定位到验证码输入框
        binding.etCode.requestFocus()

        // 2. 监听输入和协议勾选，激活登录按钮
        initListener()

        // 3. 关闭按钮点击事件：返回未登录的个人中心
        binding.ivClose.setOnClickListener {
            finish()
        }

        // 4. 登录按钮点击事件：校验信息并跳转
        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        // 5. 核心新增：点击“获取验证码”触发假倒计时
        binding.tvGetCode.setOnClickListener {
            startCountdown()
        }
    }

    /**
     * 启动假的验证码倒计时逻辑
     */
    private fun startCountdown() {
        // 1. 隐藏“获取验证码”，显示倒计时
        binding.tvGetCode.visibility = View.GONE
        binding.tvCountdown.visibility = View.VISIBLE

        // 2. 创建倒计时器
        countDownTimer = object : CountDownTimer(COUNTDOWN_TIME, COUNTDOWN_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {
                // 实时更新倒计时数字
                val remainingTime = millisUntilFinished / 1000
                binding.tvCountdown.text = "${remainingTime}s"
            }

            override fun onFinish() {
                // 倒计时结束：恢复原状态
                binding.tvCountdown.visibility = View.GONE
                binding.tvGetCode.visibility = View.VISIBLE
                binding.tvGetCode.text = "重新获取"
            }
        }

        // 3. 启动倒计时
        countDownTimer?.start()
    }

    /**
     * 监听输入框和协议勾选状态，动态激活登录按钮
     */
    private fun initListener() {
        // 手机号输入监听
        binding.etPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkLoginButtonState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 验证码输入监听
        binding.etCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkLoginButtonState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 协议勾选监听
        binding.cbAgree.setOnCheckedChangeListener { _, isChecked ->
            checkLoginButtonState()
        }
    }

    /**
     * 检查登录按钮激活状态：手机号+验证码正确 + 协议勾选
     */
    private fun checkLoginButtonState() {
        val inputPhone = binding.etPhone.text.toString().trim()
        val inputCode = binding.etCode.text.toString().trim()
        val isAgree = binding.cbAgree.isChecked

        // 激活条件：手机号等于固定值 + 验证码等于固定值 + 勾选协议
        binding.btnLogin.isEnabled = (inputPhone == FIXED_PHONE) && (inputCode == FIXED_CODE) && isAgree
    }

    /**
     * 执行登录逻辑：保存状态并跳转到登录后的个人中心
     */
    private fun performLogin() {
        // 1. 保存登录状态和用户信息到SharedPreferences
        sp.edit()
            .putBoolean("is_login", true) // 标记已登录
            .putString("phone", FIXED_PHONE) // 保存手机号
            .putString("nickname", "用户1341886") // 自定义昵称
            .putInt("follow", 0) // 初始化关注数
            .putInt("fans", 0) // 初始化粉丝数
            .putInt("usage", 0) // 初始化使用次数
            .apply()

        // 2. 跳转到登录后的个人中心页面
        val intent = Intent(this, MineContainerActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)

        // 3. 关闭登录页
        finish()
    }

    /**
     * 页面销毁时取消倒计时，避免内存泄漏
     */
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel() // 取消倒计时
    }
}