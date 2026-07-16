#!/usr/bin/env bash
#
# Развёртывание Oracle Database XE в Docker для проекта oracle-packages.
# Образ: gvenzl/oracle-xe (Docker Hub, открытый доступ).
#
# Требования: Docker, Docker Compose v2, bash (Git Bash / WSL / Linux / macOS).
#
# Использование:
#   ./deploy-oracle.sh up          # запуск
#   ./deploy-oracle.sh down        # остановка и удаление контейнера
#   ./deploy-oracle.sh status      # статус и строка подключения
#   ./deploy-oracle.sh logs        # логи
#   ./deploy-oracle.sh wait        # дождаться готовности
#   ./deploy-oracle.sh sql         # пример подключения sqlplus (если установлен)
#   ./deploy-oracle.sh load-sql    # загрузить спецификации из ../sql (нужен sqlplus)
#   ./deploy-oracle.sh reset       # удалить контейнер и volume (полный сброс данных)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"
ENV_FILE="${SCRIPT_DIR}/.env"
ENV_EXAMPLE="${SCRIPT_DIR}/.env.example"

# shellcheck disable=SC1091
load_env() {
  if [[ -f "${ENV_FILE}" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "${ENV_FILE}"
    set +a
  elif [[ -f "${ENV_EXAMPLE}" ]]; then
    echo "Файл ${ENV_FILE} не найден. Используется ${ENV_EXAMPLE}."
    echo "Рекомендуется: cp deploy/.env.example deploy/.env"
    set -a
    # shellcheck source=/dev/null
    source "${ENV_EXAMPLE}"
    set +a
  else
    echo "Ошибка: нет ${ENV_FILE} и ${ENV_EXAMPLE}" >&2
    exit 1
  fi

  ORACLE_CONTAINER_NAME="${ORACLE_CONTAINER_NAME:-oracle-packages-db}"
  ORACLE_PORT="${ORACLE_PORT:-1521}"
  ORACLE_DATABASE="${ORACLE_DATABASE:-XEPDB1}"
  APP_USER="${APP_USER:-oracle_packages}"
  APP_USER_PASSWORD="${APP_USER_PASSWORD:-OraclePackages1}"
  ORACLE_VOLUME="${ORACLE_VOLUME:-oracle-packages-oradata}"
}

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "Ошибка: Docker не найден в PATH." >&2
    exit 1
  fi
  if ! docker info >/dev/null 2>&1; then
    echo "Ошибка: Docker daemon недоступен. Запустите Docker Desktop." >&2
    exit 1
  fi
}

compose() {
  docker compose --env-file "${ENV_FILE:-${ENV_EXAMPLE}}" -f "${COMPOSE_FILE}" "$@"
}

cmd_up() {
  require_docker
  if [[ ! -f "${ENV_FILE}" ]]; then
    cp "${ENV_EXAMPLE}" "${ENV_FILE}"
    echo "Создан ${ENV_FILE} из примера. Проверьте пароли перед продакшеном."
  fi
  echo "Загрузка образа и запуск Oracle..."
  compose pull
  compose up -d
  echo ""
  echo "Контейнер запущен. Инициализация БД может занять 2–5 минут."
  echo "Проверка готовности: ./deploy-oracle.sh wait"
  cmd_status
}

cmd_down() {
  require_docker
  compose down
  echo "Контейнер остановлен."
}

cmd_status() {
  require_docker
  load_env
  echo "Контейнер: ${ORACLE_CONTAINER_NAME}"
  compose ps || true
  echo ""
  echo "Подключение (Easy Connect):"
  echo "  ${APP_USER}/${APP_USER_PASSWORD}@//localhost:${ORACLE_PORT}/${ORACLE_DATABASE}"
  echo ""
  echo "JDBC:"
  echo "  jdbc:oracle:thin:@//localhost:${ORACLE_PORT}/${ORACLE_DATABASE}"
  echo "  user=${APP_USER}  password=${APP_USER_PASSWORD}"
  echo ""
  echo "SYSTEM (админ PDB):"
  echo "  system/<ORACLE_PASSWORD>@//localhost:${ORACLE_PORT}/${ORACLE_DATABASE}"
}

cmd_logs() {
  require_docker
  compose logs -f --tail=100 oracle
}

cmd_wait() {
  require_docker
  load_env
  echo "Ожидание healthcheck контейнера ${ORACLE_CONTAINER_NAME}..."
  local i=0
  local max=60
  while (( i < max )); do
    if docker inspect --format='{{.State.Health.Status}}' "${ORACLE_CONTAINER_NAME}" 2>/dev/null | grep -q healthy; then
      echo "Oracle готов (healthy)."
      cmd_status
      return 0
    fi
    sleep 5
    (( i++ )) || true
    echo "  ... ещё не готов (${i}/${max})"
  done
  echo "Таймаут ожидания. Проверьте логи: ./deploy-oracle.sh logs" >&2
  return 1
}

cmd_sql() {
  require_docker
  load_env
  if ! command -v sqlplus >/dev/null 2>&1; then
    echo "sqlplus не установлен в PATH."
    echo "Подключитесь вручную или через SQL Developer / DBeaver:"
    echo "  ${APP_USER}/${APP_USER_PASSWORD}@//localhost:${ORACLE_PORT}/${ORACLE_DATABASE}"
    return 0
  fi
  sqlplus -L "${APP_USER}/${APP_USER_PASSWORD}@//localhost:${ORACLE_PORT}/${ORACLE_DATABASE}"
}

cmd_load_sql() {
  require_docker
  load_env
  if ! command -v sqlplus >/dev/null 2>&1; then
    echo "Для load-sql нужен Oracle sqlplus в PATH." >&2
    echo "Альтернатива: выполните файлы из ${PROJECT_ROOT}/sql вручную в SQL Developer." >&2
    exit 1
  fi
  local conn="${APP_USER}/${APP_USER_PASSWORD}@//localhost:${ORACLE_PORT}/${ORACLE_DATABASE}"
  echo "Загрузка SQL-спецификаций из ${PROJECT_ROOT}/sql ..."
  for f in standard.sql utl_raw.sql utl_encode.sql; do
    local path="${PROJECT_ROOT}/sql/${f}"
    if [[ -f "${path}" ]]; then
      echo "  -> ${f}"
      sqlplus -L -S "${conn}" @"${path}" || {
        echo "Предупреждение: ошибка при выполнении ${f} (возможны зависимости SYS)." >&2
      }
    fi
  done
  echo "Готово."
}

cmd_reset() {
  require_docker
  load_env
  read -r -p "Удалить контейнер и volume ${ORACLE_VOLUME}? Все данные БД будут потеряны [y/N]: " ans
  if [[ "${ans}" =~ ^[Yy]$ ]]; then
    compose down -v
    echo "Сброс выполнен."
  else
    echo "Отменено."
  fi
}

usage() {
  cat <<EOF
Развёртывание Oracle XE (Docker) — oracle-packages

Команды:
  up        Запустить контейнер (создаёт deploy/.env при отсутствии)
  down      Остановить и удалить контейнер
  status    Статус и строки подключения
  logs      Логи контейнера (follow)
  wait      Дождаться готовности (healthcheck)
  sql       Подключение sqlplus (если установлен)
  load-sql  Выполнить ../sql/*.sql через sqlplus
  reset     Остановить и удалить volume с данными

Пример:
  cd deploy && ./deploy-oracle.sh up && ./deploy-oracle.sh wait

EOF
}

main() {
  local cmd="${1:-}"
  case "${cmd}" in
    up)       load_env; cmd_up ;;
    down)     load_env; cmd_down ;;
    status)   load_env; cmd_status ;;
    logs)     load_env; cmd_logs ;;
    wait)     load_env; cmd_wait ;;
    sql)      load_env; cmd_sql ;;
    load-sql) load_env; cmd_load_sql ;;
    reset)    load_env; cmd_reset ;;
    -h|--help|help|"") usage ;;
    *)
      echo "Неизвестная команда: ${cmd}" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"
