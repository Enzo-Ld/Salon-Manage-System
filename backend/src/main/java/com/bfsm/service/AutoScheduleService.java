package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.common.BizException;
import com.bfsm.dto.AutoScheduleReq;
import com.bfsm.entity.Schedule;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.LeaveRecordMapper;
import com.bfsm.mapper.ScheduleMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.vo.AutoScheduleDayVo;
import com.bfsm.vo.AutoScheduleItemVo;
import com.bfsm.vo.AutoSchedulePreviewVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AutoScheduleService {
    private static final int MAX_RANGE_DAYS = 31;
    private static final int HISTORY_LOOKBACK_DAYS = 28;

    private final TechnicianMapper technicianMapper;
    private final ScheduleMapper scheduleMapper;
    private final LeaveRecordMapper leaveRecordMapper;
    private final AppointmentMapper appointmentMapper;

    public AutoScheduleService(TechnicianMapper technicianMapper, ScheduleMapper scheduleMapper,
                               LeaveRecordMapper leaveRecordMapper, AppointmentMapper appointmentMapper) {
        this.technicianMapper = technicianMapper;
        this.scheduleMapper = scheduleMapper;
        this.leaveRecordMapper = leaveRecordMapper;
        this.appointmentMapper = appointmentMapper;
    }

    public AutoSchedulePreviewVo preview(AutoScheduleReq req) {
        validate(req);
        List<Technician> technicians = loadTechnicians(req.getTechnicianIds());
        Map<Long, Long> historicalLoads = loadHistoricalLoads(req);
        Map<Long, Long> generatedLoads = new HashMap<>();
        AutoSchedulePreviewVo preview = new AutoSchedulePreviewVo();

        int totalPlanned = 0;
        int totalGenerated = 0;
        int totalShortage = 0;
        for (LocalDate date = req.getStartDate(); !date.isAfter(req.getEndDate()); date = date.plusDays(1)) {
            if (!req.getWeekdays().contains(date.getDayOfWeek().getValue() % 7)) {
                continue;
            }
            AutoScheduleDayVo day = buildDay(req, technicians, historicalLoads, generatedLoads, date);
            preview.getDays().add(day);
            totalPlanned += day.getPlannedCount();
            totalGenerated += day.getGeneratedCount();
            totalShortage += day.getShortageCount();
        }

        preview.setTotalPlanned(totalPlanned);
        preview.setTotalGenerated(totalGenerated);
        preview.setTotalShortage(totalShortage);
        return preview;
    }

    @Transactional
    public AutoSchedulePreviewVo apply(AutoScheduleReq req) {
        AutoSchedulePreviewVo preview = preview(req);
        boolean overwriteExisting = Boolean.TRUE.equals(req.getOverwriteExisting());
        for (AutoScheduleDayVo day : preview.getDays()) {
            for (AutoScheduleItemVo item : day.getItems()) {
                if (Boolean.TRUE.equals(item.getHasLeaveConflict()) || Boolean.TRUE.equals(item.getHasExistingScheduleConflict())) {
                    continue;
                }
                if (overwriteExisting) {
                    List<Schedule> overlaps = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                            .eq(Schedule::getTechnicianId, item.getTechnicianId())
                            .eq(Schedule::getWorkDate, item.getWorkDate())
                            .lt(Schedule::getStartTime, item.getEndTime())
                            .gt(Schedule::getEndTime, item.getStartTime()));
                    for (Schedule overlap : overlaps) {
                        ensureNoAppointmentConflict(overlap);
                        scheduleMapper.deleteById(overlap.getId());
                    }
                }
                Schedule schedule = new Schedule();
                schedule.setTechnicianId(item.getTechnicianId());
                schedule.setWorkDate(item.getWorkDate());
                schedule.setStartTime(item.getStartTime());
                schedule.setEndTime(item.getEndTime());
                schedule.setStatus("WORKING");
                scheduleMapper.insert(schedule);
            }
        }
        return preview;
    }

    private AutoScheduleDayVo buildDay(AutoScheduleReq req, List<Technician> technicians,
                                       Map<Long, Long> historicalLoads, Map<Long, Long> generatedLoads,
                                       LocalDate date) {
        AutoScheduleDayVo day = new AutoScheduleDayVo();
        day.setWorkDate(date);
        day.setPlannedCount(req.getDailyTechnicianCount());

        int candidatePoolSize = Math.max(1, technicians.size());
        List<Technician> candidates = technicians.stream()
                .filter(technician -> canUseTechnician(req, date, technician.getId()))
                .sorted(Comparator
                        .comparingLong((Technician technician) -> totalLoad(technician.getId(), historicalLoads, generatedLoads))
                        .thenComparingInt(technician -> rotationRank(technician.getId(), date, candidatePoolSize))
                        .thenComparing(technician -> technician.getScore() == null ? 0D : -technician.getScore().doubleValue())
                        .thenComparing(Technician::getId))
                .limit(req.getDailyTechnicianCount())
                .toList();

        for (Technician technician : candidates) {
            AutoScheduleItemVo item = new AutoScheduleItemVo();
            item.setTechnicianId(technician.getId());
            item.setTechnicianName(technician.getName());
            item.setWorkDate(date);
            item.setStartTime(req.getStartTime());
            item.setEndTime(req.getEndTime());
            item.setStatus("WORKING");
            item.setHasLeaveConflict(false);
            item.setHasExistingScheduleConflict(false);
            item.setReason("公平轮换：优先选择近期排班较少、符合周期轮换且无请假/预约冲突的技师");
            day.getItems().add(item);
            generatedLoads.put(technician.getId(), generatedLoads.getOrDefault(technician.getId(), 0L) + 1);
        }

        day.setGeneratedCount(day.getItems().size());
        day.setShortageCount(Math.max(0, day.getPlannedCount() - day.getGeneratedCount()));
        if (day.getShortageCount() > 0) {
            day.getWarnings().add("可用技师不足，缺少 " + day.getShortageCount() + " 人");
        }
        return day;
    }

    private boolean canUseTechnician(AutoScheduleReq req, LocalDate date, Long technicianId) {
        LocalDateTime startAt = LocalDateTime.of(date, req.getStartTime());
        LocalDateTime endAt = LocalDateTime.of(date, req.getEndTime());
        if (leaveRecordMapper.countApprovedOverlap(technicianId, startAt, endAt, null) > 0) {
            return false;
        }
        if (!Boolean.TRUE.equals(req.getOverwriteExisting())
                && scheduleMapper.countOverlap(technicianId, date, req.getStartTime(), req.getEndTime(), null) > 0) {
            return false;
        }
        return appointmentMapper.countConflictInRange(technicianId, startAt, endAt) == 0;
    }

    private long totalLoad(Long technicianId, Map<Long, Long> historicalLoads, Map<Long, Long> generatedLoads) {
        return historicalLoads.getOrDefault(technicianId, 0L) + generatedLoads.getOrDefault(technicianId, 0L);
    }

    private int rotationRank(Long technicianId, LocalDate date, int candidatePoolSize) {
        WeekFields weekFields = WeekFields.ISO;
        int weekSeed = date.get(weekFields.weekBasedYear()) * 53 + date.get(weekFields.weekOfWeekBasedYear());
        int daySeed = date.getDayOfWeek().getValue();
        return Math.floorMod(technicianId.intValue() - weekSeed - daySeed, candidatePoolSize);
    }

    private Map<Long, Long> loadHistoricalLoads(AutoScheduleReq req) {
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .ge(Schedule::getWorkDate, req.getStartDate().minusDays(HISTORY_LOOKBACK_DAYS))
                .le(Schedule::getWorkDate, req.getEndDate())
                .eq(Schedule::getStatus, "WORKING"));
        return schedules.stream().collect(Collectors.groupingBy(Schedule::getTechnicianId, Collectors.counting()));
    }

    private List<Technician> loadTechnicians(List<Long> technicianIds) {
        LambdaQueryWrapper<Technician> query = new LambdaQueryWrapper<Technician>()
                .eq(Technician::getStatus, "ACTIVE")
                .orderByAsc(Technician::getId);
        if (technicianIds != null && !technicianIds.isEmpty()) {
            query.in(Technician::getId, technicianIds);
        }
        return technicianMapper.selectList(query);
    }

    private void validate(AutoScheduleReq req) {
        if (req == null || req.getStartDate() == null || req.getEndDate() == null
                || req.getStartTime() == null || req.getEndTime() == null) {
            throw new BizException(4451, "自动排班必须包含日期范围和班次时间");
        }
        if (req.getStartDate().isAfter(req.getEndDate())) {
            throw new BizException(4452, "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate()) + 1 > MAX_RANGE_DAYS) {
            throw new BizException(4453, "自动排班单次最多支持 31 天");
        }
        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new BizException(4454, "上班时间必须早于下班时间");
        }
        if (req.getDailyTechnicianCount() == null || req.getDailyTechnicianCount() <= 0) {
            throw new BizException(4455, "每天排班人数必须大于 0");
        }
        if (req.getWeekdays() == null || req.getWeekdays().isEmpty()) {
            req.setWeekdays(List.of(1, 2, 3, 4, 5));
        }
        Set<Integer> validWeekdays = Set.of(0, 1, 2, 3, 4, 5, 6);
        req.setWeekdays(req.getWeekdays().stream().filter(validWeekdays::contains).distinct().toList());
        if (req.getWeekdays().isEmpty()) {
            throw new BizException(4456, "请选择有效的工作日");
        }
    }

    private void ensureNoAppointmentConflict(Schedule schedule) {
        LocalDateTime startAt = LocalDateTime.of(schedule.getWorkDate(), schedule.getStartTime());
        LocalDateTime endAt = LocalDateTime.of(schedule.getWorkDate(), schedule.getEndTime());
        if (appointmentMapper.countConflictInRange(schedule.getTechnicianId(), startAt, endAt) > 0) {
            throw new BizException(4457, "已有预约占用的排班不能被自动覆盖");
        }
    }
}
