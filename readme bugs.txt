========== commit: multiple_dialogs ============
precondition
- The user has lost Internet access or is turned off
- The app is open
test case
- Open the "burger" menu
- Repeatedly click on the item Telegram FAQ
actual res.
- Opens multiple AlertDialog with type 3
- And each of the open dialogs needs to be removed in turn
expected res.
- When executing a request, a single ProgressDialog opens

========== commit: incorrect_my_location ============
precondition
- The app is running
- The user is in a chat with the other person
test case
- Click on the "Attach to message" button ."
- Choose to share your geolocation
- On the geolocation selection screen, select a geolocation 4-5 kilometers away from the user's current location
- Then click on the "My geolocation" button ."
actual res.
- After clicking on the "My geolocation" button, the camera and marker on the map are animated, and the text "Send my current location" is shown to us"
- If you click on the "Send my current location" button during the animation, the geolocation of the position where the marker is currently located will be taken
expected res.
- If the button shows the text "Send my current location", you need to substitute the user's last known geolocation
