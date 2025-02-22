// This file is made available under Elastic License 2.0.
// This file is based on code available under the Apache license here:
//   https://github.com/apache/incubator-doris/blob/master/be/src/olap/iterators.h

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

#pragma once

#include <memory>

#include "common/status.h"
#include "storage/fs/fs_util.h"
#include "storage/olap_common.h"

namespace starrocks {

class RowCursor;
class RowBlockV2;
class Schema;
class Conditions;
class ColumnPredicate;
class ObjectPool;
class TabletSchema;

class StorageReadOptions {
public:
    struct KeyRange {
        KeyRange() : lower_key(nullptr), include_lower(false), upper_key(nullptr), include_upper(false) {}

        KeyRange(const RowCursor* lower_key_, bool include_lower_, const RowCursor* upper_key_, bool include_upper_)
                : lower_key(lower_key_),
                  include_lower(include_lower_),
                  upper_key(upper_key_),
                  include_upper(include_upper_) {}

        // the lower bound of the range, nullptr if not existed
        const RowCursor* lower_key;
        // whether `lower_key` is included in the range
        bool include_lower;
        // the upper bound of the range, nullptr if not existed
        const RowCursor* upper_key;
        // whether `upper_key` is included in the range
        bool include_upper;
    };

    fs::BlockManager* block_mgr = fs::fs_util::block_manager();

    // reader's key ranges, empty if not existed.
    // used by short key index to filter row blocks
    std::vector<KeyRange> key_ranges;

    // reader's column predicates, nullptr if not existed.
    // used by column index to filter pages and rows
    // TODO use vector<ColumnPredicate*> instead
    const Conditions* conditions = nullptr;

    // delete conditions used by column index to filter pages
    std::vector<const Conditions*> delete_conditions;
    // reader's column predicate, nullptr if not existed
    // used to fiter rows in row block
    // TODO(hkp): refactor the column predicate framework
    // to unify Conditions and ColumnPredicate
    const std::vector<const ColumnPredicate*>* column_predicates = nullptr;

    // REQUIRED (null is not allowed)
    OlapReaderStatistics* stats = nullptr;
    bool use_page_cache = false;

    Status convert_to(const StorageReadOptions** output, const TabletSchema& tablet_schema,
                      const std::vector<FieldType>& new_types, ObjectPool* obj_pool) const;
};

// Used to read data in RowBlockV2 one by one
class RowwiseIterator {
public:
    RowwiseIterator() {}
    virtual ~RowwiseIterator() {}

    // Initialize this iterator and make it ready to read with
    // input options.
    // Input options may contain scan range in which this scan.
    // Return Status::OK() if init successfully,
    // Return other error otherwise
    virtual Status init(const StorageReadOptions& opts) = 0;

    // If there is any valid data, this function will load data
    // into input batch with Status::OK() returned
    // If there is no data to read, will return Status::EndOfFile.
    // If other error happens, other error code will be returned.
    virtual Status next_batch(RowBlockV2* block) = 0;

    // return schema for this Iterator
    virtual const Schema& schema() const = 0;

    // Only used by UT. Whether lazy-materialization-read is used by this iterator or not.
    virtual bool is_lazy_materialization_read() const { return false; }

    // Return the data id such as segment id, used for keep the insert order when do
    // merge sort in priority queue
    virtual uint64_t data_id() const { return 0; }
};

} // namespace starrocks
