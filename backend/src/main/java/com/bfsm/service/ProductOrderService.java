package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bfsm.cache.RedisLockService;
import com.bfsm.common.BizException;
import com.bfsm.common.PageUtils;
import com.bfsm.common.SecurityUtils;
import com.bfsm.dto.AfterSaleApplyReq;
import com.bfsm.dto.AfterSaleReviewReq;
import com.bfsm.dto.OrderPayReq;
import com.bfsm.dto.ProductOrderCreateReq;
import com.bfsm.dto.ProductOrderShipReq;
import com.bfsm.dto.ProductOrderSubmitItemReq;
import com.bfsm.entity.CommentEntity;
import com.bfsm.entity.PaymentOrder;
import com.bfsm.entity.PaymentRefund;
import com.bfsm.entity.Product;
import com.bfsm.entity.ProductCategory;
import com.bfsm.entity.ProductOrder;
import com.bfsm.entity.ProductOrderItem;
import com.bfsm.entity.ShoppingCartItem;
import com.bfsm.entity.SysUser;
import com.bfsm.entity.UserAddress;
import com.bfsm.mapper.CommentMapper;
import com.bfsm.mapper.PaymentOrderMapper;
import com.bfsm.mapper.PaymentRefundMapper;
import com.bfsm.mapper.ProductCategoryMapper;
import com.bfsm.mapper.ProductMapper;
import com.bfsm.mapper.ProductOrderItemMapper;
import com.bfsm.mapper.ProductOrderMapper;
import com.bfsm.mapper.ShoppingCartItemMapper;
import com.bfsm.mapper.SysUserMapper;
import com.bfsm.mapper.UserAddressMapper;
import com.bfsm.security.SecurityUser;
import com.bfsm.vo.ProductOrderDetailVo;
import com.bfsm.vo.ProductOrderItemVo;
import com.bfsm.vo.PageResultVo;
import com.bfsm.vo.ProductOrderSummaryVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductOrderService {
    private static final Map<String, Set<String>> FLOW = Map.of(
            "CREATED", Set.of("PAID", "CANCELLED"),
            "PAID", Set.of("SHIPPED"),
            "SHIPPED", Set.of("COMPLETED")
    );
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "CREATED", "待支付",
            "PAID", "待发货",
            "SHIPPED", "待收货",
            "COMPLETED", "已完成",
            "CANCELLED", "已取消",
            "REFUNDED", "已退款"
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

    private final ProductMapper productMapper;
    private final ProductOrderMapper productOrderMapper;
    private final ProductOrderItemMapper productOrderItemMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final SysUserMapper sysUserMapper;
    private final RedisLockService redisLockService;
    private final CommentMapper commentMapper;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final ShoppingCartItemMapper shoppingCartItemMapper;
    private final UserAddressMapper userAddressMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentRefundMapper paymentRefundMapper;
    private final PaymentService paymentService;

    public ProductOrderService(ProductMapper productMapper, ProductOrderMapper productOrderMapper, ProductOrderItemMapper productOrderItemMapper,
                               ProductCategoryMapper productCategoryMapper, SysUserMapper sysUserMapper, RedisLockService redisLockService,
                               CommentMapper commentMapper, WalletService walletService, NotificationService notificationService,
                               ShoppingCartItemMapper shoppingCartItemMapper, UserAddressMapper userAddressMapper,
                               PaymentOrderMapper paymentOrderMapper, PaymentRefundMapper paymentRefundMapper,
                               PaymentService paymentService) {
        this.productMapper = productMapper;
        this.productOrderMapper = productOrderMapper;
        this.productOrderItemMapper = productOrderItemMapper;
        this.productCategoryMapper = productCategoryMapper;
        this.sysUserMapper = sysUserMapper;
        this.redisLockService = redisLockService;
        this.commentMapper = commentMapper;
        this.walletService = walletService;
        this.notificationService = notificationService;
        this.shoppingCartItemMapper = shoppingCartItemMapper;
        this.userAddressMapper = userAddressMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.paymentRefundMapper = paymentRefundMapper;
        this.paymentService = paymentService;
    }

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

    public List<ProductOrderSummaryVo> listCurrentUserOrders() {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        return buildSummaryList(productOrderMapper.selectList(new LambdaQueryWrapper<ProductOrder>()
                .eq(ProductOrder::getUserId, userId)
                .orderByDesc(ProductOrder::getCreatedAt)));
    }

    public PageResultVo<ProductOrderSummaryVo> pageCurrentUserOrders(Long pageNum, Long pageSize) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        Page<ProductOrder> page = new Page<>(
                PageUtils.normalizePageNum(pageNum),
                PageUtils.normalizePageSize(pageSize)
        );
        Page<ProductOrder> result = productOrderMapper.selectPage(page, new LambdaQueryWrapper<ProductOrder>()
                .eq(ProductOrder::getUserId, userId)
                .orderByDesc(ProductOrder::getCreatedAt));
        return new PageResultVo<>(
                buildSummaryList(result.getRecords()),
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                result.getPages()
        );
    }

    public ProductOrderDetailVo detailForCurrentUser(Long id) {
        ProductOrder order = productOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if (!Objects.equals(order.getUserId(), currentUser.getUserId()) && !"ADMIN".equals(currentUser.getRoleCode())) {
            throw new BizException(403, "无权查看该订单");
        }
        return toDetail(order);
    }

    @Transactional
    public void cancelForCurrentUser(Long id) {
        ProductOrder order = ownOrder(id);
        if (!"CREATED".equals(order.getStatus())) {
            throw new BizException(4103, "当前订单状态不可取消");
        }
        restoreStock(order.getId());
        order.setStatus("CANCELLED");
        order.setPayStatus("CANCELLED");
        order.setCloseTime(LocalDateTime.now());
        productOrderMapper.updateById(order);
        notificationService.notifyUser(
                order.getUserId(),
                "ORDER",
                "订单已取消",
                "你的订单 #" + order.getId() + " 已取消。",
                "/shop/orders/" + order.getId()
        );
    }

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
    public ProductOrderDetailVo confirmReceiptForCurrentUser(Long id) {
        ProductOrder order = ownOrder(id);
        if (!"SHIPPED".equals(order.getStatus())) {
            throw new BizException(4107, "只有已发货订单才能确认收货");
        }
        order.setStatus("COMPLETED");
        productOrderMapper.updateById(order);
        notificationService.notifyUser(
                order.getUserId(),
                "ORDER",
                "订单已完成",
                "订单 #" + order.getId() + " 已确认收货。",
                "/shop/orders/" + order.getId()
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

    @Transactional
    public void reviewAfterSale(Long id, AfterSaleReviewReq req) {
        ProductOrder order = requireOrder(id);
        if (!"REQUESTED".equals(afterSaleStatus(order))) {
            throw new BizException(4110, "当前订单没有待处理的售后申请");
        }
        order.setAfterSaleHandledTime(LocalDateTime.now());
        order.setAfterSaleRemark(req.getRemark());
        if (Boolean.TRUE.equals(req.getApproved())) {
            restoreStock(order.getId());
            order.setAfterSaleStatus("REFUNDED");
            order.setStatus("REFUNDED");
            order.setPayStatus("REFUNDED");
            order.setCloseTime(LocalDateTime.now());
            PaymentRefund refund = paymentService.recordProductOrderRefund(order, req.getRemark());
            String refundMessage = refund != null && "MANUAL_REQUIRED".equals(refund.getStatus())
                    ? "订单 #" + order.getId() + " 的售后申请已通过，退款需管理员人工处理。"
                    : "订单 #" + order.getId() + " 的售后申请已处理完成，退款已按原支付方式处理。";
            notificationService.notifyUser(
                    order.getUserId(),
                    "ORDER",
                    "售后已退款",
                    refundMessage,
                    "/shop/orders/" + order.getId()
            );
        } else {
            order.setAfterSaleStatus("REJECTED");
            notificationService.notifyUser(
                    order.getUserId(),
                    "ORDER",
                    "售后申请已驳回",
                    "订单 #" + order.getId() + " 的售后申请未通过，请查看处理备注。",
                    "/shop/orders/" + order.getId()
            );
        }
        productOrderMapper.updateById(order);
    }

    public List<ProductOrderSummaryVo> listAllOrders() {
        return buildSummaryList(productOrderMapper.selectList(new LambdaQueryWrapper<ProductOrder>()
                .orderByDesc(ProductOrder::getCreatedAt)));
    }

    public PageResultVo<ProductOrderSummaryVo> pageAllOrders(Long pageNum, Long pageSize) {
        Page<ProductOrder> page = new Page<>(
                PageUtils.normalizePageNum(pageNum),
                PageUtils.normalizePageSize(pageSize)
        );
        Page<ProductOrder> result = productOrderMapper.selectPage(page, new LambdaQueryWrapper<ProductOrder>()
                .orderByDesc(ProductOrder::getCreatedAt));
        return new PageResultVo<>(
                buildSummaryList(result.getRecords()),
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                result.getPages()
        );
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        ProductOrder order = requireOrder(id);
        if (!FLOW.getOrDefault(order.getStatus(), Set.of()).contains(status)) {
            throw new BizException(4104, "非法的订单状态流转");
        }
        if ("SHIPPED".equals(status)) {
            throw new BizException(4111, "请通过发货接口提交物流信息");
        }
        order.setStatus(status);
        productOrderMapper.updateById(order);
        notificationService.notifyUser(
                order.getUserId(),
                "ORDER",
                "订单状态已更新",
                "订单 #" + order.getId() + " 当前状态为" + label(STATUS_LABELS, status, status) + "。",
                "/shop/orders/" + order.getId()
        );
    }

    private ProductOrder requireOrder(Long id) {
        ProductOrder order = productOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }
        return order;
    }

    private ProductOrder ownOrder(Long id) {
        ProductOrder order = requireOrder(id);
        if (!Objects.equals(order.getUserId(), SecurityUtils.currentUserOrThrow().getUserId())) {
            throw new BizException(403, "只能操作自己的订单");
        }
        return order;
    }

    private UserAddress currentUserAddress(Long userId, Long addressId) {
        UserAddress address = userAddressMapper.selectById(addressId);
        if (address == null || !Objects.equals(address.getUserId(), userId)) {
            throw new BizException(4402, "收货地址不存在");
        }
        return address;
    }

    private List<OrderLine> resolveOrderLines(Long userId, ProductOrderCreateReq req) {
        List<OrderLine> raw = new ArrayList<>();
        if (req.getCartItemIds() != null && !req.getCartItemIds().isEmpty()) {
            List<ShoppingCartItem> cartItems = shoppingCartItemMapper.selectList(new LambdaQueryWrapper<ShoppingCartItem>()
                    .eq(ShoppingCartItem::getUserId, userId)
                    .in(ShoppingCartItem::getId, req.getCartItemIds()));
            if (cartItems.size() != req.getCartItemIds().size()) {
                throw new BizException(4305, "部分购物车商品不存在");
            }
            cartItems.forEach(item -> raw.add(new OrderLine(item.getProductId(), item.getQuantity(), List.of(item.getId()))));
        }
        if (req.getItems() != null) {
            for (ProductOrderSubmitItemReq item : req.getItems()) {
                raw.add(new OrderLine(item.getProductId(), item.getQuantity(), List.of()));
            }
        }

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        Map<Long, List<Long>> cartIds = new LinkedHashMap<>();
        for (OrderLine line : raw) {
            quantities.merge(line.productId(), line.quantity(), Integer::sum);
            cartIds.computeIfAbsent(line.productId(), key -> new ArrayList<>()).addAll(line.cartIds());
        }
        return quantities.entrySet().stream()
                .map(entry -> new OrderLine(entry.getKey(), entry.getValue(), cartIds.getOrDefault(entry.getKey(), List.of())))
                .sorted(Comparator.comparing(OrderLine::productId))
                .toList();
    }

    private List<Long> lineCartIds(List<OrderLine> lines) {
        return lines.stream()
                .map(OrderLine::cartIds)
                .flatMap(Collection::stream)
                .distinct()
                .toList();
    }

    private List<ProductOrderSummaryVo> buildSummaryList(List<ProductOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ProductOrderItem>> itemsMap = productOrderItemMapper.selectList(new LambdaQueryWrapper<ProductOrderItem>()
                        .in(ProductOrderItem::getOrderId, orders.stream().map(ProductOrder::getId).collect(Collectors.toSet())))
                .stream()
                .collect(Collectors.groupingBy(ProductOrderItem::getOrderId));
        Set<Long> productIds = itemsMap.values().stream()
                .flatMap(List::stream)
                .map(ProductOrderItem::getProductId)
                .collect(Collectors.toSet());
        Map<Long, Product> products = productIds.isEmpty() ? Map.of() : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, String> categoryNames = productCategoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>())
                .stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName));
        Map<Long, SysUser> users = sysUserMapper.selectBatchIds(orders.stream().map(ProductOrder::getUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, PaymentOrder> paymentOrders = loadLatestPaymentOrders(orders);
        Map<Long, PaymentRefund> paymentRefunds = loadLatestPaymentRefunds(paymentOrders.values());
        Map<String, CommentEntity> comments = loadProductComments(orders, itemsMap);

        return orders.stream()
                .map(order -> toSummary(
                        order,
                        itemsMap.getOrDefault(order.getId(), List.of()),
                        products,
                        categoryNames,
                        users,
                        comments,
                        paymentOrders.get(order.getId()),
                        paymentRefunds
                ))
                .toList();
    }

    private ProductOrderSummaryVo toSummary(ProductOrder order, List<ProductOrderItem> items, Map<Long, Product> products,
                                            Map<Long, String> categoryNames, Map<Long, SysUser> users,
                                            Map<String, CommentEntity> comments, PaymentOrder paymentOrder,
                                            Map<Long, PaymentRefund> paymentRefunds) {
        ProductOrderItem firstItem = items.isEmpty() ? null : items.get(0);
        Product firstProduct = firstItem == null ? null : products.get(firstItem.getProductId());
        int totalQty = items.stream().mapToInt(ProductOrderItem::getQuantity).sum();
        boolean allCommented = !items.isEmpty() && items.stream()
                .allMatch(item -> comments.containsKey(commentKey(order.getUserId(), item.getProductId())));
        boolean canComment = "COMPLETED".equals(order.getStatus()) && items.stream()
                .anyMatch(item -> !comments.containsKey(commentKey(order.getUserId(), item.getProductId())));
        SysUser user = users.get(order.getUserId());
        PaymentRefund refund = paymentOrder == null ? null : paymentRefunds.get(paymentOrder.getId());

        ProductOrderSummaryVo vo = new ProductOrderSummaryVo();
        vo.setId(order.getId());
        vo.setUserId(order.getUserId());
        vo.setUsername(user == null ? "-" : ((user.getNickname() != null && !user.getNickname().isBlank()) ? user.getNickname() : user.getUsername()));
        vo.setProductId(firstProduct == null ? null : firstProduct.getId());
        vo.setProductName(firstProduct == null ? "-" : firstProduct.getName());
        vo.setCategoryId(firstProduct == null ? null : firstProduct.getCategoryId());
        vo.setCategoryName(firstProduct == null ? "-" : categoryNames.getOrDefault(firstProduct.getCategoryId(), "未分类"));
        vo.setQuantity(totalQty);
        vo.setUnitPrice(firstItem == null ? BigDecimal.ZERO : firstItem.getPrice());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount() == null ? order.getTotalAmount() : order.getPayAmount());
        vo.setItemCount(order.getItemCount() == null ? totalQty : order.getItemCount());
        vo.setOrderSummary(orderSummary(items, products));
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(label(STATUS_LABELS, order.getStatus(), order.getStatus()));
        vo.setPayStatus(order.getPayStatus());
        vo.setPayStatusLabel(label(PAY_STATUS_LABELS, order.getPayStatus(), order.getPayStatus()));
        vo.setPayMethod(order.getPayMethod());
        vo.setPayMethodLabel(label(PAY_METHOD_LABELS, order.getPayMethod(), "-"));
        vo.setPayTime(order.getPayTime());
        vo.setLatestPaymentNo(paymentOrder == null ? null : paymentOrder.getPaymentNo());
        vo.setLatestPaymentChannel(paymentOrder == null ? null : paymentOrder.getPayChannel());
        vo.setLatestPaymentChannelLabel(paymentOrder == null ? "-" : label(PAY_METHOD_LABELS, paymentOrder.getPayChannel(), paymentOrder.getPayChannel()));
        vo.setLatestPaymentStatus(paymentOrder == null ? null : paymentOrder.getStatus());
        vo.setLatestPaymentStatusLabel(paymentOrder == null ? "-" : label(PAYMENT_STATUS_LABELS, paymentOrder.getStatus(), paymentOrder.getStatus()));
        vo.setLatestPaymentTime(resolveLatestPaymentTime(paymentOrder));
        vo.setNextActionHint(nextActionHint(order.getStatus(), afterSaleStatus(order), paymentOrder));
        vo.setCanCancel("CREATED".equals(order.getStatus()));
        vo.setCanPay("CREATED".equals(order.getStatus()) && "UNPAID".equals(order.getPayStatus()));
        vo.setCanConfirmReceipt("SHIPPED".equals(order.getStatus()));
        vo.setCanComment(canComment);
        vo.setHasCommented(allCommented);
        vo.setCanRequestAfterSale(Set.of("PAID", "COMPLETED").contains(order.getStatus()) && "NONE".equals(afterSaleStatus(order)));
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setFullAddress(fullAddress(order));
        vo.setShippingCompany(order.getShippingCompany());
        vo.setTrackingNo(order.getTrackingNo());
        vo.setShippedTime(order.getShippedTime());
        vo.setAfterSaleStatus(afterSaleStatus(order));
        vo.setAfterSaleStatusLabel(afterSaleStatusLabel(order));
        vo.setAfterSaleReason(order.getAfterSaleReason());
        vo.setAfterSaleRemark(order.getAfterSaleRemark());
        vo.setAfterSaleAppliedTime(order.getAfterSaleAppliedTime());
        vo.setAfterSaleHandledTime(order.getAfterSaleHandledTime());
        vo.setRefundNo(refund == null ? null : refund.getRefundNo());
        vo.setRefundAmount(refund == null ? null : refund.getAmount());
        vo.setRefundStatus(refund == null ? null : refund.getStatus());
        vo.setRefundStatusLabel(refund == null ? "-" : label(REFUND_STATUS_LABELS, refund.getStatus(), refund.getStatus()));
        vo.setRefundTime(refund == null ? null : (refund.getUpdatedAt() == null ? refund.getCreatedAt() : refund.getUpdatedAt()));
        vo.setCreatedAt(order.getCreatedAt());
        return vo;
    }

    private ProductOrderDetailVo toDetail(ProductOrder order) {
        List<ProductOrderItem> items = productOrderItemMapper.selectList(new LambdaQueryWrapper<ProductOrderItem>()
                .eq(ProductOrderItem::getOrderId, order.getId()));
        Map<Long, Product> products = items.isEmpty() ? Map.of() : productMapper.selectBatchIds(
                        items.stream().map(ProductOrderItem::getProductId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, String> categoryNames = productCategoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>())
                .stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName));
        Map<String, CommentEntity> comments = loadProductComments(List.of(order), Map.of(order.getId(), items));

        ProductOrderSummaryVo base = buildSummaryList(List.of(order)).get(0);
        ProductOrderDetailVo detail = new ProductOrderDetailVo();
        BeanUtils.copyProperties(base, detail);
        detail.setItems(items.stream().map(item -> {
            Product product = products.get(item.getProductId());
            ProductOrderItemVo vo = new ProductOrderItemVo();
            vo.setProductId(item.getProductId());
            vo.setProductName(product == null ? "-" : product.getName());
            vo.setCategoryId(product == null ? null : product.getCategoryId());
            vo.setCategoryName(product == null ? "-" : categoryNames.getOrDefault(product.getCategoryId(), "未分类"));
            vo.setQuantity(item.getQuantity());
            vo.setUnitPrice(item.getPrice());
            vo.setAmount(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            vo.setCommented(comments.containsKey(commentKey(order.getUserId(), item.getProductId())));
            return vo;
        }).toList());
        return detail;
    }

    private Map<String, CommentEntity> loadProductComments(List<ProductOrder> orders, Map<Long, List<ProductOrderItem>> itemsMap) {
        Set<Long> userIds = orders.stream().map(ProductOrder::getUserId).collect(Collectors.toSet());
        Set<Long> productIds = itemsMap.values().stream()
                .flatMap(List::stream)
                .map(ProductOrderItem::getProductId)
                .collect(Collectors.toSet());
        if (userIds.isEmpty() || productIds.isEmpty()) {
            return Map.of();
        }
        return commentMapper.selectList(new LambdaQueryWrapper<CommentEntity>()
                        .in(CommentEntity::getUserId, userIds)
                        .in(CommentEntity::getProductId, productIds)
                        .isNull(CommentEntity::getAppointmentId))
                .stream()
                .collect(Collectors.toMap(
                        comment -> commentKey(comment.getUserId(), comment.getProductId()),
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private Map<Long, PaymentOrder> loadLatestPaymentOrders(List<ProductOrder> orders) {
        Set<Long> orderIds = orders.stream().map(ProductOrder::getId).collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getBusinessType, "PRODUCT_ORDER")
                        .in(PaymentOrder::getBusinessId, orderIds)
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

    private void restoreStock(Long orderId) {
        List<ProductOrderItem> items = productOrderItemMapper.selectList(new LambdaQueryWrapper<ProductOrderItem>()
                .eq(ProductOrderItem::getOrderId, orderId));
        for (ProductOrderItem item : items) {
            Product product = productMapper.selectById(item.getProductId());
            if (product == null) {
                continue;
            }
            product.setStock((product.getStock() == null ? 0 : product.getStock()) + item.getQuantity());
            productMapper.updateById(product);
        }
    }

    private String orderSummary(List<ProductOrderItem> items, Map<Long, Product> products) {
        if (items.isEmpty()) {
            return "-";
        }
        Product first = products.get(items.get(0).getProductId());
        String firstName = first == null ? "商品" : first.getName();
        return items.size() == 1 ? firstName : firstName + " 等 " + items.size() + " 件商品";
    }

    private String fullAddress(ProductOrder order) {
        return String.join(" ", List.of(text(order.getProvince()), text(order.getCity()), text(order.getDistrict()), text(order.getDetailAddress()))).trim();
    }

    private String afterSaleStatus(ProductOrder order) {
        return order.getAfterSaleStatus() == null || order.getAfterSaleStatus().isBlank() ? "NONE" : order.getAfterSaleStatus();
    }

    private String afterSaleStatusLabel(ProductOrder order) {
        String status = afterSaleStatus(order);
        return switch (status) {
            case "NONE" -> "未申请";
            case "REQUESTED" -> "PAID".equals(order.getStatus()) ? "待审核退款" : "待处理售后";
            case "REFUNDED" -> "退款已完成";
            case "REJECTED" -> "PAID".equals(order.getStatus()) ? "退款已驳回" : "售后已驳回";
            default -> status;
        };
    }

    private String commentKey(Long userId, Long productId) {
        return userId + ":" + productId;
    }

    private String label(Map<String, String> map, String key, String fallback) {
        return key == null || key.isBlank() ? fallback : map.getOrDefault(key, fallback);
    }

    private String text(String value) {
        return value == null ? "" : value;
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

    private String nextActionHint(String status, String afterSaleStatus, PaymentOrder paymentOrder) {
        if ("REQUESTED".equals(afterSaleStatus)) {
            return "PAID".equals(status) ? "退款申请已提交，等待管理员审核。" : "售后申请已提交，等待管理员处理。";
        }
        if ("REFUNDED".equals(afterSaleStatus)) {
            return "退款已完成，可在钱包流水中查看退款记录。";
        }
        if ("REJECTED".equals(afterSaleStatus)) {
            return "PAID".equals(status) ? "退款申请已驳回，请查看订单处理备注。" : "售后申请已驳回，请查看订单处理备注。";
        }
        if ("CREATED".equals(status) && paymentOrder != null && "FAILED".equals(paymentOrder.getStatus())) {
            return "最近一次支付失败，请重新选择支付方式发起支付。";
        }
        if ("CREATED".equals(status) && paymentOrder != null && "PAYING".equals(paymentOrder.getStatus())) {
            return "支付单已创建，你可以继续完成支付或刷新支付状态。";
        }
        return switch (status) {
            case "CREATED" -> "请先完成支付，再继续后续流程。";
            case "PAID" -> "订单已支付，等待商家发货；若暂不需要，可发起退款申请。";
            case "SHIPPED" -> "商品已发货，请在收到商品后确认收货。";
            case "COMPLETED" -> "订单已完成，现在可以评价商品或申请售后。";
            case "CANCELLED" -> "订单已取消。";
            case "REFUNDED" -> "订单已退款。";
            default -> "当前没有可执行的后续操作。";
        };
    }

    private String normalizePayMethod(String payMethod) {
        String normalized = payMethod == null ? "" : payMethod.trim().toUpperCase();
        if (!PAY_METHOD_LABELS.containsKey(normalized)) {
            throw new BizException(4112, "暂不支持该支付方式");
        }
        return normalized;
    }

    private String buildPayNo(Long orderId, String payMethod) {
        return payMethod + "-" + orderId + "-" + System.currentTimeMillis();
    }

    private record OrderLine(Long productId, Integer quantity, List<Long> cartIds) {}
}
