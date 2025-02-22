// This file is made available under Elastic License 2.0.
// This file is based on code available under the Apache license here:
//   https://github.com/apache/incubator-doris/blob/master/be/src/exec/schema_scanner/schema_collations_scanner.cpp

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

#include "exec/schema_scanner/schema_collations_scanner.h"

#include "runtime/primitive_type.h"
#include "runtime/string_value.h"

namespace starrocks {

SchemaScanner::ColumnDesc SchemaCollationsScanner::_s_cols_columns[] = {
        //   name,       type,          size
        {"COLLATION_NAME", TYPE_VARCHAR, sizeof(StringValue), false},
        {"CHARACTER_SET_NAME", TYPE_VARCHAR, sizeof(StringValue), false},
        {"ID", TYPE_BIGINT, sizeof(int64_t), false},
        {"IS_DEFAULT", TYPE_VARCHAR, sizeof(StringValue), false},
        {"IS_COMPILED", TYPE_VARCHAR, sizeof(StringValue), false},
        {"SORTLEN", TYPE_BIGINT, sizeof(int64_t), false},
};

SchemaCollationsScanner::CollationStruct SchemaCollationsScanner::_s_collations[] = {
        {"utf8_general_ci", "utf8", 33, "Yes", "Yes", 1},
        {NULL, NULL, 0, NULL, NULL, 0},
};

SchemaCollationsScanner::SchemaCollationsScanner()
        : SchemaScanner(_s_cols_columns, sizeof(_s_cols_columns) / sizeof(SchemaScanner::ColumnDesc)), _index(0) {}

SchemaCollationsScanner::~SchemaCollationsScanner() {}

Status SchemaCollationsScanner::fill_one_row(Tuple* tuple, MemPool* pool) {
    // COLLATION_NAME
    {
        void* slot = tuple->get_slot(_tuple_desc->slots()[0]->tuple_offset());
        StringValue* str_slot = reinterpret_cast<StringValue*>(slot);
        int len = strlen(_s_collations[_index].name);
        str_slot->ptr = (char*)pool->allocate(len + 1);
        if (NULL == str_slot->ptr) {
            return Status::InternalError("No Memory.");
        }
        memcpy(str_slot->ptr, _s_collations[_index].name, len + 1);
        str_slot->len = len;
    }
    // charset
    {
        void* slot = tuple->get_slot(_tuple_desc->slots()[1]->tuple_offset());
        StringValue* str_slot = reinterpret_cast<StringValue*>(slot);
        int len = strlen(_s_collations[_index].charset);
        str_slot->ptr = (char*)pool->allocate(len + 1);
        if (NULL == str_slot->ptr) {
            return Status::InternalError("No Memory.");
        }
        memcpy(str_slot->ptr, _s_collations[_index].charset, len + 1);
        str_slot->len = len;
    }
    // id
    {
        void* slot = tuple->get_slot(_tuple_desc->slots()[2]->tuple_offset());
        *(int64_t*)slot = _s_collations[_index].id;
    }
    // is_default
    {
        void* slot = tuple->get_slot(_tuple_desc->slots()[3]->tuple_offset());
        StringValue* str_slot = reinterpret_cast<StringValue*>(slot);
        int len = strlen(_s_collations[_index].is_default);
        str_slot->ptr = (char*)pool->allocate(len + 1);
        if (NULL == str_slot->ptr) {
            return Status::InternalError("No Memory.");
        }
        memcpy(str_slot->ptr, _s_collations[_index].is_default, len + 1);
        str_slot->len = len;
    }
    // IS_COMPILED
    {
        void* slot = tuple->get_slot(_tuple_desc->slots()[4]->tuple_offset());
        StringValue* str_slot = reinterpret_cast<StringValue*>(slot);
        int len = strlen(_s_collations[_index].is_compile);
        str_slot->ptr = (char*)pool->allocate(len + 1);
        if (NULL == str_slot->ptr) {
            return Status::InternalError("No Memory.");
        }
        memcpy(str_slot->ptr, _s_collations[_index].is_compile, len + 1);
        str_slot->len = len;
    }
    // sortlen
    {
        void* slot = tuple->get_slot(_tuple_desc->slots()[5]->tuple_offset());
        *(int64_t*)slot = _s_collations[_index].sortlen;
    }
    _index++;
    return Status::OK();
}

Status SchemaCollationsScanner::get_next_row(Tuple* tuple, MemPool* pool, bool* eos) {
    if (!_is_init) {
        return Status::InternalError("call this before initial.");
    }
    if (NULL == _s_collations[_index].name) {
        *eos = true;
        return Status::OK();
    }
    if (NULL == tuple || NULL == pool || NULL == eos) {
        return Status::InternalError("invalid parameter.");
    }
    *eos = false;
    return fill_one_row(tuple, pool);
}

} // namespace starrocks
