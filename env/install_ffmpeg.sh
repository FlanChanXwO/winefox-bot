#!/bin/bash

# This script checks for and installs ffmpeg on Debian-based systems.
# It requires sudo privileges to install packages.

# Check if ffmpeg is installed by trying to find its location
if command -v ffmpeg &> /dev/null
then
    echo "ffmpeg is already installed."
    echo "Version: $(ffmpeg -version | head -n 1)"
else
    echo "ffmpeg not found, attempting to install..."

    # Update package lists and install ffmpeg
    sudo apt-get update && sudo apt-get install -y ffmpeg

    # Verify installation
    if command -v ffmpeg &> /dev/null
    then
        echo "ffmpeg installed successfully."
        echo "Version: $(ffmpeg -version | head -n 1)"
    else
        echo "Error: Failed to install ffmpeg. Please try installing it manually."
        exit 1
    fi
fi

exit 0
