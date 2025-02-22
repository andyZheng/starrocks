// This file is made available under Elastic License 2.0.
// This file is based on code available under the Apache license here:
//   https://github.com/apache/incubator-doris/blob/master/fe/fe-core/src/main/java/org/apache/doris/common/MarkedCountDownLatch.java

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

package com.starrocks.common;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;

public class MarkedCountDownLatch<K, V> extends CountDownLatch {

    private Multimap<K, V> marks;
    private Status st = Status.OK;

    public MarkedCountDownLatch(int count) {
        super(count);
        marks = HashMultimap.create();
    }

    public void addMark(K key, V value) {
        marks.put(key, value);
    }

    public synchronized boolean markedCountDown(K key, V value) {
        if (marks.remove(key, value)) {
            super.countDown();
            return true;
        }
        return false;
    }

    public synchronized List<Entry<K, V>> getLeftMarks() {
        return Lists.newArrayList(marks.entries());
    }

    public Status getStatus() {
        return st;
    }

    public synchronized void countDownToZero(Status status) {
        // update status first before countDown.
        // so that the waiting thread will get the correct status.
        if (st.ok()) {
            st = status;
        }
        while (getCount() > 0) {
            super.countDown();
        }
    }
}
