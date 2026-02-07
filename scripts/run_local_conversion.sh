#!/bin/bash
set -e

# Define virtual environment directory
VENV_DIR="venv_conversion"

echo "Creating virtual environment in $VENV_DIR..."
python3 -m venv $VENV_DIR

echo "Activating virtual environment..."
source $VENV_DIR/bin/activate

echo "Upgrading pip..."
pip install --upgrade pip

echo "Running conversion script..."
# Pass the script path. The script handles dependency installation inside the running env (which is this venv)
python3 scripts/colab_one_cell.py

echo "Deactivating..."
deactivate

echo "Done. Generated files should be in the current directory."
