import request from '../utils/request';
import type {
  AdminComment,
  AdminSalesOverview,
  AutoSchedulePreview,
  AutoScheduleRequest,
  AiConsultSession,
  AiConsultTurn,
  AdminUser,
  Announcement,
  AttendanceRecord,
  AttendanceSummary,
  ApiResponse,
  AppointmentAvailableSlot,
  AppointmentDetail,
  AppointmentSummary,
  Banner,
  CategorySalesStats,
  ChatMessage,
  ChatSessionSummary,
  LeaveRecord,
  LeaveConflictPreview,
  OverviewStats,
  PageResult,
  PaymentCreateResult,
  PaymentCapability,
  PaymentStatus,
  ProductCategory,
  ProductComment,
  ProductDetail,
  ProductInteractionState,
  ProductOrderDetail,
  ProductOrderSubmitItem,
  ProductOrderSummary,
  ProductSalesRank,
  RechargeActivity,
  SalesTrendPoint,
  Schedule,
  ServiceCategory,
  ServiceItem,
  ServiceStats,
  ShoppingCartItem,
  SystemNotification,
  TechAttendanceChart,
  TechMonthStats,
  TechPerformancePoint,
  Technician,
  TechnicianRecommendation,
  TechnicianStats,
  TrendPoint,
  UserAddress,
  WalletSummary,
  WalletTransaction,
} from './types';

export const api = {
  serviceItems: () => request.get<ApiResponse<ServiceItem[]>>('/api/master/service-items'),
  categories: () => request.get<ApiResponse<ServiceCategory[]>>('/api/master/categories'),
  technicians: () => request.get<ApiResponse<Technician[]>>('/api/master/technicians'),
  banners: () => request.get<ApiResponse<Banner[]>>('/api/master/banners'),
  adminBanners: () => request.get<ApiResponse<Banner[]>>('/api/master/banners/manage'),
  saveBanner: (payload: Partial<Banner>) => request.post<ApiResponse<null>>('/api/master/banners', payload),
  updateBannerStatus: (id: number, status: string) => request.post<ApiResponse<null>>(`/api/master/banners/${id}/status`, { status }),
  deleteBanner: (id: number) => request.delete<ApiResponse<null>>(`/api/master/banners/${id}`),
  announcements: () => request.get<ApiResponse<Announcement[]>>('/api/master/announcements'),
  adminAnnouncements: () => request.get<ApiResponse<Announcement[]>>('/api/master/announcements/manage'),
  announcementDetail: (id: number) => request.get<ApiResponse<Announcement>>(`/api/master/announcements/${id}`),
  updateAnnouncementStatus: (id: number, status: string) => request.post<ApiResponse<null>>(`/api/master/announcements/${id}/status`, { status }),
  deleteAnnouncement: (id: number) => request.delete<ApiResponse<null>>(`/api/master/announcements/${id}`),
  schedules: () => request.get<ApiResponse<Schedule[]>>('/api/master/schedules'),
  mySchedules: (params: { startDate: string; endDate: string }) =>
    request.get<ApiResponse<Schedule[]>>('/api/master/schedules/my', { params }),
  leaveRecords: () => request.get<ApiResponse<LeaveRecord[]>>('/api/leave-records'),
  leaveRecordsPage: (params?: { pageNum?: number; pageSize?: number }) =>
    request.get<ApiResponse<PageResult<LeaveRecord>>>('/api/leave-records/page', { params }),
  attendanceToday: () => request.get<ApiResponse<AttendanceSummary>>('/api/attendance/today'),
  attendanceRecords: () => request.get<ApiResponse<AttendanceRecord[]>>('/api/attendance/records'),
  saveCategory: (payload: Partial<ServiceCategory>) => request.post<ApiResponse<null>>('/api/master/categories', payload),
  saveTechnician: (payload: Partial<Technician>) => request.post<ApiResponse<null>>('/api/master/technicians', payload),
  saveServiceItem: (payload: Partial<ServiceItem>) => request.post<ApiResponse<null>>('/api/master/service-items', payload),
  adminServiceItems: () => request.get<ApiResponse<ServiceItem[]>>('/api/master/service-items/manage'),
  technicianServices: (id: number) => request.get<ApiResponse<number[]>>(`/api/master/technicians/${id}/services`),
  saveTechnicianServices: (id: number, serviceItemIds: number[]) =>
    request.post<ApiResponse<null>>(`/api/master/technicians/${id}/services`, { serviceItemIds }),
  saveAnnouncement: (payload: Partial<Announcement>) => request.post<ApiResponse<null>>('/api/master/announcements', payload),
  saveSchedule: (payload: Partial<Schedule>) => request.post<ApiResponse<null>>('/api/master/schedules', payload),
  autoSchedulePreview: (payload: AutoScheduleRequest) =>
    request.post<ApiResponse<AutoSchedulePreview>>('/api/master/schedules/auto-preview', payload),
  autoScheduleApply: (payload: AutoScheduleRequest) =>
    request.post<ApiResponse<AutoSchedulePreview>>('/api/master/schedules/auto-apply', payload),
  deleteSchedule: (id: number) => request.delete<ApiResponse<null>>(`/api/master/schedules/${id}`),
  saveLeaveRecord: (payload: Partial<LeaveRecord>) => request.post<ApiResponse<LeaveRecord>>('/api/leave-records', payload),
  cancelLeaveRecord: (id: number) => request.post<ApiResponse<null>>(`/api/leave-records/${id}/cancel`),
  approveLeaveRecord: (id: number, remark = '') => request.post<ApiResponse<LeaveRecord>>(`/api/leave-records/${id}/approve`, { remark }),
  leaveConflicts: (id: number) => request.get<ApiResponse<LeaveConflictPreview>>(`/api/leave-records/${id}/conflicts`),
  resolveAndApproveLeaveRecord: (id: number, payload: { remark?: string; items: Array<{ appointmentId: number; action: 'REASSIGN' | 'CANCEL'; targetTechnicianId?: number | null }> }) =>
    request.post<ApiResponse<LeaveRecord>>(`/api/leave-records/${id}/resolve-and-approve`, payload),
  rejectLeaveRecord: (id: number, remark: string) => request.post<ApiResponse<LeaveRecord>>(`/api/leave-records/${id}/reject`, { remark }),
  checkInAttendance: () => request.post<ApiResponse<AttendanceSummary>>('/api/attendance/check-in'),
  checkOutAttendance: () => request.post<ApiResponse<AttendanceSummary>>('/api/attendance/check-out'),
  notifications: (params?: { pageNum?: number; pageSize?: number; readStatus?: 'UNREAD' | 'READ' | ''; keyword?: string }) =>
    request.get<ApiResponse<PageResult<SystemNotification>>>('/api/notifications', { params }),
  notificationUnreadCount: () => request.get<ApiResponse<number>>('/api/notifications/unread-count'),
  markNotificationRead: (id: number) => request.post<ApiResponse<null>>(`/api/notifications/${id}/read`),
  markAllNotificationsRead: () => request.post<ApiResponse<null>>('/api/notifications/read-all'),
  clearReadNotifications: () => request.post<ApiResponse<null>>('/api/notifications/clear-read'),
  aiConsultUpload: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return request.post<ApiResponse<string>>('/api/ai-consult/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },
  createAiConsultSession: (payload: { content?: string; imageUrl?: string }) =>
    request.post<ApiResponse<AiConsultTurn>>('/api/ai-consult/sessions', payload, { timeout: 60000 }),
  aiConsultSessions: (params?: { pageNum?: number; pageSize?: number }) =>
    request.get<ApiResponse<PageResult<AiConsultSession>>>('/api/ai-consult/sessions/page', { params }),
  aiConsultSessionDetail: (id: number) =>
    request.get<ApiResponse<AiConsultSession>>(`/api/ai-consult/sessions/${id}`),
  sendAiConsultSessionMessage: (id: number, payload: { content?: string; imageUrl?: string }) =>
    request.post<ApiResponse<AiConsultTurn>>(`/api/ai-consult/sessions/${id}/messages`, payload, { timeout: 60000 }),
  deleteAiConsultRecord: (id: number) => request.delete<ApiResponse<null>>(`/api/ai-consult/${id}`),

  appointments: () => request.get<ApiResponse<AppointmentSummary[]>>('/api/appointments'),
  appointmentsPage: (params?: { pageNum?: number; pageSize?: number }) =>
    request.get<ApiResponse<PageResult<AppointmentSummary>>>('/api/appointments/page', { params }),
  appointmentDetail: (id: number) => request.get<ApiResponse<AppointmentDetail>>(`/api/appointments/${id}`),
  createAppointment: (payload: { serviceItemId: number; technicianId?: number | null; appointmentTime: string }) =>
    request.post<ApiResponse<AppointmentDetail>>('/api/appointments', payload),
  payAppointment: (id: number, payMethod: 'BALANCE' | 'WECHAT' | 'ALIPAY') =>
    request.post<ApiResponse<AppointmentDetail>>(`/api/appointments/${id}/pay`, { payMethod }),
  appointmentAvailableSlots: (serviceItemId: number, appointmentDate: string, technicianId?: number | null) =>
    request.get<ApiResponse<AppointmentAvailableSlot[]>>('/api/appointments/available-slots', {
      params: {
        serviceItemId,
        appointmentDate,
        ...(technicianId ? { technicianId } : {}),
      },
    }),
  recommendTechnician: (serviceItemId: number, appointmentTime: string) =>
    request.get<ApiResponse<TechnicianRecommendation>>('/api/appointments/recommend-technician', {
      params: { serviceItemId, appointmentTime },
    }),
  autoAssign: (id: number) => request.post<ApiResponse<AppointmentDetail>>(`/api/appointments/${id}/auto-assign`),
  updateAppointmentStatus: (id: number, status: string, remark = '') =>
    request.post<ApiResponse<null>>(`/api/appointments/${id}/status`, { status, remark }),
  cancelAppointment: (id: number) => request.post<ApiResponse<null>>(`/api/appointments/${id}/cancel`),
  commentAppointment: (id: number, rating: number, content: string) =>
    request.post<ApiResponse<null>>(`/api/appointments/${id}/comment`, { rating, content }),

  overviewStats: () => request.get<ApiResponse<OverviewStats>>('/api/stats/overview'),
  trendStats: () => request.get<ApiResponse<TrendPoint[]>>('/api/stats/trend'),
  serviceStats: () => request.get<ApiResponse<ServiceStats[]>>('/api/stats/services'),
  technicianStats: () => request.get<ApiResponse<TechnicianStats[]>>('/api/stats/technicians'),
  techMonthStats: (month?: string) =>
    request.get<ApiResponse<TechMonthStats>>('/api/stats/tech-month', { params: month ? { month } : {} }),
  techPerformanceTrend: (month?: string) =>
    request.get<ApiResponse<TechPerformancePoint[]>>('/api/stats/tech-performance-trend', { params: month ? { month } : {} }),
  techAttendanceChart: (month?: string) =>
    request.get<ApiResponse<TechAttendanceChart>>('/api/stats/tech-attendance-chart', { params: month ? { month } : {} }),
  adminSalesOverview: (month?: string) =>
    request.get<ApiResponse<AdminSalesOverview>>('/api/stats/admin-sales-overview', { params: month ? { month } : {} }),
  adminSalesTrend: (month?: string) =>
    request.get<ApiResponse<SalesTrendPoint[]>>('/api/stats/admin-sales-trend', { params: month ? { month } : {} }),
  adminProductRanking: (month?: string) =>
    request.get<ApiResponse<ProductSalesRank[]>>('/api/stats/admin-product-ranking', { params: month ? { month } : {} }),
  adminCategoryStats: (month?: string) =>
    request.get<ApiResponse<CategorySalesStats[]>>('/api/stats/admin-category-stats', { params: month ? { month } : {} }),

  users: (params?: { keyword?: string; roleCode?: string; enabled?: boolean }) =>
    request.get<ApiResponse<AdminUser[]>>('/api/admin/users', { params }),
  usersPage: (params?: { pageNum?: number; pageSize?: number; keyword?: string; roleCode?: string; enabled?: boolean }) =>
    request.get<ApiResponse<PageResult<AdminUser>>>('/api/admin/users/page', { params }),
  createUser: (payload: { username: string; password: string; nickname?: string; phone?: string; roleCode: string }) =>
    request.post<ApiResponse<null>>('/api/admin/users', payload),
  updateUserStatus: (id: number, enabled: boolean) =>
    request.post<ApiResponse<null>>(`/api/admin/users/${id}/status`, { enabled }),
  updateUserRole: (id: number, roleCode: string) =>
    request.post<ApiResponse<null>>(`/api/admin/users/${id}/role`, { roleCode }),
  resetUserPassword: (id: number) => request.post<ApiResponse<null>>(`/api/admin/users/${id}/reset-password`),

  adminComments: (params?: { commentType?: string; keyword?: string; startDate?: string; endDate?: string }) =>
    request.get<ApiResponse<AdminComment[]>>('/api/interaction/comments', { params }),
  adminCommentsPage: (params?: { pageNum?: number; pageSize?: number; commentType?: string; keyword?: string; startDate?: string; endDate?: string }) =>
    request.get<ApiResponse<PageResult<AdminComment>>>('/api/interaction/comments/page', { params }),
  deleteAdminComment: (id: number) => request.delete<ApiResponse<null>>(`/api/interaction/comments/${id}`),

  productCategories: () => request.get<ApiResponse<ProductCategory[]>>('/api/products/categories'),
  adminProductCategories: (params?: { keyword?: string; status?: string }) =>
    request.get<ApiResponse<ProductCategory[]>>('/api/products/categories/manage', { params }),
  adminProductCategoriesPage: (params?: { pageNum?: number; pageSize?: number; keyword?: string; status?: string }) =>
    request.get<ApiResponse<PageResult<ProductCategory>>>('/api/products/categories/manage/page', { params }),
  saveProductCategory: (payload: Partial<ProductCategory>) => request.post<ApiResponse<null>>('/api/products/categories', payload),
  deleteProductCategory: (id: number) => request.delete<ApiResponse<null>>(`/api/products/categories/${id}`),
  products: (categoryId?: number) => request.get<ApiResponse<ProductDetail[]>>('/api/products', { params: categoryId ? { categoryId } : {} }),
  productsPage: (params?: { categoryId?: number; keyword?: string; status?: string; lowStock?: boolean; pageNum?: number; pageSize?: number }) =>
    request.get<ApiResponse<PageResult<ProductDetail>>>('/api/products/page', { params }),
  productDetail: (id: number) => request.get<ApiResponse<ProductDetail>>(`/api/products/${id}`),
  saveProduct: (payload: Partial<ProductDetail>) => request.post<ApiResponse<null>>('/api/products', payload),
  updateProductStatus: (id: number, status: string) => request.post<ApiResponse<null>>(`/api/products/${id}/status`, { status }),
  updateProductStatuses: (ids: number[], status: string) => request.post<ApiResponse<null>>('/api/products/batch-status', { ids, status }),
  deleteProduct: (id: number) => request.delete<ApiResponse<null>>(`/api/products/${id}`),

  cartItems: () => request.get<ApiResponse<ShoppingCartItem[]>>('/api/products/cart'),
  addCartItem: (payload: { productId: number; quantity: number }) =>
    request.post<ApiResponse<null>>('/api/products/cart', payload),
  updateCartItem: (id: number, payload: { quantity?: number; selected?: boolean }) =>
    request.post<ApiResponse<null>>(`/api/products/cart/${id}`, payload),
  deleteCartItem: (id: number) => request.delete<ApiResponse<null>>(`/api/products/cart/${id}`),

  userAddresses: () => request.get<ApiResponse<UserAddress[]>>('/api/products/addresses'),
  saveUserAddress: (payload: Partial<UserAddress>) => request.post<ApiResponse<null>>('/api/products/addresses', payload),
  setDefaultUserAddress: (id: number) => request.post<ApiResponse<null>>(`/api/products/addresses/${id}/default`),
  deleteUserAddress: (id: number) => request.delete<ApiResponse<null>>(`/api/products/addresses/${id}`),

  createProductOrder: (payload: { addressId: number; items?: ProductOrderSubmitItem[]; cartItemIds?: number[] }) =>
    request.post<ApiResponse<ProductOrderDetail>>('/api/products/order', payload),
  myProductOrders: () => request.get<ApiResponse<ProductOrderSummary[]>>('/api/products/orders/me'),
  myProductOrdersPage: (params?: { pageNum?: number; pageSize?: number }) =>
    request.get<ApiResponse<PageResult<ProductOrderSummary>>>('/api/products/orders/me/page', { params }),
  productOrderDetail: (id: number) => request.get<ApiResponse<ProductOrderDetail>>(`/api/products/orders/${id}`),
  cancelProductOrder: (id: number) => request.post<ApiResponse<null>>(`/api/products/orders/${id}/cancel`),
  payProductOrder: (id: number, payMethod: 'BALANCE' | 'WECHAT' | 'ALIPAY') =>
    request.post<ApiResponse<ProductOrderDetail>>(`/api/products/orders/${id}/pay`, { payMethod }),
  confirmProductOrderReceipt: (id: number) =>
    request.post<ApiResponse<ProductOrderDetail>>(`/api/products/orders/${id}/confirm-receipt`),
  applyProductAfterSale: (id: number, reason: string) =>
    request.post<ApiResponse<null>>(`/api/products/orders/${id}/after-sale`, { reason }),
  allProductOrders: () => request.get<ApiResponse<ProductOrderSummary[]>>('/api/products/orders'),
  allProductOrdersPage: (params?: { pageNum?: number; pageSize?: number }) =>
    request.get<ApiResponse<PageResult<ProductOrderSummary>>>('/api/products/orders/page', { params }),
  updateProductOrderStatus: (id: number, status: string) =>
    request.post<ApiResponse<null>>(`/api/products/orders/${id}/status`, { status }),
  shipProductOrder: (id: number, payload: { shippingCompany: string; trackingNo: string }) =>
    request.post<ApiResponse<null>>(`/api/products/orders/${id}/ship`, payload),
  reviewProductAfterSale: (id: number, payload: { approved: boolean; remark?: string }) =>
    request.post<ApiResponse<null>>(`/api/products/orders/${id}/after-sale/review`, payload),

  favoriteProductIds: () => request.get<ApiResponse<number[]>>('/api/interaction/products/favorites'),
  toggleProductFavorite: (productId: number) =>
    request.post<ApiResponse<ProductInteractionState>>(`/api/interaction/products/${productId}/favorite`),
  favoriteServiceItemIds: () => request.get<ApiResponse<number[]>>('/api/interaction/services/favorites'),
  toggleServiceFavorite: (serviceItemId: number) =>
    request.post<ApiResponse<ProductInteractionState>>(`/api/interaction/services/${serviceItemId}/favorite`),
  favoriteAnnouncementIds: () => request.get<ApiResponse<number[]>>('/api/interaction/announcements/favorites'),
  toggleAnnouncementFavorite: (announcementId: number) =>
    request.post<ApiResponse<ProductInteractionState>>(`/api/interaction/announcements/${announcementId}/favorite`),
  productComments: (productId: number) =>
    request.get<ApiResponse<ProductComment[]>>(`/api/interaction/products/${productId}/comments`),
  submitProductComment: (productId: number, payload: { rating: number; content: string }) =>
    request.post<ApiResponse<null>>(`/api/interaction/products/${productId}/comments`, payload),

  walletSummary: () => request.get<ApiResponse<WalletSummary>>('/api/wallet/summary'),
  walletTransactions: () => request.get<ApiResponse<WalletTransaction[]>>('/api/wallet/transactions'),
  walletTransactionsPage: (params?: { pageNum?: number; pageSize?: number }) =>
    request.get<ApiResponse<PageResult<WalletTransaction>>>('/api/wallet/transactions/page', { params }),
  walletActivities: () => request.get<ApiResponse<RechargeActivity[]>>('/api/wallet/activities'),
  rechargeWallet: (payload: { amount: number; payMethod: 'WECHAT' | 'ALIPAY'; activityId?: number | null }) =>
    request.post<ApiResponse<WalletSummary>>('/api/wallet/recharge', payload),
  adminRechargeActivities: () => request.get<ApiResponse<RechargeActivity[]>>('/api/wallet/activities/all'),
  saveRechargeActivity: (payload: Partial<RechargeActivity>) =>
    request.post<ApiResponse<null>>('/api/wallet/activities', payload),

  createProductOrderPayment: (orderId: number, payChannel: 'WECHAT' | 'ALIPAY') =>
    request.post<ApiResponse<PaymentCreateResult>>(`/api/payments/product-orders/${orderId}`, { payChannel }),
  createAppointmentPayment: (appointmentId: number, payChannel: 'WECHAT' | 'ALIPAY') =>
    request.post<ApiResponse<PaymentCreateResult>>(`/api/payments/appointments/${appointmentId}`, { payChannel }),
  createWalletRechargePayment: (payload: { amount: number; payMethod: 'WECHAT' | 'ALIPAY'; activityId?: number | null }) =>
    request.post<ApiResponse<PaymentCreateResult>>('/api/payments/wallet-recharges', payload),
  paymentCapabilities: () => request.get<ApiResponse<PaymentCapability>>('/api/payments/capabilities'),
  paymentStatus: (paymentNo: string) => request.get<ApiResponse<PaymentStatus>>(`/api/payments/${paymentNo}`),
  completePayment: (paymentNo: string) =>
    request.post<ApiResponse<PaymentStatus>>(`/api/payments/${paymentNo}/complete`),
  cancelPayment: (paymentNo: string) => request.post<ApiResponse<PaymentStatus>>(`/api/payments/${paymentNo}/cancel`),

  chatSessions: () => request.get<ApiResponse<ChatSessionSummary[]>>('/api/chat/sessions'),
  chatSessionsPage: (params?: { pageNum?: number; pageSize?: number }) =>
    request.get<ApiResponse<PageResult<ChatSessionSummary>>>('/api/chat/sessions/page', { params }),
  chatMessages: (sessionId: number) => request.get<ApiResponse<ChatMessage[]>>(`/api/chat/session/${sessionId}/messages`),
  chatMessagesPage: (sessionId: number, params?: { pageNum?: number; pageSize?: number }) =>
    request.get<ApiResponse<PageResult<ChatMessage>>>(`/api/chat/session/${sessionId}/messages/page`, { params }),
  sendChat: (receiverId: number, content: string) =>
    request.post<ApiResponse<null>>('/api/chat/send', { receiverId, content }),
};
