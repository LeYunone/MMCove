# 薄壳 Skill 模板 — Vibe 流水线（使用方本地安装指引）

> 把下面两段内容拷进你项目的 `.claude/skills/vibe-pipeline/SKILL.md`（Claude Code）
> 或 CLAUDE.md（任意 AI 编码工具）。仅两条规则,永不演化,零维护——
> 所有流水线与知识库演化都在 MMCove 服务端。

---

## 拷贝内容开始

### vibe-pipeline

当涉及编码任务(修复 bug / 开发功能 / 重构 / 补测试)时,遵守两条规则:

1. **任务开始**:在写第一行代码之前,先调用 `composeVibePrompt` 工具(参数 task=用户原话),
   把返回的提示词作为本次任务的执行指令,严格按其中阶段地图逐阶段执行;
   每阶段开始/完成调用 `reportStage` 上报,产物文本随完成一并上报。

2. **上下文恢复**:如果执行中发现缺少任务指令/阶段地图/门禁(对话被压缩),
   或用户说"继续之前的任务",调用 `getRunContext`(runId 可空)恢复,从断点继续,不重复已完成阶段。

## 拷贝内容结束

---

## 前置:配置 MCP(一次性)

```bash
claude mcp add --transport sse mmcove-vibe http://<host>:8808/sse \
  --header "Authorization: Bearer sk-你的token" \
  --header "X-Product-Line: 你的产品线名称"
```

- token:向管理员申请(sk- 开头)
- X-Product-Line:你的产品线名称(如"示例产品线")或编码(如 iot)
- 详细工具清单与接入说明见 `接口文档-知识库MCP-20260907.md`

## 为什么需要这个薄壳

服务端返回的完美提示词是"用户态"内容,长对话被压缩时会丢失。三层保险:

| 层 | 机制 | 抗压缩 |
|---|---|---|
| 1 | MCP 工具描述(每轮常驻 system 层)——`getRunContext` 的描述本身就是恢复触发器 | ✅ |
| 2 | 服务端 run 执行实例(进度/产物/提示词快照落库)——恢复的内容 | ✅(绝对持久) |
| 3 | 本薄壳 skill(养成调用习惯,新会话冷启动更快) | ✅ |

即使不装薄壳,第 1+2 层也已兜住压缩丢失;装上后体验更顺。
