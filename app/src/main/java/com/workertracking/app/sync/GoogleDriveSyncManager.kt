package com.workertracking.app.sync

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.workertracking.app.data.TimesheetStorage
import com.workertracking.app.excel.ExcelManager
import com.workertracking.app.model.MonthInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class DriveFileItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: String? = null,
    val size: Long = 0
)

data class SyncStatus(
    val isSignedIn: Boolean,
    val accountEmail: String? = null,
    val folderName: String = "DreamHaus - Учёт Рабочих",
    val lastSyncTime: String? = null,
    val isSyncing: Boolean = false,
    val lastMessage: String? = null,
    val syncedFilesCount: Int = 0
)

class GoogleDriveSyncManager(private val context: Context) {

    private val storage = TimesheetStorage(context)
    private val excelManager = ExcelManager(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val driveScope = "https://www.googleapis.com/auth/drive.file"
    val folderName = "DreamHaus - Учёт Рабочих"

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(driveScope))
        .build()

    fun getSignInClient(): GoogleSignInClient {
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun isSignedIn(): Boolean {
        val account = getSignedInAccount() ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(driveScope))
    }

    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = getSignedInAccount() ?: return@withContext null
        try {
            val scopeString = "oauth2:$driveScope"
            if (account.account != null) {
                GoogleAuthUtil.getToken(context, account.account!!, scopeString)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==========================================
    // 1. DRIVE FOLDER OPERATIONS
    // ==========================================

    suspend fun findOrCreateAppFolder(token: String): String = withContext(Dispatchers.IO) {
        // Search if folder exists
        val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name)"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (response.isSuccessful) {
            val json = JSONObject(body)
            val files = json.optJSONArray("files") ?: JSONArray()
            if (files.length() > 0) {
                return@withContext files.getJSONObject(0).getString("id")
            }
        }

        // Folder not found -> Create it
        val createUrl = "https://www.googleapis.com/drive/v3/files"
        val metaJson = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
        }

        val createRequest = Request.Builder()
            .url(createUrl)
            .addHeader("Authorization", "Bearer $token")
            .post(metaJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        val createResponse = httpClient.newCall(createRequest).execute()
        val createBody = createResponse.body?.string() ?: ""
        if (!createResponse.isSuccessful) {
            throw IllegalStateException("Ошибка создания папки на Google Диске: $createBody")
        }
        val createdJson = JSONObject(createBody)
        return@withContext createdJson.getString("id")
    }

    // ==========================================
    // 2. UPLOAD / UPDATE FILE IN FOLDER
    // ==========================================

    suspend fun uploadOrUpdateFile(
        token: String,
        folderId: String,
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray
    ): DriveFileItem = withContext(Dispatchers.IO) {
        // Check if file already exists in folder
        val query = "name = '$fileName' and '$folderId' in parents and trashed = false"
        val checkUrl = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,mimeType,modifiedTime)"

        val checkReq = Request.Builder()
            .url(checkUrl)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val checkResp = httpClient.newCall(checkReq).execute()
        val checkBody = checkResp.body?.string() ?: ""
        val existingFiles = if (checkResp.isSuccessful) {
            JSONObject(checkBody).optJSONArray("files") ?: JSONArray()
        } else JSONArray()

        if (existingFiles.length() > 0) {
            // Update existing file content
            val fileId = existingFiles.getJSONObject(0).getString("id")
            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"

            val updateReq = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "Bearer $token")
                .patch(fileBytes.toRequestBody(mimeType.toMediaType()))
                .build()

            val updateResp = httpClient.newCall(updateReq).execute()
            val updateBody = updateResp.body?.string() ?: ""
            if (!updateResp.isSuccessful) {
                throw IllegalStateException("Ошибка обновления файла $fileName: $updateBody")
            }
            val resObj = JSONObject(updateBody)
            DriveFileItem(id = resObj.optString("id", fileId), name = fileName, mimeType = mimeType, size = fileBytes.size.toLong())
        } else {
            // Upload new file multipart
            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().put(folderId))
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("metadata", null, metadataJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .addFormDataPart("file", fileName, fileBytes.toRequestBody(mimeType.toMediaType()))
                .build()

            val uploadReq = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            val uploadResp = httpClient.newCall(uploadReq).execute()
            val uploadBody = uploadResp.body?.string() ?: ""
            if (!uploadResp.isSuccessful) {
                throw IllegalStateException("Ошибка загрузки файла $fileName: $uploadBody")
            }
            val resObj = JSONObject(uploadBody)
            DriveFileItem(id = resObj.getString("id"), name = fileName, mimeType = mimeType, size = fileBytes.size.toLong())
        }
    }

    // ==========================================
    // 3. DOWNLOAD FILE BY ID
    // ==========================================

    suspend fun downloadFileBytes(token: String, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw IllegalStateException("Ошибка скачивания файла с Google Диска: ${resp.code}")
        }
        resp.body?.bytes() ?: ByteArray(0)
    }

    // ==========================================
    // 4. LIST FILES IN FOLDER
    // ==========================================

    suspend fun listFilesInFolder(token: String, folderId: String): List<DriveFileItem> = withContext(Dispatchers.IO) {
        val query = "'$folderId' in parents and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,mimeType,modifiedTime,size)"

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val resp = httpClient.newCall(req).execute()
        val body = resp.body?.string() ?: ""
        if (!resp.isSuccessful) return@withContext emptyList()

        val json = JSONObject(body)
        val filesArray = json.optJSONArray("files") ?: JSONArray()
        val result = mutableListOf<DriveFileItem>()
        for (i in 0 until filesArray.length()) {
            val obj = filesArray.getJSONObject(i)
            result.add(
                DriveFileItem(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    mimeType = obj.getString("mimeType"),
                    modifiedTime = obj.optString("modifiedTime", null),
                    size = obj.optLong("size", 0)
                )
            )
        }
        result
    }

    // ==========================================
    // 5. BIDIRECTIONAL SYNC (ДВУСТОРОННЯЯ СИНХРОНИЗАЦИЯ)
    // ==========================================

    /**
     * Complete two-way sync:
     * 1. Check if cloud has a newer backup database (dreamhaus_backup.json). If cloud has newer data, downloads & restores it.
     * 2. Exports and uploads all current local files into "DreamHaus - Учёт Рабочих":
     *    - dreamhaus_backup.json (Structured full DB)
     *    - timesheet_YYYY_MM.xlsx (Formatted Excel timesheet)
     *    - rejestr_pracownikow.xlsx (Formatted Excel workers registry)
     *    - vedomost_DD_MM_YYYY.txt (Text accounting statement files)
     */
    suspend fun performTwoWaySync(
        monthInfo: MonthInfo,
        forcePullFromCloud: Boolean = false,
        forcePushToCloud: Boolean = false
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = getAccessToken()
            ?: return@withContext Pair(false, "Требуется авторизация в Google. Нажмите «Войти через Google».")

        try {
            val folderId = findOrCreateAppFolder(token)
            val cloudFiles = listFilesInFolder(token, folderId)

            val backupFileName = "dreamhaus_backup.json"
            val cloudBackup = cloudFiles.firstOrNull { it.name == backupFileName }

            var restoredFromCloud = false

            // 1. CLOUD -> APP (Проверка и загрузка из облака в приложение)
            if (cloudBackup != null && !forcePushToCloud) {
                val cloudJsonBytes = downloadFileBytes(token, cloudBackup.id)
                val cloudJsonStr = String(cloudJsonBytes, Charsets.UTF_8)

                val localBackupStr = storage.exportFullBackupJson()
                val localTimestamp = try { JSONObject(localBackupStr).optLong("timestamp", 0) } catch (e: Exception) { 0L }
                val cloudTimestamp = try { JSONObject(cloudJsonStr).optLong("timestamp", 0) } catch (e: Exception) { 0L }

                if (forcePullFromCloud || cloudTimestamp > localTimestamp) {
                    val success = storage.importFullBackupJson(cloudJsonStr)
                    if (success) {
                        restoredFromCloud = true
                    }
                }
            }

            // 2. APP -> CLOUD (Выгрузка актуальных файлов в облако)
            val settings = storage.getSettings()
            val workers = storage.getWorkers()
            val attendance = storage.getAttendance()
            val advances = storage.getAdvances()

            var uploadedCount = 0

            // 2.1 Upload Full Database Backup
            val backupJsonBytes = storage.exportFullBackupJson().toByteArray(Charsets.UTF_8)
            uploadOrUpdateFile(token, folderId, backupFileName, "application/json", backupJsonBytes)
            uploadedCount++

            // 2.2 Upload Timesheet Excel
            val timesheetFile = excelManager.exportTimesheet(
                monthInfo = monthInfo,
                workers = workers,
                entries = attendance,
                companyName = settings.companyName,
                responsiblePerson = settings.responsiblePerson
            )
            uploadOrUpdateFile(token, folderId, timesheetFile.name, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", timesheetFile.readBytes())
            uploadedCount++

            // 2.3 Upload Workers Registry Excel
            val registryFile = excelManager.exportWorkersRegistry(
                workers = workers,
                companyName = settings.companyName,
                responsiblePerson = settings.responsiblePerson
            )
            uploadOrUpdateFile(token, folderId, registryFile.name, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", registryFile.readBytes())
            uploadedCount++

            // 2.4 Upload Accounting Statements as .txt
            val dateGroups = advances.groupBy { it.date }.map { (date, items) ->
                com.workertracking.app.model.DateAdvanceGroup(date = date, items = items)
            }
            dateGroups.forEach { group ->
                val txtFile = excelManager.exportDateAdvanceGroupToTextFile(group)
                uploadOrUpdateFile(token, folderId, txtFile.name, "text/plain", txtFile.readBytes())
                uploadedCount++
            }

            val msg = if (restoredFromCloud) {
                "Двусторонняя синхронизация завершена: данные обновлены из облака и $uploadedCount файлов выгружено в папку «$folderName»"
            } else {
                "Успешная синхронизация: $uploadedCount файлов обновлено в папке «$folderName» на Google Диске"
            }

            Pair(true, msg)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "Ошибка синхронизации: ${e.message}")
        }
    }
}
