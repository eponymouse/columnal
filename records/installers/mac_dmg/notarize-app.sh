#! /bin/sh

echo "To send the app to Apple to notarize, run this command as a user with the right keychain:"
echo xcrun altool --notarize-app --primary-bundle-id "xyz.columnal" --username "columnal@twistedsquare.com" --password "@keychain:Columnal Altool" --file "$1"
echo
echo "Make note of the UUID.  Once you get the email saying it is signed, run these two commands:"
echo xcrun altool --notarization-info UUID -u "columnal@twistedsquare.com" --password "@keychain:Columnal Altool"
echo xcrun stapler staple "$1"

