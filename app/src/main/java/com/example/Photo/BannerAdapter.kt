import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.Photo.R

class BannerAdapter(private val images: List<Int>) : RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

    inner class BannerViewHolder(itemView: ImageView) : RecyclerView.ViewHolder(itemView) {
        val ivBanner: ImageView = itemView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val imageView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_banner, parent, false) as ImageView
        return BannerViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        // 处理无限轮播的位置计算（取模）
        val realPosition = position % images.size
        holder.ivBanner.setImageResource(images[realPosition])
    }

    // 为了实现无限轮播，返回一个很大的数值
    override fun getItemCount(): Int = Int.MAX_VALUE
}