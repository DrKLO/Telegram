package org.telegram.messenger.chromecast;

import java.util.ArrayList;

public class ChromecastMediaVariations {
    private final ArrayList<ChromecastMedia> variations;

    private ChromecastMediaVariations(ArrayList<ChromecastMedia> list) {
        variations = list;
    }

    private ChromecastMediaVariations(ChromecastMedia media) {
        variations = new ArrayList<>(1);
        variations.add(media);
    }

    public int getVariationsCount () {
        return variations.size();
    }

    public ChromecastMedia getVariation(int index) {
        return variations.get(index);
    }

    public static ChromecastMediaVariations of (ChromecastMedia list) {
        return new ChromecastMediaVariations(list);
    }

    public static ChromecastMediaVariations of (ArrayList<ChromecastMedia> list) {
        return new ChromecastMediaVariations(list);
    }

    public static class Builder {
        private final ArrayList<ChromecastMedia> variations = new ArrayList<>();

        public Builder add (ChromecastMedia media) {
            this.variations.add(media);
            return this;
        }

        public ChromecastMediaVariations build () {
            return new ChromecastMediaVariations(this.variations);
        }

        public boolean isEmpty () {
            return this.variations.isEmpty();
        }
    }
}
