package org.telegram.utils.twidget;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a screen reader finds in a widget, as the renderer laid it out (Renderer::accessibility_json): its
 * paragraphs of text, headings, images with a text alternative, and what takes a press, each with its
 * name, its states and where it is, in document CSS px. Immutable; built on the worker, read on the UI.
 */
public final class WidgetAccessibility {

    public static final class Node {
        /** "text", "heading", "button", "link", "image", "checkbox", "radio", "switch", "tab", "menuitem",
         *  "option", "slider", "progressbar" or "spinbutton". */
        public final String role;
        public final String name;
        public final float x, y, width, height;
        /** The heading level, 1 to 6; 0 for anything else. */
        public final int level;
        /** aria-checked / aria-pressed / aria-selected / aria-expanded: "true", "false", "mixed" or null. */
        public final String checked, pressed, selected, expanded;
        public final boolean disabled, current;
        /** aria-valuenow, aria-valuemin, aria-valuemax; NaN when not given. */
        public final float value, min, max;
        /** aria-valuetext, or null. */
        public final String valueText;
        /** "polite" or "assertive" inside a live region; null otherwise. */
        public final String live;
        /** It takes a press (a button, a link, a check box and the like). */
        public final boolean actionable;

        Node(JSONObject o) {
            role = o.optString("r", "text");
            name = o.optString("n", "");
            x = (float) o.optDouble("x", 0);
            y = (float) o.optDouble("y", 0);
            width = (float) o.optDouble("w", 0);
            height = (float) o.optDouble("h", 0);
            level = o.optInt("lv", 0);
            JSONObject st = o.optJSONObject("st");
            checked = st != null ? st.optString("checked", null) : null;
            pressed = st != null ? st.optString("pressed", null) : null;
            selected = st != null ? st.optString("selected", null) : null;
            expanded = st != null ? st.optString("expanded", null) : null;
            disabled = st != null && "true".equals(st.optString("disabled", null));
            current = st != null && "true".equals(st.optString("current", null));
            value = (float) o.optDouble("vn", Double.NaN);
            min = (float) o.optDouble("vmin", Double.NaN);
            max = (float) o.optDouble("vmax", Double.NaN);
            valueText = o.has("vt") ? o.optString("vt", null) : null;
            live = o.has("live") ? o.optString("live", null) : null;
            actionable = o.optInt("act", 0) != 0;
        }

        public boolean isText() {
            return "text".equals(role);
        }

        public boolean isHeading() {
            return "heading".equals(role);
        }

        public boolean isCheckable() {
            return "checkbox".equals(role) || "radio".equals(role) || "switch".equals(role);
        }

        public boolean isRange() {
            return "slider".equals(role) || "progressbar".equals(role) || "spinbutton".equals(role);
        }

        /** What it says about itself beside its name, the same way each time; equal for an unchanged node. */
        String signature() {
            return role + '\u0001' + name + '\u0001' + checked + '\u0001' + pressed + '\u0001' + selected + '\u0001'
                    + expanded + '\u0001' + disabled + '\u0001' + value + '\u0001' + valueText;
        }
    }

    public static final WidgetAccessibility EMPTY = new WidgetAccessibility(Collections.emptyList());

    public final List<Node> nodes;

    private WidgetAccessibility(List<Node> nodes) {
        this.nodes = nodes;
    }

    /** The renderer's JSON; EMPTY when it is empty or unreadable. */
    public static WidgetAccessibility parse(String json) {
        try {
            JSONArray array = new JSONArray(json);
            if (array.length() == 0) return EMPTY;
            ArrayList<Node> nodes = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null) nodes.add(new Node(o));
            }
            return new WidgetAccessibility(Collections.unmodifiableList(nodes));
        } catch (Exception e) {
            return EMPTY;
        }
    }

    /** What a live region says now that it did not say in `before`, or null. */
    public CharSequence liveChange(WidgetAccessibility before) {
        if (before == null) return null;
        StringBuilder said = null;
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.live == null) continue;
            boolean known = false;
            if (before != null) {
                for (int j = 0; j < before.nodes.size(); j++) {
                    Node old = before.nodes.get(j);
                    if (old.live != null && old.signature().equals(node.signature())) {
                        known = true;
                        break;
                    }
                }
            }
            if (known || node.name.isEmpty()) continue;
            if (said == null) said = new StringBuilder();
            else said.append(". ");
            said.append(node.name);
        }
        return said;
    }
}
