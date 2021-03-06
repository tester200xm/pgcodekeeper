package cz.startnet.utils.pgdiff.schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import cz.startnet.utils.pgdiff.PgDiffUtils;
import cz.startnet.utils.pgdiff.hashers.Hasher;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

public class PgFtsConfiguration extends PgStatementWithSearchPath {

    private String parser;
    /**key - fragment, value - dictionaries */
    private final Map<String, String> dictionariesMap = new HashMap<>();


    public PgFtsConfiguration(String name, String rawStatement) {
        super(name, rawStatement);
    }

    @Override
    public DbObjType getStatementType() {
        return DbObjType.FTS_CONFIGURATION;
    }

    @Override
    public AbstractSchema getContainingSchema() {
        return (AbstractSchema)getParent();
    }

    @Override
    public String getCreationSQL() {
        StringBuilder sbSql = new StringBuilder();
        sbSql.append("CREATE TEXT SEARCH CONFIGURATION ")
        .append(PgDiffUtils.getQuotedName(getContainingSchema().getName())).append('.')
        .append(PgDiffUtils.getQuotedName(getName())).append(" (\n\t");
        sbSql.append("PARSER = ").append(parser).append(" );");

        dictionariesMap.forEach((fragment, dictionaries) -> {
            sbSql.append("\n\nALTER TEXT SEARCH CONFIGURATION ")
            .append(PgDiffUtils.getQuotedName(getContainingSchema().getName())).append('.')
            .append(PgDiffUtils.getQuotedName(getName()));
            sbSql.append("\n\tADD MAPPING FOR ").append(fragment)
            .append("\n\tWITH ").append(dictionaries).append(";");
        });

        appendOwnerSQL(sbSql);

        if (comment != null && !comment.isEmpty()) {
            sbSql.append("\n\n");
            appendCommentSql(sbSql);
        }

        return sbSql.toString();
    }

    @Override
    protected StringBuilder appendCommentSql(StringBuilder sb) {
        sb.append("COMMENT ON TEXT SEARCH CONFIGURATION ");
        sb.append(PgDiffUtils.getQuotedName(getContainingSchema().getName()))
        .append('.').append(PgDiffUtils.getQuotedName(getName()));
        return sb.append(" IS ").append(comment).append(';');
    }

    @Override
    protected StringBuilder appendOwnerSQL(StringBuilder sb) {
        return owner == null ? sb
                : sb.append("\n\nALTER TEXT SEARCH CONFIGURATION ")
                .append(PgDiffUtils.getQuotedName(getContainingSchema().getName()))
                .append('.').append(PgDiffUtils.getQuotedName(getName()))
                .append(" OWNER TO ").append(PgDiffUtils.getQuotedName(owner)).append(';');
    }

    @Override
    public String getDropSQL() {
        return "DROP TEXT SEARCH CONFIGURATION " + PgDiffUtils.getQuotedName(getContainingSchema().getName())
        + '.' + PgDiffUtils.getQuotedName(getName()) + ';';
    }

    @Override
    public boolean appendAlterSQL(PgStatement newCondition, StringBuilder sb,
            AtomicBoolean isNeedDepcies) {
        final int startLength = sb.length();
        PgFtsConfiguration newConf;
        if (newCondition instanceof PgFtsConfiguration) {
            newConf = (PgFtsConfiguration) newCondition;
            if (!newConf.getParser().equals(parser)) {
                isNeedDepcies.set(true);
                return true;
            }
        } else {
            return false;
        }

        if (!Objects.equals(getComment(), newCondition.getComment())) {
            sb.append("\n\n");
            newCondition.appendCommentSql(sb);
        }

        compareOptions(newConf, sb);
        return sb.length() > startLength;
    }

    public void compareOptions(PgFtsConfiguration newConf, StringBuilder sb) {
        Map <String, String> oldMap = dictionariesMap;
        Map <String, String> newMap = newConf.dictionariesMap;

        if (oldMap.isEmpty() && newMap.isEmpty()) {
            return;
        }

        oldMap.forEach((fragment, dictionaries) -> {
            String newDictionaries = newMap.get(fragment);

            if (newDictionaries == null) {
                sb.append("\n\nALTER TEXT SEARCH CONFIGURATION ")
                .append(PgDiffUtils.getQuotedName(getContainingSchema().getName()))
                .append('.').append(PgDiffUtils.getQuotedName(getName()))
                .append("\n\tDROP MAPPING FOR ").append(fragment).append(';');
            } else if (!dictionaries.equals(newDictionaries)) {
                sb.append("\n\nALTER TEXT SEARCH CONFIGURATION ")
                .append(PgDiffUtils.getQuotedName(getContainingSchema().getName()))
                .append('.').append(PgDiffUtils.getQuotedName(getName()))
                .append("\n\tALTER MAPPING FOR ").append(fragment)
                .append("\n\tWITH ").append(newDictionaries).append(";");
            }
        });

        newMap.forEach((fragment, dictionaries) -> {
            if (!oldMap.containsKey(fragment)) {
                sb.append("\n\nALTER TEXT SEARCH CONFIGURATION ")
                .append(PgDiffUtils.getQuotedName(getContainingSchema().getName()))
                .append('.').append(PgDiffUtils.getQuotedName(getName()))
                .append("\n\tADD MAPPING FOR ").append(fragment)
                .append("\n\tWITH ").append(dictionaries).append(";");
            }
        });
    }

    @Override
    public PgFtsConfiguration shallowCopy() {
        PgFtsConfiguration conf = new PgFtsConfiguration(getName(), getRawStatement());
        conf.setComment(getComment());
        conf.setParser(getParser());
        conf.deps.addAll(deps);
        conf.setOwner(getOwner());
        conf.dictionariesMap.putAll(getDictionariesMap());
        conf.setLocation(getLocation());
        return conf;
    }

    @Override
    public PgFtsConfiguration deepCopy() {
        return shallowCopy();
    }

    @Override
    public boolean compare(PgStatement obj) {
        boolean eq = false;

        if (this == obj) {
            eq = true;
        } else if(obj instanceof PgFtsConfiguration) {
            PgFtsConfiguration config = (PgFtsConfiguration) obj;
            eq = Objects.equals(name, config.name)
                    && Objects.equals(parser, config.getParser())
                    && Objects.equals(owner, config.getOwner())
                    && Objects.equals(comment, config.getComment())
                    && Objects.equals(dictionariesMap, config.dictionariesMap);
        }

        return eq;
    }

    @Override
    public void computeHash(Hasher hasher) {
        hasher.put(name);
        hasher.put(owner);
        hasher.put(parser);
        hasher.put(dictionariesMap);
        hasher.put(comment);
    }

    public String getParser() {
        return parser;
    }

    public void setParser(final String parser) {
        this.parser = parser;
        resetHash();
    }

    public void addDictionary(String fragment, List<String> dictionaries) {
        dictionariesMap.put(fragment, String.join(", ", dictionaries));
        resetHash();
    }

    public Map<String, String> getDictionariesMap() {
        return Collections.unmodifiableMap(dictionariesMap);
    }
}
