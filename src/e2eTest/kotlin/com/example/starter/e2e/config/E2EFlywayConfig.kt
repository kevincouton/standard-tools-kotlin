package com.example.starter.e2e.config

import org.flywaydb.core.Flyway
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class E2EFlywayConfig {

    @Bean
    fun flyway(dataSource: DataSource): Flyway {
        return Flyway.configure()
            .dataSource(dataSource)
            .load()
            .also { it.migrate() }
    }

    companion object {
        @JvmStatic
        @Bean
        fun flywayDependencyPostProcessor(): BeanFactoryPostProcessor {
            return BeanFactoryPostProcessor { beanFactory ->
                if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
                    beanFactory.getBeanDefinition("entityManagerFactory").setDependsOn("flyway")
                }
            }
        }
    }
}
