package com.bfsm.service;

import com.bfsm.service.ai.AiBeautyAnalysis;
import com.bfsm.service.ai.AiProductCatalogItem;
import com.bfsm.service.ai.AiServiceCatalogItem;
import com.bfsm.vo.AiConsultProductRecommendationVo;
import com.bfsm.vo.AiConsultServiceRecommendationVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiConsultServiceTest {
    private AiConsultService aiConsultService;

    @BeforeEach
    void setUp() {
        aiConsultService = new AiConsultService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper()
        );
    }

    @Test
    void serviceId_shouldOnlyResolveFromServiceCatalog() {
        AiBeautyAnalysis.ServiceRecommendation recommendation = new AiBeautyAnalysis.ServiceRecommendation();
        recommendation.setServiceItemId(3L);
        recommendation.setReason("适合放松");
        recommendation.setFitScore(91);

        List<AiConsultServiceRecommendationVo> services = mergeServices(
                List.of(recommendation),
                List.of(service(3L, "肩颈放松理疗"))
        );

        assertEquals(1, services.size());
        assertEquals(3L, services.get(0).getServiceItemId());
        assertEquals("肩颈放松理疗", services.get(0).getServiceName());
    }

    @Test
    void productId_shouldOnlyResolveFromProductCatalog() {
        AiBeautyAnalysis.ProductRecommendation recommendation = new AiBeautyAnalysis.ProductRecommendation();
        recommendation.setProductId(3L);
        recommendation.setReason("适合居家护理");
        recommendation.setFitScore(88);

        List<AiConsultProductRecommendationVo> products = mergeProducts(
                List.of(recommendation),
                List.of(product(3L, "玻尿酸保湿面膜"))
        );

        assertEquals(1, products.size());
        assertEquals(3L, products.get(0).getProductId());
        assertEquals("玻尿酸保湿面膜", products.get(0).getProductName());
    }

    @Test
    void wrongCatalogId_shouldBeDiscarded() {
        AiBeautyAnalysis.ProductRecommendation productRecommendation = new AiBeautyAnalysis.ProductRecommendation();
        productRecommendation.setProductId(7L);
        AiBeautyAnalysis.ServiceRecommendation serviceRecommendation = new AiBeautyAnalysis.ServiceRecommendation();
        serviceRecommendation.setServiceItemId(8L);

        assertTrue(mergeProducts(List.of(productRecommendation), List.of(product(3L, "玻尿酸保湿面膜"))).isEmpty());
        assertTrue(mergeServices(List.of(serviceRecommendation), List.of(service(4L, "补水清洁护理"))).isEmpty());
    }

    @Test
    void beautyGoal_shouldTriggerBothServiceAndProductIntent() {
        Object intent = detectIntent("我想要美白");

        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeGetterMethod(intent, "serviceIntent"));
        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeGetterMethod(intent, "productIntent"));
        assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeGetterMethod(intent, "generalOnly"));
    }

    @Test
    void hydratingGoal_shouldRecommendBothServicesAndProducts() {
        List<AiServiceCatalogItem> services = List.of(
                service(1L, "补水清洁护理"),
                service(2L, "肩颈放松理疗")
        );
        List<AiProductCatalogItem> products = List.of(
                product(11L, "玻尿酸保湿面膜"),
                product(12L, "肩颈热敷贴")
        );

        assertFalse(recommendServices("我想补水保湿", services).isEmpty());
        assertFalse(recommendProducts("我想补水保湿", products).isEmpty());
    }

    @Test
    void relaxingGoal_shouldRecommendBothServicesAndProducts() {
        List<AiServiceCatalogItem> services = List.of(
                service(1L, "补水清洁护理"),
                service(2L, "肩颈放松理疗"),
                service(3L, "肩背经络舒缓")
        );
        List<AiProductCatalogItem> products = List.of(
                product(11L, "玻尿酸保湿面膜"),
                product(12L, "肩颈热敷贴")
        );

        assertFalse(recommendServices("我想放松", services).isEmpty());
        assertFalse(recommendProducts("我想放松", products).isEmpty());
    }

    @Test
    void productOnlyIntent_shouldNotForceServiceIntent() {
        Object intent = detectIntent("日常护理应该买什么");

        assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeGetterMethod(intent, "serviceIntent"));
        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeGetterMethod(intent, "productIntent"));
    }

    @Test
    void serviceOnlyIntent_shouldNotForceProductIntent() {
        Object intent = detectIntent("我想预约一个服务");

        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeGetterMethod(intent, "serviceIntent"));
        assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeGetterMethod(intent, "productIntent"));
    }

    @Test
    void generalChat_shouldNotTriggerRecommendationIntent() {
        Object intent = detectIntent("你好，你是谁");

        assertEquals(Boolean.TRUE, ReflectionTestUtils.invokeGetterMethod(intent, "generalOnly"));
        assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeGetterMethod(intent, "serviceIntent"));
        assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeGetterMethod(intent, "productIntent"));
    }

    private Object detectIntent(String question) {
        return ReflectionTestUtils.invokeMethod(aiConsultService, "detectIntent", question);
    }

    private List<AiConsultServiceRecommendationVo> mergeServices(List<AiBeautyAnalysis.ServiceRecommendation> source,
                                                                 List<AiServiceCatalogItem> catalog) {
        Map<Long, AiServiceCatalogItem> map = catalog.stream()
                .collect(Collectors.toMap(AiServiceCatalogItem::getServiceItemId, Function.identity()));
        return ReflectionTestUtils.invokeMethod(aiConsultService, "mergeServiceRecommendations", source, map);
    }

    private List<AiConsultProductRecommendationVo> mergeProducts(List<AiBeautyAnalysis.ProductRecommendation> source,
                                                                List<AiProductCatalogItem> catalog) {
        Map<Long, AiProductCatalogItem> map = catalog.stream()
                .collect(Collectors.toMap(AiProductCatalogItem::getProductId, Function.identity()));
        return ReflectionTestUtils.invokeMethod(aiConsultService, "mergeProductRecommendations", source, map);
    }

    private List<AiConsultServiceRecommendationVo> recommendServices(String question, List<AiServiceCatalogItem> catalog) {
        return ReflectionTestUtils.invokeMethod(aiConsultService, "recommendServices", question, catalog);
    }

    private List<AiConsultProductRecommendationVo> recommendProducts(String question, List<AiProductCatalogItem> catalog) {
        return ReflectionTestUtils.invokeMethod(aiConsultService, "recommendProducts", question, catalog);
    }

    private AiServiceCatalogItem service(Long id, String name) {
        AiServiceCatalogItem item = new AiServiceCatalogItem();
        item.setServiceItemId(id);
        item.setServiceName(name);
        item.setCategoryName("门店项目");
        item.setDurationMin(60);
        item.setPrice(BigDecimal.valueOf(199));
        return item;
    }

    private AiProductCatalogItem product(Long id, String name) {
        AiProductCatalogItem item = new AiProductCatalogItem();
        item.setProductId(id);
        item.setProductName(name);
        item.setCategoryName("护理商品");
        item.setPrice(BigDecimal.valueOf(99));
        item.setStock(10);
        return item;
    }
}
