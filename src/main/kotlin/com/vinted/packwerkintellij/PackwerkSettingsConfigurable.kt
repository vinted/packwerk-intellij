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
    private var lintUnsavedFiles = false
    private var ignoreRecordedViolations = false

    override fun createPanel(): DialogPanel = panel {
        val settings = project.service<PackwerkSettingsState>()
        packwerkPath = settings.packwerkPath
        enabled = settings.enabled
        lintUnsavedFiles = settings.lintUnsavedFiles
        ignoreRecordedViolations = settings.ignoreRecordedViolations

        row("Packwerk path:") {
            textField()
                .bindText(::packwerkPath)
                .comment(
                    "Note: This only accepts a single path. " +
                            "The path can be absolute or relative to the project root. " +
                            "Shell expansions or multiple arguments are not supported."
                )
        }
        row {
            checkBox("Lint Ruby files")
                .bindSelected(::enabled)
        }
        row {
            checkBox("Experimental: Lint unsaved files")
                .bindSelected(::lintUnsavedFiles)
                .comment(
                    "This enables as-you-type linting. " +
                            "Warning: this requires the linter to support the 'check-contents' command " +
                            "which is currently only supported by Packs and not Packwerk."
                )
        }
        row {
            checkBox("Experimental: Ignore recorded violations")
                .bindSelected(::ignoreRecordedViolations)
                .comment(
                    "Show violations in the editor even if they're recorded in package_todo.yml. " +
                            "Warning: not widely supported."
                )
        }

        onApply {
            settings.packwerkPath = packwerkPath
            settings.enabled = enabled
            settings.lintUnsavedFiles = lintUnsavedFiles
            settings.ignoreRecordedViolations = ignoreRecordedViolations
        }
    }
}
