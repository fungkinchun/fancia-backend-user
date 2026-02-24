package com.fancia.backend.user.storage.service

interface FileStorageService {
    fun uploadFile(filePath: String, file: ByteArray): String?
    fun downloadFile(fileName: String): ByteArray?
    fun deleteFile(fileName: String)
    fun listFiles(): MutableList<String?>
}