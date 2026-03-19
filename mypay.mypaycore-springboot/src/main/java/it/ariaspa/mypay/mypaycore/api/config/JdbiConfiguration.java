package it.ariaspa.mypay.mypaycore.api.config;

//import it.ariaspa.mypay.mypaycore.api.logging.JdbiSqlLogger;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.spi.JdbiPlugin;
import org.jdbi.v3.core.statement.SqlStatements;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Configura l'istanza Jdbi principale del middleware e i componenti di supporto.
 *
 * <p>La configurazione collega Jdbi al datasource {@code dsPa}, installa automaticamente
 * i plugin e i row mapper registrati nel contesto Spring e applica le impostazioni comuni
 * di timeout e logging SQL.
 */
@Configuration
@Slf4j
public class JdbiConfiguration {

    @Value("${sql-logging.enabled:false}")
    private String sqlLogginEnabled;

    @Value("${sql-logging.slow.milliseconds:0}")
    private int sqlLogginSlowQueryTresholdMs;

    @Value("${mypay4.statements.timeout.seconds:-1}")
    private int globalStatementTimeout;

   // @Autowired
  //  JdbiSqlLogger jdbiSqlLogger;

    /**
     * Espone l'istanza Jdbi primaria associata al datasource PA.
     *
     * @param ds datasource principale del middleware
     * @param jdbiPlugins plugin Jdbi rilevati automaticamente nel contesto Spring
     * @param rowMappers mapper riga registrati nel contesto Spring
     * @return istanza Jdbi pronta all'uso per DAO e SQL object
     */
    @Primary
    @Bean("jdbiPa")
    public Jdbi paJdbi(@Qualifier("dsPa") DataSource ds, List<JdbiPlugin> jdbiPlugins, List<RowMapper<?>> rowMappers) {
        return createJdbiImpl(ds, jdbiPlugins, rowMappers);
    }

    /**
     * Costruisce l'istanza Jdbi applicando proxy transazionale, timeout e logging SQL.
     *
     * @param ds datasource fisico su cui inizializzare Jdbi
     * @param jdbiPlugins plugin da installare sull'istanza
     * @param rowMappers mapper riga da registrare
     * @return istanza Jdbi configurata
     */
    private Jdbi createJdbiImpl(DataSource ds, List<JdbiPlugin> jdbiPlugins, List<RowMapper<?>> rowMappers) {
        String dsString;
        try (Connection conn = ds.getConnection()){
            dsString = conn.getMetaData().getURL();
        } catch (Exception e){
            dsString = ds.toString();
        }
        final String dsStringFinal = dsString;
        // Il proxy permette a Jdbi di partecipare alle transazioni Spring gia' aperte sul datasource.
        TransactionAwareDataSourceProxy proxy = new TransactionAwareDataSourceProxy(ds);
        Jdbi jdbi = Jdbi.create(proxy);
        if(globalStatementTimeout >= 0) {
            jdbi = jdbi.configure(SqlStatements.class, stmt -> {
                log.info("set default query timeout for ds {} to {} seconds", dsStringFinal, globalStatementTimeout);
                stmt.setQueryTimeout(globalStatementTimeout);
            });
        } else {
            log.info("not setting default query timeout for ds {} (value {})", dsStringFinal, globalStatementTimeout);
        }
        if(!"false".equalsIgnoreCase(sqlLogginEnabled)) {
         //   jdbiSqlLogger.setBehaviour(sqlLogginEnabled);
        //    jdbiSqlLogger.setSlowQueryTresholdMs(sqlLogginSlowQueryTresholdMs);
        //    jdbi.setSqlLogger(jdbiSqlLogger);
        }

        log.debug("Datasource {} - Installing jdbi plugins... ({} found): {}"
               , dsString, jdbiPlugins.size()
                , jdbiPlugins.stream().map(x -> x.getClass().getName()).collect(Collectors.joining(", ")) );
        jdbiPlugins.forEach(jdbi::installPlugin);
        // Register all available rowMappers
        log.debug("Datasource {} - Installing jdbi rowMappers... ({} found): {}"
                , dsString, rowMappers.size()
                , rowMappers.stream().map(x -> x.getClass().getName()).collect(Collectors.joining(", ")) );
        rowMappers.forEach(jdbi::registerRowMapper);
        return jdbi;
    }

    /**
     * Registra il plugin Jdbi che abilita DAO dichiarativi basati su SQL Object.
     *
     * @return plugin {@link SqlObjectPlugin}
     */
    @Bean
    public JdbiPlugin sqlObjectPlugin() {
        return new SqlObjectPlugin();
    }

    /**
     * Espone il message source condiviso per messaggi applicativi e validazioni.
     *
     * @return message source basato sui bundle in {@code messages/messages}
     */
    @Bean
    public ResourceBundleMessageSource messageSource() {
        var source = new ResourceBundleMessageSource();
        source.setBasenames("messages/messages");
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }

}
