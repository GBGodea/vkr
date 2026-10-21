# VKR Formal Verification Workspace

## НИР третьего семестра и основа ВКР

Реализованы [этап 2 — проектирование и методика эксперимента](docs/nir-stage-2.md)
и [этап 3 — генератор проверок](docs/nir-stage-3.md).

Аннотации методов `BankArithmetic` автоматически превращаются в Java-проверки для JBMC.
Эти же методы денежных расчётов используются приложением через точный адаптер `BigDecimal` → копейки.
Отдельно проверяется существование входов, удовлетворяющих предусловиям. Результат доказательства относится к явно указанным границам; интеграция со Spring и транзакции пока не покрыты.

```powershell
mvn clean test
python -m unittest discover -s scripts/tests -v
.\scripts\run-generated-verification.ps1 -CompareManual
.\scripts\run-stage3-examples.ps1
```

Требуются JDK 17/21, Maven, Python 3.9+ и JBMC в PATH. Параметры `-Python` и `-Jbmc` позволяют указать пути явно.
Генератор — отдельный модуль `formal-verification-processor`, не зависящий от Spring или банковской предметной области.
При изменении или удалении спецификаций используйте чистую сборку: она исключает устаревшие сгенерированные файлы.
Сохранённые результаты: [отчёт этапа 3](docs/results/stage-3/verification-report.md).

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

Формальная схема взята из пособия `Математическая логика`: предусловия и инварианты рассматриваются как гипотезы `Gamma`, постусловие метода как следствие `B`, а проверка корректности сводится к доказательству `Gamma -> B` или к отсутствию контрпримера для `Gamma & !B`.

## Ручная модель второго семестра: JML и JBMC

Для контрольного сравнения сохранена прежняя упрощенная модель счета в копейках:

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
