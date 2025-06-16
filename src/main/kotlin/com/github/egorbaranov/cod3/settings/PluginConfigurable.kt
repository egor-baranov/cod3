package com.github.egorbaranov.cod3.settings

import com.github.egorbaranov.cod3.util.UIUtils
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagLayout
import javax.swing.*

class PluginConfigurable : SearchableConfigurable {

    private var mySettingsComponent: JPanel? = null

    private lateinit var apiKeyField: JBTextField
    private lateinit var apiUrlField: JBTextField

    private var retryQuantityDropdown = ComboBox(arrayOf(1, 3, 5, 10, 20))
    private var indexingStepsDropdown = ComboBox(arrayOf(1, 2, 3))


    override fun getId(): String = "cod3.settings"

    @NlsContexts.ConfigurableName
    override fun getDisplayName(): String = "My Plugin Settings"

    private val project: Project? by lazy {
        ProjectManager.getInstance().openProjects.firstOrNull()
    }

    override fun createComponent(): JComponent {
        val settings = PluginSettingsState.getInstance()
        apiKeyField = JBTextField(settings.openAIApiKey, 40).also {
            it.text = PluginSettingsState.getInstance().openAIApiKey
        }
        apiUrlField = JBTextField(settings.openAIApiUrl, 40).also {
            it.text = PluginSettingsState.getInstance().openAIApiUrl
        }

        mySettingsComponent = panel {
            group("Advanced Settings") {

                row {
                    cell(
                        JPanel().also {
                            it.add(Box.createVerticalStrut(4))
                        }
                    )
                }

                row("API key") {
                    cell(apiKeyField)
                }

                row("LLM API url") {
                    cell(apiUrlField)
                }

                row {
                    cell(
                        JPanel().also {
                            it.add(Box.createVerticalStrut(4))
                        }
                    )
                }

                row {
                    cell(label("Retry quantity").component)
                    cell(retryQuantityDropdown)
                    cell(label("Indexing steps").component)
                    cell(indexingStepsDropdown)
                }

                row {
                    cell(
                        JPanel().also {
                            it.add(Box.createVerticalStrut(8))
                        }
                    )
                }

            }
        }
        return mySettingsComponent!!
    }

    override fun isModified(): Boolean {
        val settings = PluginSettingsState.getInstance()
        return apiKeyField.text != settings.openAIApiKey ||
                apiUrlField.text != settings.openAIApiUrl ||
                retryQuantityDropdown.item != settings.retryQuantity
    }

    override fun apply() {
        val settings = PluginSettingsState.getInstance()
        settings.openAIApiKey = apiKeyField.text
        settings.openAIApiUrl = apiUrlField.text.takeIf { it.isNotEmpty() } ?: PluginSettingsState.DEFAULT_OPENAI_API_URL
        settings.retryQuantity = retryQuantityDropdown.item
        settings.indexingSteps = indexingStepsDropdown.item
    }

    override fun reset() {
        val settings = PluginSettingsState.getInstance()
        apiKeyField.text = settings.openAIApiKey
        apiUrlField.text = settings.openAIApiUrl
        retryQuantityDropdown.item = settings.retryQuantity
        indexingStepsDropdown.item = settings.indexingSteps
    }

    private fun buildStatPanel(titleText: String, mainText: String): JPanel {
        return JPanel(BorderLayout()).also { panel ->
            val titleLabel = JLabel(titleText).apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
                horizontalAlignment = SwingConstants.CENTER
                border = BorderFactory.createEmptyBorder(8, 0, 4, 0)
            }

            val mainLabel = JLabel(mainText).apply {
                font = font.deriveFont(Font.BOLD, 32f)
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
            }

            val centerPanel = JPanel(GridBagLayout()).apply {
                add(mainLabel)
                border = BorderFactory.createEmptyBorder(0, 10, 10, 10)
            }

            panel.add(titleLabel, BorderLayout.NORTH)
            panel.add(centerPanel, BorderLayout.CENTER)

            panel.preferredSize = Dimension(120, 100)
            panel.minimumSize = Dimension(120, 100)
            panel.border = UIUtils.createRoundedBorder()
            panel.background = JBColor.namedColor("Panel.background", JBColor.WHITE)
        }
    }
}