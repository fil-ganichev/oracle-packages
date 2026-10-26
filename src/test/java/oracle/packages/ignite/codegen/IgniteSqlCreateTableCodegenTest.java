package oracle.packages.ignite.codegen;

import oracle.packages.ignite.codegen.stub.ColumnDefinition;
import oracle.packages.ignite.codegen.stub.DataTypes;
import oracle.packages.ignite.codegen.stub.RecordingDataProvider;
import oracle.packages.ignite.codegen.stub.TableDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IgniteSqlCreateTableCodegenTest {

    private static final String SCHEMA = "TBG";

    @Test
    void parsePersonDemoScript() {
        List<IgniteSqlCreateTableCodegen.ParsedTable> tables =
                IgniteSqlCreateTableCodegen.parseCreateTablesFromClasspath("ignite/person-demo.sql");

        assertThat(tables).hasSize(1);
        IgniteSqlCreateTableCodegen.ParsedTable person = tables.get(0);
        assertThat(person.tableName()).isEqualTo("PERSON");
        assertThat(person.primaryKeyColumns()).containsExactly("ID");
        assertThat(person.columns()).extracting(IgniteSqlCreateTableCodegen.ParsedColumn::name)
                .containsExactly("ID", "NAME", "AGE", "DEP_ID");
    }

    @Test
    void mapSqlTypes_coversDecimalAndVarchar() {
        IgniteSqlCreateTableCodegen.ParsedTable person =
                IgniteSqlCreateTableCodegen.parseCreateTablesFromClasspath("ignite/person-demo.sql").get(0);

        assertThat(person.columns().get(0).dataType()).isEqualTo(DataTypes.Decimal());
        assertThat(person.columns().get(0).notNull()).isTrue();
        assertThat(person.columns().get(1).dataType()).isEqualTo(DataTypes.Varchar(100));
        assertThat(person.columns().get(1).notNull()).isFalse();
        assertThat(person.columns().get(2).dataType()).isEqualTo(DataTypes.Decimal());
    }

    @Test
    void applyCreateTables_buildsTableDefinitionOnStubProvider() {
        RecordingDataProvider provider = new RecordingDataProvider();

        IgniteSqlCreateTableCodegen.applyCreateTablesFromClasspath(
                "ignite/person-demo.sql", provider, SCHEMA, true);

        TableDefinition table = provider.lastTable();
        assertThat(provider.lastReplace()).isTrue();
        assertThat(table.schema()).isEqualTo(SCHEMA);
        assertThat(table.tableName()).isEqualTo("PERSON");
        assertThat(table.primaryKey()).containsExactly("ID");

        ColumnDefinition id = table.columns().get(0);
        assertThat(id.name()).isEqualTo("ID");
        assertThat(id.type()).isEqualTo(DataTypes.Decimal());
        assertThat(id.notNull()).isTrue();

        ColumnDefinition name = table.columns().get(1);
        assertThat(name.name()).isEqualTo("NAME");
        assertThat(name.type()).isEqualTo(DataTypes.Varchar(100));
        assertThat(name.notNull()).isFalse();
    }

    @Test
    void generateCreateTableMethod_matchesExpectedShape() {
        IgniteSqlCreateTableCodegen.ParsedTable person =
                IgniteSqlCreateTableCodegen.parseCreateTablesFromClasspath("ignite/person-demo.sql").get(0);

        String code = IgniteSqlCreateTableCodegen.generateCreateTableMethod(
                person, SCHEMA, "createPersonCache", "provider");

        assertThat(code).contains("private void createPersonCache(DataProvider provider)");
        assertThat(code).contains("new TableDefinition(\"TBG\", \"PERSON\")");
        assertThat(code).contains("new ColumnDefinition(\"ID\", DataTypes.Decimal()).setNotNull()");
        assertThat(code).contains("new ColumnDefinition(\"NAME\", DataTypes.Varchar(100))");
        assertThat(code).contains(".setPrimaryKey(\"ID\")");
        assertThat(code).contains("provider.createTable(tabDef, true);");
    }

    @Test
    void parseTmpPersonScript_singleLineDdlWithInlinePrimaryKey() {
        List<IgniteSqlCreateTableCodegen.ParsedTable> tables =
                IgniteSqlCreateTableCodegen.parseCreateTablesFromClasspath("ignite/ignite_tmp_person_table.sql");

        assertThat(tables).hasSize(2);

        IgniteSqlCreateTableCodegen.ParsedTable tmpPerson = tables.get(0);
        assertThat(tmpPerson.tableName()).isEqualTo("TMP_PERSON");
        assertThat(tmpPerson.primaryKeyColumns()).containsExactly("PID");
        assertThat(tmpPerson.columns()).hasSize(8);
        assertThat(tmpPerson.columns().get(0).name()).isEqualTo("NAME");
        assertThat(tmpPerson.columns().get(0).dataType()).isEqualTo(DataTypes.Varchar(20));
        assertThat(tmpPerson.columns().get(1).dataType()).isEqualTo(DataTypes.Decimal(2, 0));
        assertThat(tmpPerson.columns().get(3).name()).isEqualTo("PID");
        assertThat(tmpPerson.columns().get(3).notNull()).isTrue();

        assertThat(tables.get(1).tableName()).isEqualTo("TMP_PERSON_LOG");
        assertThat(tables.get(1).columns()).hasSize(4);
    }

    @Test
    void mapSqlTypes_allIgniteDataTypesStubbed() {
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("INT")).isEqualTo(DataTypes.Integer());
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("BIGINT")).isEqualTo(DataTypes.Long());
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("SMALLINT")).isEqualTo(DataTypes.Short());
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("TINYINT")).isEqualTo(DataTypes.Byte());
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("FLOAT")).isEqualTo(DataTypes.Float());
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("DOUBLE")).isEqualTo(DataTypes.Double());
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("BOOLEAN")).isEqualTo(DataTypes.Boolean());
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("DATE")).isEqualTo(DataTypes.Date());
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("TIME")).isEqualTo(DataTypes.Time());
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("TIMESTAMP")).isEqualTo(DataTypes.Timestamp());
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("UUID")).isEqualTo(DataTypes.Uuid());
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("BINARY(16)")).isEqualTo(DataTypes.Binary(16));
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("VARBINARY(32)")).isEqualTo(DataTypes.Varbinary(32));
        assertThat(IgniteSqlCreateTableCodegen.mapSqlType("DECIMAL(10,2)"))
                .isEqualTo(DataTypes.Decimal(10, 2));
    }
}
