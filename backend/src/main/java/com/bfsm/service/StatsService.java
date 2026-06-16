package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.bfsm.cache.CacheService;
import com.bfsm.common.SecurityUtils;
import com.bfsm.entity.Appointment;
import com.bfsm.entity.AttendanceRecord;
import com.bfsm.entity.Product;
import com.bfsm.entity.ProductCategory;
import com.bfsm.entity.ProductOrder;
import com.bfsm.entity.ProductOrderItem;
import com.bfsm.entity.ServiceItem;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.AttendanceRecordMapper;
import com.bfsm.mapper.ProductCategoryMapper;
import com.bfsm.mapper.ProductMapper;
import com.bfsm.mapper.ProductOrderItemMapper;
import com.bfsm.mapper.ProductOrderMapper;
import com.bfsm.mapper.ServiceItemMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.security.SecurityUser;
import com.bfsm.vo.AdminSalesOverviewVo;
import com.bfsm.vo.CategorySalesStatsVo;
import com.bfsm.vo.OverviewStatsVo;
import com.bfsm.vo.ProductSalesRankVo;
import com.bfsm.vo.SalesTrendPointVo;
import com.bfsm.vo.ServiceStatsVo;
import com.bfsm.vo.TechAttendanceChartVo;
import com.bfsm.vo.TechAttendanceDayVo;
import com.bfsm.vo.TechMonthStatsVo;
import com.bfsm.vo.TechPerformancePointVo;
import com.bfsm.vo.TechnicianStatsVo;
import com.bfsm.vo.TrendPointVo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StatsService {
    private final AppointmentMapper appointmentMapper;
    private final TechnicianMapper technicianMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final ProductOrderMapper productOrderMapper;
    private final ProductOrderItemMapper productOrderItemMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final AttendanceRecordMapper attendanceRecordMapper;
    private final CacheService cacheService;

    public StatsService(AppointmentMapper appointmentMapper, TechnicianMapper technicianMapper,
                        ServiceItemMapper serviceItemMapper, ProductOrderMapper productOrderMapper,
                        ProductOrderItemMapper productOrderItemMapper, ProductMapper productMapper,
                        ProductCategoryMapper productCategoryMapper, AttendanceRecordMapper attendanceRecordMapper,
                        CacheService cacheService) {
        this.appointmentMapper = appointmentMapper;
        this.technicianMapper = technicianMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.productOrderMapper = productOrderMapper;
        this.productOrderItemMapper = productOrderItemMapper;
        this.productMapper = productMapper;
        this.productCategoryMapper = productCategoryMapper;
        this.attendanceRecordMapper = attendanceRecordMapper;
        this.cacheService = cacheService;
    }

    public OverviewStatsVo overview() {
        return cacheService.getOrLoad("stats:overview", OverviewStatsVo.class, Duration.ofMinutes(5), this::buildOverview);
    }

    public List<TrendPointVo> appointmentTrend() {
        return cacheService.getOrLoad(
                "stats:trend:7d",
                new TypeReference<List<TrendPointVo>>() {
                },
                Duration.ofMinutes(5),
                this::buildAppointmentTrend
        );
    }

    public List<ServiceStatsVo> serviceStats() {
        return cacheService.getOrLoad(
                "stats:service",
                new TypeReference<List<ServiceStatsVo>>() {
                },
                Duration.ofMinutes(5),
                this::buildServiceStats
        );
    }

    public List<TechnicianStatsVo> technicianStats() {
        return cacheService.getOrLoad(
                "stats:technician",
                new TypeReference<List<TechnicianStatsVo>>() {
                },
                Duration.ofMinutes(5),
                this::buildTechnicianStats
        );
    }

    public TechMonthStatsVo techMonth(String month) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        Technician technician = technicianMapper.selectOne(
                new LambdaQueryWrapper<Technician>().eq(Technician::getUserId, currentUser.getUserId())
        );
        String targetMonth = normalizeMonth(month);
        if (technician == null) {
            return new TechMonthStatsVo(targetMonth, 0, BigDecimal.ZERO);
        }

        long count = appointmentMapper.monthlyCompletedCount(technician.getId(), targetMonth);
        BigDecimal amount = appointmentMapper.monthlyAmount(technician.getId(), targetMonth);
        return new TechMonthStatsVo(targetMonth, count, amount);
    }

    public List<TechPerformancePointVo> techPerformanceTrend(String month) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        Technician technician = technicianMapper.selectOne(
                new LambdaQueryWrapper<Technician>().eq(Technician::getUserId, currentUser.getUserId())
        );
        String targetMonth = normalizeMonth(month);
        if (technician == null) {
            return List.of();
        }

        YearMonth yearMonth = YearMonth.parse(targetMonth);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        List<Appointment> appointments = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getTechnicianId, technician.getId())
                .eq(Appointment::getStatus, "COMPLETED")
                .ge(Appointment::getAppointmentTime, start)
                .lt(Appointment::getAppointmentTime, end));

        Map<LocalDate, List<Appointment>> grouped = appointments.stream()
                .collect(Collectors.groupingBy(item -> item.getAppointmentTime().toLocalDate()));
        List<TechPerformancePointVo> result = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            List<Appointment> dayAppointments = grouped.getOrDefault(date, List.of());
            BigDecimal amount = dayAppointments.stream()
                    .map(item -> item.getAmount() == null ? BigDecimal.ZERO : item.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new TechPerformancePointVo(date.toString(), (long) dayAppointments.size(), amount));
        }
        return result;
    }

    public TechAttendanceChartVo techAttendanceChart(String month) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        Technician technician = technicianMapper.selectOne(
                new LambdaQueryWrapper<Technician>().eq(Technician::getUserId, currentUser.getUserId())
        );
        String targetMonth = normalizeMonth(month);
        if (technician == null) {
            return new TechAttendanceChartVo(targetMonth, 0L, 0L, 0L, 0L, List.of());
        }

        YearMonth yearMonth = YearMonth.parse(targetMonth);
        List<AttendanceRecord> records = attendanceRecordMapper.selectList(new LambdaQueryWrapper<AttendanceRecord>()
                .eq(AttendanceRecord::getTechnicianId, technician.getId())
                .ge(AttendanceRecord::getWorkDate, yearMonth.atDay(1))
                .le(AttendanceRecord::getWorkDate, yearMonth.atEndOfMonth())
                .orderByAsc(AttendanceRecord::getWorkDate));
        Map<LocalDate, AttendanceRecord> recordMap = records.stream()
                .collect(Collectors.toMap(AttendanceRecord::getWorkDate, Function.identity(), (left, right) -> left));

        long onDutyCount = 0L;
        long offDutyCount = 0L;
        long leaveCount = 0L;
        long absentCount = 0L;
        List<TechAttendanceDayVo> days = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            AttendanceRecord record = recordMap.get(date);
            String status = record == null ? "ABSENT" : record.getStatus();
            String statusLabel = switch (status) {
                case "ON_DUTY" -> "在岗";
                case "OFF_DUTY" -> "已下班";
                case "ON_LEAVE" -> "请假";
                case "NOT_CHECKED_IN" -> "未打卡";
                default -> "缺勤";
            };
            if ("ON_DUTY".equals(status)) {
                onDutyCount++;
            } else if ("OFF_DUTY".equals(status)) {
                offDutyCount++;
            } else if ("ON_LEAVE".equals(status)) {
                leaveCount++;
            } else {
                absentCount++;
            }
            days.add(new TechAttendanceDayVo(
                    date.toString(),
                    status,
                    statusLabel,
                    record == null || record.getCheckInTime() == null ? null : record.getCheckInTime().toLocalTime().toString(),
                    record == null || record.getCheckOutTime() == null ? null : record.getCheckOutTime().toLocalTime().toString(),
                    toMinute(record == null || record.getCheckInTime() == null ? null : record.getCheckInTime().toLocalTime()),
                    toMinute(record == null || record.getCheckOutTime() == null ? null : record.getCheckOutTime().toLocalTime())
            ));
        }
        return new TechAttendanceChartVo(targetMonth, onDutyCount, offDutyCount, leaveCount, absentCount, days);
    }

    public AdminSalesOverviewVo adminSalesOverview(String month) {
        String targetMonth = normalizeMonth(month);
        List<ProductOrder> orders = paidOrdersForMonth(targetMonth);
        BigDecimal totalSales = orders.stream()
                .map(item -> item.getPayAmount() == null ? BigDecimal.ZERO : item.getPayAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long soldItemCount = orders.stream().mapToLong(item -> item.getItemCount() == null ? 0 : item.getItemCount()).sum();
        long productCount = productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getStatus, "ON"));
        return new AdminSalesOverviewVo(targetMonth, totalSales, (long) orders.size(), soldItemCount, productCount);
    }

    public List<SalesTrendPointVo> adminSalesTrend(String month) {
        String targetMonth = normalizeMonth(month);
        YearMonth yearMonth = YearMonth.parse(targetMonth);
        Map<LocalDate, BigDecimal> amountByDate = new LinkedHashMap<>();
        for (ProductOrder order : paidOrdersForMonth(targetMonth)) {
            LocalDate payDate = order.getPayTime().toLocalDate();
            amountByDate.merge(payDate, order.getPayAmount(), BigDecimal::add);
        }
        List<SalesTrendPointVo> result = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            result.add(new SalesTrendPointVo(date.toString(), amountByDate.getOrDefault(date, BigDecimal.ZERO)));
        }
        return result;
    }

    public List<ProductSalesRankVo> adminProductRanking(String month) {
        String targetMonth = normalizeMonth(month);
        List<ProductOrder> orders = paidOrdersForMonth(targetMonth);
        if (orders.isEmpty()) {
            return List.of();
        }
        List<ProductOrderItem> items = productOrderItemMapper.selectList(new LambdaQueryWrapper<ProductOrderItem>()
                .in(ProductOrderItem::getOrderId, orders.stream().map(ProductOrder::getId).toList()));
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> productMap = productMapper.selectBatchIds(items.stream().map(ProductOrderItem::getProductId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Integer> quantityMap = new HashMap<>();
        Map<Long, BigDecimal> amountMap = new HashMap<>();
        for (ProductOrderItem item : items) {
            quantityMap.merge(item.getProductId(), item.getQuantity(), Integer::sum);
            amountMap.merge(item.getProductId(), item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())), BigDecimal::add);
        }
        return quantityMap.entrySet().stream()
                .map(entry -> new ProductSalesRankVo(
                        entry.getKey(),
                        productMap.get(entry.getKey()) == null ? "-" : productMap.get(entry.getKey()).getName(),
                        entry.getValue(),
                        amountMap.getOrDefault(entry.getKey(), BigDecimal.ZERO)
                ))
                .sorted(Comparator.comparing(ProductSalesRankVo::getQuantity).reversed()
                        .thenComparing(ProductSalesRankVo::getAmount, Comparator.reverseOrder()))
                .limit(10)
                .toList();
    }

    public List<CategorySalesStatsVo> adminCategoryStats(String month) {
        String targetMonth = normalizeMonth(month);
        List<ProductOrder> orders = paidOrdersForMonth(targetMonth);
        if (orders.isEmpty()) {
            return List.of();
        }
        List<ProductOrderItem> items = productOrderItemMapper.selectList(new LambdaQueryWrapper<ProductOrderItem>()
                .in(ProductOrderItem::getOrderId, orders.stream().map(ProductOrder::getId).toList()));
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> productMap = productMapper.selectBatchIds(items.stream().map(ProductOrderItem::getProductId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductCategory> categoryMap = productCategoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>())
                .stream()
                .collect(Collectors.toMap(ProductCategory::getId, Function.identity()));
        Map<Long, Integer> quantityMap = new HashMap<>();
        Map<Long, BigDecimal> amountMap = new HashMap<>();
        for (ProductOrderItem item : items) {
            Product product = productMap.get(item.getProductId());
            if (product == null || product.getCategoryId() == null) {
                continue;
            }
            quantityMap.merge(product.getCategoryId(), item.getQuantity(), Integer::sum);
            amountMap.merge(product.getCategoryId(), item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())), BigDecimal::add);
        }
        return quantityMap.entrySet().stream()
                .map(entry -> new CategorySalesStatsVo(
                        entry.getKey(),
                        categoryMap.get(entry.getKey()) == null ? "未分类" : categoryMap.get(entry.getKey()).getName(),
                        entry.getValue(),
                        amountMap.getOrDefault(entry.getKey(), BigDecimal.ZERO)
                ))
                .sorted(Comparator.comparing(CategorySalesStatsVo::getAmount, Comparator.reverseOrder()))
                .toList();
    }

    private OverviewStatsVo buildOverview() {
        List<Appointment> appointments = appointmentMapper.selectList(new LambdaQueryWrapper<>());
        long total = appointments.size();
        long completed = appointments.stream().filter(item -> "COMPLETED".equals(item.getStatus())).count();
        long cancelled = appointments.stream().filter(item -> "CANCELLED".equals(item.getStatus())).count();
        long pending = appointments.stream().filter(item -> "PENDING".equals(item.getStatus())).count();
        return new OverviewStatsVo(
                total,
                completed,
                cancelled,
                pending,
                ratio(completed, total),
                ratio(cancelled, total)
        );
    }

    private List<TrendPointVo> buildAppointmentTrend() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        List<Appointment> appointments = appointmentMapper.selectList(
                new LambdaQueryWrapper<Appointment>().ge(Appointment::getAppointmentTime, start.atStartOfDay())
        );
        Map<LocalDate, Long> grouped = appointments.stream()
                .collect(Collectors.groupingBy(item -> item.getAppointmentTime().toLocalDate(), Collectors.counting()));

        List<TrendPointVo> points = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            points.add(new TrendPointVo(date.toString(), grouped.getOrDefault(date, 0L)));
        }
        return points;
    }

    private List<ServiceStatsVo> buildServiceStats() {
        List<ServiceItem> serviceItems = serviceItemMapper.selectList(new LambdaQueryWrapper<ServiceItem>());
        Map<Long, String> itemNames = serviceItems.stream()
                .collect(Collectors.toMap(ServiceItem::getId, ServiceItem::getName));

        List<Appointment> appointments = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getStatus, "COMPLETED"));
        Map<Long, Long> grouped = appointments.stream()
                .collect(Collectors.groupingBy(Appointment::getServiceItemId, Collectors.counting()));

        return grouped.entrySet().stream()
                .map(entry -> new ServiceStatsVo(entry.getKey(), itemNames.getOrDefault(entry.getKey(), "未命名项目"), entry.getValue()))
                .sorted(Comparator.comparingLong(ServiceStatsVo::getCount).reversed())
                .toList();
    }

    private List<TechnicianStatsVo> buildTechnicianStats() {
        List<Technician> technicians = technicianMapper.selectList(new LambdaQueryWrapper<Technician>());
        List<Appointment> appointments = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getStatus, "COMPLETED"));

        Map<Long, Long> countMap = appointments.stream()
                .collect(Collectors.groupingBy(Appointment::getTechnicianId, Collectors.counting()));
        Map<Long, BigDecimal> amountMap = new HashMap<>();
        for (Appointment appointment : appointments) {
            amountMap.merge(
                    appointment.getTechnicianId(),
                    appointment.getAmount() == null ? BigDecimal.ZERO : appointment.getAmount(),
                    BigDecimal::add
            );
        }

        List<TechnicianStatsVo> result = new ArrayList<>();
        for (Technician technician : technicians) {
            result.add(new TechnicianStatsVo(
                    technician.getId(),
                    technician.getName(),
                    countMap.getOrDefault(technician.getId(), 0L),
                    amountMap.getOrDefault(technician.getId(), BigDecimal.ZERO)
            ));
        }
        result.sort(Comparator.comparingLong(TechnicianStatsVo::getCompletedCount).reversed()
                .thenComparing(TechnicianStatsVo::getAmount, Comparator.reverseOrder()));
        return result;
    }

    private String normalizeMonth(String month) {
        if (month == null || month.isBlank()) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        return YearMonth.parse(month).toString();
    }

    private List<ProductOrder> paidOrdersForMonth(String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        return productOrderMapper.selectList(new LambdaQueryWrapper<ProductOrder>()
                .in(ProductOrder::getStatus, List.of("PAID", "SHIPPED", "COMPLETED"))
                .ge(ProductOrder::getPayTime, start)
                .lt(ProductOrder::getPayTime, end));
    }

    private Integer toMinute(LocalTime time) {
        return time == null ? null : time.getHour() * 60 + time.getMinute();
    }

    private double ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return 0D;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
