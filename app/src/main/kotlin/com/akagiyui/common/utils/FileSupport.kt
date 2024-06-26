package com.akagiyui.common.utils

import com.akagiyui.common.exception.CreateFolderException
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files


/**
 * 读取文件内容为字符串
 *
 * @param charset 字符集，默认为 utf-8
 * @return 文件内容
 * @throws IOException 读取文件异常
 */
@Throws(IOException::class)
fun File.readToString(charset: String = "utf-8"): String {
    BufferedReader(InputStreamReader(Files.newInputStream(this.toPath()), charset)).use { reader ->
        return reader.lineSequence().joinToString(System.lineSeparator())
    }
}

/**
 * 创建父目录
 */
fun File.createParentDir() {
    val parent = this.parentFile
    if (!parent.exists()) {
        parent.mkdirs()
    }
}

/**
 * 创建目录，如果创建失败则抛出异常
 */
fun File.mkdirOrThrow(errorMessage: String = "Creating directory failed") {
    if (this.exists() && this.isDirectory) {
        return
    }
    if (!this.mkdirs()) {
        throw CreateFolderException(errorMessage)
    }
}

/**
 * 删除文件
 */
fun File.deleteIfExists() {
    if (this.exists() && !this.delete()) {
        throw IOException("Delete file failed")
    }
}
