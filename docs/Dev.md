# Guia de Desenvolvimento

## Arquitetura do Sistema

### Fluxo de Execução

```
CLI → Config → Parser → Validation → Analyzer → Reports
```

### Módulos

1. **core.clj** - Orquestração principal
2. **cli.clj** - Gerencia argumentos e comandos
3. **config.clj** - Configuração centralizada
4. **logger.clj** - Sistema de logging
5. **parser.clj** - Leitura e parsing de CSV
6. **validation.clj** - Validação com Clojure Spec
7. **analyzer.clj** - Análise e estatísticas
8. **reports.clj** - Geração de relatórios

## Adicionando Nova Funcionalidade

### 1. Adicionar Nova Categoria

Edite `src/nubank_analyzer/config.clj`:

```clojure
:categories {"Nova Categoria" 
             {:keywords ["palavra1" "palavra2"]
              :color "#HEXCOLOR"
              :icon "🎯"}}
```

### 2. Adicionar Novo Formato de Relatório

Em `src/nubank_analyzer/reports.clj`:

```clojure
(defn generate-xml-report [analysis output-stream]
  ; Implementação aqui
  )

;; Adicionar ao case em export-report
(case format
  ; ...
  :xml (generate-xml-report analysis writer))
```

### 3. Adicionar Nova Análise

Em `src/nubank_analyzer/analyzer.clj`:

```clojure
(defn minha-analise [transactions]
  ; Implementação
  )

;; Adicionar a perform-complete-analysis
(let [minha-analise (minha-analise enriched-txs)]
  {:general general-stats
   ; ...
   :minha-analise minha-analise})
```

## Testes

### Executar Testes

```powershell
clojure -X:test
```

### Criar Novo Teste

```clojure
(ns nubank-analyzer.meu-modulo-test
  (:require [clojure.test :refer [deftest is testing]]
            [nubank-analyzer.meu-modulo :as mm]))

(deftest test-minha-funcao
  (testing "Descrição do teste"
    (is (= resultado-esperado (mm/minha-funcao input)))))
```

## Debugging

### Modo Verbose

```powershell
clojure -M -m nubank-analyzer.core -i transacoes.csv --verbose
```

### Modo Debug

```powershell
clojure -M -m nubank-analyzer.core -i transacoes.csv --debug
```

### REPL

```clojure
; Iniciar REPL
clojure

; Carregar namespace
(require '[nubank-analyzer.core :as core])
(require '[nubank-analyzer.logger :as log])

; Configurar log
(log/configure! {:level :debug :console true})

; Processar
(def result (core/analyze-file "exemplo-transacoes.csv"))
```

## Performance

### Timing

O sistema inclui timing automático via `log/with-timing`:

```clojure
(log/with-timing "Minha operação"
  ; código aqui
  )
```

### Profiling

Para operações pesadas, use `time`:

```clojure
(time (minha-funcao-pesada))
```

## Boas Práticas

1. **Sempre validar inputs** com Clojure Spec
2. **Logar operações importantes** com níveis apropriados
3. **Escrever testes** para novas funcionalidades
4. **Documentar funções** com docstrings
5. **Usar threading macros** (->, ->>) para clareza
6. **Evitar side effects** em funções de análise
7. **Tratar exceções** adequadamente

## Estrutura de Commit

```
tipo(escopo): descrição curta

Descrição detalhada se necessário

- Item 1
- Item 2
```

Tipos:
- `feat`: Nova funcionalidade
- `fix`: Correção de bug
- `docs`: Documentação
- `test`: Testes
- `refactor`: Refatoração
- `perf`: Performance

## Publicação

### Build

```powershell
clojure -X:uberjar
```

### Executar JAR

```powershell
java -jar nubank-analyzer.jar -i transacoes.csv
```

## Roadmap

- [ ] Gráficos interativos no HTML
- [ ] Export para Excel
- [ ] API REST
- [ ] Dashboard web
- [ ] Machine Learning para categorização
- [ ] Previsão de gastos
- [ ] Integração com outras APIs bancárias
