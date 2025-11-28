# PhotoSimple - 简易图片处理应用

## 项目简介
PhotoSimple 是一个轻量级图片处理Android应用，提供图片浏览、编辑、模板应用等功能，界面简洁易用。

## 功能特点
- 图片轮播展示
- 图片导入与预览
- 底部导航栏快速切换（编辑/模板/我的）
- 图片的编辑功能(删除、按比例裁剪、旋转、平移、添加水印、基础滤镜)
- 模板浏览与应用

## 技术栈
- 语言：Kotlin
- 架构：MVVM（基于ViewBinding）
- 依赖库：
  - AndroidX组件（AppCompat, Activity, ConstraintLayout等）
  - Material Design（底部导航栏）
  - Glide/Coil（图片加载）
  - ViewPager2（轮播图）
  - RecyclerView（列表展示）
  - UCrop（图片裁剪）
  - CameraX（相机功能）

## 项目结构
```
PhotoSimple/
├── app/                  # 主应用模块
│   ├── src/main/
│   │   ├── java/com/example/Photo/  # 业务代码
│   │   ├── res/                     # 资源文件
│   │   └── AndroidManifest.xml       # 清单文件
├── gradle/               # Gradle配置
└── 配置文件              # 项目配置
```

## 主要页面
1. **MainActivity**：首页，包含轮播图、推荐图片和功能入口
2. **templateActivity**：模板页面，展示可用模板
3. **MineActivity**：个人中心，展示用户信息和作品
4. **PhotoPreviewActivity**：图片预览与编辑页面
5. **LoginActivity**：未登录页面
6. **LoginMineActivity**:为登录后的用户界面

## 快速开始
1. 克隆项目到本地
2. 使用Android Studio打开项目
3. 等待Gradle同步完成
4. 连接Android设备或启动模拟器
5. 点击运行按钮

## 权限说明
- 相机权限：用于拍摄照片
- 存储权限：用于读取和保存图片

## 注意事项
- 最低支持Android 7.0（API 24）
- 推荐使用Android Studio Giraffe及以上版本
- 首次运行需授予必要权限以正常使用所有功能
