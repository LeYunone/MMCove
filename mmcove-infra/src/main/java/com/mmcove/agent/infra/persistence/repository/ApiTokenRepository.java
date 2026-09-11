package com.mmcove.agent.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mmcove.agent.common.model.entity.ApiToken;
import com.mmcove.agent.infra.persistence.mapper.ApiTokenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * API Token 仓库。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ApiTokenRepository {

    private final ApiTokenMapper apiTokenMapper;

    /**
     * 根据Key查询Token。
     */
    public Optional<ApiToken> findByKey(String key) {
        LambdaQueryWrapper<ApiToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiToken::getTokenKey, key);
        return Optional.ofNullable(apiTokenMapper.selectOne(wrapper));
    }

    /**
     * 根据ID查询Token。
     */
    public Optional<ApiToken> findById(Long id) {
        return Optional.ofNullable(apiTokenMapper.selectById(id));
    }

    /**
     * 查询用户的所有Token。
     */
    public List<ApiToken> findByUserId(String userId) {
        LambdaQueryWrapper<ApiToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiToken::getUserId, userId)
                .orderByDesc(ApiToken::getId);
        return apiTokenMapper.selectList(wrapper);
    }

    /**
     * 查询用户的启用 Token(一人一个模型:取 id 最小的一条,即最早创建的启用 Token)。
     * JWT 认证后按 userId 查配额/渠道用;无启用 Token 返回 empty(调用方匿名兜底)。
     */
    public Optional<ApiToken> findActiveByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<ApiToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiToken::getUserId, userId)
                .eq(ApiToken::getStatus, ApiToken.STATUS_ENABLED)
                .orderByAsc(ApiToken::getId)
                .last("LIMIT 1");
        return Optional.ofNullable(apiTokenMapper.selectOne(wrapper));
    }

    /**
     * 分页查询用户的Token。
     */
    public List<ApiToken> findByUserIdPaged(String userId, int offset, int limit) {
        LambdaQueryWrapper<ApiToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiToken::getUserId, userId)
                .orderByDesc(ApiToken::getId)
                .last("LIMIT " + offset + ", " + limit);
        return apiTokenMapper.selectList(wrapper);
    }

    /**
     * 搜索Token。
     */
    public List<ApiToken> search(String userId, String keyword, String keyPrefix) {
        return search(userId, keyword, keyPrefix, null);
    }

    /**
     * 搜索Token（支持按状态过滤）。
     */
    public List<ApiToken> search(String userId, String keyword, String keyPrefix, Integer status) {
        LambdaQueryWrapper<ApiToken> wrapper = new LambdaQueryWrapper<>();

        if (userId != null) {
            wrapper.eq(ApiToken::getUserId, userId);
        }

        if (status != null) {
            wrapper.eq(ApiToken::getStatus, status);
        }

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w
                .like(ApiToken::getName, keyword)
                .or()
                .like(ApiToken::getTokenKey, keyword)
            );
        }
        if (keyPrefix != null && !keyPrefix.isEmpty()) {
            wrapper.like(ApiToken::getTokenKey, keyPrefix);
        }

        wrapper.orderByDesc(ApiToken::getId);
        return apiTokenMapper.selectList(wrapper);
    }

    /**
     * 按状态分页查询Token。
     */
    public List<ApiToken> findByStatusPaged(Integer status, int offset, int limit) {
        LambdaQueryWrapper<ApiToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiToken::getStatus, status)
                .orderByDesc(ApiToken::getId)
                .last("LIMIT " + offset + ", " + limit);
        return apiTokenMapper.selectList(wrapper);
    }

    /**
     * 新增Token。
     */
    public ApiToken insert(ApiToken token) {
        apiTokenMapper.insert(token);
        return token;
    }

    /**
     * 更新Token。
     */
    public boolean update(ApiToken token) {
        return apiTokenMapper.updateById(token) > 0;
    }

    /**
     * 更新访问时间。
     */
    public void updateAccessedTime(Long tokenId) {
        LambdaUpdateWrapper<ApiToken> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApiToken::getId, tokenId)
                .set(ApiToken::getAccessedTime, System.currentTimeMillis() / 1000);
        apiTokenMapper.update(null, wrapper);
    }

    /**
     * 减少配额。
     * 使用 COALESCE 防御 used_quota 为 NULL 时 NULL + 1 = NULL 的问题。
     */
    public boolean decreaseQuota(Long tokenId, int quota) {
        LambdaUpdateWrapper<ApiToken> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApiToken::getId, tokenId)
                .setSql("remain_quota = COALESCE(remain_quota, 0) - " + quota)
                .setSql("used_quota = COALESCE(used_quota, 0) + " + quota)
                .set(ApiToken::getAccessedTime, System.currentTimeMillis() / 1000);
        return apiTokenMapper.update(null, wrapper) > 0;
    }

    /**
     * 增加配额。
     * 使用 COALESCE 防御 NULL 值。
     */
    public boolean increaseQuota(Long tokenId, int quota) {
        LambdaUpdateWrapper<ApiToken> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApiToken::getId, tokenId)
                .setSql("remain_quota = COALESCE(remain_quota, 0) + " + quota)
                .setSql("used_quota = GREATEST(COALESCE(used_quota, 0) - " + quota + ", 0)")
                .set(ApiToken::getAccessedTime, System.currentTimeMillis() / 1000);
        return apiTokenMapper.update(null, wrapper) > 0;
    }

    /**
     * 更新Token状态。
     */
    public boolean updateStatus(Long tokenId, int status) {
        LambdaUpdateWrapper<ApiToken> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApiToken::getId, tokenId)
                .set(ApiToken::getStatus, status);
        return apiTokenMapper.update(null, wrapper) > 0;
    }

    /**
     * 删除Token（软删除）。
     */
    public boolean delete(Long tokenId) {
        return apiTokenMapper.deleteById(tokenId) > 0;
    }

    /**
     * 删除用户的所有Token。
     */
    public int deleteByUserId(String userId) {
        LambdaQueryWrapper<ApiToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiToken::getUserId, userId);
        return apiTokenMapper.delete(wrapper);
    }

    /**
     * 根据设备唯一标识查询Token。
     */
    public Optional<ApiToken> findByUniqueKey(String uniqueKey) {
        LambdaQueryWrapper<ApiToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiToken::getUniqueKey, uniqueKey);
        return Optional.ofNullable(apiTokenMapper.selectOne(wrapper));
    }

    /**
     * 更新设备验证状态。
     */
    public boolean updateDeviceVerification(Long tokenId, String useVerification, Long useVerificationTime) {
        LambdaUpdateWrapper<ApiToken> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApiToken::getId, tokenId)
                .set(ApiToken::getUseVerification, useVerification)
                .set(ApiToken::getUseVerificationTime, useVerificationTime);
        return apiTokenMapper.update(null, wrapper) > 0;
    }
}
