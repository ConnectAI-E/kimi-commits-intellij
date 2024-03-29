package com.github.connectai_e.kimi.commits.intellij.settings

import com.aallam.openai.api.model.ModelId
import com.github.connectai_e.kimi.commits.intellij.settings.prompt.Prompt
import java.awt.Component
import java.util.*
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class AppSettingsListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is Locale) {
            text = value.displayLanguage
        }
        if (value is Prompt) {
            text = value.name
        }
        if (value is ModelId) {
            text = value.id
        }
        return component
    }
}