package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.dto.ProductCommentReq;
import com.bfsm.service.InteractionService;
import com.bfsm.vo.AdminCommentVo;
import com.bfsm.vo.PageResultVo;
import com.bfsm.vo.ProductCommentVo;
import com.bfsm.vo.ProductInteractionVo;
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
@RequestMapping("/api/interaction")
public class InteractionController {
    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @GetMapping("/products/favorites")
    public ApiResponse<List<Long>> favoriteProductIds() {
        return ApiResponse.ok(interactionService.favoriteProductIds());
    }

    @PostMapping("/products/{productId}/favorite")
    public ApiResponse<ProductInteractionVo> toggleProductFavorite(@PathVariable Long productId) {
        return ApiResponse.ok(interactionService.toggleProductFavorite(productId));
    }

    @GetMapping("/services/favorites")
    public ApiResponse<List<Long>> favoriteServiceItemIds() {
        return ApiResponse.ok(interactionService.favoriteServiceItemIds());
    }

    @PostMapping("/services/{serviceItemId}/favorite")
    public ApiResponse<ProductInteractionVo> toggleServiceItemFavorite(@PathVariable Long serviceItemId) {
        return ApiResponse.ok(interactionService.toggleServiceItemFavorite(serviceItemId));
    }

    @GetMapping("/announcements/favorites")
    public ApiResponse<List<Long>> favoriteAnnouncementIds() {
        return ApiResponse.ok(interactionService.favoriteAnnouncementIds());
    }

    @PostMapping("/announcements/{announcementId}/favorite")
    public ApiResponse<ProductInteractionVo> toggleAnnouncementFavorite(@PathVariable Long announcementId) {
        return ApiResponse.ok(interactionService.toggleAnnouncementFavorite(announcementId));
    }

    @GetMapping("/products/{productId}/comments")
    public ApiResponse<List<ProductCommentVo>> productComments(@PathVariable Long productId) {
        return ApiResponse.ok(interactionService.listProductComments(productId));
    }

    @PostMapping("/products/{productId}/comments")
    public ApiResponse<Void> submitProductComment(@PathVariable Long productId, @Valid @RequestBody ProductCommentReq req) {
        interactionService.submitProductComment(productId, req);
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/comments")
    public ApiResponse<List<AdminCommentVo>> comments(@RequestParam(required = false) String commentType,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String startDate,
                                                      @RequestParam(required = false) String endDate) {
        return ApiResponse.ok(interactionService.listAdminComments(commentType, keyword, startDate, endDate));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/comments/page")
    public ApiResponse<PageResultVo<AdminCommentVo>> commentsPage(@RequestParam(required = false) Long pageNum,
                                                                  @RequestParam(required = false) Long pageSize,
                                                                  @RequestParam(required = false) String commentType,
                                                                  @RequestParam(required = false) String keyword,
                                                                  @RequestParam(required = false) String startDate,
                                                                  @RequestParam(required = false) String endDate) {
        return ApiResponse.ok(interactionService.pageAdminComments(pageNum, pageSize, commentType, keyword, startDate, endDate));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable Long id) {
        interactionService.deleteAdminComment(id);
        return ApiResponse.ok();
    }
}
