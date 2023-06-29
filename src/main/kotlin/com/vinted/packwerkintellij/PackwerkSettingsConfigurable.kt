package com.vinted.packwerkintellij

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class PackwerkSettingsConfigurable(private val project: Project) : BoundConfigurable("Packwerk Settings") {
    private var packwerkPath: String = ""
    private var enabled = true

    override fun createPanel(): DialogPanel = panel {
        val settings = project.service<PackwerkSettingsState>()
        packwerkPath = settings.packwerkPath
        enabled = settings.enabled

        row("Linter options:") {
            checkBox("Run Packwerk check").bindSelected(::enabled)
        }
        row("Packwerk executable path:") {
            textField().bindText(::packwerkPath)
        }

        onApply {
            settings.packwerkPath = packwerkPath
            settings.enabled = enabled
        }
    }
}
