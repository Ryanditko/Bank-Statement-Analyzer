(ns nubank-analyzer.reports-test
  (:require [clojure.test :refer [deftest is testing]]
            [nubank-analyzer.reports :as reports]
            [clojure.string :as str]))

(deftest test-format-currency
  (testing "Currency formatting"
    (is (= "R$ 100.00" (reports/format-currency 100.0)))
    (is (= "R$ 1,234.56" (reports/format-currency 1234.56)))
    (is (= "R$ 0.00" (reports/format-currency 0.0)))
    (is (= "R$ -50.25" (reports/format-currency -50.25)))))

(deftest test-format-percentage
  (testing "Percentage formatting"
    (is (= "50.0%" (reports/format-percentage 50.0)))
    (is (= "33.3%" (reports/format-percentage 33.33)))
    (is (= "100.0%" (reports/format-percentage 100.0)))
    (is (= "0.0%" (reports/format-percentage 0.0)))))

(deftest test-format-date-br
  (testing "Brazilian date formatting"
    (is (= "15/10/2025" (reports/format-date-br "2025-10-15")))
    (is (= "01/01/2024" (reports/format-date-br "2024-01-01")))
    (is (= "31/12/2023" (reports/format-date-br "2023-12-31")))
    (is (= "invalid" (reports/format-date-br "invalid")))))

(deftest test-print-separator
  (testing "Separator printing"
    (is (= "=====" (reports/print-separator 5 "=")))
    (is (= "----------" (reports/print-separator 10 "-")))
    (is (= "" (reports/print-separator 0 "=")))))

(deftest test-print-section-header
  (testing "Section header formatting"
    (let [header (reports/print-section-header "Test Section")]
      (is (string? header))
      (is (str/includes? header "Test Section"))
      (is (str/includes? header "=")))))