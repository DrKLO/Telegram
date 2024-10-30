(function (open) {
    if (open) {
        window.__tg__openedSections = [...document.querySelectorAll('section[hidden=until-found]')];
        window.__tg__openedSections.map(section => section.hidden = "");
    } else if (window.__tg__openedSections) {
        window.__tg__openedSections.map(section => section.hidden = "until-found");
        delete window.__tg__openedSections;
    }
})($OPEN$);