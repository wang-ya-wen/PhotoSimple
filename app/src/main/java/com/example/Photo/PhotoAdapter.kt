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
