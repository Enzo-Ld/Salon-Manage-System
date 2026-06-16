package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.common.BizException;
import com.bfsm.common.SecurityUtils;
import com.bfsm.dto.CartAddReq;
import com.bfsm.dto.CartUpdateReq;
import com.bfsm.entity.Product;
import com.bfsm.entity.ProductCategory;
import com.bfsm.entity.ShoppingCartItem;
import com.bfsm.mapper.ProductCategoryMapper;
import com.bfsm.mapper.ProductMapper;
import com.bfsm.mapper.ShoppingCartItemMapper;
import com.bfsm.vo.ShoppingCartItemVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShoppingCartService {
    private final ShoppingCartItemMapper shoppingCartItemMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper productCategoryMapper;

    public ShoppingCartService(ShoppingCartItemMapper shoppingCartItemMapper, ProductMapper productMapper,
                               ProductCategoryMapper productCategoryMapper) {
        this.shoppingCartItemMapper = shoppingCartItemMapper;
        this.productMapper = productMapper;
        this.productCategoryMapper = productCategoryMapper;
    }

    public List<ShoppingCartItemVo> currentUserCart() {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        List<ShoppingCartItem> items = shoppingCartItemMapper.selectList(new LambdaQueryWrapper<ShoppingCartItem>()
                .eq(ShoppingCartItem::getUserId, userId)
                .orderByDesc(ShoppingCartItem::getUpdatedAt)
                .orderByDesc(ShoppingCartItem::getId));
        if (items.isEmpty()) {
            return List.of();
        }

        Map<Long, Product> products = productMapper.selectBatchIds(items.stream()
                        .map(ShoppingCartItem::getProductId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        Map<Long, String> categoryNames = productCategoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>())
                .stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName));

        return items.stream().map(item -> toVo(item, products.get(item.getProductId()), categoryNames)).toList();
    }

    @Transactional
    public void addCurrentUserCart(CartAddReq req) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        Product product = productMapper.selectById(req.getProductId());
        validateProduct(product);
        if (req.getQuantity() > product.getStock()) {
            throw new BizException(4302, "加入购物车的数量超过当前库存");
        }

        ShoppingCartItem exists = shoppingCartItemMapper.selectOne(new LambdaQueryWrapper<ShoppingCartItem>()
                .eq(ShoppingCartItem::getUserId, userId)
                .eq(ShoppingCartItem::getProductId, req.getProductId())
                .last("limit 1"));
        if (exists == null) {
            ShoppingCartItem item = new ShoppingCartItem();
            item.setUserId(userId);
            item.setProductId(req.getProductId());
            item.setQuantity(req.getQuantity());
            item.setSelected(1);
            shoppingCartItemMapper.insert(item);
            return;
        }

        int quantity = exists.getQuantity() == null ? 0 : exists.getQuantity();
        int newQuantity = quantity + req.getQuantity();
        if (newQuantity > product.getStock()) {
            throw new BizException(4303, "购物车数量超过当前库存");
        }
        exists.setQuantity(newQuantity);
        exists.setSelected(1);
        shoppingCartItemMapper.updateById(exists);
    }

    @Transactional
    public void updateCurrentUserCart(Long id, CartUpdateReq req) {
        ShoppingCartItem item = currentUserItem(id);
        Product product = productMapper.selectById(item.getProductId());
        validateProduct(product);

        if (req.getQuantity() != null) {
            if (req.getQuantity() > product.getStock()) {
                throw new BizException(4304, "购物车数量超过当前库存");
            }
            item.setQuantity(req.getQuantity());
        }
        if (req.getSelected() != null) {
            item.setSelected(Boolean.TRUE.equals(req.getSelected()) ? 1 : 0);
        }
        shoppingCartItemMapper.updateById(item);
    }

    @Transactional
    public void removeCurrentUserCart(Long id) {
        ShoppingCartItem item = currentUserItem(id);
        shoppingCartItemMapper.deleteById(item.getId());
    }

    private ShoppingCartItem currentUserItem(Long id) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        ShoppingCartItem item = shoppingCartItemMapper.selectById(id);
        if (item == null || !userId.equals(item.getUserId())) {
            throw new BizException(404, "购物车记录不存在");
        }
        return item;
    }

    private void validateProduct(Product product) {
        if (product == null || !"ON".equals(product.getStatus())) {
            throw new BizException(4301, "商品不存在或已下架");
        }
    }

    private ShoppingCartItemVo toVo(ShoppingCartItem item, Product product, Map<Long, String> categoryNames) {
        if (product == null) {
            throw new BizException(404, "购物车中存在无效商品，请刷新后重试");
        }
        ShoppingCartItemVo vo = new ShoppingCartItemVo();
        vo.setId(item.getId());
        vo.setProductId(product.getId());
        vo.setProductName(product.getName());
        vo.setCategoryName(categoryNames.getOrDefault(product.getCategoryId(), "未分类"));
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());
        vo.setQuantity(item.getQuantity());
        vo.setSelected(item.getSelected() != null && item.getSelected() == 1);
        vo.setStatus(product.getStatus());
        vo.setAmount(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        return vo;
    }
}
