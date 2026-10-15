package oracle.packages.jpa;

import oracle.packages.OracleTestJdbc;
import oracle.packages.util.OracleViewPackageUsage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Чтение {@code USER_VIEWS.TEXT} (LONG) через Spring Data JPA: native query и {@code TO_LOB(TEXT)}.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.open-in-view=false"
})
@ActiveProfiles("jpa-oracle-it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OracleUserViewTextJpaIT {

    private static final String VIEW_NAME = "V_JPA_VIEW_TEXT_TEST";

    @Container
    static final OracleContainer ORACLE =
            new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void oracleDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
        registry.add("spring.datasource.username", ORACLE::getUsername);
        registry.add("spring.datasource.password", ORACLE::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "oracle.jdbc.OracleDriver");
    }

    @Autowired
    UserViewRepository userViewRepository;

    @Autowired
    UserViewTextLongRepository userViewTextLongRepository;

    @BeforeAll
    static void createSampleViewAndTextReader() throws SQLException {
        try (Connection connection = OracleTestJdbc.openConnection(ORACLE);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE OR REPLACE FUNCTION jpa_read_user_view_text(p_view_name IN VARCHAR2) RETURN CLOB IS
                      l_text LONG;
                    BEGIN
                      SELECT text
                      INTO l_text
                      FROM user_views
                      WHERE view_name = UPPER(p_view_name);
                      IF l_text IS NULL THEN
                        RETURN NULL;
                      END IF;
                      RETURN TO_CLOB(l_text);
                    EXCEPTION
                      WHEN NO_DATA_FOUND THEN
                        RETURN NULL;
                    END jpa_read_user_view_text;
                    """);
            statement.execute("""
                    CREATE OR REPLACE VIEW v_jpa_view_text_test AS
                    SELECT 'line-one' AS marker,
                           'line-two' AS tail
                    FROM dual
                    """);
        }
    }

    @Test
    void entityWithoutTextColumn_loadsViewNameOnly() {
        UserView userView = userViewRepository.findById(VIEW_NAME).orElseThrow();

        assertThat(userView.getViewName()).isEqualTo(VIEW_NAME);
    }

    @Test
    void entityWithLobMappingOnLongText_failsOnLoad() {
        assertThatThrownBy(() -> userViewTextLongRepository.findById(VIEW_NAME))
                .rootCause()
                .hasMessageContaining("ORA-17004");
    }

    @Test
    void nativeQueryDirectLongColumn_readsTextAsString() throws SQLException {
        String expected;
        try (Connection connection = OracleTestJdbc.openConnection(ORACLE)) {
            expected = OracleViewPackageUsage.loadUserViewText(connection, VIEW_NAME).trim();
        }

        String directLong = userViewRepository.findViewTextLongColumn(VIEW_NAME).orElseThrow().trim();

        assertThat(directLong).isEqualTo(expected);
    }

    @Test
    void nativeQueryPlSqlWrapper_readsViewDefinitionAsString() throws SQLException {
        String expected;
        try (Connection connection = OracleTestJdbc.openConnection(ORACLE)) {
            expected = OracleViewPackageUsage.loadUserViewText(connection, VIEW_NAME).trim();
        }

        String fromJpa = userViewRepository.findViewTextViaPlSqlWrapper(VIEW_NAME).orElseThrow().trim();

        assertThat(fromJpa).isEqualTo(expected);
        assertThat(fromJpa.toUpperCase()).contains("LINE-ONE", "LINE-TWO", "FROM DUAL");
    }

    @Test
    void nativeQueryGetDdl_returnsCreateViewStatement() {
        String ddl = userViewRepository.findViewDdl(VIEW_NAME).orElseThrow();

        assertThat(ddl.toUpperCase()).contains("CREATE", "VIEW", VIEW_NAME, "LINE-ONE", "LINE-TWO");
    }
}
