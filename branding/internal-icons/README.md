# MVGram internal icon artwork

The SVG files in this directory are original, editable category glyphs. `branding/generate_icons.py` converts them into same-named Android VectorDrawable resources (`TMessagesProj/src/main/res/drawable/mvgram_*.xml`). The primary app logo and its launcher/splash/notification exports are sourced separately from `mvgram-icon.svg` and `mvgram-foreground.svg`.

| Resource stem | Category |
|---|---|
| `mvgram_chats` | Chats |
| `mvgram_messaging` | Message composition |
| `mvgram_contacts` | Contacts |
| `mvgram_calls` | Calls |
| `mvgram_groups` | Groups |
| `mvgram_settings` | Settings |
| `mvgram_profile` | Profile |
| `mvgram_search` | Search |
| `mvgram_navigation` | Location/navigation |
| `mvgram_attachments` | Attachments |
| `mvgram_camera` | Camera |
| `mvgram_gallery` | Gallery/media |
| `mvgram_files` | Files |
| `mvgram_notifications` | Notifications |
| `mvgram_music` | Audio/music |
| `mvgram_video` | Video |
| `mvgram_archive` | Archive |
| `mvgram_privacy` | Privacy/security |

The existing UI's established theme-tinted drawable IDs remain intact; the new color-category resources are separately namespaced so they can be adopted without breaking current light/dark tint behavior.
