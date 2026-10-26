package oracle.packages.ignite.codegen.stub;

@FunctionalInterface
public interface DataProvider {

    void createTable(TableDefinition tableDefinition, boolean replaceIfExists);
}
