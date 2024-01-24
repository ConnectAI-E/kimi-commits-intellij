package com.github.connectai_e.kimi.commits.intellij.settings.prompt

data class Prompt(
        var name: String = "",
        var description: String = "",
        var content: String = "",
        var canBeChanged: Boolean = true
)
