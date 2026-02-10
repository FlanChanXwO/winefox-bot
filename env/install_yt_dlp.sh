#!/bin/bash

# This script checks for and installs the latest version of yt-dlp.
# It requires sudo privileges to move the binary to a system-wide location.

# Check if yt-dlp is installed
if command -v yt-dlp &> /dev/null
then
    echo "yt-dlp is already installed."
    echo "Version: $(yt-dlp --version)"
    # Optional: Offer to update yt-dlp
    read -p "Do you want to update yt-dlp to the latest version? [y/N] " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]
    then
        echo "Updating yt-dlp..."
        sudo yt-dlp -U
        echo "yt-dlp updated successfully."
        echo "New Version: $(yt-dlp --version)"
    fi
else
    echo "yt-dlp not found, attempting to install..."

    # Download the latest yt-dlp binary from GitHub
    # Using -L to follow redirects
    curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /tmp/yt-dlp

    # Make the binary executable
    chmod +x /tmp/yt-dlp

    # Move the binary to a location in the system's PATH
    sudo mv /tmp/yt-dlp /usr/local/bin/yt-dlp

    # Verify installation
    if command -v yt-dlp &> /dev/null
    then
        echo "yt-dlp installed successfully."
        echo "Version: $(yt-dlp --version)"
    else
        echo "Error: Failed to install yt-dlp. Please try installing it manually."
        exit 1
    fi
fi

exit 0
