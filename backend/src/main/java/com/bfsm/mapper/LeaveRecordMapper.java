package com.bfsm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bfsm.entity.LeaveRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface LeaveRecordMapper extends BaseMapper<LeaveRecord> {
    @Select("""
            SELECT COUNT(1)
            FROM leave_record
            WHERE technician_id = #{technicianId}
              AND status = 'APPROVED'
              AND start_time <= #{appointmentTime}
              AND end_time > #{appointmentTime}
            """)
    long countActiveLeaveAt(@Param("technicianId") Long technicianId,
                            @Param("appointmentTime") LocalDateTime appointmentTime);

    @Select("""
            SELECT COUNT(1)
            FROM leave_record
            WHERE technician_id = #{technicianId}
              AND status = 'APPROVED'
              AND start_time < #{endTime}
              AND end_time > #{startTime}
              AND (#{excludeId} IS NULL OR id <> #{excludeId})
            """)
    long countApprovedOverlap(@Param("technicianId") Long technicianId,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime,
                              @Param("excludeId") Long excludeId);
}
