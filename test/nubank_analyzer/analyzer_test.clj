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
  (testing "Categorização de transações"
    (let [categories (:categories config/default-config)]
      (is (= "Alimentação" 
             (analyzer/categorize-transaction 
               {:description "IFOOD *IFOOD"} 
               categories)))
      (is (= "Transporte" 
             (analyzer/categorize-transaction 
               {:description "Uber *UBER"} 
               categories)))
      (is (= "Assinaturas" 
             (analyzer/categorize-transaction 
               {:description "Netflix Servicos"} 
               categories)))
      (is (= "Outros" 
             (analyzer/categorize-transaction 
               {:description "Estabelecimento Desconhecido"} 
               categories))))))

(deftest test-calculate-basic-stats
  (testing "Cálculo de estatísticas básicas"
    (let [values [10 20 30 40 50]
          stats (analyzer/calculate-basic-stats values)]
      (is (= 5 (:count stats)))
      (is (= 150 (:total stats)))
      (is (= 30.0 (:mean stats)))
      (is (= 30 (:median stats)))
      (is (= 10 (:min stats)))
      (is (= 50 (:max stats))))))

(deftest test-extract-month-year
  (testing "Extração de mês/ano"
    (is (= "10/2025" (analyzer/extract-month-year "2025-10-15")))
    (is (= "09/2025" (analyzer/extract-month-year "2025-09-01")))
    (is (= "Desconhecido" (analyzer/extract-month-year nil)))))

(deftest test-detect-duplicates
  (testing "Detecção de duplicatas"
    (let [txs [{:date "2025-10-15" :description "IFOOD" :amount -45.50}
               {:date "2025-10-15" :description "IFOOD" :amount -45.50}
               {:date "2025-10-14" :description "Uber" :amount -28.30}]
          dups (analyzer/detect-duplicates txs)]
      (is (= 1 (count dups)))
      (is (= 2 (count (first dups)))))))
