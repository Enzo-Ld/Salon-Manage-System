package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bfsm.cache.CacheService;
import com.bfsm.common.BizException;
import com.bfsm.common.PageUtils;
import com.bfsm.common.SecurityUtils;
import com.bfsm.entity.CommentEntity;
import com.bfsm.entity.Favorite;
import com.bfsm.entity.Product;
import com.bfsm.entity.ProductCategory;
import com.bfsm.entity.ProductOrder;
import com.bfsm.entity.ProductOrderItem;
import com.bfsm.mapper.CommentMapper;
import com.bfsm.mapper.FavoriteMapper;
import com.bfsm.mapper.ProductCategoryMapper;
import com.bfsm.mapper.ProductMapper;
import com.bfsm.mapper.ProductOrderItemMapper;
import com.bfsm.mapper.ProductOrderMapper;
import com.bfsm.security.SecurityUser;
import com.bfsm.vo.PageResultVo;
import com.bfsm.vo.ProductDetailVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductService {
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "ON", "上架中",
            "OFF", "已下架"
    );

    private final ProductMapper productMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final CacheService cacheService;
    private final FavoriteMapper favoriteMapper;
    private final CommentMapper commentMapper;
    private final MallConsultantService mallConsultantService;
    private final ProductOrderMapper productOrderMapper;
    private final ProductOrderItemMapper productOrderItemMapper;

    public ProductService(ProductMapper productMapper, ProductCategoryMapper productCategoryMapper, CacheService cacheService,
                          FavoriteMapper favoriteMapper, CommentMapper commentMapper,
                          MallConsultantService mallConsultantService, ProductOrderMapper productOrderMapper,
                          ProductOrderItemMapper productOrderItemMapper) {
        this.productMapper = productMapper;
        this.productCategoryMapper = productCategoryMapper;
        this.cacheService = cacheService;
        this.favoriteMapper = favoriteMapper;
        this.commentMapper = commentMapper;
        this.mallConsultantService = mallConsultantService;
        this.productOrderMapper = productOrderMapper;
        this.productOrderItemMapper = productOrderItemMapper;
    }

    public List<ProductCategory> categories() {
        return productCategoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>()
                .and(query -> query.eq(ProductCategory::getStatus, "ON").or().isNull(ProductCategory::getStatus))
                .orderByAsc(ProductCategory::getSortNo)
                .orderByAsc(ProductCategory::getId));
    }

    public List<ProductCategory> manageCategories(String keyword, String status) {
        LambdaQueryWrapper<ProductCategory> query = categoryQuery(keyword, status);
        return productCategoryMapper.selectList(query);
    }

    public PageResultVo<ProductCategory> manageCategoriesPage(String keyword, String status, Long pageNum, Long pageSize) {
        Page<ProductCategory> page = new Page<>(PageUtils.normalizePageNum(pageNum), PageUtils.normalizePageSize(pageSize));
        Page<ProductCategory> result = productCategoryMapper.selectPage(page, categoryQuery(keyword, status));
        return new PageResultVo<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Transactional
    public void saveCategory(ProductCategory body) {
        if (!StringUtils.hasText(body.getName())) {
            throw new BizException(4201, "商品分类名称不能为空");
        }
        if (!StringUtils.hasText(body.getStatus())) {
            body.setStatus("ON");
        }
        if (body.getSortNo() == null) {
            body.setSortNo(0);
        }
        if (body.getId() == null) {
            productCategoryMapper.insert(body);
        } else {
            productCategoryMapper.updateById(body);
        }
        cacheService.evict("products:list:*");
    }

    @Transactional
    public void deleteCategory(Long id) {
        ProductCategory category = productCategoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(404, "商品分类不存在");
        }
        Long productCount = productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getCategoryId, id));
        if (productCount != null && productCount > 0) {
            throw new BizException(4202, "该分类下已有商品，请先调整商品分类或下架商品");
        }
        productCategoryMapper.deleteById(id);
        cacheService.evict("products:list:*");
    }

    public List<ProductDetailVo> list(Long categoryId) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        LambdaQueryWrapper<Product> query = productQuery(categoryId, null, null, null, "ADMIN".equals(currentUser.getRoleCode()));
        List<Product> products = productMapper.selectList(query);
        Map<Long, String> categoryNames = categoryNameMap();
        return products.stream().map(product -> toDetail(product, categoryNames)).toList();
    }

    public PageResultVo<ProductDetailVo> page(Long categoryId, Long pageNum, Long pageSize) {
        return page(categoryId, null, null, null, pageNum, pageSize);
    }

    public PageResultVo<ProductDetailVo> page(Long categoryId, String keyword, String status, Boolean lowStock, Long pageNum, Long pageSize) {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        boolean adminView = "ADMIN".equals(currentUser.getRoleCode());
        Page<Product> page = new Page<>(
                PageUtils.normalizePageNum(pageNum),
                PageUtils.normalizePageSize(pageSize)
        );
        Page<Product> result = productMapper.selectPage(page, productQuery(categoryId, keyword, status, lowStock, adminView));
        Map<Long, String> categoryNames = categoryNameMap();
        return new PageResultVo<>(
                result.getRecords().stream().map(product -> toDetail(product, categoryNames)).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                result.getPages()
        );
    }

    public ProductDetailVo detail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(404, "Product not found");
        }
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if (!"ADMIN".equals(currentUser.getRoleCode()) && !"ON".equals(product.getStatus())) {
            throw new BizException(404, "Product not found");
        }
        ProductDetailVo detail = toDetail(product, categoryNameMap());
        enrichDetail(detail, currentUser.getUserId());
        return detail;
    }

    @Transactional
    public void saveProduct(Product body) {
        if (!StringUtils.hasText(body.getName())) {
            throw new BizException(4301, "商品名称不能为空");
        }
        if (body.getCategoryId() == null || productCategoryMapper.selectById(body.getCategoryId()) == null) {
            throw new BizException(4302, "请选择有效商品分类");
        }
        if (body.getPrice() == null || body.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(4303, "商品价格不能小于 0");
        }
        if (body.getStock() == null || body.getStock() < 0) {
            throw new BizException(4304, "商品库存不能小于 0");
        }
        if (!StringUtils.hasText(body.getStatus())) {
            body.setStatus("ON");
        }
        if (body.getSortNo() == null) {
            body.setSortNo(0);
        }
        if (body.getId() == null) {
            productMapper.insert(body);
        } else {
            productMapper.updateById(body);
        }
        cacheService.evict("products:list:*");
    }

    @Transactional
    public void updateProductStatus(Long id, String status) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(404, "商品不存在");
        }
        product.setStatus(normalizeStatus(status));
        productMapper.updateById(product);
        cacheService.evict("products:list:*");
    }

    @Transactional
    public void updateProductStatuses(List<Long> ids, String status) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(4305, "请选择需要处理的商品");
        }
        String normalized = normalizeStatus(status);
        ids.forEach(id -> {
            Product product = productMapper.selectById(id);
            if (product != null) {
                product.setStatus(normalized);
                productMapper.updateById(product);
            }
        });
        cacheService.evict("products:list:*");
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(404, "商品不存在");
        }
        Long orderedCount = productOrderItemMapper.selectCount(new LambdaQueryWrapper<ProductOrderItem>()
                .eq(ProductOrderItem::getProductId, id));
        if (orderedCount != null && orderedCount > 0) {
            product.setStatus("OFF");
            productMapper.updateById(product);
        } else {
            productMapper.deleteById(id);
        }
        cacheService.evict("products:list:*");
    }

    private LambdaQueryWrapper<ProductCategory> categoryQuery(String keyword, String status) {
        LambdaQueryWrapper<ProductCategory> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.like(ProductCategory::getName, keyword.trim());
        }
        if (StringUtils.hasText(status)) {
            query.eq(ProductCategory::getStatus, status.trim());
        }
        query.orderByAsc(ProductCategory::getSortNo).orderByAsc(ProductCategory::getId);
        return query;
    }

    private LambdaQueryWrapper<Product> productQuery(Long categoryId, String keyword, String status, Boolean lowStock, boolean adminView) {
        LambdaQueryWrapper<Product> query = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            query.eq(Product::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(Product::getName, trimmed)
                    .or()
                    .like(Product::getIntro, trimmed));
        }
        if (!adminView) {
            query.eq(Product::getStatus, "ON");
        } else if (StringUtils.hasText(status)) {
            query.eq(Product::getStatus, status.trim());
        }
        if (Boolean.TRUE.equals(lowStock)) {
            query.le(Product::getStock, 10);
        }
        query.orderByAsc(Product::getSortNo).orderByAsc(Product::getId);
        return query;
    }

    private ProductDetailVo toDetail(Product product, Map<Long, String> categoryNames) {
        ProductDetailVo detail = new ProductDetailVo();
        detail.setId(product.getId());
        detail.setCategoryId(product.getCategoryId());
        detail.setCategoryName(categoryNames.getOrDefault(product.getCategoryId(), "Uncategorized"));
        detail.setName(product.getName());
        detail.setIntro(product.getIntro());
        detail.setImageUrl(product.getImageUrl());
        detail.setPrice(product.getPrice());
        detail.setStock(product.getStock());
        detail.setStatus(product.getStatus());
        detail.setStatusLabel(STATUS_LABELS.getOrDefault(product.getStatus(), product.getStatus()));
        detail.setSortNo(product.getSortNo());
        detail.setCreatedAt(product.getCreatedAt());
        detail.setUpdatedAt(product.getUpdatedAt());
        detail.setFavoriteCount(0);
        detail.setCommentCount(0);
        detail.setAverageRating(BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP));
        detail.setFavorited(false);
        detail.setCanComment(false);
        detail.setHasCommented(false);
        detail.setConsultantUserId(null);
        detail.setConsultantName("");
        return detail;
    }

    private Map<Long, String> categoryNameMap() {
        return productCategoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>())
                .stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName));
    }

    private void enrichDetail(ProductDetailVo detail, Long currentUserId) {
        List<CommentEntity> comments = commentMapper.selectList(new LambdaQueryWrapper<CommentEntity>()
                .eq(CommentEntity::getProductId, detail.getId())
                .eq(CommentEntity::getStatus, "APPROVED"));
        detail.setFavoriteCount(Math.toIntExact(favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getProductId, detail.getId()))));
        detail.setCommentCount(comments.size());
        detail.setAverageRating(comments.isEmpty()
                ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(comments.stream().mapToInt(CommentEntity::getRating).average().orElse(0))
                .setScale(1, RoundingMode.HALF_UP));
        detail.setFavorited(favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, currentUserId)
                .eq(Favorite::getProductId, detail.getId())) > 0);
        CommentEntity myComment = commentMapper.selectOne(new LambdaQueryWrapper<CommentEntity>()
                .eq(CommentEntity::getUserId, currentUserId)
                .eq(CommentEntity::getProductId, detail.getId())
                .isNull(CommentEntity::getAppointmentId)
                .last("limit 1"));
        detail.setHasCommented(myComment != null);
        detail.setCanComment(!detail.isHasCommented() && hasCompletedOrder(currentUserId, detail.getId()));
        detail.setConsultantUserId(mallConsultantService.resolveConsultantUserId());
        detail.setConsultantName(mallConsultantService.resolveConsultantDisplayName());
    }

    private boolean hasCompletedOrder(Long userId, Long productId) {
        List<ProductOrderItem> items = productOrderItemMapper.selectList(new LambdaQueryWrapper<ProductOrderItem>()
                .eq(ProductOrderItem::getProductId, productId));
        if (items.isEmpty()) {
            return false;
        }

        Set<Long> orderIds = items.stream().map(ProductOrderItem::getOrderId).collect(Collectors.toSet());
        return productOrderMapper.selectCount(new LambdaQueryWrapper<ProductOrder>()
                .eq(ProductOrder::getUserId, userId)
                .in(ProductOrder::getId, orderIds)
                .eq(ProductOrder::getStatus, "COMPLETED")) > 0;
    }

    private String normalizeStatus(String status) {
        if (!"ON".equals(status) && !"OFF".equals(status)) {
            throw new BizException(4306, "商品状态只能为 ON 或 OFF");
        }
        return status;
    }
}
