package com.atuy.note.sync

import android.content.Context
import com.atuy.note.data.FolderManifest
import com.atuy.note.data.FolderRecord
import com.atuy.note.data.NOTE_MIME_TYPE
import com.atuy.note.data.NoteDocument
import com.atuy.note.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

class DriveSyncManager(
    private val context: Context,
    private val repository: NoteRepository,
) {
    data class Result(
        val uploaded: Int,
        val downloaded: Int,
        val conflicts: Int,
        val folders: List<FolderRecord>,
    )

    private data class RemoteNote(
        val fileId: String,
        val noteId: String,
        val revision: Long,
        val sha256: String,
        val name: String,
    )

    private data class RemoteListing(
        val notes: List<RemoteNote>,
        val folderManifestFileId: String?,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun sync(
        accessToken: String,
        localFolders: List<FolderRecord>,
        onProgress: (String) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        val listing = listRemote(accessToken)
        val remoteFolders = listing.folderManifestFileId
            ?.let { fileId -> runCatching { json.decodeFromString<FolderManifest>(downloadText(accessToken, fileId)).folders }.getOrNull() }
            .orEmpty()
        val mergedFolders = mergeFolders(localFolders, remoteFolders)
        uploadFolderManifest(accessToken, mergedFolders, listing.folderManifestFileId)

        val remoteById = listing.notes.associateBy { it.noteId }.toMutableMap()
        var uploaded = 0
        var downloaded = 0
        var conflicts = 0

        for (localFile in repository.noteFiles()) {
            val localDoc = repository.readDocument(localFile) ?: continue
            val localHash = sha256(localFile)
            val remote = remoteById.remove(localDoc.id)
            when {
                remote == null -> {
                    onProgress("Uploading ${localDoc.title}")
                    uploadNote(accessToken, localFile, localDoc, localHash, null)
                    uploaded++
                }
                localDoc.revision > remote.revision -> {
                    onProgress("Uploading ${localDoc.title}")
                    uploadNote(accessToken, localFile, localDoc, localHash, remote.fileId)
                    uploaded++
                }
                remote.revision > localDoc.revision -> {
                    onProgress("Downloading ${localDoc.title}")
                    downloadAndReplace(accessToken, remote)
                    downloaded++
                }
                localHash != remote.sha256 -> {
                    onProgress("Resolving conflict in ${localDoc.title}")
                    val conflict = File(localFile.parentFile, "${localDoc.id}-conflict-${System.currentTimeMillis()}.atnote")
                    download(accessToken, remote.fileId, conflict)
                    repository.replaceFromRemoteAsConflict(conflict, localDoc.folderId)
                    conflict.delete()
                    uploadNote(accessToken, localFile, localDoc, localHash, remote.fileId)
                    conflicts++
                    uploaded++
                }
            }
        }

        for (remote in remoteById.values) {
            onProgress("Downloading ${remote.name}")
            downloadAndReplace(accessToken, remote)
            downloaded++
        }
        Result(uploaded, downloaded, conflicts, mergedFolders)
    }

    private fun listRemote(token: String): RemoteListing {
        val fields = "nextPageToken,files(id,name,appProperties)"
        var pageToken: String? = null
        var folderManifestId: String? = null
        val output = mutableListOf<RemoteNote>()
        do {
            val query = buildString {
                append("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder")
                append("&q=trashed%3Dfalse")
                append("&pageSize=1000")
                append("&fields=").append(urlEncode(fields))
                pageToken?.let { append("&pageToken=").append(urlEncode(it)) }
            }
            val root = JSONObject(request("GET", query, token))
            val files = root.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                val props = file.optJSONObject("appProperties") ?: continue
                if (props.optString("kind") == FOLDER_MANIFEST_KIND) {
                    folderManifestId = file.getString("id")
                    continue
                }
                val noteId = props.optString("noteId")
                if (noteId.isBlank()) continue
                output += RemoteNote(
                    fileId = file.getString("id"),
                    noteId = noteId,
                    revision = props.optLong("revision", 0),
                    sha256 = props.optString("sha256"),
                    name = file.optString("name", "$noteId.atnote"),
                )
            }
            pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return RemoteListing(output, folderManifestId)
    }

    private fun uploadNote(
        token: String,
        file: File,
        document: NoteDocument,
        hash: String,
        remoteFileId: String?,
    ) {
        val metadata = JSONObject().apply {
            put("name", "${document.id}.atnote")
            put("mimeType", NOTE_MIME_TYPE)
            if (remoteFileId == null) put("parents", JSONArray().put("appDataFolder"))
            put("appProperties", JSONObject().apply {
                put("kind", NOTE_KIND)
                put("noteId", document.id)
                put("revision", document.revision.toString())
                put("updatedAt", document.updatedAt.toString())
                put("sha256", hash)
                put("title", document.title.take(100))
            })
        }
        resumableUpload(token, file, metadata, NOTE_MIME_TYPE, remoteFileId)
    }

    private fun uploadFolderManifest(token: String, folders: List<FolderRecord>, remoteFileId: String?) {
        val temp = File.createTempFile("note-folders-", ".json", context.cacheDir)
        try {
            temp.writeText(json.encodeToString(FolderManifest(folders = folders)))
            val metadata = JSONObject().apply {
                put("name", "library-index.json")
                put("mimeType", "application/json")
                if (remoteFileId == null) put("parents", JSONArray().put("appDataFolder"))
                put("appProperties", JSONObject().apply {
                    put("kind", FOLDER_MANIFEST_KIND)
                    put("updatedAt", System.currentTimeMillis().toString())
                })
            }
            resumableUpload(token, temp, metadata, "application/json", remoteFileId)
        } finally {
            temp.delete()
        }
    }

    private fun resumableUpload(
        token: String,
        file: File,
        metadata: JSONObject,
        mimeType: String,
        remoteFileId: String?,
    ) {
        val endpoint = if (remoteFileId == null) {
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id"
        } else {
            "https://www.googleapis.com/upload/drive/v3/files/$remoteFileId?uploadType=resumable&fields=id"
        }
        val method = if (remoteFileId == null) "POST" else "PATCH"
        val connection = open(method, endpoint, token).apply {
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", mimeType)
            setRequestProperty("X-Upload-Content-Length", file.length().toString())
            doOutput = true
        }
        connection.outputStream.use { it.write(metadata.toString().toByteArray()) }
        val initCode = connection.responseCode
        if (initCode !in 200..299) throw httpError(connection, initCode)
        val location = connection.getHeaderField("Location") ?: error("Drive did not return a resumable upload URL")
        connection.disconnect()

        val upload = open("PUT", location, token).apply {
            setRequestProperty("Content-Type", mimeType)
            setFixedLengthStreamingMode(file.length())
            doOutput = true
        }
        FileInputStream(file).use { input ->
            BufferedOutputStream(upload.outputStream).use { output -> input.copyTo(output, 256 * 1024) }
        }
        val code = upload.responseCode
        if (code !in 200..299) throw httpError(upload, code)
        upload.inputStream.use { it.readBytes() }
        upload.disconnect()
    }

    private fun downloadAndReplace(token: String, remote: RemoteNote) {
        val temp = File.createTempFile("drive-note-", ".atnote", context.cacheDir)
        try {
            download(token, remote.fileId, temp)
            repository.replaceFromRemoteBlocking(temp)
        } finally {
            temp.delete()
        }
    }

    private fun downloadText(token: String, fileId: String): String {
        val connection = open("GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media", token)
        val code = connection.responseCode
        if (code !in 200..299) throw httpError(connection, code)
        return connection.inputStream.bufferedReader().use { it.readText() }.also { connection.disconnect() }
    }

    private fun download(token: String, fileId: String, destination: File) {
        val connection = open("GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media", token)
        val code = connection.responseCode
        if (code !in 200..299) throw httpError(connection, code)
        BufferedInputStream(connection.inputStream).use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output, 256 * 1024) }
        }
        connection.disconnect()
    }

    private fun request(method: String, url: String, token: String): String {
        val connection = open(method, url, token)
        val code = connection.responseCode
        if (code !in 200..299) throw httpError(connection, code)
        return connection.inputStream.bufferedReader().use { it.readText() }.also { connection.disconnect() }
    }

    private fun open(method: String, url: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }

    private fun httpError(connection: HttpURLConnection, code: Int): IllegalStateException {
        val body = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
        connection.disconnect()
        return IllegalStateException("Google Drive HTTP $code${body?.let { ": $it" }.orEmpty()}")
    }

    private fun mergeFolders(local: List<FolderRecord>, remote: List<FolderRecord>): List<FolderRecord> =
        (local + remote)
            .groupBy { it.id }
            .map { (_, versions) -> versions.maxBy { it.updatedAt } }
            .sortedWith(compareBy<FolderRecord> { it.parentId.orEmpty() }.thenBy { it.name.lowercase() })

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val NOTE_KIND = "note"
        const val FOLDER_MANIFEST_KIND = "folderManifest"
    }
}
