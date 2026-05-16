# VKR Formal Verification Workspace

Проект теперь состоит из двух Maven-модулей:

- `formal-verification-core` - отдельная библиотека исполняемых контрактов, инвариантов и аннотаций спецификаций.
- `bank-application` - Spring Boot банковский MVP, который применяет библиотеку к критичным методам счетов и переводов.

Подробное описание формальной части: [`docs/formal-verification.md`](docs/formal-verification.md).

## Предметная область

Приложение моделирует небольшой банковский контур:

- клиент открывает счет в заданной валюте;
- счет можно пополнить;
- со счета можно списать деньги при достаточном балансе;
- между двумя активными счетами одной валюты можно выполнить перевод;
- счет можно заблокировать, после чего денежные операции по нему запрещены.

## Критичные методы

Для формальной проверки выделены методы `BankService`:

1. `createAccount` - создает активный счет с неотрицательным начальным балансом.
2. `deposit` - увеличивает баланс активного счета на положительную сумму.
3. `withdraw` - уменьшает баланс активного счета при достаточности средств.
4. `transfer` - переносит сумму между двумя активными счетами одной валюты с сохранением общей суммы.
5. `blockAccount` - переводит активный счет в состояние `BLOCKED`.

Основные инварианты:

- сумма операции больше нуля и имеет не более 2 знаков после запятой;
- баланс счета не становится отрицательным;
- заблокированный счет не участвует в денежных операциях;
- перевод между разными валютами запрещен;
- перевод сохраняет сумму балансов двух участвующих счетов.

## Formal verification core

Библиотека `formal-verification-core` дает общий API:

- `Contract.requires(...)` - предусловия;
- `Contract.ensures(...)` - постусловия;
- `Contract.invariant(...)` - инварианты;
- `ProofObligation` - запись обязательства доказательства `Gamma |- B`, прямой формулы `Gamma -> B` и обратной проверки `Gamma & !B`;
- `BigDecimalSpec` и `LongArithmeticSpec` - готовые предикаты для денежных и целочисленных проверок;
- `@Verified`, `@Precondition`, `@Postcondition`, `@Invariant` - аннотации спецификаций.

`bank-application` использует эту библиотеку в `Account`, `MoneyRules` и `BankService`.

Формальная схема взята из добавленного пособия `Математическая логика.pdf`: предусловия и инварианты рассматриваются как гипотезы `Gamma`, постусловие метода как следствие `B`, а проверка корректности сводится к доказательству `Gamma -> B` или к отсутствию контрпримера для `Gamma & !B`.

## JML и JBMC

Для JBMC добавлена упрощенная модель счета в копейках:

- `bank-application/src/test/java/com/example/bank/verification/VerifiedAccountModel.java`
- `bank-application/src/test/java/com/example/bank/verification/BankOperationVerificationHarness.java`

JML-контракт самого показательного метода находится у `VerifiedAccountModel.transferTo(...)`. Он задает предусловия перевода, изменяемые поля и постусловия сохранения общей суммы.

Запуск тестов и компиляция harness:

```powershell
mvn test
```

Запуск JBMC после установки `jbmc` в `PATH`:

```powershell
.\scripts\run-jbmc.ps1
```

## REST API

Создать счет:

```http
POST /api/accounts
```

```json
{
  "ownerName": "Ivan Petrov",
  "currency": "RUB",
  "initialBalance": 1000.00
}
```

Пополнить счет:

```http
POST /api/accounts/{accountId}/deposit
```

Списать со счета:

```http
POST /api/accounts/{accountId}/withdraw
```

Перевести деньги:

```http
POST /api/transfers
```

Заблокировать счет:

```http
POST /api/accounts/{accountId}/block
```

## Запуск

```powershell
mvn -pl bank-application spring-boot:run
```

## Тесты

```powershell
mvn test
```
