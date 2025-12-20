(ns nubank-analyzer.analyzer-test
  (:require [clojure.test :refer [deftest is testing]]
            [nubank-analyzer.analyzer :as analyzer]
            [nubank-analyzer.config :as config]))

(def sample-transactions
  [{:date "2025-10-15" :description "IFOOD" :amount -45.50}
   {:date "2025-10-14" :description "Posto Ipiranga" :amount -250.00}
   {:date "2025-10-13" :description "Netflix" :amount -44.90}
   {:date "2025-10-12" :description "Uber" :amount -28.30}
   {:date "2025-09-15" :description "IFOOD" :amount -52.30}])

(deftest test-categorize-transaction
  (testing "Transaction categorization"
    (let [categories (:categories config/default-config)]
      (is (= "Food"
             (analyzer/categorize-transaction
              {:description "IFOOD *IFOOD"}
              categories)))
      (is (= "Transport"
             (analyzer/categorize-transaction
              {:description "Uber *UBER"}
              categories)))
      (is (= "Subscriptions"
             (analyzer/categorize-transaction
              {:description "Netflix Servicos"}
              categories)))
      (is (= "Others"
             (analyzer/categorize-transaction
              {:description "Unknown Establishment"}
              categories))))))

(deftest test-calculate-basic-stats
  (testing "Basic statistics calculation"
    (let [values [10 20 30 40 50]
          stats (analyzer/calculate-basic-stats values)]
      (is (= 5 (:count stats)))
      (is (= 150 (:total stats)))
      (is (= 30.0 (:mean stats)))
      (is (= 30 (:median stats)))
      (is (= 10 (:min stats)))
      (is (= 50 (:max stats))))))

(deftest test-extract-month-year
  (testing "Month/year extraction"
    (is (= "10/2025" (analyzer/extract-month-year "2025-10-15")))
    (is (= "09/2025" (analyzer/extract-month-year "2025-09-01")))
    (is (= "Unknown" (analyzer/extract-month-year nil)))))

(deftest test-find-duplicates
  (testing "Duplicate detection"
    (let [txs [{:date "2025-10-15" :description "IFOOD" :amount -45.50}
               {:date "2025-10-15" :description "IFOOD" :amount -45.50}
               {:date "2025-10-14" :description "Uber" :amount -28.30}]
          dups (analyzer/find-duplicates txs 24)]
      (is (= 1 (count dups)))
      (is (= 2 (count (first dups)))))))
