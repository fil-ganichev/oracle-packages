package oracle.packages;

import oracle.packages.util.OracleViewPackageUsage;
import oracle.packages.util.OracleViewPackageUsage.PackageDependency;
import oracle.packages.util.OracleViewPackageUsage.ResolvedPackageUsage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Пакет с тремя функциями, VIEW с двумя из них; два запроса к каталогу и разбор TEXT VIEW.
 */
@Testcontainers(disabledWithoutDocker = true)
class OracleViewPackageDependenciesIT {

    private static final String PACKAGE_NAME = "PKG_VIEW_DEPS_TEST";
    private static final String VIEW_NAME = "V_PKG_DEPS_TEST";
    private static final String VIEW_NAME_SCHEMA_AND_CASE = "V_PKG_DEPS_TEST_ALT";
    private static final String SCHEMA_NAME = "TEST";

    @Container
    static final OracleContainer ORACLE =
            new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
                    .withUsername("test")
                    .withPassword("test");

    @BeforeAll
    static void installPackageAndView() throws SQLException {
        try (Connection connection = OracleTestJdbc.openConnection(ORACLE);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE OR REPLACE PACKAGE pkg_view_deps_test AS
                      FUNCTION func_alpha RETURN NUMBER;
                      FUNCTION func_beta RETURN NUMBER;
                      FUNCTION func_gamma RETURN NUMBER;
                    END pkg_view_deps_test;
                    """);
            statement.execute("""
                    CREATE OR REPLACE PACKAGE BODY pkg_view_deps_test AS
                      FUNCTION func_alpha RETURN NUMBER IS
                      BEGIN
                        RETURN 1;
                      END func_alpha;
                      FUNCTION func_beta RETURN NUMBER IS
                      BEGIN
                        RETURN 2;
                      END func_beta;
                      FUNCTION func_gamma RETURN NUMBER IS
                      BEGIN
                        RETURN 3;
                      END func_gamma;
                    END pkg_view_deps_test;
                    """);
            statement.execute("""
                    CREATE OR REPLACE VIEW v_pkg_deps_test AS
                    SELECT pkg_view_deps_test.func_alpha() AS col_alpha,
                           pkg_view_deps_test.func_beta() AS col_beta
                    FROM dual
                    """);
            statement.execute("""
                    CREATE OR REPLACE VIEW v_pkg_deps_test_alt AS
                    SELECT test.pkg_view_deps_test.func_alpha() AS col_alpha,
                           Pkg_View_Deps_Test.Func_Beta() AS col_beta
                    FROM dual
                    """);
        }
    }

    @Test
    void viewListsPackageDependency_thenSourceShowsTwoFunctions() throws SQLException {
        try (Connection connection = OracleTestJdbc.openConnection(ORACLE)) {
            List<PackageDependency> packageDeps =
                    OracleViewPackageUsage.listViewPackageDependencies(connection, VIEW_NAME);

            assertThat(packageDeps).hasSize(1);
            assertThat(packageDeps.get(0).packageName()).isEqualTo(PACKAGE_NAME);

            String viewText = OracleViewPackageUsage.loadUserViewText(connection, VIEW_NAME);
            Set<String> usedFunctions = OracleViewPackageUsage.findPackageFunctionsInViewText(
                    viewText, packageDeps.get(0).packageName());

            assertThat(usedFunctions).containsExactlyInAnyOrder("FUNC_ALPHA", "FUNC_BETA");
            assertThat(usedFunctions).doesNotContain("FUNC_GAMMA");
        }
    }

    @Test
    void alternateView_schemaQualifiedAndMixedCaseNames() throws SQLException {
        try (Connection connection = OracleTestJdbc.openConnection(ORACLE)) {
            List<PackageDependency> packageDeps =
                    OracleViewPackageUsage.listViewPackageDependencies(connection, VIEW_NAME_SCHEMA_AND_CASE);

            assertThat(packageDeps).hasSize(1);
            assertThat(packageDeps.get(0).packageName()).isEqualTo(PACKAGE_NAME);

            String viewText = OracleViewPackageUsage.loadUserViewText(connection, VIEW_NAME_SCHEMA_AND_CASE);
            assertThat(viewText.toLowerCase()).contains(SCHEMA_NAME.toLowerCase() + ".pkg_view_deps_test.func_alpha");
            assertThat(viewText).containsIgnoringCase("Pkg_View_Deps_Test.Func_Beta");

            Set<String> usedFunctions = OracleViewPackageUsage.findPackageFunctionsInViewText(
                    viewText, packageDeps.get(0).packageName());

            assertThat(usedFunctions).containsExactlyInAnyOrder("FUNC_ALPHA", "FUNC_BETA");
        }
    }

    @Test
    void resolveCombinesBothQueries() throws SQLException {
        try (Connection connection = OracleTestJdbc.openConnection(ORACLE)) {
            List<ResolvedPackageUsage> resolved =
                    OracleViewPackageUsage.resolvePackageFunctionsUsedByView(connection, VIEW_NAME);

            assertThat(resolved).hasSize(1);
            assertThat(resolved.get(0).dependency().packageName()).isEqualTo(PACKAGE_NAME);
            assertThat(resolved.get(0).functionNames())
                    .containsExactlyInAnyOrder("FUNC_ALPHA", "FUNC_BETA");
        }
    }
}
