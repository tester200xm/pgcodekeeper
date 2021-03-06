/**
 * Copyright 2006 StartNet s.r.o.
 *
 * Distributed under MIT license
 */
package cz.startnet.utils.pgdiff.schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import cz.startnet.utils.pgdiff.hashers.Hasher;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

/**
 * Stores table index information.
 */
public abstract class AbstractIndex extends PgStatementWithSearchPath
implements PgOptionContainer {

    /**
     * Contains USING method for PG, columns and include columns for all
     */
    private String definition;
    private String tableName;
    private String where;
    private String tableSpace;
    private boolean unique;
    private boolean clusterIndex;
    private final Set<String> columns = new HashSet<>();

    protected final Map<String, String> options = new HashMap<>();

    @Override
    public DbObjType getStatementType() {
        return DbObjType.INDEX;
    }

    public AbstractIndex(String name, String rawStatement) {
        super(name, rawStatement);
    }

    public void setDefinition(final String definition) {
        this.definition = definition;
        resetHash();
    }

    public String getDefinition() {
        return definition;
    }

    public void setClusterIndex(boolean value) {
        clusterIndex = value;
        resetHash();
    }

    public boolean isClusterIndex() {
        return clusterIndex;
    }

    public void addColumn(String column) {
        columns.add(column);
    }

    public Set<String> getColumns(){
        return Collections.unmodifiableSet(columns);
    }

    public void setTableName(final String tableName) {
        this.tableName = tableName;
        resetHash();
    }

    public String getTableName() {
        return tableName;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(final boolean unique) {
        this.unique = unique;
        resetHash();
    }

    public String getWhere() {
        return where;
    }

    public void setWhere(final String where) {
        this.where = where;
        resetHash();
    }

    public String getTableSpace() {
        return tableSpace;
    }

    public void setTableSpace(String tableSpace) {
        this.tableSpace = tableSpace;
        resetHash();
    }

    @Override
    public Map<String, String> getOptions() {
        return Collections.unmodifiableMap(options);
    }

    @Override
    public void addOption(String key, String value) {
        options.put(key, value);
    }

    @Override
    public boolean compare(PgStatement obj) {
        boolean equals = false;

        if (this == obj) {
            equals = true;
        } else if (obj instanceof AbstractIndex) {
            AbstractIndex index = (AbstractIndex) obj;
            equals = compareWithoutComments(index)
                    && Objects.equals(comment, index.getComment())
                    && clusterIndex == index.isClusterIndex()
                    && Objects.equals(options, index.options);
        }

        return equals;
    }

    protected boolean compareWithoutComments(AbstractIndex index) {
        return Objects.equals(definition, index.getDefinition())
                && Objects.equals(name, index.getName())
                && Objects.equals(tableName, index.getTableName())
                && Objects.equals(where, index.getWhere())
                && Objects.equals(tableSpace, index.getTableSpace())
                && unique == index.isUnique();
    }


    @Override
    public void computeHash(Hasher hasher) {
        hasher.put(definition);
        hasher.put(name);
        hasher.put(tableName);
        hasher.put(unique);
        hasher.put(clusterIndex);
        hasher.put(where);
        hasher.put(tableSpace);
        hasher.put(options);
        hasher.put(comment);
    }

    @Override
    public AbstractIndex shallowCopy() {
        AbstractIndex indexDst = getIndexCopy();
        indexDst.setDefinition(getDefinition());
        indexDst.setTableName(getTableName());
        indexDst.setUnique(isUnique());
        indexDst.setClusterIndex(isClusterIndex());
        indexDst.setComment(getComment());
        indexDst.setWhere(getWhere());
        indexDst.setTableSpace(getTableSpace());
        indexDst.deps.addAll(deps);
        indexDst.columns.addAll(columns);
        indexDst.options.putAll(options);
        indexDst.setLocation(getLocation());
        return indexDst;
    }

    protected abstract AbstractIndex getIndexCopy();

    @Override
    public AbstractIndex deepCopy() {
        return shallowCopy();
    }

    @Override
    public AbstractSchema getContainingSchema() {
        return (AbstractSchema) getParent().getParent();
    }
}
