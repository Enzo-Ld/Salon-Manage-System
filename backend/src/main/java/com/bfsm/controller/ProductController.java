package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.dto.AfterSaleApplyReq;
import com.bfsm.dto.AfterSaleReviewReq;
import com.bfsm.dto.CartAddReq;
import com.bfsm.dto.CartUpdateReq;
import com.bfsm.dto.OrderPayReq;
import com.bfsm.dto.ProductBatchStatusReq;
import com.bfsm.dto.ProductOrderCreateReq;
import com.bfsm.dto.ProductOrderShipReq;
import com.bfsm.dto.ProductOrderStatusReq;
import com.bfsm.dto.StatusReq;
import com.bfsm.entity.Product;
import com.bfsm.entity.ProductCategory;
import com.bfsm.entity.UserAddress;
import com.bfsm.service.ProductOrderService;
import com.bfsm.service.ProductService;
import com.bfsm.service.ShoppingCartService;
import com.bfsm.service.UserAddressService;
import com.bfsm.vo.ProductDetailVo;
import com.bfsm.vo.ProductOrderDetailVo;
import com.bfsm.vo.ProductOrderSummaryVo;
import com.bfsm.vo.PageResultVo;
import com.bfsm.vo.ShoppingCartItemVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@Tag(name = "商城模块")
public class ProductController {
    private final ProductService productService;
    private final ProductOrderService productOrderService;
    private final ShoppingCartService shoppingCartService;
    private final UserAddressService userAddressService;

    public ProductController(ProductService productService, ProductOrderService productOrderService,
                             ShoppingCartService shoppingCartService, UserAddressService userAddressService) {
        this.productService = productService;
        this.productOrderService = productOrderService;
        this.shoppingCartService = shoppingCartService;
        this.userAddressService = userAddressService;
    }

    @GetMapping("/categories")
    @Operation(summary = "查询商品分类")
    public ApiResponse<List<ProductCategory>> categories() {
        return ApiResponse.ok(productService.categories());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/categories")
    @Operation(summary = "新增或更新商品分类")
    public ApiResponse<Void> saveCategory(@RequestBody ProductCategory body) {
        productService.saveCategory(body);
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/categories/manage")
    @Operation(summary = "查询商品分类管理列表")
    public ApiResponse<List<ProductCategory>> manageCategories(@RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String status) {
        return ApiResponse.ok(productService.manageCategories(keyword, status));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/categories/manage/page")
    @Operation(summary = "分页查询商品分类管理列表")
    public ApiResponse<PageResultVo<ProductCategory>> manageCategoriesPage(@RequestParam(required = false) String keyword,
                                                                          @RequestParam(required = false) String status,
                                                                          @RequestParam(required = false) Long pageNum,
                                                                          @RequestParam(required = false) Long pageSize) {
        return ApiResponse.ok(productService.manageCategoriesPage(keyword, status, pageNum, pageSize));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/categories/{id}")
    @Operation(summary = "删除商品分类")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        productService.deleteCategory(id);
        return ApiResponse.ok();
    }

    @GetMapping
    @Operation(summary = "查询商品列表")
    public ApiResponse<List<ProductDetailVo>> list(@RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(productService.list(categoryId));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询商品列表")
    public ApiResponse<PageResultVo<ProductDetailVo>> page(@RequestParam(required = false) Long categoryId,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) Boolean lowStock,
                                                           @RequestParam(required = false) Long pageNum,
                                                           @RequestParam(required = false) Long pageSize) {
        return ApiResponse.ok(productService.page(categoryId, keyword, status, lowStock, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询商品详情")
    public ApiResponse<ProductDetailVo> detail(@PathVariable Long id) {
        return ApiResponse.ok(productService.detail(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @Operation(summary = "新增或更新商品")
    public ApiResponse<Void> save(@RequestBody Product body) {
        productService.saveProduct(body);
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/status")
    @Operation(summary = "更新商品上下架状态")
    public ApiResponse<Void> updateProductStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        productService.updateProductStatus(id, req.getStatus());
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/batch-status")
    @Operation(summary = "批量更新商品上下架状态")
    public ApiResponse<Void> updateProductStatuses(@Valid @RequestBody ProductBatchStatusReq req) {
        productService.updateProductStatuses(req.getIds(), req.getStatus());
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除商品或保护性下架")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ApiResponse.ok();
    }

    @GetMapping("/cart")
    @Operation(summary = "查询当前用户购物车")
    public ApiResponse<List<ShoppingCartItemVo>> cart() {
        return ApiResponse.ok(shoppingCartService.currentUserCart());
    }

    @PostMapping("/cart")
    @Operation(summary = "加入购物车")
    public ApiResponse<Void> addCart(@Valid @RequestBody CartAddReq req) {
        shoppingCartService.addCurrentUserCart(req);
        return ApiResponse.ok();
    }

    @PostMapping("/cart/{id}")
    @Operation(summary = "更新购物车商品")
    public ApiResponse<Void> updateCart(@PathVariable Long id, @RequestBody CartUpdateReq req) {
        shoppingCartService.updateCurrentUserCart(id, req);
        return ApiResponse.ok();
    }

    @DeleteMapping("/cart/{id}")
    @Operation(summary = "删除购物车商品")
    public ApiResponse<Void> deleteCart(@PathVariable Long id) {
        shoppingCartService.removeCurrentUserCart(id);
        return ApiResponse.ok();
    }

    @GetMapping("/addresses")
    @Operation(summary = "查询当前用户收货地址")
    public ApiResponse<List<UserAddress>> addresses() {
        return ApiResponse.ok(userAddressService.currentUserAddresses());
    }

    @PostMapping("/addresses")
    @Operation(summary = "新增或更新收货地址")
    public ApiResponse<Void> saveAddress(@RequestBody UserAddress body) {
        userAddressService.saveCurrentUserAddress(body);
        return ApiResponse.ok();
    }

    @PostMapping("/addresses/{id}/default")
    @Operation(summary = "设置默认收货地址")
    public ApiResponse<Void> setDefaultAddress(@PathVariable Long id) {
        userAddressService.setDefaultCurrentUserAddress(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/addresses/{id}")
    @Operation(summary = "删除收货地址")
    public ApiResponse<Void> deleteAddress(@PathVariable Long id) {
        userAddressService.deleteCurrentUserAddress(id);
        return ApiResponse.ok();
    }

    @PostMapping("/order")
    @Operation(summary = "提交商品订单")
    public ApiResponse<ProductOrderDetailVo> createOrder(@Valid @RequestBody ProductOrderCreateReq req) {
        return ApiResponse.ok(productOrderService.createOrder(req));
    }

    @GetMapping("/orders/me")
    @Operation(summary = "查询我的商品订单")
    public ApiResponse<List<ProductOrderSummaryVo>> myOrders() {
        return ApiResponse.ok(productOrderService.listCurrentUserOrders());
    }

    @GetMapping("/orders/me/page")
    @Operation(summary = "分页查询我的商品订单")
    public ApiResponse<PageResultVo<ProductOrderSummaryVo>> myOrdersPage(@RequestParam(required = false) Long pageNum,
                                                                         @RequestParam(required = false) Long pageSize) {
        return ApiResponse.ok(productOrderService.pageCurrentUserOrders(pageNum, pageSize));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "查询商品订单详情")
    public ApiResponse<ProductOrderDetailVo> orderDetail(@PathVariable Long id) {
        return ApiResponse.ok(productOrderService.detailForCurrentUser(id));
    }

    @PostMapping("/orders/{id}/cancel")
    @Operation(summary = "取消商品订单")
    public ApiResponse<Void> cancelOrder(@PathVariable Long id) {
        productOrderService.cancelForCurrentUser(id);
        return ApiResponse.ok();
    }

    @PostMapping("/orders/{id}/pay")
    @Operation(summary = "支付商品订单")
    public ApiResponse<ProductOrderDetailVo> payOrder(@PathVariable Long id, @Valid @RequestBody OrderPayReq req) {
        return ApiResponse.ok(productOrderService.payForCurrentUser(id, req));
    }

    @PostMapping("/orders/{id}/confirm-receipt")
    @Operation(summary = "确认收货")
    public ApiResponse<ProductOrderDetailVo> confirmReceipt(@PathVariable Long id) {
        return ApiResponse.ok(productOrderService.confirmReceiptForCurrentUser(id));
    }

    @PostMapping("/orders/{id}/after-sale")
    @Operation(summary = "申请售后")
    public ApiResponse<Void> applyAfterSale(@PathVariable Long id, @Valid @RequestBody AfterSaleApplyReq req) {
        productOrderService.applyAfterSale(id, req);
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/orders")
    @Operation(summary = "查询全部商品订单")
    public ApiResponse<List<ProductOrderSummaryVo>> orders() {
        return ApiResponse.ok(productOrderService.listAllOrders());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/orders/page")
    @Operation(summary = "分页查询全部商品订单")
    public ApiResponse<PageResultVo<ProductOrderSummaryVo>> ordersPage(@RequestParam(required = false) Long pageNum,
                                                                       @RequestParam(required = false) Long pageSize) {
        return ApiResponse.ok(productOrderService.pageAllOrders(pageNum, pageSize));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/orders/{id}/status")
    @Operation(summary = "推进商品订单状态")
    public ApiResponse<Void> updateOrderStatus(@PathVariable Long id, @Valid @RequestBody ProductOrderStatusReq req) {
        productOrderService.updateStatus(id, req.getStatus());
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/orders/{id}/ship")
    @Operation(summary = "订单发货")
    public ApiResponse<Void> shipOrder(@PathVariable Long id, @Valid @RequestBody ProductOrderShipReq req) {
        productOrderService.shipOrder(id, req);
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/orders/{id}/after-sale/review")
    @Operation(summary = "处理售后申请")
    public ApiResponse<Void> reviewAfterSale(@PathVariable Long id, @RequestBody AfterSaleReviewReq req) {
        productOrderService.reviewAfterSale(id, req);
        return ApiResponse.ok();
    }
}
