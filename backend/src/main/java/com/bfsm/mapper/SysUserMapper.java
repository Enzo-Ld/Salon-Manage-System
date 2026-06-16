package com.bfsm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bfsm.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @Select("""
            SELECT su.id
            FROM sys_user su
            JOIN sys_user_role sur ON sur.user_id = su.id
            JOIN sys_role sr ON sr.id = sur.role_id
            WHERE sr.code = #{roleCode}
              AND su.enabled = 1
            """)
    List<Long> selectIdsByRoleCode(@Param("roleCode") String roleCode);
}

