package com.mmcove.agent.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 无模板引擎的 SPA 托管：把前端入口/单段路由回落到对应 index.html，支持 history 模式。
 *
 * <p>聊天端（Vite base '/'，产物在 static/chat/）→ {@code forward:/chat/index.html}；
 * 后台端（Vite base '/admin/'，产物在 static/admin/）→ {@code forward:/admin/index.html}。
 *
 * <p>只匹配「单段、无点」的路径（如 /login、/admin/dashboard）。转发目标 /chat/index.html
 * 与 /admin/index.html 是「多段且带点」的静态资源，由 ResourceHttpRequestHandler 命中，
 * 不会被这里的单段映射再次匹配，因此不会形成转发死循环。
 *
 * <p>{@code /api/**}、{@code /v1/**}、{@code /actuator/**}、{@code /flux-images/**} 由各自的
 * @RestController / 静态资源处理器优先命中；带点的资源（*.js/*.css）也不会被单段无点正则匹配。
 *
 * <p>已知局限：多段深链（如 /admin/agents/create）直接刷新会 404。站内导航走前端路由不受影响；
 * 如需支持多段深链刷新，可后续改为基于 NoHandlerFoundException 的 ErrorController 回落。
 */
@Controller
public class SpaForwardController {

    @GetMapping("/")
    public String root() {
        return "forward:/chat/index.html";
    }

    @GetMapping("/admin")
    public String adminRoot() {
        return "forward:/admin/index.html";
    }

    /** 聊天端单段深链（无点） */
    @GetMapping("/{path:[^.]*}")
    public String chatDeepLink() {
        return "forward:/chat/index.html";
    }

    /** 后台端单段深链（/admin/xxx） */
    @GetMapping("/admin/{path:[^.]*}")
    public String adminDeepLink() {
        return "forward:/admin/index.html";
    }
}
