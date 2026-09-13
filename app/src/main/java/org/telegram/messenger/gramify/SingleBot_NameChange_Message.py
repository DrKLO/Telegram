"""
Devgram - Single Bot for Group Name Change + Message Send
SAFE VERSION - No flooding bypass, respects Telegram limits

Ye bot sirf un groups me kaam karega jahan ye ADMIN hai.
Telegram ka limit: Group title 1 minute me 1 baar se zyada change nahi hota.
FloodWait aaye to bot khud ruk jayega.

Setup:
1. @BotFather pe jao -> /newbot -> token lo
2. Bot ko apne group me add karo aur ADMIN banao (Can change group info + Can send messages)
3. Token niche daalo aur run karo: python SingleBot_NameChange_Message.py
"""

import asyncio
import logging
from telegram import Update
from telegram.ext import Application, CommandHandler, ContextTypes
from telegram.error import RetryAfter, BadRequest

# --- CONFIG ---
BOT_TOKEN = "APNA_BOT_TOKEN_YAHAN_DALO"  # BotFather se mila token
# --------------

logging.basicConfig(level=logging.INFO)

# Admin check - sirf admin hi command chala paye
async def is_user_admin(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if update.effective_chat.type == "private":
        return True
    try:
        member = await context.bot.get_chat_member(update.effective_chat.id, update.effective_user.id)
        return member.status in ["administrator", "creator"]
    except:
        return False

# /start
async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        "👋 Devgram Single Bot Ready\n\n"
        "Commands:\n"
        "/settitle <naya naam> - Group ka naam change karega\n"
        "/say <message> - Group me message bhejega\n\n"
        "Note: Bot ko admin banana zaroori hai. Flood limit ka respect karta hai."
    )

# /settitle - Group name change - SAFE
async def set_title(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not await is_user_admin(update, context):
        await update.message.reply_text("❌ Sirf admin ye command chala sakta hai.")
        return

    if not context.args:
        await update.message.reply_text("Usage: /settitle Naya Group Naam")
        return

    new_title = " ".join(context.args)
    if len(new_title) < 1 or len(new_title) > 128:
        await update.message.reply_text("❌ Title 1-128 characters ka hona chahiye.")
        return

    chat_id = update.effective_chat.id

    try:
        await context.bot.set_chat_title(chat_id=chat_id, title=new_title)
        await update.message.reply_text(f"✅ Group ka naam change ho gaya: {new_title}")
    
    except RetryAfter as e:
        # YEHI SAFE HANDLING HAI - FloodWait ka respect
        await update.message.reply_text(f"⏳ Telegram ne rok diya. {e.retry_after} sec baad try karo. (Flood limit)")
    
    except BadRequest as e:
        await update.message.reply_text(f"❌ Error: {e.message}\nBot ko 'Change group info' ka admin right do.")
    
    except Exception as e:
        await update.message.reply_text(f"❌ Error: {str(e)}")

# /say - Message send - SAFE with limit
async def say_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not await is_user_admin(update, context):
        await update.message.reply_text("❌ Sirf admin ye command chala sakta hai.")
        return

    if not context.args:
        await update.message.reply_text("Usage: /say Hello dosto")
        return

    text = " ".join(context.args)
    chat_id = update.effective_chat.id

    try:
        await context.bot.send_message(chat_id=chat_id, text=text)
        # Message bhejke delete kar do command wala msg (clean)
        try:
            await update.message.delete()
        except:
            pass

    except RetryAfter as e:
        await update.message.reply_text(f"⏳ Flood limit. {e.retry_after} sec ruk jao.")
    except Exception as e:
        await update.message.reply_text(f"❌ Error: {str(e)}")

def main():
    if BOT_TOKEN == "APNA_BOT_TOKEN_YAHAN_DALO":
        print("❌ Pehle BOT_TOKEN daalo file me!")
        return

    app = Application.builder().token(BOT_TOKEN).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(CommandHandler("settitle", set_title))
    app.add_handler(CommandHandler("say", say_message))
    
    print("Bot chal raha hai... Ctrl+C se band karo")
    app.run_polling()

if __name__ == "__main__":
    main()
