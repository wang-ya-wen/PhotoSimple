//package com.example.Photo
//
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import androidx.recyclerview.widget.RecyclerView
//import coil.load
//
//class PhotoAdapter(private val photoPaths: List<String>) :
//    RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {
//
//    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        val ivPhoto: ImageView = itemView.findViewById(android.R.id.icon)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(android.R.layout.simple_list_item_1, parent, false)
//        return PhotoViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
//        holder.ivPhoto.load(photoPaths[position])
//    }
//
//    override fun getItemCount() = photoPaths.size
//}
//package com.example.Photo.adapter
//
//import android.content.Intent
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import androidx.recyclerview.widget.RecyclerView
//import com.example.Photo.PhotoPreviewActivity
//import com.example.Photo.R
//import com.example.Photo.model.PhotoItem
//
//// 适配器构造函数接收数据列表
//// PhotoAdapter.kt
//class PhotoAdapter(private val photoList: List<PhotoItem>) :
//    RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {
//
//    // 修正ViewHolder：itemView是LinearLayout，内部包含ImageView
//    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        val imageView: ImageView = itemView.findViewById(R.id.iv_photo_item)  // 获取布局内的ImageView
//    }
//
//    // 修正onCreateViewHolder：加载布局后，直接用View作为itemView
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
//        val itemView = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_photo, parent, false)  // 根布局是LinearLayout，类型是View
//        return PhotoViewHolder(itemView)
//    }
//
//    // 在PhotoAdapter的onBindViewHolder中添加点击事件
//    // PhotoAdapter.kt的onBindViewHolder方法中添加：
//    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
//        val photo = photoList[position]
//        // 加载列表项图片
//        photo.imageRes?.let { holder.imageView.setImageResource(it) }
//
//        // 点击列表项，跳转到预览页
//        holder.itemView.setOnClickListener {
//            val intent = Intent(holder.itemView.context, PhotoPreviewActivity::class.java)
//            // 传递图片数据（资源ID或文件路径）
//            photo.imageRes?.let { res ->
//                intent.putExtra("imageRes", res)
//            }
//            photo.filePath?.let { path ->
//                intent.putExtra("filePath", path)
//            }
//            holder.itemView.context.startActivity(intent)
//        }
//    }
//
//    override fun getItemCount() = photoList.size
//}
package com.example.Photo.adapter

import PhotoItem
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.Photo.R
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.TransformationUtils.centerCrop
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

//优化
class PhotoAdapter(
    private val mediaList: List<PhotoItem>,
    private val onImageSelected: (Uri) -> Unit,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    private lateinit var context: Context
    private var selectedPosition = -1

    // ViewHolder 绑定所有布局控件
    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivPhoto: ImageView = itemView.findViewById(R.id.iv_photo)
        val clPlaceholder: ConstraintLayout = itemView.findViewById(R.id.cl_placeholder)
        val clErrorState: ConstraintLayout = itemView.findViewById(R.id.cl_error_state)
        val clSelected: ConstraintLayout = itemView.findViewById(R.id.cl_selected)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        context = recyclerView.context
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        // 设置固定大小，优化性能
        val layoutParams = itemView.layoutParams as GridLayoutManager.LayoutParams
        layoutParams.width = parent.width / 3
        layoutParams.height = parent.width / 3
        itemView.layoutParams = layoutParams
        return PhotoViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        // 获取最新有效位置
        val actualPosition = holder.bindingAdapterPosition
        if (actualPosition == RecyclerView.NO_POSITION) return

        val photoItem = mediaList[actualPosition]
        val uri = if (photoItem.isLocalDrawable) {
            val resId = photoItem.uri.split("drawable://")[1].toInt()
            Uri.parse("android.resource://${context.packageName}/$resId")
        } else {
            Uri.parse(photoItem.uri)
        }

        // 状态初始化
        holder.clPlaceholder.visibility = View.VISIBLE
        holder.clErrorState.visibility = View.GONE
        holder.clSelected.visibility = if (actualPosition == selectedPosition) View.VISIBLE else View.GONE

        // Glide加载
        Glide.with(context)
            .load(uri)
            .override(300, 300)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    // 再次验证位置有效性
                    val latestPosition = holder.bindingAdapterPosition
                    if (latestPosition == RecyclerView.NO_POSITION || latestPosition != actualPosition) return

                    holder.ivPhoto.setImageDrawable(resource)
                    holder.clPlaceholder.visibility = View.GONE
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    val latestPosition = holder.bindingAdapterPosition
                    if (latestPosition == RecyclerView.NO_POSITION || latestPosition != actualPosition) return

                    holder.clPlaceholder.visibility = View.GONE
                    holder.clErrorState.visibility = View.VISIBLE
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    val latestPosition = holder.bindingAdapterPosition
                    if (latestPosition == RecyclerView.NO_POSITION || latestPosition != actualPosition) return

                    holder.ivPhoto.setImageDrawable(null)
                    holder.clPlaceholder.visibility = View.VISIBLE
                    holder.clErrorState.visibility = View.GONE
                }
            })

        // 点击事件
        holder.itemView.setOnClickListener {
            val clickPosition = holder.bindingAdapterPosition
            if (clickPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            selectedPosition = clickPosition
            onImageSelected(uri)
            notifyItemChanged(clickPosition)
            onItemClick(photoItem.uri)
        }
    }

    override fun getItemCount() = mediaList.size
    override fun getItemId(position: Int) = mediaList[position].id
    init { setHasStableIds(true) }
}
//class PhotoAdapter(private val photoList: List<PhotoItem>,
//                   private val activity: AlbumActivity,
//                   private val onImageSelected: (Uri) -> Unit) :
//    RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {
//
//    inner class PhotoViewHolder(itemView: ImageView) : RecyclerView.ViewHolder(itemView) {
//        val ivThumbnail: ImageView = itemView
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
//        // 网格项布局：正方形图片（宽高比1:1）
//        val imageView = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_photo_grid, parent, false) as ImageView
//        return PhotoViewHolder(imageView)
//    }


//    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
//        val item = photoList[position]
//        // 区分内部资源和外部Uri
//        if (item.uri.startsWith("drawable://")) {
//            // 加载App内部drawable图片
//            val resId = item.uri.split("://")[1].toInt()
//            Glide.with(holder.itemView.context)
//                .load(resId)
//                .centerCrop()
//                .into(holder.ivThumbnail)
//        } else {
//            // 加载设备媒体库图片
//            Glide.with(holder.itemView.context)
//                .load(Uri.parse(item.uri))
//                .centerCrop()
//                .into(holder.ivThumbnail)
//        }
//
//    }



//    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
//        val item = photoList[position]
//        holder.ivThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP

//        try {
//            if (item.uri.startsWith("drawable://")) {
//                // 内部drawable资源：直接用setImageResource
//                val resId = item.uri.split("://")[1].toInt()
//                holder.ivThumbnail.setImageResource(resId)
//            } else {
//                // 外部Uri：用setImageURI
//                holder.ivThumbnail.setImageURI(Uri.parse(item.uri))
//            }
//        } catch (e: Exception) {
//            holder.ivThumbnail.setImageResource(R.drawable.photo1)
//            Log.e("AdapterDebug", "图片加载失败：${e.message}")
//        }
//        // 动态WebP加载（替换原有原生加载）
//        if (item.uri.startsWith("drawable://")) {
//            val resId = item.uri.split("://")[1].toInt()
//            Glide.with(holder.itemView.context)
//                .load(resId)
//                .into(holder.ivThumbnail)
//        } else {
//            Glide.with(holder.itemView.context)
//                .load(Uri.parse(item.uri))
//                .into(holder.ivThumbnail)
//        }
//
//        // 点击事件（跳转到全屏预览）
//        holder.ivThumbnail.setOnClickListener {
//            val intent = Intent(activity, PhotoPreviewActivity::class.java)
//            intent.putExtra("imageUri", item.uri)
//            activity.startActivity(intent)
//        }

//        holder.ivThumbnail.setOnClickListener {
//            val sourceUri = if (item.uri.startsWith("drawable://")) {
//                // 内部资源图片需转换为ContentUri（uCrop不支持直接加载drawable）
//                val resId = item.uri.split("://")[1].toInt()
//                activity.resToUri(resId)
//            } else {
//                Uri.parse(item.uri)
//            }
//        // 点击图片触发选中
//        holder.ivThumbnail.setOnClickListener {
//            // 转换内部资源为Uri（同之前的resToUri方法）
//            val sourceUri = if (item.uri.startsWith("drawable://")) {
//                activity.resToUri(item.uri.split("://")[1].toInt())
//            } else {
//                Uri.parse(item.uri)
//            }


//            // 生成裁剪后保存路径
//            val destFile = File(activity.cacheDir, "crop_${System.currentTimeMillis()}.jpg")
//            val destUri = Uri.fromFile(destFile)
//
//            // 启动uCrop
//            UCrop.of(sourceUri, destUri)
//                .withAspectRatio(1f, 1f)
//                .withMaxResultSize(1920, 1920)
//                .withOptions(getUCropOptions(activity))
//                .start(activity, AlbumActivity.REQUEST_CROP)
//        }

//    }
//    private fun Context.resToUri(resId: Int): Uri {
//        // 1. 创建临时文件
//        val tempFile = File(cacheDir, "temp_$resId.jpg")
//        // 2. 将drawable资源写入临时文件
//        resources.openRawResource(resId).use { inputStream ->
//            tempFile.outputStream().use { outputStream ->
//                inputStream.copyTo(outputStream)
//            }
//        }
//        // 3. 通过FileProvider生成Uri
//        return FileProvider.getUriForFile(
//            this,
//            "${packageName}.fileprovider",
//            tempFile
//        )
//    }
//
//    // 添加dp转px工具方法
//    fun Int.dpToPx(context: Context): Int {
//        return (this * context.resources.displayMetrics.density).toInt()
//    }
//
//    // 3. 自定义 uCrop 样式（颜色、文字等）
//    private fun getUCropOptions(context: Context): UCrop.Options {
//        return UCrop.Options().apply {
//            // 裁剪框样式（保持不变）
//            setCropFrameColor(ContextCompat.getColor(context, R.color.primary))
//            setCropFrameStrokeWidth(4)
//            setCropGridColor(ContextCompat.getColor(context, R.color.primary_light))
//            setCropGridStrokeWidth(2)
//
//            // 工具栏样式（方法名调整）
//            setToolbarTitle("裁剪图片")
//            setToolbarColor(ContextCompat.getColor(context, R.color.primary))
//            setToolbarWidgetColor(Color.WHITE)
//
//            // 底部按钮样式（修正方法名）
//
//            setCompressionQuality(90)
//        }
//    }
//
//    override fun getItemCount() = photoList.size
//}