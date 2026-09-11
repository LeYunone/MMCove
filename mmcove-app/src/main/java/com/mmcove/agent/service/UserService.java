package com.mmcove.agent.service;

import com.mmcove.agent.common.crypto.BcryptUtil;
import com.mmcove.agent.common.model.entity.ApiUser;
import com.mmcove.agent.infra.persistence.repository.ApiUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 自建用户体系：注册 / 登录 / 查询。
 *
 * <p>密码使用 BCrypt（{@link BcryptUtil}）。注意 {@link ApiUserRepository#insert} 对 $2 开头的
 * 密码不会再二次编码，因此这里先 BCrypt 哈希再 insert，存储的是 BCrypt 摘要。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final ApiUserRepository apiUserRepository;

    public ApiUser register(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码长度至少 6 位");
        }
        if (apiUserRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        ApiUser user = new ApiUser();
        user.setUsername(username);
        user.setPassword(BcryptUtil.hashText(password)); // $2a$... → insert 不会再次 SHA-256
        user.setRole(ApiUser.ROLE_COMMON_USER);
        user.setStatus(ApiUser.STATUS_ENABLED);
        user.setQuota(0L);
        return apiUserRepository.insert(user);
    }

    public ApiUser login(String username, String password) {
        ApiUser user = apiUserRepository.findByUsername(username)
                .orElseThrow(() -> badCredentials());
        if (user.getPassword() == null || !BcryptUtil.verifyText(user.getPassword(), password)) {
            throw badCredentials();
        }
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已被禁用");
        }
        log.info("[登录] 用户登录成功: username={}, userId={}", username, user.getId());
        return user;
    }

    public ApiUser getById(String id) {
        return apiUserRepository.findById(id).orElse(null);
    }

    private static ResponseStatusException badCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }
}
