(ns nubank-analyzer.reports-test
  (:require [clojure.test :refer [deftest is testing]]
            [nubank-analyzer.reports :as reports]
            [clojure.string :as str]))

(deftest test-format-currency
  (testing "Currency formatting"
    (is (= "R$ 100.00" (reports/format-currency 100.0)))
    (is (= "R$ 1,234.56" (reports/format-currency 1234.56)))
    (is (= "R$ -50.50" (reports/format-currency -50.50)))
    (is (= "R$ 0.00" (reports/format-currency 0.0)))))

(deftest test-format-percentage
  (testing "Percentage formatting"
    (is (= "50.0%" (reports/format-percentage 50.0)))
    (is (= "33.3%" (reports/format-percentage 33.33)))
    (is (= "100.0%" (reports/format-percentage 100.0)))
    (is (= "0.5%" (reports/format-percentage 0.5)))))

(deftest test-format-date-br
  (testing "Brazilian date formatting"
    (is (= "15/10/2025" (reports/format-date-br "2025-10-15")))
    (is (= "01/01/2026" (reports/format-date-br "2026-01-01")))
    (is (= "invalid" (reports/format-date-br "invalid")))))

(deftest test-print-separator
  (testing "Separator printing"
    (is (= "=====" (reports/print-separator 5 \=)))
    (is (= "---" (reports/print-separator 3 \-)))
    (is (= "" (reports/print-separator 0 \*)))))

(deftest test-print-section-header
  (testing "Section header formatting"
    (let [header (reports/print-section-header "TEST")]
      (is (string? header))
      (is (str/includes? header "TEST"))
      (is (str/includes? header "=")))))