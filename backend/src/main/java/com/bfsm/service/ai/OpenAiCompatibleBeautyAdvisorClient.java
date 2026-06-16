package com.bfsm.service.ai;

import com.bfsm.common.BizException;
import com.bfsm.config.AiConsultProperties;
import com.bfsm.entity.AiConsultMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleBeautyAdvisorClient implements AiBeautyAdvisorClient {
    private final AiConsultProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleBeautyAdvisorClient(AiConsultProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String chat(String recordContext,
                       String question,
                       List<AiConsultMessage> recentMessages,
                       List<AiServiceCatalogItem> serviceCatalog,
                       List<AiProductCatalogItem> productCatalog) {
        if (!properties.isEnabled()
                || !StringUtils.hasText(properties.getApiKey())
                || !StringUtils.hasText(properties.getBaseUrl())
                || !StringUtils.hasText(properties.getModel())) {
            throw new BizException(4501, "AI API is not configured");
        }

        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", buildChatSystemPrompt()));
            messages.add(Map.of("role", "user", "content", buildChatContextPrompt(recordContext, serviceCatalog, productCatalog)));
            for (int i = recentMessages.size() - 1; i >= 0; i--) {
                AiConsultMessage message = recentMessages.get(i);
                String role = "ASSISTANT".equalsIgnoreCase(message.getRole()) ? "assistant" : "user";
                messages.add(Map.of("role", role, "content", message.getContent()));
            }
            messages.add(Map.of("role", "user", "content", question));

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", properties.getModel());
            payload.put("temperature", properties.getTemperature());
            payload.put("messages", messages);

            String response = postChatCompletions(payload);
            return extractContent(objectMapper.readTree(response)
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content"));
        } catch (Exception ex) {
            throw new BizException(4508, "AI follow-up request failed: " + ex.getMessage());
        }
    }

    public String rewriteReply(String recordContext,
                               String question,
                               String draftAnswer,
                               List<AiServiceCatalogItem> serviceCatalog,
                               List<AiProductCatalogItem> productCatalog) {
        if (!properties.isEnabled()
                || !StringUtils.hasText(properties.getApiKey())
                || !StringUtils.hasText(properties.getBaseUrl())
                || !StringUtils.hasText(properties.getModel())) {
            throw new BizException(4501, "AI API is not configured");
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", properties.getModel());
            payload.put("temperature", 0.2);
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", buildReplyRepairPrompt()),
                    Map.of("role", "user", "content", buildReplyRepairContext(recordContext, question, draftAnswer, serviceCatalog, productCatalog))
            ));
            String response = postChatCompletions(payload);
            return extractContent(objectMapper.readTree(response)
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content"));
        } catch (Exception ex) {
            throw new BizException(4510, "AI reply repair failed: " + ex.getMessage());
        }
    }

    @Override
    public AiBeautyAnalysis analyze(AiConsultContext context) {
        if (!properties.isEnabled()
                || !StringUtils.hasText(properties.getApiKey())
                || !StringUtils.hasText(properties.getBaseUrl())
                || !StringUtils.hasText(properties.getModel())) {
            throw new BizException(4501, "AI API is not configured");
        }

        try {
            List<Map<String, Object>> userContent = new ArrayList<>();
            userContent.add(Map.of("type", "text", "text", buildUserPrompt(context)));
            if (context.getImageBytes() != null && context.getImageBytes().length > 0) {
                String imageDataUrl = "data:" + context.getImageMimeType() + ";base64," +
                        Base64.getEncoder().encodeToString(context.getImageBytes());
                userContent.add(Map.of("type", "image_url", "image_url", Map.of("url", imageDataUrl)));
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", properties.getModel());
            payload.put("temperature", properties.getTemperature());
            payload.put("response_format", Map.of("type", "json_object"));
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", buildSystemPrompt()),
                    Map.of("role", "user", "content", userContent)
            ));

            String response = postChatCompletions(payload);
            return parseAnalysis(response, context);
        } catch (Exception ex) {
            throw new BizException(4502, "AI analysis request failed: " + ex.getMessage());
        }
    }

    private String postChatCompletions(Map<String, Object> payload) {
        RestClient client = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        RestClient.RequestBodySpec request = StringUtils.hasText(properties.getChatCompletionsPath())
                ? client.post().uri(properties.getChatCompletionsPath())
                : client.post();
        return request.body(payload)
                .retrieve()
                .body(String.class);
    }

    private AiBeautyAnalysis parseAnalysis(String response, AiConsultContext context) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        String content = extractContent(contentNode);
        JsonNode data;
        try {
            data = objectMapper.readTree(content);
        } catch (Exception ex) {
            data = objectMapper.readTree(repairAnalysisJson(content, context));
        }

        AiBeautyAnalysis analysis = new AiBeautyAnalysis();
        analysis.setAnalysisMode("REMOTE_API");
        analysis.setAssistantMessage(data.path("assistantMessage").asText(""));
        analysis.setStage(data.path("stage").asText("STYLE_DISCOVERY"));
        analysis.setSummaryText(data.path("summaryText").asText(data.path("summary").asText("")));
        analysis.setFaceSummary(data.path("faceSummary").asText(""));
        analysis.setDisclaimer(data.path("disclaimer").asText("AI 建议仅供参考，最终以到店和发型师面对面沟通为准。"));
        analysis.setCareTips(readStringList(data.path("careTips")));
        analysis.setNextQuestions(readStringList(data.path("nextQuestions")));
        analysis.setIntentTags(readStringList(data.path("intentTags")));
        analysis.setRawResponse(response);

        List<AiBeautyAnalysis.HairStyleAdvice> hairStyles = new ArrayList<>();
        for (JsonNode node : data.path("hairStyles")) {
            AiBeautyAnalysis.HairStyleAdvice advice = new AiBeautyAnalysis.HairStyleAdvice();
            advice.setName(node.path("name").asText(""));
            advice.setReason(node.path("reason").asText(""));
            advice.setMaintenanceLevel(node.path("maintenanceLevel").asText("MEDIUM"));
            advice.setFitScore(node.path("fitScore").asInt(80));
            if (StringUtils.hasText(advice.getName())) {
                hairStyles.add(advice);
            }
        }
        analysis.setHairStyles(hairStyles);

        List<AiBeautyAnalysis.ServiceRecommendation> services = new ArrayList<>();
        for (JsonNode node : data.path("serviceRecommendations")) {
            AiBeautyAnalysis.ServiceRecommendation recommendation = new AiBeautyAnalysis.ServiceRecommendation();
            if (node.hasNonNull("serviceItemId")) {
                recommendation.setServiceItemId(node.path("serviceItemId").asLong());
            }
            recommendation.setReason(node.path("reason").asText(""));
            recommendation.setFitScore(node.path("fitScore").asInt(80));
            if (recommendation.getServiceItemId() != null) {
                services.add(recommendation);
            }
        }
        analysis.setServiceRecommendations(services);

        List<AiBeautyAnalysis.ProductRecommendation> products = new ArrayList<>();
        for (JsonNode node : data.path("productRecommendations")) {
            AiBeautyAnalysis.ProductRecommendation recommendation = new AiBeautyAnalysis.ProductRecommendation();
            if (node.hasNonNull("productId")) {
                recommendation.setProductId(node.path("productId").asLong());
            }
            recommendation.setReason(node.path("reason").asText(""));
            recommendation.setFitScore(node.path("fitScore").asInt(80));
            if (recommendation.getProductId() != null) {
                products.add(recommendation);
            }
        }
        analysis.setProductRecommendations(products);
        return analysis;
    }

    private String repairAnalysisJson(String rawContent, AiConsultContext context) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", properties.getModel());
        payload.put("temperature", 0.1);
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("messages", List.of(
                Map.of("role", "system", "content", buildAnalysisRepairPrompt()),
                Map.of("role", "user", "content", """
                        原始模型输出：
                        %s

                        用户输入：
                        %s

                        是否上传照片：%s

                        你现在的任务不是重新分析，而是把原始输出修正成符合目标结构、能被 JSON 解析、字段完整、内容为简体中文的结果。
                        """.formatted(
                        rawContent,
                        context.getInputText() == null ? "" : context.getInputText(),
                        context.getImageBytes() == null || context.getImageBytes().length == 0 ? "否" : "是"
                ))
        ));
        String repaired = postChatCompletions(payload);
        return extractContent(objectMapper.readTree(repaired)
                .path("choices")
                .path(0)
                .path("message")
                .path("content"));
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private String extractContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            throw new BizException(4503, "AI response content is empty");
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : contentNode) {
                String text = item.path("text").asText("");
                if (!text.isEmpty()) {
                    builder.append(text);
                }
            }
            return builder.toString();
        }
        return contentNode.toString();
    }

    private String buildSystemPrompt() {
        if (StringUtils.hasText(properties.getModel())) {
            return """
                    你是 BFSM 门店系统内的中文美容美发 AI 顾问。你只能依据用户文字、上传照片中和当前问题相关的可见线索，以及系统提供的两个独立目录给建议：
                    - 服务/护理项目目录：只能用于 serviceRecommendations，只能输出 serviceItemId。
                    - 商城商品目录：只能用于 productRecommendations，只能输出 productId。

                    服务/护理项目 ID 和商品 ID 是两套独立编号，绝对不能合并、互相代替或跨字段填写。不要因为两个 ID 数字相同就认为它们是同一个东西。

                    你必须只返回严格 JSON，不允许输出 Markdown、代码块、解释文字或前后缀。字段结构必须完全匹配：
                    {
                      "assistantMessage": "string",
                      "stage": "STYLE_DISCOVERY|SERVICE_PLANNING|CARE_PLANNING",
                      "summaryText": "string",
                      "faceSummary": "string",
                      "hairStyles": [
                        {
                          "name": "string",
                          "reason": "string",
                          "maintenanceLevel": "LOW|MEDIUM|HIGH",
                          "fitScore": 0
                        }
                      ],
                      "serviceRecommendations": [
                        {
                          "serviceItemId": 0,
                          "reason": "string",
                          "fitScore": 0
                        }
                      ],
                      "productRecommendations": [
                        {
                          "productId": 0,
                          "reason": "string",
                          "fitScore": 0
                        }
                      ],
                      "careTips": ["string"],
                      "nextQuestions": ["string"],
                      "intentTags": ["string"],
                      "disclaimer": "string"
                    }

                    输出规则：
                    1. 所有自然语言内容必须使用简体中文。
                    2. assistantMessage 是直接展示给用户的话：先正面回答，再给 1 个自然追问；语气专业、克制，不营销、不夸大效果。
                    3. stage 默认 STYLE_DISCOVERY；用户问到预约、预算、项目、先做什么、护理项目、到店方案时填 SERVICE_PLANNING；用户问到居家护理、维持方法、洗护/护肤/定型商品时填 CARE_PLANNING。
                    4. 用户问发型、美发、烫染、刘海、修饰脸型时，hairStyles 给 2 到 3 个真实发型方向；用户主要问放松、美白、补水、舒缓、护肤保养时，hairStyles 返回空数组。
                    5. careTips 给 3 到 5 条可执行建议。
                    6. serviceRecommendations 可以为空。只有用户明确问门店项目、护理项目、预约、预算、顺序、到店方案时，才推荐 0 到 3 个服务/护理项目；serviceItemId 必须来自服务/护理项目目录。
                    7. productRecommendations 可以为空。只有用户明确问商品、产品、商城、购买、居家护理、日常维持、洗护、护肤、面膜、精华、发膜、精油、定型等商品诉求时，才推荐 0 到 3 个商城商品；productId 必须来自商城商品目录。
                    8. 严禁把护理项目当商品推荐，严禁把商品当护理项目推荐。如果不确定某个 ID 属于哪一类，返回空数组。
                    9. 推荐理由必须解释“为什么适合当前诉求”，不要写成广告语。
                    10. 不得识别人，不得推断年龄、性别身份、民族、健康、职业、性格、颜值；不做医学诊断。
                    11. 信息不足时不要编造，可在 assistantMessage 和 nextQuestions 中引导用户补充预算、频率、偏好、是否要到店或居家维持。
                    12. disclaimer 必须表达“AI 建议仅供参考，最终以到店沟通为准”。
                    """;
        }
        return """
                你是 BFSM 门店系统内的中文美容美发顾问，只能依据用户文字、上传照片里可见的相关线索，以及系统提供的服务/商品目录给建议。
                你的任务不仅包括发型方向，也包括放松、美白、补水、舒缓、头皮护理和居家保养等轻美容场景。
                你必须只返回严格 JSON，且字段名必须完全匹配下面结构，不允许输出任何额外说明、Markdown、代码块或前后缀：
                {
                  "assistantMessage": "string",
                  "stage": "STYLE_DISCOVERY|SERVICE_PLANNING|CARE_PLANNING",
                  "summaryText": "string",
                  "faceSummary": "string",
                  "hairStyles": [
                    {
                      "name": "string",
                      "reason": "string",
                      "maintenanceLevel": "LOW|MEDIUM|HIGH",
                      "fitScore": 0
                    }
                  ],
                  "serviceRecommendations": [
                    {
                      "serviceItemId": 0,
                      "reason": "string",
                      "fitScore": 0
                    }
                  ],
                  "productRecommendations": [
                    {
                      "productId": 0,
                      "reason": "string",
                      "fitScore": 0
                    }
                  ],
                  "careTips": ["string"],
                  "nextQuestions": ["string"],
                  "intentTags": ["string"],
                  "disclaimer": "string"
                }
                强约束：
                1. 所有自然语言内容必须使用简体中文。
                2. assistantMessage 是直接展示给用户的话，先正面回答，再给出 1 个自然的追问引导；语气要像专业顾问，不要营销腔，不要夸张承诺。
                3. stage 默认填 STYLE_DISCOVERY；当用户明确问预算、项目怎么选、先做什么、烫染护、预约或到店方案时填 SERVICE_PLANNING；当用户明确问居家护理、维持方法、造型品、洗护产品时填 CARE_PLANNING。
                4. 如果用户明确在问发型、美发、烫染、刘海、修饰脸型等问题，hairStyles 给 2 到 3 个真实发型方向；如果用户主要在问放松、美白、补水、舒缓、护理保养，hairStyles 返回空数组。
                5. hairStyles.reason 只在发型问题中填写，重点围绕脸部修饰、发量观感、长度、层次、刘海、打理成本，不要重复套话。
                6. fitScore 范围是 0 到 100，maintenanceLevel 只能是 LOW、MEDIUM、HIGH。
                7. 如果是发型问题，faceSummary 只能描述与发型相关的可见线索；如果是护理、放松、亮肤、补水等问题，faceSummary 用保守方式概括当前诉求，不要硬做发型判断。
                8. 不得识别人、不推断年龄、性别身份、民族、健康、职业、性格、颜值，也不能做医学判断。
                9. summaryText 应概括本次判断核心、推荐方向和下一步沟通重点，长度控制在 2 到 3 句。
                10. careTips 给 3 到 5 条，必须是可执行建议。发型问题可写吹风方向、刘海整理、卷度维持；护理问题可写补水、舒缓、维持频率、到店沟通点。
                11. nextQuestions 给 3 条可点击短问题，必须是中文短句，贴近当前上下文。
                12. intentTags 给 2 到 5 个中文标签，例如“发型方向”“脸部修饰”“日常通勤”“服务项目”“居家护理”“预算规划”。
                13. serviceRecommendations 可以为空。只有用户明确问项目、预算、顺序、预约、烫染护，或明确表达放松、美白、补水、舒缓、护理保养等到店诉求时，才允许推荐 0 到 2 个服务；serviceItemId 必须严格来自提供的服务目录，不能编造。
                14. productRecommendations 可以为空。只有用户明确问洗护、定型、居家维护、商品选择，或明确表达放松、舒缓、解压、面膜、精华、保湿、精油等商品诉求时，才允许推荐 0 到 2 个商品；productId 必须严格来自提供的商品目录，不能编造。
                15. 如果信息不足，不要瞎编。可以在 assistantMessage 和 nextQuestions 里引导用户补充“平时是否烫染”“想不想留刘海”“每天愿意花多久打理”等关键信息。
                16. disclaimer 必须明确表达“仅供参考，最终以到店和发型师面对面沟通为准”。
                """;
    }

    private String buildUserPrompt(AiConsultContext context) throws Exception {
        if (StringUtils.hasText(properties.getModel())) {
            return """
                    本轮用户输入：
                    %s

                    是否上传照片：%s

                    服务/护理项目目录（只能从这里选择 serviceItemId，只能放进 serviceRecommendations）：
                    %s

                    商城商品目录（只能从这里选择 productId，只能放进 productRecommendations）：
                    %s

                    执行要求：
                    1. 先判断用户本轮真正意图：发型方向、门店护理项目、商城商品、居家护理建议，或普通补充信息。
                    2. 服务/护理项目和商城商品必须分开处理：serviceItemId 不得出现在 productRecommendations，productId 不得出现在 serviceRecommendations。
                    3. 如果用户问“护理”，优先理解为护理方向或门店护理项目；只有用户明确说商品、产品、买、购买、商城、居家护理、日常维持、面膜、精华、发膜、精油、定型等，才输出 productRecommendations。
                    4. 如果用户主要问“商品推荐”，必须优先检查商城商品目录，并在 productRecommendations 中给出 1 到 3 个商品；不要把服务/护理项目 ID 填到 productId。
                    5. 如果用户主要问“项目/预约/到店/先做什么/预算/护理项目”，必须优先检查服务/护理项目目录，并在 serviceRecommendations 中给出 1 到 3 个项目；不要把商品 ID 填到 serviceItemId。
                    6. 如果没有匹配目录项，返回空数组并在 assistantMessage 里说明需要补充偏好，不要编造 ID。
                    7. 没有上传照片时，只能基于文字诉求做保守建议；非发型问题不要强行提脸型或发质。
                    8. 上传了照片时，只描述照片里与当前问题相关的可见线索，不做身份和敏感属性推断。
                    9. 所有输出必须是简体中文 JSON，不要输出解释。
                    """.formatted(
                    context.getInputText() == null ? "" : context.getInputText(),
                    context.getImageBytes() == null || context.getImageBytes().length == 0 ? "否" : "是",
                    formatServiceCatalog(context.getServiceCatalog()),
                    formatProductCatalog(context.getProductCatalog())
            );
        }
        return """
                本轮用户输入：
                %s

                是否上传照片：%s

                服务目录（只能从这里选 serviceItemId）：
                %s

                商品目录（只能从这里选 productId）：
                %s

                执行要求：
                1. 如果用户主要在问“适合什么发型、刘海、长度、层次、显脸小、通勤好打理”，重点给发型方向，不要硬推门店项目。
                2. 如果用户主要在问“放松、美白、补水、舒缓、保养、肤色状态”，重点给护理方向和需求澄清，不要硬转成发型分析。
                3. 如果没有上传照片，只能基于文字诉求做保守建议；非发型问题不需要强行提脸型或发质。
                4. 如果上传了照片，只能描述照片里与当前问题相关的可见线索，不能做身份和敏感属性推断。
                5. 只有当项目或商品与用户诉求强相关时，才填写 serviceRecommendations 或 productRecommendations，否则返回空数组。
                6. 所有输出必须是简体中文 JSON，不要输出解释。
                """.formatted(
                context.getInputText() == null ? "" : context.getInputText(),
                context.getImageBytes() == null || context.getImageBytes().length == 0 ? "否" : "是",
                formatServiceCatalog(context.getServiceCatalog()),
                formatProductCatalog(context.getProductCatalog())
        );
    }

    private String buildChatSystemPrompt() {
        return """
                你是 BFSM 门店系统里的中文 AI 顾问，负责回答追问。
                回答规则：
                1. 必须用简体中文，先直接回答用户这次提问，再补 1 到 3 条最关键建议。
                2. 如果用户在问发型、美发问题，优先回答发型方向、刘海、长度、层次、打理方法。
                3. 如果用户在问放松、美白、补水、舒缓、保养等护理问题，优先回答护理方向、保守建议和补充信息，不要硬转成发型话题。
                4. 如果用户没有问项目、预算、预约、烫染护或商品，就不要主动展开推销服务和商品。
                4. 如果用户追问项目或商品，只能依据提供的目录推荐，不能编造名称、价格、效果。
                5. 不得识别人，不得推断年龄、性别身份、民族、健康、职业、性格、颜值。
                6. 不做医学判断，不夸大效果，不承诺绝对结果。
                7. 话风要像真人顾问，简洁、自然、专业，避免模板腔和重复免责声明。
                8. 只有在涉及最终方案、到店落地、烫染护风险时，才自然提醒一句“最终以到店沟通为准”。
                """;
    }

    private String buildAnalysisRepairPrompt() {
        return """
                你是 AI 输出修复器。
                你必须把输入内容修正为合法、可解析、字段完整的 JSON 对象，字段结构必须完全是：
                assistantMessage, stage, summaryText, faceSummary, hairStyles, serviceRecommendations,
                productRecommendations, careTips, nextQuestions, intentTags, disclaimer。
                修复规则：
                1. 只输出 JSON，不要有解释。
                2. 所有自然语言必须是简体中文。
                3. 如果原文缺字段，按最保守方式补齐。
                4. hairStyles 至少 2 个，最多 3 个；careTips 3 到 5 条；nextQuestions 3 条。
                5. stage 只能是 STYLE_DISCOVERY、SERVICE_PLANNING、CARE_PLANNING。
                6. serviceRecommendations 和 productRecommendations 没有可靠 id 时返回空数组。
                7. disclaimer 必须明确“仅供参考，最终以到店和发型师面对面沟通为准”。
                """;
    }

    private String buildReplyRepairPrompt() {
        return """
                你是门店 AI 顾问回复修正器。
                任务：把一段可能过空、过营销、英文过多、跑题或不自然的回复，修正成更适合直接展示给用户的最终中文回答。
                要求：
                1. 只输出修正后的最终回答，不要解释你做了什么。
                2. 必须使用简体中文。
                3. 先直接回答用户问题，再补 1 到 3 条关键建议。
                4. 不要营销腔，不要夸张承诺，不要空话套话。
                5. 不要编造目录外项目和商品。
                6. 如信息不足，要明确说缺什么信息。
                7. 只有涉及最终落地方案时，才简短提醒“最终以到店沟通为准”。
                """;
    }

    private String buildChatContextPrompt(String recordContext,
                                          List<AiServiceCatalogItem> serviceCatalog,
                                          List<AiProductCatalogItem> productCatalog) throws Exception {
        return """
                以下是当前会话已确认的信息，请先基于这些信息回答，不要忽略用户上下文：
                %s

                可用服务目录（仅当用户明确问到时才可引用）：
                %s

                可用商品目录（仅当用户明确问到居家护理/商品时才可引用）：
                %s

                回答时请遵守：
                - 用户问什么就先答什么，不要把所有分析结果重说一遍。
                - 如果当前信息不足，明确指出还缺什么信息。
                - 如果给方案，请给“更稳妥”和“更明显变化”两种方向中的一种或两种，帮助用户决策。
                """.formatted(recordContext, formatServiceCatalog(serviceCatalog), formatProductCatalog(productCatalog));
    }

    private String buildReplyRepairContext(String recordContext,
                                           String question,
                                           String draftAnswer,
                                           List<AiServiceCatalogItem> serviceCatalog,
                                           List<AiProductCatalogItem> productCatalog) throws Exception {
        return """
                会话上下文：
                %s

                用户这次的问题：
                %s

                原始回复草稿：
                %s

                可用服务目录：
                %s

                可用商品目录：
                %s
                """.formatted(
                recordContext,
                question,
                draftAnswer,
                formatServiceCatalog(serviceCatalog),
                formatProductCatalog(productCatalog)
        );
    }

    private String formatServiceCatalog(List<AiServiceCatalogItem> serviceCatalog) throws Exception {
        List<Map<String, Object>> services = new ArrayList<>();
        for (AiServiceCatalogItem item : serviceCatalog) {
            Map<String, Object> service = new HashMap<>();
            service.put("catalogType", "SERVICE_ITEM");
            service.put("serviceItemId", item.getServiceItemId());
            service.put("serviceName", item.getServiceName());
            service.put("categoryName", item.getCategoryName());
            service.put("durationMin", item.getDurationMin());
            service.put("price", item.getPrice());
            services.add(service);
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(services);
    }

    private String formatProductCatalog(List<AiProductCatalogItem> productCatalog) throws Exception {
        List<Map<String, Object>> products = new ArrayList<>();
        for (AiProductCatalogItem item : productCatalog) {
            Map<String, Object> product = new HashMap<>();
            product.put("catalogType", "PRODUCT");
            product.put("productId", item.getProductId());
            product.put("productName", item.getProductName());
            product.put("categoryName", item.getCategoryName());
            product.put("price", item.getPrice());
            product.put("stock", item.getStock());
            products.add(product);
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(products);
    }
}
