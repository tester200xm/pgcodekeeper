package cz.startnet.utils.pgdiff.loader.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

import cz.startnet.utils.pgdiff.PgDiffUtils;
import cz.startnet.utils.pgdiff.loader.JdbcQueries;
import cz.startnet.utils.pgdiff.parsers.antlr.statements.CreateDomain;
import cz.startnet.utils.pgdiff.schema.AbstractColumn;
import cz.startnet.utils.pgdiff.schema.AbstractConstraint;
import cz.startnet.utils.pgdiff.schema.AbstractSchema;
import cz.startnet.utils.pgdiff.schema.GenericColumn;
import cz.startnet.utils.pgdiff.schema.PgColumn;
import cz.startnet.utils.pgdiff.schema.PgConstraint;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgDomain;
import cz.startnet.utils.pgdiff.schema.PgStatement;
import cz.startnet.utils.pgdiff.schema.PgType;
import cz.startnet.utils.pgdiff.schema.PgType.PgTypeForm;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

public class TypesReader extends JdbcReader {

    static final String ADD_CONSTRAINT = "ALTER DOMAIN noname ADD CONSTRAINT noname ";

    public TypesReader(JdbcLoaderBase loader) {
        super(JdbcQueries.QUERY_TYPES_PER_SCHEMA, loader);
    }

    @Override
    protected void processResult(ResultSet result, AbstractSchema schema) throws SQLException {
        PgStatement typeOrDomain = getTypeDomain(result, schema);
        if (typeOrDomain != null) {
            if (typeOrDomain.getStatementType() == DbObjType.DOMAIN) {
                schema.addDomain((PgDomain) typeOrDomain);
            } else {
                schema.addType((PgType) typeOrDomain);
            }
        }
    }

    private PgStatement getTypeDomain(ResultSet res, AbstractSchema schema) throws SQLException {
        PgStatement st;
        String typtype = res.getString("typtype");
        if ("d".equals(typtype)) {
            st = getDomain(res, schema);
        } else {
            st = getType(res, schema.getName(), typtype);
        }
        if (st != null) {
            loader.setOwner(st, res.getLong("typowner"));
            loader.setPrivileges(st, res.getString("typacl"), schema.getName());
            String comment = res.getString("description");
            if (comment != null && !comment.isEmpty()) {
                st.setComment(loader.args, PgDiffUtils.quoteString(comment));
            }
        }
        return st;
    }

    private PgDomain getDomain(ResultSet res, AbstractSchema schema) throws SQLException {
        String schemaName = schema.getName();
        PgDomain d = new PgDomain(res.getString("typname"), "");
        loader.setCurrentObject(new GenericColumn(schemaName, d.getName(), DbObjType.DOMAIN));

        String type = res.getString("dom_basetypefmt");
        checkTypeValidity(type);
        d.setDataType(type);
        loader.cachedTypesByOid.get(res.getLong("dom_basetype")).addTypeDepcy(d);

        long collation = res.getLong("typcollation");
        if (collation != 0 && collation != res.getLong("dom_basecollation")) {
            d.setCollation(PgDiffUtils.getQuotedName(res.getString("dom_collationnspname"))
                    + '.' + PgDiffUtils.getQuotedName(res.getString("dom_collationname")));
        }

        PgDatabase dataBase = schema.getDatabase();

        String def = res.getString("dom_defaultbin");
        if (def == null) {
            def = res.getString("typdefault");
            if (def != null) {
                def = PgDiffUtils.quoteString(def);
            }
        } else {
            loader.submitAntlrTask(def, p -> p.vex_eof().vex().get(0),
                    ctx -> dataBase.addContextForAnalyze(d, ctx));
        }

        d.setDefaultValue(def);
        d.setNotNull(res.getBoolean("dom_notnull"));

        String[] connames = getColArray(res, "dom_connames");
        if (connames != null) {
            String[] condefs = getColArray(res, "dom_condefs");
            String[] concomments = getColArray(res, "dom_concomments");

            for (int i = 0; i < connames.length; ++i) {
                String conName = connames[i];
                AbstractConstraint c = new PgConstraint(conName, "");
                String definition = condefs[i];
                checkObjectValidity(definition, DbObjType.CONSTRAINT, conName);
                loader.submitAntlrTask(ADD_CONSTRAINT + definition + ';',
                        p -> p.sql().statement(0).schema_statement().schema_alter()
                        .alter_domain_statement().dom_constraint.common_constraint()
                        .check_boolean_expression(),
                        ctx -> CreateDomain.parseDomainConstraint(d, c, ctx, dataBase));

                d.addConstraint(c);
                if (concomments[i] != null && !concomments[i].isEmpty()) {
                    c.setComment(loader.args, PgDiffUtils.quoteString(concomments[i]));
                }
            }
        }

        return d;
    }

    private PgType getType(ResultSet res, String schemaName, String typtype) throws SQLException {
        String name = res.getString("typname");
        loader.setCurrentObject(new GenericColumn(schemaName, name, DbObjType.TYPE));
        PgType t;
        switch (typtype) {
        case "b":
            t = new PgType(name, PgTypeForm.BASE, "");

            t.setInputFunction(res.getString("typinput"));
            t.setOutputFunction(res.getString("typoutput"));
            if (res.getBoolean("typreceiveset")) {
                t.setReceiveFunction(res.getString("typreceive"));
            }
            if (res.getBoolean("typsendset")) {
                t.setSendFunction(res.getString("typsend"));
            }
            if (res.getBoolean("typmodinset")) {
                t.setTypmodInputFunction(res.getString("typmodin"));
            }
            if (res.getBoolean("typmodoutset")) {
                t.setTypmodOutputFunction(res.getString("typmodout"));
            }
            if (res.getBoolean("typanalyzeset")) {
                t.setAnalyzeFunction(res.getString("typanalyze"));
            }

            short len = res.getShort("typlen");
            t.setInternalLength(len == -1 ? "variable" : "" + len);
            t.setPassedByValue(res.getBoolean("typbyval"));

            String align = res.getString("typalign");
            switch (align) {
            case "c":
                align = "char";
                break;
            case "s":
                align = "int2";
                break;
            case "i":
                align = "int4";
                break;
            case "d":
                align = "double";
                break;
            }
            t.setAlignment(align);

            String storage = res.getString("typstorage");
            switch (storage) {
            case "p":
                storage = "plain";
                break;
            case "e":
                storage = "external";
                break;
            case "x":
                storage = "extended";
                break;
            case "m":
                storage = "main";
                break;
            }
            t.setStorage(storage);

            String cat = res.getString("typcategory");
            if (cat != null && !"U".equals(cat)) {
                t.setCategory(PgDiffUtils.quoteString(cat));
            }

            if (res.getBoolean("typispreferred")) {
                t.setPreferred("true");
            }

            String def = res.getString("typdefaultbin");
            if (def == null) {
                def = res.getString("typdefault");
                if (def != null) {
                    def = PgDiffUtils.quoteString(def);
                }
            }
            t.setDefaultValue(def);

            long elem = res.getLong("typelem");
            if (elem != 0) {
                JdbcType typeElem = loader.cachedTypesByOid.get(elem);
                t.setElement(typeElem.getFullName());
                // autogenerated array types are hidden
                // so we should only get explicitly defined typelems as dependencies
                typeElem.addTypeDepcy(t);
            }

            String delim = res.getString("typdelim");
            if (delim != null && !",".equals(delim)) {
                t.setDelimiter(PgDiffUtils.quoteString(delim));
            }

            if (res.getLong("typcollation") != 0) {
                t.setCollatable("true");
            }
            break;

        case "c":
            t = new PgType(name, PgTypeForm.COMPOSITE, "");

            String[] attnames = getColArray(res, "comp_attnames");
            if (attnames == null) {
                break;
            }
            String[] atttypes = getColArray(res, "comp_atttypdefns");
            Long[] atttypeids = getColArray(res, "comp_atttypids");
            Long[] attcollations = getColArray(res, "comp_attcollations");
            Long[] atttypcollations = getColArray(res, "comp_atttypcollations");
            String[] attcollationnames = getColArray(res, "comp_attcollationnames");
            String[] attcollationnspnames = getColArray(res, "comp_attcollationnspnames");
            String[] attcomments = getColArray(res, "comp_attcomments");

            for (int i = 0; i < attnames.length; ++i) {
                AbstractColumn a = new PgColumn(attnames[i]);
                String type = atttypes[i];
                checkTypeValidity(type);
                a.setType(type);
                loader.cachedTypesByOid.get(atttypeids[i]).addTypeDepcy(t);

                // unbox
                long attcollation = attcollations[i];
                if (attcollation != 0 && attcollation != atttypcollations[i]) {
                    a.setCollation(PgDiffUtils.getQuotedName(attcollationnspnames[i])
                            + '.' + PgDiffUtils.getQuotedName(attcollationnames[i]));
                }
                t.addAttr(a);
                if (attcomments[i] != null && !attcomments[i].isEmpty()) {
                    a.setComment(loader.args, PgDiffUtils.quoteString(attcomments[i]));
                }
            }
            break;

        case "e":
            t = new PgType(name, PgTypeForm.ENUM, "");

            String[] enums = getColArray(res, "enums");
            if (enums == null) {
                break;
            }
            for (String enum_ : enums) {
                t.addEnum(PgDiffUtils.quoteString(enum_));
            }
            break;

        case "r":
            t = new PgType(name, PgTypeForm.RANGE, "");

            JdbcType subtype = loader.cachedTypesByOid.get(res.getLong("rngsubtype"));
            t.setSubtype(subtype.getFullName());
            subtype.addTypeDepcy(t);

            if (!res.getBoolean("opcdefault")) {
                t.setSubtypeOpClass(PgDiffUtils.getQuotedName(res.getString("opcnspname")
                        + '.' + PgDiffUtils.getQuotedName(res.getString("opcname"))));
            }

            long collation = res.getLong("rngcollation");
            if (collation != 0 && collation != res.getLong("rngsubtypcollation")) {
                t.setCollation(PgDiffUtils.getQuotedName(res.getString("rngcollationnspname"))
                        + '.' + PgDiffUtils.getQuotedName(res.getString("rngcollationname")));
            }

            if (res.getBoolean("rngcanonicalset")) {
                t.setCanonical(res.getString("rngcanonical"));
            }
            if (res.getBoolean("rngsubdiffset")) {
                t.setCanonical(res.getString("rngsubdiff"));
            }
            break;
        default:
            t = null;
        }
        return t;
    }

    @Override
    protected DbObjType getType() {
        return DbObjType.TYPE;
    }
}
