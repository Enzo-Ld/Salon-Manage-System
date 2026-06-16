# 第6章 核心测试用例表

表6-1 管理员账号登录测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 1 |
| 用例名称 | 管理员账号正常登录测试 |
| 测试目的 | 验证数据库中管理员账号能否通过登录接口获取 JWT 并进入后台 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 输入：username=`admin`，password=`123456`；数据库：`sys_user.id=1`，角色为 `ADMIN`，`enabled=1` |
| 预期结果 | 登录成功，返回 token 和过期时间，当前用户角色为 `ADMIN` |
| 实际结果 | 实际结果：登录成功；已依据 `AuthService.login` 与 `bfsm.sql` 默认账号数据校检 |

表6-2 普通用户错误密码登录测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 2 |
| 用例名称 | 普通用户输入错误密码登录测试 |
| 测试目的 | 验证登录接口能否拦截错误密码 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 输入：username=`q`，password=`1234567`；数据库：`sys_user.id=5`，用户昵称 `user_lily`，默认正确密码为 `123456` |
| 预期结果 | 登录失败，返回业务提示 `Username or password is incorrect` |
| 实际结果 | 实际结果：登录失败并提示账号或密码错误；已依据 `AuthService.login` 校检 |

表6-3 重复用户名注册测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 3 |
| 用例名称 | 使用已存在用户名注册测试 |
| 测试目的 | 验证注册时用户名唯一性校验是否生效 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 输入：username=`admin`，password=`123456`，phone=`13900000001`，roleCode=`USER`；数据库已有 `sys_user.username=admin` |
| 预期结果 | 注册失败，返回 `Username already exists` |
| 实际结果 | 实际结果：注册失败；已依据 `AuthService.register` 中用户名查询逻辑校检 |

表6-4 重复手机号注册测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 4 |
| 用例名称 | 使用已绑定手机号注册测试 |
| 测试目的 | 验证手机号唯一约束与业务校验是否生效 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 输入：username=`new_user_phone_dup`，password=`123456`，phone=`13800000004`，roleCode=`USER`；数据库中 `13800000004` 已绑定用户 `e` |
| 预期结果 | 注册失败，返回 `Phone number is already bound to another account` |
| 实际结果 | 实际结果：注册失败；已依据 `AuthService.register` 和 `sys_user.uk_sys_user_phone` 校检 |

表6-5 公开注册技师账号测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 5 |
| 用例名称 | 公开注册非普通用户角色测试 |
| 测试目的 | 验证公开注册入口是否只允许创建普通用户 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 输入：username=`new_tech_public`，password=`123456`，phone=`13900000002`，roleCode=`TECH` |
| 预期结果 | 注册失败，返回 `Public registration only supports USER accounts` |
| 实际结果 | 实际结果：注册失败；已依据 `AuthService.register` 中 `roleCode` 校验校检 |

表6-6 非法手机号注册测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 6 |
| 用例名称 | 输入非大陆 11 位手机号注册测试 |
| 测试目的 | 验证前后端手机号格式校验是否一致 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 输入：username=`bad_phone_user`，password=`123456`，phone=`12345`，roleCode=`USER` |
| 预期结果 | 前端提示 `手机号需为 11 位大陆手机号`，后端校验为 `Phone number must be 11 digits and start with 1` |
| 实际结果 | 实际结果：非法手机号被拦截；已依据 `RegisterView.vue` 与 `PhoneUtils.validateOrThrow` 校检 |

表6-7 修改资料绑定他人手机号测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 7 |
| 用例名称 | 修改个人资料时绑定已存在手机号测试 |
| 测试目的 | 验证用户资料修改时不能占用其他账号手机号 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 当前登录用户：`q`；输入 phone=`13800000004`；数据库中该手机号属于用户 `e` |
| 预期结果 | 修改失败，返回 `Phone number is already bound to another account` |
| 实际结果 | 实际结果：修改失败；已依据 `AuthService.updateProfile` 校检 |

表6-8 服务分类与服务列表查询测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 8 |
| 用例名称 | 查询已上架服务项目测试 |
| 测试目的 | 验证用户端服务列表能否展示数据库中的上架服务 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 查询服务分类 `面部护理`；数据库服务包含 `补水清洁护理`，时长 60 分钟，价格 199.00，状态 `ON` |
| 预期结果 | 页面展示 `补水清洁护理` 及其价格、时长、分类信息 |
| 实际结果 | 实际结果：服务数据与数据库一致；已依据 `service_item`、`service_category` 数据校检 |

表6-9 服务关键字搜索测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 9 |
| 用例名称 | 使用关键字筛选服务测试 |
| 测试目的 | 验证服务搜索能否按真实服务名称匹配 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 输入关键字：`肩颈`；数据库服务包含 `肩颈放松理疗`、`深睡肩颈舒压` |
| 预期结果 | 服务列表返回名称或简介包含 `肩颈` 的服务项目 |
| 实际结果 | 实际结果：可匹配真实服务项目；已依据 `service_item` 数据校检 |

表6-10 服务收藏状态测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 10 |
| 用例名称 | 查询用户已收藏服务测试 |
| 测试目的 | 验证用户收藏状态是否从数据库读取 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 当前用户：`q`，`user_id=5`；数据库：`favorite` 中存在 `service_item_id=1` |
| 预期结果 | 服务 `补水清洁护理` 在页面显示为已收藏 |
| 实际结果 | 实际结果：收藏状态与 `favorite` 表一致；已依据数据库记录校检 |

表6-11 预约过去时间测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 11 |
| 用例名称 | 创建早于当前时间的预约测试 |
| 测试目的 | 验证预约接口能否拒绝过期时间 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 当前日期：2026-05-17；输入：serviceItemId=1，appointmentTime=`2026-04-15 10:00:00` |
| 预期结果 | 创建预约失败，返回 `预约时间不能早于当前时间` |
| 实际结果 | 实际结果：预约失败；已依据 `AppointmentService.create` 校检 |

表6-12 自动推荐技师测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 12 |
| 用例名称 | 不指定技师时自动推荐测试 |
| 测试目的 | 验证系统按排班、请假、冲突、评分和日负载推荐技师 |
| 测试方法 | 白盒测试与黑盒测试结合 |
| 测试数据 | 输入：serviceItemId=3，appointmentTime=`2026-04-15 10:00:00`；候选技师 Amy 评分 4.9、日负载 2 单，Bob 评分 4.6、日负载 0 单 |
| 预期结果 | 推荐 Bob，因为推荐分 `60 + 评分*8 - 日负载*10` 更高 |
| 实际结果 | 实际结果：推荐 `technicianId=2`、`technicianName=Bob`；已由 `SchedulingServiceTest.recommendTechnician_shouldPickBestAvailableCandidate` 校检 |

表6-13 指定技师不支持服务测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 13 |
| 用例名称 | 指定不具备服务能力的技师预约测试 |
| 测试目的 | 验证预约时技师服务能力绑定是否生效 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 输入：technicianId=1（Amy），serviceItemId=20（深睡肩颈舒压）；数据库 `technician_service` 中 serviceItemId=20 仅绑定 Bob、Jason |
| 预期结果 | 预约失败，返回 `Selected technician does not support this service item` |
| 实际结果 | 实际结果：预约失败；已依据 `ScheduleConflictChecker.validateTechnicianForAppointment` 与 `technician_service` 数据校检 |

表6-14 技师非工作时间预约测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 14 |
| 用例名称 | 指定技师排班外时间预约测试 |
| 测试目的 | 验证预约时间必须完整落在技师排班内 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 输入：technicianId=1，serviceItemId=1，appointmentTime=`2026-04-15 18:30:00`；数据库排班：Amy 在 2026-04-15 09:00-18:00 工作 |
| 预期结果 | 预约失败，返回 `Selected technician is not working in this time range` |
| 实际结果 | 实际结果：预约失败；已依据 `ScheduleConflictChecker.isWorkingDuring` 与 `schedule` 数据校检 |

表6-15 预约时段冲突测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 15 |
| 用例名称 | 重复预约同一技师同一时段测试 |
| 测试目的 | 验证有效预约之间不能产生时间重叠 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 输入：technicianId=1，serviceItemId=1，appointmentTime=`2026-04-16 09:30:00`；数据库已有预约 #11：Amy 2026-04-16 09:00-10:00，状态 `COMPLETED`，支付状态 `PAID` |
| 预期结果 | 支付或确认前冲突复检失败，提示该时段已被其他用户预约 |
| 实际结果 | 实际结果：冲突会被拦截；已依据 `AppointmentService.payForCurrentUser` 与预约冲突查询校检 |

表6-16 预约非法状态流转测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 16 |
| 用例名称 | 预约从待确认直接改为已完成测试 |
| 测试目的 | 验证预约状态机不能跳过中间状态 |
| 测试方法 | 白盒测试 |
| 测试数据 | 输入：appointmentId=9，当前 status=`PENDING`，payStatus=`PAID`，目标 status=`COMPLETED` |
| 预期结果 | 状态流转失败，返回业务码 4019，提示 `预约状态流转不合法` |
| 实际结果 | 实际结果：流转失败；已由 `AppointmentServiceTest.moveStatus_shouldRejectIllegalTransition` 校检 |

表6-17 非本人取消预约测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 17 |
| 用例名称 | 普通用户取消他人预约测试 |
| 测试目的 | 验证普通用户只能操作自己的预约 |
| 测试方法 | 白盒测试 |
| 测试数据 | 当前登录用户：`userId=10`；预约：appointmentId=7，所属用户 `userId=11` |
| 预期结果 | 取消失败，返回 403，提示 `只能取消自己的预约` |
| 实际结果 | 实际结果：取消失败；已由 `AppointmentServiceTest.cancel_shouldRejectNonOwnerUser` 校检 |

表6-18 未完成预约评价测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 18 |
| 用例名称 | 对服务中的预约提交评价测试 |
| 测试目的 | 验证只有已完成预约才允许评价 |
| 测试方法 | 白盒测试 |
| 测试数据 | 当前登录用户：`e`；预约：appointmentId=5，status=`IN_SERVICE`；输入 rating=5，content=`服务很好` |
| 预期结果 | 评价失败，返回 4027，提示 `仅已完成预约允许评价` |
| 实际结果 | 实际结果：评价失败；已由 `AppointmentServiceTest.submitComment_shouldRejectWhenAppointmentNotCompleted` 校检 |

表6-19 已评价预约重复评价测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 19 |
| 用例名称 | 对已评价预约再次提交评价测试 |
| 测试目的 | 验证同一预约不能重复评价 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 当前用户：`q`；数据库已有 `comment.appointment_id=13`，内容 `q`，预约 #13 状态 `COMPLETED` |
| 预期结果 | 重复评价失败，返回 `该预约已评价` |
| 实际结果 | 实际结果：重复评价被拦截；已依据 `AppointmentService.submitComment` 与 `comment` 表校检 |

表6-20 技师请假提交测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 20 |
| 用例名称 | 技师提交请假申请测试 |
| 测试目的 | 验证技师端新建请假记录时状态默认为待审批 |
| 测试方法 | 白盒测试 |
| 测试数据 | 当前登录技师：`tech_amy`；输入：startTime=`2026-04-16 09:00:00`，endTime=`2026-04-16 18:00:00`，reason=`Personal leave` |
| 预期结果 | 新增请假记录，status=`PENDING`，createdByUserId 为当前技师用户 |
| 实际结果 | 实际结果：生成待审批请假；已由 `LeaveRecordServiceTest.save_shouldCreatePendingLeaveRequestForTechnician` 校检 |

表6-21 请假结束时间早于开始时间测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 21 |
| 用例名称 | 请假时间区间非法测试 |
| 测试目的 | 验证请假结束时间必须晚于开始时间 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 输入：startTime=`2026-04-16 18:00:00`，endTime=`2026-04-16 09:00:00`，reason=`调休` |
| 预期结果 | 提交失败，返回 `Leave end time must be later than start time` |
| 实际结果 | 实际结果：请假失败；已依据 `LeaveRecordService.validateLeaveRecord` 校检 |

表6-22 管理员驳回请假未填备注测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 22 |
| 用例名称 | 请假驳回备注为空测试 |
| 测试目的 | 验证管理员驳回请假必须填写审核说明 |
| 测试方法 | 白盒测试 |
| 测试数据 | 当前登录管理员：`admin`；请假记录 id=12，status=`PENDING`；输入 remark=` ` |
| 预期结果 | 驳回失败，返回 4320，提示 `Review remark is required` |
| 实际结果 | 实际结果：驳回失败；已由 `LeaveRecordServiceTest.reject_shouldRequireRemark` 校检 |

表6-23 请假审批与已有预约冲突测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 23 |
| 用例名称 | 审批包含有效预约的请假测试 |
| 测试目的 | 验证请假审批会检查技师同时间段内的有效预约 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 技师：Amy；请假时间覆盖 `2026-04-16 09:00:00` 至 `2026-04-16 11:00:00`；数据库已有预约 #11、#13 在该时间段附近且状态有效 |
| 预期结果 | 审批失败，返回 `There are active appointments in the requested leave period` |
| 实际结果 | 实际结果：审批冲突被拦截；已依据 `LeaveRecordService.ensureNoAppointmentConflict` 与预约数据校检 |

表6-24 技师请假期间签到测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 24 |
| 用例名称 | 技师处于已批准请假期间签到测试 |
| 测试目的 | 验证考勤签到会检查当前请假状态 |
| 测试方法 | 白盒测试 |
| 测试数据 | 当前登录技师：`tech_amy`；当天有排班；`leaveRecordService.isOnLeaveAt` 返回 true |
| 预期结果 | 签到失败，返回 4402，提示 `Cannot check in while on approved leave` |
| 实际结果 | 实际结果：签到失败；已由 `AttendanceServiceTest.checkIn_shouldRejectWhenTechnicianIsOnLeave` 校检 |

表6-25 技师签退测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 25 |
| 用例名称 | 已签到技师正常签退测试 |
| 测试目的 | 验证已签到考勤记录可以完成签退 |
| 测试方法 | 白盒测试 |
| 测试数据 | 当前登录技师：`tech_amy`；考勤记录 status=`ON_DUTY`，checkInTime 为当前时间前 3 小时 |
| 预期结果 | 写入 checkOutTime，考勤状态变为 `OFF_DUTY` |
| 实际结果 | 实际结果：签退成功；已由 `AttendanceServiceTest.checkOut_shouldCompleteOpenAttendance` 校检 |

表6-26 加入购物车超过库存测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 26 |
| 用例名称 | 商品加入购物车数量超过库存测试 |
| 测试目的 | 验证购物车数量不能超过商品库存 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 输入：productId=2（舒缓修护精华），quantity=72；数据库库存 stock=71 |
| 预期结果 | 加入失败，返回 `加入购物车的数量超过当前库存` |
| 实际结果 | 实际结果：加入失败；已依据 `ShoppingCartService.addCurrentUserCart` 与 `product` 数据校检 |

表6-27 查询购物车金额测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 27 |
| 用例名称 | 查询当前用户购物车小计金额测试 |
| 测试目的 | 验证购物车金额按商品单价和数量计算 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 当前用户：`q`；数据库购物车 `id=15`，productId=2，quantity=2；商品 `舒缓修护精华` 单价 199.00 |
| 预期结果 | 返回购物车商品金额 398.00，选中状态为 true |
| 实际结果 | 实际结果：金额按 `199.00*2` 计算；已依据 `ShoppingCartService.toVo` 与数据库数据校检 |

表6-28 提交空商品订单测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 28 |
| 用例名称 | 未选择商品提交订单测试 |
| 测试目的 | 验证订单创建时必须包含结算商品 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 当前用户：`q`；输入：addressId=2，cartItemIds=[]，items=[] |
| 预期结果 | 创建失败，返回 `请先选择要结算的商品` |
| 实际结果 | 实际结果：创建失败；已依据 `ProductOrderService.createOrder` 校检 |

表6-29 商品库存不足下单测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 29 |
| 用例名称 | 下单数量大于库存测试 |
| 测试目的 | 验证创建订单时库存不足不会生成订单 |
| 测试方法 | 白盒测试 |
| 测试数据 | 当前用户：`user_tom`；商品库存=1；输入购买数量=2，addressId=11 |
| 预期结果 | 下单失败，返回 4102，提示 `商品库存不足` |
| 实际结果 | 实际结果：下单失败；已由 `ProductOrderServiceTest.createOrder_shouldRejectWhenStockInsufficient` 校检 |

表6-30 非本人取消商品订单测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 30 |
| 用例名称 | 普通用户取消他人商品订单测试 |
| 测试目的 | 验证用户只能操作自己的商品订单 |
| 测试方法 | 白盒测试 |
| 测试数据 | 当前登录用户：`e`，userId=4；订单：id=6，userId=9，status=`CREATED` |
| 预期结果 | 取消失败，返回 403，提示 `只能操作自己的订单` |
| 实际结果 | 实际结果：取消失败；已由 `ProductOrderServiceTest.cancelForCurrentUser_shouldRejectOtherUsersOrder` 校检 |

表6-31 商品订单非法状态流转测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 31 |
| 用例名称 | 商品订单从待支付直接改为已完成测试 |
| 测试目的 | 验证商品订单状态流转必须按 CREATED、PAID、SHIPPED、COMPLETED 顺序 |
| 测试方法 | 白盒测试 |
| 测试数据 | 订单：id=8，status=`CREATED`；目标状态：`COMPLETED` |
| 预期结果 | 流转失败，返回 4104，提示 `非法的订单状态流转` |
| 实际结果 | 实际结果：流转失败；已由 `ProductOrderServiceTest.updateStatus_shouldRejectIllegalFlow` 校检 |

表6-32 已支付订单申请售后测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 32 |
| 用例名称 | 用户对已支付未发货订单申请售后测试 |
| 测试目的 | 验证符合条件的商品订单可以提交退款或售后申请 |
| 测试方法 | 白盒测试 |
| 测试数据 | 当前用户：`q`；订单：id=18，userId=5，status=`PAID`，afterSaleStatus=`NONE`；输入 reason=`不想要了，申请退款` |
| 预期结果 | afterSaleStatus 变为 `REQUESTED`，记录售后原因，并通知管理员 |
| 实际结果 | 实际结果：售后申请成功；已由 `ProductOrderServiceTest.applyAfterSale_shouldAllowPaidOrderRefundRequest` 校检 |

表6-33 售后审核通过恢复库存测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 33 |
| 用例名称 | 管理员同意售后并恢复库存测试 |
| 测试目的 | 验证售后通过后订单退款状态和库存恢复逻辑 |
| 测试方法 | 白盒测试 |
| 测试数据 | 订单：id=20，status=`PAID`，payStatus=`PAID`，afterSaleStatus=`REQUESTED`；订单明细：productId=2，quantity=3；商品原库存=5 |
| 预期结果 | 商品库存恢复为 8，订单 status/payStatus/afterSaleStatus 均变为退款相关状态 |
| 实际结果 | 实际结果：库存恢复、订单退款；已由 `ProductOrderServiceTest.reviewAfterSale_shouldRestoreStockWhenApproved` 校检 |

表6-34 钱包充值活动支付测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 34 |
| 用例名称 | 钱包充值享受充值活动测试 |
| 测试目的 | 验证钱包充值成功后余额、累计充值、累计赠送和流水是否正确 |
| 测试方法 | 白盒测试 |
| 测试数据 | 当前用户：`q`；充值单 `R202604150001`；活动：`充100送10`；原余额 200.00，累计充值 300.00，累计赠送 20.00 |
| 预期结果 | 余额变为 310.00，累计充值 400.00，累计赠送 30.00，并生成 `RECHARGE` 与 `GIFT` 流水 |
| 实际结果 | 实际结果：钱包金额与流水正确；已由 `PaymentServiceTest.completePayment_shouldApplyWalletRechargeOnceAndNotifyUser` 校检 |

表6-35 AI 顾问空内容咨询测试用例

| 项目 | 内容 |
| --- | --- |
| 用例编号 | 35 |
| 用例名称 | AI 顾问空内容且未上传图片发送测试 |
| 测试目的 | 验证 AI 顾问会拦截无文本、无图片的无效咨询 |
| 测试方法 | 黑盒测试 |
| 测试数据 | 当前用户已登录；输入：content=` `，imageUrl=` ` |
| 预期结果 | 发送失败，返回 `请输入咨询内容，或先上传图片后再发送。` |
| 实际结果 | 实际结果：发送失败；已依据 `AiConsultService.createSession/sendSessionMessage` 校检 |

