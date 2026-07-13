package com.zzy.finsight.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapperXmlConfigurationTest {
    private static final List<Class<?>> MAPPER_TYPES = List.of(
            AdminAuditLogMapper.class,
            AdminMapper.class,
            AgentStepLogMapper.class,
            AppUserMapper.class,
            CheckpointMapper.class,
            FinancialSnapshotMapper.class,
            ReportMapper.class,
            ResearchTaskMapper.class,
            SystemMapper.class
    );

    @Test
    void parsesAllMapperXmlAndRegistersEveryAbstractMethod() throws Exception {
        Configuration configuration = new Configuration();
        for (Class<?> mapperType : MAPPER_TYPES) {
            String resource = "mapper/" + mapperType.getSimpleName() + ".xml";
            try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }

        for (Class<?> mapperType : MAPPER_TYPES) {
            List<String> statementNames = Arrays.stream(mapperType.getDeclaredMethods())
                    .filter(method -> Modifier.isAbstract(method.getModifiers()))
                    .map(Method::getName)
                    .toList();
            assertThat(statementNames)
                    .allSatisfy(methodName -> assertThat(configuration.hasStatement(
                            mapperType.getName() + "." + methodName
                    )).as(mapperType.getSimpleName() + "." + methodName).isTrue());
        }
    }
}
