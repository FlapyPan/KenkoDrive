package com.akagiyui.drive.service.impl

import cn.hutool.core.util.RandomUtil
import com.akagiyui.common.ResponseEnum
import com.akagiyui.common.delegate.LoggerDelegate
import com.akagiyui.common.exception.CustomException
import com.akagiyui.common.exception.UnAuthorizedException
import com.akagiyui.common.token.TokenTemplate
import com.akagiyui.common.utils.BASE_NUMBER
import com.akagiyui.common.utils.hasText
import com.akagiyui.common.utils.random
import com.akagiyui.drive.component.RedisCache
import com.akagiyui.drive.entity.Role
import com.akagiyui.drive.entity.User
import com.akagiyui.drive.model.CacheConstants
import com.akagiyui.drive.model.UserFilter
import com.akagiyui.drive.model.request.AddUserRequest
import com.akagiyui.drive.model.request.EmailVerifyCodeRequest
import com.akagiyui.drive.model.request.RegisterConfirmRequest
import com.akagiyui.drive.model.request.UpdateUserInfoRequest
import com.akagiyui.drive.repository.UserRepository
import com.akagiyui.drive.service.*
import jakarta.annotation.Resource
import jakarta.persistence.criteria.Predicate
import org.jetbrains.annotations.NotNull
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

/**
 * 用户服务实现类
 *
 * @author AkagiYui
 */
@Service
class UserServiceImpl(
    private val repository: UserRepository,
    private val redisCache: RedisCache,
    private val mailService: MailService,
    private val smsService: SmsService,
    private val settingService: SettingService,
    private val roleService: RoleService,
    private val tokenTemplate: TokenTemplate,
    private val passwordEncoder: PasswordEncoder,
) : UserService {
    private val log by LoggerDelegate()
    private var emailVerifyTimeout: Long = settingService.mailVerifyCodeTimeout.toLong()

    @Cacheable(value = [CacheConstants.USER_BY_ID], key = "#id")
    override fun findUserById(id: String): User {
        return repository.findById(id).orElseThrow { CustomException(ResponseEnum.NOT_FOUND) }
    }

    override fun findUserByIds(@NotNull ids: List<String>): List<User> {
        return repository.findAllById(ids)
    }

    private fun findUserByIdWithCache(id: String): User {
        return repository.findById(id).orElseThrow { CustomException(ResponseEnum.NOT_FOUND) }
    }

    override fun register(@NotNull user: User): User {
        TODO("register")
    }

    @Cacheable(cacheNames = [CacheConstants.USER_PAGE], key = "{#index, #size, #userFilter }")
    override fun find(index: Int, size: Int, userFilter: UserFilter?): Page<User> {
        val pageable: Pageable = PageRequest.of(index, size)

        val specification: Specification<User> = Specification { root, _, cb ->
            if (userFilter != null && userFilter.expression.hasText()) {
                val queryString = "%${userFilter.expression}%"
                val usernamePredicate: Predicate = cb.like(root["username"], queryString)
                val nicknamePredicate: Predicate = cb.like(root["nickname"], queryString)
                val emailPredicate: Predicate = cb.like(root["email"], queryString)
                cb.or(usernamePredicate, nicknamePredicate, emailPredicate)
            } else null
        }

        return repository.findAll(specification, pageable)
    }

    @Cacheable(cacheNames = [CacheConstants.USER_LIST])
    override fun find(): List<User> {
        return repository.findAll()
    }

    @CacheEvict(
        cacheNames = [
            CacheConstants.USER_BY_ID,
            CacheConstants.USER_PAGE,
            CacheConstants.USER_LIST,
            CacheConstants.USER_EXIST,
        ],
        allEntries = true,
    )
    override fun addUser(user: AddUserRequest): User {
        if (repository.existsByUsername(user.username)) {
            throw CustomException(ResponseEnum.USER_EXIST)
        }

        val realUser = user.toUser().apply {
            if (user.email.hasText()) {
                if (repository.existsByEmail(user.email!!)) {
                    throw CustomException(ResponseEnum.EMAIL_EXIST)
                }
                email = user.email
            }
            nickname = user.nickname.takeIf { it.hasText() } ?: user.username
            password = encryptPassword(user.username, user.password)
            disabled = false
            roles = roleService.getAllDefaultRoles()
        }

        repository.save(realUser)
        return realUser
    }

    @CacheEvict(
        cacheNames = [
            CacheConstants.USER_BY_ID,
            CacheConstants.USER_DETAILS,
            CacheConstants.USER_LOGIN_DETAILS,
            CacheConstants.USER_PAGE,
            CacheConstants.USER_LIST,
            CacheConstants.USER_EXIST,
        ],
        allEntries = true,
    )
    override fun delete(id: String) {
        if (!repository.existsById(id)) {
            throw CustomException(ResponseEnum.NOT_FOUND)
        }
        repository.deleteById(id)
    }

    @Cacheable(cacheNames = [CacheConstants.USER_EXIST], key = "#id")
    override fun isExist(id: String): Boolean {
        return repository.existsById(id)
    }

    @Transactional
    override fun getAccessToken(username: String, password: String): String {
        val user = repository.getFirstByUsernameOrEmail(username) ?: throw UnAuthorizedException()
        if (!passwordEncoder.matches(password, user.password)) {
            throw UnAuthorizedException()
        }
        return tokenTemplate.createToken(user.id)
    }

    override fun sendEmailVerifyCode(verifyRequest: EmailVerifyCodeRequest) {
        if (!settingService.registerEnabled) {
            throw CustomException(ResponseEnum.REGISTER_DISABLED)
        }

        // 检查该邮箱/用户名是否在 redis 中等待验证
        val redisKeyEmail = "emailVerifyCode:email:${verifyRequest.email}"
        if (redisKeyEmail in redisCache) {
            throw CustomException(ResponseEnum.EMAIL_EXIST)
        }
        val redisKeyUsername = "emailVerifyCode:username:${verifyRequest.username}"
        if (redisKeyUsername in redisCache) {
            throw CustomException(ResponseEnum.USER_EXIST)
        }
        // 检查该邮箱是否已经注册
        if (repository.existsByEmail(verifyRequest.email)) {
            throw CustomException(ResponseEnum.EMAIL_EXIST)
        }
        // 检查用户名是否已经注册
        if (repository.existsByUsername(verifyRequest.username)) {
            throw CustomException(ResponseEnum.USER_EXIST)
        }
        // 生成验证码
        val verifyCode = RandomUtil.randomNumbers(6)
        redisCache[redisKeyEmail, emailVerifyTimeout, TimeUnit.MINUTES] = verifyCode
        redisCache[redisKeyUsername, emailVerifyTimeout, TimeUnit.MINUTES] = verifyCode
        mailService.sendEmailVerifyCode(verifyRequest.email, verifyCode, emailVerifyTimeout)
        // 将注册信息存入 redis
        val registerInfoKey = "registerInfo:${verifyRequest.email}"
        redisCache[registerInfoKey, emailVerifyTimeout + 1, TimeUnit.MINUTES] = verifyRequest
    }

    /**
     * 用户服务，自我注入，以触发 AOP 代理
     */
    @Resource
    @Lazy
    lateinit var selfProxy: UserService

    override fun confirmRegister(registerConfirmRequest: RegisterConfirmRequest) {
        // 从 redis 取回验证码
        val redisKeyEmail = "emailVerifyCode:email:${registerConfirmRequest.email}"
        val verifyCode =
            redisCache.get<String>(redisKeyEmail) ?: throw CustomException(ResponseEnum.VERIFY_CODE_NOT_FOUND)
        // 检查验证码是否正确
        if (registerConfirmRequest.verifyCode != verifyCode) {
            throw CustomException(ResponseEnum.VERIFY_CODE_NOT_FOUND)
        }
        // 从 redis 取回用户注册信息
        val verifyRequest: EmailVerifyCodeRequest? = redisCache["registerInfo:${registerConfirmRequest.email}"]
        if (verifyRequest == null) {
            log.error("Register info not found: {}", registerConfirmRequest.email)
            throw CustomException(ResponseEnum.VERIFY_CODE_NOT_FOUND)
        }
        // 添加用户
        selfProxy.addUser(AddUserRequest().apply {
            username = verifyRequest.username
            password = verifyRequest.password
            email = verifyRequest.email
        })

        // 删除 redis 中的验证码和注册信息
        redisCache.delete(redisKeyEmail)
        redisCache.delete("emailVerifyCode:username:${verifyRequest.username}")
        redisCache.delete("registerInfo:${registerConfirmRequest.email}")
    }

    override fun encryptPassword(username: String, password: String): String {
        return encryptPassword(username, password, false)
    }

    override fun encryptPassword(username: String, password: String, raw: Boolean): String {
        val encode = password // 密码加密核心
        if (raw) {
            return encode
        }
        return passwordEncoder.encode(encode)
    }

    @CacheEvict(
        cacheNames = [
            CacheConstants.USER_BY_ID,
            CacheConstants.USER_DETAILS,
            CacheConstants.USER_LOGIN_DETAILS,
            CacheConstants.USER_PAGE,
            CacheConstants.USER_LIST,
            CacheConstants.USER_EXIST,
        ],
        allEntries = true,
    )
    override fun updateInfo(user: User, newUserInfo: UpdateUserInfoRequest) {
        if (newUserInfo.nickname.hasText()) {
            user.nickname = newUserInfo.nickname
        }
        if (newUserInfo.email.hasText()) {
            user.email = newUserInfo.email
        }
        repository.save(user)
    }

    @Transactional
    override fun getPermission(user: User): Set<String> {
        return user.roles
            .asSequence()
            .map { it.permissions }
            .flatten()
            .map { it.name }
            .toSet()
    }

    override fun getRole(user: User): Set<String> {
        return user.roles.stream().map(Role::id).collect(Collectors.toSet())
    }

    @CacheEvict(
        cacheNames = [
            CacheConstants.USER_BY_ID,
            CacheConstants.USER_DETAILS,
            CacheConstants.USER_LOGIN_DETAILS,
            CacheConstants.USER_PAGE,
            CacheConstants.USER_LIST,
            CacheConstants.USER_EXIST,
        ],
        allEntries = true,
    )
    override fun disable(id: String, disabled: Boolean) {
        val user = findUserByIdWithCache(id)
        // todo 检查是否为超级管理员
        user.disabled = disabled
        repository.save(user)
    }

    override fun resetPassword(id: String, newPassword: String) {
        val user = findUserByIdWithCache(id)
        user.password = encryptPassword(user.username!!, newPassword)
        repository.save(user)
    }

    @Transactional
    override fun addRoles(userId: String, id: Set<String>) {
        val user = findUserByIdWithCache(userId)
        val roles = roleService.find(id)
        user.roles.addAll(roles)
        repository.save(user)
    }

    @Transactional
    override fun removeRoles(userId: String, id: Set<String>) {
        val user = findUserByIdWithCache(userId)
        val roles = roleService.find(id)
        user.roles.removeAll(roles)
        repository.save(user)
    }

    @CacheEvict(
        cacheNames = [
            CacheConstants.USER_DETAILS,
            CacheConstants.USER_LOGIN_DETAILS,
            CacheConstants.USER_PAGE,
            CacheConstants.USER_LIST,
        ],
        allEntries = true,
    )
    override fun updateInfo(id: String, userInfo: UpdateUserInfoRequest) {
        val user = findUserByIdWithCache(id)
        if (userInfo.nickname.hasText()) {
            user.nickname = userInfo.nickname
        }
        if (userInfo.email.hasText()) {
            user.email = userInfo.email
        }
        if (userInfo.password.hasText()) {
            user.password = encryptPassword(user.username!!, userInfo.password!!)
        }
        repository.save(user)
    }

    @Transactional
    override fun getRoles(id: String): Set<Role> {
        val user = findUserByIdWithCache(id)
        return HashSet(user.roles)
    }

    override fun sendSmsOneTimePassword(phone: String) {
        val verifyCode = String.random(String.BASE_NUMBER, 6)
        val redisKey = "smsCode:$phone"
        redisCache[redisKey, 10, TimeUnit.MINUTES] = verifyCode
        // 发送短信验证码
        smsService.sendSmsOneTimePassword(phone, verifyCode)
    }

    @CacheEvict(
        cacheNames = [
            CacheConstants.USER_DETAILS,
            CacheConstants.USER_LOGIN_DETAILS,
            CacheConstants.USER_PAGE,
            CacheConstants.USER_LIST,
        ],
        allEntries = true,
    )
    override fun getAccessTokenBySms(phone: String, code: String): String {
        val redisKey = "smsCode:$phone"
        val verifyCode = redisCache.get<String>(redisKey) ?: throw CustomException(ResponseEnum.VERIFY_CODE_NOT_FOUND)
        if (verifyCode != code) {
            throw CustomException(ResponseEnum.VERIFY_CODE_NOT_FOUND)
        }
        redisCache.delete(redisKey)
        // 判断用户是否存在
        var user = repository.findByPhone(phone)
        if (user == null) {
            // 不存在则新增用户
            val newUser = User().apply {
                this.phone = phone
                nickname = phone
                roles = roleService.getAllDefaultRoles()
            }
            user = repository.save(newUser)
        }

        return tokenTemplate.createToken(user.id)
    }
}
