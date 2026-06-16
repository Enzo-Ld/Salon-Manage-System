package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bfsm.cache.RedisLockService;
import com.bfsm.common.BizException;
import com.bfsm.common.PageUtils;
import com.bfsm.common.SecurityUtils;
import com.bfsm.dto.AppointmentCreateReq;
import com.bfsm.dto.CommentReq;
import com.bfsm.dto.OrderPayReq;
import com.bfsm.entity.Appointment;
import com.bfsm.entity.AppointmentLog;
import com.bfsm.entity.CommentEntity;
import com.bfsm.entity.PaymentOrder;
import com.bfsm.entity.PaymentRefund;
import com.bfsm.entity.Schedule;
import com.bfsm.entity.ServiceItem;
import com.bfsm.entity.SysUser;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.AppointmentLogMapper;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.CommentMapper;
import com.bfsm.mapper.PaymentOrderMapper;
import com.bfsm.mapper.PaymentRefundMapper;
import com.bfsm.mapper.ScheduleMapper;
import com.bfsm.mapper.ServiceItemMapper;
import com.bfsm.mapper.SysUserMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.security.SecurityUser;
import com.bfsm.vo.AppointmentAvailableSlotVo;
import com.bfsm.vo.AppointmentDetailVo;
import com.bfsm.vo.AppointmentLogVo;
import com.bfsm.vo.AppointmentSummaryVo;
import com.bfsm.vo.PageResultVo;
import com.bfsm.vo.TechnicianRecommendationVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AppointmentService {
    private static final int SLOT_STEP_MINUTES = 30;

    private static final Map<String, Set<String>> FLOW = Map.of(
            "PENDING", Set.of("APPROVED", "REJECTED"),
            "APPROVED", Set.of("WAIT_SERVICE"),
            "WAIT_SERVICE", Set.of("IN_SERVICE"),
            "IN_SERVICE", Set.of("COMPLETED")
    );

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "CREATED", "待支付",
            "PENDING", "待确认",
            "APPROVED", "已确认",
            "REJECTED", "已拒绝",
            "WAIT_SERVICE", "待服务",
            "IN_SERVICE", "服务中",
            "COMPLETED", "已完成",
            "CANCELLED", "已取消"
    );

    private static final Map<String, String> PAY_STATUS_LABELS = Map.of(
            "UNPAID", "未支付",
            "PAID", "已支付",
            "CANCELLED", "已关闭",
            "REFUNDED", "已退款"
    );

    private static final Map<String, String> PAY_METHOD_LABELS = Map.of(
            "BALANCE", "钱包余额",
            "WECHAT", "微信支付",
            "ALIPAY", "支付宝"
    );

    private static final Map<String, String> PAYMENT_STATUS_LABELS = Map.of(
            "CREATED", "已创建",
            "PAYING", "待支付",
            "SUCCESS", "支付成功",
            "FAILED", "支付失败",
            "CLOSED", "已关闭",
            "REFUNDING", "退款处理中",
            "MANUAL_REQUIRED", "需人工处理",
            "REFUNDED", "已退款"
    );

    private static final Map<String, String> REFUND_STATUS_LABELS = Map.of(
            "CREATED", "已创建",
            "SUCCESS", "退款成功",
            "PROCESSING", "退款处理中",
            "MANUAL_REQUIRED", "需人工处理",
            "FAILED", "退款失败"
    );

    private static final Map<String, String> ASSIGN_MODE_LABELS = Map.of(
            "MANUAL", "手动选择",
            "AUTO", "系统推荐"
    );

    private final AppointmentMapper appointmentMapper;
    private final AppointmentLogMapper appointmentLogMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final ScheduleMapper scheduleMapper;
    private final SchedulingService schedulingService;
    private final TechnicianMapper technicianMapper;
    private final RedisLockService redisLockService;
    private final ScheduleConflictChecker scheduleConflictChecker;
    private final CommentMapper commentMapper;
    private final SysUserMapper sysUserMapper;
    private final NotificationService notificationService;
    private final ChatService chatService;
    private final WalletService walletService;
    private final PaymentService paymentService;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentRefundMapper paymentRefundMapper;
    private final TechnicianRatingService technicianRatingService;

    public AppointmentService(AppointmentMapper appointmentMapper,
                              AppointmentLogMapper appointmentLogMapper,
                              ServiceItemMapper serviceItemMapper,
                              ScheduleMapper scheduleMapper,
                              SchedulingService schedulingService,
                              TechnicianMapper technicianMapper,
                              RedisLockService redisLockService,
                              ScheduleConflictChecker scheduleConflictChecker,
                              CommentMapper commentMapper,
                              SysUserMapper sysUserMapper,
                              NotificationService notificationService,
                              ChatService chatService,
                              WalletService walletService,
                              PaymentService paymentService,
                              PaymentOrderMapper paymentOrderMapper,
                              PaymentRefundMapper paymentRefundMapper,
                              TechnicianRatingService technicianRatingService) {
        this.appointmentMapper = appointmentMapper;
        this.appointmentLogMapper = appointmentLogMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.scheduleMapper = scheduleMapper;
        this.schedulingService = schedulingService;
        this.technicianMapper = technicianMapper;
        this.redisLockService = redisLockService;
        this.scheduleConflictChecker = scheduleConflictChecker;
        this.commentMapper = commentMapper;
        this.sysUserMapper = sysUserMapper;
        this.notificationService = notificationService;
        this.chatService = chatService;
        this.walletService = walletService;
        this.paymentService = paymentService;
        this.paymentOrderMapper = paymentOrderMapper;
        this.paymentRefundMapper = paymentRefundMapper;
        this.technicianRatingService = technicianRatingService;
    }

    @Transactional
    public AppointmentDetailVo create(AppointmentCreateReq req) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if (req.getAppointmentTime().isBefore(LocalDateTime.now())) {
            throw new BizException(4010, "预约时间不能早于当前时间");
        }

        ServiceItem item = serviceItemOrThrow(req.getServiceItemId());
        LocalDateTime appointmentEndTime = resolveAppointmentEndTime(item, req.getAppointmentTime());

        Long technicianId = req.getTechnicianId();
        String assignMode = "MANUAL";
        if (technicianId == null) {
            TechnicianRecommendationVo recommended = schedulingService.recommendTechnician(
                    req.getServiceItemId(),
                    req.getAppointmentTime()
            );
            if (recommended == null) {
                throw new BizException(4011, "当前时间段没有可推荐的技师");
            }
            technicianId = recommended.getTechnicianId();
            assignMode = "AUTO";
        } else {
            scheduleConflictChecker.validateTechnicianForAppointment(
                    technicianId,
                    req.getServiceItemId(),
                    req.getAppointmentTime(),
                    appointmentEndTime
            );
        }

        String lockKey = lockKey(technicianId, req.getAppointmentTime());
        String lockToken = redisLockService.tryLock(lockKey, Duration.ofSeconds(8));
        if (lockToken == null) {
            throw new BizException(4012, "预约请求过于频繁，请稍后再试");
        }

        try {
            scheduleConflictChecker.validateTechnicianForAppointment(
                    technicianId,
                    req.getServiceItemId(),
                    req.getAppointmentTime(),
                    appointmentEndTime
            );
            if (scheduleConflictChecker.hasConflictInRange(technicianId, req.getAppointmentTime(), appointmentEndTime)) {
                throw new BizException(4013, "该时间段技师已被预约");
            }

            Appointment appointment = new Appointment();
            appointment.setUserId(currentUser.getUserId());
            appointment.setTechnicianId(technicianId);
            appointment.setServiceItemId(req.getServiceItemId());
            appointment.setAppointmentTime(req.getAppointmentTime());
            appointment.setAppointmentEndTime(appointmentEndTime);
            appointment.setDurationMinSnapshot(item.getDurationMin());
            appointment.setStatus("CREATED");
            appointment.setPayStatus("UNPAID");
            appointment.setAmount(item.getPrice());
            appointment.setAssignMode(assignMode);
            appointmentMapper.insert(appointment);
            log(appointment.getId(), null, "CREATED", "创建预约待支付");
            return detail(appointment.getId());
        } finally {
            redisLockService.unlock(lockKey, lockToken);
        }
    }

    public List<AppointmentSummaryVo> listForCurrentUser() {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        LambdaQueryWrapper<Appointment> query = new LambdaQueryWrapper<>();
        if ("USER".equals(currentUser.getRoleCode())) {
            query.eq(Appointment::getUserId, currentUser.getUserId());
        } else if ("TECH".equals(currentUser.getRoleCode())) {
            Technician technician = currentTechnician();
            if (technician == null) {
                return List.of();
            }
            query.eq(Appointment::getTechnicianId, technician.getId())
                    .ne(Appointment::getStatus, "CREATED");
        }
        query.orderByDesc(Appointment::getAppointmentTime);
        return buildSummaryList(appointmentMapper.selectList(query));
    }

    public PageResultVo<AppointmentSummaryVo> pageForCurrentUser(Long pageNum, Long pageSize) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        LambdaQueryWrapper<Appointment> query = new LambdaQueryWrapper<>();
        if ("USER".equals(currentUser.getRoleCode())) {
            query.eq(Appointment::getUserId, currentUser.getUserId());
        } else if ("TECH".equals(currentUser.getRoleCode())) {
            Technician technician = currentTechnician();
            if (technician == null) {
                return new PageResultVo<>(List.of(), 0, PageUtils.normalizePageNum(pageNum), PageUtils.normalizePageSize(pageSize), 0);
            }
            query.eq(Appointment::getTechnicianId, technician.getId())
                    .ne(Appointment::getStatus, "CREATED");
        }
        query.orderByDesc(Appointment::getAppointmentTime);

        Page<Appointment> page = new Page<>(
                PageUtils.normalizePageNum(pageNum),
                PageUtils.normalizePageSize(pageSize)
        );
        Page<Appointment> result = appointmentMapper.selectPage(page, query);
        return new PageResultVo<>(
                buildSummaryList(result.getRecords()),
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                result.getPages()
        );
    }

    public AppointmentDetailVo detail(Long id) {
        Appointment appointment = appointmentMapper.selectById(id);
        if (appointment == null) {
            throw new BizException(404, "预约不存在");
        }
        assertCanView(appointment);

        AppointmentDetailVo detailVo = toDetail(appointment);
        List<AppointmentLog> logs = appointmentLogMapper.selectList(
                new LambdaQueryWrapper<AppointmentLog>()
                        .eq(AppointmentLog::getAppointmentId, id)
                        .orderByAsc(AppointmentLog::getCreatedAt)
        );
        Map<Long, String> operatorNames = logs.isEmpty()
                ? Map.of()
                : sysUserMapper.selectBatchIds(
                        logs.stream()
                                .map(AppointmentLog::getOperatorUserId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet())
                ).stream().collect(Collectors.toMap(
                        SysUser::getId,
                        item -> fallbackName(item.getNickname(), item.getUsername())
                ));
        detailVo.setLogs(logs.stream().map(log -> toLogVo(log, operatorNames)).toList());
        return detailVo;
    }

    public TechnicianRecommendationVo recommend(Long serviceItemId, LocalDateTime appointmentTime) {
        if (appointmentTime.isBefore(LocalDateTime.now())) {
            throw new BizException(4010, "预约时间不能早于当前时间");
        }
        serviceItemOrThrow(serviceItemId);
        TechnicianRecommendationVo recommendation = schedulingService.recommendTechnician(serviceItemId, appointmentTime);
        if (recommendation == null) {
            throw new BizException(4011, "当前时间段没有可推荐的技师");
        }
        return recommendation;
    }

    public List<AppointmentAvailableSlotVo> availableSlots(Long serviceItemId, LocalDate appointmentDate, Long technicianId) {
        if (appointmentDate.isBefore(LocalDate.now())) {
            throw new BizException(4010, "预约日期不能早于今天");
        }

        ServiceItem item = serviceItemOrThrow(serviceItemId);
        List<Technician> candidates = loadSlotCandidates(serviceItemId, technicianId);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<LocalDateTime, List<Technician>> slotMap = new TreeMap<>();
        for (Technician technician : candidates) {
            List<Schedule> schedules = scheduleMapper.selectList(
                    new LambdaQueryWrapper<Schedule>()
                            .eq(Schedule::getTechnicianId, technician.getId())
                            .eq(Schedule::getWorkDate, appointmentDate)
                            .eq(Schedule::getStatus, "WORKING")
                            .orderByAsc(Schedule::getStartTime)
            );
            appendTechnicianSlots(slotMap, technician, schedules, item, appointmentDate);
        }

        return slotMap.entrySet().stream()
                .map(entry -> toAvailableSlot(item, entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional
    public AppointmentDetailVo autoAssign(Long id) {
        Appointment appointment = appointmentMapper.selectById(id);
        if (appointment == null) {
            throw new BizException(404, "预约不存在");
        }
        if (!Set.of("PENDING", "APPROVED").contains(appointment.getStatus())) {
            throw new BizException(4014, "当前状态不支持自动分配");
        }
        if (!"PAID".equals(normalizePayStatus(appointment))) {
            throw new BizException(4015, "预约尚未支付，不能自动分配");
        }

        TechnicianRecommendationVo recommendation = recommend(appointment.getServiceItemId(), appointment.getAppointmentTime());
        String lockKey = lockKey(recommendation.getTechnicianId(), appointment.getAppointmentTime());
        String lockToken = redisLockService.tryLock(lockKey, Duration.ofSeconds(8));
        if (lockToken == null) {
            throw new BizException(4016, "自动分配繁忙，请稍后再试");
        }
        try {
            if (scheduleConflictChecker.hasConflictInRangeExclude(
                    recommendation.getTechnicianId(),
                    appointment.getAppointmentTime(),
                    appointmentEndTimeOf(appointment),
                    appointment.getId()
            )) {
                throw new BizException(4017, "自动分配时检测到技师冲突");
            }
            appointment.setTechnicianId(recommendation.getTechnicianId());
            appointment.setAssignMode("AUTO");
            appointmentMapper.updateById(appointment);
            notifyTechnicianAssignment(appointment, recommendation.getReason());
            log(appointment.getId(), appointment.getStatus(), appointment.getStatus(), "系统自动分配：" + recommendation.getReason());
            return detail(id);
        } finally {
            redisLockService.unlock(lockKey, lockToken);
        }
    }

    @Transactional
    public void moveStatus(Long id, String toStatus, String remark) {
        Appointment appointment = appointmentMapper.selectById(id);
        if (appointment == null) {
            throw new BizException(404, "预约不存在");
        }
        assertCanOperate(appointment);

        if (!"PAID".equals(normalizePayStatus(appointment))) {
            throw new BizException(4018, "预约尚未支付，不能继续处理");
        }

        Set<String> next = FLOW.getOrDefault(appointment.getStatus(), Set.of());
        if (!next.contains(toStatus)) {
            throw new BizException(4019, "预约状态流转不合法");
        }

        String lockKey = lockKey(appointment.getTechnicianId(), appointment.getAppointmentTime());
        String lockToken = null;
        if ("APPROVED".equals(toStatus)) {
            lockToken = redisLockService.tryLock(lockKey, Duration.ofSeconds(8));
            if (lockToken == null) {
                throw new BizException(4020, "审核过于频繁，请稍后再试");
            }
        }

        try {
            if ("APPROVED".equals(toStatus)) {
                scheduleConflictChecker.validateTechnicianForAppointment(
                        appointment.getTechnicianId(),
                        appointment.getServiceItemId(),
                        appointment.getAppointmentTime(),
                        appointmentEndTimeOf(appointment)
                );
                if (scheduleConflictChecker.hasConflictInRangeExclude(
                        appointment.getTechnicianId(),
                        appointment.getAppointmentTime(),
                        appointmentEndTimeOf(appointment),
                        appointment.getId()
                )) {
                    throw new BizException(4021, "审核时检测到技师排班冲突");
                }
            }

            String fromStatus = appointment.getStatus();
            appointment.setStatus(toStatus);
            if ("REJECTED".equals(toStatus)) {
                refundAppointmentIfNeeded(appointment, "预约未通过，发起原路退款");
            }
            appointmentMapper.updateById(appointment);
            notifyAppointmentStatusChanged(appointment, fromStatus, toStatus, remark);
            notifyAppointmentApprovedInChat(appointment, fromStatus, toStatus);
            log(id, fromStatus, toStatus, remark == null || remark.isBlank() ? defaultRemark(toStatus) : remark);
        } finally {
            if (lockToken != null) {
                redisLockService.unlock(lockKey, lockToken);
            }
        }
    }

    @Transactional
    public void cancel(Long id) {
        Appointment appointment = appointmentMapper.selectById(id);
        if (appointment == null) {
            throw new BizException(404, "预约不存在");
        }
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if ("USER".equals(currentUser.getRoleCode()) && !currentUser.getUserId().equals(appointment.getUserId())) {
            throw new BizException(403, "只能取消自己的预约");
        }
        if (!Set.of("CREATED", "PENDING", "APPROVED").contains(appointment.getStatus())) {
            throw new BizException(4022, "当前状态不可取消");
        }

        closePendingPayments(appointment.getId());

        boolean refunded = refundAppointmentIfNeeded(appointment, "用户取消预约退款");
        if (!refunded && "UNPAID".equals(normalizePayStatus(appointment))) {
            appointment.setPayStatus("CANCELLED");
        }
        String fromStatus = appointment.getStatus();
        appointment.setStatus("CANCELLED");
        appointment.setCancelTime(LocalDateTime.now());
        appointmentMapper.updateById(appointment);
        notifyAppointmentCancelled(appointment, refunded);
        log(id, fromStatus, "CANCELLED", refunded ? "用户取消预约，已发起原路退款处理" : "用户取消预约");
    }

    @Transactional
    public AppointmentDetailVo payForCurrentUser(Long id, OrderPayReq req) {
        Appointment appointment = ownAppointment(id);
        if (!"CREATED".equals(appointment.getStatus()) || !"UNPAID".equals(normalizePayStatus(appointment))) {
            throw new BizException(4023, "当前预约不可支付");
        }

        String payMethod = normalizePayMethod(req.getPayMethod());
        if (!"BALANCE".equals(payMethod)) {
            throw new BizException(4024, "在线支付请通过支付收银台接口发起");
        }

        if (appointment.getAppointmentTime().isBefore(LocalDateTime.now())) {
            throw new BizException(4025, "预约时间已过期，请重新选择时间段");
        }
        scheduleConflictChecker.validateTechnicianForAppointment(
                appointment.getTechnicianId(),
                appointment.getServiceItemId(),
                appointment.getAppointmentTime(),
                appointmentEndTimeOf(appointment)
        );
        if (scheduleConflictChecker.hasConflictInRangeExclude(
                appointment.getTechnicianId(),
                appointment.getAppointmentTime(),
                appointmentEndTimeOf(appointment),
                appointment.getId()
        )) {
            throw new BizException(4026, "该时间段已被其他用户预约，请重新选择时间段");
        }

        walletService.consumeForAppointment(
                appointment.getUserId(),
                appointment.getId(),
                appointment.getAmount(),
                "支付服务预约 #" + appointment.getId()
        );
        closePendingPayments(appointment.getId());

        appointment.setPayMethod("BALANCE");
        appointment.setPayStatus("PAID");
        appointment.setPayTime(LocalDateTime.now());
        appointment.setPayNo(buildPayNo(appointment.getId(), "BALANCE"));
        appointment.setStatus("PENDING");
        appointmentMapper.updateById(appointment);
        paymentService.recordBalanceAppointmentPayment(appointment);

        ServiceItem item = serviceItemMapper.selectById(appointment.getServiceItemId());
        notifyAppointmentPaid(appointment, item);
        log(id, "CREATED", "PENDING", "用户使用钱包余额完成预约支付");
        return detail(id);
    }

    @Transactional
    public void submitComment(Long appointmentId, CommentReq req) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new BizException(404, "预约不存在");
        }
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if (!currentUser.getUserId().equals(appointment.getUserId())) {
            throw new BizException(403, "只能评价自己的预约");
        }
        if (!"COMPLETED".equals(appointment.getStatus())) {
            throw new BizException(4027, "仅已完成预约允许评价");
        }
        if (appointment.getCommentText() != null && !appointment.getCommentText().isBlank()) {
            throw new BizException(4028, "该预约已评价");
        }

        CommentEntity comment = new CommentEntity();
        comment.setAppointmentId(appointmentId);
        comment.setUserId(appointment.getUserId());
        comment.setRating(req.getRating());
        comment.setContent(req.getContent());
        comment.setStatus("APPROVED");
        commentMapper.insert(comment);

        appointment.setCommentText(req.getContent());
        appointmentMapper.updateById(appointment);
        technicianRatingService.refreshRating(appointment.getTechnicianId());
        log(appointmentId, appointment.getStatus(), appointment.getStatus(), "提交服务评价");
    }

    private void assertCanView(Appointment appointment) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if ("ADMIN".equals(currentUser.getRoleCode())) {
            return;
        }
        if ("USER".equals(currentUser.getRoleCode()) && currentUser.getUserId().equals(appointment.getUserId())) {
            return;
        }
        if ("TECH".equals(currentUser.getRoleCode())) {
            Technician technician = currentTechnician();
            if (technician != null && technician.getId().equals(appointment.getTechnicianId())) {
                return;
            }
        }
        throw new BizException(403, "无权查看该预约");
    }

    private void assertCanOperate(Appointment appointment) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if ("ADMIN".equals(currentUser.getRoleCode())) {
            return;
        }
        if ("TECH".equals(currentUser.getRoleCode())) {
            Technician technician = currentTechnician();
            if (technician != null && technician.getId().equals(appointment.getTechnicianId())) {
                return;
            }
        }
        throw new BizException(403, "无权操作该预约");
    }

    private List<AppointmentSummaryVo> buildSummaryList(List<Appointment> appointments) {
        if (appointments.isEmpty()) {
            return List.of();
        }

        Map<Long, SysUser> users = sysUserMapper.selectBatchIds(
                        appointments.stream().map(Appointment::getUserId).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, Technician> technicians = technicianMapper.selectBatchIds(
                        appointments.stream().map(Appointment::getTechnicianId).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(Technician::getId, Function.identity()));
        Map<Long, ServiceItem> items = serviceItemMapper.selectBatchIds(
                        appointments.stream().map(Appointment::getServiceItemId).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(ServiceItem::getId, Function.identity()));
        Map<Long, AppointmentLog> latestLogs = appointmentLogMapper.selectList(
                        new LambdaQueryWrapper<AppointmentLog>()
                                .in(AppointmentLog::getAppointmentId, appointments.stream().map(Appointment::getId).collect(Collectors.toSet()))
                ).stream()
                .collect(Collectors.toMap(
                        AppointmentLog::getAppointmentId,
                        Function.identity(),
                        BinaryOperator.maxBy(Comparator.comparing(AppointmentLog::getCreatedAt))
                ));
        Map<Long, PaymentOrder> paymentOrders = loadLatestPaymentOrders(appointments);
        Map<Long, PaymentRefund> paymentRefunds = loadLatestPaymentRefunds(paymentOrders.values());

        return appointments.stream()
                .map(item -> toSummary(item, users, technicians, items, latestLogs.get(item.getId()),
                        paymentOrders.get(item.getId()), paymentRefunds))
                .toList();
    }

    private AppointmentDetailVo toDetail(Appointment appointment) {
        AppointmentSummaryVo summary = buildSummaryList(List.of(appointment)).get(0);
        AppointmentDetailVo detail = new AppointmentDetailVo();
        detail.setId(summary.getId());
        detail.setUserId(summary.getUserId());
        detail.setUsername(summary.getUsername());
        detail.setTechnicianId(summary.getTechnicianId());
        detail.setTechnicianUserId(summary.getTechnicianUserId());
        detail.setTechnicianName(summary.getTechnicianName());
        detail.setServiceItemId(summary.getServiceItemId());
        detail.setServiceItemName(summary.getServiceItemName());
        detail.setAppointmentTime(summary.getAppointmentTime());
        detail.setAppointmentEndTime(summary.getAppointmentEndTime());
        detail.setDurationMinSnapshot(summary.getDurationMinSnapshot());
        detail.setStatus(summary.getStatus());
        detail.setStatusLabel(summary.getStatusLabel());
        detail.setPayStatus(summary.getPayStatus());
        detail.setPayStatusLabel(summary.getPayStatusLabel());
        detail.setPayMethod(summary.getPayMethod());
        detail.setPayMethodLabel(summary.getPayMethodLabel());
        detail.setPayTime(summary.getPayTime());
        detail.setPayNo(summary.getPayNo());
        detail.setAmount(summary.getAmount());
        detail.setAssignMode(summary.getAssignMode());
        detail.setAssignModeLabel(summary.getAssignModeLabel());
        detail.setLatestPaymentNo(summary.getLatestPaymentNo());
        detail.setLatestPaymentChannel(summary.getLatestPaymentChannel());
        detail.setLatestPaymentChannelLabel(summary.getLatestPaymentChannelLabel());
        detail.setLatestPaymentStatus(summary.getLatestPaymentStatus());
        detail.setLatestPaymentStatusLabel(summary.getLatestPaymentStatusLabel());
        detail.setLatestPaymentTime(summary.getLatestPaymentTime());
        detail.setNextActionHint(summary.getNextActionHint());
        detail.setRefundNo(summary.getRefundNo());
        detail.setRefundAmount(summary.getRefundAmount());
        detail.setRefundStatus(summary.getRefundStatus());
        detail.setRefundStatusLabel(summary.getRefundStatusLabel());
        detail.setRefundTime(summary.getRefundTime());
        detail.setCanPay(summary.isCanPay());
        detail.setLatestRemark(summary.getLatestRemark());
        detail.setCanCancel(summary.isCanCancel());
        detail.setCanComment(summary.isCanComment());
        return detail;
    }

    private AppointmentSummaryVo toSummary(Appointment appointment,
                                           Map<Long, SysUser> users,
                                           Map<Long, Technician> technicians,
                                           Map<Long, ServiceItem> items,
                                           AppointmentLog latestLog,
                                           PaymentOrder paymentOrder,
                                           Map<Long, PaymentRefund> paymentRefunds) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        AppointmentSummaryVo summary = new AppointmentSummaryVo();
        summary.setId(appointment.getId());
        summary.setUserId(appointment.getUserId());
        SysUser user = users.get(appointment.getUserId());
        summary.setUsername(user == null ? "-" : fallbackName(user.getNickname(), user.getUsername()));
        summary.setTechnicianId(appointment.getTechnicianId());
        Technician technician = technicians.get(appointment.getTechnicianId());
        summary.setTechnicianUserId(technician == null ? null : technician.getUserId());
        summary.setTechnicianName(technician == null ? "-" : technician.getName());
        summary.setServiceItemId(appointment.getServiceItemId());
        ServiceItem item = items.get(appointment.getServiceItemId());
        summary.setServiceItemName(item == null ? "-" : item.getName());
        summary.setAppointmentTime(appointment.getAppointmentTime());
        summary.setAppointmentEndTime(appointmentEndTimeOf(appointment));
        summary.setDurationMinSnapshot(durationSnapshotOf(appointment, item));
        summary.setStatus(appointment.getStatus());
        summary.setStatusLabel(label(STATUS_LABELS, appointment.getStatus(), appointment.getStatus()));
        summary.setPayStatus(normalizePayStatus(appointment));
        summary.setPayStatusLabel(label(PAY_STATUS_LABELS, normalizePayStatus(appointment), normalizePayStatus(appointment)));
        summary.setPayMethod(appointment.getPayMethod());
        summary.setPayMethodLabel(label(PAY_METHOD_LABELS, appointment.getPayMethod(), "-"));
        summary.setPayTime(appointment.getPayTime());
        summary.setPayNo(appointment.getPayNo());
        summary.setAmount(appointment.getAmount());
        summary.setAssignMode(appointment.getAssignMode());
        summary.setAssignModeLabel(label(ASSIGN_MODE_LABELS, appointment.getAssignMode(), appointment.getAssignMode()));
        summary.setLatestPaymentNo(paymentOrder == null ? null : paymentOrder.getPaymentNo());
        summary.setLatestPaymentChannel(paymentOrder == null ? null : paymentOrder.getPayChannel());
        summary.setLatestPaymentChannelLabel(paymentOrder == null ? "-" : label(PAY_METHOD_LABELS, paymentOrder.getPayChannel(), paymentOrder.getPayChannel()));
        summary.setLatestPaymentStatus(paymentOrder == null ? null : paymentOrder.getStatus());
        summary.setLatestPaymentStatusLabel(paymentOrder == null ? "-" : label(PAYMENT_STATUS_LABELS, paymentOrder.getStatus(), paymentOrder.getStatus()));
        summary.setLatestPaymentTime(resolveLatestPaymentTime(paymentOrder));

        PaymentRefund refund = paymentOrder == null ? null : paymentRefunds.get(paymentOrder.getId());
        summary.setRefundNo(refund == null ? null : refund.getRefundNo());
        summary.setRefundAmount(refund == null ? null : refund.getAmount());
        summary.setRefundStatus(refund == null ? null : refund.getStatus());
        summary.setRefundStatusLabel(refund == null ? "-" : label(REFUND_STATUS_LABELS, refund.getStatus(), refund.getStatus()));
        summary.setRefundTime(refund == null ? null : (refund.getUpdatedAt() == null ? refund.getCreatedAt() : refund.getUpdatedAt()));
        summary.setNextActionHint(nextActionHint(appointment, paymentOrder));
        summary.setLatestRemark(latestLog == null ? "" : latestLog.getRemark());
        summary.setCanPay("USER".equals(currentUser.getRoleCode())
                && currentUser.getUserId().equals(appointment.getUserId())
                && "CREATED".equals(appointment.getStatus())
                && "UNPAID".equals(normalizePayStatus(appointment)));
        summary.setCanCancel("USER".equals(currentUser.getRoleCode())
                && currentUser.getUserId().equals(appointment.getUserId())
                && Set.of("CREATED", "PENDING", "APPROVED").contains(appointment.getStatus()));
        summary.setCanComment("USER".equals(currentUser.getRoleCode())
                && currentUser.getUserId().equals(appointment.getUserId())
                && "COMPLETED".equals(appointment.getStatus())
                && (appointment.getCommentText() == null || appointment.getCommentText().isBlank()));
        return summary;
    }

    private Map<Long, PaymentOrder> loadLatestPaymentOrders(List<Appointment> appointments) {
        Set<Long> appointmentIds = appointments.stream().map(Appointment::getId).collect(Collectors.toSet());
        if (appointmentIds.isEmpty()) {
            return Map.of();
        }
        return paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getBusinessType, "APPOINTMENT")
                        .in(PaymentOrder::getBusinessId, appointmentIds)
                        .orderByDesc(PaymentOrder::getId))
                .stream()
                .collect(Collectors.toMap(
                        PaymentOrder::getBusinessId,
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private Map<Long, PaymentRefund> loadLatestPaymentRefunds(Collection<PaymentOrder> paymentOrders) {
        Set<Long> paymentOrderIds = paymentOrders.stream()
                .filter(Objects::nonNull)
                .map(PaymentOrder::getId)
                .collect(Collectors.toSet());
        if (paymentOrderIds.isEmpty()) {
            return Map.of();
        }
        return paymentRefundMapper.selectList(new LambdaQueryWrapper<PaymentRefund>()
                        .in(PaymentRefund::getPaymentOrderId, paymentOrderIds)
                        .orderByDesc(PaymentRefund::getId))
                .stream()
                .collect(Collectors.toMap(
                        PaymentRefund::getPaymentOrderId,
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private AppointmentLogVo toLogVo(AppointmentLog log, Map<Long, String> operatorNames) {
        AppointmentLogVo vo = new AppointmentLogVo();
        vo.setId(log.getId());
        vo.setFromStatus(log.getFromStatus());
        vo.setFromStatusLabel(label(STATUS_LABELS, log.getFromStatus(), log.getFromStatus()));
        vo.setToStatus(log.getToStatus());
        vo.setToStatusLabel(label(STATUS_LABELS, log.getToStatus(), log.getToStatus()));
        vo.setOperatorUserId(log.getOperatorUserId());
        vo.setOperatorName(operatorNames.getOrDefault(log.getOperatorUserId(), "系统"));
        vo.setRemark(log.getRemark());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    private void log(Long appointmentId, String from, String to, String remark) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        AppointmentLog log = new AppointmentLog();
        log.setAppointmentId(appointmentId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorUserId(currentUser.getUserId());
        log.setRemark(remark);
        appointmentLogMapper.insert(log);
    }

    private ServiceItem serviceItemOrThrow(Long serviceItemId) {
        ServiceItem item = serviceItemMapper.selectById(serviceItemId);
        if (item == null || !"ON".equals(item.getStatus())) {
            throw new BizException(404, "服务项目不存在或已停用");
        }
        if (item.getDurationMin() == null || item.getDurationMin() <= 0) {
            throw new BizException(4029, "服务项目时长配置无效");
        }
        return item;
    }

    private LocalDateTime resolveAppointmentEndTime(ServiceItem item, LocalDateTime appointmentTime) {
        return appointmentTime.plusMinutes(item.getDurationMin());
    }

    private LocalDateTime appointmentEndTimeOf(Appointment appointment) {
        if (appointment.getAppointmentEndTime() != null) {
            return appointment.getAppointmentEndTime();
        }
        Integer duration = appointment.getDurationMinSnapshot();
        if (duration == null || duration <= 0) {
            ServiceItem item = appointment.getServiceItemId() == null ? null : serviceItemMapper.selectById(appointment.getServiceItemId());
            duration = item == null || item.getDurationMin() == null ? 60 : item.getDurationMin();
        }
        return appointment.getAppointmentTime().plusMinutes(duration);
    }

    private Integer durationSnapshotOf(Appointment appointment, ServiceItem item) {
        if (appointment.getDurationMinSnapshot() != null && appointment.getDurationMinSnapshot() > 0) {
            return appointment.getDurationMinSnapshot();
        }
        return item == null || item.getDurationMin() == null ? 60 : item.getDurationMin();
    }

    private List<Technician> loadSlotCandidates(Long serviceItemId, Long technicianId) {
        if (technicianId == null) {
            return technicianMapper.selectActiveByServiceItemId(serviceItemId);
        }

        Technician technician = technicianMapper.selectById(technicianId);
        if (technician == null || !"ACTIVE".equals(technician.getStatus())) {
            return List.of();
        }
        if (!scheduleConflictChecker.supportsService(technicianId, serviceItemId)) {
            return List.of();
        }
        return List.of(technician);
    }

    private void appendTechnicianSlots(Map<LocalDateTime, List<Technician>> slotMap,
                                       Technician technician,
                                       List<Schedule> schedules,
                                       ServiceItem item,
                                       LocalDate appointmentDate) {
        for (Schedule schedule : schedules) {
            LocalDateTime scheduleStart = LocalDateTime.of(appointmentDate, schedule.getStartTime());
            LocalDateTime latestStart = LocalDateTime.of(appointmentDate, schedule.getEndTime()).minusMinutes(item.getDurationMin());
            if (latestStart.isBefore(scheduleStart)) {
                continue;
            }

            for (LocalDateTime cursor = scheduleStart; !cursor.isAfter(latestStart); cursor = cursor.plusMinutes(SLOT_STEP_MINUTES)) {
                if (cursor.isBefore(LocalDateTime.now())) {
                    continue;
                }
                LocalDateTime endTime = cursor.plusMinutes(item.getDurationMin());
                if (scheduleConflictChecker.isOnLeaveDuring(technician.getId(), cursor, endTime)) {
                    continue;
                }
                if (scheduleConflictChecker.hasConflictInRange(technician.getId(), cursor, endTime)) {
                    continue;
                }

                List<Technician> slotTechnicians = slotMap.computeIfAbsent(cursor, key -> new ArrayList<>());
                boolean exists = slotTechnicians.stream().anyMatch(itemTech -> itemTech.getId().equals(technician.getId()));
                if (!exists) {
                    slotTechnicians.add(technician);
                }
            }
        }
    }

    private AppointmentAvailableSlotVo toAvailableSlot(ServiceItem item, LocalDateTime startTime, List<Technician> technicians) {
        LocalDateTime endTime = startTime.plusMinutes(item.getDurationMin());
        TechnicianRecommendationVo recommendation = schedulingService.recommendFromCandidates(item, startTime, technicians);

        AppointmentAvailableSlotVo slot = new AppointmentAvailableSlotVo();
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);
        slot.setLabel(startTime.format(DateTimeFormatter.ofPattern("HH:mm")) + " - "
                + endTime.format(DateTimeFormatter.ofPattern("HH:mm")));
        slot.setAvailableTechnicianCount(technicians.size());
        slot.setTechnicianNames(technicians.stream().map(Technician::getName).distinct().toList());
        if (recommendation != null) {
            slot.setRecommendedTechnicianId(recommendation.getTechnicianId());
            slot.setRecommendedTechnicianName(recommendation.getTechnicianName());
        }
        return slot;
    }

    private String lockKey(Long technicianId, LocalDateTime appointmentTime) {
        return "lock:appointment:" + technicianId + ":" + appointmentTime.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private String label(Map<String, String> labels, String key, String fallback) {
        if (key == null || key.isBlank()) {
            return fallback;
        }
        return labels.getOrDefault(key, fallback);
    }

    private String normalizePayStatus(Appointment appointment) {
        if (appointment.getPayStatus() == null || appointment.getPayStatus().isBlank()) {
            return "UNPAID";
        }
        return appointment.getPayStatus();
    }

    private Technician currentTechnician() {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        return technicianMapper.selectOne(
                new LambdaQueryWrapper<Technician>().eq(Technician::getUserId, currentUser.getUserId())
        );
    }

    private Appointment ownAppointment(Long id) {
        Appointment appointment = appointmentMapper.selectById(id);
        if (appointment == null) {
            throw new BizException(404, "预约不存在");
        }
        if (!Objects.equals(appointment.getUserId(), SecurityUtils.currentUserOrThrow().getUserId())) {
            throw new BizException(403, "只能操作自己的预约");
        }
        return appointment;
    }

    private boolean refundAppointmentIfNeeded(Appointment appointment, String reason) {
        if (!"PAID".equals(normalizePayStatus(appointment))) {
            return false;
        }
        PaymentRefund refund = paymentService.recordAppointmentRefund(appointment, reason);
        if (refund != null && "SUCCESS".equals(refund.getStatus())) {
            appointment.setPayStatus("REFUNDED");
        }
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

    private String normalizePayMethod(String payMethod) {
        String normalized = payMethod == null ? "" : payMethod.trim().toUpperCase();
        if (!PAY_METHOD_LABELS.containsKey(normalized)) {
            throw new BizException(4030, "暂不支持该支付方式");
        }
        return normalized;
    }

    private String buildPayNo(Long appointmentId, String payMethod) {
        return payMethod + "-APPOINTMENT-" + appointmentId + "-" + System.currentTimeMillis();
    }

    private LocalDateTime resolveLatestPaymentTime(PaymentOrder paymentOrder) {
        if (paymentOrder == null) {
            return null;
        }
        if (paymentOrder.getPaidTime() != null) {
            return paymentOrder.getPaidTime();
        }
        return paymentOrder.getUpdatedAt() == null ? paymentOrder.getCreatedAt() : paymentOrder.getUpdatedAt();
    }

    private String nextActionHint(Appointment appointment, PaymentOrder paymentOrder) {
        String payStatus = normalizePayStatus(appointment);
        if ("CREATED".equals(appointment.getStatus()) && paymentOrder != null && "FAILED".equals(paymentOrder.getStatus())) {
            return paymentOrder.getNotifyContent() == null || paymentOrder.getNotifyContent().isBlank()
                    ? "最近一次支付失败，请重新选择支付方式并完成支付。"
                    : paymentOrder.getNotifyContent();
        }
        if ("CREATED".equals(appointment.getStatus()) && paymentOrder != null && "PAYING".equals(paymentOrder.getStatus())) {
            return "支付单已创建，你可以继续完成支付或刷新支付状态。";
        }
        return switch (appointment.getStatus()) {
            case "CREATED" -> "请先完成支付，支付成功后预约才会进入待确认。";
            case "PENDING" -> "预约已支付成功，等待技师确认。";
            case "APPROVED" -> "预约已确认，请按照预约时间准时到店。";
            case "WAIT_SERVICE" -> "你已到达预约时段，等待技师开始服务。";
            case "IN_SERVICE" -> "服务进行中，请耐心等待完成。";
            case "COMPLETED" -> "服务已完成，现在可以提交评价。";
            case "REJECTED" -> "REFUNDED".equals(payStatus) ? "预约未通过，款项已按原路退款。" : "预约未通过。";
            case "CANCELLED" -> "REFUNDED".equals(payStatus) ? "预约已取消，款项已按原路退款。" : "预约已取消。";
            default -> "当前没有可执行的后续操作。";
        };
    }

    private String defaultRemark(String toStatus) {
        return switch (toStatus) {
            case "APPROVED" -> "预约审核通过";
            case "REJECTED" -> "预约审核未通过";
            case "WAIT_SERVICE" -> "预约进入待服务";
            case "IN_SERVICE" -> "预约进入服务中";
            case "COMPLETED" -> "预约服务完成";
            default -> "预约状态已更新";
        };
    }

    private String fallbackName(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null || fallback.isBlank() ? "-" : fallback;
    }

    private void notifyAppointmentPaid(Appointment appointment, ServiceItem item) {
        String serviceName = item == null ? "服务项目" : item.getName();
        notificationService.notifyUser(
                appointment.getUserId(),
                "APPOINTMENT",
                "预约支付成功",
                "预约 #" + appointment.getId() + " 已支付成功，等待技师确认。",
                "/appointments"
        );
        notificationService.notifyUser(
                technicianUserId(appointment.getTechnicianId()),
                "APPOINTMENT",
                "有新的预约待确认",
                "你有一条新的“" + serviceName + "”预约待确认，服务时间为 "
                        + appointment.getAppointmentTime() + " - " + appointmentEndTimeOf(appointment) + "。",
                "/tech/appointments"
        );
    }

    private void notifyTechnicianAssignment(Appointment appointment, String reason) {
        notificationService.notifyUser(
                technicianUserId(appointment.getTechnicianId()),
                "APPOINTMENT",
                "预约已分配给你",
                "预约 #" + appointment.getId() + " 已分配给你。"
                        + (reason == null || reason.isBlank() ? "" : " 推荐原因：" + reason),
                "/tech/appointments"
        );
    }

    private void notifyAppointmentStatusChanged(Appointment appointment, String fromStatus, String toStatus, String remark) {
        String title = "预约状态更新为" + label(STATUS_LABELS, toStatus, toStatus);
        String extra = "REJECTED".equals(toStatus) && "REFUNDED".equals(normalizePayStatus(appointment)) ? " 款项已按原路退款。" : "";
        String content = "预约 #" + appointment.getId() + "（" + serviceName(appointment.getServiceItemId()) + "）已从 "
                + label(STATUS_LABELS, fromStatus, fromStatus) + " 变更为 "
                + label(STATUS_LABELS, toStatus, toStatus) + "。"
                + (remark == null || remark.isBlank() ? "" : " 备注：" + remark) + extra;
        notificationService.notifyUser(appointment.getUserId(), "APPOINTMENT", title, content, "/appointments");
        notificationService.notifyUser(technicianUserId(appointment.getTechnicianId()), "APPOINTMENT", title, content, "/tech/appointments");
    }

    private void notifyAppointmentCancelled(Appointment appointment, boolean refunded) {
        String content = "预约 #" + appointment.getId() + "（" + serviceName(appointment.getServiceItemId()) + "）已取消。"
                + (refunded ? " 已发起原路退款处理。" : "");
        notificationService.notifyUser(appointment.getUserId(), "APPOINTMENT", "预约已取消", content, "/appointments");
        notificationService.notifyUser(technicianUserId(appointment.getTechnicianId()), "APPOINTMENT", "预约已取消", content, "/tech/appointments");
    }

    private void notifyAppointmentApprovedInChat(Appointment appointment, String fromStatus, String toStatus) {
        if (!"PENDING".equals(fromStatus) || !"APPROVED".equals(toStatus)) {
            return;
        }
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if (!"TECH".equals(currentUser.getRoleCode())) {
            return;
        }
        Long senderUserId = technicianUserId(appointment.getTechnicianId());
        if (senderUserId == null || !senderUserId.equals(currentUser.getUserId())) {
            return;
        }

        String appointmentPeriod = formatAppointmentPeriod(appointment.getAppointmentTime(), appointmentEndTimeOf(appointment));
        String content = "您好，您的预约已确认。预约时间为 " + appointmentPeriod
                + "，请按时到达；如需调整，请提前在在线沟通中联系我。";
        chatService.sendSystemMessage(senderUserId, appointment.getUserId(), content);
    }

    private String serviceName(Long serviceItemId) {
        ServiceItem item = serviceItemId == null ? null : serviceItemMapper.selectById(serviceItemId);
        return item == null ? "服务项目" : item.getName();
    }

    private Long technicianUserId(Long technicianId) {
        if (technicianId == null) {
            return null;
        }
        Technician technician = technicianMapper.selectById(technicianId);
        return technician == null ? null : technician.getUserId();
    }

    private String formatAppointmentPeriod(LocalDateTime startTime, LocalDateTime endTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return formatter.format(startTime) + " - " + formatter.format(endTime);
    }
}
