package com.vinted.packwerkintellij

import com.intellij.openapi.components.*

@State(
    name = "com.vinted.packwerkintellij.ApplicationSettingsState",
    storages = [Storage("PackwerkSettings.xml")]
)
@Service
class PackwerkSettingsState : PersistentStateComponent<PackwerkSettingsState> {
    var packwerkPath: String = "bin/packwerk"
    var runOnSave = false

    override fun getState(): PackwerkSettingsState {
        return this
    }

    override fun loadState(state: PackwerkSettingsState) {
        this.packwerkPath = state.packwerkPath
        this.runOnSave = state.runOnSave
    }
}
