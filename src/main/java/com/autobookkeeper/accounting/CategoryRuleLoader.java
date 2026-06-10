package com.autobookkeeper.accounting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Component
public class CategoryRuleLoader {

    private static final Logger logger = LoggerFactory.getLogger(CategoryRuleLoader.class);

    private final Map<String, List<String>> rules;

    public CategoryRuleLoader() {
        this.rules = loadFromClasspath();
    }

    public CategoryRuleLoader(Map<String, List<String>> rules) {
        this.rules = new LinkedHashMap<>(rules);
    }

    public Map<String, List<String>> rules() {
        return rules;
    }

    private Map<String, List<String>> loadFromClasspath() {
        Properties properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("category_rules.properties")) {
            if (inputStream != null) {
                properties.load(new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8));
            }
        } catch (IOException exception) {
            logger.warn("Failed to load category_rules.properties from classpath, using empty rules", exception);
            return Map.of();
        }

        Map<String, List<String>> loaded = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            List<String> keywords = Arrays.stream(properties.getProperty(name).split(","))
                    .map(String::trim)
                    .filter(keyword -> !keyword.isBlank())
                    .toList();
            loaded.put(name, keywords);
        }
        return loaded;
    }
}
