//package com.example.Photo
//
//import android.content.Intent
//import android.content.SharedPreferences
//import android.os.Bundle
//import android.text.Editable
//import android.text.TextWatcher
//import androidx.appcompat.app.AppCompatActivity
//import com.example.Photo.databinding.ActivityLoginBinding
//
//class twoLoginActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityLoginBinding // 数据绑定类
//    private lateinit var sp: SharedPreferences
//    private val FIXED_PHONE = "13800138000"
//    private val FIXED_CODE = "123456"
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        // 初始化数据绑定（必须）
//        binding = ActivityLoginBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        sp = getSharedPreferences("user_info", MODE_PRIVATE)
//        initInputListener()
//        initButtonClick()
//        updateButtonState()
//        initBottomNav() // 初始化底部导航栏
//
//        // 正确引用：etPhone（布局ID无下划线，直接引用）
//        binding.etPhone.setText(FIXED_PHONE)
//    }
//
//    /**
//     * 初始化输入监听：匹配布局中的ID
//     */
//    private fun initInputListener() {
//        // 手机号输入框：binding.etPhone（布局ID=etPhone，正确）
//        binding.etPhone.addTextChangedListener(object : TextWatcher {
//            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//                updateButtonState()
//            }
//            override fun afterTextChanged(s: Editable?) {}
//        })
//
//        // 验证码输入框：binding.etCode（布局ID=etCode，正确）
//        binding.etCode.addTextChangedListener(object : TextWatcher {
//            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//                updateButtonState()
//            }
//            override fun afterTextChanged(s: Editable?) {}
//        })
//
//        // 协议勾选框：binding.cbAgree（布局ID=cbAgree，正确）
//        binding.cbAgree.setOnCheckedChangeListener { _, _ ->
//            updateButtonState()
//        }
//    }
//
//    /**
//     * 更新按钮状态：匹配布局中的ID
//     */
//    private fun updateButtonState() {
//        val phone = binding.etPhone.text.toString().trim()
//        val code = binding.etCode.text.toString().trim()
//        val isAgree = binding.cbAgree.isChecked
//
//        // 获取验证码按钮：binding.btnGetCode（布局ID=btnGetCode，正确）
//        binding.btnGetCode.isEnabled = phone == FIXED_PHONE
//        binding.btnGetCode.alpha = if (binding.btnGetCode.isEnabled) 1f else 0.5f
//
//        // 登录按钮：binding.btnLogin（布局ID=btnLogin，正确）
//        binding.btnLogin.isEnabled = (phone == FIXED_PHONE && code == FIXED_CODE && isAgree)
//        binding.btnLogin.alpha = if (binding.btnLogin.isEnabled) 1f else 0.5f
//    }
//
//    /**
//     * 初始化按钮点击事件：匹配布局中的ID
//     */
//    private fun initButtonClick() {
//        // 获取验证码按钮
//        binding.btnGetCode.setOnClickListener {
//            binding.etCode.setText(FIXED_CODE)
//        }
//
//        // 登录按钮
//        binding.btnLogin.setOnClickListener {
//            sp.edit()
//                .putBoolean("is_login", true)
//                .putString("nickname", "测试用户")
//                .putInt("follow", 10)
//                .putInt("fans", 20)
//                .putInt("usage", 50)
//                .apply()
//            finish() // 关闭登录页，返回我的页面
//        }
//
//        // 关闭按钮：binding.ivClose（布局ID=iv_close，下划线转驼峰，正确）
//        binding.ivClose.setOnClickListener {
//            finish()
//        }
//    }
//
//    /**
//     * 初始化底部导航栏：布局ID是bottom_nav → 代码中是binding.bottomNav（驼峰转换）
//     */
//    private fun initBottomNav() {
//        // 底部导航栏：binding.bottomNav（布局ID=bottom_nav，正确）
//        binding.bottomNav.setOnItemSelectedListener { item ->
//            when (item.itemId) {
//                R.id.nav_edit -> {
//                    // 跳转到首页
//                    startActivity(Intent(this, MainActivity::class.java))
//                    finish()
//                    true
//                }
//                R.id.nav_mine -> {
//                    // 跳转到我的页面
//                    startActivity(Intent(this, MineActivity::class.java))
//                    finish()
//                    true
//                }
//                R.id.nav_template -> {
//                    // 跳转到模板页：修复类名（大驼峰），确保TemplateActivity已创建
//                    startActivity(Intent(this, templateActivity::class.java))
//                    finish()
//                    true
//                }
//                else -> false
//            }
//        }
//        // 设置“我的”为选中状态
//        binding.bottomNav.selectedItemId = R.id.nav_mine
//    }
//}
////package com.example.Photo
////
////import android.content.Intent
////import android.content.SharedPreferences
////import android.os.Bundle
////import android.os.CountDownTimer
////import android.text.Editable
////import android.text.TextWatcher
////import android.widget.Toast
////import androidx.appcompat.app.AppCompatActivity
////import com.example.Photo.databinding.ActivityLoginBinding
////
////class LoginActivity1 : AppCompatActivity() {
////    private lateinit var binding: ActivityLoginBinding
////    private var countDownTimer: CountDownTimer? = null
////    private lateinit var sp: SharedPreferences
////
////    // 固定的手机号和验证码（核心：只有输入这两个值才允许登录）
////    private val FIXED_PHONE = "19137539336"
////    private val FIXED_CODE = "123456"
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        binding = ActivityLoginBinding.inflate(layoutInflater)
////        setContentView(binding.root)
////
////        sp = getSharedPreferences("user_info", MODE_PRIVATE)
////
////        // 监听输入，更新按钮状态
////        binding.etPhone.addTextChangedListener(textWatcher)
////        binding.etCode.addTextChangedListener(textWatcher)
////        binding.cbAgree.setOnCheckedChangeListener { _, _ -> updateButtonState() }
////
////        // 发送验证码按钮点击
////        binding.btnGetCode.setOnClickListener {
////            val phone = binding.etPhone.text.toString().trim()
////            if (phone == FIXED_PHONE) {
////                // 只有输入固定手机号，才显示“验证码已发送”
////                Toast.makeText(this, "验证码已发送：$FIXED_CODE", Toast.LENGTH_LONG).show()
////                startCountDown()
////            } else {
////                Toast.makeText(this, "请输入正确的手机号", Toast.LENGTH_SHORT).show()
////            }
////        }
////
////        // 登录按钮点击
////        binding.btnLogin.setOnClickListener {
////            val phone = binding.etPhone.text.toString().trim()
////            val code = binding.etCode.text.toString().trim()
////
////            // 校验是否为固定的手机号和验证码
////            if (phone == FIXED_PHONE && code == FIXED_CODE && binding.cbAgree.isChecked) {
////                // 登录成功：保存用户信息到SharedPreferences
////                saveUserInfo()
////                // 跳转到登录后的页面（如个人中心）
////                val intent = Intent(this, LoginMineActivity::class.java)
////                startActivity(intent)
////                finish() // 关闭登录页，防止返回
////            } else {
////                Toast.makeText(this, "手机号或验证码错误", Toast.LENGTH_SHORT).show()
////            }
////        }
////    }
////
////    // 保存预设的用户信息到本地
////    private fun saveUserInfo() {
////        sp.edit()
////            .putBoolean("is_login", true)
////            .putString("user_id", "4500739072144") // 预设用户ID
////            .putString("avatar", "drawable/avatar_default") // 预设头像（替换为你的资源名）
////            .putString("nickname", "用户 4500739072144") // 预设昵称
////            .putInt("follow", 0) // 预设关注数
////            .putInt("fans", 0) // 预设粉丝数
////            .putInt("usage", 0) // 预设使用量
////            .apply()
////    }
////
////    // 倒计时逻辑（保持不变，仅做交互效果）
////    private fun startCountDown() {
////        countDownTimer?.cancel()
////        countDownTimer = object : CountDownTimer(60000, 1000) {
////            override fun onTick(millisUntilFinished: Long) {
////                binding.btnGetCode.text = "${millisUntilFinished / 1000}秒后重新发送"
////                binding.btnGetCode.isEnabled = false
////            }
////
////            override fun onFinish() {
////                binding.btnGetCode.text = "发送验证码"
////                binding.btnGetCode.isEnabled = true
////            }
////        }.start()
////    }
////
////    // 更新按钮状态（只有输入正确手机号+非空验证码+勾选协议，登录按钮才可用）
////    private fun updateButtonState() {
////        val phoneValid = binding.etPhone.text.toString().trim() == FIXED_PHONE
////        val codeValid = binding.etCode.text.toString().trim().isNotEmpty()
////        binding.btnLogin.isEnabled = phoneValid && codeValid && binding.cbAgree.isChecked
////        binding.btnGetCode.isEnabled = phoneValid && binding.btnGetCode.text == "发送验证码"
////    }
////
////    // 文本输入监听
////    private val textWatcher = object : TextWatcher {
////        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
////        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
////        override fun afterTextChanged(s: Editable?) {
////            updateButtonState()
////        }
////    }
////
////    override fun onDestroy() {
////        super.onDestroy()
////        countDownTimer?.cancel()
////    }
////}
//
////package com.example.Photo
////
////
////import android.content.SharedPreferences
////import android.os.Bundle
////import android.os.CountDownTimer
////import android.text.Editable
////import android.text.TextWatcher
////import android.util.Patterns
////import android.view.View
////import android.widget.Toast
////import androidx.appcompat.app.AppCompatActivity
////import com.example.Photo.databinding.ActivityLoginBinding
////
////class com.example.Photo.LoginActivity : AppCompatActivity() {
////    private lateinit var binding: ActivityLoginBinding
////    // 倒计时器
////    private var countDownTimer: CountDownTimer? = null
////    // 模拟的验证码（实际项目从后端获取）
////    private var mockVerifyCode: String = ""
////    // SharedPreferences：存储登录状态
////    private lateinit var sp: SharedPreferences
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        binding = ActivityLoginBinding.inflate(layoutInflater)
////        setContentView(binding.root)
////
////        // 初始化SharedPreferences
////        sp = getSharedPreferences("user_info", MODE_PRIVATE)
////
////        // 手机号输入框监听：实时校验格式
////        binding.etPhone.addTextChangedListener(object : TextWatcher {
////            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
////            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
////            override fun afterTextChanged(s: Editable?) {
////                updateButtonState()
////            }
////        })
////
////        // 验证码输入框监听
////        binding.etCode.addTextChangedListener(object : TextWatcher {
////            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
////            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
////            override fun afterTextChanged(s: Editable?) {
////                updateButtonState()
////            }
////        })
////
////        // 发送验证码按钮点击
////        binding.btnGetCode.setOnClickListener {
////            val phone = binding.etPhone.text.toString().trim()
////            if (isPhoneValid(phone)) {
////                sendVerifyCode(phone) // 模拟发送验证码
////                startCountDown() // 启动倒计时
////            } else {
////                Toast.makeText(this, "请输入正确的手机号", Toast.LENGTH_SHORT).show()
////            }
////        }
////
////        // 登录按钮点击
////        binding.btnLogin.setOnClickListener {
////            val phone = binding.etPhone.text.toString().trim()
////            val code = binding.etCode.text.toString().trim()
////            if (checkLoginParams(phone, code)) {
////                verifyCode(phone, code) // 模拟验证验证码
////            }
////        }
////    }
////
////    /**
////     * 校验手机号格式
////     */
////    private fun isPhoneValid(phone: String): Boolean {
////        return phone.length == 11 && Patterns.PHONE.matcher(phone).matches()
////    }
////
////    /**
////     * 更新按钮状态（发送验证码/登录）
////     */
////    private fun updateButtonState() {
////        val phone = binding.etPhone.text.toString().trim()
////        val code = binding.etCode.text.toString().trim()
////
////        // 发送验证码按钮：仅当手机号合法且未倒计时时可点击
////        binding.btnGetCode.isEnabled = isPhoneValid(phone) && binding.btnGetCode.text == "发送验证码"
////
////        // 登录按钮：手机号合法 + 验证码不为空 + 勾选协议
////        binding.btnLogin.isEnabled = isPhoneValid(phone) && code.isNotEmpty() && binding.cbAgree.isChecked
////    }
////
////    /**
////     * 模拟发送验证码（实际项目替换为后端接口调用）
////     */
////    private fun sendVerifyCode(phone: String) {
////        // 模拟生成6位随机验证码
////        mockVerifyCode = (100000..999999).random().toString()
////        Toast.makeText(this, "验证码已发送：$mockVerifyCode（模拟）", Toast.LENGTH_LONG).show()
////        // 实际项目：调用Retrofit/OkHttp发送POST请求到后端短信接口
////        // 示例：apiService.sendVerifyCode(phone)
////    }
////
////    /**
////     * 启动60秒倒计时
////     */
////    private fun startCountDown() {
////        countDownTimer = object : CountDownTimer(60000, 1000) {
////            override fun onTick(millisUntilFinished: Long) {
////                binding.btnGetCode.text = "${millisUntilFinished / 1000}秒后重新发送"
////                binding.btnGetCode.isEnabled = false
////            }
////
////            override fun onFinish() {
////                binding.btnGetCode.text = "发送验证码"
////                binding.btnGetCode.isEnabled = isPhoneValid(binding.etPhone.text.toString().trim())
////            }
////        }.start()
////    }
////
////    /**
////     * 校验登录参数
////     */
////    private fun checkLoginParams(phone: String, code: String): Boolean {
////        if (!isPhoneValid(phone)) {
////            Toast.makeText(this, "请输入正确的手机号", Toast.LENGTH_SHORT).show()
////            return false
////        }
////        if (code.isEmpty()) {
////            Toast.makeText(this, "请输入验证码", Toast.LENGTH_SHORT).show()
////            return false
////        }
////        if (!binding.cbAgree.isChecked) {
////            Toast.makeText(this, "请同意用户协议和隐私政策", Toast.LENGTH_SHORT).show()
////            return false
////        }
////        return true
////    }
////
////    /**
////     * 模拟验证验证码（实际项目替换为后端接口调用）
////     */
////    private fun verifyCode(phone: String, code: String) {
////        // 模拟验证：输入的验证码与生成的一致则登录成功
////        if (code == mockVerifyCode) {
////            Toast.makeText(this, "登录成功！", Toast.LENGTH_SHORT).show()
////            // 存储登录状态和手机号
////            sp.edit()
////                .putBoolean("is_login", true)
////                .putString("login_phone", phone)
////                .apply()
////            // 登录成功后返回MineActivity
////            setResult(RESULT_OK)
////            finish()
////        } else {
////            Toast.makeText(this, "验证码错误，请重新输入", Toast.LENGTH_SHORT).show()
////        }
////    }
////
////    /**
////     * 销毁时取消倒计时
////     */
////    override fun onDestroy() {
////        super.onDestroy()
////        countDownTimer?.cancel()
////    }
////}
//////简单的实现逻辑
//////class com.example.Photo.LoginActivity : AppCompatActivity() {
//////    private lateinit var binding: ActivityLoginBinding
//////    private var countDownTimer: CountDownTimer? = null
//////
//////    override fun onCreate(savedInstanceState: Bundle?) {
//////        super.onCreate(savedInstanceState)
//////        binding = ActivityLoginBinding.inflate(layoutInflater)
//////        setContentView(binding.root)
//////
//////        // 关闭按钮
//////        binding.ivClose.setOnClickListener { finish() }
//////
//////        // 手机号输入监听（输入后启用“获取验证码”）
//////        binding.etPhone.addTextChangedListener(object : TextWatcher {
//////            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//////            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//////                binding.btnGetCode.isEnabled = s?.length == 11
//////            }
//////            override fun afterTextChanged(s: Editable?) {}
//////        })
//////
//////        // 验证码输入监听+协议勾选（满足后启用“登录”）
//////        val enableLogin = {
//////            val phoneValid = binding.etPhone.text.length == 11
//////            val codeValid = binding.etCode.text.length >= 4
//////            val agree = binding.cbAgree.isChecked
//////            binding.btnLogin.isEnabled = phoneValid && codeValid && agree
//////        }
//////        binding.etCode.addTextChangedListener(object : TextWatcher {
//////            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//////            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { enableLogin() }
//////            override fun afterTextChanged(s: Editable?) {}
//////        })
//////        binding.cbAgree.setOnCheckedChangeListener { _, _ -> enableLogin() }
//////
//////        // 获取验证码
//////        binding.btnGetCode.setOnClickListener {
//////            val phone = binding.etPhone.text.toString()
//////            Toast.makeText(this, "已向$phone 发送验证码", Toast.LENGTH_SHORT).show()
//////            // 倒计时60秒
//////            countDownTimer = object : CountDownTimer(60000, 1000) {
//////                override fun onTick(millisUntilFinished: Long) {
//////                    binding.btnGetCode.text = "${millisUntilFinished / 1000}秒后重新获取"
//////                    binding.btnGetCode.isEnabled = false
//////                }
//////                override fun onFinish() {
//////                    binding.btnGetCode.text = "获取验证码"
//////                    binding.btnGetCode.isEnabled = true
//////                }
//////            }.start()
//////        }
//////
//////        // 登录按钮
//////        binding.btnLogin.setOnClickListener {
//////            val phone = binding.etPhone.text.toString()
//////            val code = binding.etCode.text.toString()
//////            // 实际项目中替换为接口请求
//////            Toast.makeText(this, "手机号$phone 登录成功", Toast.LENGTH_SHORT).show()
//////            // 登录成功后返回“我的”页面
//////            setResult(RESULT_OK)
//////            finish()
//////        }
//////    }
//////
//////    override fun onDestroy() {
//////        super.onDestroy()
//////        countDownTimer?.cancel()
//////    }
//////}