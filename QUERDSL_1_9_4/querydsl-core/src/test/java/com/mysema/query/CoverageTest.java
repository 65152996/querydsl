/*
 * Copyright (c) 2010 Mysema Ltd.
 * All rights reserved.
 *
 */
package com.mysema.query;

import static com.mysema.query.alias.Alias.$;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.mysema.query.alias.Alias;
import com.mysema.query.types.Expr;
import com.mysema.query.types.Operation;
import com.mysema.query.types.Operator;
import com.mysema.query.types.Ops;

/**
 * The Class CoverageTest.
 */
public class CoverageTest {

    public interface Entity{

        int getNum();

        String getStr();

        boolean isBool();

        List<String> getList();

        Set<String> getSet();

        Map<String, String> getMap();

        java.util.Date getDateTime();

        java.sql.Date getDate();

        java.sql.Time getTime();

        String[] getArray();
    }

    private MatchingFilters matchers = new MatchingFilters(Module.COLLECTIONS, Target.MEM);

    private Projections projections = new Projections(Module.COLLECTIONS, Target.MEM);

    private Filters filters = new Filters(projections, Module.COLLECTIONS, Target.MEM);

    @SuppressWarnings("unchecked")
    @Test
    public void test() throws IllegalArgumentException, IllegalAccessException{
        // make sure all Operators are covered in expression factory methods
        Set<Operator<?>> usedOperators = new HashSet<Operator<?>>();
        List<Expr<?>> exprs = new ArrayList<Expr<?>>();

        Entity entity = Alias.alias(Entity.class, "entity");
        // numeric
        exprs.addAll(projections.numeric($(entity.getNum()), $(entity.getNum()), 1, false));
        exprs.addAll(matchers.numeric($(entity.getNum()), $(entity.getNum()), 1));
        exprs.addAll(filters.numeric($(entity.getNum()), $(entity.getNum()), 1));
        exprs.addAll(projections.numericCasts($(entity.getNum()), $(entity.getNum()), 1));
        // string
        exprs.addAll(projections.string($(entity.getStr()), $(entity.getStr()), "abc"));
        exprs.addAll(matchers.string($(entity.getStr()), $(entity.getStr()), "abc"));
        exprs.addAll(filters.string($(entity.getStr()), $(entity.getStr()), "abc"));

        // date
        exprs.addAll(projections.date($(entity.getDate()), $(entity.getDate()), new java.sql.Date(0)));
        exprs.addAll(matchers.date($(entity.getDate()), $(entity.getDate()), new java.sql.Date(0)));
        exprs.addAll(filters.date($(entity.getDate()), $(entity.getDate()), new java.sql.Date(0)));
        // dateTime
        exprs.addAll(projections.dateTime($(entity.getDateTime()), $(entity.getDateTime()), new java.util.Date(0)));
        exprs.addAll(matchers.dateTime($(entity.getDateTime()), $(entity.getDateTime()), new java.util.Date(0)));
        exprs.addAll(filters.dateTime($(entity.getDateTime()), $(entity.getDateTime()), new java.util.Date(0)));
        // time
        exprs.addAll(projections.time($(entity.getTime()), $(entity.getTime()), new java.sql.Time(0)));
        exprs.addAll(matchers.time($(entity.getTime()), $(entity.getTime()), new java.sql.Time(0)));
        exprs.addAll(filters.time($(entity.getTime()), $(entity.getTime()), new java.sql.Time(0)));

        // boolean
        exprs.addAll(filters.booleanFilters($(entity.isBool()), $(entity.isBool())));
        // collection
        exprs.addAll(projections.list($(entity.getList()), $(entity.getList()), ""));
        exprs.addAll(filters.list($(entity.getList()), $(entity.getList()), ""));
        // array
        exprs.addAll(projections.array($(entity.getArray()), $(entity.getArray()), ""));
        exprs.addAll(filters.array($(entity.getArray()), $(entity.getArray()), ""));
        // map
        exprs.addAll(projections.map($(entity.getMap()), $(entity.getMap()), "", ""));
        exprs.addAll(filters.map($(entity.getMap()), $(entity.getMap()), "", ""));

        for (Expr<?> e : exprs){
            if (e instanceof Operation){
                Operation<?> op = (Operation<?>)e;
                if (op.getArg(0) instanceof Operation){
                    usedOperators.add(((Operation<?>)op.getArg(0)).getOperator());
                }else if (op.getArgs().size() > 1 && op.getArg(1) instanceof Operation){
                    usedOperators.add(((Operation<?>)op.getArg(1)).getOperator());
                }
                usedOperators.add(op.getOperator());
            }

        }

        // missing mappings
        usedOperators.addAll(Arrays.<Operator<?>>asList(
            Ops.INSTANCE_OF,
            Ops.ALIAS,
            Ops.ARRAY_SIZE,
            Ops.MOD,
            Ops.STRING_CAST,

            Ops.XOR,
            Ops.XNOR,

            Ops.CASE_WHEN,
            Ops.CASE_ELSE,

            Ops.CASE_EQ_WHEN,
            Ops.CASE_EQ_ELSE,

            Ops.LIST,
            Ops.COALESCE,
            Ops.ORDINAL, // TODO: add support

            // aggregation
            Ops.AggOps.AVG_AGG,
            Ops.AggOps.MAX_AGG,
            Ops.AggOps.MIN_AGG,
            Ops.AggOps.SUM_AGG,
            Ops.AggOps.COUNT_AGG,
            Ops.AggOps.COUNT_ALL_AGG,
            Ops.EXISTS
         ));

        List<Operator<?>> notContained = new ArrayList<Operator<?>>();
        for (Field field : Ops.class.getFields()){
            if (Operator.class.isAssignableFrom(field.getType())){
                Operator<?> val = (Operator<?>) field.get(null);
                if (!usedOperators.contains(val)){
                    System.err.println(field.getName() + " was not contained");
                    notContained.add(val);
                }
            }
        }

        assertTrue(notContained.size() + " errors in processing, see log for details", notContained.isEmpty());
    }

}