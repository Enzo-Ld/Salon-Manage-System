package com.bfsm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bfsm.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalTime;

@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {
    @Select("""
            SELECT COUNT(1)
            FROM schedule
            WHERE technician_id = #{technicianId}
              AND work_date = #{workDate}
              AND status = 'WORKING'
              AND start_time <= #{appointmentTime}
              AND end_time > #{appointmentTime}
            """)
    long countWorkingSlot(@Param("technicianId") Long technicianId,
                          @Param("workDate") LocalDate workDate,
                          @Param("appointmentTime") LocalTime appointmentTime);

    @Select("""
            SELECT COUNT(1)
            FROM schedule
            WHERE technician_id = #{technicianId}
              AND work_date = #{workDate}
              AND status = 'WORKING'
              AND start_time <= #{startTime}
              AND end_time >= #{endTime}
            """)
    long countWorkingRange(@Param("technicianId") Long technicianId,
                           @Param("workDate") LocalDate workDate,
                           @Param("startTime") LocalTime startTime,
                           @Param("endTime") LocalTime endTime);

    @Select("""
            SELECT COUNT(1)
            FROM schedule
            WHERE technician_id = #{technicianId}
              AND work_date = #{workDate}
              AND start_time < #{endTime}
              AND end_time > #{startTime}
              AND (#{excludeId} IS NULL OR id <> #{excludeId})
            """)
    long countOverlap(@Param("technicianId") Long technicianId,
                      @Param("workDate") LocalDate workDate,
                      @Param("startTime") LocalTime startTime,
                      @Param("endTime") LocalTime endTime,
                      @Param("excludeId") Long excludeId);
}
