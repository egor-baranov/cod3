import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JButton
import javax.swing.SwingConstants

class RoundedIconButton(
    svgPath: String,
    private val fixedSize: Int = JBUIScale.scale(16),
    padding: Int = 0
) : JButton() {

    init {
        // load your SVG resource (e.g. resources/icons/send.svg)
        icon = IconLoader.getIcon(svgPath, javaClass)

        // center the icon
        horizontalAlignment = CENTER
        verticalAlignment = CENTER

        // no default painting
        isContentAreaFilled = false
        isFocusPainted = false

        // padding _inside_ the pill (you can set to 0 since size is fixed)
        border = JBUI.Borders.empty(padding)

        background = JBColor.background().brighter()
        cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
    }

    // Force all sizes to fixedSize × fixedSize
    override fun getPreferredSize() = Dimension(fixedSize, fixedSize)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            )
            // full-round “pill” with diameter = height
            g2.color = background
            g2.fillRoundRect(0, 0, width, height, height, height)
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}
