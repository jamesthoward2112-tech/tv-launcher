package uk.co.howardtv;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Events from published ICS feeds (Outlook, Google, ...), with the recurring rules such feeds
 * use (daily, weekly, monthly, yearly; BYDAY, BYMONTHDAY, BYMONTH, INTERVAL, COUNT, UNTIL),
 * EXDATEs, moved occurrences (RECURRENCE-ID) and Windows time zone names. The same rules as
 * the ambient screensaver's calendar, ported from its JavaScript.
 */
final class Agenda {
    static final class Item {
        final ZonedDateTime start, end;
        final boolean allDay;
        final String summary;

        Item(ZonedDateTime start, ZonedDateTime end, boolean allDay, String summary) {
            this.start = start;
            this.end = end;
            this.allDay = allDay;
            this.summary = summary;
        }
    }

    private static final class Prop {
        final String value;
        final Map<String, String> params;

        Prop(String value, Map<String, String> params) {
            this.value = value;
            this.params = params;
        }
    }

    /** A parsed date or date-time: its wall-clock fields and zone (null when floating). */
    private static final class Dt {
        final LocalDateTime wall;
        final boolean allDay;
        final ZoneId zone;

        Dt(LocalDateTime wall, boolean allDay, ZoneId zone) {
            this.wall = wall;
            this.allDay = allDay;
            this.zone = zone;
        }

        ZonedDateTime at(ZoneId local) {
            return wall.atZone(zone != null ? zone : local);
        }
    }

    private static final Map<String, String> WIN_TZ = new HashMap<>();
    static {
        String[] pairs = {
            "Central Standard Time", "America/Chicago", "Eastern Standard Time", "America/New_York",
            "Mountain Standard Time", "America/Denver", "US Mountain Standard Time", "America/Phoenix",
            "Pacific Standard Time", "America/Los_Angeles", "Alaskan Standard Time", "America/Anchorage",
            "Hawaiian Standard Time", "Pacific/Honolulu", "GMT Standard Time", "Europe/London",
            "W. Europe Standard Time", "Europe/Berlin", "Romance Standard Time", "Europe/Paris",
            "Central Europe Standard Time", "Europe/Budapest", "China Standard Time", "Asia/Shanghai",
            "Tokyo Standard Time", "Asia/Tokyo", "India Standard Time", "Asia/Kolkata",
            "Korea Standard Time", "Asia/Seoul", "AUS Eastern Standard Time", "Australia/Sydney",
            "UTC", "UTC", "Coordinated Universal Time", "UTC",
        };
        for (int i = 0; i < pairs.length; i += 2) WIN_TZ.put(pairs[i], pairs[i + 1]);
    }
    private static final List<String> WD = Arrays.asList("SU", "MO", "TU", "WE", "TH", "FR", "SA");
    private static final Pattern DATE = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})(?:T(\\d{2})(\\d{2})(\\d{2})?(Z)?)?$");
    private static final Pattern NTH_DAY = Pattern.compile("^([+-]?\\d+)?([A-Z]{2})$");
    private static final Pattern DURATION =
            Pattern.compile("P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?");

    private final List<Map<String, Object>> events = new ArrayList<>();
    /** "uid|epoch day" of each occurrence that a RECURRENCE-ID event replaces. */
    private final Set<String> overrides = new HashSet<>();

    /** Every VEVENT in text, which may hold several feeds one after another. */
    Agenda(String text) {
        String[] lines = text.replace("\r\n ", "").replace("\r\n\t", "").replace("\n ", "").replace("\n\t", "")
                .split("\r?\n");
        Map<String, Object> cur = null;
        int depth = 0;
        for (String line : lines) {
            if (line.equals("BEGIN:VEVENT")) { cur = new HashMap<>(); depth = 0; continue; }
            if (cur == null) continue;
            if (line.startsWith("BEGIN:")) { depth++; continue; }
            if (line.startsWith("END:") && depth > 0) { depth--; continue; }
            if (line.equals("END:VEVENT")) { events.add(cur); cur = null; continue; }
            if (depth > 0) continue;  // VALARM and the like
            int i = line.indexOf(':');
            if (i < 0) continue;
            String[] head = line.substring(0, i).split(";");
            Map<String, String> params = new HashMap<>();
            for (int k = 1; k < head.length; k++) {
                int j = head[k].indexOf('=');
                if (j > 0) params.put(head[k].substring(0, j).toUpperCase(), head[k].substring(j + 1));
            }
            String key = head[0].toUpperCase(), value = line.substring(i + 1);
            if (key.equals("EXDATE")) {
                @SuppressWarnings("unchecked")
                List<Prop> ex = (List<Prop>) cur.computeIfAbsent("EXDATE", k -> new ArrayList<Prop>());
                for (String v : value.split(",")) ex.add(new Prop(v, params));
            } else {
                cur.put(key, new Prop(value, params));
            }
        }
        for (Map<String, Object> ev : events) {
            Dt r = parseDt(prop(ev, "RECURRENCE-ID"));
            if (r != null && prop(ev, "UID") != null) overrides.add(prop(ev, "UID").value + "|" + r.wall.toLocalDate().toEpochDay());
        }
    }

    boolean isEmpty() {
        return events.isEmpty();
    }

    private static Prop prop(Map<String, Object> ev, String key) {
        Object o = ev.get(key);
        return o instanceof Prop ? (Prop) o : null;
    }

    private static ZoneId zone(String tzid) {
        if (tzid == null) return null;
        tzid = tzid.replaceAll("^\"|\"$", "");
        if (WIN_TZ.containsKey(tzid)) tzid = WIN_TZ.get(tzid);
        try {
            return ZoneId.of(tzid);
        } catch (Exception e) {
            return null;
        }
    }

    private static Dt parseDt(Prop p) {
        if (p == null) return null;
        Matcher m = DATE.matcher(p.value.trim());
        if (!m.matches()) return null;
        boolean allDay = m.group(4) == null;
        LocalDateTime wall = LocalDateTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)), allDay ? 0 : Integer.parseInt(m.group(4)),
                allDay ? 0 : Integer.parseInt(m.group(5)), m.group(6) == null ? 0 : Integer.parseInt(m.group(6)));
        ZoneId z = allDay ? null : m.group(7) != null ? ZoneOffset.UTC : zone(p.params.get("TZID"));
        return new Dt(wall, allDay, z);
    }

    private static String unescape(String s) {
        return s.replaceAll("(?i)\\\\n", " ").replaceAll("\\\\([,;\\\\])", "$1");
    }

    private static int wd(LocalDate d) {
        return d.getDayOfWeek().getValue() % 7;  // SU = 0, as in WD
    }

    private static int intOr(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Does a rule starting on s produce an occurrence on t? (COUNT and UNTIL are checked by the caller.) */
    private static boolean ruleHits(Map<String, String> r, LocalDate s, LocalDate t) {
        long sn = s.toEpochDay(), tn = t.toEpochDay();
        if (tn < sn) return false;
        int iv = Math.max(1, intOr(r.get("INTERVAL"), 1));
        int twd = wd(t);
        List<String> byday = r.containsKey("BYDAY") ? Arrays.asList(r.get("BYDAY").split(",")) : null;
        List<String> bymd = r.containsKey("BYMONTHDAY") ? Arrays.asList(r.get("BYMONTHDAY").split(",")) : null;
        List<String> bymo = r.containsKey("BYMONTH") ? Arrays.asList(r.get("BYMONTH").split(",")) : null;
        if (bymo != null && !bymo.contains(String.valueOf(t.getMonthValue()))) return false;
        String freq = String.valueOf(r.get("FREQ"));
        switch (freq) {
            case "DAILY":
                return (tn - sn) % iv == 0 && (byday == null || byday.contains(WD.get(twd)));
            case "WEEKLY": {
                int wkst = Math.max(0, WD.indexOf(r.getOrDefault("WKST", "MO")));
                int swd = wd(s);
                long sw = sn - ((swd - wkst + 7) % 7), tw = tn - ((twd - wkst + 7) % 7);
                if (((tw - sw) / 7) % iv != 0) return false;
                return byday != null ? byday.contains(WD.get(twd)) : twd == swd;
            }
            case "MONTHLY":
                if ((((t.getYear() - s.getYear()) * 12L) + t.getMonthValue() - s.getMonthValue()) % iv != 0) return false;
                return dayMatches(byday, bymd, s, t);
            case "YEARLY":
                if ((t.getYear() - s.getYear()) % iv != 0) return false;
                if (bymo == null && t.getMonthValue() != s.getMonthValue()) return false;
                return dayMatches(byday, bymd, s, t);
            default:
                return false;
        }
    }

    private static boolean dayMatches(List<String> byday, List<String> bymd, LocalDate s, LocalDate t) {
        int len = t.lengthOfMonth(), d = t.getDayOfMonth();
        if (bymd != null) {
            for (String x : bymd) {
                int n = intOr(x, 0);
                if (n == d || (n < 0 && len + n + 1 == d)) return true;
            }
            return false;
        }
        if (byday != null) {
            for (String b : byday) {
                Matcher m = NTH_DAY.matcher(b);
                if (!m.matches() || WD.indexOf(m.group(2)) != wd(t)) continue;
                if (m.group(1) == null) return true;
                int n = Integer.parseInt(m.group(1).replace("+", ""));
                int nth = (d - 1) / 7 + 1, fromEnd = (len - d) / 7 + 1;
                if (n > 0 ? n == nth : -n == fromEnd) return true;
            }
            return false;
        }
        return d == s.getDayOfMonth();
    }

    /** Occurrences of ev that overlap the local day starting at dStart. */
    private void occurrences(Map<String, Object> ev, boolean recurring, ZonedDateTime dStart, ZoneId local, List<Item> out) {
        Dt st = parseDt(prop(ev, "DTSTART"));
        if (st == null) return;
        Dt en = parseDt(prop(ev, "DTEND"));
        Duration dur = en != null ? Duration.between(st.at(local), en.at(local)) : st.allDay ? Duration.ofDays(1) : Duration.ZERO;
        long days = en != null && st.allDay ? Math.max(1, en.wall.toLocalDate().toEpochDay() - st.wall.toLocalDate().toEpochDay()) : 1;
        Prop dp = prop(ev, "DURATION");
        if (dp != null && en == null) {
            Matcher m = DURATION.matcher(dp.value);
            if (m.find()) {
                long d = intOr(m.group(1), 0) * 7L + intOr(m.group(2), 0);
                dur = Duration.ofDays(d).plusHours(intOr(m.group(3), 0)).plusMinutes(intOr(m.group(4), 0))
                        .plusSeconds(intOr(m.group(5), 0));
                if (st.allDay) days = Math.max(1, d);
            }
        }
        ZonedDateTime dEnd = dStart.plusDays(1);
        Prop sp = prop(ev, "SUMMARY");
        String summary = unescape(sp != null ? sp.value : "(No title)");
        Prop rp = recurring ? prop(ev, "RRULE") : null;
        if (rp == null) {
            add(item(st.allDay ? st.wall.toLocalDate() : null, st.at(local), dur, days, summary, local), dStart, dEnd, out);
            return;
        }
        Map<String, String> r = new HashMap<>();
        for (String kv : rp.value.split(";")) {
            int i = kv.indexOf('=');
            if (i > 0) r.put(kv.substring(0, i).toUpperCase(), kv.substring(i + 1));
        }
        Dt until = r.containsKey("UNTIL") ? parseDt(new Prop(r.get("UNTIL"), new HashMap<>())) : null;
        Set<Long> ex = new HashSet<>();
        @SuppressWarnings("unchecked")
        List<Prop> exdates = (List<Prop>) ev.get("EXDATE");
        if (exdates != null) for (Prop x : exdates) {
            Dt p = parseDt(x);
            if (p != null) ex.add(p.wall.toLocalDate().toEpochDay());
        }
        Prop uid = prop(ev, "UID");
        LocalDate s = st.wall.toLocalDate();
        // The day itself and the day before (an occurrence from yesterday may run into today).
        for (int back = 1; back >= 0; back--) {
            ZonedDateTime cand = dStart.minusDays(back).plusHours(12);
            LocalDate t = cand.withZoneSameInstant(st.zone != null ? st.zone : local).toLocalDate();
            if (!ruleHits(r, s, t)) continue;
            long tn = t.toEpochDay();
            if (ex.contains(tn) || (uid != null && overrides.contains(uid.value + "|" + tn))) continue;
            ZonedDateTime start = st.allDay ? t.atStartOfDay(local)
                    : ZonedDateTime.of(t, st.wall.toLocalTime(), st.zone != null ? st.zone : local);
            if (until != null && start.isAfter(until.at(local).plusSeconds(until.allDay ? 86399 : 0))) continue;
            if (r.containsKey("COUNT")) {
                int count = intOr(r.get("COUNT"), Integer.MAX_VALUE), n = 0;
                for (long k = s.toEpochDay(); k <= tn && n <= count; k++) if (ruleHits(r, s, LocalDate.ofEpochDay(k))) n++;
                if (n > count) continue;
            }
            add(item(st.allDay ? t : null, start, dur, days, summary, local), dStart, dEnd, out);
        }
    }

    /** An all-day item (allDayDate set) covers whole local days; a timed one runs for dur. */
    private static Item item(LocalDate allDayDate, ZonedDateTime start, Duration dur, long days, String summary, ZoneId local) {
        if (allDayDate != null) {
            ZonedDateTime s = allDayDate.atStartOfDay(local);
            return new Item(s, s.plusDays(days), true, summary);
        }
        ZonedDateTime s = start.withZoneSameInstant(local);  // shown in the TV's time, not the event's
        return new Item(s, s.plus(dur), false, summary);
    }

    private static void add(Item o, ZonedDateTime dStart, ZonedDateTime dEnd, List<Item> out) {
        boolean instant = o.end.equals(o.start);
        if (o.start.isBefore(dEnd) && (o.end.isAfter(dStart) || (instant && !o.start.isBefore(dStart)))) out.add(o);
    }

    /** The day's events, all-day ones first, then by start; cancelled ones left out. */
    List<Item> on(LocalDate day, ZoneId local) {
        ZonedDateTime dStart = day.atStartOfDay(local);
        List<Item> out = new ArrayList<>();
        for (Map<String, Object> ev : events) {
            Prop status = prop(ev, "STATUS");
            if (status != null && status.value.equals("CANCELLED")) continue;
            occurrences(ev, !ev.containsKey("RECURRENCE-ID"), dStart, local, out);
        }
        Set<String> seen = new HashSet<>();
        List<Item> unique = new ArrayList<>();
        for (Item o : out) if (seen.add(o.summary + o.start.toInstant())) unique.add(o);
        unique.sort((a, b) -> a.allDay != b.allDay ? (a.allDay ? -1 : 1) : a.start.compareTo(b.start));
        return unique;
    }
}
