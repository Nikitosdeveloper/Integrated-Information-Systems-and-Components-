# Credit Scoring: система анализа кредитных заявок

Java 21, Spring Boot 3.3, Maven (многомодульный).

## Запуск
```bash
cd infra && docker compose up -d && cd ..     # PostgreSQL :55432, MongoDB :57017, Redis :56379, Kafka :59092
export JAVA_HOME=<путь к JDK 21>
mvn -q -DskipTests package
scripts/run-all.sh                            # 8 процессов; логи в logs/
scripts/stop-all.sh
```
GraphQL: http://localhost:8080/graphql, GraphiQL: http://localhost:8080/graphiql.
Запрос из консоли: `scripts/gql.sh '<query>' [token]`.

## Тестовые пользователи (пароль у всех `Test1234!`)
admin@bank.test (ADMIN), analyst1@bank.test, analyst2@bank.test (ANALYST), committee@bank.test (COMMITTEE),
borrower1@alfa.test, borrower2@stroy.test, borrower3@soft.test (BORROWER), blocked.analyst@bank.test (заблокирован).
Токен: `mutation { login(email:"…", password:"Test1234!") { token } }`, далее заголовок `Authorization: Bearer <token>`.
