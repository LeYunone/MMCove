package com.mmcove.agent.tools.external.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 参数规约注解:声明式约束,由 {@code ParamSpecToolCallback} 在工具调用前统一拦截校验/归一。
 *
 * <p>标在 {@code @Tool} 方法的参数上,声明该参数的枚举合法值/数值范围/正则/必填。
 *
 * <p><b>「精准防飘移」L5</b>:用确定性闸门对冲 LLM 概率性——LLM 传「中文」自动归一为「zh_CN」,
 * 越界值拦截,非法返回结构化 err(合法值提示)触发 LLM 自愈重试。一处装饰器覆盖所有 MCP 工具,
 * 新增工具只需标注本注解即获得防护。
 *
 * <p>枚举声明示例(格式「标准值:别名1,别名2」,别名为可选):
 * <pre>{@code
 * @ParamSpec(enumValues = {
 *     "zh_CN:中文,简体中文,汉语,chinese,zh",
 *     "en_US:英文,英语,english,en"
 * })
 * String lang
 * }</pre>
 *
 * @since 2026-07-21
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ParamSpec.Container.class)
public @interface ParamSpec {

    /** 枚举合法值 + 别名,格式「标准值:别名1,别名2」(别名为可选)。命中别名归一为标准值。 */
    String[] enumValues() default {};

    /** 数值最小值(仅对数值型 String 参数生效,如音量/亮度)。默认负无穷 = 不校验。 */
    double min() default Double.NEGATIVE_INFINITY;

    /** 数值最大值。默认正无穷 = 不校验。 */
    double max() default Double.POSITIVE_INFINITY;

    /** 正则约束(如 deviceKey 格式、时间 HH:mm)。空 = 不校验。 */
    String regex() default "";

    /** 是否必填(缺则返回 err 提示 LLM 反问用户)。 */
    boolean required() default false;

    /** {@link Repeatable} 容器。 */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Container {
        ParamSpec[] value();
    }
}
