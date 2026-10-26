package oracle.packages.ignite.codegen.stub;

public class RecordingDataProvider implements DataProvider {

    private TableDefinition lastTable;
    private boolean lastReplace;

    @Override
    public void createTable(TableDefinition tableDefinition, boolean replaceIfExists) {
        this.lastTable = tableDefinition;
        this.lastReplace = replaceIfExists;
    }

    public TableDefinition lastTable() {
        return lastTable;
    }

    public boolean lastReplace() {
        return lastReplace;
    }
}
