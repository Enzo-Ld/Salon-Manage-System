package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.cache.CacheService;
import com.bfsm.common.AvatarUrlUtils;
import com.bfsm.common.BizException;
import com.bfsm.common.PageUtils;
import com.bfsm.common.PhoneUtils;
import com.bfsm.common.SecurityUtils;
import com.bfsm.dto.AdminCreateUserReq;
import com.bfsm.entity.SysRole;
import com.bfsm.entity.SysUser;
import com.bfsm.entity.SysUserRole;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.SysRoleMapper;
import com.bfsm.mapper.SysUserMapper;
import com.bfsm.mapper.SysUserRoleMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.security.SecurityUser;
import com.bfsm.vo.AdminUserVo;
import com.bfsm.vo.PageResultVo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminUserService {
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final TechnicianMapper technicianMapper;
    private final PasswordEncoder passwordEncoder;
    private final CacheService cacheService;

    public AdminUserService(SysUserMapper sysUserMapper, SysRoleMapper sysRoleMapper, SysUserRoleMapper sysUserRoleMapper,
                            TechnicianMapper technicianMapper, PasswordEncoder passwordEncoder,
                            CacheService cacheService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.technicianMapper = technicianMapper;
        this.passwordEncoder = passwordEncoder;
        this.cacheService = cacheService;
    }

    @Transactional
    public void createUser(AdminCreateUserReq req) {
        String username = req.getUsername().trim();
        String password = req.getPassword().trim();
        String nickname = normalizeNullable(req.getNickname());
        String phone = PhoneUtils.normalize(req.getPhone());
        PhoneUtils.validateOrThrow(phone);

        SysUser exists = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (exists != null) {
            throw new BizException(4302, "Username already exists");
        }
        if (phone != null && hasUserWithPhone(phone)) {
            throw new BizException(4311, "Phone number is already bound to another account");
        }

        SysRole role = requireRole(req.getRoleCode());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setEnabled(1);
        sysUserMapper.insert(user);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        sysUserRoleMapper.insert(userRole);

        syncTechnicianProfile(user, role.getCode());
    }

    public List<AdminUserVo> listUsers(String keyword, String roleCode, Boolean enabled) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            query.and(wrapper -> wrapper.like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getNickname, keyword)
                    .or()
                    .like(SysUser::getPhone, keyword));
        }
        if (enabled != null) {
            query.eq(SysUser::getEnabled, enabled ? 1 : 0);
        }
        List<SysUser> users = sysUserMapper.selectList(query);
        if (users.isEmpty()) {
            return List.of();
        }
        Map<Long, SysRole> rolesByUserId = loadUserRoles(users.stream().map(SysUser::getId).toList());
        return users.stream()
                .map(user -> toVo(user, rolesByUserId.get(user.getId())))
                .filter(item -> roleCode == null || roleCode.isBlank() || roleCode.equals(item.getRoleCode()))
                .toList();
    }

    public PageResultVo<AdminUserVo> pageUsers(Long pageNum, Long pageSize, String keyword, String roleCode, Boolean enabled) {
        return PageUtils.paginate(listUsers(keyword, roleCode, enabled), pageNum, pageSize);
    }

    @Transactional
    public void updateUserEnabled(Long userId, boolean enabled) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if (Objects.equals(currentUser.getUserId(), userId) && !enabled) {
            throw new BizException(4300, "Cannot disable the current logged-in account");
        }
        SysUser user = requireUser(userId);
        user.setEnabled(enabled ? 1 : 0);
        sysUserMapper.updateById(user);
        cacheService.evict("auth:me:" + userId);
    }

    @Transactional
    public void updateUserRole(Long userId, String roleCode) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        String normalizedRoleCode = roleCode.trim().toUpperCase();
        if (Objects.equals(currentUser.getUserId(), userId) && !"ADMIN".equals(normalizedRoleCode)) {
            throw new BizException(4301, "Cannot change the current logged-in admin to another role");
        }

        SysRole role = requireRole(normalizedRoleCode);
        SysUser user = requireUser(userId);

        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        sysUserRoleMapper.insert(userRole);

        syncTechnicianProfile(user, normalizedRoleCode);
        cacheService.evict("auth:me:" + userId);
    }

    @Transactional
    public void resetPassword(Long userId) {
        SysUser user = requireUser(userId);
        user.setPassword(passwordEncoder.encode("123456"));
        sysUserMapper.updateById(user);
    }

    private Map<Long, SysRole> loadUserRoles(List<Long> userIds) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, userIds));
        if (userRoles.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysRole> roleMap = sysRoleMapper.selectBatchIds(
                        userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SysRole::getId, Function.identity()));
        return userRoles.stream().collect(Collectors.toMap(
                SysUserRole::getUserId,
                item -> roleMap.get(item.getRoleId()),
                (left, right) -> left
        ));
    }

    private AdminUserVo toVo(SysUser user, SysRole role) {
        AdminUserVo vo = new AdminUserVo();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setAvatarUrl(AvatarUrlUtils.normalize(user.getAvatarUrl()));
        vo.setEnabled(user.getEnabled());
        vo.setRoleCode(role == null ? "UNKNOWN" : role.getCode());
        vo.setRoleName(role == null ? "Unknown role" : role.getName());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    private SysRole requireRole(String roleCode) {
        String normalizedRoleCode = roleCode == null ? "" : roleCode.trim().toUpperCase();
        SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, normalizedRoleCode));
        if (role == null) {
            throw new BizException(404, "Role does not exist");
        }
        return role;
    }

    private void syncTechnicianProfile(SysUser user, String roleCode) {
        Technician technician = technicianMapper.selectOne(new LambdaQueryWrapper<Technician>()
                .eq(Technician::getUserId, user.getId())
                .last("limit 1"));
        if ("TECH".equals(roleCode)) {
            if (technician == null) {
                technician = new Technician();
                technician.setUserId(user.getId());
                technician.setName(resolveTechnicianName(user));
                technician.setLevelName("中级技师");
                technician.setScore(BigDecimal.valueOf(5));
                technician.setRatingAvg(BigDecimal.valueOf(5));
                technician.setRatingCount(0);
                technician.setStatus("ACTIVE");
                technician.setCreatedAt(LocalDateTime.now());
                technician.setUpdatedAt(LocalDateTime.now());
                technicianMapper.insert(technician);
            } else {
                technician.setName(resolveTechnicianName(user));
                technician.setStatus("ACTIVE");
                technician.setUpdatedAt(LocalDateTime.now());
                technicianMapper.updateById(technician);
            }
            return;
        }

        if (technician != null && !"INACTIVE".equals(technician.getStatus())) {
            technician.setStatus("INACTIVE");
            technician.setUpdatedAt(LocalDateTime.now());
            technicianMapper.updateById(technician);
        }
    }

    private String resolveTechnicianName(SysUser user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname().trim();
        }
        return user.getUsername();
    }

    private SysUser requireUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "User does not exist");
        }
        return user;
    }

    private boolean hasUserWithPhone(String phone) {
        return sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)) > 0;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
