package it.ariaspa.mypay.mypaycore.api.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * Configura il datasource principale del middleware utilizzato dal layer Jdbi.
 *
 * <p>La configurazione legge le proprieta' con prefisso {@code spring.datasource.pa.*}
 * per creare il pool HikariCP dedicato al database PostgreSQL di MyPay e il relativo
 * transaction manager JDBC.
 *
 * <p>Quando la proprieta' {@code spring.datasource.cryptPassword} e' attiva, la password
 * del datasource viene decifrata tramite Jasypt prima della creazione del bean.
 */
@Configuration
@Slf4j
public class DataSourceConfiguration {
    @Value("${spring.datasource.cryptPassword:false}")
    private boolean isPasswordEncrypted;

    @Value("${spring.datasource.pa.hikari.minimumIdle:-1}")
    private int dataSourceMypayMinimumIdle;
    @Value("${spring.datasource.pa.hikari.maximumPoolSize:-1}")
    private int dataSourceMypayMaximumPoolSize;

    @Autowired
    @Qualifier("jasyptStringEncryptor")
    private StringEncryptor encryptor;

    /**
     * Espone le proprieta' base del datasource PA.
     *
     * @return contenitore Spring delle proprieta' lette da {@code spring.datasource.pa.*}
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.pa")
    public DataSourceProperties paDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Crea il datasource HikariCP principale usato dal middleware e da Jdbi.
     *
     * <p>Se configurato, il valore della password viene prima decifrato; successivamente
     * vengono applicati i parametri di pooling e disabilitato l'auto-commit per delegare
     * la gestione transazionale a Spring.
     *
     * @return datasource primario associato al bean {@code dsPa}
     */
    @Bean(name = "dsPa")
    @ConfigurationProperties("spring.datasource.pa")
    @Primary
    public DataSource paDataSource() {
        if (isPasswordEncrypted) {
            paDataSourceProperties().setPassword(encryptor.decrypt(paDataSourceProperties().getPassword()));
        }
        HikariDataSource ds = paDataSourceProperties().initializeDataSourceBuilder().type(HikariDataSource.class)
                .build();

        ds.setMinimumIdle(dataSourceMypayMinimumIdle);
        ds.setMaximumPoolSize(dataSourceMypayMaximumPoolSize);
        ds.setAutoCommit(false);
        log.info("creating data source [middleware] with minimumIdle:{} maximumPoolSize:{}", ds.getMinimumIdle(), ds.getMaximumPoolSize());
        return ds;
    }

    /**
     * Registra il transaction manager JDBC associato al datasource principale.
     *
     * @param datasource datasource PA utilizzato da Jdbi e dai servizi applicativi
     * @return transaction manager Spring basato su JDBC
     */
    @Bean(name = "tmPa")
    @Primary
    DataSourceTransactionManager paTransactionManager(@Qualifier("dsPa") DataSource datasource) {
        return new DataSourceTransactionManager(datasource);
    }

}
