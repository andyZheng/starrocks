// This file is made available under Elastic License 2.0.
// This file is based on code available under the Apache license here:
//   https://github.com/apache/incubator-doris/blob/master/be/src/exprs/in_predicate.h

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

#ifndef STARROCKS_BE_SRC_QUERY_EXPRS_IN_PREDICATE_H
#define STARROCKS_BE_SRC_QUERY_EXPRS_IN_PREDICATE_H

#include <boost/unordered_set.hpp>
#include <string>

#include "exprs/hybird_set.h"
#include "exprs/predicate.h"
#include "runtime/raw_value.h"

namespace starrocks {

// has two method:
// 1. construct from TExprNode
// 2. construct by new one, and push child.
class InPredicate : public Predicate {
public:
    virtual ~InPredicate();
    virtual Expr* clone(ObjectPool* pool) const override { return pool->add(new InPredicate(*this)); }

    Status prepare(RuntimeState* state, const TypeDescriptor&);
    Status open(RuntimeState* state, ExprContext* context, FunctionContext::FunctionStateScope scope);
    virtual Status prepare(RuntimeState* state, const RowDescriptor& row_desc, ExprContext* context);

    virtual BooleanVal get_boolean_val(ExprContext* context, TupleRow* row);

    // this function add one item in hashset, not add to children.
    // if add to children, when List is long, copy is a expensive op.
    void insert(void* value);

    HybirdSetBase* hybird_set() const { return _hybird_set.get(); }

    bool is_not_in() const { return _is_not_in; }

protected:
    friend class Expr;
    friend class HashJoinNode;

    InPredicate(const TExprNode& node);

    // virtual Status prepare(RuntimeState* state, const RowDescriptor& desc);
    virtual std::string debug_string() const;

private:
    const bool _is_not_in;
    bool _is_prepare;
    bool _null_in_set;
    std::shared_ptr<HybirdSetBase> _hybird_set;
};

} // namespace starrocks

#endif
