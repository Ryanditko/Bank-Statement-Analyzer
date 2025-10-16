(ns nubank-analyzer.config
  "Módulo de configuração centralizada
   Gerencia configurações de categorias, formatos e preferências"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

;; ============================================================================
;; Configuração Padrão
;; ============================================================================

(def default-config
  {:app {:name "Nubank Analyzer"
         :version "2.0.0"
         :author "Professional Edition"}
   
   :csv {:delimiter \,
         :quote \"
         :encoding "UTF-8"
         :skip-empty-lines true}
   
   :parser {:date-formats ["dd/MM/yyyy" "yyyy-MM-dd" "dd-MM-yyyy"]
            :currency-locale "pt-BR"
            :amount-precision 2}
   
   :categories {"Alimentação" {:keywords ["restaurante" "lanchonete" "padaria" "ifood" 
                                          "uber eats" "rappi" "mcdonalds" "burger king" 
                                          "pizza" "açai" "acai" "cafe" "café" "bar" "pub"
                                          "subway" "kfc" "outback" "sushi" "japonês"]
                               :color "#FF6B6B"
                               :icon "🍔"}
                
                "Transporte" {:keywords ["uber" "99" "taxi" "metrô" "metro" "onibus" "ônibus"
                                        "bus" "passagem" "combustivel" "combustível" "gasolina"
                                        "posto" "ipiranga" "shell" "br petrobras" "estacionamento"
                                        "pedágio" "pedagio"]
                              :color "#4ECDC4"
                              :icon "🚗"}
                
                "Assinaturas" {:keywords ["spotify" "netflix" "amazon prime" "disney" "hbo"
                                         "youtube premium" "apple music" "deezer" "globoplay"
                                         "paramount" "crunchyroll" "prime video" "star+"
                                         "max" "telegram premium" "chatgpt" "github"]
                               :color "#95E1D3"
                               :icon "📺"}
                
                "Supermercado" {:keywords ["carrefour" "pão de açucar" "pao de acucar" "extra"
                                          "walmart" "mercado" "supermercado" "atacadão" "atacadao"
                                          "zaffari" "dia%" "sam's club" "assai" "big box"
                                          "nacional" "bompreco"]
                                :color "#F38181"
                                :icon "🛒"}
                
                "Saúde" {:keywords ["drogaria" "farmacia" "farmácia" "clinica" "clínica"
                                   "hospital" "laboratorio" "laboratório" "consulta"
                                   "drogasil" "pacheco" "ultrafarma" "pague menos"
                                   "droga raia" "medico" "médico" "dentista" "exame"]
                         :color "#AA96DA"
                         :icon "💊"}
                
                "Educação" {:keywords ["curso" "livro" "livraria" "udemy" "coursera"
                                      "faculdade" "escola" "universidade" "material escolar"
                                      "alura" "pluralsight" "linkedin learning" "domestika"]
                            :color "#FCBAD3"
                            :icon "📚"}
                
                "Lazer" {:keywords ["cinema" "teatro" "show" "ingresso" "parque"
                                   "viagem" "hotel" "airbnb" "booking" "decolar"
                                   "cinemark" "uci" "kinoplex" "evento"]
                         :color "#FFFFD2"
                         :icon "🎬"}
                
                "Compras Online" {:keywords ["amazon" "mercado livre" "americanas" "magazine luiza"
                                            "shopee" "aliexpress" "shein" "kabum" "pichau"
                                            "submarino" "casas bahia" "ponto frio"]
                                  :color "#A8D8EA"
                                  :icon "🛍️"}
                
                "Serviços" {:keywords ["internet" "telefone" "celular" "luz" "energia"
                                      "água" "agua" "condominio" "condomínio" "aluguel"
                                      "vivo" "claro" "tim" "oi" "copel" "cemig" "eletropaulo"]
                            :color "#FFD93D"
                            :icon "🔧"}
                
                "Investimentos" {:keywords ["corretora" "btg" "xp" "clear" "rico"
                                           "nuinvest" "easynvest" "inter invest"
                                           "tesouro" "cdb" "fundo" "ação"]
                                 :color "#6BCB77"
                                 :icon "📈"}
                
                "Transferências" {:keywords ["pix" "transferencia" "transferência" "ted" "doc"
                                            "envio" "pagamento" "qr code"]
                                  :color "#C7CEEA"
                                  :icon "💸"}
                
                "Pet" {:keywords ["pet" "veterinari" "ração" "racao" "petz" "cobasi"
                                 "petshop" "pet shop" "animal"]
                       :color "#FFB6B9"
                       :icon "🐾"}
                
                "Casa" {:keywords ["mobilia" "móvel" "decoração" "decoracao" "leroy"
                                  "tok stok" "etna" "home center" "construção" "construcao"]
                        :color "#FFDAB9"
                        :icon "🏠"}
                
                "Vestuário" {:keywords ["roupa" "calça" "camisa" "sapato" "tênis" "tenis"
                                       "zara" "renner" "c&a" "riachuelo" "nike" "adidas"
                                       "fashion" "moda"]
                             :color "#E4C1F9"
                             :icon "👔"}}
   
   :report {:formats [:txt :json :edn :csv :html]
            :default-format :txt
            :include-charts false
            :locale "pt-BR"}
   
   :analysis {:detect-duplicates true
              :duplicate-threshold-hours 24
              :min-transaction-amount 0.01
              :outlier-threshold 3.0  ; desvios padrão
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
    (spit file-path (with-out-str (clojure.pprint/pprint config)))
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
  (get-category-config default-config "Alimentação")
  )
