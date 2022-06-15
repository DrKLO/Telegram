> Yeah, well... I'm gonna go build my own theme park, with blackjack and hookers.

(c) Bender Bending Rodríguez

![Telegraher](/TMessagesProj/src/main/res/mipmap-xhdpi/ic_launcher_sa.png)

## Telegraher

* **No one gets to decide** what i run on my device
* **No one gets to decide** where i run my app
* **No one gets to decide** what must be deleted

This is my device so i control it 😎

This app have nothing with privacy, it's remotely controlled. It's pissing me off, so i changed
that.

I took an original Telegram client from ["official" repo](https://github.com/DrKLO/Telegram) and
made my own theme park with blackjack and hoookers.

Special thanks:

* my wife and my dog, love them 🍑
* mr Rodríguez for the inspiration
* some anonymous folks over the telegram for the great ideas (can't share their names here, cause
  they are anonymous)
* "Telegram🦄magic🦄team" for their "magic🦄updates" including ~~private~~ chats and "magic🦄ads"

### WTF?! / is it legit?

Follow the ~~white rabbit~~ the git flow:

* i took and forked the original client
* i cloned the latest `master` branch (with 8.3.1 patch) into `master_8.3.1`
* i made another branch `noshit_8.3.1` from `master_8.3.1`, it contain changes

It gives us `telegram` -> `master` -> `master_8.3.1` -> `noshit_8.3.1`

So **all the code changes** are in `noshit_8.3.1` (when this project started, actual version is
different)

### Detailed summary / noshit_8.7.4

* DISABLED ADS
    * YES!!1
    * no more sponsored messages, we still download them but do not display
        * we still count views for this ADS to hide our behavior of the app "who don't earn money
          for TG"
* EVERY element have `save to downloads`/`save to gallery`
    * ~~messages are elements too, you still can click but cannot save them into downloads~~ fixed
        * ~~however i do not recomment this~~
    * use it wisely
* ~~6 accounts instead of 3~~ ~~32~~ 128 accounts
    * to enbale more than 3 you need to activate "3+" option
    * ~~client support upto 6 accounts~~
        * ~~on the 1st run when you type phone number and continue it can throw an API error~~
            * ~~error was only on the version with 16 accounts~~
                * ~~to get more accounts w/o having problems with TGs api need to change the App's
                  flow~~
            * in case of an error close the error by clicking OK and let it relax for 5 minutes
            * continue and log in
* DISABLED REMOTE DELETIONS
    * NO more deletions via GCM PUSHES (chats and messages), WTF 💩
    * NO more deletions in groups and channels
        * i hate the channels that wipes the content
    * NO more remote deletions in private chats
    * NO more remote deletions in secret chats
        * self destruction timer doesn't work but other folks see that you're opened and deleted it
    * NO more "history wipe" or chat deletions
        * history/messages remains where they are
        * chat becomes inactive if other folks are deleted it for them and/or for you
* FULL ACCESS in "restrict saving content" chats
    * screenshots, gif imports, media saving
    * DO NOT save GIFS via saving gifs
        * just click on them and choose "save to gallery"
        * after this share this media no matter where as a video file **WITHOUT** sound
        * it will become a GIF in your collection
    * you **can't forward** message as is due it use **telegram API** and their server will block
      it, so save/copy
        * save GIF / forward message ARE using telegrams API, while SHARING or DOWNLOADING not
* FULL ACCESS in secret chats (GREEN ONES!)
    * you can download any medias and documents
    * you can take screenshots or record your screen
        * WTF apple can do that, we can do it too!
* GIFs have controls
    * you can start/stop GIFs or navigate on a timeline
* KEEP CACHED chats
    * cached chats are always with you even if you're BANNED (
        * once banned you will get a message about this
        * you can navigate in chat where you was banned using your cache
        * to remove cached chat you must delete it ("leave that chat")
    * even when when you restart your app it will load cached chats
* HISTORY in private chats
    * message separated inside by RFC1123 timestamp field
    * when someone will send you a PM and will change it, while you in chat you will see `edited` as
      usual
        * to see changes you need to **close/open** this **chat**
            * this will be probably fixed in future (display in real time changes)
    * this also affect bots, so when your bots edit their messages, you will see old/new versions
* NO MORE timer
    * when someone send you a media with a timer in a secret (green) chat this message will be
      deleted on a device who sent it once you open it
    * when someone send you a media with a timer in a private (NON-green) chat this message will be
      deleted on a device who sent it once OR twice you open it
        * EASY AND SHORT: open photo twice and it will WIPE it from a device who sent it ;-)
        * LONG AND DETAILED: due it will send an event only if full file is downloaded by your
          client when you open this (guess due file size). In most of cases client download media
          file during 1st open, so you need to open it again (just tap on it)
        * sometimes full media downloaded when preview generated, in such case open once file and it
          will wipe it from a user who sent it
* ~~SNOW & BLUR~~
    * ~~they are added back into debug menu (when you press version 2 times)~~
    * since 8.5.0 doesn't work cause TG devs are fuckedup this part (rendering)
        * if you had enable snow/blur please disable them temporary in menu to avoid and CPU usage
* SHADOWBAN feature! block anyone just for you! Shadowban settings works for messages in group chats
    * if you shadowban (SB) an user you will not see his messages
    * if you SB a group you will not see anonymous admin messages
    * if you SB a channel, you will not see messages in chat made by the users who user channels to
      hide their IDs nor messages in these channels (but you will see automatic reposts in channel
      linked with the group, this moment i will fix later for better SB:) )
* You can DISABLE doubletap (=quick) reactions
    * open quick reactions
    * select already selected reaction one more time
    * checkbox will disappear, double tap reactions are disabled.
* NO MORE `edit_hide`
    * telegram can send you messages with `edit_hide==true` to hide what this message has been
      edited
    * now if message contains signs of editions it will marked
* DISABLED emulator detections
    * idk why the client use this, but i disabled it
    * if i want to run it on emulator telegram no need to know it
* LEGIT Phone
    * for the app and TG you have simcard, sim is online, phone is actual
        * app don't check it anymore, it's disabled
* Hi, i'm Vanilla 💅
    * we use actual sha256 fingerprint from vanilla version 8.4.4
    * we say we're `org.telegram.messenger`
    * we say Google installed us `com.android.vending`
* APP name changed & APP icon changed & APP package changed
    * of you want to run using old name just rollback those commits and build the app
* APPs api & hash are changed for legit TG client
    * actually they are all on `4`/`014b35b6184100b085b0d0572f9b5103`
* Left-side menu changed, not it's display the username (if exists) and the list of accounts have
  phonenumber added here
* KABOOM, new features in Storage management, which allow you to wipe the app's data (include the
  accs, confirmation is required) from the Srttings/Data/Storage or from Android's app menu/Storage
  management
* Device spoofing
* Our menu NOW near the Kaboom button (but you can bring it back!)
* Admins now can delete all own messages in a group chat
* Bring it back and there are no vanilla links. ~~APP do not manage APKs anymore~~
    * because some folks have issues to install APKs from TG fork on Android10 and/or MIUI
        * ~~before it have a code and required install pkg permissions~~
            * ~~thats why w/o permission TG displayed an error that there are no tool in system to
              install APKs~~

### Build

It's very simple

* download the repo `git clone https://github.com/nikitasius/Telegraher.git`
* build it
    * you can use official guide `https://core.telegram.org/reproducible-builds`
        * open the folder with the repo
        * git checkout lastest **noshit** branch (`git checkout remotes/origin/noshit_8.7.4` for
          example)
        * run `docker build -t telegram-build .`
        * run `docker run --rm -v "$PWD":/home/source telegram-build`
            * and ~1h later you will get 9 different builds (under deb11 with 12 cores and 16Gb Ram
              on NVMe)
    * you can download Android studio `https://developer.android.com/studio`
        * add a bit more ram (i use 4096M for the studio)
        * open the project
        * let gradle it work
        * when it's done go to Build -> Select build variant
            * choose build you need
                * afatDebug - it's debug builds
                * ***Release - release builds

### APKs & sha256

* **sdk23** mean for android 6+, the other are working from 4.1+
    * so if you have android 6 or higher, you should download **sdk23** version
* arm64-v8a (new devices)
    * `x`  [Telegraher.8.74.2.arm64_v8a.apk](https://github.com/nikitasius/Telegraher/releases/download/noshit_8.74.2_arm64_v8a/Telegraher.8.74.2.arm64_v8a.apk)
    * `x`  [Telegraher.8.74.2.arm64_v8a_sdk23.apk](https://github.com/nikitasius/Telegraher/releases/download/noshit_8.74.2_arm64_v8a/Telegraher.8.74.2.arm64_v8a_sdk23.apk)
* armeabi-v7a (old devices)
    * `x`  [Telegraher.8.74.2.armeabi_v7a.apk](https://github.com/nikitasius/Telegraher/releases/download/noshit_8.74.2_armeabi_v7a/Telegraher.8.74.2.armeabi_v7a.apk)
    * `x`  [Telegraher.8.74.2.armeabi_v7a_sdk23.apk](https://github.com/nikitasius/Telegraher/releases/download/noshit_8.74.2_armeabi_v7a/Telegraher.8.74.2.armeabi_v7a_sdk23.apk)
* PC x86, 32 bits (for an emulator for example)
    * `x`  [Telegraher.8.74.2.x86.apk](https://github.com/nikitasius/Telegraher/releases/download/noshit_8.74.2_x86/Telegraher.8.74.2.x86.apk)
    * `x`  [Telegraher.8.74.2.x86_sdk23.apk](https://github.com/nikitasius/Telegraher/releases/download/noshit_8.74.2_x86/Telegraher.8.74.2.x86_sdk23.apk)
* PC x86, 64 bits (for 64 bits CPU)
    * `x`  [Telegraher.8.74.2.x86_64.apk](https://github.com/nikitasius/Telegraher/releases/download/noshit_8.74.2_x86_64/Telegraher.8.74.2.x86_64.apk)
    * `x`  [Telegraher.8.74.2.x86_64_sdk23.apk](https://github.com/nikitasius/Telegraher/releases/download/noshit_8.74.2_x86_64/Telegraher.8.74.2.x86_64_sdk23.apk)

### Issues/Wishlist

Feel free to use the "issues section". I'm not an Android programmer, i'm a Java developper.
Probably it's a good thing 😃

### Changes

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

### Already installed this version?

Android will offer you to reinstall, simply accept this option and it the app will be reinstalled
and it will keep all the settings/accounts.

### Code mirrors

* Github: https://github.com/nikitasius/Telegraher
* Gitlab: https://gitlab.com/nikitasius/Telegraher
    * autosync from github
* HTTPS: https://git.evildayz.com/Telegraher/
    * manually sync (add a script later 😀)
    * `releases` w/ actual releases and cloned `Telegraher` & `Telegraher.git` in `.tar.gz`

### Coffee

* Here is my [PayPal](https://paypal.me/nikitasius) `https://paypal.me/nikitasius`
* Here is
  my [BTC](bitcoin:bc1q5egmj6vjejmsu4lu3nmdshvx6p0kcajlw5u9a0?message=github_telegraher) `bc1q5egmj6vjejmsu4lu3nmdshvx6p0kcajlw5u9a0`
* Here is
  my [Yoomoney](https://yoomoney.ru/to/410015481871381) `https://yoomoney.ru/to/410015481871381`

> In fact, forget the park!