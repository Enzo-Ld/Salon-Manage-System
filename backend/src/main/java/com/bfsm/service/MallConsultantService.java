package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.entity.SysRole;
import com.bfsm.entity.SysUser;
import com.bfsm.entity.SysUserRole;
import com.bfsm.mapper.SysRoleMapper;
import com.bfsm.mapper.SysUserMapper;
import com.bfsm.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;

@Service
public class MallConsultantService {
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysUserMapper sysUserMapper;

    public MallConsultantService(SysRoleMapper sysRoleMapper, SysUserRoleMapper sysUserRoleMapper, SysUserMapper sysUserMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysUserMapper = sysUserMapper;
    }

    public Long resolveConsultantUserId() {
        SysRole adminRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, "ADMIN")
                .last("limit 1"));
        if (adminRole == null) {
            return null;
        }
        SysUserRole relation = sysUserRoleMapper.selectOne(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, adminRole.getId())
                .last("limit 1"));
        return relation == null ? null : relation.getUserId();
    }

    public String resolveConsultantDisplayName() {
        Long userId = resolveConsultantUserId();
        if (userId == null) {
            return "";
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return "";
        }
        return user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getUsername();
    }
}
