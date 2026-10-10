package lins.applications.appwidget.helper

import kotlin.math.max
import kotlin.math.min

/**
 * 图片解码后的目标尺寸。
 * @param width 目标宽度，单位为像素；fit 生成的结果至少为 1。
 * @param height 目标高度，单位为像素；fit 生成的结果至少为 1。
 */
data class ImageSize(val width: Int, val height: Int)
/**
 * 源图中用于圆形蒙版的中心正方形区域，坐标均以源图左上角为原点。
 * @param left 正方形左边界的横坐标，单位为像素。
 * @param top 正方形上边界的纵坐标，单位为像素。
 * @param size 正方形边长，单位为像素；centerCrop 返回源图短边长度。
 */
data class CropSquare(val left: Int, val top: Int, val size: Int)

/** 纯几何运算，不创建 Bitmap；数值边界可以在普通 JVM 测试中验证。 */
object ImageGeometry {
    /**
     * 按比例缩小到最长边不超过 maxSide，不放大小图。
     * 极端长宽比经整数截断后也至少保留 1 像素，避免解码器收到零尺寸。
     * @param width 源图宽度，单位为像素，必须大于 0。
     * @param height 源图高度，单位为像素，必须大于 0。
     * @param maxSide 允许的最长边像素数，必须大于 0；不放大小图。
     * @return ImageSize；保持比例、最长边受限且两个方向至少为 1 像素的目标尺寸。
     */
    fun fit(width: Int, height: Int, maxSide: Int): ImageSize {
        require(width > 0 && height > 0 && maxSide > 0)
        // 缩放比最多为 1，保持小图原尺寸；宽高共同使用一个比例。
        val scale = min(1.0, maxSide.toDouble() / max(width, height))
        return ImageSize(max(1, (width * scale).toInt()), max(1, (height * scale).toInt()))
    }

    /**
     * 以短边为边长裁剪中心，奇数差值舍去半像素，最多相差 1 像素。
     * @param width 源图宽度，单位为像素，必须大于 0。
     * @param height 源图高度，单位为像素，必须大于 0。
     * @return CropSquare；源图中心正方形的左上角坐标与边长，单位为像素。
     */
    fun centerCrop(width: Int, height: Int): CropSquare {
        require(width > 0 && height > 0)
        // 从较长方向的两端对称裁去多余像素，整数除法决定奇数差值的取舍。
        val size = min(width, height)
        return CropSquare((width - size) / 2, (height - size) / 2, size)
    }
}
