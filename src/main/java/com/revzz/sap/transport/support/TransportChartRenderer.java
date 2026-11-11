package com.revzz.sap.transport.support;

import com.revzz.sap.transport.domain.ObjectConflict;
import com.revzz.sap.transport.domain.ReleaseEntry;
import com.revzz.sap.transport.domain.Severity;
import com.revzz.sap.transport.domain.TransportAssessment;

import java.util.List;

/**
 * Server-side SVG charts.
 *
 * <p>The charts are drawn on the server, as plain SVG markup, with no chart
 * library, no CDN and no client-side JavaScript. That is a deliberate choice
 * for a tool that has to run inside a corporate landscape where outbound
 * internet access is often blocked, and where a change board has to be able
 * to save the page and still see the evidence.</p>
 */
public final class TransportChartRenderer {

    private TransportChartRenderer() {
    }

    /** The colour of a severity band. */
    public static String severityColour(Severity severity) {
        if (severity == null) {
            return "#63748a";
        }
        switch (severity) {
            case CRITICAL:
                return "#d7263d";
            case HIGH:
                return "#e8710a";
            case MEDIUM:
                return "#b8860b";
            case LOW:
                return "#1a7f5a";
            default:
                return "#2e9e6b";
        }
    }

    /** The colour of a release train state. */
    public static String stateColour(String state) {
        if (state == null) {
            return "#63748a";
        }
        if (ReleaseEntry.STATE_READY.equals(state)) {
            return "#2e9e6b";
        }
        if (ReleaseEntry.STATE_BLOCKED.equals(state)) {
            return "#d7263d";
        }
        if (ReleaseEntry.STATE_IN_DEVELOPMENT.equals(state)) {
            return "#e8710a";
        }
        return "#63748a";
    }

    /**
     * A horizontal bar chart of the change readiness score of every request.
     *
     * @param assessments the requests, already sorted worst first
     */
    public static String requestBars(List<TransportAssessment> assessments) {
        int width = 900;
        int labelWidth = 108;
        int valueWidth = 74;
        int rowHeight = 34;
        int top = 46;
        int height = top + Math.max(1, assessments.size()) * rowHeight + 16;

        int barWidth = width - labelWidth - valueWidth - 24;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg class=\"requests\" viewBox=\"0 0 ").append(width).append(' ')
                .append(height).append("\" xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" ")
                .append("aria-label=\"Change readiness score by transport request\">");

        svg.append("<text x=\"0\" y=\"18\" class=\"c-head\">READINESS SCORE BY REQUEST</text>");
        svg.append("<text x=\"").append(width).append("\" y=\"18\" class=\"c-scale\" ")
                .append("text-anchor=\"end\">0 &#8211; 100</text>");

        svg.append("<rect x=\"").append(labelWidth).append("\" y=\"").append(top - 14)
                .append("\" width=\"").append(barWidth)
                .append("\" height=\"").append(Math.max(1, assessments.size()) * rowHeight)
                .append("\" fill=\"#eef2f7\" rx=\"4\"/>");

        for (int i = 0; i < assessments.size(); i++) {
            TransportAssessment assessment = assessments.get(i);
            int y = top + i * rowHeight;
            int filled = Math.round(barWidth
                    * Math.max(0, Math.min(100, assessment.getScore())) / 100f);
            String colour = severityColour(assessment.getSeverity());

            svg.append("<text x=\"0\" y=\"").append(y + 5)
                    .append("\" class=\"c-label\">")
                    .append(esc(assessment.getRequest().getTrkorr())).append("</text>");
            svg.append("<rect x=\"").append(labelWidth).append("\" y=\"").append(y - 11)
                    .append("\" width=\"").append(Math.max(2, filled))
                    .append("\" height=\"20\" fill=\"").append(colour).append("\" rx=\"3\"/>");
            svg.append("<text x=\"").append(labelWidth + barWidth + 8).append("\" y=\"").append(y + 4)
                    .append("\" class=\"c-value\" fill=\"").append(colour).append("\">")
                    .append(assessment.getScore()).append("</text>");
            svg.append("<text x=\"").append(labelWidth + barWidth + 40).append("\" y=\"").append(y + 4)
                    .append("\" class=\"c-sub\">").append(assessment.getSeverity().name())
                    .append("</text>");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    /**
     * The object conflicts: how many open requests hold each contested object.
     */
    public static String conflictBars(List<ObjectConflict> conflicts) {
        int width = 900;
        int labelWidth = 300;
        int rowHeight = 30;
        int top = 46;
        int height = top + Math.max(1, conflicts.size()) * rowHeight + 16;
        int barWidth = width - labelWidth - 150;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg class=\"conflicts\" viewBox=\"0 0 ").append(width).append(' ')
                .append(height).append("\" xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" ")
                .append("aria-label=\"Objects held by more than one open request\">");

        svg.append("<text x=\"0\" y=\"18\" class=\"c-head\">OBJECT CONFLICTS</text>");
        svg.append("<text x=\"").append(width).append("\" y=\"18\" class=\"c-scale\" ")
                .append("text-anchor=\"end\">open requests holding the object</text>");

        for (int i = 0; i < conflicts.size(); i++) {
            ObjectConflict conflict = conflicts.get(i);
            int y = top + i * rowHeight;
            int filled = (int) Math.round(barWidth * Math.min(1.0, conflict.requestCount() / 3.0));

            svg.append("<text x=\"0\" y=\"").append(y + 4)
                    .append("\" class=\"c-label\">").append(esc(conflict.getObjectName()))
                    .append("</text>");
            svg.append("<text x=\"").append(labelWidth - 12).append("\" y=\"").append(y + 4)
                    .append("\" class=\"c-sub\" text-anchor=\"end\">")
                    .append(esc(conflict.getObjectType())).append("</text>");
            svg.append("<rect x=\"").append(labelWidth).append("\" y=\"").append(y - 10)
                    .append("\" width=\"").append(Math.max(2, filled))
                    .append("\" height=\"18\" fill=\"#d7263d\" rx=\"3\"/>");
            svg.append("<text x=\"").append(labelWidth + barWidth + 10).append("\" y=\"").append(y + 4)
                    .append("\" class=\"c-value\" fill=\"#d7263d\">")
                    .append(conflict.requestCount()).append("</text>");
            svg.append("<text x=\"").append(labelWidth + barWidth + 40).append("\" y=\"").append(y + 4)
                    .append("\" class=\"c-sub\">").append(esc(conflict.getRequestList()))
                    .append("</text>");
        }

        if (conflicts.isEmpty()) {
            svg.append("<text x=\"0\" y=\"").append(top + 4)
                    .append("\" class=\"c-sub\">No object is held by more than one open request.")
                    .append("</text>");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    /**
     * The release train: the requests for one target system, in import order.
     */
    public static String releaseTrain(List<ReleaseEntry> train) {
        int boxWidth = 152;
        int boxHeight = 58;
        int gap = 26;
        int top = 54;
        int width = Math.max(420, 20 + Math.max(1, train.size()) * (boxWidth + gap));
        int height = top + boxHeight + 40;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg class=\"train\" viewBox=\"0 0 ").append(width).append(' ')
                .append(height).append("\" xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" ")
                .append("aria-label=\"Release train\">");

        svg.append("<text x=\"0\" y=\"18\" class=\"c-head\">RELEASE TRAIN</text>");
        svg.append("<text x=\"").append(width).append("\" y=\"18\" class=\"c-scale\" ")
                .append("text-anchor=\"end\">import order</text>");

        if (train.isEmpty()) {
            svg.append("<text x=\"0\" y=\"").append(top + 20)
                    .append("\" class=\"c-sub\">No requests target this system.</text>");
            svg.append("</svg>");
            return svg.toString();
        }

        for (int i = 0; i < train.size(); i++) {
            ReleaseEntry entry = train.get(i);
            int x = 10 + i * (boxWidth + gap);
            int y = top;
            String colour = stateColour(entry.getState());

            if (i > 0) {
                svg.append("<line x1=\"").append(x - gap + 4).append("\" y1=\"")
                        .append(y + boxHeight / 2).append("\" x2=\"").append(x - 6)
                        .append("\" y2=\"").append(y + boxHeight / 2)
                        .append("\" class=\"c-arrow\"/>");
            }

            svg.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" width=\"").append(boxWidth).append("\" height=\"")
                    .append(boxHeight).append("\" rx=\"7\" fill=\"#ffffff\" stroke=\"")
                    .append(colour).append("\" stroke-width=\"2\"/>");
            svg.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" width=\"5\" height=\"").append(boxHeight)
                    .append("\" rx=\"2\" fill=\"").append(colour).append("\"/>");
            svg.append("<text x=\"").append(x + 14).append("\" y=\"").append(y + 22)
                    .append("\" class=\"c-box-title\">")
                    .append(esc(entry.getRequest().getTrkorr())).append("</text>");
            svg.append("<text x=\"").append(x + 14).append("\" y=\"").append(y + 39)
                    .append("\" class=\"c-box-sub\">").append(esc(entry.getState()))
                    .append(" · ").append(entry.getObjectCount()).append(" obj</text>");
            svg.append("<text x=\"").append(x + 14).append("\" y=\"").append(y + 52)
                    .append("\" class=\"c-box-sub\">")
                    .append(esc(entry.getRequest().getStatusText())).append("</text>");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    /** Escapes the five characters that matter in XML text and attributes. */
    public static String esc(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&apos;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }
}
