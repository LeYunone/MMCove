package com.mmcove.agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.entity.PipelineStage;
import com.mmcove.agent.common.model.entity.PipelineStageStep;
import com.mmcove.agent.common.model.entity.PipelineTemplate;
import com.mmcove.agent.common.model.entity.ProductLinePipeline;
import com.mmcove.agent.common.model.entity.VibeRun;
import com.mmcove.agent.common.model.entity.VibeRunArtifact;
import com.mmcove.agent.infra.persistence.repository.PipelineStageRepository;
import com.mmcove.agent.infra.persistence.repository.PipelineStageStepRepository;
import com.mmcove.agent.infra.persistence.repository.PipelineTemplateRepository;
import com.mmcove.agent.infra.persistence.repository.ProductLinePipelineRepository;
import com.mmcove.agent.infra.persistence.repository.VibeRunRepository;
import com.mmcove.agent.vibe.PipelineRegistry;
import com.mmcove.agent.vibe.PipelineYamlService;
import com.mmcove.agent.vibe.VibePromptComposerService;
import com.mmcove.agent.vibe.VibeRunService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vibe 流水线管理控制器:流水线/阶段库 CRUD、产品线绑定、YAML 导入导出、
 * compose 调试预览、执行实例轨迹。
 *
 * <p>写操作后调 {@code pipelineRegistry.refreshCache()}(项目惯例,见 AgentController)。
 * 鉴权经 {@code AuthInterceptor.isAdminPath}(需追加 /api/vibe 前缀)。
 *
 * @since 2026-09-11
 */
@RestController
@RequestMapping("/api/vibe")
@RequiredArgsConstructor
public class VibeController {

    private final PipelineTemplateRepository pipelineTemplateRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final PipelineStageStepRepository pipelineStageStepRepository;
    private final ProductLinePipelineRepository productLinePipelineRepository;
    private final VibeRunRepository vibeRunRepository;
    private final VibeRunService vibeRunService;
    private final PipelineRegistry pipelineRegistry;
    private final PipelineYamlService pipelineYamlService;
    private final VibePromptComposerService composerService;

    // ==================== 流水线 ====================

    /** 流水线列表(含步骤序列展开;传 productLineId=该线私有+全局共享,不传=全量) */
    @GetMapping("/pipelines")
    public ApiResponse<List<Map<String, Object>>> listPipelines(
            @RequestParam(required = false) Long productLineId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PipelineTemplate template : pipelineTemplateRepository.findAll()) {
            if (productLineId != null && template.getProductLineId() != null
                    && !productLineId.equals(template.getProductLineId())) {
                continue;
            }
            result.add(pipelineDetail(template));
        }
        return ApiResponse.success(result);
    }

    /** 流水线详情(含步骤序列展开) */
    @GetMapping("/pipelines/{id:\\d+}")
    public ApiResponse<Map<String, Object>> getPipeline(@PathVariable Long id) {
        return pipelineTemplateRepository.findById(id)
                .map(this::pipelineDetail)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "流水线不存在"));
    }

    /** 创建流水线(body 含 steps 阶段序列) */
    @PostMapping("/pipelines")
    public ApiResponse<Map<String, Object>> createPipeline(@RequestBody PipelineUpsertRequest request) {
        String error = validateUpsert(request, true);
        if (error != null) {
            return ApiResponse.error(400, error);
        }
        PipelineTemplate template = request.toTemplate();
        template.setStatus(1);
        pipelineTemplateRepository.insert(template);
        replaceSteps(template.getId(), request.getSteps());
        pipelineRegistry.refreshCache();
        return ApiResponse.success(pipelineDetail(template));
    }

    /** 更新流水线(steps 全量替换;intent_type 唯一,冲突拒绝) */
    @PutMapping("/pipelines/{id:\\d+}")
    public ApiResponse<Map<String, Object>> updatePipeline(@PathVariable Long id,
            @RequestBody PipelineUpsertRequest request) {
        PipelineTemplate existing = pipelineTemplateRepository.findById(id).orElse(null);
        if (existing == null) {
            return ApiResponse.error(404, "流水线不存在");
        }
        String error = validateUpsert(request, false);
        if (error != null) {
            return ApiResponse.error(400, error);
        }
        PipelineTemplate template = request.toTemplate();
        template.setId(id);
        template.setPipelineCode(existing.getPipelineCode());
        template.setProductLineId(existing.getProductLineId());
        template.setCreatedAt(existing.getCreatedAt());
        if (template.getStatus() == null) {
            template.setStatus(existing.getStatus());
        }
        pipelineTemplateRepository.update(template);
        if (request.getSteps() != null) {
            replaceSteps(id, request.getSteps());
        }
        pipelineRegistry.refreshCache();
        return ApiResponse.success(pipelineDetail(template));
    }

    /** 删除流水线(有产品线绑定或执行实例时拒绝) */
    @DeleteMapping("/pipelines/{id:\\d+}")
    public ApiResponse<Void> deletePipeline(@PathVariable Long id) {
        if (pipelineTemplateRepository.findById(id).isEmpty()) {
            return ApiResponse.error(404, "流水线不存在");
        }
        List<ProductLinePipeline> bindings = productLinePipelineRepository.findByPipelineId(id);
        if (!bindings.isEmpty()) {
            return ApiResponse.error(400, "该流水线被 " + bindings.size() + " 条产品线绑定,请先解除绑定");
        }
        if (!vibeRunRepository.findByPipelineId(id).isEmpty()) {
            return ApiResponse.error(400, "该流水线存在历史执行实例,不允许删除(可停用)");
        }
        pipelineStageStepRepository.deleteByPipelineId(id);
        pipelineTemplateRepository.deleteById(id);
        pipelineRegistry.refreshCache();
        return ApiResponse.success();
    }

    /**
     * 把(共享)流水线复制为某产品线的私有副本:此后本线 compose 私有优先,
     * 随便改不影响其他产品线。code 加线后缀防冲突。
     */
    @PostMapping("/pipelines/{id:\\d+}/fork")
    public ApiResponse<Map<String, Object>> forkPipeline(@PathVariable Long id,
            @RequestParam Long productLineId) {
        PipelineTemplate source = pipelineTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("流水线不存在: " + id));
        if (productLineId.equals(source.getProductLineId())) {
            return ApiResponse.error(400, "该流水线已是此产品线的私有副本");
        }
        if (pipelineTemplateRepository.findPrivateByIntentAndLine(source.getIntentType(), productLineId).isPresent()) {
            return ApiResponse.error(400, "本产品线已有该意图的私有副本,请在流水线列表中直接编排");
        }
        String newCode = source.getPipelineCode() + "-l" + productLineId;
        if (pipelineTemplateRepository.findByCode(newCode).isPresent()) {
            return ApiResponse.error(400, "副本编码已存在: " + newCode);
        }
        PipelineTemplate copy = new PipelineTemplate();
        copy.setPipelineCode(newCode);
        copy.setName(source.getName() + "(本线副本)");
        copy.setIntentType(source.getIntentType());
        copy.setProductLineId(productLineId);
        copy.setDescription(source.getDescription());
        copy.setKeywords(source.getKeywords());
        copy.setExecutionConvention(source.getExecutionConvention());
        copy.setStatus(1);
        pipelineTemplateRepository.insert(copy);

        List<PipelineStageStep> steps = new ArrayList<>();
        for (PipelineStageStep step : pipelineStageStepRepository.findByPipelineIdOrderBySeq(id)) {
            PipelineStageStep s = new PipelineStageStep();
            s.setStageId(step.getStageId());
            s.setSeq(step.getSeq());
            s.setStepParams(step.getStepParams());
            steps.add(s);
        }
        pipelineStageStepRepository.replaceAll(copy.getId(), steps);
        pipelineRegistry.refreshCache();
        return ApiResponse.success(pipelineDetail(copy));
    }

    // ==================== YAML 导入导出 ====================

    /** 导出流水线为自包含 YAML */
    @GetMapping("/pipelines/{id:\\d+}/export")
    public ApiResponse<String> exportPipeline(@PathVariable Long id) {
        try {
            return ApiResponse.success(pipelineYamlService.exportToYaml(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 导入 YAML(支持 extends 继承语法,导入时物化) */
    @PostMapping("/pipelines/import")
    public ApiResponse<PipelineYamlService.ImportResult> importPipeline(@RequestBody String yamlText) {
        try {
            return ApiResponse.success(pipelineYamlService.importFromYaml(yamlText));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    // ==================== 阶段库 ====================

    @GetMapping("/stages")
    public ApiResponse<List<PipelineStage>> listStages() {
        return ApiResponse.success(pipelineStageRepository.findAllActive());
    }

    @PostMapping("/stages")
    public ApiResponse<PipelineStage> createStage(@RequestBody PipelineStage stage) {
        if (!StringUtils.hasText(stage.getStageCode())) {
            return ApiResponse.error(400, "阶段编码(stageCode)不能为空");
        }
        if (!StringUtils.hasText(stage.getName())) {
            return ApiResponse.error(400, "阶段名称(name)不能为空");
        }
        if (pipelineStageRepository.findByCode(stage.getStageCode().trim()).isPresent()) {
            return ApiResponse.error(400, "阶段编码已存在: " + stage.getStageCode());
        }
        stage.setStageCode(stage.getStageCode().trim());
        if (stage.getSubAgentCount() == null || stage.getSubAgentCount() < 1) {
            stage.setSubAgentCount(1);
        }
        stage.setStatus(1);
        pipelineStageRepository.insert(stage);
        pipelineRegistry.refreshCache();
        return ApiResponse.success(stage);
    }

    @PutMapping("/stages/{id:\\d+}")
    public ApiResponse<PipelineStage> updateStage(@PathVariable Long id, @RequestBody PipelineStage stage) {
        return pipelineStageRepository.findById(id)
                .map(existing -> {
                    stage.setId(id);
                    stage.setStageCode(existing.getStageCode());
                    stage.setCreatedAt(existing.getCreatedAt());
                    if (stage.getStatus() == null) {
                        stage.setStatus(existing.getStatus());
                    }
                    if (stage.getSubAgentCount() == null || stage.getSubAgentCount() < 1) {
                        stage.setSubAgentCount(1);
                    }
                    pipelineStageRepository.update(stage);
                    pipelineRegistry.refreshCache();
                    return ApiResponse.success(stage);
                })
                .orElse(ApiResponse.error(404, "阶段不存在"));
    }

    /** 删除阶段(被流水线引用时拒绝——阶段全局共享,防改一处坏全线) */
    @DeleteMapping("/stages/{id:\\d+}")
    public ApiResponse<Void> deleteStage(@PathVariable Long id) {
        if (pipelineStageRepository.findById(id).isEmpty()) {
            return ApiResponse.error(404, "阶段不存在");
        }
        long refCount = pipelineStageStepRepository.countByStageId(id);
        if (refCount > 0) {
            return ApiResponse.error(400, "该阶段被 " + refCount + " 条流水线引用(全局共享),不允许删除,可停用");
        }
        pipelineStageRepository.deleteById(id);
        pipelineRegistry.refreshCache();
        return ApiResponse.success();
    }

    // ==================== 产品线绑定 ====================

    /** 某产品线的流水线绑定+覆盖列表 */
    @GetMapping("/product-lines/{id:\\d+}/pipeline-bindings")
    public ApiResponse<List<ProductLinePipeline>> listBindings(@PathVariable Long id) {
        return ApiResponse.success(productLinePipelineRepository.findByProductLineId(id));
    }

    /** 整存整取某产品线的流水线绑定(全量替换;stage_overrides 键校验,未知键拒绝防静默失效) */
    @PutMapping("/product-lines/{id:\\d+}/pipeline-bindings")
    public ApiResponse<List<ProductLinePipeline>> replaceBindings(@PathVariable Long id,
            @RequestBody List<ProductLinePipeline> bindings) {
        if (bindings != null) {
            for (ProductLinePipeline binding : bindings) {
                String error = validateStageOverrides(binding);
                if (error != null) {
                    return ApiResponse.error(400, error);
                }
            }
        }
        productLinePipelineRepository.replaceAll(id, bindings);
        pipelineRegistry.refreshCache();
        return ApiResponse.success(productLinePipelineRepository.findByProductLineId(id));
    }

    /** 校验 stage_overrides 的键都是目标流水线序列中的 stageCode(写错 code 静默忽略是配置事故源) */
    private String validateStageOverrides(ProductLinePipeline binding) {
        if (binding.getStageOverrides() == null || binding.getStageOverrides().isBlank()) {
            return null;
        }
        java.util.Set<String> validCodes = new java.util.HashSet<>();
        for (PipelineStageStep step : pipelineStageStepRepository
                .findByPipelineIdOrderBySeq(binding.getPipelineId())) {
            pipelineStageRepository.findById(step.getStageId())
                    .ifPresent(stage -> validCodes.add(stage.getStageCode()));
        }
        java.util.Set<String> keys = new java.util.HashSet<>();
        try {
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(binding.getStageOverrides()).fieldNames()
                    .forEachRemaining(keys::add);
        } catch (Exception e) {
            return "stage_overrides 不是合法 JSON: " + e.getMessage();
        }
        keys.removeAll(validCodes);
        if (!keys.isEmpty()) {
            return "stage_overrides 含未知阶段编码(将静默失效,请修正): " + keys
                    + ";流水线可用阶段: " + validCodes;
        }
        return null;
    }

    // ==================== 预览与执行轨迹 ====================

    /**
     * compose 全链路调试预览(JWT 无产品线身份,productLineId 必传;dryRun 不落库,
     * 不建执行实例——防止管理员预览造僵尸 RUNNING 污染恢复兜底链路)。
     */
    @PostMapping("/compose-preview")
    public ApiResponse<VibePromptComposerService.ComposedPrompt> composePreview(
            @RequestBody ComposePreviewRequest request) {
        if (!StringUtils.hasText(request.getTask())) {
            return ApiResponse.error(400, "task 不能为空");
        }
        if (request.getProductLineId() == null) {
            return ApiResponse.error(400, "productLineId 不能为空(管理端预览须显式指定产品线)");
        }
        try {
            return ApiResponse.success(composerService.compose(
                    request.getTask(), request.getProductLineName(), request.getProductLineId(), true));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 执行实例分页轨迹(管理侧观测) */
    @GetMapping("/runs")
    public ApiResponse<Map<String, Object>> pageRuns(
            @RequestParam(required = false) Long productLineId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<VibeRun> result = vibeRunRepository.pageByLine(productLineId, page, Math.min(size, 100));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", result.getRecords());
        data.put("totalElements", result.getTotal());
        data.put("pageNumber", result.getCurrent());
        data.put("pageSize", result.getSize());
        return ApiResponse.success(data);
    }

    /** 执行实例详情(含全部产物) */
    @GetMapping("/runs/{runCode}")
    public ApiResponse<Map<String, Object>> runDetail(@PathVariable String runCode) {
        try {
            VibeRun run = vibeRunService.findRun(runCode);
            List<VibeRunArtifact> artifacts = vibeRunService.artifactsOf(run.getId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("run", run);
            data.put("artifacts", artifacts);
            return ApiResponse.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    /** 手动刷新流水线缓存 */
    @PostMapping("/refresh")
    public ApiResponse<Void> refresh() {
        pipelineRegistry.refreshCache();
        return ApiResponse.success();
    }

    // ==================== 内部 ====================

    private Map<String, Object> pipelineDetail(PipelineTemplate template) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", template.getId());
        detail.put("pipelineCode", template.getPipelineCode());
        detail.put("name", template.getName());
        detail.put("intentType", template.getIntentType());
        detail.put("productLineId", template.getProductLineId());
        detail.put("shared", template.getProductLineId() == null);
        detail.put("description", template.getDescription());
        detail.put("keywords", template.getKeywords());
        detail.put("executionConvention", template.getExecutionConvention());
        detail.put("status", template.getStatus());
        Map<Long, String> stageCodeById = new LinkedHashMap<>();
        for (PipelineStage stage : pipelineStageRepository.findByIds(
                pipelineStageStepRepository.findByPipelineIdOrderBySeq(template.getId()).stream()
                        .map(PipelineStageStep::getStageId).toList())) {
            stageCodeById.put(stage.getId(), stage.getStageCode());
        }
        List<Map<String, Object>> steps = new ArrayList<>();
        for (PipelineStageStep step : pipelineStageStepRepository
                .findByPipelineIdOrderBySeq(template.getId())) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("seq", step.getSeq());
            s.put("stageId", step.getStageId());
            s.put("stageCode", stageCodeById.get(step.getStageId()));
            s.put("stepParams", step.getStepParams());
            steps.add(s);
        }
        detail.put("steps", steps);
        return detail;
    }

    private void replaceSteps(Long pipelineId, List<StepUpsertRequest> steps) {
        Map<String, PipelineStage> stageByCode = new LinkedHashMap<>();
        for (PipelineStage stage : pipelineStageRepository.findAllActive()) {
            stageByCode.put(stage.getStageCode(), stage);
        }
        List<PipelineStageStep> entities = new ArrayList<>();
        for (StepUpsertRequest request : steps) {
            PipelineStage stage = stageByCode.get(request.getStageCode());
            if (stage == null) {
                throw new IllegalArgumentException("阶段不存在: " + request.getStageCode());
            }
            PipelineStageStep step = new PipelineStageStep();
            step.setStageId(stage.getId());
            step.setSeq(request.getSeq());
            step.setStepParams(request.getStepParams());
            entities.add(step);
        }
        pipelineStageStepRepository.replaceAll(pipelineId, entities);
    }

    private String validateUpsert(PipelineUpsertRequest request, boolean isCreate) {
        if (!StringUtils.hasText(request.getPipelineCode())) {
            return "流水线编码(pipelineCode)不能为空";
        }
        if (!StringUtils.hasText(request.getName())) {
            return "流水线名称(name)不能为空";
        }
        if (!StringUtils.hasText(request.getIntentType())) {
            return "意图类型(intentType)不能为空";
        }
        if (isCreate && pipelineTemplateRepository.findByCode(request.getPipelineCode().trim()).isPresent()) {
            return "流水线编码已存在: " + request.getPipelineCode();
        }
        // scope 内意图唯一(共享模板或同一产品线的私有副本,同类意图只允许一条启用)
        boolean duplicated = request.getProductLineId() == null
                ? pipelineTemplateRepository.findSharedByIntentType(request.getIntentType()).isPresent()
                : pipelineTemplateRepository.findPrivateByIntentAndLine(request.getIntentType(), request.getProductLineId()).isPresent();
        if (isCreate && duplicated) {
            return "该意图类型在此范围内已有流水线(共享模板或本产品线私有副本只能有一条)";
        }
        return null;
    }

    // ==================== 请求体 ====================

    /** compose 预览请求 */
    @Data
    public static class ComposePreviewRequest {
        private String task;
        private Long productLineId;
        private String productLineName;
    }

    /** 流水线整存请求(模板字段 + 步骤序列) */
    @Data
    public static class PipelineUpsertRequest {
        private String pipelineCode;
        private String name;
        private String intentType;
        private Long productLineId;
        private String description;
        private String keywords;
        private String executionConvention;
        private Integer status;
        private List<StepUpsertRequest> steps;

        PipelineTemplate toTemplate() {
            PipelineTemplate template = new PipelineTemplate();
            template.setPipelineCode(pipelineCode == null ? null : pipelineCode.trim());
            template.setName(name);
            template.setIntentType(intentType);
            template.setProductLineId(productLineId);
            template.setDescription(description);
            template.setKeywords(keywords);
            template.setExecutionConvention(executionConvention);
            template.setStatus(status);
            return template;
        }
    }

    /** 步骤请求(stageCode + seq + 可选覆盖) */
    @Data
    public static class StepUpsertRequest {
        private String stageCode;
        private Integer seq;
        private String stepParams;
    }
}
