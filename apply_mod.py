import os
import re

BASE_DIR = "TMessagesProj/src/main/java/org/telegram"

def patch_file(rel_path, pattern, replacement):
    full_path = os.path.join(BASE_DIR, rel_path)
    if not os.path.exists(full_path):
        print(f"[!] Файл не найден: {full_path}")
        return
    with open(full_path, "r", encoding="utf-8") as f:
        content = f.read()
    
    new_content, count = re.subn(pattern, replacement, content, count=1)
    if count > 0:
        with open(full_path, "w", encoding="utf-8") as f:
            f.write(new_content)
        print(f"[+] Успешно пропатчен: {rel_path}")
    else:
        print(f"[-] Сигнатура не найдена в: {rel_path}")

# 1. Создаем GhostConfig.java
ghost_config_path = os.path.join(BASE_DIR, "messenger/GhostConfig.java")
ghost_config_code = """package org.telegram.messenger;

public class GhostConfig {
    public static boolean dontReadMessages = true;
    public static boolean dontReadStories = true;
    public static boolean hideTyping = true;
    public static boolean hideOnline = true;
    public static boolean forceMaxFps = true;
    public static boolean springTransitions = true;
}
"""
os.makedirs(os.path.dirname(ghost_config_path), exist_ok=True)
with open(ghost_config_path, "w", encoding="utf-8") as f:
    f.write(ghost_config_code)
print("[+] Создан файл конфигурации: messenger/GhostConfig.java")

# 2. Скрытие прочтения сообщений (MessagesController.java)
patch_file(
    "messenger/MessagesController.java",
    r"(private void completeReadTask\(ReadTask task\)\s*\{)",
    r"\1\n        if (GhostConfig.dontReadMessages && task.dialogId != getUserConfig().getClientUserId()) { return; }"
)

# 3. Скрытие прослушивания голосовых и кружочков (MessagesController.java)
patch_file(
    "messenger/MessagesController.java",
    r"(public void markMessageContentAsRead\([^{]+\{)",
    r"\1\n        if (GhostConfig.dontReadMessages) { return; }"
)

# 4. Скрытие статуса «печатает...» / «записывает аудио...» (MessagesController.java)
patch_file(
    "messenger/MessagesController.java",
    r"(public void sendTyping\([^{]+\{)",
    r"\1\n        if (GhostConfig.hideTyping) { return; }"
)

# 5. Вечный оффлайн (MessagesController.java)
patch_file(
    "messenger/MessagesController.java",
    r"req\.offline\s*=\s*false;",
    r"req.offline = GhostConfig.hideOnline ? true : false;"
)

# 6. Анонимный просмотр Stories (StoriesController.java)
patch_file(
    "ui/Stories/StoriesController.java",
    r"(public void markStoryAsRead\(long dialogId,\s*TL_stories\.StoryItem storyItem\)\s*\{)",
    r"\1\n        if (GhostConfig.dontReadStories) { return; }"
)

# 7. Увеличение кэша списков для плавного скролла (RecyclerListView.java)
patch_file(
    "ui/Components/RecyclerListView.java",
    r"(public RecyclerListView\(Context context\)\s*\{[\s\S]*?super\(context\);)",
    r"\1\n        setItemViewCacheSize(15);\n        setDrawingCacheEnabled(true);\n        setDrawingCacheQuality(android.view.View.DRAWING_CACHE_QUALITY_HIGH);"
)

# 8. Плавные пружинные переходы 350 мс (ActionBarLayout.java)
patch_file(
    "ui/ActionBar/ActionBarLayout.java",
    r"animatorSet\.setDuration\(220\);",
    r"animatorSet.setDuration(org.telegram.messenger.GhostConfig.springTransitions ? 350 : 220);"
)

