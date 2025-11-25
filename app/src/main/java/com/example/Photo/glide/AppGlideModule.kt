package com.example.Photo.glide

import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule

// 关键：添加@GlideModule注解，解决编译期模块缺失问题
@GlideModule
class MyAppGlideModule : AppGlideModule() {
    // 无需重写方法，仅作为Glide的编译期入口
}