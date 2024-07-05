package org.zotero.android.screens.settings.account

import android.content.Context
import android.net.Uri
import androidx.core.text.HtmlCompat
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.zotero.android.api.network.CustomResult
import org.zotero.android.architecture.BaseViewModel2
import org.zotero.android.architecture.Defaults
import org.zotero.android.architecture.ViewEffect
import org.zotero.android.architecture.ViewState
import org.zotero.android.architecture.navigation.toolbar.data.SyncProgress
import org.zotero.android.architecture.navigation.toolbar.data.SyncProgressEventStream
import org.zotero.android.database.DbRequest
import org.zotero.android.database.DbWrapper
import org.zotero.android.database.objects.RCustomLibraryType
import org.zotero.android.database.requests.DeleteAllWebDavDeletionsDbRequest
import org.zotero.android.database.requests.MarkAttachmentsNotUploadedDbRequest
import org.zotero.android.files.FileStore
import org.zotero.android.screens.root.RootActivity
import org.zotero.android.sync.KeyGenerator
import org.zotero.android.sync.Libraries
import org.zotero.android.sync.LibraryIdentifier
import org.zotero.android.sync.SessionController
import org.zotero.android.sync.SyncError
import org.zotero.android.sync.SyncKind
import org.zotero.android.sync.SyncScheduler
import org.zotero.android.webdav.WebDavController
import org.zotero.android.webdav.WebDavSessionStorage
import org.zotero.android.webdav.data.FileSyncType
import org.zotero.android.webdav.data.WebDavScheme
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
internal class SettingsAccountViewModel @Inject constructor(
    private val defaults: Defaults,
    private val sessionController: SessionController,
    private val sessionStorage: WebDavSessionStorage,
    private val webDavController: WebDavController,
    private val fileStore: FileStore,
    private val context: Context,
    private val dbWrapper: DbWrapper,
    private val syncScheduler: SyncScheduler,
    private val syncProgressEventStream: SyncProgressEventStream,
) : BaseViewModel2<SettingsAccountViewState, SettingsAccountViewEffect>(SettingsAccountViewState()) {

    fun init() = initOnce {
        val isVerified = sessionStorage.isVerified

        updateState {
            copy(
                account = defaults.getUsername(),
                fileSyncType = if (sessionStorage.isEnabled) {
                    FileSyncType.webDav
                } else {
                    FileSyncType.zotero
                },
                scheme = sessionStorage.scheme,
                url = sessionStorage.url,
                username = sessionStorage.username,
                password = sessionStorage.password,
                webDavVerificationResult = if (isVerified) {
                    CustomResult.GeneralSuccess(Unit)
                } else {
                    null
                }
            )
        }
    }

    fun onBack() {
        triggerEffect(SettingsAccountViewEffect.OnBack)
    }

    fun openDeleteAccount() {
        val uri = Uri.parse("https://www.zotero.org/settings/deleteaccount")
        triggerEffect(SettingsAccountViewEffect.OpenWebpage(uri))
    }

    fun openManageAccount() {
        val uri = Uri.parse("https://www.zotero.org/settings/account")
        triggerEffect(SettingsAccountViewEffect.OpenWebpage(uri))

    }

    fun onSignOut() {
        sessionController.reset()
        context.startActivity(RootActivity.getIntentClearTask(context))
    }

    fun dismissWebDavOptionsPopup() {
        updateState {
            copy(
                showWebDavOptionsPopup = false
            )
        }
    }

    fun showWebDavOptionsPopup() {
        updateState {
            copy(
                showWebDavOptionsPopup = true
            )
        }
    }

    fun onZoteroOptionSelected() {
        dismissWebDavOptionsPopup()
    }

    fun onWebDavOptionSelected() {
        dismissWebDavOptionsPopup()
    }

    private fun set(type: FileSyncType) {
        if (viewState.fileSyncType == type) {
            return
        }

        syncScheduler.cancelSync()

        val oldType = viewState.fileSyncType
        updateState {
            copy(
                fileSyncType = type,
                markingForReupload = true
            )
        }

        markAttachmentsForReupload(type) { error ->
            updateState {
                var res = this
                if (error != null) {
                    res = res.copy(fileSyncType = oldType)
                }
                res.copy(markingForReupload = false)
            }
            if (error != null) {
                return@markAttachmentsForReupload
            }

            sessionStorage.isEnabled = type == FileSyncType.webDav

            if (type == FileSyncType.zotero) {
                if (syncScheduler.inProgress.value) {
                    syncScheduler.cancelSync()
                }
                syncScheduler.request(SyncKind.normal, Libraries.all)
            }
        }
    }

    private fun set(url: String) {
        if (viewState.url == url) {
            return
        }
        var decodedUrl = url
        if (url.contains("%")) {
            decodedUrl = HtmlCompat.fromHtml(
                url,
                HtmlCompat.FROM_HTML_MODE_LEGACY
            ).toString()
        }
        sessionStorage.url = decodedUrl
        webDavController.resetVerification()

        updateState {
            copy(
                url = url,
                webDavVerificationResult = null,
                markingForReupload = true
            )
        }

        markAttachmentsForReupload(FileSyncType.webDav) {
            updateState {
                copy(markingForReupload = false)
            }
        }
    }

    private fun markAttachmentsForReupload(type: FileSyncType, completion: (Exception?) -> Unit) {
        try {
            performMark(type)
            completion(null)
        } catch (error: Exception) {
            Timber.e(error, "SettingsAccountViewModel: can't mark all attachments not uploaded")
            completion(error)
        }
    }

    fun performMark(type: FileSyncType) {
        val keys = downloadedAttachmentKeys()
        val requests = mutableListOf<DbRequest>(
            MarkAttachmentsNotUploadedDbRequest(
                keys = keys,
                libraryId = LibraryIdentifier.custom(RCustomLibraryType.myLibrary)
            )
        )
        if (type == FileSyncType.zotero) {
            requests.add(DeleteAllWebDavDeletionsDbRequest())
        }
        dbWrapper.realmDbStorage.perform(requests)
    }

    private fun downloadedAttachmentKeys(): List<String> {
        val contents =
            fileStore.downloads(LibraryIdentifier.custom(RCustomLibraryType.myLibrary)).listFiles()
                ?: emptyArray()
        return contents.filter { file ->
            val fullPath = file.absolutePath
            val partOfPath = fullPath.substring(fullPath.indexOf("downloads"))
            val relativeComponentsCount = partOfPath.count { it == File.separatorChar }
            val lastPathPart = partOfPath.substring(partOfPath.lastIndexOf(File.separatorChar) + 1)

            if (relativeComponentsCount == 2 && (lastPathPart
                    ?: "").length == KeyGenerator.length
            ) {
                val contents = file.list() ?: emptyArray()
                return@filter contents.isNotEmpty()

            }
            false
        }.map {
            val fullPath = it.absolutePath
            fullPath.substring(fullPath.lastIndexOf(File.separatorChar) + 1)
        }
    }

    fun setScheme(scheme: WebDavScheme) {
        if (viewState.scheme == scheme) {
            return
        }
        updateState {
            copy(scheme = scheme, webDavVerificationResult = null)
        }
        sessionStorage.scheme = scheme
        webDavController.resetVerification()
    }

    fun setUsername(username: String) {
        if (viewState.username == username) {
            return
        }
        updateState {
            copy(username = username, webDavVerificationResult = null)
        }
        sessionStorage.username = username
        webDavController.resetVerification()
    }

    fun setPassword(password: String) {
        if (viewState.password == password) {
            return
        }
        updateState {
            copy(password = password, webDavVerificationResult = null)
        }

        sessionStorage.password = password
        webDavController.resetVerification()
    }

    private fun cancelVerification() {
        updateState {
            copy(isVerifyingWebDav = false)
        }
    }

    fun recheckKeys() {
        if(syncScheduler.inProgress.value) {
            syncScheduler.cancelSync()
        }
        observeSyncIssues()
        syncScheduler.request(SyncKind.keysOnly, Libraries.all)
    }

    private fun observeSyncIssues() {
        syncProgressEventStream.flow()
            .onEach { progress ->
                process(progress = progress)
            }
            .launchIn(viewModelScope)
    }

    private fun process(progress: SyncProgress) {
        when (progress) {
            is SyncProgress.aborted -> {
                val fatalError = progress.error
                if (fatalError is SyncError.Fatal.forbidden) {
                    sessionController.reset()
                }

            }
            else -> {
                //no-op
            }
        }
    }
}

internal data class SettingsAccountViewState(
    val account: String = "",
    val showWebDavOptionsPopup: Boolean = false,
    var fileSyncType: FileSyncType = FileSyncType.zotero,
    var markingForReupload: Boolean = false,
    var scheme: WebDavScheme = WebDavScheme.https,
    var url: String = "",
    var username: String = "",
    var password: String = "",
    var isVerifyingWebDav: Boolean = false,
    var webDavVerificationResult: CustomResult<Unit>? = null,
) : ViewState

internal sealed class SettingsAccountViewEffect : ViewEffect {
    object OnBack : SettingsAccountViewEffect()
    data class OpenWebpage(val uri: Uri) : SettingsAccountViewEffect()
}