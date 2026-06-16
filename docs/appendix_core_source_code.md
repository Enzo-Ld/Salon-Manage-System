# 附录  程序源代码

## 模块的核心代码

说明：本附录代码均摘自项目 `E:\Java\bfsm` 的实际源文件，未编写与项目无关的伪代码。为便于论文阅读，代码块中以 `// 附录注释：` 开头的内容为后补说明性注释，其余语句均来自项目源码。

---

### 1. 认证与权限模块

#### 1.1 Spring Security 安全配置

源文件：`backend/src/main/java/com/bfsm/security/SecurityConfig.java`

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 附录注释：使用 BCrypt 对用户密码进行不可逆加密存储。
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                // 附录注释：系统采用 JWT，无需服务端 Session。
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/payments/alipay/notify",
                                "/api/payments/wechat/notify",
                                "/api/payments/wechat/refund-notify",
                                "/avatars/**",
                                "/uploads/**",
                                "/ws/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/doc.html",
                                "/webjars/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(401, "UNAUTHORIZED")));
                }));

        // 附录注释：将 JWT 解析过滤器放在用户名密码认证过滤器之前。
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

#### 1.2 JWT 生成与解析

源文件：`backend/src/main/java/com/bfsm/security/JwtTokenService.java`

```java
@Service
public class JwtTokenService {
    private final JwtProperties props;
    private final Key key;

    public JwtTokenService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String username, String roleCode) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getAccessTokenTtlSeconds());
        return Jwts.builder()
                .setIssuer(props.getIssuer())
                .setSubject(String.valueOf(userId))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                // 附录注释：JWT 中写入用户名和角色，后续接口鉴权会使用角色信息。
                .addClaims(Map.of(
                        "username", username,
                        "role", roleCode
                ))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
```

#### 1.3 JWT 请求过滤器

源文件：`backend/src/main/java/com/bfsm/security/JwtAuthFilter.java`

```java
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtTokenService jwtTokenService;

    public JwtAuthFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring("Bearer ".length());
            try {
                Claims claims = jwtTokenService.parse(token);
                Long userId = Long.valueOf(claims.getSubject());
                String username = (String) claims.get("username");
                String role = (String) claims.get("role");

                // 附录注释：将 JWT 中的用户身份放入 Spring Security 上下文。
                SecurityUser principal = new SecurityUser(userId, username, "", role, true);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ignored) {
                // ignore invalid token, continue as anonymous
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

#### 1.4 注册、登录与当前用户缓存

源文件：`backend/src/main/java/com/bfsm/service/AuthService.java`

```java
@Transactional
public void register(AuthRegisterReq req) {
    String username = req.getUsername().trim();
    String roleCode = req.getRoleCode().trim().toUpperCase();
    String nickname = normalizeNullable(req.getNickname());
    String phone = PhoneUtils.normalize(req.getPhone());
    PhoneUtils.validateOrThrow(phone);

    SysUser exists = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, username));
    if (exists != null) {
        throw new BizException(4001, "用户已存在");
    }
    if (phone != null && hasUserWithPhone(phone, null)) {
        throw new BizException(4311, "电话号码已被绑定");
    }
    if (!"USER".equals(roleCode)) {
        throw new BizException(4002, "仅能注册普通用户");
    }

    SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getCode, roleCode));
    if (role == null) {
        throw new BizException(4003, "角色不存在");
    }

    SysUser user = new SysUser();
    user.setUsername(username);
    // 附录注释：注册时不保存明文密码，而是保存 BCrypt 加密后的结果。
    user.setPassword(passwordEncoder.encode(req.getPassword().trim()));
    user.setNickname(nickname);
    user.setPhone(phone);
    user.setEnabled(1);
    userMapper.insert(user);

    SysUserRole userRole = new SysUserRole();
    userRole.setUserId(user.getId());
    userRole.setRoleId(role.getId());
    userRoleMapper.insert(userRole);
}

public AuthTokenVo login(AuthLoginReq req) {
    SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, req.getUsername()));
    if (user == null || user.getEnabled() == null || user.getEnabled() == 0) {
        throw new BizException(4004, "账户不存在或已被停用");
    }
    if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
        throw new BizException(4005, "用户名或密码不正确");
    }

    SysUserRole userRole = userRoleMapper.selectOne(new LambdaQueryWrapper<SysUserRole>()
            .eq(SysUserRole::getUserId, user.getId()));
    if (userRole == null) {
        throw new BizException(4006, "用户没有角色关联");
    }
    SysRole role = roleMapper.selectById(userRole.getRoleId());
    if (role == null) {
        throw new BizException(4007, "角色信息不可用");
    }

    // 附录注释：登录成功后签发访问令牌，前端后续请求携带该 token。
    String token = jwtTokenService.createAccessToken(user.getId(), user.getUsername(), role.getCode());
    return new AuthTokenVo(token, jwtProperties.getAccessTokenTtlSeconds());
}

public CurrentUserVo currentUser() {
    SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
    // 附录注释：当前用户信息会缓存 20 分钟，减少频繁查询用户表。
    return cacheService.getOrLoad(
            "auth:me:" + currentUser.getUserId(),
            CurrentUserVo.class,
            Duration.ofMinutes(20),
            () -> buildCurrentUser(currentUser)
    );
}
```

---

### 2. 预约与排班模块

#### 2.1 预约提交

源文件：`backend/src/main/java/com/bfsm/service/AppointmentService.java`

```java
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
        // 附录注释：用户未指定技师时，由排班服务自动推荐可服务技师。
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

    // 附录注释：按技师和日期加 Redis 锁，避免多个用户同时抢占同一技师档期。
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
```

#### 2.2 可预约时间段生成

源文件：`backend/src/main/java/com/bfsm/service/AppointmentService.java`

```java
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

        // 附录注释：以 30 分钟为步长生成候选时间段，并逐一排除过期、请假、已有预约冲突的档期。
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
```

#### 2.3 排班冲突检查

源文件：`backend/src/main/java/com/bfsm/service/ScheduleConflictChecker.java`

```java
@Service
public class ScheduleConflictChecker {
    private final TechnicianMapper technicianMapper;
    private final ScheduleMapper scheduleMapper;
    private final AppointmentMapper appointmentMapper;
    private final LeaveRecordService leaveRecordService;

    public ScheduleConflictChecker(TechnicianMapper technicianMapper, ScheduleMapper scheduleMapper,
                                   AppointmentMapper appointmentMapper, LeaveRecordService leaveRecordService) {
        this.technicianMapper = technicianMapper;
        this.scheduleMapper = scheduleMapper;
        this.appointmentMapper = appointmentMapper;
        this.leaveRecordService = leaveRecordService;
    }

    public void validateTechnicianForAppointment(Long technicianId, Long serviceItemId,
                                                 LocalDateTime startTime, LocalDateTime endTime) {
        // 附录注释：一个技师必须同时满足“支持服务、正在排班、没有请假”三项条件。
        if (!supportsService(technicianId, serviceItemId)) {
            throw new BizException(4008, "Selected technician does not support this service item");
        }
        if (!isWorkingDuring(technicianId, startTime, endTime)) {
            throw new BizException(4009, "Selected technician is not working in this time range");
        }
        if (isOnLeaveDuring(technicianId, startTime, endTime)) {
            throw new BizException(4311, "Selected technician is on leave in this period");
        }
    }

    public boolean supportsService(Long technicianId, Long serviceItemId) {
        return technicianMapper.countServiceSupport(technicianId, serviceItemId) > 0;
    }

    public boolean isWorkingDuring(Long technicianId, LocalDateTime startTime, LocalDateTime endTime) {
        return scheduleMapper.countWorkingRange(
                technicianId,
                startTime.toLocalDate(),
                startTime.toLocalTime(),
                endTime.toLocalTime()
        ) > 0;
    }

    public boolean isOnLeaveDuring(Long technicianId, LocalDateTime startTime, LocalDateTime endTime) {
        return leaveRecordService.isOnLeaveDuring(technicianId, startTime, endTime);
    }

    public boolean hasConflictInRange(Long technicianId, LocalDateTime startTime, LocalDateTime endTime) {
        return appointmentMapper.countConflict(technicianId, startTime, endTime) > 0;
    }

    public boolean hasConflictInRangeExclude(Long technicianId, LocalDateTime startTime,
                                             LocalDateTime endTime, Long excludeId) {
        return appointmentMapper.countConflictExclude(technicianId, startTime, endTime, excludeId) > 0;
    }
}
```

#### 2.4 技师自动推荐

源文件：`backend/src/main/java/com/bfsm/service/SchedulingService.java`

```java
public TechnicianRecommendationVo recommendFromCandidates(ServiceItem item, LocalDateTime appointmentTime,
                                                          List<Technician> candidates) {
    if (item == null || item.getDurationMin() == null || candidates == null || candidates.isEmpty()) {
        return null;
    }

    LocalDateTime appointmentEndTime = appointmentTime.plusMinutes(item.getDurationMin());
    return candidates.stream()
            .filter(technician -> scheduleConflictChecker.isWorkingDuring(technician.getId(), appointmentTime, appointmentEndTime))
            .filter(technician -> !scheduleConflictChecker.isOnLeaveDuring(technician.getId(), appointmentTime, appointmentEndTime))
            .filter(technician -> !scheduleConflictChecker.hasConflictInRange(technician.getId(), appointmentTime, appointmentEndTime))
            .map(technician -> toRecommendation(technician, appointmentTime, appointmentEndTime, item.getDurationMin()))
            // 附录注释：选择综合评分最高的技师作为推荐结果。
            .max(Comparator.comparingDouble(TechnicianRecommendationVo::getScore))
            .orElse(null);
}

private TechnicianRecommendationVo toRecommendation(Technician technician, LocalDateTime appointmentTime,
                                                    LocalDateTime appointmentEndTime, Integer durationMin) {
    long dayLoad = appointmentMapper.countDayLoad(technician.getId(), appointmentTime.toLocalDate());
    double rating = technician.getScore() == null ? 5.0D : technician.getScore().doubleValue();
    double score = 60 + rating * 8 - dayLoad * 10;

    List<String> reasons = new ArrayList<>();
    reasons.add("具备当前服务项目能力");
    reasons.add("该服务时段完整落在排班内");
    reasons.add("该时间段没有请假和预约冲突");
    reasons.add("今日负载 " + dayLoad + " 单");
    reasons.add("历史评分 " + String.format("%.1f", rating));

    TechnicianRecommendationVo recommendation = new TechnicianRecommendationVo();
    recommendation.setTechnicianId(technician.getId());
    recommendation.setTechnicianName(technician.getName());
    recommendation.setScore(score);
    recommendation.setReason(String.join("，", reasons));
    recommendation.setAppointmentTime(appointmentTime);
    recommendation.setAppointmentEndTime(appointmentEndTime);
    recommendation.setDurationMinSnapshot(durationMin);
    return recommendation;
}
```

#### 2.5 预约状态流转与取消

源文件：`backend/src/main/java/com/bfsm/service/AppointmentService.java`

```java
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

    // 附录注释：FLOW 定义了预约状态机，防止非法状态跳转。
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
```

---

### 3. 支付模块

#### 3.1 发起商品订单、预约与钱包充值支付

源文件：`backend/src/main/java/com/bfsm/service/PaymentService.java`

```java
@Transactional
public PaymentCreateVo createProductOrderPayment(Long orderId, PaymentCreateReq req) {
    SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
    String payChannel = normalizeCheckoutChannel(req.getPayChannel());
    String payMode = resolveSupportedPayMode(payChannel);
    ProductOrder order = requireCurrentUserOrder(orderId, currentUser.getUserId());
    if (!"CREATED".equals(order.getStatus()) || !"UNPAID".equals(order.getPayStatus())) {
        throw new BizException(4701, "当前订单不可发起支付");
    }

    closePendingPayments("PRODUCT_ORDER", order.getId(), "A new payment was created");

    PaymentOrder paymentOrder = new PaymentOrder();
    paymentOrder.setBusinessType("PRODUCT_ORDER");
    paymentOrder.setBusinessId(order.getId());
    paymentOrder.setPaymentNo(buildPaymentNo("P"));
    paymentOrder.setOutTradeNo(buildOutTradeNo("PO", order.getId()));
    paymentOrder.setPayChannel(payChannel);
    paymentOrder.setPayMode(payMode);
    paymentOrder.setAmount(scale(order.getPayAmount()));
    paymentOrder.setStatus("PAYING");
    paymentOrder.setSubject("商品订单支付 #" + order.getId());
    paymentOrder.setBodyText("用于商品订单的统一支付收银台流程");
    paymentOrder.setExpireTime(LocalDateTime.now().plusMinutes(15));
    paymentOrderMapper.insert(paymentOrder);

    // 附录注释：根据支付模式路由到对应支付网关。
    return gatewayForMode(payMode).create(paymentOrder);
}

@Transactional
public PaymentCreateVo createAppointmentPayment(Long appointmentId, PaymentCreateReq req) {
    SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
    String payChannel = normalizeCheckoutChannel(req.getPayChannel());
    String payMode = resolveSupportedPayMode(payChannel);
    Appointment appointment = requireCurrentUserAppointment(appointmentId, currentUser.getUserId());
    if (!"CREATED".equals(appointment.getStatus()) || !"UNPAID".equals(normalizeAppointmentPayStatus(appointment))) {
        throw new BizException(4711, "当前预约不可发起支付");
    }

    closePendingPayments("APPOINTMENT", appointment.getId(), "A new payment was created");

    PaymentOrder paymentOrder = new PaymentOrder();
    paymentOrder.setBusinessType("APPOINTMENT");
    paymentOrder.setBusinessId(appointment.getId());
    paymentOrder.setPaymentNo(buildPaymentNo("A"));
    paymentOrder.setOutTradeNo(buildOutTradeNo("AP", appointment.getId()));
    paymentOrder.setPayChannel(payChannel);
    paymentOrder.setPayMode(payMode);
    paymentOrder.setAmount(scale(appointment.getAmount()));
    paymentOrder.setStatus("PAYING");
    paymentOrder.setSubject(appointmentSubject(appointment));
    paymentOrder.setBodyText("用于服务预约的统一支付收银台流程");
    paymentOrder.setExpireTime(LocalDateTime.now().plusMinutes(15));
    paymentOrderMapper.insert(paymentOrder);

    return gatewayForMode(payMode).create(paymentOrder);
}

@Transactional
public PaymentCreateVo createWalletRechargePayment(RechargeReq req) {
    SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
    String payChannel = normalizeCheckoutChannel(req.getPayMethod());
    String payMode = resolveSupportedPayMode(payChannel);
    BigDecimal amount = scale(req.getAmount());
    Long activityId = validateRechargeActivity(req.getActivityId(), amount);

    closePendingPayments("WALLET_RECHARGE", currentUser.getUserId(), "A new payment was created");

    PaymentOrder paymentOrder = new PaymentOrder();
    paymentOrder.setBusinessType("WALLET_RECHARGE");
    paymentOrder.setBusinessId(currentUser.getUserId());
    paymentOrder.setPaymentNo(buildPaymentNo("R"));
    paymentOrder.setOutTradeNo(buildOutTradeNo("WR", currentUser.getUserId()));
    paymentOrder.setPayChannel(payChannel);
    paymentOrder.setPayMode(payMode);
    paymentOrder.setAmount(amount);
    paymentOrder.setStatus("PAYING");
    paymentOrder.setSubject("钱包充值");
    paymentOrder.setBodyText("用于钱包充值的统一支付收银台流程");
    paymentOrder.setExtraInfo(activityId == null ? null : String.valueOf(activityId));
    paymentOrder.setExpireTime(LocalDateTime.now().plusMinutes(15));
    paymentOrderMapper.insert(paymentOrder);

    return gatewayForMode(payMode).create(paymentOrder);
}
```

#### 3.2 支付成功后的业务同步

源文件：`backend/src/main/java/com/bfsm/service/PaymentService.java`

```java
private void markPaymentSuccess(PaymentOrder paymentOrder,
                                String platformTradeNo,
                                LocalDateTime paidTime,
                                String notifyContent) {
    if (!PENDING_STATUSES.contains(paymentOrder.getStatus())) {
        return;
    }
    LocalDateTime actualPaidTime = paidTime == null ? LocalDateTime.now() : paidTime;
    int updated = paymentOrderMapper.markSuccessIfPending(
            paymentOrder.getId(),
            platformTradeNo,
            actualPaidTime,
            notifyContent
    );
    if (updated == 0) {
        return;
    }
    PaymentOrder successOrder = paymentOrderMapper.selectById(paymentOrder.getId());
    if (successOrder == null) {
        successOrder = paymentOrder;
        successOrder.setStatus("SUCCESS");
        successOrder.setPaidTime(actualPaidTime);
        successOrder.setPlatformTradeNo(platformTradeNo);
        successOrder.setNotifyContent(notifyContent);
    }
    // 附录注释：支付成功后按业务类型同步订单、预约或钱包充值状态。
    applyBusinessSuccess(successOrder);
}

private void applyAppointmentPaymentSuccess(PaymentOrder paymentOrder) {
    Appointment appointment = appointmentMapper.selectById(paymentOrder.getBusinessId());
    if (appointment == null) {
        throw new BizException(4707, "关联预约不存在");
    }
    if (!"UNPAID".equals(normalizeAppointmentPayStatus(appointment)) || !"CREATED".equals(appointment.getStatus())) {
        return;
    }

    appointment.setPayMethod(paymentOrder.getPayChannel());
    appointment.setPayStatus("PAID");
    appointment.setPayTime(paymentOrder.getPaidTime());
    appointment.setPayNo(paymentOrder.getPaymentNo());

    // 附录注释：支付成功后再次校验档期，若已不可用则自动退款。
    String blockReason = appointmentPaymentBlockReason(appointment);
    if (blockReason != null) {
        appointment.setStatus("CANCELLED");
        appointment.setCancelTime(LocalDateTime.now());
        appointmentMapper.updateById(appointment);
        PaymentRefund refund = createOrLoadRefund(paymentOrder, scale(appointment.getAmount()),
                "预约支付后档期不可用自动退款：" + blockReason);
        if (refund != null && "SUCCESS".equals(refund.getStatus())) {
            appointment.setPayStatus("REFUNDED");
            appointmentMapper.updateById(appointment);
        }
        notificationService.notifyUser(
                appointment.getUserId(),
                "APPOINTMENT",
                "预约已自动退款",
                "预约 #" + appointment.getId() + " 支付成功后检测到档期不可用，系统已发起原路退款。",
                "/appointments"
        );
        return;
    }

    appointment.setStatus("PENDING");
    appointmentMapper.updateById(appointment);

    ServiceItem serviceItem = serviceItemMapper.selectById(appointment.getServiceItemId());
    String serviceName = serviceItem == null ? "服务项目" : serviceItem.getName();
    LocalDateTime endTime = appointmentEndTimeOf(appointment);

    notificationService.notifyUser(
            appointment.getUserId(),
            "APPOINTMENT",
            "预约支付成功",
            "预约 #" + appointment.getId() + " 已支付成功，等待技师确认预约。",
            "/appointments"
    );
    notificationService.notifyUser(
            technicianUserId(appointment.getTechnicianId()),
            "APPOINTMENT",
            "有新的预约待确认",
            "你有一条新的“" + serviceName + "”预约待确认，服务时间为 "
                    + appointment.getAppointmentTime() + " - " + endTime + "。",
            "/tech/appointments"
    );
}
```

#### 3.3 超时支付单关闭

源文件：`backend/src/main/java/com/bfsm/service/PaymentService.java`

```java
@Transactional
public int closeExpiredPayments() {
    List<PaymentOrder> expired = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
            .in(PaymentOrder::getStatus, PENDING_STATUSES)
            .isNotNull(PaymentOrder::getExpireTime)
            .le(PaymentOrder::getExpireTime, LocalDateTime.now())
            .orderByAsc(PaymentOrder::getExpireTime));
    int closed = 0;
    for (PaymentOrder paymentOrder : expired) {
        PaymentGatewayStatus remoteStatus = refreshRemoteStatus(paymentOrder);
        if (remoteStatus != null && "SUCCESS".equals(remoteStatus.getStatus())) {
            markPaymentSuccess(paymentOrder, remoteStatus.getPlatformTradeNo(), remoteStatus.getPaidTime(),
                    remoteStatus.getNotifyContent());
            continue;
        }
        try {
            gatewayFor(paymentOrder).close(paymentOrder);
        } catch (Exception ignored) {
            // Local expiry remains authoritative for stale unpaid BFSM orders.
        }
        if (closePaymentOrderIfPending(paymentOrder, "CLOSED", "Payment expired", "支付单已超时关闭")) {
            expireBusiness(paymentOrder);
            closed += 1;
        }
    }
    return closed;
}
```

---

### 4. 商品订单模块

#### 4.1 创建订单并扣减库存

源文件：`backend/src/main/java/com/bfsm/service/ProductOrderService.java`

```java
@Transactional
public ProductOrderDetailVo createOrder(ProductOrderCreateReq req) {
    SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
    UserAddress address = currentUserAddress(currentUser.getUserId(), req.getAddressId());
    List<OrderLine> lines = resolveOrderLines(currentUser.getUserId(), req);
    if (lines.isEmpty()) {
        throw new BizException(4100, "请先选择要结算的商品");
    }

    Map<String, String> lockTokens = new LinkedHashMap<>();
    Map<Long, Product> products = new LinkedHashMap<>();
    try {
        for (OrderLine line : lines) {
            Product product = productMapper.selectById(line.productId());
            if (product == null || !"ON".equals(product.getStatus())) {
                throw new BizException(404, "商品不存在或已下架");
            }
            // 附录注释：按商品加锁，防止并发下单导致库存超卖。
            String lockKey = "lock:product:stock:" + product.getId();
            String token = redisLockService.tryLock(lockKey, Duration.ofSeconds(8));
            if (token == null) {
                throw new BizException(4101, "商品库存正在处理中，请稍后重试");
            }
            lockTokens.put(lockKey, token);

            Product latest = productMapper.selectById(product.getId());
            if (latest == null || latest.getStock() == null || latest.getStock() < line.quantity()) {
                throw new BizException(4102, "商品库存不足");
            }
            latest.setStock(latest.getStock() - line.quantity());
            productMapper.updateById(latest);
            products.put(latest.getId(), latest);
        }

        ProductOrder order = new ProductOrder();
        order.setUserId(currentUser.getUserId());
        order.setStatus("CREATED");
        order.setPayStatus("UNPAID");
        order.setAfterSaleStatus("NONE");
        order.setItemCount(lines.stream().mapToInt(OrderLine::quantity).sum());
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setProvince(address.getProvince());
        order.setCity(address.getCity());
        order.setDistrict(address.getDistrict());
        order.setDetailAddress(address.getDetailAddress());
        order.setAddressTag(address.getTagName());

        BigDecimal total = lines.stream()
                .map(line -> products.get(line.productId()).getPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
        order.setPayAmount(total);
        productOrderMapper.insert(order);

        for (OrderLine line : lines) {
            ProductOrderItem item = new ProductOrderItem();
            item.setOrderId(order.getId());
            item.setProductId(line.productId());
            item.setQuantity(line.quantity());
            item.setPrice(products.get(line.productId()).getPrice());
            productOrderItemMapper.insert(item);
        }

        List<Long> cartIds = lineCartIds(lines);
        if (!cartIds.isEmpty()) {
            shoppingCartItemMapper.deleteByIds(cartIds);
        }

        notificationService.notifyUser(
                order.getUserId(),
                "ORDER",
                "订单已创建",
                "你的商品订单 #" + order.getId() + " 已创建，等待支付。",
                "/shop/orders/" + order.getId()
        );
        return detailForCurrentUser(order.getId());
    } finally {
        lockTokens.forEach((key, token) -> redisLockService.unlock(key, token));
    }
}
```

#### 4.2 订单支付、发货与售后

源文件：`backend/src/main/java/com/bfsm/service/ProductOrderService.java`

```java
@Transactional
public ProductOrderDetailVo payForCurrentUser(Long id, OrderPayReq req) {
    ProductOrder order = ownOrder(id);
    if (!"CREATED".equals(order.getStatus()) || !"UNPAID".equals(order.getPayStatus())) {
        throw new BizException(4106, "当前订单不可支付");
    }
    String payMethod = normalizePayMethod(req.getPayMethod());
    if ("BALANCE".equals(payMethod)) {
        walletService.consumeForOrder(order.getUserId(), order.getId(), order.getPayAmount(), "支付商品订单#" + order.getId());
    }
    order.setPayMethod(payMethod);
    order.setPayStatus("PAID");
    order.setStatus("PAID");
    order.setPayTime(LocalDateTime.now());
    order.setPayNo(buildPayNo(order.getId(), payMethod));
    productOrderMapper.updateById(order);
    if ("BALANCE".equals(payMethod)) {
        paymentService.recordBalanceProductOrderPayment(order);
    }

    notificationService.notifyUser(
            order.getUserId(),
            "ORDER",
            "订单支付成功",
            "订单 #" + order.getId() + " 已支付完成，等待商家发货。",
            "/shop/orders/" + order.getId()
    );
    notificationService.notifyAdmins(
            "ORDER",
            "有新订单待发货",
            "订单 #" + order.getId() + " 已支付，请及时处理发货。",
            "/admin/product-orders"
    );
    return detailForCurrentUser(order.getId());
}

@Transactional
public void shipOrder(Long id, ProductOrderShipReq req) {
    ProductOrder order = requireOrder(id);
    if (!"PAID".equals(order.getStatus())) {
        throw new BizException(4104, "当前订单状态不可发货");
    }
    order.setStatus("SHIPPED");
    order.setShippingCompany(req.getShippingCompany().trim());
    order.setTrackingNo(req.getTrackingNo().trim());
    order.setShippedTime(LocalDateTime.now());
    productOrderMapper.updateById(order);
    notificationService.notifyUser(
            order.getUserId(),
            "ORDER",
            "订单已发货",
            "订单 #" + order.getId() + " 已发货，请留意物流信息。",
            "/shop/orders/" + order.getId()
    );
}

@Transactional
public void applyAfterSale(Long id, AfterSaleApplyReq req) {
    ProductOrder order = ownOrder(id);
    if (!Set.of("PAID", "COMPLETED").contains(order.getStatus())) {
        throw new BizException(4108, "只有已支付未发货或已完成订单才能申请退款/售后");
    }
    if ("PAID".equals(order.getStatus()) && order.getShippedTime() != null) {
        throw new BizException(4108, "已发货订单暂不支持直接退款，请先走售后流程");
    }
    if (!"NONE".equals(afterSaleStatus(order))) {
        throw new BizException(4109, "该订单已提交退款或售后申请");
    }
    order.setAfterSaleStatus("REQUESTED");
    order.setAfterSaleReason(req.getReason().trim());
    order.setAfterSaleAppliedTime(LocalDateTime.now());
    productOrderMapper.updateById(order);
    notificationService.notifyAdmins(
            "ORDER",
            "有退款/售后申请待处理",
            "订单 #" + order.getId() + " 提交了退款或售后申请。",
            "/admin/product-orders"
    );
    notificationService.notifyUser(
            order.getUserId(),
            "ORDER",
            "退款/售后申请已提交",
            "订单 #" + order.getId() + " 的退款/售后申请已提交，等待管理员审核。",
            "/shop/orders/" + order.getId()
    );
}
```

---

### 5. Redis 分布式锁

源文件：`backend/src/main/java/com/bfsm/cache/RedisLockService.java`

```java
@Service
public class RedisLockService {
    private final StringRedisTemplate redisTemplate;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else return 0 end", Long.class);

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String tryLock(String key, Duration ttl) {
        // 附录注释：使用随机 token 标识锁持有者，避免误删其他请求持有的锁。
        String token = UUID.randomUUID().toString();
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    public void unlock(String key, String token) {
        if (token == null) return;
        // 附录注释：Lua 脚本保证“判断 token 与删除锁”两个动作的原子性。
        redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
    }
}
```

---

### 6. AI 咨询模块

#### 6.1 创建 AI 咨询会话

源文件：`backend/src/main/java/com/bfsm/service/AiConsultService.java`

```java
@Transactional
public AiConsultTurnVo createSession(AiConsultSessionReq req) {
    Long userId = SecurityUtils.currentUserOrThrow().getUserId();
    String content = req == null ? "" : trim(req.getContent());
    String imageUrl = req == null ? "" : normalizeOptionalImageUrl(req.getImageUrl());
    if (!StringUtils.hasText(content) && !StringUtils.hasText(imageUrl)) {
        throw new BizException(4509, "请输入咨询内容，或先上传图片后再发送。");
    }
    if (!StringUtils.hasText(content)) {
        content = "请结合这张图片，给我推荐适合的服务项目和护理商品。";
    }

    // 附录注释：加载门店服务和商品目录，使 AI 推荐能映射到真实可购买、可预约的项目。
    Catalog catalog = loadCatalog();
    TurnResult result = buildTurn(content, imageUrl, null, catalog);

    AiConsultRecord record = new AiConsultRecord();
    record.setUserId(userId);
    record.setTitle(buildSessionTitle(content));
    record.setStage(result.stage());
    record.setImageUrl(imageUrl);
    record.setInputText(content);
    record.setSummaryText(result.summaryText());
    record.setFaceSummary(result.observation());
    record.setHairstyleJson(writeJson(result.hairStyles()));
    record.setServiceJson(writeJson(result.services()));
    record.setProductJson(writeJson(result.products()));
    record.setCareTipsJson(writeJson(result.careTips()));
    record.setIntentTagsJson(writeJson(result.intentTags()));
    record.setPreferenceJson(writeJson(Map.of("hasImage", StringUtils.hasText(imageUrl), "initialRequest", content)));
    record.setDisclaimer(DEFAULT_DISCLAIMER);
    record.setAnalysisMode(result.analysisMode());
    record.setRawResponse(result.rawResponse());
    record.setLastMessageAt(LocalDateTime.now());
    record.setRecommendationSnapshotJson(writeJson(buildSnapshot(record, result)));
    aiConsultRecordMapper.insert(record);

    AiConsultMessage userMessage = createMessage(record, "USER", content, imageUrl);
    aiConsultMessageMapper.insert(userMessage);
    AiConsultMessage assistantMessage = createMessage(record, "ASSISTANT", result.assistantMessage(), "");
    aiConsultMessageMapper.insert(assistantMessage);

    AiConsultTurnVo turn = new AiConsultTurnVo();
    turn.setSession(sessionDetail(record.getId()));
    turn.setAssistantMessage(toMessageVo(assistantMessage));
    return turn;
}
```

#### 6.2 AI 分析与本地降级推荐

源文件：`backend/src/main/java/com/bfsm/service/AiConsultService.java`

```java
private TurnResult buildTurn(String question, String imageUrl, AiConsultRecord record, Catalog catalog) {
    Intent intent = detectIntent(question);
    if (intent.generalOnly()) {
        return buildGeneralTurn(question);
    }

    AiBeautyAnalysis remote = tryRemoteAnalysis(question, imageUrl, record, catalog);
    List<AiConsultServiceRecommendationVo> services = mergeServiceRecommendations(
            remote == null ? List.of() : remote.getServiceRecommendations(),
            catalog.servicesById()
    );
    List<AiConsultProductRecommendationVo> products = mergeProductRecommendations(
            remote == null ? List.of() : remote.getProductRecommendations(),
            catalog.productsById()
    );

    // 附录注释：远程 AI 无结果时，使用本地规则根据关键词匹配服务和商品。
    if (intent.serviceIntent() && services.isEmpty()) {
        services = recommendServices(question, catalog.services());
    }
    if (intent.productIntent() && products.isEmpty()) {
        products = recommendProducts(question, catalog.products());
    }
    if (!intent.serviceIntent()) {
        services = List.of();
    }
    if (!intent.productIntent()) {
        products = List.of();
    }

    List<AiConsultHairStyleVo> hairStyles = intent.hairIntent()
            ? mergeHairStyles(remote == null ? List.of() : remote.getHairStyles())
            : List.of();
    List<String> careTips = buildCareTips(question, services, products);
    String summary = chooseSummary(remote, question, intent, services, products);
    String observation = StringUtils.hasText(imageUrl)
            ? "已结合本次上传图片作为辅助上下文，实际判断仍建议到店后由技师确认。"
            : "本轮主要依据你的文字诉求和门店当前服务、商品目录生成建议。";
    String assistant = chooseAssistantMessage(remote, question, summary, services, products, intent);
    List<String> tags = buildIntentTags(intent);
    String stage = detectStage(intent);
    String mode = remote == null ? "LOCAL_RULE" : "REMOTE_API";
    String raw = remote == null ? "" : defaultString(remote.getRawResponse());
    return new TurnResult(stage, summary, observation, assistant, hairStyles, services, products, careTips, tags, mode, raw);
}

private AiBeautyAnalysis tryRemoteAnalysis(String question, String imageUrl, AiConsultRecord record, Catalog catalog) {
    if (remoteClient == null || aiConsultProperties == null || !aiConsultProperties.isEnabled()) {
        return null;
    }
    try {
        AiConsultContext context = new AiConsultContext();
        context.setInputText(buildRemoteInput(question, record));
        context.setServiceCatalog(catalog.services());
        context.setProductCatalog(catalog.products());
        if (StringUtils.hasText(imageUrl)) {
            LoadedImage loadedImage = loadImage(imageUrl);
            context.setImageBytes(loadedImage.bytes());
            context.setImageMimeType(loadedImage.mimeType());
        }
        return remoteClient.analyze(context);
    } catch (Exception ex) {
        if (aiConsultProperties.isAllowDegradedResponse() || aiConsultProperties.isFallbackToLocal()) {
            return null;
        }
        if (ex instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new BizException(4502, "AI analysis request failed: " + ex.getMessage());
    }
}
```

#### 6.3 加载服务与商品目录

源文件：`backend/src/main/java/com/bfsm/service/AiConsultService.java`

```java
private List<AiServiceCatalogItem> loadActiveServiceCatalog() {
    Map<Long, String> categoryNames = serviceCategoryMapper.selectList(new LambdaQueryWrapper<ServiceCategory>()
                    .orderByAsc(ServiceCategory::getSortNo)
                    .orderByAsc(ServiceCategory::getId))
            .stream()
            .collect(Collectors.toMap(ServiceCategory::getId, ServiceCategory::getName, (a, b) -> a));
    return serviceItemMapper.selectList(new LambdaQueryWrapper<ServiceItem>()
                    .eq(ServiceItem::getStatus, "ON")
                    .orderByAsc(ServiceItem::getId))
            .stream()
            .map(item -> {
                AiServiceCatalogItem catalogItem = new AiServiceCatalogItem();
                catalogItem.setServiceItemId(item.getId());
                catalogItem.setServiceName(item.getName());
                catalogItem.setCategoryName(categoryNames.getOrDefault(item.getCategoryId(), "未分类"));
                catalogItem.setDurationMin(item.getDurationMin());
                catalogItem.setPrice(item.getPrice());
                return catalogItem;
            })
            .toList();
}

private List<AiProductCatalogItem> loadActiveProductCatalog() {
    Map<Long, String> categoryNames = productCategoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>()
                    .orderByAsc(ProductCategory::getId))
            .stream()
            .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));
    return productMapper.selectList(new LambdaQueryWrapper<Product>()
                    .eq(Product::getStatus, "ON")
                    .gt(Product::getStock, 0)
                    .orderByAsc(Product::getId))
            .stream()
            .map(product -> {
                AiProductCatalogItem item = new AiProductCatalogItem();
                item.setProductId(product.getId());
                item.setProductName(product.getName());
                item.setCategoryName(categoryNames.getOrDefault(product.getCategoryId(), "未分类"));
                item.setPrice(product.getPrice());
                item.setStock(product.getStock());
                return item;
            })
            .toList();
}
```

---

### 7. 前端请求与路由控制

#### 7.1 Axios 请求封装

源文件：`frontend/src/utils/request.ts`

```ts
import axios from 'axios';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../store/auth';

export const API_BASE_URL = 'http://localhost:8080';

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
});

request.interceptors.request.use((config) => {
  const store = useAuthStore();
  if (store.token) {
    // 附录注释：前端统一在请求头中携带 JWT。
    config.headers.Authorization = `Bearer ${store.token}`;
  }
  return config;
});

request.interceptors.response.use(
  (response) => {
    const store = useAuthStore();
    const payload = response.data;
    if (payload && typeof payload.code === 'number' && payload.code !== 0) {
      if (payload.code === 401) {
        store.logout();
      }
      ElMessage.error(payload.message || '请求失败');
      return Promise.reject(new Error(payload.message || '请求失败'));
    }
    return response;
  },
  (error) => {
    const store = useAuthStore();
    const message = error?.response?.data?.message || error?.message || '网络异常';
    if (error?.response?.status === 401) {
      store.logout();
    }
    ElMessage.error(message);
    return Promise.reject(error);
  },
);

export default request;
```

#### 7.2 前端角色路由守卫

源文件：`frontend/src/router/index.ts`

```ts
router.beforeEach(async (to) => {
  const auth = useAuthStore();

  if (auth.token && !auth.user) {
    try {
      // 附录注释：刷新页面后若只有 token，则先请求当前用户信息。
      await auth.loadMe();
    } catch {
      auth.logout();
    }
  }

  if (to.meta.guestOnly && auth.token) {
    return auth.defaultRoute;
  }

  if (to.path !== '/login' && to.path !== '/register' && !auth.token) {
    return '/login';
  }

  const roles = to.meta.roles as string[] | undefined;
  if (roles && auth.user && !roles.includes(auth.user.roleCode)) {
    return auth.defaultRoute;
  }

  return true;
});
```

