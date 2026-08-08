package oracle.packages.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-тесты обогащения SELECT/INSERT полями из мапы.
 */
class SqlNewFieldsEnricherTest {

    @Test
    void enrichSelect_addsMissingNewFields() {
        String sql = "SELECT id, name FROM employees";
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("pid", "1");
        fields.put("name", "'Bob'");
        fields.put("age", "15");
        fields.put("dep_id", "1");

        String enriched = SqlNewFieldsEnricher.enrichSelect(sql, fields);

        assertThat(enriched).isEqualTo(
                "SELECT id, name, 1 new_pid, 'Bob' new_name, 15 new_age, 1 new_dep_id FROM employees");
    }

    @Test
    void enrichSelect_skipsExistingNewAlias() {
        String sql = "SELECT 1 new_pid, name FROM dual";
        Map<String, String> fields = Map.of(
                "pid", "99",
                "name", "'Bob'"
        );

        String enriched = SqlNewFieldsEnricher.enrichSelect(sql, fields);

        assertThat(enriched).contains("1 new_pid");
        assertThat(enriched).doesNotContain("99 new_pid");
        assertThat(enriched).contains("'Bob' new_name");
    }

    @Test
    void enrichSelect_looksForNewPrefixedAliasOnly() {
        // колонка dep_id без префикса new_ не мешает добавить new_dep_id
        String sql = "SELECT dep_id FROM dual";
        Map<String, String> fields = Map.of("dep_id", "1");

        String enriched = SqlNewFieldsEnricher.enrichSelect(sql, fields);

        assertThat(enriched).isEqualTo("SELECT dep_id, 1 new_dep_id FROM dual");
    }

    @Test
    void enrichSelect_supportsAsAliasAndSkipsCaseInsensitively() {
        String sql = "SELECT id AS new_pid FROM dual";
        Map<String, String> fields = Map.of("pid", "1", "age", "15");

        String enriched = SqlNewFieldsEnricher.enrichSelect(sql, fields);

        assertThat(enriched).contains("id AS new_pid");
        assertThat(enriched).doesNotContain("1 new_pid");
        assertThat(enriched).contains("15 new_age");
    }

    @Test
    void enrichSelect_exampleFromDual() {
        String sql = "SELECT 1 FROM DUAL";
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("pid", "1");
        fields.put("name", "'Bob'");
        fields.put("age", "15");
        fields.put("dep_id", "1");

        String enriched = SqlNewFieldsEnricher.enrichSelect(sql, fields);

        assertThat(enriched).isEqualTo(
                "SELECT 1, 1 new_pid, 'Bob' new_name, 15 new_age, 1 new_dep_id FROM DUAL");
    }

    @Test
    void enrichSelect_rejectsNonSelect() {
        assertThatThrownBy(() -> SqlNewFieldsEnricher.enrichSelect("DELETE FROM t", Map.of("a", "1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a SELECT");
    }

    @Test
    void enrichInsert_values_addsColumnsWithoutNewPrefix() {
        String sql = "INSERT INTO emp (id, name) VALUES (1, 'Ann')";
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("pid", "10");
        fields.put("age", "20");

        String enriched = SqlNewFieldsEnricher.enrichInsert(sql, fields);

        assertThat(enriched).isEqualTo(
                "INSERT INTO emp (id, name, pid, age) VALUES (1, 'Ann', 10, 20)");
    }

    @Test
    void enrichInsert_values_multiTuples() {
        String sql = "INSERT INTO emp (id) VALUES (1), (2)";
        Map<String, String> fields = Map.of("dep_id", "5");

        String enriched = SqlNewFieldsEnricher.enrichInsert(sql, fields);

        assertThat(enriched).isEqualTo(
                "INSERT INTO emp (id, dep_id) VALUES (1, 5), (2, 5)");
    }

    @Test
    void enrichInsert_select_addsPlainColumnNames() {
        String sql = "INSERT INTO emp (id, name) SELECT e.id, e.name FROM employees e";
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("age", "15");
        fields.put("dep_id", "1");

        String enriched = SqlNewFieldsEnricher.enrichInsert(sql, fields);

        assertThat(enriched).startsWith("INSERT INTO emp (id, name, age, dep_id) SELECT ");
        assertThat(enriched).contains("15 age");
        assertThat(enriched).contains("1 dep_id");
        assertThat(enriched).doesNotContain("new_age");
        assertThat(enriched).doesNotContain("new_dep_id");
        assertThat(enriched).contains("FROM employees e");
    }

    @Test
    void enrichInsert_skipsExistingColumnByPlainName() {
        String sql = "INSERT INTO emp (id, pid) VALUES (1, 9)";
        Map<String, String> fields = Map.of("pid", "1", "age", "15");

        String enriched = SqlNewFieldsEnricher.enrichInsert(sql, fields);

        assertThat(enriched).isEqualTo(
                "INSERT INTO emp (id, pid, age) VALUES (1, 9, 15)");
    }

    @Test
    void enrichInsert_ignoresNewPrefixedColumnWhenLookingForField() {
        // new_pid в списке колонок не считается полем pid
        String sql = "INSERT INTO emp (id, new_pid) VALUES (1, 9)";
        Map<String, String> fields = Map.of("pid", "1");

        String enriched = SqlNewFieldsEnricher.enrichInsert(sql, fields);

        assertThat(enriched).isEqualTo(
                "INSERT INTO emp (id, new_pid, pid) VALUES (1, 9, 1)");
    }

    @Test
    void enrichInsert_requiresExplicitColumns() {
        assertThatThrownBy(() ->
                SqlNewFieldsEnricher.enrichInsert(
                        "INSERT INTO emp VALUES (1, 'Ann')",
                        Map.of("age", "15")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicitly list columns");
    }

    @Test
    void enrichInsert_rejectsNonInsert() {
        assertThatThrownBy(() ->
                SqlNewFieldsEnricher.enrichInsert("SELECT 1 FROM dual", Map.of("a", "1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not an INSERT");
    }

    @Test
    void enrichInsert_statusDefault_notSplitIntoLetters() {
        String sql = """
                INSERT INTO SCS_CO_VIP_E(Vip_no, C_op, No_doc, Cod_bn_pr, Id, Id_Cln, S_db_cr, Cod_db_cr, Seq_Payment_Code, Payer_Cor_Id, Pay_Tax_Number, Recipient_MFO, Recipient_Cor_Id, Rec_Tax_Number, Payment_Purpose_Code, Payment_Purpose, Payer_Name, Recipient_Name, Processing_Type, Doc_Entering_Date, Seq_Doc, No_str) VALUES (:P_VIP_NO, :P_OP_CODE, :P_NO_DOC, decode(:P_SEND_TYPE, 'C', :P_REM_MFO, :P_REC_MFO), decode(:P_SEND_TYPE, 'C', :P_REM_ACC_ID, :P_REC_ACC_ID), decode(:P_SEND_TYPE, 'C', :P_REC_ACC_ID, :P_REM_ACC_ID), :P_PAYM_AMOUNT, decode(:P_SEND_TYPE, 'D', 'Д', 'C', 'К', 'К'), :P_SEQ_PAYM_CODE, :P_REM_COR_ID, :P_REM_TAX_NUMBER, :P_REC_MFO, :P_REC_COR_ID, :P_REC_TAX_NUMBER, :P_PAYM_PURP_CODE, :P_PAYM_PURP, :P_REM_NAME, :P_REC_NAME, :P_PROC_TYPE, :P_CREATE_DATE, :P_SEQ_DOC, :V_NO_STR)
                """;

        String enriched = SqlNewFieldsEnricher.enrichInsert(sql, Map.of("STATUS", "'NEW'"));

        assertThat(SqlNewFieldsEnricher.insertColumnName("STATUS")).isEqualTo("status");
        assertThat(enriched).doesNotContain("S_T_A_T_U_S");
        assertThat(enriched).doesNotContain("s_t_a_t_u_s");
        assertThat(enriched).contains("No_str, status)");
        assertThat(enriched).contains(":V_NO_STR, 'NEW')");
        assertThat(SqlNewFieldsEnricher.extractInsertTableName(enriched)).isEqualTo("SCS_CO_VIP_E");
    }

    @Test
    void extractInsertTableName_simpleAndSchemaQualified() {
        assertThat(SqlNewFieldsEnricher.extractInsertTableName(
                "INSERT INTO emp (id) VALUES (1)")).isEqualTo("emp");
        assertThat(SqlNewFieldsEnricher.extractInsertTableName(
                "insert into hr.employees (id) select 1 from dual")).isEqualTo("hr.employees");
        assertThat(SqlNewFieldsEnricher.extractInsertTableName(
                "INSERT INTO \"Emp\" (id) VALUES (1)")).isEqualTo("\"Emp\"");
    }

    @Test
    void extractInsertTableName_rejectsNonInsert() {
        assertThatThrownBy(() -> SqlNewFieldsEnricher.extractInsertTableName("SELECT 1 FROM dual"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not an INSERT");
    }

    @Test
    void naming_selectUsesNewPrefix_insertDoesNot() {
        assertThat(SqlNewFieldsEnricher.selectAlias("depId")).isEqualTo("new_dep_id");
        assertThat(SqlNewFieldsEnricher.selectAlias("dep_id")).isEqualTo("new_dep_id");
        assertThat(SqlNewFieldsEnricher.insertColumnName("depId")).isEqualTo("dep_id");
        assertThat(SqlNewFieldsEnricher.insertColumnName("dep_id")).isEqualTo("dep_id");
        assertThat(SqlNewFieldsEnricher.insertColumnName("new_pid")).isEqualTo("pid");
    }

    @Test
    void transformFieldExpression_twoPartNextval() {
        assertThat(SqlNewFieldsEnricher.transformFieldExpression("emp_seq.nextval"))
                .isEqualTo("dbms_seq.nextval('emp_seq')");
        assertThat(SqlNewFieldsEnricher.transformFieldExpression("  EMP_SEQ.NEXTVAL  "))
                .isEqualTo("dbms_seq.nextval('EMP_SEQ')");
    }

    @Test
    void transformFieldExpression_threePartNextval_usesMiddlePart() {
        assertThat(SqlNewFieldsEnricher.transformFieldExpression("hr.emp_seq.nextval"))
                .isEqualTo("dbms_seq.nextval('emp_seq')");
    }

    @Test
    void transformFieldExpression_stripsQuotes() {
        assertThat(SqlNewFieldsEnricher.transformFieldExpression("\"HR\".\"EMP_SEQ\".\"nextval\""))
                .isEqualTo("dbms_seq.nextval('EMP_SEQ')");
        assertThat(SqlNewFieldsEnricher.transformFieldExpression("\"EMP_SEQ\".nextval"))
                .isEqualTo("dbms_seq.nextval('EMP_SEQ')");
    }

    @Test
    void transformFieldExpression_leavesOtherExpressionsUntouched() {
        assertThat(SqlNewFieldsEnricher.transformFieldExpression("'NEW'")).isEqualTo("'NEW'");
        assertThat(SqlNewFieldsEnricher.transformFieldExpression(":P_VIP_NO")).isEqualTo(":P_VIP_NO");
        assertThat(SqlNewFieldsEnricher.transformFieldExpression("a.b.c.nextval")).isEqualTo("a.b.c.nextval");
        assertThat(SqlNewFieldsEnricher.transformFieldExpression("seq.currval")).isEqualTo("seq.currval");
    }

    @Test
    void enrichSelect_transformsNextvalExpression() {
        String sql = "SELECT id FROM dual";
        Map<String, String> fields = Map.of("id", "hr.emp_seq.nextval");

        String enriched = SqlNewFieldsEnricher.enrichSelect(sql, fields);

        assertThat(enriched).isEqualTo(
                "SELECT id, dbms_seq.nextval('emp_seq') new_id FROM dual");
    }

    @Test
    void enrichInsert_transformsNextvalExpression() {
        String sql = "INSERT INTO emp (name) VALUES ('Ann')";
        Map<String, String> fields = Map.of("id", "emp_seq.nextval");

        String enriched = SqlNewFieldsEnricher.enrichInsert(sql, fields);

        assertThat(enriched).isEqualTo(
                "INSERT INTO emp (name, id) VALUES ('Ann', dbms_seq.nextval('emp_seq'))");
    }
}
