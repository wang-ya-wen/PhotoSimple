//package com.example.Photo
//
//import android.content.Intent
//import android.content.SharedPreferences
//import android.os.Bundle
//import android.os.CountDownTimer
//import android.text.Editable
//import android.text.TextWatcher
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import com.example.Photo.databinding.ActivityLoginBinding
//
//class firstLoginActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityLoginBinding
//    private var countDownTimer: CountDownTimer? = null
//    private lateinit var sp: SharedPreferences
//
//    // 固定的手机号和验证码（核心：只有输入这两个值才允许登录）
//    private val FIXED_PHONE = "13418860476"
//    private val FIXED_CODE = "123456"
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityLoginBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        sp = getSharedPreferences("user_info", MODE_PRIVATE)
//
//        // 监听输入，更新按钮状态
//        binding.etPhone.addTextChangedListener(textWatcher)
//        binding.etCode.addTextChangedListener(textWatcher)
//        binding.cbAgree.setOnCheckedChangeListener { _, _ -> updateButtonState() }
//
//        // 发送验证码按钮点击
//        binding.btnGetCode.setOnClickListener {
//            val phone = binding.etPhone.text.toString().trim()
//            if (phone == FIXED_PHONE) {
//                // 只有输入固定手机号，才显示“验证码已发送”
//                Toast.makeText(this, "验证码已发送：$FIXED_CODE", Toast.LENGTH_LONG).show()
//                startCountDown()
//            } else {
//                Toast.makeText(this, "请输入正确的手机号", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        // 登录按钮点击
//        binding.btnLogin.setOnClickListener {
//            val phone = binding.etPhone.text.toString().trim()
//            val code = binding.etCode.text.toString().trim()
//
//            // 校验是否为固定的手机号和验证码
//            if (phone == FIXED_PHONE && code == FIXED_CODE && binding.cbAgree.isChecked) {
//                // 登录成功：保存用户信息到SharedPreferences
//                saveUserInfo()
//                // 跳转到登录后的页面（如个人中心）
//                val intent = Intent(this, LoginMineActivity::class.java)
//                startActivity(intent)
//                finish() // 关闭登录页，防止返回
//            } else {
//                Toast.makeText(this, "手机号或验证码错误", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    // 保存预设的用户信息到本地
//    private fun saveUserInfo() {
//        sp.edit()
//            .putBoolean("is_login", true)
//            .putString("user_id", "4500739072144") // 预设用户ID
//            .putString("avatar", "drawable/avatar_default") // 预设头像（替换为你的资源名）
//            .putString("nickname", "用户 4500739072144") // 预设昵称
//            .putInt("follow", 0) // 预设关注数
//            .putInt("fans", 0) // 预设粉丝数
//            .putInt("usage", 0) // 预设使用量
//            .apply()
//    }
//
//    // 倒计时逻辑（保持不变，仅做交互效果）
//    private fun startCountDown() {
//        countDownTimer?.cancel()
//        countDownTimer = object : CountDownTimer(60000, 1000) {
//            override fun onTick(millisUntilFinished: Long) {
//                binding.btnGetCode.text = "${millisUntilFinished / 1000}秒后重新发送"
//                binding.btnGetCode.isEnabled = false
//            }
//
//            override fun onFinish() {
//                binding.btnGetCode.text = "发送验证码"
//                binding.btnGetCode.isEnabled = true
//            }
//        }.start()
//    }
//
//    // 更新按钮状态（只有输入正确手机号+非空验证码+勾选协议，登录按钮才可用）
//    private fun updateButtonState() {
//        val phoneValid = binding.etPhone.text.toString().trim() == FIXED_PHONE
//        val codeValid = binding.etCode.text.toString().trim().isNotEmpty()
//        binding.btnLogin.isEnabled = phoneValid && codeValid && binding.cbAgree.isChecked
//        binding.btnGetCode.isEnabled = phoneValid && binding.btnGetCode.text == "发送验证码"
//    }
//
//    // 文本输入监听
//    private val textWatcher = object : TextWatcher {
//        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
//        override fun afterTextChanged(s: Editable?) {
//            updateButtonState()
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        countDownTimer?.cancel()
//    }
//}
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