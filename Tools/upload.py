import asyncio
import sys
import os
from telegram import Bot
from telegram.error import TelegramError
from telegram.request import HTTPXRequest
from urllib.parse import urljoin

async def upload_file(bot_token, chat_id, file_path, bot_endpoint='https://api.telegram.org/', caption=None):
    if not os.path.isfile(file_path):
        raise FileNotFoundError(f"File not found: {file_path}")

    request = HTTPXRequest(
        connect_timeout=120.0,
        read_timeout=120.0,
        write_timeout=120.0,
        pool_timeout=120.0
    )

    bot = Bot(token=bot_token, base_url=urljoin(bot_endpoint, 'bot'), request=request)

    try:
        await bot.initialize()
        with open(file_path, 'rb') as file:
            await bot.send_document(chat_id=chat_id, document=file, caption=caption, parse_mode="Markdown")
        print("✅ File uploaded successfully.")
    except TelegramError as e:
        print(f"❌ Failed to send file: {e}")
        raise

if __name__ == "__main__":
    if len(sys.argv) < 5:
        print("Usage: python upload.py <BOT_TOKEN> <CHAT_ID> <FILE_PATH> [BOT_ENDPOINT] [<CAPTION>]")
        sys.exit(1)

    token = sys.argv[1]
    chat_id = sys.argv[2]
    file_path = sys.argv[3]
    bot_endpoint = sys.argv[4] if len(sys.argv) > 4 else None
    caption = sys.argv[5] if len(sys.argv) > 5 else None

    asyncio.run(upload_file(token, chat_id, file_path, bot_endpoint, caption))
