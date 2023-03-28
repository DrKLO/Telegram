/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/test/metrics/metrics_logger_and_exporter.h"

#include <map>
#include <memory>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/numerics/samples_stats_counter.h"
#include "api/test/metrics/metric.h"
#include "api/test/metrics/metrics_exporter.h"
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

TEST(MetricsLoggerAndExporterTest, LogSingleValueMetricRecordsMetric) {
  TestMetricsExporterFactory exporter_factory;
  {
    std::vector<std::unique_ptr<MetricsExporter>> exporters;
    exporters.push_back(exporter_factory.CreateExporter());
    MetricsLoggerAndExporter logger(Clock::GetRealTimeClock(),
                                    std::move(exporters));
    logger.LogSingleValueMetric(
        "metric_name", "test_case_name",
        /*value=*/10, Unit::kMilliseconds,
        ImprovementDirection::kBiggerIsBetter,
        std::map<std::string, std::string>{{"key", "value"}});
  }

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

TEST(MetricsLoggerAndExporterTest,
     LogMetricWithSamplesStatsCounterRecordsMetric) {
  TestMetricsExporterFactory exporter_factory;
  {
    std::vector<std::unique_ptr<MetricsExporter>> exporters;
    exporters.push_back(exporter_factory.CreateExporter());
    MetricsLoggerAndExporter logger(Clock::GetRealTimeClock(),
                                    std::move(exporters));

    SamplesStatsCounter values;
    values.AddSample(SamplesStatsCounter::StatsSample{
        .value = 10,
        .time = Clock::GetRealTimeClock()->CurrentTime(),
        .metadata =
            std::map<std::string, std::string>{{"point_key1", "value1"}}});
    values.AddSample(SamplesStatsCounter::StatsSample{
        .value = 20,
        .time = Clock::GetRealTimeClock()->CurrentTime(),
        .metadata =
            std::map<std::string, std::string>{{"point_key2", "value2"}}});
    logger.LogMetric("metric_name", "test_case_name", values,
                     Unit::kMilliseconds, ImprovementDirection::kBiggerIsBetter,
                     std::map<std::string, std::string>{{"key", "value"}});
  }

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
  ASSERT_THAT(metric.time_series.samples.size(), Eq(2lu));
  EXPECT_THAT(metric.time_series.samples[0].value, Eq(10.0));
  EXPECT_THAT(metric.time_series.samples[0].sample_metadata,
              Eq(std::map<std::string, std::string>{{"point_key1", "value1"}}));
  EXPECT_THAT(metric.time_series.samples[1].value, Eq(20.0));
  EXPECT_THAT(metric.time_series.samples[1].sample_metadata,
              Eq(std::map<std::string, std::string>{{"point_key2", "value2"}}));
  ASSERT_THAT(metric.stats.mean, absl::optional<double>(15.0));
  ASSERT_THAT(metric.stats.stddev, absl::optional<double>(5.0));
  ASSERT_THAT(metric.stats.min, absl::optional<double>(10.0));
  ASSERT_THAT(metric.stats.max, absl::optional<double>(20.0));
}

TEST(MetricsLoggerAndExporterTest,
     LogMetricWithEmptySamplesStatsCounterRecordsEmptyMetric) {
  TestMetricsExporterFactory exporter_factory;
  {
    std::vector<std::unique_ptr<MetricsExporter>> exporters;
    exporters.push_back(exporter_factory.CreateExporter());
    MetricsLoggerAndExporter logger(Clock::GetRealTimeClock(),
                                    std::move(exporters));
    SamplesStatsCounter values;
    logger.LogMetric("metric_name", "test_case_name", values, Unit::kUnitless,
                     ImprovementDirection::kBiggerIsBetter, DefaultMetadata());
  }

  std::vector<Metric> metrics = exporter_factory.exported_metrics;
  ASSERT_THAT(metrics.size(), Eq(1lu));
  EXPECT_THAT(metrics[0].name, Eq("metric_name"));
  EXPECT_THAT(metrics[0].test_case, Eq("test_case_name"));
  EXPECT_THAT(metrics[0].time_series.samples, IsEmpty());
  ASSERT_THAT(metrics[0].stats.mean, Eq(absl::nullopt));
  ASSERT_THAT(metrics[0].stats.stddev, Eq(absl::nullopt));
  ASSERT_THAT(metrics[0].stats.min, Eq(absl::nullopt));
  ASSERT_THAT(metrics[0].stats.max, Eq(absl::nullopt));
}

TEST(MetricsLoggerAndExporterTest, LogMetricWithStatsRecordsMetric) {
  TestMetricsExporterFactory exporter_factory;
  {
    std::vector<std::unique_ptr<MetricsExporter>> exporters;
    exporters.push_back(exporter_factory.CreateExporter());
    MetricsLoggerAndExporter logger(Clock::GetRealTimeClock(),
                                    std::move(exporters));
    Metric::Stats metric_stats{.mean = 15, .stddev = 5, .min = 10, .max = 20};
    logger.LogMetric("metric_name", "test_case_name", metric_stats,
                     Unit::kMilliseconds, ImprovementDirection::kBiggerIsBetter,
                     std::map<std::string, std::string>{{"key", "value"}});
  }

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
  ASSERT_THAT(metric.time_series.samples.size(), Eq(0lu));
  ASSERT_THAT(metric.stats.mean, absl::optional<double>(15.0));
  ASSERT_THAT(metric.stats.stddev, absl::optional<double>(5.0));
  ASSERT_THAT(metric.stats.min, absl::optional<double>(10.0));
  ASSERT_THAT(metric.stats.max, absl::optional<double>(20.0));
}

TEST(MetricsLoggerAndExporterTest, LogSingleValueMetricRecordsMultipleMetrics) {
  TestMetricsExporterFactory exporter_factory;
  {
    std::vector<std::unique_ptr<MetricsExporter>> exporters;
    exporters.push_back(exporter_factory.CreateExporter());
    MetricsLoggerAndExporter logger(Clock::GetRealTimeClock(),
                                    std::move(exporters));

    logger.LogSingleValueMetric("metric_name1", "test_case_name1",
                                /*value=*/10, Unit::kMilliseconds,
                                ImprovementDirection::kBiggerIsBetter,
                                DefaultMetadata());
    logger.LogSingleValueMetric("metric_name2", "test_case_name2",
                                /*value=*/10, Unit::kMilliseconds,
                                ImprovementDirection::kBiggerIsBetter,
                                DefaultMetadata());
  }

  std::vector<Metric> metrics = exporter_factory.exported_metrics;
  ASSERT_THAT(metrics.size(), Eq(2lu));
  EXPECT_THAT(metrics[0].name, Eq("metric_name1"));
  EXPECT_THAT(metrics[0].test_case, Eq("test_case_name1"));
  EXPECT_THAT(metrics[1].name, Eq("metric_name2"));
  EXPECT_THAT(metrics[1].test_case, Eq("test_case_name2"));
}

TEST(MetricsLoggerAndExporterTest,
     LogMetricWithSamplesStatsCounterRecordsMultipleMetrics) {
  TestMetricsExporterFactory exporter_factory;
  {
    std::vector<std::unique_ptr<MetricsExporter>> exporters;
    exporters.push_back(exporter_factory.CreateExporter());
    MetricsLoggerAndExporter logger(Clock::GetRealTimeClock(),
                                    std::move(exporters));
    SamplesStatsCounter values;
    values.AddSample(SamplesStatsCounter::StatsSample{
        .value = 10,
        .time = Clock::GetRealTimeClock()->CurrentTime(),
        .metadata = DefaultMetadata()});
    values.AddSample(SamplesStatsCounter::StatsSample{
        .value = 20,
        .time = Clock::GetRealTimeClock()->CurrentTime(),
        .metadata = DefaultMetadata()});

    logger.LogMetric("metric_name1", "test_case_name1", values,
                     Unit::kMilliseconds, ImprovementDirection::kBiggerIsBetter,
                     DefaultMetadata());
    logger.LogMetric("metric_name2", "test_case_name2", values,
                     Unit::kMilliseconds, ImprovementDirection::kBiggerIsBetter,
                     DefaultMetadata());
  }

  std::vector<Metric> metrics = exporter_factory.exported_metrics;
  ASSERT_THAT(metrics.size(), Eq(2lu));
  EXPECT_THAT(metrics[0].name, Eq("metric_name1"));
  EXPECT_THAT(metrics[0].test_case, Eq("test_case_name1"));
  EXPECT_THAT(metrics[1].name, Eq("metric_name2"));
  EXPECT_THAT(metrics[1].test_case, Eq("test_case_name2"));
}

TEST(MetricsLoggerAndExporterTest, LogMetricWithStatsRecordsMultipleMetrics) {
  TestMetricsExporterFactory exporter_factory;
  {
    std::vector<std::unique_ptr<MetricsExporter>> exporters;
    exporters.push_back(exporter_factory.CreateExporter());
    MetricsLoggerAndExporter logger(Clock::GetRealTimeClock(),
                                    std::move(exporters));
    Metric::Stats metric_stats{.mean = 15, .stddev = 5, .min = 10, .max = 20};

    logger.LogMetric("metric_name1", "test_case_name1", metric_stats,
                     Unit::kMilliseconds, ImprovementDirection::kBiggerIsBetter,
                     DefaultMetadata());
    logger.LogMetric("metric_name2", "test_case_name2", metric_stats,
                     Unit::kMilliseconds, ImprovementDirection::kBiggerIsBetter,
                     DefaultMetadata());
  }

  std::vector<Metric> metrics = exporter_factory.exported_metrics;
  ASSERT_THAT(metrics.size(), Eq(2lu));
  EXPECT_THAT(metrics[0].name, Eq("metric_name1"));
  EXPECT_THAT(metrics[0].test_case, Eq("test_case_name1"));
  EXPECT_THAT(metrics[1].name, Eq("metric_name2"));
  EXPECT_THAT(metrics[1].test_case, Eq("test_case_name2"));
}

TEST(MetricsLoggerAndExporterTest,
     LogMetricThroughtAllMethodsAccumulateAllMetrics) {
  TestMetricsExporterFactory exporter_factory;
  {
    std::vector<std::unique_ptr<MetricsExporter>> exporters;
    exporters.push_back(exporter_factory.CreateExporter());
    MetricsLoggerAndExporter logger(Clock::GetRealTimeClock(),
                                    std::move(exporters));
    SamplesStatsCounter values;
    values.AddSample(SamplesStatsCounter::StatsSample{
        .value = 10,
        .time = Clock::GetRealTimeClock()->CurrentTime(),
        .metadata = DefaultMetadata()});
    values.AddSample(SamplesStatsCounter::StatsSample{
        .value = 20,
        .time = Clock::GetRealTimeClock()->CurrentTime(),
        .metadata = DefaultMetadata()});
    Metric::Stats metric_stats{.mean = 15, .stddev = 5, .min = 10, .max = 20};

    logger.LogSingleValueMetric("metric_name1", "test_case_name1",
                                /*value=*/10, Unit::kMilliseconds,
                                ImprovementDirection::kBiggerIsBetter,
                                DefaultMetadata());
    logger.LogMetric("metric_name2", "test_case_name2", values,
                     Unit::kMilliseconds, ImprovementDirection::kBiggerIsBetter,
                     DefaultMetadata());
    logger.LogMetric("metric_name3", "test_case_name3", metric_stats,
                     Unit::kMilliseconds, ImprovementDirection::kBiggerIsBetter,
                     DefaultMetadata());
  }

  std::vector<Metric> metrics = exporter_factory.exported_metrics;
  ASSERT_THAT(metrics.size(), Eq(3lu));
  EXPECT_THAT(metrics[0].name, Eq("metric_name1"));
  EXPECT_THAT(metrics[0].test_case, Eq("test_case_name1"));
  EXPECT_THAT(metrics[1].name, Eq("metric_name2"));
  EXPECT_THAT(metrics[1].test_case, Eq("test_case_name2"));
  EXPECT_THAT(metrics[2].name, Eq("metric_name3"));
  EXPECT_THAT(metrics[2].test_case, Eq("test_case_name3"));
}

TEST(MetricsLoggerAndExporterTest,
     OneFailedExporterDoesNotPreventExportToOthers) {
  TestMetricsExporterFactory exporter_factory1;
  TestMetricsExporterFactory exporter_factory2;
  TestMetricsExporterFactory exporter_factory3;
  {
    std::vector<std::unique_ptr<MetricsExporter>> exporters;
    exporters.push_back(exporter_factory1.CreateExporter());
    exporters.push_back(exporter_factory2.CreateFailureExporter());
    exporters.push_back(exporter_factory3.CreateExporter());
    MetricsLoggerAndExporter logger(Clock::GetRealTimeClock(),
                                    std::move(exporters),
                                    /*crash_on_export_failure=*/false);

    logger.LogSingleValueMetric("metric_name", "test_case_name",
                                /*value=*/10, Unit::kMilliseconds,
                                ImprovementDirection::kBiggerIsBetter,
                                DefaultMetadata());
  }

  std::vector<Metric> metrics1 = exporter_factory1.exported_metrics;
  std::vector<Metric> metrics2 = exporter_factory2.exported_metrics;
  std::vector<Metric> metrics3 = exporter_factory3.exported_metrics;
  ASSERT_THAT(metrics1.size(), Eq(1lu));
  EXPECT_THAT(metrics1[0].name, Eq("metric_name"));
  ASSERT_THAT(metrics2.size(), Eq(1lu));
  EXPECT_THAT(metrics2[0].name, Eq("metric_name"));
  ASSERT_THAT(metrics3.size(), Eq(1lu));
  EXPECT_THAT(metrics3[0].name, Eq("metric_name"));
}

}  // namespace
}  // namespace test
}  // namespace webrtc
