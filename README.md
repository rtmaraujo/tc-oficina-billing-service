# tc-oficina-billing-service

Microsserviço de **orçamento e pagamento** da Oficina Mecânica. Gera orçamentos, controla o status (aprovação, pagamento, rejeição) e publica eventos no Kafka para a Saga.

- **Porta:** `8082`
- **Banco:** PostgreSQL (`oficina_billing`)
- **Namespace Kubernetes:** `oficina-billing`
- **Repositório:** https://github.com/rtmaraujo/tc-oficina-billing-service

## Stack

- Java 17 + Spring Boot 3.2.2
- Spring Web, Spring Data JPA, PostgreSQL
- Spring Kafka
- JUnit 5 + Mockito
- JaCoCo (cobertura mínima de 85%)
- Docker multi-stage + Kubernetes (Traefik)

## Arquitetura

```
presentation/   -> Controller REST
application/    -> Casos de uso (interface + impl)
domain/         -> Orcamento, StatusOrcamento
infrastructure/ -> Entidade JPA e repositório
config/         -> Listeners Kafka
```

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/billing/orcamento?clienteCpf={cpf}&valor={valor}` | Gera orçamento (status `PENDENTE`) |
| `GET` | `/api/billing/orcamento/{id}` | Consulta orçamento por ID |
| `GET` | `/api/billing/cliente/{clienteCpf}` | Lista orçamentos por cliente |
| `PUT` | `/api/billing/orcamento/{id}/status?novoStatus={status}` | Atualiza o status |

### Exemplos

```bash
# Gerar orçamento
curl -X POST "http://localhost:8082/api/billing/orcamento?clienteCpf=12345678909&valor=250.00"

# Consultar
curl "http://localhost:8082/api/billing/orcamento/1"

# Aprovar
curl -X PUT "http://localhost:8082/api/billing/orcamento/1/status?novoStatus=APROVADO"

# Registrar pagamento
curl -X PUT "http://localhost:8082/api/billing/orcamento/1/status?novoStatus=PAGO"
```

## Documentação da API (Swagger)

Desabilitado no perfil local e habilitado apenas no perfil `k8s`:

- `http://44.253.115.185/billing/swagger-ui.html`
- OpenAPI: `http://44.253.115.185/billing/v3/api-docs`

## Status do orçamento

`PENDENTE` → `APROVADO` → `PAGO`
Alternativos: `REJEITADO`, `CANCELADO`.

Ao mudar para `PAGO`, o serviço gera um `pagamentoId` (`PAG-<timestamp>`).

## Modelo de dados

Tabela `orcamento`:

| Campo | Tipo | Observação |
|---|---|---|
| `id` | Long | PK, auto incremento |
| `codigo_orcamento` | String | único, gerado como `ORC-<timestamp>` |
| `cliente_cpf` | String | CPF do cliente |
| `valor` | Double | valor do orçamento |
| `status` | Enum | `StatusOrcamento` |
| `data_criacao` | LocalDateTime | preenchida na criação |
| `data_atualizacao` | LocalDateTime | |
| `pagamento_id` | String | gerado ao marcar `PAGO` |

## Eventos Kafka

| Evento | Direção | Quando |
|---|---|---|
| `budget-generated` | Produz | Orçamento criado |
| `budget-approved` | Produz | Status `APROVADO` |
| `payment-completed` | Produz | Status `PAGO` |
| `budget-rejected` | Produz | Status `REJEITADO` |
| `os-created` | Consome | Listener `billing-service-group` |

## Integração Mercado Pago

As propriedades `mercadopago.*` (`access-token`, `webhook-secret`, `notification-url`, `base-url`) e as variáveis de ambiente já estão previstas na configuração e nos manifests. **A integração (preferência de pagamento e webhook) ainda não está implementada no código** — consta como pendência no README raiz.

## Configuração

| Variável | Padrão (local) | Descrição |
|---|---|---|
| `SERVER_PORT` | `8082` | Porta HTTP |
| `SPRING_PROFILES_ACTIVE` | — | `docker` ou `k8s` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/oficina_billing` | URL do PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | `oficina_user` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | `oficina_pass` | Senha do banco |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Endereço do Kafka |
| `MERCADOPAGO_ACCESS_TOKEN` | `TEST-xxxx` | Token do Mercado Pago |
| `MERCADOPAGO_WEBHOOK_SECRET` | `xxxx` | Segredo do webhook |
| `MERCADOPAGO_NOTIFICATION_URL` | `http://localhost:8082/webhook/mercadopago` | URL de notificação |

## Como executar localmente

### Com Docker Compose (recomendado)

```bash
docker compose up --build
```

Sobe PostgreSQL, Zookeeper, Kafka e o serviço. A porta HTTP é `8082`.

### Sem Docker

Pré-requisitos: PostgreSQL em `localhost:5432` (database `oficina_billing`) e Kafka em `localhost:9092`.

```bash
mvn spring-boot:run
```

## Testes e cobertura

```bash
mvn test              # testes unitários
mvn verify            # testes + jacoco:check (mínimo 85% LINE e BRANCH)
```

- Testes unitários: domínio, entidade de persistência e serviço de aplicação.
- Cobertura atual: **100%** de linhas e branches.
- Relatório: `target/site/jacoco/index.html`.

O build falha se a cobertura ficar abaixo de 85%, via `jacoco-maven-plugin` (`check`).

## Docker

```bash
docker build -t tc-oficina-billing-service:local .
docker run -p 8082:8082 --env SPRING_DATASOURCE_URL=... --env KAFKA_BOOTSTRAP_SERVERS=... tc-oficina-billing-service:local
```

## Deploy no Kubernetes

Manifests em `k8s/`:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
cp k8s/secret.example.yaml k8s/secret.yaml   # preencha as credenciais reais
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/ingressroute.yaml
```

- Imagem: `200111905660.dkr.ecr.us-west-2.amazonaws.com/tc-oficina-billing-service:latest`
- 2 réplicas, probes em `/actuator/health/{readiness,liveness}`
- Rotas Traefik: `/billing/*` e `/webhook/*`

## CI/CD

`.github/workflows/ci.yml`:

1. Build e testes com Java 17.
2. `mvn verify` — executa testes + `jacoco:check` (>= 85%).
3. Em push para `main`: build/push da imagem no ECR e `kubectl set image` no namespace `oficina-billing`.

## Observabilidade

Actuator exposto em `/actuator` (`health`, `info`, `metrics`, `prometheus` no perfil `k8s`).

## Estrutura

```
src/main/java/com/oficina/billing/service/
├── application/        # interface e implementação dos casos de uso
├── config/             # listeners Kafka
├── domain/             # Orcamento, StatusOrcamento
├── infrastructure/     # entidade JPA e repositório
└── presentation/       # controller REST
src/test/java/...        # testes unitários
```
