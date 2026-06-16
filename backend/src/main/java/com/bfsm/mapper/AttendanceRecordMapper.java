package com.bfsm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bfsm.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {
    @Select("""
            SELECT *
            FROM attendance_record
            WHERE technician_id = #{technicianId}
              AND work_date = #{workDate}
            LIMIT 1
            """)
    AttendanceRecord selectByTechnicianAndDate(@Param("technicianId") Long technicianId,
                                               @Param("workDate") LocalDate workDate);

    @Select("""
            SELECT *
            FROM attendance_record
            WHERE technician_id = #{technicianId}
            ORDER BY work_date DESC, id DESC
            LIMIT #{limit}
            """)
    List<AttendanceRecord> selectRecentByTechnician(@Param("technicianId") Long technicianId,
                                                    @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM attendance_record
            ORDER BY work_date DESC, id DESC
            LIMIT #{limit}
            """)
    List<AttendanceRecord> selectRecentAll(@Param("limit") int limit);
}
