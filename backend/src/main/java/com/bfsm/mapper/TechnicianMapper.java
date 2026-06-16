package com.bfsm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bfsm.entity.Technician;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TechnicianMapper extends BaseMapper<Technician> {
    @Select("""
            SELECT t.*
            FROM technician t
            INNER JOIN technician_service ts ON ts.technician_id = t.id
            WHERE t.status = 'ACTIVE'
              AND ts.service_item_id = #{serviceItemId}
            ORDER BY t.id
            """)
    List<Technician> selectActiveByServiceItemId(@Param("serviceItemId") Long serviceItemId);

    @Select("""
            SELECT COUNT(1)
            FROM technician t
            INNER JOIN technician_service ts ON ts.technician_id = t.id
            WHERE t.id = #{technicianId}
              AND t.status = 'ACTIVE'
              AND ts.service_item_id = #{serviceItemId}
            """)
    long countServiceSupport(@Param("technicianId") Long technicianId, @Param("serviceItemId") Long serviceItemId);
}
