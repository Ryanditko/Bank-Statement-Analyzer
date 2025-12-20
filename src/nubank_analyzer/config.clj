(ns nubank-analyzer.config
  "Centralized configuration module
   Manages category configurations, formats and preferences"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]))

;; ============================================================================
;; Default Configuration
;; ============================================================================

(def default-config
  {:app {:name "Nubank Analyzer"
         :version "2.0.0"
         :author "Bank Statement Analyzer"}

   :csv {:delimiter \,
         :quote \"
         :encoding "UTF-8"
         :skip-empty-lines true}

   :parser {:date-formats ["dd/MM/yyyy" "yyyy-MM-dd" "dd-MM-yyyy"]
            :currency-locale "pt-BR"
            :amount-precision 2}

   :categories {"Food" {:keywords ["restaurante" "lanchonete" "padaria" "ifood" "restaurant"
                                   "uber eats" "rappi" "mcdonalds" "burger king"
                                   "pizza" "açai" "acai" "cafe" "café" "bar" "pub"
                                   "subway" "kfc" "outback" "sushi" "japonês"]
                        :color "#FF6B6B"
                        :icon "food"}

                "Transport" {:keywords ["uber" "99" "taxi" "metrô" "metro" "onibus" "ônibus"
                                        "bus" "passagem" "combustivel" "combustível" "gasolina" "gas"
                                        "posto" "ipiranga" "shell" "br petrobras" "estacionamento"
                                        "pedágio" "pedagio" "parking" "toll"]
                             :color "#4ECDC4"
                             :icon "transport"}

                "Subscriptions" {:keywords ["spotify" "netflix" "amazon prime" "disney" "hbo"
                                            "youtube premium" "apple music" "deezer" "globoplay"
                                            "paramount" "crunchyroll" "prime video" "star+"
                                            "max" "telegram premium" "chatgpt" "github" "subscription"]
                                 :color "#95E1D3"
                                 :icon "subscription"}

                "Supermarket" {:keywords ["carrefour" "pão de açucar" "pao de acucar" "extra"
                                          "walmart" "mercado" "supermercado" "atacadão" "atacadao"
                                          "zaffari" "dia%" "sam's club" "assai" "big box"
                                          "nacional" "bompreco" "market" "grocery"]
                               :color "#F38181"
                               :icon "supermarket"}

                "Health" {:keywords ["drogaria" "farmacia" "farmácia" "clinica" "clínica" "pharmacy"
                                     "hospital" "laboratorio" "laboratório" "consulta" "health"
                                     "drogasil" "pacheco" "ultrafarma" "pague menos"
                                     "droga raia" "medico" "médico" "dentist" "dentista" "exame" "doctor"]
                          :color "#AA96DA"
                          :icon "health"}

                "Education" {:keywords ["curso" "livro" "livraria" "udemy" "coursera" "course"
                                        "faculdade" "escola" "universidade" "material escolar" "education"
                                        "alura" "pluralsight" "linkedin learning" "domestika" "school" "university"]
                             :color "#FCBAD3"
                             :icon "education"}

                "Entertainment" {:keywords ["cinema" "teatro" "show" "ingresso" "parque" "entertainment"
                                            "viagem" "hotel" "airbnb" "booking" "decolar" "travel"
                                            "cinemark" "uci" "kinoplex" "evento" "event" "movie"]
                                 :color "#FFFFD2"
                                 :icon "entertainment"}

                "Online Shopping" {:keywords ["amazon" "mercado livre" "americanas" "magazine luiza"
                                              "shopee" "aliexpress" "shein" "kabum" "pichau"
                                              "submarino" "casas bahia" "ponto frio" "shopping"]
                                   :color "#A8D8EA"
                                   :icon "shopping"}

                "Utilities" {:keywords ["internet" "telefone" "celular" "luz" "energia" "phone"
                                        "água" "agua" "condominio" "condomínio" "aluguel" "water"
                                        "vivo" "claro" "tim" "oi" "copel" "cemig" "eletropaulo" "utilities"]
                             :color "#FFD93D"
                             :icon "utilities"}

                "Investments" {:keywords ["corretora" "btg" "xp" "clear" "rico" "investment"
                                          "nuinvest" "easynvest" "inter invest"
                                          "tesouro" "cdb" "fundo" "ação" "stock" "fund"]
                               :color "#6BCB77"
                               :icon "investment"}

                "Transfers" {:keywords ["pix" "transferencia" "transferência" "ted" "doc"
                                        "envio" "pagamento" "qr code" "transfer" "payment"]
                             :color "#C7CEEA"
                             :icon "transfer"}

                "Pet" {:keywords ["pet" "veterinari" "ração" "racao" "petz" "cobasi"
                                  "petshop" "pet shop" "animal" "veterinary"]
                       :color "#FFB6B9"
                       :icon "pet"}

                "Home" {:keywords ["mobilia" "móvel" "decoração" "decoracao" "leroy" "furniture"
                                   "tok stok" "etna" "home center" "construção" "construcao" "home"]
                        :color "#FFDAB9"
                        :icon "home"}

                "Clothing" {:keywords ["roupa" "calça" "camisa" "sapato" "tênis" "tenis" "clothes"
                                       "zara" "renner" "c&a" "riachuelo" "nike" "adidas"
                                       "fashion" "moda" "clothing"]
                            :color "#E4C1F9"
                            :icon "clothing"}}

   :report {:formats [:txt :json :edn :csv :html]
            :default-format :txt
            :include-charts false
            :locale "pt-BR"}

   :analysis {:detect-duplicates true
              :duplicate-threshold-hours 24
              :min-transaction-amount 0.01
              :outlier-threshold 3.0  ; standard deviations
              :trend-period-months 3}

   :logging {:level :info  ; :debug :info :warn :error
             :output :console  ; :console :file :both
             :file "logs/nubank-analyzer.log"
             :format :pretty}})

;; ============================================================================
;; Funções de Configuração
;; ============================================================================

(defn load-config
  "Carrega configuração de arquivo EDN, faz merge com padrões"
  [file-path]
  (try
    (if (.exists (io/file file-path))
      (let [user-config (-> file-path slurp edn/read-string)]
        (merge-with merge default-config user-config))
      default-config)
    (catch Exception e
      (println "⚠️  Erro ao carregar config, usando padrões:" (.getMessage e))
      default-config)))

(defn save-config
  "Salva configuração em arquivo EDN"
  [config file-path]
  (try
    (io/make-parents file-path)
    (spit file-path (with-out-str (pprint/pprint config)))
    true
    (catch Exception e
      (println "❌ Erro ao salvar config:" (.getMessage e))
      false)))

(defn get-category-config
  "Retorna configuração de uma categoria específica"
  [config category-name]
  (get-in config [:categories category-name]))

(defn get-all-categories
  "Retorna lista de todas as categorias configuradas"
  [config]
  (keys (:categories config)))

(defn validate-config
  "Valida estrutura da configuração"
  [config]
  (and (map? config)
       (contains? config :app)
       (contains? config :categories)
       (every? map? (vals (:categories config)))))

(defn get-app-version
  "Retorna versão da aplicação"
  [config]
  (get-in config [:app :version]))

(defn generate-default-config-file
  "Gera arquivo de configuração padrão para o usuário customizar"
  [output-path]
  (save-config default-config output-path))

(comment
  ;; Exemplos de uso

  ;; Carregar config
  (def cfg (load-config "config.edn"))

  ;; Salvar config customizada
  (save-config (assoc-in default-config [:app :name] "Meu Analyzer") "my-config.edn")

  ;; Obter categorias
  (get-all-categories default-config)

  ;; Config de categoria
  (get-category-config default-config "Alimentação"))
