from telegram import Update
from telegram.ext import Updater, CommandHandler, CallbackContext

# Define a command handler function
def start(update: Update, context: CallbackContext) -> None:
    update.message.reply_text('Salom! Kino joylash uchun tayyorman!')

def main() -> None:
    # Create an Updater object with your bot's token
    updater = Updater("YOUR_BOT_TOKEN")

    # Get the dispatcher to register handlers
    dispatcher = updater.dispatcher

    # Register the command handler
    dispatcher.add_handler(CommandHandler("start", start))

    # Start the Bot
    updater.start_polling()

    # Run the bot until you send a signal to stop
    updater.idle()

if __name__ == '__main__':
    main()
