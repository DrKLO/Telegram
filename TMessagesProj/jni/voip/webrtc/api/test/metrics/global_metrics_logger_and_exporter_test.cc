/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/metrics/global_metrics_logger_and_exporter.h"

#include <map>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/test/metrics/metric.h"
#include "api/test/metrics/metrics_exporter.h"
#include "api/test/metrics/metrics_logger.h"
#include "system_wrappers/include/clock.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {
namespace test {
namespace {

using ::testing::Eq;
using ::testing::IsEmpty;

std::map<std::string, std::string> DefaultMetadata() {
  return std::map<std::string, std::string>{{"key", "value"}};
}

struct TestMetricsExporterFactory {
 public:
  std::unique_ptr<MetricsExporter> CreateExporter() {
    return std::make_unique<TestMetricsExporter>(this, /*export_result=*/true);
  }

  std::unique_ptr<MetricsExporter> CreateFailureExporter() {
    return std::make_unique<TestMetricsExporter>(this, /*export_result=*/false);
  }

  std::vector<Metric> exported_metrics;

 private:
  class TestMetricsExporter : public MetricsExporter {
   public:
    TestMetricsExporter(TestMetricsExporterFactory* factory, bool export_result)
        : factory_(factory), export_result_(export_result) {}
    ~TestMetricsExporter() override = default;

    bool Export(rtc::ArrayView<const Metric> metrics) override {
      factory_->exported_metrics =
          std::vector<Metric>(metrics.begin(), metrics.end());
      return export_result_;
    }

    TestMetricsExporterFactory* factory_;
    bool export_result_;
  };
};

TEST(ExportPerfMetricTest, CollectedMetricsAreExporter) {
  TestMetricsExporterFactory exporter_factory;

  DefaultMetricsLogger logger(Clock::GetRealTimeClock());
  logger.LogSingleValueMetric(
      "metric_name", "test_case_name",
      /*value=*/10, Unit::kMilliseconds, ImprovementDirection::kBiggerIsBetter,
      std::map<std::string, std::string>{{"key", "value"}});

  std::vector<std::unique_ptr<MetricsExporter>> exporters;
  exporters.push_back(exporter_factory.CreateExporter());
  ASSERT_TRUE(ExportPerfMetric(logger, std::move(exporters)));

  std::vector<Metric> metrics = exporter_factory.exported_metrics;
  ASSERT_THAT(metrics.size(), Eq(1lu));
  const Metric& metric = metrics[0];
  EXPECT_THAT(metric.name, Eq("metric_name"));
  EXPECT_THAT(metric.test_case, Eq("test_case_name"));
  EXPECT_THAT(metric.unit, Eq(Unit::kMilliseconds));
  EXPECT_THAT(metric.improvement_direction,
              Eq(ImprovementDirection::kBiggerIsBetter));
  EXPECT_THAT(metric.metric_metadata,
              Eq(std::map<std::string, std::string>{{"key", "value"}}));
  ASSERT_THAT(metric.time_series.samples.size(), Eq(1lu));
  EXPECT_THAT(metric.time_series.samples[0].value, Eq(10.0));
  EXPECT_THAT(metric.time_series.samples[0].sample_metadata,
              Eq(std::map<std::string, std::string>{}));
  ASSERT_THAT(metric.stats.mean, absl::optional<double>(10.0));
  ASSERT_THAT(metric.stats.stddev, absl::nullopt);
  ASSERT_THAT(metric.stats.min, absl::optional<double>(10.0));
  ASSERT_THAT(metric.stats.max, absl::optional<double>(10.0));
}

TEST(ExportPerfMetricTest, OneFailedExporterDoesNotPreventExportToOthers) {
  TestMetricsExporterFactory exporter_factory1;
  TestMetricsExporterFactory exporter_factory2;
  TestMetricsExporterFactory exporter_factory3;

  DefaultMetricsLogger logger(Clock::GetRealTimeClock());
  logger.LogSingleValueMetric("metric_name", "test_case_name",
                              /*value=*/10, Unit::kMilliseconds,
                              ImprovementDirection::kBiggerIsBetter,
                              DefaultMetadata());

  std::vector<std::unique_ptr<MetricsExporter>> exporters;
  exporters.push_back(exporter_factory1.CreateExporter());
  exporters.push_back(exporter_factory2.CreateFailureExporter());
  exporters.push_back(exporter_factory3.CreateExporter());
  ASSERT_FALSE(ExportPerfMetric(logger, std::move(exporters)));

  std::vector<Metric> metrics1 = exporter_factory1.exported_metrics;
  std::vector<Metric> metrics2 = exporter_factory2.exported_metrics;
  std::vector<Metric> metrics3 = exporter_factory3.exported_metrics;
  ASSERT_THAT(metrics1.size(), Eq(1lu))
      << metrics1[0].name << "; " << metrics1[1].name;
  EXPECT_THAT(metrics1[0].name, Eq("metric_name"));
  ASSERT_THAT(metrics2.size(), Eq(1lu));
  EXPECT_THAT(metrics2[0].name, Eq("metric_name"));
  ASSERT_THAT(metrics3.size(), Eq(1lu));
  EXPECT_THAT(metrics3[0].name, Eq("metric_name"));
}

}  // namespace
}  // namespace test
}  // namespace webrtc
