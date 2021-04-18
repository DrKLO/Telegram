/*
 * Copyright (c) 2020 Samsung Electronics Co., Ltd. All rights reserved.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef VTASKQUEUE_H
#define VTASKQUEUE_H

#include <deque>

template <typename Task>
class TaskQueue {
    using lock_t = std::unique_lock<std::mutex>;
    std::deque<Task>      _q;
    bool                    _done{false};
    std::mutex              _mutex;
    std::condition_variable _ready;

public:
    bool try_pop(Task &task)
    {
        lock_t lock{_mutex, std::try_to_lock};
        if (!lock || _q.empty()) return false;
        task = std::move(_q.front());
        _q.pop_front();
        return true;
    }

    bool try_push(Task &&task)
    {
        {
            lock_t lock{_mutex, std::try_to_lock};
            if (!lock) return false;
            _q.push_back(std::move(task));
        }
        _ready.notify_one();
        return true;
    }

    void done()
    {
        {
            lock_t lock{_mutex};
            _done = true;
        }
        _ready.notify_all();
    }

    bool pop(Task &task)
    {
        lock_t lock{_mutex};
        while (_q.empty() && !_done) _ready.wait(lock);
        if (_q.empty()) return false;
        task = std::move(_q.front());
        _q.pop_front();
        return true;
    }

    void push(Task &&task)
    {
        {
            lock_t lock{_mutex};
            _q.push_back(std::move(task));
        }
        _ready.notify_one();
    }

};

#endif  // VTASKQUEUE_H
