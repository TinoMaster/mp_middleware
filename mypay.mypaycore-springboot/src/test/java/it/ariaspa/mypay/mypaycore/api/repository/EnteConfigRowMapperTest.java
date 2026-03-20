package it.ariaspa.mypay.mypaycore.api.repository;

import it.ariaspa.mypay.mypaycore.api.domain.EnteConfig;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import org.jdbi.v3.core.statement.StatementContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test unitari per {@link EnteConfigRowMapper}.
 *
 * <p>Verifica la corretta conversione delle righe del ResultSet in oggetti
 * {@link EnteConfig}, inclusa la gestione dei campi nullable e la conversione
 * dell'enum {@link ModalitaRouting}.
 */
@ExtendWith(MockitoExtension.class)
class EnteConfigRowMapperTest {

    @Mock
    private ResultSet resultSet;

    @Mock
    private StatementContext statementContext;

    private EnteConfigRowMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new EnteConfigRowMapper();
    }

    @Test
    @DisplayName("map - converte correttamente una riga con PIATTAFORMA_UNITARIA")
    void map_piattaformaUnitaria() throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        Timestamp ts = Timestamp.valueOf(now);

        when(resultSet.getLong("id")).thenReturn(1L);
        when(resultSet.getString("cod_ipa_ente")).thenReturn("R_LOMBARDIA");
        when(resultSet.getString("tipo_operazione")).thenReturn("pivotSILAutorizzaImportFlussoTesoreria");
        when(resultSet.getString("modalita_routing")).thenReturn("PIATTAFORMA_UNITARIA");
        when(resultSet.getBoolean("attivo")).thenReturn(true);
        when(resultSet.getString("note")).thenReturn("Ente pilota");
        when(resultSet.getTimestamp("data_creazione")).thenReturn(ts);
        when(resultSet.getTimestamp("data_aggiornamento")).thenReturn(ts);

        EnteConfig config = mapper.map(resultSet, statementContext);

        assertEquals(1L, config.getId());
        assertEquals("R_LOMBARDIA", config.getCodIpaEnte());
        assertEquals("pivotSILAutorizzaImportFlussoTesoreria", config.getTipoOperazione());
        assertEquals(ModalitaRouting.PIATTAFORMA_UNITARIA, config.getModalitaRouting());
        assertTrue(config.isAttivo());
        assertEquals("Ente pilota", config.getNote());
        assertEquals(now, config.getDataCreazione());
        assertEquals(now, config.getDataAggiornamento());
    }

    @Test
    @DisplayName("map - converte correttamente una riga con LEGACY")
    void map_legacy() throws SQLException {
        when(resultSet.getLong("id")).thenReturn(2L);
        when(resultSet.getString("cod_ipa_ente")).thenReturn("COMUNE_MILANO");
        when(resultSet.getString("tipo_operazione")).thenReturn("operazioneTest");
        when(resultSet.getString("modalita_routing")).thenReturn("LEGACY");
        when(resultSet.getBoolean("attivo")).thenReturn(true);
        when(resultSet.getString("note")).thenReturn(null);
        when(resultSet.getTimestamp("data_creazione")).thenReturn(null);
        when(resultSet.getTimestamp("data_aggiornamento")).thenReturn(null);

        EnteConfig config = mapper.map(resultSet, statementContext);

        assertEquals(2L, config.getId());
        assertEquals("COMUNE_MILANO", config.getCodIpaEnte());
        assertEquals(ModalitaRouting.LEGACY, config.getModalitaRouting());
        assertNull(config.getNote());
        assertNull(config.getDataCreazione());
        assertNull(config.getDataAggiornamento());
    }

    @Test
    @DisplayName("map - valore modalita_routing non valido lancia eccezione")
    void map_invalidModalita_throwsException() throws SQLException {
        when(resultSet.getLong("id")).thenReturn(3L);
        when(resultSet.getString("cod_ipa_ente")).thenReturn("TEST");
        when(resultSet.getString("tipo_operazione")).thenReturn("op");
        when(resultSet.getString("modalita_routing")).thenReturn("VALORE_INVALIDO");

        assertThrows(IllegalArgumentException.class,
                () -> mapper.map(resultSet, statementContext));
    }
}
