package com.zzy.finsight.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验控制器方法和数据字段的中文注释规范。
 */
class DocumentationConventionTest {
    private static final Pattern ENDPOINT_ANNOTATION = Pattern.compile(
            "@(GetMapping|PostMapping|PatchMapping|DeleteMapping|PutMapping|ExceptionHandler)\\b"
    );
    private static final Pattern DOCUMENTED_ENDPOINT = Pattern.compile(
            "/\\*\\*.*?[\\p{IsHan}].*?\\*/\\s*"
                    + "@(GetMapping|PostMapping|PatchMapping|DeleteMapping|PutMapping|ExceptionHandler)\\b",
            Pattern.DOTALL
    );
    private static final Pattern PUBLIC_RECORD = Pattern.compile(
            "public\\s+record\\s+(\\w+)(?:<[^>{}]+>)?\\s*\\((.*?)\\)\\s*\\{",
            Pattern.DOTALL
    );
    private static final Pattern COMPONENT_NAME = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*$");
    private static final Pattern PRIVATE_DTO_FIELD = Pattern.compile(
            "(?m)^\\s*private\\s+(?!static\\b)(?:final\\s+)?[\\w.<>?,\\[\\]]+\\s+(\\w+)\\s*(?:=[^;]*)?;"
    );
    private static final Pattern CHINESE = Pattern.compile("[\\p{IsHan}]");

    @Test
    void controllerEndpointsHaveChineseJavadoc() throws IOException {
        Path controllerRoot = Path.of("src/main/java/com/zzy/finsight/controller");
        try (Stream<Path> paths = Files.walk(controllerRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        String source = readSource(path);
                        assertThat(countMatches(DOCUMENTED_ENDPOINT, source))
                                .as("Controller 接口方法缺少中文 Javadoc：%s", path)
                                .isEqualTo(countMatches(ENDPOINT_ANNOTATION, source));
                    });
        }
    }

    @Test
    void publicRecordComponentsHaveChineseParamDocumentation() throws IOException {
        for (Path root : dataModelRoots()) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .forEach(this::assertRecordComponentsDocumented);
            }
        }
    }

    @Test
    void mutableDtoFieldsHaveChineseJavadoc() throws IOException {
        Path dtoRoot = Path.of("src/main/java/com/zzy/finsight/dto");
        try (Stream<Path> paths = Files.walk(dtoRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(this::assertPrivateDtoFieldsDocumented);
        }
    }

    /** 返回需要检查字段说明的数据模型目录。 */
    private List<Path> dataModelRoots() {
        return List.of(
                Path.of("src/main/java/com/zzy/finsight/domain"),
                Path.of("src/main/java/com/zzy/finsight/dto"),
                Path.of("src/main/java/com/zzy/finsight/auth"),
                Path.of("src/main/java/com/zzy/finsight/rag"),
                Path.of("src/main/java/com/zzy/finsight/search")
        );
    }

    /** 校验源码中的每个公开 record 组件都有中文参数说明。 */
    private void assertRecordComponentsDocumented(Path path) {
        String source = readSource(path);
        Matcher recordMatcher = PUBLIC_RECORD.matcher(source);
        while (recordMatcher.find()) {
            String recordName = recordMatcher.group(1);
            String javadoc = findDirectJavadoc(source, recordMatcher.start());
            for (String component : splitRecordComponents(recordMatcher.group(2))) {
                Matcher nameMatcher = COMPONENT_NAME.matcher(component.trim());
                assertThat(nameMatcher.find())
                        .as("无法识别 record 组件：%s#%s", path, recordName)
                        .isTrue();
                String componentName = nameMatcher.group(1);
                Pattern paramPattern = Pattern.compile(
                        "@param\\s+" + Pattern.quote(componentName) + "\\s+[^\\r\\n]*[\\p{IsHan}]"
                );
                assertThat(paramPattern.matcher(javadoc).find())
                        .as("record 字段缺少中文说明：%s#%s.%s", path, recordName, componentName)
                        .isTrue();
            }
        }
    }

    /** 校验普通 DTO 的每个实例字段都有中文 Javadoc。 */
    private void assertPrivateDtoFieldsDocumented(Path path) {
        String source = readSource(path);
        Matcher fieldMatcher = PRIVATE_DTO_FIELD.matcher(source);
        while (fieldMatcher.find()) {
            String javadoc = findDirectJavadoc(source, fieldMatcher.start());
            assertThat(CHINESE.matcher(javadoc).find())
                    .as("DTO 字段缺少中文说明：%s#%s", path, fieldMatcher.group(1))
                    .isTrue();
        }
    }

    /** 查找声明前紧邻的 Javadoc，并允许中间存在字段注解。 */
    private String findDirectJavadoc(String source, int declarationStart) {
        int javadocStart = source.lastIndexOf("/**", declarationStart);
        assertThat(javadocStart).as("声明前缺少 Javadoc").isGreaterThanOrEqualTo(0);
        int javadocEnd = source.indexOf("*/", javadocStart);
        assertThat(javadocEnd).as("Javadoc 未正常结束").isLessThan(declarationStart);
        String between = source.substring(javadocEnd + 2, declarationStart);
        assertThat(between).as("Javadoc 必须紧邻声明").matches(
                "(?s)\\s*(?:@[\\w.]+(?:\\([^\\r\\n]*\\))?\\s*)*"
        );
        return source.substring(javadocStart, javadocEnd + 2);
    }

    /** 按顶层逗号拆分 record 组件，避免拆开泛型参数。 */
    private List<String> splitRecordComponents(String source) {
        List<String> components = new ArrayList<>();
        int start = 0;
        int parentheses = 0;
        int angles = 0;
        int brackets = 0;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '(') {
                parentheses++;
            } else if (current == ')') {
                parentheses--;
            } else if (current == '<') {
                angles++;
            } else if (current == '>') {
                angles--;
            } else if (current == '[') {
                brackets++;
            } else if (current == ']') {
                brackets--;
            } else if (current == ',' && parentheses == 0 && angles == 0 && brackets == 0) {
                components.add(source.substring(start, index));
                start = index + 1;
            }
        }
        components.add(source.substring(start));
        return components;
    }

    /** 以 UTF-8 编码读取源码。 */
    private String readSource(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("读取源码失败：" + path, exception);
        }
    }

    /** 统计正则表达式的匹配次数。 */
    private int countMatches(Pattern pattern, String source) {
        int count = 0;
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
