(ns nubank-analyzer.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [nubank-analyzer.config :as config]))

(deftest test-default-config
  (testing "Default configuration structure"
    (is (map? config/default-config))
    (is (contains? config/default-config :app))
    (is (contains? config/default-config :categories))
    (is (contains? config/default-config :parser))
    (is (contains? config/default-config :csv))))

(deftest test-app-metadata
  (testing "Application metadata"
    (let [app (:app config/default-config)]
      (is (= "Nubank Analyzer" (:name app)))
      (is (= "1.0.0" (:version app)))
      (is (string? (:author app))))))

(deftest test-categories-config
  (testing "Categories configuration"
    (let [categories (:categories config/default-config)]
      (is (map? categories))
      (is (contains? categories "Food"))
      (is (contains? categories "Transport"))
      (is (contains? categories "Subscriptions"))

      (let [food-category (get categories "Food")]
        (is (vector? (:keywords food-category)))
        (is (seq (:keywords food-category)))
        (is (string? (:color food-category)))))))

(deftest test-parser-config
  (testing "Parser configuration"
    (let [parser (:parser config/default-config)]
      (is (vector? (:date-formats parser)))
      (is (seq (:date-formats parser)))
      (is (string? (:currency-locale parser)))
      (is (number? (:amount-precision parser))))))

(deftest test-analysis-config
  (testing "Analysis configuration"
    (let [analysis (:analysis config/default-config)]
      (is (boolean? (:detect-duplicates analysis)))
      (is (number? (:duplicate-threshold-hours analysis)))
      (is (number? (:min-transaction-amount analysis)))
      (is (number? (:outlier-threshold analysis))))))

(deftest test-csv-config
  (testing "CSV configuration"
    (let [csv (:csv config/default-config)]
      (is (char? (:delimiter csv))) 
      (is (char? (:quote csv)))
      (is (string? (:encoding csv))))))
