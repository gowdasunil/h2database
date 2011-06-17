/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.jaqu;

//## Java 1.5 begin ##
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.h2.jaqu.util.Utils;
//## Java 1.5 end ##

/**
 * This class represents a query.
 *
 * @param <T> the return type
 */
//## Java 1.5 begin ##
public class Query<T> {

    private Db db;
    private SelectTable<T> from;
    private ArrayList<Token> conditions = Utils.newArrayList();
    private ArrayList<SelectTable> joins = Utils.newArrayList();
    private final HashMap<Object, SelectColumn> aliasMap = Utils.newHashMap();
    private ArrayList<OrderExpression> orderByList = Utils.newArrayList();
    private Object[] groupByExpressions;

    Query(Db db) {
        this.db = db;
    }

    static <T> Query<T> from(Db db, T alias) {
        Query<T> query = new Query<T>(db);
        TableDefinition def = db.define(alias.getClass());
        query.from = new SelectTable(db, query, alias, false);
        def.initSelectObject(query.from, alias, query.aliasMap);
        return query;
    }

    public long selectCount() {
        SqlStatement selectList = new SqlStatement(db);
        selectList.setSQL("COUNT(*)");
        ResultSet rs = prepare(selectList, false).executeQuery();
        try {
            rs.next();
            long value = rs.getLong(1);
            return value;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<T> select() {
        return select(false);
    }

    public List<T> selectDistinct() {
        return select(true);
    }

    public <X, Z> X selectFirst(Z x) {
        List<X> list = (List<X>) select(x);
        return list.isEmpty() ? null : list.get(0);
    }

    public String getSQL() {
        SqlStatement selectList = new SqlStatement(db);
        selectList.setSQL("*");
        return prepare(selectList, false).getSQL().trim();
    }

    private List<T> select(boolean distinct) {
        List<T> result = Utils.newArrayList();
        SqlStatement selectList = new SqlStatement(db);
        selectList.setSQL("*");
        ResultSet rs = prepare(selectList, distinct).executeQuery();
        try {
            while (rs.next()) {
                T item = from.newObject();
                from.getAliasDefinition().readRow(item, rs);
                result.add(item);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public int delete() {
        SqlStatement stat = new SqlStatement(db);
        stat.appendSQL("DELETE FROM ");
        from.appendSQL(stat);
        appendWhere(stat);
        return stat.executeUpdate();
    }

    public <X, Z> List<X> selectDistinct(Z x) {
        return select(x, true);
    }

    public <X, Z> List<X> select(Z x) {
        return select(x, false);
    }

    private <X, Z> List<X> select(Z x, boolean distinct) {
        Class< ? > clazz = x.getClass();
        if (Utils.isSimpleType(clazz)) {
            return getSimple((X) x, distinct);
        }
        clazz = clazz.getSuperclass();
        return select((Class<X>) clazz, (X) x, distinct);
    }

    private <X> List<X> select(Class<X> clazz, X x, boolean distinct) {
        TableDefinition<X> def = db.define(clazz);
        SqlStatement selectList = def.getSelectList(this, x);
        ResultSet rs = prepare(selectList, distinct).executeQuery();
        List<X> result = Utils.newArrayList();
        try {
            while (rs.next()) {
                X row = Utils.newObject(clazz);
                def.readRow(row, rs);
                result.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private <X> List<X> getSimple(X x, boolean distinct) {
        SqlStatement selectList = new SqlStatement(db);
        appendSQL(selectList, x);
        ResultSet rs = prepare(selectList, distinct).executeQuery();
        List<X> result = Utils.newArrayList();
        try {
            while (rs.next()) {
                try {
                    X value = (X) rs.getObject(1);
                    result.add(value);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public <A> QueryCondition<T, A> where(A x) {
        return new QueryCondition<T, A>(this, x);
    }

    public QueryWhere<T> whereTrue(Boolean condition) {
        Token token = new Function("", condition);
        addConditionToken(token);
        return new QueryWhere<T>(this);
    }
//## Java 1.5 end ##

    /**
     * Order by a number of columns.
     *
     * @param expressions the columns
     * @return the query
     */
//## Java 1.5 begin ##
    public Query<T> orderBy(Object... expressions) {
        for (Object expr : expressions) {
            OrderExpression<Object> e =
                new OrderExpression<Object>(this, expr, false, false, false);
            addOrderBy(e);
        }
        return this;
    }

    public Query<T> orderByDesc(Object expr) {
        OrderExpression<Object> e =
            new OrderExpression<Object>(this, expr, true, false, false);
        addOrderBy(e);
        return this;
    }

    public Query<T> groupBy(Object... groupByExpressions) {
        this.groupByExpressions = groupByExpressions;
        return this;
    }

    void appendSQL(SqlStatement stat, Object x) {
        if (x == Function.count()) {
            stat.appendSQL("COUNT(*)");
            return;
        }
        Token token = Db.getToken(x);
        if (token != null) {
            token.appendSQL(stat, this);
            return;
        }
        SelectColumn col = aliasMap.get(x);
        if (col != null) {
            col.appendSQL(stat);
            return;
        }
        stat.appendSQL("?");
        stat.addParameter(x);
    }

    void addConditionToken(Token condition) {
        conditions.add(condition);
    }

    void appendWhere(SqlStatement stat) {
        if (!conditions.isEmpty()) {
            stat.appendSQL(" WHERE ");
            for (Token token : conditions) {
                token.appendSQL(stat, this);
                stat.appendSQL(" ");
            }
        }
    }

    SqlStatement prepare(SqlStatement selectList, boolean distinct) {
        SqlStatement stat = selectList;
        String selectSQL = stat.getSQL();
        stat.setSQL("");
        stat.appendSQL("SELECT ");
        if (distinct) {
            stat.appendSQL("DISTINCT ");
        }
        stat.appendSQL(selectSQL);
        stat.appendSQL(" FROM ");
        from.appendSQL(stat);
        for (SelectTable join : joins) {
            join.appendSQLAsJoin(stat, this);
        }
        appendWhere(stat);
        if (groupByExpressions != null) {
            stat.appendSQL(" GROUP BY ");
            for (int i = 0; i < groupByExpressions.length; i++) {
                if (i > 0) {
                    stat.appendSQL(", ");
                }
                Object obj = groupByExpressions[i];
                appendSQL(stat, obj);
                stat.appendSQL(" ");
            }
        }
        if (!orderByList.isEmpty()) {
            stat.appendSQL(" ORDER BY ");
            for (int i = 0; i < orderByList.size(); i++) {
                if (i > 0) {
                    stat.appendSQL(", ");
                }
                OrderExpression o = orderByList.get(i);
                o.appendSQL(stat);
                stat.appendSQL(" ");
            }
        }
        return stat;
    }
//## Java 1.5 end ##

    /**
     * Join another table.
     *
     * @param alias an alias for the table to join
     * @return the joined query
     */
//## Java 1.5 begin ##
    public QueryJoin innerJoin(Object alias) {
        TableDefinition def = db.define(alias.getClass());
        SelectTable join = new SelectTable(db, this, alias, false);
        def.initSelectObject(join, alias, aliasMap);
        joins.add(join);
        return new QueryJoin(this, join);
    }

    Db getDb() {
        return db;
    }

    boolean isJoin() {
        return !joins.isEmpty();
    }

    SelectColumn getSelectColumn(Object obj) {
        return aliasMap.get(obj);
    }

    void addOrderBy(OrderExpression expr) {
        orderByList.add(expr);
    }

}
//## Java 1.5 end ##
