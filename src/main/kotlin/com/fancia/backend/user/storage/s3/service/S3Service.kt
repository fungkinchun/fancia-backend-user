package com.fancia.backend.user.storage.s3.service

import com.fancia.backend.user.storage.s3.config.S3Configuration
import com.fancia.backend.user.storage.service.FileStorageService
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.net.URI

@Service
class S3Service(private val s3Configuration: S3Configuration) : FileStorageService {
    private val s3Client: S3Client? = s3Configuration.baseUrl?.let {
        S3Client.builder()
            .endpointOverride(URI.create(it))
            .region(Region.of(s3Configuration.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        s3Configuration.accessKey,
                        s3Configuration.secretKey
                    )
                )
            )
            .forcePathStyle(true)
            .build()
    }

    override fun uploadFile(filePath: String, file: ByteArray): String? {
        return try {
            val request = PutObjectRequest.builder()
                .bucket(s3Configuration.bucketName)
                .storageClass(s3Configuration.storageClass)
                .key(filePath)
                .build()

            s3Client?.let {
                it.putObject(request, RequestBody.fromBytes(file))
                val getUrlRequest = GetUrlRequest.builder()
                    .bucket(s3Configuration.bucketName)
                    .key(filePath)
                    .build()

                s3Client.utilities().getUrl(getUrlRequest).toURI().toString()
            } ?: throw RuntimeException("S3 client is not initialized")
        } catch (e: Exception) {
            throw RuntimeException("Failed to upload file or retrieve its URL", e)
        }
    }

    override fun downloadFile(fileName: String): ByteArray? {
        return try {
            val request = GetObjectRequest.builder()
                .bucket(s3Configuration.bucketName)
                .key(fileName)
                .build()

            s3Client?.let {
                val response: ResponseBytes<GetObjectResponse> = it.getObjectAsBytes(request)
                response.asByteArray()
            } ?: throw RuntimeException("S3 client is not initialized")
        } catch (e: NoSuchKeyException) {
            throw RuntimeException("Failed to download file: $fileName", e)
        } catch (e: Exception) {
            throw RuntimeException("Failed to download file: $fileName", e)
        }
    }

    override fun deleteFile(fileName: String) {
        try {
            val request = DeleteObjectRequest.builder()
                .bucket(s3Configuration.bucketName)
                .key(fileName)
                .build()

            s3Client?.deleteObject(request) ?: throw RuntimeException("S3 client is not initialized")
        } catch (e: Exception) {
            throw RuntimeException("Failed to delete file: $fileName", e)
        }
    }

    override fun listFiles(): MutableList<String?> {
        return try {
            val listRequest = ListObjectsV2Request.builder()
                .bucket(s3Configuration.bucketName)
                .build()

            s3Client?.let { client ->
                val response = client.listObjectsV2(listRequest)
                response.contents()
                    .map { it.key() }
                    .toMutableList()
            } ?: throw RuntimeException("S3 client is not initialized")
        } catch (e: Exception) {
            throw RuntimeException("Failed to list files in bucket", e)
        }
    }
}