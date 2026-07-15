package com.zzy.finsight.component.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * 从类路径加载版本化离线评测数据集。
 */
@Component
public class EvaluationDatasetLoader {
    private final ObjectMapper objectMapper;

    public EvaluationDatasetLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    /** 加载指定类路径资源，资源缺失或格式非法时立即失败。 */
    public EvaluationDataset load(String resourcePath) {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("未找到离线评测数据集：" + resourcePath);
            }
            return objectMapper.readValue(input, EvaluationDataset.class);
        } catch (IOException exception) {
            throw new IllegalStateException("读取离线评测数据集失败：" + resourcePath, exception);
        }
    }
}
