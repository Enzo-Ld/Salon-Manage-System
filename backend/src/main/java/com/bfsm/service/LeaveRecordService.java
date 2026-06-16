package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bfsm.common.BizException;
import com.bfsm.common.PageUtils;
import com.bfsm.common.SecurityUtils;
import com.bfsm.entity.LeaveRecord;
import com.bfsm.entity.Appointment;
import com.bfsm.entity.ServiceItem;
import com.bfsm.entity.SysUser;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.AppointmentLogMapper;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.LeaveRecordMapper;
import com.bfsm.mapper.ScheduleMapper;
import com.bfsm.mapper.ServiceItemMapper;
import com.bfsm.mapper.SysUserMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.security.SecurityUser;
import com.bfsm.vo.LeaveAppointmentConflictVo;
import com.bfsm.vo.LeaveConflictPreviewVo;
import com.bfsm.vo.PageResultVo;
import com.bfsm.vo.TechnicianCandidateVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class LeaveRecordService {
    private static final Set<String> ALLOWED_STATUS = Set.of("PENDING", "APPROVED", "REJECTED", "CANCELLED");

    private final LeaveRecordMapper leaveRecordMapper;
    private final TechnicianMapper technicianMapper;
    private final AppointmentMapper appointmentMapper;
    private final NotificationService notificationService;
    private final ScheduleMapper scheduleMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final SysUserMapper sysUserMapper;

    public LeaveRecordService(LeaveRecordMapper leaveRecordMapper, TechnicianMapper technicianMapper,
                              AppointmentMapper appointmentMapper, NotificationService notificationService,
                              ScheduleMapper scheduleMapper, ServiceItemMapper serviceItemMapper,
                              SysUserMapper sysUserMapper) {
        this.leaveRecordMapper = leaveRecordMapper;
        this.technicianMapper = technicianMapper;
        this.appointmentMapper = appointmentMapper;
        this.notificationService = notificationService;
        this.scheduleMapper = scheduleMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.sysUserMapper = sysUserMapper;
    }

    public List<LeaveRecord> listForCurrentRole() {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        LambdaQueryWrapper<LeaveRecord> query = new LambdaQueryWrapper<LeaveRecord>()
                .orderByDesc(LeaveRecord::getStartTime)
                .orderByDesc(LeaveRecord::getId);

        if ("TECH".equals(currentUser.getRoleCode())) {
            Technician technician = currentTechnicianOrThrow(currentUser.getUserId());
            query.eq(LeaveRecord::getTechnicianId, technician.getId());
        } else if (!"ADMIN".equals(currentUser.getRoleCode())) {
            throw new BizException(403, "No permission to view leave records");
        }

        return leaveRecordMapper.selectList(query);
    }

    public PageResultVo<LeaveRecord> pageForCurrentRole(Long pageNum, Long pageSize) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        LambdaQueryWrapper<LeaveRecord> query = new LambdaQueryWrapper<LeaveRecord>()
                .orderByDesc(LeaveRecord::getStartTime)
                .orderByDesc(LeaveRecord::getId);

        if ("TECH".equals(currentUser.getRoleCode())) {
            Technician technician = currentTechnicianOrThrow(currentUser.getUserId());
            query.eq(LeaveRecord::getTechnicianId, technician.getId());
        } else if (!"ADMIN".equals(currentUser.getRoleCode())) {
            throw new BizException(403, "No permission to view leave records");
        }

        Page<LeaveRecord> page = new Page<>(
                PageUtils.normalizePageNum(pageNum),
                PageUtils.normalizePageSize(pageSize)
        );
        return PageResultVo.from(leaveRecordMapper.selectPage(page, query));
    }

    @Transactional
    public LeaveRecord save(LeaveRecord body) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if ("TECH".equals(currentUser.getRoleCode())) {
            if (body.getId() != null) {
                throw new BizException(4301, "Technicians can only submit new leave requests");
            }
            Technician technician = currentTechnicianOrThrow(currentUser.getUserId());
            body.setTechnicianId(technician.getId());
            body.setStatus("PENDING");
        } else if ("ADMIN".equals(currentUser.getRoleCode())) {
            if (body.getId() != null) {
                throw new BizException(4302, "Please use review actions to update existing leave records");
            }
            if (body.getTechnicianId() == null) {
                throw new BizException(4303, "Technician is required");
            }
            Technician technician = technicianMapper.selectById(body.getTechnicianId());
            if (technician == null) {
                throw new BizException(4304, "Technician not found");
            }
            if (body.getStatus() == null || body.getStatus().isBlank()) {
                body.setStatus("APPROVED");
            }
        } else {
            throw new BizException(403, "No permission to save leave records");
        }

        validateLeaveRecord(body);
        if ("APPROVED".equals(body.getStatus())) {
            ensureNoApprovedLeaveConflict(body.getTechnicianId(), body.getStartTime(), body.getEndTime(), body.getId());
            ensureNoAppointmentConflict(body.getTechnicianId(), body.getStartTime(), body.getEndTime());
        }

        body.setCreatedByUserId(currentUser.getUserId());
        body.setReviewedByUserId(null);
        body.setReviewedAt(null);
        body.setReviewRemark(null);
        if ("APPROVED".equals(body.getStatus()) || "REJECTED".equals(body.getStatus())) {
            body.setReviewedByUserId(currentUser.getUserId());
            body.setReviewedAt(LocalDateTime.now());
            if (body.getReviewRemark() != null && !body.getReviewRemark().isBlank()) {
                body.setReviewRemark(body.getReviewRemark().trim());
            }
        }
        leaveRecordMapper.insert(body);
        if ("TECH".equals(currentUser.getRoleCode())) {
            notificationService.notifyAdmins(
                    "LEAVE",
                    "New leave request pending review",
                    "Technician " + technicianName(body.getTechnicianId()) + " submitted a leave request for "
                            + formatRange(body.getStartTime(), body.getEndTime()) + ".",
                    "/admin/leaves"
            );
        }
        return leaveRecordMapper.selectById(body.getId());
    }

    @Transactional
    public void cancel(Long id) {
        LeaveRecord record = leaveRecordMapper.selectById(id);
        if (record == null) {
            throw new BizException(404, "Leave record not found");
        }
        assertOwnerOrAdmin(record);
        if ("CANCELLED".equals(record.getStatus())) {
            return;
        }
        if (!Set.of("PENDING", "APPROVED").contains(record.getStatus())) {
            throw new BizException(4311, "Only pending or approved leave records can be cancelled");
        }
        record.setStatus("CANCELLED");
        leaveRecordMapper.updateById(record);
        if ("TECH".equals(SecurityUtils.currentUserOrThrow().getRoleCode())) {
            notificationService.notifyAdmins(
                    "LEAVE",
                    "Leave request withdrawn",
                    "Technician " + technicianName(record.getTechnicianId()) + " withdrew a leave request for "
                            + formatRange(record.getStartTime(), record.getEndTime()) + ".",
                    "/admin/leaves"
            );
        }
    }

    @Transactional
    public LeaveRecord approve(Long id, String remark) {
        return review(id, "APPROVED", remark, false);
    }

    @Transactional
    public LeaveRecord reject(Long id, String remark) {
        return review(id, "REJECTED", remark, true);
    }

    public boolean isOnLeaveAt(Long technicianId, LocalDateTime appointmentTime) {
        return leaveRecordMapper.countActiveLeaveAt(technicianId, appointmentTime) > 0;
    }

    public boolean isOnLeaveDuring(Long technicianId, LocalDateTime startTime, LocalDateTime endTime) {
        return leaveRecordMapper.countApprovedOverlap(technicianId, startTime, endTime, null) > 0;
    }

    public void ensureNoApprovedLeaveConflict(Long technicianId, LocalDateTime startTime,
                                              LocalDateTime endTime, Long excludeId) {
        if (leaveRecordMapper.countApprovedOverlap(technicianId, startTime, endTime, excludeId) > 0) {
            throw new BizException(4312, "Leave time overlaps with another approved leave record");
        }
    }

    public void ensureNoAppointmentConflict(Long technicianId, LocalDateTime startTime, LocalDateTime endTime) {
        if (appointmentMapper.countConflictInRange(technicianId, startTime, endTime) > 0) {
            throw new BizException(4313, "There are active appointments in the requested leave period");
        }
    }

    public LeaveConflictPreviewVo conflictPreview(Long leaveRecordId) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            throw new BizException(403, "No permission to preview leave conflicts");
        }
        LeaveRecord record = leaveRecordMapper.selectById(leaveRecordId);
        if (record == null) {
            throw new BizException(404, "Leave record not found");
        }

        LeaveConflictPreviewVo preview = new LeaveConflictPreviewVo();
        preview.setLeaveRecordId(record.getId());
        List<Appointment> conflicts = appointmentMapper.selectActiveConflictsInRange(
                record.getTechnicianId(),
                record.getStartTime(),
                record.getEndTime()
        );
        preview.setConflicts(conflicts.stream().map(this::toConflictVo).toList());
        preview.setHasConflicts(!preview.getConflicts().isEmpty());
        return preview;
    }

    private void validateLeaveRecord(LeaveRecord body) {
        if (body.getStartTime() == null || body.getEndTime() == null) {
            throw new BizException(4314, "Leave start and end time are required");
        }
        if (!body.getStartTime().isBefore(body.getEndTime())) {
            throw new BizException(4315, "Leave end time must be later than start time");
        }
        if (body.getReason() == null || body.getReason().isBlank()) {
            throw new BizException(4316, "Leave reason is required");
        }
        if (!ALLOWED_STATUS.contains(body.getStatus())) {
            throw new BizException(4317, "Unsupported leave status");
        }
    }

    private Technician currentTechnicianOrThrow(Long userId) {
        Technician technician = technicianMapper.selectOne(
                new LambdaQueryWrapper<Technician>().eq(Technician::getUserId, userId)
        );
        if (technician == null) {
            throw new BizException(4318, "Technician profile not found");
        }
        return technician;
    }

    private void assertOwnerOrAdmin(LeaveRecord record) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if ("ADMIN".equals(currentUser.getRoleCode())) {
            return;
        }
        if (!"TECH".equals(currentUser.getRoleCode())) {
            throw new BizException(403, "No permission to operate this leave record");
        }
        Technician technician = currentTechnicianOrThrow(currentUser.getUserId());
        if (!technician.getId().equals(record.getTechnicianId())) {
            throw new BizException(403, "No permission to operate this leave record");
        }
    }

    private LeaveRecord review(Long id, String targetStatus, String remark, boolean requireRemark) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            throw new BizException(403, "No permission to review leave records");
        }

        LeaveRecord record = leaveRecordMapper.selectById(id);
        if (record == null) {
            throw new BizException(404, "Leave record not found");
        }
        if (!"PENDING".equals(record.getStatus())) {
            throw new BizException(4319, "Only pending leave requests can be reviewed");
        }
        if (requireRemark && (remark == null || remark.isBlank())) {
            throw new BizException(4320, "Review remark is required");
        }
        if ("APPROVED".equals(targetStatus)) {
            ensureNoApprovedLeaveConflict(record.getTechnicianId(), record.getStartTime(), record.getEndTime(), record.getId());
            ensureNoAppointmentConflict(record.getTechnicianId(), record.getStartTime(), record.getEndTime());
        }

        record.setStatus(targetStatus);
        record.setReviewedByUserId(currentUser.getUserId());
        record.setReviewedAt(LocalDateTime.now());
        record.setReviewRemark(remark == null || remark.isBlank() ? null : remark.trim());
        leaveRecordMapper.updateById(record);
        notificationService.notifyUser(
                technicianUserId(record.getTechnicianId()),
                "LEAVE",
                "APPROVED".equals(targetStatus) ? "Leave request approved" : "Leave request rejected",
                "Your leave request for " + formatRange(record.getStartTime(), record.getEndTime())
                        + " is now " + targetStatus.toLowerCase() + "."
                        + (record.getReviewRemark() == null ? "" : " Remark: " + record.getReviewRemark()),
                "/tech/leaves"
        );
        return leaveRecordMapper.selectById(id);
    }

    private LeaveAppointmentConflictVo toConflictVo(Appointment appointment) {
        LeaveAppointmentConflictVo vo = new LeaveAppointmentConflictVo();
        vo.setAppointmentId(appointment.getId());
        vo.setUserId(appointment.getUserId());
        SysUser user = sysUserMapper.selectById(appointment.getUserId());
        vo.setUsername(user == null ? ("用户#" + appointment.getUserId()) : fallbackName(user.getNickname(), user.getUsername()));
        vo.setTechnicianId(appointment.getTechnicianId());
        vo.setTechnicianName(technicianName(appointment.getTechnicianId()));
        vo.setServiceItemId(appointment.getServiceItemId());
        ServiceItem serviceItem = serviceItemMapper.selectById(appointment.getServiceItemId());
        vo.setServiceItemName(serviceItem == null ? ("服务#" + appointment.getServiceItemId()) : serviceItem.getName());
        vo.setAppointmentTime(appointment.getAppointmentTime());
        vo.setAppointmentEndTime(appointmentEndTimeOf(appointment));
        vo.setStatus(appointment.getStatus());
        vo.setPayStatus(appointment.getPayStatus());
        vo.setCandidates(findReassignCandidates(appointment));
        return vo;
    }

    private List<TechnicianCandidateVo> findReassignCandidates(Appointment appointment) {
        LocalDateTime startAt = appointment.getAppointmentTime();
        LocalDateTime endAt = appointmentEndTimeOf(appointment);
        return technicianMapper.selectActiveByServiceItemId(appointment.getServiceItemId()).stream()
                .filter(technician -> !technician.getId().equals(appointment.getTechnicianId()))
                .filter(technician -> scheduleMapper.countWorkingRange(
                        technician.getId(),
                        startAt.toLocalDate(),
                        startAt.toLocalTime(),
                        endAt.toLocalTime()
                ) > 0)
                .filter(technician -> leaveRecordMapper.countApprovedOverlap(technician.getId(), startAt, endAt, null) == 0)
                .filter(technician -> appointmentMapper.countConflictExclude(technician.getId(), startAt, endAt, appointment.getId()) == 0)
                .map(technician -> {
                    TechnicianCandidateVo candidate = new TechnicianCandidateVo();
                    candidate.setTechnicianId(technician.getId());
                    candidate.setTechnicianName(technician.getName());
                    candidate.setReason("同一时段有排班，且无请假/预约冲突");
                    return candidate;
                })
                .toList();
    }

    private LocalDateTime appointmentEndTimeOf(Appointment appointment) {
        if (appointment.getAppointmentEndTime() != null) {
            return appointment.getAppointmentEndTime();
        }
        int duration = appointment.getDurationMinSnapshot() == null || appointment.getDurationMinSnapshot() <= 0
                ? 60
                : appointment.getDurationMinSnapshot();
        return appointment.getAppointmentTime().plusMinutes(duration);
    }

    private String fallbackName(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private Long technicianUserId(Long technicianId) {
        if (technicianId == null) {
            return null;
        }
        Technician technician = technicianMapper.selectById(technicianId);
        return technician == null ? null : technician.getUserId();
    }

    private String technicianName(Long technicianId) {
        Technician technician = technicianId == null ? null : technicianMapper.selectById(technicianId);
        return technician == null ? ("#" + technicianId) : technician.getName();
    }

    private String formatRange(LocalDateTime startTime, LocalDateTime endTime) {
        return startTime + " to " + endTime;
    }
}
