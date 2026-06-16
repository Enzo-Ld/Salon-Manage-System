package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.common.BizException;
import com.bfsm.common.PageUtils;
import com.bfsm.common.SecurityUtils;
import com.bfsm.dto.ProductCommentReq;
import com.bfsm.entity.Announcement;
import com.bfsm.entity.Appointment;
import com.bfsm.entity.CommentEntity;
import com.bfsm.entity.Favorite;
import com.bfsm.entity.Product;
import com.bfsm.entity.ProductOrder;
import com.bfsm.entity.ProductOrderItem;
import com.bfsm.entity.ServiceItem;
import com.bfsm.entity.SysUser;
import com.bfsm.mapper.AnnouncementMapper;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.CommentMapper;
import com.bfsm.mapper.FavoriteMapper;
import com.bfsm.mapper.ProductMapper;
import com.bfsm.mapper.ProductOrderItemMapper;
import com.bfsm.mapper.ProductOrderMapper;
import com.bfsm.mapper.ServiceItemMapper;
import com.bfsm.mapper.SysUserMapper;
import com.bfsm.vo.AdminCommentVo;
import com.bfsm.vo.PageResultVo;
import com.bfsm.vo.ProductCommentVo;
import com.bfsm.vo.ProductInteractionVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InteractionService {
    private final FavoriteMapper favoriteMapper;
    private final CommentMapper commentMapper;
    private final ProductMapper productMapper;
    private final ProductOrderMapper productOrderMapper;
    private final ProductOrderItemMapper productOrderItemMapper;
    private final SysUserMapper sysUserMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final AnnouncementMapper announcementMapper;
    private final AppointmentMapper appointmentMapper;
    private final TechnicianRatingService technicianRatingService;

    public InteractionService(FavoriteMapper favoriteMapper, CommentMapper commentMapper,
                              ProductMapper productMapper, ProductOrderMapper productOrderMapper,
                              ProductOrderItemMapper productOrderItemMapper, SysUserMapper sysUserMapper,
                              ServiceItemMapper serviceItemMapper, AnnouncementMapper announcementMapper,
                              AppointmentMapper appointmentMapper, TechnicianRatingService technicianRatingService) {
        this.favoriteMapper = favoriteMapper;
        this.commentMapper = commentMapper;
        this.productMapper = productMapper;
        this.productOrderMapper = productOrderMapper;
        this.productOrderItemMapper = productOrderItemMapper;
        this.sysUserMapper = sysUserMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.announcementMapper = announcementMapper;
        this.appointmentMapper = appointmentMapper;
        this.technicianRatingService = technicianRatingService;
    }

    public List<Long> favoriteProductIds() {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        return favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .isNotNull(Favorite::getProductId))
                .stream()
                .map(Favorite::getProductId)
                .distinct()
                .toList();
    }

    public List<Long> favoriteServiceItemIds() {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        return favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .isNotNull(Favorite::getServiceItemId))
                .stream()
                .map(Favorite::getServiceItemId)
                .distinct()
                .toList();
    }

    public List<Long> favoriteAnnouncementIds() {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        return favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .isNotNull(Favorite::getAnnouncementId))
                .stream()
                .map(Favorite::getAnnouncementId)
                .distinct()
                .toList();
    }

    @Transactional
    public ProductInteractionVo toggleProductFavorite(Long productId) {
        ensureProduct(productId);
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        Favorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId)
                .last("limit 1"));
        boolean active;
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            active = false;
        } else {
            Favorite favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setProductId(productId);
            favoriteMapper.insert(favorite);
            active = true;
        }
        return buildInteractionResult(active, Math.toIntExact(favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getProductId, productId))));
    }

    @Transactional
    public ProductInteractionVo toggleServiceItemFavorite(Long serviceItemId) {
        ServiceItem serviceItem = serviceItemMapper.selectById(serviceItemId);
        if (serviceItem == null) {
            throw new BizException(404, "Service item not found");
        }
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        Favorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getServiceItemId, serviceItemId)
                .last("limit 1"));
        boolean active;
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            active = false;
        } else {
            Favorite favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setServiceItemId(serviceItemId);
            favoriteMapper.insert(favorite);
            active = true;
        }
        return buildInteractionResult(active, Math.toIntExact(favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getServiceItemId, serviceItemId))));
    }

    @Transactional
    public ProductInteractionVo toggleAnnouncementFavorite(Long announcementId) {
        Announcement announcement = announcementMapper.selectById(announcementId);
        if (announcement == null) {
            throw new BizException(404, "Announcement not found");
        }
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        Favorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getAnnouncementId, announcementId)
                .last("limit 1"));
        boolean active;
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            active = false;
        } else {
            Favorite favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setAnnouncementId(announcementId);
            favoriteMapper.insert(favorite);
            active = true;
        }
        return buildInteractionResult(active, Math.toIntExact(favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getAnnouncementId, announcementId))));
    }

    public List<ProductCommentVo> listProductComments(Long productId) {
        ensureProduct(productId);
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        List<CommentEntity> comments = commentMapper.selectList(new LambdaQueryWrapper<CommentEntity>()
                .eq(CommentEntity::getProductId, productId)
                .orderByDesc(CommentEntity::getCreatedAt));
        if (comments.isEmpty()) {
            return List.of();
        }

        Map<Long, SysUser> users = sysUserMapper.selectBatchIds(comments.stream()
                        .map(CommentEntity::getUserId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        return comments.stream().map(comment -> {
            ProductCommentVo vo = new ProductCommentVo();
            SysUser author = users.get(comment.getUserId());
            vo.setId(comment.getId());
            vo.setUserId(comment.getUserId());
            vo.setUsername(author == null ? "-" : displayName(author));
            vo.setRating(comment.getRating());
            vo.setContent(comment.getContent());
            vo.setStatus(comment.getStatus());
            vo.setCreatedAt(comment.getCreatedAt());
            vo.setMine(userId.equals(comment.getUserId()));
            return vo;
        }).toList();
    }

    @Transactional
    public void submitProductComment(Long productId, ProductCommentReq req) {
        ensureProduct(productId);
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        ensureCommentPermission(userId, productId);

        CommentEntity existing = commentMapper.selectOne(new LambdaQueryWrapper<CommentEntity>()
                .eq(CommentEntity::getUserId, userId)
                .eq(CommentEntity::getProductId, productId)
                .isNull(CommentEntity::getAppointmentId)
                .last("limit 1"));
        if (existing == null) {
            CommentEntity comment = new CommentEntity();
            comment.setUserId(userId);
            comment.setProductId(productId);
            comment.setAppointmentId(null);
            comment.setRating(req.getRating());
            comment.setContent(req.getContent().trim());
            comment.setStatus("APPROVED");
            commentMapper.insert(comment);
            return;
        }

        existing.setRating(req.getRating());
        existing.setContent(req.getContent().trim());
        existing.setStatus("APPROVED");
        commentMapper.updateById(existing);
    }

    public List<AdminCommentVo> listAdminComments(String commentType, String keyword, String startDate, String endDate) {
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        List<CommentEntity> comments = commentMapper.selectList(new LambdaQueryWrapper<CommentEntity>()
                .orderByDesc(CommentEntity::getCreatedAt));
        if (comments.isEmpty()) {
            return List.of();
        }

        Map<Long, SysUser> users = sysUserMapper.selectBatchIds(
                        comments.stream().map(CommentEntity::getUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, Product> products = productMapper.selectBatchIds(
                        comments.stream().map(CommentEntity::getProductId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Appointment> appointments = appointmentMapper.selectBatchIds(
                        comments.stream().map(CommentEntity::getAppointmentId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Appointment::getId, Function.identity()));
        Map<Long, ServiceItem> serviceItems = serviceItemMapper.selectBatchIds(
                        appointments.values().stream().map(Appointment::getServiceItemId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(ServiceItem::getId, Function.identity()));

        return comments.stream()
                .map(comment -> toAdminCommentVo(comment, users, products, appointments, serviceItems))
                .filter(item -> commentType == null || commentType.isBlank() || commentType.equals(item.getCommentType()))
                .filter(item -> keyword == null || keyword.isBlank()
                        || item.getUsername().contains(keyword)
                        || item.getContent().contains(keyword)
                        || item.getTargetTitle().contains(keyword))
                .filter(item -> start == null || !item.getCreatedAt().toLocalDate().isBefore(start))
                .filter(item -> end == null || !item.getCreatedAt().toLocalDate().isAfter(end))
                .toList();
    }

    public PageResultVo<AdminCommentVo> pageAdminComments(Long pageNum, Long pageSize, String commentType,
                                                          String keyword, String startDate, String endDate) {
        return PageUtils.paginate(listAdminComments(commentType, keyword, startDate, endDate), pageNum, pageSize);
    }

    @Transactional
    public void deleteAdminComment(Long id) {
        CommentEntity comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BizException(404, "评论不存在");
        }
        commentMapper.deleteById(id);
        if (comment.getAppointmentId() != null) {
            Appointment appointment = appointmentMapper.selectById(comment.getAppointmentId());
            if (appointment != null) {
                appointment.setCommentText(null);
                appointmentMapper.updateById(appointment);
                technicianRatingService.refreshRating(appointment.getTechnicianId());
            }
        }
    }

    private void ensureCommentPermission(Long userId, Long productId) {
        List<ProductOrderItem> orderItems = productOrderItemMapper.selectList(new LambdaQueryWrapper<ProductOrderItem>()
                .eq(ProductOrderItem::getProductId, productId));
        if (orderItems.isEmpty()) {
            throw new BizException(4105, "No commentable order found for this product");
        }

        Set<Long> orderIds = orderItems.stream().map(ProductOrderItem::getOrderId).collect(Collectors.toSet());
        List<ProductOrder> orders = productOrderMapper.selectList(new LambdaQueryWrapper<ProductOrder>()
                .eq(ProductOrder::getUserId, userId)
                .in(ProductOrder::getId, orderIds)
                .eq(ProductOrder::getStatus, "COMPLETED"));
        if (orders.isEmpty()) {
            throw new BizException(4105, "Product comments are available after order completion");
        }
    }

    private Product ensureProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(404, "Product not found");
        }
        return product;
    }

    private String displayName(SysUser user) {
        return user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getUsername();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    private ProductInteractionVo buildInteractionResult(boolean active, int count) {
        ProductInteractionVo vo = new ProductInteractionVo();
        vo.setActive(active);
        vo.setCount(count);
        return vo;
    }

    private AdminCommentVo toAdminCommentVo(CommentEntity comment, Map<Long, SysUser> users, Map<Long, Product> products,
                                            Map<Long, Appointment> appointments, Map<Long, ServiceItem> serviceItems) {
        AdminCommentVo vo = new AdminCommentVo();
        SysUser user = users.get(comment.getUserId());
        vo.setId(comment.getId());
        vo.setUserId(comment.getUserId());
        vo.setUsername(user == null ? "-" : displayName(user));
        vo.setRating(comment.getRating());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt());
        if (comment.getProductId() != null) {
            Product product = products.get(comment.getProductId());
            vo.setCommentType("PRODUCT");
            vo.setCommentTypeLabel("商品评论");
            vo.setTargetTitle(product == null ? "商品" : product.getName());
        } else {
            Appointment appointment = appointments.get(comment.getAppointmentId());
            ServiceItem serviceItem = appointment == null ? null : serviceItems.get(appointment.getServiceItemId());
            vo.setCommentType("APPOINTMENT");
            vo.setCommentTypeLabel("预约评价");
            vo.setTargetTitle(serviceItem == null ? "预约服务" : serviceItem.getName());
        }
        return vo;
    }
}
