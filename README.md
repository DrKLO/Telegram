## Telegram messenger for Android

[Telegram](http://telegram.org) is a messaging app with a focus on speed and security. It’s superfast, simple and free.
This repo contains the official source code for [Telegram App for Android](https://play.google.com/store/apps/details?id=org.telegram.messenger).

##Creating your Telegram Application

We welcome all developers to use our API and source code to create applications on our platform.
There are several things we require from **all developers** for the moment.

1. [**Obtain your own api_id**](https://core.telegram.org/api/obtaining_api_id) for your application.
2. Please **do not** use the name Telegram for your app — or make sure your users understand that it is unofficial.
3. Kindly **do not** use our standard logo (white paper plane in a blue circle) as your app's logo.
3. Please study our [**security guidelines**](https://core.telegram.org/mtproto/security_guidelines) and take good care of your users' data and privacy.
4. Please remember to publish **your** code too in order to comply with the licences.

### API, Protocol documentation

Telegram API manuals: http://core.telegram.org/api

MTproto protocol manuals: http://core.telegram.org/mtproto

### Usage

**Beware of using the dev branch and uploading it to any markets, in many cases it not will work as expected**.

First of all, take a look at **src/main/java/org/telegram/messenger/BuildVars.java** and fill it with correct values.
Import the root folder into your IDE (tested on Android Studio), then run project.

### Localization

We moved all translations to https://www.transifex.com/projects/p/telegram/. Please use it.

### Reporting bugs 

When reporting bugs, please fill the following form in order to understand your issue and fix it as fast as possible:

* **Summary:** How would you describe the bug in less than 60 characters? It should quickly and uniquely identify a bug report as well as explain the problem, not your suggested solution. Good: "Canceling a File Copy dialog crashes File Manager" Bad: "Software crashes" Bad: "Browser should work with my web site"

* **Component:** In which sub-part of the software does it exist? This field is a requirement to submit any bug report. Click the word "Component" to see a description of each component. If none seems appropriate, highlight the "General" component.

* **OS/Version:** On which operating system (OS) did you find it? (e.g. Android, iOS, Linux, Windows XP, Mac OS X.) Example: "If you know the bug happens on more than one type of operating system, choose "All". If your OS isn't listed, choose Other".

* **Description:** The details of your problem report, including:
  * _Overview:_ This is a larger detailed restatement of the summary. An example would be: "Drag-selecting any page crashes Mac builds in the NSGetFactory function".
  * _Version:_ To find this go to the "About" menu. It should look something like this: "_Version 0.8.24_".

* **Steps to Reproduce:** Minimized, easy-to-follow steps that will trigger the bug. If they're necessary, make sure to include any special setup steps. A good example of this would look like the following:

  1. View any web page. (I used the default sample page, http://www.google.com/).

  2. Drag-select the page. Specifically, while holding down the mouse button, drag the mouse pointer downwards from any point in the browser's content region to the bottom of the browser's content region.

* **Actual Results:** What the application did after performing the above steps. An example would be: _The application crashed_

* **Expected Results:** What the application should have done, were the bug not present. An example would be: _The window should scroll downwards. Scrolled content should be selected. Or, at least, the application should not crash._
