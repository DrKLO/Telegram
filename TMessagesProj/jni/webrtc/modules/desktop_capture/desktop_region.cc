/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/desktop_region.h"

#include <assert.h>

#include <algorithm>
#include <utility>

namespace webrtc {

DesktopRegion::RowSpan::RowSpan(int32_t left, int32_t right)
    : left(left), right(right) {}

DesktopRegion::Row::Row(const Row&) = default;
DesktopRegion::Row::Row(Row&&) = default;

DesktopRegion::Row::Row(int32_t top, int32_t bottom)
    : top(top), bottom(bottom) {}

DesktopRegion::Row::~Row() {}

DesktopRegion::DesktopRegion() {}

DesktopRegion::DesktopRegion(const DesktopRect& rect) {
  AddRect(rect);
}

DesktopRegion::DesktopRegion(const DesktopRect* rects, int count) {
  AddRects(rects, count);
}

DesktopRegion::DesktopRegion(const DesktopRegion& other) {
  *this = other;
}

DesktopRegion::~DesktopRegion() {
  Clear();
}

DesktopRegion& DesktopRegion::operator=(const DesktopRegion& other) {
  Clear();
  rows_ = other.rows_;
  for (Rows::iterator it = rows_.begin(); it != rows_.end(); ++it) {
    // Copy each row.
    Row* row = it->second;
    it->second = new Row(*row);
  }
  return *this;
}

bool DesktopRegion::Equals(const DesktopRegion& region) const {
  // Iterate over rows of the tow regions and compare each row.
  Rows::const_iterator it1 = rows_.begin();
  Rows::const_iterator it2 = region.rows_.begin();
  while (it1 != rows_.end()) {
    if (it2 == region.rows_.end() || it1->first != it2->first ||
        it1->second->top != it2->second->top ||
        it1->second->bottom != it2->second->bottom ||
        it1->second->spans != it2->second->spans) {
      return false;
    }
    ++it1;
    ++it2;
  }
  return it2 == region.rows_.end();
}

void DesktopRegion::Clear() {
  for (Rows::iterator row = rows_.begin(); row != rows_.end(); ++row) {
    delete row->second;
  }
  rows_.clear();
}

void DesktopRegion::SetRect(const DesktopRect& rect) {
  Clear();
  AddRect(rect);
}

void DesktopRegion::AddRect(const DesktopRect& rect) {
  if (rect.is_empty())
    return;

  // Top of the part of the |rect| that hasn't been inserted yet. Increased as
  // we iterate over the rows until it reaches |rect.bottom()|.
  int top = rect.top();

  // Iterate over all rows that may intersect with |rect| and add new rows when
  // necessary.
  Rows::iterator row = rows_.upper_bound(top);
  while (top < rect.bottom()) {
    if (row == rows_.end() || top < row->second->top) {
      // If |top| is above the top of the current |row| then add a new row above
      // the current one.
      int32_t bottom = rect.bottom();
      if (row != rows_.end() && row->second->top < bottom)
        bottom = row->second->top;
      row = rows_.insert(row, Rows::value_type(bottom, new Row(top, bottom)));
    } else if (top > row->second->top) {
      // If the |top| falls in the middle of the |row| then split |row| into
      // two, at |top|, and leave |row| referring to the lower of the two,
      // ready to insert a new span into.
      assert(top <= row->second->bottom);
      Rows::iterator new_row = rows_.insert(
          row, Rows::value_type(top, new Row(row->second->top, top)));
      row->second->top = top;
      new_row->second->spans = row->second->spans;
    }

    if (rect.bottom() < row->second->bottom) {
      // If the bottom of the |rect| falls in the middle of the |row| split
      // |row| into two, at |top|, and leave |row| referring to the upper of
      // the two, ready to insert a new span into.
      Rows::iterator new_row = rows_.insert(
          row, Rows::value_type(rect.bottom(), new Row(top, rect.bottom())));
      row->second->top = rect.bottom();
      new_row->second->spans = row->second->spans;
      row = new_row;
    }

    // Add a new span to the current row.
    AddSpanToRow(row->second, rect.left(), rect.right());
    top = row->second->bottom;

    MergeWithPrecedingRow(row);

    // Move to the next row.
    ++row;
  }

  if (row != rows_.end())
    MergeWithPrecedingRow(row);
}

void DesktopRegion::AddRects(const DesktopRect* rects, int count) {
  for (int i = 0; i < count; ++i) {
    AddRect(rects[i]);
  }
}

void DesktopRegion::MergeWithPrecedingRow(Rows::iterator row) {
  assert(row != rows_.end());

  if (row != rows_.begin()) {
    Rows::iterator previous_row = row;
    previous_row--;

    // If |row| and |previous_row| are next to each other and contain the same
    // set of spans then they can be merged.
    if (previous_row->second->bottom == row->second->top &&
        previous_row->second->spans == row->second->spans) {
      row->second->top = previous_row->second->top;
      delete previous_row->second;
      rows_.erase(previous_row);
    }
  }
}

void DesktopRegion::AddRegion(const DesktopRegion& region) {
  // TODO(sergeyu): This function is not optimized - potentially it can iterate
  // over rows of the two regions similar to how it works in Intersect().
  for (Iterator it(region); !it.IsAtEnd(); it.Advance()) {
    AddRect(it.rect());
  }
}

void DesktopRegion::Intersect(const DesktopRegion& region1,
                              const DesktopRegion& region2) {
  Clear();

  Rows::const_iterator it1 = region1.rows_.begin();
  Rows::const_iterator end1 = region1.rows_.end();
  Rows::const_iterator it2 = region2.rows_.begin();
  Rows::const_iterator end2 = region2.rows_.end();
  if (it1 == end1 || it2 == end2)
    return;

  while (it1 != end1 && it2 != end2) {
    // Arrange for |it1| to always be the top-most of the rows.
    if (it2->second->top < it1->second->top) {
      std::swap(it1, it2);
      std::swap(end1, end2);
    }

    // Skip |it1| if it doesn't intersect |it2| at all.
    if (it1->second->bottom <= it2->second->top) {
      ++it1;
      continue;
    }

    // Top of the |it1| row is above the top of |it2|, so top of the
    // intersection is always the top of |it2|.
    int32_t top = it2->second->top;
    int32_t bottom = std::min(it1->second->bottom, it2->second->bottom);

    Rows::iterator new_row = rows_.insert(
        rows_.end(), Rows::value_type(bottom, new Row(top, bottom)));
    IntersectRows(it1->second->spans, it2->second->spans,
                  &new_row->second->spans);
    if (new_row->second->spans.empty()) {
      delete new_row->second;
      rows_.erase(new_row);
    } else {
      MergeWithPrecedingRow(new_row);
    }

    // If |it1| was completely consumed, move to the next one.
    if (it1->second->bottom == bottom)
      ++it1;
    // If |it2| was completely consumed, move to the next one.
    if (it2->second->bottom == bottom)
      ++it2;
  }
}

// static
void DesktopRegion::IntersectRows(const RowSpanSet& set1,
                                  const RowSpanSet& set2,
                                  RowSpanSet* output) {
  RowSpanSet::const_iterator it1 = set1.begin();
  RowSpanSet::const_iterator end1 = set1.end();
  RowSpanSet::const_iterator it2 = set2.begin();
  RowSpanSet::const_iterator end2 = set2.end();
  assert(it1 != end1 && it2 != end2);

  do {
    // Arrange for |it1| to always be the left-most of the spans.
    if (it2->left < it1->left) {
      std::swap(it1, it2);
      std::swap(end1, end2);
    }

    // Skip |it1| if it doesn't intersect |it2| at all.
    if (it1->right <= it2->left) {
      ++it1;
      continue;
    }

    int32_t left = it2->left;
    int32_t right = std::min(it1->right, it2->right);
    assert(left < right);

    output->push_back(RowSpan(left, right));

    // If |it1| was completely consumed, move to the next one.
    if (it1->right == right)
      ++it1;
    // If |it2| was completely consumed, move to the next one.
    if (it2->right == right)
      ++it2;
  } while (it1 != end1 && it2 != end2);
}

void DesktopRegion::IntersectWith(const DesktopRegion& region) {
  DesktopRegion old_region;
  Swap(&old_region);
  Intersect(old_region, region);
}

void DesktopRegion::IntersectWith(const DesktopRect& rect) {
  DesktopRegion region;
  region.AddRect(rect);
  IntersectWith(region);
}

void DesktopRegion::Subtract(const DesktopRegion& region) {
  if (region.rows_.empty())
    return;

  // |row_b| refers to the current row being subtracted.
  Rows::const_iterator row_b = region.rows_.begin();

  // Current vertical position at which subtraction is happening.
  int top = row_b->second->top;

  // |row_a| refers to the current row we are subtracting from. Skip all rows
  // above |top|.
  Rows::iterator row_a = rows_.upper_bound(top);

  // Step through rows of the both regions subtracting content of |row_b| from
  // |row_a|.
  while (row_a != rows_.end() && row_b != region.rows_.end()) {
    // Skip |row_a| if it doesn't intersect with the |row_b|.
    if (row_a->second->bottom <= top) {
      // Each output row is merged with previously-processed rows before further
      // rows are processed.
      MergeWithPrecedingRow(row_a);
      ++row_a;
      continue;
    }

    if (top > row_a->second->top) {
      // If |top| falls in the middle of |row_a| then split |row_a| into two, at
      // |top|, and leave |row_a| referring to the lower of the two, ready to
      // subtract spans from.
      assert(top <= row_a->second->bottom);
      Rows::iterator new_row = rows_.insert(
          row_a, Rows::value_type(top, new Row(row_a->second->top, top)));
      row_a->second->top = top;
      new_row->second->spans = row_a->second->spans;
    } else if (top < row_a->second->top) {
      // If the |top| is above |row_a| then skip the range between |top| and
      // top of |row_a| because it's empty.
      top = row_a->second->top;
      if (top >= row_b->second->bottom) {
        ++row_b;
        if (row_b != region.rows_.end())
          top = row_b->second->top;
        continue;
      }
    }

    if (row_b->second->bottom < row_a->second->bottom) {
      // If the bottom of |row_b| falls in the middle of the |row_a| split
      // |row_a| into two, at |top|, and leave |row_a| referring to the upper of
      // the two, ready to subtract spans from.
      int bottom = row_b->second->bottom;
      Rows::iterator new_row =
          rows_.insert(row_a, Rows::value_type(bottom, new Row(top, bottom)));
      row_a->second->top = bottom;
      new_row->second->spans = row_a->second->spans;
      row_a = new_row;
    }

    // At this point the vertical range covered by |row_a| lays within the
    // range covered by |row_b|. Subtract |row_b| spans from |row_a|.
    RowSpanSet new_spans;
    SubtractRows(row_a->second->spans, row_b->second->spans, &new_spans);
    new_spans.swap(row_a->second->spans);
    top = row_a->second->bottom;

    if (top >= row_b->second->bottom) {
      ++row_b;
      if (row_b != region.rows_.end())
        top = row_b->second->top;
    }

    // Check if the row is empty after subtraction and delete it. Otherwise move
    // to the next one.
    if (row_a->second->spans.empty()) {
      Rows::iterator row_to_delete = row_a;
      ++row_a;
      delete row_to_delete->second;
      rows_.erase(row_to_delete);
    } else {
      MergeWithPrecedingRow(row_a);
      ++row_a;
    }
  }

  if (row_a != rows_.end())
    MergeWithPrecedingRow(row_a);
}

void DesktopRegion::Subtract(const DesktopRect& rect) {
  DesktopRegion region;
  region.AddRect(rect);
  Subtract(region);
}

void DesktopRegion::Translate(int32_t dx, int32_t dy) {
  Rows new_rows;

  for (Rows::iterator it = rows_.begin(); it != rows_.end(); ++it) {
    Row* row = it->second;

    row->top += dy;
    row->bottom += dy;

    if (dx != 0) {
      // Translate each span.
      for (RowSpanSet::iterator span = row->spans.begin();
           span != row->spans.end(); ++span) {
        span->left += dx;
        span->right += dx;
      }
    }

    if (dy != 0)
      new_rows.insert(new_rows.end(), Rows::value_type(row->bottom, row));
  }

  if (dy != 0)
    new_rows.swap(rows_);
}

void DesktopRegion::Swap(DesktopRegion* region) {
  rows_.swap(region->rows_);
}

// static
bool DesktopRegion::CompareSpanRight(const RowSpan& r, int32_t value) {
  return r.right < value;
}

// static
bool DesktopRegion::CompareSpanLeft(const RowSpan& r, int32_t value) {
  return r.left < value;
}

// static
void DesktopRegion::AddSpanToRow(Row* row, int left, int right) {
  // First check if the new span is located to the right of all existing spans.
  // This is an optimization to avoid binary search in the case when rectangles
  // are inserted sequentially from left to right.
  if (row->spans.empty() || left > row->spans.back().right) {
    row->spans.push_back(RowSpan(left, right));
    return;
  }

  // Find the first span that ends at or after |left|.
  RowSpanSet::iterator start = std::lower_bound(
      row->spans.begin(), row->spans.end(), left, CompareSpanRight);
  assert(start < row->spans.end());

  // Find the first span that starts after |right|.
  RowSpanSet::iterator end =
      std::lower_bound(start, row->spans.end(), right + 1, CompareSpanLeft);
  if (end == row->spans.begin()) {
    // There are no overlaps. Just insert the new span at the beginning.
    row->spans.insert(row->spans.begin(), RowSpan(left, right));
    return;
  }

  // Move end to the left, so that it points the last span that ends at or
  // before |right|.
  end--;

  // At this point [start, end] is the range of spans that intersect with the
  // new one.
  if (end < start) {
    // There are no overlaps. Just insert the new span at the correct position.
    row->spans.insert(start, RowSpan(left, right));
    return;
  }

  left = std::min(left, start->left);
  right = std::max(right, end->right);

  // Replace range [start, end] with the new span.
  *start = RowSpan(left, right);
  ++start;
  ++end;
  if (start < end)
    row->spans.erase(start, end);
}

// static
bool DesktopRegion::IsSpanInRow(const Row& row, const RowSpan& span) {
  // Find the first span that starts at or after |span.left| and then check if
  // it's the same span.
  RowSpanSet::const_iterator it = std::lower_bound(
      row.spans.begin(), row.spans.end(), span.left, CompareSpanLeft);
  return it != row.spans.end() && *it == span;
}

// static
void DesktopRegion::SubtractRows(const RowSpanSet& set_a,
                                 const RowSpanSet& set_b,
                                 RowSpanSet* output) {
  assert(!set_a.empty() && !set_b.empty());

  RowSpanSet::const_iterator it_b = set_b.begin();

  // Iterate over all spans in |set_a| adding parts of it that do not intersect
  // with |set_b| to the |output|.
  for (RowSpanSet::const_iterator it_a = set_a.begin(); it_a != set_a.end();
       ++it_a) {
    // If there is no intersection then append the current span and continue.
    if (it_b == set_b.end() || it_a->right < it_b->left) {
      output->push_back(*it_a);
      continue;
    }

    // Iterate over |set_b| spans that may intersect with |it_a|.
    int pos = it_a->left;
    while (it_b != set_b.end() && it_b->left < it_a->right) {
      if (it_b->left > pos)
        output->push_back(RowSpan(pos, it_b->left));
      if (it_b->right > pos) {
        pos = it_b->right;
        if (pos >= it_a->right)
          break;
      }
      ++it_b;
    }
    if (pos < it_a->right)
      output->push_back(RowSpan(pos, it_a->right));
  }
}

DesktopRegion::Iterator::Iterator(const DesktopRegion& region)
    : region_(region),
      row_(region.rows_.begin()),
      previous_row_(region.rows_.end()) {
  if (!IsAtEnd()) {
    assert(row_->second->spans.size() > 0);
    row_span_ = row_->second->spans.begin();
    UpdateCurrentRect();
  }
}

DesktopRegion::Iterator::~Iterator() {}

bool DesktopRegion::Iterator::IsAtEnd() const {
  return row_ == region_.rows_.end();
}

void DesktopRegion::Iterator::Advance() {
  assert(!IsAtEnd());

  while (true) {
    ++row_span_;
    if (row_span_ == row_->second->spans.end()) {
      previous_row_ = row_;
      ++row_;
      if (row_ != region_.rows_.end()) {
        assert(row_->second->spans.size() > 0);
        row_span_ = row_->second->spans.begin();
      }
    }

    if (IsAtEnd())
      return;

    // If the same span exists on the previous row then skip it, as we've
    // already returned this span merged into the previous one, via
    // UpdateCurrentRect().
    if (previous_row_ != region_.rows_.end() &&
        previous_row_->second->bottom == row_->second->top &&
        IsSpanInRow(*previous_row_->second, *row_span_)) {
      continue;
    }

    break;
  }

  assert(!IsAtEnd());
  UpdateCurrentRect();
}

void DesktopRegion::Iterator::UpdateCurrentRect() {
  // Merge the current rectangle with the matching spans from later rows.
  int bottom;
  Rows::const_iterator bottom_row = row_;
  Rows::const_iterator previous;
  do {
    bottom = bottom_row->second->bottom;
    previous = bottom_row;
    ++bottom_row;
  } while (bottom_row != region_.rows_.end() &&
           previous->second->bottom == bottom_row->second->top &&
           IsSpanInRow(*bottom_row->second, *row_span_));
  rect_ = DesktopRect::MakeLTRB(row_span_->left, row_->second->top,
                                row_span_->right, bottom);
}

}  // namespace webrtc
