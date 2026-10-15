package oracle.packages.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OracleViewPackageUsageTest {

    @Test
    void findPackageFunctionsInViewText_detectsUsedFunctionsOnly() {
        String viewText = """
                SELECT pkg_view_deps_test.func_alpha() AS a,
                       PKG_VIEW_DEPS_TEST.FUNC_BETA() AS b
                FROM dual
                """;

        assertThat(OracleViewPackageUsage.findPackageFunctionsInViewText(viewText, "PKG_VIEW_DEPS_TEST"))
                .containsExactly("FUNC_ALPHA", "FUNC_BETA");
    }

    @Test
    void findPackageFunctionsInViewText_schemaQualified() {
        String viewText = "SELECT test.pkg_view_deps_test.func_gamma() FROM dual";

        assertThat(OracleViewPackageUsage.findPackageFunctionsInViewText(viewText, "pkg_view_deps_test"))
                .containsExactly("FUNC_GAMMA");
    }
}
