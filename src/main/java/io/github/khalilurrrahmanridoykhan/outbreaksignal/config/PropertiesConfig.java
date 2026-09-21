package io.github.khalilurrrahmanridoykhan.outbreaksignal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutbreakProperties.class)
class PropertiesConfig {
}
