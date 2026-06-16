package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.common.BizException;
import com.bfsm.common.SecurityUtils;
import com.bfsm.entity.Appointment;
import com.bfsm.entity.AppointmentLog;
import com.bfsm.entity.PaymentOrder;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.AppointmentLogMapper;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.PaymentOrderMapper;
import com.bfsm.mapper.TechnicianMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class AppointmentAdminService {
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "APPROVED", "WAIT_SERVICE", "IN_SERVICE");

    private final AppointmentMapper appointmentMapper;
    private final AppointmentLogMapper appointmentLogMapper;
    private final TechnicianMapper technicianMapper;
    private final ScheduleConflictChecker scheduleConflictChecker;
    private final NotificationService notificationService;
    private final WalletService walletService;
    private final PaymentService paymentService;
    private final PaymentOrderMapper paymentOrderMapper;

    public AppointmentAdminService(AppointmentMapper appointmentMapper, AppointmentLogMapper appointmentLogMapper,
                                   TechnicianMapper technicianMapper, ScheduleConflictChecker scheduleConflictChecker,
                                   NotificationService notificationService, WalletService walletService,
                                   PaymentService paymentService, PaymentOrderMapper paymentOrderMapper) {
        this.appointmentMapper = appointmentMapper;
        this.appointmentLogMapper = appointmentLogMapper;
        this.technicianMapper = technicianMapper;
        this.scheduleConflictChecker = scheduleConflictChecker;
        this.notificationService = notificationService;
        this.walletService = walletService;
        this.paymentService = paymentService;
        this.paymentOrderMapper = paymentOrderMapper;
    }

    @Transactional
    public void reassign(Long appointmentId, Long targetTechnicianId, String remark) {
        Appointment appointment = activeAppointmentOrThrow(appointmentId);
        if (targetTechnicianId == null || targetTechnicianId.equals(appointment.getTechnicianId())) {
            throw new BizException(4461, "请选择新的可用技师");
        }
        Technician technician = technicianMapper.selectById(targetTechnicianId);
        if (technician == null || !"ACTIVE".equals(technician.getStatus())) {
            throw new BizException(4462, "目标技师不存在或已停用");
        }
        LocalDateTime endTime = appointmentEndTimeOf(appointment);
        scheduleConflictChecker.validateTechnicianForAppointment(
                targetTechnicianId,
                appointment.getServiceItemId(),
                appointment.getAppointmentTime(),
                endTime
        );
        if (scheduleConflictChecker.hasConflictInRangeExclude(
                targetTechnicianId,
                appointment.getAppointmentTime(),
                endTime,
                appointment.getId()
        )) {
            throw new BizException(4463, "目标技师该时段已有预约");
        }

        Long oldTechnicianId = appointment.getTechnicianId();
        appointment.setTechnicianId(targetTechnicianId);
        appointment.setAssignMode("MANUAL");
        appointmentMapper.updateById(appointment);
        log(appointment, "管理员转派预约：原技师 #" + oldTechnicianId + "，新技师 " + technician.getName()
                + appendRemark(remark));
        notifyReassigned(appointment, oldTechnicianId, technician.getName());
    }

    @Transactional
    public void cancelForLeave(Long appointmentId, String remark) {
        Appointment appointment = activeAppointmentOrThrow(appointmentId);
        String fromStatus = appointment.getStatus();
        boolean refunded = refundAppointmentIfNeeded(appointment, "技师请假冲突，管理员取消预约");
        closePendingPayments(appointment.getId());
        appointment.setStatus("CANCELLED");
        appointment.setCancelTime(LocalDateTime.now());
        appointmentMapper.updateById(appointment);
        log(appointment, "管理员因请假冲突取消预约" + appendRemark(remark));
        notificationService.notifyUser(
                appointment.getUserId(),
                "APPOINTMENT",
                "预约已取消",
                "预约 #" + appointment.getId() + " 因技师请假冲突已取消。"
                        + (refunded ? " 款项已退回钱包。" : ""),
                "/appointments"
        );
        notificationService.notifyUser(
                technicianUserId(appointment.getTechnicianId()),
                "APPOINTMENT",
                "预约已取消",
                "预约 #" + appointment.getId() + " 因请假冲突已由管理员取消。",
                "/tech/appointments"
        );
    }

    private Appointment activeAppointmentOrThrow(Long appointmentId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new BizException(404, "预约不存在");
        }
        if (!ACTIVE_STATUSES.contains(appointment.getStatus())) {
            throw new BizException(4464, "仅有效预约可以处理请假冲突");
        }
        return appointment;
    }

    private boolean refundAppointmentIfNeeded(Appointment appointment, String reason) {
        if (!"PAID".equals(normalizePayStatus(appointment))) {
            return false;
        }
        walletService.refundForAppointment(
                appointment.getUserId(),
                appointment.getId(),
                appointment.getAmount(),
                "预约退款#" + appointment.getId()
        );
        paymentService.recordAppointmentRefund(appointment, reason);
        appointment.setPayStatus("REFUNDED");
        return true;
    }

    private void closePendingPayments(Long appointmentId) {
        List<PaymentOrder> paymentOrders = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getBusinessType, "APPOINTMENT")
                .eq(PaymentOrder::getBusinessId, appointmentId)
                .in(PaymentOrder::getStatus, Set.of("CREATED", "PAYING")));
        for (PaymentOrder paymentOrder : paymentOrders) {
            paymentOrder.setStatus("CLOSED");
            paymentOrder.setNotifyContent("预约已取消，支付单已关闭");
            paymentOrderMapper.updateById(paymentOrder);
        }
    }

    private void log(Appointment appointment, String remark) {
        AppointmentLog log = new AppointmentLog();
        log.setAppointmentId(appointment.getId());
        log.setFromStatus(appointment.getStatus());
        log.setToStatus(appointment.getStatus());
        log.setOperatorUserId(SecurityUtils.currentUserOrThrow().getUserId());
        log.setRemark(remark);
        appointmentLogMapper.insert(log);
    }

    private void notifyReassigned(Appointment appointment, Long oldTechnicianId, String newTechnicianName) {
        String content = "预约 #" + appointment.getId() + " 已由管理员转派给 " + newTechnicianName + "，预约时间不变。";
        notificationService.notifyUser(appointment.getUserId(), "APPOINTMENT", "预约技师已调整", content, "/appointments");
        notificationService.notifyUser(technicianUserId(oldTechnicianId), "APPOINTMENT", "预约已转出", "预约 #" + appointment.getId() + " 已转派给其他技师。", "/tech/appointments");
        notificationService.notifyUser(technicianUserId(appointment.getTechnicianId()), "APPOINTMENT", "收到转派预约", content, "/tech/appointments");
    }

    private Long technicianUserId(Long technicianId) {
        Technician technician = technicianId == null ? null : technicianMapper.selectById(technicianId);
        return technician == null ? null : technician.getUserId();
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

    private String normalizePayStatus(Appointment appointment) {
        return appointment.getPayStatus() == null || appointment.getPayStatus().isBlank() ? "UNPAID" : appointment.getPayStatus();
    }

    private String appendRemark(String remark) {
        return remark == null || remark.isBlank() ? "" : "，备注：" + remark.trim();
    }
}
