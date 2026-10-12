# WorldGuard для NeoForge 1.21.1

Неофициальный перенос WorldGuard на серверный API Minecraft/NeoForge. Исходный плагин из `need_migrate` сохранён как материал для миграции; Bukkit и Spigot для работы мода не нужны.

## Установка

1. Используйте Minecraft **1.21.1**, NeoForge **21.1.256 или новее в ветке 21.1** и Java **21**.
2. Поместите `build/libs/worldguard-1.0.0-neoforge.jar` в папку `mods` сервера.
3. Запустите сервер. Настройки появятся в `config/worldguard/config.yml`.

Мод устанавливается только на сервер: он не добавляет клиентский контент и не требует собственных сетевых каналов. Клиентам этот JAR не нужен. Требования остальных модов сервера действуют отдельно.

**WorldEdit необязателен.** Интеграция проверена с NeoForge-версией WorldEdit **7.3.8** для Minecraft 1.21.1. Если он установлен на сервере, можно использовать его выделения кубоида и полигона. Без него работают собственные команды выделения.

## Права и первый регион

**Для создания и управления своим регионом OP не нужен.** Обычный игрок может создать приват через `/rg claim <id>` или `/rg define <id>`: он автоматически становится владельцем. Владелец может изменять границы и флаги, добавлять/удалять владельцев и участников, удалять свой регион. Участники получают доступ к защищённым действиям, но не к изменению региона. Учитываются владельцы и участники, унаследованные от родительского региона. Плагин прав не требуется.

Размер обычного привата ограничен `regions.max-claim-volume` (по умолчанию **30 000 блоков**), количество — `regions.max-region-count-per-player.default` (по умолчанию **7** на измерение). Ограничения действуют и для `/rg define`, и при изменении границ. Создание и расширение привата не могут пересекать чужой регион.

Глобальные регионы, приоритеты, наследование, обход защиты, загрузка/импорт данных и команды `/wg` доступны консоли и операторам с уровнем не ниже `operator-level` (по умолчанию **2**). Эти ограничения не препятствуют обычным командам `/rg`.

Операторы по умолчанию обходят региональную защиту. Для проверки защиты оператором используйте `/rg bypass`. Для отключения обхода по умолчанию задайте `regions.disable-bypass-by-default: true` и выполните `/wg reload`.

Для выделения возьмите **обычный деревянный топор** в основную руку. **ЛКМ по блоку** задаёт первую точку, **ПКМ по другому блоку** — вторую. Вершины входят в выбранную область. При выделении блоки не ломаются, брёвна не обтёсываются, топор не теряет прочность. Работает на сервере без WorldEdit и без мода у клиента.

После выделения создайте регион и добавьте участника:

```text
/rg claim home
/rg addmember home FriendName
/rg flag home pvp deny
```

При установленном WorldEdit игроки с разрешением `worldedit.selection.pos` используют его инструмент выделения; остальные используют встроенный выбор деревянным топором. Команды координат остаются доступны:

```text
/rg pos1 100 60 100
/rg pos2 120 90 120
/rg claim home
/rg addmember home FriendName
/rg flag home pvp deny
/rg flag home entry -g nonmembers deny
/rg info home
```

Имена игроков в примере замените реальными. Также принимаются UUID. Выделение собственными командами имеет приоритет над WorldEdit; `/rg selection clear` возвращает использование выделения WorldEdit.

Для полигона задайте не меньше трёх точек `/rg point <x> <y> <z>` и высоты `/rg heights <minY> <maxY>`. `/rg selection clear` сбрасывает выделение. `/rg global` создаёт `__global__` для правил всего измерения.

## Команды

`/region`, `/regions` и `/rg` — синонимы. `/rg help` выводит справку. Чтобы обратиться к другому измерению, используйте `/rg in <dimension> <command>` или исходный параметр `-w <world>`, например `/rg info -w world home`.

| Задача | Команда |
| --- | --- |
| Создать или обновить регион | `/rg define <id> [owners]`, `/rg claim <id>`, `/rg redefine <id>` |
| Выбрать существующий регион | `/rg select <id>` |
| Посмотреть регионы | `/rg info [id]`, `/rg list [page]`, `/rg list owner <player>` |
| Владельцы и участники | `/rg addowner`, `/rg removeowner`, `/rg addmember`, `/rg removemember` |
| Приоритет и наследование | `/rg setpriority <id> <number>`, `/rg setparent <id> [parent]` |
| Настроить флаг | `/rg flag <id> <flag> [-g group] <value>` |
| Удалить значение флага | `/rg flag <id> <flag>` |
| Задать пустое текстовое значение | `/rg flag <id> <flag> -e` |
| Список флагов | `/rg flags [-p page] [id]`; значения можно менять кликом в чате |
| Телепортация | `/rg tp <id> [center]`, флаг `teleport` со значением `here` |
| Удалить регион | `/rg remove <id>`, `-u` снимает связь у дочерних регионов, `-f` удаляет потомков |
| Сохранение и загрузка | `/rg save`, `/rg reload`, `/wg reload` |
| Диагностика | `/wg version`, `/wg report`, `/wg flushstates` |
| Перенести данные | `/rg import <file> [replace]`, `/wg importconfig <file> [dimension]` |
| Перенести хранилище | `/rg migratedb yaml sql`, `/rg migratedb sql yaml` |
| Миграции UUID и высоты | `/rg migrateuuid`, `/rg migrateheights` |

`/rg define -g template` создаёт именованный глобальный шаблон наследования. Весь мир покрывает специальный регион `__global__`. Старые параметры управления регионами и фильтры `/rg list` сохранены через адаптер команд.

Группы применения флагов: `all`, `members`, `owners`, `nonmembers`, `nonowners`, `none`. Эти группы описывают отношение игрока к региону. Приоритеты, наследование и запрет при равном приоритете учитываются при вычислении правил.

Административные команды: `/god`, `/ungod`, `/heal`, `/slay`, `/wglocate`, `/locate player`, `/stack`, `/stopfire`, `/allowfire`, `/halt-activity`, `/wg profile`, `/wg stopprofile`. `/halt-activity confirm` останавливает естественную активность и удаляет загруженных мобов, предметы и снаряды; `/halt-activity resume` возобновляет её.

`/stack` объединяет одинаковые предметы во всём инвентаре. `/locate <player>` направляет личный компас на игрока, `/locate` возвращает точку появления мира. Ванильный `/locate structure` сохраняется.

`/wg debug testbreak|testplace|testinteract|testdamage [player] [-t] [-s]` симулирует событие NeoForge. `-t` использует взгляд указанного игрока, `-s` выводит результат также в журнал. `/wg report` сохраняет диагностический файл `config/worldguard/report.txt` с исключёнными паролями, JDBC-адресами и host keys. `/wg profile [minutes] [-i milliseconds] [-t "thread name"]` собирает стеки потоков и сохраняет отчёт в `config/worldguard/profiles`; `/wg stopprofile` завершает запись раньше. `/wg running` и `/wg queue` показывают состояние профилировщика и UUID-запросов.

## Перенос существующего сервера

При первом запуске мод читает `plugins/WorldGuard` в корне сервера. Глобальный `config.yml`, региональные YAML, конфигурации миров и `blacklist.txt` копируются или импортируются в новую структуру. Исходные файлы сохраняются. Автоимпорт регионов выполняется, если целевой `regions.yml` отсутствует; при SQL-хранилище — если целевой набор регионов пуст.

Соответствие имён старых миров задаётся в `migration.worlds`:

```yaml
migration:
  auto-import: true
  worlds:
    world: minecraft:overworld
    world_nether: minecraft:the_nether
    world_the_end: minecraft:the_end
    old_custom_world: example:custom_dimension
```

Если исходный `config.yml` уже скопирован автоматически и не содержит `migration.worlds`, используются три стандартных соответствия. Для нестандартных миров настройте соответствия и перезапустите сервер. Измерения должны существовать на новом сервере.

Для ручного импорта положите YAML в `config/worldguard/import/`:

```text
/rg in minecraft:overworld import old-regions.yml
/wg importconfig old-config.yml
```

Обычный импорт сохраняет существующие регионы и пропускает совпадающие ID. Суффикс `replace` заменяет набор регионов выбранного измерения. Конфигурация перед заменой сохраняется в `config.yml.bak`.

Нативные файлы находятся в `config/worldguard/worlds/<namespace>/<dimension>/regions.yml`, например `worlds/minecraft/overworld/regions.yml`. Регионы сохраняются после изменения; запись производится через временный файл с атомарной заменой. Ошибочный YAML не заменяет действующие данные. Неудачная перезагрузка блокирует защищаемые действия до исправления и успешного `/wg reload`.

Подробности совместимости и отличия от Spigot описаны в [MIGRATION.md](MIGRATION.md).

## SQL и UUID

Драйверы SQLite и MySQL/MariaDB включены в JAR. Для существующего SQL WorldGuard 7 сохраните исходные `dsn`, имя пользователя, пароль и префикс таблиц в `regions.sql`. Имена миров из базы сопоставляются через `migration.worlds`.

Пример новой SQLite-базы:

```yaml
regions:
  sql:
    use: true
    dsn: jdbc:sqlite:config/worldguard/regions.sqlite
    username: ''
    password: ''
    table-prefix: ''
```

Для MySQL/MariaDB используйте, например, `jdbc:mysql://localhost:3306/worldguard`. После изменения настроек выполните `/wg reload`. `/rg migratedb yaml sql` копирует YAML-регионы в SQL и сохраняет прежние целевые данные в `config/worldguard/backups`; затем включите `regions.sql.use`. Обратный перенос выполняет `/rg migratedb sql yaml`, после чего выключите `regions.sql.use`.

UUID-миграция охватывает все сохранённые измерения. Она использует известных игроков и vanilla-кэш; в online-mode неизвестные имена разрешаются асинхронно через службу профилей Minecraft. В offline-mode неизвестные имена сохраняются без генерации UUID при `keep-names-that-lack-uuids: true`. Настройки `regions.uuid-migration.perform-on-next-start` и `keep-names-that-lack-uuids` сохранены. Перед изменениями создаётся YAML-копия регионов.

## Защита сундуков табличками

Опциональная функция включается ключом `chest-protection.enable: true`. На табличке под сундуком или рядом с ним первая строка должна быть `[Lock]`, следующие — имена или UUID владельцев. Первый владелец при создании должен совпадать с автором таблички, если не действует операторский обход. Учитываются обе половины двойного сундука. Защищаются доступ, разрушение, изменение таблички и перенос предметов воронками.

## Сборка и проверка

Нужна JDK 21 в `JAVA_HOME`:

```powershell
./gradlew.bat build
./gradlew.bat runGameTestServer
./gradlew.bat runPackagedGameTestServer
```

`build` запускает модульные тесты и создаёт JAR в `build/libs`. Игровые тесты в `run-gametest` проверяют настоящие серверные события и mixin-хуки. Для проверки WorldEdit положите его NeoForge JAR в `run-gametest/mods`. `runPackagedGameTestServer` загружает собранный мод и отдельный тестовый модуль из `run-packaged/mods`, проверяя вложенные библиотеки. Тестовый модуль в `build/testing` нужен только для проверки.

MySQL/MariaDB-тесты включаются переменной `WORLDGUARD_TEST_MYSQL_DSN`, например `jdbc:mysql://127.0.0.1:13307/worldguard_test`; при необходимости задайте `WORLDGUARD_TEST_MYSQL_USER` и `WORLDGUARD_TEST_MYSQL_PASSWORD`. Они создают таблицы со случайным префиксом и удаляют свои таблицы после проверки. SQLite проверяется всегда. `python tools/audit_migration.py` обновляет [инвентарь переноса](MIGRATION_AUDIT.md).

Лицензия — LGPL-3.0-or-later. Атрибуция и лицензии находятся в `NOTICE.txt`, `LICENSE.txt` и `COPYING.txt`.
