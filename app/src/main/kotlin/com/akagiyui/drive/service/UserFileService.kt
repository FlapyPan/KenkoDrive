package com.akagiyui.drive.service

import com.akagiyui.drive.entity.FileInfo
import com.akagiyui.drive.entity.User
import com.akagiyui.drive.entity.UserFile

/**
 * 用户文件关联 服务接口
 *
 * @author AkagiYui
 */
interface UserFileService {
    /**
     * 添加用户文件与真实文件的关联
     * @param user 用户
     * @param userFileName 用户文件名
     * @param fileInfo 真实文件信息
     * @param folderId 用户文件夹ID
     */
    fun addAssociation(user: User, userFileName: String, fileInfo: FileInfo, folderId: String? = null): UserFile

    /**
     * 获取文件夹下的文件
     * @param folderId 文件夹ID
     */
    fun getFiles(folderId: String?): List<UserFile>

    /**
     * 判断文件是否有用户关联
     */
    fun existByFileId(fileId: String): Boolean

    /**
     * 获取用户文件
     */
    fun getUserFileById(id: String): UserFile

    /**
     * 获取临时文件ID
     */
    fun getTemporaryId(userFileId: String): Pair<String, UserFile>

    /**
     * 根据临时文件ID获取文件信息
     */
    fun getFileInfoByTemporaryId(temporaryId: String): UserFile

    /**
     * 删除用户文件
     */
    fun userDeleteFile(id: String)
}
