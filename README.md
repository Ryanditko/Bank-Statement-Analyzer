# Clojure Gains 💜

[![Clojure](https://img.shields.io/badge/Clojure-1.11.1-brightgreen.svg)](https://clojure.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Sistema profissional de análise de transações bancárias do Nubank, desenvolvido em Clojure com arquitetura modular, validação robusta e múltiplos formatos de exportação.

---

## ✨ Funcionalidades

### 📊 Análise
- ✅ **Parsing robusto de CSV** com suporte a múltiplos formatos
- ✅ **Categorização automática** em 14 categorias
- ✅ **Análise mensal** com estatísticas completas
- ✅ **Detecção de duplicatas** inteligente
- ✅ **Transações recorrentes** (assinaturas)
- ✅ **Análise de tendências** temporais
- ✅ **Top estabelecimentos** e gastos
- ✅ **Detecção de outliers** estatísticos

### 📝 Relatórios Multi-formato
- 📄 **TXT** - Formatado para leitura
- 🔧 **JSON** - Para integração
- 💾 **EDN** - Formato Clojure
- 📊 **CSV** - Transações processadas
- 🌐 **HTML** - Visual interativo

### 🛠️ Recursos
- ⚙️ **Configuração externa** em EDN
- 📋 **Sistema de logging** profissional
- ✔️ **Validação com Spec**
- 🔍 **Filtros avançados**
- 🧪 **Testes unitários**
- 🚀 **CLI completa**

---

## 💻 Uso Rápido

```powershell
# Análise básica
clojure -M -m nubank-analyzer.core -i exemplo-transacoes.csv

# Salvar em HTML
clojure -M -m nubank-analyzer.core -i transacoes.csv -o relatorio.html -f html

# Exportar todos os formatos
clojure -M -m nubank-analyzer.core -i transacoes.csv -f all

# Filtrar por categoria
clojure -M -m nubank-analyzer.core -i transacoes.csv --category "Alimentação"

# Apenas validar
clojure -M -m nubank-analyzer.core -i transacoes.csv --validate-only

# Ajuda completa
clojure -M -m nubank-analyzer.core --help
```

---

## 📁 Estrutura do Projeto

```
Clojure-Script/
├── src/nubank_analyzer/
│   ├── core.clj          # Orquestração principal
│   ├── cli.clj           # Interface de comando
│   ├── config.clj        # Configuração
│   ├── logger.clj        # Sistema de log
│   ├── parser.clj        # Parsing de CSV
│   ├── validation.clj    # Validação Spec
│   ├── analyzer.clj      # Análise estatística
│   └── reports.clj       # Geração de relatórios
├── test/nubank_analyzer/ # Testes unitários
├── resources/            # Configurações
├── exemplo-transacoes.csv
└── deps.edn
```

---

## 🏷️ Categorias Automáticas (14)

🍔 Alimentação • 🚗 Transporte • 📺 Assinaturas • 🛒 Supermercado  
💊 Saúde • 📚 Educação • 🎬 Lazer • 🛍️ Compras Online  
🔧 Serviços • 📈 Investimentos • 💸 Transferências • 🐾 Pet  
🏠 Casa • 👔 Vestuário

---

## 📊 Exemplo de Saída

```
════════════════════════════════════════════════════════════════════════════
              ANÁLISE COMPLETA DE TRANSAÇÕES NUBANK
              Professional Edition v2.0
════════════════════════════════════════════════════════════════════════════

📊 RESUMO GERAL
  Total de Transações:      15
  Valor Total:              R$ 1,701.35
  Média por Transação:      R$ 113.42
  Mediana:                  R$ 52.30
  Desvio Padrão:            R$ 142.58

📅 ANÁLISE MENSAL
  10/2025
    Total:           R$ 1,701.35 (15 transações)
    Média:           R$ 113.42
    Top 3 categorias:
      Transferências       R$ 500.00
      Supermercado         R$ 320.45
      Transporte           R$ 331.90

🏷️ ANÁLISE POR CATEGORIA
  Transferências
    Total:           R$ 500.00 (29.4% do total)
    Transações:      1 (média: R$ 500.00)

💰 TOP 20 MAIORES GASTOS
   1. 05/10/2025 | R$ 500.00 | Transferências | PIX Transferencia
   2. 10/10/2025 | R$ 320.45 | Supermercado | Carrefour Supermerc

🔄 TRANSAÇÕES RECORRENTES
  Netflix Servicos
    Valor:           R$ 44.90
    Ocorrências:     2 vezes
```

---

## ⚙️ Configuração Customizada

```powershell
# Gerar config padrão
clojure -M -m nubank-analyzer.core --export-config my-config.edn

# Usar config customizada
clojure -M -m nubank-analyzer.core -i transacoes.csv -c my-config.edn
```

Edite `my-config.edn` para adicionar categorias customizadas:

```clojure
:categories {"Minha Categoria" {:keywords ["palavra1" "palavra2"]
                                :color "#FF0000"
                                :icon "📦"}}
```

---

## 🧪 Testes

```powershell
# Executar todos os testes
clojure -X:test
```

---

## 📱 Exportar do Nubank

1. App Nubank → **Menu** → **Cartão de Crédito**
2. Selecione a **fatura**
3. **⋮** → **Exportar fatura** → **CSV**

---

## 🔧 Desenvolvimento REPL

```clojure
; Analisar arquivo
(require '[nubank-analyzer.core :as core])
(def analysis (core/analyze-file "exemplo-transacoes.csv"))

; Ver estatísticas
(get-in analysis [:general :stats])

; Exportar
(require '[nubank-analyzer.reports :as reports])
(reports/export-report analysis :html "relatorio.html")
```

---

## 📜 Licença

Livre para uso pessoal e educacional.

---

## 🤝 Contribuindo

Pull requests são bem-vindos! Para mudanças importantes, abra uma issue primeiro.
