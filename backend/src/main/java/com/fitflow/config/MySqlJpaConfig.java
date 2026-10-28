package com.fitflow.config;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Configuration
@EnableTransactionManagement
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
@EnableJpaRepositories(
        basePackages = {
                "com.fitflow.auth.repository",
                "com.fitflow.user.repository",
                "com.fitflow.trainer.repository",
                "com.fitflow.fitnessclass.repository",
                "com.fitflow.session.repository",
                "com.fitflow.booking.repository",
                "com.fitflow.membership.repository",
                "com.fitflow.payment.repository",
                "com.fitflow.payout.repository",
                "com.fitflow.wellness.repository",
                "com.fitflow.admin.repository",
                "com.fitflow.settings.repository",
                "com.fitflow.notification.repository",
                "com.fitflow.common.job"
        },
        entityManagerFactoryRef = "mysqlEntityManagerFactory",
        transactionManagerRef = "mysqlTransactionManager"
)
public class MySqlJpaConfig {

    @Bean
    public EntityManagerFactoryBuilder entityManagerFactoryBuilder() {
        return new EntityManagerFactoryBuilder(new HibernateJpaVendorAdapter(), new HashMap<>(), null);
    }

    @Bean
    public DateTimeProvider dateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }

    @Primary
    @Bean(name = "mysqlDataSourceProperties")
    @ConfigurationProperties("fitflow.datasource.mysql")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "mysqlDataSource")
    public DataSource mysqlDataSource(@Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean(name = "mysqlFlyway")
    public Flyway mysqlFlyway(
            @Qualifier("mysqlDataSource") DataSource dataSource,
            @Value("${fitflow.flyway.mysql.locations:classpath:db/migration/mysql}") String[] locations) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        return flyway;
    }

    @Primary
    @Bean(name = "mysqlEntityManagerFactory")
    @DependsOn("mysqlFlyway")
    public LocalContainerEntityManagerFactoryBean mysqlEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("mysqlDataSource") DataSource dataSource) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "validate");
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");

        return builder
                .dataSource(dataSource)
                .packages(
                        "com.fitflow.common.entity",
                        "com.fitflow.auth.entity",
                        "com.fitflow.user.entity",
                        "com.fitflow.trainer.entity",
                        "com.fitflow.fitnessclass.entity",
                        "com.fitflow.session.entity",
                        "com.fitflow.booking.entity",
                        "com.fitflow.membership.entity",
                        "com.fitflow.payment.entity",
                        "com.fitflow.payout.entity",
                        "com.fitflow.wellness.entity",
                        "com.fitflow.admin.entity",
                        "com.fitflow.settings.entity",
                        "com.fitflow.notification.entity",
                        "com.fitflow.common.job"
                )
                .persistenceUnit("mysql")
                .properties(properties)
                .build();
    }

    @Primary
    @Bean(name = "mysqlTransactionManager")
    public PlatformTransactionManager mysqlTransactionManager(
            @Qualifier("mysqlEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
