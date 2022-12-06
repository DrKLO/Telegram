navigator.clipboard.__proto__.readText = function() {
    return new Promise(function(resolve, reject) {
        resolve(TelegramWebviewProxy.getClipboardText());
    });
};