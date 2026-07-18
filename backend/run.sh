#!/bin/bash

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_DIR="$( dirname "$SCRIPT_DIR" )"

cd "$PROJECT_DIR"

echo "📂 Project root: $PROJECT_DIR"
echo "📂 Backend directory: $SCRIPT_DIR"

if [ ! -d "backend/.venv" ]; then
    echo "⚙️ Creating Python virtual environment in backend/.venv (without-pip)..."
    python3 -m venv backend/.venv --without-pip
    
    echo "⚡ Activating virtual environment..."
    source backend/.venv/bin/activate
    
    echo "🥾 Bootstrapping pip..."
    curl -sS https://bootstrap.pypa.io/get-pip.py | python3
else
    echo "⚡ Activating virtual environment..."
    source backend/.venv/bin/activate
fi

echo "📦 Installing/verifying backend requirements..."
pip install -r backend/requirements.txt

echo "🚀 Starting FastAPI BridgeSense backend..."
python3 -m uvicorn backend.app.main:app --host 0.0.0.0 --port 8000 --reload

