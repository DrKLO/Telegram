// polyfill for web share api
if (window.navigator && !window.navigator.canShare) {
    window.navigator.canShare = () => true;
}
if (window.navigator && !window.navigator.share) {
    window.navigator.share = data => {
        if (window.navigator.__share__receive) {
            return new Promise((_, reject) => reject(new DOMException("Other sharing operations are in progress", "InvalidStateError")))
        }
        if (typeof data !== 'object' || !data.url && !data.title && !data.text && !data.files) {
            return new Promise((_, reject) => reject(new DOMException("share(...) receives only object with either url, title, text or files", "TypeError")))
        }
        if (!window.TelegramWebview) {
            return new Promise((_, reject) => reject(new DOMException("Must be handling a user gesture to perform a share", "NotAllowedError")))
        }
        const { url, title, text } = data
        const file = (Array.isArray(data.file) ? data.file[0] : data.file) || data.files && data.files[0]
        if (file && file.arrayBuffer && file.size < 1024 * 1024 * 3) {
            file.arrayBuffer().then(buffer => {
                const bytes = Array.from(new Uint8Array(buffer))
                const filename = file.name
                const filetype = file.type
                window.TelegramWebview.resolveShare(JSON.stringify({ url, title, text }), bytes, filename, filetype);
            })
        } else {
            window.TelegramWebview.resolveShare(JSON.stringify({ url, title, text }), null, null, null);
        }
        return new Promise((resolve, reject) => {
            window.navigator.__share__receive = reason => {
                window.navigator.__share__receive = undefined;
                if (!reason) {
                    resolve();
                } else if (reason === 'security') {
                    reject(new DOMException("Must be handling a user gesture to perform a share", "NotAllowedError"));
                } else if (reason === 'abort') {
                    reject(new DOMException("The operation was aborted.", "AbortError"));
                } else {
                    reject(new DOMException("", "DataError"));
                }
            };
        });
    };
}