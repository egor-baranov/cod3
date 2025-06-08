import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints

/**
 * A JBTextField that shows gray placeholder text when empty & unfocused,
 * aligned and spaced exactly like normal text.
 */
class PlaceholderTextField(
    private val placeholder: String,
    columns: Int = 50
) : JBTextField(columns) {

    init {
        // Make sure we're using the normal text‐field font/style
        font = UIUtil.getLabelFont()
        // If you want the platform default, you can omit setting font here
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        if (text.isEmpty() && !hasFocus()) {
            val g2 = g.create() as Graphics2D
            try {
                // 1) Use the same font as the text field
                g2.font = font

                // 2) Enable text antialiasing for crisp rendering
                g2.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                )

                // 3) Placeholder color
                g2.color = UIUtil.getInactiveTextColor()

                // 4) Compute baseline
                val fm = g2.fontMetrics
                val x = insets.left + margin.left + 1
                val y = (height - fm.height) / 2 + fm.ascent

                // 5) Draw the placeholder
                g2.drawString(placeholder, x, y)
            } finally {
                g2.dispose()
            }
        }
    }



    override fun getPreferredSize(): Dimension {
        // Ensure preferred height is at least enough for one line of text
        val fm = getFontMetrics(font)
        val h = fm.height + insets.top + insets.bottom + margin.top + margin.bottom
        val w = super.getPreferredSize().width
        return Dimension(w, h)
    }
}
