package com.example.Photo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class GradientCropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bitmap: Bitmap? = null
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        // 加载示例图片（可替换成你的图片）
        bitmap = BitmapFactory.decodeResource(resources, R.drawable.photo1)
        // 关闭硬件加速（避免渐变效果异常）
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 把渐变方向从“左右”改成“上下”
        gradientPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(), // 从上到下的渐变
            Color.argb(255, 255, 255, 255), // 顶部不透明
            Color.argb(0, 255, 255, 255),   // 底部透明
            Shader.TileMode.CLAMP
        )
        gradientPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let {
            // 1. 绘制原图
            canvas.drawBitmap(it, null, Rect(0, 0, width, height), paint)
            // 2. 绘制渐变遮罩（实现“从左到右逐渐消失”的效果）
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)
        }
    }
}