package com.bfsm.service;

import com.bfsm.cache.CacheService;
import com.bfsm.entity.Product;
import com.bfsm.entity.ProductCategory;
import com.bfsm.mapper.CommentMapper;
import com.bfsm.mapper.FavoriteMapper;
import com.bfsm.mapper.ProductCategoryMapper;
import com.bfsm.mapper.ProductMapper;
import com.bfsm.mapper.ProductOrderItemMapper;
import com.bfsm.mapper.ProductOrderMapper;
import com.bfsm.security.SecurityUser;
import com.bfsm.vo.ProductDetailVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductCategoryMapper productCategoryMapper;
    @Mock
    private CacheService cacheService;
    @Mock
    private FavoriteMapper favoriteMapper;
    @Mock
    private CommentMapper commentMapper;
    @Mock
    private MallConsultantService mallConsultantService;
    @Mock
    private ProductOrderMapper productOrderMapper;
    @Mock
    private ProductOrderItemMapper productOrderItemMapper;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                productMapper,
                productCategoryMapper,
                cacheService,
                favoriteMapper,
                commentMapper,
                mallConsultantService,
                productOrderMapper,
                productOrderItemMapper
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_shouldReturnCategoryNameForProducts() {
        setAuthentication(1L, "admin", "ADMIN");
        ProductCategory category = new ProductCategory();
        category.setId(3L);
        category.setName("Skincare");

        Product product = new Product();
        product.setId(5L);
        product.setCategoryId(3L);
        product.setName("Mask");
        product.setPrice(BigDecimal.valueOf(59));
        product.setStock(20);
        product.setStatus("ON");

        when(productCategoryMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(category));
        when(productMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(product));

        ProductDetailVo detail = productService.list(null).get(0);

        assertEquals("Skincare", detail.getCategoryName());
        assertEquals("上架中", detail.getStatusLabel());
    }

    private void setAuthentication(Long userId, String username, String roleCode) {
        SecurityUser principal = new SecurityUser(userId, username, "", roleCode, true);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
