// This file is licensed under the Elastic License 2.0. Copyright 2021 StarRocks Limited.

#include <memory>
#include <vector>

#include "column/chunk.h"
#include "storage/olap_cond.h"
#include "storage/row_cursor.h"
#include "storage/rowset/vectorized/rowset_options.h"
#include "storage/vectorized/delete_predicates.h"
#include "storage/vectorized/reader_params.h"
#include "storage/vectorized/seek_range.h"

namespace starrocks::vectorized {

class ColumnPredicate;
class RowsetReadOptions;

class Reader final : public ChunkIterator {
public:
    explicit Reader(Schema schema);
    ~Reader() override { close(); }

    Status init(const ReaderParams& read_params);

    void close() override;

    const OlapReaderStatistics& stats() const { return _stats; }
    OlapReaderStatistics* mutable_stats() { return &_stats; }

    size_t merged_rows() const override { return _collect_iter->merged_rows(); }

protected:
    Status do_get_next(Chunk* chunk) override;

private:
    using PredicateList = std::vector<const ColumnPredicate*>;
    using PredicateMap = std::unordered_map<ColumnId, PredicateList>;

    Status _parse_seek_range(const ReaderParams& read_params, std::vector<SeekRange>* ranges);
    Status _init_predicates(const ReaderParams& read_params);
    Status _init_load_bf_columns(const ReaderParams& read_params);
    Status _init_delete_predicates(const ReaderParams& read_params, DeletePredicates* dels);
    Status _init_collector(const ReaderParams& read_params);
    Status _to_seek_tuple(const TabletSchema& tablet_schema, const OlapTuple& input, SeekTuple* tuple);
    Status _get_segment_iterators(const TabletSharedPtr& tablet, const Version& version,
                                  const RowsetReadOptions& options, std::vector<ChunkIteratorPtr>* iters);

    MemTracker _memtracker;
    MemPool _mempool;

    // TODO(zhuming): unused now.
    std::set<uint32_t> _load_bf_columns;
    // TODO(zhuming): unused now.
    std::vector<uint32_t> _return_columns;

    PredicateMap _pushdown_predicates;
    DeletePredicates _delete_predicates;
    PredicateList _predicate_free_list;

    std::shared_ptr<ChunkIterator> _collect_iter;

    // TODO(zhuming): some metrics not updated now.
    OlapReaderStatistics _stats;
};

} // namespace starrocks::vectorized
