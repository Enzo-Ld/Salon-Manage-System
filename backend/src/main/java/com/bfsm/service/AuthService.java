package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.cache.CacheService;
import com.bfsm.common.AvatarUrlUtils;
import com.bfsm.common.BizException;
import com.bfsm.common.PhoneUtils;
import com.bfsm.common.SecurityUtils;
import com.bfsm.dto.AuthLoginReq;
import com.bfsm.dto.AuthRegisterReq;
import com.bfsm.dto.ProfileUpdateReq;
import com.bfsm.entity.SysRole;
import com.bfsm.entity.SysUser;
import com.bfsm.entity.SysUserRole;
import com.bfsm.mapper.SysRoleMapper;
import com.bfsm.mapper.SysUserMapper;
import com.bfsm.mapper.SysUserRoleMapper;
import com.bfsm.security.JwtProperties;
import com.bfsm.security.JwtTokenService;
import com.bfsm.security.SecurityUser;
import com.bfsm.vo.AuthTokenVo;
import com.bfsm.vo.CurrentUserVo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final CacheService cacheService;

    public AuthService(SysUserMapper userMapper, SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper,
                       PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService, JwtProperties jwtProperties,
                       CacheService cacheService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.cacheService = cacheService;
    }

    @Transactional
    public void register(AuthRegisterReq req) {
        String username = req.getUsername().trim();
        String roleCode = req.getRoleCode().trim().toUpperCase();
        String nickname = normalizeNullable(req.getNickname());
        String phone = PhoneUtils.normalize(req.getPhone());
        PhoneUtils.validateOrThrow(phone);

        SysUser exists = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (exists != null) {
            throw new BizException(4001, "用户已存在");
        }
        if (phone != null && hasUserWithPhone(phone, null)) {
            throw new BizException(4311, "电话号码已被绑定");
        }
        if (!"USER".equals(roleCode)) {
            throw new BizException(4002, "仅能注册普通用户");
        }

        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, roleCode));
        if (role == null) {
            throw new BizException(4003, "角色不存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(req.getPassword().trim()));
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setEnabled(1);
        userMapper.insert(user);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);
    }

    public AuthTokenVo login(AuthLoginReq req) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername()));
        if (user == null || user.getEnabled() == null || user.getEnabled() == 0) {
            throw new BizException(4004, "账户不存在或已被停用");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(4005, "用户名或密码不正确");
        }

        SysUserRole userRole = userRoleMapper.selectOne(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, user.getId()));
        if (userRole == null) {
            throw new BizException(4006, "用户没有角色关联");
        }
        SysRole role = roleMapper.selectById(userRole.getRoleId());
        if (role == null) {
            throw new BizException(4007, "角色信息不可用");
        }

        String token = jwtTokenService.createAccessToken(user.getId(), user.getUsername(), role.getCode());
        return new AuthTokenVo(token, jwtProperties.getAccessTokenTtlSeconds());
    }

    public CurrentUserVo currentUser() {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        return cacheService.getOrLoad(
                "auth:me:" + currentUser.getUserId(),
                CurrentUserVo.class,
                Duration.ofMinutes(20),
                () -> buildCurrentUser(currentUser)
        );
    }

    @Transactional
    public CurrentUserVo updateProfile(ProfileUpdateReq req) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        SysUser user = userMapper.selectById(currentUser.getUserId());
        if (user == null) {
            throw new BizException(401, "UNAUTHORIZED");
        }

        String phone = PhoneUtils.normalize(req.getPhone());
        PhoneUtils.validateOrThrow(phone);
        if (phone != null && hasUserWithPhone(phone, user.getId())) {
            throw new BizException(4311, "电话号码已被绑定");
        }

        user.setNickname(normalizeNullable(req.getNickname()));
        user.setPhone(phone);
        user.setAvatarUrl(AvatarUrlUtils.normalize(req.getAvatarUrl()));

        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            if (req.getCurrentPassword() == null || req.getCurrentPassword().isBlank()) {
                throw new BizException(4008, "请输入当前密码");
            }
            if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
                throw new BizException(4009, "当前密码不正确");
            }
            user.setPassword(passwordEncoder.encode(req.getNewPassword().trim()));
        }

        userMapper.updateById(user);
        cacheService.evict("auth:me:" + currentUser.getUserId());
        return buildCurrentUser(currentUser);
    }

    public void logout() {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        cacheService.evict("auth:me:" + currentUser.getUserId());
    }

    private CurrentUserVo buildCurrentUser(SecurityUser currentUser) {
        SysUser user = userMapper.selectById(currentUser.getUserId());
        if (user == null) {
            throw new BizException(401, "UNAUTHORIZED");
        }
        return new CurrentUserVo(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getPhone(),
                AvatarUrlUtils.normalize(user.getAvatarUrl()),
                currentUser.getRoleCode()
        );
    }

    private boolean hasUserWithPhone(String phone, Long excludeUserId) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone);
        if (excludeUserId != null) {
            query.ne(SysUser::getId, excludeUserId);
        }
        return userMapper.selectCount(query) > 0;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
