package it.ariaspa.mypay.mypaycore.api.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Configurazione del DataSource PostgreSQL "pa" (database principale di MyPay).
 *
 * <p>Spring Boot non auto-configura datasource con prefisso personalizzato
 * ({@code spring.datasource.pa.*}), quindi è necessaria questa classe esplicita.
 *
 * <p>Il datasource è configurabile via:
 * <ul>
 *   <li>{@code spring.datasource.pa.url}</li>
 *   <li>{@code spring.datasource.pa.username}</li>
 *   <li>{@code spring.datasource.pa.password}</li>
 *   <li>{@code spring.datasource.pa.hikari.*} (pool HikariCP)</li>
 * </ul>
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "it.ariaspa.mypay.mypaycore.api",
        entityManagerFactoryRef = "paEntityManagerFactory",
        transactionManagerRef = "paTransactionManager"
)
public class DataSourceConfig {

    /**
     * Proprietà base del datasource PA (url, username, password, driver).
     * Lette da {@code spring.datasource.pa.*}.
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.pa")
    public DataSourceProperties paDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * DataSource HikariCP per il database PA.
     * Le proprietà del pool sono lette da {@code spring.datasource.pa.hikari.*}.
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.pa.hikari")
    public HikariDataSource paDataSource() {
        return paDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * EntityManagerFactory JPA per il datasource PA.
     * Scansiona l'intero package base dell'applicazione per le entity {@code @Entity}.
     * Quando verranno aggiunte entity JPA, inserirle nel package {@code domain}.
     */
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean paEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(paDataSource());
        factory.setPackagesToScan("it.ariaspa.mypay.mypaycore.api");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPersistenceUnitName("pa");
        return factory;
    }

    /**
     * TransactionManager JPA per il datasource PA.
     */
    @Bean
    @Primary
    public PlatformTransactionManager paTransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(paEntityManagerFactory().getObject());
        return transactionManager;
    }
}
