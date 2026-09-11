package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mmcove.agent.common.model.entity.ApiUser;
import com.mmcove.agent.common.util.PasswordEncoder;
import com.mmcove.agent.infra.persistence.mapper.ApiUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * API User 仓库(配额账户载体,关联平台 userId)。
 *
 * <p>旧的登录/注册/会话身份方法(login/register/session/findByAccessToken/verifyPassword/
 * existsByUsername/searchUsers)随统一平台用户体系(auth-center JWT)迁移已移除。
 * ApiUser 现仅作配额账户:Token 申请时按平台 userId 自动建账(ChatController.applyToken)。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ApiUserRepository {

    private final ApiUserMapper apiUserMapper;

    /**
     * 根据用户名查询用户。
     */
    public Optional<ApiUser> findByUsername(String username) {
        LambdaQueryWrapper<ApiUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiUser::getUsername, username);
        return Optional.ofNullable(apiUserMapper.selectOne(wrapper));
    }

    /**
     * 根据ID查询用户。
     */
    public Optional<ApiUser> findById(String id) {
        return Optional.ofNullable(apiUserMapper.selectById(id));
    }

    /**
     * 加密密码。
     */
    public String encodePassword(String rawPassword) {
        return PasswordEncoder.encode(rawPassword);
    }

    /**
     * 新增用户(密码自动加盐哈希)。
     */
    public ApiUser insert(ApiUser user) {
        if (user.getPassword() != null && !user.getPassword().startsWith("$2")) {
            user.setPassword(encodePassword(user.getPassword()));
        }
        apiUserMapper.insert(user);
        return user;
    }

    /**
     * 更新用户。
     */
    public boolean update(ApiUser user) {
        return apiUserMapper.updateById(user) > 0;
    }

    /**
     * 更新配额。
     */
    public boolean updateQuota(String userId, long quota) {
        ApiUser user = new ApiUser();
        user.setId(userId);
        user.setQuota(quota);
        return apiUserMapper.updateById(user) > 0;
    }

    /**
     * 增加配额。
     */
    public boolean increaseQuota(String userId, long delta) {
        LambdaQueryWrapper<ApiUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiUser::getId, userId)
                .apply("quota = quota + " + delta);
        return apiUserMapper.update(null, wrapper) > 0;
    }

    /**
     * 减少配额。
     */
    public boolean decreaseQuota(String userId, long delta) {
        LambdaQueryWrapper<ApiUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiUser::getId, userId)
                .apply("quota = quota - " + delta);
        return apiUserMapper.update(null, wrapper) > 0;
    }
}
