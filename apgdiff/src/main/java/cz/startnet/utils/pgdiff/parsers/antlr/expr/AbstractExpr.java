package cz.startnet.utils.pgdiff.parsers.antlr.expr;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cz.startnet.utils.pgdiff.PgDiffUtils;
import cz.startnet.utils.pgdiff.loader.SupportedVersion;
import cz.startnet.utils.pgdiff.parsers.antlr.AntlrParser;
import cz.startnet.utils.pgdiff.parsers.antlr.QNameParser;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Data_typeContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Function_args_parserContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.IdentifierContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Schema_qualified_nameContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Schema_qualified_name_nontypeContext;
import cz.startnet.utils.pgdiff.parsers.antlr.statements.ParserAbstract;
import cz.startnet.utils.pgdiff.schema.DbObjNature;
import cz.startnet.utils.pgdiff.schema.GenericColumn;
import cz.startnet.utils.pgdiff.schema.IFunction;
import cz.startnet.utils.pgdiff.schema.IRelation;
import cz.startnet.utils.pgdiff.schema.ISchema;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgFunction;
import cz.startnet.utils.pgdiff.schema.system.PgSystemStorage;
import ru.taximaxim.codekeeper.apgdiff.Log;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;
import ru.taximaxim.codekeeper.apgdiff.utils.Pair;

public abstract class AbstractExpr {

    // TODO get postgresql version.
    // Need to get version. I can get it from JdbcLoader(READER),
    // but I can't get it from PgDumpLoader(WRITER).
    protected final PgSystemStorage systemStorage;
    protected final String schema;
    private final AbstractExpr parent;
    private final Set<GenericColumn> depcies;

    protected final PgDatabase db;

    public Set<GenericColumn> getDepcies() {
        return Collections.unmodifiableSet(depcies);
    }

    public AbstractExpr(String schema, PgDatabase db) {
        this.schema = schema;
        parent = null;
        depcies = new LinkedHashSet<>();
        this.db = db;
        systemStorage = PgSystemStorage.getObjectsFromResources(SupportedVersion.VERSION_9_5);
    }

    protected AbstractExpr(AbstractExpr parent) {
        this.schema = parent.schema;
        this.parent = parent;
        depcies = parent.depcies;
        this.db = parent.db;
        this.systemStorage = parent.systemStorage;
    }

    protected List<Pair<String, String>> findCte(String cteName) {
        return parent == null ? null : parent.findCte(cteName);
    }

    protected boolean hasCte(String cteName) {
        return findCte(cteName) != null;
    }

    /**
     * @param schema optional schema qualification of name, may be null
     * @param name alias of the referenced object
     * @param column optional referenced column alias, may be null
     * @return a pair of (Alias, Dealiased name) where Alias is the given name.
     *          Dealiased name can be null if the name is internal to the query
     *          and is not a reference to external table.<br>
     *          null if the name is not found
     */
    protected Entry<String, GenericColumn> findReference(String schema, String name, String column) {
        return parent == null ? null : parent.findReference(schema, name, column);
    }

    /**
     * @param schema optional schema qualification of name, may be null
     * @param name alias of the referenced object
     * @return a pair of (Alias, ColumnsList) where Alias is the given name.
     *          ColumnsList list of columns as pair 'columnName-columnType' of the internal query.<br>
     */
    protected Entry<String, List<Pair<String, String>>> findReferenceComplex(String name) {
        return parent == null ? null : parent.findReferenceComplex(name);
    }

    /**
     * @return {@link cz.startnet.utils.pgdiff.parsers.antlr.expr.AbstractExprWithNmspc#unaliasedNamespace
     * unaliased namespaces}
     */
    protected Set<GenericColumn> getAllUnaliasedNmsp() {
        return parent == null ? null : parent.getAllUnaliasedNmsp();
    }

    /**
     * @return {@link cz.startnet.utils.pgdiff.parsers.antlr.expr.AbstractExprWithNmspc#namespace
     * The local namespace of this Select.}
     */
    protected Map<String, GenericColumn> getAllReferences() {
        return parent == null ? null : parent.getAllReferences();
    }

    /**
     * @return {@link cz.startnet.utils.pgdiff.parsers.antlr.expr.AbstractExprWithNmspc#complexNamespace
     * Map contains alias and list of pairs. Pairs returned by aliased subquery.}
     */
    protected Map<String, List<Pair<String, String>>> getAllReferencesComplex() {
        return parent == null ? null : parent.getAllReferencesComplex();
    }

    protected GenericColumn addRelationDepcy(List<IdentifierContext> ids) {
        GenericColumn depcy = new GenericColumn(
                getSchemaNameForRelation(ids), QNameParser.getFirstName(ids), DbObjType.TABLE);
        depcies.add(depcy);
        return depcy;
    }

    protected void addFunctionDepcyNotOverloaded(List<IdentifierContext> ids) {
        IdentifierContext schemaNameCtx = QNameParser.getSchemaNameCtx(ids);
        String schemaName;
        if (schemaNameCtx != null) {
            schemaName = schemaNameCtx.getText();
            if (GenericColumn.SYS_SCHEMAS.contains(schemaName)) {
                return;
            }
        } else {
            schemaName = schema;
        }

        PgFunction function = db.getSchema(schemaName).getFunctions().stream()
                .filter(f -> QNameParser.getFirstName(ids).equals(f.getBareName()))
                .findAny().orElse(null);
        if (function != null) {
            depcies.add(new GenericColumn(schemaName, function.getName(), DbObjType.FUNCTION));
        }
    }

    private String getSchemaNameForRelation(List<IdentifierContext> ids) {
        IdentifierContext schemaCtx = QNameParser.getSchemaNameCtx(ids);
        if (schemaCtx != null) {
            return schemaCtx.getText();
        }
        String relationName = QNameParser.getFirstName(ids);

        if (db.getSchema(schema).containsRelation(relationName)) {
            return schema;
        }
        for (ISchema s : systemStorage.getSchemas()) {
            if (s.containsRelation(relationName)) {
                return s.getName();
            }
        }
        Log.log(Log.LOG_WARNING, "Could not find schema for relation: " + relationName);
        return schema;
    }

    private String getSchemaNameForFunction(IdentifierContext sch, String signature) {
        if (sch != null) {
            return sch.getText();
        }
        if (db.getSchema(schema).containsFunction(signature)) {
            return schema;
        }
        for (ISchema s : systemStorage.getSchemas()) {
            if (s.containsFunction(signature)) {
                return s.getName();
            }
        }
        Log.log(Log.LOG_WARNING, "Could not find schema for function: " + signature);
        return schema;
    }

    protected void addFunctionDepcy(IFunction function){
        depcies.add(new GenericColumn(function.getContainingSchema().getName(),
                function.getName(), DbObjType.FUNCTION));
    }

    protected void addTypeDepcy(Data_typeContext type) {
        Schema_qualified_name_nontypeContext typeName = type.predefined_type().schema_qualified_name_nontype();

        if (typeName != null) {
            IdentifierContext qual = typeName.identifier();
            String schema = qual == null ? this.schema : qual.getText();

            depcies.add(new GenericColumn(schema,
                    typeName.identifier_nontype().getText(), DbObjType.TYPE));
        }
    }

    /**
     * @return column with its type
     */
    protected Pair<String, String> processColumn(Schema_qualified_nameContext qname) {
        List<IdentifierContext> ids = qname.identifier();
        String columnName = QNameParser.getFirstName(ids);
        String columnType = TypesSetManually.COLUMN;
        String columnParent = null;
        Pair<String, String> pair = new Pair<>(columnName, null);

        if (ids.size() > 1) {
            String schemaName = QNameParser.getThirdName(ids);
            columnParent = QNameParser.getSecondName(ids);

            Entry<String, GenericColumn> ref = findReference(schemaName, columnParent, columnName);
            if (ref != null) {
                GenericColumn referencedTable = ref.getValue();
                if (referencedTable != null) {
                    columnParent = referencedTable.table;
                    GenericColumn genericColumn = new GenericColumn(referencedTable.schema, columnParent, columnName, DbObjType.COLUMN);
                    Entry<DbObjNature, String> systemOrUserColumnType = getSystemOrUserColumnType(genericColumn);
                    columnType = systemOrUserColumnType.getValue();

                    // Add dependency only for user's objects.
                    if (systemOrUserColumnType.getKey() != null
                            && DbObjNature.USER.equals(systemOrUserColumnType.getKey())) {
                        depcies.add(genericColumn);
                    }
                } else {
                    Entry<String, List<Pair<String, String>>> refComplex = findReferenceComplex(columnParent);
                    if (refComplex != null) {
                        columnType = refComplex.getValue().stream()
                                .filter(entry -> columnName.equals(entry.getKey()))
                                .map(Entry::getValue)
                                .findAny().orElse(TypesSetManually.COLUMN);
                    }
                }
            } else {
                Log.log(Log.LOG_WARNING, "Unknown column reference: "
                        + schemaName + ' ' + columnParent + ' ' + columnName);
            }
        } else {
            // table-less columns analysis
            columnType = processTablelessColumn(columnName);
        }

        pair.setValue(columnType);

        return pair;
    }

    private Entry<DbObjNature, String> getSystemOrUserColumnType(GenericColumn genericColumn) {
        for (IRelation relation : PgDiffUtils.sIter(findRelations(genericColumn.schema, genericColumn.table))) {
            for (Pair<String, String> colPair : PgDiffUtils.sIter(relation.getRelationColumns())) {
                if (genericColumn.column.equals(colPair.getKey())) {
                    return new SimpleEntry<>(relation.getStatementNature(), colPair.getValue());
                }
            }
        }
        return new SimpleEntry<>(null, TypesSetManually.COLUMN);
    }

    private String processTablelessColumn(String columnName) {
        List<String> columnTypes = new ArrayList<>();

        // Comparing 'tableless column' with columns from 'unaliased namespace' and
        // getting corresponding type for 'tableless column'.
        Set<GenericColumn> allUnaliasedNmsp = getAllUnaliasedNmsp();
        if (allUnaliasedNmsp != null) {
            for (GenericColumn gTableOrView : allUnaliasedNmsp) {
                String colType = getTypeOfCorrespondingColTblOrView(columnName,
                        getTableOrViewColumns(gTableOrView.schema, gTableOrView.table));
                if (colType != null) {
                    addColumnDepcy(gTableOrView.schema, gTableOrView.table, columnName);
                    columnTypes.add(colType);
                }
            }
        }

        // Comparing 'tableless column' with columns from aliased table or view and
        // getting corresponding type for 'tableless column'.
        Map<String, GenericColumn> allReferences = getAllReferences();
        if (allReferences != null) {
            for (Entry<String, GenericColumn> nmsp : allReferences.entrySet()) {
                GenericColumn gTableOrView = nmsp.getValue();
                if (gTableOrView == null) {
                    continue;
                }
                String colType = getTypeOfCorrespondingColTblOrView(columnName,
                        getTableOrViewColumns(gTableOrView.schema, gTableOrView.table));
                if (colType != null) {
                    addColumnDepcy(gTableOrView.schema, gTableOrView.table, columnName);
                    columnTypes.add(colType);
                }
            }
        }

        // Comparing 'tableless column' with columns from subquery and
        // getting corresponding type for 'tableless column'.
        Map<String, List<Pair<String, String>>> allReferencesComplex = getAllReferencesComplex();
        if (allReferencesComplex != null) {
            columnTypes.addAll(getTypeOfCorrespondingColComplex(columnName, allReferencesComplex));
        }

        if (columnTypes.size() == 1) {
            return columnTypes.get(0);
        } else if (columnTypes.size() > 1) {
            // TODO Warn the user about an error of ambiguity in the dialog.
            Log.log(Log.LOG_ERROR, "Ambiguous column reference: " + columnName);
        }
        return TypesSetManually.COLUMN;
    }

    private List<String> getTypeOfCorrespondingColComplex(String columnName, Map<String, List<Pair<String, String>>> colsOfAlias) {
        List<String> columnTypes = new ArrayList<>();
        for (Entry<String, List<Pair<String, String>>> nmsp : colsOfAlias.entrySet()) {
            String colType = getTypeOfCorrespondingColTblOrView(columnName, nmsp.getValue());
            if (colType != null) {
                columnTypes.add(colType);
            }
        }
        return columnTypes;
    }

    private String getTypeOfCorrespondingColTblOrView(String columnName, List<Pair<String, String>> tableOrViewColumns) {
        for (Pair<String, String> colPair : tableOrViewColumns) {
            if (columnName.equals(colPair.getKey())) {
                return colPair.getValue();
            }
        }
        return null;
    }

    protected void addColumnsDepcies(Schema_qualified_nameContext table, List<IdentifierContext> cols) {
        List<IdentifierContext> ids = table.identifier();
        String schemaName = QNameParser.getSchemaName(ids, schema);
        String tableName = QNameParser.getFirstName(ids);
        for (IdentifierContext col : cols) {
            depcies.add(new GenericColumn(schemaName, tableName, col.getText(), DbObjType.COLUMN));
        }
    }

    protected void addColumnsDepcies(String schemaName, String tableOrView, List<Pair<String, String>> cols) {
        String sName = schemaName != null ? schemaName : this.schema;
        for (Pair<String, String> col : cols) {
            depcies.add(new GenericColumn(sName, tableOrView, col.getKey(), DbObjType.COLUMN));
        }
    }

    protected GenericColumn addColumnDepcy(String schemaName, String tableOrView, String columnName) {
        String sName = schemaName != null ? schemaName : this.schema;
        GenericColumn genericColumn = new GenericColumn(sName, tableOrView, columnName, DbObjType.COLUMN);
        depcies.add(genericColumn);
        return genericColumn;
    }

    protected void addFunctionSigDepcy(String signature) {
        SQLParser p = AntlrParser.makeBasicParser(SQLParser.class, signature, "function signature");
        Function_args_parserContext sig = p.function_args_parser();
        List<IdentifierContext> ids = sig.schema_qualified_name().identifier();
        depcies.add(new GenericColumn(getSchemaNameForFunction(QNameParser.getSchemaNameCtx(ids), signature),
                PgDiffUtils.getQuotedName(QNameParser.getFirstName(ids)) +
                ParserAbstract.getFullCtxText(sig.function_args()),
                DbObjType.FUNCTION));
    }

    protected void addSchemaDepcy(List<IdentifierContext> ids) {
        depcies.add(new GenericColumn(QNameParser.getFirstName(ids), DbObjType.SCHEMA));
    }

    /**
     * Returns colPairs (name-type) from the 'tableOrView' of 'qualSchemaName' schema.
     */
    protected List<Pair<String, String>> getTableOrViewColumns(String qualSchemaName, String tableOrView) {
        List<Pair<String, String>> ret = new ArrayList<>();
        findRelations(qualSchemaName, tableOrView)
        .forEach(relation -> ret.addAll(relation.getRelationColumns().collect(Collectors.toList())));
        return ret;
    }

    protected Stream<IRelation> findRelations(String schemaName, String relationName) {
        Stream<IRelation> foundRelations;
        if (PgSystemStorage.SCHEMA_PG_CATALOG.equals(schemaName)
                || PgSystemStorage.SCHEMA_INFORMATION_SCHEMA.equals(schemaName)) {
            foundRelations = systemStorage.getSchema(schemaName).getRelations()
                    .map(r -> (IRelation) r);
        } else if (schemaName != null) {
            foundRelations = db.getSchema(schemaName).getRelations();
        } else {
            foundRelations = Stream.concat(db.getSchema(schema).getRelations(),
                    systemStorage.getSchema(PgSystemStorage.SCHEMA_PG_CATALOG).getRelations());
        }

        return foundRelations.filter(r -> r.getName().equals(relationName));
    }
}