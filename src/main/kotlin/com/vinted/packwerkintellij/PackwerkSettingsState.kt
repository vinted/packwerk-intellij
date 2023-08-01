package com.vinted.packwerkintellij

import com.intellij.openapi.components.*

@State(
    name = "com.vinted.packwerkintellij.ApplicationSettingsState",
    storages = [Storage("PackwerkSettings.xml")]
)
@Service
class PackwerkSettingsState : PersistentStateComponent<PackwerkSettingsState> {
    var packwerkPath: String = "bin/packwerk"
    var enabled = true
    var lintUnsavedFiles = false
    var ignoreRecordedViolations = false

    override fun getState(): PackwerkSettingsState {
        return this
    }

    override fun loadState(state: PackwerkSettingsState) {
        this.packwerkPath = state.packwerkPath
        this.enabled = state.enabled
        this.lintUnsavedFiles = state.lintUnsavedFiles
        this.ignoreRecordedViolations = state.ignoreRecordedViolations
    }
}
