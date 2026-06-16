package com.bfsm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.cache.CacheService;
import com.bfsm.common.ApiResponse;
import com.bfsm.common.BizException;
import com.bfsm.common.SecurityUtils;
import com.bfsm.dto.AutoScheduleReq;
import com.bfsm.dto.StatusReq;
import com.bfsm.dto.TechnicianServiceReq;
import com.bfsm.entity.Announcement;
import com.bfsm.entity.Banner;
import com.bfsm.entity.Schedule;
import com.bfsm.entity.ServiceCategory;
import com.bfsm.entity.ServiceItem;
import com.bfsm.entity.SysRole;
import com.bfsm.entity.SysUser;
import com.bfsm.entity.SysUserRole;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.AnnouncementMapper;
import com.bfsm.mapper.BannerMapper;
import com.bfsm.mapper.ScheduleMapper;
import com.bfsm.mapper.ServiceCategoryMapper;
import com.bfsm.mapper.ServiceItemMapper;
import com.bfsm.mapper.SysRoleMapper;
import com.bfsm.mapper.SysUserMapper;
import com.bfsm.mapper.SysUserRoleMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.service.AutoScheduleService;
import com.bfsm.service.LeaveRecordService;
import com.bfsm.service.TechnicianRatingService;
import com.bfsm.vo.AutoSchedulePreviewVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/master")
@Tag(name = "基础数据模块")
public class MasterDataController {
    private static final String CACHE_TECHNICIANS = "list:technicians";
    private static final String CACHE_SERVICE_ITEMS = "list:service-items";
    private static final String CACHE_BANNERS = "list:banners";
    private static final String CACHE_ANNOUNCEMENTS = "list:announcements";

    private final TechnicianMapper technicianMapper;
    private final ServiceCategoryMapper serviceCategoryMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final AnnouncementMapper announcementMapper;
    private final BannerMapper bannerMapper;
    private final ScheduleMapper scheduleMapper;
    private final CacheService cacheService;
    private final LeaveRecordService leaveRecordService;
    private final AppointmentMapper appointmentMapper;
    private final AutoScheduleService autoScheduleService;
    private final JdbcTemplate jdbcTemplate;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final TechnicianRatingService technicianRatingService;

    public MasterDataController(
            TechnicianMapper technicianMapper,
            ServiceCategoryMapper serviceCategoryMapper,
            ServiceItemMapper serviceItemMapper,
            AnnouncementMapper announcementMapper,
            BannerMapper bannerMapper,
            ScheduleMapper scheduleMapper,
            CacheService cacheService,
            LeaveRecordService leaveRecordService,
            AppointmentMapper appointmentMapper,
            AutoScheduleService autoScheduleService,
            JdbcTemplate jdbcTemplate,
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            TechnicianRatingService technicianRatingService
    ) {
        this.technicianMapper = technicianMapper;
        this.serviceCategoryMapper = serviceCategoryMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.announcementMapper = announcementMapper;
        this.bannerMapper = bannerMapper;
        this.scheduleMapper = scheduleMapper;
        this.cacheService = cacheService;
        this.leaveRecordService = leaveRecordService;
        this.appointmentMapper = appointmentMapper;
        this.autoScheduleService = autoScheduleService;
        this.jdbcTemplate = jdbcTemplate;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.technicianRatingService = technicianRatingService;
    }

    @GetMapping("/technicians")
    @Operation(summary = "查询技师列表")
    public ApiResponse<List<Technician>> technicians() {
        List<Technician> list = cacheService.getOrLoad(
                CACHE_TECHNICIANS,
                List.class,
                Duration.ofMinutes(20),
                () -> technicianMapper.selectList(new LambdaQueryWrapper<Technician>().orderByAsc(Technician::getId))
        );
        return ApiResponse.ok(list);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/technicians")
    @Operation(summary = "新增或更新技师")
    @Transactional
    public ApiResponse<Void> saveTechnician(@RequestBody Technician body) {
        Technician existing = null;
        if (body.getId() != null) {
            existing = technicianMapper.selectById(body.getId());
            if (existing == null) {
                throw new BizException(404, "技师不存在");
            }
        }
        validateAndPrepareTechnician(body);
        technicianRatingService.prepareForAdminSave(body, existing);
        if (body.getId() == null) {
            technicianMapper.insert(body);
        } else {
            technicianMapper.updateById(body);
        }
        ensureTechRole(body.getUserId());
        cacheService.evict(CACHE_TECHNICIANS);
        cacheService.evict("auth:me:" + body.getUserId());
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/technicians/{id}/services")
    @Operation(summary = "查询技师可服务项目")
    public ApiResponse<List<Long>> technicianServices(@PathVariable Long id) {
        if (technicianMapper.selectById(id) == null) {
            throw new BizException(404, "技师不存在");
        }
        return ApiResponse.ok(jdbcTemplate.queryForList(
                "SELECT service_item_id FROM technician_service WHERE technician_id = ? ORDER BY service_item_id",
                Long.class,
                id
        ));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/technicians/{id}/services")
    @Operation(summary = "保存技师可服务项目")
    @Transactional
    public ApiResponse<Void> saveTechnicianServices(@PathVariable Long id, @RequestBody TechnicianServiceReq req) {
        if (technicianMapper.selectById(id) == null) {
            throw new BizException(404, "技师不存在");
        }
        jdbcTemplate.update("DELETE FROM technician_service WHERE technician_id = ?", id);
        if (req.getServiceItemIds() != null) {
            for (Long serviceItemId : req.getServiceItemIds()) {
                if (serviceItemId != null && serviceItemMapper.selectById(serviceItemId) != null) {
                    jdbcTemplate.update(
                            "INSERT INTO technician_service (technician_id, service_item_id) VALUES (?, ?)",
                            id,
                            serviceItemId
                    );
                }
            }
        }
        return ApiResponse.ok();
    }

    @GetMapping("/categories")
    @Operation(summary = "查询服务分类")
    public ApiResponse<List<ServiceCategory>> categories() {
        return ApiResponse.ok(
                serviceCategoryMapper.selectList(new LambdaQueryWrapper<ServiceCategory>().orderByAsc(ServiceCategory::getSortNo))
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/categories")
    @Operation(summary = "新增或更新服务分类")
    public ApiResponse<Void> saveCategory(@RequestBody ServiceCategory body) {
        if (body.getId() == null) {
            serviceCategoryMapper.insert(body);
        } else {
            serviceCategoryMapper.updateById(body);
        }
        return ApiResponse.ok();
    }

    @GetMapping("/service-items")
    @Operation(summary = "查询服务项目")
    public ApiResponse<List<ServiceItem>> serviceItems() {
        List<ServiceItem> list = cacheService.getOrLoad(
                CACHE_SERVICE_ITEMS,
                List.class,
                Duration.ofMinutes(20),
                () -> serviceItemMapper.selectList(
                        new LambdaQueryWrapper<ServiceItem>()
                                .eq(ServiceItem::getStatus, "ON")
                                .orderByAsc(ServiceItem::getId)
                )
        );
        return ApiResponse.ok(list);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/service-items/manage")
    @Operation(summary = "查询服务项目管理列表")
    public ApiResponse<List<ServiceItem>> serviceItemManageList() {
        return ApiResponse.ok(serviceItemMapper.selectList(
                new LambdaQueryWrapper<ServiceItem>()
                        .orderByAsc(ServiceItem::getCategoryId)
                        .orderByAsc(ServiceItem::getId)
        ));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/service-items")
    @Operation(summary = "新增或更新服务项目")
    public ApiResponse<Void> saveServiceItem(@RequestBody ServiceItem body) {
        if (body.getId() == null) {
            serviceItemMapper.insert(body);
        } else {
            serviceItemMapper.updateById(body);
        }
        cacheService.evict(CACHE_SERVICE_ITEMS);
        return ApiResponse.ok();
    }

    @GetMapping("/banners")
    @Operation(summary = "查询首页轮播图")
    public ApiResponse<List<Banner>> banners() {
        List<Banner> list = cacheService.getOrLoad(
                CACHE_BANNERS,
                List.class,
                Duration.ofMinutes(10),
                () -> bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getStatus, "ON")
                        .orderByAsc(Banner::getSortNo)
                        .orderByDesc(Banner::getId))
        );
        return ApiResponse.ok(list);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/banners/manage")
    @Operation(summary = "查询轮播图管理列表")
    public ApiResponse<List<Banner>> bannerManageList() {
        return ApiResponse.ok(
                bannerMapper.selectList(new LambdaQueryWrapper<Banner>().orderByAsc(Banner::getSortNo).orderByDesc(Banner::getId))
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/banners")
    @Operation(summary = "新增或更新轮播图")
    public ApiResponse<Void> saveBanner(@RequestBody Banner body) {
        if (!StringUtils.hasText(body.getTitle())) {
            throw new BizException(4201, "轮播图标题不能为空");
        }
        if (!StringUtils.hasText(body.getImageUrl())) {
            throw new BizException(4202, "轮播图图片地址不能为空");
        }
        if (!StringUtils.hasText(body.getStatus())) {
            body.setStatus("ON");
        }
        if (body.getSortNo() == null) {
            body.setSortNo(1);
        }
        if (body.getId() == null) {
            bannerMapper.insert(body);
        } else {
            bannerMapper.updateById(body);
        }
        cacheService.evict(CACHE_BANNERS);
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/banners/{id}/status")
    @Operation(summary = "更新轮播图状态")
    public ApiResponse<Void> updateBannerStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        Banner banner = bannerMapper.selectById(id);
        if (banner == null) {
            throw new BizException(404, "轮播图不存在");
        }
        banner.setStatus(req.getStatus());
        bannerMapper.updateById(banner);
        cacheService.evict(CACHE_BANNERS);
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/banners/{id}")
    @Operation(summary = "删除轮播图")
    public ApiResponse<Void> deleteBanner(@PathVariable Long id) {
        bannerMapper.deleteById(id);
        cacheService.evict(CACHE_BANNERS);
        return ApiResponse.ok();
    }

    @GetMapping("/announcements")
    @Operation(summary = "查询已发布资讯")
    public ApiResponse<List<Announcement>> announcements() {
        List<Announcement> list = cacheService.getOrLoad(
                CACHE_ANNOUNCEMENTS,
                List.class,
                Duration.ofMinutes(10),
                () -> announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getPublishStatus, "PUBLISHED")
                        .orderByDesc(Announcement::getPublishTime)
                        .orderByDesc(Announcement::getId))
        );
        return ApiResponse.ok(list);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/announcements/manage")
    @Operation(summary = "查询资讯管理列表")
    public ApiResponse<List<Announcement>> announcementManageList() {
        return ApiResponse.ok(
                announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                        .orderByDesc(Announcement::getPublishTime)
                        .orderByDesc(Announcement::getCreatedAt)
                        .orderByDesc(Announcement::getId))
        );
    }

    @GetMapping("/announcements/{id}")
    @Operation(summary = "查询资讯详情")
    public ApiResponse<Announcement> announcementDetail(@PathVariable Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BizException(404, "资讯不存在");
        }
        if (!"PUBLISHED".equals(announcement.getPublishStatus()) && !isAdmin()) {
            throw new BizException(404, "资讯不存在");
        }
        return ApiResponse.ok(announcement);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/announcements")
    @Operation(summary = "新增或更新资讯")
    public ApiResponse<Void> saveAnnouncement(@RequestBody Announcement body) {
        if (!StringUtils.hasText(body.getTitle())) {
            throw new BizException(4101, "资讯标题不能为空");
        }
        if (!StringUtils.hasText(body.getSummary())) {
            throw new BizException(4102, "资讯摘要不能为空");
        }
        if (!StringUtils.hasText(body.getContent())) {
            throw new BizException(4103, "资讯正文不能为空");
        }
        if (!StringUtils.hasText(body.getPublishStatus())) {
            body.setPublishStatus("DRAFT");
        }
        if ("PUBLISHED".equals(body.getPublishStatus()) && body.getPublishTime() == null) {
            body.setPublishTime(LocalDateTime.now());
        }
        if (body.getId() == null) {
            announcementMapper.insert(body);
        } else {
            announcementMapper.updateById(body);
        }
        cacheService.evict(CACHE_ANNOUNCEMENTS);
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/announcements/{id}/status")
    @Operation(summary = "更新资讯发布状态")
    public ApiResponse<Void> updateAnnouncementStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BizException(404, "资讯不存在");
        }
        announcement.setPublishStatus(req.getStatus());
        if ("PUBLISHED".equals(req.getStatus()) && announcement.getPublishTime() == null) {
            announcement.setPublishTime(LocalDateTime.now());
        }
        announcementMapper.updateById(announcement);
        cacheService.evict(CACHE_ANNOUNCEMENTS);
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/announcements/{id}")
    @Operation(summary = "删除资讯")
    public ApiResponse<Void> deleteAnnouncement(@PathVariable Long id) {
        announcementMapper.deleteById(id);
        cacheService.evict(CACHE_ANNOUNCEMENTS);
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('TECH')")
    @GetMapping("/schedules/my")
    @Operation(summary = "查询当前技师自己的排班")
    public ApiResponse<List<Schedule>> mySchedules(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BizException(4406, "开始日期不能晚于结束日期");
        }
        Technician technician = currentTechnicianOrThrow();
        return ApiResponse.ok(
                scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getTechnicianId, technician.getId())
                        .ge(Schedule::getWorkDate, startDate)
                        .le(Schedule::getWorkDate, endDate)
                        .orderByAsc(Schedule::getWorkDate)
                        .orderByAsc(Schedule::getStartTime))
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/schedules")
    @Operation(summary = "查询排班列表")
    public ApiResponse<List<Schedule>> schedules() {
        return ApiResponse.ok(
                scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                        .orderByDesc(Schedule::getWorkDate)
                        .orderByAsc(Schedule::getStartTime))
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/schedules")
    @Operation(summary = "新增或更新排班")
    public ApiResponse<Void> saveSchedule(@RequestBody Schedule body) {
        if (body.getTechnicianId() == null || body.getWorkDate() == null
                || body.getStartTime() == null || body.getEndTime() == null) {
            throw new BizException(4401, "排班必须包含技师、日期和时间段");
        }

        LocalDateTime startAt = LocalDateTime.of(body.getWorkDate(), body.getStartTime());
        LocalDateTime endAt = LocalDateTime.of(body.getWorkDate(), body.getEndTime());
        if (!startAt.isBefore(endAt)) {
            throw new BizException(4402, "下班时间必须晚于上班时间");
        }

        leaveRecordService.ensureNoApprovedLeaveConflict(body.getTechnicianId(), startAt, endAt, null);
        if (scheduleMapper.countOverlap(body.getTechnicianId(), body.getWorkDate(), body.getStartTime(), body.getEndTime(), body.getId()) > 0) {
            throw new BizException(4404, "同一技师同一天的班次不能重叠");
        }

        if (body.getId() == null) {
            scheduleMapper.insert(body);
        } else {
            Schedule old = scheduleMapper.selectById(body.getId());
            if (old == null) {
                throw new BizException(404, "排班记录不存在");
            }
            if (hasScheduleChanged(old, body)) {
                ensureNoAppointmentConflict(old);
            }
            scheduleMapper.updateById(body);
        }
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/schedules/auto-preview")
    @Operation(summary = "自动排班预览")
    public ApiResponse<AutoSchedulePreviewVo> autoSchedulePreview(@RequestBody AutoScheduleReq req) {
        return ApiResponse.ok(autoScheduleService.preview(req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/schedules/auto-apply")
    @Operation(summary = "应用自动排班")
    public ApiResponse<AutoSchedulePreviewVo> autoScheduleApply(@RequestBody AutoScheduleReq req) {
        return ApiResponse.ok(autoScheduleService.apply(req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/schedules/{id}")
    @Operation(summary = "取消排班")
    public ApiResponse<Void> deleteSchedule(@PathVariable Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BizException(404, "排班记录不存在");
        }
        if (schedule.getWorkDate() != null && schedule.getWorkDate().isBefore(LocalDate.now())) {
            throw new BizException(4403, "已过日期的排班不能取消");
        }
        ensureNoAppointmentConflict(schedule);
        scheduleMapper.deleteById(id);
        return ApiResponse.ok();
    }

    private void ensureNoAppointmentConflict(Schedule schedule) {
        LocalDateTime startAt = LocalDateTime.of(schedule.getWorkDate(), schedule.getStartTime());
        LocalDateTime endAt = LocalDateTime.of(schedule.getWorkDate(), schedule.getEndTime());
        if (appointmentMapper.countConflictInRange(schedule.getTechnicianId(), startAt, endAt) > 0) {
            throw new BizException(4405, "该排班时段已有有效预约，请先处理预约后再调整排班");
        }
    }

    private boolean hasScheduleChanged(Schedule old, Schedule next) {
        return !old.getTechnicianId().equals(next.getTechnicianId())
                || !old.getWorkDate().equals(next.getWorkDate())
                || !old.getStartTime().equals(next.getStartTime())
                || !old.getEndTime().equals(next.getEndTime())
                || !old.getStatus().equals(next.getStatus());
    }

    private void validateAndPrepareTechnician(Technician body) {
        if (body.getUserId() == null) {
            throw new BizException(4501, "请选择需要绑定的用户账号");
        }
        SysUser user = sysUserMapper.selectById(body.getUserId());
        if (user == null) {
            throw new BizException(4502, "绑定用户不存在");
        }
        if (!Integer.valueOf(1).equals(user.getEnabled())) {
            throw new BizException(4503, "绑定用户已禁用，不能设为技师");
        }
        Technician exists = technicianMapper.selectOne(new LambdaQueryWrapper<Technician>()
                .eq(Technician::getUserId, body.getUserId())
                .last("limit 1"));
        if (exists != null && (body.getId() == null || !exists.getId().equals(body.getId()))) {
            throw new BizException(4504, "该用户已绑定其他技师档案");
        }
        if (!StringUtils.hasText(body.getName())) {
            body.setName(resolveTechnicianName(user));
        }
        body.setLevelName(normalizeTechnicianLevel(body.getLevelName()));
        if (!StringUtils.hasText(body.getStatus())) {
            body.setStatus("ACTIVE");
        }
    }

    private void ensureTechRole(Long userId) {
        SysRole techRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, "TECH")
                .last("limit 1"));
        if (techRole == null) {
            throw new BizException(4505, "TECH 角色不存在");
        }
        Long count = sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, techRole.getId()));
        if (count != null && count > 0) {
            return;
        }
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(techRole.getId());
        sysUserRoleMapper.insert(userRole);
    }

    private String normalizeTechnicianLevel(String levelName) {
        if (!StringUtils.hasText(levelName) || "Standard Technician".equals(levelName.trim())) {
            return "中级技师";
        }
        return levelName.trim();
    }

    private String resolveTechnicianName(SysUser user) {
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        return user.getUsername();
    }

    private boolean isAdmin() {
        try {
            return "ADMIN".equals(SecurityUtils.currentUserOrThrow().getRoleCode());
        } catch (Exception ex) {
            return false;
        }
    }

    private Technician currentTechnicianOrThrow() {
        Long currentUserId = SecurityUtils.currentUserOrThrow().getUserId();
        Technician technician = technicianMapper.selectOne(
                new LambdaQueryWrapper<Technician>().eq(Technician::getUserId, currentUserId)
        );
        if (technician == null) {
            throw new BizException(4410, "Technician profile not found");
        }
        return technician;
    }
}
