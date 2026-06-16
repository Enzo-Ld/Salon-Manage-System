package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.common.BizException;
import com.bfsm.common.PageUtils;
import com.bfsm.common.SecurityUtils;
import com.bfsm.config.AiConsultProperties;
import com.bfsm.dto.AiConsultMessageReq;
import com.bfsm.dto.AiConsultSessionReq;
import com.bfsm.entity.AiConsultMessage;
import com.bfsm.entity.AiConsultRecord;
import com.bfsm.entity.Product;
import com.bfsm.entity.ProductCategory;
import com.bfsm.entity.ServiceCategory;
import com.bfsm.entity.ServiceItem;
import com.bfsm.mapper.AiConsultMessageMapper;
import com.bfsm.mapper.AiConsultRecordMapper;
import com.bfsm.mapper.ProductCategoryMapper;
import com.bfsm.mapper.ProductMapper;
import com.bfsm.mapper.ServiceCategoryMapper;
import com.bfsm.mapper.ServiceItemMapper;
import com.bfsm.service.ai.AiBeautyAnalysis;
import com.bfsm.service.ai.AiConsultContext;
import com.bfsm.service.ai.AiProductCatalogItem;
import com.bfsm.service.ai.AiServiceCatalogItem;
import com.bfsm.service.ai.MockBeautyAdvisorClient;
import com.bfsm.service.ai.OpenAiCompatibleBeautyAdvisorClient;
import com.bfsm.vo.AiConsultHairStyleVo;
import com.bfsm.vo.AiConsultMessageVo;
import com.bfsm.vo.AiConsultProductRecommendationVo;
import com.bfsm.vo.AiConsultRecordVo;
import com.bfsm.vo.AiConsultServiceRecommendationVo;
import com.bfsm.vo.AiConsultSessionVo;
import com.bfsm.vo.AiConsultTurnVo;
import com.bfsm.vo.AiRecommendationSnapshotVo;
import com.bfsm.vo.AiStyleDirectionVo;
import com.bfsm.vo.PageResultVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiConsultService {
    private static final String AI_UPLOAD_PREFIX = "/uploads/ai-consult/";
    private static final String DEFAULT_DISCLAIMER = "AI 顾问建议仅供参考，实际服务方案和商品使用请结合到店沟通、个人肤质发质与过敏情况综合判断。";
    private static final String DEFAULT_TITLE = "AI 顾问咨询";

    private final FileStorageService fileStorageService;
    private final AiConsultRecordMapper aiConsultRecordMapper;
    private final AiConsultMessageMapper aiConsultMessageMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final ServiceCategoryMapper serviceCategoryMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final OpenAiCompatibleBeautyAdvisorClient remoteClient;
    private final AiConsultProperties aiConsultProperties;
    private final ObjectMapper objectMapper;

    public AiConsultService(FileStorageService fileStorageService,
                            AiConsultRecordMapper aiConsultRecordMapper,
                            AiConsultMessageMapper aiConsultMessageMapper,
                            ServiceItemMapper serviceItemMapper,
                            ServiceCategoryMapper serviceCategoryMapper,
                            ProductMapper productMapper,
                            ProductCategoryMapper productCategoryMapper,
                            OpenAiCompatibleBeautyAdvisorClient remoteClient,
                            MockBeautyAdvisorClient ignoredMockClient,
                            AiConsultProperties aiConsultProperties,
                            ObjectMapper objectMapper) {
        this.fileStorageService = fileStorageService;
        this.aiConsultRecordMapper = aiConsultRecordMapper;
        this.aiConsultMessageMapper = aiConsultMessageMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.serviceCategoryMapper = serviceCategoryMapper;
        this.productMapper = productMapper;
        this.productCategoryMapper = productCategoryMapper;
        this.remoteClient = remoteClient;
        this.aiConsultProperties = aiConsultProperties;
        this.objectMapper = objectMapper;
    }

    public String uploadImage(MultipartFile file) {
        return fileStorageService.storeAiConsultImage(file);
    }

    @Transactional
    public AiConsultTurnVo createSession(AiConsultSessionReq req) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        String content = req == null ? "" : trim(req.getContent());
        String imageUrl = req == null ? "" : normalizeOptionalImageUrl(req.getImageUrl());
        if (!StringUtils.hasText(content) && !StringUtils.hasText(imageUrl)) {
            throw new BizException(4509, "请输入咨询内容，或先上传图片后再发送。");
        }
        if (!StringUtils.hasText(content)) {
            content = "请结合这张图片，给我推荐适合的服务项目和护理商品。";
        }

        Catalog catalog = loadCatalog();
        TurnResult result = buildTurn(content, imageUrl, null, catalog);

        AiConsultRecord record = new AiConsultRecord();
        record.setUserId(userId);
        record.setTitle(buildSessionTitle(content));
        record.setStage(result.stage());
        record.setImageUrl(imageUrl);
        record.setInputText(content);
        record.setSummaryText(result.summaryText());
        record.setFaceSummary(result.observation());
        record.setHairstyleJson(writeJson(result.hairStyles()));
        record.setServiceJson(writeJson(result.services()));
        record.setProductJson(writeJson(result.products()));
        record.setCareTipsJson(writeJson(result.careTips()));
        record.setIntentTagsJson(writeJson(result.intentTags()));
        record.setPreferenceJson(writeJson(Map.of("hasImage", StringUtils.hasText(imageUrl), "initialRequest", content)));
        record.setDisclaimer(DEFAULT_DISCLAIMER);
        record.setAnalysisMode(result.analysisMode());
        record.setRawResponse(result.rawResponse());
        record.setLastMessageAt(LocalDateTime.now());
        record.setRecommendationSnapshotJson(writeJson(buildSnapshot(record, result)));
        aiConsultRecordMapper.insert(record);

        AiConsultMessage userMessage = createMessage(record, "USER", content, imageUrl);
        aiConsultMessageMapper.insert(userMessage);
        AiConsultMessage assistantMessage = createMessage(record, "ASSISTANT", result.assistantMessage(), "");
        aiConsultMessageMapper.insert(assistantMessage);

        AiConsultTurnVo turn = new AiConsultTurnVo();
        turn.setSession(sessionDetail(record.getId()));
        turn.setAssistantMessage(toMessageVo(assistantMessage));
        return turn;
    }

    public PageResultVo<AiConsultSessionVo> pageSessions(Long pageNum, Long pageSize) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        List<AiConsultSessionVo> sessions = aiConsultRecordMapper.selectList(new LambdaQueryWrapper<AiConsultRecord>()
                        .eq(AiConsultRecord::getUserId, userId)
                        .orderByDesc(AiConsultRecord::getLastMessageAt)
                        .orderByDesc(AiConsultRecord::getCreatedAt)
                        .orderByDesc(AiConsultRecord::getId))
                .stream()
                .map(this::toSessionVo)
                .toList();
        return PageUtils.paginate(sessions, pageNum, pageSize);
    }

    public AiConsultSessionVo sessionDetail(Long id) {
        AiConsultRecord record = requireOwnedRecord(id);
        AiConsultSessionVo vo = toSessionVo(record);
        vo.setMessages(listMessages(id));
        return vo;
    }

    @Transactional
    public AiConsultTurnVo sendSessionMessage(Long recordId, AiConsultMessageReq req) {
        AiConsultRecord record = requireOwnedRecord(recordId);
        String content = req == null ? "" : trim(req.getContent());
        String imageUrl = req == null ? "" : normalizeOptionalImageUrl(req.getImageUrl());
        if (!StringUtils.hasText(content) && !StringUtils.hasText(imageUrl)) {
            throw new BizException(4509, "请输入咨询内容，或先上传图片后再发送。");
        }
        if (!StringUtils.hasText(content)) {
            content = "请结合这张新图片，继续给我推荐适合的服务项目和护理商品。";
        }

        AiConsultMessage userMessage = createMessage(record, "USER", content, imageUrl);
        aiConsultMessageMapper.insert(userMessage);

        Catalog catalog = loadCatalog();
        TurnResult result = buildTurn(content, imageUrl, record, catalog);
        mergeRecordAfterTurn(record, result, content, imageUrl);
        aiConsultRecordMapper.updateById(record);

        AiConsultMessage assistantMessage = createMessage(record, "ASSISTANT", result.assistantMessage(), "");
        aiConsultMessageMapper.insert(assistantMessage);

        AiConsultTurnVo turn = new AiConsultTurnVo();
        turn.setAssistantMessage(toMessageVo(assistantMessage));
        turn.setSession(sessionDetail(record.getId()));
        return turn;
    }

    @Transactional
    public void deleteCurrentUserRecord(Long id) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        AiConsultRecord record = aiConsultRecordMapper.selectById(id);
        if (record == null) {
            throw new BizException(404, "AI consult record not found");
        }
        if (!Objects.equals(record.getUserId(), userId)) {
            throw new BizException(403, "No permission to delete this AI consult record");
        }
        aiConsultMessageMapper.delete(new LambdaQueryWrapper<AiConsultMessage>()
                .eq(AiConsultMessage::getRecordId, id)
                .eq(AiConsultMessage::getUserId, userId));
        aiConsultRecordMapper.deleteById(id);
    }

    public List<AiConsultMessageVo> listMessages(Long recordId) {
        AiConsultRecord record = requireOwnedRecord(recordId);
        return aiConsultMessageMapper.selectList(new LambdaQueryWrapper<AiConsultMessage>()
                        .eq(AiConsultMessage::getRecordId, record.getId())
                        .eq(AiConsultMessage::getUserId, record.getUserId())
                        .orderByAsc(AiConsultMessage::getCreatedAt)
                        .orderByAsc(AiConsultMessage::getId))
                .stream()
                .map(this::toMessageVo)
                .toList();
    }

    private TurnResult buildTurn(String question, String imageUrl, AiConsultRecord record, Catalog catalog) {
        Intent intent = detectIntent(question);
        if (intent.generalOnly()) {
            return buildGeneralTurn(question);
        }

        AiBeautyAnalysis remote = tryRemoteAnalysis(question, imageUrl, record, catalog);
        List<AiConsultServiceRecommendationVo> services = mergeServiceRecommendations(
                remote == null ? List.of() : remote.getServiceRecommendations(),
                catalog.servicesById()
        );
        List<AiConsultProductRecommendationVo> products = mergeProductRecommendations(
                remote == null ? List.of() : remote.getProductRecommendations(),
                catalog.productsById()
        );

        if (intent.serviceIntent() && services.isEmpty()) {
            services = recommendServices(question, catalog.services());
        }
        if (intent.productIntent() && products.isEmpty()) {
            products = recommendProducts(question, catalog.products());
        }
        if (!intent.serviceIntent()) {
            services = List.of();
        }
        if (!intent.productIntent()) {
            products = List.of();
        }

        List<AiConsultHairStyleVo> hairStyles = intent.hairIntent()
                ? mergeHairStyles(remote == null ? List.of() : remote.getHairStyles())
                : List.of();
        List<String> careTips = buildCareTips(question, services, products);
        String summary = chooseSummary(remote, question, intent, services, products);
        String observation = StringUtils.hasText(imageUrl)
                ? "已结合本次上传图片作为辅助上下文，实际判断仍建议到店后由技师确认。"
                : "本轮主要依据你的文字诉求和门店当前服务、商品目录生成建议。";
        String assistant = chooseAssistantMessage(remote, question, summary, services, products, intent);
        List<String> tags = buildIntentTags(intent);
        String stage = detectStage(intent);
        String mode = remote == null ? "LOCAL_RULE" : "REMOTE_API";
        String raw = remote == null ? "" : defaultString(remote.getRawResponse());
        return new TurnResult(stage, summary, observation, assistant, hairStyles, services, products, careTips, tags, mode, raw);
    }

    private AiBeautyAnalysis tryRemoteAnalysis(String question, String imageUrl, AiConsultRecord record, Catalog catalog) {
        if (remoteClient == null || aiConsultProperties == null || !aiConsultProperties.isEnabled()) {
            return null;
        }
        try {
            AiConsultContext context = new AiConsultContext();
            context.setInputText(buildRemoteInput(question, record));
            context.setServiceCatalog(catalog.services());
            context.setProductCatalog(catalog.products());
            if (StringUtils.hasText(imageUrl)) {
                LoadedImage loadedImage = loadImage(imageUrl);
                context.setImageBytes(loadedImage.bytes());
                context.setImageMimeType(loadedImage.mimeType());
            }
            return remoteClient.analyze(context);
        } catch (Exception ex) {
            if (aiConsultProperties.isAllowDegradedResponse() || aiConsultProperties.isFallbackToLocal()) {
                return null;
            }
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BizException(4502, "AI analysis request failed: " + ex.getMessage());
        }
    }

    private String buildRemoteInput(String question, AiConsultRecord record) {
        if (record == null) {
            return question;
        }
        return """
                当前用户追问：%s

                历史咨询摘要：%s
                已推荐服务 JSON：%s
                已推荐商品 JSON：%s
                原始诉求：%s
                """.formatted(
                question,
                defaultString(record.getSummaryText()),
                defaultString(record.getServiceJson()),
                defaultString(record.getProductJson()),
                defaultString(record.getInputText())
        );
    }

    private List<AiConsultServiceRecommendationVo> mergeServiceRecommendations(
            List<AiBeautyAnalysis.ServiceRecommendation> source,
            Map<Long, AiServiceCatalogItem> servicesById) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Set<Long> seen = new LinkedHashSet<>();
        return source.stream()
                .filter(item -> item != null && item.getServiceItemId() != null)
                .filter(item -> seen.add(item.getServiceItemId()))
                .map(item -> toServiceRecommendation(servicesById.get(item.getServiceItemId()), item.getReason(), item.getFitScore()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(AiConsultServiceRecommendationVo::getFitScore).reversed())
                .limit(3)
                .toList();
    }

    private List<AiConsultProductRecommendationVo> mergeProductRecommendations(
            List<AiBeautyAnalysis.ProductRecommendation> source,
            Map<Long, AiProductCatalogItem> productsById) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Set<Long> seen = new LinkedHashSet<>();
        return source.stream()
                .filter(item -> item != null && item.getProductId() != null)
                .filter(item -> seen.add(item.getProductId()))
                .map(item -> toProductRecommendation(productsById.get(item.getProductId()), item.getReason(), item.getFitScore()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(AiConsultProductRecommendationVo::getFitScore).reversed())
                .limit(3)
                .toList();
    }

    private List<AiConsultServiceRecommendationVo> recommendServices(String question, List<AiServiceCatalogItem> catalog) {
        return catalog.stream()
                .map(item -> Map.entry(item, scoreService(question, item)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<AiServiceCatalogItem, Integer>comparingByValue().reversed()
                        .thenComparing(entry -> priceValue(entry.getKey().getPrice())))
                .limit(3)
                .map(entry -> toServiceRecommendation(entry.getKey(), buildServiceReason(question, entry.getKey()), Math.max(70, entry.getValue())))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<AiConsultProductRecommendationVo> recommendProducts(String question, List<AiProductCatalogItem> catalog) {
        return catalog.stream()
                .map(item -> Map.entry(item, scoreProduct(question, item)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<AiProductCatalogItem, Integer>comparingByValue().reversed()
                        .thenComparing(entry -> priceValue(entry.getKey().getPrice())))
                .limit(3)
                .map(entry -> toProductRecommendation(entry.getKey(), buildProductReason(question, entry.getKey()), Math.max(70, entry.getValue())))
                .filter(Objects::nonNull)
                .toList();
    }

    private AiConsultServiceRecommendationVo toServiceRecommendation(AiServiceCatalogItem item, String reason, Integer score) {
        if (item == null || item.getServiceItemId() == null) {
            return null;
        }
        AiConsultServiceRecommendationVo vo = new AiConsultServiceRecommendationVo();
        vo.setServiceItemId(item.getServiceItemId());
        vo.setServiceName(item.getServiceName());
        vo.setCategoryName(item.getCategoryName());
        vo.setDurationMin(item.getDurationMin());
        vo.setPrice(item.getPrice());
        vo.setReason(StringUtils.hasText(reason) ? reason.trim() : buildServiceReason("", item));
        vo.setFitScore(normalizeScore(score));
        return vo;
    }

    private AiConsultProductRecommendationVo toProductRecommendation(AiProductCatalogItem item, String reason, Integer score) {
        if (item == null || item.getProductId() == null) {
            return null;
        }
        AiConsultProductRecommendationVo vo = new AiConsultProductRecommendationVo();
        vo.setProductId(item.getProductId());
        vo.setProductName(item.getProductName());
        vo.setCategoryName(item.getCategoryName());
        vo.setPrice(item.getPrice());
        vo.setStock(item.getStock());
        vo.setReason(StringUtils.hasText(reason) ? reason.trim() : buildProductReason("", item));
        vo.setFitScore(normalizeScore(score));
        return vo;
    }

    private int scoreService(String question, AiServiceCatalogItem item) {
        String text = normalize(question);
        String index = normalize(item.getServiceName() + " " + item.getCategoryName());
        int score = 20;
        score += keywordScore(text, index, List.of("放松", "舒缓", "解压", "肩颈", "背部", "腰背", "疲劳"), List.of("放松", "舒缓", "理疗", "经络", "肩颈", "肩背", "腰背"), 45);
        score += keywordScore(text, index, List.of("美白", "亮肤", "肤色", "暗沉", "提亮"), List.of("亮肤", "美白", "面部", "护理", "修护"), 42);
        score += keywordScore(text, index, List.of("补水", "保湿", "干燥", "清洁", "保养", "日常护理"), List.of("补水", "清洁", "保湿", "面部", "护理"), 38);
        score += keywordScore(text, index, List.of("头皮", "出油", "头屑"), List.of("头皮", "净澈", "舒缓", "护理"), 40);
        score += keywordScore(text, index, List.of("发型", "刘海", "剪发", "修剪", "烫", "染", "发质", "受损"), List.of("剪", "烫", "染", "头皮", "发", "修护"), 36);
        if (containsAny(text, "预算", "先做", "到店", "预约", "服务", "项目")) {
            score += 18;
        }
        return score;
    }

    private int scoreProduct(String question, AiProductCatalogItem item) {
        String text = normalize(question);
        String index = normalize(item.getProductName() + " " + item.getCategoryName());
        int score = 18;
        score += keywordScore(text, index, List.of("买", "商品", "商城", "产品", "居家", "日常护理", "维持"), List.of("面膜", "精华", "乳", "霜", "精油", "洗发", "发膜", "定型", "热敷"), 35);
        score += keywordScore(text, index, List.of("补水", "保湿", "干燥"), List.of("补水", "保湿", "面膜", "玻尿酸", "乳", "霜"), 42);
        score += keywordScore(text, index, List.of("美白", "亮肤", "肤色", "暗沉"), List.of("亮肤", "修护", "精华", "面膜"), 38);
        score += keywordScore(text, index, List.of("放松", "舒缓", "肩颈", "解压"), List.of("舒缓", "精油", "热敷", "眼罩", "肩颈"), 42);
        score += keywordScore(text, index, List.of("头皮", "洗发", "控油", "头屑"), List.of("洗发", "头皮", "控油", "净澈"), 40);
        score += keywordScore(text, index, List.of("发膜", "护发", "发质", "受损", "毛躁", "定型", "造型"), List.of("发膜", "护发", "修护", "精油", "定型", "喷雾"), 40);
        return score;
    }

    private int keywordScore(String text, String index, List<String> userKeywords, List<String> itemKeywords, int points) {
        boolean userMatched = userKeywords.stream().anyMatch(text::contains);
        boolean itemMatched = itemKeywords.stream().anyMatch(index::contains);
        return userMatched && itemMatched ? points : 0;
    }

    private String buildServiceReason(String question, AiServiceCatalogItem item) {
        String text = normalize(question);
        if (containsAny(text, "放松", "舒缓", "解压", "肩颈", "背部", "腰背")) {
            return item.getServiceName() + "更贴合当前放松舒缓诉求，适合作为到店优先体验项目。";
        }
        if (containsAny(text, "美白", "亮肤", "肤色", "暗沉")) {
            return item.getServiceName() + "更适合作为提亮肤色、改善暗沉方向的到店护理选择。";
        }
        if (containsAny(text, "补水", "保湿", "干燥", "保养", "清洁")) {
            return item.getServiceName() + "适合作为补水保养和基础状态稳定的服务起点。";
        }
        if (containsAny(text, "头皮", "发质", "发型", "剪发", "烫", "染")) {
            return item.getServiceName() + "能把发型或发质诉求落到具体到店沟通与执行中。";
        }
        return item.getServiceName() + "与当前诉求匹配度较高，可作为优先了解的服务项目。";
    }

    private String buildProductReason(String question, AiProductCatalogItem item) {
        String text = normalize(question);
        if (containsAny(text, "补水", "保湿", "干燥")) {
            return item.getProductName() + "适合用于居家补水保湿，帮助延续基础护理效果。";
        }
        if (containsAny(text, "美白", "亮肤", "肤色", "暗沉")) {
            return item.getProductName() + "更贴近日常亮肤和改善肤色观感的维护需求。";
        }
        if (containsAny(text, "放松", "舒缓", "解压", "肩颈")) {
            return item.getProductName() + "适合作为到店放松项目后的居家舒缓补充。";
        }
        if (containsAny(text, "发质", "头皮", "护发", "发膜", "洗发", "定型", "造型")) {
            return item.getProductName() + "适合用于日常头发护理或造型维持。";
        }
        return item.getProductName() + "适合作为本次咨询目标的居家护理搭配。";
    }

    private String chooseSummary(AiBeautyAnalysis remote,
                                 String question,
                                 Intent intent,
                                 List<AiConsultServiceRecommendationVo> services,
                                 List<AiConsultProductRecommendationVo> products) {
        if (remote != null && StringUtils.hasText(remote.getSummaryText())) {
            return remote.getSummaryText().trim();
        }
        if (intent.serviceIntent() && intent.productIntent()) {
            return "这次更适合采用“到店服务 + 居家商品维护”的组合，先解决主要诉求，再用商品延续效果。";
        }
        if (intent.serviceIntent()) {
            String name = services.isEmpty() ? "相关服务项目" : services.get(0).getServiceName();
            return "你的诉求更适合先落到到店服务上，可以优先了解 " + name + "。";
        }
        if (intent.productIntent()) {
            String name = products.isEmpty() ? "相关护理商品" : products.get(0).getProductName();
            return "你的诉求更偏向居家维护和商品选择，可以优先了解 " + name + "。";
        }
        return "我会先根据你的描述梳理方向，再在需要时补充服务项目和商品建议。";
    }

    private String chooseAssistantMessage(AiBeautyAnalysis remote,
                                          String question,
                                          String summary,
                                          List<AiConsultServiceRecommendationVo> services,
                                          List<AiConsultProductRecommendationVo> products,
                                          Intent intent) {
        if (remote != null && StringUtils.hasText(remote.getAssistantMessage())) {
            return remote.getAssistantMessage().trim();
        }
        StringBuilder builder = new StringBuilder(summary);
        if (!services.isEmpty()) {
            builder.append("\n\n推荐服务：");
            builder.append(services.stream()
                    .map(item -> item.getServiceName() + "（" + item.getReason() + "）")
                    .collect(Collectors.joining("；")));
        }
        if (!products.isEmpty()) {
            builder.append("\n\n推荐商品：");
            builder.append(products.stream()
                    .map(item -> item.getProductName() + "（" + item.getReason() + "）")
                    .collect(Collectors.joining("；")));
        }
        if (!intent.serviceIntent() && !intent.productIntent()) {
            builder.append("\n\n你可以继续告诉我更具体的护理目标、预算或想要购买/预约的方向。");
        } else if (intent.serviceIntent() && intent.productIntent()) {
            builder.append("\n\n更稳妥的顺序是先确认到店项目，再决定回家维持用的商品。");
        } else if (intent.serviceIntent()) {
            builder.append("\n\n如果你有预算或时间要求，我可以继续帮你排一个先后顺序。");
        } else {
            builder.append("\n\n如果你愿意补充肤质、发质或预算，我可以继续缩小商品选择。");
        }
        return builder.toString();
    }

    private TurnResult buildGeneralTurn(String question) {
        String answer;
        if (containsAny(normalize(question), "你是谁", "你能做什么", "介绍")) {
            answer = "我是 BFSM AI 顾问，可以先和你正常对话，也可以根据你的放松、护肤、发型、到店服务或居家护理商品需求给出建议。";
        } else if (containsAny(normalize(question), "你好", "在吗", "hello", "hi")) {
            answer = "你好，我在。你可以直接说想放松、补水、美白、护理头皮、改善发质，或者问我适合买什么商品、先预约什么服务。";
        } else {
            answer = "我可以继续陪你聊，也可以帮你把需求落到具体服务项目和护理商品上。";
        }
        return new TurnResult("STYLE_DISCOVERY", "当前是普通问答，还没有触发具体服务或商品推荐。",
                "普通问答不会强行推荐服务或商品。", answer, List.of(), List.of(), List.of(), List.of(),
                List.of("普通问答"), "LOCAL_RULE", "");
    }

    private Intent detectIntent(String question) {
        String text = normalize(question);
        boolean careGoalIntent = containsAny(text,
                "美白", "亮肤", "肤色", "暗沉", "补水", "保湿", "干燥", "保养", "日常护理",
                "放松", "舒缓", "解压");
        boolean serviceOnlyIntent = containsAny(text,
                "服务", "项目", "预约", "到店", "先做", "预算", "清洁", "头皮",
                "发型", "刘海", "剪发", "烫", "染", "发质");
        boolean productOnlyIntent = containsAny(text,
                "商品", "商城", "买", "购买", "产品", "居家", "维持", "面膜", "精华", "精油",
                "发膜", "洗发", "护发", "定型", "喷雾", "眼罩", "热敷");
        boolean purchaseDominant = productOnlyIntent
                && !serviceOnlyIntent
                && containsAny(text, "买", "购买", "商品", "商城", "产品");
        boolean serviceIntent = serviceOnlyIntent || (careGoalIntent && !purchaseDominant);
        boolean productIntent = careGoalIntent || productOnlyIntent;
        boolean hairIntent = containsAny(text, "发型", "刘海", "剪发", "烫", "染", "头皮", "发质", "护发", "发膜");
        boolean generalOnly = !serviceIntent && !productIntent;
        return new Intent(serviceIntent, productIntent, hairIntent, generalOnly);
    }

    private String detectStage(Intent intent) {
        if (intent.productIntent()) {
            return "CARE_PLANNING";
        }
        if (intent.serviceIntent()) {
            return "SERVICE_PLANNING";
        }
        return "STYLE_DISCOVERY";
    }

    private List<String> buildIntentTags(Intent intent) {
        List<String> tags = new ArrayList<>();
        if (intent.serviceIntent()) {
            tags.add("服务推荐");
        }
        if (intent.productIntent()) {
            tags.add("商品推荐");
        }
        if (intent.hairIntent()) {
            tags.add("发型护理");
        }
        if (tags.isEmpty()) {
            tags.add("普通问答");
        }
        return tags;
    }

    private List<String> buildCareTips(String question,
                                       List<AiConsultServiceRecommendationVo> services,
                                       List<AiConsultProductRecommendationVo> products) {
        List<String> tips = new ArrayList<>();
        if (!services.isEmpty()) {
            tips.add("预约前可以先确认预算、可到店时间和最想改善的一项问题。");
        }
        if (!products.isEmpty()) {
            tips.add("商品建议先少量尝试，确认肤质或发质适配后再稳定使用。");
        }
        if (containsAny(normalize(question), "美白", "亮肤", "肤色")) {
            tips.add("亮肤类护理需要搭配防晒和规律作息，不建议追求一次性强刺激效果。");
        } else if (containsAny(normalize(question), "发质", "头皮", "护发")) {
            tips.add("发质和头皮护理更看重持续维护，洗护频率和吹干方式也会影响效果。");
        } else if (containsAny(normalize(question), "放松", "舒缓", "解压")) {
            tips.add("放松舒缓类项目可结合肩颈、背部疲劳位置来选择，不必一次安排过多项目。");
        }
        return tips.stream().distinct().limit(3).toList();
    }

    private List<AiConsultHairStyleVo> mergeHairStyles(List<AiBeautyAnalysis.HairStyleAdvice> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .filter(item -> item != null && StringUtils.hasText(item.getName()))
                .limit(3)
                .map(item -> {
                    AiConsultHairStyleVo vo = new AiConsultHairStyleVo();
                    vo.setName(item.getName());
                    vo.setReason(StringUtils.hasText(item.getReason()) ? item.getReason() : "适合作为本次发型沟通的参考方向。");
                    vo.setMaintenanceLevel(StringUtils.hasText(item.getMaintenanceLevel()) ? item.getMaintenanceLevel() : "MEDIUM");
                    vo.setFitScore(normalizeScore(item.getFitScore()));
                    return vo;
                })
                .toList();
    }

    private void mergeRecordAfterTurn(AiConsultRecord record, TurnResult result, String question, String imageUrl) {
        if (StringUtils.hasText(imageUrl)) {
            record.setImageUrl(imageUrl);
        }
        record.setStage(result.stage());
        record.setSummaryText(result.summaryText());
        record.setFaceSummary(result.observation());
        record.setHairstyleJson(writeJson(result.hairStyles()));
        record.setServiceJson(writeJson(result.services()));
        record.setProductJson(writeJson(result.products()));
        record.setCareTipsJson(writeJson(result.careTips()));
        record.setIntentTagsJson(writeJson(result.intentTags()));
        record.setRecommendationSnapshotJson(writeJson(buildSnapshot(record, result)));
        record.setAnalysisMode(result.analysisMode());
        record.setRawResponse(result.rawResponse());
        record.setLastMessageAt(LocalDateTime.now());
        if (StringUtils.hasText(question)) {
            record.setTitle(buildSessionTitle(question));
        }
    }

    private AiRecommendationSnapshotVo buildSnapshot(AiConsultRecord record, TurnResult result) {
        AiRecommendationSnapshotVo snapshot = new AiRecommendationSnapshotVo();
        snapshot.setStage(result.stage());
        snapshot.setSummaryText(result.summaryText());
        snapshot.setFaceHairObservation(result.observation());
        snapshot.setStyleDirections(result.hairStyles().stream().map(item -> {
            AiStyleDirectionVo direction = new AiStyleDirectionVo();
            direction.setName(item.getName());
            direction.setReason(item.getReason());
            direction.setMaintenanceLevel(item.getMaintenanceLevel());
            direction.setFitScore(item.getFitScore());
            return direction;
        }).toList());
        snapshot.setCareTips(result.careTips());
        snapshot.setServiceRecommendations(result.services());
        snapshot.setProductRecommendations(result.products());
        snapshot.setIntentTags(result.intentTags());
        snapshot.setNextQuestions(nextQuestions(result));
        snapshot.setDisclaimer(StringUtils.hasText(record.getDisclaimer()) ? record.getDisclaimer() : DEFAULT_DISCLAIMER);
        snapshot.setAnalysisMode(result.analysisMode());
        return snapshot;
    }

    private List<String> nextQuestions(TurnResult result) {
        if (!result.services().isEmpty() && !result.products().isEmpty()) {
            return List.of("预算有限的话先做哪一步？", "服务和商品怎么搭配更稳妥？", "到店前需要注意什么？");
        }
        if (!result.services().isEmpty()) {
            return List.of("预算有限先做哪个服务？", "这些服务适合什么顺序？", "到店时怎么和技师沟通？");
        }
        if (!result.products().isEmpty()) {
            return List.of("这些商品怎么搭配使用？", "预算有限先买哪个？", "敏感肌或受损发质要注意什么？");
        }
        return List.of("我想放松，推荐什么服务？", "日常护理应该买什么？", "预算有限服务和商品怎么搭配？");
    }

    private Catalog loadCatalog() {
        return new Catalog(loadActiveServiceCatalog(), loadActiveProductCatalog());
    }

    private List<AiServiceCatalogItem> loadActiveServiceCatalog() {
        Map<Long, String> categoryNames = serviceCategoryMapper.selectList(new LambdaQueryWrapper<ServiceCategory>()
                        .orderByAsc(ServiceCategory::getSortNo)
                        .orderByAsc(ServiceCategory::getId))
                .stream()
                .collect(Collectors.toMap(ServiceCategory::getId, ServiceCategory::getName, (a, b) -> a));
        return serviceItemMapper.selectList(new LambdaQueryWrapper<ServiceItem>()
                        .eq(ServiceItem::getStatus, "ON")
                        .orderByAsc(ServiceItem::getId))
                .stream()
                .map(item -> {
                    AiServiceCatalogItem catalogItem = new AiServiceCatalogItem();
                    catalogItem.setServiceItemId(item.getId());
                    catalogItem.setServiceName(item.getName());
                    catalogItem.setCategoryName(categoryNames.getOrDefault(item.getCategoryId(), "未分类"));
                    catalogItem.setDurationMin(item.getDurationMin());
                    catalogItem.setPrice(item.getPrice());
                    return catalogItem;
                })
                .toList();
    }

    private List<AiProductCatalogItem> loadActiveProductCatalog() {
        Map<Long, String> categoryNames = productCategoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>()
                        .orderByAsc(ProductCategory::getId))
                .stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, "ON")
                        .gt(Product::getStock, 0)
                        .orderByAsc(Product::getId))
                .stream()
                .map(product -> {
                    AiProductCatalogItem item = new AiProductCatalogItem();
                    item.setProductId(product.getId());
                    item.setProductName(product.getName());
                    item.setCategoryName(categoryNames.getOrDefault(product.getCategoryId(), "未分类"));
                    item.setPrice(product.getPrice());
                    item.setStock(product.getStock());
                    return item;
                })
                .toList();
    }

    private AiConsultRecord requireOwnedRecord(Long recordId) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        AiConsultRecord record = aiConsultRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(404, "AI consult record not found");
        }
        if (!Objects.equals(record.getUserId(), userId)) {
            throw new BizException(403, "No permission to access this AI consult record");
        }
        return record;
    }

    private AiConsultMessage createMessage(AiConsultRecord record, String role, String content, String imageUrl) {
        AiConsultMessage message = new AiConsultMessage();
        message.setRecordId(record.getId());
        message.setUserId(record.getUserId());
        message.setRole(role);
        message.setContent(content);
        message.setImageUrl(imageUrl);
        return message;
    }

    private AiConsultSessionVo toSessionVo(AiConsultRecord record) {
        AiConsultSessionVo vo = new AiConsultSessionVo();
        vo.setId(record.getId());
        vo.setTitle(StringUtils.hasText(record.getTitle()) ? record.getTitle() : DEFAULT_TITLE);
        vo.setStage(StringUtils.hasText(record.getStage()) ? record.getStage() : "STYLE_DISCOVERY");
        vo.setImageUrl(record.getImageUrl());
        vo.setInputText(record.getInputText());
        vo.setAnalysisMode(record.getAnalysisMode());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        vo.setLastMessageAt(record.getLastMessageAt() == null ? record.getCreatedAt() : record.getLastMessageAt());
        vo.setSnapshot(readSnapshot(record));
        AiConsultMessage latest = latestMessage(record.getId());
        vo.setPreviewText(buildPreview(record, latest));
        vo.setMessageCount(countMessages(record.getId()));
        return vo;
    }

    private AiConsultRecordVo toRecordVo(AiConsultRecord record) {
        AiConsultRecordVo vo = new AiConsultRecordVo();
        vo.setId(record.getId());
        vo.setTitle(record.getTitle());
        vo.setStage(record.getStage());
        vo.setImageUrl(record.getImageUrl());
        vo.setInputText(record.getInputText());
        vo.setSummaryText(record.getSummaryText());
        vo.setFaceSummary(record.getFaceSummary());
        vo.setHairStyles(readList(record.getHairstyleJson(), new TypeReference<>() {}));
        vo.setServiceRecommendations(readList(record.getServiceJson(), new TypeReference<>() {}));
        vo.setProductRecommendations(readList(record.getProductJson(), new TypeReference<>() {}));
        vo.setCareTips(readList(record.getCareTipsJson(), new TypeReference<>() {}));
        vo.setNextQuestions(readSnapshot(record).getNextQuestions());
        vo.setDisclaimer(record.getDisclaimer());
        vo.setAnalysisMode(record.getAnalysisMode());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    private AiConsultMessageVo toMessageVo(AiConsultMessage message) {
        AiConsultMessageVo vo = new AiConsultMessageVo();
        vo.setId(message.getId());
        vo.setRecordId(message.getRecordId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setImageUrl(message.getImageUrl());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }

    private AiRecommendationSnapshotVo readSnapshot(AiConsultRecord record) {
        if (StringUtils.hasText(record.getRecommendationSnapshotJson())) {
            try {
                return objectMapper.readValue(record.getRecommendationSnapshotJson(), AiRecommendationSnapshotVo.class);
            } catch (Exception ignored) {
                // Rebuild from denormalized fields below.
            }
        }
        TurnResult result = new TurnResult(
                StringUtils.hasText(record.getStage()) ? record.getStage() : "STYLE_DISCOVERY",
                defaultString(record.getSummaryText()),
                defaultString(record.getFaceSummary()),
                defaultString(record.getSummaryText()),
                readList(record.getHairstyleJson(), new TypeReference<>() {}),
                readList(record.getServiceJson(), new TypeReference<>() {}),
                readList(record.getProductJson(), new TypeReference<>() {}),
                readList(record.getCareTipsJson(), new TypeReference<>() {}),
                readList(record.getIntentTagsJson(), new TypeReference<>() {}),
                StringUtils.hasText(record.getAnalysisMode()) ? record.getAnalysisMode() : "LOCAL_RULE",
                defaultString(record.getRawResponse())
        );
        return buildSnapshot(record, result);
    }

    private AiConsultMessage latestMessage(Long recordId) {
        return aiConsultMessageMapper.selectList(new LambdaQueryWrapper<AiConsultMessage>()
                        .eq(AiConsultMessage::getRecordId, recordId)
                        .orderByDesc(AiConsultMessage::getCreatedAt)
                        .orderByDesc(AiConsultMessage::getId)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private int countMessages(Long recordId) {
        Long count = aiConsultMessageMapper.selectCount(new LambdaQueryWrapper<AiConsultMessage>()
                .eq(AiConsultMessage::getRecordId, recordId));
        return count == null ? 0 : count.intValue();
    }

    private String buildPreview(AiConsultRecord record, AiConsultMessage latest) {
        String text = latest == null ? record.getSummaryText() : latest.getContent();
        if (!StringUtils.hasText(text)) {
            text = DEFAULT_TITLE;
        }
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > 60 ? text.substring(0, 60) + "..." : text;
    }

    private String normalizeOptionalImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return "";
        }
        String normalized = imageUrl.trim();
        if (!normalized.startsWith(AI_UPLOAD_PREFIX)) {
            throw new BizException(4505, "AI consult image must be uploaded through the system first");
        }
        return normalized;
    }

    private LoadedImage loadImage(String imageUrl) {
        try {
            String relativePath = imageUrl.substring("/uploads/".length());
            Path root = Path.of("uploads").toAbsolutePath().normalize();
            Path path = root.resolve(relativePath).toAbsolutePath().normalize();
            if (!Files.exists(path) || !path.startsWith(root)) {
                throw new BizException(4506, "Uploaded AI consult image does not exist");
            }
            String mimeType = Files.probeContentType(path);
            return new LoadedImage(Files.readAllBytes(path), mimeType == null ? "image/jpeg" : mimeType);
        } catch (IOException ex) {
            throw new BizException(4507, "Failed to read uploaded AI consult image");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private <T> T readList(String json, TypeReference<T> type) {
        if (!StringUtils.hasText(json)) {
            try {
                return objectMapper.readValue("[]", type);
            } catch (Exception ex) {
                throw new BizException(4511, "Failed to initialize AI consult JSON fallback");
            }
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            try {
                return objectMapper.readValue("[]", type);
            } catch (Exception nested) {
                throw new BizException(4511, "Failed to initialize AI consult JSON fallback");
            }
        }
    }

    private String buildSessionTitle(String content) {
        String title = StringUtils.hasText(content) ? content.replaceAll("\\s+", " ").trim() : DEFAULT_TITLE;
        return title.length() > 28 ? title.substring(0, 28) + "..." : title;
    }

    private int normalizeScore(Integer score) {
        if (score == null) {
            return 80;
        }
        return Math.max(0, Math.min(100, score));
    }

    private double priceValue(BigDecimal price) {
        return price == null ? 0D : price.doubleValue();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private boolean containsAny(String source, String... keywords) {
        if (!StringUtils.hasText(source)) {
            return false;
        }
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private record Catalog(List<AiServiceCatalogItem> services, List<AiProductCatalogItem> products) {
        Map<Long, AiServiceCatalogItem> servicesById() {
            return services.stream()
                    .collect(Collectors.toMap(AiServiceCatalogItem::getServiceItemId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        }

        Map<Long, AiProductCatalogItem> productsById() {
            return products.stream()
                    .collect(Collectors.toMap(AiProductCatalogItem::getProductId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        }
    }

    private record Intent(boolean serviceIntent, boolean productIntent, boolean hairIntent, boolean generalOnly) {
    }

    private record TurnResult(String stage,
                              String summaryText,
                              String observation,
                              String assistantMessage,
                              List<AiConsultHairStyleVo> hairStyles,
                              List<AiConsultServiceRecommendationVo> services,
                              List<AiConsultProductRecommendationVo> products,
                              List<String> careTips,
                              List<String> intentTags,
                              String analysisMode,
                              String rawResponse) {
    }

    private record LoadedImage(byte[] bytes, String mimeType) {
    }
}
