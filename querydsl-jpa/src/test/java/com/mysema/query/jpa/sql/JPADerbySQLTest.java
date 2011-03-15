/*
 * Copyright (c) 2010 Mysema Ltd.
 * All rights reserved.
 *
 */
package com.mysema.query.jpa.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.mysema.query.SearchResults;
import com.mysema.query.jpa.domain.Cat;
import com.mysema.query.jpa.domain.QCat;
import com.mysema.query.jpa.domain.sql.SAnimal;
import com.mysema.query.sql.DerbyTemplates;
import com.mysema.query.sql.SQLTemplates;
import com.mysema.query.types.ConstructorExpression;
import com.mysema.query.types.Expression;
import com.mysema.testutil.JPAConfig;
import com.mysema.testutil.JPATestRunner;

@RunWith(JPATestRunner.class)
@JPAConfig("derby")
public class JPADerbySQLTest {

    private static final SQLTemplates derbyTemplates = new DerbyTemplates();

    private EntityManager entityManager;

    protected JPASQLQuery query(){
        return new JPASQLQuery(entityManager, derbyTemplates);
    }

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Before
    public void setUp(){
        entityManager.persist(new Cat("Beck",1));
        entityManager.persist(new Cat("Kate",2));
        entityManager.persist(new Cat("Kitty",3));
        entityManager.persist(new Cat("Bobby",4));
        entityManager.persist(new Cat("Harold",5));
        entityManager.persist(new Cat("Tim",6));
        entityManager.flush();
    }

    @Test
    public void ScalarQueries(){
        SAnimal cat = new SAnimal("cat");

        // count
        assertEquals(6l, query().from(cat).where(cat.dtype.eq("C")).count());

        // countDistinct
        assertEquals(6l, query().from(cat).where(cat.dtype.eq("C")).countDistinct());

        // list
        assertEquals(6, query().from(cat).where(cat.dtype.eq("C")).list(cat.id).size());

        // list with limit
        assertEquals(3, query().from(cat).limit(3).list(cat.id).size());

        // list with offset
        assertEquals(3, query().from(cat).offset(3).list(cat.id).size());

        // list with limit and offset
        assertEquals(3, query().from(cat).offset(3).limit(3).list(cat.id).size());

        // list multiple
        print(query().from(cat).where(cat.dtype.eq("C")).list(cat.id, cat.name, cat.bodyweight));

        // listResults
        SearchResults<String> results = query().from(cat).limit(3).orderBy(cat.name.asc()).listResults(cat.name);
        assertEquals(Arrays.asList("Beck","Bobby","Harold"), results.getResults());
        assertEquals(6l, results.getTotal());
        
        // unique Result
        query().from(cat).limit(1).uniqueResult(cat.id);
        
        query().from(cat).limit(1).uniqueResult(new Expression[]{cat.id});
        
        // single Result
        query().from(cat).singleResult(cat.id);
        
        query().from(cat).singleResult(new Expression[]{cat.id});

    }

    @Test
    public void EntityQueries(){
        SAnimal cat = new SAnimal("cat");
        SAnimal mate = new SAnimal("mate");
        QCat catEntity = QCat.cat;

        // 1
        List<Cat> cats = query().from(cat).orderBy(cat.name.asc()).list(catEntity);
        assertEquals(6, cats.size());
        for (Cat c : cats){
            System.out.println(c.getName());
        }

        // 2
        cats = query().from(cat)
            .innerJoin(mate).on(cat.mateId.eq(mate.id))
            .where(cat.dtype.eq("C"), mate.dtype.eq("C"))
            .list(catEntity);
        assertTrue(cats.isEmpty());
    }

    @Test
    @Ignore
    public void EntityProjections(){
        // not yet supported
        SAnimal cat = new SAnimal("cat");

        List<Cat> cats = query().from(cat).orderBy(cat.name.asc()).list(ConstructorExpression.create(Cat.class, cat.name, cat.id));
        assertEquals(6, cats.size());
        for (Cat c : cats){
            System.out.println(c.getName());
        }
    }

    @Test
    public void Wildcard(){
        SAnimal cat = new SAnimal("cat");

        List<Object[]> rows = query().from(cat).list(cat.all());
        assertEquals(6, rows.size());
        print(rows);

//        rows = query().from(cat).list(cat.id, cat.all());
//        assertEquals(6, rows.size());
//        print(rows);
    }
    
    @Test
    public void Null_as_uniqueResult(){
        SAnimal cat = new SAnimal("cat");
        assertNull(query().from(cat).where(cat.name.eq(UUID.randomUUID().toString())).uniqueResult(cat.name));
    }
    

    private void print(Iterable<Object[]> rows){
        for (Object[] row : rows){
            System.out.println(Arrays.asList(row));
        }
    }    

}
