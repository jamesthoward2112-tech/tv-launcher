package uk.co.howardtv;

import org.json.JSONObject;

/** One line of weather from Open-Meteo (no key): "12° Clear · H 20° L 9°". */
final class Weather {
    private Weather() {}

    static String fetch(double lat, double lon, String units) throws Exception {
        JSONObject w = new JSONObject(Net.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat
                + "&longitude=" + lon + "&current=temperature_2m,weather_code"
                + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
                + "&temperature_unit=" + (units.equals("F") ? "fahrenheit" : "celsius")));
        JSONObject cur = w.getJSONObject("current"), day = w.getJSONObject("daily");
        return Math.round(cur.getDouble("temperature_2m")) + "° " + describe(cur.getInt("weather_code"))
                + "  ·  H " + Math.round(day.getJSONArray("temperature_2m_max").getDouble(0))
                + "°  L " + Math.round(day.getJSONArray("temperature_2m_min").getDouble(0)) + "°";
    }

    /** WMO weather interpretation codes, as Open-Meteo reports them. */
    private static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorms";
        return "";
    }
}
