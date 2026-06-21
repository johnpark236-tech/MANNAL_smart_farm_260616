#!/usr/bin/env bash
set -euo pipefail

REPO_URL="https://github.com/johnpark236-tech/MANNAL_smart_farm_260616.git"
APP_DIR="${HOME}/MANNAL_smart_farm_260616"

sudo apt update
sudo apt install -y python3 python3-venv python3-pip git

if [[ -d "${APP_DIR}/.git" ]]; then
  git -C "${APP_DIR}" pull --ff-only
else
  git clone "${REPO_URL}" "${APP_DIR}"
fi

cd "${APP_DIR}/backend"

python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt

if [[ ! -f .env ]]; then
  cp .env.example .env
  chmod 600 .env
  echo "Created backend/.env from .env.example."
  echo "Edit .env and enter the real API keys before starting the service."
fi

echo "Backend installation complete: ${APP_DIR}/backend"
echo "Next: edit .env, install the systemd unit, and start mannal-backend."
