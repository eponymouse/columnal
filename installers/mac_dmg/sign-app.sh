#! /bin/sh

codesign --verbose --timestamp -s "Developer ID Application: Neil Brown" "$1" &&
    codesign --verify --deep --verbose=4 "$1" &&
    spctl -a -t exec -vv "$1" || echo "Failed to sign and verify Mac Bundle"
