# Changes

* 8.85.49
    * app use data from BuildVars everywhere
    * login flow looks now pretty legit for me
* 8.85.48
    * bit changed login part
    * .gitignore fix
    * you can enable WAL mode for DB (using debug menu in the client)
* 8.85.47
    * hide deletion marks fix
    * added persian translation, thanks to OxMohsen
    * forwarded message real time fix
    * seconds added to message history
* 8.85.46
    * "clear db" is disabled cause we need to rework vanilla code (we don't use journal/wal)
    * "delete downloaded file" also works for videos & music
    * history message now use default font size from the app
        * default for dates, `-2` for the texts
    * you can change sticker size now: x0.25, x0.5, x1 & x2
    * you can enable real forwarded message time
    * you can hide stickers in chats
        * alpha version, they are still in cache, still takes place in ui (just a white element)
        * but they are not rendered :)
* 8.85.45
    * we compress libs, so APK should be smaller, but installation longer.
        * "Gplay optimisations failed"? well we're not on gplay.
    * graherium speedup now separated for upload & download
        * cause some folks are facing issues with uploads)
    * arabic translations are added, thanks to RHineix
    * graherium connection speed override is added. You can keep it `auto`, setup `slow` or be `high` 😎
        * with this you can send files realllly faster or use network less than usually being on wifi
        * it doesn't override your autodownloads presets, so be smart
    * Privacy "don't use Apple" is added and enabled by default
        * cause telegram app use itunes to pick the covers. If you need it - just disable the feature
* 8.85.44
    * 40, 41, 42, 43, 44 - due new flow debugging
    * release
    * package name now `com.evildayz.code.telegraher2` so you can install it in parallel with 8.7.x version and move
      your accs
    * apk also now have our own signature to avoid phishing (someone mod an app and say "it's new graher 9.0.1")
* 8.85.39
    * well we disable journal_mode
        * i don't see any good moments on a telegram app except files which growing up. There are no rollbacks, app just
          select/insert/delete the data. So now it's off. Lets see how it will work on mass tests.
    * SM now show internal accountIds
    * before `Reset session manager & device spoofing` now `SYNC session manager & RESET device spoofing` in a debug
      menu
    * added Disable spoilers
* 8.85.38
    * you can choose XXX as your message notification icon also
    * added to debug menu wipe ALL message history for actual account
    * added to th menu enable/disable message history
* 8.85.37
    * removed some log shitting when some folks edit messages too fast
    * vanilla sticker flow trigger which enable vanilla flow and disable our custom overlays flow
* 8.85.36
    * crash fix for `getMaxInternalAccountId()`
* 8.85.35
    * improved multiacc (the C part) with the vector, that fix remove crashes when you have 40+ accounts
    * gradle libs updates
    * settings to hide numbers on a left panel
    * session manager updates (new code for IDs), also i recommend you to do "reset sesssion manager & device spoofing"
      from the debug menu
    * added "kill that app" in a settings menu, see the effect after simple reload (no need to kill, simple re-render)
    * you can delete OFFLINE sessions in a session manager (they are offline when you disabled them AND restarted the
      app)
* 8.85.21
    * copy phone & username on long click from session manager (from the details)
* 8.85.20
    * fixed sticker overlays
    * graherium: star for everyone, noone or just for peperemium folks
* 8.85.19
    * medias with timers are fixed, so send "ok, i read that" click on TTL button after the media is fully loaded on
      your client. It will wipe it for the sender (but not for you)
    * fixed issue with notification icon
    * fixed online/offline account indication for session manager
    * graherium
        * animated avatars for everyone or disable it also for everyone
        * enable/disable sticker overlays
    * when you logout it wipe account folder (except for account zero = the 1st one)
* 8.85.15
    * fixed issues related to deletion marks (missing aliases for queries which crashed sqlite3)
        * fixed issue w/ not loading dialogs
        * fixed issues w/ missing pinned messages
* 8.85.12
    * updated libs in gradle
    * removed google vision, no more google so
    * deletion marks on new architecture
        * when someone delete FULL story in GREEN chats, no marks applied, but messages are saved. The only difference
          visually with the old
        * no more fantom deletions, work well on multiacc
    * Graherium: they have stars, we have ballz
        * speedup upload & download
* 8.85.11
    * added to menu delete downloaded file
      files which you download can be deleted directly from message w/o going to "download menu".
      UI normally udpates well, but if you wiped your file and UI still show it's here, just re-enter the chat or
      channel.
    * session manager now track well name/surname & username changes
* 8.85.10
    * fixed issue on 1st connection
* 8.85.9
    * fixed crash on empty app
* 8.85.8
    * device spoofing stuff
        * main th menu
            * click on default device params to change them
            * you can also reset them to real phone values
                * these params doesn't affect existing accounts
        * session manager
            * single click enable/disable account
                * long press will open a menu
                * single click on name enable/disable
                * single click on device properties will edit them for current account
                * long press will edit the default params (same is single click on params in main telegraher menu)
        * sure, you need to kill the app to apply changes
        * if you had another betas with device spoofing, enable ALL accounts in session manager, go to debug menu and
          reset
          ThAccounts and ThDeviceSpoofing and restart the app
        * we use 8.8.6 as a version and all apps have codeversion 2 (store bundle)
* ~~8.85.7~~
* 8.85.6
    * session manager
        * simple ON/OFF session manager in TH menu. So apply the settings you need to kill the app (via the button in
          the menu or manually).
        * if you disable actual account, then you will get on start empty chatlist until you swap the account.
* 8.85.5
    * new message history
    * reworked DC information on profile page
    * added copy-into-clipboard for ID, DC & messages from history
    * tabs on forward now can be enabled or disabled via TH menu
    * removed option "3+" for multiacc cause we have multiacc
* 8.85.4
    * unlimited multiacc from nekox w/o chinese bloatware
    * software have package with `_beta` on it, for testing purposes only
    * porting features from Telegraher on 8.8.5 base
    * SharedPrerefences use async `apply()` instead of sync `commit()`
    * fixed glitch with name/number field at left menu
    * multiacc fix
    * removed permissions `GET_ACCOUNTS` & `MANAGE_ACCOUNTS`
* 8.74.6
    * fixed version and codeversion for smooth updates
    * probably fixed TG vibro
      bug [9111e6](https://github.com/nikitasius/Telegraher/commit/9111e618b734bc9012c5ab4421bc7ed28e0950d7) (
      or i not enough drunk to understand their flow)
    * "Chudmin" (3511604) & "iMiKED" (1017942) are pidormoders from 4pda (tossing salads modership)
    * added "Disable vibro" option to disable vibrations globally
    * added "disable start beep" and "disable end beep", thats to disable beeps when you calling
      someone via voip
        * thats pretty annoying at least the endone
* 8.74.5
    * still working on armeabi-v7 but in theory fixed crash on multiaccs
        * the vanilla shitcode loop which makes kind of pause was increased x10 times in previous
          update
* 8.74.4
    * removed mltoolkit from google, 0 tracker app
    * max account reduced from 128 to 80 as a temp fix for crash on armeabi-v7 devices
        * need to change multiacc code cause too much old vanilla is here
* 8.74.3
    * another custom icon
    * spellerror in russian translation
    * proximity sensor modes (requires app restart)
        * default
        * disable start/stop
        * disable blackscreen also
    * app notification icon selector
* 8.74.2
    * yes! our own versioning, 8.74.2 mean "made from vanilla 8.7.4, release 2"
        * the API use vanilla 8.7.4
        * it solve update & reinstall issues
    * fixed bug with non working UI elements
    * "delete also for.." is checked by default for messages, chats and the history wipe
    * when you an admin and you wanna delete your own messages as an admin, you need to click 3
      times on a checkbox "delete all from your_nickname" to activate it
        * it's for security reasons, cause some folks wiped their own messages by mistakes
    * new notification icon, thanks Wolfsschanze44 !
        * the icon setup for new notifications, while background process still keep [xxx]-one
* noshit_8.7.4_release1
    * the vanilla release as is (nearly)
    * disabled `needSendDebugLog` (from 8.7.0) for voice calls
    * maximum account updated from 32 to 128
    * removed custom fonts (need to do that another way)
    * bit beta so feel free to feedback in our chat
        * custom icons & features in next release2 update!
* noshit_8.6.2_release8
    * remote deletions fix (medias w/ timers), it's fixed. Open it twice to wipe for sender.
    * added tabs on forward
    * next channel on swap disabled by default
    * added custom support for fonts. Actually REGULAR affect most of UI except the messages. Need
      to dig deeper to find how to change it for messages well.
    * fixed a crhash when admin delete in chat messages posted by a channel
* noshit_8.6.2_release7
    * `isoparser` video fix (rollback from new 1.1.22 to old 1.0.6)
* noshit_8.6.2_release6
    * > To let you receive push notifications w/o GApps
        * thank you, sir ^^
* noshit_8.6.2_release5
    * google services are removed
    * TH menu hardcoded values replaced with the values from xml
        * added russian language, i will add french too. Feel free to commit/add via issues new
          ones, i will create special issue for this
    * added osmand maps (from TG FOSS)
    * notifications background service fix from TG FOSS too
    * fixed bug with "The Void". Now is you choose "The Void" for our menu it will disappear
      forever..
    * updated from packets from gradle, hope it will work fine :)
* noshit_8.6.2_release4
    * changed position for th menu (can be everywhere or disappear in the void and never bring back)
    * added support upto 8k videos (h264 only, yep, no h265 cause tg doesn't support it from the
      box)
    * fixed device spoofing issue (thats the user who reported it, can't find you..)
    * added HD gifs. Regarding the code desktop consider as gifs all videos w/o sounds upto 1080p,
      while android app upto 720p.
    * you can change default camera to main camera for round videos
* noshit_8.6.2_release3
    * fixed device spoofing issue
    * you can spoof now active sessions too
* noshit_8.6.2_release2
    * device spoofing
        * Well folks here is it. The device spoofing. You can setup the brand, the model and the Sdk
          number (os). These values will be used for all NEW connections. Once you registered an
          account it will be setup with the data you used for. To do this you need to access our
          menu and override params. Them you click the RED button "kill the app" cause app need to
          be STARTED to get new params to init. Use it wisely. For example:
            * 9 accounts, 3 different virtual devices = 3 "real" devices :)
            * By default it will use the vanilla data.
    * fixed SHORTCUT_SHARE
    * added `IP_STRATEGY_BYTE` byte
    * corrected `files` folder (when TG fuckups to create the one)
    * Fork don't create and don't use system accounts anymore
        * but remains fully compatible with the contacts made in vanilla TG
    * You can setup size and round bitrate for round videos
        * round videos with the size !=x1 are doesn't considered as round btw
    * new C++ code for accounts and new account system.
        * actually upto 32, by default it's disabled, you have to enable "3+"
        * if you had >3 accounts you need to enable this option and restart the app.
    * new code in LocaleController to replace some CLOUD values by our local ones
    * button to "Kill the app"
    * (!) our menu by default NOW in Settings/Data and Storage/Storage Usage section
        * to bring it to where it's usually you need to checkbox "* Show Telegraher menu"
    * added vanilla fingerprint from 8.6.2 store, it's the same btw as before.
* noshit_8.6.2_release1
    * update to vanilla TG 8.6.2
    * video compression and quality as before
        * cause TG reduced max bitrate and added fps limiter, so i removed it
    * renamed some strings from Telegram to Telegraher
    * changed "promo" texts which user see on start
* noshit_8.6.1_release5
    * added full shadowban mode, read
      details [here](https://github.com/nikitasius/Telegraher/commit/bab6fe99ec1897532b9136f9391983bafd0c921b)
    * "on create a 2nd+ account" sync contacts checkbox is disabled, but you can always sync them
      via vanilla menu
    * fixed but #22 which caused white squared sometimes in the channels and rarely in groups.
* noshit_8.6.1_release4
    * th settings, show/hide
        * numeric id, datacenter, shadowban, deletion marks
    * fixed numeric id display cases
    * fixed issue #18
* noshit_8.6.1_release3
    * removed "test correction for getMinTabletSide"
* noshit_8.6.1_release2
    * added HD voices (48kHz)
        * added slow badman voice, just for fun :)
    * test correction for getMinTabletSide to see if it will solve waste space issue on some tablets
        * in my case (emulator phone/tablets) and phone all is fine
* noshit_8.6.1_release1
    * TG vanilla 8.6.0 & 8.6.1
    * enable/disable link previews for classic chats
    * invite links are fixed (for github now)
    * debug menu unlocked a bit
* noshit_8.5.4_release4
    * admins now can delete all own messages in group chat
    * KABOOM moved on Storage tab to be accessed from Android app menu w/o being logged in a client
    * replaced vanilla TG links with github fork links
    * bring back app install permission
        * cause folks had issues to install the APKs from UI on Android10 and/or MIUI
    * fixed space waste for large screen tablets in landscape mode
    * chat font now between 6 and 72
    * removed "splash" on startup
    * fixed bug in UI with double "save to gallery" in a menu
* noshit_8.5.4_release3
    * added Kaboom in Settings/Data, it will wipe app's data (including accs!) and it require a
      confirmation
    * fixed copy ID issue #10
    * fixed theme issue for left side menu #11
    * added GSON dependency (we use gson here for JSON)
    * shadowban features
        * shadowban settings works for messages in group chats
            * if you shadowban (SB) an user you will not see his messages
            * if you SB a group you will not see anonymous admin messages
            * if you SB a channel, you will not see messages in chat made by the users who user
              channels to hide their IDs nor messages in these channels (but you will see automatic
              reposts in channel linked with the group, this moment i will fix later for better
              SB:) )

* noshit_8.5.4_release2
    * this and all next build are build using CI/CD from github
        * it mean that github automatically build the app and not me :)
    * bring back "New Channel" into main menu
    * left slide menu doesn't display phone number under the account anymore
        * it display the username (if exists) or empty text
        * phone number now displayed for each account in spoiler menu and can be hidden
            * handy if you taking screenshots/filming your screen
    * every profile tab now display the ID (users/groups/channels/bots etc)
        * you can copy ID on click

* noshit_8.5.4_release1
    * tg official bugfixes
    * removed google stats (app measurement)

* noshit_8.5.2_release1
    * tg official bugfixes
        * snow/blur are still fuckedup
* noshit_8.5.1_release1
    * bugfixes they are added to fix some of the crap they did (but still no blur and snow)
        * due blur & snow are fucked up, i disabled them by default. If you had enabled please
          disable to avoid cpu usage
            * blur via settings/chatmenu, snow via debugmenu
    * forward and delete limits are raised from 100 upto 1024
        * technically forward have huge native limit, so 200+ forward via API w/o issues
        * delete have native limit 100 per request, so i slice your array with ids into chunks with
          100 ids
            * technically there are NO limit, BUT TG api have rate limits, so if you wanna delete
              8000 messages, it's 80 chunks = 80 requests, TG will probably ban you for 5-10
              minites (no more deletions during this time due you're abused API)
            * so it's a nice feature, but remember about TG's api rates
* noshit_8.5.0_release1
    * updated to TG 8.5.0
    * fixed issue #7
    * fixed issue with ffmpeg
      lib ([301601](https://github.com/nikitasius/Telegraher/commit/3016016c51ce8ce530bd7a9566c53e9fbb68ada2)
    * blur & snow doesn't work cause TG devs are fucked it up
        * if you had blur/snow enabled please disable it to avoid CPU usage
* noshit_8.4.4_release2
    * added `delete` mark
        * when someone wiped messages OR history you will see it, just need to close/open an actual
          chat
        * `deleted` will me marked on the place of `edited`
        * remote deletions via GCM pushes should to work too (i have no gapps here, but some folks
          have)
        * if chat have a timer there are no `deleted` flag, i will add it later due we know that it
          will be deleted once read (cause there are a timer :D )
    * new internal variable `DUROV_RELOGIN`=`1` and a table
        * here we store level of our changes, due we added new column `isdel` into `messages_v2`
          table and made a new table `telegraher_init`
        * i used vanilla code to apply DB update (same what TG use when you see "hi, tg updating
          database etc")
    * fixed an issue #6
        * when we wiped remote chats they aren't wiped so not they are wiped
* noshit_8.4.4_release1
    * keep using original TG fingerprint from 8.4.4 (same as 8.4.3)
    * snowflakes added back into menu
    * blur added back into menu
    * disabled APK managing by TG
        * WTF what they did smoked, but messengers will NOT install the APKs
    * you can enable/change/disable double tab reactions (=quick reactions)
        * vanilla client offer only change them :)
* noshit_8.4.3_release2
    * we use now fingerprint, package name and referer (who installed us, i.e. Google Play) from a
      vanilla version
    * APP lost few permission due it not need it anymore
        * app do not check number, sim state or is number is the actual you use on
          regisration/login, due we just say "yes"/`true`
    * due app is from github official "check update" disabled, so app will not ask TG servers if
      there are new one.
    * api keys are `4`/`014b35b6184100b085b0d0572f9b5103` due gplay/store/web versions are use them
* noshit_8.4.3_release1
    * update to 8.4.3
    * disabled access to all reactions
        * since TG again moderate/censor your private (non-green) chats you can't use them anymore,
          because server simply ignores it and reject w/ an error.
* noshit_8.4.2_release3
    * now the ads is **loaded**, views are **counted** but **the ads isn't displayed**
* noshit_8.4.2_release2
    * fixed issue #4
    * fixed save menu buttons
    * disabled auto reaction on doubletap
    * fixed `edit_hide`
    * all **official** reaction are available for private messages
        * doesn't work in groups/channels due TG servers are using whitelists
* noshit_8.4.2_release1
    * use Telegram 8.4.2 code base now
    * added video controls to GIFs
* noshit_8.3.1_release2
    * Fixed issues #1 and #2