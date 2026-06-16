package com.bfsm.service.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class MockBeautyAdvisorClient implements AiBeautyAdvisorClient {
    @Override
    public AiBeautyAnalysis analyze(AiConsultContext context) {
        String prompt = context.getInputText() == null ? "" : context.getInputText().trim();
        boolean hairIntent = isHairIntent(prompt);
        AiBeautyAnalysis analysis = new AiBeautyAnalysis();
        analysis.setAnalysisMode("DEGRADED_FALLBACK");
        analysis.setSummaryText(buildSummary(prompt));
        analysis.setFaceSummary(hairIntent
                ? "当前结果基于图片上传状态与文字诉求生成，适合作为初步风格判断和继续追问的起点。"
                : "当前结果基于文字诉求和系统目录生成，适合作为放松、亮肤、补水等护理方向的保守建议。");
        analysis.setHairStyles(hairIntent ? buildHairStyles(prompt) : List.of());
        analysis.setServiceRecommendations(buildServiceRecommendations(prompt, context.getServiceCatalog()));
        analysis.setProductRecommendations(buildProductRecommendations(prompt, context.getProductCatalog()));
        analysis.setCareTips(hairIntent
                ? List.of(
                        "建议先从更稳妥、易维护的方向开始，再决定是否做更明显的造型变化。",
                        "如果准备到店沟通，可以优先确认刘海、层次、长度和后续维护成本。",
                        "如果后续需要居家维持效果，再补充护理或定型商品会更自然。"
                )
                : List.of(
                        "如果你更在意放松感，可以先从时长适中、舒缓感更明确的项目开始尝试。",
                        "如果你更在意亮白或补水，建议继续补充预算和希望达到的效果强度。",
                        "如果后续想居家维持，再搭配面膜、精华或舒缓类商品会更合适。"
                ));
        analysis.setDisclaimer("当前结果基于系统规则与项目数据生成，最终方案请结合到店沟通综合判断。");
        analysis.setRawResponse("mock");
        return analysis;
    }

    private String buildSummary(String prompt) {
        if (containsAny(prompt, "放松", "舒缓", "解压", "压力")) {
            return "这次更适合先从放松、舒缓和调整状态的方向入手，再决定是否继续做更完整的护理组合。";
        }
        if (containsAny(prompt, "美白", "亮白", "肤色", "暗沉")) {
            return "这次更适合优先围绕亮肤、匀净和补水修护来做保守判断，再按预算继续细化。";
        }
        if (containsAny(prompt, "补水", "保湿", "干燥")) {
            return "这次更适合先从补水保湿和舒缓修护入手，把基础状态稳定下来。";
        }
        if (containsAny(prompt, "通勤", "上班", "自然")) {
            return "这次更适合从自然、利落、容易打理的方向入手，先把整体轮廓和层次理顺。";
        }
        if (containsAny(prompt, "温柔", "气质", "柔和")) {
            return "这次更适合走线条柔和、轮廓自然包裹脸部的路线，重点在于轻盈感和耐看度。";
        }
        if (containsAny(prompt, "显脸小", "刘海", "修饰")) {
            return "这次更适合优先处理面部修饰感，重点看刘海、侧分和发尾层次的配合。";
        }
        return "这次建议先确认更适合你的发型方向和打理难度，再决定是否继续落到具体项目。";
    }

    private List<AiBeautyAnalysis.HairStyleAdvice> buildHairStyles(String prompt) {
        List<AiBeautyAnalysis.HairStyleAdvice> results = new ArrayList<>();
        results.add(hairStyle("自然层次发", "适合作为大多数日常场景下的稳妥方向，兼顾修饰和易打理。", "MEDIUM", 92));
        if (containsAny(prompt, "刘海", "额头", "显脸小", "圆脸")) {
            results.add(hairStyle("轻薄刘海中长发", "更适合需要修饰额头和面中比例的诉求，视觉上更轻盈。", "MEDIUM", 89));
        } else {
            results.add(hairStyle("侧分柔和中长发", "线条自然、适配度高，适合通勤和稳定日常风格。", "LOW", 88));
        }
        if (containsAny(prompt, "卷", "蓬松", "造型")) {
            results.add(hairStyle("轻卷空气感发型", "更适合增加蓬松度和造型氛围，但维护成本会略高。", "HIGH", 86));
        } else {
            results.add(hairStyle("低层次内扣短中发", "整体更收敛、好整理，适合不想花太多时间打理的人。", "LOW", 84));
        }
        return results;
    }

    private List<AiBeautyAnalysis.ServiceRecommendation> buildServiceRecommendations(String prompt, List<AiServiceCatalogItem> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            return List.of();
        }
        String normalizedPrompt = prompt.toLowerCase(Locale.ROOT);
        return catalog.stream()
                .map(item -> scoreService(normalizedPrompt, item))
                .sorted(Comparator.comparingInt(ScoredService::score).reversed()
                        .thenComparing(scored -> scored.item().getPrice() == null ? 0D : scored.item().getPrice().doubleValue()))
                .limit(3)
                .map(scored -> {
                    AiBeautyAnalysis.ServiceRecommendation recommendation = new AiBeautyAnalysis.ServiceRecommendation();
                    recommendation.setServiceItemId(scored.item().getServiceItemId());
                    recommendation.setFitScore(Math.max(scored.score(), 72));
                    recommendation.setReason(buildServiceReason(prompt, scored.item()));
                    return recommendation;
                })
                .toList();
    }

    private ScoredService scoreService(String prompt, AiServiceCatalogItem item) {
        String index = ((item.getServiceName() == null ? "" : item.getServiceName()) + " " +
                (item.getCategoryName() == null ? "" : item.getCategoryName())).toLowerCase(Locale.ROOT);
        int score = 0;
        if (containsPair(prompt, index, "放松", "放松", "舒缓", "理疗", "精油")) score += 28;
        if (containsPair(prompt, index, "舒缓", "舒缓", "修护", "理疗", "精油")) score += 24;
        if (containsPair(prompt, index, "美白", "亮白", "亮肤", "匀净")) score += 28;
        if (containsPair(prompt, index, "补水", "补水", "保湿", "修护")) score += 24;
        if (containsPair(prompt, index, "肤色", "亮白", "亮肤", "匀净")) score += 22;
        if (containsPair(prompt, index, "头皮", "头皮", "控油", "清洁", "舒缓")) score += 26;
        if (containsPair(prompt, index, "毛躁", "修复", "护理", "柔顺", "滋养")) score += 24;
        if (containsPair(prompt, index, "受损", "修复", "护理", "滋养")) score += 24;
        if (containsPair(prompt, index, "染", "染")) score += 24;
        if (containsPair(prompt, index, "烫", "烫", "卷")) score += 24;
        if (containsPair(prompt, index, "发型", "造型", "设计", "剪")) score += 18;
        if (containsPair(prompt, index, "通勤", "剪", "造型", "护理")) score += 14;
        if (containsPair(prompt, index, "年轻", "造型", "护理", "修复")) score += 14;
        if (containsPair(prompt, index, "清新", "造型", "护理")) score += 12;
        if (score == 0 && item.getServiceName() != null && !item.getServiceName().isBlank()) {
            score = 10;
        }
        return new ScoredService(item, score);
    }

    private boolean containsPair(String prompt, String index, String promptKeyword, String... serviceKeywords) {
        if (!prompt.contains(promptKeyword)) {
            return false;
        }
        for (String serviceKeyword : serviceKeywords) {
            if (index.contains(serviceKeyword)) {
                return true;
            }
        }
        return false;
    }

    private String buildServiceReason(String prompt, AiServiceCatalogItem item) {
        String serviceName = item.getServiceName() == null ? "该服务项目" : item.getServiceName();
        if (containsAny(prompt, "放松", "舒缓", "解压", "压力")) {
            return serviceName + "更贴近当前想要放松、舒缓和调整状态的诉求。";
        }
        if (containsAny(prompt, "美白", "亮白", "肤色", "暗沉")) {
            return serviceName + "更适合作为亮肤、匀净和改善肤色观感的护理方向。";
        }
        if (containsAny(prompt, "补水", "保湿", "干燥")) {
            return serviceName + "更适合作为稳定肤感、补水保湿和舒缓修护的优先选择。";
        }
        if (containsAny(prompt, "毛躁", "修复", "受损")) {
            return serviceName + "更适合作为改善发质状态和降低毛躁感的优先方案。";
        }
        if (containsAny(prompt, "头皮", "出油", "敏感")) {
            return serviceName + "更贴近当前头皮管理与基础护理诉求。";
        }
        if (containsAny(prompt, "发型", "显脸小", "刘海", "通勤")) {
            return serviceName + "更有利于把当前发型方向落实到到店沟通和实际造型中。";
        }
        return serviceName + "与当前形象优化目标匹配度较高，适合作为后续落地选择。";
    }

    private List<AiBeautyAnalysis.ProductRecommendation> buildProductRecommendations(String prompt, List<AiProductCatalogItem> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            return List.of();
        }
        String normalizedPrompt = prompt.toLowerCase(Locale.ROOT);
        return catalog.stream()
                .map(item -> scoreProduct(normalizedPrompt, item))
                .sorted(Comparator.comparingInt(ScoredProduct::score).reversed()
                        .thenComparing(scored -> scored.item().getPrice() == null ? 0D : scored.item().getPrice().doubleValue()))
                .limit(3)
                .map(scored -> {
                    AiBeautyAnalysis.ProductRecommendation recommendation = new AiBeautyAnalysis.ProductRecommendation();
                    recommendation.setProductId(scored.item().getProductId());
                    recommendation.setFitScore(Math.max(scored.score(), 72));
                    recommendation.setReason(buildProductReason(prompt, scored.item()));
                    return recommendation;
                })
                .toList();
    }

    private ScoredProduct scoreProduct(String prompt, AiProductCatalogItem item) {
        String index = ((item.getProductName() == null ? "" : item.getProductName()) + " " +
                (item.getCategoryName() == null ? "" : item.getCategoryName())).toLowerCase(Locale.ROOT);
        int score = 8;
        if (containsPair(prompt, index, "放松", "精油", "眼罩", "舒缓")) score += 24;
        if (containsPair(prompt, index, "舒缓", "舒缓", "修护", "面膜", "精油")) score += 24;
        if (containsPair(prompt, index, "美白", "亮白", "焕亮", "修护")) score += 24;
        if (containsPair(prompt, index, "补水", "补水", "保湿", "面膜", "乳")) score += 22;
        if (containsPair(prompt, index, "肤色", "亮白", "焕亮", "保湿")) score += 20;
        if (containsPair(prompt, index, "毛躁", "修复", "柔顺", "护发", "精油", "发膜")) score += 28;
        if (containsPair(prompt, index, "受损", "修复", "护理", "发膜", "滋养")) score += 26;
        if (containsPair(prompt, index, "头皮", "头皮", "控油", "清洁", "洗发")) score += 24;
        if (containsPair(prompt, index, "打理", "定型", "造型", "喷雾", "发蜡")) score += 22;
        if (containsPair(prompt, index, "通勤", "定型", "造型", "护理")) score += 16;
        return new ScoredProduct(item, score);
    }

    private String buildProductReason(String prompt, AiProductCatalogItem item) {
        String productName = item.getProductName() == null ? "该商品" : item.getProductName();
        if (containsAny(prompt, "放松", "舒缓", "解压", "压力")) {
            return productName + "适合作为到店放松护理后的居家舒缓补充。";
        }
        if (containsAny(prompt, "美白", "亮白", "肤色", "暗沉")) {
            return productName + "更贴近亮肤、焕亮和改善肤色状态的日常护理方向。";
        }
        if (containsAny(prompt, "补水", "保湿", "干燥")) {
            return productName + "适合作为补水保湿和稳定肤感的日常护理补充。";
        }
        if (containsAny(prompt, "毛躁", "受损", "修复")) {
            return productName + "适合作为改善毛躁和受损发质的日常护理补充。";
        }
        if (containsAny(prompt, "打理", "造型", "通勤")) {
            return productName + "可帮助维持日常造型效果，降低每天出门前的打理成本。";
        }
        return productName + "可作为本次发型建议后的护理或造型搭配。";
    }

    private AiBeautyAnalysis.HairStyleAdvice hairStyle(String name, String reason, String maintenanceLevel, int fitScore) {
        AiBeautyAnalysis.HairStyleAdvice advice = new AiBeautyAnalysis.HairStyleAdvice();
        advice.setName(name);
        advice.setReason(reason);
        advice.setMaintenanceLevel(maintenanceLevel);
        advice.setFitScore(fitScore);
        return advice;
    }

    private boolean containsAny(String source, String... keywords) {
        if (source == null || source.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHairIntent(String source) {
        return containsAny(source, "发型", "刘海", "脸型", "显脸小", "通勤", "层次", "卷", "烫", "染", "修剪", "头皮", "发质");
    }

    private record ScoredService(AiServiceCatalogItem item, int score) {
    }

    private record ScoredProduct(AiProductCatalogItem item, int score) {
    }
}
