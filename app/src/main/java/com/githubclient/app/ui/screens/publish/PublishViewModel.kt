package com.githubclient.app.ui.screens.publish

import androidx.lifecycle.ViewModel
import com.githubclient.app.automation.PublishManager
import com.githubclient.app.automation.PublishState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PublishViewModel @Inject constructor(
    private val manager: PublishManager
) : ViewModel() {

    val state: StateFlow<PublishState> = manager.state
    val log: StateFlow<List<String>> = manager.log

    fun publish(repoName: String, sourcePath: String, triggerDispatch: Boolean) {
        manager.publish(repoName, sourcePath, triggerDispatch)
    }

    fun reset() = manager.reset()
}
