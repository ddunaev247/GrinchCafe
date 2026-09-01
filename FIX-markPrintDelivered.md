# Исправление: markPrintDelivered помечает весь стол как напечатанный

> Статус: **не реализовано** — задокументировано для будущей работы.  
> Дата: 2026-08-22

---

## Суть проблемы

У каждой позиции заказа два счётчика:

- `count` — сколько штук в чеке
- `printedCount` — сколько уже отправлено на печать

**Дельта** = `count - printedCount`. Печать запускается только если есть дельта хотя бы у одной позиции.

После успешной печати вызывается `PrintSessionHelper.markPrintDelivered()`, который через `markPrinted(db, plan.tableLines)` выставляет `printedCount = count` **для всех позиций стола**, без учёта фильтров категорий и без учёта того, на какой принтер позиция реально ушла.

**Файлы:**

- `app/src/main/java/com/example/deviceinfo/util/PrintSessionHelper.java` — `buildPlan`, `markPrintDelivered`, `markPrinted`
- `app/src/main/java/com/example/deviceinfo/TableActivity.java` — вызов после USB (до кухни)
- `app/src/main/java/com/example/deviceinfo/server/CafeApiHandler.java` — вызов после `PrintDispatcher.dispatchSync`

---

## Как сейчас формируются чеки

В `buildPlan()`:

| Принтер | Источник данных | Фильтр |
|---------|-----------------|--------|
| **Бар (USB)** | все позиции стола | `ReceiptCategoryConfig.Target.BAR` |
| **Кухня (сеть)** | только **дельта** (новые позиции) | `ReceiptCategoryConfig.Target.KITCHEN` + комплекс всегда |

Фильтрация применяется при формировании текста чека. В `PrintPlan` сохраняется только полный `tableLines` — отфильтрованные списки **не сохраняются**.

---

## Пример бага

**Настройки:** на кухне напитки выключены, суп включён. На баре всё включено.

**Стол:**

| Позиция | count | printedCount |
|---------|-------|--------------|
| Суп     | 1     | 0            |
| Пиво    | 1     | 0            |

**Шаг 1 — нажали «Печать»:**

- Дельта: суп + пиво → печать запускается
- **Бар:** суп + пиво
- **Кухня:** только суп (пиво отфильтровано)

**Шаг 2 — печать успешна → `markPrintDelivered`:**

- Суп: `printedCount` 0→1 — корректно
- Пиво: `printedCount` 0→1 — **ошибка**: на кухню не отправлялось, но помечено как напечатанное

**Шаг 3 — следующая печать:**

- Дельта пустая → «Нечего печатать»
- Пиво **никогда не уйдёт на кухню**, даже если позже включить категорию в настройках

**Риск:** высокий при активном использовании фильтров категорий чеков.

---

## Дополнительный нюанс (TableActivity)

После **успеха USB** сразу вызывается `onPrintDelivered(plan)` — **до** отправки на кухню:

```java
// TableActivity.sendToPosPrinter → onSuccess
onPrintDelivered(plan);
sendToNetworkPrinter(false, null, plan);
```

Пометка «всё напечатано» может произойти только по бару, даже если кухня потом упадёт.

В API (`CafeApiHandler`) вызов один — после `PrintDispatcher.dispatchSync`, но там та же проблема с `plan.tableLines` целиком.

---

## Предлагаемое исправление

### Принцип

Помечать как напечатанные **только те позиции (и в том объёме), которые реально попали хотя бы на один успешно доставленный чек**.

---

### 1. Расширить `PrintPlan`

Добавить поля (заполнять в `buildPlan` из уже существующих `barLines` и `kitchenDeltas`):

```java
public final List<OrderLine> barLines;           // что ушло в барный чек (полные count)
public final List<OrderLine> kitchenDeltaLines;  // что ушло на кухню (только дельта)
```

---

### 2. Изменить `markPrintDelivered`

Принимать, **какие каналы реально доставили** чек:

```java
markPrintDelivered(DatabaseHelper db, PrintPlan plan,
                   boolean barDelivered, boolean kitchenDelivered)
```

Логика для каждой строки из `plan.tableLines`:

| Ситуация | Действие |
|----------|----------|
| Позиция **не** в баре и **не** на кухне (фильтр) | **не трогать** `printedCount` |
| Попала на **бар** и бар доставлен | `printedCount = count` (бар печатает полное количество) |
| Только на **кухню** (нет на баре) и кухня доставлена | `printedCount += delta` из `kitchenDeltaLines` |
| На обоих каналах | достаточно успеха бара → `printedCount = count` |

Псевдокод:

```java
for (OrderLine tableLine : plan.tableLines) {
    boolean onBar = barDelivered && containsLine(plan.barLines, tableLine);
    boolean onKitchen = kitchenDelivered && containsLine(plan.kitchenDeltaLines, tableLine);

    if (!onBar && !onKitchen) {
        continue;
    }
    if (onBar) {
        tableLine.setPrintedCount(tableLine.getCount());
    } else {
        int delta = deltaAmount(plan.kitchenDeltaLines, tableLine);
        tableLine.setPrintedCount(tableLine.getPrintedCount() + delta);
    }
    db.updateOrderLine(tableLine);
}
db.setPrintVersion(plan.tableId, plan.receiptVersion);
```

Вспомогательные методы:

- `containsLine(list, tableLine)` — сопоставление по `id` строки заказа (или `lineKey`)
- `deltaAmount(kitchenDeltaLines, tableLine)` — `count` из копии в `kitchenDeltaLines` для этой строки

---

### 3. Обновить вызовы

**CafeApiHandler.dispatchPrint:**

```java
PrintDispatcher.Result result = PrintDispatcher.dispatchSync(appContext, plan);
if (result.anyDelivered()) {
    PrintSessionHelper.markPrintDelivered(db, plan,
            result.usbDelivered, result.networkDelivered);
}
```

**TableActivity** — помечать **один раз**, когда известен итог обоих каналов, а не сразу после USB:

| USB | Кухня | Пометка |
|-----|-------|---------|
| OK  | не нужна | по `barLines` |
| OK  | OK | по бару + кухне |
| fail | OK | только `kitchenDeltaLines` |
| fail | fail | **ничего не помечать** |

Убрать ранний `onPrintDelivered(plan)` в `sendToPosPrinter.onSuccess`; перенести в общий финальный callback после попытки обоих каналов.

---

## Поведение после исправления

### Тот же пример (суп + пиво, пиво без кухни)

| Шаг | Суп | Пиво |
|-----|-----|------|
| После 1-й печати (бар + кухня OK) | printed=1 | printed=0 |
| Включили пиво на кухне в настройках | — | — |
| 2-я печать | дельта 0 | дельта 1 → уходит на кухню |
| После 2-й печати | printed=1 | printed=1 |

### Другие сценарии

- **Только бар** (кухня не настроена) → помечаются только позиции из `barLines`
- **Только кухня** (бар пустой по фильтру) → помечаются только дельты из `kitchenDeltaLines`
- **Комплекс** → всегда в фильтре → помечается при доставке соответствующего канала
- **Позиция исключена на обоих принтерах** → `printedCount` не меняется; при наличии других дельт печать других позиций возможна

---

## Edge case (опционально, отдельно)

Если на столе есть дельта **только** у позиций, исключённых на **обоих** принтерах, `buildPlan` может вернуть `null` («нечего печатать»), хотя формально дельта есть. Сейчас такие позиции при смешанном столе ошибочно помечаются вместе с остальными. После исправления они останутся с `printedCount < count` — это корректно. При необходимости можно добавить отдельное сообщение пользователю.

---

## Затрагиваемые файлы (чеклист)

- [ ] `app/.../util/PrintSessionHelper.java` — `PrintPlan`, `buildPlan`, `markPrintDelivered`
- [ ] `app/.../TableActivity.java` — порядок вызова пометки после USB/кухни
- [ ] `app/.../server/CafeApiHandler.java` — передача `usbDelivered` / `networkDelivered`

Схему БД менять **не нужно** — поле `printed_count` уже рассчитано на поштучный учёт.

---

## Сравнение

| | Сейчас | После исправления |
|--|--------|-------------------|
| Что помечается | весь стол | только реально напечатанное |
| Пиво без кухни | может «потеряться» | остаётся в дельте |
| Согласованность бар/кухня | нет | учитывается каждый канал |
| Фильтры категорий | ломают учёт | работают корректно |
