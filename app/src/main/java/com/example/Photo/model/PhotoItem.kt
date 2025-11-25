data class PhotoItem(
    val id: Long,
    val uri: String,
    val isVideo: Boolean,
    val thumbnailUri: String?,
    val isLocalDrawable: Boolean = false
)