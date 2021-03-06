package cz.startnet.utils.pgdiff.parsers.antlr.statements;

import java.util.List;

import cz.startnet.utils.pgdiff.parsers.antlr.QNameParser;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_fts_dictionaryContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.IdentifierContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Option_with_valueContext;
import cz.startnet.utils.pgdiff.schema.AbstractSchema;
import cz.startnet.utils.pgdiff.schema.GenericColumn;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgFtsDictionary;
import cz.startnet.utils.pgdiff.schema.PgStatement;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

public class CreateFtsDictionary extends ParserAbstract {

    private final Create_fts_dictionaryContext ctx;

    public CreateFtsDictionary(Create_fts_dictionaryContext ctx, PgDatabase db) {
        super(db);
        this.ctx = ctx;
    }

    @Override
    public PgStatement getObject() {
        List<IdentifierContext> ids = ctx.name.identifier();
        AbstractSchema schema = getSchemaSafe(ids, db.getDefaultSchema());
        String name = QNameParser.getFirstName(ids);
        PgFtsDictionary dictionary = new PgFtsDictionary(name, getFullCtxText(ctx.getParent()));
        for (Option_with_valueContext option : ctx.option_with_value()) {
            fillOptionParams(option.value.getText(), option.name.getText(), false, dictionary::addOption);
        }

        List<IdentifierContext> templateIds = ctx.template.identifier();
        dictionary.setTemplate(ParserAbstract.getFullCtxText(ctx.template));
        String templateSchema = QNameParser.getSchemaName(templateIds, getDefSchemaName());
        if (!"pg_catalog".equals(templateSchema)) {
            dictionary.addDep(new GenericColumn(templateSchema,
                    QNameParser.getFirstName(templateIds), DbObjType.FTS_TEMPLATE));
        }

        schema.addFtsDictionary(dictionary);
        return dictionary;
    }
}
