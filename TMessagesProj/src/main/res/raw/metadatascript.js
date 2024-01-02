Object.fromEntries(
    [...document.querySelectorAll('meta')]
        .map(a => [
            a.getAttribute('property') || a.getAttribute('name') || a.getAttribute('http-equiv'),
            a.content
        ])
        .concat([
            ['image', (document.querySelector('article[data-testid=tweet]:first-child div[data-testid=tweetPhoto] img') || {src:null}).src]
        ])
        .filter(([k, v]) => k && v)
)