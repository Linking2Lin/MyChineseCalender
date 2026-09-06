package lins.applications.appwidget.helper

import kotlin.math.max
import kotlin.math.min

/** 图片解码后的目标尺寸，单位为像素。 */
data class ImageSize(val width: Int, val height: Int)
/** 居中裁剪的源像素区域；left/top 为起点，size 为正方形边长。 */
data class CropSquare(val left: Int, val top: Int, val size: Int)

/** 纯几何运算，不创建 Bitmap；数值边界可以在普通 JVM 测试中验证。 */
object ImageGeometry {
    /**
     * 按比例缩小到最长边不超过 maxSide，不放大小图。
     * 极端长宽比经整数截断后也至少保留 1 像素，避免解码器收到零尺寸。
     */
    fun fit(width: Int, height: Int, maxSide: Int): ImageSize {
        require(width > 0 && height > 0 && maxSide > 0)
        val scale = min(1.0, maxSide.toDouble() / max(width, height))
        return ImageSize(max(1, (width * scale).toInt()), max(1, (height * scale).toInt()))
    }

    /** 以短边为边长裁剪中心，奇数差值舍去半像素，最多相差 1 像素。 */
    fun centerCrop(width: Int, height: Int): CropSquare {
        require(width > 0 && height > 0)
        val size = min(width, height)
        return CropSquare((width - size) / 2, (height - size) / 2, size)
    }
}
