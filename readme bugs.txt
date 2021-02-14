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
