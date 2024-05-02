package com.akagiyui.drive.service

import com.akagiyui.drive.entity.FileInfo
import com.akagiyui.drive.model.request.PreUploadRequest
import org.springframework.web.multipart.MultipartFile

/**
 * 上传 服务接口
 *
 * @author AkagiYui
 */
interface UploadService {
    /**
     * 请求文件上传
     */
    fun requestUpload(preUploadRequest: PreUploadRequest)

    /**
     * 上传分片
     */
    fun uploadChunk(fileHash: String, chunk: MultipartFile, chunkHash: String, chunkIndex: Int)

    /**
     * 接收文件
     */
    fun receiveMultipartFiles(files: List<MultipartFile>, folder: String?): List<FileInfo>
}
