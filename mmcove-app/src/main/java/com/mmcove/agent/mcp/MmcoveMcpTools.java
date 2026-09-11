package com.mmcove.agent.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

/**
 * MCP 工具集。
 * 使用 @Tool 注解标注的方法会自动暴露为 MCP 接口，
 * 外部 LLM 客户端（Claude Desktop 等）可通过 MCP 协议调用。
 */
@Slf4j
@Service
public class MmcoveMcpTools {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Random random = new Random();

    private static final String[] WEATHER_TYPES = {"晴", "多云", "阴", "小雨", "中雨", "雷阵雨"};
    private static final String[] WIND_DIRS = {"东风", "南风", "西风", "北风", "东南风", "西北风"};

    /**
     * 查询天气 — 演示外部 API 调用类工具。
     * 实际生产中替换为真实天气 API 调用。
     */
    @Tool(description = "查询指定城市的天气预报信息，包括温度、天气状况、湿度等。如果用户没有明确指定城市，不要调用此工具，必须先询问用户所在城市。禁止猜测或使用默认城市。")
    public String queryWeather(
            @ToolParam(description = "用户明确指定的城市名称，如 '北京'、'上海'、'深圳'、'London'。如果用户未提供，不要猜测，必须先向用户询问。") String city) {
        log.info("[MCP] 查询天气: city={}", city);

        // TODO: 替换为真实天气 API
        String weather = WEATHER_TYPES[random.nextInt(WEATHER_TYPES.length)];
        int tempHigh = 15 + random.nextInt(20);
        int tempLow = tempHigh - 5 - random.nextInt(10);
        int humidity = 30 + random.nextInt(50);
        String wind = WIND_DIRS[random.nextInt(WIND_DIRS.length)];
        int windLevel = 1 + random.nextInt(5);

        return String.format("%s 天气预报：%n天气：%s%n温度：%d°C ~ %d°C%n湿度：%d%%%n风力：%s %d级",
                city, weather, tempLow, tempHigh, humidity, wind, windLevel);
    }

    /**
     * 生成 UUID — 演示简单工具。
     */
    @Tool(description = "生成一个随机的 UUID 字符串")
    public String generateUuid() {
        String uuid = UUID.randomUUID().toString();
        log.info("[MCP] 生成 UUID: {}", uuid);
        return uuid;
    }

    /**
     * 数学计算 — 支持加减乘除和括号的表达式求值。
     */
    @Tool(description = "执行数学计算，支持加减乘除运算。输入格式为数学表达式，例如 '2+3*4'")
    public String calculate(
            @ToolParam(description = "数学表达式，如 '2+3*4' 或 '(10+5)/3'") String expression) {
        log.info("[MCP] 数学计算: expression={}", expression);
        try {
            double result = evaluateExpr(expression.replaceAll("\\s+", ""));
            String resultStr;
            if (result == (long) result) {
                resultStr = String.valueOf((long) result);
            } else {
                resultStr = String.valueOf(result);
            }
            log.info("[MCP] 计算结果: {} = {}", expression, resultStr);
            return resultStr;
        } catch (Exception e) {
            return "计算失败: " + e.getMessage();
        }
    }

    /**
     * 搜索互联网 — 模拟搜索结果。
     */
    @Tool(description = "搜索互联网获取信息。输入为搜索关键词。")
    public String webSearch(
            @ToolParam(description = "搜索关键词") String query) {
        log.info("[MCP] 搜索: query={}", query);
        return String.format("""
                搜索 "%s" 的结果（模拟）：
                1. 相关信息条目一：这是关于 %s 的第一条搜索结果摘要。
                2. 相关信息条目二：这是关于 %s 的第二条搜索结果摘要。
                3. 相关信息条目三：这是关于 %s 的第三条搜索结果摘要。
                注意：这是模拟搜索结果，实际部署时需接入真实搜索 API。
                """, query, query, query, query);
    }

    /**
     * 哈希计算 — 支持 MD5、SHA-256。
     */
    @Tool(description = "计算文本的哈希值。输入格式：'算法:文本'，算法支持 md5 和 sha256，例如 'md5:hello world'")
    public String calculateHash(
            @ToolParam(description = "格式：'算法:文本'，算法支持 md5 和 sha256，例如 'md5:hello'") String input) {
        log.info("[MCP] 哈希计算: input={}", input);
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "请输入要计算哈希的内容，格式：'算法:文本'";
        }

        String[] parts = trimmed.split(":", 2);
        if (parts.length < 2) {
            return "格式错误，请使用 '算法:文本' 格式，例如 'md5:hello'";
        }

        String algorithm = parts[0].trim().toUpperCase();
        String text = parts[1].trim();

        try {
            String algorithmName = switch (algorithm) {
                case "MD5" -> "MD5";
                case "SHA256" -> "SHA-256";
                default -> throw new IllegalArgumentException("不支持的算法: " + algorithm + "，请使用 md5 或 sha256");
            };

            MessageDigest digest = MessageDigest.getInstance(algorithmName);
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            String hexResult = HexFormat.of().formatHex(hash);

            log.info("[MCP] 哈希结果: {}({}) = {}", algorithm, text, hexResult);
            return algorithm + " 哈希值: " + hexResult;

        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "哈希计算失败: " + e.getMessage();
        }
    }

    /**
     * Base64 编解码。
     */
    @Tool(description = "Base64 编码或解码。输入格式：'encode:文本' 进行编码，'decode:Base64字符串' 进行解码")
    public String base64Codec(
            @ToolParam(description = "格式：'encode:文本' 或 'decode:Base64字符串'") String input) {
        log.info("[MCP] Base64 编解码: input={}", input);
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "请输入内容，格式：'encode:文本' 或 'decode:Base64字符串'";
        }

        String[] parts = trimmed.split(":", 2);
        if (parts.length < 2) {
            return "格式错误，请使用 'encode:文本' 或 'decode:Base64字符串'";
        }

        String mode = parts[0].trim().toLowerCase();
        String content = parts[1].trim();

        try {
            String result = switch (mode) {
                case "encode" -> {
                    String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
                    log.info("[MCP] Base64 encode '{}' → {}", content, encoded);
                    yield encoded;
                }
                case "decode" -> {
                    String decoded = new String(Base64.getDecoder().decode(content), StandardCharsets.UTF_8);
                    log.info("[MCP] Base64 decode '{}' → {}", content, decoded);
                    yield decoded;
                }
                default -> throw new IllegalArgumentException("不支持的操作: " + mode + "，请使用 encode 或 decode");
            };

            return result;

        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "Base64 处理失败: " + e.getMessage();
        }
    }

    /**
     * 时间戳转换 — 时间戳与日期互转。
     */
    @Tool(description = "时间戳与日期时间互转。输入时间戳数字转为可读日期，输入日期时间(yyyy-MM-dd HH:mm:ss)转为时间戳，输入 'now' 获取当前时间戳")
    public String timestampConvert(
            @ToolParam(description = "时间戳数字、'yyyy-MM-dd HH:mm:ss' 格式日期、或 'now'") String input) {
        log.info("[MCP] 时间戳转换: input={}", input);
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "请输入时间戳、日期时间或 'now'";
        }

        try {
            String result;
            if ("now".equalsIgnoreCase(trimmed) || "现在".equals(trimmed)) {
                long ts = System.currentTimeMillis();
                result = String.format("当前时间戳: %d\n可读时间: %s",
                        ts, LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()).format(FORMATTER));
            } else if (trimmed.matches("\\d+")) {
                long ts = Long.parseLong(trimmed);
                if (ts < 1_000_000_000_000L) {
                    ts *= 1000;
                }
                String readable = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()).format(FORMATTER);
                result = String.format("时间戳: %d\n可读时间: %s", Long.parseLong(trimmed), readable);
            } else {
                LocalDateTime ldt = LocalDateTime.parse(trimmed, FORMATTER);
                long ts = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                result = String.format("日期时间: %s\n时间戳(毫秒): %d\n时间戳(秒): %d",
                        trimmed, ts, ts / 1000);
            }

            log.info("[MCP] 转换结果: {}", result);
            return result;

        } catch (Exception e) {
            return "转换失败，请检查输入格式。支持：时间戳数字、'yyyy-MM-dd HH:mm:ss' 格式日期、或 'now'";
        }
    }

    /**
     * 查询订单 — 演示业务数据查询类工具。
     * 这是后续真实业务工具的模板。
     */
    @Tool(description = "根据订单号查询订单详情，包括订单状态、金额、商品信息")
    public String queryOrder(
            @ToolParam(description = "订单编号") String orderId) {
        log.info("[MCP] 查询订单: orderId={}", orderId);

        return String.format("""
                订单详情：
                - 订单号：%s
                - 状态：已发货
                - 金额：¥299.00
                - 商品：示例商品A × 1
                - 收货地址：北京市朝阳区xx路xx号
                - 下单时间：2026-04-15 10:30:00
                （当前为模拟数据，接入真实业务后返回实际订单信息）""", orderId);
    }

    /**
     * 查询库存 — 演示业务数据查询类工具。
     */
    @Tool(description = "查询指定商品的库存数量和仓库位置")
    public String queryInventory(
            @ToolParam(description = "商品名称或SKU编号") String product) {
        log.info("[MCP] 查询库存: product={}", product);

        int stock = 50 + random.nextInt(200);
        String[] warehouses = {"北京仓", "上海仓", "广州仓"};
        String warehouse = warehouses[random.nextInt(warehouses.length)];

        return String.format("商品「%s」库存信息：%n库存数量：%d%n所在仓库：%s",
                product, stock, warehouse);
    }

    // ==================== 表达式求值器 ====================

    private double evaluateExpr(String expr) {
        return new ExprParser(expr).parse();
    }

    private static class ExprParser {
        private final String expr;
        private int pos = -1;
        private int ch;

        ExprParser(String expr) {
            this.expr = expr;
            nextChar();
        }

        void nextChar() {
            ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
        }

        boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        double parse() {
            double x = parseExpression();
            if (pos < expr.length()) throw new RuntimeException("意外的字符: " + (char) ch);
            return x;
        }

        double parseExpression() {
            double x = parseTerm();
            for (; ; ) {
                if (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        double parseTerm() {
            double x = parseFactor();
            for (; ; ) {
                if (eat('*')) x *= parseFactor();
                else if (eat('/')) x /= parseFactor();
                else return x;
            }
        }

        double parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) return -parseFactor();

            double x;
            int startPos = this.pos;
            if (eat('(')) {
                x = parseExpression();
                eat(')');
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                x = Double.parseDouble(expr.substring(startPos, this.pos));
            } else {
                throw new RuntimeException("无法解析: " + expr.substring(startPos));
            }
            return x;
        }
    }
}
