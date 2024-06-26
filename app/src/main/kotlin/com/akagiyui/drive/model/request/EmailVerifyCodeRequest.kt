package com.akagiyui.drive.model.request

import jakarta.validation.constraints.*

/**
 * 邮箱验证码发送请求 请求体
 *
 * @author AkagiYui
 */
class EmailVerifyCodeRequest {
    /**
     * 邮箱
     */
    @NotBlank(message = "{email cannot be empty}")
    @NotNull(message = "{email is missing}")
    @Email(message = "{email format is incorrect}")
    lateinit var email: String

    /**
     * 用户名
     * <p>
     * 仅限字母、数字、下划线
     */
    @NotBlank(message = "{username cannot be empty}")
    @NotNull(message = "{username is missing}")
    @Size(min = 3, max = 20, message = "{username length must be between 3 and 20}")
    // ^[a-zA-Z0-9_]+$
    @Pattern(regexp = "^\\w+$", message = "{username can only contain letters, numbers and underscores}")
    lateinit var username: String

    /**
     * 密码
     */
    @NotBlank(message = "{password cannot be empty}")
    @NotNull(message = "{password is missing}")
    @Size(min = 8, max = 64, message = "{password length must be more than 8}")
    lateinit var password: String

    /**
     * 昵称
     */
    @Size(max = 20, message = "{nickname length must be less than 20}")
    var nickname: String? = null
}
