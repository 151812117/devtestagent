package com.example.agent.agent.sub;

import com.example.agent.model.AgentRequest;
import com.example.agent.model.ExecutionResult;
import com.example.agent.model.IntentParseResult;
import com.example.agent.service.LlmService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 菜单推荐智能体
 * 用于第三方研发支持系统的菜单推荐
 */
@Slf4j
@Component
public class MenuRecommendationAgent implements SubAgent {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final Resource systemPrompt;

    // 菜单知识库
    private static final Map<String, List<MenuInfo>> MENU_KNOWLEDGE_BASE = new HashMap<>();

    static {
        // 接口测试菜单
        MENU_KNOWLEDGE_BASE.put("接口测试", Arrays.asList(
            new MenuInfo("接口测试->创建批次", "创建新的测试批次，用于组织和管理测试案例", 
                Arrays.asList("创建批次", "新建批次", "批次管理")),
            new MenuInfo("接口测试->添加案例到批次", "将测试案例添加到指定的测试批次中", 
                Arrays.asList("添加案例", "加入案例", "案例管理")),
            new MenuInfo("接口测试->执行批次", "执行指定批次的所有测试案例", 
                Arrays.asList("执行批次", "运行批次", "开始测试")),
            new MenuInfo("接口测试->分析执行结果", "分析批次的执行结果，生成测试报告和统计信息", 
                Arrays.asList("分析结果", "查看报告", "测试结果"))
        ));

        // 环境管理菜单
        MENU_KNOWLEDGE_BASE.put("环境管理", Arrays.asList(
            new MenuInfo("环境管理->申请资源", "申请环境资源用于测试", 
                Arrays.asList("申请环境", "创建环境", "资源申请")),
            new MenuInfo("环境管理->回收资源", "回收环境资源，释放占用", 
                Arrays.asList("回收环境", "释放资源", "删除环境")),
            new MenuInfo("环境管理->查看资源状态", "查看当前环境资源的状态和使用情况", 
                Arrays.asList("查看环境", "资源状态", "环境监控")),
            new MenuInfo("环境管理->资源续期", "延长环境资源的使用时间", 
                Arrays.asList("续期", "延长", "扩容"))
        ));

        // 测试管理菜单
        MENU_KNOWLEDGE_BASE.put("测试管理", Arrays.asList(
            new MenuInfo("测试管理->创建测试计划", "创建新的测试计划", 
                Arrays.asList("测试计划", "计划管理")),
            new MenuInfo("测试管理->测试用例库", "管理测试用例库", 
                Arrays.asList("用例库", "案例库", "测试案例")),
            new MenuInfo("测试管理->测试报告", "查看和导出测试报告", 
                Arrays.asList("报告", "导出报告"))
        ));
    }

    public MenuRecommendationAgent(
            LlmService llmService,
            ObjectMapper objectMapper,
            @Qualifier("menuRecommendationSystemPrompt") Resource systemPrompt) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String getName() {
        return "MenuRecommendationAgent";
    }

    @Override
    public Mono<IntentParseResult> parseIntent(AgentRequest request) {
        log.info("[MenuRecommendationAgent] Parsing intent for query: {}", request.getUserQuery());

        return Mono.fromCallable(() -> {
            // 1. 构建提示词
            String prompt = buildParsePrompt(request);
            String systemPromptContent = getSystemPromptContent();
            
            // 2. 调用 LLM 解析意图
            String response = llmService.generate(systemPromptContent, prompt).block();

            log.info("[MenuRecommendationAgent] LLM response: {}", response);

            // 3. 解析 LLM 输出
            return parseLLMResponse(response, request.getUserQuery());
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @Override
    public Mono<ExecutionResult> execute(AgentRequest request) {
        // 菜单推荐主要是返回推荐结果，不需要实际执行操作
        String action = getActionFromRequest(request);
        log.info("[MenuRecommendationAgent] Executing action: {}", action);

        Map<String, Object> result = new HashMap<>();
        result.put("action", action);
        result.put("message", "菜单推荐完成，请查看推荐结果");
        result.put("recommendedMenus", request.getParameters() != null ? 
            request.getParameters().get("recommendedMenus") : null);

        return Mono.just(ExecutionResult.builder()
            .success(true)
            .message("菜单推荐完成")
            .data(result)
            .action(action)
            .build());
    }

    /**
     * 构建意图解析提示词
     */
    private String buildParsePrompt(AgentRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请分析以下用户需求，推荐最合适的系统菜单：\n\n");
        prompt.append("用户输入：").append(request.getUserQuery()).append("\n\n");
        
        if (request.getMemory() != null && !request.getMemory().isEmpty()) {
            prompt.append("历史记忆：\n").append(request.getMemory()).append("\n\n");
        }

        prompt.append("可用的菜单分类：\n");
        prompt.append("1. 接口测试 - 包括：创建批次、添加案例到批次、执行批次、分析执行结果\n");
        prompt.append("2. 环境管理 - 包括：申请资源、回收资源、查看资源状态、资源续期\n");
        prompt.append("3. 测试管理 - 包括：创建测试计划、测试用例库、测试报告\n\n");
        
        prompt.append("请以 JSON 格式返回解析结果，包含以下字段：\n");
        prompt.append("- action: 操作类型（recommendMenu）\n");
        prompt.append("- target: 推荐的目标菜单路径（如：接口测试->创建批次）\n");
        prompt.append("- parameters: 提取的参数\n");
        prompt.append("- menuRecommendation: 菜单推荐详情对象，包含：\n");
        prompt.append("  - recommendedMenus: 推荐的菜单列表，每个菜单包含 path（路径）、description（描述）、confidence（置信度 0-1）\n");
        prompt.append("  - reason: 推荐理由\n");
        prompt.append("- think: 你的思考过程\n");
        prompt.append("- missingParameters: 需要补充的参数名和说明\n");

        return prompt.toString();
    }

    /**
     * 解析 LLM 响应
     */
    private IntentParseResult parseLLMResponse(String response, String originalQuery) {
        try {
            // 尝试提取 JSON 部分
            String jsonStr = extractJson(response);
            JsonNode jsonNode = objectMapper.readTree(jsonStr);

            String action = jsonNode.has("action") ? jsonNode.get("action").asText().trim() : "recommendMenu";
            String target = jsonNode.has("target") ? jsonNode.get("target").asText().trim() : "";
            
            Map<String, Object> parameters = new HashMap<>();
            if (jsonNode.has("parameters")) {
                parameters = objectMapper.convertValue(jsonNode.get("parameters"), 
                    new TypeReference<Map<String, Object>>() {});
            }
            
            // 提取菜单推荐信息
            Map<String, Object> menuRecommendation = new HashMap<>();
            if (jsonNode.has("menuRecommendation")) {
                menuRecommendation = objectMapper.convertValue(jsonNode.get("menuRecommendation"), 
                    new TypeReference<Map<String, Object>>() {});
            } else {
                // 如果没有返回菜单推荐，使用关键词匹配生成推荐
                menuRecommendation = generateMenuRecommendation(originalQuery);
            }
            parameters.put("menuRecommendation", menuRecommendation);
            
            // 提取思考过程
            String think = jsonNode.has("think") ? jsonNode.get("think").asText() : generateThink(originalQuery);
            
            // 提取需要补充的参数
            Map<String, String> missingParameters = new HashMap<>();
            if (jsonNode.has("missingParameters")) {
                missingParameters = objectMapper.convertValue(jsonNode.get("missingParameters"), 
                    new TypeReference<Map<String, String>>() {});
            }

            // 设置默认值
            setDefaultValues(action, parameters);

            Map<String, String> paramDescriptions = buildParameterDescriptions(action);
            
            // 根据缺失参数数量判断确认消息
            String confirmationMessage;
            if (missingParameters.isEmpty()) {
                confirmationMessage = "请确认以上菜单推荐是否正确";
            } else if (missingParameters.size() == 1) {
                String paramName = missingParameters.keySet().iterator().next();
                String paramDesc = missingParameters.get(paramName);
                confirmationMessage = "请补充" + paramDesc + "信息";
            } else {
                confirmationMessage = "请补充以下参数信息";
            }

            return IntentParseResult.builder()
                .action(action)
                .target(target)
                .parameters(parameters)
                .parameterDescriptions(paramDescriptions)
                .confidence(0.9)
                .originalQuery(originalQuery)
                .needConfirmation(true)
                .confirmationMessage(confirmationMessage)
                .think(think)
                .missingParameters(missingParameters)
                .build();

        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
            return createDefaultParseResult(originalQuery);
        }
    }

    /**
     * 根据关键词生成菜单推荐
     */
    private Map<String, Object> generateMenuRecommendation(String query) {
        Map<String, Object> recommendation = new HashMap<>();
        List<Map<String, Object>> recommendedMenus = new ArrayList<>();
        StringBuilder reason = new StringBuilder();
        
        String lowerQuery = query.toLowerCase();
        
        // 遍历菜单知识库进行匹配
        for (Map.Entry<String, List<MenuInfo>> entry : MENU_KNOWLEDGE_BASE.entrySet()) {
            String category = entry.getKey();
            List<MenuInfo> menus = entry.getValue();
            
            for (MenuInfo menu : menus) {
                double confidence = calculateConfidence(lowerQuery, menu);
                if (confidence > 0.3) {
                    Map<String, Object> menuMap = new HashMap<>();
                    menuMap.put("path", menu.path);
                    menuMap.put("description", menu.description);
                    menuMap.put("confidence", confidence);
                    recommendedMenus.add(menuMap);
                    
                    if (reason.length() > 0) {
                        reason.append("；");
                    }
                    reason.append("匹配到菜单：").append(menu.path);
                }
            }
        }
        
        // 按置信度排序
        recommendedMenus.sort((a, b) -> 
            Double.compare((Double) b.get("confidence"), (Double) a.get("confidence")));
        
        // 只保留前5个推荐
        if (recommendedMenus.size() > 5) {
            recommendedMenus = recommendedMenus.subList(0, 5);
        }
        
        recommendation.put("recommendedMenus", recommendedMenus);
        recommendation.put("reason", reason.length() > 0 ? reason.toString() : "根据您的需求推荐相关菜单");
        
        return recommendation;
    }

    /**
     * 计算匹配置信度
     */
    private double calculateConfidence(String query, MenuInfo menu) {
        double confidence = 0.0;
        
        // 检查菜单路径匹配
        if (query.contains(menu.path.toLowerCase().replace("->", ""))) {
            confidence += 0.5;
        }
        
        // 检查关键词匹配
        for (String keyword : menu.keywords) {
            if (query.contains(keyword.toLowerCase())) {
                confidence += 0.3;
            }
        }
        
        // 检查描述匹配（简单分词匹配）
        String[] descWords = menu.description.toLowerCase().split("");
        for (String word : descWords) {
            if (word.length() > 1 && query.contains(word)) {
                confidence += 0.1;
            }
        }
        
        return Math.min(confidence, 1.0);
    }

    /**
     * 生成思考过程
     */
    private String generateThink(String query) {
        StringBuilder think = new StringBuilder();
        think.append("根据您的需求\"").append(query).append("\"，我分析了可用的系统菜单。");
        think.append("通过关键词匹配，我为您推荐了最相关的菜单选项。");
        think.append("请确认推荐的菜单是否符合您的需求，或者您可以描述更具体的需求以获得更精准的推荐。");
        return think.toString();
    }

    /**
     * 从响应中提取 JSON
     */
    private String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    /**
     * 构建参数说明
     */
    private Map<String, String> buildParameterDescriptions(String action) {
        Map<String, String> descriptions = new HashMap<>();
        
        descriptions.put("targetMenu", "目标菜单路径");
        descriptions.put("menuCategory", "菜单分类");
        descriptions.put("requirementDetail", "需求详情");
        
        return descriptions;
    }

    /**
     * 设置默认值
     */
    private void setDefaultValues(String action, Map<String, Object> parameters) {
        // 菜单推荐不需要特别设置默认值
    }

    /**
     * 创建默认解析结果
     */
    private IntentParseResult createDefaultParseResult(String originalQuery) {
        Map<String, Object> parameters = new HashMap<>();
        
        // 使用关键词匹配生成推荐
        Map<String, Object> menuRecommendation = generateMenuRecommendation(originalQuery);
        parameters.put("menuRecommendation", menuRecommendation);
        
        return IntentParseResult.builder()
            .action("recommendMenu")
            .target("")
            .parameters(parameters)
            .confidence(0.7)
            .originalQuery(originalQuery)
            .needConfirmation(true)
            .confirmationMessage("已根据您的需求推荐相关菜单，请确认或修改")
            .think("从用户输入中提取了关键词，匹配了相关菜单")
            .missingParameters(new HashMap<>())
            .build();
    }

    /**
     * 从请求中获取操作类型
     */
    private String getActionFromRequest(AgentRequest request) {
        if (request.getParameters() != null && request.getParameters().containsKey("action")) {
            return request.getParameters().get("action").toString().trim();
        }
        return "recommendMenu";
    }

    private String getSystemPromptContent() {
        try {
            return new String(systemPrompt.getInputStream().readAllBytes());
        } catch (Exception e) {
            log.error("Failed to read system prompt", e);
            return "你是一个菜单推荐助手，帮助用户找到合适的系统功能菜单。";
        }
    }

    /**
     * 菜单信息内部类
     */
    private static class MenuInfo {
        final String path;
        final String description;
        final List<String> keywords;

        MenuInfo(String path, String description, List<String> keywords) {
            this.path = path;
            this.description = description;
            this.keywords = keywords;
        }
    }
}
