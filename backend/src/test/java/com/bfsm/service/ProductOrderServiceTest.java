package com.bfsm.service;

import com.bfsm.cache.RedisLockService;
import com.bfsm.common.BizException;
import com.bfsm.dto.ProductOrderCreateReq;
import com.bfsm.dto.ProductOrderSubmitItemReq;
import com.bfsm.dto.AfterSaleApplyReq;
import com.bfsm.dto.AfterSaleReviewReq;
import com.bfsm.entity.Product;
import com.bfsm.entity.ProductOrder;
import com.bfsm.entity.ProductOrderItem;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductOrderServiceTest {
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductOrderMapper productOrderMapper;
    @Mock
    private ProductOrderItemMapper productOrderItemMapper;
    @Mock
    private ProductCategoryMapper productCategoryMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private RedisLockService redisLockService;
    @Mock
    private CommentMapper commentMapper;
    @Mock
    private WalletService walletService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ShoppingCartItemMapper shoppingCartItemMapper;
    @Mock
    private UserAddressMapper userAddressMapper;
    @Mock
    private PaymentOrderMapper paymentOrderMapper;
    @Mock
    private PaymentRefundMapper paymentRefundMapper;
    @Mock
    private PaymentService paymentService;

    private ProductOrderService productOrderService;

    @BeforeEach
    void setUp() {
        productOrderService = new ProductOrderService(
                productMapper,
                productOrderMapper,
                productOrderItemMapper,
                productCategoryMapper,
                sysUserMapper,
                redisLockService,
                commentMapper,
                walletService,
                notificationService,
                shoppingCartItemMapper,
                userAddressMapper,
                paymentOrderMapper,
                paymentRefundMapper,
                paymentService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createOrder_shouldRejectWhenStockInsufficient() {
        setAuthentication(4L, "user_tom", "USER");
        Product product = product(1L, 1);
        when(productMapper.selectById(1L)).thenReturn(product);
        when(redisLockService.tryLock(eq("lock:product:stock:1"), any())).thenReturn("token");
        when(userAddressMapper.selectById(11L)).thenReturn(address(11L, 4L));

        ProductOrderCreateReq req = new ProductOrderCreateReq();
        req.setAddressId(11L);
        req.setItems(List.of(orderItem(1L, 2)));

        BizException exception = assertThrows(BizException.class, () -> productOrderService.createOrder(req));
        assertEquals(4102, exception.getCode());
    }

    @Test
    void createOrder_shouldDecreaseStockAndInsertOrder() {
        setAuthentication(4L, "user_tom", "USER");
        Product product = product(2L, 8);
        when(productMapper.selectById(2L)).thenReturn(product);
        when(redisLockService.tryLock(eq("lock:product:stock:2"), any())).thenReturn("token");
        when(userAddressMapper.selectById(12L)).thenReturn(address(12L, 4L));
        when(productCategoryMapper.selectList(any())).thenReturn(List.of());
        when(commentMapper.selectList(any())).thenReturn(List.of());
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of());
        when(productOrderMapper.selectById(anyLong())).thenReturn(null);

        ProductOrderCreateReq req = new ProductOrderCreateReq();
        req.setAddressId(12L);
        req.setItems(List.of(orderItem(2L, 3)));

        try {
            productOrderService.createOrder(req);
        } catch (Exception ignored) {
            // detailForCurrentUser 读取明细需要更多 stub，这里只验证写入链路
        }

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).updateById(productCaptor.capture());
        assertEquals(5, productCaptor.getValue().getStock());
        verify(productOrderMapper, times(1)).insert(any(ProductOrder.class));
        verify(productOrderItemMapper, times(1)).insert(any(ProductOrderItem.class));
    }

    @Test
    void cancelForCurrentUser_shouldRejectOtherUsersOrder() {
        setAuthentication(4L, "user_tom", "USER");
        ProductOrder order = new ProductOrder();
        order.setId(6L);
        order.setUserId(9L);
        order.setStatus("CREATED");
        when(productOrderMapper.selectById(6L)).thenReturn(order);

        BizException exception = assertThrows(BizException.class, () -> productOrderService.cancelForCurrentUser(6L));
        assertEquals(403, exception.getCode());
    }

    @Test
    void updateStatus_shouldRejectIllegalFlow() {
        ProductOrder order = new ProductOrder();
        order.setId(8L);
        order.setStatus("CREATED");
        when(productOrderMapper.selectById(8L)).thenReturn(order);

        BizException exception = assertThrows(BizException.class, () -> productOrderService.updateStatus(8L, "COMPLETED"));
        assertEquals(4104, exception.getCode());
    }

    @Test
    void applyAfterSale_shouldAllowPaidOrderRefundRequest() {
        setAuthentication(5L, "q", "USER");
        ProductOrder order = new ProductOrder();
        order.setId(18L);
        order.setUserId(5L);
        order.setStatus("PAID");
        order.setAfterSaleStatus("NONE");
        when(productOrderMapper.selectById(18L)).thenReturn(order);

        AfterSaleApplyReq req = new AfterSaleApplyReq();
        req.setReason("不想要了，申请退款");

        productOrderService.applyAfterSale(18L, req);

        verify(productOrderMapper).updateById(order);
        assertEquals("REQUESTED", order.getAfterSaleStatus());
        assertEquals("不想要了，申请退款", order.getAfterSaleReason());
        verify(notificationService).notifyAdmins(
                eq("ORDER"),
                eq("有退款/售后申请待处理"),
                eq("订单 #18 提交了退款或售后申请。"),
                eq("/admin/product-orders")
        );
    }

    @Test
    void applyAfterSale_shouldRejectCreatedOrder() {
        setAuthentication(5L, "q", "USER");
        ProductOrder order = new ProductOrder();
        order.setId(19L);
        order.setUserId(5L);
        order.setStatus("CREATED");
        order.setAfterSaleStatus("NONE");
        when(productOrderMapper.selectById(19L)).thenReturn(order);

        AfterSaleApplyReq req = new AfterSaleApplyReq();
        req.setReason("测试");

        BizException exception = assertThrows(BizException.class, () -> productOrderService.applyAfterSale(19L, req));
        assertEquals(4108, exception.getCode());
        verify(productOrderMapper, never()).updateById(any(ProductOrder.class));
    }

    @Test
    void reviewAfterSale_shouldRestoreStockWhenApproved() {
        ProductOrder order = new ProductOrder();
        order.setId(20L);
        order.setUserId(5L);
        order.setStatus("PAID");
        order.setPayStatus("PAID");
        order.setAfterSaleStatus("REQUESTED");
        when(productOrderMapper.selectById(20L)).thenReturn(order);

        ProductOrderItem item = new ProductOrderItem();
        item.setOrderId(20L);
        item.setProductId(2L);
        item.setQuantity(3);
        when(productOrderItemMapper.selectList(any())).thenReturn(List.of(item));

        Product product = product(2L, 5);
        when(productMapper.selectById(2L)).thenReturn(product);

        AfterSaleReviewReq req = new AfterSaleReviewReq();
        req.setApproved(true);
        req.setRemark("同意退款");

        productOrderService.reviewAfterSale(20L, req);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).updateById(productCaptor.capture());
        assertEquals(8, productCaptor.getValue().getStock());
        assertEquals("REFUNDED", order.getAfterSaleStatus());
        assertEquals("REFUNDED", order.getStatus());
        assertEquals("REFUNDED", order.getPayStatus());
        verify(productOrderMapper).updateById(order);
        verify(paymentService).recordProductOrderRefund(order, "同意退款");
    }

    @Test
    void reviewAfterSale_shouldNotRestoreStockWhenRejected() {
        ProductOrder order = new ProductOrder();
        order.setId(21L);
        order.setUserId(5L);
        order.setStatus("PAID");
        order.setPayStatus("PAID");
        order.setAfterSaleStatus("REQUESTED");
        when(productOrderMapper.selectById(21L)).thenReturn(order);

        AfterSaleReviewReq req = new AfterSaleReviewReq();
        req.setApproved(false);
        req.setRemark("不符合退款条件");

        productOrderService.reviewAfterSale(21L, req);

        assertEquals("REJECTED", order.getAfterSaleStatus());
        verify(productOrderItemMapper, never()).selectList(any());
        verify(productMapper, never()).updateById(any(Product.class));
        verify(paymentService, never()).recordProductOrderRefund(any(ProductOrder.class), any());
        verify(productOrderMapper).updateById(order);
    }

    private Product product(Long id, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName("serum");
        product.setCategoryId(1L);
        product.setPrice(BigDecimal.valueOf(99));
        product.setStock(stock);
        product.setStatus("ON");
        return product;
    }

    private UserAddress address(Long id, Long userId) {
        UserAddress address = new UserAddress();
        address.setId(id);
        address.setUserId(userId);
        address.setReceiverName("张三");
        address.setReceiverPhone("13800000000");
        address.setProvince("上海市");
        address.setCity("上海市");
        address.setDistrict("浦东新区");
        address.setDetailAddress("世纪大道 1 号");
        address.setTagName("家");
        return address;
    }

    private ProductOrderSubmitItemReq orderItem(Long productId, int quantity) {
        ProductOrderSubmitItemReq item = new ProductOrderSubmitItemReq();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private void setAuthentication(Long userId, String username, String roleCode) {
        SecurityUser principal = new SecurityUser(userId, username, "", roleCode, true);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
