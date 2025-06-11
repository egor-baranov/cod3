import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon
import kotlin.math.roundToInt

fun Icon.scaled(width: Int, height: Int): Icon {
    // If this is an ImageIcon, scale the image into a BufferedImage with high-quality hints
    if (this is ImageIcon) {
        val originalImage: Image = this.image
        if (width <= 0 || height <= 0) {
            // fallback: return a transparent icon of requested size
            return createEmptyIcon(width.coerceAtLeast(1), height.coerceAtLeast(1))
        }
        // Create a buffered image with transparency
        val buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2 = buffered.createGraphics()
        try {
            // Set high-quality rendering hints
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            // Draw the original image into the buffered image, scaling it.
            g2.drawImage(originalImage, 0, 0, width, height, null)
        } finally {
            g2.dispose()
        }
        return ImageIcon(buffered)
    }

    // Otherwise, wrap non-ImageIcon as before but add rendering hints in paintIcon
    val origWidth = this.iconWidth
    val origHeight = this.iconHeight
    if (origWidth <= 0 || origHeight <= 0) {
        return createEmptyIcon(width, height)
    }

    return object : Icon {
        override fun getIconWidth() = width
        override fun getIconHeight() = height

        override fun paintIcon(c: java.awt.Component?, g: java.awt.Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                // translate
                g2.translate(x, y)
                // set high-quality hints before scaling
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                // compute scale
                val scaleX = width.toDouble() / origWidth
                val scaleY = height.toDouble() / origHeight
                g2.scale(scaleX, scaleY)
                // paint the original icon into this scaled graphics
                this@scaled.paintIcon(c, g2, 0, 0)
            } finally {
                g2.dispose()
            }
        }
    }
}

private fun createEmptyIcon(width: Int, height: Int): Icon {
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    return object : Icon {
        override fun getIconWidth() = w
        override fun getIconHeight() = h
        override fun paintIcon(c: java.awt.Component?, g: java.awt.Graphics, x: Int, y: Int) {
            // nothing: transparent
        }
    }
}

fun Icon.scaledBy(factor: Double): Icon {
    val origW = this.iconWidth
    val origH = this.iconHeight
    val targetW = if (origW > 0) (origW * factor).roundToInt() else 0
    val targetH = if (origH > 0) (origH * factor).roundToInt() else 0
    return this.scaled(targetW.coerceAtLeast(1), targetH.coerceAtLeast(1))
}
