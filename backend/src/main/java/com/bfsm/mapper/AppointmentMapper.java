package com.bfsm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bfsm.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {
    @Select("""
            SELECT COUNT(1)
            FROM appointment
            WHERE technician_id = #{technicianId}
              AND appointment_time < #{endTime}
              AND COALESCE(appointment_end_time, DATE_ADD(appointment_time, INTERVAL COALESCE(duration_min_snapshot, 60) MINUTE)) > #{startTime}
              AND status IN ('PENDING', 'APPROVED', 'WAIT_SERVICE', 'IN_SERVICE')
            """)
    long countConflict(@Param("technicianId") Long technicianId,
                       @Param("startTime") LocalDateTime startTime,
                       @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT COUNT(1)
            FROM appointment
            WHERE technician_id = #{technicianId}
              AND appointment_time < #{endTime}
              AND COALESCE(appointment_end_time, DATE_ADD(appointment_time, INTERVAL COALESCE(duration_min_snapshot, 60) MINUTE)) > #{startTime}
              AND id <> #{excludeId}
              AND status IN ('PENDING', 'APPROVED', 'WAIT_SERVICE', 'IN_SERVICE')
            """)
    long countConflictExclude(@Param("technicianId") Long technicianId,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime,
                              @Param("excludeId") Long excludeId);

    @Select("""
            SELECT COUNT(1)
            FROM appointment
            WHERE technician_id = #{technicianId}
              AND DATE(appointment_time) = #{workDate}
              AND status NOT IN ('REJECTED', 'CANCELLED')
            """)
    long countDayLoad(@Param("technicianId") Long technicianId, @Param("workDate") LocalDate workDate);

    @Select("""
            SELECT COUNT(1)
            FROM appointment
            WHERE technician_id = #{technicianId}
              AND DATE_FORMAT(appointment_time, '%Y-%m') = #{yearMonth}
              AND status = 'COMPLETED'
            """)
    long monthlyCompletedCount(@Param("technicianId") Long technicianId, @Param("yearMonth") String yearMonth);

    @Select("""
            SELECT IFNULL(SUM(amount), 0)
            FROM appointment
            WHERE technician_id = #{technicianId}
              AND DATE_FORMAT(appointment_time, '%Y-%m') = #{yearMonth}
              AND status = 'COMPLETED'
            """)
    BigDecimal monthlyAmount(@Param("technicianId") Long technicianId, @Param("yearMonth") String yearMonth);

    @Select("""
            SELECT COUNT(1)
            FROM appointment
            WHERE technician_id = #{technicianId}
              AND appointment_time < #{endTime}
              AND COALESCE(appointment_end_time, DATE_ADD(appointment_time, INTERVAL COALESCE(duration_min_snapshot, 60) MINUTE)) > #{startTime}
              AND status IN ('PENDING', 'APPROVED', 'WAIT_SERVICE', 'IN_SERVICE')
            """)
    long countConflictInRange(@Param("technicianId") Long technicianId,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT *
            FROM appointment
            WHERE technician_id = #{technicianId}
              AND appointment_time < #{endTime}
              AND COALESCE(appointment_end_time, DATE_ADD(appointment_time, INTERVAL COALESCE(duration_min_snapshot, 60) MINUTE)) > #{startTime}
              AND status IN ('PENDING', 'APPROVED', 'WAIT_SERVICE', 'IN_SERVICE')
            ORDER BY appointment_time ASC, id ASC
            """)
    List<Appointment> selectActiveConflictsInRange(@Param("technicianId") Long technicianId,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);
}
