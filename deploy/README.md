# Oracle Database XE — развёртывание в Docker

Каталог `deploy/` содержит скрипты для запуска **Oracle Database XE** в Docker с использованием открытого образа [gvenzl/oracle-xe](https://hub.docker.com/r/gvenzl/oracle-xe) (Docker Hub, без регистрации на container-registry.oracle.com).

Образ основан на Oracle XE (Express Edition), подходит для разработки и проверки PL/SQL-спецификаций из `sql/`.

## Требования

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows/macOS) или Docker Engine (Linux)
- Docker Compose v2 (`docker compose`)
- Bash: Git Bash, WSL, Linux или macOS

## Быстрый старт

```bash
cd deploy
cp .env.example .env          # при необходимости измените пароли
chmod +x deploy-oracle.sh
./deploy-oracle.sh up
./deploy-oracle.sh wait
./deploy-oracle.sh status
```

Первая инициализация БД обычно занимает **2–5 минут**.

## Команды скрипта

| Команда | Описание |
|---------|----------|
| `up` | Скачать образ и запустить контейнер |
| `down` | Остановить контейнер |
| `status` | Статус и строки подключения (JDBC, Easy Connect) |
| `wait` | Дождаться `healthy` |
| `logs` | Просмотр логов |
| `sql` | Подключение через `sqlplus` (если установлен) |
| `load-sql` | Выполнить `../sql/standard.sql`, `utl_raw.sql`, `utl_encode.sql` |
| `reset` | Удалить контейнер и volume (полный сброс данных) |

## Подключение

После `wait` / `status`:

```
oracle_packages/OraclePackages1@//localhost:1521/XEPDB1
```

JDBC:

```
jdbc:oracle:thin:@//localhost:1521/XEPDB1
```

Администратор PDB: `system` / пароль из `ORACLE_PASSWORD` в `.env`.

## Файлы

| Файл | Назначение |
|------|------------|
| `deploy-oracle.sh` | Основной bash-скрипт |
| `docker-compose.yml` | Описание сервиса Oracle |
| `.env.example` | Пример переменных окружения |
| `init/00-grants.sh` | Права для `APP_USER` при первом старте |

Скрипты в `init/` монтируются в `/container-entrypoint-initdb.d` и выполняются **один раз** при создании новой БД.

## Загрузка спецификаций проекта

Пакеты в `sql/` — это **SYS.STANDARD**, **UTL_RAW**, **UTL_ENCODE**; для их компиляции в Oracle обычно нужны права DBA. Для экспериментов:

1. Подключитесь как `system` к PDB `XEPDB1`.
2. Выполните нужные `.sql` из каталога `../sql`.
3. Либо используйте `./deploy-oracle.sh load-sql` (требуется `sqlplus` в PATH).

Java-эмуляция в `src/` **не требует** запущенного Oracle; контейнер нужен для сверки поведения с реальной СУБД.

## Альтернативные образы

В `.env` можно сменить `ORACLE_IMAGE`:

- `gvenzl/oracle-xe:21-slim` — компактный XE 21c (по умолчанию)
- `gvenzl/oracle-xe:21-faststart` — быстрее первый старт, больше размер
- `gvenzl/oracle-free:23-slim-faststart` — Oracle Database 23c Free

Официальные образы Oracle: [container-registry.oracle.com](https://container-registry.oracle.com/) — требуют принятия лицензии и `docker login`.

## Остановка и сброс

```bash
./deploy-oracle.sh down      # остановить, данные в volume сохраняются
./deploy-oracle.sh reset     # удалить volume и все данные
```

## Windows

В PowerShell скрипт не запускается напрямую. Используйте **Git Bash** или **WSL**:

```bash
cd /c/Work/Projects/oracle-packages/deploy
./deploy-oracle.sh up
```
