/*
 * Copyright (c) 2010 Mysema Ltd.
 * All rights reserved.
 *
 */
package com.mysema.query.serialization;

import com.mysema.query.types.FactoryExpression;
import com.mysema.query.types.Param;
import com.mysema.query.types.SerializerBase;
import com.mysema.query.types.SubQueryExpression;
import com.mysema.query.types.Templates;

public class DummySerializer extends SerializerBase<DummySerializer>{

    public DummySerializer(Templates patterns) {
    super(patterns);
    }

    @Override
    public void visit(SubQueryExpression<?> query) {
    // TODO Auto-generated method stub
    }

    @Override
    public void visit(FactoryExpression<?> expr) {
    // TODO Auto-generated method stub
    }

    @Override
    public void visit(Param<?> param) {
        // TODO Auto-generated method stub

    }

}